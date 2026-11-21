package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** One logged-in device/session, flattened from the tl
 *  `session` object (fields verified in td_api.tl). */
data class SessionEntry(
    val id: Long,
    val isCurrent: Boolean,
    val applicationName: String,
    val deviceModel: String,
    val platform: String,
    val systemVersion: String,
    val deviceTypeLabel: String,
    val ip: String,
    val location: String,
    val logInDate: Int,
    val lastActiveDate: Int,
    val isUnconfirmed: Boolean,
    val canAcceptSecretChats: Boolean,
    val canAcceptCalls: Boolean
)

/**
 * Session management (v0.6.0, E3): getActiveSessions lists
 * every device logged into this Telegram account;
 * terminateSession ends one, terminateAllOtherSessions ends
 * all but the current one. All three verified in td_api.tl.
 */
object SessionStore {
    private val _sessions = MutableStateFlow<List<SessionEntry>>(emptyList())
    val sessions: StateFlow<List<SessionEntry>> = _sessions

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    fun load() {
        _loading.value = true
        TdRepository.sendRaw(TdClient.request("getActiveSessions")) { response ->
            _loading.value = false
            if (TdClient.isError(response)) {
                _notice.value =
                    "读取设备列表失败（错误码 ${response.optInt("code")}）。"
                return@sendRaw
            }
            val arr = response.optJSONArray("sessions")
            val out = ArrayList<SessionEntry>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val s = arr.optJSONObject(i) ?: continue
                    out.add(parse(s))
                }
            }
            // Current device first, then most recently active.
            out.sortWith(
                compareByDescending<SessionEntry> { it.isCurrent }
                    .thenByDescending { it.lastActiveDate }
            )
            _sessions.value = out
        }
    }

    private fun parse(s: JSONObject): SessionEntry {
        val deviceType = s.optJSONObject("device_type")
            ?.optString("@type") ?: ""
        return SessionEntry(
            id = s.optLong("id"),
            isCurrent = s.optBoolean("is_current"),
            applicationName = s.optString("application_name"),
            deviceModel = s.optString("device_model"),
            platform = s.optString("platform"),
            systemVersion = s.optString("system_version"),
            deviceTypeLabel = deviceTypeLabel(deviceType),
            ip = s.optString("ip_address"),
            location = s.optString("location"),
            logInDate = s.optInt("log_in_date"),
            lastActiveDate = s.optInt("last_active_date"),
            isUnconfirmed = s.optBoolean("is_unconfirmed"),
            canAcceptSecretChats = s.optBoolean("can_accept_secret_chats"),
            canAcceptCalls = s.optBoolean("can_accept_calls")
        )
    }

    /** sessionDeviceType* -> a short human label; unknown
     *  types fall back to the raw suffix. */
    private fun deviceTypeLabel(type: String): String {
        val suffix = type.removePrefix("sessionDeviceType")
        return when (suffix) {
            "Android" -> "Android"
            "Apple" -> "Apple"
            "Ipad" -> "iPad"
            "Windows" -> "Windows"
            "Mac" -> "Mac"
            "Linux" -> "Linux"
            "Web" -> "网页版"
            "Chrome", "Brave", "Edge", "Firefox", "Opera",
            "Safari", "Vivaldi" -> "浏览器"
            else -> suffix
        }
    }

    fun terminate(sessionId: Long) {
        val query = TdClient.request("terminateSession").apply {
            put("session_id", sessionId)
        }
        TdRepository.sendRaw(query) { response ->
            _notice.value = if (TdClient.isError(response)) {
                "结束会话失败（错误码 ${response.optInt("code")}）。"
            } else {
                "已结束那个设备的会话。"
            }
            if (!TdClient.isError(response)) load()
        }
    }

    fun terminateOthers() {
        TdRepository.sendRaw(TdClient.request("terminateAllOtherSessions")) { response ->
            _notice.value = if (TdClient.isError(response)) {
                "操作失败（错误码 ${response.optInt("code")}）。"
            } else {
                "已退出全部其他设备。"
            }
            if (!TdClient.isError(response)) load()
        }
    }
}
