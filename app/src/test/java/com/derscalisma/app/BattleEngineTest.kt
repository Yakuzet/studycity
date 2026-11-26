package com.derscalisma.app

import com.derscalisma.app.data.game.*
import org.junit.Assert.assertTrue
import org.junit.Test

/** Savaş dengesini kabaca kontrol eder: erken seviyeler makul bir orduyla kazanılabilmeli. */
class BattleEngineTest {

    private fun simulate(
        level: Int,
        army: Map<TType, Int>,
        troopLevel: Int = 1,
        hero: Int = 0,
        spells: Map<SType, Int> = emptyMap(),
        label: String = "",
    ): BattleEngine {
        val e = BattleEngine(Campaign.village(level), army, army.keys.associateWith { troopLevel }, seed = 7, heroLevel = hero, spells = spells)
        // Duvar kırıcılar önce, sonra diğerleri: haritanın sol ve alt kenarına dağıt.
        var k = 0
        val order = army.entries.sortedBy { if (it.key == TType.BREAKER) 0 else 1 }
        order.forEach { (t, n) ->
            repeat(n) {
                val pos = 1.5f + (k % 20)
                if (k % 2 == 0) e.deploy(t, 1.0f, pos) else e.deploy(t, pos, 22.5f)
                k++
            }
        }
        if (hero > 0) e.deploy(TType.HERO, 1.0f, 12f)
        var steps = 0
        var castDone = false
        while (!e.finished && steps < 20_000) {
            e.step(0.033f)
            steps++
            if (!castDone && e.time > 12f) {
                castDone = true
                spells.forEach { (s, n) -> repeat(n) { e.castSpell(s, 11.5f, 11.5f) } }
                e.useHeroAbility()
            }
        }
        println(
            "Seviye $level ${e.village.name} $label: ${e.stars}⭐ %${(e.destruction * 100).toInt()} " +
                "süre ${e.time.toInt()}sn, sağ kalan ${e.troops.size}, bina ${e.totalBuildings}, sur ${e.village.walls}"
        )
        return e
    }

    @Test
    fun level1WinnableWithStarterArmy() {
        val e = simulate(1, mapOf(TType.SWORD to 15))
        assertTrue("Seviye 1, 15 kılıççıyla en az 1 yıldız vermeli", e.stars >= 1)
    }

    @Test
    fun wallsAndBreakers() {
        val noBreak = simulate(5, mapOf(TType.SWORD to 20, TType.APPRENTICE to 10), label = "(kırıcısız)")
        val withBreak = simulate(5, mapOf(TType.SWORD to 16, TType.BREAKER to 4, TType.APPRENTICE to 8), label = "(kırıcılı)")
        assertTrue("Surlu köy kırıcılarla da kazanılabilmeli", withBreak.stars >= 1)
        assertTrue(noBreak.finished)
    }

    @Test
    fun progressionCurve() {
        simulate(8, mapOf(TType.SWORD to 15, TType.BREAKER to 4, TType.APPRENTICE to 10, TType.KNIGHT to 2), troopLevel = 2)
        simulate(12, mapOf(TType.SWORD to 15, TType.BREAKER to 4, TType.APPRENTICE to 10, TType.KNIGHT to 3, TType.MAGE to 2), troopLevel = 2, hero = 3)
        simulate(
            18, mapOf(TType.SWORD to 10, TType.BREAKER to 6, TType.KNIGHT to 4, TType.MAGE to 5, TType.HEALER to 1),
            troopLevel = 3, hero = 6, spells = mapOf(SType.RAGE to 1, SType.HEAL to 1),
        )
        val e = simulate(
            25, mapOf(TType.SWORD to 10, TType.BREAKER to 8, TType.KNIGHT to 5, TType.MAGE to 6, TType.HEALER to 2),
            troopLevel = 4, hero = 9, spells = mapOf(SType.RAGE to 2, SType.LIGHTNING to 1, SType.HEAL to 1),
        )
        assertTrue(e.finished)
    }

    @Test
    fun deployForbiddenNearBuildingsAndOnWalls() {
        val e = BattleEngine(Campaign.village(5), mapOf(TType.SWORD to 3), emptyMap())
        assertTrue(!e.canDeploy(11.5f, 11.5f)) // Merkez Kale'nin üstü
        assertTrue(e.canDeploy(0.5f, 0.5f))
    }

    @Test
    fun allCampaignLevelsGenerate() {
        for (l in 1..Campaign.LEVELS) {
            val v = Campaign.village(l)
            assertTrue(v.structures >= 4)
            if (l >= 3) assertTrue("Seviye $l surlu olmalı", v.walls > 0)
        }
    }
}
