@file:OptIn(ExperimentalMaterial3Api::class)

package com.derscalisma.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.derscalisma.app.data.*
import com.derscalisma.app.ui.*

@Composable
fun GoalsScreen(data: AppData, now: Long) {
    val today = Time.date(now)
    val rec = data.days[today.toString()] ?: DayRecord()
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Goal?>(null) }

    AdaptiveColumns(
        Pane(1f) {
            Panel(Modifier.fillMaxWidth(), title = "⭐ Ana Hedef") {
                LabeledControl("Günlük çalışma süresi") {
                    Stepper(
                        data.settings.dailyGoalMinutes,
                        { v -> Repo.updateSettings { it.copy(dailyGoalMinutes = v) } },
                        min = 15, max = 720, step = 15, suffix = " dk",
                    )
                }
                Text(
                    "Bu hedefe her gün ulaşmak 🔥 Günlük Hedef Serisi'ni büyütür. En çok para veren seri budur.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Panel(Modifier.fillMaxWidth(), title = "🪙 Ödül Tablosu") {
                RewardLine("🔥 Günlük hedef", "100 + 15 × seri")
                RewardLine("🎯 Aralık hedefi", "40 + 6 × seri")
                RewardLine("📖 Sayfa / test / konu", "25 + 4 × seri")
                RewardLine("📅 Aralıkta çalışmak", "15 + 2 × seri")
                RewardLine("⏱️ Her dakika", "1")
                RewardLine("✅ Tüm turları bitirmek", "5 × tur")
                RewardLine("🎯 Dikkat sorusuna cevap", "1")
                RewardLine("🏅 Seri rozetleri", "3·7·14·30·60·100·365")
                Text(
                    "Seri çarpanı en fazla 30'a kadar sayılır.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        Pane(1.4f) {
            DeadlinesPanel(data, now)
            Panel(
                Modifier.fillMaxWidth(),
                title = "Günlük Hedefler",
                action = { FilledTonalButton(onClick = { adding = true }) { Text("+ Ekle") } },
            ) {
                if (data.goals.isEmpty()) {
                    Text("Sayfa, test veya konu hedefi ekleyerek ek seriler kazan.", style = MaterialTheme.typography.bodyMedium)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.goals.forEach { g ->
                        val streak = Streaks.goal(data, g.id, today)
                        val progress = rec.progress[g.id] ?: 0
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${g.type.emoji} ${g.name}",
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text("🔥 $streak", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { deleting = g }) { Text("Sil") }
                                }
                                LabeledControl(
                                    "Günlük hedef",
                                    hint = "Bugün: $progress / ${g.dailyTarget} ${g.unit}" + if (g.id in rec.goalsMet) " ✅" else "",
                                ) {
                                    Stepper(
                                        g.dailyTarget,
                                        { v -> Repo.setGoalTarget(g.id, v) },
                                        min = 1, max = 2000, step = g.type.step, suffix = " ${g.unit}",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    )

    if (adding) AddGoalDialog(onDismiss = { adding = false })
    deleting?.let { g ->
        ConfirmDialog(
            title = "Hedefi sil?",
            text = "\"${g.name}\" hedefi ve serisi silinecek.",
            confirmText = "Sil",
            onConfirm = { Repo.deleteGoal(g.id) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun RewardLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AddGoalDialog(onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(GoalType.PAGES) }
    var name by remember { mutableStateOf(GoalType.PAGES.defaultName) }
    var unit by remember { mutableStateOf(GoalType.PAGES.defaultUnit) }
    var targetText by remember { mutableStateOf(GoalType.PAGES.defaultTarget.toString()) }
    val target = targetText.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Hedef") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GoalType.entries.forEach { gt ->
                        FilterChip(
                            selected = type == gt,
                            onClick = {
                                type = gt
                                name = gt.defaultName
                                unit = gt.defaultUnit
                                targetText = gt.defaultTarget.toString()
                            },
                            label = { Text("${gt.emoji} ${gt.defaultName}") },
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("Hedef adı (ör. Matematik soru)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { v -> targetText = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Günlük hedef") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it.take(12) },
                    label = { Text("Birim (sayfa, soru...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = target != null && target > 0,
                onClick = {
                    Repo.addGoal(type, name.trim(), unit.trim(), target ?: 1)
                    onDismiss()
                },
            ) { Text("Ekle") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
    )
}
