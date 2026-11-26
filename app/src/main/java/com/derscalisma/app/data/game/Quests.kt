package com.derscalisma.app.data.game

import com.derscalisma.app.data.AppData
import com.derscalisma.app.data.MINUTE_MS

/** Kademeli görev: her kademe tamamlanınca ödülü alınır, sonraki kademe açılır. */
class Quest(
    val id: String,
    val emoji: String,
    val title: String,
    val unit: String,
    val tiers: IntArray,
    val rewards: IntArray,
    val progress: (AppData) -> Int,
)

data class QuestStatus(val quest: Quest, val claimedTiers: Int, val value: Int) {
    val done get() = claimedTiers >= quest.tiers.size
    val target get() = quest.tiers.getOrElse(claimedTiers) { quest.tiers.last() }
    val reward get() = quest.rewards.getOrElse(claimedTiers) { 0 }
    val claimable get() = !done && value >= target
}

object Quests {
    val all = listOf(
        Quest("study", "📖", "Çalışkan", "saat", intArrayOf(1, 5, 20, 50, 100, 250), intArrayOf(100, 300, 800, 2000, 5000, 12000)) { d ->
            (d.days.values.sumOf { it.studyMs } / (60 * MINUTE_MS)).toInt()
        },
        Quest("streak", "🔥", "Seri Ustası", "gün", intArrayOf(3, 7, 14, 30, 60, 100), intArrayOf(150, 400, 900, 2500, 6000, 12000)) { it.bestDailyStreak },
        Quest("goals", "🎯", "Hedef Avcısı", "hedef", intArrayOf(5, 25, 100, 250), intArrayOf(100, 400, 1500, 4000)) { d ->
            d.days.values.sumOf { it.goalsMet.size + (if (it.dailyMet) 1 else 0) }
        },
        Quest("windows", "📅", "Dakik", "aralık", intArrayOf(5, 25, 100), intArrayOf(150, 600, 2000)) { d ->
            d.days.values.sumOf { it.windowsMet.size }
        },
        Quest("focus", "🧠", "Odak", "toplu cevap", intArrayOf(10, 50, 200, 500), intArrayOf(100, 400, 1500, 4000)) { d ->
            d.days.values.sumOf { it.focusYes }
        },
        Quest("townhall", "🏰", "Mimar", "seviye", intArrayOf(2, 3, 4, 5, 6), intArrayOf(300, 800, 2000, 4000, 8000)) { Game.th(it.game) },
        Quest("builders", "👷", "Usta Başı", "inşaatçı", intArrayOf(2, 3, 4, 5), intArrayOf(300, 800, 1500, 3000)) { it.game.builders },
        Quest("walls", "🧱", "Surcu", "sur", intArrayOf(10, 30, 60, 100), intArrayOf(100, 300, 700, 1500)) { d ->
            d.game.buildings.count { it.type == BType.WALL }
        },
        Quest("troops", "⚔️", "Ordu Kurucu", "asker", intArrayOf(20, 100, 300, 1000), intArrayOf(100, 400, 1000, 3000)) { it.game.troopsTrained },
        Quest("battles", "🏆", "Fatih", "zafer", intArrayOf(1, 5, 20, 50, 100), intArrayOf(100, 400, 1200, 3000, 6000)) { it.game.battlesWon },
        Quest("stars", "⭐", "Yıldız Avcısı", "yıldız", intArrayOf(3, 15, 40, 70, 90), intArrayOf(200, 600, 1500, 3500, 8000)) { Game.stars(it.game) },
        Quest("hero", "👑", "Komutanın Yolu", "seviye", intArrayOf(1, 3, 5, 8, 10), intArrayOf(200, 600, 1500, 3500, 7000)) { d ->
            Game.maxBuiltLevel(d.game, BType.HERO_ALTAR)
        },
    )

    fun status(d: AppData): List<QuestStatus> = all.map { q -> QuestStatus(q, d.game.claimed[q.id] ?: 0, q.progress(d)) }
    fun claimableCount(d: AppData) = status(d).count { it.claimable }
}
