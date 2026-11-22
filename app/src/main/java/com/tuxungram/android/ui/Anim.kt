package com.tuxungram.android.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import kotlin.math.roundToInt

/**
 * The app's motion system (v0.9.11, A1). Until this version
 * the app had effectively no transitions — every screen
 * change was a hard cut. All new animation goes through the
 * specs here so durations and easing stay consistent
 * (150–300 ms, Compose's standard curves — the same family
 * mature clients use) and so the system reduced-motion
 * setting is honoured in one place: Settings.Global's
 * ANIMATOR_DURATION_SCALE is read once at startup, and
 * every duration produced here is multiplied by it. A scale
 * of 0 (animations disabled in system settings) collapses
 * every spec to an instant cut, which is exactly what the
 * setting asks for.
 */
object Motion {
    @Volatile
    var durationScale: Float = 1f
        private set

    fun init(context: Context) {
        durationScale = try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        } catch (e: Exception) {
            1f
        }
    }

    /** A base duration in milliseconds, scaled by the
     *  system animator scale. */
    fun ms(base: Int): Int = (base * durationScale).roundToInt()

    /** The standard tween: FastOutSlowIn unless told
     *  otherwise, duration scaled. */
    fun <T> tween(
        base: Int = 250,
        easing: Easing = FastOutSlowInEasing
    ): TweenSpec<T> = tween(durationMillis = ms(base), easing = easing)
}
