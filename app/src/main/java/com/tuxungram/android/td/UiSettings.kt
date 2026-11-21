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
    private const val KEY_CHAT_BG = "chat_bg"
    private const val KEY_BUBBLE_STYLE = "bubble_style"
    private const val KEY_FONT_SIZE = "font_size"

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

    /** Chat wallpapers (v0.7.0, F3): solid colors in the theme's
     *  family, each with a light and a dark variant so the choice
     *  stays sensible in both modes. Index 0 = 默认 (no wallpaper,
     *  the theme surface shows through). */
    val CHAT_BG_NAMES = listOf("默认", "薄雾蓝", "米纸", "浅绿", "粉樱", "石墨")
    val CHAT_BG_COLORS: List<Pair<Long, Long>> = listOf(
        0L to 0L,
        0xFFDDEBF5L to 0xFF1B2733L,
        0xFFF5EFE0L to 0xFF2A251CL,
        0xFFE3F0DCL to 0xFF1E2B1AL,
        0xFFF9E4ECL to 0xFF2E1E26L,
        0xFFE8EAEDL to 0xFF14181DL
    )

    /** Bubble styles (v0.7.0, F3): 现代 = generous rounding,
     *  经典 = the tighter rounding older clients used. */
    const val BUBBLE_MODERN = 0
    const val BUBBLE_CLASSIC = 1
    val BUBBLE_NAMES = listOf("现代", "经典")

    /** Font sizes (v0.7.0, F3): applied as a density fontScale
     *  over the whole UI, so message text AND list text scale
     *  together, like in mature clients. */
    val FONT_SCALES = listOf(0.85f, 1.0f, 1.15f, 1.3f)
    val FONT_NAMES = listOf("小", "标准", "大", "特大")

    private val _unreadCap = MutableStateFlow(999)
    val unreadCap: StateFlow<Int> = _unreadCap

    private val _keepAlive = MutableStateFlow(true)
    val keepAlive: StateFlow<Boolean> = _keepAlive

    private val _themeMode = MutableStateFlow(THEME_SYSTEM)
    val themeMode: StateFlow<Int> = _themeMode

    private val _seedIndex = MutableStateFlow(0)
    val seedIndex: StateFlow<Int> = _seedIndex

    private val _chatBg = MutableStateFlow(0)
    val chatBg: StateFlow<Int> = _chatBg

    private val _bubbleStyle = MutableStateFlow(BUBBLE_MODERN)
    val bubbleStyle: StateFlow<Int> = _bubbleStyle

    private val _fontSize = MutableStateFlow(1)
    val fontSize: StateFlow<Int> = _fontSize

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
        val bg = prefs.getInt(KEY_CHAT_BG, 0)
        _chatBg.value = if (bg in CHAT_BG_COLORS.indices) bg else 0
        val bubble = prefs.getInt(KEY_BUBBLE_STYLE, BUBBLE_MODERN)
        _bubbleStyle.value =
            if (bubble in BUBBLE_MODERN..BUBBLE_CLASSIC) bubble else BUBBLE_MODERN
        val font = prefs.getInt(KEY_FONT_SIZE, 1)
        _fontSize.value = if (font in FONT_SCALES.indices) font else 1
    }

    fun setChatBg(index: Int) {
        if (index !in CHAT_BG_COLORS.indices) return
        _chatBg.value = index
        persist { it.putInt(KEY_CHAT_BG, index) }
    }

    fun setBubbleStyle(style: Int) {
        if (style !in BUBBLE_MODERN..BUBBLE_CLASSIC) return
        _bubbleStyle.value = style
        persist { it.putInt(KEY_BUBBLE_STYLE, style) }
    }

    fun setFontSize(index: Int) {
        if (index !in FONT_SCALES.indices) return
        _fontSize.value = index
        persist { it.putInt(KEY_FONT_SIZE, index) }
    }

    /** ARGB of the chosen chat wallpaper for the current mode;
     *  null = 默认 (let the theme surface show). */
    fun chatBackgroundArgb(dark: Boolean): Long? {
        val index = _chatBg.value
        if (index == 0) return null
        val pair = CHAT_BG_COLORS.getOrNull(index) ?: return null
        return if (dark) pair.second else pair.first
    }

    /** Corner radius (dp) of message bubbles under the chosen
     *  style. */
    fun bubbleCornerDp(): Int =
        if (_bubbleStyle.value == BUBBLE_CLASSIC) 8 else 18

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
