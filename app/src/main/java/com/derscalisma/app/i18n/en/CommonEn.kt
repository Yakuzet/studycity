package com.derscalisma.app.i18n.en

import com.derscalisma.app.data.GoalType
import com.derscalisma.app.data.PauseReason
import com.derscalisma.app.i18n.CommonStrings
import com.derscalisma.app.i18n.NavStrings
import com.derscalisma.app.i18n.plural

object CommonEn : CommonStrings {
    override val appName = "StudyCity"
    override val ok = "OK"
    override val cancel = "Cancel"
    override val save = "Save"
    override val delete = "Delete"
    override val edit = "Edit"
    override val add = "Add"
    override val close = "Close"
    override val back = "Back"
    override val done = "Done"
    override val skip = "Skip"
    override val next = "Next"
    override val start = "Start"
    override val continueLabel = "Continue"
    override val yes = "Yes"
    override val no = "No"
    override val on = "On"
    override val off = "Off"
    override val today = "Today"
    override val yesterday = "Yesterday"
    override val tomorrow = "Tomorrow"
    override val everyDay = "Every day"
    override val weekdays = "Weekdays"
    override val weekend = "Weekend"
    override val none = "None"

    override val coinName = "Study coins"
    override val balance = "Balance"

    override fun minutes(n: Int) = "$n min"
    override fun minutesLong(n: Int) = "$n " + plural(n, "minute", "minutes")
    override fun hours(n: Int) = "$n h"
    override fun hoursMinutes(h: Int, m: Int) = if (m == 0) "$h h" else "$h h $m min"
    override fun seconds(n: Int) = "$n s"
    override fun days(n: Int) = "$n " + plural(n, "day", "days")
    override fun percent(p: Int) = "$p%"
    override fun roundOf(i: Int, n: Int) = "Round $i/$n"
    override fun rounds(n: Int) = "$n " + plural(n, "round", "rounds")

    override val dayMonthPattern = "MMMM d"
    override val weekdayDayMonthPattern = "EEEE, MMMM d"

    override fun goalTypeName(t: GoalType) = when (t) {
        GoalType.PAGES -> "Pages"
        GoalType.QUESTIONS -> "Questions / tests"
        GoalType.TOPICS -> "Topics"
        GoalType.CUSTOM -> "Custom goal"
    }

    override fun goalTypeUnit(t: GoalType, n: Int) = when (t) {
        GoalType.PAGES -> plural(n, "page", "pages")
        GoalType.QUESTIONS -> plural(n, "question", "questions")
        GoalType.TOPICS -> plural(n, "topic", "topics")
        GoalType.CUSTOM -> plural(n, "item", "items")
    }

    override fun pauseReason(r: PauseReason) = when (r) {
        PauseReason.VISITOR -> "Someone came by"
        PauseReason.COOKING -> "Cooking"
        PauseReason.CHORES -> "Chores, laundry"
        PauseReason.PHONE -> "Phone call"
        PauseReason.TOILET -> "Bathroom"
        PauseReason.FAMILY -> "Family, kids"
        PauseReason.HEALTH -> "Health"
        PauseReason.OTHER -> "Other urgent task"
    }

    override val defaultWindowName = "Study block"
    override val defaultDeadlineName = "Deadline goal"

    override fun notEnoughCoins(missing: Int) = "$missing study coins short"
}

object NavEn : NavStrings {
    override val today = "Today"
    override val focus = "Focus"
    override val plan = "Plan"
    override val village = "Village"
    override val store = "Store"
    override val settings = "Settings"
    override val wallet = "Wallet"
    override val miniWork = "Studying"
    override val miniBreak = "Break"
    override val miniPaused = "Paused"
    override val miniWaiting = "Waiting for the next phase"
    override val villageOnlyTurkish = "The village is in Turkish for now. Translations arrive with the village update."
}
