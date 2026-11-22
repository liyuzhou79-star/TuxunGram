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
    private const val KEY_ACCENT_MODE = "accent_mode"
    private const val KEY_CHAT_BG = "chat_bg"
    private const val KEY_CHAT_BG_IMAGE = "chat_bg_image"
    private const val KEY_CHAT_BG_PER_CHAT = "chat_bg_per_chat"
    private const val KEY_BUBBLE_STYLE = "bubble_style"
    private const val KEY_FONT_SIZE = "font_size"
    private const val KEY_DUCK_BAR = "duck_bar"
    private const val KEY_LABEL_DOTS = "label_dots"
    private const val KEY_SOUND_FX = "sound_fx"
    private const val KEY_VOICE_SPEED = "voice_speed"
    private const val KEY_SEEN_VERSION = "seen_version"

    val UNREAD_CAPS = listOf(99, 999, 9999)

    /** Theme modes: follow system / always light / always dark. */
    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    /** Accent seed colors (Batch 3, C3): official blue, green,
     *  purple, orange — stored as ARGB longs. The first entry
     *  became the official accent in v0.9.17 (chat_messagePanelSend
     *  of the official day theme). */
    val SEED_COLORS = listOf(
        0xFF229AF0, 0xFF43A047, 0xFF7E57C2, 0xFFFB8C00
    )
    val SEED_NAMES = listOf("官方蓝", "绿", "紫", "橙")

    /** Appearance presets (v0.9.17, X1): OFFICIAL = the full
     *  official-style palette as one preset (the default);
     *  CUSTOM = the C3 accent-seed behaviour. Migration: a
     *  stored non-default seed (someone deliberately picked
     *  green/purple/orange under C3) keeps CUSTOM; everyone
     *  else lands on OFFICIAL. */
    const val ACCENT_OFFICIAL = 0
    const val ACCENT_CUSTOM = 1

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

    /** In-app message sounds (v0.9.29, V1): the send /
     *  arrival / recording tones SoundFx plays, on by
     *  default like in the official client. */
    private val _soundFx = MutableStateFlow(true)
    val soundFx: StateFlow<Boolean> = _soundFx

    private val _themeMode = MutableStateFlow(THEME_SYSTEM)
    val themeMode: StateFlow<Int> = _themeMode

    private val _seedIndex = MutableStateFlow(0)
    val seedIndex: StateFlow<Int> = _seedIndex

    private val _accentMode = MutableStateFlow(ACCENT_OFFICIAL)
    val accentMode: StateFlow<Int> = _accentMode

    private val _chatBg = MutableStateFlow(0)
    val chatBg: StateFlow<Int> = _chatBg

    /** Custom chat wallpaper image (v0.9.4, N4): the local
     *  file path of the one global wallpaper picture, copied
     *  into app storage when picked. Empty = no picture; the
     *  solid chat background above applies instead. When a
     *  path is set it wins over the solid colors — the two
     *  are alternatives, never blended. */
    private val _chatBgImage = MutableStateFlow("")
    val chatBgImage: StateFlow<String> = _chatBgImage

    /** Per-chat wallpaper overrides (v0.9.16, W5): chat id
     *  -> local picture path, persisted as one JSON object.
     *  An override wins over the global picture and the
     *  solid colors for that chat only. It takes effect
     *  only while its file still exists — a settings backup
     *  carries these paths, not the pictures, so a backup
     *  restored on another phone leaves dangling paths that
     *  must fall back to the global wallpaper instead of a
     *  blank background (see effectiveWallpaperImage). */
    private val _chatWallpapers =
        MutableStateFlow<Map<Long, String>>(emptyMap())
    val chatWallpapers: StateFlow<Map<Long, String>> = _chatWallpapers

    private val _bubbleStyle = MutableStateFlow(BUBBLE_MODERN)
    val bubbleStyle: StateFlow<Int> = _bubbleStyle

    private val _fontSize = MutableStateFlow(1)
    val fontSize: StateFlow<Int> = _fontSize

    /** Bottom navigation bar (the "Duck bar", v0.9.19, Z5):
     *  the app's own bottom tab bar, on by default. Turning
     *  it off leaves the home level to the official drawer
     *  navigation alone — every tab's destination stays
     *  reachable from the drawer (contacts / settings items,
     *  the header opens 我的）. */
    private val _duckBar = MutableStateFlow(true)
    val duckBar: StateFlow<Boolean> = _duckBar

    /** Label color dots on chat-list rows (v0.9.28, U4):
     *  when on, a chat carrying local labels shows up to
     *  three small color dots on its row. Off by default —
     *  the list stays exactly as it was unless asked. */
    private val _labelDots = MutableStateFlow(false)
    val labelDots: StateFlow<Boolean> = _labelDots

    /** Voice-message playback speed (v0.9.2, L5): one global
     *  choice (1x / 1.5x / 2x), remembered across messages and
     *  restarts, like the mature clients do it. */
    private val _voiceSpeed = MutableStateFlow(1f)
    val voiceSpeed: StateFlow<Float> = _voiceSpeed

    val VOICE_SPEEDS = listOf(1f, 1.5f, 2f)

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getInt(KEY_UNREAD_CAP, 999)
        _unreadCap.value = if (saved in UNREAD_CAPS) saved else 999
        _keepAlive.value = prefs.getBoolean(KEY_KEEP_ALIVE, true)
        _soundFx.value = prefs.getBoolean(KEY_SOUND_FX, true)
        val mode = prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM)
        _themeMode.value =
            if (mode in THEME_SYSTEM..THEME_DARK) mode else THEME_SYSTEM
        val seed = prefs.getInt(KEY_SEED_INDEX, 0)
        _seedIndex.value = if (seed in SEED_COLORS.indices) seed else 0
        _accentMode.value = if (prefs.contains(KEY_ACCENT_MODE)) {
            val m = prefs.getInt(KEY_ACCENT_MODE, ACCENT_OFFICIAL)
            if (m in ACCENT_OFFICIAL..ACCENT_CUSTOM) m else ACCENT_OFFICIAL
        } else if (_seedIndex.value != 0) {
            // C3 migration (v0.9.17): a deliberately chosen
            // non-default accent stays in charge.
            ACCENT_CUSTOM
        } else {
            ACCENT_OFFICIAL
        }
        val bg = prefs.getInt(KEY_CHAT_BG, 0)
        _chatBg.value = if (bg in CHAT_BG_COLORS.indices) bg else 0
        _chatBgImage.value = prefs.getString(KEY_CHAT_BG_IMAGE, "") ?: ""
        _chatWallpapers.value = parseChatWallpapers(
            prefs.getString(KEY_CHAT_BG_PER_CHAT, "") ?: ""
        )
        val bubble = prefs.getInt(KEY_BUBBLE_STYLE, BUBBLE_MODERN)
        _bubbleStyle.value =
            if (bubble in BUBBLE_MODERN..BUBBLE_CLASSIC) bubble else BUBBLE_MODERN
        val font = prefs.getInt(KEY_FONT_SIZE, 1)
        _fontSize.value = if (font in FONT_SCALES.indices) font else 1
        _duckBar.value = prefs.getBoolean(KEY_DUCK_BAR, true)
        _labelDots.value = prefs.getBoolean(KEY_LABEL_DOTS, false)
        val speed = prefs.getFloat(KEY_VOICE_SPEED, 1f)
        _voiceSpeed.value = if (speed in VOICE_SPEEDS) speed else 1f
    }

    fun setVoiceSpeed(speed: Float) {
        if (speed !in VOICE_SPEEDS) return
        _voiceSpeed.value = speed
        persist { it.putFloat(KEY_VOICE_SPEED, speed) }
    }

    fun setChatBg(index: Int) {
        if (index !in CHAT_BG_COLORS.indices) return
        _chatBg.value = index
        persist { it.putInt(KEY_CHAT_BG, index) }
    }

    /** Set (or, with an empty path, clear) the custom chat
     *  wallpaper picture (v0.9.4, N4). */
    fun setChatBgImage(path: String) {
        _chatBgImage.value = path
        persist { it.putString(KEY_CHAT_BG_IMAGE, path) }
    }

    /** The wallpaper picture one chat actually shows
     *  (v0.9.16, W5): its override when one is set and the
     *  file is still there, else the global picture
     *  ("" = no picture; the solid colors apply). */
    fun effectiveWallpaperImage(chatId: Long): String {
        val override = _chatWallpapers.value[chatId]
        if (override != null && java.io.File(override).exists()) {
            return override
        }
        return _chatBgImage.value
    }

    fun setChatWallpaper(chatId: Long, path: String) {
        val next = _chatWallpapers.value + (chatId to path)
        _chatWallpapers.value = next
        persistChatWallpapers(next)
    }

    fun clearChatWallpaper(chatId: Long) {
        if (!_chatWallpapers.value.containsKey(chatId)) return
        val next = _chatWallpapers.value - chatId
        _chatWallpapers.value = next
        persistChatWallpapers(next)
    }

    /** Replace the whole override map (settings import,
     *  v0.9.16, W5). Entries whose files do not exist on
     *  this phone stay in the map but never take effect —
     *  effectiveWallpaperImage checks existence per chat. */
    fun replaceChatWallpapers(map: Map<Long, String>) {
        _chatWallpapers.value = map
        persistChatWallpapers(map)
    }

    private fun persistChatWallpapers(map: Map<Long, String>) {
        val obj = org.json.JSONObject()
        for ((id, path) in map) obj.put(id.toString(), path)
        persist { it.putString(KEY_CHAT_BG_PER_CHAT, obj.toString()) }
    }

    private fun parseChatWallpapers(raw: String): Map<Long, String> {
        if (raw.isEmpty()) return emptyMap()
        return try {
            val obj = org.json.JSONObject(raw)
            val out = HashMap<Long, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val id = key.toLongOrNull() ?: continue
                val path = obj.optString(key)
                if (path.isNotEmpty()) out[id] = path
            }
            out
        } catch (t: Throwable) {
            emptyMap()
        }
    }

    fun setBubbleStyle(style: Int) {
        if (style !in BUBBLE_MODERN..BUBBLE_CLASSIC) return
        _bubbleStyle.value = style
        persist { it.putInt(KEY_BUBBLE_STYLE, style) }
    }

    fun setDuckBar(on: Boolean) {
        _duckBar.value = on
        persist { it.putBoolean(KEY_DUCK_BAR, on) }
    }

    fun setLabelDots(on: Boolean) {
        _labelDots.value = on
        persist { it.putBoolean(KEY_LABEL_DOTS, on) }
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

    fun setAccentMode(mode: Int) {
        if (mode !in ACCENT_OFFICIAL..ACCENT_CUSTOM) return
        _accentMode.value = mode
        persist { it.putInt(KEY_ACCENT_MODE, mode) }
    }

    private fun persist(block: (android.content.SharedPreferences.Editor) -> Unit) {
        val ctx = appContext ?: return
        val editor = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        block(editor)
        editor.apply()
    }

    fun setSoundFx(on: Boolean) {
        _soundFx.value = on
        persist { it.putBoolean(KEY_SOUND_FX, on) }
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

    /** The newest version whose guide / what's-new page the
     *  user has already seen (v0.9.6, P2). Empty = never (a
     *  first install): the full onboarding shows. A version
     *  different from the running one = an update happened:
     *  the what's-new page shows once instead. */
    fun seenVersion(): String {
        val ctx = appContext ?: return ""
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SEEN_VERSION, "") ?: ""
    }

    fun markSeenVersion(version: String) {
        persist { it.putString(KEY_SEEN_VERSION, version) }
    }

    /** Badge text for an unread count under the current cap. */
    fun badgeText(unreadCount: Int): String {
        val cap = _unreadCap.value
        return if (unreadCount > cap) "$cap+" else unreadCount.toString()
    }
}
