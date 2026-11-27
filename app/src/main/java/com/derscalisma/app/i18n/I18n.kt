package com.derscalisma.app.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.derscalisma.app.i18n.de.DeStrings
import com.derscalisma.app.i18n.en.EnStrings
import com.derscalisma.app.i18n.fr.FrStrings
import com.derscalisma.app.i18n.ja.JaStrings
import com.derscalisma.app.i18n.ru.RuStrings
import com.derscalisma.app.i18n.tr.TrStrings
import com.derscalisma.app.i18n.zh.ZhStrings
import java.util.Locale

/**
 * Uygulama dilleri. Metinler Kotlin nesnelerinde durur: her dil her metni sağlamak zorundadır
 * (eksik çeviri derleme hatası verir). Parametreli metinler fonksiyondur, çoğul kuralları dile göre yazılır.
 */
enum class Lang(val code: String, val nativeName: String, val locale: Locale) {
    TR("tr", "Türkçe", Locale("tr", "TR")),
    EN("en", "English", Locale.US),
    DE("de", "Deutsch", Locale.GERMANY),
    FR("fr", "Français", Locale.FRANCE),
    RU("ru", "Русский", Locale("ru", "RU")),
    JA("ja", "日本語", Locale.JAPAN),
    ZH("zh", "简体中文", Locale.SIMPLIFIED_CHINESE);

    val strings: Strings
        get() = when (this) {
            TR -> TrStrings
            EN -> EnStrings
            DE -> DeStrings
            FR -> FrStrings
            RU -> RuStrings
            JA -> JaStrings
            ZH -> ZhStrings
        }

    /** Japonca ve Çince: büyük/küçük harf yok, satır aralığı biraz daha geniş. */
    val cjk: Boolean get() = this == JA || this == ZH
}

object I18n {
    /** Geçerli dil. Compose durumu: okuyan ekranlar dil değişince yeniden çizilir. */
    var lang: Lang by mutableStateOf(Lang.TR)

    /** Ayardaki dil kodu; boş ya da bilinmiyorsa telefonun dili, o da yoksa İngilizce. */
    fun resolve(code: String): Lang = Lang.entries.find { it.code == code } ?: system()

    fun system(): Lang {
        val sys = Locale.getDefault().language
        return Lang.entries.find { it.code == sys } ?: Lang.EN
    }

    fun apply(code: String) {
        val l = resolve(code)
        if (l != lang) lang = l
    }
}

/** Geçerli dilin metinleri. */
val S: Strings get() = I18n.lang.strings

/** Geçerli dilin yerel ayarı (tarih, sayı biçimleri için). */
val L: Locale get() = I18n.lang.locale

/** Bir dilin bütün metinleri, ekran ekran bölümlere ayrılmış. */
class Strings(
    val common: CommonStrings,
    val nav: NavStrings,
    val today: TodayStrings,
    val focus: FocusStrings,
    val rewards: RewardStrings,
    val plan: PlanStrings,
    val settings: SettingsStrings,
    val store: StoreStrings,
    val onboarding: OnboardingStrings,
    val notify: NotifyStrings,
    val engine: EngineStrings,
)

/** Rusça çoğul: 1 / 2-4 / 5+ (11-14 hep "many"). */
fun ruPlural(n: Int, one: String, few: String, many: String): String {
    val a = kotlin.math.abs(n)
    val m10 = a % 10
    val m100 = a % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}

/** İngilizce / Almanca / Fransızca tarzı tekil-çoğul. Fransızcada 0 ve 1 tekildir. */
fun plural(n: Int, one: String, other: String, zeroIsOne: Boolean = false): String =
    if (n == 1 || (zeroIsOne && n == 0)) one else other
