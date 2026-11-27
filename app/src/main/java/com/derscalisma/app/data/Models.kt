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

    // ---- Ses ve görünüm
    val musicEnabled: Boolean = true,
    val sfxEnabled: Boolean = true,
    val musicVolume: Int = 50,
    val sfxVolume: Int = 80,
    /** Köy ve savaş 3D mi çizilsin (kapalıysa 2D). */
    val view3d: Boolean = true,

    // ---- v1.5: dil
    /** Arayüz dili (tr, en, de, fr, ru, ja, zh). Boşsa telefonun dili kullanılır. */
    val language: String = "",

    // ---- v1.5: otomatik geçişler
    /** Çalışma turu bitince molaya kendiliğinden geç. */
    val autoBreak: Boolean = true,
    /** Molaya geçmeden önce beklenecek süre (sn). Bu arada "ek süre" seçilebilir. 0 = hemen. */
    val autoBreakDelaySec: Int = 10,
    /** Mola bitince çalışmaya kendiliğinden geç. */
    val autoWork: Boolean = true,
    /** Çalışmaya geçmeden önce beklenecek süre (sn). 0 = hemen. */
    val autoWorkDelaySec: Int = 10,

    // ---- v1.5: dikkat sorusu
    /** "Odaklı mısın?" sorusunun cevap süresi (sn). Süre dolunca odaklı sayılır ve az sonra tekrar sorulur. */
    val focusAnswerSec: Int = 10,

    // ---- v1.5: ses
    /** Çalışma müziği (StudyTrack.id). "off" = kapalı. */
    val studyMusic: String = "off",
    /** Çalışma müziği molada da çalsın. */
    val studyMusicInBreak: Boolean = false,
    /** Uygulama açıkken faz değişiminde ses ve titreşim. */
    val phaseSounds: Boolean = true,
)

/**
 * Cevap bekleyen "odaklı mısın?" sorusu ve cevapsız kalırsa tekrar sorma zinciri.
 *
 * Zincir: 1. soru cevapsız kalırsa odaklı sayılır; [Focus.REASK_MS] sonra 2. soru gelir. 2. soru da
 * cevapsız kalırsa dağınık sayılır ve uyarı verilir; birkaç kez daha sorulur. Son soru da cevapsız
 * kalırsa çalışma süresi [firstAt] anına geri alınır ve zamanlayıcı duraklatılır ([AwayState]).
 */
@Serializable
data class FocusAsk(
    val askedAt: Long,
    /** Cevap süresinin dolduğu an. 0 = süresiz (eski kayıtlar). */
    val expiresAt: Long = 0,
    /** 1 = ilk soru; 2 ve sonrası "orada mısın?" tekrarları. */
    val attempt: Int = 1,
    /** Zincirin ilk sorusunun anı: hiç cevap gelmezse çalışma süresi buraya geri alınır. */
    val firstAt: Long = askedAt,
    /** false: tekrar sorusu bekleniyor, [askedAt] anında gösterilecek. */
    val visible: Boolean = true,
    /** Tur sonunda sorulan soru: cevapsız kalırsa tekrar sorulmaz. */
    val endOfRound: Boolean = false,
)

/** Dikkat sorularına üst üste cevap gelmedi: süre ilk sorunun anına geri alındı, zamanlayıcı duraklatıldı. */
@Serializable
data class AwayState(
    /** İlk cevapsız sorunun anı (çalışma süresi buraya geri alındı). */
    val since: Long,
    /** Durumun fark edildiği an. */
    val detectedAt: Long,
    /** Sayılmayan (geri alınan) çalışma süresi. */
    val lostMs: Long,
)

/** Acil durum / ara verme sebepleri. Zaman çizelgesinde ve geçmişte gösterilir. */
@Serializable
enum class PauseReason { VISITOR, COOKING, CHORES, PHONE, TOILET, FAMILY, HEALTH, OTHER }

/** Zaman çizelgesi bölüm türü. */
@Serializable
enum class SegKind { WORK, BREAK, PAUSE, AWAY }

/** Oturum zaman çizelgesinin bir parçası. */
@Serializable
data class Segment(
    val start: Long,
    val end: Long,
    val kind: SegKind,
    /** Çalışma bölümünde dikkat: 1 odaklı, 0 dağınık, -1 bilinmiyor (soru sorulmadı). */
    val focus: Int = -1,
    /** Ek süre (normal turun dışında, daha çok ödül veren) çalışma. */
    val overtime: Boolean = false,
    val reason: PauseReason? = null,
)

/** Bitmiş bir oturumun kaydı: geçmiş ve zaman çizelgesi için. */
@Serializable
data class SessionLog(
    /** Oturum kimliği = başlangıç anı. */
    val id: Long,
    val start: Long,
    val end: Long,
    val workMs: Long,
    val segments: List<Segment> = emptyList(),
    val focusYes: Int = 0,
    val focusNo: Int = 0,
    val completed: Boolean = false,
    /** Zamanlayıcı açılmadan elle eklenen çalışma. */
    val manual: Boolean = false,
    val overtimeMs: Long = 0,
    val coins: Int = 0,
) {
    /** Bütün sorulara "odaklıyım" denmiş, hiç dağılma ya da yokluk yok: zaman çizelgesinde parlar. */
    val perfect: Boolean
        get() = !manual && focusYes > 0 && focusNo == 0 && segments.none { it.kind == SegKind.AWAY }
}

/** Ders parası kazancı / harcaması türü. Metinler dil dosyalarında. */
@Serializable
enum class EarnKind {
    STUDY, OVERTIME, SESSION_COMPLETE, FOCUS_ANSWER, EARLY_RETURN, DAILY_GOAL, MILESTONE, GOAL,
    WINDOW_ATTEND, WINDOW_GOAL, DEADLINE, MANUAL, LIBRARY, GAME, BREAK_EXTENSION, AWAY_PENALTY, PURCHASE, OTHER,
}

/** Cüzdan hareketi. Oturum sırasında kazanılanlar oturum özetinde tek tek gösterilir. */
@Serializable
data class Earning(
    val at: Long,
    /** Pozitif kazanç, negatif harcama. */
    val amount: Int,
    val kind: EarnKind,
    /** Türüne göre: dakika, seri günü, tur sayısı, gün... */
    val n: Int = 0,
    /** Kullanıcının verdiği ad (hedef, ders saati, süreli hedef, ürün) ya da serbest metin. */
    val label: String = "",
    /** Bir oturum sırasında kazanıldıysa oturumun kimliği (= başlangıç anı), değilse 0. */
    val session: Long = 0,
)

/** Günlük hedef tamamlandığında gösterilen seri kutlaması (uygulama kapalıyken olduysa açılınca gösterilir). */
@Serializable
data class Celebration(val streak: Int, val day: String, val newBest: Boolean = false)

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
    /** Eklendiği gün (yyyy-MM-dd). Seri hesabında bundan önceki günler sayılmaz. Eski kayıtlarda boş. */
    val createdDay: String = "",
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

    // ---- v1.5
    /**
     * Faz bitti, sıradakine geçiş bekleniyor. [phase] biten fazdır; [phaseEndAt] bittiği an.
     * Otomatik geçiş kapalıysa kullanıcı başlatana kadar, açıksa [autoAt] anına kadar beklenir.
     */
    val waiting: Boolean = false,
    /** Bekleme otomatikse sıradaki fazın başlayacağı an. */
    val autoAt: Long? = null,
    /** Kronometre: çalışma süresi bitti ama kullanıcı ek süreyle devam ediyor, sayaç yukarı sayar. */
    val stopwatch: Boolean = false,
    /** Şu anki çalışma bölümü ek süre (normal dakikadan fazla ödül verir). */
    val overtime: Boolean = false,
    /** Bu oturumda ek sürede çalışılan toplam süre. */
    val overtimeMs: Long = 0,
    /** Bu oturumda satın alınan mola uzatmaları. */
    val breakExtensions: Int = 0,
    /** Sıradaki mola uzatılamaz (yanıtsız kalma cezası). */
    val extensionBlocked: Boolean = false,
    /** Mola yarısı dolmadan atlandı: sıradaki çalışma turunun dakika ödülü düşük. */
    val fatigue: Boolean = false,
    /** Duraklatmanın sebebi (acil durum). */
    val pauseReason: PauseReason? = null,
    /** Zaman çizelgesi: bu oturumun kapanmış bölümleri. */
    val segments: List<Segment> = emptyList(),
    /** Açık bölümün başlangıcı. 0 ise [phaseStartedAt] kullanılır (eski kayıtlar). */
    val segStart: Long = 0,
) {
    val isPaused: Boolean get() = pausedRemainingMs != null
    val phaseTotalMs: Long get() = ((if (phase == Phase.WORK) workMinutes else breakMinutes) * MINUTE_MS + phaseExtraMs).coerceAtLeast(MINUTE_MS / 6)
    /** Oturum kimliği (kazançlar ve geçmiş bununla eşleşir). */
    val id: Long get() = startedAt
    val isLastRound: Boolean get() = round >= rounds
    /** Zaman akıyor mu (duraklatılmamış ve faz geçişi beklenmiyor). */
    val isRunning: Boolean get() = !isPaused && !waiting
}

@Serializable
data class PlacedBuilding(val x: Int, val y: Int, val buildingId: String)

@Serializable
data class LogEvent(val at: Long, val text: String, val coins: Int = 0)

/**
 * Bitmiş ve özeti henüz kapatılmamış oturum. Özet ekranı bunu gösterir; oturumda kazanılan paralar
 * ([Earning.session] == [id]) özet kapatılana kadar bakiyede "bekleyen" olarak gösterilir.
 */
@Serializable
data class SessionSummary(
    val workMs: Long,
    val endedAt: Long,
    val focusYes: Int = 0,
    val focusNo: Int = 0,
    // ---- v1.5
    /** Oturum kimliği (= başlangıç anı). Eski kayıtlarda 0. */
    val id: Long = 0,
    val startedAt: Long = 0,
    /** Bütün turlar bitirildi. */
    val completed: Boolean = false,
    val rounds: Int = 0,
    val overtimeMs: Long = 0,
    /** Sayfa / soru ilerlemesi girildi ya da geçildi. */
    val logged: Boolean = false,
)

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
    /** Belirli bir tarihe kadar bitirilecek hedefler (ör. 20 Aralık'a kadar 400 sayfa). */
    val deadlines: List<DeadlineGoal> = emptyList(),
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
    /** Eski metin günlüğü (köy olayları burada kalır). Ders parası hareketleri [ledger]'da. */
    val log: List<LogEvent> = emptyList(),

    // ---- v1.5
    /** Ders parası hareketleri (en yenisi başta, sınırlı sayıda). */
    val ledger: List<Earning> = emptyList(),
    /** Bitmiş oturumlar (en yenisi başta): geçmiş ve zaman çizelgesi. */
    val sessionLogs: List<SessionLog> = emptyList(),
    /** Gösterilmeyi bekleyen seri kutlaması. */
    val celebration: Celebration? = null,
    /** Dikkat sorularına cevap gelmediği için duraklatılmış oturum (kullanıcıya sorulacak). */
    val away: AwayState? = null,
    /** İlk açılış tanıtımı yapıldı. Yeni kurulumda false olarak başlar. */
    val onboarded: Boolean = true,
    /** Kayıt biçimi sürümü (yükseltmede bir kez yapılacak dönüşümler için). */
    val schema: Int = 0,
)

/** Oturum sürerken / özeti kapanmadan kazanılan, bakiyede henüz gösterilmeyen paralar. */
fun AppData.heldCoins(): Int {
    val running = timer?.id ?: 0L
    val summary = lastSession?.id ?: 0L
    if (running == 0L && summary == 0L) return 0
    return ledger.asSequence()
        .filter { it.session != 0L && (it.session == running || it.session == summary) }
        .sumOf { it.amount }
        .coerceAtLeast(0)
}

/** Ekranda gösterilen bakiye: bekleyen oturum kazançları özet kapanınca eklenir. */
fun AppData.visibleCoins(): Int = (coins - heldCoins()).coerceAtLeast(0)

fun defaultGoals() = listOf(
    Goal("g_pages", GoalType.PAGES, "Sayfa", 30, "sayfa"),
    Goal("g_questions", GoalType.QUESTIONS, "Soru / Test", 60, "soru"),
)

/** Belirli bir güne kadar tamamlanacak hedef. Bir günlük hedefe bağlıysa o hedefe girilen ilerleme otomatik sayılır. */
@Serializable
data class DeadlineGoal(
    val id: String,
    val title: String,
    val unit: String,
    val total: Int,
    /** yyyy-MM-dd */
    val startDate: String,
    val dueDate: String,
    val linkedGoalId: String? = null,
    /** Elle eklenen ilerleme (bağlı hedeften gelenler hariç). */
    val manual: Int = 0,
    val completedAt: Long? = null,
)
