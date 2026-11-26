package com.derscalisma.app.data

import java.time.LocalDate

/**
 * Seriler geçmiş gün kayıtlarından hesaplanır (sayaç tutulmaz), bu yüzden her zaman tutarlıdır.
 * "Bugün" henüz tamamlanmadıysa seri dünden itibaren sayılır (seri hâlâ kurtarılabilir).
 */
object Streaks {

    fun daily(data: AppData, today: LocalDate): Int =
        lenient(data, today) { it.dailyMet || it.frozen }

    /** Verilen gün dahil, geriye doğru kesintisiz seri (esneklik yok). */
    fun dailyEndingAt(data: AppData, date: LocalDate): Int =
        countBack(data, date) { it.dailyMet || it.frozen }

    fun goal(data: AppData, goalId: String, today: LocalDate): Int =
        lenient(data, today) { goalId in it.goalsMet }

    /** Art arda kaç çalışma aralığında çalışıldı (aralık içinde en az 5 dk). */
    fun windowAttend(data: AppData, now: Long): Int =
        windowStreak(data, now) { rec, w -> w.id in rec.windowsAttended }

    /** Art arda kaç çalışma aralığında aralık hedefine ulaşıldı. */
    fun windowGoal(data: AppData, now: Long): Int =
        windowStreak(data, now) { rec, w -> w.id in rec.windowsMet }

    private fun lenient(data: AppData, today: LocalDate, pred: (DayRecord) -> Boolean): Int {
        val rec = data.days[today.toString()]
        return if (rec != null && pred(rec)) countBack(data, today, pred)
        else countBack(data, today.minusDays(1), pred)
    }

    private fun countBack(data: AppData, from: LocalDate, pred: (DayRecord) -> Boolean): Int {
        var d = from
        var n = 0
        while (n < 5000) {
            val rec = data.days[d.toString()] ?: break
            if (!pred(rec)) break
            n++
            d = d.minusDays(1)
        }
        return n
    }

    private fun windowStreak(
        data: AppData,
        now: Long,
        pred: (DayRecord, StudyWindow) -> Boolean,
    ): Int {
        if (data.windows.isEmpty()) return 0
        val today = Time.date(now)
        val nowMin = Time.minuteOfDay(now)
        var count = 0
        for (offset in 0..400) {
            val date = today.minusDays(offset.toLong())
            val rec = data.days[date.toString()] ?: DayRecord()
            val windows = data.windows
                .filter { date.dayOfWeek.value in it.days }
                .sortedByDescending { it.startMin }
            for (w in windows) {
                val ok = pred(rec, w)
                if (offset == 0 && nowMin < w.endMin) {
                    // Henüz bitmemiş aralık: başarıldıysa sayılır, başarılmadıysa seriyi bozmaz.
                    if (ok) count++
                    continue
                }
                if (ok) count++ else return count
            }
        }
        return count
    }
}
