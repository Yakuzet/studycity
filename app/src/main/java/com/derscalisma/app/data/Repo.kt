package com.derscalisma.app.data

import android.content.Context
import com.derscalisma.app.audio.Cue
import com.derscalisma.app.data.game.Game
import com.derscalisma.app.data.game.GameCatalog
import com.derscalisma.app.data.game.advanceGame
import com.derscalisma.app.data.game.ensureBoss
import com.derscalisma.app.data.game.giveChest
import com.derscalisma.app.data.game.hitBoss
import com.derscalisma.app.data.game.migrateOldCity
import com.derscalisma.app.data.game.processSpells
import com.derscalisma.app.data.game.processTraining
import com.derscalisma.app.data.game.raidVillage
import com.derscalisma.app.i18n.Fmt
import com.derscalisma.app.i18n.I18n
import com.derscalisma.app.i18n.S
import com.derscalisma.app.notify.AlarmScheduler
import com.derscalisma.app.notify.Beeper
import com.derscalisma.app.notify.Notifier
import com.derscalisma.app.notify.Reminders
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import kotlin.math.roundToInt

enum class PhaseEventKind { WORK_START, BREAK_START, ROUND_END, BREAK_END, FINISHED }

data class PhaseEvent(val kind: PhaseEventKind, val round: Int, val rounds: Int, val minutes: Int)

const val SCHEMA = 15
private const val LEDGER_MAX = 400
private const val LOGS_MAX = 400

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
    private lateinit var store: SaveStore
    @Volatile private var initialized = false

    private val _state = MutableStateFlow(AppData())
    val state: StateFlow<AppData> = _state.asStateFlow()

    /** Ekranda gösterilecek kısa mesajlar. Arabellekli: ekran hazır olmadan gelenler kaybolmaz. */
    private val _messages = Channel<String>(capacity = 32)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Oturum dışında kazanılan para (ekrandaki uçan para animasyonu için). Arayüz bağlar. */
    @Volatile var onEarned: ((Int) -> Unit)? = null

    /** Uygulama ön plandaysa ödüller bildirim yerine ekranda gösterilir. */
    @Volatile var foreground = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            ctx = context.applicationContext
            store = SaveStore(ctx.filesDir, json)
            var d = store.load()
            if (store.freshInstall) {
                d = d.copy(schema = SCHEMA, onboarded = true, settings = d.settings.copy(language = "tr"))
            } else if (d.schema < SCHEMA) {
                // v1.4 ve öncesi: arayüz Türkçeydi, öyle kalsın.
                d = d.copy(schema = SCHEMA, settings = d.settings.copy(language = d.settings.language.ifBlank { "tr" }))
            }
            _state.value = d
            I18n.apply(d.settings.language)
            initialized = true
        }
        store.recoveryNotice?.let { toast(it) }
    }

    fun toast(msg: String) {
        _messages.trySend(msg)
    }

    // ---------------------------------------------------------------- persistence & yedekleme

    private fun save(d: AppData) {
        try {
            store.save(d, System.currentTimeMillis())
        } catch (e: Exception) {
            toast(S.engine.saveFailed(e.message ?: ""))
        }
    }

    /** Bu açılışta hiç kayıt yoktu (yeni kurulum ya da uygulama silinip kuruldu). */
    val freshInstall: Boolean get() = initialized && store.freshInstall

    fun dismissFreshInstall() = store.dismissFresh()

    fun exportJson(): String = json.encodeToString(AppData.serializer(), _state.value)

    /** Dışarıdan bir yedeği yükler; mevcut kayıt ayrıca saklanır. Hata varsa mesaj döner. */
    fun importJson(text: String): String? {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        if (obj == null || !looksLikeSave(obj)) return S.engine.notABackup
        val d = store.strict(text) ?: store.lenient(text) ?: return S.engine.notABackup
        restore(d)
        return null
    }

    private fun looksLikeSave(o: JsonObject) = "settings" in o && ("days" in o || "coins" in o || "game" in o)

    fun restoreFromFile(name: String): String? {
        val d = store.readFile(name) ?: return S.engine.fileUnreadable
        restore(d)
        return null
    }

    private fun restore(d: AppData) {
        store.keepCurrentCopy()
        store.dismissFresh()
        // Yedek bir oturum sürerken alınmış olabilir: o eski oturumu "çalışılmış" sayma.
        mutate { data = d.copy(timer = null, focusAsk = null, away = null, lastSession = null, schema = SCHEMA, onboarded = true) }
        onAppResume()
        toast(S.engine.restored(d.coins, d.bestDailyStreak))
    }

    fun saveFiles(): List<java.io.File> = store.allFiles()

    /** İndirilenler klasörüne otomatik yedek (en fazla 30 dakikada bir, arka planda). */
    fun backupExternally(force: Boolean = false) {
        if (!initialized || !ExternalBackup.supported) return
        if (!force && System.currentTimeMillis() - ExternalBackup.lastBackupAt(ctx) < ExternalBackup.INTERVAL_MS) return
        val d = _state.value
        if (d.totalEarned == 0 && d.days.isEmpty()) return // boş kayıtla eski yedeğin üzerine yazma
        val text = exportJson()
        Thread {
            val ok = ExternalBackup.write(ctx, text)
            if (force) toast(if (ok) S.engine.backupWritten else S.engine.backupFailed)
        }.start()
    }

    // ---------------------------------------------------------------- transaction

    class Tx(var data: AppData, val now: Long) {
        val notices = mutableListOf<String>()
        val alerts = mutableListOf<Pair<String, String>>()
        val phaseEvents = mutableListOf<PhaseEvent>()
        var beep = false
        var askFocus = false
        var focusWarn = false
        /** Oturum dışında kazanılıp ekranda uçacak para. */
        var visibleEarned = 0
        /** Ön plandaysa çalınacak sesler (Sfx, Jingle ya da Cue). */
        val sounds = mutableListOf<Any>()

        fun day(key: String): DayRecord = data.days[key] ?: DayRecord()

        fun updateDay(key: String, f: (DayRecord) -> DayRecord) {
            data = data.copy(days = data.days + (key to f(day(key))))
        }

        /** Şu an hangi oturuma kazanç yazılıyor (sürmekte olan ya da özeti açık olan). */
        private fun sessionId(): Long = data.timer?.id ?: data.lastSession?.id?.takeIf { it != 0L } ?: 0L

        /** Ders parası kazancı / harcaması. Aynı oturumdaki aynı tür kazançlar tek satırda birleşir. */
        fun earn(kind: EarnKind, amount: Int, n: Int = 0, label: String = "") {
            if (amount == 0) return
            val session = sessionId()
            if (amount > 0) {
                updateDay(Time.dayKey(now)) { it.copy(coinsEarned = it.coinsEarned + amount) }
                data = data.copy(coins = data.coins + amount, totalEarned = data.totalEarned + amount)
            } else {
                data = data.copy(coins = (data.coins + amount).coerceAtLeast(0))
            }
            val idx = if (session != 0L) data.ledger.indexOfFirst { it.session == session && it.kind == kind && it.label == label } else -1
            val ledger = if (idx >= 0) {
                val old = data.ledger[idx]
                data.ledger.toMutableList().also { it[idx] = old.copy(amount = old.amount + amount, n = old.n + n, at = now) }
            } else {
                (listOf(Earning(now, amount, kind, n, label, session)) + data.ledger).take(LEDGER_MAX)
            }
            data = data.copy(ledger = ledger)
            if (session == 0L && amount > 0) visibleEarned += amount
        }

        /** Eski API (köy kodu): türsüz para ekleme. */
        fun addCoins(amount: Int) = earn(EarnKind.GAME, amount)

        /** Eski API (köy kodu): para + metin. */
        fun award(amount: Int, text: String) {
            earn(EarnKind.GAME, amount, label = text)
            log(text, amount)
            notices += if (amount > 0) "$text  +$amount" else text
            if (amount > 0) sounds += com.derscalisma.app.audio.Sfx.COIN
        }

        /** Kazanç + (oturum yoksa) kısa bildirim. */
        fun reward(kind: EarnKind, amount: Int, n: Int = 0, label: String = "") {
            earn(kind, amount, n, label)
            if (data.timer == null && data.lastSession == null) {
                notices += S.rewards.earning(kind, n, label) + "  +" + Fmt.number(amount)
            }
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
        if (after.settings.language != before.settings.language) {
            I18n.apply(after.settings.language)
            Notifier.createChannels(ctx)
        }
        AlarmScheduler.syncTimer(ctx, after)
        if (after.timer != before.timer) Notifier.timerOngoing(ctx, after.timer, after.settings)
        AlarmScheduler.scheduleReminders(ctx, after, tx.now)
        val fg = foreground
        tx.phaseEvents.lastOrNull()?.let { e ->
            if (fg) {
                if (after.settings.phaseSounds) {
                    tx.sounds += when (e.kind) {
                        PhaseEventKind.WORK_START -> Cue.WORK_START
                        PhaseEventKind.BREAK_START -> Cue.BREAK_START
                        PhaseEventKind.ROUND_END, PhaseEventKind.BREAK_END -> Cue.ROUND_END
                        PhaseEventKind.FINISHED -> Cue.SESSION_DONE
                    }
                    Beeper.vibrate(ctx, short = true)
                }
            } else {
                Notifier.phase(ctx, e)
            }
        }
        if (tx.beep || tx.askFocus || tx.focusWarn) {
            if (fg) {
                tx.sounds += if (tx.focusWarn) Cue.FOCUS_WARN else Cue.FOCUS_ASK
                if (after.settings.focusVibrate) Beeper.vibrate(ctx, short = false)
            } else {
                Beeper.beep(ctx, after.settings.focusVibrate)
            }
        }
        val ask = after.focusAsk
        if (ask != null && ask.visible && (tx.askFocus || tx.focusWarn) && !fg) Notifier.focusAsk(ctx, ask, after.settings)
        if (before.focusAsk != null && (ask == null || !ask.visible)) Notifier.cancelFocusAsk(ctx)
        tx.alerts.forEach { (title, text) -> Notifier.alert(ctx, title, text) }
        tx.notices.forEach { if (fg) toast(it) else Notifier.reward(ctx, it) }
        if (fg && tx.visibleEarned > 0) onEarned?.invoke(tx.visibleEarned)
        if (fg) tx.sounds.forEach {
            when (it) {
                is com.derscalisma.app.audio.Sfx -> com.derscalisma.app.audio.Sound.play(it)
                is com.derscalisma.app.audio.Jingle -> com.derscalisma.app.audio.Sound.jingle(it)
                is Cue -> com.derscalisma.app.audio.Sound.cue(it)
            }
        }
    }

    // ---------------------------------------------------------------- lifecycle

    fun onAppResume() {
        mutate {
            checkpoint()
            processRollover()
            catchUp()
            pruneFired()
            migrateOldCity()
            ensureBoss()
            processTraining()
            processSpells()
        }
        val d = _state.value
        AlarmScheduler.syncTimer(ctx, d)
        Notifier.timerOngoing(ctx, d.timer, d.settings)
    }

    fun rescheduleAll() = onAppResume()

    fun runReminders() {
        runRemindersTx()
        backupExternally()
    }

    private fun runRemindersTx() = mutate {
        checkpoint()
        processRollover()
        catchUp()
        val today = Time.date(now)
        if (data.settings.remindersEnabled) {
            val due = Reminders.forDay(data, today, now)
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

    /**
     * Sürmekte olan çalışmayı şu ana kadar kaydeder (faz bitmeden). Gece yarısı kapanışından önce
     * çağrılır: yoksa dün çalışılan ama henüz yazılmamış süre yüzünden seri yanlışlıkla bozulur.
     */
    private fun Tx.checkpoint() {
        val t = data.timer ?: return
        if (!t.isRunning || t.phase != Phase.WORK || data.focusAsk != null) return
        if (now <= t.phaseStartedAt || now >= t.phaseEndAt) return
        if (Time.dayKey(t.phaseStartedAt) == Time.dayKey(now)) return
        val dayStart = Time.startOfDay(Time.date(now))
        creditWork(t.phaseStartedAt, dayStart, t)
        data = data.copy(timer = t.copy(phaseStartedAt = dayStart, sessionWorkMs = t.sessionWorkMs + (dayStart - t.phaseStartedAt),
            overtimeMs = t.overtimeMs + if (t.overtime) dayStart - t.phaseStartedAt else 0))
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
        if (!yesterday.isAfter(last)) return // saat geri alındıysa geriye gitme
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
                        alerts += S.engine.freezeUsedTitle to S.engine.freezeUsedText(prev)
                    } else {
                        alerts += S.engine.streakLostTitle to S.engine.streakLostText(prev)
                        raidVillage(prev)
                    }
                }
            }
            d = d.plusDays(1)
        }
        data = data.copy(lastProcessedDay = yesterday.toString())
    }

    // ---------------------------------------------------------------- timer: genel

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
                segStart = now,
            ),
            lastSession = null,
            away = null,
        )
        sounds += Cue.WORK_START
    }

    /**
     * Çalışan oturumun ayarlarını değiştirir. Geçen süre korunur: örneğin 25 dk'lık turun 10. dakikasında
     * süreyi 40 dk yaparsan kalan 15 dk 30 dk olur. Yeni değerler varsayılan ayar olarak da kaydedilir.
     */
    fun adjustActiveTimer(work: Int? = null, brk: Int? = null, rounds: Int? = null) = mutate {
        catchUp()
        var t = data.timer ?: return@mutate
        if (work != null && work != t.workMinutes) {
            val delta = (work - t.workMinutes) * MINUTE_MS
            t = t.copy(workMinutes = work)
            if (t.phase == Phase.WORK && !t.waiting && !t.stopwatch) t = shiftPhase(t, delta)
        }
        if (brk != null && brk != t.breakMinutes) {
            val delta = (brk - t.breakMinutes) * MINUTE_MS
            t = t.copy(breakMinutes = brk)
            if (t.phase == Phase.BREAK && !t.waiting) t = shiftPhase(t, delta)
        }
        if (rounds != null) t = t.copy(rounds = rounds.coerceAtLeast(t.round))
        data = data.copy(
            timer = t,
            settings = data.settings.copy(workMinutes = t.workMinutes, breakMinutes = t.breakMinutes, rounds = t.rounds),
        )
        catchUp()
    }

    /** Sadece şu anki fazı uzatır / kısaltır (ayarları değiştirmez). Kısaltma fazı bitiremez. */
    fun extendPhase(minutes: Int) = mutate {
        catchUp()
        val t = data.timer ?: return@mutate
        if (t.waiting || t.stopwatch) return@mutate
        val delta = minutes * MINUTE_MS
        data = data.copy(timer = shiftPhase(t, delta).let { it.copy(phaseExtraMs = it.phaseExtraMs + delta) })
        catchUp()
    }

    private fun Tx.shiftPhase(t: TimerState, delta: Long): TimerState {
        val rem = t.pausedRemainingMs
        return if (rem != null) t.copy(pausedRemainingMs = (rem + delta).coerceAtLeast(MINUTE_MS))
        else if (delta >= 0) t.copy(phaseEndAt = t.phaseEndAt + delta)
        else t.copy(phaseEndAt = maxOf(minOf(t.phaseEndAt, now + MINUTE_MS), t.phaseEndAt + delta))
    }

    fun testBeep() = Beeper.beep(ctx, state.value.settings.focusVibrate)

    fun pauseTimer(reason: PauseReason? = null) = mutate {
        catchUp()
        var t = data.timer ?: return@mutate
        if (t.isPaused || t.waiting) return@mutate
        if (t.phase == Phase.WORK) {
            creditWork(t.phaseStartedAt, now, t)
            t = t.copy(sessionWorkMs = t.sessionWorkMs + (now - t.phaseStartedAt), overtimeMs = t.overtimeMs + if (t.overtime) now - t.phaseStartedAt else 0)
        }
        t = closeSeg(t, if (t.phase == Phase.WORK) SegKind.WORK else SegKind.BREAK, now)
        // Kullanıcı buradaysa bekleyen dikkat zinciri anlamını yitirir.
        data = data.copy(focusAsk = null, timer = t.copy(pausedRemainingMs = t.phaseEndAt - now, phaseStartedAt = now, pauseReason = reason, segStart = now))
    }

    /** Duraklatılmışken sebep seçme / değiştirme. */
    fun setPauseReason(reason: PauseReason?) = mutate {
        val t = data.timer ?: return@mutate
        if (!t.isPaused) return@mutate
        data = data.copy(timer = t.copy(pauseReason = reason))
    }

    fun resumeTimer() = mutate {
        var t = data.timer ?: return@mutate
        val rem = t.pausedRemainingMs ?: return@mutate
        val pausedFor = now - t.phaseStartedAt // duraklatırken phaseStartedAt = duraklatma anı
        t = closeSeg(t, SegKind.PAUSE, now, reason = t.pauseReason)
        data = data.copy(
            away = null,
            timer = t.copy(
                pausedRemainingMs = null,
                phaseStartedAt = now,
                phaseEndAt = now + rem,
                nextBeepAt = t.nextBeepAt?.plus(pausedFor),
                pauseReason = null,
                segStart = now,
            ),
        )
    }

    /** Fazı şimdi bitirir. Mola erken bitiyorsa: yarısı dolduysa ödül, dolmadıysa sıradaki tur yorgun. */
    fun skipPhase() = mutate {
        catchUp()
        var t = data.timer ?: return@mutate
        if (t.waiting) {
            beginNext(t, now)
            return@mutate
        }
        if (t.phase == Phase.WORK) {
            if (!t.isPaused) {
                creditWork(t.phaseStartedAt, now, t)
                t = t.copy(sessionWorkMs = t.sessionWorkMs + (now - t.phaseStartedAt), overtimeMs = t.overtimeMs + if (t.overtime) now - t.phaseStartedAt else 0)
                t = closeSeg(t, SegKind.WORK, now)
            } else t = closeSeg(t, SegKind.PAUSE, now, reason = t.pauseReason)
            data = data.copy(focusAsk = null)
            beginNext(t.copy(pausedRemainingMs = null, phaseEndAt = now), now)
        } else {
            val remaining = t.pausedRemainingMs ?: (t.phaseEndAt - now).coerceAtLeast(0)
            val total = t.phaseTotalMs
            val elapsed = total - remaining
            t = closeSeg(t, if (t.isPaused) SegKind.PAUSE else SegKind.BREAK, now, reason = t.pauseReason)
            if (elapsed >= total * Rewards.BREAK_MIN_FRACTION) {
                val unused = (remaining / MINUTE_MS).toInt()
                if (unused > 0) earn(EarnKind.EARLY_RETURN, unused * Rewards.EARLY_RETURN_PER_MIN, unused)
            } else {
                t = t.copy(fatigue = true)
            }
            beginNext(t.copy(pausedRemainingMs = null, phaseEndAt = now), now)
        }
    }

    /** Molayı atlamak "dinlenmemiş" sayılır mı (yarısı dolmadı)? Arayüz uyarı göstermek için sorar. */
    fun breakSkipIsEarly(t: TimerState, now: Long): Boolean {
        if (t.phase != Phase.BREAK || t.waiting) return false
        val remaining = t.pausedRemainingMs ?: (t.phaseEndAt - now).coerceAtLeast(0)
        return t.phaseTotalMs - remaining < t.phaseTotalMs * Rewards.BREAK_MIN_FRACTION
    }

    fun stopTimer() = mutate {
        catchUp()
        var t = data.timer ?: return@mutate
        if (t.phase == Phase.WORK && t.isRunning) {
            creditWork(t.phaseStartedAt, now, t)
            t = t.copy(sessionWorkMs = t.sessionWorkMs + (now - t.phaseStartedAt), overtimeMs = t.overtimeMs + if (t.overtime) now - t.phaseStartedAt else 0)
            t = closeSeg(t, SegKind.WORK, now)
        } else if (t.isRunning) {
            t = closeSeg(t, SegKind.BREAK, now)
        } else if (t.isPaused) {
            t = closeSeg(t, SegKind.PAUSE, now, reason = t.pauseReason)
        }
        finishSession(t, completed = false)
    }

    // ---------------------------------------------------------------- timer: geçişler, ek süre, mola

    /** Bekleme ekranında "şimdi başla": sıradaki faza geç. */
    fun startNext() = mutate {
        catchUp()
        val t = data.timer ?: return@mutate
        if (t.waiting) beginNext(t, now)
    }

    /** Tur bitti ama soru yarım: [minutes] dk ek süre (geri sayım). Tur bittikten hemen sonraysa aradaki süre de sayılır. */
    fun addOvertime(minutes: Int) = mutate {
        catchUp()
        val t = data.timer ?: return@mutate
        if (t.phase != Phase.WORK) return@mutate
        if (t.waiting) {
            val start = if (now - t.phaseEndAt <= Rewards.OVERTIME_BACKDATE_MS) t.phaseEndAt else now
            data = data.copy(timer = t.copy(
                waiting = false, autoAt = null, overtime = true, stopwatch = false,
                phaseStartedAt = start, segStart = start, phaseEndAt = maxOf(start + minutes * MINUTE_MS, now + 30_000),
                nextBeepAt = Focus.nextBeepAt(data.settings, start), checksDone = data.settings.focusCheckDivisions,
            ))
        } else if (t.overtime && !t.stopwatch) {
            data = data.copy(timer = shiftPhase(t, minutes * MINUTE_MS))
        }
        catchUp()
    }

    /** Tur bitti: kronometreyle devam et (istediğinde "bitti" de). */
    fun startStopwatch() = mutate {
        catchUp()
        val t = data.timer ?: return@mutate
        if (t.phase != Phase.WORK || !t.waiting) return@mutate
        val start = if (now - t.phaseEndAt <= Rewards.OVERTIME_BACKDATE_MS) t.phaseEndAt else now
        data = data.copy(timer = t.copy(
            waiting = false, autoAt = null, overtime = true, stopwatch = true,
            phaseStartedAt = start, segStart = start, phaseEndAt = start + Rewards.MAX_OVERTIME_MIN * MINUTE_MS,
            nextBeepAt = Focus.nextBeepAt(data.settings, start), checksDone = data.settings.focusCheckDivisions,
        ))
    }

    /** Kronometreyi durdur: ek süre kaydedilir, sıradaki faza geçilir. */
    fun finishStopwatch() = mutate {
        catchUp()
        var t = data.timer ?: return@mutate
        if (!t.stopwatch) return@mutate
        if (t.isRunning) {
            creditWork(t.phaseStartedAt, now, t)
            t = t.copy(sessionWorkMs = t.sessionWorkMs + (now - t.phaseStartedAt), overtimeMs = t.overtimeMs + (now - t.phaseStartedAt))
            t = closeSeg(t, SegKind.WORK, now)
        } else t = closeSeg(t, SegKind.PAUSE, now, reason = t.pauseReason)
        data = data.copy(focusAsk = null)
        beginNext(t.copy(pausedRemainingMs = null, phaseEndAt = now, stopwatch = false), now)
    }

    /** Kronometrede geçen ek süre. */
    fun stopwatchElapsed(t: TimerState, now: Long): Long {
        val rem = t.pausedRemainingMs ?: (t.phaseEndAt - now)
        return (Rewards.MAX_OVERTIME_MIN * MINUTE_MS - rem).coerceAtLeast(0)
    }

    /** Mola uzatma satın al (+5 dk). Hata varsa mesaj döner. */
    fun extendBreak(): String? = mutate {
        catchUp()
        val t = data.timer ?: return@mutate null
        if (t.phase != Phase.BREAK) return@mutate null
        if (t.extensionBlocked) return@mutate S.engine.extensionBlocked
        if (t.breakExtensions >= Rewards.MAX_BREAK_EXTENSIONS) return@mutate S.engine.extensionLimit
        val price = Rewards.breakExtensionPrice(t.breakExtensions)
        val have = data.visibleCoins()
        if (have < price) return@mutate S.common.notEnoughCoins(price - have)
        // Ücret oturum dışından ödenir (bekleyen oturum kazancından değil).
        val plus = Rewards.BREAK_EXTENSION_MIN * MINUTE_MS
        val nt = when {
            t.waiting -> t.copy(waiting = false, autoAt = null, phaseStartedAt = now, segStart = now, phaseEndAt = now + plus)
            t.isPaused -> t.copy(pausedRemainingMs = (t.pausedRemainingMs ?: 0) + plus)
            else -> t.copy(phaseEndAt = t.phaseEndAt + plus)
        }
        data = data.copy(timer = nt.copy(breakExtensions = t.breakExtensions + 1, phaseExtraMs = t.phaseExtraMs + plus))
        spendOutside(EarnKind.BREAK_EXTENSION, price)
        null
    }

    /** Oturumdan bağımsız harcama (bekleyen oturum kazancına karışmasın diye oturum kimliği 0). */
    private fun Tx.spendOutside(kind: EarnKind, amount: Int) {
        data = data.copy(
            coins = (data.coins - amount).coerceAtLeast(0),
            ledger = (listOf(Earning(now, -amount, kind)) + data.ledger).take(LEDGER_MAX),
        )
    }

    // ---------------------------------------------------------------- timer: çekirdek

    private fun Tx.catchUp() {
        var guard = 0
        while (guard++ < 200) {
            processFocusTimeouts()
            var t = data.timer ?: return
            if (t.isPaused) return
            if (t.waiting) {
                val at = t.autoAt
                if (at != null && now >= at) {
                    beginNext(t, at)
                    continue
                }
                return
            }
            if (t.phase == Phase.WORK) {
                t = processFocus(t, minOf(now, t.phaseEndAt - 1))
                data = data.copy(timer = t)
            }
            if (now < t.phaseEndAt) return
            // Cevap bekleyen dikkat sorusu varken çalışma turu bitmez: zincir çözülünce devam eder.
            val ask = data.focusAsk
            if (t.phase == Phase.WORK && ask != null && !ask.endOfRound) {
                val next = if (ask.visible) ask.expiresAt else ask.askedAt
                if (next in 1..now) continue
                return
            }
            endPhase(t, t.phaseEndAt)
        }
    }

    /** Faz [at] anında doğal olarak bitti. */
    private fun Tx.endPhase(t0: TimerState, at: Long) {
        var t = t0
        val s = data.settings
        if (t.phase == Phase.WORK) {
            creditWork(t.phaseStartedAt, at, t)
            t = t.copy(
                sessionWorkMs = t.sessionWorkMs + (at - t.phaseStartedAt),
                overtimeMs = t.overtimeMs + if (t.overtime) at - t.phaseStartedAt else 0,
            )
            t = closeSeg(t, SegKind.WORK, at)
            if (s.focusCheckDivisions == 1 && !t.overtime) requestFocusAsk(endOfRound = true)
            if (t.stopwatch) {
                beginNext(t.copy(stopwatch = false), at)
                return
            }
        } else {
            t = closeSeg(t, SegKind.BREAK, at)
        }
        val auto = if (t.phase == Phase.WORK) s.autoBreak else s.autoWork
        val delay = (if (t.phase == Phase.WORK) s.autoBreakDelaySec else s.autoWorkDelaySec).coerceAtLeast(0)
        if (auto && delay == 0) {
            beginNext(t, at)
            return
        }
        data = data.copy(timer = t.copy(waiting = true, autoAt = if (auto) at + delay * 1000L else null, phaseEndAt = at, pausedRemainingMs = null, segStart = at))
        phaseEvents += PhaseEvent(if (t.phase == Phase.WORK) PhaseEventKind.ROUND_END else PhaseEventKind.BREAK_END, t.round, t.rounds, 0)
    }

    /** Biten fazdan sonrakine [at] anında geç; son turdan sonra oturumu bitirir. */
    private fun Tx.beginNext(t: TimerState, at: Long) {
        val s = data.settings
        val base = t.copy(
            waiting = false, autoAt = null, pausedRemainingMs = null, stopwatch = false, overtime = false,
            phaseStartedAt = at, segStart = at, checksDone = 0, phaseExtraMs = 0, pauseReason = null,
        )
        if (t.phase == Phase.WORK) {
            if (t.round >= t.rounds) {
                finishSession(t, completed = true)
                return
            }
            val next = if (t.breakMinutes <= 0) base.copy(
                round = t.round + 1, phase = Phase.WORK, phaseEndAt = at + t.workMinutes * MINUTE_MS,
                nextBeepAt = Focus.nextBeepAt(s, at), fatigue = false,
            ) else base.copy(phase = Phase.BREAK, phaseEndAt = at + t.breakMinutes * MINUTE_MS, nextBeepAt = null, fatigue = false)
            data = data.copy(timer = next)
            phaseEvents += if (next.phase == Phase.BREAK) PhaseEvent(PhaseEventKind.BREAK_START, t.round, t.rounds, t.breakMinutes)
            else PhaseEvent(PhaseEventKind.WORK_START, next.round, t.rounds, t.workMinutes)
        } else {
            if (t.round >= t.rounds) {
                finishSession(t, completed = true)
                return
            }
            val next = base.copy(
                round = t.round + 1, phase = Phase.WORK, phaseEndAt = at + t.workMinutes * MINUTE_MS,
                nextBeepAt = Focus.nextBeepAt(s, at), extensionBlocked = false,
            )
            data = data.copy(timer = next)
            phaseEvents += PhaseEvent(PhaseEventKind.WORK_START, next.round, t.rounds, t.workMinutes)
        }
    }

    private fun closeSeg(t: TimerState, kind: SegKind, end: Long, focus: Int = -1, reason: PauseReason? = null): TimerState {
        val start = if (t.segStart > 0) t.segStart else t.phaseStartedAt
        if (end - start < 1000) return t.copy(segStart = end)
        val seg = Segment(start, end, kind, focus, overtime = kind == SegKind.WORK && t.overtime, reason = reason)
        return t.copy(segments = t.segments + seg, segStart = end)
    }

    // ---------------------------------------------------------------- dikkat sorusu

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

    private fun Tx.requestFocusAsk(endOfRound: Boolean = false) {
        if (data.focusAsk != null) return
        val sec = data.settings.focusAnswerSec.coerceIn(Focus.MIN_ANSWER_SEC, Focus.MAX_ANSWER_SEC)
        data = data.copy(focusAsk = FocusAsk(askedAt = now, expiresAt = now + sec * 1000L, attempt = 1, firstAt = now, endOfRound = endOfRound))
        askFocus = true
    }

    /** Süresi dolan soruları sırayla işler (uygulama kapalıyken birkaç adım birden geçmiş olabilir). */
    private fun Tx.processFocusTimeouts() {
        val sec = data.settings.focusAnswerSec.coerceIn(Focus.MIN_ANSWER_SEC, Focus.MAX_ANSWER_SEC) * 1000L
        var guard = 0
        while (guard++ < 10) {
            val a = data.focusAsk ?: return
            if (!a.visible) {
                if (now < a.askedAt) return
                data = data.copy(focusAsk = a.copy(visible = true, expiresAt = a.askedAt + sec))
                if (a.attempt >= 2) focusWarn = true else askFocus = true
                continue
            }
            if (a.expiresAt <= 0 || now < a.expiresAt) return
            val t = data.timer
            val inWork = t != null && t.phase == Phase.WORK && t.isRunning
            when {
                a.endOfRound || !inWork -> {
                    recordFocus(true, a.expiresAt, a.endOfRound)
                    data = data.copy(focusAsk = null)
                }
                a.attempt == 1 -> {
                    // Cevap yok: odaklı say, az sonra tekrar sor.
                    recordFocus(true, a.expiresAt, false)
                    data = data.copy(focusAsk = a.copy(attempt = 2, visible = false, askedAt = a.expiresAt + Focus.REASK_MS, expiresAt = 0))
                }
                a.attempt < Focus.MAX_ATTEMPTS -> {
                    if (a.attempt == 2) recordFocus(false, a.expiresAt, false)
                    data = data.copy(focusAsk = a.copy(attempt = a.attempt + 1, visible = false, askedAt = a.expiresAt + Focus.REASK_MS, expiresAt = 0))
                }
                else -> goAway(a)
            }
        }
    }

    /** Zincir cevapsız bitti: çalışma süresini ilk sorunun anına geri al ve duraklat. */
    private fun Tx.goAway(a: FocusAsk) {
        var t = data.timer ?: return
        val since = a.firstAt.coerceIn(t.phaseStartedAt, maxOf(t.phaseStartedAt, now))
        if (since > t.phaseStartedAt) {
            creditWork(t.phaseStartedAt, since, t)
            t = t.copy(sessionWorkMs = t.sessionWorkMs + (since - t.phaseStartedAt), overtimeMs = t.overtimeMs + if (t.overtime) since - t.phaseStartedAt else 0)
        }
        t = closeSeg(t, SegKind.WORK, since)
        t = closeSeg(t.copy(segStart = since), SegKind.AWAY, now)
        val remaining = (t.phaseEndAt - since).coerceAtLeast(MINUTE_MS)
        data = data.copy(
            focusAsk = null,
            away = AwayState(since = since, detectedAt = now, lostMs = now - since),
            timer = t.copy(pausedRemainingMs = remaining, phaseStartedAt = now, segStart = now, pauseReason = null),
        )
        alerts += S.notify.awayTitle to S.notify.awayText(Fmt.time(since))
        focusWarn = true
    }

    private fun Tx.recordFocus(focused: Boolean, at: Long, endOfRound: Boolean) {
        updateDay(Time.dayKey(at)) { if (focused) it.copy(focusYes = it.focusYes + 1) else it.copy(focusNo = it.focusNo + 1) }
        val v = if (focused) 1 else 0
        data.timer?.let { t0 ->
            var t = if (focused) t0.copy(sessionFocusYes = t0.sessionFocusYes + 1) else t0.copy(sessionFocusNo = t0.sessionFocusNo + 1)
            t = if (!endOfRound && t.phase == Phase.WORK && t.isRunning && at > (if (t.segStart > 0) t.segStart else t.phaseStartedAt)) {
                closeSeg(t, SegKind.WORK, at, focus = v)
            } else {
                // Tur sonu sorusu: son çalışma bölümünü işaretle.
                val i = t.segments.indexOfLast { it.kind == SegKind.WORK }
                if (i >= 0 && t.segments[i].focus == -1) t.copy(segments = t.segments.toMutableList().also { it[i] = it[i].copy(focus = v) }) else t
            }
            data = data.copy(timer = t)
        }
        if (data.timer == null) {
            data.lastSession?.let { ls ->
                data = data.copy(lastSession = if (focused) ls.copy(focusYes = ls.focusYes + 1) else ls.copy(focusNo = ls.focusNo + 1))
                val i = data.sessionLogs.indexOfFirst { it.id == ls.id }
                if (i >= 0) {
                    val log = data.sessionLogs[i]
                    val si = log.segments.indexOfLast { it.kind == SegKind.WORK }
                    val segs = if (si >= 0 && log.segments[si].focus == -1) log.segments.toMutableList().also { it[si] = it[si].copy(focus = v) } else log.segments
                    val nl = log.copy(segments = segs, focusYes = log.focusYes + if (focused) 1 else 0, focusNo = log.focusNo + if (focused) 0 else 1)
                    data = data.copy(sessionLogs = data.sessionLogs.toMutableList().also { it[i] = nl })
                }
            }
        }
    }

    /** "Odaklı mısın?" cevabı. */
    fun answerFocus(focused: Boolean) = mutate {
        val a = data.focusAsk ?: return@mutate
        if (!a.visible) return@mutate
        recordFocus(focused, now, a.endOfRound)
        earn(EarnKind.FOCUS_ANSWER, 1, 1) // dürüst cevap için küçük teşekkür, cevaba göre değişmez
        data = data.copy(focusAsk = null)
        catchUp()
    }

    /**
     * Yanıtsız kalınca sorulan "neredeydin?" sorusu. [reason] acil durumsa ceza yok, sebep kaydedilir.
     * null ise: mola ücreti + ceza alınır, bir mola uzatma hakkı gider ve sıradaki mola uzatılamaz.
     */
    fun resolveAway(reason: PauseReason?) = mutate {
        val away = data.away ?: return@mutate
        var t = data.timer
        if (t != null) {
            val segs = t.segments.toMutableList()
            val i = segs.indexOfLast { it.kind == SegKind.AWAY && it.start == away.since }
            if (i >= 0) segs[i] = segs[i].copy(reason = reason)
            t = t.copy(segments = segs)
            if (reason == null) {
                val fee = Rewards.breakExtensionPrice(t.breakExtensions) + Rewards.AWAY_FINE
                spendOutside(EarnKind.AWAY_PENALTY, fee)
                notices += S.engine.awayPenalty(fee)
                t = t.copy(breakExtensions = t.breakExtensions + 1, extensionBlocked = true)
            } else {
                t = t.copy(pauseReason = reason)
            }
        }
        data = data.copy(away = null, timer = t)
    }

    // ---------------------------------------------------------------- oturum sonu

    private fun Tx.finishSession(t: TimerState, completed: Boolean) {
        data = data.copy(timer = null, focusAsk = data.focusAsk?.takeIf { it.endOfRound }, away = null)
        val worked = t.sessionWorkMs
        updateDay(Time.dayKey(now)) { it.copy(sessions = it.sessions + 1) }
        if (worked >= MINUTE_MS) {
            // Özet için oturum kimliği önce yazılır ki bundan sonraki kazançlar da bu oturuma eklensin.
            data = data.copy(lastSession = SessionSummary(
                workMs = worked, endedAt = now, focusYes = t.sessionFocusYes, focusNo = t.sessionFocusNo,
                id = t.id, startedAt = t.startedAt, completed = completed, rounds = t.round, overtimeMs = t.overtimeMs,
                logged = data.goals.isEmpty() && data.deadlines.none { it.completedAt == null },
            ))
        }
        if (completed) {
            phaseEvents += PhaseEvent(PhaseEventKind.FINISHED, t.round, t.rounds, (worked / MINUTE_MS).toInt())
            if (worked >= 10 * MINUTE_MS) earn(EarnKind.SESSION_COMPLETE, Rewards.sessionComplete(t.rounds), t.rounds)
            if (worked >= 20 * MINUTE_MS) giveChest(com.derscalisma.app.data.game.ChestKind.WOOD, "Oturum tamamlandı")
        }
        if (worked >= MINUTE_MS) {
            val coins = data.ledger.filter { it.session == t.id }.sumOf { it.amount }
            val log = SessionLog(
                id = t.id, start = t.startedAt, end = now, workMs = worked, segments = t.segments,
                focusYes = t.sessionFocusYes, focusNo = t.sessionFocusNo, completed = completed,
                overtimeMs = t.overtimeMs, coins = coins,
            )
            data = data.copy(sessionLogs = (listOf(log) + data.sessionLogs).take(LOGS_MAX))
        }
    }

    /** Özet ekranında "Tamam": oturum kazançları bakiyeye geçer. */
    fun closeSummary() = mutate {
        val ls = data.lastSession ?: return@mutate
        val i = data.sessionLogs.indexOfFirst { it.id == ls.id }
        if (i >= 0) {
            val coins = data.ledger.filter { it.session == ls.id }.sumOf { it.amount }
            data = data.copy(sessionLogs = data.sessionLogs.toMutableList().also { it[i] = it[i].copy(coins = coins) })
        }
        data = data.copy(lastSession = null)
    }

    fun dismissCelebration() = mutate { data = data.copy(celebration = null) }

    // ---------------------------------------------------------------- elle süre ekleme

    /** Zamanlayıcıyı açmayı unuttuysan bugüne çalışma ekler. Hata varsa mesaj döner. */
    fun addManualStudy(start: Long, end: Long): String? = mutate {
        if (end > now + 60_000) return@mutate S.engine.manualFuture
        if (end - start < MINUTE_MS || end - start > 12 * 60 * MINUTE_MS) return@mutate S.engine.manualInvalid
        if (Time.dayKey(start) != Time.dayKey(now) || Time.dayKey(end - 1) != Time.dayKey(now)) return@mutate S.engine.manualOtherDay
        val busy = data.sessionLogs.filter { Time.dayKey(it.end) == Time.dayKey(now) || Time.dayKey(it.start) == Time.dayKey(now) }
            .map { it.start to it.end } + listOfNotNull(data.timer?.let { it.startedAt to now })
        if (busy.any { (a, b) -> start < b && end > a }) return@mutate S.engine.manualOverlap
        val before = data.coins
        val oldLast = data.lastSession
        data = data.copy(lastSession = null)
        creditWork(start, end, null, manual = true)
        data = data.copy(lastSession = oldLast)
        val log = SessionLog(
            id = start, start = start, end = end, workMs = end - start,
            segments = listOf(Segment(start, end, SegKind.WORK)), manual = true, completed = true, coins = data.coins - before,
        )
        data = data.copy(sessionLogs = (listOf(log) + data.sessionLogs).sortedByDescending { it.start }.take(LOGS_MAX))
        notices += S.engine.manualAdded(((end - start) / MINUTE_MS).toInt())
        null
    }

    // ---------------------------------------------------------------- kaydetme ve ödüller

    /** [t]: çalışmanın ait olduğu oturum (ek süre / yorgunluk için); elle eklenen çalışmada null. */
    private fun Tx.creditWork(start: Long, end: Long, t: TimerState?, manual: Boolean = false) {
        if (end <= start) return
        var s = start
        while (s < end) {
            val date = Time.date(s)
            val e = minOf(end, Time.startOfDay(date.plusDays(1)))
            creditPiece(date, s, e, t, manual)
            s = e
        }
    }

    private fun Tx.windowsOn(date: LocalDate) = data.windows.filter { date.dayOfWeek.value in it.days }

    private fun Tx.creditPiece(date: LocalDate, s: Long, e: Long, t: TimerState?, manual: Boolean) {
        val key = date.toString()
        val windows = windowsOn(date)
        val old = day(key).studyMs
        updateDay(key) { rec ->
            val wm = rec.windowMs.toMutableMap()
            for (w in windows) {
                val ov = minOf(e, Time.at(date, w.endMin)) - maxOf(s, Time.at(date, w.startMin))
                if (ov > 0) wm[w.id] = (wm[w.id] ?: 0L) + ov
            }
            rec.copy(studyMs = rec.studyMs + (e - s), windowMs = wm)
        }
        val minutes = ((old + (e - s)) / MINUTE_MS - old / MINUTE_MS).toInt()
        if (minutes > 0) {
            when {
                manual -> earn(EarnKind.MANUAL, minutes * Rewards.COINS_PER_MINUTE, minutes)
                t?.overtime == true -> {
                    earn(EarnKind.STUDY, minutes * Rewards.COINS_PER_MINUTE, minutes)
                    earn(EarnKind.OVERTIME, minutes * (Rewards.OVERTIME_COINS_PER_MINUTE - Rewards.COINS_PER_MINUTE), minutes)
                }
                t?.fatigue == true -> earn(EarnKind.STUDY, (minutes * Rewards.COINS_PER_MINUTE * Rewards.FATIGUE_PERCENT / 100).coerceAtLeast(0), minutes)
                else -> earn(EarnKind.STUDY, minutes * Rewards.COINS_PER_MINUTE, minutes)
            }
            val pct = Game.libraryBonus(data.game)
            if (pct > 0) {
                // Yuvarlama kaybı olmasın: günün toplam dakikasına göre hesapla.
                val newMin = ((old + (e - s)) / MINUTE_MS).toInt()
                val oldMin = (old / MINUTE_MS).toInt()
                val bonus = newMin * pct / 100 - oldMin * pct / 100
                if (bonus > 0) earn(EarnKind.LIBRARY, bonus, pct)
            }
        }
        advanceGame(e - s)
        hitBoss(e - s)
        checkAchievements(date, e)
    }

    private fun Tx.checkAchievements(date: LocalDate, at: Long) {
        val key = date.toString()
        for (w in windowsOn(date)) {
            if (w.createdDay.isNotEmpty() && key < w.createdDay) continue
            val ms = day(key).windowMs[w.id] ?: 0L
            if (ms >= Rewards.ATTEND_MS && w.id !in day(key).windowsAttended) {
                updateDay(key) { it.copy(windowsAttended = it.windowsAttended + w.id) }
                val st = Streaks.windowAttend(data, at)
                reward(EarnKind.WINDOW_ATTEND, Rewards.windowAttend(st), st, Fmt.windowName(w))
            }
            if (ms >= w.targetMinutes * MINUTE_MS && w.id !in day(key).windowsMet) {
                updateDay(key) { it.copy(windowsMet = it.windowsMet + w.id) }
                val st = Streaks.windowGoal(data, at)
                reward(EarnKind.WINDOW_GOAL, Rewards.windowGoal(st), st, Fmt.windowName(w))
            }
        }

        val goalMs = data.settings.dailyGoalMinutes * MINUTE_MS
        if (!day(key).dailyMet && day(key).studyMs >= goalMs) {
            updateDay(key) { it.copy(dailyMet = true) }
            val st = Streaks.daily(data, date)
            reward(EarnKind.DAILY_GOAL, Rewards.daily(st), st)
            giveChest(com.derscalisma.app.data.game.ChestKind.SILVER, "Günlük hedef")
            Rewards.milestones[st]?.let { reward(EarnKind.MILESTONE, it, st) }
            val oldBest = data.bestDailyStreak
            if (key == Time.dayKey(now)) data = data.copy(celebration = Celebration(st, key, newBest = st > oldBest))
            if (!foreground) alerts += S.notify.dailyGoalTitle(st) to S.notify.dailyGoalText(st)
            sounds += Cue.STREAK
            if (st > oldBest) {
                data = data.copy(bestDailyStreak = st)
                GameCatalog.DECOR_UNLOCK
                    .filter { (_, need) -> need in (oldBest + 1)..st }
                    .forEach { (t, _) -> log("🏡 Köyün için yeni süs açıldı: ${t.title}") }
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
            reward(EarnKind.GOAL, Rewards.goal(st), st, Fmt.goalName(g))
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
        checkDeadlines()
    }

    /** Süreli hedeflerden tamamlananları ödüllendirir (bir kez). */
    private fun Tx.checkDeadlines() {
        val today = Time.date(now)
        val list = data.deadlines.map { g ->
            if (g.completedAt != null) return@map g
            val st = Deadlines.status(data, g, today)
            if (st.remaining > 0) return@map g
            // Aynı gün açılıp bitirilen ya da çok küçük hedefler küçük ödül alır (sınırsız para kazanılmasın).
            val reward = if (g.startDate >= today.toString() || g.total < 10) 10 else Deadlines.reward(st)
            reward(EarnKind.DEADLINE, reward, (st.daysLeft - 1).coerceAtLeast(0), Fmt.deadlineName(g))
            g.copy(completedAt = now)
        }
        if (list != data.deadlines) data = data.copy(deadlines = list)
    }

    // ---------------------------------------------------------------- süreli hedefler

    fun addDeadline(title: String, unit: String, total: Int, dueDate: String, linkedGoalId: String?) = mutate {
        val g = DeadlineGoal(
            id = Time.newId(),
            title = title.ifBlank { "Süreli hedef" },
            unit = unit.ifBlank { GoalType.CUSTOM.defaultUnit },
            total = total.coerceAtLeast(1),
            startDate = Time.dayKey(now),
            dueDate = dueDate,
            linkedGoalId = linkedGoalId,
        )
        data = data.copy(deadlines = data.deadlines + g)
    }

    fun deleteDeadline(id: String) = mutate {
        data = data.copy(deadlines = data.deadlines.filterNot { it.id == id })
    }

    fun addDeadlineProgress(id: String, delta: Int) = mutate {
        data = data.copy(deadlines = data.deadlines.map { if (it.id == id) it.copy(manual = (it.manual + delta).coerceAtLeast(0)) else it })
        checkDeadlines()
    }

    /** Bağlı günlük hedefi, süreli hedefi zamanında bitirecek günlük miktara ayarlar. */
    fun matchDailyToDeadline(id: String) = mutate {
        val g = data.deadlines.find { it.id == id } ?: return@mutate
        val goalId = g.linkedGoalId ?: return@mutate
        val st = Deadlines.status(data, g, Time.date(now))
        val target = st.perDay.coerceAtLeast(1)
        data = data.copy(goals = data.goals.map { if (it.id == goalId) it.copy(dailyTarget = target) else it })
        data.goals.find { it.id == goalId }?.let { checkGoal(it, Time.date(now)) }
    }

    // ---------------------------------------------------------------- goals & settings

    fun updateSettings(f: (Settings) -> Settings) = mutate {
        val old = data.settings
        val s = f(old)
        if (s == old) return@mutate
        data = data.copy(settings = s)
        // Bip ayarı değiştiyse çalışan turdaki sıradaki bipi yeniden planla.
        val t = data.timer
        val beepChanged = old.focusBeepEnabled != s.focusBeepEnabled || old.focusBeepRandom != s.focusBeepRandom ||
            old.focusBeepEvery != s.focusBeepEvery || old.focusBeepMin != s.focusBeepMin || old.focusBeepMax != s.focusBeepMax
        if (t != null && beepChanged && t.phase == Phase.WORK) {
            val base = if (t.isPaused) t.phaseStartedAt else now
            data = data.copy(timer = t.copy(nextBeepAt = Focus.nextBeepAt(s, base)))
        }
        if (old.dailyGoalMinutes != s.dailyGoalMinutes) checkAchievements(Time.date(now), now)
    }

    fun setLanguage(code: String) = updateSettings { it.copy(language = code) }

    fun completeOnboarding() = mutate { data = data.copy(onboarded = true) }

    fun addProgress(goalId: String, delta: Int) = mutate { addProgressInternal(goalId, delta) }

    fun logSession(values: Map<String, Int>) = mutate {
        values.forEach { (id, v) ->
            if (v <= 0) return@forEach
            if (id.startsWith("dl:")) {
                val dl = id.removePrefix("dl:")
                data = data.copy(deadlines = data.deadlines.map { if (it.id == dl) it.copy(manual = it.manual + v) else it })
            } else addProgressInternal(id, v)
        }
        checkDeadlines()
        data.lastSession?.let { data = data.copy(lastSession = it.copy(logged = true)) }
    }

    fun skipSessionLog() = mutate { data.lastSession?.let { data = data.copy(lastSession = it.copy(logged = true)) } }

    fun addGoal(type: GoalType, name: String, unit: String, target: Int) = mutate {
        val g = Goal(Time.newId(), type, name.ifBlank { type.defaultName }, target.coerceAtLeast(1), unit.ifBlank { type.defaultUnit })
        data = data.copy(goals = data.goals + g)
    }

    fun setGoalTarget(goalId: String, target: Int) = mutate {
        data = data.copy(goals = data.goals.map { if (it.id == goalId) it.copy(dailyTarget = target.coerceAtLeast(1)) else it })
        data.goals.find { it.id == goalId }?.let { checkGoal(it, Time.date(now)) }
    }

    fun deleteGoal(goalId: String) = mutate {
        // Bu hedefe bağlı süreli hedefler bağımsız olur; sayılmış ilerleme korunur.
        val dls = data.deadlines.map { dl ->
            if (dl.linkedGoalId != goalId) dl else dl.copy(manual = Deadlines.progress(data, dl), linkedGoalId = null)
        }
        data = data.copy(goals = data.goals.filterNot { it.id == goalId }, deadlines = dls)
    }

    fun saveWindow(w: StudyWindow) = mutate {
        val exists = data.windows.find { it.id == w.id }
        val nw = if (exists == null) w.copy(createdDay = Time.dayKey(now)) else w.copy(createdDay = exists.createdDay)
        data = data.copy(
            windows = if (exists != null) data.windows.map { if (it.id == w.id) nw else it } else data.windows + nw
        )
        checkAchievements(Time.date(now), now)
    }

    fun deleteWindow(id: String) = mutate {
        data = data.copy(windows = data.windows.filterNot { it.id == id })
    }

    // ---------------------------------------------------------------- market

    fun buy(itemId: String): String? = mutate {
        val item = Catalog.item(itemId) ?: return@mutate null
        val have = data.visibleCoins()
        if (item.id == FREEZE_ID) {
            if (data.streakFreezes >= MAX_FREEZES) return@mutate S.engine.maxFreezes(MAX_FREEZES)
            if (have < item.price) return@mutate S.common.notEnoughCoins(item.price - have)
            data = data.copy(streakFreezes = data.streakFreezes + 1)
            spendOutside(EarnKind.PURCHASE, item.price)
            return@mutate S.engine.freezeBought
        }
        if (item.id in data.owned || item.price == 0) {
            equipInternal(item)
            return@mutate null
        }
        if (have < item.price) return@mutate S.common.notEnoughCoins(item.price - have)
        data = data.copy(owned = data.owned + item.id)
        spendOutside(EarnKind.PURCHASE, item.price)
        equipInternal(item)
        S.engine.bought
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
        data = AppData(
            lastProcessedDay = Time.date(now).minusDays(1).toString(), schema = SCHEMA,
            settings = Settings(language = data.settings.language),
        )
    }
}

/** Zamanlayıcının bir sonraki işlenmesi gereken anı (alarm ve ekran yenilemesi için). */
fun nextEventAt(d: AppData): Long? {
    var at: Long? = null
    fun min(x: Long?) {
        if (x != null && x > 0 && (at == null || x < at!!)) at = x
    }
    d.focusAsk?.let { min(if (it.visible) it.expiresAt else it.askedAt) }
    val t = d.timer
    if (t != null && !t.isPaused) {
        if (t.waiting) min(t.autoAt) else min(Focus.nextEventAt(t, d.settings))
    }
    return at
}
