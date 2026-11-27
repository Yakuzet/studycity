package com.derscalisma.app.notify

import com.derscalisma.app.data.AppData
import com.derscalisma.app.data.DayRecord
import com.derscalisma.app.data.Deadlines
import com.derscalisma.app.data.MINUTE_MS
import com.derscalisma.app.data.Pace
import com.derscalisma.app.data.Phase
import com.derscalisma.app.data.Streaks
import com.derscalisma.app.data.StudyWindow
import com.derscalisma.app.data.Time
import com.derscalisma.app.i18n.Fmt
import com.derscalisma.app.i18n.S
import java.time.LocalDate

enum class ReminderKind { WINDOW_START, WINDOW_NUDGE, WINDOW_ENDING, STREAK_WARN, LAST_CHANCE, DEADLINE }

data class Reminder(val key: String, val at: Long, val kind: ReminderKind, val windowId: String? = null)

object Reminders {
    private const val LAST_CHANCE_MIN = 22 * 60 + 30
    /** Bitiş uyarısı: kalan hedef + bu kadar dakika önce (hemen başlarsan yetişsin diye). */
    private const val ENDING_SLACK_MIN = 5

    /** Bugün kaydedilmiş + şu an sürmekte olan çalışma. */
    fun liveTodayMs(data: AppData, now: Long): Long {
        val rec = data.days[Time.dayKey(now)] ?: DayRecord()
        val t = data.timer
        var extra = 0L
        if (t != null && t.isRunning && t.phase == Phase.WORK) {
            val start = maxOf(t.phaseStartedAt, Time.startOfDay(Time.date(now)))
            extra = (minOf(now, t.phaseEndAt) - start).coerceAtLeast(0)
        }
        return rec.studyMs + extra
    }

    /** Ders saatinde bugün kaydedilmiş + şu an sürmekte olan çalışma. */
    fun liveWindowMs(data: AppData, w: StudyWindow, now: Long): Long {
        val date = Time.date(now)
        val rec = data.days[date.toString()] ?: DayRecord()
        var ms = rec.windowMs[w.id] ?: 0L
        val t = data.timer
        if (t != null && t.isRunning && t.phase == Phase.WORK) {
            val ov = minOf(minOf(now, t.phaseEndAt), Time.at(date, w.endMin)) - maxOf(t.phaseStartedAt, Time.at(date, w.startMin))
            if (ov > 0) ms += ov
        }
        return ms
    }

    private fun studying(data: AppData) = data.timer?.let { it.isRunning && it.phase == Phase.WORK } == true

    fun forDay(data: AppData, date: LocalDate, now: Long): List<Reminder> {
        val s = data.settings
        val list = mutableListOf<Reminder>()
        val isToday = date == Time.date(now)
        for (w in data.windows.filter { date.dayOfWeek.value in it.days }) {
            val start = Time.at(date, w.startMin)
            val end = Time.at(date, w.endMin)
            list += Reminder("$date|${w.id}|start", start, ReminderKind.WINDOW_START, w.id)
            val nudge = s.windowNudgeMinutes
            if (nudge > 0 && w.startMin + nudge < w.endMin - 5) {
                list += Reminder("$date|${w.id}|nudge", Time.at(date, w.startMin + nudge), ReminderKind.WINDOW_NUDGE, w.id)
            }
            // Bitiş uyarısı: kalan hedef + 5 dk önce. Ör. 120 dk kaldıysa bitişten 125 dk önce.
            val doneMin = if (isToday) (liveWindowMs(data, w, now) / MINUTE_MS).toInt() else 0
            val leftMin = (w.targetMinutes - doneMin).coerceAtLeast(0)
            if (leftMin > 0) {
                val at = maxOf(end - (leftMin + ENDING_SLACK_MIN) * MINUTE_MS, start + MINUTE_MS)
                if (at < end) list += Reminder("$date|${w.id}|end", at, ReminderKind.WINDOW_ENDING, w.id)
            }
        }
        val warnMin = s.warnHour * 60 + s.warnMinute
        if (data.deadlines.any { it.completedAt == null && it.dueDate >= date.toString() }) {
            list += Reminder("$date|deadline", Time.at(date, (warnMin - 60).coerceAtLeast(12 * 60)), ReminderKind.DEADLINE)
        }
        list += Reminder("$date|warn", Time.at(date, warnMin), ReminderKind.STREAK_WARN)
        if (warnMin < LAST_CHANCE_MIN) {
            list += Reminder("$date|last", Time.at(date, LAST_CHANCE_MIN), ReminderKind.LAST_CHANCE)
        }
        return list
    }

    /** Bir sonraki alarm: en yakın hatırlatma ya da gece yarısı gün kapanışı. */
    fun nextAlarmTime(data: AppData, now: Long): Long {
        val today = Time.date(now)
        val rollover = Time.startOfDay(today.plusDays(1)) + 30_000
        if (!data.settings.remindersEnabled) return rollover
        val next = (forDay(data, today, now) + forDay(data, today.plusDays(1), now))
            .filter { it.at > now && it.key !in data.firedReminders }
            .minOfOrNull { it.at }
        return if (next == null) rollover else minOf(next, rollover)
    }

    /** Hatırlatmanın gösterilip gösterilmeyeceğine karar verir; gösterilecekse (başlık, metin). */
    fun evaluate(data: AppData, r: Reminder, now: Long): Pair<String, String>? {
        val today = Time.date(now)
        if (Time.dayKey(r.at) != today.toString()) return null // dünden kalan hatırlatma
        val rec = data.days[today.toString()] ?: DayRecord()
        val timerRunning = data.timer?.let { !it.isPaused } == true
        val w = r.windowId?.let { id -> data.windows.find { it.id == id } }
        val n = S.notify

        return when (r.kind) {
            ReminderKind.WINDOW_START -> {
                val win = w ?: return null
                if (timerRunning) return null
                n.windowStartTitle(Fmt.windowName(win)) to
                    n.windowStartText(Fmt.range(win.startMin, win.endMin), win.targetMinutes, Streaks.windowAttend(data, now))
            }

            ReminderKind.WINDOW_NUDGE -> {
                val win = w ?: return null
                if (timerRunning || win.id in rec.windowsAttended) return null
                n.windowNudgeTitle to n.windowNudgeText(Fmt.windowName(win), Fmt.hm(win.endMin), Streaks.windowAttend(data, now))
            }

            ReminderKind.WINDOW_ENDING -> {
                val win = w ?: return null
                if (win.id in rec.windowsMet || studying(data)) return null
                val left = (win.targetMinutes - liveWindowMs(data, win, now) / MINUTE_MS).toInt().coerceAtLeast(0)
                if (left == 0) return null
                val untilEnd = ((Time.at(today, win.endMin) - now) / MINUTE_MS).toInt().coerceAtLeast(0)
                n.windowEndingTitle(Fmt.windowName(win)) to n.windowEndingText(left, untilEnd, Streaks.windowGoal(data, now))
            }

            ReminderKind.DEADLINE -> {
                val behind = data.deadlines
                    .filter { it.completedAt == null }
                    .map { Deadlines.status(data, it, today) }
                    .filter { it.pace == Pace.BEHIND && it.today < it.todayQuota }
                if (behind.isEmpty()) return null
                n.deadlineTitle to behind.joinToString("\n") {
                    n.deadlineLine(Fmt.deadlineName(it.goal), Fmt.amount(it.todayQuota - it.today, it.goal.unit), it.daysLeft)
                }
            }

            ReminderKind.STREAK_WARN, ReminderKind.LAST_CHANCE -> {
                if (rec.dailyMet || studying(data)) return null
                val st = Streaks.daily(data, today)
                if (r.kind == ReminderKind.LAST_CHANCE && st == 0) return null
                val left = ((data.settings.dailyGoalMinutes * MINUTE_MS - liveTodayMs(data, now) + MINUTE_MS - 1) / MINUTE_MS).toInt().coerceAtLeast(0)
                if (r.kind == ReminderKind.LAST_CHANCE) n.lastChanceTitle(st) to n.lastChanceText(left, data.streakFreezes)
                else n.streakWarnTitle(st) to n.streakWarnText(left, st)
            }
        }
    }
}
