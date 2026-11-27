@file:OptIn(ExperimentalLayoutApi::class)

package com.derscalisma.app.ui.rewards

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.derscalisma.app.audio.Cue
import com.derscalisma.app.audio.Sound
import com.derscalisma.app.data.*
import com.derscalisma.app.i18n.Fmt
import com.derscalisma.app.i18n.S
import com.derscalisma.app.ui.icons.AppIcons
import com.derscalisma.app.ui.kit.*
import com.derscalisma.app.ui.theme.AppTheme
import com.derscalisma.app.ui.theme.Radius
import com.derscalisma.app.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// =====================================================================================================
// Uçan paralar
// =====================================================================================================

/** Ekranda uçan tek bir para. */
class FlyingCoin(val from: Offset, val to: Offset, val startNs: Long, val durMs: Int, val value: Int, val bend: Float) {
    var landed = false
}

/** Paraları bir yerden bir hedefe uçurur; her para hedefe varınca [onLand] çağrılır ve ses çıkar. */
class CoinFlight {
    val coins: SnapshotStateList<FlyingCoin> = mutableStateListOf()
    var clockNs by mutableLongStateOf(0L)

    fun launch(amount: Int, from: Offset, to: Offset, nowNs: Long, maxCoins: Int = 14, spreadMs: Int = 55, onLand: (Int) -> Unit = {}) {
        if (amount <= 0 || from == Offset.Unspecified || to == Offset.Unspecified) {
            onLand(amount)
            return
        }
        val n = min(maxCoins, max(1, amount))
        var left = amount
        for (i in 0 until n) {
            val v = if (i == n - 1) left else amount / n
            left -= v
            val jitter = Offset(Random.nextFloat() * 60f - 30f, Random.nextFloat() * 30f - 15f)
            coins += FlyingCoin(from + jitter, to, nowNs + i * spreadMs * 1_000_000L, 620 + Random.nextInt(160), v, Random.nextFloat() * 0.6f - 0.3f)
        }
        landHandler = onLand
    }

    var landHandler: (Int) -> Unit = {}

    fun step(nowNs: Long) {
        clockNs = nowNs
        val it = coins.iterator()
        var removed = false
        while (it.hasNext()) {
            val c = it.next()
            if (!c.landed && nowNs >= c.startNs + c.durMs * 1_000_000L) {
                c.landed = true
                landHandler(c.value)
                Sound.cue(Cue.COIN, volume = 0.55f, rate = 0.9f + Random.nextFloat() * 0.35f)
            }
            if (c.landed) {
                it.remove()
                removed = true
            }
        }
        if (removed) clockNs = nowNs
    }
}

/** Uçan paraları çizer. Kök koordinatlarda tam ekran katman. */
@Composable
fun CoinFlightCanvas(flight: CoinFlight, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    LaunchedEffect(flight) {
        while (true) {
            withFrameNanos { flight.step(it) }
        }
    }
    val t = flight.clockNs
    if (flight.coins.isEmpty()) return
    Canvas(modifier.fillMaxSize()) {
        for (coin in flight.coins) {
            val p = ((t - coin.startNs) / 1_000_000f / coin.durMs).coerceIn(0f, 1f)
            if (t < coin.startNs) continue
            val e = FastOutSlowInEasing.transform(p)
            val mid = Offset((coin.from.x + coin.to.x) / 2 + (coin.to.y - coin.from.y) * coin.bend, min(coin.from.y, coin.to.y) - 80f)
            val a = coin.from * ((1 - e) * (1 - e)) + mid * (2 * (1 - e) * e) + coin.to * (e * e)
            val r = 9.dp.toPx() * (1.15f - 0.35f * e)
            drawCircle(c.coinRim, r, a)
            drawCircle(c.coin, r * 0.8f, a)
            drawCircle(Color.White.copy(alpha = 0.5f), r * 0.25f, a + Offset(-r * 0.3f, -r * 0.3f))
        }
    }
}

/** Uygulama geneli para animasyonu: alttan bakiye rozetine. */
object CoinFx {
    val flight = CoinFlight()
    /** Rozetin kök koordinatlardaki merkezi. */
    var target by mutableStateOf(Offset.Unspecified)
    /** Varsayılan çıkış noktası (ekranın alt ortası). */
    var bottom by mutableStateOf(Offset.Unspecified)
    /** Henüz rozete varmamış miktar: rozet bunu bakiyeden düşerek gösterir. */
    var inFlight by mutableIntStateOf(0)
    var lastAmount by mutableIntStateOf(0)
    var lastAt by mutableLongStateOf(0L)

    fun burst(amount: Int, from: Offset = bottom) {
        if (amount <= 0) return
        inFlight += amount
        lastAmount = amount
        lastAt = System.nanoTime()
        flight.launch(amount, from, target, System.nanoTime()) { v -> inFlight = (inFlight - v).coerceAtLeast(0) }
    }
}

/** Üst çubuktaki bakiye: dokununca cüzdan açılır; yeni kazanç uçarken solunda "+N" görünür. */
@Composable
fun BalancePill(data: AppData, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shown = (data.visibleCoins() - CoinFx.inFlight).coerceAtLeast(0)
    val pulse by animateFloatAsState(if (CoinFx.inFlight > 0) 1.08f else 1f, spring(dampingRatio = 0.4f), label = "pill")
    Row(verticalAlignment = Alignment.CenterVertically) {
        val recent = System.nanoTime() - CoinFx.lastAt < 2_200_000_000L && CoinFx.lastAmount > 0
        AnimatedVisibility(recent || CoinFx.inFlight > 0, enter = fadeIn()) {
            Text(
                "+" + Fmt.number(CoinFx.lastAmount),
                style = MaterialTheme.typography.titleSmall,
                color = c.success,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        Row(
            Modifier
                .scale(pulse)
                .height(36.dp)
                .clip(CircleShape)
                .background(c.surfaceRaised)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp)
                .onGloballyPositioned { CoinFx.target = it.boundsInRoot().let { b -> Offset(b.left + 18.dp.value * 1.5f, b.center.y) } },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoinGlyph(18.dp)
            Spacer(Modifier.width(6.dp))
            Text(Fmt.number(shown), style = MaterialTheme.typography.titleSmall, color = c.text)
        }
    }
    // "+N" yazısı süre dolunca kaybolsun diye arada bir yeniden çiz.
    LaunchedEffect(CoinFx.lastAt) {
        delay(2300)
        CoinFx.lastAmount = CoinFx.lastAmount
    }
}

// =====================================================================================================
// Zaman çizelgesi
// =====================================================================================================

data class FocusSplit(val focusedMs: Long, val distractedMs: Long, val unknownMs: Long, val overtimeMs: Long)

fun focusSplit(segments: List<Segment>): FocusSplit {
    var f = 0L
    var d = 0L
    var u = 0L
    var o = 0L
    for (s in segments) if (s.kind == SegKind.WORK) {
        val ms = s.end - s.start
        when (s.focus) {
            1 -> f += ms
            0 -> d += ms
            else -> u += ms
        }
        if (s.overtime) o += ms
    }
    return FocusSplit(f, d, u, o)
}

/** Ardışık çalışma bölümlerini saat aralıklarına birleştirir: "14:00–14:25". */
fun workRanges(segments: List<Segment>): List<Pair<Long, Long>> {
    val out = mutableListOf<Pair<Long, Long>>()
    for (s in segments.filter { it.kind == SegKind.WORK }.sortedBy { it.start }) {
        val last = out.lastOrNull()
        if (last != null && s.start - last.second < 60_000) out[out.size - 1] = last.first to max(last.second, s.end)
        else out += s.start to s.end
    }
    return out
}

@Composable
private fun segColor(s: Segment): Color {
    val c = AppTheme.colors
    return when (s.kind) {
        SegKind.WORK -> when (s.focus) {
            1 -> c.success
            0 -> c.danger
            else -> c.accent.copy(alpha = 0.75f)
        }
        SegKind.BREAK -> c.textTertiary.copy(alpha = 0.35f)
        SegKind.PAUSE -> c.warning.copy(alpha = 0.45f)
        SegKind.AWAY -> c.danger.copy(alpha = 0.35f)
    }
}

/**
 * Tek oturum çubuğu: bölümler dikkat yoğunluğuna göre renklenir. Bütün oturum odaklıysa çubuk parlar.
 */
@Composable
fun SessionBar(segments: List<Segment>, perfect: Boolean, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 14.dp) {
    val colors = segments.map { segColor(it) }
    val c = AppTheme.colors
    val glow = if (perfect) {
        val tr = rememberInfiniteTransition(label = "glow")
        tr.animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "g").value
    } else 0f
    Canvas(modifier.fillMaxWidth().height(height + if (perfect) 10.dp else 0.dp)) {
        if (segments.isEmpty()) return@Canvas
        val start = segments.minOf { it.start }
        val end = segments.maxOf { it.end }
        val span = (end - start).coerceAtLeast(1).toFloat()
        val h = height.toPx()
        val top = (size.height - h) / 2
        if (perfect) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(c.success.copy(alpha = glow * 0.5f), c.coin.copy(alpha = glow * 0.6f), c.success.copy(alpha = glow * 0.5f))),
                Offset(-4f, top - 5.dp.toPx()), Size(size.width + 8f, h + 10.dp.toPx()), CornerRadius(h, h),
            )
        }
        drawRoundRect(c.hairline, Offset(0f, top), Size(size.width, h), CornerRadius(h / 2, h / 2))
        segments.forEachIndexed { i, s ->
            val x0 = (s.start - start) / span * size.width
            val x1 = (s.end - start) / span * size.width
            drawRoundRect(colors[i], Offset(x0, top), Size((x1 - x0).coerceAtLeast(2f), h), CornerRadius(h / 3, h / 3))
        }
    }
}

/** Günün zaman çizelgesi: oturumlar saat ekseninde, odaklı oturumlar parlayarak. */
@Composable
fun DayTimeline(logs: List<SessionLog>, running: TimerState?, now: Long, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val live = running?.let { t ->
        val open = if (t.isRunning && t.segStart > 0) listOf(
            Segment(t.segStart, now, if (t.phase == Phase.WORK) SegKind.WORK else SegKind.BREAK)
        ) else if (t.isPaused && t.segStart > 0) listOf(Segment(t.segStart, now, SegKind.PAUSE)) else emptyList()
        t.segments + open
    }.orEmpty()
    val all = logs.map { it to it.segments } + listOfNotNull(if (live.isNotEmpty()) null to live else null)
    if (all.isEmpty()) {
        Text(S.rewards.timelineEmpty, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        return
    }
    val first = all.minOf { (_, s) -> s.minOfOrNull { it.start } ?: now }
    val last = all.maxOf { (_, s) -> s.maxOfOrNull { it.end } ?: now }
    val h0 = (Time.minuteOfDay(first) / 60).coerceAtMost(22)
    val h1 = ((Time.minuteOfDay(last) + 59) / 60).coerceIn(h0 + 2, 24)
    val day0 = Time.startOfDay(Time.date(first))
    val from = day0 + h0 * 60 * MINUTE_MS
    val to = day0 + h1 * 60 * MINUTE_MS
    val segColors = all.map { (_, s) -> s.map { segColor(it) } }
    val tr = rememberInfiniteTransition(label = "tl")
    val glow by tr.animateFloat(0.3f, 0.85f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "tlg")
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            val span = (to - from).toFloat()
            val h = 16.dp.toPx()
            val top = (size.height - h) / 2
            drawRoundRect(c.hairline, Offset(0f, top), Size(size.width, h), CornerRadius(h / 2, h / 2))
            all.forEachIndexed { i, (log, segs) ->
                if (segs.isEmpty()) return@forEachIndexed
                val sx = (segs.minOf { it.start } - from) / span * size.width
                val ex = (segs.maxOf { it.end } - from) / span * size.width
                if (log?.perfect == true) {
                    drawRoundRect(c.coin.copy(alpha = glow * 0.55f), Offset(sx - 4f, top - 6.dp.toPx()), Size(ex - sx + 8f, h + 12.dp.toPx()), CornerRadius(h, h))
                }
                segs.forEachIndexed { j, s ->
                    val x0 = (s.start - from) / span * size.width
                    val x1 = (s.end - from) / span * size.width
                    drawRect(segColors[i][j], Offset(x0, top), Size((x1 - x0).coerceAtLeast(2f), h))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            val step = if (h1 - h0 > 8) 3 else if (h1 - h0 > 4) 2 else 1
            for (hh in h0..h1 step step) {
                Text(Fmt.hm(hh * 60), style = MaterialTheme.typography.labelSmall, color = c.textTertiary, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun TimelineLegend() {
    val c = AppTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.m), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            c.success to S.rewards.legendFocused, c.danger to S.rewards.legendDistracted,
            c.accent.copy(alpha = 0.75f) to S.rewards.legendUnknown, c.textTertiary.copy(alpha = 0.35f) to S.rewards.legendBreak,
            c.warning.copy(alpha = 0.45f) to S.rewards.legendPause, c.coin to S.rewards.perfectSession,
        ).forEach { (col, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(col))
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
        }
    }
}

/** Oturum listesi satırı: saat aralığı, süre, dikkat yüzdesi ve çubuk. */
@Composable
fun SessionRow(log: SessionLog) {
    val c = AppTheme.colors
    val p = Focus.percent(log.focusYes, log.focusNo)
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.rangeMs(log.start, log.end), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (log.manual) Tag(S.rewards.manualTag)
            if (log.perfect) Tag(S.rewards.perfectSession, color = c.text, container = c.coin.copy(alpha = 0.35f), icon = AppIcons.AutoAwesome)
            Spacer(Modifier.width(Space.s))
            Text(Fmt.duration(log.workMs) + (p?.let { " · " + S.common.percent(it) } ?: ""), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Spacer(Modifier.height(4.dp))
        SessionBar(log.segments, log.perfect, height = 8.dp)
    }
}

// =====================================================================================================
// Seri kutlaması
// =====================================================================================================

/** Titreşen alev (üç katmanlı damla + kıvılcımlar). */
@Composable
fun Flame(modifier: Modifier = Modifier, intensity: Float = 1f) {
    val c = AppTheme.colors
    val tr = rememberInfiniteTransition(label = "flame")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "ft")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun drop(scale: Float, color: Brush, phase: Float) {
            val sway = sin((t * 2 * PI + phase).toFloat()) * w * 0.035f * intensity
            val flick = 1f + sin((t * 4 * PI + phase * 2).toFloat()) * 0.04f * intensity
            val cx = w / 2
            val bottom = h * 0.96f
            val top = bottom - h * 0.92f * scale * flick
            val half = w * 0.36f * scale
            val p = Path().apply {
                moveTo(cx + sway * 2, top)
                cubicTo(cx + half * 0.55f + sway, top + (bottom - top) * 0.35f, cx + half * 1.05f, bottom - (bottom - top) * 0.35f, cx + half * 0.7f, bottom - (bottom - top) * 0.12f)
                quadraticTo(cx, bottom + (bottom - top) * 0.06f, cx - half * 0.7f, bottom - (bottom - top) * 0.12f)
                cubicTo(cx - half * 1.05f, bottom - (bottom - top) * 0.35f, cx - half * 0.55f + sway, top + (bottom - top) * 0.35f, cx + sway * 2, top)
                close()
            }
            drawPath(p, color)
        }
        drop(1f, Brush.verticalGradient(listOf(c.flame.copy(alpha = 0.9f), Color(0xFFE8452C)), 0f, h), 0f)
        drop(0.72f, Brush.verticalGradient(listOf(c.flameCore, c.flame), h * 0.3f, h), 1.3f)
        drop(0.42f, Brush.verticalGradient(listOf(Color.White, c.flameCore), h * 0.55f, h), 2.1f)
        // kıvılcımlar
        for (i in 0 until 7) {
            val ph = (t + i / 7f) % 1f
            val x = w / 2 + sin((i * 1.7f + ph * 3f).toDouble()).toFloat() * w * 0.28f
            val y = h * (0.75f - ph * 0.85f)
            drawCircle(c.flameCore.copy(alpha = (1 - ph) * 0.9f), w * 0.018f * (1 - ph * 0.5f), Offset(x, y))
        }
    }
}

/** Günlük hedef tamamlanınca: alev, gün sayısı ve haftalık şerit. */
@Composable
fun StreakCelebration(data: AppData, cel: Celebration, onDone: () -> Unit) {
    val c = AppTheme.colors
    val appear = remember { Animatable(0f) }
    val count = remember { Animatable((cel.streak - 1).coerceAtLeast(0).toFloat()) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(cel) {
        Sound.cue(Cue.STREAK)
        appear.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        delay(250)
        count.animateTo(cel.streak.toFloat(), tween(500))
        pop.animateTo(1.25f, tween(120))
        pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow))
    }
    val today = Time.date(System.currentTimeMillis())
    Box(
        Modifier.fillMaxSize().background(c.canvas.copy(alpha = 0.97f)).clickable(MutableInteractionSource(), null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).padding(Space.xl).scale(0.9f + 0.1f * appear.value),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Flame(Modifier.size(170.dp, 200.dp), intensity = 0.6f + 0.4f * appear.value)
                Text(
                    Fmt.number(count.value.toInt()),
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    modifier = Modifier.padding(top = 70.dp).scale(pop.value),
                )
            }
            Spacer(Modifier.height(Space.l))
            Text(S.rewards.streakDays(cel.streak), style = MaterialTheme.typography.headlineMedium, color = c.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.xs))
            Text(
                if (cel.newBest && cel.streak > 1) S.rewards.newBest else S.rewards.streakSubtitle,
                style = MaterialTheme.typography.bodyLarge, color = if (cel.newBest && cel.streak > 1) c.warning else c.textSecondary, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.xl))
            // Haftalık şerit: tamamlanan günler alevli, bugün tutuşuyor.
            val order = Fmt.weekOrder()
            val weekStart = today.minusDays(((today.dayOfWeek.value - order.first() + 7) % 7).toLong())
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                order.forEachIndexed { i, _ ->
                    val d = weekStart.plusDays(i.toLong())
                    val rec = data.days[d.toString()]
                    val met = rec?.dailyMet == true || rec?.frozen == true
                    val isToday = d == today
                    val ignite = if (isToday) appear.value else 1f
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Fmt.weekdayShort(d.dayOfWeek.value).take(3), style = MaterialTheme.typography.labelSmall, color = if (isToday) c.text else c.textTertiary)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(if (met) c.warningContainer else c.surfaceRaised),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (met) Icon(
                                if (rec?.frozen == true) AppIcons.AcUnit else AppIcons.LocalFireDepartment, null,
                                tint = if (rec?.frozen == true) c.accent else c.flame,
                                modifier = Modifier.size(22.dp).scale(if (isToday) ignite else 1f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.xxl))
            PrimaryButton(S.rewards.streakKeepGoing, onDone, Modifier.fillMaxWidth())
        }
    }
}

// =====================================================================================================
// Oturum özeti
// =====================================================================================================

/**
 * Oturum bitince: önce (hedef varsa) ilerleme girişi, sonra özet ve ödüllerin tek tek açılışı.
 * Her ödülün paraları alttaki toplama uçar; "Tamam"da toplam bakiyeye uçar.
 */
@Composable
fun SessionSummaryOverlay(data: AppData, ls: SessionSummary) {
    val c = AppTheme.colors
    Box(Modifier.fillMaxSize().background(c.canvas).clickable(MutableInteractionSource(), null) {}) {
        if (!ls.logged) ProgressLogStep(data, ls) else RewardStep(data, ls)
    }
}

@Composable
private fun ProgressLogStep(data: AppData, ls: SessionSummary) {
    val values = remember(ls.id) { mutableStateMapOf<String, Int>() }
    val c = AppTheme.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = Space.maxContent)) {
            Spacer(Modifier.height(Space.xl))
            IconBadge(AppIcons.TaskAlt, size = 56.dp)
            Spacer(Modifier.height(Space.l))
            Text(S.rewards.logTitle, style = MaterialTheme.typography.headlineMedium)
            Text(S.rewards.logHint, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Spacer(Modifier.height(Space.l))
            AppCard(Modifier.fillMaxWidth()) {
                data.goals.forEach { g ->
                    ListRow(Fmt.goalName(g), subtitle = Fmt.goalUnit(g, 2), icon = goalIcon(g.type)) {
                        PillStepper(values[g.id] ?: 0, { values[g.id] = it }, 0, 2000)
                    }
                }
                data.deadlines.filter { it.linkedGoalId == null && it.completedAt == null }.forEach { dl ->
                    ListRow(Fmt.deadlineName(dl), subtitle = Fmt.unit(dl.unit, 2), icon = AppIcons.Event) {
                        PillStepper(values["dl:${dl.id}"] ?: 0, { values["dl:${dl.id}"] = it }, 0, 2000)
                    }
                }
            }
            Spacer(Modifier.height(Space.xl))
            PrimaryButton(S.rewards.seeRewards, { Repo.logSession(values.toMap()) }, Modifier.fillMaxWidth())
            GhostButton(S.common.skip, { Repo.skipSessionLog() }, Modifier.fillMaxWidth())
        }
    }
}

fun goalIcon(t: GoalType) = when (t) {
    GoalType.PAGES -> AppIcons.MenuBook
    GoalType.QUESTIONS -> AppIcons.Quiz
    GoalType.TOPICS -> AppIcons.Topic
    GoalType.CUSTOM -> AppIcons.Flag
}

@Composable
private fun RewardStep(data: AppData, ls: SessionSummary) {
    val c = AppTheme.colors
    val log = data.sessionLogs.find { it.id == ls.id }
    val segs = log?.segments.orEmpty()
    val split = focusSplit(segs)
    val rewards = remember(data.ledger, ls.id) { data.ledger.filter { it.session == ls.id && it.amount != 0 }.reversed() }
    val total = rewards.sumOf { it.amount }
    var shown by remember(ls.id) { mutableIntStateOf(0) }
    var counted by remember(ls.id) { mutableIntStateOf(0) }
    val local = remember { CoinFlight() }
    val rowPos = remember { mutableStateMapOf<Int, Offset>() }
    var totalPos by remember { mutableStateOf(Offset.Unspecified) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(ls.id, rewards.size) {
        delay(400)
        Sound.cue(Cue.SESSION_DONE)
        for (i in rewards.indices) {
            if (i < shown) continue
            delay(450)
            shown = i + 1
            Sound.cue(Cue.POP)
            delay(120)
            val amt = rewards[i].amount
            if (amt > 0) {
                local.launch(amt, rowPos[i] ?: Offset.Unspecified, totalPos, System.nanoTime(), maxCoins = 8, spreadMs = 45) { v -> counted += v }
            } else counted += amt
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = Space.maxContent).fillMaxWidth()) {
                Spacer(Modifier.height(Space.xl))
                Text(if (ls.completed) S.rewards.summaryDone else S.rewards.summaryStopped, style = MaterialTheme.typography.headlineMedium)
                Text(Fmt.rangeMs(ls.startedAt.takeIf { it > 0 } ?: (ls.endedAt - ls.workMs), ls.endedAt), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                Spacer(Modifier.height(Space.l))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    StatTile(S.rewards.totalTime, Fmt.duration(ls.workMs), Modifier.weight(1f), icon = AppIcons.Timer)
                    StatTile(S.rewards.focusedTime, Fmt.duration(split.focusedMs), Modifier.weight(1f), icon = AppIcons.CenterFocusStrong, tint = c.success, muted = split.focusedMs == 0L)
                }
                Spacer(Modifier.height(Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    StatTile(S.rewards.distractedTime, Fmt.duration(split.distractedMs), Modifier.weight(1f), icon = AppIcons.Psychology, tint = c.danger, muted = split.distractedMs == 0L)
                    StatTile(S.rewards.extraTime, Fmt.duration(ls.overtimeMs), Modifier.weight(1f), icon = AppIcons.MoreTime, tint = c.warning, muted = ls.overtimeMs == 0L)
                }
                if (segs.isNotEmpty()) {
                    Spacer(Modifier.height(Space.l))
                    AppCard(Modifier.fillMaxWidth()) {
                        CardHeader(S.rewards.studiedRanges, subtitle = if (log?.perfect == true) S.rewards.perfectSession else null)
                        Spacer(Modifier.height(Space.s))
                        SessionBar(segs, log?.perfect == true)
                        Spacer(Modifier.height(Space.s))
                        Text(workRanges(segs).joinToString("   ") { (a, b) -> Fmt.rangeMs(a, b) }, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
                val prog = data.goals.mapNotNull { g ->
                    val v = data.days[Time.dayKey(ls.endedAt)]?.progress?.get(g.id) ?: 0
                    if (v > 0) "${Fmt.goalName(g)}: ${Fmt.amount(v, g.unit)}" else null
                }
                if (prog.isNotEmpty()) {
                    Spacer(Modifier.height(Space.s))
                    Text(S.rewards.progressDone + " " + prog.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Spacer(Modifier.height(Space.l))
                AppCard(Modifier.fillMaxWidth()) {
                    CardHeader(S.rewards.rewardsTitle)
                    if (rewards.isEmpty()) Text(S.rewards.noRewards, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    rewards.forEachIndexed { i, e ->
                        AnimatedVisibility(i < shown, enter = fadeIn(tween(250)) + slideInVertically { it / 2 }) {
                            ListRow(
                                S.rewards.earning(e.kind, e.n, e.label),
                                modifier = Modifier.onGloballyPositioned { rowPos[i] = it.boundsInRoot().let { b -> Offset(b.right - 40f, b.center.y) } },
                            ) { CoinAmount(e.amount, signed = true, color = if (e.amount < 0) c.danger else c.text) }
                        }
                    }
                }
                Spacer(Modifier.height(140.dp))
            }
        }
        // Alt: toplam ve Tamam
        Surface(color = c.surface, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Column(Modifier.widthIn(max = Space.maxContent).fillMaxWidth().padding(Space.l).navigationBarsPadding().align(Alignment.TopCenter)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.rewards.total, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Box(Modifier.onGloballyPositioned { totalPos = it.boundsInRoot().center }) {
                        CoinAmount(if (closing) 0 else counted, style = MaterialTheme.typography.headlineSmall, glyph = 24.dp)
                    }
                }
                Spacer(Modifier.height(Space.m))
                PrimaryButton(S.common.ok, {
                    if (closing) return@PrimaryButton
                    closing = true
                    scope.launch {
                        // Toplamdaki paralar bakiyeye uçar; açılış bitmeden basıldıysa kalanlar da eklenir.
                        val amount = total.coerceAtLeast(0)
                        CoinFx.burst(amount, totalPos)
                        Repo.closeSummary()
                    }
                }, Modifier.fillMaxWidth())
            }
        }
        CoinFlightCanvas(local)
    }
}

// =====================================================================================================
// Cüzdan
// =====================================================================================================

@Composable
fun WalletSheet(data: AppData, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    val r = S.rewards
    val today = Time.date(System.currentTimeMillis())
    val streak = Streaks.daily(data, today)
    AppSheet(onDismiss, title = r.walletTitle) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoinAmount(data.visibleCoins(), style = MaterialTheme.typography.displaySmall, glyph = 32.dp)
            }
            Text(r.todayEarned + ": " + Fmt.number(data.days[today.toString()]?.coinsEarned ?: 0), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            if (data.heldCoins() > 0) Text(r.heldNote, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            SectionHeader(r.howToEarn)
            val rows = listOf(
                Triple(AppIcons.Timer, r.ruleMinute(Rewards.COINS_PER_MINUTE), null),
                Triple(AppIcons.MoreTime, r.ruleOvertime(Rewards.OVERTIME_COINS_PER_MINUTE), null),
                Triple(AppIcons.LocalFireDepartment, r.ruleDaily(Rewards.daily(streak + 1)), r.ruleDailyHint),
                Triple(AppIcons.Flag, r.ruleGoal, null),
                Triple(AppIcons.Schedule, r.ruleWindow, null),
                Triple(AppIcons.TaskAlt, r.ruleSession(5), null),
                Triple(AppIcons.FreeBreakfast, r.ruleEarlyBreak(Rewards.EARLY_RETURN_PER_MIN), r.ruleFatigue),
                Triple(AppIcons.Psychology, r.ruleFocus, null),
                Triple(AppIcons.EmojiEvents, r.ruleMilestones, null),
            )
            rows.forEach { (ic, t, sub) -> ListRow(t, subtitle = sub, icon = ic, iconTint = c.accent) }
            SectionHeader(r.spending)
            ListRow(r.ruleExtension((0 until Rewards.MAX_BREAK_EXTENSIONS).joinToString(" / ") { Fmt.number(Rewards.breakExtensionPrice(it)) }), icon = AppIcons.LocalCafe)
            ListRow(r.ruleAway(Rewards.AWAY_FINE), icon = AppIcons.Warning)
            if (data.ledger.isNotEmpty()) {
                SectionHeader(r.recent)
                data.ledger.take(20).forEach { e ->
                    ListRow(r.earning(e.kind, e.n, e.label), subtitle = Fmt.date(Time.date(e.at)) + " " + Fmt.time(e.at)) {
                        CoinAmount(e.amount, signed = true, color = if (e.amount < 0) c.danger else c.text)
                    }
                }
            }
        }
    }
}

// =====================================================================================================
// Odak sorusu bandı ve "neredeydin?"
// =====================================================================================================

/** Ekranı kapatmayan soru bandı: geri sayım çubuğu ve iki düğme. */
@Composable
fun FocusAskBanner(ask: FocusAsk, now: Long, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val f = S.focus
    val total = (ask.expiresAt - ask.askedAt).coerceAtLeast(1)
    val left = (ask.expiresAt - now).coerceAtLeast(0)
    val warn = ask.attempt >= 2
    Surface(
        modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = Space.l),
        shape = RoundedCornerShape(Radius.l),
        color = if (warn) c.dangerContainer else c.surfaceHigh,
        contentColor = c.text,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(Space.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (warn) AppIcons.Warning else AppIcons.Psychology, null, tint = if (warn) c.danger else c.accent)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(if (warn) f.areYouThere else f.focusQuestion, style = MaterialTheme.typography.titleMedium)
                    if (ask.expiresAt > 0) Text(f.answerIn(((left + 999) / 1000).toInt()), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            if (ask.expiresAt > 0) {
                Spacer(Modifier.height(Space.s))
                LinearBar(left.toFloat() / total, color = if (warn) c.danger else c.accent, height = 4.dp)
            }
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                TonalButton(f.distractedBtn, { Repo.answerFocus(false) }, Modifier.weight(1f))
                PrimaryButton(f.focusedBtn, { Repo.answerFocus(true) }, Modifier.weight(1f))
            }
        }
    }
}

/** Sorulara cevap gelmediği için duraklatıldı: neden ayrıldın? */
@Composable
fun AwayDialog(data: AppData, away: AwayState) {
    val f = S.focus
    val t = data.timer
    val fee = Rewards.breakExtensionPrice(t?.breakExtensions ?: 0) + Rewards.AWAY_FINE
    AppDialog(
        title = f.awayTitle,
        icon = AppIcons.Emergency,
        text = f.awayText(Fmt.time(away.since), (away.lostMs / MINUTE_MS).toInt()),
        onDismiss = {},
        dismissOnOutside = false,
    ) {
        Text(f.awayPick, style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.textSecondary)
        Spacer(Modifier.height(Space.s))
        ReasonGrid { r ->
            Repo.resolveAway(r)
            Repo.resumeTimer()
        }
        Spacer(Modifier.height(Space.m))
        DangerButton(f.awayNoReason(fee), {
            Repo.resolveAway(null)
            Repo.resumeTimer()
        }, Modifier.fillMaxWidth())
    }
}

fun reasonIcon(r: PauseReason) = when (r) {
    PauseReason.VISITOR -> AppIcons.DoorFront
    PauseReason.COOKING -> AppIcons.SoupKitchen
    PauseReason.CHORES -> AppIcons.LocalLaundryService
    PauseReason.PHONE -> AppIcons.Call
    PauseReason.TOILET -> AppIcons.Wc
    PauseReason.FAMILY -> AppIcons.ChildCare
    PauseReason.HEALTH -> AppIcons.Medication
    PauseReason.OTHER -> AppIcons.MoreHoriz
}

/** Acil durum sebepleri ızgarası. */
@Composable
fun ReasonGrid(selected: PauseReason? = null, onPick: (PauseReason) -> Unit) {
    ChoiceChips(PauseReason.entries, selected, { S.common.pauseReason(it) }, onPick, icon = { reasonIcon(it) })
}

// =====================================================================================================
// Elle çalışma ekleme
// =====================================================================================================

@Composable
fun ManualStudyDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val f = S.focus
    val nowMin = Time.minuteOfDay(System.currentTimeMillis())
    var end by remember { mutableIntStateOf(nowMin) }
    var start by remember { mutableIntStateOf((nowMin - 25).coerceAtLeast(0)) }
    val dur = end - start
    fun pick(init: Int, set: (Int) -> Unit) = TimePickerDialog(ctx, { _, h, m -> set(h * 60 + m) }, init / 60, init % 60, true).show()
    AppDialog(
        title = f.manualTitle,
        icon = AppIcons.MoreTime,
        text = f.manualText,
        onDismiss = onDismiss,
        confirmText = f.manualAdd,
        confirmEnabled = dur in 1..720,
        dismissText = S.common.cancel,
        onConfirm = {
            val d = Time.date(System.currentTimeMillis())
            val err = Repo.addManualStudy(Time.at(d, start), Time.at(d, end))
            if (err != null) Repo.toast(err) else onDismiss()
        },
    ) {
        ListRow(f.manualStart, onClick = { pick(start) { start = it } }, icon = AppIcons.Schedule) { TrailingValue(Fmt.hm(start)) }
        RowDivider()
        ListRow(f.manualEnd, onClick = { pick(end) { end = it } }, icon = AppIcons.Schedule) { TrailingValue(Fmt.hm(end)) }
        Text(f.manualDuration(dur.coerceAtLeast(0)), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
    }
}
