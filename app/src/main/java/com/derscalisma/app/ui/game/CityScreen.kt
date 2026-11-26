@file:OptIn(ExperimentalMaterial3Api::class)

package com.derscalisma.app.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derscalisma.app.data.AppData
import com.derscalisma.app.data.MINUTE_MS
import com.derscalisma.app.data.Repo
import com.derscalisma.app.data.game.*
import com.derscalisma.app.ui.*
import kotlin.math.roundToInt

enum class CityTab(val title: String) { VILLAGE("🏰 Köy"), ARMY("⚔️ Ordu"), CAMPAIGN("🗺️ Seferler") }

private data class Placing(val type: BType, val x: Int, val y: Int, val moveId: String?)

@Composable
fun CityScreen(
    data: AppData,
    now: Long,
    onAttack: (Int) -> Unit,
    onStudy: () -> Unit,
    initialTab: CityTab = CityTab.VILLAGE,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val g = data.game

    // Eğitim kuyruğunu canlı tut.
    LaunchedEffect(g.queue.isNotEmpty(), now / 1000) {
        val head = g.queue.firstOrNull()
        if (head != null && now >= g.queueStartedAt + Game.trainMs(g, head.troop)) Game.refresh()
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CityTab.entries.forEach { t ->
                    FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.title) })
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "👷 ${Game.freeBuilders(g)}/${g.builders}  ⭐ ${Game.stars(g)}",
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        when (tab) {
            CityTab.VILLAGE -> VillageTab(data, now, onStudy)
            CityTab.ARMY -> ArmyTab(data, now)
            CityTab.CAMPAIGN -> CampaignTab(data, onAttack) { tab = CityTab.ARMY }
        }
    }
}

// ====================================================================== Köy

@Composable
private fun VillageTab(data: AppData, now: Long, onStudy: () -> Unit) {
    val g = data.game
    var selectedId by remember { mutableStateOf<String?>(null) }
    var placing by remember { mutableStateOf<Placing?>(null) }
    var shop by remember { mutableStateOf(false) }
    val compact = LocalLayout.current.compact
    val selected = g.buildings.find { it.id == selectedId }
    val ghost = placing?.let { Ghost(it.type, it.x, it.y, Game.canPlace(g, it.type.size, it.x, it.y, it.moveId)) }

    val onTap: (Int, Int) -> Unit = { tx, ty ->
        val p = placing
        if (p != null) {
            placing = p.copy(x = tx - p.type.size / 2, y = ty - p.type.size / 2)
        } else {
            selectedId = g.buildings.find { tx >= it.x && tx < it.x + it.type.size && ty >= it.y && ty < it.y + it.type.size }?.id
        }
    }

    val panel: (@Composable ColumnScope.() -> Unit)? = when {
        placing != null -> { { PlacementPanel(data, placing!!, ghost?.ok == true) { placing = null } } }
        selected != null -> { { BuildingPanel(data, selected, now, onStudy, onMove = { placing = Placing(selected.type, selected.x, selected.y, selected.id) }) { selectedId = null } } }
        else -> null
    }

    val map: @Composable (Modifier) -> Unit = { mod ->
        Box(mod.clip(RoundedCornerShape(16.dp))) {
            VillageView(g, now, selectedId, ghost, onTap, Modifier.fillMaxSize())
            Row(
                Modifier.align(Alignment.TopEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(onClick = { shop = true; placing = null }) { Text("🔨 İnşa Et") }
            }
            if (g.buildings.any { it.upgrade != null } || g.research != null) {
                Surface(
                    Modifier.align(Alignment.BottomStart).padding(8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = panelColor(),
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🏗️ İnşaatlar ders çalıştıkça ilerler", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(6.dp))
                        TextButton(onClick = onStudy, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("▶ Çalış") }
                    }
                }
            }
        }
    }

    if (!compact) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            map(Modifier.weight(1f).fillMaxHeight())
            if (panel != null) {
                Panel(Modifier.width(300.dp).fillMaxHeight()) {
                    Column(Modifier.verticalScroll(rememberScrollState()), content = panel)
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            map(Modifier.weight(1f).fillMaxWidth())
            if (panel != null) {
                Panel(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    Column(Modifier.verticalScroll(rememberScrollState()), content = panel)
                }
            }
        }
    }

    if (shop) {
        ShopDialog(data, onDismiss = { shop = false }) { type ->
            shop = false
            selectedId = null
            val spot = Game.findFreeSpot(g, type.size)
            if (spot == null) Repo.toast("Köyde yer kalmadı") else placing = Placing(type, spot.first, spot.second, null)
        }
    }
}

@Composable
private fun SpriteIcon(type: BType, size: Int = 44) {
    val sp = rememberSprites()
    Image(sp.building(type), contentDescription = type.title, modifier = Modifier.size(size.dp))
}

@Composable
private fun PlacementPanel(data: AppData, p: Placing, ok: Boolean, onDone: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SpriteIcon(p.type)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(if (p.moveId == null) "${p.type.title} kur" else "${p.type.title} taşı", fontWeight = FontWeight.Bold)
            Text("Haritada dokunduğun yere taşınır.", style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(8.dp))
    if (p.moveId == null) {
        val cost = GameCatalog.cost(p.type, 1)
        val study = GameCatalog.studyMinutes(p.type, 1)
        Text("💰 $cost 🪙" + if (study > 0) "   📖 $study dk ders" else "   ⚡ anında", fontWeight = FontWeight.SemiBold)
    }
    if (!ok) Text("Buraya sığmıyor", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = ok, onClick = {
            val msg = if (p.moveId == null) Game.build(p.type, p.x, p.y) else Game.move(p.moveId, p.x, p.y)
            msg?.let(Repo::toast)
            onDone()
        }) { Text("✓ Buraya") }
        OutlinedButton(onClick = onDone) { Text("✕ Vazgeç") }
    }
}

@Composable
private fun BuildingPanel(
    data: AppData,
    b: GBuilding,
    now: Long,
    onStudy: () -> Unit,
    onMove: () -> Unit,
    onClose: () -> Unit,
) {
    val g = data.game
    val cs = MaterialTheme.colorScheme
    val th = Game.th(g)
    Row(verticalAlignment = Alignment.CenterVertically) {
        SpriteIcon(b.type, 52)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(b.type.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    b.type.cat == BCat.DECOR -> "Süs"
                    b.level == 0 -> "İnşa ediliyor"
                    else -> "Seviye ${b.level} / ${b.type.maxLevel}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClose) { Text("✕") }
    }
    Text(b.type.description, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(6.dp))
    statLines(g, b.type, b.level.coerceAtLeast(1), now, b).forEach {
        Text(it, style = MaterialTheme.typography.bodyMedium)
    }
    if (b.type == BType.GOLD_MINE && b.level >= 1 && b.upgrade == null) {
        Button(onClick = { Game.collect(b.id)?.let(Repo::toast) }, modifier = Modifier.padding(top = 6.dp)) {
            Text("⛏️ Topla (+${Game.mineStored(b, now)} 🪙)")
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))

    val u = b.upgrade
    if (u != null) {
        val done = u.progressMs / MINUTE_MS
        val total = u.requiredMs / MINUTE_MS
        Text(if (b.level == 0) "🏗️ İnşa ediliyor" else "🔨 Seviye ${u.toLevel}'e yükseltiliyor", fontWeight = FontWeight.SemiBold)
        LinearProgressIndicator(
            progress = { (u.progressMs.toFloat() / u.requiredMs).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        Text("📖 $done / $total dk ders çalışıldı", style = MaterialTheme.typography.bodySmall)
        Text(
            "Kalan ${total - done} dk. İnşaatçılar sadece sen ders çalışırken çalışır!",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            Button(onClick = onStudy) { Text("▶ Çalışmaya başla") }
            TextButton(onClick = { Game.cancel(b.id)?.let(Repo::toast) }) { Text("İptal (½ iade)") }
        }
    } else if (b.type.cat != BCat.DECOR && b.type != BType.BUILDER_HUT && b.level < b.type.maxLevel) {
        val to = b.level + 1
        val cost = GameCatalog.cost(b.type, to)
        val study = GameCatalog.studyMinutes(b.type, to)
        Text("Seviye $to'e yükselt", fontWeight = FontWeight.SemiBold)
        Text("💰 $cost 🪙   📖 $study dk ders", style = MaterialTheme.typography.bodyMedium)
        statLines(g, b.type, to, now, null).forEach {
            Text("→ $it", style = MaterialTheme.typography.bodySmall, color = cs.secondary)
        }
        val block = when {
            to > GameCatalog.maxLevelAt(b.type, th) -> "🔒 Önce Merkez Kale'yi yükselt"
            Game.freeBuilders(g) <= 0 -> "👷 Boş inşaatçı yok"
            data.coins < cost -> "💰 ${cost - data.coins} 🪙 daha lazım"
            else -> null
        }
        if (block != null) Text(block, color = cs.error, style = MaterialTheme.typography.bodySmall)
        Button(
            enabled = block == null,
            onClick = { Game.upgrade(b.id)?.let(Repo::toast) },
            modifier = Modifier.padding(top = 6.dp),
        ) { Text("🔨 Yükselt") }
    } else if (b.type.cat != BCat.DECOR && b.type != BType.BUILDER_HUT) {
        Text("🏆 En yüksek seviyede", fontWeight = FontWeight.SemiBold)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        OutlinedButton(onClick = onMove) { Text("↔ Taşı") }
        if (b.type.cat == BCat.DECOR) {
            OutlinedButton(onClick = { Game.removeDecor(b.id)?.let(Repo::toast); onClose() }) { Text("🗑 Kaldır") }
        }
    }
}

private fun statLines(g: GameState, t: BType, level: Int, now: Long, b: GBuilding?): List<String> = when (t) {
    BType.TOWN_HALL -> listOf("Diğer binalar en fazla Sv ${level + 1} olabilir", "Bina sınırlarını artırır")
    BType.BUILDER_HUT -> listOf("Toplam inşaatçı: ${g.builders}")
    BType.ARMY_CAMP -> listOf("🏕️ Ordu kapasitesi: ${GameCatalog.armyCapacity(level)}")
    BType.BARRACKS -> listOf("⚔️ Açtığı asker: ${TType.entries.filter { it.barracksLevel <= level }.joinToString { it.title }}")
    BType.ACADEMY -> listOf("📚 Asker seviye sınırı: ${GameCatalog.maxTroopLevel(level)}")
    BType.ARCHER_TOWER, BType.CANNON, BType.MAGIC_TOWER -> {
        val d = GameCatalog.defense(t, level)!!
        listOf("🎯 Hasar: ${d.dps.roundToInt()}/sn • Menzil: ${d.range}", "❤️ Can: ${GameCatalog.hp(t, level).roundToInt()}")
    }
    BType.LIBRARY -> listOf("📖 Ders parası bonusu: +%${GameCatalog.libraryBonusPercent(level)}")
    BType.GOLD_MINE -> {
        val rate = GameCatalog.mineRatePerHour(level)
        val stored = if (b != null) " • Depoda: ${Game.mineStored(b, now)}" else ""
        listOf("⛏️ Üretim: $rate 🪙/saat (en fazla ${rate * GameCatalog.MINE_CAP_HOURS})$stored")
    }
    else -> emptyList()
}

@Composable
private fun ShopDialog(data: AppData, onDismiss: () -> Unit, onPick: (BType) -> Unit) {
    val g = data.game
    val th = Game.th(g)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔨 İnşa Et") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // İnşaatçı satın alma
                val nextBuilder = g.builders + 1
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SpriteIcon(BType.BUILDER_HUT, 40)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("👷 Yeni inşaatçı (${g.builders}/${GameCatalog.MAX_BUILDERS})", fontWeight = FontWeight.SemiBold)
                            Text("Aynı anda bir inşaat daha yürütür", style = MaterialTheme.typography.labelSmall)
                        }
                        if (g.builders < GameCatalog.MAX_BUILDERS) {
                            Button(
                                onClick = { Game.buyBuilder()?.let(Repo::toast) },
                                enabled = data.coins >= GameCatalog.BUILDER_COST[nextBuilder],
                                contentPadding = PaddingValues(horizontal = 10.dp),
                            ) { Text("${GameCatalog.BUILDER_COST[nextBuilder]} 🪙") }
                        }
                    }
                }
                BCat.entries.filter { it != BCat.CORE }.forEach { cat ->
                    Text(cat.title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    BType.entries.filter { it.cat == cat }.forEach { t ->
                        val block = Game.buildBlockReason(g, t, data.coins, data.bestDailyStreak)
                        val limit = GameCatalog.limit(t, th)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().clickable(enabled = block == null) { onPick(t) },
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { SpriteIcon(t, 40) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        t.title + if (cat != BCat.DECOR) "  (${Game.count(g, t)}/$limit)" else "",
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val study = GameCatalog.studyMinutes(t, 1)
                                    Text(
                                        "💰 ${GameCatalog.cost(t, 1)}" + if (study > 0) "  📖 $study dk" else "  ⚡ anında",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    if (block != null) Text(block, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}

// ====================================================================== Ordu

@Composable
private fun ArmyTab(data: AppData, now: Long) {
    val g = data.game
    val sp = rememberSprites()
    val cap = Game.capacity(g)
    val used = Game.armyHousing(g) + Game.queueHousing(g)

    AdaptiveColumns(
        Pane(1f) {
            Panel(Modifier.fillMaxWidth(), title = "🏕️ Ordun") {
                if (cap == 0) {
                    Text("Asker eğitmek için önce Köy sekmesinden Ordugâh ve Kışla kur.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Kapasite: ${Game.armyHousing(g)} + ${Game.queueHousing(g)} eğitimde / $cap")
                    LinearProgressIndicator(
                        progress = { (used.toFloat() / cap).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                }
                if (g.army.isEmpty()) Text("Henüz hazır asker yok.", style = MaterialTheme.typography.bodySmall)
                g.army.entries.sortedBy { it.key.ordinal }.forEach { (t, n) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Image(sp.troop(t), t.title, Modifier.size(32.dp), filterQuality = FilterQuality.None)
                        Spacer(Modifier.width(8.dp))
                        Text("${t.title} (Sv ${Game.troopLevel(g, t)})", modifier = Modifier.weight(1f))
                        Text("×$n", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Panel(Modifier.fillMaxWidth(), title = "⏳ Eğitim") {
                val bc = Game.barracksCount(g)
                Text(
                    if (bc > 1) "$bc kışla birlikte çalışıyor (×$bc hız)" else "Daha fazla kışla eğitimi hızlandırır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (g.queue.isEmpty()) Text("Kuyruk boş.", style = MaterialTheme.typography.bodySmall)
                g.queue.forEachIndexed { i, job ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Image(sp.troop(job.troop), job.troop.title, Modifier.size(28.dp), filterQuality = FilterQuality.None)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${job.troop.title} ×${job.count}")
                            if (i == 0) {
                                val total = Game.trainMs(g, job.troop)
                                val p = ((now - g.queueStartedAt).toFloat() / total).coerceIn(0f, 1f)
                                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        TextButton(onClick = { Game.cancelTrain(i) }) { Text("✕") }
                    }
                }
            }
        },
        Pane(1.5f) {
            Panel(Modifier.fillMaxWidth(), title = "⚔️ Asker Eğit") {
                ResponsiveGrid(TType.entries, minItemWidth = 210.dp) { t, mod -> TroopCard(data, t, mod) }
            }
            Panel(Modifier.fillMaxWidth(), title = "📚 Akademi") { AcademySection(data) }
        },
    )
}

@Composable
private fun TroopCard(data: AppData, t: TType, modifier: Modifier) {
    val g = data.game
    val sp = rememberSprites()
    val lvl = Game.troopLevel(g, t)
    val mult = GameCatalog.troopMultiplier(lvl)
    val locked = Game.barracksLevel(g) < t.barracksLevel
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(sp.troop(t), t.title, Modifier.size(44.dp), filterQuality = FilterQuality.None, alpha = if (locked) 0.4f else 1f)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("${t.title}  Sv $lvl", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.description, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            val power = if (t == TType.HEALER) "💚 ${(t.dps * mult).roundToInt()}/sn" else "⚔️ ${(t.dps * mult).roundToInt()}/sn"
            Text(
                "❤️ ${(t.hp * mult).roundToInt()}  $power  🏠 ${t.housing}  ⏱ ${t.trainSec}sn",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            if (locked) {
                Text("🔒 Kışla Sv ${t.barracksLevel} gerekli", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${t.cost} 🪙", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { Game.train(t, 1)?.let(Repo::toast) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+1") }
                    FilledTonalButton(onClick = { Game.train(t, 5)?.let(Repo::toast) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+5") }
                }
            }
        }
    }
}

@Composable
private fun AcademySection(data: AppData) {
    val g = data.game
    val ac = Game.academyLevel(g)
    if (ac < 1) {
        Text("Askerlerini güçlendirmek için Akademi kur (Merkez Kale 3).", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val r = g.research
    if (r != null) {
        Text("📚 ${r.troop.title} → Sv ${r.toLevel}", fontWeight = FontWeight.SemiBold)
        LinearProgressIndicator(
            progress = { (r.progressMs.toFloat() / r.requiredMs).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        Text("📖 ${r.progressMs / MINUTE_MS} / ${r.requiredMs / MINUTE_MS} dk ders (araştırmalar da ders çalışırken ilerler)", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
    }
    val maxLvl = GameCatalog.maxTroopLevel(ac)
    TType.entries.filter { Game.barracksLevel(g) >= it.barracksLevel }.forEach { t ->
        val lvl = Game.troopLevel(g, t)
        LabeledControl(
            "${t.title} Sv $lvl",
            hint = if (lvl >= maxLvl) "Akademi sınırında" else "→ Sv ${lvl + 1}: ${GameCatalog.researchCost(t, lvl + 1)} 🪙 • ${GameCatalog.researchStudyMinutes(t, lvl + 1)} dk ders",
        ) {
            FilledTonalButton(
                enabled = r == null && lvl < maxLvl && data.coins >= GameCatalog.researchCost(t, lvl + 1),
                onClick = { Game.research(t)?.let(Repo::toast) },
            ) { Text("Araştır") }
        }
    }
}

// ====================================================================== Seferler

@Composable
private fun CampaignTab(data: AppData, onAttack: (Int) -> Unit, onTrain: () -> Unit) {
    val g = data.game
    val armySize = Game.armySize(g)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(230.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🗺️ Seferler", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Ordun: $armySize asker • Toplam ⭐ ${Game.stars(g)} • Zafer: ${g.battlesWon}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "%50 yıkım = ⭐, Merkez Kale = ⭐, %100 = ⭐. Yeni yıldızlar bonus altın verir.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (armySize == 0) OutlinedButton(onClick = onTrain) { Text("Asker eğit") }
                }
            }
        }
        items((1..Campaign.LEVELS).toList(), key = { it }) { lvl ->
            val v = remember(lvl) { Campaign.village(lvl) }
            val stars = g.stars[lvl] ?: 0
            val unlocked = Game.levelUnlocked(g, lvl)
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$lvl. ${v.name}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("🏰 Sv ${v.th} • 🗼 ${v.defenses} kule • 🏠 ${v.buildings.size} bina", style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        "⭐".repeat(stars) + "☆".repeat(3 - stars),
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 16.sp,
                    )
                }
                val loot = if (stars >= 3) (v.loot * Campaign.REPLAY_LOOT_FACTOR).roundToInt() else v.loot
                Text(
                    "💰 Ganimet: $loot 🪙" + if (stars < 3) "  + ⭐ başına ${Campaign.starBonus(lvl)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                if (!unlocked) {
                    Text("🔒 Önceki köyden en az 1 ⭐ al", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Button(
                        onClick = { onAttack(lvl) },
                        enabled = armySize > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (armySize > 0) "⚔️ Saldır" else "Ordun boş") }
                }
            }
        }
    }
}
