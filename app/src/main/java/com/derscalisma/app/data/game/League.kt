package com.derscalisma.app.data.game

/** Toplam sefer yıldızına göre lig; üst ligler ganimete bonus verir. */
enum class League(val title: String, val emoji: String, val minStars: Int, val lootBonus: Int) {
    BRONZE("Bronz Lig", "🥉", 0, 0),
    SILVER("Gümüş Lig", "🥈", 15, 5),
    GOLD("Altın Lig", "🥇", 35, 10),
    DIAMOND("Elmas Lig", "💎", 60, 15),
    LEGEND("Efsane Lig", "👑", 80, 25);

    companion object {
        fun of(stars: Int): League = entries.last { stars >= it.minStars }
        fun next(stars: Int): League? = entries.firstOrNull { it.minStars > stars }
    }
}
