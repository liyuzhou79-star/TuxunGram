package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

enum class MsgKind { TEXT, PHOTO, OTHER }

data class MsgItem(
    val id: Long,
    val outgoing: Boolean,
    val dateSeconds: Int,
    val kind: MsgKind,
    val text: String,
    val senderName: String,
    val senderUserId: Long,
    val photoFileId: Int,
    val photoPath: String?,
    val photoWidth: Int,
    val photoHeight: Int,
    val sendingState: String
)

/**
 * State for the one chat currently open on screen: history paging,
 * live appends, send bookkeeping (pending/succeeded/failed via
 * TDLib's old_message_id reconciliation), photo downloads, read
 * marking, and a small user cache for sender names in groups.
 */
object MessageStore {
    private val _messages = MutableStateFlow<List<MsgItem>>(emptyList())
    val messages: StateFlow<List<MsgItem>> = _messages

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError

    @Volatile
    var currentChatId: Long = 0L
        private set

    @Volatile
    private var allHistoryLoaded = false

    @Volatile
    private var loadingHistory = false

    private val byId = ConcurrentHashMap<Long, MsgItem>()
    private val userNames = ConcurrentHashMap<Long, String>()
    private val userFetchPending = ConcurrentHashMap.newKeySet<Long>()

    @Volatile
    private var attached = false

    private fun attach() {
        if (attached) return
        attached = true
        TdRepository.addUpdateListener { update -> handleUpdate(update) }
    }

    fun open(chatId: Long) {
        attach()
        currentChatId = chatId
        byId.clear()
        _messages.value = emptyList()
        _sendError.value = null
        allHistoryLoaded = false
        TdRepository.sendRaw(TdClient.request("openChat").apply {
            put("chat_id", chatId)
        }) {}
        loadHistory(fromMessageId = 0L)
    }

    fun close() {
        val chatId = currentChatId
        if (chatId != 0L) {
            TdRepository.sendRaw(TdClient.request("closeChat").apply {
                put("chat_id", chatId)
            }) {}
        }
        currentChatId = 0L
        byId.clear()
        _messages.value = emptyList()
    }

    fun loadOlder() {
        if (allHistoryLoaded || loadingHistory) return
        val oldest = byId.keys.minOrNull() ?: return
        loadHistory(fromMessageId = oldest)
    }

    private fun loadHistory(fromMessageId: Long) {
        val chatId = currentChatId
        if (chatId == 0L || loadingHistory) return
        loadingHistory = true
        val query = TdClient.request("getChatHistory").apply {
            put("chat_id", chatId)
            put("from_message_id", fromMessageId)
            put("offset", 0)
            put("limit", 50)
            put("only_local", false)
        }
        TdRepository.sendRaw(query) { response ->
            loadingHistory = false
            if (currentChatId != chatId) return@sendRaw
            if (TdClient.isError(response)) return@sendRaw
            val arr = response.optJSONArray("messages") ?: JSONArray()
            if (arr.length() < 50) allHistoryLoaded = true
            var newestId = 0L
            for (i in 0 until arr.length()) {
                val msg = arr.optJSONObject(i) ?: continue
                val item = parseMessage(msg)
                byId[item.id] = item
                if (item.id > newestId) newestId = item.id
            }
            publish()
            if (fromMessageId == 0L && newestId != 0L) {
                markRead(chatId, newestId)
            }
        }
    }

    fun sendText(text: String) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val query = TdClient.request("sendMessage").apply {
            put("chat_id", chatId)
            put("input_message_content", JSONObject().apply {
                put("@type", "inputMessageText")
                put("text", formattedText(text))
                put("link_preview_options", JSONObject.NULL)
                put("clear_draft", true)
            })
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = sendErrorText(response)
            } else {
                val item = parseMessage(response)
                byId[item.id] = item
                publish()
            }
        }
    }

    fun sendPhoto(path: String, caption: String) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val query = TdClient.request("sendMessage").apply {
            put("chat_id", chatId)
            put("input_message_content", JSONObject().apply {
                put("@type", "inputMessagePhoto")
                put("photo", JSONObject().apply {
                    put("@type", "inputPhoto")
                    put("photo", JSONObject().apply {
                        put("@type", "inputFileLocal")
                        put("path", path)
                    })
                    put("thumbnail", JSONObject.NULL)
                    put("video", JSONObject.NULL)
                    put("added_sticker_file_ids", JSONArray())
                    put("width", 0)
                    put("height", 0)
                })
                put("caption", formattedText(caption))
                put("show_caption_above_media", false)
                put("self_destruct_type", JSONObject.NULL)
                put("has_spoiler", false)
            })
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = sendErrorText(response)
            } else {
                val item = parseMessage(response)
                byId[item.id] = item
                publish()
            }
        }
    }

    fun clearSendError() {
        _sendError.value = null
    }

    private fun sendErrorText(error: JSONObject): String {
        val message = error.optString("message")
        return when {
            message.contains("CHAT_WRITE_FORBIDDEN") ||
                message.contains("have no rights") ||
                message.contains("not enough rights") ->
                "你在这个对话里不能发言。"
            message.contains("FLOOD_WAIT") -> "发太快了，等一会儿再发。"
            else -> "发送失败，再试一次。（错误码 ${error.optInt("code")}）"
        }
    }

    private fun formattedText(text: String): JSONObject = JSONObject().apply {
        put("@type", "formattedText")
        put("text", text)
        put("entities", JSONArray())
    }

    private fun markRead(chatId: Long, messageId: Long) {
        val query = TdClient.request("viewMessages").apply {
            put("chat_id", chatId)
            put("message_ids", JSONArray().put(messageId))
            put("source", JSONObject().put("@type", "messageSourceChatHistory"))
            put("force_read", true)
        }
        TdRepository.sendRaw(query) {}
    }

    private fun handleUpdate(update: JSONObject) {
        when (update.optString("@type")) {
            "updateNewMessage" -> {
                val msg = update.optJSONObject("message") ?: return
                if (msg.optLong("chat_id") != currentChatId) return
                val item = parseMessage(msg)
                byId[item.id] = item
                publish()
                if (!item.outgoing) markRead(currentChatId, item.id)
            }
            "updateMessageSendSucceeded" -> {
                val msg = update.optJSONObject("message") ?: return
                if (msg.optLong("chat_id") != currentChatId) return
                val oldId = update.optLong("old_message_id")
                val item = parseMessage(msg)
                byId.remove(oldId)
                byId[item.id] = item
                publish()
            }
            "updateMessageSendFailed" -> {
                val msg = update.optJSONObject("message") ?: return
                if (msg.optLong("chat_id") != currentChatId) return
                val oldId = update.optLong("old_message_id")
                val existing = byId[oldId] ?: byId[msg.optLong("id")] ?: return
                byId[existing.id] = existing.copy(sendingState = "failed")
                publish()
            }
            "updateMessageContent" -> {
                val chatId = update.optLong("chat_id")
                if (chatId != currentChatId) return
                val messageId = update.optLong("message_id")
                val existing = byId[messageId] ?: return
                val content = update.optJSONObject("new_content") ?: return
                byId[messageId] = parseContent(existing, content)
                publish()
            }
            "updateFile" -> {
                val file = update.optJSONObject("file") ?: return
                val local = file.optJSONObject("local") ?: return
                if (!local.optBoolean("is_downloading_completed")) return
                val fileId = file.optInt("id")
                val path = local.optString("path")
                var changed = false
                for ((id, item) in byId) {
                    if (item.photoFileId == fileId && item.photoPath == null) {
                        byId[id] = item.copy(photoPath = path)
                        changed = true
                    }
                }
                if (changed) publish()
            }
            "updateUser" -> {
                val user = update.optJSONObject("user") ?: return
                val name = userNameOf(user)
                if (name.isNotEmpty()) {
                    userNames[user.optLong("id")] = name
                    publish()
                }
            }
        }
    }

    private fun userNameOf(user: JSONObject): String {
        val first = user.optString("first_name")
        val last = user.optString("last_name")
        return listOf(first, last).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun senderNameFor(sender: JSONObject?): String {
        if (sender == null) return ""
        return when (sender.optString("@type")) {
            "messageSenderUser" -> {
                val userId = sender.optLong("user_id")
                userNames[userId] ?: run {
                    fetchUser(userId)
                    ""
                }
            }
            "messageSenderChat" -> ""
            else -> ""
        }
    }

    private fun fetchUser(userId: Long) {
        if (!userFetchPending.add(userId)) return
        val query = TdClient.request("getUser").apply { put("user_id", userId) }
        TdRepository.sendRaw(query) { response ->
            userFetchPending.remove(userId)
            if (TdClient.isError(response)) return@sendRaw
            val name = userNameOf(response)
            if (name.isNotEmpty()) {
                userNames[userId] = name
                publish()
            }
        }
    }

    private fun parseMessage(msg: JSONObject): MsgItem {
        val base = MsgItem(
            id = msg.optLong("id"),
            outgoing = msg.optBoolean("is_outgoing"),
            dateSeconds = msg.optInt("date"),
            kind = MsgKind.OTHER,
            text = "",
            senderName = senderNameFor(msg.optJSONObject("sender_id")),
            senderUserId = msg.optJSONObject("sender_id")
                ?.takeIf { it.optString("@type") == "messageSenderUser" }
                ?.optLong("user_id") ?: 0L,
            photoFileId = 0,
            photoPath = null,
            photoWidth = 0,
            photoHeight = 0,
            sendingState = when (
                msg.optJSONObject("sending_state")?.optString("@type")
            ) {
                "messageSendingStatePending" -> "pending"
                "messageSendingStateFailed" -> "failed"
                else -> ""
            }
        )
        val content = msg.optJSONObject("content") ?: return base
        return parseContent(base, content)
    }

    private fun parseContent(base: MsgItem, content: JSONObject): MsgItem {
        return when (content.optString("@type")) {
            "messageText" -> base.copy(
                kind = MsgKind.TEXT,
                text = content.optJSONObject("text")?.optString("text") ?: ""
            )
            "messagePhoto" -> {
                val photo = content.optJSONObject("photo")
                val size = pickPhotoSize(photo?.optJSONArray("sizes"))
                val file = size?.optJSONObject("photo")
                val local = file?.optJSONObject("local")
                val downloaded = local?.optBoolean("is_downloading_completed") == true
                val fileId = file?.optInt("id", 0) ?: 0
                if (fileId != 0 && !downloaded) {
                    TdRepository.sendRaw(TdClient.request("downloadFile").apply {
                        put("file_id", fileId)
                        put("priority", 16)
                        put("offset", 0L)
                        put("limit", 0L)
                        put("synchronous", false)
                    }) {}
                }
                base.copy(
                    kind = MsgKind.PHOTO,
                    text = content.optJSONObject("caption")?.optString("text") ?: "",
                    photoFileId = fileId,
                    photoPath = if (downloaded) local?.optString("path") else null,
                    photoWidth = size?.optInt("width", 0) ?: 0,
                    photoHeight = size?.optInt("height", 0) ?: 0
                )
            }
            else -> base.copy(kind = MsgKind.OTHER, text = "")
        }
    }

    /** Prefer a mid-size rendition (~720 px wide) for bubbles;
     *  fall back to the largest available. */
    private fun pickPhotoSize(sizes: JSONArray?): JSONObject? {
        if (sizes == null || sizes.length() == 0) return null
        var best: JSONObject? = null
        var largest: JSONObject? = null
        for (i in 0 until sizes.length()) {
            val s = sizes.optJSONObject(i) ?: continue
            val w = s.optInt("width")
            if (largest == null || w > largest.optInt("width")) largest = s
            if (w in 1..900 && (best == null || w > best.optInt("width"))) best = s
        }
        return best ?: largest
    }

    private fun publish() {
        _messages.value = byId.values.sortedBy { it.id }.map { item ->
            // Backfill sender names that arrived after first parse.
            val name = if (item.senderUserId != 0L) {
                userNames[item.senderUserId]
            } else {
                null
            }
            if (name != null && name != item.senderName) {
                item.copy(senderName = name)
            } else {
                item
            }
        }
    }
}
