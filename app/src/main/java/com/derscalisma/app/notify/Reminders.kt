package com.derscalisma.app.notify

import com.derscalisma.app.data.AppData
import com.derscalisma.app.data.DayRecord
import com.derscalisma.app.data.Deadlines
import com.derscalisma.app.data.Pace
import com.derscalisma.app.data.MINUTE_MS
import com.derscalisma.app.data.Streaks
import com.derscalisma.app.data.Time
import java.time.LocalDate

enum class ReminderKind { WINDOW_START, WINDOW_NUDGE, WINDOW_ENDING, STREAK_WARN, LAST_CHANCE, DEADLINE }

data class Reminder(val key: String, val at: Long, val kind: ReminderKind, val windowId: String? = null)

object Reminders {
    private const val LAST_CHANCE_MIN = 22 * 60 + 30
    private const val ENDING_BEFORE_MIN = 15

    fun forDay(data: AppData, date: LocalDate): List<Reminder> {
        val day0 = Time.startOfDay(date)
        val s = data.settings
        val list = mutableListOf<Reminder>()
        for (w in data.windows.filter { date.dayOfWeek.value in it.days }) {
            list += Reminder("$date|${w.id}|start", day0 + w.startMin * MINUTE_MS, ReminderKind.WINDOW_START, w.id)
            val nudge = s.windowNudgeMinutes
            if (nudge > 0 && w.startMin + nudge < w.endMin - 5) {
                list += Reminder("$date|${w.id}|nudge", day0 + (w.startMin + nudge) * MINUTE_MS, ReminderKind.WINDOW_NUDGE, w.id)
            }
            if (w.endMin - ENDING_BEFORE_MIN > w.startMin + nudge) {
                list += Reminder("$date|${w.id}|end", day0 + (w.endMin - ENDING_BEFORE_MIN) * MINUTE_MS, ReminderKind.WINDOW_ENDING, w.id)
            }
        }
        val warnMin = s.warnHour * 60 + s.warnMinute
        if (data.deadlines.any { it.completedAt == null && it.dueDate >= date.toString() }) {
            list += Reminder("$date|deadline", day0 + (warnMin - 60).coerceAtLeast(12 * 60) * MINUTE_MS, ReminderKind.DEADLINE)
        }
        list += Reminder("$date|warn", day0 + warnMin * MINUTE_MS, ReminderKind.STREAK_WARN)
        if (warnMin < LAST_CHANCE_MIN) {
            list += Reminder("$date|last", day0 + LAST_CHANCE_MIN * MINUTE_MS, ReminderKind.LAST_CHANCE)
        }
        return list
    }

    /** Bir sonraki alarm: en yakın hatırlatma ya da gece yarısı gün kapanışı. */
    fun nextAlarmTime(data: AppData, now: Long): Long {
        val today = Time.date(now)
        val rollover = Time.startOfDay(today.plusDays(1)) + 30_000
        if (!data.settings.remindersEnabled) return rollover
        val next = (forDay(data, today) + forDay(data, today.plusDays(1)))
            .filter { it.at > now && it.key !in data.firedReminders }
            .minOfOrNull { it.at }
        return if (next == null) rollover else minOf(next, rollover)
    }

    /** Hatırlatmanın gösterilip gösterilmeyeceğine karar verir; gösterilecekse (başlık, metin). */
    fun evaluate(data: AppData, r: Reminder, now: Long): Pair<String, String>? {
        val today = Time.date(now)
        val rec = data.days[Time.dayKey(r.at)] ?: DayRecord()
        val timerRunning = data.timer?.let { !it.isPaused } == true
        val w = r.windowId?.let { id -> data.windows.find { it.id == id } }

        return when (r.kind) {
            ReminderKind.WINDOW_START -> {
                val win = w ?: return null
                if (timerRunning) return null
                val st = Streaks.windowAttend(data, now)
                "📚 Ders saati: ${win.label}" to buildString {
                    append("${Time.hm(win.startMin)}–${Time.hm(win.endMin)} aralığı başladı. ")
                    append("Hedef: ${win.targetMinutes} dk. Hemen çalışmaya başlamalısın!")
                    if (st > 0) append(" Aralık serin: $st 🔥")
                }
            }

            ReminderKind.WINDOW_NUDGE -> {
                val win = w ?: return null
                if (timerRunning || win.id in rec.windowsAttended) return null
                val st = Streaks.windowAttend(data, now)
                "⏰ Hâlâ başlamadın!" to buildString {
                    if (st > 0) append("$st aralıklık çalışma serin tehlikede. ")
                    append("${win.label} aralığı ${Time.hm(win.endMin)}'e kadar sürüyor, şimdi başla.")
                }
            }

            ReminderKind.WINDOW_ENDING -> {
                val win = w ?: return null
                if (win.id in rec.windowsMet) return null
                val done = (rec.windowMs[win.id] ?: 0L) / MINUTE_MS
                val left = (win.targetMinutes - done).coerceAtLeast(0)
                val st = Streaks.windowGoal(data, now)
                "⌛ ${win.label} bitmek üzere" to buildString {
                    append("Aralık hedefine $left dk kaldı, aralık 15 dk içinde bitiyor.")
                    if (st > 0) append(" $st aralıklık hedef serini kaybetme!")
                }
            }

            ReminderKind.DEADLINE -> {
                val behind = data.deadlines
                    .filter { it.completedAt == null }
                    .map { Deadlines.status(data, it, today) }
                    .filter { it.pace == Pace.BEHIND && it.today < it.perDay }
                if (behind.isEmpty()) return null
                "📅 Süreli hedefinde geridesin" to behind.joinToString("\n") {
                    "${it.goal.title}: bugün ${it.perDay - it.today} ${it.goal.unit} daha (${it.daysLeft} gün kaldı)"
                }
            }

            ReminderKind.STREAK_WARN, ReminderKind.LAST_CHANCE -> {
                if (rec.dailyMet) return null
                val st = Streaks.daily(data, today)
                if (r.kind == ReminderKind.LAST_CHANCE && st == 0) return null
                val left = (data.settings.dailyGoalMinutes - rec.studyMs / MINUTE_MS).coerceAtLeast(0)
                val title = if (r.kind == ReminderKind.LAST_CHANCE) "🚨 Son şans! $st günlük serin bitmek üzere"
                else if (st > 0) "🔥 $st günlük serin tehlikede!"
                else "🎯 Günlük hedefin seni bekliyor"
                val text = buildString {
                    append("Bugünkü hedefe $left dk kaldı. ")
                    if (r.kind == ReminderKind.LAST_CHANCE) {
                        append("Gece yarısından önce tamamla, yoksa serin sıfırlanır!")
                        if (data.streakFreezes > 0) append(" (🧊 ${data.streakFreezes} dondurucun var)")
                    } else {
                        append(if (st > 0) "Serini kaybetme, şimdi başla!" else "Bugün yeni bir seri başlat!")
                    }
                }
                title to text
            }
        }
    }
}
