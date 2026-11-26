package com.derscalisma.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derscalisma.app.data.*
import com.derscalisma.app.ui.*

private data class StreakItem(val emoji: String, val title: String, val value: Int, val unit: String)

@Composable
fun HomeScreen(data: AppData, now: Long, onStart: () -> Unit) {
    val today = Time.date(now)
    val rec = data.days[today.toString()] ?: DayRecord()
    val goalMs = data.settings.dailyGoalMinutes * MINUTE_MS
    val todayMs = liveTodayMs(data, now)
    val minuteKey = now / MINUTE_MS
    val layout = LocalLayout.current

    val dailyStreak = remember(data, minuteKey) { Streaks.daily(data, today) }
    val streakItems = remember(data, minuteKey) {
        listOf(
            StreakItem("📅", "Aralıkta Çalışma", Streaks.windowAttend(data, now), "aralık"),
            StreakItem("🎯", "Aralık Hedefi", Streaks.windowGoal(data, now), "aralık"),
        ) + data.goals.map { g -> StreakItem(g.type.emoji, "${g.name} Hedefi", Streaks.goal(data, g.id, today), "gün") }
    }
    val ringSize = if (layout.compact) 180.dp else (layout.height - 190.dp).coerceIn(110.dp, 200.dp)

    AdaptiveColumns(
        Pane(1f) {
            Panel(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Bugünkü Ana Hedef", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    ProgressRing(
                        progress = todayMs.toFloat() / goalMs,
                        modifier = Modifier.size(ringSize),
                        style = Catalog.timerStyle(data.timerStyle),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${todayMs / MINUTE_MS}", fontSize = (ringSize.value / 4.5f).sp, fontWeight = FontWeight.Black)
                            Text("/ ${data.settings.dailyGoalMinutes} dk", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    val left = ((goalMs - todayMs) / MINUTE_MS).coerceAtLeast(0)
                    Text(
                        if (rec.dailyMet) "✅ Günlük hedef tamam!" else "Hedefe $left dk kaldı",
                        fontWeight = FontWeight.SemiBold,
                        color = if (rec.dailyMet) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                    )
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                        Text(if (data.timer == null) "▶  Çalışmaya Başla" else "⏱  Zamanlayıcıya Git", maxLines = 1)
                    }
                    Text(
                        "Bugün kazanılan: ${rec.coinsEarned} 🪙",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Panel(Modifier.fillMaxWidth(), title = "🎯 Dikkat") {
                FocusSummary("Bugün", rec.focusYes, rec.focusNo)
                if (!data.settings.focusBeepEnabled && data.settings.focusCheckDivisions == 0) {
                    Text(
                        "Dikkat kontrolü kapalı. Çalış sekmesinden açabilirsin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        Pane(1.2f) {
            DailyStreakHero(dailyStreak, data.bestDailyStreak, data.streakFreezes, rec.dailyMet)
            ResponsiveGrid(streakItems, minItemWidth = 160.dp) { item, mod ->
                StreakCard(item.emoji, item.title, item.value, mod, unit = item.unit)
            }
        },
        Pane(1.2f) {
            Panel(Modifier.fillMaxWidth(), title = "Bugünkü Hedefler") {
                if (data.goals.isEmpty()) {
                    Text("Hedefler sekmesinden sayfa, test veya konu hedefi ekle.", style = MaterialTheme.typography.bodySmall)
                }
                data.goals.forEach { g -> GoalProgressRow(g, rec.progress[g.id] ?: 0, g.id in rec.goalsMet) }
            }
            Panel(Modifier.fillMaxWidth(), title = "Çalışma Aralığı") {
                Text(windowStatusText(data, now), style = MaterialTheme.typography.bodyMedium)
            }
            Panel(Modifier.fillMaxWidth(), title = "Son Ödüller") {
                if (data.log.isEmpty()) Text("Henüz ödül yok. İlk oturumunu başlat!", style = MaterialTheme.typography.bodySmall)
                data.log.take(6).forEach {
                    Text(it.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        },
    )
}

@Composable
private fun DailyStreakHero(streak: Int, best: Int, freezes: Int, metToday: Boolean) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = panelColor(),
        contentColor = cs.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(
            Modifier.background(
                Brush.horizontalGradient(listOf(cs.tertiary.copy(alpha = 0.28f), cs.primary.copy(alpha = 0.10f)))
            )
        ) {
            val narrow = maxWidth < 380.dp
            val main: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥", fontSize = 40.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Günlük Hedef Serisi", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        Text("$streak gün", fontSize = 32.sp, fontWeight = FontWeight.Black, color = cs.tertiary, maxLines = 1)
                        Text(
                            if (metToday) "Bugün tamamlandı, seri güvende 💪"
                            else if (streak > 0) "Bugün hedefi tamamla, seriyi koru!"
                            else "Hedefini tamamla, seriyi başlat!",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            val stats: @Composable () -> Unit = {
                Text("🏆 En iyi: $best gün", style = MaterialTheme.typography.labelMedium)
                Text("🧊 Dondurucu: $freezes", style = MaterialTheme.typography.labelMedium)
                Text("En çok ödül veren seri!", style = MaterialTheme.typography.labelSmall, color = cs.tertiary)
            }
            if (narrow) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    main()
                    stats()
                }
            } else {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { main() }
                    Column(horizontalAlignment = Alignment.End) { stats() }
                }
            }
        }
    }
}

@Composable
private fun GoalProgressRow(g: Goal, value: Int, met: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${g.type.emoji} ${g.name}",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                (if (met) "✅ " else "") + "$value / ${g.dailyTarget} ${g.unit}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        LinearProgressIndicator(
            progress = { (value.toFloat() / g.dailyTarget).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            color = if (met) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val big = g.type.step.coerceAtLeast(5)
            QuickBtn("−1") { Repo.addProgress(g.id, -1) }
            QuickBtn("+1") { Repo.addProgress(g.id, 1) }
            QuickBtn("+$big") { Repo.addProgress(g.id, big) }
        }
    }
}

@Composable
private fun QuickBtn(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        modifier = Modifier.height(30.dp),
    ) { Text(text, fontSize = 12.sp) }
}
