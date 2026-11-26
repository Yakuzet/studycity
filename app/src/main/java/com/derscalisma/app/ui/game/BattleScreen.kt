package com.derscalisma.app.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derscalisma.app.data.AppData
import com.derscalisma.app.data.game.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Haritayı ekrana sığdıran dönüşüm. */
internal class MapFit(val left: Float, val top: Float, val tile: Float) {
    fun sx(x: Float) = left + x * tile
    fun sy(y: Float) = top + y * tile
    fun toMap(p: Offset) = Offset((p.x - left) / tile, (p.y - top) / tile)
}

internal fun fitFor(w: Float, h: Float): MapFit {
    val tile = minOf(w, h) / VILLAGE_SIZE
    return MapFit((w - tile * VILLAGE_SIZE) / 2f, (h - tile * VILLAGE_SIZE) / 2f, tile)
}

@Composable
fun BattleScreen(level: Int, data: AppData, onExit: () -> Unit) {
    val sprites = rememberSprites()
    val engine = remember(level) {
        BattleEngine(Campaign.village(level), data.game.army, data.game.troopLevels, seed = (System.nanoTime() % 100000).toInt())
    }
    val ground = remember(level) { sprites.ground(seed = level * 31 + 7) }
    var frame by remember { mutableLongStateOf(0L) }
    var selected by remember { mutableStateOf(engine.remaining.keys.minByOrNull { it.ordinal }) }
    var reward by remember { mutableStateOf<BattleReward?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var mapSize by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(engine) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                engine.step((now - last) / 1_000_000_000f)
                last = now
                frame++
            }
            if (engine.finished && reward == null) {
                reward = Game.finishBattle(engine.result())
                delay(900)
                showResult = true
            }
            if (showResult && engine.effects.isEmpty() && engine.projectiles.isEmpty()) break
        }
    }

    BackHandler(enabled = !engine.finished) { confirmExit = true }

    fun deployAt(p: Offset) {
        val t = selected ?: return
        if (mapSize == Size.Zero) return
        val fit = fitFor(mapSize.width, mapSize.height)
        val m = fit.toMap(p)
        engine.deploy(t, m.x, m.y)
        if ((engine.remaining[t] ?: 0) <= 0) {
            selected = engine.remaining.filterValues { it > 0 }.keys.minByOrNull { it.ordinal }
        }
        frame++
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF2E4A26))) {
        Column(Modifier.fillMaxSize()) {
            BattleHud(engine, frame) { confirmExit = true }
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { mapSize = Size(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(engine) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            deployAt(down.position)
                            var last = down.uptimeMillis
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull() ?: break
                                if (!ch.pressed) break
                                if (ch.uptimeMillis - last > 130) {
                                    deployAt(ch.position)
                                    last = ch.uptimeMillis
                                }
                                ch.consume()
                            }
                        }
                    }
            ) {
                frame // her karede yeniden çiz
                drawBattle(engine, sprites, ground, fitFor(size.width, size.height))
            }
            TroopBar(engine, frame, selected) { selected = it }
        }

        if (showResult) {
            ResultOverlay(engine, reward, onExit)
        }
    }

    if (confirmExit && !engine.finished) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Savaşı bitir?") },
            text = { Text("Şu anki yıkım oranıyla savaş biter. İndirdiğin askerler geri gelmez, indirmediklerin ordunda kalır.") },
            confirmButton = { TextButton(onClick = { engine.surrender(); confirmExit = false }) { Text("Bitir") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Devam et") } },
        )
    }
}

@Composable
private fun BattleHud(engine: BattleEngine, frame: Long, onSurrender: () -> Unit) {
    frame
    Row(
        Modifier.fillMaxWidth().background(Color(0xCC101418)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("⚔️ ${engine.village.name}", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("Seviye ${engine.village.level} • 💰 ${engine.village.loot}", color = Color(0xFFB9C4D0), fontSize = 12.sp)
        }
        val left = engine.timeLeft.roundToInt()
        Text("⏱ ${left / 60}:${(left % 60).toString().padStart(2, '0')}", color = Color.White, fontWeight = FontWeight.Bold)
        Text("%${(engine.destruction * 100).roundToInt()}", color = Color(0xFFFFD166), fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text("⭐".repeat(engine.stars) + "☆".repeat(3 - engine.stars), color = Color(0xFFFFD166), fontSize = 18.sp)
        if (!engine.finished) {
            OutlinedButton(onClick = onSurrender, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("🏳️ Bitir", color = Color.White)
            }
        }
    }
}

@Composable
private fun TroopBar(engine: BattleEngine, frame: Long, selected: TType?, onSelect: (TType) -> Unit) {
    frame
    val sprites = rememberSprites()
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xCC101418))
            .horizontalScroll(rememberScrollState())
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val all = (engine.remaining.keys + engine.deployed.keys).toSortedSet(compareBy { it.ordinal })
        if (all.isEmpty()) Text("Ordun boş! Önce Ordu sekmesinden asker eğit.", color = Color.White)
        all.forEach { t ->
            val n = engine.remaining[t] ?: 0
            val sel = t == selected && n > 0
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (sel) Color(0xFF3A5BD9) else Color(0xFF26303A))
                    .border(2.dp, if (sel) Color(0xFFFFD166) else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable(enabled = n > 0) { onSelect(t) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    bitmap = sprites.troop(t),
                    contentDescription = t.title,
                    modifier = Modifier.size(34.dp),
                    filterQuality = FilterQuality.None,
                    alpha = if (n > 0) 1f else 0.35f,
                )
                Text("${t.title} ×$n", color = if (n > 0) Color.White else Color(0xFF8893A0), fontSize = 11.sp, maxLines = 1)
            }
        }
        if (!engine.finished && engine.troopsLeft > 0 && engine.deployed.isEmpty()) {
            Text("Kırmızı olmayan bir yere dokun ya da parmağını sürükle", color = Color(0xFFB9C4D0), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ResultOverlay(engine: BattleEngine, reward: BattleReward?, onExit: () -> Unit) {
    val stars = engine.stars
    Box(Modifier.fillMaxSize().background(Color(0xAA000000)), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF1B2230), modifier = Modifier.padding(24.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (stars > 0) "🏆 Zafer!" else "💀 Yenilgi",
                    color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0 until 3) {
                        val earned = i < stars
                        val anim = remember { Animatable(0f) }
                        LaunchedEffect(Unit) {
                            delay(250L * i)
                            anim.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f))
                        }
                        Text(
                            if (earned) "⭐" else "☆",
                            fontSize = 44.sp,
                            color = if (earned) Color(0xFFFFD166) else Color(0xFF5A6472),
                            modifier = Modifier.scale(if (earned) anim.value else 1f),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("%${(engine.destruction * 100).roundToInt()} yıkım", color = Color.White, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                if (reward != null) {
                    Text("💰 Ganimet: +${reward.loot} 🪙", color = Color(0xFFFFD166))
                    if (reward.starBonus > 0) Text("⭐ Yeni yıldız bonusu: +${reward.starBonus} 🪙", color = Color(0xFFFFD166))
                }
                val lost = engine.deployed.entries.joinToString("  ") { "${it.key.title} ×${it.value}" }
                if (lost.isNotEmpty()) Text("Kullanılan: $lost", color = Color(0xFFB9C4D0), fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onExit) { Text("🏰 Köye Dön") }
            }
        }
    }
}

// ---------------------------------------------------------------------- çizim

internal fun DrawScope.drawBattle(e: BattleEngine, sp: Sprites, ground: ImageBitmap, fit: MapFit) {
    val t = fit.tile
    val now = e.time
    drawImage(
        ground,
        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
        srcSize = androidx.compose.ui.unit.IntSize(ground.width, ground.height),
        dstOffset = androidx.compose.ui.unit.IntOffset(fit.left.roundToInt(), fit.top.roundToInt()),
        dstSize = androidx.compose.ui.unit.IntSize((t * VILLAGE_SIZE).roundToInt(), (t * VILLAGE_SIZE).roundToInt()),
        filterQuality = FilterQuality.Low,
    )

    // Asker indirilemeyen bölge: başta hafif, hatalı dokunuşta belirgin.
    val failAge = now - e.lastDeployFail
    val zoneAlpha = when {
        failAge < 0.8f -> 0.35f * (1f - failAge / 0.8f) + 0.08f
        e.deployed.isEmpty() && !e.finished -> 0.12f
        else -> 0f
    }
    if (zoneAlpha > 0f) {
        for (i in 0 until VILLAGE_SIZE) for (j in 0 until VILLAGE_SIZE) {
            if (e.forbidden[i][j]) drawRect(Color.Red.copy(alpha = zoneAlpha), Offset(fit.sx(i.toFloat()), fit.sy(j.toFloat())), Size(t, t))
        }
    }

    // Yıkıntılar
    e.buildings.filter { !it.alive }.forEach { b ->
        drawFit(sp.rubble, fit.sx(b.x + b.s * 0.1f), fit.sy(b.y + b.s * 0.2f), b.s * 0.8f * t, b.s * 0.7f * t, alpha = 0.9f)
        val age = now - b.destroyedAt
        if (age < 8f) {
            val flick = 0.85f + 0.15f * sin(now * 18f + b.id)
            val fw = b.s * 0.45f * t * flick
            drawFit(sp.fire, fit.sx(b.cx) - fw / 2f, fit.sy(b.cy) - fw * 1.2f, fw, fw * 1.4f, alpha = (1f - age / 8f).coerceIn(0f, 1f))
        }
    }

    // Binalar ve askerler y'ye göre sıralı çizilir.
    data class Item(val y: Float, val draw: () -> Unit)
    val items = ArrayList<Item>()
    e.buildings.filter { it.alive }.forEach { b ->
        items += Item(b.y + b.s) {
            val recoil = if (now - b.shotAt < 0.12f) 0.94f else 1f
            val inset = b.s * (1f - recoil) / 2f
            drawFit(
                sp.building(b.type),
                fit.sx(b.x + 0.05f + inset), fit.sy(b.y + 0.05f + inset),
                (b.s - 0.1f - inset * 2) * t, (b.s - 0.1f - inset * 2) * t,
                colorFilter = if (b.hitFlash > 0f) HitFlash else null,
            )
            if (b.hp < b.maxHp) healthBar(fit.sx(b.cx), fit.sy(b.y) - t * 0.25f, b.s * t * 0.7f, b.hp / b.maxHp)
        }
    }
    e.troops.forEach { tr ->
        items += Item(tr.y) {
            val bob = if (tr.moving) (sin(tr.age * 14f) * 0.06f) else 0f
            val lunge = if (tr.attackAnim > 0f) (if (tr.facingLeft) -1f else 1f) * 0.12f * sin(tr.attackAnim / 0.25f * PI.toFloat()) else 0f
            val cx = fit.sx(tr.x + lunge)
            val cy = fit.sy(tr.y)
            val w = t * 0.95f
            drawOval(Color(0x55000000), Offset(cx - w * 0.3f, cy - w * 0.08f), Size(w * 0.6f, w * 0.2f))
            if (tr.type == TType.HEALER && tr.attackAnim > 0f) {
                drawCircle(Color(0x5560FF80), radius = tr.type.splash * t * (1.1f - tr.attackAnim), center = Offset(cx, cy))
            }
            drawFit(
                sp.troop(tr.type), cx - w / 2f, cy - w * (1f + bob), w, w,
                colorFilter = if (tr.hitFlash > 0f) HurtFlash else null, flipX = tr.facingLeft, pixelated = true,
            )
            if (tr.hp < tr.maxHp) healthBar(cx, cy - w * 1.15f, w * 0.7f, tr.hp / tr.maxHp, good = Color(0xFF5BC0FF))
        }
    }
    items.sortBy { it.y }
    items.forEach { it.draw() }

    // Mermiler
    e.projectiles.forEach { p ->
        val x = fit.sx(p.x)
        val y = fit.sy(p.y)
        when (p.kind) {
            BattleEngine.PKind.ARROW -> {
                val ang = atan2(p.ty - p.y, p.tx - p.x)
                rotate(ang * 180f / PI.toFloat(), pivot = Offset(x, y)) {
                    drawLine(Color(0xFF6B4423), Offset(x - t * 0.35f, y), Offset(x + t * 0.1f, y), strokeWidth = t * 0.07f)
                    drawLine(Color(0xFFDDDDDD), Offset(x + t * 0.05f, y), Offset(x + t * 0.18f, y), strokeWidth = t * 0.09f)
                }
            }
            BattleEngine.PKind.BALL -> {
                val prog = 1f - (hypot(p.tx - p.x, p.ty - p.y) / p.totalDist).coerceIn(0f, 1f)
                val arc = sin(prog * PI.toFloat()) * t * 1.4f
                drawCircle(Color(0x44000000), radius = t * 0.14f, center = Offset(x, y))
                drawCircle(Color(0xFF2B2B2B), radius = t * 0.17f, center = Offset(x, y - arc))
                drawCircle(Color(0x66FFFFFF), radius = t * 0.06f, center = Offset(x - t * 0.05f, y - arc - t * 0.05f))
            }
            BattleEngine.PKind.ENEMY_MAGIC -> glow(x, y, t * 0.3f, Color(0xFFB266FF))
            BattleEngine.PKind.BOLT -> glow(x, y, t * 0.18f, Color(0xFF66E0FF))
            BattleEngine.PKind.MAGE_BOLT -> glow(x, y, t * 0.3f, Color(0xFFFF8A3D))
        }
    }

    // Efektler
    e.effects.forEach { f ->
        val k = ((now - f.born) / f.life).coerceIn(0f, 1f)
        val x = fit.sx(f.x + f.vx * (now - f.born))
        val y = fit.sy(f.y + f.vy * (now - f.born))
        when (f.kind) {
            BattleEngine.FxKind.EXPLOSION -> {
                drawCircle(Color(0xFFFFE08A).copy(alpha = (1f - k) * 0.9f), radius = f.size * t * (0.3f + k), center = Offset(x, y))
                drawCircle(Color(0xFFFF7A1A).copy(alpha = (1f - k) * 0.8f), radius = f.size * t * (0.2f + 0.7f * k), center = Offset(x, y))
            }
            BattleEngine.FxKind.SMOKE -> drawCircle(Color(0xFF777777).copy(alpha = (1f - k) * 0.55f), radius = f.size * t * (0.3f + 0.6f * k), center = Offset(x, y))
            BattleEngine.FxKind.HIT -> drawCircle(Color.White.copy(alpha = 1f - k), radius = f.size * t * (0.2f + 0.5f * k), center = Offset(x, y), style = Stroke(t * 0.06f))
            BattleEngine.FxKind.DEATH -> drawCircle(Color(0xFFCCCCCC).copy(alpha = (1f - k) * 0.7f), radius = f.size * t * (0.2f + 0.6f * k), center = Offset(x, y - k * t * 0.6f))
            BattleEngine.FxKind.HEAL -> {
                val yy = y - k * t * 0.8f
                val s = t * 0.14f
                val c = Color(0xFF6CFF8A).copy(alpha = 1f - k)
                drawLine(c, Offset(x - s, yy), Offset(x + s, yy), strokeWidth = t * 0.07f)
                drawLine(c, Offset(x, yy - s), Offset(x, yy + s), strokeWidth = t * 0.07f)
            }
            BattleEngine.FxKind.DEPLOY -> drawCircle(Color.White.copy(alpha = (1f - k) * 0.8f), radius = f.size * t * k, center = Offset(x, y), style = Stroke(t * 0.05f))
            BattleEngine.FxKind.SPLASH -> drawCircle(Color(0xFFFFB066).copy(alpha = (1f - k) * 0.6f), radius = f.size * t * (0.3f + 0.7f * k), center = Offset(x, y), style = Stroke(t * 0.08f))
        }
    }
}

private fun DrawScope.glow(x: Float, y: Float, r: Float, c: Color) {
    drawCircle(
        Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), center = Offset(x, y), radius = r * 1.8f),
        radius = r * 1.8f, center = Offset(x, y),
    )
    drawCircle(Color.White.copy(alpha = 0.9f), radius = r * 0.35f, center = Offset(x, y))
}

/** Test / önizleme için: verilen motor durumunun tek bir karesini çizer. */
@Composable
internal fun BattleFrame(engine: BattleEngine, modifier: Modifier = Modifier) {
    val sprites = rememberSprites()
    val ground = remember { sprites.ground(seed = engine.village.level * 31 + 7) }
    Canvas(modifier) { drawBattle(engine, sprites, ground, fitFor(size.width, size.height)) }
}
