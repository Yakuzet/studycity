package com.derscalisma.app.data.game

/**
 * Oyun dengesi. Yükseltmeler para + ders süresi ister; inşaatlar sadece ders çalışırken ilerler.
 * Tablolarda indeks = hedef seviye (0. eleman kullanılmaz).
 */
object GameCatalog {

    private val COST = mapOf(
        BType.TOWN_HALL to intArrayOf(0, 0, 1000, 2500, 5000, 9000, 15000),
        BType.BARRACKS to intArrayOf(0, 150, 600, 1500, 3500, 7000),
        BType.ARMY_CAMP to intArrayOf(0, 200, 800, 2000, 4500, 8000),
        BType.ACADEMY to intArrayOf(0, 800, 2500, 6000, 12000),
        BType.ARCHER_TOWER to intArrayOf(0, 250, 700, 1600, 3500, 7000, 12000),
        BType.CANNON to intArrayOf(0, 400, 1000, 2200, 4500, 8500, 14000),
        BType.MAGIC_TOWER to intArrayOf(0, 2000, 5000, 10000, 18000),
        BType.LIBRARY to intArrayOf(0, 500, 1500, 3500, 7000, 12000),
        BType.GOLD_MINE to intArrayOf(0, 250, 800, 2000, 4500, 8000),
        BType.TREE to intArrayOf(0, 30),
        BType.PINE to intArrayOf(0, 30),
        BType.BUSH to intArrayOf(0, 20),
        BType.FIELD to intArrayOf(0, 60),
        BType.HOUSE to intArrayOf(0, 150),
        BType.STALL to intArrayOf(0, 200),
        BType.STATUE to intArrayOf(0, 500),
        BType.WINDMILL to intArrayOf(0, 900),
    )

    /** Gereken ders süresi (dakika). */
    private val STUDY = mapOf(
        BType.TOWN_HALL to intArrayOf(0, 0, 120, 240, 420, 600, 900),
        BType.BARRACKS to intArrayOf(0, 10, 45, 120, 240, 420),
        BType.ARMY_CAMP to intArrayOf(0, 15, 60, 150, 300, 480),
        BType.ACADEMY to intArrayOf(0, 60, 180, 360, 600),
        BType.ARCHER_TOWER to intArrayOf(0, 15, 60, 150, 300, 480, 720),
        BType.CANNON to intArrayOf(0, 20, 75, 180, 360, 540, 780),
        BType.MAGIC_TOWER to intArrayOf(0, 120, 300, 540, 840),
        BType.LIBRARY to intArrayOf(0, 30, 90, 200, 360, 540),
        BType.GOLD_MINE to intArrayOf(0, 15, 60, 150, 300, 480),
    )

    /** Merkez Kale seviyesine (1..6) göre en fazla kaç tane yapılabilir. */
    private val LIMIT = mapOf(
        BType.BARRACKS to intArrayOf(1, 1, 2, 2, 3, 3),
        BType.ARMY_CAMP to intArrayOf(1, 1, 2, 2, 3, 3),
        BType.ACADEMY to intArrayOf(0, 0, 1, 1, 1, 1),
        BType.ARCHER_TOWER to intArrayOf(1, 2, 2, 3, 3, 4),
        BType.CANNON to intArrayOf(0, 1, 1, 2, 2, 3),
        BType.MAGIC_TOWER to intArrayOf(0, 0, 0, 1, 1, 2),
        BType.LIBRARY to intArrayOf(0, 1, 1, 1, 1, 1),
        BType.GOLD_MINE to intArrayOf(1, 1, 2, 2, 3, 3),
    )

    /** Süs eşyaları en iyi günlük seriyle açılır. */
    val DECOR_UNLOCK = mapOf(
        BType.HOUSE to 3, BType.STALL to 5, BType.STATUE to 7, BType.WINDMILL to 14,
    )

    /** 2., 3., 4., 5. inşaatçının fiyatı. Pahalı olmaları bilerek. */
    val BUILDER_COST = intArrayOf(0, 0, 1500, 4000, 8000, 15000)
    const val MAX_BUILDERS = 5

    const val DECOR_LIMIT = 40

    fun cost(t: BType, level: Int): Int = COST[t]?.getOrNull(level) ?: 0
    fun studyMinutes(t: BType, level: Int): Int = STUDY[t]?.getOrNull(level) ?: 0

    fun limit(t: BType, th: Int): Int = when {
        t.cat == BCat.DECOR -> DECOR_LIMIT
        t == BType.TOWN_HALL || t == BType.BUILDER_HUT -> 1
        else -> LIMIT[t]?.getOrNull((th - 1).coerceIn(0, 5)) ?: 0
    }

    /** Bir binanın çıkabileceği en yüksek seviye (Merkez Kale seviyesi +1, kendi sınırına kadar). */
    fun maxLevelAt(t: BType, th: Int): Int =
        if (t == BType.TOWN_HALL) t.maxLevel else minOf(t.maxLevel, th + 1)

    /** Bir sonraki Merkez Kale seviyesinde açılan ilk bina limitini bulur (ipucu metni için). */
    fun unlockHint(t: BType, th: Int): String? {
        if (t.cat == BCat.DECOR) return DECOR_UNLOCK[t]?.let { "$it günlük seri gerekli" }
        for (lvl in th + 1..6) if (limit(t, lvl) > limit(t, th)) return "Merkez Kale $lvl gerekli"
        return null
    }

    // ---- Bina istatistikleri

    fun hp(t: BType, level: Int): Float {
        val base = when (t) {
            BType.TOWN_HALL -> 1500f
            BType.ARCHER_TOWER -> 420f
            BType.CANNON -> 520f
            BType.MAGIC_TOWER -> 460f
            BType.BUILDER_HUT -> 250f
            BType.HOUSE, BType.WINDMILL -> 220f
            else -> 320f
        }
        return base * (1f + 0.3f * (level.coerceAtLeast(1) - 1))
    }

    fun armyCapacity(level: Int) = intArrayOf(0, 15, 25, 35, 45, 60).getOrElse(level) { 0 }
    fun libraryBonusPercent(level: Int) = intArrayOf(0, 5, 10, 15, 20, 30).getOrElse(level) { 0 }
    fun mineRatePerHour(level: Int) = intArrayOf(0, 8, 12, 18, 26, 36).getOrElse(level) { 0 }
    const val MINE_CAP_HOURS = 10

    data class DefenseStats(val dps: Float, val range: Float, val interval: Float, val splash: Float)

    fun defense(t: BType, level: Int): DefenseStats? {
        val l = level.coerceAtLeast(1)
        return when (t) {
            BType.ARCHER_TOWER -> DefenseStats(intArrayOf(0, 12, 16, 21, 27, 34, 42)[l.coerceAtMost(6)].toFloat(), 6.5f, 0.8f, 0f)
            BType.CANNON -> DefenseStats(intArrayOf(0, 22, 29, 37, 47, 59, 72)[l.coerceAtMost(6)].toFloat(), 5.5f, 1.4f, 0.4f)
            BType.MAGIC_TOWER -> DefenseStats(intArrayOf(0, 18, 24, 32, 42)[l.coerceAtMost(4)].toFloat(), 5.0f, 1.6f, 1.3f)
            else -> null
        }
    }

    // ---- Askerler

    /** Asker seviyesi çarpanı (can ve hasar). */
    fun troopMultiplier(level: Int) = 1f + 0.18f * (level.coerceAtLeast(1) - 1)

    const val MAX_TROOP_LEVEL = 5

    fun researchCost(t: TType, toLevel: Int): Int {
        val base = intArrayOf(0, 0, 500, 1500, 4000, 9000).getOrElse(toLevel) { 0 }
        return (base * (1f + 0.25f * (t.ordinal))).toInt()
    }

    fun researchStudyMinutes(t: TType, toLevel: Int): Int {
        val base = intArrayOf(0, 0, 30, 90, 240, 480).getOrElse(toLevel) { 0 }
        return (base * (1f + 0.15f * (t.ordinal))).toInt()
    }

    /** Akademi seviyesi kaç asker seviyesine izin verir. */
    fun maxTroopLevel(academyLevel: Int) = if (academyLevel <= 0) 1 else minOf(MAX_TROOP_LEVEL, academyLevel + 1)
}
