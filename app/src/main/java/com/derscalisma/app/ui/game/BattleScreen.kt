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

/** Savaşta seçili olan: bir asker türü ya da bir büyü. */
private sealed interface Pick {
    data class Troop(val t: TType) : Pick
    data class Spell(val s: SType) : Pick
}

@Composable
fun BattleScreen(level: Int, data: AppData, onExit: () -> Unit) {
    val sprites = rememberSprites()
    val engine = remember(level) {
        val g = data.game
        val now = System.currentTimeMillis()
        BattleEngine(
            Campaign.village(level), g.army, g.troopLevels,
            seed = (System.nanoTime() % 100000).toInt(),
            heroLevel = if (Game.heroReady(g, now)) Game.heroLevel(g) else 0,
            spells = g.spells,
        )
    }
    val ground = remember(level) { sprites.ground(seed = level * 31 + 7) }
    var frame by remember { mutableLongStateOf(0L) }
    var pick by remember {
        mutableStateOf<Pick?>(engine.remaining.keys.filter { it != TType.HERO }.minByOrNull { it.ordinal }?.let { Pick.Troop(it) })
    }
    var reward by remember { mutableStateOf<BattleReward?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var mapSize by remember { mutableStateOf(Size.Zero) }

    DisposableEffect(Unit) {
        com.derscalisma.app.audio.Sound.music(com.derscalisma.app.audio.Music.BATTLE)
        onDispose { com.derscalisma.app.audio.Sound.music(null) }
    }

    LaunchedEffect(engine) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                engine.step((now - last) / 1_000_000_000f)
                last = now
                frame++
                playBattleSounds(engine)
            }
            if (engine.finished && reward == null) {
                reward = Game.finishBattle(engine.result())
                com.derscalisma.app.audio.Sound.music(null)
                com.derscalisma.app.audio.Sound.jingle(if (engine.stars > 0) com.derscalisma.app.audio.Jingle.VICTORY else com.derscalisma.app.audio.Jingle.DEFEAT)
                delay(900)
                showResult = true
            }
            if (showResult && engine.effects.isEmpty() && engine.projectiles.isEmpty()) break
        }
    }

    BackHandler(enabled = !engine.finished) { confirmExit = true }

    fun nextTroop(): Pick? = engine.remaining.filterValues { it > 0 }.keys
        .filter { it != TType.HERO }.minByOrNull { it.ordinal }?.let { Pick.Troop(it) }

    fun actAtTile(mx: Float, my: Float, first: Boolean) {
        val cur = pick ?: return
        val m = Offset(mx, my)
        when (cur) {
            is Pick.Spell -> if (first) {
                engine.castSpell(cur.s, m.x, m.y)
                if ((engine.spellsLeft[cur.s] ?: 0) <= 0) pick = nextTroop()
            }
            is Pick.Troop -> {
                engine.deploy(cur.t, m.x, m.y)
                if ((engine.remaining[cur.t] ?: 0) <= 0) pick = nextTroop()
            }
        }
        frame++
    }

    fun actAt(p: Offset, first: Boolean) {
        if (mapSize == Size.Zero) return
        val m = fitFor(mapSize.width, mapSize.height).toMap(p)
        actAtTile(m.x, m.y, first)
    }

    val use3d = data.settings.view3d && !androidx.compose.ui.platform.LocalInspectionMode.current

    Box(Modifier.fillMaxSize().background(Color(0xFF2E4A26))) {
        Column(Modifier.fillMaxSize()) {
            BattleHud(engine, frame) { confirmExit = true }
            if (use3d) com.derscalisma.app.ui.game3d.Battle3D(engine, frame, { x, y, first -> actAtTile(x, y, first) }, Modifier.weight(1f).fillMaxWidth())
            else Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { mapSize = Size(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(engine) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            actAt(down.position, first = true)
                            var last = down.uptimeMillis
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull() ?: break
                                if (!ch.pressed) break
                                if (ch.uptimeMillis - last > 130) {
                                    actAt(ch.position, first = false)
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
            TroopBar(engine, frame, pick) { pick = it }
        }

        if (showResult) {
            ResultOverlay(engine, reward, onExit)
        }
    }

    if (confirmExit && !engine.finished) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Savaşı bitir?") },
            text = { Text("Şu anki yıkım oranıyla savaş biter. İndirdiğin askerler ve kullandığın büyüler geri gelmez, kullanmadıkların kalır.") },
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
private fun BarCard(selected: Boolean, enabled: Boolean, gold: Boolean = false, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF3A5BD9) else if (gold) Color(0xFF4A3A12) else Color(0xFF26303A))
            .border(2.dp, if (selected) Color(0xFFFFD166) else if (gold) Color(0xFFB8902E) else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun TroopBar(engine: BattleEngine, frame: Long, pick: Pick?, onPick: (Pick) -> Unit) {
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
        if (all.isEmpty() && engine.spellsLeft.isEmpty()) Text("Ordun boş! Önce Ordu sekmesinden asker eğit.", color = Color.White)
        all.forEach { t ->
            val n = engine.remaining[t] ?: 0
            val isHero = t == TType.HERO
            val sel = pick == Pick.Troop(t) && n > 0
            BarCard(selected = sel, enabled = n > 0, gold = isHero, onClick = { onPick(Pick.Troop(t)) }) {
                Image(
                    bitmap = sprites.troop(t),
                    contentDescription = t.title,
                    modifier = Modifier.size(34.dp),
                    filterQuality = FilterQuality.None,
                    alpha = if (n > 0 || (isHero && engine.hero != null)) 1f else 0.35f,
                )
                Text(
                    if (isHero) "Komutan Sv ${engine.heroLevel}" else "${t.title} ×$n",
                    color = if (n > 0) Color.White else Color(0xFF8893A0), fontSize = 11.sp, maxLines = 1,
                )
            }
            if (isHero && engine.hero != null && !engine.heroAbilityUsed && !engine.finished) {
                Button(
                    onClick = { engine.useHeroAbility() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB8902E)),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) { Text("📯 Savaş Narası", fontSize = 12.sp) }
            }
        }
        engine.spellsLeft.keys.union(engine.spellsUsed.keys).sortedBy { it.ordinal }.forEach { sp ->
            val n = engine.spellsLeft[sp] ?: 0
            BarCard(selected = pick == Pick.Spell(sp) && n > 0, enabled = n > 0, onClick = { onPick(Pick.Spell(sp)) }) {
                Text(sp.emoji, fontSize = 24.sp, modifier = Modifier.height(34.dp))
                Text("${sp.title} ×$n", color = if (n > 0) Color.White else Color(0xFF8893A0), fontSize = 11.sp, maxLines = 1)
            }
        }
        if (!engine.finished && engine.troopsLeft > 0 && engine.deployed.isEmpty()) {
            Text("Kırmızı olmayan bir yere dokun ya da parmağını sürükle", color = Color(0xFFB9C4D0), fontSize = 12.sp)
        }
        if (pick is Pick.Spell) {
            Text("Büyüyü atmak için haritada bir yere dokun", color = Color(0xFFFFD166), fontSize = 12.sp)
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
                    if (reward.leagueBonus > 0) Text("🏅 Lig bonusu dahil: +${reward.leagueBonus} 🪙", color = Color(0xFFB9C4D0), fontSize = 12.sp)
                    if (reward.starBonus > 0) Text("⭐ Yeni yıldız bonusu: +${reward.starBonus} 🪙", color = Color(0xFFFFD166))
                }
                val lost = (engine.deployed.entries.filter { it.key != TType.HERO }.map { "${it.key.title} ×${it.value}" } +
                    engine.spellsUsed.entries.map { "${it.key.emoji} ×${it.value}" }).joinToString("  ")
                if (lost.isNotEmpty()) Text("Kullanılan: $lost", color = Color(0xFFB9C4D0), fontSize = 12.sp)
                if (engine.heroDeployed) {
                    val h = engine.hero
                    Text(
                        if (h == null) "👑 Komutan yaralandı, 20 dk dinlenecek" else "👑 Komutan %${(h.hp / h.maxHp * 100).roundToInt()} canla döndü",
                        color = Color(0xFFB9C4D0), fontSize = 12.sp,
                    )
                }
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

    // Surlar
    for (i in 0 until VILLAGE_SIZE) for (j in 0 until VILLAGE_SIZE) {
        val max = e.wallMax[i][j]
        if (max <= 0f) continue
        val x = fit.sx(i.toFloat())
        val y = fit.sy(j.toFloat())
        if (e.wallHp[i][j] > 0f) {
            drawWall(
                e.wallLevel[i][j], x, y, t,
                right = i + 1 < VILLAGE_SIZE && e.wallHp[i + 1][j] > 0f,
                down = j + 1 < VILLAGE_SIZE && e.wallHp[i][j + 1] > 0f,
                damaged = 1f - e.wallHp[i][j] / max,
            )
        } else {
            drawCircle(Color(0x887A7A7A), radius = t * 0.12f, center = Offset(x + t * 0.35f, y + t * 0.55f))
            drawCircle(Color(0x887A7A7A), radius = t * 0.09f, center = Offset(x + t * 0.62f, y + t * 0.4f))
        }
    }

    // Büyü alanları
    e.activeSpells.forEach { sp ->
        val age = now - sp.start
        if (age > sp.duration + 0.5f) return@forEach
        val fade = if (age > sp.duration) (1f - (age - sp.duration) / 0.5f).coerceIn(0f, 1f) else 1f
        val c = when (sp.kind) {
            SType.HEAL -> Color(0xFF4CFF7A)
            SType.RAGE -> Color(0xFFC04CFF)
            SType.LIGHTNING -> Color(0xFF9AD8FF)
        }
        val r = sp.radius * t * (if (age < 0.25f) age / 0.25f else 1f)
        val pulse = 0.75f + 0.25f * sin(now * 6f)
        drawCircle(c.copy(alpha = 0.18f * fade * pulse), radius = r, center = Offset(fit.sx(sp.x), fit.sy(sp.y)))
        drawCircle(c.copy(alpha = 0.7f * fade), radius = r, center = Offset(fit.sx(sp.x), fit.sy(sp.y)), style = Stroke(t * 0.07f))
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
            val w = t * (if (tr.type == TType.HERO) 1.35f else 0.95f)
            drawOval(Color(0x55000000), Offset(cx - w * 0.3f, cy - w * 0.08f), Size(w * 0.6f, w * 0.2f))
            if (tr.type == TType.HERO) {
                drawCircle(Color(0xFFFFD166).copy(alpha = 0.55f + 0.25f * sin(now * 4f)), radius = w * 0.45f, center = Offset(cx, cy), style = Stroke(t * 0.06f))
            }
            if (tr.type == TType.HEALER && tr.attackAnim > 0f) {
                drawCircle(Color(0x5560FF80), radius = tr.type.splash * t * (1.1f - tr.attackAnim), center = Offset(cx, cy))
            }
            drawFit(
                sp.troop(tr.type), cx - w / 2f, cy - w * (1f + bob), w, w,
                colorFilter = if (tr.hitFlash > 0f) HurtFlash else null, flipX = tr.facingLeft, pixelated = true,
            )
            if (tr.type == TType.BREAKER) {
                val bc = Offset(cx + (if (tr.facingLeft) -w * 0.25f else w * 0.25f), cy - w * 0.45f)
                drawCircle(Color(0xFF222222), radius = w * 0.17f, center = bc)
                drawCircle(if (sin(now * 20f) > 0) Color(0xFFFFE066) else Color(0xFFFF7A1A), radius = w * 0.06f, center = bc + Offset(0f, -w * 0.2f))
            }
            if (tr.hp < tr.maxHp) healthBar(cx, cy - w * 1.15f, w * 0.7f, tr.hp / tr.maxHp, good = if (tr.type == TType.HERO) Color(0xFFFFD166) else Color(0xFF5BC0FF))
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
        if (now < f.born) return@forEach
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
            BattleEngine.FxKind.LIGHTNING -> {
                val top = fit.sy(f.y) - t * 6f
                var px = x
                var py = top
                val segs = 7
                for (i in 1..segs) {
                    val ny = top + (y - top) * i / segs
                    val nx = if (i == segs) x else x + (sin(f.born * 91f + i * 2.3f) * t * 0.5f)
                    drawLine(Color(0xFFE8F6FF).copy(alpha = 1f - k), Offset(px, py), Offset(nx, ny), strokeWidth = t * 0.12f)
                    drawLine(Color(0xFF7FC8FF).copy(alpha = (1f - k) * 0.6f), Offset(px, py), Offset(nx, ny), strokeWidth = t * 0.3f)
                    px = nx
                    py = ny
                }
                drawCircle(Color(0xFFFFFFFF).copy(alpha = (1f - k) * 0.8f), radius = t * (0.4f + k), center = Offset(x, y))
            }
            BattleEngine.FxKind.RUBBLE -> {
                for (i in 0 until 4) {
                    val a = i * 1.57f + f.born
                    drawCircle(Color(0xFF8A8A8A).copy(alpha = 1f - k), radius = t * 0.1f, center = Offset(x + kotlin.math.cos(a) * t * 0.5f * k, y + kotlin.math.sin(a) * t * 0.5f * k))
                }
            }
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


internal fun playBattleSounds(e: BattleEngine) {
    if (e.sounds.isEmpty()) return
    for (snd in e.sounds) {
        val sfx = when (snd) {
            BattleEngine.Snd.DEPLOY -> com.derscalisma.app.audio.Sfx.DEPLOY
            BattleEngine.Snd.SWORD -> com.derscalisma.app.audio.Sfx.SWORD
            BattleEngine.Snd.ARROW -> com.derscalisma.app.audio.Sfx.ARROW
            BattleEngine.Snd.CANNON -> com.derscalisma.app.audio.Sfx.CANNON
            BattleEngine.Snd.MAGIC -> com.derscalisma.app.audio.Sfx.MAGIC
            BattleEngine.Snd.BOOM -> com.derscalisma.app.audio.Sfx.EXPLOSION
            BattleEngine.Snd.WALL -> com.derscalisma.app.audio.Sfx.HIT_METAL
            BattleEngine.Snd.SPELL -> com.derscalisma.app.audio.Sfx.SPELL
            BattleEngine.Snd.LIGHTNING -> com.derscalisma.app.audio.Sfx.LIGHTNING
            BattleEngine.Snd.HEAL -> com.derscalisma.app.audio.Sfx.HEAL
            BattleEngine.Snd.DIE -> com.derscalisma.app.audio.Sfx.PUNCH
        }
        com.derscalisma.app.audio.Sound.play(sfx, volume = 0.7f)
    }
    e.sounds.clear()
}
