package com.derscalisma.app.data

import com.derscalisma.app.data.game.GameState
import kotlinx.serialization.Serializable

const val MINUTE_MS = 60_000L

@Serializable
data class Settings(
    /** Ana hedef: günlük çalışma süresi (dakika). En çok ödül veren seri buna bağlı. */
    val dailyGoalMinutes: Int = 120,
    val workMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val rounds: Int = 4,
    /** Günlük hedef tamamlanmadıysa seri uyarısının saati. */
    val warnHour: Int = 20,
    val warnMinute: Int = 0,
    val remindersEnabled: Boolean = true,
    /** Aralık başladıktan kaç dk sonra hâlâ başlanmadıysa hatırlatılsın (0 = kapalı). */
    val windowNudgeMinutes: Int = 10,
    val keepScreenOn: Boolean = true,

    // ---- Dikkat toplama sistemi
    /** Çalışırken aralıklarla bip sesi çal. */
    val focusBeepEnabled: Boolean = false,
    /** true: [focusBeepMin]–[focusBeepMax] arası rastgele; false: her [focusBeepEvery] dakikada. */
    val focusBeepRandom: Boolean = true,
    val focusBeepEvery: Int = 10,
    val focusBeepMin: Int = 5,
    val focusBeepMax: Int = 15,
    /** Her bipten sonra "dikkatin toplu mu?" diye sor. */
    val focusAskAfterBeep: Boolean = true,
    /** Çalışma turu içinde ek soru: 0 kapalı, 1 tur sonunda, 2 yarısında, 3 her 1/3'te, 4 her 1/4'te. */
    val focusCheckDivisions: Int = 2,
    val focusVibrate: Boolean = true,
)

/** Cevap bekleyen "dikkatin toplu mu?" sorusu. */
@Serializable
data class FocusAsk(val askedAt: Long)

@Serializable
enum class GoalType(val emoji: String, val defaultName: String, val defaultUnit: String, val defaultTarget: Int, val step: Int) {
    PAGES("📖", "Sayfa", "sayfa", 30, 5),
    QUESTIONS("📝", "Soru / Test", "soru", 60, 10),
    TOPICS("🧠", "Konu", "konu", 2, 1),
    CUSTOM("⭐", "Özel Hedef", "adet", 1, 1),
}

@Serializable
data class Goal(
    val id: String,
    val type: GoalType,
    val name: String,
    val dailyTarget: Int,
    val unit: String,
)

/** Haftalık tekrar eden ders çalışma aralığı. days: 1 = Pazartesi ... 7 = Pazar */
@Serializable
data class StudyWindow(
    val id: String,
    val label: String,
    val startMin: Int,
    val endMin: Int,
    val days: Set<Int> = (1..7).toSet(),
    val targetMinutes: Int = 60,
)

@Serializable
data class DayRecord(
    val studyMs: Long = 0,
    val windowMs: Map<String, Long> = emptyMap(),
    val progress: Map<String, Int> = emptyMap(),
    val dailyMet: Boolean = false,
    val frozen: Boolean = false,
    val goalsMet: Set<String> = emptySet(),
    val windowsMet: Set<String> = emptySet(),
    val windowsAttended: Set<String> = emptySet(),
    val coinsEarned: Int = 0,
    val sessions: Int = 0,
    val focusYes: Int = 0,
    val focusNo: Int = 0,
)

@Serializable
enum class Phase { WORK, BREAK }

@Serializable
data class TimerState(
    val workMinutes: Int,
    val breakMinutes: Int,
    val rounds: Int,
    val round: Int = 1,
    val phase: Phase = Phase.WORK,
    /** Çalışırken: mevcut kesintisiz bölümün başlangıcı. */
    val phaseStartedAt: Long,
    /** Çalışırken: mevcut fazın bitiş anı. */
    val phaseEndAt: Long,
    /** null değilse zamanlayıcı duraklatılmış demektir. */
    val pausedRemainingMs: Long? = null,
    val sessionWorkMs: Long = 0,
    val startedAt: Long,
    /** Sıradaki dikkat bip sesinin zamanı (sadece çalışma fazında). */
    val nextBeepAt: Long? = null,
    /** Bu çalışma turunda sorulmuş ara soru sayısı. */
    val checksDone: Int = 0,
    val sessionFocusYes: Int = 0,
    val sessionFocusNo: Int = 0,
    /** Bu faza elle eklenen / çıkarılan süre (+5 dk gibi). Faz değişince sıfırlanır. */
    val phaseExtraMs: Long = 0,
) {
    val isPaused: Boolean get() = pausedRemainingMs != null
    val phaseTotalMs: Long get() = ((if (phase == Phase.WORK) workMinutes else breakMinutes) * MINUTE_MS + phaseExtraMs).coerceAtLeast(MINUTE_MS / 6)
}

@Serializable
data class PlacedBuilding(val x: Int, val y: Int, val buildingId: String)

@Serializable
data class LogEvent(val at: Long, val text: String, val coins: Int = 0)

@Serializable
data class SessionSummary(val workMs: Long, val endedAt: Long, val focusYes: Int = 0, val focusNo: Int = 0)

@Serializable
data class AppData(
    val settings: Settings = Settings(),
    val goals: List<Goal> = defaultGoals(),
    val windows: List<StudyWindow> = emptyList(),
    val days: Map<String, DayRecord> = emptyMap(),
    val coins: Int = 100,
    val totalEarned: Int = 0,
    val timer: TimerState? = null,
    /** Bitmiş ve ilerlemesi (sayfa/soru) henüz girilmemiş oturum. */
    val lastSession: SessionSummary? = null,
    /** v1.0 emoji şehri; yalnızca eski kayıtları okuyup iade etmek için duruyor. */
    val city: List<PlacedBuilding> = emptyList(),
    val game: GameState = GameState(),
    val owned: Set<String> = setOf(DEFAULT_THEME, DEFAULT_GROUND, DEFAULT_TIMER_STYLE, DEFAULT_BG),
    val theme: String = DEFAULT_THEME,
    val background: String = DEFAULT_BG,
    val focusAsk: FocusAsk? = null,
    val ground: String = DEFAULT_GROUND,
    val timerStyle: String = DEFAULT_TIMER_STYLE,
    val streakFreezes: Int = 0,
    val bestDailyStreak: Int = 0,
    val lastProcessedDay: String? = null,
    val firedReminders: Set<String> = emptySet(),
    val log: List<LogEvent> = emptyList(),
)

fun defaultGoals() = listOf(
    Goal("g_pages", GoalType.PAGES, "Sayfa", 30, "sayfa"),
    Goal("g_questions", GoalType.QUESTIONS, "Soru / Test", 60, "soru"),
)
