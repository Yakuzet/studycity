package com.derscalisma.app.ui

import com.derscalisma.app.data.AppData
import com.derscalisma.app.data.DayRecord
import com.derscalisma.app.data.MINUTE_MS
import com.derscalisma.app.data.Phase
import com.derscalisma.app.data.StudyWindow
import com.derscalisma.app.data.Time
import com.derscalisma.app.data.TimerState

/** Ekranda canlı gösterim için: kaydedilmiş süre + şu an devam eden çalışma bölümü. */
fun liveTodayMs(data: AppData, now: Long): Long {
    val rec = data.days[Time.dayKey(now)] ?: DayRecord()
    val t = data.timer
    var extra = 0L
    if (t != null && !t.isPaused && t.phase == Phase.WORK) {
        val start = maxOf(t.phaseStartedAt, Time.startOfDay(Time.date(now)))
        extra = (minOf(now, t.phaseEndAt) - start).coerceAtLeast(0)
    }
    return rec.studyMs + extra
}

fun liveWindowMs(data: AppData, w: StudyWindow, now: Long): Long {
    val rec = data.days[Time.dayKey(now)] ?: DayRecord()
    var ms = rec.windowMs[w.id] ?: 0L
    val t = data.timer
    if (t != null && !t.isPaused && t.phase == Phase.WORK) {
        val day0 = Time.startOfDay(Time.date(now))
        val ws = day0 + w.startMin * MINUTE_MS
        val we = day0 + w.endMin * MINUTE_MS
        val ov = minOf(minOf(now, t.phaseEndAt), we) - maxOf(t.phaseStartedAt, ws)
        if (ov > 0) ms += ov
    }
    return ms
}

fun liveSessionMs(t: TimerState, now: Long): Long {
    val extra = if (!t.isPaused && t.phase == Phase.WORK) (minOf(now, t.phaseEndAt) - t.phaseStartedAt).coerceAtLeast(0) else 0L
    return t.sessionWorkMs + extra
}

fun windowsOnToday(data: AppData, now: Long): List<StudyWindow> {
    val dow = Time.date(now).dayOfWeek.value
    return data.windows.filter { dow in it.days }.sortedBy { it.startMin }
}

/** Şu anki ya da sıradaki çalışma aralığını anlatan kısa metin. */
fun windowStatusText(data: AppData, now: Long): String {
    if (data.windows.isEmpty()) return "Henüz çalışma aralığı eklemedin. Aralıklar sekmesinden ekleyebilirsin."
    val nowMin = Time.minuteOfDay(now)
    val todays = windowsOnToday(data, now)
    todays.find { nowMin >= it.startMin && nowMin < it.endMin }?.let { w ->
        val done = liveWindowMs(data, w, now) / MINUTE_MS
        return "🟢 Şu an: ${w.label} (${Time.hm(w.startMin)}–${Time.hm(w.endMin)})\nAralık hedefi: $done / ${w.targetMinutes} dk"
    }
    todays.find { it.startMin > nowMin }?.let { w ->
        return "⏭ Sıradaki: ${w.label}, bugün ${Time.hm(w.startMin)}–${Time.hm(w.endMin)}"
    }
    val today = Time.date(now)
    for (i in 1..7) {
        val d = today.plusDays(i.toLong())
        val w = data.windows.filter { d.dayOfWeek.value in it.days }.minByOrNull { it.startMin } ?: continue
        val dayName = if (i == 1) "yarın" else Time.dayLong[d.dayOfWeek.value - 1]
        return "⏭ Sıradaki: ${w.label}, $dayName ${Time.hm(w.startMin)}–${Time.hm(w.endMin)}"
    }
    return "Yakında çalışma aralığı yok."
}
