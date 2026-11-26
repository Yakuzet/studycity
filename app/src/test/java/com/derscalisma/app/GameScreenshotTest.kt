package com.derscalisma.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.derscalisma.app.ui.LayoutInfo
import com.derscalisma.app.ui.LocalLayout
import com.derscalisma.app.ui.game.CityScreen
import com.derscalisma.app.ui.game.CityTab
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.derscalisma.app.data.*
import com.derscalisma.app.data.game.*
import com.derscalisma.app.ui.AppRoot
import com.derscalisma.app.ui.Screen
import com.derscalisma.app.ui.game.BattleFrame
import com.derscalisma.app.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test

/** Köy oyununun görsel kontrolü. Kayıt: gradlew :app:recordPaparazziDebug */
class GameScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 2340, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE),
        maxPercentDifference = 100.0,
    )

    private fun sampleVillage(): GameState {
        val now = System.currentTimeMillis()
        return GameState(
            builders = 2,
            buildings = listOf(
                GBuilding("th", BType.TOWN_HALL, 2, 10, 10),
                GBuilding("hut1", BType.BUILDER_HUT, 1, 6, 11),
                GBuilding("hut2", BType.BUILDER_HUT, 1, 3, 3),
                GBuilding("mine1", BType.GOLD_MINE, 2, 14, 11, collectedAt = now - 3 * 3_600_000L),
                GBuilding("b1", BType.BARRACKS, 2, 7, 14, upgrade = Upgrade(3, 120 * MINUTE_MS, 50 * MINUTE_MS)),
                GBuilding("c1", BType.ARMY_CAMP, 1, 14, 15),
                GBuilding("a1", BType.ARCHER_TOWER, 2, 10, 6),
                GBuilding("a2", BType.CANNON, 1, 16, 7, upgrade = Upgrade(2, 75 * MINUTE_MS, 10 * MINUTE_MS)),
                GBuilding("l1", BType.LIBRARY, 0, 5, 7, upgrade = Upgrade(1, 30 * MINUTE_MS, 12 * MINUTE_MS)),
                GBuilding("w1", BType.WINDMILL, 1, 18, 12),
                GBuilding("t1", BType.TREE, 1, 3, 18), GBuilding("t2", BType.PINE, 1, 4, 19),
                GBuilding("t3", BType.TREE, 1, 19, 3), GBuilding("f1", BType.FIELD, 1, 18, 15),
                GBuilding("f2", BType.FIELD, 1, 19, 15), GBuilding("s1", BType.STATUE, 1, 12, 14),
            ),
            army = mapOf(TType.SWORD to 12, TType.APPRENTICE to 6),
            queue = listOf(TrainJob(TType.APPRENTICE, 3)),
            queueStartedAt = now - 5000,
            stars = mapOf(1 to 3, 2 to 2, 3 to 1),
        )
    }

    private fun shot(name: String, screen: Screen) {
        val data = AppData(coins = 2350, game = sampleVillage(), bestDailyStreak = 9)
        paparazzi.snapshot(name) { AppTheme(data.theme) { AppRoot(data, initialScreen = screen) } }
    }

    @Test fun village() = shot("village", Screen.CITY)

    @Test
    fun villagePortrait() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5)
        shot("village_portrait", Screen.CITY)
    }

    private fun tab(name: String, tab: CityTab) {
        val data = AppData(coins = 2350, game = sampleVillage(), bestDailyStreak = 9)
        paparazzi.snapshot(name) {
            AppTheme(data.theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
                        CompositionLocalProvider(LocalLayout provides LayoutInfo(maxWidth, maxHeight)) {
                            CityScreen(data, System.currentTimeMillis(), {}, {}, initialTab = tab)
                        }
                    }
                }
            }
        }
    }

    @Test fun army() = tab("army", CityTab.ARMY)
    @Test fun campaign() = tab("campaign", CityTab.CAMPAIGN)

    @Test
    fun battleFrame() {
        val e = BattleEngine(Campaign.village(6), mapOf(TType.SWORD to 15, TType.APPRENTICE to 10, TType.KNIGHT to 2, TType.MAGE to 2), emptyMap(), seed = 3)
        repeat(15) { e.deploy(TType.SWORD, 1.2f, 4f + it * 0.9f) }
        repeat(10) { e.deploy(TType.APPRENTICE, 4f + it, 22.6f) }
        repeat(2) { e.deploy(TType.KNIGHT, 1.0f, 12f + it) }
        repeat(2) { e.deploy(TType.MAGE, 2f + it, 22.7f) }
        repeat((14f / 0.033f).toInt()) { e.step(0.033f) }
        paparazzi.snapshot("battle") {
            BattleFrame(e, Modifier.fillMaxSize().background(Color(0xFF2E4A26)))
        }
    }
}
