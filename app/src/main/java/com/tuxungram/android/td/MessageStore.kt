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
    val sendingState: String,
    val replyToId: Long = 0L,
    val replyChatId: Long = 0L,
    val replySummary: String = ""
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

    /** The message the next send will reply to, if the user picked
     *  "reply" from the long-press menu. */
    private val _replyTarget = MutableStateFlow<MsgItem?>(null)
    val replyTarget: StateFlow<MsgItem?> = _replyTarget

    private val replyFetchPending = ConcurrentHashMap.newKeySet<Long>()

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
        _replyTarget.value = null
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

    fun setReplyTarget(item: MsgItem?) {
        _replyTarget.value = item
    }

    /** sendMessage.reply_to for the current reply target, if any.
     *  Signature verified in td_api.tl: inputMessageReplyToMessage
     *  carries message_id plus optional quote fields. */
    private fun replyToJson(): JSONObject? {
        val target = _replyTarget.value ?: return null
        return JSONObject().apply {
            put("@type", "inputMessageReplyToMessage")
            put("message_id", target.id)
            put("quote", JSONObject.NULL)
            put("checklist_task_id", 0)
            put("poll_option_id", "")
        }
    }

    fun sendText(text: String) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val replyTo = replyToJson()
        _replyTarget.value = null
        val query = TdClient.request("sendMessage").apply {
            put("chat_id", chatId)
            if (replyTo != null) put("reply_to", replyTo)
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
        val replyTo = replyToJson()
        _replyTarget.value = null
        val query = TdClient.request("sendMessage").apply {
            put("chat_id", chatId)
            if (replyTo != null) put("reply_to", replyTo)
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

    /** Edit one of my own text messages (editMessageText, verified
     *  in td_api.tl). The bubble updates when TDLib pushes
     *  updateMessageContent back. */
    fun editText(messageId: Long, newText: String) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val query = TdClient.request("editMessageText").apply {
            put("chat_id", chatId)
            put("message_id", messageId)
            put("reply_markup", JSONObject.NULL)
            put("input_message_content", JSONObject().apply {
                put("@type", "inputMessageText")
                put("text", formattedText(newText))
                put("link_preview_options", JSONObject.NULL)
                put("clear_draft", false)
            })
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = sendErrorText(response)
            }
        }
    }

    /** Delete a message. Own messages are revoked for everyone;
     *  other people's messages can only be removed locally
     *  (revoke = false). The bubble disappears when TDLib pushes
     *  updateDeleteMessages. */
    fun deleteMessage(item: MsgItem) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val query = TdClient.request("deleteMessages").apply {
            put("chat_id", chatId)
            put("message_ids", JSONArray().put(item.id))
            put("revoke", item.outgoing)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = sendErrorText(response)
            }
        }
    }

    /** Forward one message to another chat (forwardMessages,
     *  verified in td_api.tl). Independent of which chat is open,
     *  so the chat-list picker can call it after this store closed. */
    fun forwardMessage(
        fromChatId: Long,
        messageId: Long,
        toChatId: Long,
        onResult: (String?) -> Unit
    ) {
        val query = TdClient.request("forwardMessages").apply {
            put("chat_id", toChatId)
            put("topic_id", JSONObject.NULL)
            put("from_chat_id", fromChatId)
            put("message_ids", JSONArray().put(messageId))
            put("options", JSONObject.NULL)
            put("send_copy", false)
            put("remove_caption", false)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                onResult(sendErrorText(response))
            } else {
                onResult(null)
            }
        }
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
            },
            replyToId = replyTargetId(msg),
            replyChatId = replyTargetChatId(msg),
            replySummary = replySummaryOf(msg)
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

    private fun replyTargetId(msg: JSONObject): Long {
        val replyTo = msg.optJSONObject("reply_to") ?: return 0L
        if (replyTo.optString("@type") != "messageReplyToMessage") return 0L
        return replyTo.optLong("message_id")
    }

    private fun replyTargetChatId(msg: JSONObject): Long {
        val replyTo = msg.optJSONObject("reply_to") ?: return 0L
        if (replyTo.optString("@type") != "messageReplyToMessage") return 0L
        return replyTo.optLong("chat_id")
    }

    /** One-line summary of the replied-to message. TDLib embeds the
     *  replied content in messageReplyToMessage.content when it is
     *  loaded; otherwise fetch the message once and patch every
     *  item that replies to it. */
    private fun replySummaryOf(msg: JSONObject): String {
        val replyTo = msg.optJSONObject("reply_to") ?: return ""
        if (replyTo.optString("@type") != "messageReplyToMessage") return ""
        val embedded = replyTo.optJSONObject("content")
        if (embedded != null) return summarizeContent(embedded)
        val targetId = replyTo.optLong("message_id")
        val targetChat = replyTo.optLong("chat_id")
        if (targetId != 0L) fetchReplySummary(targetChat, targetId)
        return ""
    }

    private fun fetchReplySummary(chatId: Long, messageId: Long) {
        if (!replyFetchPending.add(messageId)) return
        val query = TdClient.request("getMessage").apply {
            put("chat_id", chatId)
            put("message_id", messageId)
        }
        TdRepository.sendRaw(query) { response ->
            replyFetchPending.remove(messageId)
            if (TdClient.isError(response)) return@sendRaw
            val content = response.optJSONObject("content") ?: return@sendRaw
            val summary = summarizeContent(content)
            if (summary.isEmpty()) return@sendRaw
            var changed = false
            for ((id, item) in byId) {
                if (item.replyToId == messageId && item.replySummary.isEmpty()) {
                    byId[id] = item.copy(replySummary = summary)
                    changed = true
                }
            }
            if (changed) publish()
        }
    }

    /** Short one-liner for a message content object, used for reply
     *  quotes and (later) other compact renderings. */
    fun summarizeContent(content: JSONObject): String {
        return when (content.optString("@type")) {
            "messageText" ->
                content.optJSONObject("text")?.optString("text")
                    ?.replace('\n', ' ')?.take(60) ?: ""
            "messagePhoto" -> {
                val caption = content.optJSONObject("caption")
                    ?.optString("text") ?: ""
                if (caption.isEmpty()) "[图片]" else "[图片] $caption"
            }
            "messageDocument" -> {
                val name = content.optJSONObject("document")
                    ?.optString("file_name") ?: ""
                if (name.isEmpty()) "[文件]" else "[文件] $name"
            }
            "messageVideo" -> "[视频]"
            "messageVoiceNote" -> "[语音]"
            "messageAnimation" -> "[动图]"
            "messageSticker" -> "[贴纸]"
            else -> "[消息]"
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
