package com.derscalisma.app.data.game

import kotlin.random.Random

data class EnemyBuilding(val type: BType, val level: Int, val x: Int, val y: Int)

data class EnemyVillage(
    val level: Int,
    val name: String,
    val th: Int,
    val buildings: List<EnemyBuilding>,
    val loot: Int,
) {
    val defenses get() = buildings.count { it.type.cat == BCat.DEFENSE }
}

/** Sefer haritası: her seviye sabit tohumla üretilir, yani her seferinde aynı köy çıkar. */
object Campaign {
    const val LEVELS = 30

    private val names = listOf(
        "Çamlıtepe", "Kayabaşı", "Dereköy", "Yeşilova", "Akpınar", "Karaağaç", "Taşlıca", "Kuzeykale",
        "Sisli Vadi", "Bozkır Hanı", "Kızılkule", "Gölbaşı", "Demirkapı", "Rüzgârlı Tepe", "Altınsu",
        "Kartalyuva", "Buzlu Geçit", "Ateşdağ", "Kumluca", "Sarp Kaya", "Gümüşhan", "Fırtına Kalesi",
        "Ejder Yuvası", "Gölge Ormanı", "Yıldız Kalesi", "Taş Burç", "Kara Sur", "Tunç Kale", "Bulut Kale",
        "Zirve Kalesi",
    )

    /** Tamamen yıldızlanmış seviyeler tekrar oynanınca ganimetin bu kadarı verilir. */
    const val REPLAY_LOOT_FACTOR = 0.2f

    fun starBonus(level: Int) = 40 * level + 60

    fun village(level: Int): EnemyVillage {
        val rng = Random(level * 7919 + 13)
        val th = (1 + (level - 1) / 5).coerceAtMost(6)
        val defLevel = (1 + (level - 1) / 4).coerceAtMost(6)
        val list = mutableListOf<EnemyBuilding>()
        val occ = Array(VILLAGE_SIZE) { BooleanArray(VILLAGE_SIZE) }

        fun free(x: Int, y: Int, s: Int): Boolean {
            for (i in x until x + s) for (j in y until y + s) if (occ[i][j]) return false
            return true
        }

        fun mark(x: Int, y: Int, s: Int) {
            for (i in x until x + s) for (j in y until y + s) occ[i][j] = true
        }

        fun place(type: BType, lvl: Int, lo: Int, hi: Int) {
            val s = type.size
            repeat(300) {
                val x = rng.nextInt(lo, hi - s + 1)
                val y = rng.nextInt(lo, hi - s + 1)
                if (free(x, y, s)) {
                    mark(x, y, s)
                    list += EnemyBuilding(type, lvl, x, y)
                    return
                }
            }
        }

        // Merkez Kale ortada.
        mark(10, 10, 3)
        list += EnemyBuilding(BType.TOWN_HALL, th, 10, 10)

        val archers = (1 + level / 3).coerceAtMost(6)
        val cannons = (level / 4).coerceAtMost(4)
        val magic = ((level - 8) / 6).coerceIn(0, 3)
        repeat(archers) { place(BType.ARCHER_TOWER, defLevel, 6, 18) }
        repeat(cannons) { place(BType.CANNON, defLevel, 6, 18) }
        repeat(magic) { place(BType.MAGIC_TOWER, (defLevel - 2).coerceIn(1, 4), 7, 17) }

        val other = (defLevel).coerceAtMost(5)
        repeat(1 + level / 10) { place(BType.BARRACKS, other, 4, 20) }
        repeat(1 + level / 8) { place(BType.ARMY_CAMP, other, 4, 20) }
        repeat((1 + level / 6).coerceAtMost(3)) { place(BType.GOLD_MINE, other, 4, 20) }
        if (level >= 4) place(BType.LIBRARY, other, 4, 20)
        repeat((2 + level / 5).coerceAtMost(6)) { place(BType.HOUSE, 1, 4, 20) }
        repeat(1 + level / 12) { place(BType.BUILDER_HUT, 1, 4, 20) }

        return EnemyVillage(level, names[(level - 1) % names.size], th, list, loot = 150 + 60 * level)
    }
}
