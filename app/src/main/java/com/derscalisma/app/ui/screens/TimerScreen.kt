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
            AutoPanel(data)
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
    val c = com.derscalisma.app.ui.theme.AppTheme.colors
    val f = com.derscalisma.app.i18n.S.focus
    val layout = LocalLayout.current
    var earlyBreak by remember { mutableStateOf(false) }
    var emergency by remember { mutableStateOf(false) }

    Panel(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val ringSize = if (layout.compact) minOf(maxWidth * 0.78f, 290.dp)
            else minOf(maxWidth * 0.85f, layout.height - 190.dp).coerceIn(130.dp, 360.dp)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val isBreak = t?.phase == Phase.BREAK
                val total = t?.phaseTotalMs ?: (s.workMinutes * MINUTE_MS)
                val remaining = when {
                    t == null -> total
                    t.waiting -> 0L
                    t.pausedRemainingMs != null -> t.pausedRemainingMs
                    else -> (t.phaseEndAt - now).coerceAtLeast(0)
                }
                val color = when {
                    t?.overtime == true -> c.warning
                    isBreak -> c.success
                    else -> c.accent
                }
                Text(
                    when {
                        t == null -> f.ready
                        t.isPaused -> f.paused
                        t.overtime -> f.overtimeBadge
                        isBreak -> f.onBreak
                        else -> f.studying
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                )
                if (t?.fatigue == true && t.phase == Phase.WORK) Text(f.fatigueBadge, style = MaterialTheme.typography.bodySmall, color = c.warning)
                Spacer(Modifier.height(6.dp))
                com.derscalisma.app.ui.kit.Ring(
                    progress = if (t?.stopwatch == true) 1f else if (total > 0) 1f - remaining.toFloat() / total else 0f,
                    modifier = Modifier.size(ringSize),
                    color = color,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val label = if (t?.stopwatch == true) "+" + Time.clock(Repo.stopwatchElapsed(t, now)) else Time.clock(remaining)
                        Text(label, style = MaterialTheme.typography.displayLarge.copy(fontSize = (ringSize.value / 4.6f).sp))
                        val round = t?.round ?: 1
                        val rounds = t?.rounds ?: s.rounds
                        Text(com.derscalisma.app.i18n.S.common.roundOf(round, rounds), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        RoundDots(round, rounds, done = t?.let { if (it.phase == Phase.BREAK) it.round else it.round - 1 } ?: 0)
                        if (t != null) {
                            val held = data.ledger.filter { it.session == t.id }.sumOf { it.amount }
                            if (held > 0) Text(f.sessionEarned(held), style = MaterialTheme.typography.labelMedium, color = c.success)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                when {
                    t == null -> com.derscalisma.app.ui.kit.PrimaryButton(f.startCta(s.workMinutes, s.rounds), { Repo.startTimer() }, Modifier.fillMaxWidth())
                    t.waiting -> WaitingCard(t, now)
                    else -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            val pad = PaddingValues(horizontal = 6.dp)
                            if (t.stopwatch) {
                                Button(onClick = { Repo.finishStopwatch() }, modifier = Modifier.weight(1f), contentPadding = pad) { Text(f.finishOvertime, maxLines = 1) }
                            } else if (t.isPaused) {
                                Button(onClick = { Repo.resumeTimer() }, modifier = Modifier.weight(1f), contentPadding = pad) { Text(f.resume, maxLines = 1) }
                            } else {
                                Button(onClick = { Repo.pauseTimer() }, modifier = Modifier.weight(1f), contentPadding = pad) { Text(f.pause, maxLines = 1) }
                            }
                            if (!t.stopwatch) OutlinedButton(onClick = {
                                if (Repo.breakSkipIsEarly(t, System.currentTimeMillis())) earlyBreak = true else Repo.skipPhase()
                            }, modifier = Modifier.weight(1f), contentPadding = pad) { Text(if (isBreak) f.endBreak else f.skipRound, maxLines = 1) }
                            OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f), contentPadding = pad) { Text(f.stop, maxLines = 1) }
                        }
                        Spacer(Modifier.height(6.dp))
                        if (isBreak) {
                            val left = Rewards.MAX_BREAK_EXTENSIONS - t.breakExtensions
                            if (t.extensionBlocked) Text(f.extendBlocked, style = MaterialTheme.typography.bodySmall, color = c.danger)
                            else if (left > 0) com.derscalisma.app.ui.kit.TonalButton(
                                f.extendBreak(Rewards.BREAK_EXTENSION_MIN, Rewards.breakExtensionPrice(t.breakExtensions)),
                                { Repo.extendBreak()?.let(Repo::toast) },
                                icon = com.derscalisma.app.ui.icons.AppIcons.LocalCafe,
                            )
                        } else if (!t.isPaused) {
                            com.derscalisma.app.ui.kit.GhostButton(f.emergency, { emergency = true }, icon = com.derscalisma.app.ui.icons.AppIcons.Emergency, color = c.danger)
                        }
                        if (t.isPaused && data.away == null) {
                            Text(f.pauseReasonPrompt, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                            Spacer(Modifier.height(4.dp))
                            com.derscalisma.app.ui.rewards.ReasonGrid(t.pauseReason) { Repo.setPauseReason(if (it == t.pauseReason) null else it) }
                        }
                    }
                }
            }
        }
    }
    if (earlyBreak) {
        com.derscalisma.app.ui.kit.AppDialog(
            title = f.earlyBreakTitle, text = f.earlyBreakText, icon = com.derscalisma.app.ui.icons.AppIcons.FreeBreakfast,
            onDismiss = { earlyBreak = false }, dismissText = f.keepResting, confirmText = f.skipAnyway,
            onConfirm = { Repo.skipPhase(); earlyBreak = false },
        )
    }
    if (emergency) {
        com.derscalisma.app.ui.kit.AppDialog(title = f.emergencyTitle, text = f.emergencyText, icon = com.derscalisma.app.ui.icons.AppIcons.Emergency, onDismiss = { emergency = false }) {
            com.derscalisma.app.ui.rewards.ReasonGrid { Repo.pauseTimer(it); emergency = false }
        }
    }
}

/** Faz bitti: ek süre, kronometre ya da sıradaki faz. */
@Composable
private fun WaitingCard(t: TimerState, now: Long) {
    val f = com.derscalisma.app.i18n.S.focus
    val c = com.derscalisma.app.ui.theme.AppTheme.colors
    val afterWork = t.phase == Phase.WORK
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (!afterWork) f.breakOver else if (t.isLastRound) f.lastRoundDone else f.roundDone, style = MaterialTheme.typography.titleLarge)
        t.autoAt?.let { at ->
            val sec = ((at - now + 999) / 1000).toInt().coerceAtLeast(0)
            Text(
                if (!afterWork) f.autoWorkIn(sec) else if (t.isLastRound) f.autoFinishIn(sec) else f.autoBreakIn(sec),
                style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
            )
        }
        Spacer(Modifier.height(8.dp))
        com.derscalisma.app.ui.kit.PrimaryButton(
            if (!afterWork) f.startWork else if (t.isLastRound) f.finishSession else f.startBreak,
            { Repo.startNext() }, Modifier.fillMaxWidth(),
        )
        if (afterWork) {
            Spacer(Modifier.height(8.dp))
            Text(f.needMoreTime, style = MaterialTheme.typography.titleSmall)
            Text(f.overtimeHint, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(2, 5, 10).forEach { m -> FilledTonalButton(onClick = { Repo.addOvertime(m) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+$m") } }
                FilledTonalButton(onClick = { Repo.startStopwatch() }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(f.stopwatch) }
            }
        } else if (!t.extensionBlocked && t.breakExtensions < Rewards.MAX_BREAK_EXTENSIONS) {
            Spacer(Modifier.height(6.dp))
            com.derscalisma.app.ui.kit.TonalButton(
                f.extendBreak(Rewards.BREAK_EXTENSION_MIN, Rewards.breakExtensionPrice(t.breakExtensions)),
                { Repo.extendBreak()?.let(Repo::toast) },
            )
        }
    }
}

/** Otomatik geçişler, müzik ve elle süre ekleme. */
@Composable
private fun AutoPanel(data: AppData) {
    val s = data.settings
    val f = com.derscalisma.app.i18n.S.focus
    var manual by remember { mutableStateOf(false) }
    val delays = listOf(0, 5, 10, 15, 30, 60)
    fun delayLabel(d: Int) = if (d == 0) f.immediately else com.derscalisma.app.i18n.S.common.seconds(d)
    Panel(Modifier.fillMaxWidth(), title = f.sessionSettings) {
        LabeledControl(f.autoBreak, hint = f.autoBreakHint) {
            Switch(checked = s.autoBreak, onCheckedChange = { v -> Repo.updateSettings { it.copy(autoBreak = v) } })
        }
        if (s.autoBreak) com.derscalisma.app.ui.kit.ChoiceChips(delays, s.autoBreakDelaySec, { delayLabel(it) }, { d -> Repo.updateSettings { it.copy(autoBreakDelaySec = d) } })
        LabeledControl(f.autoWork, hint = f.autoWorkHint) {
            Switch(checked = s.autoWork, onCheckedChange = { v -> Repo.updateSettings { it.copy(autoWork = v) } })
        }
        if (s.autoWork) com.derscalisma.app.ui.kit.ChoiceChips(delays, s.autoWorkDelaySec, { delayLabel(it) }, { d -> Repo.updateSettings { it.copy(autoWorkDelaySec = d) } })
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(f.music, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        val tracks = listOf("off") + com.derscalisma.app.audio.StudyTrack.entries.map { it.id }
        com.derscalisma.app.ui.kit.ChoiceChips(tracks, s.studyMusic, { if (it == "off") f.musicOff else f.track(it) }, { id -> Repo.updateSettings { it.copy(studyMusic = id) } })
        if (s.studyMusic != "off") LabeledControl(f.musicInBreak) {
            Switch(checked = s.studyMusicInBreak, onCheckedChange = { v -> Repo.updateSettings { it.copy(studyMusicInBreak = v) } })
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        com.derscalisma.app.ui.kit.TonalButton(f.manualTitle, { manual = true }, icon = com.derscalisma.app.ui.icons.AppIcons.MoreTime)
    }
    if (manual) com.derscalisma.app.ui.rewards.ManualStudyDialog { manual = false }
}

@Composable
private fun SessionSettingsPanel(data: AppData, now: Long) {
    val s = data.settings
    val t = data.timer
    val cs = MaterialTheme.colorScheme
    // Oturum çalışıyorsa değerler doğrudan çalışan oturumdan okunur ve ona uygulanır.
    val work = t?.workMinutes ?: s.workMinutes
    val brk = t?.breakMinutes ?: s.breakMinutes
    val rounds = t?.rounds ?: s.rounds
    fun apply(w: Int = work, b: Int = brk, r: Int = rounds) {
        if (t != null) Repo.adjustActiveTimer(w, b, r)
        else Repo.updateSettings { it.copy(workMinutes = w, breakMinutes = b, rounds = r) }
    }
    Panel(Modifier.fillMaxWidth(), title = if (t != null) "⏱️ Aktif Oturum Ayarları" else "⏱️ Oturum Ayarları") {
        if (t != null) {
            Text(
                "Değişiklikler çalışan oturuma hemen uygulanır, geçen süre korunur.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
        LabeledControl("Çalışma süresi") {
            Stepper(work, { v -> apply(w = v) }, min = 5, max = 180, step = 5, suffix = " dk")
        }
        LabeledControl("Mola süresi") {
            Stepper(brk, { v -> apply(b = v) }, min = 0, max = 60, step = 1, suffix = " dk")
        }
        LabeledControl("Tur sayısı", hint = if (t != null) "Şu an ${t.round}. turdasın" else null) {
            Stepper(rounds, { v -> apply(r = v) }, min = t?.round ?: 1, max = 12)
        }
        if (t != null) {
            LabeledControl(if (t.phase == Phase.WORK) "Bu çalışma turunu" else "Bu molayı") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(onClick = { Repo.extendPhase(-5) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("−5 dk") }
                    FilledTonalButton(onClick = { Repo.extendPhase(5) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+5 dk") }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEach { p ->
                FilterChip(
                    selected = work == p.work && brk == p.brk && rounds == p.rounds,
                    onClick = { apply(p.work, p.brk, maxOf(p.rounds, t?.round ?: 1)) },
                    label = { Text("${p.work}/${p.brk} ×${p.rounds}") },
                )
            }
        }
        if (t == null) {
            val workTotal = work * rounds
            val allTotal = workTotal + brk * (rounds - 1)
            Text(
                "Toplam $workTotal dk çalışma • şimdi başlarsan bitiş ≈ ${Time.timeOf(now + allTotal * MINUTE_MS)}",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        } else {
            // Kalan fazların toplamıyla tahmini bitiş
            val left = if (t.isPaused) (t.pausedRemainingMs ?: 0L) else (t.phaseEndAt - now).coerceAtLeast(0)
            val rest = (t.rounds - t.round) * (t.workMinutes + t.breakMinutes) * MINUTE_MS -
                (if (t.phase == Phase.BREAK) t.breakMinutes * MINUTE_MS else 0L)
            Text(
                "Oturumun tahmini bitişi ≈ ${Time.timeOf(now + left + rest.coerceAtLeast(0))}" + if (t.isPaused) " (duraklatıldı)" else "",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
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
        LabeledControl(com.derscalisma.app.i18n.S.focus.answerTime, hint = com.derscalisma.app.i18n.S.focus.answerTimeHint) {
            Stepper(s.focusAnswerSec, { v -> Repo.updateSettings { it.copy(focusAnswerSec = v) } }, min = Focus.MIN_ANSWER_SEC, max = Focus.MAX_ANSWER_SEC, step = 5, suffix = " sn")
        }
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
