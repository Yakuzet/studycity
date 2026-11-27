package com.derscalisma.app.data

import kotlin.math.roundToInt
import kotlin.random.Random

/** Dikkat toplama sistemi: bip zamanlaması, tur içi soru noktaları ve yüzdeler. */
object Focus {
    /** Cevapsız kalan sorudan sonra tekrar sorma aralığı. */
    const val REASK_MS = 45_000L
    /** Zincirdeki en fazla soru: sonuncusu da cevapsız kalırsa süre geri alınır ve duraklatılır. */
    const val MAX_ATTEMPTS = 4
    /** Cevap süresi ayarının sınırları (sn). */
    const val MIN_ANSWER_SEC = 5
    const val MAX_ANSWER_SEC = 60

    /** [from] anından sonraki bip zamanı; bip kapalıysa null. Rastgele modda dakika kesirli seçilir. */
    fun nextBeepAt(s: Settings, from: Long): Long? {
        if (!s.focusBeepEnabled) return null
        val minutes = if (s.focusBeepRandom) {
            val lo = s.focusBeepMin.coerceAtLeast(1)
            val hi = s.focusBeepMax.coerceAtLeast(lo)
            lo + Random.nextDouble() * (hi - lo)
        } else {
            s.focusBeepEvery.coerceAtLeast(1).toDouble()
        }
        return from + (minutes * MINUTE_MS).toLong()
    }

    /** Çalışma turunun 1/n noktalarındaki bir sonraki soru zamanı. */
    fun nextCheckAt(t: TimerState, s: Settings): Long? {
        val n = s.focusCheckDivisions
        if (t.phase != Phase.WORK || t.isPaused || n < 2 || t.checksDone >= n - 1) return null
        val total = t.phaseTotalMs
        val start = t.phaseEndAt - total
        return start + total * (t.checksDone + 1) / n
    }

    /** Zamanlayıcının bir sonraki işlenmesi gereken anı: faz sonu, bip ya da ara soru. */
    fun nextEventAt(t: TimerState, s: Settings): Long {
        var at = t.phaseEndAt
        if (t.phase == Phase.WORK) {
            val beep = t.nextBeepAt
            if (s.focusBeepEnabled && beep != null && beep < at) at = beep
            nextCheckAt(t, s)?.let { if (it < at) at = it }
        }
        return at
    }

    /** Toplu cevapların yüzdesi; hiç cevap yoksa null. */
    fun percent(yes: Int, no: Int): Int? =
        if (yes + no == 0) null else (yes * 100f / (yes + no)).roundToInt()

    fun divisionLabel(n: Int) = when (n) {
        0 -> "Kapalı"
        1 -> "Tur sonunda"
        2 -> "Yarısında"
        3 -> "Her 1/3'te"
        else -> "Her 1/4'te"
    }
}
