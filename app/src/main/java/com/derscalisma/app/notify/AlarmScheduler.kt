package com.derscalisma.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.derscalisma.app.data.AppData
import com.derscalisma.app.data.Focus
import com.derscalisma.app.data.Settings
import com.derscalisma.app.data.TimerState

object AlarmScheduler {
    private const val RC_TIMER = 10
    private const val RC_REMIND = 11

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    /** Faz sonu, dikkat bipi ve ara soru: hangisi önce geliyorsa ona alarm kurar. */
    fun syncTimer(ctx: Context, t: TimerState?, s: Settings) {
        if (t == null || t.isPaused) cancel(ctx, RC_TIMER, TimerReceiver::class.java)
        else set(ctx, RC_TIMER, Focus.nextEventAt(t, s), TimerReceiver::class.java)
    }

    fun scheduleReminders(ctx: Context, data: AppData, now: Long) {
        set(ctx, RC_REMIND, Reminders.nextAlarmTime(data, now), ReminderReceiver::class.java)
    }

    private fun pending(ctx: Context, rc: Int, cls: Class<*>): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, rc, Intent(ctx, cls),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun set(ctx: Context, rc: Int, at: Long, cls: Class<*>) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, rc, cls)
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancel(ctx: Context, rc: Int, cls: Class<*>) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, rc, cls))
    }
}
