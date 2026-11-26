package com.derscalisma.app

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.derscalisma.app.data.*
import com.derscalisma.app.ui.AppRoot
import com.derscalisma.app.ui.Screen
import com.derscalisma.app.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Her ekranı farklı en-boy oranlarında çizer; düzenin iç içe girmediğini gözle kontrol etmek için.
 * Kayıt: gradlew :app:recordPaparazziDebug  → app/src/test/snapshots/images
 */
@RunWith(Parameterized::class)
class LayoutScreenshotTest(private val deviceName: String, private val device: DeviceConfig) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun devices(): List<Array<Any>> = listOf(
            arrayOf("phone_portrait", DeviceConfig.PIXEL_5),
            arrayOf(
                "phone_landscape",
                DeviceConfig.PIXEL_5.copy(screenWidth = 2340, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE),
            ),
            arrayOf(
                "small_landscape",
                DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE),
            ),
            arrayOf("tablet_portrait", DeviceConfig.PIXEL_C.copy(screenWidth = 1800, screenHeight = 2560, orientation = ScreenOrientation.PORTRAIT)),
            arrayOf("tablet_landscape", DeviceConfig.PIXEL_C),
        )
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = device, maxPercentDifference = 100.0)

    private fun sample(background: String = "bg_bamboo"): AppData {
        val now = System.currentTimeMillis()
        val today = Time.dayKey(now)
        return AppData(
            coins = 1240,
            background = background,
            bestDailyStreak = 9,
            streakFreezes = 1,
            settings = Settings(focusBeepEnabled = true),
            windows = listOf(
                StudyWindow("w1", "Akşam Çalışması", 17 * 60, 19 * 60, (1..5).toSet(), 90),
                StudyWindow("w2", "Sabah Tekrarı", 8 * 60, 9 * 60, (1..7).toSet(), 45),
            ),
            goals = defaultGoals() + Goal("g3", GoalType.TOPICS, "Konu", 2, "konu"),
            days = mapOf(
                today to DayRecord(
                    studyMs = 75 * MINUTE_MS,
                    progress = mapOf("g_pages" to 22, "g_questions" to 60),
                    goalsMet = setOf("g_questions"),
                    focusYes = 7,
                    focusNo = 2,
                    coinsEarned = 140,
                )
            ),
            timer = TimerState(
                workMinutes = 25, breakMinutes = 5, rounds = 4, round = 2,
                phaseStartedAt = now - 10 * MINUTE_MS, phaseEndAt = now + 15 * MINUTE_MS,
                startedAt = now - 40 * MINUTE_MS, sessionFocusYes = 3, sessionFocusNo = 1,
            ),
            log = listOf(
                LogEvent(now, "🔥 Günlük hedefe ulaştın! Seri: 9 gün  +235 🪙", 235),
                LogEvent(now, "📝 Soru / Test hedefi tamam! Seri: 4 gün  +41 🪙", 41),
            ),
        )
    }

    private fun shot(screen: Screen, data: AppData = sample()) {
        paparazzi.snapshot(name = "${screen.name.lowercase()}_$deviceName") {
            AppTheme(data.theme) { AppRoot(data, initialScreen = screen) }
        }
    }

    @Test fun home() = shot(Screen.HOME)
    @Test fun timer() = shot(Screen.TIMER)
    @Test fun goals() = shot(Screen.GOALS)
    @Test fun windows() = shot(Screen.WINDOWS)
    @Test fun market() = shot(Screen.MARKET, sample(background = DEFAULT_BG))
}
