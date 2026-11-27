package com.derscalisma.app.ui.game3d

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import com.derscalisma.app.data.AppData
import com.derscalisma.app.data.Time
import com.derscalisma.app.data.game.*
import com.derscalisma.app.ui.game.Ghost
import com.derscalisma.app.ui.game.VillageView
import com.derscalisma.app.ui.game.healthBar
import com.derscalisma.app.ui.game.rememberSprites
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberView
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** 3D köy. Önizleme / test ortamında 2D köye düşer. */
@Composable
fun Village3D(
    data: AppData,
    now: Long,
    selectedId: String?,
    ghost: Ghost?,
    onTap: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        VillageView(data.game, now, selectedId, ghost, onTap, modifier)
        return
    }
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val view = rememberView(engine)
    val cameraNode = rememberCameraNode(engine)
    val light = rememberMainLightNode(engine)
    val root = remember { Node(engine) }
    val nodes = rememberNodes { add(root) }
    val filamentScene = io.github.sceneview.rememberScene(engine)
    val sprites = rememberSprites()
    val world = remember {
        World3D(engine, modelLoader, materialLoader, view, root, cameraNode, light).also {
            it.ground(sprites.ground(4242).asAndroidBitmap(), 7)
            it.updateCamera()
        }
    }
    val scene = remember { VillageScene(world) }
    // Yükleyiciler kapanmadan önce düğümleri ayır (bu efekt sonra tanımlandığı için önce çalışır).
    DisposableEffect(world) { onDispose { world.detachAll() } }
    var frame by remember { mutableLongStateOf(0L) }
    val latest by rememberUpdatedState(data)
    val tap by rememberUpdatedState(onTap)

    Box(modifier) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            view = view,
            cameraNode = cameraNode,
            mainLightNode = light,
            childNodes = nodes,
            scene = filamentScene,
            cameraManipulator = null,
            onGestureListener = null,
            onFrame = { t ->
                scene.frame(latest, t)
                frame++
            },
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        world.pan(pan.x, pan.y, size.height.toFloat())
                        world.dist = (world.dist / zoom).coerceIn(7f, 34f)
                        world.yaw -= rotation
                        world.updateCamera()
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        world.tileAt(p.x, p.y)?.let { (x, z) -> if (x >= 0 && z >= 0) tap(x.toInt(), z.toInt()) }
                    }
                }
        ) {
            frame
            scene.overlay(this, latest, now, selectedId, ghost)
        }
    }
}

/** 3D köyün içeriği: binalar, surlar, işçiler, köylüler, canavar. */
class VillageScene(private val w: World3D) {
    private class Vis(val sig: String, val parts: List<Placed>)
    private class Walker(val node: ModelNode, val key: String, var x: Float, var z: Float, var tx: Float, var tz: Float, var wait: Float = 0f, var walking: Boolean = false)

    private val buildings = HashMap<String, Vis>()
    private val workers = HashMap<String, ModelNode>()
    private val walkers = ArrayList<Walker>()
    private var boss: ModelNode? = null
    private var bossAnim = ""
    private var lastGame: GameState? = null
    private var time = 0f
    private var lastNanos = 0L
    private var lastLight = -100f
    private val rnd = Random(11)
    private var bubbleText: String? = null
    private var bubbleOwner = 0
    private var bubbleUntil = 0f
    private var nextBubble = 3f

    fun frame(d: AppData, nanos: Long) {
        val dt = if (lastNanos == 0L) 0.016f else ((nanos - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = nanos
        time += dt
        if (d.game !== lastGame) {
            sync(d.game)
            lastGame = d.game
        }
        if (time - lastLight > 5f) {
            lastLight = time
            w.daylight(Time.minuteOfDay(System.currentTimeMillis()) / 60f)
        }
        animateParts(dt)
        animateWalkers(d, dt)
        animateBoss(d)
    }

    // ------------------------------------------------------------------ eşitleme

    private fun wallSig(g: GameState, b: GBuilding): String {
        val set = g.buildings.filter { it.type == BType.WALL }.map { it.x to it.y }.toHashSet()
        val h = (b.x - 1 to b.y) in set || (b.x + 1 to b.y) in set
        val v = (b.x to b.y - 1) in set || (b.x to b.y + 1) in set
        return "${if (h) "h" else ""}${if (v) "v" else ""}"
    }

    private fun sync(g: GameState) {
        val ids = g.buildings.map { it.id }.toHashSet()
        buildings.keys.filter { it !in ids }.forEach { id -> buildings.remove(id)?.let { w.remove(it.parts) } }
        workers.keys.filter { id -> g.buildings.none { it.id == id && it.upgrade != null } }.forEach { id -> workers.remove(id)?.let { w.remove(it) } }

        for (b in g.buildings) {
            val s = b.type.size.toFloat()
            val cx = w.wx(b.x + s / 2f)
            val cz = w.wz(b.y + s / 2f)
            val sig = "${b.type}|${b.level}|${b.x}|${b.y}|${b.upgrade != null}|" + if (b.type == BType.WALL) wallSig(g, b) else ""
            if (buildings[b.id]?.sig == sig) continue
            buildings.remove(b.id)?.let { w.remove(it.parts) }
            val parts = when {
                b.type == BType.WALL -> {
                    val ws = wallSig(g, b)
                    val key = when (ws) {
                        "h" -> Models3D.wallModel(b.level)
                        "v" -> Models3D.wallModel(b.level)
                        else -> Models3D.WALL_PILLAR
                    }
                    w.build(listOf(Part(key, width = 1f, rotY = if (ws == "h") 90f else 0f)), cx, cz, 1f, sizeMul = 0.95f)
                }
                b.level == 0 -> w.build(listOf(Part(Models3D.SCAFFOLD, width = s * 0.85f)), cx, cz, s)
                else -> {
                    val main = w.build(Models3D.recipe(b.type, b.level, s), cx, cz, s, sizeMul = 0.92f + 0.03f * b.level.coerceAtMost(5))
                    if (b.upgrade != null) main + w.build(listOf(Part(Models3D.SCAFFOLD_SMALL, width = 0.7f, dx = s * 0.32f, dz = s * 0.32f)), cx, cz, s) else main
                }
            }
            buildings[b.id] = Vis(sig, parts)
            if (b.upgrade != null && workers[b.id] == null) {
                w.character(Models3D.WORKER, 0.8f)?.let { node ->
                    w.setPos(node, Models3D.WORKER, cx - s * 0.5f - 0.2f, cz + s * 0.5f)
                    w.face(node, 1f, -1f)
                    node.playAnimation("interact-right", loop = true)
                    workers[b.id] = node
                }
            }
        }
        if (walkers.isEmpty()) {
            Models3D.VILLAGERS.forEachIndexed { i, key ->
                val node = w.character(key, 0.75f) ?: return@forEachIndexed
                val x = -6f + i * 4f
                val z = 7f - i * 2f
                w.setPos(node, key, x, z)
                walkers += Walker(node, key, x, z, x, z, wait = i * 1.5f)
            }
        }
    }

    // ------------------------------------------------------------------ canlandırma

    private fun animateParts(dt: Float) {
        for (vis in buildings.values) for (p in vis.parts) {
            when (p.part.tag) {
                "blades" -> p.node.rotation = Rotation(time * 60f, p.baseRotY, 0f)
                "crystal" -> {
                    p.node.position = Position(p.basePos.x, p.basePos.y + 0.12f + 0.08f * sin(time * 2f), p.basePos.z)
                    p.node.rotation = Rotation(0f, time * 40f, 0f)
                }
                "weapon" -> p.node.rotation = Rotation(0f, p.baseRotY + 25f * sin(time * 0.6f + p.basePos.x), 0f)
                "fire" -> {
                    val k = 1f + 0.08f * sin(time * 17f + p.basePos.z)
                    p.node.scale = io.github.sceneview.math.Scale(p.scale * k, p.scale * (2f - k), p.scale * k)
                }
            }
        }
        for (node in workers.values) {
            // çekiç vuruşu: hafif zıplama
            val pos = node.position
            node.position = Position(pos.x, abs(sin(time * 6f)) * 0.04f, pos.z)
        }
    }

    private fun animateWalkers(d: AppData, dt: Float) {
        val occupied = d.game.buildings
        for (wk in walkers) {
            if (wk.wait > 0f) {
                wk.wait -= dt
                if (wk.walking) {
                    wk.walking = false
                    wk.node.stopAnimation("walk")
                    wk.node.playAnimation("idle", loop = true)
                }
                continue
            }
            val dx = wk.tx - wk.x
            val dz = wk.tz - wk.z
            val dist = hypot(dx, dz)
            if (dist < 0.1f) {
                // yeni hedef: binaların üstüne değil, boş bir yere
                repeat(10) {
                    val tx = rnd.nextFloat() * 18f - 9f
                    val tz = rnd.nextFloat() * 18f - 9f
                    val tile = (tx + w.n / 2f).toInt() to (tz + w.n / 2f).toInt()
                    if (occupied.none { tile.first >= it.x && tile.first < it.x + it.type.size && tile.second >= it.y && tile.second < it.y + it.type.size }) {
                        wk.tx = tx
                        wk.tz = tz
                        return@repeat
                    }
                }
                wk.wait = 2f + rnd.nextFloat() * 4f
                continue
            }
            if (!wk.walking) {
                wk.walking = true
                wk.node.stopAnimation("idle")
                wk.node.playAnimation("walk", loop = true)
            }
            val step = minOf(dist, 0.9f * dt)
            wk.x += dx / dist * step
            wk.z += dz / dist * step
            w.setPos(wk.node, wk.key, wk.x, wk.z)
            w.face(wk.node, dx, dz)
        }
        // konuşma balonları
        if (time > nextBubble) {
            val lines = Surprises.villagerLines(d, System.currentTimeMillis())
            if (lines.isNotEmpty() && walkers.isNotEmpty()) {
                bubbleText = lines[rnd.nextInt(lines.size)]
                bubbleOwner = rnd.nextInt(walkers.size)
                bubbleUntil = time + 4.5f
            }
            nextBubble = time + 8f + rnd.nextFloat() * 5f
        }
    }

    private fun animateBoss(d: AppData) {
        val b = d.game.boss
        if (b == null || b.defeated) {
            boss?.let { w.remove(it); boss = null }
            return
        }
        val node = boss ?: w.character(Models3D.BOSS, 3.4f)?.also {
            w.setPos(it, Models3D.BOSS, 0f, -w.n / 2f - 2.2f)
            w.face(it, 0f, 1f)
            boss = it
        } ?: return
        val hurt = b.dealtMs > b.shownMs
        val anim = if (hurt) "emote-no" else "idle"
        if (anim != bossAnim) {
            if (bossAnim.isNotEmpty()) node.stopAnimation(bossAnim)
            node.playAnimation(anim, loop = true)
            bossAnim = anim
        }
    }

    // ------------------------------------------------------------------ 2D katman

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 30f; typeface = Typeface.DEFAULT_BOLD }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 34f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    private fun DrawScope.footprint(x: Int, y: Int, s: Int, color: Color, stroke: Boolean) {
        val pts = listOf(x to y, x + s to y, x + s to y + s, x to y + s).mapNotNull { (a, b) -> w.screen(w.wx(a.toFloat()), 0.02f, w.wz(b.toFloat())) }
        if (pts.size < 4) return
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        if (stroke) drawPath(path, color, style = Stroke(5f)) else drawPath(path, color)
    }

    fun overlay(ds: DrawScope, d: AppData, now: Long, selectedId: String?, ghost: Ghost?) = with(ds) {
        val g = d.game
        // seçim
        g.buildings.find { it.id == selectedId }?.let { b ->
            if (ghost == null) footprint(b.x, b.y, b.type.size, Color.White.copy(alpha = 0.6f + 0.4f * sin(time * 5f)), stroke = true)
        }
        // yerleştirme gölgesi
        if (ghost != null) {
            footprint(ghost.x, ghost.y, ghost.type.size, (if (ghost.ok) Color(0xFF3DDC84) else Color(0xFFFF4D4D)).copy(alpha = 0.45f), stroke = false)
            footprint(ghost.x, ghost.y, ghost.type.size, if (ghost.ok) Color(0xFF3DDC84) else Color(0xFFFF4D4D), stroke = true)
        }
        // inşaat çubukları, maden altınları, seviye rozetleri
        for (b in g.buildings) {
            val vis = buildings[b.id] ?: continue
            val s = b.type.size.toFloat()
            val top = vis.parts.maxOfOrNull { it.topY } ?: 1f
            val p = w.screen(w.wx(b.x + s / 2f), top + 0.3f, w.wz(b.y + s / 2f)) ?: continue
            val ppt = w.pxPerTile(w.wx(b.x + s / 2f), top, w.wz(b.y + s / 2f))
            b.upgrade?.let { u ->
                healthBar(p.x, p.y, s * ppt * 0.8f, (u.progressMs.toFloat() / u.requiredMs).coerceIn(0f, 1f), good = Color(0xFFFFD166))
            }
            if (b.type == BType.GOLD_MINE && Game.mineStored(b, now) >= 1) {
                val c = p + Offset(0f, -abs(sin(time * 3f)) * ppt * 0.3f - ppt * 0.2f)
                drawCircle(Color(0x55000000), radius = ppt * 0.3f, center = c + Offset(0f, 4f))
                drawCircle(Color(0xFFFFC83D), radius = ppt * 0.28f, center = c)
                drawCircle(Color(0xFFFFE89A), radius = ppt * 0.16f, center = c)
            }
            if (b.type.cat != BCat.DECOR && b.type != BType.WALL && b.level >= 1 && ppt > 18f) {
                val c = p + Offset(-s * ppt * 0.45f, ppt * 0.2f)
                drawCircle(Color(0xDD1B2230), radius = 18f, center = c)
                drawCircle(Color(0xFFFFD166), radius = 18f, center = c, style = Stroke(3f))
                whitePaint.textSize = 22f
                drawContext.canvas.nativeCanvas.drawText("${b.level}", c.x, c.y + 8f, whitePaint)
            }
        }
        // canavarın adı, canı
        g.boss?.takeIf { !it.defeated }?.let { b ->
            val p = w.screen(0f, 3.8f, -w.n / 2f - 2.2f)
            if (p != null) {
                val k = Surprises.bossKind(b)
                healthBar(p.x, p.y, 160f, 1f - b.dealtMs.toFloat() / b.maxMs, good = Color(0xFFE5484D))
                whitePaint.textSize = 30f
                drawContext.canvas.nativeCanvas.drawText("${k.emoji} ${k.name}", p.x, p.y - 12f, whitePaint)
                if (b.dealtMs > b.shownMs) {
                    val mins = (b.dealtMs - b.shownMs) / 60_000L
                    whitePaint.textSize = 44f
                    whitePaint.color = android.graphics.Color.rgb(255, 107, 74)
                    drawContext.canvas.nativeCanvas.drawText("-$mins dk!", p.x + 90f, p.y - 40f - (time * 30f % 40f), whitePaint)
                    whitePaint.color = android.graphics.Color.WHITE
                }
            }
        }
        // konuşma balonu
        val text = bubbleText
        if (text != null && time < bubbleUntil && bubbleOwner < walkers.size) {
            val wk = walkers[bubbleOwner]
            w.screen(wk.x, 1.05f, wk.z)?.let { p -> bubble(this, p, text) }
        }
    }

    private fun bubble(ds: DrawScope, at: Offset, text: String) = with(ds) {
        val maxW = 420f
        textPaint.textSize = 28f
        val words = text.split(" ")
        val lines = ArrayList<String>()
        var cur = ""
        for (wd in words) {
            val t = if (cur.isEmpty()) wd else "$cur $wd"
            if (textPaint.measureText(t) > maxW && cur.isNotEmpty()) {
                lines += cur
                cur = wd
            } else cur = t
        }
        if (cur.isNotEmpty()) lines += cur
        val bw = (lines.maxOf { textPaint.measureText(it) } + 28f)
        val bh = lines.size * 34f + 18f
        val left = (at.x - bw / 2f).coerceIn(4f, size.width - bw - 4f)
        val top = at.y - bh - 18f
        drawRoundRect(Color(0xF2FFFFFF), Offset(left, top), Size(bw, bh), CornerRadius(18f))
        val tail = Path().apply { moveTo(at.x - 10f, top + bh); lineTo(at.x + 10f, top + bh); lineTo(at.x, top + bh + 16f); close() }
        drawPath(tail, Color(0xF2FFFFFF))
        lines.forEachIndexed { i, l -> drawContext.canvas.nativeCanvas.drawText(l, left + 14f, top + 38f + i * 34f, textPaint) }
    }
}
