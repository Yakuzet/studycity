package com.derscalisma.app.i18n

import com.derscalisma.app.data.EarnKind

/** Oturum özeti, cüzdan, kazanç türleri, seri kutlaması, zaman çizelgesi. */
interface RewardStrings {
    /** Kazanç satırının adı: "Çalışma (50 dk)", "Günlük hedef · 5 günlük seri". */
    fun earning(kind: EarnKind, n: Int, label: String): String

    val summaryDone: String
    val summaryStopped: String
    val logTitle: String
    val logHint: String
    val seeRewards: String
    val focusedTime: String
    val distractedTime: String
    val totalTime: String
    val extraTime: String
    val studiedRanges: String
    val progressDone: String
    val rewardsTitle: String
    val total: String
    val perfectSession: String
    val noRewards: String

    fun streakDays(n: Int): String
    val streakSubtitle: String
    val newBest: String
    val streakKeepGoing: String

    val walletTitle: String
    val todayEarned: String
    val howToEarn: String
    val spending: String
    val recent: String
    val heldNote: String
    fun ruleMinute(per: Int): String
    fun ruleOvertime(per: Int): String
    fun ruleDaily(now: Int): String
    val ruleDailyHint: String
    val ruleGoal: String
    val ruleWindow: String
    fun ruleSession(perRound: Int): String
    fun ruleEarlyBreak(per: Int): String
    val ruleFocus: String
    val ruleMilestones: String
    fun ruleExtension(prices: String): String
    fun ruleAway(fine: Int): String
    val ruleFatigue: String

    val timelineTitle: String
    val timelineEmpty: String
    val legendFocused: String
    val legendDistracted: String
    val legendUnknown: String
    val legendBreak: String
    val legendPause: String
    val legendAway: String
    val manualTag: String
}
