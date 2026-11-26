package com.derscalisma.app.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.derscalisma.app.R
import com.derscalisma.app.data.Phase
import com.derscalisma.app.data.PhaseEvent
import com.derscalisma.app.data.PhaseEventKind
import com.derscalisma.app.data.Time
import com.derscalisma.app.data.TimerState
import com.derscalisma.app.ui.MainActivity
import java.util.concurrent.atomic.AtomicInteger

object Notifier {
    private const val CH_TIMER = "timer"
    private const val CH_PHASE = "phase"
    private const val CH_REMIND = "remind"
    private const val CH_REWARD = "reward"
    private const val CH_FOCUS = "focus"

    private const val ID_TIMER = 1
    private const val ID_PHASE = 2
    private const val ID_FOCUS = 3
    private val nextId = AtomicInteger(100)

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_TIMER, "Zamanlayıcı", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Çalışırken kalan süreyi gösterir"
                    setShowBadge(false)
                },
                NotificationChannel(CH_PHASE, "Çalışma / Mola geçişleri", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Mola ve çalışma başladığında uyarır"
                    enableVibration(true)
                },
                NotificationChannel(CH_REMIND, "Hatırlatmalar ve seri uyarıları", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Ders saati başladı, serini kaybetmek üzeresin gibi uyarılar"
                    enableVibration(true)
                },
                NotificationChannel(CH_REWARD, "Ödüller", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Hedef ve seri ödülleri"
                },
                NotificationChannel(CH_FOCUS, "Dikkat kontrolü", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "\"Dikkatin toplu mu?\" soruları"
                    enableVibration(true)
                },
            )
        )
    }

    private fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun timerAction(ctx: Context, action: String, title: String): Notification.Action {
        val pi = PendingIntent.getBroadcast(
            ctx, action.hashCode(),
            Intent(ctx, TimerReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_notification), title, pi).build()
    }

    private fun builder(ctx: Context, channel: String): Notification.Builder =
        Notification.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)

    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    fun timerOngoing(ctx: Context, t: TimerState?) {
        if (t == null) {
            nm(ctx).cancel(ID_TIMER)
            return
        }
        val phase = if (t.phase == Phase.WORK) "📖 Çalışma" else "☕ Mola"
        val b = builder(ctx, CH_TIMER)
            .setContentTitle("$phase • Tur ${t.round}/${t.rounds}")
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
        val paused = t.pausedRemainingMs
        if (paused != null) {
            b.setContentText("⏸ Duraklatıldı • Kalan ${Time.clock(paused)}")
                .setShowWhen(false)
                .addAction(timerAction(ctx, TimerReceiver.ACTION_RESUME, "Devam et"))
        } else {
            b.setContentText("Bitiş: ${Time.timeOf(t.phaseEndAt)}")
                .setWhen(t.phaseEndAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(timerAction(ctx, TimerReceiver.ACTION_PAUSE, "Duraklat"))
        }
        b.addAction(timerAction(ctx, TimerReceiver.ACTION_STOP, "Bitir"))
        nm(ctx).notify(ID_TIMER, b.build())
    }

    fun phase(ctx: Context, e: PhaseEvent) {
        val (title, text) = when (e.kind) {
            PhaseEventKind.BREAK_START -> "☕ Mola zamanı!" to "${e.minutes} dk dinlen. Tur ${e.round}/${e.rounds} tamamlandı."
            PhaseEventKind.WORK_START -> "📖 Çalışma zamanı!" to "Tur ${e.round}/${e.rounds} başladı, ${e.minutes} dk odaklan."
            PhaseEventKind.FINISHED -> "🎉 Oturum bitti!" to "Toplam ${e.minutes} dk çalıştın. Sayfa ve soru ilerlemeni kaydetmeyi unutma."
        }
        nm(ctx).notify(
            ID_PHASE,
            builder(ctx, CH_PHASE)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build()
        )
    }

    fun focusAsk(ctx: Context) {
        nm(ctx).notify(
            ID_FOCUS,
            builder(ctx, CH_FOCUS)
                .setContentTitle("🎯 Dikkatin toplu mu?")
                .setContentText("Şu an gerçekten derse mi odaklısın? Dürüstçe cevapla.")
                .setCategory(Notification.CATEGORY_REMINDER)
                .addAction(timerAction(ctx, TimerReceiver.ACTION_FOCUS_YES, "✅ Evet, odaklıyım"))
                .addAction(timerAction(ctx, TimerReceiver.ACTION_FOCUS_NO, "💭 Hayır, dağıldım"))
                .build()
        )
    }

    fun cancelFocusAsk(ctx: Context) = nm(ctx).cancel(ID_FOCUS)

    fun alert(ctx: Context, title: String, text: String) {
        nm(ctx).notify(
            nextId.incrementAndGet(),
            builder(ctx, CH_REMIND)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_REMINDER)
                .build()
        )
    }

    fun reward(ctx: Context, text: String) {
        nm(ctx).notify(
            nextId.incrementAndGet(),
            builder(ctx, CH_REWARD)
                .setContentTitle("🏆 Ödül kazandın")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .build()
        )
    }
}
