package com.derscalisma.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

enum class Pace { DONE, AHEAD, ON_TRACK, BEHIND, OVERDUE }

data class DeadlineStatus(
    val goal: DeadlineGoal,
    val done: Int,
    val remaining: Int,
    val totalDays: Int,
    /** Bugün dahil kalan gün. */
    val daysLeft: Int,
    /** Bitirmek için günde gereken (bugün dahil). */
    val perDay: Int,
    /** Bugünün başına kadar yapılmış olması gereken. */
    val expected: Int,
    /** Bugün bu hedefe yapılan ilerleme. */
    val today: Int,
    /** Şu anki ortalama hızla tahmini bitiş günü. */
    val projected: LocalDate?,
    val pace: Pace,
    /** Bugün planda kalmak için bugün yapılması gereken toplam (bugün yapılan dahil). */
    val todayQuota: Int = perDay,
) {
    val ratio get() = (done.toFloat() / goal.total).coerceIn(0f, 1f)
    val expectedRatio get() = (expected.toFloat() / goal.total).coerceIn(0f, 1f)
    val delta get() = done - expected
}

object Deadlines {

    fun progress(d: AppData, g: DeadlineGoal): Int {
        val linked = g.linkedGoalId?.let { id ->
            d.days.filterKeys { it >= g.startDate && it <= g.dueDate }.values.sumOf { it.progress[id] ?: 0 }
        } ?: 0
        return g.manual + linked
    }

    fun status(d: AppData, g: DeadlineGoal, today: LocalDate): DeadlineStatus {
        val start = LocalDate.parse(g.startDate)
        val due = LocalDate.parse(g.dueDate)
        val done = progress(d, g)
        val remaining = (g.total - done).coerceAtLeast(0)
        val totalDays = (ChronoUnit.DAYS.between(start, due) + 1).toInt().coerceAtLeast(1)
        val daysLeft = (ChronoUnit.DAYS.between(today, due) + 1).toInt().coerceIn(0, totalDays)
        val elapsedBeforeToday = ChronoUnit.DAYS.between(start, today).toInt().coerceIn(0, totalDays)
        val expected = (g.total.toLong() * elapsedBeforeToday / totalDays).toInt()
        val perDay = if (daysLeft > 0) ceil(remaining / daysLeft.toDouble()).toInt() else remaining
        val todayDone = g.linkedGoalId?.let { d.days[today.toString()]?.progress?.get(it) } ?: 0
        val daysSoFar = (elapsedBeforeToday + 1).coerceAtMost(totalDays)
        val rate = done.toDouble() / daysSoFar
        val projected = when {
            remaining == 0 -> today
            rate <= 0.0 -> null
            else -> today.plusDays(ceil(remaining / rate).toLong() - 1)
        }
        val pace = when {
            remaining == 0 -> Pace.DONE
            daysLeft == 0 -> Pace.OVERDUE
            done >= expected + (g.total / totalDays).coerceAtLeast(1) -> Pace.AHEAD
            done >= expected -> Pace.ON_TRACK
            else -> Pace.BEHIND
        }
        val quota = if (daysLeft > 0) ceil((remaining + todayDone) / daysLeft.toDouble()).toInt() else remaining
        return DeadlineStatus(g, done, remaining, totalDays, daysLeft, quota, expected, todayDone, projected, pace, quota)
    }

    /** Zamanında bitirme ödülü: büyüklüğe ve erken bitirmeye göre. */
    fun reward(s: DeadlineStatus): Int =
        if (s.daysLeft == 0) 50 else 200 + s.goal.total.coerceAtMost(1000) / 2 + 20 * (s.daysLeft - 1).coerceAtMost(30)
}
