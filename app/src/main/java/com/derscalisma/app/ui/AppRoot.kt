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
import com.derscalisma.app.data.Focus
import com.derscalisma.app.data.Repo
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
    LaunchedEffect(now) {
        if (timer != null && !timer.isPaused && now >= Focus.nextEventAt(timer, data.settings)) Repo.tick()
    }

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
        Box(Modifier.fillMaxSize().background(cs.background)) {
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
                        NavRail(screen, data.coins) { selected = it }
                        ContentArea(
                            Modifier.weight(1f).fillMaxHeight()
                                .padding(start = 8.dp, top = 12.dp, end = 14.dp, bottom = 12.dp)
                        ) { ScreenContent(screen, data, now, onStart, onAttack) }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        TopBar(screen, data.coins)
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
        }
    }

    val session = data.lastSession
    if (data.focusAsk != null) {
        FocusDialog()
    } else if (session != null && data.goals.isNotEmpty()) {
        SessionLogDialog(data, session)
    }
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
private fun FocusDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("🎯 Dikkatin toplu mu?") },
        text = { Text("Şu an gerçekten derse mi odaklısın, yoksa aklın başka yerde mi? Dürüst cevap ver, dikkat yüzden buna göre hesaplanır.") },
        confirmButton = { Button(onClick = { Repo.answerFocus(true) }) { Text("✅ Evet, odaklıyım") } },
        dismissButton = { OutlinedButton(onClick = { Repo.answerFocus(false) }) { Text("💭 Dağıldım") } },
    )
}

@Composable
private fun NavRail(current: Screen, coins: Int, onSelect: (Screen) -> Unit) {
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
        Text("🪙 $coins", fontWeight = FontWeight.Bold, color = cs.tertiary, fontSize = 14.sp, maxLines = 1)
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
private fun TopBar(current: Screen, coins: Int) {
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
        Text("🪙 $coins", fontWeight = FontWeight.Bold, color = cs.tertiary)
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
