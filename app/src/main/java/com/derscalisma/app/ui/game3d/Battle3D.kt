package com.derscalisma.app.ui.game3d

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.derscalisma.app.data.game.*
import com.derscalisma.app.ui.game.healthBar
import com.derscalisma.app.ui.game.rememberSprites
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberView
import java.util.IdentityHashMap
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin

/** 3D savaş alanı. [onAct] karo koordinatında asker indirme / büyü atma. */
@Composable
fun Battle3D(
    battle: BattleEngine,
    frame: Long,
    onAct: (x: Float, y: Float, first: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            it.ground(sprites.ground(battle.village.level * 31 + 7).asAndroidBitmap(), battle.village.level)
            it.yaw = 25f
            it.pitch = 58f
            it.dist = 28f
            it.updateCamera()
            it.daylight(13f)
        }
    }
    val scene = remember { BattleScene(world, battle) }
    // Yükleyiciler kapanmadan önce düğümleri ayır (bu efekt sonra tanımlandığı için önce çalışır).
    DisposableEffect(world) { onDispose { world.detachAll() } }
    val act by rememberUpdatedState(onAct)

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
            onFrame = { scene.frame() },
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        world.tileAt(down.position.x, down.position.y)?.let { (x, y) -> act(x, y, true) }
                        var multi = false
                        var last = down.uptimeMillis
                        while (true) {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                multi = true
                                world.dist = (world.dist / ev.calculateZoom()).coerceIn(9f, 34f)
                                world.yaw -= ev.calculateRotation()
                                val pan = ev.calculatePan()
                                world.pan(pan.x, pan.y, size.height.toFloat())
                                world.updateCamera()
                                ev.changes.forEach { it.consume() }
                            } else if (!multi) {
                                val ch = pressed[0]
                                if (ch.uptimeMillis - last > 130) {
                                    world.tileAt(ch.position.x, ch.position.y)?.let { (x, y) -> act(x, y, false) }
                                    last = ch.uptimeMillis
                                }
                                ch.consume()
                            }
                        }
                    }
                }
        ) {
            frame
            scene.overlay(this)
        }
    }
}

class BattleScene(private val w: World3D, private val e: BattleEngine) {
    private class BVis(val b: BattleEngine.Bld, var parts: List<Placed>, var destroyed: Boolean = false)
    private class TVis(val node: ModelNode, val key: String, val height: Float, var anim: String = "", var dyingAt: Float = -1f, var x: Float = 0f, var z: Float = 0f)

    private val buildings = ArrayList<BVis>()
    private val walls = HashMap<Int, List<Placed>>()
    private val troops = HashMap<Int, TVis>()
    private val projectiles = IdentityHashMap<BattleEngine.Proj, Node>()
    private var time = 0f
    private var lastNanos = 0L
    private val matCyan = w.materialLoader.createColorInstance(Color(0xFF66E0FF))
    private val matOrange = w.materialLoader.createColorInstance(Color(0xFFFF8A3D))
    private val matPurple = w.materialLoader.createColorInstance(Color(0xFFB266FF))

    init {
        for (b in e.buildings) {
            val cx = w.wx(b.cx)
            val cz = w.wz(b.cy)
            buildings += BVis(b, w.build(Models3D.recipe(b.type, b.level, b.s), cx, cz, b.s, sizeMul = 0.92f + 0.03f * b.level.coerceAtMost(5)))
        }
        for (i in 0 until w.n) for (j in 0 until w.n) {
            if (e.wallMax[i][j] <= 0f) continue
            val h = (i > 0 && e.wallMax[i - 1][j] > 0f) || (i < w.n - 1 && e.wallMax[i + 1][j] > 0f)
            val v = (j > 0 && e.wallMax[i][j - 1] > 0f) || (j < w.n - 1 && e.wallMax[i][j + 1] > 0f)
            val key = if (h != v) Models3D.wallModel(e.wallLevel[i][j]) else Models3D.WALL_PILLAR
            walls[j * w.n + i] = w.build(listOf(Part(key, width = 1f, rotY = if (h && !v) 90f else 0f)), w.wx(i + 0.5f), w.wz(j + 0.5f), 1f, sizeMul = 0.95f)
        }
    }

    fun frame() {
        val nanos = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.016f else ((nanos - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = nanos
        time += dt
        syncBuildings()
        syncWalls()
        syncTroops()
        syncProjectiles()
    }

    private fun syncBuildings() {
        for (v in buildings) {
            if (!v.destroyed && !v.b.alive) {
                v.destroyed = true
                w.remove(v.parts)
                v.parts = w.build(
                    listOf(Part(Models3D.RUBBLE, width = v.b.s * 0.8f), Part(Models3D.RUBBLE_ROCKS, width = v.b.s * 0.5f, dx = 0.2f)),
                    w.wx(v.b.cx), w.wz(v.b.cy), v.b.s,
                )
                continue
            }
            if (v.destroyed) continue
            val def = v.b.defense ?: continue
            // silah en yakın askere döner, ateş edince geri teper
            val target = e.troops.filter { it.alive && hypot(it.x - v.b.cx, it.y - v.b.cy) <= def.range }
                .minByOrNull { hypot(it.x - v.b.cx, it.y - v.b.cy) }
            for (p in v.parts) {
                when (p.part.tag) {
                    "weapon" -> {
                        if (target != null) {
                            val yaw = Math.toDegrees(atan2((target.x - v.b.cx).toDouble(), (target.y - v.b.cy).toDouble())).toFloat()
                            p.node.rotation = Rotation(0f, yaw, 0f)
                        }
                        val recoil = if (e.time - v.b.shotAt < 0.12f) 0.88f else 1f
                        p.node.scale = Scale(p.scale * recoil)
                    }
                    "crystal" -> {
                        p.node.position = Position(p.basePos.x, p.basePos.y + 0.12f + 0.08f * sin(time * 2f), p.basePos.z)
                        p.node.rotation = Rotation(0f, time * 60f, 0f)
                    }
                }
            }
        }
    }

    private fun syncWalls() {
        val gone = walls.keys.filter { idx -> e.wallHp[idx % w.n][idx / w.n] <= 0f }
        for (idx in gone) {
            walls.remove(idx)?.let { w.remove(it) }
            w.build(listOf(Part(Models3D.RUBBLE_ROCKS, width = 0.55f)), w.wx(idx % w.n + 0.5f), w.wz(idx / w.n + 0.5f), 1f)
        }
    }

    private fun animFor(t: BattleEngine.Troop): String = when {
        t.moving -> if (t.type == TType.BREAKER) "sprint" else "walk"
        t.attackAnim > 0f -> when {
            t.type == TType.HEALER -> "interact-right"
            t.type.range >= 1.5f -> "holding-right-shoot"
            else -> "attack-melee-right"
        }
        t.type.range >= 1.5f && t.target != null -> "holding-right"
        t.target != null && t.type != TType.HEALER -> "attack-melee-right"
        else -> "idle"
    }

    private fun setAnim(v: TVis, anim: String, loop: Boolean = true) {
        if (v.anim == anim) return
        if (v.anim.isNotEmpty()) runCatching { v.node.stopAnimation(v.anim) }
        runCatching { v.node.playAnimation(anim, loop = loop) }
        v.anim = anim
    }

    private fun syncTroops() {
        val alive = HashSet<Int>()
        for (t in e.troops) {
            alive += t.id
            val v = troops[t.id] ?: run {
                val (key, h) = Models3D.character(t.type)
                val node = w.character(key, h) ?: return@run null
                TVis(node, key, h).also { troops[t.id] = it }
            } ?: continue
            val nx = w.wx(t.x)
            val nz = w.wz(t.y)
            val dx = nx - v.x
            val dz = nz - v.z
            v.x = nx
            v.z = nz
            w.setPos(v.node, v.key, nx, nz)
            val tgt = t.target
            if (t.moving) w.face(v.node, dx, dz)
            else if (tgt != null) w.face(v.node, w.wx(tgt.cx) - nx, w.wz(tgt.cy) - nz)
            setAnim(v, animFor(t))
        }
        val dead = troops.filter { it.key !in alive }
        for ((id, v) in dead) {
            if (v.dyingAt < 0f) {
                v.dyingAt = time
                setAnim(v, "die", loop = false)
            } else if (time - v.dyingAt > 1.3f) {
                w.remove(v.node)
                troops.remove(id)
            }
        }
    }

    private fun syncProjectiles() {
        val live = e.projectiles.toHashSet()
        projectiles.keys.filter { it !in live }.forEach { p -> projectiles.remove(p)?.let { w.remove(it) } }
        for (p in e.projectiles) {
            val node = projectiles[p] ?: when (p.kind) {
                BattleEngine.PKind.ARROW -> w.model("td/weapon-ammo-arrow")?.also { it.scale = Scale(0.7f); w.root.addChildNode(it) }
                BattleEngine.PKind.BALL -> w.model("td/weapon-ammo-cannonball")?.also { it.scale = Scale(1.1f); w.root.addChildNode(it) }
                BattleEngine.PKind.BOLT -> SphereNode(w.engine, radius = 0.12f, materialInstance = matCyan).also { w.root.addChildNode(it) }
                BattleEngine.PKind.MAGE_BOLT -> SphereNode(w.engine, radius = 0.2f, materialInstance = matOrange).also { w.root.addChildNode(it) }
                BattleEngine.PKind.ENEMY_MAGIC -> SphereNode(w.engine, radius = 0.2f, materialInstance = matPurple).also { w.root.addChildNode(it) }
            }?.also { projectiles[p] = it } ?: continue
            val prog = 1f - (hypot(p.tx - p.x, p.ty - p.y) / p.totalDist).coerceIn(0f, 1f)
            val startH = if (p.fromEnemy) 1.4f else 0.6f
            val y = startH + (0.5f - startH) * prog + if (p.kind == BattleEngine.PKind.BALL) sin(prog * PI.toFloat()) * 1.6f else 0f
            node.position = Position(w.wx(p.x), y, w.wz(p.y))
            if (p.kind == BattleEngine.PKind.ARROW) {
                node.rotation = Rotation(0f, Math.toDegrees(atan2((p.tx - p.sx).toDouble(), (p.ty - p.sy).toDouble())).toFloat(), 0f)
            }
        }
    }

    // ------------------------------------------------------------------ 2D katman

    private fun DrawScope.tileQuad(i: Int, j: Int, c: Color) {
        val pts = listOf(i to j, i + 1 to j, i + 1 to j + 1, i to j + 1).mapNotNull { (a, b) -> w.screen(w.wx(a.toFloat()), 0.03f, w.wz(b.toFloat())) }
        if (pts.size < 4) return
        drawPath(Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }, c)
    }

    fun overlay(ds: DrawScope) = with(ds) {
        val now = e.time
        // indirme yasağı bölgesi
        val failAge = now - e.lastDeployFail
        val zoneAlpha = when {
            failAge < 0.8f -> 0.35f * (1f - failAge / 0.8f) + 0.08f
            e.deployed.isEmpty() && !e.finished -> 0.12f
            else -> 0f
        }
        if (zoneAlpha > 0f) {
            for (i in 0 until w.n) for (j in 0 until w.n) if (e.forbidden[i][j]) tileQuad(i, j, Color.Red.copy(alpha = zoneAlpha))
        }
        // büyü alanları
        for (sp in e.activeSpells) {
            val age = now - sp.start
            if (age > sp.duration + 0.5f) continue
            val c = when (sp.kind) {
                SType.HEAL -> Color(0xFF4CFF7A)
                SType.RAGE -> Color(0xFFC04CFF)
                SType.LIGHTNING -> Color(0xFF9AD8FF)
            }
            val center = w.screen(w.wx(sp.x), 0.05f, w.wz(sp.y)) ?: continue
            val ppt = w.pxPerTile(w.wx(sp.x), 0f, w.wz(sp.y))
            val rx = sp.radius * ppt
            val ry = rx * 0.62f
            val fade = if (age > sp.duration) (1f - (age - sp.duration) / 0.5f).coerceIn(0f, 1f) else 1f
            drawOval(c.copy(alpha = 0.2f * fade), Offset(center.x - rx, center.y - ry), Size(rx * 2, ry * 2))
            drawOval(c.copy(alpha = 0.75f * fade), Offset(center.x - rx, center.y - ry), Size(rx * 2, ry * 2), style = Stroke(4f))
        }
        // yanan yıkıntılar
        for (v in buildings) if (v.destroyed) {
            val age = now - v.b.destroyedAt
            if (age < 8f) {
                val p = w.screen(w.wx(v.b.cx), 0.6f, w.wz(v.b.cy)) ?: continue
                val ppt = w.pxPerTile(w.wx(v.b.cx), 0f, w.wz(v.b.cy))
                val flick = 0.85f + 0.15f * sin(time * 18f + v.b.id)
                val r = v.b.s * ppt * 0.35f * flick
                val a = (1f - age / 8f).coerceIn(0f, 1f)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE066).copy(alpha = a), Color(0xFFFF5A1A).copy(alpha = 0.7f * a), Color.Transparent), center = p, radius = r * 1.6f), radius = r * 1.6f, center = p)
            }
        }
        // bina canları
        for (v in buildings) {
            if (v.destroyed || v.b.hp >= v.b.maxHp) continue
            val top = v.parts.maxOfOrNull { it.topY } ?: 1f
            val p = w.screen(w.wx(v.b.cx), top + 0.25f, w.wz(v.b.cy)) ?: continue
            val ppt = w.pxPerTile(w.wx(v.b.cx), top, w.wz(v.b.cy))
            healthBar(p.x, p.y, v.b.s * ppt * 0.7f, v.b.hp / v.b.maxHp)
        }
        // askerler: kahraman halkası, can çubukları, bombalar
        for (t in e.troops) {
            val v = troops[t.id] ?: continue
            val feet = w.screen(v.x, 0.02f, v.z) ?: continue
            val ppt = w.pxPerTile(v.x, 0f, v.z)
            if (t.type == TType.HERO) {
                drawOval(Color(0xFFFFD166).copy(alpha = 0.6f + 0.25f * sin(time * 4f)), Offset(feet.x - ppt * 0.6f, feet.y - ppt * 0.35f), Size(ppt * 1.2f, ppt * 0.7f), style = Stroke(4f))
            }
            if (t.type == TType.HEALER && t.attackAnim > 0f) {
                drawCircle(Color(0x5560FF80), radius = t.type.splash * ppt * (1.1f - t.attackAnim), center = feet)
            }
            if (t.hp < t.maxHp) {
                val head = w.screen(v.x, v.height + 0.2f, v.z) ?: continue
                healthBar(head.x, head.y, ppt * 0.7f, t.hp / t.maxHp, good = if (t.type == TType.HERO) Color(0xFFFFD166) else Color(0xFF5BC0FF))
            }
            if (t.type == TType.BREAKER) {
                val head = w.screen(v.x, v.height + 0.15f, v.z) ?: continue
                drawCircle(Color(0xFF222222), radius = ppt * 0.16f, center = head)
                drawCircle(if (sin(time * 20f) > 0) Color(0xFFFFE066) else Color(0xFFFF7A1A), radius = ppt * 0.06f, center = head + Offset(0f, -ppt * 0.2f))
            }
        }
        // efektler
        for (f in e.effects) {
            if (now < f.born) continue
            val k = ((now - f.born) / f.life).coerceIn(0f, 1f)
            val fx = w.wx(f.x + f.vx * (now - f.born))
            val fz = w.wz(f.y + f.vy * (now - f.born))
            val lift = when (f.kind) { BattleEngine.FxKind.SMOKE -> 0.6f + k * 1.2f; BattleEngine.FxKind.HEAL -> 0.6f + k * 0.8f; else -> 0.5f }
            val p = w.screen(fx, lift, fz) ?: continue
            val t = w.pxPerTile(fx, lift, fz)
            when (f.kind) {
                BattleEngine.FxKind.EXPLOSION -> {
                    drawCircle(Color(0xFFFFE08A).copy(alpha = (1f - k) * 0.9f), radius = f.size * t * (0.3f + k), center = p)
                    drawCircle(Color(0xFFFF7A1A).copy(alpha = (1f - k) * 0.8f), radius = f.size * t * (0.2f + 0.7f * k), center = p)
                }
                BattleEngine.FxKind.SMOKE -> drawCircle(Color(0xFF777777).copy(alpha = (1f - k) * 0.55f), radius = f.size * t * (0.3f + 0.6f * k), center = p)
                BattleEngine.FxKind.HIT -> drawCircle(Color.White.copy(alpha = 1f - k), radius = f.size * t * (0.2f + 0.5f * k), center = p, style = Stroke(t * 0.06f))
                BattleEngine.FxKind.DEATH -> drawCircle(Color(0xFFCCCCCC).copy(alpha = (1f - k) * 0.7f), radius = f.size * t * (0.2f + 0.6f * k), center = p)
                BattleEngine.FxKind.HEAL -> {
                    val s = t * 0.14f
                    val c = Color(0xFF6CFF8A).copy(alpha = 1f - k)
                    drawLine(c, Offset(p.x - s, p.y), Offset(p.x + s, p.y), strokeWidth = t * 0.07f)
                    drawLine(c, Offset(p.x, p.y - s), Offset(p.x, p.y + s), strokeWidth = t * 0.07f)
                }
                BattleEngine.FxKind.DEPLOY -> drawCircle(Color.White.copy(alpha = (1f - k) * 0.8f), radius = f.size * t * k, center = p, style = Stroke(t * 0.05f))
                BattleEngine.FxKind.SPLASH -> drawCircle(Color(0xFFFFB066).copy(alpha = (1f - k) * 0.6f), radius = f.size * t * (0.3f + 0.7f * k), center = p, style = Stroke(t * 0.08f))
                BattleEngine.FxKind.RUBBLE -> drawCircle(Color(0xFF8A8A8A).copy(alpha = 1f - k), radius = t * 0.3f * (0.5f + k), center = p, style = Stroke(t * 0.08f))
                BattleEngine.FxKind.LIGHTNING -> {
                    var px = p.x
                    var py = 0f
                    for (i in 1..8) {
                        val ny = p.y * i / 8
                        val nx = if (i == 8) p.x else p.x + sin(f.born * 91f + i * 2.3f) * t * 0.6f
                        drawLine(Color(0xFFE8F6FF).copy(alpha = 1f - k), Offset(px, py), Offset(nx, ny), strokeWidth = t * 0.12f)
                        drawLine(Color(0xFF7FC8FF).copy(alpha = (1f - k) * 0.6f), Offset(px, py), Offset(nx, ny), strokeWidth = t * 0.3f)
                        px = nx
                        py = ny
                    }
                    drawCircle(Color.White.copy(alpha = (1f - k) * 0.8f), radius = t * (0.4f + k), center = p)
                }
            }
        }
    }
}
