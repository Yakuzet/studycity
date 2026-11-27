package com.derscalisma.app.i18n

/** Veri katmanının kullanıcıya gösterdiği mesajlar (Repo). */
interface EngineStrings {
    fun saveFailed(msg: String): String
    val notABackup: String
    val fileUnreadable: String
    fun restored(coins: Int, best: Int): String
    val backupWritten: String
    val backupFailed: String
    val freezeUsedTitle: String
    fun freezeUsedText(days: Int): String
    val streakLostTitle: String
    fun streakLostText(days: Int): String
    val extensionBlocked: String
    val extensionLimit: String
    fun awayPenalty(coins: Int): String
    val manualFuture: String
    val manualInvalid: String
    val manualOtherDay: String
    val manualOverlap: String
    fun manualAdded(minutes: Int): String
    fun maxFreezes(n: Int): String
    val freezeBought: String
    val bought: String
}

/** Bildirimler, hatırlatmalar, bildirim kanalları. */
interface NotifyStrings {
    val chTimer: String
    val chTimerDesc: String
    val chPhase: String
    val chPhaseDesc: String
    val chRemind: String
    val chRemindDesc: String
    val chReward: String
    val chRewardDesc: String
    val chFocus: String
    val chFocusDesc: String

    val phaseWork: String
    val phaseBreak: String
    val waitingAfterWork: String
    val waitingAfterBreak: String
    fun pausedRemaining(clock: String): String
    val stopwatchRunning: String
    fun endsAt(time: String): String
    val actionPause: String
    val actionResume: String
    val actionStop: String
    val actionOvertime5: String
    val actionStartBreak: String
    val actionStartWork: String
    val actionFinishOvertime: String

    val breakStartTitle: String
    fun breakStartText(minutes: Int, round: Int, rounds: Int): String
    val workStartTitle: String
    fun workStartText(round: Int, rounds: Int, minutes: Int): String
    val roundEndTitle: String
    val roundEndText: String
    val breakEndTitle: String
    val breakEndText: String
    val finishedTitle: String
    fun finishedText(minutes: Int): String

    val focusTitle: String
    val focusText: String
    val focusAgainTitle: String
    val focusAgainText: String
    val focusYes: String
    val focusNo: String
    val awayTitle: String
    fun awayText(since: String): String

    fun windowStartTitle(name: String): String
    fun windowStartText(range: String, targetMin: Int, streak: Int): String
    val windowNudgeTitle: String
    fun windowNudgeText(name: String, end: String, streak: Int): String
    fun windowEndingTitle(name: String): String
    fun windowEndingText(leftMin: Int, untilEndMin: Int, streak: Int): String
    val deadlineTitle: String
    fun deadlineLine(name: String, amount: String, daysLeft: Int): String
    fun streakWarnTitle(streak: Int): String
    fun streakWarnText(leftMin: Int, streak: Int): String
    fun lastChanceTitle(streak: Int): String
    fun lastChanceText(leftMin: Int, freezes: Int): String
    fun dailyGoalTitle(streak: Int): String
    fun dailyGoalText(streak: Int): String
    val rewardTitle: String
    val testTitle: String
    val testText: String
}
