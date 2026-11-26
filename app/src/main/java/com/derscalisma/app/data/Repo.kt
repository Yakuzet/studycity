package com.derscalisma.app.data

import android.content.Context
import com.derscalisma.app.data.game.Game
import com.derscalisma.app.data.game.GameCatalog
import com.derscalisma.app.data.game.advanceGame
import com.derscalisma.app.data.game.migrateOldCity
import com.derscalisma.app.data.game.processTraining
import com.derscalisma.app.data.game.raidVillage
import com.derscalisma.app.notify.AlarmScheduler
import com.derscalisma.app.notify.Beeper
import com.derscalisma.app.notify.Notifier
import com.derscalisma.app.notify.Reminders
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

enum class PhaseEventKind { WORK_START, BREAK_START, FINISHED }

data class PhaseEvent(val kind: PhaseEventKind, val round: Int, val rounds: Int, val minutes: Int)

/**
 * Tüm uygulama durumunun tek kaynağı. Durum JSON olarak dosyaya yazılır.
 * Her değişiklik [mutate] içinden yapılır; bildirimler ve alarmlar değişiklikten sonra ayarlanır.
 */
object Repo {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
    private val lock = Any()
    private lateinit var ctx: Context
    private lateinit var file: File
    @Volatile private var initialized = false

    private val _state = MutableStateFlow(AppData())
    val state: StateFlow<AppData> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Uygulama ön plandaysa ödüller bildirim yerine ekranda gösterilir. */
    @Volatile var foreground = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            ctx = context.applicationContext
            file = File(ctx.filesDir, "data.json")
            _state.value = load()
            initialized = true
        }
    }

    fun toast(msg: String) {
        _messages.tryEmit(msg)
    }

    // ---------------------------------------------------------------- persistence

    private fun load(): AppData {
        if (!file.exists()) return AppData()
        return try {
            json.decodeFromString(AppData.serializer(), file.readText())
        } catch (e: Exception) {
            file.renameTo(File(file.parentFile, "data.corrupt.${System.currentTimeMillis()}.json"))
            AppData()
        }
    }

    private fun save(d: AppData) {
        val text = json.encodeToString(AppData.serializer(), d)
        val tmp = File(file.parentFile, "data.json.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    // ---------------------------------------------------------------- transaction

    class Tx(var data: AppData, val now: Long) {
        val notices = mutableListOf<String>()
        val alerts = mutableListOf<Pair<String, String>>()
        val phaseEvents = mutableListOf<PhaseEvent>()
        var beep = false
        var askFocus = false

        fun day(key: String): DayRecord = data.days[key] ?: DayRecord()

        fun updateDay(key: String, f: (DayRecord) -> DayRecord) {
            data = data.copy(days = data.days + (key to f(day(key))))
        }

        fun addCoins(amount: Int) {
            if (amount <= 0) return
            updateDay(Time.dayKey(now)) { it.copy(coinsEarned = it.coinsEarned + amount) }
            data = data.copy(coins = data.coins + amount, totalEarned = data.totalEarned + amount)
        }

        fun award(amount: Int, text: String) {
            addCoins(amount)
            val full = if (amount > 0) "$text  +$amount 🪙" else text
            log(full, amount)
            notices += full
        }

        fun log(text: String, coins: Int = 0) {
            data = data.copy(log = (listOf(LogEvent(now, text, coins)) + data.log).take(60))
        }
    }

    internal fun <T> mutate(block: Tx.() -> T): T {
        val (before, tx, result) = synchronized(lock) {
            val before = _state.value
            val tx = Tx(before, System.currentTimeMillis())
            val result = tx.block()
            if (tx.data != before) {
                _state.value = tx.data
                save(tx.data)
            }
            Triple(before, tx, result)
        }
        afterCommit(before, tx)
        return result
    }

    private fun afterCommit(before: AppData, tx: Tx) {
        val after = tx.data
        AlarmScheduler.syncTimer(ctx, after.timer, after.settings)
        if (after.timer != before.timer) Notifier.timerOngoing(ctx, after.timer)
        AlarmScheduler.scheduleReminders(ctx, after, tx.now)
        tx.phaseEvents.lastOrNull()?.let { Notifier.phase(ctx, it) }
        if (tx.beep || (tx.askFocus && foreground)) Beeper.beep(ctx, after.settings.focusVibrate)
        if (tx.askFocus && !foreground && after.focusAsk != null) Notifier.focusAsk(ctx)
        if (before.focusAsk != null && after.focusAsk == null) Notifier.cancelFocusAsk(ctx)
        tx.alerts.forEach { (title, text) -> Notifier.alert(ctx, title, text) }
        tx.notices.forEach { if (foreground) _messages.tryEmit(it) else Notifier.reward(ctx, it) }
    }

    // ---------------------------------------------------------------- lifecycle

    fun onAppResume() {
        mutate {
            processRollover()
            catchUp()
            pruneFired()
            migrateOldCity()
            processTraining()
            val ask = data.focusAsk
            if (ask != null && now - ask.askedAt > 30 * MINUTE_MS) data = data.copy(focusAsk = null)
        }
        val d = _state.value
        AlarmScheduler.syncTimer(ctx, d.timer, d.settings)
        Notifier.timerOngoing(ctx, d.timer)
    }

    fun rescheduleAll() = onAppResume()

    fun runReminders() = mutate {
        processRollover()
        catchUp()
        val today = Time.date(now)
        if (data.settings.remindersEnabled) {
            val due = (Reminders.forDay(data, today.minusDays(1)) + Reminders.forDay(data, today))
                .filter { it.at <= now && now - it.at <= 60 * MINUTE_MS && it.key !in data.firedReminders }
            for (r in due) {
                Reminders.evaluate(data, r, now)?.let { alerts += it }
                data = data.copy(firedReminders = data.firedReminders + r.key)
            }
        }
        pruneFired()
    }

    private fun Tx.pruneFired() {
        val cutoff = Time.date(now).minusDays(2).toString()
        val kept = data.firedReminders.filter { it.substringBefore('|') >= cutoff }.toSet()
        if (kept.size != data.firedReminders.size) data = data.copy(firedReminders = kept)
    }

    /** Geçmiş günleri kapatır: kaçırılan gün varsa dondurucu kullanır ya da seri kaybını bildirir. */
    private fun Tx.processRollover() {
        val today = Time.date(now)
        val yesterday = today.minusDays(1)
        val last = data.lastProcessedDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (last == null || last.isBefore(yesterday.minusDays(400))) {
            data = data.copy(lastProcessedDay = yesterday.toString())
            return
        }
        var d = last.plusDays(1)
        while (!d.isAfter(yesterday)) {
            val key = d.toString()
            val rec = day(key)
            if (!rec.dailyMet && !rec.frozen) {
                val prev = Streaks.dailyEndingAt(data, d.minusDays(1))
                if (prev > 0) {
                    if (data.streakFreezes > 0) {
                        updateDay(key) { it.copy(frozen = true) }
                        data = data.copy(streakFreezes = data.streakFreezes - 1)
                        log("🧊 Seri dondurucu kullanıldı, $prev günlük serin korundu")
                        alerts += "🧊 Serin korundu" to
                            "Hedefe ulaşamadığın bir gün oldu ama seri dondurucu $prev günlük serini kurtardı. Bugün hedefini tamamla!"
                    } else {
                        log("💔 $prev günlük seri sona erdi")
                        alerts += "💔 Serin sona erdi" to
                            "$prev günlük günlük hedef serin sıfırlandı. Bugün yeniden başla, yeni bir seri kur!"
                        raidVillage(prev)
                    }
                }
            }
            d = d.plusDays(1)
        }
        data = data.copy(lastProcessedDay = yesterday.toString())
    }

    // ---------------------------------------------------------------- timer

    fun tick() = mutate { catchUp() }

    fun startTimer() = mutate {
        catchUp()
        if (data.timer != null) return@mutate
        val s = data.settings
        data = data.copy(
            timer = TimerState(
                workMinutes = s.workMinutes,
                breakMinutes = s.breakMinutes,
                rounds = s.rounds,
                phaseStartedAt = now,
                phaseEndAt = now + s.workMinutes * MINUTE_MS,
                startedAt = now,
                nextBeepAt = Focus.nextBeepAt(s, now),
            ),
            lastSession = null,
        )
    }

    /** "Dikkatin toplu mu?" sorusunun cevabı. */
    fun answerFocus(focused: Boolean) = mutate {
        if (data.focusAsk == null) return@mutate
        updateDay(Time.dayKey(now)) {
            if (focused) it.copy(focusYes = it.focusYes + 1) else it.copy(focusNo = it.focusNo + 1)
        }
        data.timer?.let { t ->
            data = data.copy(
                timer = if (focused) t.copy(sessionFocusYes = t.sessionFocusYes + 1)
                else t.copy(sessionFocusNo = t.sessionFocusNo + 1)
            )
        }
        data.lastSession?.let { ls ->
            if (data.timer == null) data = data.copy(
                lastSession = if (focused) ls.copy(focusYes = ls.focusYes + 1) else ls.copy(focusNo = ls.focusNo + 1)
            )
        }
        addCoins(1) // dürüst cevap için küçük teşekkür, cevaba göre değişmez
        data = data.copy(focusAsk = null)
    }

    fun testBeep() = Beeper.beep(ctx, state.value.settings.focusVibrate)

    fun pauseTimer() = mutate {
        catchUp()
        val t = data.timer ?: return@mutate
        if (t.isPaused) return@mutate
        var nt = t
        if (t.phase == Phase.WORK) {
            creditWork(t.phaseStartedAt, now)
            nt = nt.copy(sessionWorkMs = nt.sessionWorkMs + (now - t.phaseStartedAt))
        }
        data = data.copy(timer = nt.copy(pausedRemainingMs = t.phaseEndAt - now, phaseStartedAt = now))
    }

    fun resumeTimer() = mutate {
        val t = data.timer ?: return@mutate
        val rem = t.pausedRemainingMs ?: return@mutate
        val pausedFor = now - t.phaseStartedAt // duraklatırken phaseStartedAt = duraklatma anı
        data = data.copy(
            timer = t.copy(
                pausedRemainingMs = null,
                phaseStartedAt = now,
                phaseEndAt = now + rem,
                nextBeepAt = t.nextBeepAt?.plus(pausedFor),
            )
        )
    }

    fun skipPhase() = mutate {
        catchUp()
        var t = data.timer ?: return@mutate
        if (t.phase == Phase.WORK && !t.isPaused) {
            creditWork(t.phaseStartedAt, now)
            t = t.copy(sessionWorkMs = t.sessionWorkMs + (now - t.phaseStartedAt))
        }
        val next = nextPhase(t, now)
        if (next == null) finishSession(t, completed = false)
        else data = data.copy(timer = next)
    }

    fun stopTimer() = mutate {
        catchUp()
        var t = data.timer ?: return@mutate
        if (t.phase == Phase.WORK && !t.isPaused) {
            creditWork(t.phaseStartedAt, now)
            t = t.copy(sessionWorkMs = t.sessionWorkMs + (now - t.phaseStartedAt))
        }
        finishSession(t, completed = false)
    }

    private fun Tx.catchUp() {
        var t = data.timer ?: return
        if (t.isPaused) return
        while (true) {
            if (t.phase == Phase.WORK) t = processFocus(t, minOf(now, t.phaseEndAt - 1))
            if (now < t.phaseEndAt) break
            if (t.phase == Phase.WORK) {
                creditWork(t.phaseStartedAt, t.phaseEndAt)
                t = t.copy(sessionWorkMs = t.sessionWorkMs + (t.phaseEndAt - t.phaseStartedAt))
                if (data.settings.focusCheckDivisions == 1) requestFocusAsk()
            }
            val next = nextPhase(t, t.phaseEndAt)
            if (next == null) {
                finishSession(t, completed = true)
                return
            }
            t = next
            phaseEvents += if (t.phase == Phase.WORK)
                PhaseEvent(PhaseEventKind.WORK_START, t.round, t.rounds, t.workMinutes)
            else
                PhaseEvent(PhaseEventKind.BREAK_START, t.round, t.rounds, t.breakMinutes)
        }
        data = data.copy(timer = t)
    }

    /** Çalışma fazında [upTo] anına kadar gelen bip ve ara soruları işler. */
    private fun Tx.processFocus(t: TimerState, upTo: Long): TimerState {
        val s = data.settings
        var nt = t
        val beepAt = nt.nextBeepAt
        if (s.focusBeepEnabled) {
            if (beepAt == null) {
                nt = nt.copy(nextBeepAt = Focus.nextBeepAt(s, upTo))
            } else if (upTo >= beepAt) {
                // Telefon uykudaysa birden fazla bip kaçmış olabilir: sadece bir kez çal.
                beep = true
                if (s.focusAskAfterBeep) requestFocusAsk()
                nt = nt.copy(nextBeepAt = Focus.nextBeepAt(s, maxOf(beepAt, upTo)))
            }
        }
        var asked = false
        while (true) {
            val at = Focus.nextCheckAt(nt, s) ?: break
            if (upTo < at) break
            nt = nt.copy(checksDone = nt.checksDone + 1)
            asked = true
        }
        if (asked) requestFocusAsk()
        return nt
    }

    private fun Tx.requestFocusAsk() {
        if (data.focusAsk == null) data = data.copy(focusAsk = FocusAsk(now))
        askFocus = true
    }

    private fun Tx.nextPhase(t: TimerState, at: Long): TimerState? = when (t.phase) {
        Phase.WORK ->
            if (t.round >= t.rounds) null
            else if (t.breakMinutes <= 0) t.copy(
                round = t.round + 1, phase = Phase.WORK, pausedRemainingMs = null,
                phaseStartedAt = at, phaseEndAt = at + t.workMinutes * MINUTE_MS,
                nextBeepAt = Focus.nextBeepAt(data.settings, at), checksDone = 0,
            )
            else t.copy(
                phase = Phase.BREAK, pausedRemainingMs = null,
                phaseStartedAt = at, phaseEndAt = at + t.breakMinutes * MINUTE_MS,
                nextBeepAt = null, checksDone = 0,
            )
        Phase.BREAK -> t.copy(
            round = t.round + 1, phase = Phase.WORK, pausedRemainingMs = null,
            phaseStartedAt = at, phaseEndAt = at + t.workMinutes * MINUTE_MS,
            nextBeepAt = Focus.nextBeepAt(data.settings, at), checksDone = 0,
        )
    }

    private fun Tx.finishSession(t: TimerState, completed: Boolean) {
        data = data.copy(timer = null)
        val worked = t.sessionWorkMs
        updateDay(Time.dayKey(now)) { it.copy(sessions = it.sessions + 1) }
        if (completed) {
            phaseEvents += PhaseEvent(PhaseEventKind.FINISHED, t.round, t.rounds, (worked / MINUTE_MS).toInt())
            award(Rewards.sessionComplete(t.rounds), "✅ ${t.rounds} turluk oturumu tamamladın!")
        } else if (worked >= MINUTE_MS) {
            log("⏹ Oturum bitirildi: ${Time.duration(worked)} çalışıldı")
        }
        if (worked >= MINUTE_MS && data.goals.isNotEmpty()) {
            data = data.copy(lastSession = SessionSummary(worked, now, t.sessionFocusYes, t.sessionFocusNo))
        }
    }

    // ---------------------------------------------------------------- crediting & rewards

    private fun Tx.creditWork(start: Long, end: Long) {
        if (end <= start) return
        var s = start
        while (s < end) {
            val date = Time.date(s)
            val e = minOf(end, Time.startOfDay(date.plusDays(1)))
            creditPiece(date, s, e)
            s = e
        }
    }

    private fun Tx.windowsOn(date: LocalDate) = data.windows.filter { date.dayOfWeek.value in it.days }

    private fun Tx.creditPiece(date: LocalDate, s: Long, e: Long) {
        val key = date.toString()
        val day0 = Time.startOfDay(date)
        val windows = windowsOn(date)
        val old = day(key).studyMs
        updateDay(key) { rec ->
            val wm = rec.windowMs.toMutableMap()
            for (w in windows) {
                val ov = minOf(e, day0 + w.endMin * MINUTE_MS) - maxOf(s, day0 + w.startMin * MINUTE_MS)
                if (ov > 0) wm[w.id] = (wm[w.id] ?: 0L) + ov
            }
            rec.copy(studyMs = rec.studyMs + (e - s), windowMs = wm)
        }
        val minutes = ((old + (e - s)) / MINUTE_MS - old / MINUTE_MS).toInt()
        val base = minutes * Rewards.COINS_PER_MINUTE
        val bonus = (base * Game.libraryBonus(data.game) / 100f).roundToInt()
        addCoins(base + bonus)
        advanceGame(e - s)
        checkAchievements(date, e)
    }

    private fun Tx.checkAchievements(date: LocalDate, at: Long) {
        val key = date.toString()
        for (w in windowsOn(date)) {
            val ms = day(key).windowMs[w.id] ?: 0L
            if (ms >= Rewards.ATTEND_MS && w.id !in day(key).windowsAttended) {
                updateDay(key) { it.copy(windowsAttended = it.windowsAttended + w.id) }
                val st = Streaks.windowAttend(data, at)
                award(Rewards.windowAttend(st), "📅 ${w.label} aralığında çalıştın! Aralık serisi: $st")
            }
            if (ms >= w.targetMinutes * MINUTE_MS && w.id !in day(key).windowsMet) {
                updateDay(key) { it.copy(windowsMet = it.windowsMet + w.id) }
                val st = Streaks.windowGoal(data, at)
                award(Rewards.windowGoal(st), "🎯 ${w.label} hedefini tamamladın! Aralık hedef serisi: $st")
            }
        }

        val goalMs = data.settings.dailyGoalMinutes * MINUTE_MS
        if (!day(key).dailyMet && day(key).studyMs >= goalMs) {
            updateDay(key) { it.copy(dailyMet = true) }
            val st = Streaks.daily(data, date)
            award(Rewards.daily(st), "🔥 Günlük hedefe ulaştın! Seri: $st gün")
            Rewards.milestones[st]?.let { award(it, "🏅 $st günlük seri rozeti kazandın!") }
            val oldBest = data.bestDailyStreak
            if (st > oldBest) {
                data = data.copy(bestDailyStreak = st)
                GameCatalog.DECOR_UNLOCK
                    .filter { (_, need) -> need in (oldBest + 1)..st }
                    .forEach { (t, _) -> notices += "🏡 Köyün için yeni süs açıldı: ${t.title}" }
            }
        }

        for (g in data.goals) checkGoal(g, date)
    }

    private fun Tx.checkGoal(g: Goal, date: LocalDate) {
        val key = date.toString()
        val rec = day(key)
        if ((rec.progress[g.id] ?: 0) >= g.dailyTarget && g.id !in rec.goalsMet) {
            updateDay(key) { it.copy(goalsMet = it.goalsMet + g.id) }
            val st = Streaks.goal(data, g.id, date)
            award(Rewards.goal(st), "${g.type.emoji} ${g.name} hedefi tamam! Seri: $st gün")
        }
    }

    private fun Tx.addProgressInternal(goalId: String, delta: Int) {
        val today = Time.date(now)
        val key = today.toString()
        updateDay(key) {
            val v = ((it.progress[goalId] ?: 0) + delta).coerceAtLeast(0)
            it.copy(progress = it.progress + (goalId to v))
        }
        data.goals.find { it.id == goalId }?.let { checkGoal(it, today) }
    }

    // ---------------------------------------------------------------- goals & settings

    fun updateSettings(f: (Settings) -> Settings) = mutate {
        val old = data.settings
        val s = f(old)
        data = data.copy(settings = s)
        // Bip ayarı değiştiyse çalışan turdaki sıradaki bipi yeniden planla.
        val t = data.timer
        val beepChanged = old.focusBeepEnabled != s.focusBeepEnabled || old.focusBeepRandom != s.focusBeepRandom ||
            old.focusBeepEvery != s.focusBeepEvery || old.focusBeepMin != s.focusBeepMin || old.focusBeepMax != s.focusBeepMax
        if (t != null && beepChanged && t.phase == Phase.WORK) {
            val base = if (t.isPaused) t.phaseStartedAt else now
            data = data.copy(timer = t.copy(nextBeepAt = Focus.nextBeepAt(s, base)))
        }
        checkAchievements(Time.date(now), now)
    }

    fun addProgress(goalId: String, delta: Int) = mutate { addProgressInternal(goalId, delta) }

    fun logSession(values: Map<String, Int>) = mutate {
        values.forEach { (id, v) -> if (v > 0) addProgressInternal(id, v) }
        data = data.copy(lastSession = null)
    }

    fun dismissSession() = mutate { data = data.copy(lastSession = null) }

    fun addGoal(type: GoalType, name: String, unit: String, target: Int) = mutate {
        val g = Goal(Time.newId(), type, name.ifBlank { type.defaultName }, target.coerceAtLeast(1), unit.ifBlank { type.defaultUnit })
        data = data.copy(goals = data.goals + g)
    }

    fun setGoalTarget(goalId: String, target: Int) = mutate {
        data = data.copy(goals = data.goals.map { if (it.id == goalId) it.copy(dailyTarget = target.coerceAtLeast(1)) else it })
        data.goals.find { it.id == goalId }?.let { checkGoal(it, Time.date(now)) }
    }

    fun deleteGoal(goalId: String) = mutate {
        data = data.copy(goals = data.goals.filterNot { it.id == goalId })
    }

    fun saveWindow(w: StudyWindow) = mutate {
        val exists = data.windows.any { it.id == w.id }
        data = data.copy(
            windows = if (exists) data.windows.map { if (it.id == w.id) w else it } else data.windows + w
        )
        checkAchievements(Time.date(now), now)
    }

    fun deleteWindow(id: String) = mutate {
        data = data.copy(windows = data.windows.filterNot { it.id == id })
    }

    // ---------------------------------------------------------------- market

    fun buy(itemId: String): String? = mutate {
        val item = Catalog.item(itemId) ?: return@mutate "Ürün bulunamadı"
        if (item.id == FREEZE_ID) {
            if (data.streakFreezes >= MAX_FREEZES) return@mutate "En fazla $MAX_FREEZES dondurucu taşıyabilirsin"
            if (data.coins < item.price) return@mutate "Yetersiz para: ${item.price - data.coins} 🪙 daha lazım"
            data = data.copy(coins = data.coins - item.price, streakFreezes = data.streakFreezes + 1)
            return@mutate "🧊 Seri dondurucu alındı"
        }
        if (item.id in data.owned || item.price == 0) {
            equipInternal(item)
            return@mutate null
        }
        if (data.coins < item.price) return@mutate "Yetersiz para: ${item.price - data.coins} 🪙 daha lazım"
        data = data.copy(coins = data.coins - item.price, owned = data.owned + item.id)
        equipInternal(item)
        "${item.emoji} ${item.name} satın alındı"
    }

    fun equip(itemId: String) = mutate {
        val item = Catalog.item(itemId) ?: return@mutate
        if (item.id in data.owned || item.price == 0) equipInternal(item)
        Unit
    }

    private fun Tx.equipInternal(item: MarketItem) {
        data = when (item.category) {
            ItemCategory.BACKGROUND -> data.copy(background = item.id)
            ItemCategory.THEME -> data.copy(theme = item.id)
            ItemCategory.GROUND -> data.copy(ground = item.id)
            ItemCategory.TIMER -> data.copy(timerStyle = item.id)
            ItemCategory.BOOST -> data
        }
    }

    fun resetAll() = mutate {
        data = AppData(lastProcessedDay = Time.date(now).minusDays(1).toString())
    }
}
