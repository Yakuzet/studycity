package com.derscalisma.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.derscalisma.app.data.Repo

/** Faz bitiş alarmı ve bildirimdeki Duraklat / Devam / Bitir düğmeleri. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        when (intent.action) {
            ACTION_PAUSE -> Repo.pauseTimer()
            ACTION_RESUME -> Repo.resumeTimer()
            ACTION_STOP -> Repo.stopTimer()
            ACTION_FOCUS_YES -> Repo.answerFocus(true)
            ACTION_FOCUS_NO -> Repo.answerFocus(false)
            else -> Repo.tick()
        }
    }

    companion object {
        const val ACTION_FOCUS_YES = "com.derscalisma.app.action.FOCUS_YES"
        const val ACTION_FOCUS_NO = "com.derscalisma.app.action.FOCUS_NO"
        const val ACTION_PAUSE = "com.derscalisma.app.action.PAUSE"
        const val ACTION_RESUME = "com.derscalisma.app.action.RESUME"
        const val ACTION_STOP = "com.derscalisma.app.action.STOP"
    }
}

/** Ders aralığı, seri uyarısı ve gün kapanışı hatırlatmaları. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        Repo.runReminders()
    }
}

/** Telefon yeniden başlayınca / saat değişince alarmları yeniden kurar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        Repo.rescheduleAll()
    }
}
