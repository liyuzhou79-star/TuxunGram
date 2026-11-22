package com.tuxungram.android.td

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** One network type's row on the auto-download settings page
 *  (v0.9.16, W1): the tier this app last applied for it, or
 *  "" when this app never set one (the server's own default
 *  is in effect — TDLib offers no getter for the effective
 *  per-type settings, only the presets, so the page must not
 *  pretend to know more than that). */
data class AutoDlTypeUi(
    val type: String,
    val appliedTier: String = ""
)

/**
 * Media auto-download settings (v0.9.16, W1):
 * getAutoDownloadSettingsPresets / setAutoDownloadSettings
 * (td_api.tl lines 17446 / 17449). The presets object carries
 * three ready-made autoDownloadSettings (low / medium / high);
 * writing one back for a network type is account-level server
 * state, shared with the official clients — change it here
 * and every client of the account follows.
 *
 * The page exposes the three user-meaningful network types
 * (mobile / mobile roaming / WiFi) with four choices each:
 * 关闭 (the low preset with is_auto_download_enabled flipped
 * off — the full object is written back so every other field
 * rides along verbatim) and the three presets themselves.
 * Which tier was applied per type is persisted locally (see
 * AutoDlTypeUi for why there is no server read-back).
 */
object AutoDownloadStore {

    const val TYPE_MOBILE = "networkTypeMobile"
    const val TYPE_ROAMING = "networkTypeMobileRoaming"
    const val TYPE_WIFI = "networkTypeWiFi"

    val TYPES = listOf(TYPE_MOBILE, TYPE_ROAMING, TYPE_WIFI)

    const val TIER_OFF = "off"
    const val TIER_LOW = "low"
    const val TIER_MEDIUM = "medium"
    const val TIER_HIGH = "high"

    private const val PREFS = "autodl"

    private var appContext: Context? = null

    private val _types = MutableStateFlow(
        TYPES.map { AutoDlTypeUi(type = it) }
    )
    val types: StateFlow<List<AutoDlTypeUi>> = _types

    /** Raw preset objects, kept verbatim for write-back. */
    @Volatile
    private var presets: JSONObject? = null

    private val _presetsLoaded = MutableStateFlow(false)
    val presetsLoaded: StateFlow<Boolean> = _presetsLoaded

    private val _presetsFailed = MutableStateFlow(false)
    val presetsFailed: StateFlow<Boolean> = _presetsFailed

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _types.value = TYPES.map { type ->
            AutoDlTypeUi(
                type = type,
                appliedTier = prefs.getString("tier_$type", "") ?: ""
            )
        }
    }

    fun loadPresets() {
        if (_presetsLoaded.value) return
        val query = TdClient.request("getAutoDownloadSettingsPresets")
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _presetsFailed.value = true
            } else {
                presets = response
                _presetsFailed.value = false
                _presetsLoaded.value = true
            }
        }
    }

    /** (photo, video, other) max auto-download sizes in bytes
     *  for a preset tier, for the page's per-tier summary;
     *  null while presets are unavailable. */
    fun tierLimits(tier: String): Triple<Long, Long, Long>? {
        val p = presets ?: return null
        val o = p.optJSONObject(tier) ?: return null
        return Triple(
            o.optLong("max_photo_file_size"),
            o.optLong("max_video_file_size"),
            o.optLong("max_other_file_size")
        )
    }

    /** Apply one tier to one network type. The choice is
     *  persisted locally only after the server accepts it, so
     *  the page never shows a tier the account does not have. */
    fun apply(type: String, tier: String) {
        val settings = buildSettings(tier) ?: run {
            _notice.value = "预设还没加载好，稍等一下再试。"
            return
        }
        val query = TdClient.request("setAutoDownloadSettings").apply {
            put("settings", settings)
            put("type", JSONObject().put("@type", type))
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _notice.value = "没保存上，检查网络后再试一次。"
            } else {
                _notice.value = null
                val ctx = appContext
                if (ctx != null) {
                    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putString("tier_$type", tier).apply()
                }
                _types.value = _types.value.map {
                    if (it.type == type) it.copy(appliedTier = tier)
                    else it
                }
            }
        }
    }

    private fun buildSettings(tier: String): JSONObject? {
        if (tier == TIER_OFF) {
            val low = presets?.optJSONObject(TIER_LOW)
            if (low != null) {
                // Copy the low preset and flip the master
                // switch; every other field rides along.
                return JSONObject(low.toString()).apply {
                    put("is_auto_download_enabled", false)
                }
            }
            // Presets not loaded yet: a complete disabled
            // object is still a valid autoDownloadSettings.
            return JSONObject().apply {
                put("@type", "autoDownloadSettings")
                put("is_auto_download_enabled", false)
                put("max_photo_file_size", 0)
                put("max_video_file_size", 0L)
                put("max_other_file_size", 0L)
                put("video_upload_bitrate", 0)
                put("preload_large_videos", false)
                put("preload_next_audio", false)
                put("preload_stories", false)
                put("use_less_data_for_calls", false)
            }
        }
        val preset = presets?.optJSONObject(tier) ?: return null
        // Verbatim copy (never mutate the cached preset).
        return JSONObject(preset.toString())
    }
}
