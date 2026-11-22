package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * Account-level housekeeping settings (v0.9.24, Q1 + Q4):
 * the account self-destruct period (getAccountTtl /
 * setAccountTtl, accountTtl{days}, 30–730 days per td_api.tl)
 * and the default auto-delete timer for new chats
 * (getDefaultMessageAutoDeleteTime /
 * setDefaultMessageAutoDeleteTime, messageAutoDeleteTime{time}
 * in seconds, 0 or a whole number of days). Both are read back
 * from the server and shown as read; a failed read leaves the
 * value unknown instead of guessing, and a failed write is
 * reported in plain language — the same honesty rules as
 * PrivacyStore.
 */
object AccountPrefsStore {

    /** TTL choices the settings page offers, days to label.
     *  The server accepts any 30–730 day value; a read-back
     *  that matches none of these is shown as a raw day count
     *  (see [ttlLabel]). */
    val TTL_OPTIONS: List<Pair<Int, String>> = listOf(
        30 to "1 个月",
        90 to "3 个月",
        180 to "6 个月",
        365 to "12 个月",
        730 to "24 个月"
    )

    /** Default auto-delete choices, seconds to label. */
    val AUTO_DELETE_OPTIONS: List<Pair<Int, String>> = listOf(
        0 to "关",
        86400 to "24 小时",
        604800 to "7 天",
        2678400 to "31 天"
    )

    private val _ttlDays = MutableStateFlow<Int?>(null)
    val ttlDays: StateFlow<Int?> = _ttlDays

    private val _autoDeleteSec = MutableStateFlow<Int?>(null)
    val autoDeleteSec: StateFlow<Int?> = _autoDeleteSec

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    fun load() {
        val ttlQuery = TdClient.request("getAccountTtl")
        TdRepository.sendRaw(ttlQuery) { response ->
            if (TdClient.isError(response)) {
                _loadError.value = "读取账号设置失败，检查网络后再试。"
            } else {
                _ttlDays.value = response.optInt("days", 0)
            }
        }
        val adQuery = TdClient.request("getDefaultMessageAutoDeleteTime")
        TdRepository.sendRaw(adQuery) { response ->
            if (TdClient.isError(response)) {
                _loadError.value = "读取账号设置失败，检查网络后再试。"
            } else {
                _autoDeleteSec.value = response.optInt("time", 0)
            }
        }
    }

    fun ttlLabel(days: Int?): String = when {
        days == null -> "读取中"
        else -> TTL_OPTIONS.firstOrNull { it.first == days }?.second
            ?: "$days 天"
    }

    fun autoDeleteLabel(seconds: Int?): String = when {
        seconds == null -> "读取中"
        else -> AUTO_DELETE_OPTIONS.firstOrNull { it.first == seconds }
            ?.second
            ?: if (seconds % 86400 == 0) {
                "${seconds / 86400} 天"
            } else {
                "$seconds 秒"
            }
    }

    private fun plainError(error: JSONObject, what: String): String {
        val message = error.optString("message")
        return when {
            message.contains("FLOOD_WAIT") -> "操作太频繁了，等一会儿再试。"
            else -> "$what 失败，再试一次。（错误码 ${error.optInt("code")}）"
        }
    }

    /** Callback receives null on success or a plain-language
     *  error sentence. On success the value is re-read from
     *  the server so the page shows what is actually stored. */
    fun setTtl(days: Int, cb: (String?) -> Unit) {
        val query = TdClient.request("setAccountTtl").apply {
            put("ttl", JSONObject().apply {
                put("@type", "accountTtl")
                put("days", days)
            })
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                cb(plainError(response, "设置自毁期限"))
            } else {
                load()
                cb(null)
            }
        }
    }

    fun setAutoDelete(seconds: Int, cb: (String?) -> Unit) {
        val query = TdClient.request("setDefaultMessageAutoDeleteTime")
            .apply {
                put("message_auto_delete_time", JSONObject().apply {
                    put("@type", "messageAutoDeleteTime")
                    put("time", seconds)
                })
            }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                cb(plainError(response, "设置默认自动删除"))
            } else {
                load()
                cb(null)
            }
        }
    }
}
