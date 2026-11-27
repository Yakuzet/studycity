package com.derscalisma.app.i18n.en

import com.derscalisma.app.i18n.EngineStrings
import com.derscalisma.app.i18n.NotifyStrings
import com.derscalisma.app.i18n.plural

object EngineEn : EngineStrings {
    override fun saveFailed(msg: String) = "Couldn't save: $msg"
    override val notABackup = "This file isn't a StudyCity backup or can't be read."
    override val fileUnreadable = "This file can't be read."
    override fun restored(coins: Int, best: Int) = "Save restored: $coins study coins, best streak $best " + plural(best, "day", "days")
    override val backupWritten = "Backup saved to Downloads/StudyCity"
    override val backupFailed = "Backup couldn't be saved"
    override val freezeUsedTitle = "Your streak is safe"
    override fun freezeUsedText(days: Int) = "You missed a day, but a streak freeze saved your $days-day streak. Hit your goal today."
    override val streakLostTitle = "Your streak ended"
    override fun streakLostText(days: Int) = "Your $days-day streak was reset. Start again today and build a new one."
    override val extensionBlocked = "This break can't be extended because you didn't respond."
    override val extensionLimit = "No break extensions left in this session."
    override fun awayPenalty(coins: Int) = "Break fee and fine: −$coins study coins. The next break can't be extended."
    override val manualFuture = "You can't add a time in the future."
    override val manualInvalid = "The time must be between 1 minute and 12 hours."
    override val manualOtherDay = "You can only add study time for today."
    override val manualOverlap = "These times overlap a recorded session."
    override fun manualAdded(minutes: Int) = "Added $minutes min of study"
    override fun maxFreezes(n: Int) = "You can hold up to $n streak freezes"
    override val freezeBought = "Streak freeze bought"
    override val bought = "Bought and applied"
}

object NotifyEn : NotifyStrings {
    override val chTimer = "Timer"
    override val chTimerDesc = "Shows the time left while you study"
    override val chPhase = "Study and break changes"
    override val chPhaseDesc = "Alerts you when a break or study round starts"
    override val chRemind = "Reminders and streak alerts"
    override val chRemindDesc = "Study block started, streak at risk and similar alerts"
    override val chReward = "Rewards"
    override val chRewardDesc = "Goal and streak rewards"
    override val chFocus = "Focus check"
    override val chFocusDesc = "\"Are you focused?\" questions"

    override val phaseWork = "Studying"
    override val phaseBreak = "Break"
    override val waitingAfterWork = "Round done. Need extra time, or start the break?"
    override val waitingAfterBreak = "Break's over. Start studying when you're ready."
    override fun pausedRemaining(clock: String) = "Paused • $clock left"
    override val stopwatchRunning = "Extra time: finish the question, then take your break"
    override fun endsAt(time: String) = "Ends at $time"
    override val actionPause = "Pause"
    override val actionResume = "Resume"
    override val actionStop = "Finish"
    override val actionOvertime5 = "+5 min extra"
    override val actionStartBreak = "Start break"
    override val actionStartWork = "Start studying"
    override val actionFinishOvertime = "Done, start break"

    override val breakStartTitle = "Break time"
    override fun breakStartText(minutes: Int, round: Int, rounds: Int) = "Rest for $minutes min. Round $round/$rounds done."
    override val workStartTitle = "Time to study"
    override fun workStartText(round: Int, rounds: Int, minutes: Int) = "Round $round/$rounds started. Focus for $minutes min."
    override val roundEndTitle = "Round done"
    override val roundEndText = "Need to finish a question? Take extra time. Otherwise start your break."
    override val breakEndTitle = "Break's over"
    override val breakEndText = "Start studying when you're ready."
    override val finishedTitle = "Session complete"
    override fun finishedText(minutes: Int) = "You studied $minutes min. Open the app to see your summary and rewards."

    override val focusTitle = "Are you focused?"
    override val focusText = "Are you really focused on studying right now? Answer honestly."
    override val focusAgainTitle = "Are you there?"
    override val focusAgainText = "No answer to the last question. If you don't answer, the timer will stop."
    override val focusYes = "Focused"
    override val focusNo = "Distracted"
    override val awayTitle = "Timer stopped"
    override fun awayText(since: String) = "No answers came in. Time was rolled back to $since. When you're back, tell us why you left."

    override fun windowStartTitle(name: String) = "Study block: $name"
    override fun windowStartText(range: String, targetMin: Int, streak: Int) =
        "$range has started. Goal: $targetMin min. Start now." + if (streak > 0) " Block streak: $streak." else ""
    override val windowNudgeTitle = "You haven't started yet"
    override fun windowNudgeText(name: String, end: String, streak: Int) =
        (if (streak > 0) "Your $streak-block streak is at risk. " else "") + "$name ends at $end. Start now."
    override fun windowEndingTitle(name: String) = "Last call for $name"
    override fun windowEndingText(leftMin: Int, untilEndMin: Int, streak: Int) =
        "$leftMin min left to your goal and $untilEndMin min until the block ends. Start now and you'll make it." +
            if (streak > 0) " Don't lose your $streak-block goal streak." else ""
    override val deadlineTitle = "You're behind on a deadline goal"
    override fun deadlineLine(name: String, amount: String, daysLeft: Int) = "$name: $amount more today ($daysLeft " + plural(daysLeft, "day", "days") + " left)"
    override fun streakWarnTitle(streak: Int) = if (streak > 0) "Your $streak-day streak is at risk" else "Your daily goal is waiting"
    override fun streakWarnText(leftMin: Int, streak: Int) =
        "$leftMin min left to today's goal. " + if (streak > 0) "Keep your streak, start now." else "Start a new streak today."
    override fun lastChanceTitle(streak: Int) = "Last chance: your $streak-day streak is about to end"
    override fun lastChanceText(leftMin: Int, freezes: Int) =
        "$leftMin min left to today's goal. Finish before midnight or your streak resets." +
            if (freezes > 0) " (You have $freezes streak " + plural(freezes, "freeze", "freezes") + ")" else ""
    override fun dailyGoalTitle(streak: Int) = "Daily goal done: $streak " + plural(streak, "day", "days")
    override fun dailyGoalText(streak: Int) = "Your streak is now $streak. Open the app to celebrate."
    override val rewardTitle = "You earned a reward"
    override val testTitle = "Test notification"
    override val testText = "Notifications work. I'll remind you when it's time to study."
}
