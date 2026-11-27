package com.derscalisma.app.data.game

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Gerçek zamanlı savaş simülasyonu (karo koordinatlarında). Android'e bağımlı değildir;
 * ekran her karede [step] çağırır ve alanları okuyarak çizer.
 *
 * Binalar ve surlar engeldir. Askerler A* ile yol bulur: sur kırmak dolaşmaktan ucuzsa surdan geçer.
 */
class BattleEngine(
    val village: EnemyVillage,
    army: Map<TType, Int>,
    troopLevels: Map<TType, Int>,
    seed: Int = 1,
    val heroLevel: Int = 0,
    spells: Map<SType, Int> = emptyMap(),
) {
    private val n = VILLAGE_SIZE
    val size = n.toFloat()
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

        fun closest(px: Float, py: Float): Pair<Float, Float> = px.coerceIn(x, x + s) to py.coerceIn(y, y + s)

        fun dist(px: Float, py: Float): Float {
            val (qx, qy) = closest(px, py)
            return hypot(px - qx, py - qy)
        }
    }

    class Troop(val id: Int, val type: TType, var x: Float, var y: Float, val maxHp: Float, val power: Float) {
        var hp = maxHp
        var target: Bld? = null
        var wallTarget = -1
        var path: IntArray? = null
        var pathPos = 0
        var pathVersion = -1
        var lastRepath = -10f
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
        val wallIdx: Int = -1,
    ) {
        var done = false
        val totalDist = max(0.01f, hypot(tx - sx, ty - sy))
    }

    enum class FxKind { EXPLOSION, SMOKE, HIT, DEATH, HEAL, DEPLOY, SPLASH, LIGHTNING, RUBBLE }

    class Fx(val kind: FxKind, val x: Float, val y: Float, val born: Float, val life: Float, val size: Float, val vx: Float = 0f, val vy: Float = 0f)

    /** Ekranın çalacağı ses olayları (her karede boşaltılır). */
    enum class Snd { DEPLOY, SWORD, ARROW, CANNON, MAGIC, BOOM, WALL, SPELL, LIGHTNING, HEAL, DIE }

    class ActiveSpell(val kind: SType, val x: Float, val y: Float, val start: Float, val duration: Float, val radius: Float)

    // ------------------------------------------------------------------ durum

    val buildings: List<Bld> = village.buildings.filter { it.type != BType.WALL }.mapIndexed { i, b ->
        Bld(i, b.type, b.level, b.x.toFloat(), b.y.toFloat(), b.type.size.toFloat(), GameCatalog.hp(b.type, b.level))
    }

    /** Sur canı / en yüksek canı / seviyesi (karo başına; sur yoksa 0). */
    val wallHp = Array(n) { FloatArray(n) }
    val wallMax = Array(n) { FloatArray(n) }
    val wallLevel = Array(n) { IntArray(n) }

    /** Karo başına bina kimliği (-1 = boş). */
    private val occ = Array(n) { IntArray(n) { -1 } }
    private var destroyVersion = 0

    val troops = mutableListOf<Troop>()
    val projectiles = mutableListOf<Proj>()
    val effects = mutableListOf<Fx>()
    val activeSpells = mutableListOf<ActiveSpell>()
    val sounds = ArrayList<Snd>()
    val remaining: MutableMap<TType, Int> = army.filterValues { it > 0 }.toMutableMap()
    val deployed = mutableMapOf<TType, Int>()
    val spellsLeft: MutableMap<SType, Int> = spells.filterValues { it > 0 }.toMutableMap()
    val spellsUsed = mutableMapOf<SType, Int>()
    var heroAbilityUsed = false
        private set
    var time = 0f
        private set
    var finished = false
        private set
    var lastDeployFail = -10f
        private set
    private var nextId = 0

    init {
        if (heroLevel > 0) remaining[TType.HERO] = 1 else remaining.remove(TType.HERO)
        buildings.forEach { b ->
            for (i in b.x.toInt() until (b.x + b.s).toInt()) for (j in b.y.toInt() until (b.y + b.s).toInt()) occ[i][j] = b.id
        }
        village.buildings.filter { it.type == BType.WALL }.forEach { w ->
            val hp = GameCatalog.wallHp(w.level)
            wallHp[w.x][w.y] = hp
            wallMax[w.x][w.y] = hp
            wallLevel[w.x][w.y] = w.level
        }
    }

    /** Bina çevresindeki 1 karelik alana asker indirilemez (surlar bu alanı genişletmez). */
    val forbidden: Array<BooleanArray> = Array(n) { i ->
        BooleanArray(n) { j ->
            buildings.any { b -> i >= b.x - 1 && i <= b.x + b.s && j >= b.y - 1 && j <= b.y + b.s }
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
    val hero: Troop? get() = troops.find { it.type == TType.HERO }
    val heroDeployed get() = (deployed[TType.HERO] ?: 0) > 0

    fun wallAlive(i: Int, j: Int) = i in 0 until n && j in 0 until n && wallHp[i][j] > 0f

    // ------------------------------------------------------------------ oyuncu işlemleri

    fun canDeploy(x: Float, y: Float): Boolean {
        if (x < 0f || y < 0f || x >= size || y >= size) return false
        val i = x.toInt()
        val j = y.toInt()
        return !forbidden[i][j] && !wallAlive(i, j)
    }

    fun deploy(t: TType, x: Float, y: Float): Boolean {
        if (finished || (remaining[t] ?: 0) <= 0) return false
        if (!canDeploy(x, y)) {
            lastDeployFail = time
            return false
        }
        val mult = if (t == TType.HERO) GameCatalog.heroMultiplier(heroLevel) else GameCatalog.troopMultiplier(levels[t] ?: 1)
        val jx = (rng.nextFloat() - 0.5f) * 0.4f
        val jy = (rng.nextFloat() - 0.5f) * 0.4f
        troops += Troop(nextId++, t, x + jx, y + jy, t.hp * mult, t.dps * mult)
        remaining[t] = (remaining[t] ?: 0) - 1
        deployed[t] = (deployed[t] ?: 0) + 1
        effects += Fx(FxKind.DEPLOY, x, y, time, 0.5f, if (t == TType.HERO) 1.6f else 0.8f)
        sounds += Snd.DEPLOY
        return true
    }

    fun castSpell(k: SType, x: Float, y: Float): Boolean {
        if (finished || (spellsLeft[k] ?: 0) <= 0) return false
        if (x < 0f || y < 0f || x >= size || y >= size) return false
        spellsLeft[k] = (spellsLeft[k] ?: 0) - 1
        spellsUsed[k] = (spellsUsed[k] ?: 0) + 1
        activeSpells += ActiveSpell(k, x, y, time, k.duration, k.radius)
        sounds += if (k == SType.LIGHTNING) Snd.LIGHTNING else Snd.SPELL
        if (k == SType.LIGHTNING) {
            repeat(3) {
                val a = rng.nextFloat() * 6.283f
                val r = rng.nextFloat() * k.radius * 0.7f
                val px = x + cos(a) * r
                val py = y + sin(a) * r
                effects += Fx(FxKind.LIGHTNING, px, py, time + it * 0.12f, 0.45f, 1f)
                buildings.forEach { b -> if (b.alive && b.dist(px, py) <= 1.0f) damageBuilding(b, k.power / 3f) }
                forWallsNear(px, py, 1.0f) { i, j -> damageWall(i, j, k.power / 3f) }
            }
        }
        return true
    }

    /** Komutan'ın "Savaş Narası": kendini iyileştirir ve çevresindeki askerleri öfkelendirir (bir kez). */
    fun useHeroAbility(): Boolean {
        val h = hero ?: return false
        if (heroAbilityUsed || finished) return false
        heroAbilityUsed = true
        h.hp = min(h.maxHp, h.hp + h.maxHp * 0.35f)
        activeSpells += ActiveSpell(SType.RAGE, h.x, h.y, time, 5f, 3.5f)
        effects += Fx(FxKind.HEAL, h.x, h.y, time, 0.8f, 1f)
        effects += Fx(FxKind.DEPLOY, h.x, h.y, time, 0.7f, 3.5f)
        return true
    }

    fun surrender() {
        finished = true
    }

    fun result(): BattleResult {
        val heroRatio = if (heroDeployed) (hero?.let { it.hp / it.maxHp } ?: 0f) else null
        return BattleResult(village.level, stars, destruction, deployed.filterKeys { it != TType.HERO }, spellsUsed.toMap(), heroRatio)
    }

    // ------------------------------------------------------------------ simülasyon

    fun step(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0f, 0.05f)
        time += dt
        if (!finished) {
            troops.toList().forEach { if (it.alive) updateTroop(it, dt) }
            separate(dt)
            buildings.forEach { if (it.alive) updateDefense(it, dt) }
            applySpells(dt)
        }
        updateProjectiles(dt)
        buildings.forEach { it.hitFlash = max(0f, it.hitFlash - dt) }
        troops.forEach { it.hitFlash = max(0f, it.hitFlash - dt) }

        val dead = troops.filter { !it.alive }
        dead.forEach { if (it.type != TType.BREAKER) effects += Fx(FxKind.DEATH, it.x, it.y, time, 0.6f, 0.7f) }
        if (dead.isNotEmpty()) sounds += Snd.DIE
        troops.removeAll(dead)
        effects.removeAll { time - it.born > it.life && it.born <= time }
        activeSpells.removeAll { time - it.start > it.duration + 0.5f }

        if (!finished) {
            val noArmy = troops.isEmpty() && troopsLeft == 0 && deployed.isNotEmpty()
            if (destruction >= 0.999f || time >= duration || noArmy) finished = true
        }
    }

    private fun rageAt(x: Float, y: Float): Boolean = activeSpells.any {
        it.kind == SType.RAGE && time - it.start <= it.duration && hypot(it.x - x, it.y - y) <= it.radius
    }

    private fun applySpells(dt: Float) {
        for (s in activeSpells) {
            if (s.kind != SType.HEAL || time - s.start > s.duration) continue
            for (t in troops) {
                if (!t.alive || t.hp >= t.maxHp) continue
                if (hypot(t.x - s.x, t.y - s.y) <= s.radius) {
                    t.hp = min(t.maxHp, t.hp + s.kind.power * dt)
                    if (rng.nextFloat() < dt * 2f) effects += Fx(FxKind.HEAL, t.x, t.y, time, 0.7f, 0.5f)
                }
            }
        }
    }

    // ------------------------------------------------------------------ askerler

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
        val speed = t.type.speed * (if (rageAt(t.x, t.y)) 1.4f else 1f)
        val step = min(d, speed * dt)
        t.x += dx / d * step
        t.y += dy / d * step
        if (abs(dx) > 0.05f) t.facingLeft = dx < 0
        t.moving = true
    }

    private fun power(t: Troop) = t.power * (if (rageAt(t.x, t.y)) SType.RAGE.power else 1f)

    private fun updateTroop(t: Troop, dt: Float) {
        t.age += dt
        t.attackAnim = max(0f, t.attackAnim - dt)
        t.moving = false
        when (t.type.pref) {
            TargetPref.TROOPS -> updateHealer(t, dt)
            TargetPref.WALLS -> updateBreaker(t, dt)
            else -> updateAttacker(t, dt)
        }
    }

    private fun updateAttacker(t: Troop, dt: Float) {
        var tgt = t.target
        if (tgt == null || !tgt.alive) {
            tgt = chooseTarget(t)
            t.target = tgt
            t.path = null
        }
        if (tgt == null) return
        if (tgt.dist(t.x, t.y) <= t.type.range) {
            attackBuilding(t, tgt, dt)
            return
        }
        if (t.path == null || (t.pathVersion != destroyVersion && time - t.lastRepath > 1.5f)) {
            val target = tgt
            t.path = findPath(t, target.id, target.cx, target.cy) { i, j -> target.dist(i + 0.5f, j + 0.5f) <= t.type.range + 0.35f }
            t.pathPos = 0
            t.pathVersion = destroyVersion
            t.lastRepath = time
        }
        val target = tgt
        followPath(t, dt) {
            val (px, py) = target.closest(t.x, t.y)
            moveToward(t, px, py, dt)
        }
    }

    /** Yolu izler; yoldaki sur sağlamsa önce onu kırar. Yol bittiyse [fallback] çalışır. */
    private inline fun followPath(t: Troop, dt: Float, fallback: () -> Unit) {
        val p = t.path
        if (p == null || t.pathPos >= p.size) {
            fallback()
            return
        }
        val idx = p[t.pathPos]
        val i = idx % n
        val j = idx / n
        if (wallAlive(i, j)) {
            attackWall(t, i, j, dt)
            return
        }
        val cx = i + 0.5f
        val cy = j + 0.5f
        if (hypot(cx - t.x, cy - t.y) < 0.25f) t.pathPos++ else moveToward(t, cx, cy, dt)
    }

    private fun attackBuilding(t: Troop, tgt: Bld, dt: Float) {
        if (abs(tgt.cx - t.x) > 0.05f) t.facingLeft = tgt.cx < t.x
        t.cooldown -= dt
        if (t.cooldown > 0f) return
        t.cooldown = 1f
        t.attackAnim = 0.25f
        val dmg = power(t)
        if (t.type.range < 1.5f) {
            sounds += Snd.SWORD
            damageBuilding(tgt, dmg)
            val (hx, hy) = tgt.closest(t.x, t.y)
            effects += Fx(FxKind.HIT, hx, hy, time, 0.25f, if (t.type == TType.HERO) 0.8f else 0.5f)
        } else {
            val kind = if (t.type == TType.MAGE) PKind.MAGE_BOLT else PKind.BOLT
            sounds += Snd.MAGIC
            projectiles += Proj(kind, t.x, t.y - 0.3f, t.x, t.y - 0.3f, tgt.cx, tgt.cy, null, dmg, t.type.splash, 9f, false)
        }
    }

    private fun attackWall(t: Troop, i: Int, j: Int, dt: Float) {
        val cx = i + 0.5f
        if (abs(cx - t.x) > 0.05f) t.facingLeft = cx < t.x
        t.cooldown -= dt
        if (t.cooldown > 0f) return
        t.cooldown = 1f
        t.attackAnim = 0.25f
        val dmg = power(t)
        if (t.type.range < 1.5f) {
            sounds += Snd.SWORD
            damageWall(i, j, dmg)
            effects += Fx(FxKind.HIT, cx, j + 0.5f, time, 0.25f, 0.5f)
        } else {
            val kind = if (t.type == TType.MAGE) PKind.MAGE_BOLT else PKind.BOLT
            projectiles += Proj(kind, t.x, t.y - 0.3f, t.x, t.y - 0.3f, cx, j + 0.5f, null, dmg, 0f, 9f, false, wallIdx = j * n + i)
        }
    }

    private fun updateBreaker(t: Troop, dt: Float) {
        if (t.wallTarget < 0 || !wallAlive(t.wallTarget % n, t.wallTarget / n)) {
            t.wallTarget = nearestWall(t.x, t.y)
            t.path = null
        }
        if (t.wallTarget < 0) {
            // Sur kalmadıysa en yakın binaya koşup patlar.
            val b = buildings.filter { it.alive }.minByOrNull { it.dist(t.x, t.y) } ?: return
            if (b.dist(t.x, t.y) <= 0.6f) explode(t) else {
                val (px, py) = b.closest(t.x, t.y)
                moveToward(t, px, py, dt)
            }
            return
        }
        val wi = t.wallTarget % n
        val wj = t.wallTarget / n
        if (hypot(wi + 0.5f - t.x, wj + 0.5f - t.y) <= 1.05f) {
            explode(t)
            return
        }
        if (t.path == null || (t.pathVersion != destroyVersion && time - t.lastRepath > 1.5f)) {
            t.path = findPath(t, -1, wi + 0.5f, wj + 0.5f) { i, j -> abs(i - wi) <= 1 && abs(j - wj) <= 1 && !(i == wi && j == wj) }
            t.pathPos = 0
            t.pathVersion = destroyVersion
            t.lastRepath = time
        }
        // Yolda başka bir sura denk gelirse orada patlar (o da sur).
        val p = t.path
        if (p != null && t.pathPos < p.size) {
            val idx = p[t.pathPos]
            if (wallAlive(idx % n, idx / n)) {
                explode(t)
                return
            }
        }
        followPath(t, dt) { moveToward(t, wi + 0.5f, wj + 0.5f, dt) }
    }

    private fun explode(t: Troop) {
        val dmg = power(t)
        forWallsNear(t.x, t.y, t.type.splash) { i, j -> damageWall(i, j, dmg * 10f) }
        buildings.forEach { if (it.alive && it.dist(t.x, t.y) <= t.type.splash) damageBuilding(it, dmg) }
        effects += Fx(FxKind.EXPLOSION, t.x, t.y, time, 0.6f, 1.3f)
        sounds += Snd.BOOM
        t.hp = 0f
    }

    private fun nearestWall(x: Float, y: Float): Int {
        var best = -1
        var bd = Float.MAX_VALUE
        for (i in 0 until n) for (j in 0 until n) {
            if (wallHp[i][j] <= 0f) continue
            val d = hypot(i + 0.5f - x, j + 0.5f - y)
            if (d < bd) {
                bd = d
                best = j * n + i
            }
        }
        return best
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
                sounds += Snd.HEAL
            }
        }
        if (healed) t.attackAnim = 0.3f
    }

    /** Askerlerin tam üst üste binmesini engeller (görsel olarak daha doğal). */
    private fun separate(dt: Float) {
        val c = troops.size
        for (i in 0 until c) {
            val a = troops[i]
            for (j in i + 1 until c) {
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

    // ------------------------------------------------------------------ yol bulma (A*)

    private class Node(val idx: Int, val f: Float)

    /**
     * [goal] koşulunu sağlayan bir karoya en ucuz yol. Binalar geçilmez (hedef bina hariç);
     * sur karoları geçilir ama kırma süresi kadar pahalıdır.
     */
    private fun findPath(t: Troop, passTarget: Int, hx: Float, hy: Float, goal: (Int, Int) -> Boolean): IntArray? {
        val total = n * n
        val g = FloatArray(total) { Float.MAX_VALUE }
        val from = IntArray(total) { -1 }
        val closed = BooleanArray(total)
        val si = t.x.toInt().coerceIn(0, n - 1)
        val sj = t.y.toInt().coerceIn(0, n - 1)
        val start = sj * n + si
        val breakFactor = t.type.speed / max(t.power, 1f)
        fun blocked(i: Int, j: Int): Boolean {
            val o = occ[i][j]
            return o != -1 && o != passTarget
        }
        fun stepCost(i: Int, j: Int): Float = if (wallHp[i][j] > 0f) 1f + wallHp[i][j] * breakFactor else 1f

        val open = PriorityQueue<Node>(64, compareBy { it.f })
        g[start] = 0f
        open.add(Node(start, hypot(si + 0.5f - hx, sj + 0.5f - hy)))
        var expansions = 0
        while (open.isNotEmpty() && expansions < total * 2) {
            val cur = open.poll()!!
            if (closed[cur.idx]) continue
            closed[cur.idx] = true
            expansions++
            val ci = cur.idx % n
            val cj = cur.idx / n
            if (cur.idx != start && goal(ci, cj)) {
                var k = cur.idx
                val out = ArrayList<Int>()
                while (k != start && k != -1) {
                    out += k
                    k = from[k]
                }
                out.reverse()
                return out.toIntArray()
            }
            for (dx in -1..1) for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue
                val ni = ci + dx
                val nj = cj + dy
                if (ni < 0 || nj < 0 || ni >= n || nj >= n) continue
                if (blocked(ni, nj)) continue
                val diag = dx != 0 && dy != 0
                if (diag && (blocked(ci + dx, cj) || blocked(ci, cj + dy) || wallHp[ci + dx][cj] > 0f || wallHp[ci][cj + dy] > 0f)) continue
                val ni2 = nj * n + ni
                if (closed[ni2]) continue
                val ng = g[cur.idx] + stepCost(ni, nj) * (if (diag) 1.414f else 1f)
                if (ng < g[ni2]) {
                    g[ni2] = ng
                    from[ni2] = cur.idx
                    open.add(Node(ni2, ng + hypot(ni + 0.5f - hx, nj + 0.5f - hy)))
                }
            }
        }
        return null
    }

    // ------------------------------------------------------------------ savunma

    private fun updateDefense(b: Bld, dt: Float) {
        val def = b.defense ?: return
        b.cooldown -= dt
        if (b.cooldown > 0f) return
        val target = troops.filter { it.alive && hypot(it.x - b.cx, it.y - b.cy) <= def.range }
            .minByOrNull { hypot(it.x - b.cx, it.y - b.cy) } ?: return
        b.cooldown = def.interval
        b.shotAt = time
        sounds += when (b.type) { BType.ARCHER_TOWER -> Snd.ARROW; BType.CANNON -> Snd.CANNON; else -> Snd.MAGIC }
        val dmg = def.dps * def.interval
        val sy = b.y + 0.2f
        projectiles += when (b.type) {
            BType.ARCHER_TOWER -> Proj(PKind.ARROW, b.cx, sy, b.cx, sy, target.x, target.y, target, dmg, 0f, 14f, true)
            BType.CANNON -> Proj(PKind.BALL, b.cx, sy, b.cx, sy, target.x, target.y, null, dmg, def.splash, 9f, true)
            else -> Proj(PKind.ENEMY_MAGIC, b.cx, sy, b.cx, sy, target.x, target.y, target, dmg, def.splash, 8f, true)
        }
    }

    // ------------------------------------------------------------------ mermiler ve hasar

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
            return
        }
        if (p.wallIdx >= 0) {
            damageWall(p.wallIdx % n, p.wallIdx / n, p.damage)
            effects += Fx(FxKind.HIT, p.x, p.y, time, 0.25f, 0.4f)
            return
        }
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

    private inline fun forWallsNear(x: Float, y: Float, r: Float, f: (Int, Int) -> Unit) {
        val lo = (x - r - 1).toInt().coerceAtLeast(0)
        val hi = (x + r + 1).toInt().coerceAtMost(n - 1)
        val lo2 = (y - r - 1).toInt().coerceAtLeast(0)
        val hi2 = (y + r + 1).toInt().coerceAtMost(n - 1)
        for (i in lo..hi) for (j in lo2..hi2) {
            if (wallHp[i][j] > 0f && hypot(i + 0.5f - x, j + 0.5f - y) <= r + 0.5f) f(i, j)
        }
    }

    private fun hurtTroop(t: Troop, dmg: Float) {
        t.hp -= dmg
        t.hitFlash = 0.15f
    }

    private fun damageWall(i: Int, j: Int, dmg: Float) {
        if (wallHp[i][j] <= 0f) return
        wallHp[i][j] -= dmg
        if (wallHp[i][j] <= 0f) {
            wallHp[i][j] = 0f
            destroyVersion++
            sounds += Snd.WALL
            effects += Fx(FxKind.RUBBLE, i + 0.5f, j + 0.5f, time, 0.6f, 0.8f)
        }
    }

    private fun damageBuilding(b: Bld, dmg: Float) {
        if (!b.alive) return
        b.hp -= dmg
        b.hitFlash = 0.12f
        if (b.hp <= 0f) {
            b.hp = 0f
            b.destroyedAt = time
            for (i in b.x.toInt() until (b.x + b.s).toInt()) for (j in b.y.toInt() until (b.y + b.s).toInt()) occ[i][j] = -1
            destroyVersion++
            effects += Fx(FxKind.EXPLOSION, b.cx, b.cy, time, 0.7f, b.s * 1.1f)
            sounds += Snd.BOOM
            repeat(6) {
                val a = rng.nextFloat() * 6.283f
                val sp = 0.6f + rng.nextFloat() * 0.9f
                effects += Fx(
                    FxKind.SMOKE, b.cx, b.cy, time, 1.2f + rng.nextFloat() * 0.6f, 0.5f + rng.nextFloat() * 0.5f,
                    vx = cos(a) * sp, vy = sin(a) * sp - 0.4f,
                )
            }
        }
    }
}
