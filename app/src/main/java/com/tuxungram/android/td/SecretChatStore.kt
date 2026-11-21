package com.tuxungram.android.td

import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** Flattened secret-chat state for the UI (v0.6.0, E1).
 *  [state] is "pending" / "ready" / "closed" / "unknown",
 *  mapped from secretChatState* in td_api.tl. */
data class SecretChatInfo(
    val chatId: Long,
    val secretChatId: Int,
    val userId: Long,
    val state: String,
    /** First 32 bytes of secretChat.key_hash as hex — the
     *  fingerprint users compare out-of-band (the tl documents
     *  exactly this rendering as the alternative to the 12x12
     *  pixel identicon). Empty until the key exists. */
    val keyHashHex: String,
    val layer: Int
)

/**
 * Secret chats (end-to-end encrypted, device-only). Interfaces
 * verified against td_api.tl: createNewSecretChat(user_id) ->
 * Chat, getSecretChat(secret_chat_id) -> secretChat (state /
 * key_hash / layer), updateSecretChat pushes state changes,
 * closeSecretChat(secret_chat_id), and the self-destruct timer
 * is the chat-wide setChatMessageAutoDeleteTime — the tl has
 * no per-secret-chat timer function; for secret chats this is
 * the call that sets the encrypted chat's TTL.
 *
 * Incoming secret chats need no accept step: with
 * use_secret_chats on, TDLib creates the chat and delivers it
 * via updateNewChat like any other chat.
 *
 * Honest protocol limits (also worded so in the UI): secret
 * chat messages live only on the two participating devices —
 * no cloud copy, no history sync to a new device, nothing to
 * back up. That is the protocol's design, not an app gap.
 */
object SecretChatStore {
    private val _info = MutableStateFlow<SecretChatInfo?>(null)
    val info: StateFlow<SecretChatInfo?> = _info

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    @Volatile
    private var attached = false

    @Volatile
    private var watchedChatId = 0L

    private fun attach() {
        if (attached) return
        attached = true
        TdRepository.addUpdateListener { update ->
            if (update.optString("@type") != "updateSecretChat") {
                return@addUpdateListener
            }
            val sc = update.optJSONObject("secret_chat")
                ?: return@addUpdateListener
            val current = _info.value ?: return@addUpdateListener
            if (current.secretChatId == sc.optInt("id")) {
                _info.value = parse(current.chatId, sc)
            }
        }
    }

    /** Create a secret chat with [userId]; [onCreated] receives
     *  the new chat id so the UI can navigate into it. */
    fun create(userId: Long, onCreated: (Long) -> Unit) {
        val query = TdClient.request("createNewSecretChat").apply {
            put("user_id", userId)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                TdRepository.reportError(
                    response.optInt("code") to response.optString("message")
                )
            } else {
                onCreated(response.optLong("id"))
            }
        }
    }

    /** Load (and keep watching) the secret-chat state behind a
     *  chat id. No-ops for non-secret chats. */
    fun loadForChat(chatId: Long) {
        attach()
        watchedChatId = chatId
        _info.value = null
        _notice.value = null
        val query = TdClient.request("getChat").apply {
            put("chat_id", chatId)
        }
        TdRepository.sendRaw(query) { chat ->
            if (TdClient.isError(chat) || watchedChatId != chatId) {
                return@sendRaw
            }
            val type = chat.optJSONObject("type") ?: return@sendRaw
            if (type.optString("@type") != "chatTypeSecret") return@sendRaw
            val secretChatId = type.optInt("secret_chat_id")
            val inner = TdClient.request("getSecretChat").apply {
                put("secret_chat_id", secretChatId)
            }
            TdRepository.sendRaw(inner) { sc ->
                if (TdClient.isError(sc) || watchedChatId != chatId) {
                    return@sendRaw
                }
                _info.value = parse(chatId, sc)
            }
        }
    }

    private fun parse(chatId: Long, sc: JSONObject): SecretChatInfo {
        val state = when (sc.optJSONObject("state")?.optString("@type")) {
            "secretChatStatePending" -> "pending"
            "secretChatStateReady" -> "ready"
            "secretChatStateClosed" -> "closed"
            else -> "unknown"
        }
        return SecretChatInfo(
            chatId = chatId,
            secretChatId = sc.optInt("id"),
            userId = sc.optLong("user_id"),
            state = state,
            keyHashHex = keyHashHex(sc),
            layer = sc.optInt("layer")
        )
    }

    /** key_hash crosses the JSON interface as a base64 string
     *  (TDLib encodes every bytes field that way); an array of
     *  byte values is handled too, just in case. */
    private fun keyHashHex(sc: JSONObject): String {
        val bytes: ByteArray = when (val raw = sc.opt("key_hash")) {
            is String -> try {
                Base64.decode(raw, Base64.DEFAULT)
            } catch (t: Throwable) {
                ByteArray(0)
            }
            is org.json.JSONArray -> ByteArray(raw.length()) { i ->
                raw.optInt(i).toByte()
            }
            else -> ByteArray(0)
        }
        val out = StringBuilder()
        val n = minOf(32, bytes.size)
        for (i in 0 until n) {
            out.append(String.format("%02x", bytes[i].toInt() and 0xFF))
        }
        return out.toString()
    }

    /** Self-destruct timer for the whole secret chat
     *  (0 = off), via setChatMessageAutoDeleteTime. */
    fun setSelfDestruct(chatId: Long, seconds: Int) {
        val query = TdClient.request("setChatMessageAutoDeleteTime").apply {
            put("chat_id", chatId)
            put("message_auto_delete_time", seconds)
        }
        TdRepository.sendRaw(query) { response ->
            _notice.value = if (TdClient.isError(response)) {
                "设置失败（错误码 ${response.optInt("code")}）。"
            } else if (seconds == 0) {
                "阅后即焚已关闭。"
            } else {
                "阅后即焚已设为 ${ttlLabel(seconds)}。"
            }
            if (!TdClient.isError(response)) ProfileStore.load(chatId)
        }
    }

    /** Close the secret chat for good (both sides see it end). */
    fun close(chatId: Long, secretChatId: Int) {
        val query = TdClient.request("closeSecretChat").apply {
            put("secret_chat_id", secretChatId)
        }
        TdRepository.sendRaw(query) { response ->
            _notice.value = if (TdClient.isError(response)) {
                "关闭失败（错误码 ${response.optInt("code")}）。"
            } else {
                "秘密聊天已关闭。"
            }
            if (!TdClient.isError(response)) loadForChat(chatId)
        }
    }

    val TTL_OPTIONS = listOf(0, 5, 10, 30, 60, 3600, 86400, 604800)

    fun ttlLabel(seconds: Int): String = when (seconds) {
        0 -> "关闭"
        5 -> "5 秒"
        10 -> "10 秒"
        30 -> "30 秒"
        60 -> "1 分钟"
        3600 -> "1 小时"
        86400 -> "1 天"
        604800 -> "1 周"
        else -> "$seconds 秒"
    }
}
