package com.derscalisma.app.i18n.tr

import com.derscalisma.app.data.GoalType
import com.derscalisma.app.data.PauseReason
import com.derscalisma.app.i18n.CommonStrings
import com.derscalisma.app.i18n.NavStrings

object CommonTr : CommonStrings {
    override val appName = "Ders Şehri"
    override val ok = "Tamam"
    override val cancel = "Vazgeç"
    override val save = "Kaydet"
    override val delete = "Sil"
    override val edit = "Düzenle"
    override val add = "Ekle"
    override val close = "Kapat"
    override val back = "Geri"
    override val done = "Bitti"
    override val skip = "Geç"
    override val next = "İleri"
    override val start = "Başlat"
    override val continueLabel = "Devam et"
    override val yes = "Evet"
    override val no = "Hayır"
    override val on = "Açık"
    override val off = "Kapalı"
    override val today = "Bugün"
    override val yesterday = "Dün"
    override val tomorrow = "Yarın"
    override val everyDay = "Her gün"
    override val weekdays = "Hafta içi"
    override val weekend = "Hafta sonu"
    override val none = "Yok"

    override val coinName = "Ders parası"
    override val balance = "Bakiye"

    override fun minutes(n: Int) = "$n dk"
    override fun minutesLong(n: Int) = "$n dakika"
    override fun hours(n: Int) = "$n sa"
    override fun hoursMinutes(h: Int, m: Int) = if (m == 0) "$h sa" else "$h sa $m dk"
    override fun seconds(n: Int) = "$n sn"
    override fun days(n: Int) = "$n gün"
    override fun percent(p: Int) = "%$p"
    override fun roundOf(i: Int, n: Int) = "Tur $i/$n"
    override fun rounds(n: Int) = "$n tur"

    override val dayMonthPattern = "d MMMM"
    override val weekdayDayMonthPattern = "d MMMM EEEE"

    override fun goalTypeName(t: GoalType) = when (t) {
        GoalType.PAGES -> "Sayfa"
        GoalType.QUESTIONS -> "Soru / Test"
        GoalType.TOPICS -> "Konu"
        GoalType.CUSTOM -> "Özel hedef"
    }

    override fun goalTypeUnit(t: GoalType, n: Int) = when (t) {
        GoalType.PAGES -> "sayfa"
        GoalType.QUESTIONS -> "soru"
        GoalType.TOPICS -> "konu"
        GoalType.CUSTOM -> "adet"
    }

    override fun pauseReason(r: PauseReason) = when (r) {
        PauseReason.VISITOR -> "Biri geldi"
        PauseReason.COOKING -> "Yemek"
        PauseReason.CHORES -> "Ev işi, çamaşır"
        PauseReason.PHONE -> "Telefon, arama"
        PauseReason.TOILET -> "Tuvalet"
        PauseReason.FAMILY -> "Aile, çocuk"
        PauseReason.HEALTH -> "Sağlık"
        PauseReason.OTHER -> "Başka bir acil iş"
    }

    override val defaultWindowName = "Ders saati"
    override val defaultDeadlineName = "Süreli hedef"

    override fun notEnoughCoins(missing: Int) = "$missing ders parası eksik"
}

object NavTr : NavStrings {
    override val today = "Bugün"
    override val focus = "Odak"
    override val plan = "Plan"
    override val village = "Köy"
    override val store = "Mağaza"
    override val settings = "Ayarlar"
    override val wallet = "Cüzdan"
    override val miniWork = "Çalışma"
    override val miniBreak = "Mola"
    override val miniPaused = "Duraklatıldı"
    override val miniWaiting = "Sıradaki faz bekleniyor"
    override val villageOnlyTurkish = ""
}
