package com.derscalisma.app

import com.derscalisma.app.data.AppData
import com.derscalisma.app.data.game.*
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Kayıt dosyası: yeni oyun alanları yazılıp okunabilmeli, eski (oyunsuz) kayıtlar da açılmalı. */
class SaveFormatTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    @Test
    fun roundTripWithGame() {
        val d = AppData(
            coins = 999,
            game = GameState(
                army = mapOf(TType.SWORD to 5, TType.MAGE to 2),
                troopLevels = mapOf(TType.SWORD to 3),
                stars = mapOf(1 to 3, 2 to 1),
                queue = listOf(TrainJob(TType.KNIGHT, 2)),
                research = Research(TType.APPRENTICE, 2, 60_000),
            ),
        )
        val back = json.decodeFromString(AppData.serializer(), json.encodeToString(AppData.serializer(), d))
        assertEquals(d, back)
    }

    @Test
    fun oldSaveWithoutGameLoads() {
        val old = """{"coins": 321, "city": [{"x": 1, "y": 2, "buildingId": "house"}]}"""
        val d = json.decodeFromString(AppData.serializer(), old)
        assertEquals(321, d.coins)
        assertEquals(1, d.city.size)
        assertEquals(BType.TOWN_HALL, d.game.buildings.first().type)
    }
}
