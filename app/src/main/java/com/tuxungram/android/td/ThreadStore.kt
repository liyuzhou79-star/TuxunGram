package com.tuxungram.android.td

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Channel-post comment threads (v0.8.0, G1). A channel post's
 * thread lives in the channel's linked discussion group:
 * getMessageThread(channelChatId, postId) resolves it to a
 * MessageThreadInfo{chat_id = discussion group, message_thread_id
 * = the mirrored post's id there}; history pages with
 * getMessageThreadHistory and comments are sent with topic_id =
 * messageTopicThread (all verified in td_api.tl). Message parsing
 * is MessageStore.parseMessage — one parser for the whole app.
 *
 * Paging follows the D1 rule: a short page is NOT the end (TDLib
 * chooses page sizes); history is over only when a page adds
 * nothing older than the current oldest.
 */
object ThreadStore {
    private val _messages = MutableStateFlow<List<MsgItem>>(emptyList())
    val messages: StateFlow<List<MsgItem>> = _messages

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** True once getMessageThread has resolved the thread. */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    private val _replyTarget = MutableStateFlow<MsgItem?>(null)
    val replyTarget: StateFlow<MsgItem?> = _replyTarget

    @Volatile
    var sourceChatId = 0L
        private set

    @Volatile
    var threadChatId = 0L
        private set

    @Volatile
    private var rootMessageId = 0L

    private val byId = ConcurrentHashMap<Long, MsgItem>()

    // Exhaustion of the loaded window, exposed as a
    // flow since v0.9.32 (Y6) for the media viewer.
    private val _allLoaded = MutableStateFlow(false)
    val allLoadedFlow: StateFlow<Boolean> = _allLoaded
    private var allLoaded: Boolean
        get() = _allLoaded.value
        set(value) { _allLoaded.value = value }

    @Volatile
    private var loading = false

    /** Paging-in-flight flag for the top-of-list spinner
     *  (v0.9.28, U2; mirrors the chat store's). */
    private val _historyLoading = MutableStateFlow(false)
    val historyLoading: StateFlow<Boolean> = _historyLoading

    /** One delayed retry is allowed when the very first
     *  history page comes back empty — the local database
     *  may still be filling in (mirrors MessageStore). */
    @Volatile
    private var firstPageRetried = false

    // ---- Deep-paging hardening (v0.9.34, A1) ----
    // The two pieces MessageStore landed in v0.9.28 (U2),
    // ported so all three history hosts share one paging
    // contract (the gap v0.9.29's V5 audit recorded and
    // deliberately left; this version closes it).

    /** Window generation: bumped every time the loaded
     *  window is replaced wholesale (open, close). History
     *  responses — and the getMessageThread resolution
     *  itself — carry the generation they were sent under;
     *  a response from a replaced window is dropped instead
     *  of leaking its messages into the new window (the
     *  plain sourceChatId check cannot catch reopening the
     *  SAME thread quickly). */
    @Volatile
    private var historyGeneration = 0

    /** The from_message_id anchor an older-page retry is
     *  already scheduled for; at most one delayed retry per
     *  anchor, cleared when a page lands with older
     *  messages. */
    @Volatile
    private var olderRetryAnchor = 0L

    @Volatile
    private var attached = false

    // ---- Linked-chat detection for the 评论 entry point ----

    private val _linkedChatId = MutableStateFlow(0L)
    val linkedChatId: StateFlow<Long> = _linkedChatId

    @Volatile
    private var linkedForChat = 0L

    /** Resolve a channel's discussion group (supergroupFullInfo
     *  .linked_chat_id, verified in td_api.tl). The chat screen
     *  calls this for channel chats; the comment entry only
     *  appears when the result is non-zero. */
    fun loadLinkedChat(chatId: Long) {
        if (linkedForChat == chatId) return
        linkedForChat = chatId
        _linkedChatId.value = 0L
        val query = TdClient.request("getChat").apply {
            put("chat_id", chatId)
        }
        TdRepository.sendRaw(query) { chat ->
            if (TdClient.isError(chat)) return@sendRaw
            val type = chat.optJSONObject("type") ?: return@sendRaw
            if (type.optString("@type") != "chatTypeSupergroup") {
                return@sendRaw
            }
            val full = TdClient.request("getSupergroupFullInfo").apply {
                put("supergroup_id", type.optLong("supergroup_id"))
            }
            TdRepository.sendRaw(full) { info ->
                if (!TdClient.isError(info) && linkedForChat == chatId) {
                    _linkedChatId.value = info.optLong("linked_chat_id")
                }
            }
        }
    }

    private fun attach() {
        if (attached) return
        attached = true
        TdRepository.addUpdateListener { update -> handleUpdate(update) }
    }

    fun open(sourceChat: Long, postId: Long) {
        attach()
        sourceChatId = sourceChat
        threadChatId = 0L
        rootMessageId = 0L
        byId.clear()
        _messages.value = emptyList()
        _error.value = null
        _ready.value = false
        _replyTarget.value = null
        allLoaded = false
        firstPageRetried = false
        historyGeneration++
        olderRetryAnchor = 0L
        val gen = historyGeneration
        val query = TdClient.request("getMessageThread").apply {
            put("chat_id", sourceChat)
            put("message_id", postId)
        }
        TdRepository.sendRaw(query) { info ->
            if (sourceChatId != sourceChat ||
                gen != historyGeneration
            ) {
                return@sendRaw
            }
            if (TdClient.isError(info)) {
                _error.value = "这个帖子没有评论区。"
                return@sendRaw
            }
            threadChatId = info.optLong("chat_id")
            rootMessageId = info.optLong("message_thread_id")
            if (threadChatId == 0L || rootMessageId == 0L) {
                _error.value = "这个帖子没有评论区。"
                return@sendRaw
            }
            _ready.value = true
            TdRepository.sendRaw(TdClient.request("openChat").apply {
                put("chat_id", threadChatId)
            }) {}
            // The thread info already carries the newest few
            // messages; seed with them, then page properly.
            val seed = info.optJSONArray("messages")
            if (seed != null) {
                for (i in 0 until seed.length()) {
                    val msg = seed.optJSONObject(i) ?: continue
                    val item = MessageStore.parseMessage(msg)
                    byId[item.id] = item
                }
                publish()
            }
            loadHistory(fromMessageId = 0L)
        }
    }

    fun close() {
        historyGeneration++
        olderRetryAnchor = 0L
        if (threadChatId != 0L) {
            TdRepository.sendRaw(TdClient.request("closeChat").apply {
                put("chat_id", threadChatId)
            }) {}
        }
        sourceChatId = 0L
        threadChatId = 0L
        rootMessageId = 0L
        byId.clear()
        _messages.value = emptyList()
    }

    fun loadOlder() {
        if (!_ready.value || allLoaded || loading) return
        val oldest = byId.keys.minOrNull() ?: return
        loadHistory(fromMessageId = oldest)
    }

    /** One delayed retry for an older page that came back
     *  empty or with an error (v0.9.34, A1; the MessageStore
     *  U2 contract). Deep inside a huge thread TDLib can
     *  answer a page with nothing while that segment is
     *  still being fetched — reading that as end-of-history
     *  strands the user above a fake bottom. At most one
     *  retry per anchor; a page that answers with the anchor
     *  itself is the genuine end and never retries. */
    private fun scheduleOlderRetry(
        chatId: Long,
        rootId: Long,
        anchorId: Long,
        gen: Int
    ) {
        if (olderRetryAnchor == anchorId) return
        olderRetryAnchor = anchorId
        Handler(Looper.getMainLooper()).postDelayed({
            if (threadChatId == chatId &&
                rootMessageId == rootId &&
                historyGeneration == gen &&
                !allLoaded && !loading &&
                byId.keys.minOrNull() == anchorId
            ) {
                loadHistory(
                    fromMessageId = anchorId, isOlderRetry = true
                )
            }
        }, 1200)
    }

    private fun loadHistory(
        fromMessageId: Long,
        isOlderRetry: Boolean = false
    ) {
        val chatId = threadChatId
        val rootId = rootMessageId
        if (chatId == 0L || rootId == 0L || loading) return
        loading = true
        _historyLoading.value = true
        val gen = historyGeneration
        val query = TdClient.request("getMessageThreadHistory").apply {
            put("chat_id", chatId)
            put("message_id", rootId)
            put("from_message_id", fromMessageId)
            put("offset", 0)
            put("limit", 50)
        }
        TdRepository.sendRaw(query) { response ->
            loading = false
            _historyLoading.value = false
            if (threadChatId != chatId) return@sendRaw
            // Stale window (A1): open / close replaced the
            // window while this page was in flight; its
            // messages belong to the old window and must not
            // leak into the new one.
            if (gen != historyGeneration) return@sendRaw
            if (TdClient.isError(response)) {
                if (fromMessageId != 0L && !isOlderRetry) {
                    scheduleOlderRetry(chatId, rootId, fromMessageId, gen)
                }
                return@sendRaw
            }
            val arr = response.optJSONArray("messages") ?: JSONArray()
            val previousOldest = byId.keys.minOrNull()
            var addedOlder = false
            var newestId = 0L
            for (i in 0 until arr.length()) {
                val msg = arr.optJSONObject(i) ?: continue
                val item = MessageStore.parseMessage(msg)
                byId[item.id] = item
                if (previousOldest == null || item.id < previousOldest) {
                    addedOlder = true
                }
                if (item.id > newestId) newestId = item.id
            }
            if (fromMessageId != 0L) {
                when {
                    addedOlder -> olderRetryAnchor = 0L
                    // A completely empty answer is suspect
                    // (A1): retry once before believing it.
                    arr.length() == 0 && !isOlderRetry ->
                        scheduleOlderRetry(
                            chatId, rootId, fromMessageId, gen
                        )
                    else -> allLoaded = true
                }
            } else if (arr.length() == 0) {
                if (!firstPageRetried) {
                    firstPageRetried = true
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (threadChatId == chatId &&
                            rootMessageId == rootId &&
                            historyGeneration == gen &&
                            !allLoaded && !loading
                        ) {
                            loadHistory(fromMessageId = 0L)
                        }
                    }, 1500)
                } else {
                    allLoaded = true
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

    fun sendText(text: String) {
        sendWithContent(JSONObject().apply {
            put("@type", "inputMessageText")
            // Same send markdown as the main chat (v0.9.15, V3).
            put("text", MessageStore.richFormattedText(text))
            put("link_preview_options", JSONObject.NULL)
            put("clear_draft", true)
        })
    }

    /** Send a picked photo into the thread (v0.9.1, J2: media
     *  sends use the main chat's content builders, only the
     *  topic wiring differs). */
    fun sendPhoto(path: String, caption: String) {
        sendWithContent(MessageStore.photoContent(path, caption))
    }

    /** Send a picked file / video / voice note into the thread. */
    fun sendFile(path: String, mimeType: String, fileName: String) {
        sendWithContent(MessageStore.fileContent(path, mimeType, fileName))
    }

    private fun sendWithContent(content: JSONObject) {
        val chatId = threadChatId
        val rootId = rootMessageId
        if (chatId == 0L || rootId == 0L) return
        val target = _replyTarget.value
        _replyTarget.value = null
        val query = TdClient.request("sendMessage").apply {
            put("chat_id", chatId)
            put("topic_id", JSONObject().apply {
                put("@type", "messageTopicThread")
                put("message_thread_id", rootId)
            })
            if (target != null) {
                put("reply_to", JSONObject().apply {
                    put("@type", "inputMessageReplyToMessage")
                    put("message_id", target.id)
                    put("quote", JSONObject.NULL)
                    put("checklist_task_id", 0)
                    put("poll_option_id", "")
                })
            }
            put("input_message_content", content)
        }
        TdRepository.sendRaw(query) { response ->
            if (threadChatId != chatId) return@sendRaw
            if (TdClient.isError(response)) {
                _error.value = "评论没发出去，再试一次。"
            } else {
                val item = MessageStore.parseMessage(response)
                byId[item.id] = item
                publish()
            }
        }
    }

    /** Vote in a poll posted inside the thread (v0.9.1, J2):
     *  same setPollAnswer route as the main chat; results come
     *  back through updatePoll below. */
    fun setPollAnswer(item: MsgItem, optionIds: List<Int>) {
        val chatId = threadChatId
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
                _error.value = "投票没成功，再试一次。"
            }
        }
    }

    fun toggleReaction(item: MsgItem, emoji: String) {
        val chatId = threadChatId
        if (chatId == 0L) return
        MessageStore.toggleReactionIn(chatId, item, emoji) {
            _error.value = it
        }
    }

    /** Tick or untick one checklist task in a thread
     *  (v0.9.30, W4): markChecklistTasksAsDone against the
     *  thread's own chat id — the discussion group, not the
     *  channel the post came from. Same one-of-two-lists
     *  shape as MessageStore.markChecklistTask; the fresh
     *  state arrives with updateMessageContent and is
     *  re-parsed by this store's own listener. */
    fun markChecklistTask(item: MsgItem, task: ChecklistTaskUi) {
        val chatId = threadChatId
        if (chatId == 0L) return
        val done = JSONArray()
        val notDone = JSONArray()
        if (task.completed) notDone.put(task.id) else done.put(task.id)
        val query = TdClient.request("markChecklistTasksAsDone").apply {
            put("chat_id", chatId)
            put("message_id", item.id)
            put("marked_as_done_task_ids", done)
            put("marked_as_not_done_task_ids", notDone)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _error.value = "清单勾选没成功，再试一次。"
            }
        }
    }

    /** Re-send a failed comment (v0.9.31, X5): the thread
     *  bubble showed 发送失败 with no retry path — the main
     *  chat has had MessageStore.resend since v0.9.9, this
     *  store never grew its own. Same resendMessages shape,
     *  against the discussion group's chat id; success lands
     *  through this store's updateMessageSendSucceeded. */
    fun resend(item: MsgItem) {
        val chatId = threadChatId
        if (chatId == 0L || item.id == 0L) return
        val query = TdClient.request("resendMessages").apply {
            put("chat_id", chatId)
            put("message_ids", JSONArray().put(item.id))
            put("quote", JSONObject.NULL)
            put("paid_message_star_count", 0)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _error.value = "重发失败，再试一次。"
            }
        }
    }

    fun deleteMessage(item: MsgItem) {
        val chatId = threadChatId
        if (chatId == 0L) return
        val query = TdClient.request("deleteMessages").apply {
            put("chat_id", chatId)
            put("message_ids", JSONArray().put(item.id))
            put("revoke", item.outgoing)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _error.value = "删除失败，再试一次。"
            }
        }
    }

    /** Download a comment's file (downloadFile is global; only
     *  the local byId patch differs from MessageStore's). */
    fun downloadFileFor(item: MsgItem) {
        if (item.fileId == 0 || item.filePath != null) return
        // Via the download center's queue, boosted (v0.9.23, P2);
        // message coordinates for the persistent list (T2).
        DownloadStore.enqueue(
            item.fileId, boost = true,
            chatId = threadChatId, messageId = item.id
        )
        val existing = byId[item.id] ?: return
        byId[item.id] = existing.copy(fileDownloading = true)
        publish()
    }

    fun clearError() {
        _error.value = null
    }

    private fun markRead(chatId: Long, messageId: Long) {
        val query = TdClient.request("viewMessages").apply {
            put("chat_id", chatId)
            put("message_ids", JSONArray().put(messageId))
            put(
                "source",
                JSONObject().put("@type", "messageSourceMessageThreadHistory")
            )
            put("force_read", true)
        }
        TdRepository.sendRaw(query) {}
    }

    private fun isOurThreadMessage(msg: JSONObject): Boolean {
        val topic = msg.optJSONObject("topic_id") ?: return false
        return topic.optString("@type") == "messageTopicThread" &&
            topic.optLong("message_thread_id") == rootMessageId
    }

    private fun handleUpdate(update: JSONObject) {
        val chatId = threadChatId
        if (chatId == 0L) return
        when (update.optString("@type")) {
            "updateNewMessage" -> {
                val msg = update.optJSONObject("message") ?: return
                if (msg.optLong("chat_id") != chatId) return
                val item = MessageStore.parseMessage(msg)
                // Accept the thread's own messages; also accept
                // replies into the thread whose topic TDLib did
                // not fill in (they reply to a known comment).
                if (!isOurThreadMessage(msg) &&
                    !byId.containsKey(item.replyToId)
                ) {
                    return
                }
                byId[item.id] = item
                publish()
                if (!item.outgoing) markRead(chatId, item.id)
            }
            "updateMessageSendSucceeded" -> {
                val msg = update.optJSONObject("message") ?: return
                if (msg.optLong("chat_id") != chatId) return
                val item = MessageStore.parseMessage(msg)
                byId.remove(update.optLong("old_message_id"))
                byId[item.id] = item
                publish()
            }
            "updateMessageSendFailed" -> {
                val msg = update.optJSONObject("message") ?: return
                if (msg.optLong("chat_id") != chatId) return
                val oldId = update.optLong("old_message_id")
                val existing = byId[oldId] ?: byId[msg.optLong("id")] ?: return
                byId[existing.id] = existing.copy(sendingState = "failed")
                publish()
            }
            "updateMessageInteractionInfo" -> {
                if (update.optLong("chat_id") != chatId) return
                val messageId = update.optLong("message_id")
                val existing = byId[messageId] ?: return
                val info = update.optJSONObject("interaction_info")
                // reactionsOf is MessageStore-private; reparse
                // via a fresh getMessage is overkill — the chips
                // refresh on the next history page or reopen.
                if (info != null) {
                    TdRepository.sendRaw(TdClient.request("getMessage").apply {
                        put("chat_id", chatId)
                        put("message_id", messageId)
                    }) { fresh ->
                        if (!TdClient.isError(fresh) &&
                            threadChatId == chatId &&
                            byId.containsKey(messageId)
                        ) {
                            byId[messageId] = MessageStore.parseMessage(fresh)
                            publish()
                        }
                    }
                }
            }
            "updateMessageContent", "updateMessageEdited" -> {
                // Content edits and edited-marker flips
                // (v0.9.30, W7 audit fix): this store handled
                // neither update, so inside a thread an edit,
                // a checklist tick (W4) or a live-location move
                // only appeared after reopening the thread,
                // and the 已编辑 marker (v0.9.29) never
                // flipped. Refetch + reparse — the same
                // pattern updateMessageInteractionInfo uses
                // above.
                if (update.optLong("chat_id") != chatId) return
                val messageId = update.optLong("message_id")
                if (!byId.containsKey(messageId)) return
                TdRepository.sendRaw(TdClient.request("getMessage").apply {
                    put("chat_id", chatId)
                    put("message_id", messageId)
                }) { fresh ->
                    if (!TdClient.isError(fresh) &&
                        threadChatId == chatId &&
                        byId.containsKey(messageId)
                    ) {
                        byId[messageId] = MessageStore.parseMessage(fresh)
                        publish()
                    }
                }
            }
            "updatePoll" -> {
                // Same handling as the main chat: the full poll
                // object is pushed on every vote change; match
                // it to this thread's poll messages by poll id.
                val pollObj = update.optJSONObject("poll") ?: return
                val pollId = pollObj.optLong("id")
                var changed = false
                for ((id, item) in byId) {
                    if (item.pollId == pollId && item.pollId != 0L) {
                        byId[id] = item.copy(
                            poll = MessageStore.parsePoll(pollObj)
                        )
                        changed = true
                    }
                }
                if (changed) publish()
            }
            "updateDeleteMessages" -> {
                if (update.optLong("chat_id") != chatId) return
                val ids = update.optJSONArray("message_ids") ?: return
                var changed = false
                for (i in 0 until ids.length()) {
                    if (byId.remove(ids.optLong(i)) != null) changed = true
                }
                if (changed) publish()
            }
            "updateFile" -> {
                val file = update.optJSONObject("file") ?: return
                val local = file.optJSONObject("local") ?: return
                if (!local.optBoolean("is_downloading_completed")) return
                val fileId = file.optInt("id")
                val path = local.optString("path")
                var changed = false
                for ((id, item) in byId) {
                    if (item.fileId == fileId && item.fileId != 0 &&
                        item.filePath == null
                    ) {
                        byId[id] = item.copy(
                            filePath = path, fileDownloading = false
                        )
                        changed = true
                    }
                    if (item.photoFileId == fileId && item.photoPath == null) {
                        byId[id] = item.copy(photoPath = path)
                        changed = true
                    }
                    // Paid media entries (v0.9.35, B1).
                    val paidPatched = item.withPaidFile(fileId, path)
                    if (paidPatched != null) {
                        byId[id] = paidPatched
                        changed = true
                    }
                }
                if (changed) publish()
            }
        }
    }

    // Publish throttling (v0.9.33, Z6): same coalescing as
    // MessageStore — bursts of updates during a history
    // load used to re-sort the whole window per update.
    // Leading edge flushes immediately; the trailing flush
    // rides a throwaway request so it runs on the receiver
    // thread like every other cache access.
    private const val PUBLISH_WINDOW_MS = 50L

    private val publishHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var publishDirty = false

    @Volatile
    private var publishScheduled = false

    @Volatile
    private var lastPublishAt = 0L

    private fun publish() {
        publishDirty = true
        val now = System.currentTimeMillis()
        if (now - lastPublishAt >= PUBLISH_WINDOW_MS) {
            flushPublish()
            return
        }
        if (publishScheduled) return
        publishScheduled = true
        publishHandler.postDelayed({
            publishScheduled = false
            TdRepository.sendRaw(
                TdClient.request("getAuthorizationState")
            ) {
                flushPublish()
            }
        }, PUBLISH_WINDOW_MS)
    }

    private fun flushPublish() {
        if (!publishDirty) return
        publishDirty = false
        lastPublishAt = System.currentTimeMillis()
        _messages.value = byId.values.sortedBy { it.id }
    }
}
