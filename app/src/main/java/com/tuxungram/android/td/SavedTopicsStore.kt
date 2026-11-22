package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** One Saved Messages topic (v0.9.25, R1), flattened from
 *  the savedMessagesTopic object. Topics are NOT arbitrary
 *  user-created folders: TDLib defines exactly three kinds —
 *  "my notes", one per author whose name was hidden, and one
 *  per chat messages were saved from (savedMessagesTopicType*
 *  in td_api.tl, verified). They appear on their own when a
 *  message is forwarded into Saved Messages, so there is no
 *  create-topic API to call; pinning IS offered
 *  (toggleSavedMessagesTopicIsPinned). */
data class SavedTopicUi(
    val id: Long,
    /** "notes" / "author" / "chat". */
    val kind: String,
    /** Source chat for kind == "chat" (0 otherwise). */
    val fromChatId: Long,
    val isPinned: Boolean,
    val order: Long,
    val lastText: String,
    val lastDate: Int
) {
    /** Display title: notes and hidden-author topics have
     *  fixed names; chat topics resolve through ChatStore. */
    val title: String
        get() = when (kind) {
            "notes" -> "我的笔记"
            "author" -> "隐藏来源的转发"
            else -> ChatStore.itemOf(fromChatId)?.title
                ?: "来自对话的收藏"
        }
}

/**
 * Saved Messages topics (v0.9.25, R1). The list is driven the
 * way TDLib intends: loadSavedMessagesTopics kicks the load
 * and topics arrive/patch through updateSavedMessagesTopic;
 * a topic's messages come from getSavedMessagesTopicHistory
 * (paged, newest first, parsed with MessageStore.parseMessage
 * so every content kind renders its usual summary).
 */
object SavedTopicsStore {
    private val _topics = MutableStateFlow<List<SavedTopicUi>>(emptyList())
    val topics: StateFlow<List<SavedTopicUi>> = _topics

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _history = MutableStateFlow<List<MsgItem>>(emptyList())
    val history: StateFlow<List<MsgItem>> = _history

    private val _historyLoading = MutableStateFlow(false)
    val historyLoading: StateFlow<Boolean> = _historyLoading

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    private val byId = LinkedHashMap<Long, SavedTopicUi>()

    @Volatile
    private var attached = false

    @Volatile
    private var openTopicId = 0L

    @Volatile
    private var historyExhausted = false

    fun clearNotice() {
        _notice.value = null
    }

    private fun attach() {
        if (attached) return
        attached = true
        TdRepository.addUpdateListener { update ->
            if (update.optString("@type") ==
                "updateSavedMessagesTopic"
            ) {
                val topic = update.optJSONObject("topic")
                    ?: return@addUpdateListener
                val parsed = parseTopic(topic)
                    ?: return@addUpdateListener
                synchronized(byId) {
                    byId[parsed.id] = parsed
                    publishLocked()
                }
            }
        }
    }

    private fun publishLocked() {
        _topics.value = byId.values.sortedWith(
            compareByDescending<SavedTopicUi> { it.isPinned }
                .thenByDescending { it.order }
        )
    }

    /** (Re)load the topic list: clear and let the updates
     *  stream the topics back in. A 404 from
     *  loadSavedMessagesTopics simply means "all loaded". */
    fun load() {
        attach()
        synchronized(byId) { byId.clear() }
        _topics.value = emptyList()
        _loading.value = true
        val query = TdClient.request("loadSavedMessagesTopics").apply {
            put("limit", 100)
        }
        TdRepository.sendRaw(query) { response ->
            _loading.value = false
            if (TdClient.isError(response) &&
                response.optInt("code") != 404
            ) {
                _notice.value =
                    "收藏话题没拉到，再试一次。（错误码 " +
                        "${response.optInt("code")}）"
            }
        }
    }

    fun togglePin(topic: SavedTopicUi) {
        val query = TdClient.request(
            "toggleSavedMessagesTopicIsPinned"
        ).apply {
            put("saved_messages_topic_id", topic.id)
            put("is_pinned", !topic.isPinned)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _notice.value =
                    "置顶没改成，再试一次。（错误码 " +
                        "${response.optInt("code")}）"
            } else {
                // No dedicated update is guaranteed for the
                // pin flag alone; patch optimistically — a
                // later updateSavedMessagesTopic reconciles.
                synchronized(byId) {
                    byId[topic.id] =
                        topic.copy(isPinned = !topic.isPinned)
                    publishLocked()
                }
            }
        }
    }

    /** Open one topic: reset and page its history in. */
    fun openTopic(topicId: Long) {
        openTopicId = topicId
        historyExhausted = false
        _history.value = emptyList()
        loadMoreHistory()
    }

    fun loadMoreHistory() {
        val topicId = openTopicId
        if (topicId == 0L || historyExhausted ||
            _historyLoading.value
        ) {
            return
        }
        _historyLoading.value = true
        val fromId = _history.value.lastOrNull()?.id ?: 0L
        val query = TdClient.request(
            "getSavedMessagesTopicHistory"
        ).apply {
            put("saved_messages_topic_id", topicId)
            put("from_message_id", fromId)
            put("offset", 0)
            put("limit", 50)
        }
        TdRepository.sendRaw(query) { response ->
            _historyLoading.value = false
            if (openTopicId != topicId) return@sendRaw
            if (TdClient.isError(response)) {
                historyExhausted = true
                if (_history.value.isEmpty()) {
                    _notice.value =
                        "这个话题里的消息没拉到，再试一次。" +
                            "（错误码 ${response.optInt("code")}）"
                }
                return@sendRaw
            }
            val arr = response.optJSONArray("messages")
            val page = ArrayList<MsgItem>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    page.add(MessageStore.parseMessage(m))
                }
            }
            // Same end-of-history rule as the chat pager
            // (D1): only a page with no older message at all
            // ends paging — a short page does not.
            if (page.isEmpty() ||
                page.last().id == fromId && fromId != 0L
            ) {
                historyExhausted = true
            }
            if (page.isNotEmpty()) {
                val known = _history.value.map { it.id }.toSet()
                _history.value = _history.value +
                    page.filter { it.id !in known }
            }
        }
    }

    private fun parseTopic(topic: JSONObject): SavedTopicUi? {
        val id = topic.optLong("id", 0L)
        if (id == 0L) return null
        val type = topic.optJSONObject("type")
        val (kind, fromChat) = when (type?.optString("@type")) {
            "savedMessagesTopicTypeMyNotes" -> "notes" to 0L
            "savedMessagesTopicTypeAuthorHidden" -> "author" to 0L
            "savedMessagesTopicTypeSavedFromChat" ->
                "chat" to (type?.optLong("chat_id", 0L) ?: 0L)
            else -> "chat" to 0L
        }
        val last = topic.optJSONObject("last_message")
        var lastText = ""
        var lastDate = 0
        if (last != null) {
            lastText = MessageStore.parseMessage(last).summaryText()
            lastDate = last.optInt("date")
        }
        return SavedTopicUi(
            id = id,
            kind = kind,
            fromChatId = fromChat,
            isPinned = topic.optBoolean("is_pinned"),
            order = topic.optLong("order"),
            lastText = lastText,
            lastDate = lastDate
        )
    }
}

/** One-line summary of a message for list rows (topic lists,
 *  search results): the text/caption when there is one, else
 *  the same typed placeholder the chat list uses. */
fun MsgItem.summaryText(): String {
    if (text.isNotEmpty()) return text
    return when (kind) {
        MsgKind.PHOTO -> "[图片]"
        MsgKind.VIDEO -> "[视频]"
        MsgKind.DOCUMENT -> fileName.ifEmpty { "[文件]" }
        MsgKind.VOICE -> "[语音]"
        MsgKind.VIDEO_NOTE -> "[圆形视频]"
        MsgKind.STICKER -> "[贴纸]"
        MsgKind.ANIMATION -> "[动图]"
        MsgKind.POLL -> "[投票]"
        MsgKind.LOCATION -> "[位置]"
        MsgKind.CONTACT -> "[联系人]"
        MsgKind.DICE -> "[骰子]"
        MsgKind.GAME -> "[游戏]"
        MsgKind.CHECKLIST -> checklistTitle.ifEmpty { "[清单]" }
        else -> "[消息]"
    }
}
