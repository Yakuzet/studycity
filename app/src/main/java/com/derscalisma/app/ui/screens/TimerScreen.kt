@file:OptIn(ExperimentalMaterial3Api::class)

package com.derscalisma.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derscalisma.app.data.*
import com.derscalisma.app.ui.*

private data class Preset(val work: Int, val brk: Int, val rounds: Int)

private val presets = listOf(
    Preset(25, 5, 4),
    Preset(50, 10, 3),
    Preset(45, 15, 2),
    Preset(90, 20, 2),
)

@Composable
fun TimerScreen(data: AppData, now: Long) {
    var confirmStop by remember { mutableStateOf(false) }

    AdaptiveColumns(
        Pane(1.2f) { TimerPanel(data, now) { confirmStop = true } },
        Pane(1f) {
            SessionSettingsPanel(data, now)
            FocusSettingsPanel(data, now)
            TodayPanel(data, now)
        },
    )

    if (confirmStop) {
        ConfirmDialog(
            title = "Oturumu bitir?",
            text = "Şu ana kadar çalıştığın süre kaydedilecek.",
            confirmText = "Bitir",
            onConfirm = { Repo.stopTimer() },
            onDismiss = { confirmStop = false },
        )
    }
}

@Composable
private fun TimerPanel(data: AppData, now: Long, onStop: () -> Unit) {
    val t = data.timer
    val s = data.settings
    val cs = MaterialTheme.colorScheme
    val layout = LocalLayout.current

    Panel(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val ringSize = if (layout.compact) minOf(maxWidth * 0.8f, 300.dp)
            else minOf(maxWidth * 0.85f, layout.height - 150.dp).coerceIn(140.dp, 380.dp)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val isBreak = t?.phase == Phase.BREAK
                val total = t?.phaseTotalMs ?: (s.workMinutes * MINUTE_MS)
                val remaining = when {
                    t == null -> total
                    t.pausedRemainingMs != null -> t.pausedRemainingMs
                    else -> (t.phaseEndAt - now).coerceAtLeast(0)
                }
                val color = if (isBreak) cs.secondary else cs.primary

                Text(
                    when {
                        t == null -> "Hazır"
                        t.isPaused -> "⏸ Duraklatıldı"
                        isBreak -> "☕ MOLA"
                        else -> "📖 ÇALIŞMA"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                Spacer(Modifier.height(6.dp))
                ProgressRing(
                    progress = if (total > 0) 1f - remaining.toFloat() / total else 0f,
                    modifier = Modifier.size(ringSize),
                    color = color,
                    style = Catalog.timerStyle(data.timerStyle),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Time.clock(remaining), fontSize = (ringSize.value / 5.2f).sp, fontWeight = FontWeight.Black)
                        val round = t?.round ?: 1
                        val rounds = t?.rounds ?: s.rounds
                        Text("Tur $round / $rounds", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        RoundDots(round, rounds, done = t?.let { if (it.phase == Phase.BREAK) it.round else it.round - 1 } ?: 0)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val pad = PaddingValues(horizontal = 6.dp)
                    if (t == null) {
                        Button(onClick = { Repo.startTimer() }, modifier = Modifier.weight(1f)) { Text("▶  Başlat") }
                    } else {
                        if (t.isPaused) {
                            Button(onClick = { Repo.resumeTimer() }, modifier = Modifier.weight(1f), contentPadding = pad) {
                                Text("▶ Devam", maxLines = 1)
                            }
                        } else {
                            Button(onClick = { Repo.pauseTimer() }, modifier = Modifier.weight(1f), contentPadding = pad) {
                                Text("⏸ Duraklat", maxLines = 1)
                            }
                        }
                        OutlinedButton(onClick = { Repo.skipPhase() }, modifier = Modifier.weight(1f), contentPadding = pad) {
                            Text("⏭ Atla", maxLines = 1)
                        }
                        OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f), contentPadding = pad) {
                            Text("⏹ Bitir", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionSettingsPanel(data: AppData, now: Long) {
    val s = data.settings
    val cs = MaterialTheme.colorScheme
    Panel(Modifier.fillMaxWidth(), title = "⏱️ Oturum Ayarları") {
        if (data.timer != null) {
            Text("Süre ayarları bir sonraki oturumda geçerli olur.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        LabeledControl("Çalışma süresi") {
            Stepper(s.workMinutes, { v -> Repo.updateSettings { it.copy(workMinutes = v) } }, min = 5, max = 180, step = 5, suffix = " dk")
        }
        LabeledControl("Mola süresi") {
            Stepper(s.breakMinutes, { v -> Repo.updateSettings { it.copy(breakMinutes = v) } }, min = 0, max = 60, step = 1, suffix = " dk")
        }
        LabeledControl("Tur sayısı") {
            Stepper(s.rounds, { v -> Repo.updateSettings { it.copy(rounds = v) } }, min = 1, max = 12)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEach { p ->
                FilterChip(
                    selected = s.workMinutes == p.work && s.breakMinutes == p.brk && s.rounds == p.rounds,
                    onClick = { Repo.updateSettings { it.copy(workMinutes = p.work, breakMinutes = p.brk, rounds = p.rounds) } },
                    label = { Text("${p.work}/${p.brk} ×${p.rounds}") },
                )
            }
        }
        val workTotal = s.workMinutes * s.rounds
        val allTotal = workTotal + s.breakMinutes * (s.rounds - 1)
        Text(
            "Toplam $workTotal dk çalışma • şimdi başlarsan bitiş ≈ ${Time.timeOf(now + allTotal * MINUTE_MS)}",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun FocusSettingsPanel(data: AppData, now: Long) {
    val s = data.settings
    val cs = MaterialTheme.colorScheme
    Panel(Modifier.fillMaxWidth(), title = "🎯 Dikkat Toplama") {
        LabeledControl("Bip sesi", hint = "Çalışırken aralıklarla bip çalar") {
            Switch(checked = s.focusBeepEnabled, onCheckedChange = { v -> Repo.updateSettings { it.copy(focusBeepEnabled = v) } })
        }
        if (s.focusBeepEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = s.focusBeepRandom,
                    onClick = { Repo.updateSettings { it.copy(focusBeepRandom = true) } },
                    label = { Text("🎲 Rastgele") },
                )
                FilterChip(
                    selected = !s.focusBeepRandom,
                    onClick = { Repo.updateSettings { it.copy(focusBeepRandom = false) } },
                    label = { Text("⏱ Sabit aralık") },
                )
            }
            if (s.focusBeepRandom) {
                LabeledControl("En kısa aralık") {
                    Stepper(s.focusBeepMin, { v ->
                        Repo.updateSettings { it.copy(focusBeepMin = v, focusBeepMax = maxOf(it.focusBeepMax, v)) }
                    }, min = 1, max = 120, suffix = " dk")
                }
                LabeledControl("En uzun aralık") {
                    Stepper(s.focusBeepMax, { v ->
                        Repo.updateSettings { it.copy(focusBeepMax = v) }
                    }, min = s.focusBeepMin, max = 180, suffix = " dk")
                }
                Text(
                    "Her bip, ${s.focusBeepMin}–${s.focusBeepMax} dk arasında rastgele bir anda çalar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            } else {
                LabeledControl("Bip aralığı") {
                    Stepper(s.focusBeepEvery, { v -> Repo.updateSettings { it.copy(focusBeepEvery = v) } }, min = 1, max = 120, suffix = " dk")
                }
            }
            LabeledControl("Her bipten sonra sor", hint = "\"Dikkatin toplu mu?\"") {
                Switch(checked = s.focusAskAfterBeep, onCheckedChange = { v -> Repo.updateSettings { it.copy(focusAskAfterBeep = v) } })
            }
            LabeledControl("Titreşim") {
                Switch(checked = s.focusVibrate, onCheckedChange = { v -> Repo.updateSettings { it.copy(focusVibrate = v) } })
            }
            OutlinedButton(onClick = { Repo.testBeep() }) { Text("🔊 Bipi dene") }
        }
        Spacer(Modifier.height(6.dp))
        Text("Çalışma turunda ayrıca sor", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..4).forEach { n ->
                FilterChip(
                    selected = s.focusCheckDivisions == n,
                    onClick = { Repo.updateSettings { it.copy(focusCheckDivisions = n) } },
                    label = { Text(Focus.divisionLabel(n)) },
                )
            }
        }
        val example = when (s.focusCheckDivisions) {
            0 -> "Turun içinde ayrıca soru sorulmaz."
            1 -> "Her çalışma turu bitince sorulur."
            else -> {
                val n = s.focusCheckDivisions
                val parts = (1 until n).joinToString(", ") { k -> "${s.workMinutes * k / n}." }
                "${s.workMinutes} dk'lık turda $parts dakikada sorulur."
            }
        }
        Text(example, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        val t = data.timer
        if (t != null) FocusSummary("Bu oturum", t.sessionFocusYes, t.sessionFocusNo)
        val rec = data.days[Time.dayKey(now)] ?: DayRecord()
        FocusSummary("Bugün", rec.focusYes, rec.focusNo)
    }
}

@Composable
private fun TodayPanel(data: AppData, now: Long) {
    val t = data.timer
    Panel(Modifier.fillMaxWidth(), title = "📊 Bugün") {
        val todayMs = liveTodayMs(data, now)
        val goalMs = data.settings.dailyGoalMinutes * MINUTE_MS
        Text("${todayMs / MINUTE_MS} / ${data.settings.dailyGoalMinutes} dk", fontWeight = FontWeight.Bold)
        LinearProgressIndicator(
            progress = { (todayMs.toFloat() / goalMs).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        if (t != null) {
            Text("Bu oturumda: ${Time.duration(liveSessionMs(t, now))}", style = MaterialTheme.typography.bodySmall)
        }
        Text(windowStatusText(data, now), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RoundDots(round: Int, rounds: Int, done: Int) {
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 1..rounds.coerceAtMost(12)) {
            val c = when {
                i <= done -> cs.secondary
                i == round -> cs.primary
                else -> cs.onSurface.copy(alpha = 0.2f)
            }
            Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        }
    }
}
