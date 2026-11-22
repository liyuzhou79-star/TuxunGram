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
    // Named duration specs (v0.9.29, V2). Until this version
    // call sites picked their own numbers (120–280 ms for
    // comparable motions), so the app felt uneven — one panel
    // rose in 180 ms, the next in 240. Every recurring motion
    // now takes its duration from this table, aligned to the
    // official client's hand-feel (its transitions sit in the
    // 200–300 ms band on the cubic-bezier(0.4, 0, 0.2, 1)
    // family — the same curve as FastOutSlowInEasing, which
    // tween() already defaults to). All values still pass
    // through ms(), so the system reduced-motion scale
    // collapses every one of them together.
    /** Route / page transitions: push and pop slides. */
    const val PAGE_IN = 300
    const val PAGE_OUT = 200
    /** Full-screen crossfades: app gate, auth flow, header
     *  mode switches, same-depth route changes. */
    const val CROSSFADE_IN = 250
    const val CROSSFADE_OUT = 200
    /** Bottom-nav tab switch: a short fade with drift. */
    const val TAB_IN = 200
    const val TAB_OUT = 150
    /** Long-press floating menu: scale + fade from its
     *  anchor (0.85 start scale at the call site). */
    const val MENU_IN = 180
    const val MENU_OUT = 130
    /** Input-area panels rising over the keyboard area:
     *  emoji / sticker / command / suggestion panels. */
    const val PANEL_IN = 230
    const val PANEL_OUT = 180
    /** A message that just arrived or was just sent settling
     *  into the list (fade + slight scale). */
    const val ITEM_APPEAR = 220
    /** The floating "back to latest" button appearing. */
    const val SCROLL_BTN_IN = 180
    const val SCROLL_BTN_OUT = 140
    /** Folder tab underline slide. */
    const val TAB_UNDERLINE = 200
    /** Chat-list rows gliding to a new position. */
    const val LIST_PLACEMENT = 250
    /** Press feedback on the send button. */
    const val PRESS = 120

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
