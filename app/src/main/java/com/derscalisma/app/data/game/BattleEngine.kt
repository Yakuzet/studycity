package com.derscalisma.app.data.game

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Gerçek zamanlı savaş simülasyonu (karo koordinatlarında). Android'e bağımlı değildir,
 * ekran her karede [step] çağırır ve alanları okuyarak çizer.
 */
class BattleEngine(
    val village: EnemyVillage,
    army: Map<TType, Int>,
    troopLevels: Map<TType, Int>,
    seed: Int = 1,
) {
    val size = VILLAGE_SIZE.toFloat()
    val duration = 150f
    private val rng = Random(seed)
    private val levels = troopLevels

    class Bld(val id: Int, val type: BType, val level: Int, val x: Float, val y: Float, val s: Float, val maxHp: Float) {
        var hp = maxHp
        var cooldown = 0.5f
        var hitFlash = 0f
        var destroyedAt = -1f
        var shotAt = -10f
        val defense = GameCatalog.defense(type, level)
        val alive get() = hp > 0f
        val cx get() = x + s / 2f
        val cy get() = y + s / 2f

        fun closest(px: Float, py: Float): Pair<Float, Float> =
            px.coerceIn(x, x + s) to py.coerceIn(y, y + s)

        fun dist(px: Float, py: Float): Float {
            val (qx, qy) = closest(px, py)
            return hypot(px - qx, py - qy)
        }
    }

    class Troop(val id: Int, val type: TType, var x: Float, var y: Float, val maxHp: Float, val power: Float) {
        var hp = maxHp
        var target: Bld? = null
        var cooldown = 0.3f
        var facingLeft = false
        var moving = false
        var hitFlash = 0f
        var attackAnim = 0f
        var age = 0f
        val alive get() = hp > 0f
    }

    enum class PKind { ARROW, BALL, ENEMY_MAGIC, BOLT, MAGE_BOLT }

    class Proj(
        val kind: PKind,
        val sx: Float,
        val sy: Float,
        var x: Float,
        var y: Float,
        var tx: Float,
        var ty: Float,
        val targetTroop: Troop?,
        val damage: Float,
        val splash: Float,
        val speed: Float,
        val fromEnemy: Boolean,
    ) {
        var done = false
        val totalDist = max(0.01f, hypot(tx - sx, ty - sy))
    }

    enum class FxKind { EXPLOSION, SMOKE, HIT, DEATH, HEAL, DEPLOY, SPLASH }

    class Fx(val kind: FxKind, val x: Float, val y: Float, val born: Float, val life: Float, val size: Float, val vx: Float = 0f, val vy: Float = 0f)

    val buildings: List<Bld> = village.buildings.mapIndexed { i, b ->
        Bld(i, b.type, b.level, b.x.toFloat(), b.y.toFloat(), b.type.size.toFloat(), GameCatalog.hp(b.type, b.level))
    }
    val troops = mutableListOf<Troop>()
    val projectiles = mutableListOf<Proj>()
    val effects = mutableListOf<Fx>()
    val remaining: MutableMap<TType, Int> = army.filterValues { it > 0 }.toMutableMap()
    val deployed = mutableMapOf<TType, Int>()
    var time = 0f
        private set
    var finished = false
        private set
    var lastDeployFail = -10f
        private set
    private var nextId = 0

    /** Bina çevresindeki 1 karelik alana asker indirilemez. */
    val forbidden: Array<BooleanArray> = Array(VILLAGE_SIZE) { i ->
        BooleanArray(VILLAGE_SIZE) { j ->
            village.buildings.any { b -> i >= b.x - 1 && i <= b.x + b.type.size && j >= b.y - 1 && j <= b.y + b.type.size }
        }
    }

    val totalBuildings get() = buildings.size
    val destroyedCount get() = buildings.count { !it.alive }
    val destruction: Float get() = if (buildings.isEmpty()) 1f else destroyedCount.toFloat() / buildings.size
    val townHallDestroyed get() = buildings.any { it.type == BType.TOWN_HALL && !it.alive }
    val stars: Int
        get() = (if (destruction >= 0.5f) 1 else 0) + (if (townHallDestroyed) 1 else 0) + (if (destruction >= 0.999f) 1 else 0)
    val timeLeft get() = (duration - time).coerceAtLeast(0f)
    val troopsLeft get() = remaining.values.sum()

    fun canDeploy(x: Float, y: Float): Boolean {
        if (x < 0f || y < 0f || x >= size || y >= size) return false
        return !forbidden[x.toInt()][y.toInt()]
    }

    fun deploy(t: TType, x: Float, y: Float): Boolean {
        if (finished || (remaining[t] ?: 0) <= 0) return false
        if (!canDeploy(x, y)) {
            lastDeployFail = time
            return false
        }
        val mult = GameCatalog.troopMultiplier(levels[t] ?: 1)
        val jx = (rng.nextFloat() - 0.5f) * 0.4f
        val jy = (rng.nextFloat() - 0.5f) * 0.4f
        troops += Troop(nextId++, t, x + jx, y + jy, t.hp * mult, t.dps * mult)
        remaining[t] = (remaining[t] ?: 0) - 1
        deployed[t] = (deployed[t] ?: 0) + 1
        effects += Fx(FxKind.DEPLOY, x, y, time, 0.5f, 0.8f)
        return true
    }

    fun surrender() {
        finished = true
    }

    fun result(): BattleResult = BattleResult(village.level, stars, destruction, deployed.toMap())

    fun step(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0f, 0.05f)
        time += dt
        if (!finished) {
            troops.forEach { if (it.alive) updateTroop(it, dt) }
            separate(dt)
            buildings.forEach { if (it.alive) updateDefense(it, dt) }
        }
        updateProjectiles(dt)
        buildings.forEach { it.hitFlash = max(0f, it.hitFlash - dt) }
        troops.forEach { it.hitFlash = max(0f, it.hitFlash - dt) }

        val dead = troops.filter { !it.alive }
        dead.forEach { effects += Fx(FxKind.DEATH, it.x, it.y, time, 0.6f, 0.7f) }
        troops.removeAll(dead)
        effects.removeAll { time - it.born > it.life }

        if (!finished) {
            val noArmy = troops.isEmpty() && troopsLeft == 0 && deployed.isNotEmpty()
            if (destruction >= 0.999f || time >= duration || noArmy) finished = true
        }
    }

    // ------------------------------------------------------------------ troops

    private fun chooseTarget(t: Troop): Bld? {
        val alive = buildings.filter { it.alive }
        val pool = if (t.type.pref == TargetPref.DEFENSE) alive.filter { it.defense != null }.ifEmpty { alive } else alive
        return pool.minByOrNull { it.dist(t.x, t.y) }
    }

    private fun moveToward(t: Troop, px: Float, py: Float, dt: Float) {
        val dx = px - t.x
        val dy = py - t.y
        val d = hypot(dx, dy)
        if (d < 0.001f) return
        val step = min(d, t.type.speed * dt)
        t.x += dx / d * step
        t.y += dy / d * step
        if (abs(dx) > 0.05f) t.facingLeft = dx < 0
        t.moving = true
    }

    private fun updateTroop(t: Troop, dt: Float) {
        t.age += dt
        t.attackAnim = max(0f, t.attackAnim - dt)
        t.moving = false
        if (t.type.pref == TargetPref.TROOPS) {
            updateHealer(t, dt)
            return
        }
        var tgt = t.target
        if (tgt == null || !tgt.alive) {
            tgt = chooseTarget(t)
            t.target = tgt
        }
        if (tgt == null) return
        val d = tgt.dist(t.x, t.y)
        if (d > t.type.range) {
            val (px, py) = tgt.closest(t.x, t.y)
            moveToward(t, px, py, dt)
            return
        }
        if (abs(tgt.cx - t.x) > 0.05f) t.facingLeft = tgt.cx < t.x
        t.cooldown -= dt
        if (t.cooldown > 0f) return
        t.cooldown = 1f
        t.attackAnim = 0.25f
        if (t.type.range < 1.5f) {
            damageBuilding(tgt, t.power)
            val (hx, hy) = tgt.closest(t.x, t.y)
            effects += Fx(FxKind.HIT, hx, hy, time, 0.25f, 0.5f)
        } else {
            val kind = if (t.type == TType.MAGE) PKind.MAGE_BOLT else PKind.BOLT
            projectiles += Proj(kind, t.x, t.y - 0.3f, t.x, t.y - 0.3f, tgt.cx, tgt.cy, null, t.power, t.type.splash, 9f, false)
        }
    }

    private fun updateHealer(t: Troop, dt: Float) {
        val allies = troops.filter { it.alive && it !== t && it.type != TType.HEALER }
        val ally = allies.minByOrNull { (it.hp / it.maxHp) * 10f + hypot(it.x - t.x, it.y - t.y) * 0.15f } ?: return
        val d = hypot(ally.x - t.x, ally.y - t.y)
        if (d > 1.6f) moveToward(t, ally.x, ally.y, dt)
        t.cooldown -= dt
        if (t.cooldown > 0f) return
        t.cooldown = 1f
        var healed = false
        for (a in troops) {
            if (!a.alive || a === t) continue
            if (hypot(a.x - t.x, a.y - t.y) <= t.type.splash && a.hp < a.maxHp) {
                a.hp = min(a.maxHp, a.hp + t.power)
                effects += Fx(FxKind.HEAL, a.x, a.y, time, 0.7f, 0.5f)
                healed = true
            }
        }
        if (healed) t.attackAnim = 0.3f
    }

    /** Askerlerin tam üst üste binmesini engeller (görsel olarak daha doğal). */
    private fun separate(dt: Float) {
        val n = troops.size
        for (i in 0 until n) {
            val a = troops[i]
            for (j in i + 1 until n) {
                val b = troops[j]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val d = hypot(dx, dy)
                if (d < 0.35f && d > 0.0001f) {
                    val push = (0.35f - d) * 0.5f * min(1f, dt * 10f)
                    a.x -= dx / d * push
                    a.y -= dy / d * push
                    b.x += dx / d * push
                    b.y += dy / d * push
                }
            }
        }
        troops.forEach {
            it.x = it.x.coerceIn(0.1f, size - 0.1f)
            it.y = it.y.coerceIn(0.1f, size - 0.1f)
        }
    }

    // ------------------------------------------------------------------ defenses

    private fun updateDefense(b: Bld, dt: Float) {
        val def = b.defense ?: return
        b.cooldown -= dt
        if (b.cooldown > 0f) return
        val target = troops.filter { it.alive && hypot(it.x - b.cx, it.y - b.cy) <= def.range }
            .minByOrNull { hypot(it.x - b.cx, it.y - b.cy) } ?: return
        b.cooldown = def.interval
        b.shotAt = time
        val dmg = def.dps * def.interval
        val sy = b.y + 0.2f
        projectiles += when (b.type) {
            BType.ARCHER_TOWER -> Proj(PKind.ARROW, b.cx, sy, b.cx, sy, target.x, target.y, target, dmg, 0f, 14f, true)
            BType.CANNON -> Proj(PKind.BALL, b.cx, sy, b.cx, sy, target.x, target.y, null, dmg, def.splash, 9f, true)
            else -> Proj(PKind.ENEMY_MAGIC, b.cx, sy, b.cx, sy, target.x, target.y, target, dmg, def.splash, 8f, true)
        }
    }

    // ------------------------------------------------------------------ projectiles & damage

    private fun updateProjectiles(dt: Float) {
        for (p in projectiles) {
            p.targetTroop?.let { if (it.alive) { p.tx = it.x; p.ty = it.y } }
            val dx = p.tx - p.x
            val dy = p.ty - p.y
            val d = hypot(dx, dy)
            val step = p.speed * dt
            if (d <= step) {
                p.x = p.tx
                p.y = p.ty
                p.done = true
                impact(p)
            } else {
                p.x += dx / d * step
                p.y += dy / d * step
            }
        }
        projectiles.removeAll { it.done }
    }

    private fun impact(p: Proj) {
        if (p.fromEnemy) {
            if (p.splash > 0f) {
                troops.forEach { if (it.alive && hypot(it.x - p.x, it.y - p.y) <= p.splash) hurtTroop(it, p.damage) }
                effects += Fx(if (p.kind == PKind.BALL) FxKind.SMOKE else FxKind.SPLASH, p.x, p.y, time, 0.5f, p.splash * 1.4f)
            } else {
                p.targetTroop?.let { if (it.alive) hurtTroop(it, p.damage) }
            }
        } else {
            if (p.splash > 0f) {
                buildings.forEach { if (it.alive && it.dist(p.x, p.y) <= p.splash) damageBuilding(it, p.damage) }
                effects += Fx(FxKind.SPLASH, p.x, p.y, time, 0.45f, p.splash * 1.3f)
            } else {
                buildings.filter { it.alive }.minByOrNull { it.dist(p.x, p.y) }?.let {
                    if (it.dist(p.x, p.y) < 0.6f) damageBuilding(it, p.damage)
                }
                effects += Fx(FxKind.HIT, p.x, p.y, time, 0.25f, 0.4f)
            }
        }
    }

    private fun hurtTroop(t: Troop, dmg: Float) {
        t.hp -= dmg
        t.hitFlash = 0.15f
    }

    private fun damageBuilding(b: Bld, dmg: Float) {
        if (!b.alive) return
        b.hp -= dmg
        b.hitFlash = 0.12f
        if (b.hp <= 0f) {
            b.hp = 0f
            b.destroyedAt = time
            effects += Fx(FxKind.EXPLOSION, b.cx, b.cy, time, 0.7f, b.s * 1.1f)
            repeat(6) {
                val a = rng.nextFloat() * 6.283f
                val sp = 0.6f + rng.nextFloat() * 0.9f
                effects += Fx(
                    FxKind.SMOKE, b.cx, b.cy, time, 1.2f + rng.nextFloat() * 0.6f, 0.5f + rng.nextFloat() * 0.5f,
                    vx = kotlin.math.cos(a) * sp, vy = kotlin.math.sin(a) * sp - 0.4f,
                )
            }
        }
    }
}
