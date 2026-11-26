package com.derscalisma.app.data.game

import com.derscalisma.app.data.Catalog
import com.derscalisma.app.data.MINUTE_MS
import com.derscalisma.app.data.Repo
import com.derscalisma.app.data.Time
import kotlin.math.roundToInt

data class BattleResult(
    val level: Int,
    val stars: Int,
    val destruction: Float,
    val used: Map<TType, Int>,
    val spellsUsed: Map<SType, Int> = emptyMap(),
    /** Komutan savaşa girdiyse kalan can oranı. */
    val heroHpRatio: Float? = null,
)

data class BattleReward(val loot: Int, val starBonus: Int, val newStars: Int, val leagueBonus: Int = 0)

/** Köy oyununun sorguları ve oyuncu işlemleri. */
object Game {

    // ------------------------------------------------------------------ sorgular

    fun th(g: GameState) = g.buildings.find { it.type == BType.TOWN_HALL }?.level ?: 1
    fun count(g: GameState, t: BType) = g.buildings.count { it.type == t }
    fun busyBuilders(g: GameState) = g.buildings.count { it.upgrade != null }
    fun freeBuilders(g: GameState) = (g.builders - busyBuilders(g)).coerceAtLeast(0)
    fun maxBuiltLevel(g: GameState, t: BType) = g.buildings.filter { it.type == t }.maxOfOrNull { it.level } ?: 0
    fun barracksLevel(g: GameState) = maxBuiltLevel(g, BType.BARRACKS)
    fun barracksCount(g: GameState) = g.buildings.count { it.type == BType.BARRACKS && it.level >= 1 }
    fun academyLevel(g: GameState) = maxBuiltLevel(g, BType.ACADEMY)
    fun troopLevel(g: GameState, t: TType) = g.troopLevels[t] ?: 1

    fun capacity(g: GameState) =
        g.buildings.filter { it.type == BType.ARMY_CAMP }.sumOf { GameCatalog.armyCapacity(it.level) }

    fun armyHousing(g: GameState) = g.army.entries.sumOf { it.key.housing * it.value }
    fun queueHousing(g: GameState) = g.queue.sumOf { it.troop.housing * it.count }
    fun armySize(g: GameState) = g.army.values.sum()

    fun libraryBonus(g: GameState) = GameCatalog.libraryBonusPercent(maxBuiltLevel(g, BType.LIBRARY))

    fun trainMs(g: GameState, t: TType) = t.trainSec * 1000L / barracksCount(g).coerceAtLeast(1)

    fun mineStored(b: GBuilding, now: Long): Int {
        if (b.type != BType.GOLD_MINE || b.level < 1 || b.upgrade != null) return 0
        val rate = GameCatalog.mineRatePerHour(b.level)
        val hours = (now - b.collectedAt).coerceAtLeast(0) / 3_600_000.0
        return minOf((rate * hours).toInt(), rate * GameCatalog.MINE_CAP_HOURS)
    }

    fun defensePower(g: GameState): Float = g.buildings.sumOf { b ->
        when {
            b.level < 1 -> 0.0
            b.type == BType.WALL -> 0.5 * b.level
            else -> (GameCatalog.defense(b.type, b.level)?.dps ?: 0f).toDouble()
        }
    }.toFloat()

    // ---- Komutan ve büyüler

    fun heroLevel(g: GameState) = g.buildings.find { it.type == BType.HERO_ALTAR && it.level >= 1 }?.level ?: 0

    /** Komutan savaşa hazır mı? (Kürsü var, yükseltilmiyor, dinlenmesi bitti) */
    fun heroReady(g: GameState, now: Long): Boolean {
        val altar = g.buildings.find { it.type == BType.HERO_ALTAR } ?: return false
        return altar.level >= 1 && altar.upgrade == null && now >= g.heroReadyAt
    }

    fun forgeLevel(g: GameState) = maxBuiltLevel(g, BType.SPELL_FORGE)
    fun spellCapacity(g: GameState) = GameCatalog.spellCapacity(forgeLevel(g))
    fun spellCount(g: GameState) = g.spells.values.sum() + g.spellQueue.sumOf { it.count }
    fun league(g: GameState) = League.of(stars(g))

    fun raidStrength(g: GameState, streak: Int): Float = 6f * th(g) + streak.coerceAtMost(25)

    fun stars(g: GameState) = g.stars.values.sum()

    fun levelUnlocked(g: GameState, level: Int) = level == 1 || (g.stars[level - 1] ?: 0) > 0

    fun canPlace(g: GameState, size: Int, x: Int, y: Int, ignoreId: String? = null): Boolean {
        if (x < 1 || y < 1 || x + size > VILLAGE_SIZE - 1 || y + size > VILLAGE_SIZE - 1) return false
        return g.buildings.none { b ->
            b.id != ignoreId &&
                x < b.x + b.type.size && b.x < x + size &&
                y < b.y + b.type.size && b.y < y + size
        }
    }

    fun findFreeSpot(g: GameState, size: Int): Pair<Int, Int>? {
        val c = VILLAGE_SIZE / 2
        for (r in 0 until VILLAGE_SIZE) {
            for (dx in -r..r) for (dy in -r..r) {
                if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != r) continue
                val x = c + dx - size / 2
                val y = c + dy - size / 2
                if (canPlace(g, size, x, y)) return x to y
            }
        }
        return null
    }

    /** Bu bina türü şu an yapılabilir mi? Değilse sebebi. */
    fun buildBlockReason(g: GameState, t: BType, coins: Int, bestStreak: Int): String? {
        val th = th(g)
        if (t.cat == BCat.DECOR) {
            val need = GameCatalog.DECOR_UNLOCK[t]
            if (need != null && bestStreak < need) return "🔒 $need günlük seri gerekli"
            if (g.buildings.count { it.type.cat == BCat.DECOR } >= GameCatalog.DECOR_LIMIT) return "Süs sınırına ulaştın"
        } else {
            if (count(g, t) >= GameCatalog.limit(t, th)) {
                return GameCatalog.unlockHint(t, th)?.let { "🔒 $it" } ?: "En fazla sayıya ulaştın"
            }
            if (freeBuilders(g) <= 0 && GameCatalog.studyMinutes(t, 1) > 0) return "Boş inşaatçı yok"
        }
        val cost = GameCatalog.cost(t, 1)
        if (coins < cost) return "Yetersiz altın"
        return null
    }

    // ------------------------------------------------------------------ işlemler

    fun build(t: BType, x: Int, y: Int): String? = Repo.mutate {
        val g = data.game
        Game.buildBlockReason(g, t, data.coins, data.bestDailyStreak)?.let { return@mutate it }
        if (!canPlace(g, t.size, x, y)) return@mutate "Buraya sığmıyor"
        val cost = GameCatalog.cost(t, 1)
        val study = GameCatalog.studyMinutes(t, 1)
        val b = if (t.cat == BCat.DECOR || study == 0) {
            GBuilding(Time.newId(), t, 1, x, y, collectedAt = now)
        } else {
            GBuilding(Time.newId(), t, 0, x, y, upgrade = Upgrade(1, study * MINUTE_MS), collectedAt = now)
        }
        data = data.copy(coins = data.coins - cost, game = g.copy(buildings = g.buildings + b))
        if (b.upgrade != null) "🏗️ ${t.title} inşaatı başladı: $study dk ders çalışınca bitecek" else null
    }

    fun upgrade(id: String): String? = Repo.mutate {
        val g = data.game
        val b = g.buildings.find { it.id == id } ?: return@mutate null
        if (b.upgrade != null) return@mutate "Zaten yükseltiliyor"
        val to = b.level + 1
        if (to > b.type.maxLevel) return@mutate "En yüksek seviyede"
        if (to > GameCatalog.maxLevelAt(b.type, th(g))) return@mutate "🔒 Önce Merkez Kale'yi yükselt"
        val cost = GameCatalog.cost(b.type, to)
        if (data.coins < cost) return@mutate "Yetersiz altın: ${cost - data.coins} 🪙 daha lazım"
        if (b.type == BType.WALL) {
            data = data.copy(coins = data.coins - cost, game = g.copy(buildings = g.buildings.map { if (it.id == id) it.copy(level = to) else it }))
            return@mutate null
        }
        if (freeBuilders(g) <= 0) return@mutate "Boş inşaatçı yok"
        val stored = mineStored(b, now)
        val nb = b.copy(upgrade = Upgrade(to, GameCatalog.studyMinutes(b.type, to) * MINUTE_MS))
        data = data.copy(
            coins = data.coins - cost + stored,
            game = g.copy(buildings = g.buildings.map { if (it.id == id) nb else it }),
        )
        "🔨 ${b.type.title} seviye $to yükseltmesi başladı"
    }

    fun cancel(id: String): String? = Repo.mutate {
        val g = data.game
        val b = g.buildings.find { it.id == id } ?: return@mutate null
        val u = b.upgrade ?: return@mutate null
        val refund = GameCatalog.cost(b.type, u.toLevel) / 2
        val list = if (b.level == 0) g.buildings.filterNot { it.id == id }
        else g.buildings.map { if (it.id == id) it.copy(upgrade = null, collectedAt = now) else it }
        data = data.copy(coins = data.coins + refund, game = g.copy(buildings = list))
        "İptal edildi, $refund 🪙 iade"
    }

    fun move(id: String, x: Int, y: Int): String? = Repo.mutate {
        val g = data.game
        val b = g.buildings.find { it.id == id } ?: return@mutate null
        if (!canPlace(g, b.type.size, x, y, ignoreId = id)) return@mutate "Buraya sığmıyor"
        data = data.copy(game = g.copy(buildings = g.buildings.map { if (it.id == id) it.copy(x = x, y = y) else it }))
        null
    }

    fun removeDecor(id: String): String? = Repo.mutate {
        val g = data.game
        val b = g.buildings.find { it.id == id } ?: return@mutate null
        if (b.type.cat != BCat.DECOR) return@mutate null
        val refund = GameCatalog.cost(b.type, 1) / 2
        data = data.copy(coins = data.coins + refund, game = g.copy(buildings = g.buildings.filterNot { it.id == id }))
        "Kaldırıldı, $refund 🪙 iade"
    }

    fun collect(id: String): String? = Repo.mutate {
        val g = data.game
        val b = g.buildings.find { it.id == id } ?: return@mutate null
        val amount = mineStored(b, now)
        if (amount <= 0) return@mutate "Henüz toplanacak altın yok"
        data = data.copy(
            coins = data.coins + amount,
            totalEarned = data.totalEarned + amount,
            game = g.copy(buildings = g.buildings.map { if (it.id == id) it.copy(collectedAt = now) else it }),
        )
        "⛏️ +$amount 🪙 toplandı"
    }

    fun buyBuilder(): String? = Repo.mutate {
        val g = data.game
        if (g.builders >= GameCatalog.MAX_BUILDERS) return@mutate "En fazla ${GameCatalog.MAX_BUILDERS} inşaatçı olabilir"
        val cost = GameCatalog.BUILDER_COST[g.builders + 1]
        if (data.coins < cost) return@mutate "Yetersiz altın: ${cost - data.coins} 🪙 daha lazım"
        val spot = findFreeSpot(g, BType.BUILDER_HUT.size) ?: return@mutate "Köyde yer kalmadı"
        val hut = GBuilding(Time.newId(), BType.BUILDER_HUT, 1, spot.first, spot.second)
        data = data.copy(coins = data.coins - cost, game = g.copy(builders = g.builders + 1, buildings = g.buildings + hut))
        "👷 Yeni inşaatçı işe başladı!"
    }

    fun train(t: TType, n: Int): String? = Repo.mutate {
        processTraining()
        val g = data.game
        if (barracksLevel(g) < t.barracksLevel) return@mutate "🔒 Kışla seviye ${t.barracksLevel} gerekli"
        val free = capacity(g) - armyHousing(g) - queueHousing(g)
        val count = minOf(n, free / t.housing)
        if (count <= 0) return@mutate "Ordugâh dolu"
        val cost = t.cost * count
        if (data.coins < cost) return@mutate "Yetersiz altın"
        val q = g.queue.toMutableList()
        val last = q.lastOrNull()
        if (last != null && last.troop == t) q[q.size - 1] = last.copy(count = last.count + count) else q += TrainJob(t, count)
        data = data.copy(
            coins = data.coins - cost,
            game = g.copy(queue = q, queueStartedAt = if (g.queue.isEmpty()) now else g.queueStartedAt),
        )
        null
    }

    fun cancelTrain(index: Int): String? = Repo.mutate {
        processTraining()
        val g = data.game
        val job = g.queue.getOrNull(index) ?: return@mutate null
        val q = g.queue.toMutableList().also { it.removeAt(index) }
        data = data.copy(
            coins = data.coins + job.troop.cost * job.count,
            game = g.copy(queue = q, queueStartedAt = if (index == 0) now else g.queueStartedAt),
        )
        null
    }

    fun research(t: TType): String? = Repo.mutate {
        val g = data.game
        val ac = academyLevel(g)
        if (ac < 1) return@mutate "🔒 Önce Akademi kur"
        if (g.research != null) return@mutate "Akademi şu an başka bir araştırma yapıyor"
        val to = troopLevel(g, t) + 1
        if (to > GameCatalog.maxTroopLevel(ac)) return@mutate "🔒 Akademi'yi yükselt"
        val cost = GameCatalog.researchCost(t, to)
        if (data.coins < cost) return@mutate "Yetersiz altın"
        data = data.copy(
            coins = data.coins - cost,
            game = g.copy(research = Research(t, to, GameCatalog.researchStudyMinutes(t, to) * MINUTE_MS)),
        )
        "📚 ${t.title} seviye $to araştırması başladı"
    }

    /** Aynı seviyedeki tüm surları paranın yettiği kadar bir seviye yükseltir. */
    fun upgradeWalls(level: Int): String? = Repo.mutate {
        val g = data.game
        val to = level + 1
        if (to > GameCatalog.maxLevelAt(BType.WALL, th(g))) return@mutate "🔒 Önce Merkez Kale'yi yükselt"
        val cost = GameCatalog.cost(BType.WALL, to)
        var coins = data.coins
        var n = 0
        val list = g.buildings.map { b ->
            if (b.type == BType.WALL && b.level == level && coins >= cost) {
                coins -= cost
                n++
                b.copy(level = to)
            } else b
        }
        if (n == 0) return@mutate "Yetersiz altın"
        data = data.copy(coins = coins, game = g.copy(buildings = list))
        "🧱 $n sur seviye $to oldu"
    }

    fun brew(s: SType, n: Int): String? = Repo.mutate {
        processSpells()
        val g = data.game
        if (forgeLevel(g) < s.forgeLevel) return@mutate "🔒 Büyü Atölyesi Sv ${s.forgeLevel} gerekli"
        val count = minOf(n, spellCapacity(g) - spellCount(g))
        if (count <= 0) return@mutate "Büyü kapasitesi dolu"
        if (data.coins < s.cost * count) return@mutate "Yetersiz altın"
        val q = g.spellQueue.toMutableList()
        val last = q.lastOrNull()
        if (last != null && last.spell == s) q[q.size - 1] = last.copy(count = last.count + count) else q += SpellJob(s, count)
        data = data.copy(
            coins = data.coins - s.cost * count,
            game = g.copy(spellQueue = q, spellQueueStartedAt = if (g.spellQueue.isEmpty()) now else g.spellQueueStartedAt),
        )
        null
    }

    fun cancelBrew(index: Int): String? = Repo.mutate {
        processSpells()
        val g = data.game
        val job = g.spellQueue.getOrNull(index) ?: return@mutate null
        val q = g.spellQueue.toMutableList().also { it.removeAt(index) }
        data = data.copy(
            coins = data.coins + job.spell.cost * job.count,
            game = g.copy(spellQueue = q, spellQueueStartedAt = if (index == 0) now else g.spellQueueStartedAt),
        )
        null
    }

    fun claimQuest(id: String): String? = Repo.mutate {
        val st = Quests.status(data).find { it.quest.id == id } ?: return@mutate null
        if (!st.claimable) return@mutate null
        val g = data.game
        data = data.copy(
            coins = data.coins + st.reward,
            totalEarned = data.totalEarned + st.reward,
            game = g.copy(claimed = g.claimed + (id to st.claimedTiers + 1)),
        )
        log("${st.quest.emoji} Görev: ${st.quest.title} (${st.target} ${st.quest.unit})", st.reward)
        "${st.quest.emoji} ${st.quest.title} ödülü: +${st.reward} 🪙"
    }

    /** Eğitim ve büyü kuyruklarını günceller (ekranlar açıkken çağrılır). */
    fun refresh() = Repo.mutate {
        processTraining()
        processSpells()
    }

    fun finishBattle(r: BattleResult): BattleReward = Repo.mutate {
        val g = data.game
        val village = Campaign.village(r.level)
        val army = g.army.toMutableMap()
        r.used.forEach { (t, n) ->
            val left = (army[t] ?: 0) - n
            if (left > 0) army[t] = left else army.remove(t)
        }
        val spells = g.spells.toMutableMap()
        r.spellsUsed.forEach { (s, n) ->
            val left = (spells[s] ?: 0) - n
            if (left > 0) spells[s] = left else spells.remove(s)
        }
        val heroReadyAt = r.heroHpRatio?.let { now + (GameCatalog.HERO_FULL_REGEN_MS * (1f - it.coerceIn(0f, 1f))).toLong() } ?: g.heroReadyAt
        val prev = g.stars[r.level] ?: 0
        val factor = if (prev >= 3) Campaign.REPLAY_LOOT_FACTOR else 1f
        val baseLoot = (village.loot * r.destruction * factor).roundToInt()
        val leagueBonus = baseLoot * League.of(stars(g)).lootBonus / 100
        val loot = baseLoot + leagueBonus
        val newStars = (r.stars - prev).coerceAtLeast(0)
        val bonus = newStars * Campaign.starBonus(r.level)
        val total = loot + bonus
        data = data.copy(
            coins = data.coins + total,
            totalEarned = data.totalEarned + total,
            game = g.copy(
                army = army,
                spells = spells,
                heroReadyAt = heroReadyAt,
                stars = g.stars + (r.level to maxOf(prev, r.stars)),
                battlesWon = g.battlesWon + if (r.stars > 0) 1 else 0,
            ),
        )
        log("⚔️ ${village.name}: ${"⭐".repeat(r.stars)} %${(r.destruction * 100).roundToInt()} yıkım", total)
        BattleReward(loot, bonus, newStars, leagueBonus)
    }
}

// ---------------------------------------------------------------------- Repo içinden çağrılanlar

/** Ders çalışılan süre kadar tüm inşaatları ve araştırmayı ilerletir. */
internal fun Repo.Tx.advanceGame(ms: Long) {
    if (ms <= 0) return
    val g = data.game
    if (g.buildings.none { it.upgrade != null } && g.research == null) return
    val done = mutableListOf<String>()
    val list = g.buildings.map { b ->
        val u = b.upgrade ?: return@map b
        val p = u.progressMs + ms
        if (p >= u.requiredMs) {
            done += if (b.level == 0) "🏗️ ${b.type.title} inşa edildi!" else "🏗️ ${b.type.title} seviye ${u.toLevel} oldu!"
            b.copy(level = u.toLevel, upgrade = null, collectedAt = if (b.type == BType.GOLD_MINE) now else b.collectedAt)
        } else {
            b.copy(upgrade = u.copy(progressMs = p))
        }
    }
    var research = g.research
    var levels = g.troopLevels
    research?.let { r ->
        val p = r.progressMs + ms
        if (p >= r.requiredMs) {
            levels = levels + (r.troop to r.toLevel)
            done += "📚 ${r.troop.title} seviye ${r.toLevel} oldu!"
            research = null
        } else {
            research = r.copy(progressMs = p)
        }
    }
    data = data.copy(game = g.copy(buildings = list, research = research, troopLevels = levels))
    done.forEach {
        log(it)
        notices += it
    }
}

internal fun Repo.Tx.processTraining() {
    val g = data.game
    if (g.queue.isEmpty()) return
    var start = g.queueStartedAt
    val army = g.army.toMutableMap()
    val queue = g.queue.toMutableList()
    var changed = false
    var trained = 0
    while (queue.isNotEmpty()) {
        val head = queue[0]
        val end = start + Game.trainMs(g, head.troop)
        if (now < end) break
        army[head.troop] = (army[head.troop] ?: 0) + 1
        trained++
        start = end
        if (head.count <= 1) queue.removeAt(0) else queue[0] = head.copy(count = head.count - 1)
        changed = true
    }
    if (changed) {
        data = data.copy(
            game = g.copy(
                army = army,
                queue = queue,
                queueStartedAt = if (queue.isEmpty()) 0 else start,
                troopsTrained = g.troopsTrained + trained,
            )
        )
    }
}

internal fun Repo.Tx.processSpells() {
    val g = data.game
    if (g.spellQueue.isEmpty()) return
    var start = g.spellQueueStartedAt
    val spells = g.spells.toMutableMap()
    val queue = g.spellQueue.toMutableList()
    var changed = false
    while (queue.isNotEmpty()) {
        val head = queue[0]
        val end = start + head.spell.brewSec * 1000L
        if (now < end) break
        spells[head.spell] = (spells[head.spell] ?: 0) + 1
        start = end
        if (head.count <= 1) queue.removeAt(0) else queue[0] = head.copy(count = head.count - 1)
        changed = true
    }
    if (changed) {
        data = data.copy(game = g.copy(spells = spells, spellQueue = queue, spellQueueStartedAt = if (queue.isEmpty()) 0 else start))
    }
}

/** Günlük seri bozulunca köye baskın: savunma yeterliyse püskürtülür, değilse altın kaybedilir. */
internal fun Repo.Tx.raidVillage(prevStreak: Int) {
    val g = data.game
    val defense = Game.defensePower(g)
    val attack = Game.raidStrength(g, prevStreak)
    if (defense >= attack) {
        data = data.copy(game = g.copy(raidsDefended = g.raidsDefended + 1))
        log("🛡️ Baskın püskürtüldü")
        alerts += "🛡️ Baskın püskürtüldü!" to
            "Serin bozulunca köyüne baskın yapıldı ama kulelerin (güç ${defense.roundToInt()}) saldırganları (güç ${attack.roundToInt()}) durdurdu."
    } else {
        val loss = minOf((data.coins * 0.08f).roundToInt(), 60 * Game.th(g))
        data = data.copy(coins = data.coins - loss, game = g.copy(raidsLost = g.raidsLost + 1))
        log("⚔️ Köye baskın: -$loss 🪙", -loss)
        alerts += "⚔️ Köyüne baskın yapıldı!" to
            "Serin bozulduğu için köyün korumasız kaldı ve $loss 🪙 kaybettin. Savunma kuleleri kur, serini koru!"
    }
}

/** v1.0'daki emoji şehrin binalarını iade eder (yeni köy oyunu geldi). */
internal fun Repo.Tx.migrateOldCity() {
    if (data.city.isEmpty()) return
    val refund = data.city.sumOf { Catalog.building(it.buildingId)?.price ?: 0 }
    data = data.copy(city = emptyList(), coins = data.coins + refund)
    log("🏙️ Eski şehir binaları iade edildi", refund)
    notices += "🏰 Yeni köy oyunu geldi! Eski şehir binaların için $refund 🪙 iade edildi."
}
