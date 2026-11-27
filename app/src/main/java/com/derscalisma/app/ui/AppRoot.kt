package com.derscalisma.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derscalisma.app.data.AppData
import com.derscalisma.app.data.Catalog
import com.derscalisma.app.data.Phase
import com.derscalisma.app.data.Repo
import com.derscalisma.app.data.nextEventAt
import com.derscalisma.app.ui.rewards.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.derscalisma.app.ui.game.BattleScreen
import com.derscalisma.app.ui.game.CityScreen
import com.derscalisma.app.ui.screens.*
import kotlinx.coroutines.delay

enum class Screen(val emoji: String, val title: String, val short: String) {
    HOME("🏠", "Ana Sayfa", "Ana"),
    TIMER("⏱️", "Çalış", "Çalış"),
    GOALS("🎯", "Hedefler", "Hedef"),
    WINDOWS("🗓️", "Aralıklar", "Aralık"),
    CITY("🏰", "Köy", "Köy"),
    MARKET("🛒", "Market", "Market"),
    SETTINGS("⚙️", "Ayarlar", "Ayar"),
}

private val visibleScreens = Screen.entries.toList()

@Composable
fun rememberNow(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(1000)
    }
}

@Composable
fun AppRoot(data: AppData, initialScreen: Screen = Screen.HOME) {
    var selected by rememberSaveable { mutableStateOf(initialScreen) }
    val screen = if (selected in visibleScreens) selected else Screen.HOME
    val now by rememberNow()
    val timer = data.timer
    val cs = MaterialTheme.colorScheme

    // Uygulama açıkken faz sonu, bip ve ara soruları anında işle.
    // Sıradaki olay (faz sonu, otomatik geçiş, soru süresi) geldiyse hemen işle.
    LaunchedEffect(now) {
        val at = nextEventAt(data)
        if (at != null && now >= at) Repo.tick()
    }
    // Oturum dışında kazanılan paralar alttan bakiyeye uçar.
    DisposableEffect(Unit) {
        Repo.onEarned = { CoinFx.burst(it) }
        onDispose { Repo.onEarned = null }
    }
    // Çalışma müziği: çalışırken (ve istenirse molada) seçili parça.
    val st = data.settings
    val track = com.derscalisma.app.audio.StudyTrack.of(st.studyMusic)
    val playStudy = track != null && timer != null && !timer.isPaused &&
        (timer.phase == Phase.WORK && !timer.waiting || st.studyMusicInBreak)
    LaunchedEffect(playStudy, track) {
        com.derscalisma.app.audio.Sound.studyMusic(if (playStudy) track else null)
    }
    var wallet by remember { mutableStateOf(false) }

    val view = LocalView.current
    val keepOn = data.settings.keepScreenOn && timer != null && !timer.isPaused
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        Repo.messages.collect { snackbar.showSnackbar(it) }
    }

    val bg = Catalog.background(data.background)
    var battleLevel by rememberSaveable { mutableStateOf<Int?>(null) }
    val onStart = {
        if (data.timer == null) Repo.startTimer()
        selected = Screen.TIMER
    }
    val onAttack: (Int) -> Unit = { battleLevel = it }

    CompositionLocalProvider(
        LocalHasBackground provides (bg != null),
        LocalContentColor provides cs.onBackground,
    ) {
        Box(Modifier.fillMaxSize().background(cs.background).onGloballyPositioned {
            val b = it.boundsInRoot()
            CoinFx.bottom = androidx.compose.ui.geometry.Offset(b.center.x, b.bottom - 120f)
        }) {
            if (bg != null) {
                Image(
                    painter = painterResource(bg.image),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(cs.background.copy(alpha = 0.30f), cs.background.copy(alpha = 0.62f)))
                    )
                )
            }

            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val useRail = maxWidth >= maxHeight || maxWidth >= 840.dp
                val level = battleLevel
                if (level != null) {
                    BattleScreen(level, data) { battleLevel = null }
                } else if (useRail) {
                    Row(Modifier.fillMaxSize()) {
                        NavRail(screen, data, { wallet = true }) { selected = it }
                        ContentArea(
                            Modifier.weight(1f).fillMaxHeight()
                                .padding(start = 8.dp, top = 12.dp, end = 14.dp, bottom = 12.dp)
                        ) { ScreenContent(screen, data, now, onStart, onAttack) }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        TopBar(screen, data) { wallet = true }
                        ContentArea(
                            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                        ) { ScreenContent(screen, data, now, onStart, onAttack) }
                        BottomBar(screen) { selected = it }
                    }
                }
            }
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 60.dp),
            )
            val session = data.lastSession
            val cel = data.celebration
            when {
                session != null && battleLevel == null -> SessionSummaryOverlay(data, session)
                cel != null -> StreakCelebration(data, cel) { Repo.dismissCelebration() }
            }
            val ask = data.focusAsk
            if (ask != null && ask.visible && now >= ask.askedAt) {
                FocusAskBanner(ask, now, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 64.dp))
            }
            CoinFlightCanvas(CoinFx.flight)
        }
    }
    data.away?.let { AwayDialog(data, it) }
    if (wallet) WalletSheet(data) { wallet = false }
}

@Composable
private fun ContentArea(modifier: Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        CompositionLocalProvider(LocalLayout provides LayoutInfo(maxWidth, maxHeight)) {
            content()
        }
    }
}

@Composable
private fun ScreenContent(screen: Screen, data: AppData, now: Long, onStart: () -> Unit, onAttack: (Int) -> Unit) {
    when (screen) {
        Screen.HOME -> HomeScreen(data, now, onStart)
        Screen.TIMER -> TimerScreen(data, now)
        Screen.GOALS -> GoalsScreen(data, now)
        Screen.WINDOWS -> WindowsScreen(data, now)
        Screen.CITY -> CityScreen(data, now, onAttack = onAttack, onStudy = onStart)
        Screen.MARKET -> MarketScreen(data)
        Screen.SETTINGS -> SettingsScreen(data)
    }
}


@Composable
private fun NavRail(current: Screen, data: AppData, onWallet: () -> Unit, onSelect: (Screen) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxHeight()
            .width(84.dp)
            .background(panelColor())
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BalancePill(data, onWallet)
        Spacer(Modifier.height(6.dp))
        visibleScreens.forEach { s ->
            val sel = s == current
            Column(
                Modifier
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (sel) cs.primary.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelect(s) }
                    .width(70.dp)
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(s.emoji, fontSize = 18.sp)
                Text(
                    s.title,
                    fontSize = 10.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color = if (sel) cs.primary else cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TopBar(current: Screen, data: AppData, onWallet: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(panelColor()).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${current.emoji}  ${current.title}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BalancePill(data, onWallet)
    }
}

@Composable
private fun BottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(panelColor()).padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        visibleScreens.forEach { s ->
            val sel = s == current
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (sel) cs.primary.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelect(s) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(s.emoji, fontSize = 18.sp)
                Text(
                    s.short,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color = if (sel) cs.primary else cs.onSurfaceVariant,
                )
            }
        }
    }
}
