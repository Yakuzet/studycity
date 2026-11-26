package com.derscalisma.app.data

import com.derscalisma.app.R

const val DEFAULT_BG = "bg_none"
const val DEFAULT_THEME = "theme_default"
const val DEFAULT_GROUND = "ground_grass"
const val DEFAULT_TIMER_STYLE = "timer_classic"
const val FREEZE_ID = "streak_freeze"
const val MAX_FREEZES = 3

object Rewards {
    const val COINS_PER_MINUTE = 1
    const val ATTEND_MS = 5 * MINUTE_MS

    /** En ödüllendirici seri: günlük hedef. */
    fun daily(streak: Int) = 100 + 15 * streak.coerceAtMost(30)
    fun windowGoal(streak: Int) = 40 + 6 * streak.coerceAtMost(30)
    fun windowAttend(streak: Int) = 15 + 2 * streak.coerceAtMost(30)
    fun goal(streak: Int) = 25 + 4 * streak.coerceAtMost(30)
    fun sessionComplete(rounds: Int) = 5 * rounds

    val milestones = mapOf(
        3 to 100, 7 to 300, 14 to 600, 30 to 1500, 60 to 3000, 100 to 6000, 365 to 20000,
    )
}

data class Building(
    val id: String,
    val name: String,
    val emoji: String,
    val price: Int,
    val population: Int,
    /** Açılması için gereken en iyi günlük hedef serisi. */
    val unlockStreak: Int,
)

enum class ItemCategory(val title: String) {
    BACKGROUND("🖼️ Arka Planlar"),
    THEME("🎨 Temalar"),
    TIMER("⏱️ Zamanlayıcı"),
    GROUND("🌍 Şehir Zemini"),
    BOOST("⚡ Güçlendirme"),
}

data class MarketItem(
    val id: String,
    val name: String,
    val emoji: String,
    val price: Int,
    val category: ItemCategory,
    val description: String,
)

data class ThemeDef(
    val id: String,
    val dark: Boolean,
    val primary: Long,
    val secondary: Long,
    val tertiary: Long,
    val background: Long,
    val surface: Long,
)

data class GroundDef(val id: String, val base: Long, val alt: Long)

/** Fotoğraflar Unsplash lisanslıdır (Lorem Picsum üzerinden), renkleri uygulama için iyileştirildi. */
data class BackgroundDef(
    val id: String,
    val image: Int,
    val thumb: Int,
    val photographer: String,
    val mood: Long,
)

enum class TimerStyleKind { CLASSIC, BOLD, DOTS, GRADIENT }

object Catalog {

    val buildings = listOf(
        Building("tent", "Çadır", "⛺", 15, 1, 0),
        Building("tree", "Ağaç", "🌳", 20, 0, 0),
        Building("flowers", "Çiçek Bahçesi", "🌷", 30, 0, 0),
        Building("house", "Ev", "🏠", 60, 4, 0),
        Building("garden_house", "Bahçeli Ev", "🏡", 120, 6, 1),
        Building("cafe", "Kafe", "☕", 150, 2, 2),
        Building("fountain", "Çeşme", "⛲", 180, 0, 2),
        Building("apartment", "Apartman", "🏢", 300, 20, 3),
        Building("school", "Okul", "🏫", 400, 10, 5),
        Building("library", "Kütüphane", "🏛️", 600, 8, 7),
        Building("hospital", "Hastane", "🏥", 800, 12, 10),
        Building("factory", "Fabrika", "🏭", 900, 15, 12),
        Building("stadium", "Stadyum", "🏟️", 1400, 25, 14),
        Building("ferris", "Lunapark", "🎡", 1800, 10, 18),
        Building("castle", "Kale", "🏰", 2500, 30, 21),
        Building("tower", "Kule", "🗼", 3500, 40, 30),
        Building("university", "Üniversite", "🎓", 5000, 60, 45),
        Building("rocket", "Uzay Üssü", "🚀", 10000, 100, 60),
    )

    val themes = listOf(
        ThemeDef(DEFAULT_THEME, true, 0xFF8AA4FF, 0xFF6EE7B7, 0xFFFFB86B, 0xFF0E1322, 0xFF182036),
        ThemeDef("theme_light", false, 0xFF3F5BD9, 0xFF0E9F6E, 0xFFE07A1F, 0xFFF1F4FA, 0xFFFFFFFF),
        ThemeDef("theme_forest", true, 0xFF7BD88F, 0xFFC9E265, 0xFFFFC56B, 0xFF0F1A13, 0xFF18281D),
        ThemeDef("theme_ocean", true, 0xFF4FD1E8, 0xFF7AA2FF, 0xFFFF9EAA, 0xFF06161F, 0xFF0E2430),
        ThemeDef("theme_sunset", true, 0xFFFF8A65, 0xFFFFC857, 0xFFE76F9B, 0xFF1E1220, 0xFF2B1A2C),
        ThemeDef("theme_lavender", true, 0xFFC3A6FF, 0xFFFFA8D9, 0xFF8FE3CF, 0xFF16121F, 0xFF221C30),
        ThemeDef("theme_sakura", false, 0xFFD6457A, 0xFF7B61C9, 0xFF2E9E8F, 0xFFFFF3F7, 0xFFFFFFFF),
        ThemeDef("theme_gold", true, 0xFFFFD166, 0xFF9AD1D4, 0xFFFF8C61, 0xFF121212, 0xFF1F1D18),
    )

    val grounds = listOf(
        GroundDef(DEFAULT_GROUND, 0xFF5C9E45, 0xFF67A94F),
        GroundDef("ground_sand", 0xFFD9C08A, 0xFFE2CB97),
        GroundDef("ground_snow", 0xFFE3ECF4, 0xFFD5E1EC),
        GroundDef("ground_spring", 0xFFF2C6D8, 0xFFF6D3E1),
        GroundDef("ground_night", 0xFF1C2541, 0xFF222C4B),
    )

    val timerStyles = mapOf(
        DEFAULT_TIMER_STYLE to TimerStyleKind.CLASSIC,
        "timer_bold" to TimerStyleKind.BOLD,
        "timer_dots" to TimerStyleKind.DOTS,
        "timer_gradient" to TimerStyleKind.GRADIENT,
    )

    val backgrounds = listOf(
        BackgroundDef("bg_aurora", R.drawable.bg_aurora, R.drawable.bg_aurora_thumb, "Nelly Volkovich", 0xFF83463D),
        BackgroundDef("bg_stars", R.drawable.bg_stars, R.drawable.bg_stars_thumb, "ahmadreza sajadi", 0xFF1D2D45),
        BackgroundDef("bg_clouds", R.drawable.bg_clouds, R.drawable.bg_clouds_thumb, "Joshua Earle", 0xFFAEB4B7),
        BackgroundDef("bg_waterfall", R.drawable.bg_waterfall, R.drawable.bg_waterfall_thumb, "Jeff Sheldon", 0xFF528AA0),
        BackgroundDef("bg_dunes", R.drawable.bg_dunes, R.drawable.bg_dunes_thumb, "Luca Zanon", 0xFFEE5406),
        BackgroundDef("bg_winter", R.drawable.bg_winter, R.drawable.bg_winter_thumb, "Ali Inay", 0xFFCFD9E6),
        BackgroundDef("bg_sunset", R.drawable.bg_sunset, R.drawable.bg_sunset_thumb, "Leah Tardivel", 0xFFF19762),
        BackgroundDef("bg_bamboo", R.drawable.bg_bamboo, R.drawable.bg_bamboo_thumb, "Ståle Grut", 0xFF4B652E),
        BackgroundDef("bg_autumn", R.drawable.bg_autumn, R.drawable.bg_autumn_thumb, "Rula Sibai", 0xFFEC5E01),
        BackgroundDef("bg_fjord", R.drawable.bg_fjord, R.drawable.bg_fjord_thumb, "Jeff Sheldon", 0xFF3D7BB6),
        BackgroundDef("bg_tulips", R.drawable.bg_tulips, R.drawable.bg_tulips_thumb, "Leigh Kendell", 0xFFAE8253),
        BackgroundDef("bg_nightcity", R.drawable.bg_nightcity, R.drawable.bg_nightcity_thumb, "Anders Jildén", 0xFF41295B),
    )

    val items = listOf(
        MarketItem(DEFAULT_BG, "Sade", "⬛", 0, ItemCategory.BACKGROUND, "Fotoğrafsız, temanın düz rengi"),
        MarketItem("bg_tulips", "Lale Bahçesi", "🌷", 250, ItemCategory.BACKGROUND, "Canlı turuncu laleler"),
        MarketItem("bg_bamboo", "Bambu Ormanı", "🎋", 300, ItemCategory.BACKGROUND, "Yükselen yeşil bambular, huzur veren odak"),
        MarketItem("bg_winter", "Karlı Yol", "❄️", 300, ItemCategory.BACKGROUND, "Sessiz, beyaz bir kış ormanı"),
        MarketItem("bg_autumn", "Sonbahar Parkı", "🍂", 350, ItemCategory.BACKGROUND, "Turuncu yapraklar arasında bank"),
        MarketItem("bg_fjord", "Fiyort", "🏞️", 400, ItemCategory.BACKGROUND, "Dağların arasından kıvrılan mavi su"),
        MarketItem("bg_waterfall", "Şelale", "💧", 450, ItemCategory.BACKGROUND, "Yeşillikler içinde akan şelale"),
        MarketItem("bg_dunes", "Çöl Kumulları", "🏜️", 500, ItemCategory.BACKGROUND, "Göğe uzanan turuncu kumul"),
        MarketItem("bg_clouds", "Bulutların Üstü", "☁️", 600, ItemCategory.BACKGROUND, "Zirvede, bulut denizine bakış"),
        MarketItem("bg_sunset", "Gün Batımı", "🌅", 700, ItemCategory.BACKGROUND, "Denizde pembe ve turuncu gökyüzü"),
        MarketItem("bg_nightcity", "Gece Şehri", "🌉", 800, ItemCategory.BACKGROUND, "Köprü ışıkları ve şehir silueti"),
        MarketItem("bg_stars", "Yıldız İzleri", "🌠", 1000, ItemCategory.BACKGROUND, "Uzun pozlamada dönen yıldızlar"),
        MarketItem("bg_aurora", "Kuzey Işıkları", "🌌", 1500, ItemCategory.BACKGROUND, "Karlı ormanda dans eden aurora"),

        MarketItem(DEFAULT_THEME, "Gece Mavisi", "🌌", 0, ItemCategory.THEME, "Varsayılan karanlık tema"),
        MarketItem("theme_light", "Gündüz", "☀️", 150, ItemCategory.THEME, "Ferah, aydınlık bir tema"),
        MarketItem("theme_forest", "Orman", "🌲", 300, ItemCategory.THEME, "Yeşil tonlarda sakin tema"),
        MarketItem("theme_ocean", "Okyanus", "🌊", 300, ItemCategory.THEME, "Derin mavi ve turkuaz"),
        MarketItem("theme_sunset", "Gün Batımı", "🌇", 500, ItemCategory.THEME, "Turuncu ve pembe sıcak tonlar"),
        MarketItem("theme_lavender", "Lavanta", "💜", 500, ItemCategory.THEME, "Yumuşak mor tonlar"),
        MarketItem("theme_sakura", "Sakura", "🌸", 700, ItemCategory.THEME, "Pembe, aydınlık bahar teması"),
        MarketItem("theme_gold", "Altın Çağ", "👑", 1500, ItemCategory.THEME, "Siyah ve altın, en prestijli tema"),

        MarketItem(DEFAULT_TIMER_STYLE, "Klasik Halka", "⭕", 0, ItemCategory.TIMER, "Sade ilerleme halkası"),
        MarketItem("timer_bold", "Kalın Halka", "🟠", 200, ItemCategory.TIMER, "Kalın ve dikkat çekici halka"),
        MarketItem("timer_dots", "Noktalı Halka", "⚪", 300, ItemCategory.TIMER, "Noktalardan oluşan halka"),
        MarketItem("timer_gradient", "Gökkuşağı Halka", "🌈", 500, ItemCategory.TIMER, "Renk geçişli halka"),

        MarketItem(DEFAULT_GROUND, "Çimen", "🌱", 0, ItemCategory.GROUND, "Yeşil çimen zemin"),
        MarketItem("ground_sand", "Çöl", "🏜️", 200, ItemCategory.GROUND, "Kumlu çöl zemini"),
        MarketItem("ground_snow", "Kar", "❄️", 400, ItemCategory.GROUND, "Karlı kış şehri"),
        MarketItem("ground_spring", "Bahar", "🌸", 600, ItemCategory.GROUND, "Pembe bahar zemini"),
        MarketItem("ground_night", "Gece Şehri", "🌃", 800, ItemCategory.GROUND, "Gece ışıkları altında şehir"),

        MarketItem(FREEZE_ID, "Seri Dondurucu", "🧊", 250, ItemCategory.BOOST,
            "Bir gün hedefi kaçırırsan günlük serini otomatik korur. En fazla $MAX_FREEZES tane."),
    )

    fun building(id: String) = buildings.find { it.id == id }
    fun item(id: String) = items.find { it.id == id }
    fun theme(id: String) = themes.find { it.id == id } ?: themes.first()
    fun ground(id: String) = grounds.find { it.id == id } ?: grounds.first()
    fun timerStyle(id: String) = timerStyles[id] ?: TimerStyleKind.CLASSIC
    fun background(id: String) = backgrounds.find { it.id == id }
}
