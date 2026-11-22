package com.tuxungram.android.td

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

/**
 * Light touch feedback (v0.9.30, W6): the official client
 * answers a handful of gestures with a short, quiet buzz —
 * a long-press menu opening, a double-tap reaction landing,
 * a message leaving, recording starting/stopping. This
 * mirrors those moments with ONE restrained pulse (10 ms at
 * low amplitude), and only while the system-wide haptic
 * feedback switch (Settings.System.HAPTIC_FEEDBACK_ENABLED)
 * is on: a user who turned touch feedback off in the system
 * gets none from us either. Every call is best-effort — a
 * missing vibrator or a denied permission stays silent.
 */
object Haptics {

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var lastAt: Long = 0L

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun enabled(): Boolean {
        val ctx = appContext ?: return false
        return try {
            Settings.System.getInt(
                ctx.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1
            ) == 1
        } catch (t: Throwable) {
            true
        }
    }

    /** One short, soft pulse. Throttled so a burst of UI
     *  events (e.g. several sends in a row) is one buzz. */
    fun tick() {
        val ctx = appContext ?: return
        if (!enabled()) return
        val now = System.currentTimeMillis()
        if (now - lastAt < 120) return
        lastAt = now
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(10, 50)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(10)
            }
        } catch (t: Throwable) {
            // Haptics must never take the UI down with them.
        }
    }
}
