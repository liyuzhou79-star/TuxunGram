package com.tuxungram.android.td

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One forum topic row, flattened from forumTopic /
 *  forumTopicInfo (verified in td_api.tl). */
data class ForumTopicUi(
    val topicId: Int,
    val name: String,
    val isGeneral: Boolean,
    val isClosed: Boolean,
    val unreadCount: Int,
    val isPinned: Boolean,
    val lastText: String,
    val lastDate: Int,
    val iconColor: Int = 0,
    /** The topic creator's user id (forumTopicInfo.creator_id,
     *  v0.9.15 V5) — a topic can be managed by its creator or
     *  by an admin holding can_manage_topics. 0 when the
     *  sender was not a user (channel-created topics). */
    val creatorId: Long = 0L
)

/**
 * Forum supergroups (v0.8.0, G2): topic list + one topic's
 * message stream. Interfaces verified in td_api.tl:
 * getSupergroup.is_forum for detection, getForumTopics for the
 * list (offset triple from the last topic of the previous
 * page), getForumTopicHistory for a topic's messages, and
 * sendMessage with topic_id = messageTopicForum for posting.
 * Paging follows the D1 rule (a short page is not the end).
 */
object ForumStore {
    // ---- Detection ----

    private val _isForum = MutableStateFlow(false)
    val isForum: StateFlow<Boolean> = _isForum

    @Volatile
    private var forumCheckedFor = 0L

    fun checkForum(chatId: Long) {
        if (forumCheckedFor == chatId) return
        forumCheckedFor = chatId
        _isForum.value = false
        val query = TdClient.request("getChat").apply {
            put("chat_id", chatId)
        }
        TdRepository.sendRaw(query) { chat ->
            if (TdClient.isError(chat)) return@sendRaw
            val type = chat.optJSONObject("type") ?: return@sendRaw
            if (type.optString("@type") != "chatTypeSupergroup") {
                return@sendRaw
            }
            val sg = TdClient.request("getSupergroup").apply {
                put("supergroup_id", type.optLong("supergroup_id"))
            }
            TdRepository.sendRaw(sg) { info ->
                if (!TdClient.isError(info) && forumCheckedFor == chatId) {
                    _isForum.value = info.optBoolean("is_forum")
                }
            }
        }
    }

    // ---- Topic list ----

    private val _topics = MutableStateFlow<List<ForumTopicUi>>(emptyList())
    val topics: StateFlow<List<ForumTopicUi>> = _topics

    private val _topicsLoading = MutableStateFlow(false)
    val topicsLoading: StateFlow<Boolean> = _topicsLoading

    @Volatile
    private var topicsChatId = 0L

    @Volatile
    private var topicsExhausted = false

    @Volatile
    private var topicsPaging = false

    // Offset triple of the last fetched topic.
    @Volatile
    private var offDate = 0

    @Volatile
    private var offMessageId = 0L

    @Volatile
    private var offTopicId = 0

    fun loadTopics(chatId: Long) {
        if (topicsChatId != chatId) {
            topicsChatId = chatId
            _topics.value = emptyList()
            topicsExhausted = false
            offDate = 0
            offMessageId = 0L
            offTopicId = 0
        }
        pageTopics()
    }

    fun loadMoreTopics() {
        if (!topicsExhausted) pageTopics()
    }

    private fun pageTopics() {
        val chatId = topicsChatId
        if (chatId == 0L || topicsPaging) return
        topicsPaging = true
        _topicsLoading.value = true
        val query = TdClient.request("getForumTopics").apply {
            put("chat_id", chatId)
            put("query", "")
            put("offset_date", offDate)
            put("offset_message_id", offMessageId)
            put("offset_forum_topic_id", offTopicId)
            put("limit", 50)
        }
        TdRepository.sendRaw(query) { response ->
            topicsPaging = false
            _topicsLoading.value = false
            if (topicsChatId != chatId) return@sendRaw
            if (TdClient.isError(response)) {
                topicsExhausted = true
                return@sendRaw
            }
            val arr = response.optJSONArray("topics") ?: JSONArray()
            val known = _topics.value.map { it.topicId }.toHashSet()
            val fresh = ArrayList<ForumTopicUi>()
            var lastRaw: JSONObject? = null
            for (i in 0 until arr.length()) {
                val topic = arr.optJSONObject(i) ?: continue
                lastRaw = topic
                val ui = parseTopic(topic) ?: continue
                if (known.add(ui.topicId)) fresh.add(ui)
            }
            if (lastRaw != null) {
                val lastMsg = lastRaw.optJSONObject("last_message")
                offDate = lastMsg?.optInt("date") ?: 0
                offMessageId = lastMsg?.optLong("id") ?: 0L
                offTopicId = lastRaw.optJSONObject("info")
                    ?.optInt("forum_topic_id") ?: 0
            }
            if (fresh.isEmpty()) {
                topicsExhausted = true
            } else {
                _topics.value = _topics.value + fresh
            }
        }
    }

    private fun parseTopic(topic: JSONObject): ForumTopicUi? {
        val info = topic.optJSONObject("info") ?: return null
        val lastMsg = topic.optJSONObject("last_message")
        val lastText = if (lastMsg != null) {
            val parsed = MessageStore.parseMessage(lastMsg)
            parsed.text.ifEmpty {
                when (parsed.kind) {
                    MsgKind.PHOTO -> "[图片]"
                    MsgKind.VIDEO -> "[视频]"
                    MsgKind.VOICE -> "[语音]"
                    MsgKind.DOCUMENT -> "[文件]"
                    MsgKind.STICKER -> "[贴纸]"
                    MsgKind.ANIMATION -> "[动图]"
                    MsgKind.POLL -> "[投票]"
                    else -> ""
                }
            }
        } else {
            ""
        }
        // forumTopicIcon is FLAT in this tl (color +
        // custom_emoji_id, no nested "type" object) — the
        // pre-v0.9.15 code looked for a forumTopicIconGeneric
        // wrapper that does not exist here, so the color was
        // always 0. The stored color gets an opaque alpha so
        // Color(iconColor) renders it directly; the wire value
        // is plain RGB.
        val icon = info.optJSONObject("icon")
        val rawColor = if (
            icon != null && icon.optLong("custom_emoji_id") == 0L
        ) {
            icon.optInt("color")
        } else {
            0
        }
        val creator = info.optJSONObject("creator_id")
        val creatorId = if (
            creator?.optString("@type") == "messageSenderUser"
        ) {
            creator.optLong("user_id")
        } else {
            0L
        }
        return ForumTopicUi(
            topicId = info.optInt("forum_topic_id"),
            name = info.optString("name").ifEmpty {
                if (info.optBoolean("is_general")) "General" else "话题"
            },
            isGeneral = info.optBoolean("is_general"),
            isClosed = info.optBoolean("is_closed"),
            unreadCount = topic.optInt("unread_count"),
            isPinned = topic.optBoolean("is_pinned"),
            lastText = lastText,
            lastDate = lastMsg?.optInt("date") ?: info.optInt("creation_date"),
            iconColor = if (rawColor != 0) {
                rawColor or (0xFF shl 24)
            } else {
                0
            },
            creatorId = creatorId
        )
    }

    // ---- Topic management (v0.9.15, V5) ----
    // Interfaces verified in td_api.tl: createForumTopic
    // (needs the can_create_topics member right or
    // can_manage_topics admin right), editForumTopic,
    // toggleForumTopicIsClosed (both directions — there is
    // no separate reopen call) and deleteForumTopic.
    // forumTopicInfo carries NO per-topic rights, so the
    // gates are composed from the viewer's chat rights plus
    // topic ownership, and every server refusal is mapped to
    // a plain sentence on manageError.

    private val _canCreateTopics = MutableStateFlow(false)
    val canCreateTopics: StateFlow<Boolean> = _canCreateTopics

    private val _canManageTopics = MutableStateFlow(false)
    val canManageTopics: StateFlow<Boolean> = _canManageTopics

    private val _myUserId = MutableStateFlow(0L)
    val myUserId: StateFlow<Long> = _myUserId

    private val _manageError = MutableStateFlow<String?>(null)
    val manageError: StateFlow<String?> = _manageError

    @Volatile
    private var rightsChatId = 0L

    fun clearManageError() {
        _manageError.value = null
    }

    fun loadViewerRights(chatId: Long) {
        if (rightsChatId == chatId) return
        rightsChatId = chatId
        _canCreateTopics.value = false
        _canManageTopics.value = false
        TdRepository.sendRaw(TdClient.request("getMe")) { me ->
            if (TdClient.isError(me)) return@sendRaw
            val myId = me.optLong("id")
            if (myId == 0L) return@sendRaw
            _myUserId.value = myId
            TdRepository.sendRaw(TdClient.request("getChat").apply {
                put("chat_id", chatId)
            }) { chat ->
                if (!TdClient.isError(chat) &&
                    chat.optJSONObject("permissions")
                        ?.optBoolean("can_create_topics") == true
                ) {
                    _canCreateTopics.value = true
                }
            }
            val memberQuery = TdClient.request("getChatMember").apply {
                put("chat_id", chatId)
                put("member_id", JSONObject().apply {
                    put("@type", "messageSenderUser")
                    put("user_id", myId)
                })
            }
            TdRepository.sendRaw(memberQuery) { member ->
                if (TdClient.isError(member)) return@sendRaw
                val status = member.optJSONObject("status")
                    ?: return@sendRaw
                when (status.optString("@type")) {
                    "chatMemberStatusCreator" -> {
                        _canManageTopics.value = true
                        _canCreateTopics.value = true
                    }
                    "chatMemberStatusAdministrator" -> {
                        if (status.optJSONObject("rights")
                                ?.optBoolean("can_manage_topics") == true
                        ) {
                            _canManageTopics.value = true
                        }
                    }
                }
            }
        }
    }

    /** Whether the viewer may manage [topic]: an admin with
     *  can_manage_topics, or the topic's own creator. The
     *  General topic is never manageable (the server pins its
     *  name and keeps it undeletable). */
    fun canManageTopic(topic: ForumTopicUi): Boolean {
        if (topic.isGeneral) return false
        if (_canManageTopics.value) return true
        val me = _myUserId.value
        return me != 0L && topic.creatorId == me
    }

    private fun reloadTopicsFromTop() {
        topicsExhausted = false
        offDate = 0
        offMessageId = 0L
        offTopicId = 0
        _topics.value = emptyList()
        pageTopics()
    }

    private fun patchTopic(topicId: Int, change: (ForumTopicUi) -> ForumTopicUi) {
        _topics.value = _topics.value.map {
            if (it.topicId == topicId) change(it) else it
        }
    }

    fun createTopic(name: String) {
        val chatId = topicsChatId
        val trimmed = name.trim()
        if (chatId == 0L || trimmed.isEmpty()) return
        // Telegram's generic topic colors (RGB), picked
        // deterministically from the name so retries agree.
        val palette = intArrayOf(
            0x6FB9F0, 0xFFD67E, 0xCB86DB,
            0x8EEE98, 0xFF93B2, 0xFB6F5F
        )
        val color = palette[
            (trimmed.hashCode() and Int.MAX_VALUE) % palette.size
        ]
        val query = TdClient.request("createForumTopic").apply {
            put("chat_id", chatId)
            put("name", trimmed)
            put("is_name_implicit", false)
            put("icon", JSONObject().apply {
                put("@type", "forumTopicIcon")
                put("color", color)
                put("custom_emoji_id", 0L)
            })
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _manageError.value =
                    "话题没建好：可能是权限不够，或者名字不合要求。"
            } else {
                reloadTopicsFromTop()
            }
        }
    }

    fun renameTopic(topic: ForumTopicUi, name: String) {
        val chatId = topicsChatId
        val trimmed = name.trim()
        if (chatId == 0L || trimmed.isEmpty()) return
        val query = TdClient.request("editForumTopic").apply {
            put("chat_id", chatId)
            put("forum_topic_id", topic.topicId)
            put("name", trimmed)
            put("edit_icon_custom_emoji", false)
            put("icon_custom_emoji_id", 0L)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _manageError.value =
                    "改名没成功：可能是权限不够，或服务器拒绝了。"
            } else {
                patchTopic(topic.topicId) { it.copy(name = trimmed) }
            }
        }
    }

    fun setTopicClosed(topic: ForumTopicUi, closed: Boolean) {
        val chatId = topicsChatId
        if (chatId == 0L) return
        val query = TdClient.request("toggleForumTopicIsClosed").apply {
            put("chat_id", chatId)
            put("forum_topic_id", topic.topicId)
            put("is_closed", closed)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _manageError.value = if (closed) {
                    "关闭没成功：可能是权限不够，或服务器拒绝了。"
                } else {
                    "重新打开没成功：可能是权限不够，或服务器拒绝了。"
                }
            } else {
                patchTopic(topic.topicId) {
                    it.copy(isClosed = closed)
                }
            }
        }
    }

    fun deleteTopic(topic: ForumTopicUi) {
        val chatId = topicsChatId
        if (chatId == 0L) return
        val query = TdClient.request("deleteForumTopic").apply {
            put("chat_id", chatId)
            put("forum_topic_id", topic.topicId)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _manageError.value =
                    "删除没成功：可能是权限不够，或服务器拒绝了。"
            } else {
                _topics.value = _topics.value.filter {
                    it.topicId != topic.topicId
                }
            }
        }
    }

    // ---- One topic's messages ----

    private val _messages = MutableStateFlow<List<MsgItem>>(emptyList())
    val messages: StateFlow<List<MsgItem>> = _messages

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _replyTarget = MutableStateFlow<MsgItem?>(null)
    val replyTarget: StateFlow<MsgItem?> = _replyTarget

    @Volatile
    var topicChatId = 0L
        private set

    @Volatile
    var openTopicId = 0
        private set

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

    @Volatile
    private var attached = false

    private fun attach() {
        if (attached) return
        attached = true
        TdRepository.addUpdateListener { update -> handleUpdate(update) }
    }

    fun openTopic(chatId: Long, topicId: Int) {
        attach()
        topicChatId = chatId
        openTopicId = topicId
        byId.clear()
        _messages.value = emptyList()
        _error.value = null
        _replyTarget.value = null
        allLoaded = false
        TdRepository.sendRaw(TdClient.request("openChat").apply {
            put("chat_id", chatId)
        }) {}
        loadHistory(fromMessageId = 0L)
    }

    fun closeTopic() {
        topicChatId = 0L
        openTopicId = 0
        byId.clear()
        _messages.value = emptyList()
    }

    fun loadOlder() {
        if (allLoaded || loading) return
        val oldest = byId.keys.minOrNull() ?: return
        loadHistory(fromMessageId = oldest)
    }

    private fun loadHistory(fromMessageId: Long) {
        val chatId = topicChatId
        val topicId = openTopicId
        if (chatId == 0L || topicId == 0 || loading) return
        loading = true
        _historyLoading.value = true
        val query = TdClient.request("getForumTopicHistory").apply {
            put("chat_id", chatId)
            put("forum_topic_id", topicId)
            put("from_message_id", fromMessageId)
            put("offset", 0)
            put("limit", 50)
        }
        TdRepository.sendRaw(query) { response ->
            loading = false
            _historyLoading.value = false
            if (topicChatId != chatId || openTopicId != topicId) {
                return@sendRaw
            }
            if (TdClient.isError(response)) return@sendRaw
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
                if (!addedOlder) allLoaded = true
            } else if (arr.length() == 0) {
                allLoaded = true
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

    /** Send a picked photo into the topic (v0.9.1, J2: media
     *  sends use the main chat's content builders, only the
     *  topic wiring differs). */
    fun sendPhoto(path: String, caption: String) {
        sendWithContent(MessageStore.photoContent(path, caption))
    }

    /** Send a picked file / video / voice note into the topic. */
    fun sendFile(path: String, mimeType: String, fileName: String) {
        sendWithContent(MessageStore.fileContent(path, mimeType, fileName))
    }

    private fun sendWithContent(content: JSONObject) {
        val chatId = topicChatId
        val topicId = openTopicId
        if (chatId == 0L || topicId == 0) return
        val target = _replyTarget.value
        _replyTarget.value = null
        val query = TdClient.request("sendMessage").apply {
            put("chat_id", chatId)
            put("topic_id", JSONObject().apply {
                put("@type", "messageTopicForum")
                put("forum_topic_id", topicId)
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
            if (topicChatId != chatId) return@sendRaw
            if (TdClient.isError(response)) {
                _error.value = "没发出去，再试一次。"
            } else {
                val item = MessageStore.parseMessage(response)
                byId[item.id] = item
                publish()
            }
        }
    }

    /** Vote in a poll posted inside the topic (v0.9.1, J2):
     *  same setPollAnswer route as the main chat; results come
     *  back through updatePoll below. */
    fun setPollAnswer(item: MsgItem, optionIds: List<Int>) {
        val chatId = topicChatId
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
        val chatId = topicChatId
        if (chatId == 0L) return
        MessageStore.toggleReactionIn(chatId, item, emoji) {
            _error.value = it
        }
    }

    /** Tick or untick one checklist task inside a forum
     *  topic (v0.9.30, W4): markChecklistTasksAsDone against
     *  the topic's chat id; same shape as the main chat and
     *  thread versions, fresh state via updateMessageContent. */
    fun markChecklistTask(item: MsgItem, task: ChecklistTaskUi) {
        val chatId = topicChatId
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

    /** Re-send a failed topic message (v0.9.31, X5): same
     *  gap as the thread store — the bubble flagged 发送失败
     *  but offered no retry. resendMessages against the
     *  forum chat id; success lands through this store's
     *  updateMessageSendSucceeded. */
    fun resend(item: MsgItem) {
        val chatId = topicChatId
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
        val chatId = topicChatId
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

    fun downloadFileFor(item: MsgItem) {
        if (item.fileId == 0 || item.filePath != null) return
        // Via the download center's queue, boosted (v0.9.23, P2);
        // message coordinates for the persistent list (T2).
        DownloadStore.enqueue(
            item.fileId, boost = true,
            chatId = topicChatId, messageId = item.id
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
                JSONObject().put("@type", "messageSourceForumTopicHistory")
            )
            put("force_read", true)
        }
        TdRepository.sendRaw(query) {}
    }

    private fun isOurTopicMessage(msg: JSONObject): Boolean {
        val topic = msg.optJSONObject("topic_id") ?: return false
        return topic.optString("@type") == "messageTopicForum" &&
            topic.optInt("forum_topic_id") == openTopicId
    }

    private fun handleUpdate(update: JSONObject) {
        val chatId = topicChatId
        if (chatId == 0L || openTopicId == 0) return
        when (update.optString("@type")) {
            "updateNewMessage" -> {
                val msg = update.optJSONObject("message") ?: return
                if (msg.optLong("chat_id") != chatId) return
                if (!isOurTopicMessage(msg)) return
                val item = MessageStore.parseMessage(msg)
                byId[item.id] = item
                publish()
                if (!item.outgoing) markRead(chatId, item.id)
            }
            "updateMessageSendSucceeded" -> {
                val msg = update.optJSONObject("message") ?: return
                if (msg.optLong("chat_id") != chatId) return
                if (!isOurTopicMessage(msg)) return
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
                if (!byId.containsKey(messageId)) return
                TdRepository.sendRaw(TdClient.request("getMessage").apply {
                    put("chat_id", chatId)
                    put("message_id", messageId)
                }) { fresh ->
                    if (!TdClient.isError(fresh) &&
                        topicChatId == chatId &&
                        byId.containsKey(messageId)
                    ) {
                        byId[messageId] = MessageStore.parseMessage(fresh)
                        publish()
                    }
                }
            }
            "updateMessageContent", "updateMessageEdited" -> {
                // Content edits and edited-marker flips
                // (v0.9.30, W7 audit fix): same gap and same
                // refetch + reparse fix as ThreadStore — edits,
                // checklist ticks (W4) and the 已编辑 marker
                // inside a topic previously needed a reopen.
                if (update.optLong("chat_id") != chatId) return
                val messageId = update.optLong("message_id")
                if (!byId.containsKey(messageId)) return
                TdRepository.sendRaw(TdClient.request("getMessage").apply {
                    put("chat_id", chatId)
                    put("message_id", messageId)
                }) { fresh ->
                    if (!TdClient.isError(fresh) &&
                        topicChatId == chatId &&
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
                // it to this topic's poll messages by poll id.
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
