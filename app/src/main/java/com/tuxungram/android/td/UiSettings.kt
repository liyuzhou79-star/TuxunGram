package com.tuxungram.android.td

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Small display preferences, persisted in SharedPreferences and
 * exposed as StateFlows so the UI re-renders the moment they change.
 * Currently: the unread-badge cap (99 / 999 / 9999, default 999) —
 * the same option mature third-party Telegram clients offer, since
 * channel-heavy accounts routinely blow past two digits — and the
 * background keep-alive / message-notification switch (Batch 3).
 */
object UiSettings {
    private const val PREFS = "ui"
    private const val KEY_UNREAD_CAP = "unread_cap"
    private const val KEY_KEEP_ALIVE = "keep_alive"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_SEED_INDEX = "seed_index"

    val UNREAD_CAPS = listOf(99, 999, 9999)

    /** Theme modes: follow system / always light / always dark. */
    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    /** Accent seed colors (Batch 3, C3): Telegram blue, green,
     *  purple, orange — stored as ARGB longs. */
    val SEED_COLORS = listOf(
        0xFF2AABEE, 0xFF43A047, 0xFF7E57C2, 0xFFFB8C00
    )
    val SEED_NAMES = listOf("电报蓝", "绿", "紫", "橙")

    private val _unreadCap = MutableStateFlow(999)
    val unreadCap: StateFlow<Int> = _unreadCap

    private val _keepAlive = MutableStateFlow(true)
    val keepAlive: StateFlow<Boolean> = _keepAlive

    private val _themeMode = MutableStateFlow(THEME_SYSTEM)
    val themeMode: StateFlow<Int> = _themeMode

    private val _seedIndex = MutableStateFlow(0)
    val seedIndex: StateFlow<Int> = _seedIndex

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getInt(KEY_UNREAD_CAP, 999)
        _unreadCap.value = if (saved in UNREAD_CAPS) saved else 999
        _keepAlive.value = prefs.getBoolean(KEY_KEEP_ALIVE, true)
        val mode = prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM)
        _themeMode.value =
            if (mode in THEME_SYSTEM..THEME_DARK) mode else THEME_SYSTEM
        val seed = prefs.getInt(KEY_SEED_INDEX, 0)
        _seedIndex.value = if (seed in SEED_COLORS.indices) seed else 0
    }

    fun setThemeMode(mode: Int) {
        if (mode !in THEME_SYSTEM..THEME_DARK) return
        _themeMode.value = mode
        persist { it.putInt(KEY_THEME_MODE, mode) }
    }

    fun setSeedIndex(index: Int) {
        if (index !in SEED_COLORS.indices) return
        _seedIndex.value = index
        persist { it.putInt(KEY_SEED_INDEX, index) }
    }

    private fun persist(block: (android.content.SharedPreferences.Editor) -> Unit) {
        val ctx = appContext ?: return
        val editor = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        block(editor)
        editor.apply()
    }

    fun setKeepAlive(on: Boolean) {
        _keepAlive.value = on
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_KEEP_ALIVE, on)
            .apply()
    }

    fun setUnreadCap(cap: Int) {
        if (cap !in UNREAD_CAPS) return
        _unreadCap.value = cap
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_UNREAD_CAP, cap)
            .apply()
    }

    /** Badge text for an unread count under the current cap. */
    fun badgeText(unreadCount: Int): String {
        val cap = _unreadCap.value
        return if (unreadCount > cap) "$cap+" else unreadCount.toString()
    }
}
