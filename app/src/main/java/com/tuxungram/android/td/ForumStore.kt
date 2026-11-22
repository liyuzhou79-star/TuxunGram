package com.tuxungram.android.td

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
    val iconColor: Int = 0
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
        val icon = info.optJSONObject("icon")
        val iconType = icon?.optJSONObject("type")
        val color = if (
            iconType?.optString("@type") == "forumTopicIconGeneric"
        ) {
            iconType.optInt("color")
        } else {
            0
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
            iconColor = color
        )
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

    @Volatile
    private var allLoaded = false

    @Volatile
    private var loading = false

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
        val query = TdClient.request("getForumTopicHistory").apply {
            put("chat_id", chatId)
            put("forum_topic_id", topicId)
            put("from_message_id", fromMessageId)
            put("offset", 0)
            put("limit", 50)
        }
        TdRepository.sendRaw(query) { response ->
            loading = false
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

    private fun publish() {
        _messages.value = byId.values.sortedBy { it.id }
    }
}
