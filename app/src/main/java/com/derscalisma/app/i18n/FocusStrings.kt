package com.derscalisma.app.i18n

/** Odak (zamanlayıcı) ekranı, ek süre, mola kuralları, odak sorusu, acil durum, elle süre ekleme. */
interface FocusStrings {
    val presetClassic: String
    val presetDeep: String
    val presetLong: String
    val presetCustom: String
    fun plannedEnd(rounds: Int, totalMin: Int, end: String): String
    fun startCta(work: Int, rounds: Int): String
    val work: String
    val breakLabel: String
    val roundsLabel: String
    val ready: String
    val studying: String
    val onBreak: String
    val paused: String
    val overtime: String
    val pause: String
    val resume: String
    val skipRound: String
    val endBreak: String
    val stop: String
    val stopTitle: String
    val stopText: String
    val adjust: String
    val thisPhase: String
    fun sessionEarned(n: Int): String
    fun estimatedEnd(time: String): String

    val roundDone: String
    val lastRoundDone: String
    fun autoBreakIn(sec: Int): String
    fun autoWorkIn(sec: Int): String
    fun autoFinishIn(sec: Int): String
    val needMoreTime: String
    val overtimeHint: String
    val stopwatch: String
    val startBreak: String
    val finishSession: String
    val breakOver: String
    val startWork: String
    val finishOvertime: String
    val overtimeBadge: String

    fun extendBreak(min: Int, price: Int): String
    val extendBlocked: String
    fun extensionsLeft(n: Int): String
    val earlyBreakTitle: String
    val earlyBreakText: String
    val keepResting: String
    val skipAnyway: String
    val fatigueBadge: String

    val emergency: String
    val emergencyTitle: String
    val emergencyText: String
    val pauseReasonPrompt: String

    val focusQuestion: String
    val areYouThere: String
    val focusedBtn: String
    val distractedBtn: String
    fun answerIn(sec: Int): String

    val awayTitle: String
    fun awayText(since: String, lostMin: Int): String
    val awayPick: String
    fun awayNoReason(fee: Int): String

    val manualTitle: String
    val manualText: String
    val manualStart: String
    val manualEnd: String
    fun manualDuration(min: Int): String
    val manualAdd: String

    val sessionSettings: String
    val autoBreak: String
    val autoBreakHint: String
    val autoWork: String
    val autoWorkHint: String
    val delay: String
    val immediately: String
    val focusCheck: String
    val beep: String
    val beepHint: String
    val beepRandom: String
    val beepFixed: String
    val shortest: String
    val longest: String
    val every: String
    val askAfterBeep: String
    val vibrate: String
    val testBeep: String
    val askInRound: String
    fun division(n: Int): String
    val answerTime: String
    val answerTimeHint: String
    val music: String
    val musicOff: String
    fun track(id: String): String
    val musicInBreak: String
    val today: String
}
