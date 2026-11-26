package com.derscalisma.app

import com.derscalisma.app.data.AppData
import com.derscalisma.app.data.SaveStore
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SaveStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    @Test
    fun freshInstallWhenNoFile() {
        val s = SaveStore(tmp.root, json)
        val d = s.load()
        assertTrue(s.freshInstall)
        assertEquals(AppData().coins, d.coins)
    }

    @Test
    fun brokenFieldIsSkippedAndRestKept() {
        // "coins" sayı değil, bir gün kaydı bozuk: geri kalan her şey kurtarılmalı.
        File(tmp.root, "data.json").writeText(
            """{"coins":"bozuk","bestDailyStreak":12,"totalEarned":777,
               "days":{"2026-11-20":{"studyMs":3600000,"dailyMet":true},"2026-11-21":{"studyMs":"x"}}}"""
        )
        val s = SaveStore(tmp.root, json)
        val d = s.load()
        assertFalse(s.freshInstall)
        assertNotNull(s.recoveryNotice)
        assertEquals(12, d.bestDailyStreak)
        assertEquals(777, d.totalEarned)
        assertEquals(true, d.days["2026-11-20"]?.dailyMet)
        assertTrue("bozuk kopya kenara alınmalı", tmp.root.listFiles()!!.any { it.name.startsWith("data.corrupt") })
    }

    @Test
    fun missingMainFallsBackToBackup() {
        File(tmp.root, "data.bak1.json").writeText(json.encodeToString(AppData.serializer(), AppData(coins = 4321)))
        val s = SaveStore(tmp.root, json)
        val d = s.load()
        assertEquals(4321, d.coins)
        assertFalse(s.freshInstall)
    }

    @Test
    fun saveRoundTrip() {
        val s = SaveStore(tmp.root, json)
        s.save(AppData(coins = 55), System.currentTimeMillis())
        assertEquals(55, SaveStore(tmp.root, json).load().coins)
    }
}
