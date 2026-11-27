@file:OptIn(ExperimentalMaterial3Api::class)

package com.derscalisma.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derscalisma.app.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("d MMMM", Locale("tr"))

fun prettyDate(iso: String): String = runCatching { LocalDate.parse(iso).format(dateFmt) }.getOrDefault(iso)

/** Süreli hedefler paneli: ilerleme, plana göre durum, günde gereken miktar. */
@Composable
fun DeadlinesPanel(data: AppData, now: Long, compact: Boolean = false) {
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<DeadlineGoal?>(null) }
    val today = Time.date(now)
    val list = data.deadlines.map { Deadlines.status(data, it, today) }
        .sortedWith(compareBy({ it.pace == Pace.DONE }, { it.goal.dueDate }))
    if (compact && list.none { it.pace != Pace.DONE }) return

    Panel(
        Modifier.fillMaxWidth(),
        title = "📅 Süreli Hedefler",
        action = if (compact) null else ({ FilledTonalButton(onClick = { adding = true }) { Text("+ Ekle") } }),
    ) {
        if (list.isEmpty()) {
            Text(
                "Bir tarihe kadar bitirmek istediğin işi ekle: \"20 Aralık'a kadar 400 sayfa\" gibi. " +
                    "Günde ne kadar yapman gerektiğini ve planın önünde mi gerisinde mi olduğunu gösteririm.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            list.filter { !compact || it.pace != Pace.DONE }.forEach { st -> DeadlineCard(data, st, compact) { deleting = st.goal } }
        }
    }

    if (adding) DeadlineDialog(data) { adding = false }
    deleting?.let { g ->
        ConfirmDialog(
            title = "Süreli hedefi sil?",
            text = "\"${g.title}\" silinecek.",
            confirmText = "Sil",
            onConfirm = { Repo.deleteDeadline(g.id) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun DeadlineCard(data: AppData, st: DeadlineStatus, compact: Boolean, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val g = st.goal
    val paceColor = when (st.pace) {
        Pace.DONE, Pace.AHEAD -> cs.secondary
        Pace.ON_TRACK -> cs.tertiary
        Pace.BEHIND, Pace.OVERDUE -> cs.error
    }
    Surface(color = cs.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(paceLabel(st.pace), color = paceColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                "${st.done} / ${g.total} ${g.unit} • son gün ${prettyDate(g.dueDate)}" +
                    if (st.pace != Pace.DONE && st.pace != Pace.OVERDUE) " • ${st.daysLeft} gün kaldı" else "",
                style = MaterialTheme.typography.bodySmall,
            )
            // İlerleme çubuğu + "bugün burada olmalıydın" çizgisi
            BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(10.dp)) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(5.dp)).background(cs.onSurface.copy(alpha = 0.12f)))
                Box(Modifier.fillMaxHeight().fillMaxWidth(st.ratio).clip(RoundedCornerShape(5.dp)).background(paceColor))
                if (st.pace != Pace.DONE) {
                    Box(
                        Modifier
                            .padding(start = maxWidth * st.expectedRatio)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(cs.onSurface),
                    )
                }
            }
            if (st.pace != Pace.DONE) {
                val perDayText = if (st.daysLeft > 0) "Günde ${st.perDay} ${g.unit}" else "Kalan ${st.remaining} ${g.unit}"
                val todayText = if (g.linkedGoalId != null && st.daysLeft > 0) " • bugün ${st.today}/${st.perDay}" else ""
                Text(perDayText + todayText, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                val deltaText = when {
                    st.delta > 0 -> "Plandan ${st.delta} ${g.unit} öndesin"
                    st.delta < 0 -> "Plandan ${-st.delta} ${g.unit} gerisin"
                    else -> "Tam planda"
                }
                val proj = st.projected?.let { p ->
                    val due = LocalDate.parse(g.dueDate)
                    " • bu hızla ${p.format(dateFmt)} biter" + if (p.isAfter(due)) " ⚠️" else " 👍"
                } ?: ""
                Text(deltaText + proj, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            } else {
                Text("🏁 Tamamlandı! Ödül kazanıldı.", style = MaterialTheme.typography.bodySmall, color = cs.secondary)
            }
            if (!compact) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(-1, 1, 5, 10).forEach { d ->
                        OutlinedButton(
                            onClick = { Repo.addDeadlineProgress(g.id, d) },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(32.dp),
                        ) { Text(if (d > 0) "+$d" else "$d", fontSize = 12.sp) }
                    }
                    val linked = data.goals.find { it.id == g.linkedGoalId }
                    if (linked != null && st.pace != Pace.DONE && st.daysLeft > 0 && linked.dailyTarget != st.perDay) {
                        FilledTonalButton(onClick = { Repo.matchDailyToDeadline(g.id) }, modifier = Modifier.height(32.dp)) {
                            Text("Günlük hedefi ${st.perDay} yap", fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = onDelete) { Text("Sil") }
                }
                g.linkedGoalId?.let { id ->
                    data.goals.find { it.id == id }?.let {
                        Text("🔗 \"${it.name}\" günlük hedefine girilenler buraya da sayılır.", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeadlineDialog(data: AppData, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val today = LocalDate.now()
    var title by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("300") }
    var linked by remember { mutableStateOf(data.goals.firstOrNull { it.type == GoalType.PAGES }?.id) }
    var unit by remember { mutableStateOf(data.goals.find { it.id == linked }?.unit ?: "sayfa") }
    var due by remember { mutableStateOf(today.plusDays(14)) }
    val total = totalText.toIntOrNull()
    val days = (java.time.temporal.ChronoUnit.DAYS.between(today, due) + 1).toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📅 Yeni Süreli Hedef") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(40) },
                    label = { Text("Ne? (ör. Fizik kitabını bitir)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = totalText, onValueChange = { v -> totalText = v.filter { it.isDigit() }.take(5) },
                        label = { Text("Toplam") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = unit, onValueChange = { unit = it.take(12) },
                        label = { Text("Birim") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                OutlinedButton(onClick = {
                    DatePickerDialog(ctx, { _, y, m, d -> due = LocalDate.of(y, m + 1, d) }, due.year, due.monthValue - 1, due.dayOfMonth).apply {
                        datePicker.minDate = System.currentTimeMillis()
                    }.show()
                }, modifier = Modifier.fillMaxWidth()) { Text("Son gün: ${due.format(dateFmt)} ($days gün)") }
                Text("Günlük hedefe bağla (oraya girdiklerin buraya da sayılır):", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = linked == null, onClick = { linked = null }, label = { Text("Bağlama") })
                    data.goals.forEach { gl ->
                        FilterChip(selected = linked == gl.id, onClick = { linked = gl.id; unit = gl.unit }, label = { Text("${gl.type.emoji} ${gl.name}") })
                    }
                }
                if (total != null && total > 0 && days > 0) {
                    Text(
                        "Günde yaklaşık ${(total + days - 1) / days} $unit yapman gerekecek.",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = total != null && total > 0 && days > 0,
                onClick = {
                    Repo.addDeadline(title.trim(), unit.trim(), total ?: 1, due.toString(), linked)
                    onDismiss()
                },
            ) { Text("Ekle") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
    )
}

private fun paceLabel(p: Pace) = when (p) {
    Pace.DONE -> "Tamamlandı"
    Pace.AHEAD -> "Plandan önde"
    Pace.ON_TRACK -> "Planda"
    Pace.BEHIND -> "Plandan geride"
    Pace.OVERDUE -> "Süresi geçti"
}
