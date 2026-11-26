package com.derscalisma.app

import com.derscalisma.app.data.game.*
import org.junit.Assert.assertTrue
import org.junit.Test

/** Savaş dengesini kabaca kontrol eder: erken seviyeler makul bir orduyla kazanılabilmeli. */
class BattleEngineTest {

    private fun simulate(level: Int, army: Map<TType, Int>, troopLevel: Int = 1): BattleEngine {
        val e = BattleEngine(Campaign.village(level), army, army.keys.associateWith { troopLevel }, seed = 7)
        // Askerleri haritanın sol ve alt kenarına dağıtarak indir.
        var k = 0
        army.forEach { (t, n) ->
            repeat(n) {
                val pos = 1.5f + (k % 20)
                if (k % 2 == 0) e.deploy(t, 1.0f, pos) else e.deploy(t, pos, 22.5f)
                k++
            }
        }
        var steps = 0
        while (!e.finished && steps < 20_000) {
            e.step(0.033f)
            steps++
        }
        println(
            "Seviye $level ${e.village.name}: ${e.stars}⭐ %${(e.destruction * 100).toInt()} " +
                "süre ${e.time.toInt()}sn, sağ kalan ${e.troops.size}, bina ${e.totalBuildings}"
        )
        return e
    }

    @Test
    fun level1WinnableWithStarterArmy() {
        val e = simulate(1, mapOf(TType.SWORD to 15))
        assertTrue("Seviye 1, 15 kılıççıyla en az 1 yıldız vermeli", e.stars >= 1)
    }

    @Test
    fun progressionCurve() {
        simulate(3, mapOf(TType.SWORD to 15, TType.APPRENTICE to 10))
        simulate(6, mapOf(TType.SWORD to 15, TType.APPRENTICE to 10, TType.KNIGHT to 2))
        simulate(10, mapOf(TType.SWORD to 10, TType.APPRENTICE to 10, TType.KNIGHT to 3, TType.MAGE to 2), troopLevel = 2)
        val e = simulate(20, mapOf(TType.SWORD to 10, TType.KNIGHT to 4, TType.MAGE to 4, TType.HEALER to 1), troopLevel = 3)
        assertTrue(e.finished)
    }

    @Test
    fun deployForbiddenNearBuildings() {
        val e = BattleEngine(Campaign.village(1), mapOf(TType.SWORD to 3), emptyMap())
        assertTrue(!e.canDeploy(11.5f, 11.5f)) // Merkez Kale'nin üstü
        assertTrue(e.canDeploy(0.5f, 0.5f))
    }

    @Test
    fun allCampaignLevelsGenerate() {
        for (l in 1..Campaign.LEVELS) {
            val v = Campaign.village(l)
            assertTrue(v.buildings.size >= 4)
        }
    }
}
