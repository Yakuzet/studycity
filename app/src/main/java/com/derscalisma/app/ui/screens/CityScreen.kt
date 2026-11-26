@file:OptIn(ExperimentalMaterial3Api::class)

package com.derscalisma.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derscalisma.app.data.*
import com.derscalisma.app.ui.*

const val CITY_COLS = 12
const val CITY_ROWS = 7

@Composable
fun CityScreen(data: AppData) {
    var selected by remember { mutableStateOf<String?>(null) }
    var removeMode by remember { mutableStateOf(false) }
    val ground = Catalog.ground(data.ground)
    val placed = remember(data.city) { data.city.associateBy { it.x to it.y } }
    val population = data.city.sumOf { Catalog.building(it.buildingId)?.population ?: 0 }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1.7f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏙️ Şehrin", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("👥 $population   🏗️ ${data.city.size}   🪙 ${data.coins}", fontWeight = FontWeight.SemiBold)
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val cell = minOf(maxWidth / CITY_COLS, maxHeight / CITY_ROWS)
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    for (y in 0 until CITY_ROWS) {
                        Row {
                            for (x in 0 until CITY_COLS) {
                                val p = placed[x to y]
                                val b = p?.let { Catalog.building(it.buildingId) }
                                Box(
                                    Modifier
                                        .size(cell)
                                        .background(Color(if ((x + y) % 2 == 0) ground.base else ground.alt))
                                        .clickable {
                                            if (removeMode) {
                                                if (p != null) Repo.removeBuilding(x, y)?.let(Repo::toast)
                                            } else if (p == null) {
                                                val s = selected
                                                if (s == null) Repo.toast("Önce sağdan bir bina seç")
                                                else Repo.placeBuilding(x, y, s)?.let(Repo::toast)
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (b != null) Text(b.emoji, fontSize = (cell.value * 0.6f).sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Panel(Modifier.weight(1f).fillMaxHeight(), title = "İnşa Et") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = removeMode,
                    onClick = { removeMode = !removeMode; if (removeMode) selected = null },
                    label = { Text("🗑️ Kaldır (½ iade)") },
                )
            }
            Text(
                if (removeMode) "Kaldırmak istediğin binaya dokun."
                else if (selected != null) "Yerleştirmek için boş bir kareye dokun."
                else "Bir bina seç. Yeni binalar günlük seri rekorunla açılır.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(Catalog.buildings, key = { it.id }) { b ->
                    val locked = data.bestDailyStreak < b.unlockStreak
                    val affordable = data.coins >= b.price
                    val isSel = selected == b.id
                    val cs = MaterialTheme.colorScheme
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSel) cs.primaryContainer else cs.surfaceVariant,
                        border = if (isSel) BorderStroke(2.dp, cs.primary) else null,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !locked) {
                            selected = b.id
                            removeMode = false
                        },
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (locked) "🔒" else b.emoji, fontSize = 26.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (locked) "${b.unlockStreak} günlük seri gerekli"
                                    else if (b.population > 0) "+${b.population} nüfus" else "Dekor",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${b.price} 🪙",
                                fontWeight = FontWeight.Bold,
                                color = if (!locked && affordable) cs.tertiary else cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
