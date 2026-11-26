package com.derscalisma.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

object Time {
    val dayShort = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
    val dayLong = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")

    private fun zone(): ZoneId = ZoneId.systemDefault()

    fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone()).toLocalDate()

    fun dayKey(ms: Long): String = date(ms).toString()

    fun startOfDay(d: LocalDate): Long = d.atStartOfDay(zone()).toInstant().toEpochMilli()

    fun minuteOfDay(ms: Long): Int {
        val z = Instant.ofEpochMilli(ms).atZone(zone())
        return z.hour * 60 + z.minute
    }

    fun hm(minuteOfDay: Int): String =
        String.format(Locale.US, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

    fun timeOf(ms: Long): String = hm(minuteOfDay(ms))

    /** Geri sayım biçimi: 24:59 veya 1:05:00 */
    fun clock(ms: Long): String {
        val total = ((ms.coerceAtLeast(0) + 999) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun duration(ms: Long): String {
        val m = ms / MINUTE_MS
        val h = m / 60
        return if (h > 0) "$h sa ${m % 60} dk" else "$m dk"
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)
}
