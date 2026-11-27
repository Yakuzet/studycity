package com.derscalisma.app.data.game

import com.derscalisma.app.audio.Jingle
import com.derscalisma.app.audio.Sfx
import com.derscalisma.app.data.AppData
import com.derscalisma.app.data.MINUTE_MS
import com.derscalisma.app.data.Repo
import com.derscalisma.app.data.Streaks
import com.derscalisma.app.data.Time
import com.derscalisma.app.data.Deadlines
import com.derscalisma.app.data.Pace
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlin.random.Random

/** Haftalık canavar: her hafta yeni biri gelir, her çalışılan dakika ona vurur. */
@Serializable
data class BossState(
    val week: String,
    val kind: Int,
    val maxMs: Long,
    val dealtMs: Long = 0,
    val defeated: Boolean = false,
    /** Köy ekranında son gösterilen hasar (yeni hasarı uçan sayılarla göstermek için). */
    val shownMs: Long = 0,
)

data class BossKind(val name: String, val emoji: String, val taunt: String, val defeatLine: String)

@Serializable
enum class ChestKind(val title: String, val emoji: String) {
    WOOD("Ahşap Sandık", "🧰"),
    SILVER("Gümüş Sandık", "🥈"),
    GOLD("Altın Sandık", "👑"),
}

data class ChestReward(
    val coins: Int,
    val potions: Int,
    val spells: Map<SType, Int>,
    val decor: BType?,
)

object Surprises {
    val bosses = listOf(
        BossKind("Erteleme Canavarı", "👹", "Yarın yaparsın... yarın, yarın...", "Erteleme Canavarı yere serildi! Artık \"yarın\" yok."),
        BossKind("Telefon Cini", "📱", "Sadece bir video daha izle!", "Telefon Cini lambasına geri kaçtı!"),
        BossKind("Uyku Devi", "😴", "Beş dakika daha yat...", "Uyku Devi uyandı ve ormana kaçtı!"),
        BossKind("Dağınıklık Hortlağı", "👻", "Şuna da bir bakıver, bir de şuna...", "Dağınıklık Hortlağı dağıldı gitti!"),
    )

    fun weekKey(d: LocalDate) = "${d.get(IsoFields.WEEK_BASED_YEAR)}-W${d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"

    fun bossKind(b: BossState) = bosses[b.kind % bosses.size]

    /** Sandık açınca çıkanlar; nadir süsler sadece sandıktan gelir. */
    fun rollChest(kind: ChestKind, g: GameState, rnd: Random = Random.Default): ChestReward {
        val coins = when (kind) {
            ChestKind.WOOD -> rnd.nextInt(60, 140)
            ChestKind.SILVER -> rnd.nextInt(180, 380)
            ChestKind.GOLD -> rnd.nextInt(700, 1300)
        }
        val potions = when (kind) {
            ChestKind.WOOD -> if (rnd.nextFloat() < 0.15f) 1 else 0
            ChestKind.SILVER -> if (rnd.nextFloat() < 0.5f) 1 else 0
            ChestKind.GOLD -> 1 + if (rnd.nextFloat() < 0.4f) 1 else 0
        }
        val spells = mutableMapOf<SType, Int>()
        val forge = Game.forgeLevel(g)
        if (forge >= 1 && kind != ChestKind.WOOD && rnd.nextFloat() < 0.6f) {
            val s = SType.entries.filter { it.forgeLevel <= forge }.random(rnd)
            spells[s] = 1
        }
        val decorChance = when (kind) { ChestKind.WOOD -> 0.05f; ChestKind.SILVER -> 0.2f; ChestKind.GOLD -> 0.65f }
        val decor = if (rnd.nextFloat() < decorChance) listOf(BType.GOLD_MONUMENT, BType.SAGE_STATUE, BType.DRAGON_STATUE).random(rnd) else null
        return ChestReward(coins, potions, spells, decor)
    }

    /** Köylülerin konuşma balonları: senin gerçek durumuna göre değişir. */
    fun villagerLines(d: AppData, now: Long): List<String> {
        val today = Time.date(now)
        val rec = d.days[today.toString()]
        val mins = (rec?.studyMs ?: 0L) / MINUTE_MS
        val streak = Streaks.daily(d, today)
        val hour = Time.minuteOfDay(now) / 60
        val out = mutableListOf<String>()
        when {
            mins == 0L && hour >= 18 -> out += "Bugün hiç çalışmadın mı? Kale seni bekliyor!"
            mins == 0L -> out += "Günaydın! İlk oturumu başlatınca inşaatçılar işe koyulur."
            rec?.dailyMet == true -> out += "Bugünkü hedefi tamamladın, köy bayram ediyor! 🎉"
            else -> out += "Bugün $mins dakika çalıştın, harika gidiyorsun!"
        }
        if (streak >= 3) out += "$streak günlük seri! Bu köyde efsane oldun."
        d.game.boss?.let { b ->
            val k = bossKind(b)
            if (!b.defeated) {
                val left = ((b.maxMs - b.dealtMs).coerceAtLeast(0)) / MINUTE_MS
                out += "${k.emoji} ${k.name} hâlâ kapımızda! $left dakika daha çalışırsan yenilir."
            } else out += "${k.name}'nı yendin, herkes sana minnettar!"
        }
        if (d.game.buildings.any { it.upgrade != null }) out += "İnşaatçılar çekiçlerini bekliyor: sen çalış, onlar yapsın! 🔨"
        if (d.game.chests.isNotEmpty()) out += "Meydanda açılmamış ${d.game.chests.size} sandık var! 🎁"
        d.deadlines.filter { it.completedAt == null }.map { Deadlines.status(d, it, today) }.firstOrNull { it.pace == Pace.BEHIND }?.let {
            out += "\"${it.goal.title}\" için bugün ${it.perDay} ${it.goal.unit} yapmalısın."
        }
        if (hour >= 23 || hour < 5) out += "Gece yarısı geçti, dinlenmek de çalışmanın parçası. 🌙"
        if (d.game.battlesWon > 0) out += "${d.game.battlesWon} zaferin dillerde dolaşıyor! ⚔️"
        return out
    }
}

// ---------------------------------------------------------------------- Repo içinden çağrılanlar

/** Bu haftanın canavarı yoksa getirir; geçen haftaki yenilmediyse biraz altın çalıp kaçar. */
internal fun Repo.Tx.ensureBoss() {
    val week = Surprises.weekKey(Time.date(now))
    val g = data.game
    val cur = g.boss
    if (cur != null && cur.week == week) return
    var coins = data.coins
    if (cur != null && !cur.defeated) {
        val k = Surprises.bossKind(cur)
        val loss = minOf((coins * 0.05f).toInt(), 150)
        coins -= loss
        alerts += "${k.emoji} ${k.name} kaçtı!" to "Geçen hafta onu yenemedin ve kaçarken $loss 🪙 çaldı. Bu hafta yeni bir canavar geldi!"
    }
    val idx = Time.date(now).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    val maxMs = (data.settings.dailyGoalMinutes * 5L).coerceAtLeast(300L) * MINUTE_MS
    val boss = BossState(week, idx % Surprises.bosses.size, maxMs)
    data = data.copy(coins = coins, game = g.copy(boss = boss))
    if (cur != null) notices += "${Surprises.bossKind(boss).emoji} Bu hafta köyüne ${Surprises.bossKind(boss).name} geldi! Her çalıştığın dakika ona vurur."
}

/** Çalışılan süre kadar canavara vurur. */
internal fun Repo.Tx.hitBoss(ms: Long) {
    val b = data.game.boss ?: return
    if (b.defeated || ms <= 0) return
    val dealt = b.dealtMs + ms
    if (dealt >= b.maxMs) {
        val k = Surprises.bossKind(b)
        data = data.copy(game = data.game.copy(boss = b.copy(dealtMs = b.maxMs, defeated = true), chests = data.game.chests + ChestKind.GOLD))
        award(500, "${k.emoji} ${k.defeatLine} Altın sandık kazandın!")
        sounds += Jingle.VICTORY
    } else {
        data = data.copy(game = data.game.copy(boss = b.copy(dealtMs = dealt)))
    }
}

internal fun Repo.Tx.giveChest(kind: ChestKind, why: String) {
    data = data.copy(game = data.game.copy(chests = data.game.chests + kind))
    notices += "${kind.emoji} $why: ${kind.title} kazandın!"
}

object SurpriseActions {
    /** İlk sandığı açar ve ödülü döner. */
    fun openChest(): Pair<ChestKind, ChestReward>? = Repo.mutate {
        val g = data.game
        val kind = g.chests.firstOrNull() ?: return@mutate null
        val r = Surprises.rollChest(kind, g)
        val spells = g.spells.toMutableMap()
        var room = Game.spellCapacity(g) - Game.spellCount(g)
        val gotSpells = mutableMapOf<SType, Int>()
        r.spells.forEach { (s, n) ->
            val add = minOf(n, room.coerceAtLeast(0))
            if (add > 0) {
                spells[s] = (spells[s] ?: 0) + add
                gotSpells[s] = add
                room -= add
            }
        }
        val tokens = g.decorTokens.toMutableMap()
        r.decor?.let { tokens[it] = (tokens[it] ?: 0) + 1 }
        data = data.copy(
            coins = data.coins + r.coins,
            totalEarned = data.totalEarned + r.coins,
            game = g.copy(
                chests = g.chests.drop(1),
                potions = g.potions + r.potions,
                spells = spells,
                decorTokens = tokens,
            ),
        )
        log("${kind.emoji} ${kind.title} açıldı", r.coins)
        sounds += Jingle.CHEST
        kind to r.copy(spells = gotSpells)
    }

    /** İnşaat İksiri: tüm inşaatlara ve araştırmaya 30 dk'lık ders ilerlemesi ekler. */
    fun usePotion(): String? = Repo.mutate {
        val g = data.game
        if (g.potions <= 0) return@mutate "İksirin yok"
        if (g.buildings.none { it.upgrade != null } && g.research == null) return@mutate "Şu an yürüyen bir inşaat yok"
        data = data.copy(game = g.copy(potions = g.potions - 1))
        advanceGame(30 * MINUTE_MS)
        sounds += Sfx.SPELL
        "🧪 İksir içildi: tüm inşaatlar 30 dk ilerledi!"
    }

    /** Köy ekranı açılınca, canavara son ziyaretten beri vurulan hasar "gösterildi" olarak işaretlenir. */
    fun markBossShown() = Repo.mutate {
        val b = data.game.boss ?: return@mutate
        if (b.shownMs != b.dealtMs) data = data.copy(game = data.game.copy(boss = b.copy(shownMs = b.dealtMs)))
    }
}
