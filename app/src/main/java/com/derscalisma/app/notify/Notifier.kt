package com.derscalisma.app.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.derscalisma.app.R
import com.derscalisma.app.data.FocusAsk
import com.derscalisma.app.data.Phase
import com.derscalisma.app.data.PhaseEvent
import com.derscalisma.app.data.PhaseEventKind
import com.derscalisma.app.data.Settings
import com.derscalisma.app.data.TimerState
import com.derscalisma.app.i18n.Fmt
import com.derscalisma.app.i18n.S
import com.derscalisma.app.ui.MainActivity

object Notifier {
    private const val CH_TIMER = "timer"
    private const val CH_PHASE = "phase"
    private const val CH_REMIND = "remind"
    private const val CH_REWARD = "reward"
    private const val CH_FOCUS = "focus"

    private const val ID_TIMER = 1
    private const val ID_PHASE = 2
    private const val ID_FOCUS = 3

    /** Kanalları oluşturur; dil değişince adları güncellemek için yeniden çağrılır. */
    fun createChannels(ctx: Context) {
        val n = S.notify
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_TIMER, n.chTimer, NotificationManager.IMPORTANCE_LOW).apply {
                    description = n.chTimerDesc
                    setShowBadge(false)
                },
                NotificationChannel(CH_PHASE, n.chPhase, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = n.chPhaseDesc
                    enableVibration(true)
                },
                NotificationChannel(CH_REMIND, n.chRemind, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = n.chRemindDesc
                    enableVibration(true)
                },
                NotificationChannel(CH_REWARD, n.chReward, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = n.chRewardDesc
                },
                NotificationChannel(CH_FOCUS, n.chFocus, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = n.chFocusDesc
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

    /** Her bildirime ayrı kimlik: süreç yeniden başlasa da okunmamış uyarıların üstüne yazılmaz. */
    private fun uniqueId(): Int = (System.currentTimeMillis() % 1_000_000_000L).toInt() + 1000

    fun timerOngoing(ctx: Context, t: TimerState?, s: Settings) {
        if (t == null) {
            nm(ctx).cancel(ID_TIMER)
            return
        }
        val n = S.notify
        val phase = if (t.phase == Phase.WORK) n.phaseWork else n.phaseBreak
        val b = builder(ctx, CH_TIMER)
            .setContentTitle("$phase • ${S.common.roundOf(t.round, t.rounds)}")
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
        val paused = t.pausedRemainingMs
        when {
            t.waiting -> {
                b.setContentText(if (t.phase == Phase.WORK) n.waitingAfterWork else n.waitingAfterBreak).setShowWhen(false)
                if (t.phase == Phase.WORK) b.addAction(timerAction(ctx, TimerReceiver.ACTION_OVERTIME, n.actionOvertime5))
                b.addAction(timerAction(ctx, TimerReceiver.ACTION_NEXT, if (t.phase == Phase.WORK) n.actionStartBreak else n.actionStartWork))
            }
            paused != null -> {
                b.setContentText(n.pausedRemaining(Fmt.clock(paused)))
                    .setShowWhen(false)
                    .addAction(timerAction(ctx, TimerReceiver.ACTION_RESUME, n.actionResume))
            }
            t.stopwatch -> {
                b.setContentText(n.stopwatchRunning)
                    .setWhen(t.phaseEndAt - com.derscalisma.app.data.Rewards.MAX_OVERTIME_MIN * com.derscalisma.app.data.MINUTE_MS)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .addAction(timerAction(ctx, TimerReceiver.ACTION_NEXT, n.actionFinishOvertime))
            }
            else -> {
                b.setContentText(n.endsAt(Fmt.time(t.phaseEndAt)))
                    .setWhen(t.phaseEndAt)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .addAction(timerAction(ctx, TimerReceiver.ACTION_PAUSE, n.actionPause))
            }
        }
        b.addAction(timerAction(ctx, TimerReceiver.ACTION_STOP, n.actionStop))
        nm(ctx).notify(ID_TIMER, b.build())
    }

    fun phase(ctx: Context, e: PhaseEvent) {
        val n = S.notify
        val (title, text) = when (e.kind) {
            PhaseEventKind.BREAK_START -> n.breakStartTitle to n.breakStartText(e.minutes, e.round, e.rounds)
            PhaseEventKind.WORK_START -> n.workStartTitle to n.workStartText(e.round, e.rounds, e.minutes)
            PhaseEventKind.ROUND_END -> n.roundEndTitle to n.roundEndText
            PhaseEventKind.BREAK_END -> n.breakEndTitle to n.breakEndText
            PhaseEventKind.FINISHED -> n.finishedTitle to n.finishedText(e.minutes)
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

    fun focusAsk(ctx: Context, a: FocusAsk, s: Settings) {
        val n = S.notify
        val b = builder(ctx, CH_FOCUS)
            .setContentTitle(if (a.attempt >= 2) n.focusAgainTitle else n.focusTitle)
            .setContentText(if (a.attempt >= 2) n.focusAgainText else n.focusText)
            .setCategory(Notification.CATEGORY_REMINDER)
            .addAction(timerAction(ctx, TimerReceiver.ACTION_FOCUS_YES, n.focusYes))
            .addAction(timerAction(ctx, TimerReceiver.ACTION_FOCUS_NO, n.focusNo))
        if (a.expiresAt > 0) b.setTimeoutAfter((a.expiresAt - System.currentTimeMillis()).coerceAtLeast(1000))
        nm(ctx).notify(ID_FOCUS, b.build())
    }

    fun cancelFocusAsk(ctx: Context) = nm(ctx).cancel(ID_FOCUS)

    fun alert(ctx: Context, title: String, text: String) {
        nm(ctx).notify(
            uniqueId(),
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
            uniqueId(),
            builder(ctx, CH_REWARD)
                .setContentTitle(S.notify.rewardTitle)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .build()
        )
    }
}
