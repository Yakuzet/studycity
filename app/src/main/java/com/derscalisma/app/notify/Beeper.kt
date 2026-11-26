package com.derscalisma.app.notify

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

/** Dikkat toplama bipi: kısa çift bip + isteğe bağlı titreşim. */
object Beeper {
    fun beep(ctx: Context, vibrate: Boolean) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_ALARM, 85)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
            Handler(Looper.getMainLooper()).postDelayed({ tg.release() }, 900)
        } catch (_: Exception) {
            // Ses donanımı meşgulse sessizce geç.
        }
        if (vibrate) {
            try {
                ctx.getSystemService(Vibrator::class.java)
                    ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180), -1))
            } catch (_: Exception) {
            }
        }
    }
}
