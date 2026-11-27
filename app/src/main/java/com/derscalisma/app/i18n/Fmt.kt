package com.derscalisma.app.i18n

import com.derscalisma.app.data.DeadlineGoal
import com.derscalisma.app.data.Goal
import com.derscalisma.app.data.GoalType
import com.derscalisma.app.data.MINUTE_MS
import com.derscalisma.app.data.StudyWindow
import com.derscalisma.app.data.Time
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import kotlin.math.abs

/** Geçerli dile göre biçimlendirme. Ekranlarda sayı, süre ve tarihleri hep buradan yaz. */
object Fmt {

    /** Süre (aşağı yuvarlanmış dakika): "1 sa 20 dk", "45 dk". */
    fun duration(ms: Long): String = minutesText((ms.coerceAtLeast(0) / MINUTE_MS).toInt())

    /** Kalan süre (yukarı yuvarlanmış dakika): 30 sn kalınca "0 dk" değil "1 dk" yazar. */
    fun durationCeil(ms: Long): String = minutesText(((ms.coerceAtLeast(0) + MINUTE_MS - 1) / MINUTE_MS).toInt())

    fun minutesText(totalMin: Int): String {
        val h = totalMin / 60
        return if (h > 0) S.common.hoursMinutes(h, totalMin % 60) else S.common.minutes(totalMin)
    }

    /** Binlik ayraçlı tam sayı: 1.500 / 1,500 / 1 500. */
    fun number(n: Int): String = NumberFormat.getIntegerInstance(L).format(n)

    /** İşaretli: +40, −15. */
    fun signed(n: Int): String = when {
        n > 0 -> "+" + number(n)
        n < 0 -> "−" + number(abs(n))
        else -> "0"
    }

    /** 27 Kasım / November 27 / 11月27日 */
    fun date(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern(S.common.dayMonthPattern, L))

    fun date(iso: String): String = runCatching { date(LocalDate.parse(iso)) }.getOrDefault(iso)

    /** 27 Kasım Cuma / Friday, November 27 */
    fun dateWithWeekday(d: LocalDate): String =
        d.format(DateTimeFormatter.ofPattern(S.common.weekdayDayMonthPattern, L)).replaceFirstChar { it.titlecase(L) }

    /** Haftanın günü, 1 = Pazartesi ... 7 = Pazar. */
    fun weekdayShort(dow: Int): String = DayOfWeek.of(dow).getDisplayName(TextStyle.SHORT, L).replaceFirstChar { it.titlecase(L) }

    fun weekdayLong(dow: Int): String = DayOfWeek.of(dow).getDisplayName(TextStyle.FULL, L).replaceFirstChar { it.titlecase(L) }

    /** Takvimde haftanın ilk günü (1 = Pazartesi, 7 = Pazar): gün seçicilerin sırası için. */
    fun firstDayOfWeek(): Int = WeekFields.of(L).firstDayOfWeek.value

    /** Haftanın günleri, dilin takvim sırasıyla. */
    fun weekOrder(): List<Int> {
        val first = firstDayOfWeek()
        return (0 until 7).map { (first - 1 + it) % 7 + 1 }
    }

    /** 14:30 */
    fun time(ms: Long): String = Time.timeOf(ms)

    /** Günün dakikasından saat: 1050 → 17:30 */
    fun hm(minuteOfDay: Int): String = Time.hm(minuteOfDay)

    /** Geri sayım: 24:59 veya 1:05:00 */
    fun clock(ms: Long): String = Time.clock(ms)

    /** "17:00–19:00" */
    fun range(startMin: Int, endMin: Int): String = "${hm(startMin)}–${hm(endMin)}"

    fun rangeMs(start: Long, end: Long): String = "${time(start)}–${time(end)}"

    /** Kullanıcı adı değiştirmediyse hazır hedefin adını dile göre gösterir. */
    fun goalName(g: Goal): String = if (g.name == g.type.defaultName) S.common.goalTypeName(g.type) else g.name

    /** Birim: hazır birimlerse ("sayfa", "soru"...) dile ve sayıya göre çevirir, değilse olduğu gibi. */
    fun unit(raw: String, n: Int): String {
        val t = GoalType.entries.find { it.defaultUnit == raw } ?: return raw
        return S.common.goalTypeUnit(t, n)
    }

    fun goalUnit(g: Goal, n: Int): String = unit(g.unit, n)

    /** "30 sayfa", "30 pages" */
    fun amount(n: Int, rawUnit: String): String = "${number(n)} ${unit(rawUnit, n)}"

    fun windowName(w: StudyWindow): String =
        if (w.label == "Ders Saati" || w.label == "Ders saati") S.common.defaultWindowName else w.label

    fun deadlineName(d: DeadlineGoal): String =
        if (d.title == "Süreli hedef") S.common.defaultDeadlineName else d.title

    /** "Her gün", "Hafta içi", ya da "Pzt Çar Cum" (dilin gün sırasıyla). */
    fun days(days: Set<Int>): String = when {
        days.size == 7 -> S.common.everyDay
        days == (1..5).toSet() -> S.common.weekdays
        days == setOf(6, 7) -> S.common.weekend
        else -> weekOrder().filter { it in days }.joinToString(" ") { weekdayShort(it) }
    }
}
