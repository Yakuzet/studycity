package com.derscalisma.app

import com.derscalisma.app.data.*
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Test

/** v1.0 / v1.1 biçimindeki dolu bir kaydın yeni sürümde okunabildiğini doğrular. */
class OldSaveTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    @Test
    fun v10ShapedSaveDecodes() {
        val now = System.currentTimeMillis()
        val rich = AppData(
            coins = 1234, totalEarned = 5000, bestDailyStreak = 5, streakFreezes = 1,
            windows = listOf(StudyWindow("w1", "Akşam", 1020, 1140, setOf(1, 2, 3), 90)),
            days = mapOf("2026-11-25" to DayRecord(studyMs = 7_200_000, dailyMet = true, goalsMet = setOf("g_pages"), progress = mapOf("g_pages" to 40), windowMs = mapOf("w1" to 3_600_000))),
            timer = TimerState(25, 5, 4, round = 2, phase = Phase.BREAK, phaseStartedAt = now, phaseEndAt = now + 300_000, startedAt = now - 1_800_000),
            lastSession = SessionSummary(1_500_000, now),
            city = listOf(PlacedBuilding(1, 2, "house")),
            log = listOf(LogEvent(now, "🔥 Günlük hedefe ulaştın!  +175 🪙", 175)),
        )
        val obj = json.encodeToJsonElement(AppData.serializer(), rich).jsonObject.toMutableMap()
        // v1.0'da olmayan alanları çıkar
        listOf("game", "background", "focusAsk").forEach { obj.remove(it) }
        obj["settings"] = JsonObject(obj["settings"]!!.jsonObject.filterKeys { !it.startsWith("focus") })
        obj["timer"] = JsonObject(obj["timer"]!!.jsonObject.filterKeys { it !in setOf("nextBeepAt", "checksDone", "sessionFocusYes", "sessionFocusNo") })
        obj["lastSession"] = JsonObject(obj["lastSession"]!!.jsonObject.filterKeys { it !in setOf("focusYes", "focusNo") })
        obj["days"] = JsonObject(obj["days"]!!.jsonObject.mapValues { (_, v) -> JsonObject(v.jsonObject.filterKeys { it !in setOf("focusYes", "focusNo") }) })
        val old = JsonObject(obj).toString()

        val back = json.decodeFromString(AppData.serializer(), old)
        assertEquals(1234, back.coins)
        assertEquals(5, back.bestDailyStreak)
        assertEquals(1, back.windows.size)
        assertEquals(true, back.days["2026-11-25"]?.dailyMet)
        assertEquals(2, back.timer?.round)
    }
}
