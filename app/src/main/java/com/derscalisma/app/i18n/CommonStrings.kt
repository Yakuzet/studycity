package com.derscalisma.app.i18n

import com.derscalisma.app.data.GoalType
import com.derscalisma.app.data.PauseReason

/** Her ekranda kullanılan ortak metinler, birimler ve biçimler. */
interface CommonStrings {
    val appName: String
    val ok: String
    val cancel: String
    val save: String
    val delete: String
    val edit: String
    val add: String
    val close: String
    val back: String
    val done: String
    val skip: String
    val next: String
    val start: String
    val continueLabel: String
    val yes: String
    val no: String
    val on: String
    val off: String
    val today: String
    val yesterday: String
    val tomorrow: String
    val everyDay: String
    val weekdays: String
    val weekend: String
    val none: String

    /** Ders parasının adı ("Ders parası", "Study coins"). */
    val coinName: String
    val balance: String

    /** Kısa dakika: "25 dk", "25 min". */
    fun minutes(n: Int): String
    /** Uzun dakika: "25 dakika", "25 minutes". */
    fun minutesLong(n: Int): String
    /** "2 sa", "2 h". */
    fun hours(n: Int): String
    /** "1 sa 20 dk", "1 h 20 min". */
    fun hoursMinutes(h: Int, m: Int): String
    /** "10 sn", "10 s". */
    fun seconds(n: Int): String
    /** "5 gün", "5 days". */
    fun days(n: Int): String
    /** Yüzde, dilin yazımıyla: "%80", "80%", "80 %". */
    fun percent(p: Int): String
    /** "Tur 2/4", "Round 2/4". */
    fun roundOf(i: Int, n: Int): String
    /** "4 tur", "4 rounds". */
    fun rounds(n: Int): String

    /** java.time deseni: gün + ay ("d MMMM", "MMMM d", "M月d日"). */
    val dayMonthPattern: String
    /** java.time deseni: haftanın günü + gün + ay ("d MMMM EEEE", "EEEE, MMMM d"). */
    val weekdayDayMonthPattern: String

    /** Hazır hedef türünün adı (kullanıcı adını değiştirmediyse bu gösterilir). */
    fun goalTypeName(t: GoalType): String
    /** Hedef türünün birimi, sayıya göre çoğul: "30 sayfa", "30 pages". Sadece birim döner. */
    fun goalTypeUnit(t: GoalType, n: Int): String
    /** Acil durum / ara sebebi. */
    fun pauseReason(r: PauseReason): String

    /** Yeni ders saatinin varsayılan adı. */
    val defaultWindowName: String
    /** Adsız süreli hedefin adı. */
    val defaultDeadlineName: String

    /** "120 ders parası eksik". */
    fun notEnoughCoins(missing: Int): String
}

/** Gezinme çubuğu ve üst çubuk. */
interface NavStrings {
    val today: String
    val focus: String
    val plan: String
    val village: String
    val store: String
    val settings: String
    val wallet: String
    /** Başka sekmedeyken alt şerit: "Çalışma", "Mola". */
    val miniWork: String
    val miniBreak: String
    val miniPaused: String
    val miniWaiting: String
    /** Köy ekranları bu sürümde yalnızca Türkçe. */
    val villageOnlyTurkish: String
}
