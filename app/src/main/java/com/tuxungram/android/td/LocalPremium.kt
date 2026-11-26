package com.tuxungram.android.td

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * Local premium (v0.9.63, H1) — pure on-device decoration,
 * the user's own idea and wording: a sticker after your own
 * name, a self-drawn badge, a personalised banner on the
 * 我的 page and premium-coloured identity icons. It exists
 * ONLY inside this app on this phone:
 *
 * - nothing here is ever sent to a server, put into a
 *   message, a profile field, a share or an export of chat
 *   content — the decorations are applied at render time,
 *   on this device, to the signed-in user's own name and
 *   avatar, and nowhere else;
 * - it unlocks no Premium feature and imitates none: every
 *   server-gated feature keeps behaving exactly as the
 *   server decides for this account;
 * - other people see the account exactly as before, and
 *   another device shows nothing of it;
 * - it is unrelated to a real Telegram Premium
 *   subscription: MeStore.isPremium carries the server
 *   state, and the two never interact.
 *
 * Storage follows the local pins / labels model: a
 * SharedPreferences file of our own, one JSON object per
 * account slot (chat- and user-facing identity only makes
 * sense inside one account), loaded once at start for the
 * foreground slot because switching accounts restarts the
 * process. Logout clears the slot's entry (the same Closed
 * hook that clears local pins and labels), and the whole
 * configuration rides the settings backup — the backup is
 * itself a local file the user carries by hand.
 */
object LocalPremium {
    private const val PREFS = "local_premium"

    /** The built-in name-sticker choices: plain emoji /
     *  symbol characters rendered as text next to the name
     *  (no image assets, so the package does not grow).
     *  The empty string means "no sticker". A sticker from
     *  an installed sticker pack was considered and left
     *  out: a pack sticker is a TDLib cache file that the
     *  system may delete at any time, which would leave a
     *  broken image next to the user's name everywhere —
     *  recorded in the checkpoint. */
    val STICKERS = listOf(
        "⭐", "👑", "💎", "🔥", "🌟", "✨",
        "⚡", "🎵", "🍀", "🦊", "🐱", "🌙",
        "☀️", "❄️", "🏆", "🎧"
    )

    /** Me-page banner styles: 0 = none (the plain header
     *  the page has always had); the rest are self-made
     *  two-colour gradients, light over dark, chosen so
     *  white header text stays readable on every one. */
    val BANNER_NAMES = listOf(
        "无", "星空紫", "落日橙", "海洋蓝", "森林绿", "玫瑰粉"
    )
    val BANNER_COLORS: List<Pair<Long, Long>> = listOf(
        0L to 0L,
        0xFF5B4BC4L to 0xFF9D6FE8L,
        0xFFD84315L to 0xFFF9A825L,
        0xFF1565C0L to 0xFF00ACC1L,
        0xFF2E7D32L to 0xFF7CB342L,
        0xFFAD1457L to 0xFFEF5350L
    )

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled

    private val _sticker = MutableStateFlow("")
    val sticker: StateFlow<String> = _sticker

    private val _badge = MutableStateFlow(false)
    val badge: StateFlow<Boolean> = _badge

    private val _banner = MutableStateFlow(0)
    val banner: StateFlow<Int> = _banner

    private val _iconStyle = MutableStateFlow(false)
    val iconStyle: StateFlow<Boolean> = _iconStyle

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var slot = 0

    private fun key() = "premium_$slot"

    fun init(context: Context) {
        appContext = context.applicationContext
        slot = AccountStore.currentSlot()
        apply(load())
    }

    private data class Config(
        val enabled: Boolean = false,
        val sticker: String = "",
        val badge: Boolean = false,
        val banner: Int = 0,
        val iconStyle: Boolean = false
    )

    private fun current() = Config(
        enabled = _enabled.value,
        sticker = _sticker.value,
        badge = _badge.value,
        banner = _banner.value,
        iconStyle = _iconStyle.value
    )

    private fun apply(config: Config) {
        _enabled.value = config.enabled
        _sticker.value = config.sticker
        _badge.value = config.badge
        _banner.value = config.banner
        _iconStyle.value = config.iconStyle
    }

    private fun load(): Config {
        val ctx = appContext ?: return Config()
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(), "") ?: ""
        if (raw.isEmpty()) return Config()
        return parse(raw) ?: Config()
    }

    /** Parse one stored / backed-up configuration object.
     *  Every field is range-checked here so a corrupt prefs
     *  value or a hostile backup can only ever produce a
     *  valid configuration or none at all. Shared by the
     *  prefs loader and the settings-backup importer. */
    private fun parse(raw: String): Config? {
        return try {
            val obj = JSONObject(raw)
            val sticker = obj.optString("sticker", "")
            if (sticker.isNotEmpty() && sticker !in STICKERS) {
                return null
            }
            val banner = obj.optInt("banner", 0)
            if (banner !in BANNER_COLORS.indices) return null
            Config(
                enabled = obj.optBoolean("enabled", false),
                sticker = sticker,
                badge = obj.optBoolean("badge", false),
                banner = banner,
                iconStyle = obj.optBoolean("icon_style", false)
            )
        } catch (t: Throwable) {
            null
        }
    }

    /** True when [raw] is a configuration object this
     *  store would accept (the settings-backup importer's
     *  staging check; the importer still applies through
     *  [replaceAll], which re-validates). */
    fun isValid(raw: String): Boolean = parse(raw) != null

    /** The configuration as one JSON object — the prefs
     *  storage form and the settings-backup section share
     *  it, so the two can never drift apart. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", _enabled.value)
        put("sticker", _sticker.value)
        put("badge", _badge.value)
        put("banner", _banner.value)
        put("icon_style", _iconStyle.value)
    }

    private fun persist() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(), toJson().toString())
            .apply()
    }

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        persist()
    }

    /** [value] must be "" (no sticker) or one of
     *  [STICKERS]; anything else is ignored, never stored. */
    fun setSticker(value: String) {
        if (value.isNotEmpty() && value !in STICKERS) return
        _sticker.value = value
        persist()
    }

    fun setBadge(on: Boolean) {
        _badge.value = on
        persist()
    }

    fun setBanner(index: Int) {
        if (index !in BANNER_COLORS.indices) return
        _banner.value = index
        persist()
    }

    fun setIconStyle(on: Boolean) {
        _iconStyle.value = on
        persist()
    }

    /** Replace the whole configuration (settings import).
     *  The importer has already validated the object with
     *  [parse]; a null parse leaves everything untouched. */
    fun replaceAll(raw: String): Boolean {
        val config = parse(raw) ?: return false
        apply(config)
        persist()
        return true
    }

    /** Drop this slot's whole configuration (logout): the
     *  decorations name the outgoing account, so they leave
     *  with it — the same rule as local pins and labels. */
    fun clear() {
        apply(Config())
        persist()
    }
}
