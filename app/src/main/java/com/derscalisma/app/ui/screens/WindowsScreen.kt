@file:OptIn(ExperimentalMaterial3Api::class)

package com.derscalisma.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.derscalisma.app.data.*
import com.derscalisma.app.ui.*

@Composable
fun WindowsScreen(data: AppData, now: Long) {
    var editing by remember { mutableStateOf<StudyWindow?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<StudyWindow?>(null) }
    val today = Time.date(now)
    val rec = data.days[today.toString()] ?: DayRecord()
    val nowMin = Time.minuteOfDay(now)
    val streaks = listOf(
        Triple("📅", "Aralıkta Çalışma Serisi", Streaks.windowAttend(data, now)),
        Triple("🎯", "Aralık Hedef Serisi", Streaks.windowGoal(data, now)),
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(260.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ResponsiveGrid(streaks, minItemWidth = 200.dp) { (emoji, title, value), mod ->
                    StreakCard(emoji, title, value, mod, unit = "aralık")
                }
                Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Çalışma Aralığı Ekle") }
            }
        }
        if (data.windows.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Panel(Modifier.fillMaxWidth(), title = "Çalışma aralığı nedir?") {
                    Text(
                        "Hangi saatlerde ders çalışacağını belirle (ör. hafta içi 17:00–19:00). " +
                            "Aralık başlayınca \"çalışmaya başlamalısın\" bildirimi gelir, başlamazsan tekrar hatırlatılır. " +
                            "Aralık içinde en az 5 dk çalışırsan Aralıkta Çalışma Serisi, aralık hedefine ulaşırsan Aralık Hedef Serisi büyür.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        items(data.windows.sortedBy { it.startMin }, key = { it.id }) { w ->
            val isToday = today.dayOfWeek.value in w.days
            val active = isToday && nowMin >= w.startMin && nowMin < w.endMin
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (active) "🟢 " else "") + w.label,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${Time.hm(w.startMin)}–${Time.hm(w.endMin)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    if (w.days.size == 7) "Her gün" else w.days.sorted().joinToString(" ") { Time.dayShort[it - 1] },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                if (isToday) {
                    val done = liveWindowMs(data, w, now) / MINUTE_MS
                    Text(
                        "Bugün: $done / ${w.targetMinutes} dk" +
                            (if (w.id in rec.windowsMet) " ✅" else if (w.id in rec.windowsAttended) " 📅" else ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LinearProgressIndicator(
                        progress = { (done.toFloat() / w.targetMinutes).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                } else {
                    Text("Hedef: ${w.targetMinutes} dk • bugün yok", style = MaterialTheme.typography.bodySmall)
                }
                Row {
                    TextButton(onClick = { editing = w }) { Text("Düzenle") }
                    TextButton(onClick = { deleting = w }) { Text("Sil") }
                }
            }
        }
    }

    if (creating) WindowDialog(null, onDismiss = { creating = false })
    editing?.let { WindowDialog(it, onDismiss = { editing = null }) }
    deleting?.let { w ->
        ConfirmDialog(
            title = "Aralığı sil?",
            text = "\"${w.label}\" aralığı ve hatırlatmaları silinecek.",
            confirmText = "Sil",
            onConfirm = { Repo.deleteWindow(w.id) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun WindowDialog(initial: StudyWindow?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var label by remember { mutableStateOf(initial?.label ?: "Ders Saati") }
    var start by remember { mutableIntStateOf(initial?.startMin ?: (17 * 60)) }
    var end by remember { mutableIntStateOf(initial?.endMin ?: (19 * 60)) }
    var days by remember { mutableStateOf(initial?.days ?: (1..5).toSet()) }
    var target by remember { mutableIntStateOf(initial?.targetMinutes ?: 90) }
    val duration = end - start
    val valid = duration >= 15 && days.isNotEmpty() && label.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yeni Çalışma Aralığı" else "Aralığı Düzenle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(30) },
                    label = { Text("İsim") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Başlangıç", style = MaterialTheme.typography.labelMedium)
                        OutlinedButton(onClick = { pickTime(ctx, start) { start = it } }, modifier = Modifier.fillMaxWidth()) {
                            Text(Time.hm(start))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Bitiş", style = MaterialTheme.typography.labelMedium)
                        OutlinedButton(onClick = { pickTime(ctx, end) { end = it } }, modifier = Modifier.fillMaxWidth()) {
                            Text(Time.hm(end))
                        }
                    }
                }
                if (duration < 15) {
                    Text(
                        "Bitiş, başlangıçtan en az 15 dk sonra olmalı (gece yarısını geçen aralıklar desteklenmez).",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (d in 1..7) {
                        FilterChip(
                            selected = d in days,
                            onClick = { days = if (d in days) days - d else days + d },
                            label = { Text(Time.dayShort[d - 1]) },
                        )
                    }
                }
                LabeledControl("Aralık hedefi") {
                    Stepper(
                        target.coerceAtMost(maxOf(5, duration)),
                        { target = it },
                        min = 5, max = maxOf(5, duration), step = 5, suffix = " dk",
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    Repo.saveWindow(
                        StudyWindow(
                            id = initial?.id ?: Time.newId(),
                            label = label.trim(),
                            startMin = start,
                            endMin = end,
                            days = days,
                            targetMinutes = target.coerceIn(5, maxOf(5, duration)),
                        )
                    )
                    onDismiss()
                },
            ) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
    )
}
