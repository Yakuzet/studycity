package com.derscalisma.app

import com.derscalisma.app.data.*
import com.derscalisma.app.data.game.*
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File

/**
 * Emülatörde görsel test için dolu bir kayıt üretir: build/sample-save.json
 * (adb ile uygulamanın files/data.json dosyasına kopyalanır).
 */
class SampleSaveWriter {
    @Test
    fun write() {
        val now = System.currentTimeMillis()
        val today = Time.dayKey(now)
        val walls = (6..17).map { GBuilding("wt$it", BType.WALL, 3, it, 5) } +
            (6..17).map { GBuilding("wb$it", BType.WALL, 3, it, 18) } +
            (6..17).map { GBuilding("wl$it", BType.WALL, 2, 5, it) } +
            (6..17).map { GBuilding("wr$it", BType.WALL, 2, 18, it) }
        val g = GameState(
            builders = 3,
            buildings = listOf(
                GBuilding("th", BType.TOWN_HALL, 4, 10, 10),
                GBuilding("hut1", BType.BUILDER_HUT, 1, 2, 2),
                GBuilding("hut2", BType.BUILDER_HUT, 1, 20, 2),
                GBuilding("hut3", BType.BUILDER_HUT, 1, 2, 20),
                GBuilding("mine1", BType.GOLD_MINE, 3, 14, 7, collectedAt = now - 5 * 3_600_000L),
                GBuilding("b1", BType.BARRACKS, 3, 7, 14, upgrade = Upgrade(4, 240 * MINUTE_MS, 90 * MINUTE_MS)),
                GBuilding("c1", BType.ARMY_CAMP, 3, 14, 15),
                GBuilding("a1", BType.ARCHER_TOWER, 4, 7, 7),
                GBuilding("a2", BType.CANNON, 3, 15, 11),
                GBuilding("m1", BType.MAGIC_TOWER, 2, 7, 11),
                GBuilding("l1", BType.LIBRARY, 2, 11, 14),
                GBuilding("ac", BType.ACADEMY, 0, 11, 7, upgrade = Upgrade(1, 60 * MINUTE_MS, 20 * MINUTE_MS)),
                GBuilding("alt", BType.HERO_ALTAR, 3, 20, 9),
                GBuilding("forge", BType.SPELL_FORGE, 1, 20, 13),
                GBuilding("w1", BType.WINDMILL, 1, 2, 14),
                GBuilding("t1", BType.TREE, 1, 3, 7), GBuilding("t2", BType.PINE, 1, 4, 9),
                GBuilding("t3", BType.TREE, 1, 21, 18), GBuilding("f1", BType.FIELD, 1, 2, 11),
                GBuilding("f2", BType.FIELD, 1, 3, 11), GBuilding("f3", BType.FIELD, 1, 2, 12),
                GBuilding("st", BType.STATUE, 1, 9, 16), GBuilding("gm", BType.GOLD_MONUMENT, 1, 13, 13),
                GBuilding("h1", BType.HOUSE, 1, 20, 20),
            ) + walls,
            army = mapOf(TType.SWORD to 15, TType.BREAKER to 4, TType.APPRENTICE to 8, TType.KNIGHT to 2, TType.MAGE to 2),
            troopLevels = mapOf(TType.SWORD to 2),
            spells = mapOf(SType.RAGE to 1, SType.HEAL to 1),
            stars = mapOf(1 to 3, 2 to 3, 3 to 2),
            chests = listOf(ChestKind.WOOD, ChestKind.GOLD),
            potions = 1,
            boss = BossState(Surprises.weekKey(Time.date(now)), 0, 600 * MINUTE_MS, dealtMs = 220 * MINUTE_MS, shownMs = 180 * MINUTE_MS),
            battlesWon = 5,
        )
        val d = AppData(
            coins = 4200,
            totalEarned = 9000,
            bestDailyStreak = 9,
            game = g,
            lastProcessedDay = Time.date(now).minusDays(1).toString(),
            days = mapOf(today to DayRecord(studyMs = 50 * MINUTE_MS, focusYes = 4, focusNo = 1)),
            deadlines = listOf(
                DeadlineGoal("d1", "Fizik kitabı", "sayfa", 400, Time.date(now).minusDays(4).toString(), Time.date(now).plusDays(10).toString(), null, manual = 90),
            ),
        )
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val out = File("build/sample-save.json")
        out.writeText(json.encodeToString(AppData.serializer(), d))
        println("yazıldı: ${out.absolutePath}")
    }
}
