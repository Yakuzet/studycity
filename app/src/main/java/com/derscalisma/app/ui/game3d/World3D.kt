package com.derscalisma.app.ui.game3d

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import com.derscalisma.app.data.game.VILLAGE_SIZE
import com.google.android.filament.View
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.texture.ImageTexture
import io.github.sceneview.utils.screenToRay
import io.github.sceneview.utils.worldToScreen
import com.google.android.filament.Engine
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Yerleştirilmiş bir parça (canlandırma için başlangıç durumu saklanır). */
class Placed(val node: ModelNode, val part: Part, val basePos: Position, val baseRotY: Float, val scale: Float, val topY: Float)

/**
 * 3D sahnenin yardımcısı. Dünya koordinatları: 1 birim = 1 karo, Y yukarı, harita merkezi (0,0).
 */
class World3D(
    val engine: Engine,
    val modelLoader: ModelLoader,
    val materialLoader: MaterialLoader,
    val view: View,
    val root: Node,
    val camera: CameraNode,
    val light: LightNode?,
) {
    val n = VILLAGE_SIZE

    fun wx(tileX: Float) = tileX - n / 2f
    fun wz(tileY: Float) = tileY - n / 2f

    // ------------------------------------------------------------------ modeller

    fun model(key: String): ModelNode? = try {
        ModelNode(modelInstance = modelLoader.createModelInstance("models/$key.glb"), autoAnimate = false).apply {
            isShadowCaster = true
            isShadowReceiver = true
        }
    } catch (e: Throwable) {
        android.util.Log.w("World3D", "Model yüklenemedi: $key (${e.javaClass.simpleName}: ${e.message})")
        null
    }

    private fun rotXZ(x: Float, z: Float, deg: Float): Pair<Float, Float> {
        val r = Math.toRadians(deg.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        return (x * c + z * s) to (-x * s + z * c)
    }

    /** Modeli ölçekler, tabanını [baseY]'ye oturtur ve merkezini (cx, cz)'ye getirir. */
    fun place(node: ModelNode, key: String, cx: Float, cz: Float, baseY: Float, scale: Float, rotY: Float): Position {
        val b = MODEL_BOUNDS[key] ?: MBounds(-0.5f, 0.5f, 0f, 1f, -0.5f, 0.5f)
        val (ox, oz) = rotXZ(b.cx * scale, b.cz * scale, rotY)
        val pos = Position(cx - ox, baseY - b.minY * scale, cz - oz)
        node.scale = Scale(scale)
        node.rotation = Rotation(0f, rotY, 0f)
        node.position = pos
        return pos
    }

    /** Bir tarifi (cx, cz) merkezli kurar ve kök düğüme ekler. */
    fun build(parts: List<Part>, cx: Float, cz: Float, footprint: Float, sizeMul: Float = 1f): List<Placed> {
        val out = ArrayList<Placed>()
        var top = 0f
        var prevScale = 1f
        for (p in parts) {
            val b = MODEL_BOUNDS[p.model] ?: continue
            val scale = when {
                p.keepScale -> prevScale
                p.height != null -> p.height * sizeMul / b.h
                else -> (p.width ?: footprint) * sizeMul / max(b.w, b.d)
            }
            val baseY = if (p.onTop) top else p.y * sizeMul
            val node = model(p.model) ?: continue
            val pos = place(node, p.model, cx + p.dx * sizeMul, cz + p.dz * sizeMul, baseY, scale, p.rotY)
            top = baseY + b.h * scale
            prevScale = scale
            root.addChildNode(node)
            out += Placed(node, p, pos, p.rotY, scale, top)
        }
        return out
    }

    fun remove(list: List<Placed>) = list.forEach { remove(it.node) }

    /**
     * Düğümü sahneden çıkarır. Model düğümlerinin varlığı yükleyici üzerinden silinir
     * (node.destroy() varlığın kök öğesini siler, yükleyici kapanırken ikinci kez silmeye çalışıp çöker).
     */
    fun remove(node: Node) {
        root.removeChildNode(node)
        if (node is ModelNode) runCatching { modelLoader.destroyModel(node.modelInstance.asset) }
        else runCatching { node.destroy() }
    }

    /** Ekran kapanırken: yükleyiciler kapanmadan önce tüm düğümleri sahneden ayır (kare döngüsü onlara dokunmasın). */
    fun detachAll() {
        root.childNodes.toList().forEach { root.removeChildNode(it) }
    }

    /** Karakter: boyu [heightTiles] olacak şekilde ölçeklenir. */
    fun character(key: String, heightTiles: Float): ModelNode? {
        val node = model(key) ?: return null
        val b = MODEL_BOUNDS[key] ?: return node
        node.scale = Scale(heightTiles / b.h)
        root.addChildNode(node)
        return node
    }

    fun setPos(node: ModelNode, key: String, wx: Float, wz: Float, y: Float = 0f) {
        val b = MODEL_BOUNDS[key]
        val s = node.scale.x
        node.position = Position(wx, y - (b?.minY ?: 0f) * s, wz)
    }

    /** Karakterin yüzünü (dx, dz) yönüne çevirir. Kenney karakterleri +Z'ye bakar. */
    fun face(node: Node, dx: Float, dz: Float) {
        if (dx * dx + dz * dz < 1e-5f) return
        node.rotation = Rotation(0f, Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat(), 0f)
    }

    // ------------------------------------------------------------------ zemin ve çevre

    fun ground(tex: Bitmap, seed: Int) {
        val texture = ImageTexture.Builder().bitmap(tex).build(engine)
        val mat = materialLoader.createTextureInstance(texture, isOpaque = true, roughness = 1f)
        root.addChildNode(
            CubeNode(engine, size = Size(n.toFloat(), 0.2f, n.toFloat()), center = Position(0f, -0.1f, 0f), materialInstance = mat).apply {
                isShadowReceiver = true
            }
        )
        val outer = materialLoader.createColorInstance(androidx.compose.ui.graphics.Color(0xFF3E7A35), roughness = 1f)
        root.addChildNode(CubeNode(engine, size = Size(140f, 0.2f, 140f), center = Position(0f, -0.22f, 0f), materialInstance = outer))
        // Haritanın çevresinde orman
        val rnd = Random(seed)
        val half = n / 2f
        for (k in 0 until 70) {
            val side = k % 4
            val along = rnd.nextFloat() * (n + 8) - half - 4
            val off = half + 0.6f + rnd.nextFloat() * 5f
            val (x, z) = when (side) {
                0 -> along to -off
                1 -> along to off
                2 -> -off to along
                else -> off to along
            }
            val key = Models3D.BORDER_TREES[rnd.nextInt(Models3D.BORDER_TREES.size)]
            val node = model(key) ?: continue
            val b = MODEL_BOUNDS[key] ?: continue
            place(node, key, x, z, 0f, (1.4f + rnd.nextFloat() * 1.2f) / b.h, rnd.nextFloat() * 360f)
            root.addChildNode(node)
        }
    }

    // ------------------------------------------------------------------ ışık: gündüz / gece

    private var baseIntensity = -1f

    /** Gerçek saate göre güneş: sabah sıcak, öğlen beyaz, akşam turuncu, gece mavi ve loş. */
    fun daylight(hourOfDay: Float) {
        val l = light ?: return
        if (baseIntensity < 0) baseIntensity = l.intensity
        val h = hourOfDay
        val (k, r, g, b) = when {
            h < 5f || h >= 21f -> listOf(0.12f, 0.55f, 0.65f, 1f)
            h < 7f -> listOf(0.45f, 1f, 0.75f, 0.55f)
            h < 17f -> listOf(1f, 1f, 0.98f, 0.92f)
            h < 19.5f -> listOf(0.7f, 1f, 0.72f, 0.45f)
            else -> listOf(0.3f, 0.7f, 0.6f, 0.9f)
        }
        l.intensity = baseIntensity * k
        l.color = io.github.sceneview.math.Color(r, g, b, 1f)
        val ang = ((h - 6f) / 12f).coerceIn(0f, 1f) * Math.PI.toFloat()
        l.lightDirection = Direction(-cos(ang) * 0.6f, -0.85f, -0.45f)
    }

    // ------------------------------------------------------------------ kamera

    var yaw = 35f
    var pitch = 52f
    var dist = 22f
    var targetX = 0f
    var targetZ = 0f

    fun updateCamera() {
        val yr = Math.toRadians(yaw.toDouble())
        val pr = Math.toRadians(pitch.toDouble())
        val x = targetX + (sin(yr) * cos(pr) * dist).toFloat()
        val y = (sin(pr) * dist).toFloat()
        val z = targetZ + (cos(yr) * cos(pr) * dist).toFloat()
        camera.position = Position(x, y, z)
        camera.lookAt(Position(targetX, 0f, targetZ))
    }

    /** Ekrandaki kaydırmayı kameraya göre yere çevirir. */
    fun pan(dxPx: Float, dyPx: Float, viewH: Float) {
        val k = dist / max(viewH, 1f) * 1.2f
        val yr = Math.toRadians(yaw.toDouble())
        val rightX = cos(yr).toFloat()
        val rightZ = -sin(yr).toFloat()
        val fwdX = -sin(yr).toFloat()
        val fwdZ = -cos(yr).toFloat()
        targetX = (targetX - (dxPx * rightX - dyPx * fwdX) * k).coerceIn(-n / 2f, n / 2f)
        targetZ = (targetZ - (dxPx * rightZ - dyPx * fwdZ) * k).coerceIn(-n / 2f, n / 2f)
    }

    /** Ekran noktasından yere (y=0) ışın atar; karo koordinatı döner. */
    fun tileAt(px: Float, py: Float): Pair<Float, Float>? {
        val ray = runCatching { view.screenToRay(px, py) }.getOrNull() ?: return null
        val o = ray.origin
        val d = ray.direction
        if (d.y > -1e-4f) return null
        val t = -o.y / d.y
        val x = o.x + d.x * t
        val z = o.z + d.z * t
        return (x + n / 2f) to (z + n / 2f)
    }

    /** Dünya noktasını ekran pikseline çevirir. */
    fun screen(x: Float, y: Float, z: Float): Offset? = runCatching {
        val p = view.worldToScreen(Position(x, y, z))
        Offset(p.x, p.y)
    }.getOrNull()

    /** O derinlikte 1 karonun kaç piksel olduğu (efekt boyutları için). */
    fun pxPerTile(x: Float, y: Float, z: Float): Float {
        val a = screen(x, y, z) ?: return 20f
        val b = screen(x + 1f, y, z) ?: return 20f
        return (b - a).getDistance().coerceIn(2f, 400f)
    }
}
