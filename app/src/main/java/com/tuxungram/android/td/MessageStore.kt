package com.tuxungram.android.td

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

enum class MsgKind { TEXT, PHOTO, DOCUMENT, VIDEO, VOICE, STICKER, ANIMATION, POLL, OTHER }

/** One poll option, flattened from pollOption. */
data class PollOptionUi(
    val text: String,
    val voterCount: Int,
    val percentage: Int,
    val chosen: Boolean,
    val isCorrect: Boolean = false
)

/** A poll message, flattened from the poll object; refreshed
 *  wholesale whenever TDLib pushes updatePoll. */
data class PollUi(
    val question: String,
    val options: List<PollOptionUi>,
    val totalVoters: Int,
    val isClosed: Boolean,
    val isAnonymous: Boolean,
    val allowsMultiple: Boolean,
    val isQuiz: Boolean,
    val explanation: String = ""
)

/** One reaction shown under a bubble: the emoji, how many people
 *  used it, and whether I am one of them. */
data class ReactionChip(
    val emoji: String,
    val count: Int,
    val chosen: Boolean
)

/** One inline-keyboard button under a bot message, flattened
 *  from replyMarkupInlineKeyboard / inlineKeyboardButtonType*
 *  (verified in td_api.tl). [kind] is one of: url, callback,
 *  callbackPw (callback that needs the 2FA password), switch
 *  (switch-inline query), copy (copy-text), user, game, buy,
 *  disabled. [data] is the callback payload, base64 as TDLib's
 *  JSON interface ships bytes. */
data class InlineBtn(
    val text: String,
    val kind: String,
    val url: String = "",
    val data: String = "",
    val query: String = "",
    val copyText: String = "",
    val userId: Long = 0L
)

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
    val replySummary: String = "",
    // Generic downloadable file (document / video / voice note).
    val fileId: Int = 0,
    val filePath: String? = null,
    val fileName: String = "",
    val fileSize: Long = 0L,
    val fileMime: String = "",
    val durationSec: Int = 0,
    val fileDownloading: Boolean = false,
    val reactions: List<ReactionChip> = emptyList(),
    // Outgoing delivery state derived from the chat's
    // last_read_outbox_message_id: "" (not applicable / pending),
    // "sent" (delivered, unread), "read".
    val readState: String = "",
    // Sticker emoji (STICKER kind); the image itself rides the
    // generic fileId/filePath fields and renders from filePath.
    val stickerEmoji: String = "",
    // Poll payload (POLL kind).
    val pollId: Long = 0L,
    val poll: PollUi? = null,
    // Link preview card (TEXT kind), straight from messageText's
    // link_preview object — TDLib ships it with the message.
    val lpSiteName: String = "",
    val lpTitle: String = "",
    val lpDescription: String = "",
    val lpUrl: String = "",
    val lpPhotoFileId: Int = 0,
    val lpPhotoPath: String? = null,
    // Inline keyboard from the message's reply_markup (v0.8.0,
    // G4); rows of buttons rendered under the bubble.
    val buttons: List<List<InlineBtn>> = emptyList(),
    // Channel post flag (message.is_channel_post); the comment
    // entry (G1) only makes sense on these.
    val isChannelPost: Boolean = false
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

    // ---- Drafts + peer typing state (v0.7.0, F5) ----

    /** The chat's saved draft text (from the chat object's
     *  draft_message, which TDLib syncs across devices itself). */
    private val _draftText = MutableStateFlow("")
    val draftText: StateFlow<String> = _draftText

    /** True once the open chat's draft has been read; the UI
     *  must not save (or restore) before this, or opening a chat
     *  could wipe a cloud draft it has not seen yet. */
    private val _draftLoaded = MutableStateFlow(false)
    val draftLoaded: StateFlow<Boolean> = _draftLoaded

    @Volatile
    private var lastSavedDraft: String? = null

    /** What the other side is doing right now, for the top-bar
     *  subtitle: "正在输入…" / "正在录音…" (prefixed with the
     *  sender's name in groups). */
    private val _peerAction = MutableStateFlow("")
    val peerAction: StateFlow<String> = _peerAction

    private val peerActions =
        ConcurrentHashMap<Long, String>()

    @Volatile
    private var lastActionAt = 0L

    @Volatile
    private var lastTypingSentAt = 0L

    private val replyFetchPending = ConcurrentHashMap.newKeySet<Long>()

    // ---- In-chat search + jump-to-message (Batch 2, B4) ----

    private val _searchResults = MutableStateFlow<List<MsgItem>>(emptyList())
    val searchResults: StateFlow<List<MsgItem>> = _searchResults

    private val _searchExhausted = MutableStateFlow(true)
    val searchExhausted: StateFlow<Boolean> = _searchExhausted

    private var searchQuery = ""
    private var searchFromId = 0L

    /** Set by navigation (global search / in-chat search) before
     *  or after opening a chat; the chat screen consumes it and
     *  jumps to that message. */
    @Volatile
    var pendingLocateId: Long = 0L

    private val _locateId = MutableStateFlow(0L)
    val locateId: StateFlow<Long> = _locateId

    /** searchChatMessages paging: from_message_id cursor plus
     *  next_from_message_id from the previous page (verified in
     *  td_api.tl — a different scheme from global search). */
    fun searchInChat(query: String, append: Boolean = false) {
        val chatId = currentChatId
        if (chatId == 0L) return
        if (!append) {
            searchQuery = query.trim()
            searchFromId = 0L
            _searchResults.value = emptyList()
            _searchExhausted.value = searchQuery.isEmpty()
            if (searchQuery.isEmpty()) return
        }
        val q = TdClient.request("searchChatMessages").apply {
            put("chat_id", chatId)
            put("topic_id", JSONObject.NULL)
            put("query", searchQuery)
            put("sender_id", JSONObject.NULL)
            put("from_message_id", searchFromId)
            put("offset", 0)
            put("limit", 30)
            put("filter", JSONObject().put("@type", "searchMessagesFilterEmpty"))
        }
        TdRepository.sendRaw(q) { response ->
            if (currentChatId != chatId) return@sendRaw
            if (TdClient.isError(response)) {
                _searchExhausted.value = true
                return@sendRaw
            }
            val next = response.optLong("next_from_message_id")
            searchFromId = next
            if (next == 0L) _searchExhausted.value = true
            val arr = response.optJSONArray("messages") ?: JSONArray()
            if (arr.length() == 0) {
                _searchExhausted.value = true
                return@sendRaw
            }
            val items = ArrayList<MsgItem>()
            for (i in 0 until arr.length()) {
                val msg = arr.optJSONObject(i) ?: continue
                items.add(parseMessage(msg))
            }
            _searchResults.value = _searchResults.value + items
        }
    }

    /** Replace the loaded history with the window around
     *  [messageId] (the message plus neighbours on both sides)
     *  and flag it for the UI to scroll to. getChatHistory with a
     *  negative offset reaches back from the anchor. */
    fun jumpTo(messageId: Long) {
        val chatId = currentChatId
        if (chatId == 0L || messageId == 0L) return
        byId.clear()
        _messages.value = emptyList()
        allHistoryLoaded = false
        val query = TdClient.request("getChatHistory").apply {
            put("chat_id", chatId)
            put("from_message_id", messageId)
            put("offset", -25)
            put("limit", 50)
            put("only_local", false)
        }
        TdRepository.sendRaw(query) { response ->
            if (currentChatId != chatId) return@sendRaw
            if (TdClient.isError(response)) return@sendRaw
            val arr = response.optJSONArray("messages") ?: JSONArray()
            // A short jump window says nothing about older
            // history (D1): leave the flag alone and let
            // loadOlder probe from the window's oldest message;
            // its no-new-older-messages rule settles the flag.
            if (arr.length() == 0) allHistoryLoaded = true
            for (i in 0 until arr.length()) {
                val msg = arr.optJSONObject(i) ?: continue
                val item = parseMessage(msg)
                byId[item.id] = item
            }
            publish()
            _locateId.value = messageId
        }
    }

    @Volatile
    var currentChatId: Long = 0L
        private set

    /** Newest message the other side has read (from the chat's
     *  last_read_outbox_message_id, kept current by
     *  updateChatReadOutbox). Drives the sent/read ticks on bubbles. */
    @Volatile
    private var lastReadOutbox = 0L

    @Volatile
    private var allHistoryLoaded = false

    @Volatile
    private var loadingHistory = false

    /** One delayed retry is allowed when the very first history
     *  page comes back empty — on a fresh login the local
     *  database may still be filling in (Phase D, D1). */
    @Volatile
    private var firstPageRetried = false

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
        _searchResults.value = emptyList()
        _searchExhausted.value = true
        searchQuery = ""
        searchFromId = 0L
        _locateId.value = 0L
        allHistoryLoaded = false
        firstPageRetried = false
        lastReadOutbox = 0L
        _draftText.value = ""
        _draftLoaded.value = false
        lastSavedDraft = null
        peerActions.clear()
        _peerAction.value = ""
        lastTypingSentAt = 0L
        TdRepository.sendRaw(TdClient.request("openChat").apply {
            put("chat_id", chatId)
        }) {}
        // Seed the read position for bubble ticks; afterwards
        // updateChatReadOutbox keeps it current. The same response
        // carries the chat's draft (F5): restore it into the
        // input box once the UI sees draftLoaded flip.
        TdRepository.sendRaw(TdClient.request("getChat").apply {
            put("chat_id", chatId)
        }) { response ->
            if (!TdClient.isError(response) && currentChatId == chatId) {
                lastReadOutbox = response.optLong("last_read_outbox_message_id")
                val draftContent = response.optJSONObject("draft_message")
                    ?.optJSONObject("content")
                if (draftContent != null &&
                    draftContent.optString("@type") == "draftMessageContentText"
                ) {
                    val text = draftContent.optJSONObject("text")
                        ?.optString("text") ?: ""
                    _draftText.value = text
                    lastSavedDraft = text
                }
                _draftLoaded.value = true
                publish()
            }
        }
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
            // D1 root-cause fix: td_api.tl states the returned
            // count "is chosen by TDLib and can be smaller than
            // the specified limit", so a short page must NOT be
            // read as end-of-history (the old `length < 50` check
            // froze paging whenever TDLib felt like returning a
            // short page — the reported "sometimes there,
            // sometimes not" missing history). History is over
            // only when a page adds nothing older than what we
            // already have (for an older-page request), or when
            // the first page is empty even after one retry.
            val previousOldest = byId.keys.minOrNull()
            var addedOlder = false
            var newestId = 0L
            for (i in 0 until arr.length()) {
                val msg = arr.optJSONObject(i) ?: continue
                val item = parseMessage(msg)
                byId[item.id] = item
                if (previousOldest == null || item.id < previousOldest) {
                    addedOlder = true
                }
                if (item.id > newestId) newestId = item.id
            }
            if (fromMessageId != 0L) {
                if (!addedOlder) allHistoryLoaded = true
            } else if (arr.length() == 0) {
                if (!firstPageRetried) {
                    firstPageRetried = true
                    // The td layer is callback-based (no coroutine
                    // scope anywhere); a main-looper delay is the
                    // local idiom for "try again shortly".
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (currentChatId == chatId && byId.isEmpty()) {
                            loadHistory(fromMessageId = 0L)
                        }
                    }, 1500)
                } else {
                    allHistoryLoaded = true
                }
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

    /** Persist the input box as the chat's draft
     *  (setChatDraftMessage, verified in td_api.tl). TDLib
     *  stores drafts server-side and syncs them to the user's
     *  other devices on its own; an empty text deletes the
     *  draft (draft_message = null). No-ops when nothing changed
     *  since the last save, so the UI can call it on a debounce
     *  without spamming. */
    fun saveDraft(text: String) {
        val chatId = currentChatId
        if (chatId == 0L) return
        if (text == lastSavedDraft) return
        lastSavedDraft = text
        val draft: Any = if (text.isEmpty()) {
            JSONObject.NULL
        } else {
            JSONObject().apply {
                put("@type", "draftMessage")
                val target = _replyTarget.value
                if (target != null) {
                    put("reply_to", JSONObject().apply {
                        put("@type", "inputMessageReplyToMessage")
                        put("message_id", target.id)
                        put("quote", JSONObject.NULL)
                        put("checklist_task_id", 0)
                        put("poll_option_id", "")
                    })
                } else {
                    put("reply_to", JSONObject.NULL)
                }
                put("date", 0)
                put("content", JSONObject().apply {
                    put("@type", "draftMessageContentText")
                    put("text", formattedText(text))
                    put("link_preview_options", JSONObject.NULL)
                })
                put("effect_id", 0)
                put("suggested_post_info", JSONObject.NULL)
            }
        }
        val query = TdClient.request("setChatDraftMessage").apply {
            put("chat_id", chatId)
            put("topic_id", JSONObject.NULL)
            put("draft_message", draft)
        }
        TdRepository.sendRaw(query) {}
    }

    /** Tell the other side I am typing (sendChatAction with
     *  chatActionTyping, verified in td_api.tl), throttled to
     *  once per 5 seconds — the action itself expires on the
     *  receiving side after a few seconds. */
    fun sendTypingAction() {
        val chatId = currentChatId
        if (chatId == 0L) return
        val now = System.currentTimeMillis()
        if (now - lastTypingSentAt < 5000) return
        lastTypingSentAt = now
        val query = TdClient.request("sendChatAction").apply {
            put("chat_id", chatId)
            put("topic_id", JSONObject.NULL)
            put("business_connection_id", "")
            put("action", JSONObject().put("@type", "chatActionTyping"))
        }
        TdRepository.sendRaw(query) {}
    }

    /** Safety net for the typing subtitle: senders normally end
     *  with chatActionCancel, but a lost update must not leave
     *  "正在输入…" on screen forever. Called by the UI a few
     *  seconds after the last shown action. */
    fun expirePeerActions() {
        if (peerActions.isEmpty()) return
        if (System.currentTimeMillis() - lastActionAt > 6000) {
            peerActions.clear()
            _peerAction.value = ""
        }
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
    ) = forwardMessages(fromChatId, listOf(messageId), toChatId, onResult)

    /** Forward several messages in one call — forwardMessages
     *  takes a whole message_ids vector (verified in td_api.tl);
     *  the multi-select bar uses this (v0.7.0, F1). Ids are sent
     *  oldest-first so the target chat reads in order. */
    fun forwardMessages(
        fromChatId: Long,
        messageIds: List<Long>,
        toChatId: Long,
        onResult: (String?) -> Unit
    ) {
        if (messageIds.isEmpty()) {
            onResult(null)
            return
        }
        val ids = JSONArray()
        for (id in messageIds.sorted()) ids.put(id)
        val query = TdClient.request("forwardMessages").apply {
            put("chat_id", toChatId)
            put("topic_id", JSONObject.NULL)
            put("from_chat_id", fromChatId)
            put("message_ids", ids)
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

    /** Delete a multi-select batch (v0.7.0, F1). With
     *  [revokeAll] (admin with the delete right) everything is
     *  revoked for everyone in one call; otherwise own messages
     *  are revoked and other people's are removed locally only —
     *  the same split as the single-message delete. Bubbles
     *  disappear via updateDeleteMessages. */
    fun deleteSelected(items: List<MsgItem>, revokeAll: Boolean) {
        val chatId = currentChatId
        if (chatId == 0L || items.isEmpty()) return
        fun send(ids: List<Long>, revoke: Boolean) {
            if (ids.isEmpty()) return
            val arr = JSONArray()
            for (id in ids) arr.put(id)
            val query = TdClient.request("deleteMessages").apply {
                put("chat_id", chatId)
                put("message_ids", arr)
                put("revoke", revoke)
            }
            TdRepository.sendRaw(query) { response ->
                if (TdClient.isError(response)) {
                    _sendError.value = sendErrorText(response)
                }
            }
        }
        if (revokeAll) {
            send(items.map { it.id }, revoke = true)
        } else {
            send(items.filter { it.outgoing }.map { it.id }, revoke = true)
            send(items.filter { !it.outgoing }.map { it.id }, revoke = false)
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
            "updateMessageInteractionInfo" -> {
                val chatId = update.optLong("chat_id")
                if (chatId != currentChatId) return
                val messageId = update.optLong("message_id")
                val existing = byId[messageId] ?: return
                byId[messageId] = existing.copy(
                    reactions = reactionsOf(update.optJSONObject("interaction_info"))
                )
                publish()
            }
            "updateDeleteMessages" -> {
                // Deleted on any device (or by the other side / an
                // admin): the bubbles must disappear here too.
                val chatId = update.optLong("chat_id")
                if (chatId != currentChatId) return
                val ids = update.optJSONArray("message_ids") ?: return
                var changed = false
                for (i in 0 until ids.length()) {
                    if (byId.remove(ids.optLong(i)) != null) changed = true
                }
                if (changed) publish()
            }
            "updateChatReadOutbox" -> {
                val chatId = update.optLong("chat_id")
                if (chatId != currentChatId) return
                lastReadOutbox = update.optLong("last_read_outbox_message_id")
                publish()
            }
            "updateChatAction" -> {
                // The other side typing / recording / uploading
                // (verified in td_api.tl: chat_id, topic_id,
                // sender_id, action). Keyed per sender so a
                // group can show who it is; cancel removes them.
                val chatId = update.optLong("chat_id")
                if (chatId != currentChatId) return
                val sender = update.optJSONObject("sender_id") ?: return
                val actionType = update.optJSONObject("action")
                    ?.optString("@type") ?: return
                val isUser = sender.optString("@type") == "messageSenderUser"
                val key = when (sender.optString("@type")) {
                    "messageSenderUser" -> sender.optLong("user_id")
                    "messageSenderChat" -> sender.optLong("chat_id")
                    else -> 0L
                }
                if (key == 0L) return
                if (actionType == "chatActionCancel") {
                    peerActions.remove(key)
                } else {
                    val label = when (actionType) {
                        "chatActionTyping" -> "正在输入…"
                        "chatActionRecordingVoiceNote" -> "正在录音…"
                        "chatActionRecordingVideoNote" -> "正在录视频消息…"
                        "chatActionUploadingPhoto" -> "正在发送图片…"
                        "chatActionUploadingVideo" -> "正在发送视频…"
                        "chatActionUploadingDocument" -> "正在发送文件…"
                        "chatActionUploadingVoiceNote" -> "正在发送语音…"
                        "chatActionChoosingSticker" -> "正在选贴纸…"
                        else -> return
                    }
                    val name = if (isUser) {
                        userNames[key] ?: run {
                            fetchUser(key)
                            ""
                        }
                    } else {
                        ""
                    }
                    val isGroup = ChatStore.itemOf(chatId)?.isGroup == true
                    peerActions[key] =
                        if (isGroup && name.isNotEmpty()) {
                            "$name $label"
                        } else {
                            label
                        }
                }
                lastActionAt = System.currentTimeMillis()
                _peerAction.value = peerActions.values.firstOrNull() ?: ""
            }
            "updatePoll" -> {
                // Full poll object pushed on every vote change;
                // match it to the open chat's poll message by id.
                val pollObj = update.optJSONObject("poll") ?: return
                val pollId = pollObj.optLong("id")
                var changed = false
                for ((id, item) in byId) {
                    if (item.pollId == pollId && item.pollId != 0L) {
                        byId[id] = item.copy(poll = parsePoll(pollObj))
                        changed = true
                    }
                }
                if (changed) publish()
            }
            "updateFile" -> {
                val file = update.optJSONObject("file") ?: return
                val local = file.optJSONObject("local") ?: return
                val fileId = file.optInt("id")
                val completed = local.optBoolean("is_downloading_completed")
                val active = local.optBoolean("is_downloading_active")
                val path = local.optString("path")
                var changed = false
                for ((id, item) in byId) {
                    if (completed && item.photoFileId == fileId && item.photoPath == null) {
                        byId[id] = item.copy(photoPath = path)
                        changed = true
                    }
                    if (completed && item.lpPhotoFileId == fileId && item.lpPhotoPath == null) {
                        byId[id] = item.copy(lpPhotoPath = path)
                        changed = true
                    }
                    if (item.fileId == fileId && item.fileId != 0) {
                        if (completed && item.filePath == null) {
                            byId[id] = item.copy(filePath = path, fileDownloading = false)
                            changed = true
                        } else if (!completed && active != item.fileDownloading) {
                            byId[id] = item.copy(fileDownloading = active)
                            changed = true
                        }
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

    fun parseMessage(msg: JSONObject): MsgItem {
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
            replySummary = replySummaryOf(msg),
            reactions = reactionsOf(msg.optJSONObject("interaction_info")),
            buttons = parseButtons(msg.optJSONObject("reply_markup")),
            isChannelPost = msg.optBoolean("is_channel_post")
        )
        val content = msg.optJSONObject("content") ?: return base
        return parseContent(base, content)
    }

    private fun parseContent(base: MsgItem, content: JSONObject): MsgItem {
        return when (content.optString("@type")) {
            "messageText" -> {
                val lp = content.optJSONObject("link_preview")
                val lpPhoto = lpPhotoFile(lp)
                val lpFileId = lpPhoto?.optInt("id", 0) ?: 0
                if (lpFileId != 0 && downloadedPath(lpPhoto) == null) {
                    requestDownload(lpFileId, priority = 1)
                }
                base.copy(
                    kind = MsgKind.TEXT,
                    text = content.optJSONObject("text")?.optString("text") ?: "",
                    lpSiteName = lp?.optString("site_name") ?: "",
                    lpTitle = lp?.optString("title") ?: "",
                    lpDescription = lp?.optJSONObject("description")
                        ?.optString("text") ?: "",
                    lpUrl = lp?.optString("url") ?: "",
                    lpPhotoFileId = lpFileId,
                    lpPhotoPath = downloadedPath(lpPhoto)
                )
            }
            "messageSticker" -> {
                val sticker = content.optJSONObject("sticker")
                val file = sticker?.optJSONObject("sticker")
                val fileId = file?.optInt("id", 0) ?: 0
                if (fileId != 0 && downloadedPath(file) == null) {
                    requestDownload(fileId, priority = 16)
                }
                base.copy(
                    kind = MsgKind.STICKER,
                    stickerEmoji = sticker?.optString("emoji") ?: "",
                    fileId = fileId,
                    filePath = downloadedPath(file),
                    fileMime = "image/webp",
                    photoWidth = sticker?.optInt("width", 0) ?: 0,
                    photoHeight = sticker?.optInt("height", 0) ?: 0
                )
            }
            "messageAnimation" -> {
                val animation = content.optJSONObject("animation")
                val file = animation?.optJSONObject("animation")
                val fileId = file?.optInt("id", 0) ?: 0
                if (fileId != 0 && downloadedPath(file) == null) {
                    requestDownload(fileId, priority = 16)
                }
                base.copy(
                    kind = MsgKind.ANIMATION,
                    text = content.optJSONObject("caption")?.optString("text") ?: "",
                    fileId = fileId,
                    filePath = downloadedPath(file),
                    fileName = animation?.optString("file_name") ?: "",
                    fileSize = fileSizeOf(file),
                    fileMime = animation?.optString("mime_type") ?: "video/mp4",
                    durationSec = animation?.optInt("duration", 0) ?: 0,
                    photoWidth = animation?.optInt("width", 0) ?: 0,
                    photoHeight = animation?.optInt("height", 0) ?: 0
                )
            }
            "messagePoll" -> {
                val pollObj = content.optJSONObject("poll")
                base.copy(
                    kind = MsgKind.POLL,
                    pollId = pollObj?.optLong("id", 0L) ?: 0L,
                    poll = pollObj?.let { parsePoll(it) }
                )
            }
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
            "messageDocument" -> {
                val doc = content.optJSONObject("document")
                val file = doc?.optJSONObject("document")
                base.copy(
                    kind = MsgKind.DOCUMENT,
                    text = content.optJSONObject("caption")?.optString("text") ?: "",
                    fileId = file?.optInt("id", 0) ?: 0,
                    filePath = downloadedPath(file),
                    fileName = doc?.optString("file_name") ?: "",
                    fileSize = fileSizeOf(file),
                    fileMime = doc?.optString("mime_type") ?: ""
                )
            }
            "messageVideo" -> {
                val video = content.optJSONObject("video")
                val file = video?.optJSONObject("video")
                base.copy(
                    kind = MsgKind.VIDEO,
                    text = content.optJSONObject("caption")?.optString("text") ?: "",
                    fileId = file?.optInt("id", 0) ?: 0,
                    filePath = downloadedPath(file),
                    fileName = video?.optString("file_name") ?: "",
                    fileSize = fileSizeOf(file),
                    fileMime = video?.optString("mime_type") ?: "",
                    durationSec = video?.optInt("duration", 0) ?: 0
                )
            }
            "messageVoiceNote" -> {
                val voice = content.optJSONObject("voice_note")
                val file = voice?.optJSONObject("voice")
                base.copy(
                    kind = MsgKind.VOICE,
                    text = content.optJSONObject("caption")?.optString("text") ?: "",
                    fileId = file?.optInt("id", 0) ?: 0,
                    filePath = downloadedPath(file),
                    fileName = "语音消息",
                    fileSize = fileSizeOf(file),
                    fileMime = voice?.optString("mime_type") ?: "",
                    durationSec = voice?.optInt("duration", 0) ?: 0
                )
            }
            else -> base.copy(kind = MsgKind.OTHER, text = "")
        }
    }

    private fun requestDownload(fileId: Int, priority: Int) {
        TdRepository.sendRaw(TdClient.request("downloadFile").apply {
            put("file_id", fileId)
            put("priority", priority)
            put("offset", 0L)
            put("limit", 0L)
            put("synchronous", false)
        }) {}
    }

    /** The photo file inside a linkPreview's type object, when the
     *  type carries one (article/app/photo/... types all expose a
     *  `photo` photo object; pick its largest size's file). */
    private fun lpPhotoFile(lp: JSONObject?): JSONObject? {
        val photo = lp?.optJSONObject("type")?.optJSONObject("photo") ?: return null
        val sizes = photo.optJSONArray("sizes") ?: return null
        var best: JSONObject? = null
        for (i in 0 until sizes.length()) {
            val s = sizes.optJSONObject(i) ?: continue
            if (best == null || s.optInt("width") > best.optInt("width")) best = s
        }
        return best?.optJSONObject("photo")
    }

    /** Flatten a poll object (fields verified in td_api.tl: poll,
     *  pollOption, pollTypeQuiz). */
    private fun parsePoll(poll: JSONObject): PollUi {
        val type = poll.optJSONObject("type")
        val isQuiz = type?.optString("@type") == "pollTypeQuiz"
        val correctIds = HashSet<Int>()
        var explanation = ""
        if (isQuiz && type != null) {
            val arr = type.optJSONArray("correct_option_ids")
            if (arr != null) {
                for (i in 0 until arr.length()) correctIds.add(arr.optInt(i))
            }
            explanation = type.optJSONObject("explanation")
                ?.optString("text") ?: ""
        }
        val options = ArrayList<PollOptionUi>()
        val arr = poll.optJSONArray("options") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            options.add(
                PollOptionUi(
                    text = o.optJSONObject("text")?.optString("text") ?: "",
                    voterCount = o.optInt("voter_count"),
                    percentage = o.optInt("vote_percentage"),
                    chosen = o.optBoolean("is_chosen"),
                    isCorrect = correctIds.contains(i)
                )
            )
        }
        return PollUi(
            question = poll.optJSONObject("question")?.optString("text") ?: "",
            options = options,
            totalVoters = poll.optInt("total_voter_count"),
            isClosed = poll.optBoolean("is_closed"),
            isAnonymous = poll.optBoolean("is_anonymous"),
            allowsMultiple = poll.optBoolean("allows_multiple_answers"),
            isQuiz = isQuiz,
            explanation = explanation
        )
    }

    /** Vote (or retract, with an empty option list) via
     *  setPollAnswer; results refresh through updatePoll. */
    fun setPollAnswer(item: MsgItem, optionIds: List<Int>) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val ids = JSONArray()
        for (id in optionIds) ids.put(id)
        val query = TdClient.request("setPollAnswer").apply {
            put("chat_id", chatId)
            put("message_id", item.id)
            put("option_ids", ids)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = sendErrorText(response)
            }
        }
    }

    /** Send a sticker already known to TDLib (from the recent /
     *  favorite lists) by its local file id — inputFileId is the
     *  documented way to reference an existing file. */
    fun sendStickerFile(fileId: Int, emoji: String) {
        val chatId = currentChatId
        if (chatId == 0L || fileId == 0) return
        val replyTo = replyToJson()
        _replyTarget.value = null
        val query = TdClient.request("sendMessage").apply {
            put("chat_id", chatId)
            if (replyTo != null) put("reply_to", replyTo)
            put("input_message_content", JSONObject().apply {
                put("@type", "inputMessageSticker")
                put("sticker", JSONObject().apply {
                    put("@type", "inputSticker")
                    put("sticker", JSONObject().apply {
                        put("@type", "inputFileId")
                        put("id", fileId)
                    })
                    put("thumbnail", JSONObject.NULL)
                    put("width", 0)
                    put("height", 0)
                })
                put("emoji", emoji)
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

    /** Send a saved GIF (animation) by its local file id. */
    fun sendAnimationFile(fileId: Int, duration: Int, width: Int, height: Int) {
        val chatId = currentChatId
        if (chatId == 0L || fileId == 0) return
        val replyTo = replyToJson()
        _replyTarget.value = null
        val query = TdClient.request("sendMessage").apply {
            put("chat_id", chatId)
            if (replyTo != null) put("reply_to", replyTo)
            put("input_message_content", JSONObject().apply {
                put("@type", "inputMessageAnimation")
                put("animation", JSONObject().apply {
                    put("@type", "inputAnimation")
                    put("animation", JSONObject().apply {
                        put("@type", "inputFileId")
                        put("id", fileId)
                    })
                    put("thumbnail", JSONObject.NULL)
                    put("added_sticker_file_ids", JSONArray())
                    put("duration", duration)
                    put("width", width)
                    put("height", height)
                })
                put("caption", formattedText(""))
                put("show_caption_above_media", false)
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

    /** Send a locally recorded voice note (OGG/Opus file).
     *  [waveform] is the base64 of TDLib's 5-bit packed amplitudes;
     *  a wrong waveform only renders oddly, never blocks sending. */
    fun sendVoiceNote(path: String, durationSec: Int, waveform: String) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val replyTo = replyToJson()
        _replyTarget.value = null
        val query = TdClient.request("sendMessage").apply {
            put("chat_id", chatId)
            if (replyTo != null) put("reply_to", replyTo)
            put("input_message_content", JSONObject().apply {
                put("@type", "inputMessageVoiceNote")
                put("voice_note", JSONObject().apply {
                    put("@type", "inputVoiceNote")
                    put("voice_note", JSONObject().apply {
                        put("@type", "inputFileLocal")
                        put("path", path)
                    })
                    put("duration", durationSec)
                    put("waveform", waveform)
                })
                put("caption", formattedText(""))
                put("self_destruct_type", JSONObject.NULL)
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

    private fun downloadedPath(file: JSONObject?): String? {
        val local = file?.optJSONObject("local") ?: return null
        return if (local.optBoolean("is_downloading_completed")) {
            local.optString("path").ifEmpty { null }
        } else {
            null
        }
    }

    private fun fileSizeOf(file: JSONObject?): Long {
        if (file == null) return 0L
        val size = file.optLong("size", 0L)
        return if (size > 0) size else file.optLong("expected_size", 0L)
    }

    /** Start (or restart) downloading a document/video/voice file;
     *  progress and completion arrive through updateFile. */
    fun downloadFileFor(item: MsgItem) {
        if (item.fileId == 0 || item.filePath != null) return
        TdRepository.sendRaw(TdClient.request("downloadFile").apply {
            put("file_id", item.fileId)
            put("priority", 16)
            put("offset", 0L)
            put("limit", 0L)
            put("synchronous", false)
        }) {}
        val existing = byId[item.id] ?: return
        byId[item.id] = existing.copy(fileDownloading = true)
        publish()
    }

    /** Send a picked file: video MIME goes as a video message,
     *  .ogg/.opus audio as a voice note, everything else as a
     *  document. Input types verified in td_api.tl (inputDocument /
     *  inputVideo / inputVoiceNote and their input* wrappers). */
    fun sendFile(path: String, mimeType: String, fileName: String) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val replyTo = replyToJson()
        _replyTarget.value = null
        val localFile = JSONObject().apply {
            put("@type", "inputFileLocal")
            put("path", path)
        }
        val content = when {
            mimeType.startsWith("video/") -> JSONObject().apply {
                put("@type", "inputMessageVideo")
                put("video", JSONObject().apply {
                    put("@type", "inputVideo")
                    put("video", localFile)
                    put("thumbnail", JSONObject.NULL)
                    put("cover", JSONObject.NULL)
                    put("start_timestamp", 0)
                    put("added_sticker_file_ids", JSONArray())
                    put("duration", 0)
                    put("width", 0)
                    put("height", 0)
                    put("supports_streaming", true)
                })
                put("caption", formattedText(""))
                put("show_caption_above_media", false)
                put("self_destruct_type", JSONObject.NULL)
                put("has_spoiler", false)
            }
            mimeType == "audio/ogg" || fileName.endsWith(".opus", true) ||
                fileName.endsWith(".ogg", true) -> JSONObject().apply {
                put("@type", "inputMessageVoiceNote")
                put("voice_note", JSONObject().apply {
                    put("@type", "inputVoiceNote")
                    put("voice_note", localFile)
                    put("duration", 0)
                    put("waveform", "")
                })
                put("caption", formattedText(""))
                put("self_destruct_type", JSONObject.NULL)
            }
            else -> JSONObject().apply {
                put("@type", "inputMessageDocument")
                put("document", JSONObject().apply {
                    put("@type", "inputDocument")
                    put("document", localFile)
                    put("thumbnail", JSONObject.NULL)
                    put("disable_content_type_detection", false)
                })
                put("caption", formattedText(""))
            }
        }
        val query = TdClient.request("sendMessage").apply {
            put("chat_id", chatId)
            if (replyTo != null) put("reply_to", replyTo)
            put("input_message_content", content)
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

    /** Flatten messageInteractionInfo.reactions (verified in
     *  td_api.tl: messageReactions -> messageReaction{type,
     *  total_count, is_chosen}); only plain emoji reactions are
     *  rendered, custom-emoji ones are skipped for now. */
    private fun reactionsOf(interactionInfo: JSONObject?): List<ReactionChip> {
        val reactions = interactionInfo?.optJSONObject("reactions")
            ?.optJSONArray("reactions") ?: return emptyList()
        val out = ArrayList<ReactionChip>()
        for (i in 0 until reactions.length()) {
            val reaction = reactions.optJSONObject(i) ?: continue
            val type = reaction.optJSONObject("type") ?: continue
            if (type.optString("@type") != "reactionTypeEmoji") continue
            val emoji = type.optString("emoji")
            if (emoji.isEmpty()) continue
            out.add(
                ReactionChip(
                    emoji = emoji,
                    count = reaction.optInt("total_count"),
                    chosen = reaction.optBoolean("is_chosen")
                )
            )
        }
        return out
    }

    /** Flatten a reply_markup object into button rows. Only
     *  replyMarkupInlineKeyboard carries tappable buttons;
     *  reply-markup keyboards / force-reply are input-area
     *  concepts this app does not render yet. Button types
     *  verified in td_api.tl (inlineKeyboardButtonType*). */
    private fun parseButtons(markup: JSONObject?): List<List<InlineBtn>> {
        if (markup == null) return emptyList()
        if (markup.optString("@type") != "replyMarkupInlineKeyboard") {
            return emptyList()
        }
        val rows = markup.optJSONArray("rows") ?: return emptyList()
        val out = ArrayList<List<InlineBtn>>()
        for (r in 0 until rows.length()) {
            val rowArr = rows.optJSONArray(r) ?: continue
            val row = ArrayList<InlineBtn>()
            for (b in 0 until rowArr.length()) {
                val btn = rowArr.optJSONObject(b) ?: continue
                val type = btn.optJSONObject("type") ?: continue
                val text = btn.optString("text")
                if (text.isEmpty()) continue
                row.add(
                    when (type.optString("@type")) {
                        "inlineKeyboardButtonTypeUrl" -> InlineBtn(
                            text, "url", url = type.optString("url")
                        )
                        "inlineKeyboardButtonTypeLoginUrl",
                        "inlineKeyboardButtonTypeWebApp" -> InlineBtn(
                            text, "url", url = type.optString("url")
                        )
                        "inlineKeyboardButtonTypeCallback" -> InlineBtn(
                            text, "callback", data = type.optString("data")
                        )
                        "inlineKeyboardButtonTypeCallbackWithPassword" ->
                            InlineBtn(
                                text, "callbackPw",
                                data = type.optString("data")
                            )
                        "inlineKeyboardButtonTypeSwitchInline" -> InlineBtn(
                            text, "switch", query = type.optString("query")
                        )
                        "inlineKeyboardButtonTypeCopyText" -> InlineBtn(
                            text, "copy", copyText = type.optString("text")
                        )
                        "inlineKeyboardButtonTypeUser" -> InlineBtn(
                            text, "user", userId = type.optLong("user_id")
                        )
                        "inlineKeyboardButtonTypeCallbackGame" ->
                            InlineBtn(text, "game")
                        "inlineKeyboardButtonTypeBuy" ->
                            InlineBtn(text, "buy")
                        else -> InlineBtn(text, "disabled")
                    }
                )
            }
            if (row.isNotEmpty()) out.add(row)
        }
        return out
    }

    /** Press a callback button (getCallbackQueryAnswer, verified
     *  in td_api.tl). The bot's answer comes back as
     *  callbackQueryAnswer{text, show_alert, url}; the UI decides
     *  how to present it. [password] is only for
     *  callbackWithPassword buttons (the account's 2FA password,
     *  wrapped in callbackQueryPayloadDataWithPassword). Works
     *  for any chat, so thread/forum pages reuse it too. */
    fun answerCallback(
        chatId: Long,
        messageId: Long,
        data: String,
        password: String?,
        onAnswer: (text: String, showAlert: Boolean, url: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val payload = JSONObject().apply {
            if (password != null) {
                put("@type", "callbackQueryPayloadDataWithPassword")
                put("password", password)
            } else {
                put("@type", "callbackQueryPayloadData")
            }
            put("data", data)
        }
        val query = TdClient.request("getCallbackQueryAnswer").apply {
            put("chat_id", chatId)
            put("message_id", messageId)
            put("payload", payload)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                onError(sendErrorText(response))
            } else {
                onAnswer(
                    response.optString("text"),
                    response.optBoolean("show_alert"),
                    response.optString("url")
                )
            }
        }
    }

    val COMMON_REACTIONS = listOf("👍", "❤️", "🔥", "🎉", "😂", "👏")

    /** Add my reaction, or remove it if I already chose it
     *  (addMessageReaction / removeMessageReaction, verified in
     *  td_api.tl). The chips refresh via updateMessageInteractionInfo. */
    fun toggleReaction(item: MsgItem, emoji: String) {
        val chatId = currentChatId
        if (chatId == 0L) return
        toggleReactionIn(chatId, item, emoji) { _sendError.value = it }
    }

    /** toggleReaction for an explicit chat — thread and forum
     *  pages (v0.8.0) show messages from chats other than the
     *  open one and reuse this; errors go to [onError] instead
     *  of the chat screen's send-error line. */
    fun toggleReactionIn(
        chatId: Long,
        item: MsgItem,
        emoji: String,
        onError: (String) -> Unit
    ) {
        val already = item.reactions.any { it.emoji == emoji && it.chosen }
        val reactionType = JSONObject().apply {
            put("@type", "reactionTypeEmoji")
            put("emoji", emoji)
        }
        val query = if (already) {
            TdClient.request("removeMessageReaction").apply {
                put("chat_id", chatId)
                put("message_id", item.id)
                put("reaction_type", reactionType)
            }
        } else {
            TdClient.request("addMessageReaction").apply {
                put("chat_id", chatId)
                put("message_id", item.id)
                put("reaction_type", reactionType)
                put("is_big", false)
                put("update_recent_reactions", false)
            }
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                // The picker filters to the chat's allowed list,
                // but the list can change between opening the chat
                // and tapping; a 400 here is almost always "this
                // reaction is not allowed in this chat" — say that
                // in plain words, never show the raw code.
                val message = response.optString("message")
                onError(
                    if (
                        response.optInt("code") == 400 ||
                        message.contains("REACTION", ignoreCase = true)
                    ) {
                        "这个对话不允许这个回应。"
                    } else {
                        sendErrorText(response)
                    }
                )
            }
        }
    }

    /** Shown when the UI blocks a reaction the chat does not
     *  allow, before it ever reaches the server. */
    fun reportReactionBlocked() {
        _sendError.value = "这个对话不允许这个回应。"
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
            "messagePoll" -> {
                val q = content.optJSONObject("poll")
                    ?.optJSONObject("question")?.optString("text") ?: ""
                if (q.isEmpty()) "[投票]" else "[投票] $q"
            }
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
            val withName = if (name != null && name != item.senderName) {
                item.copy(senderName = name)
            } else {
                item
            }
            // Delivery ticks follow the chat's read position, not a
            // one-off flag set at send time.
            val readState = when {
                !withName.outgoing || withName.sendingState.isNotEmpty() -> ""
                withName.id <= lastReadOutbox -> "read"
                else -> "sent"
            }
            if (readState != withName.readState) {
                withName.copy(readState = readState)
            } else {
                withName
            }
        }
    }
}
