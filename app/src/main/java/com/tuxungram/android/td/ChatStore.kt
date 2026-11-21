package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One row of the chat list, already flattened for the UI. */
data class ChatItem(
    val id: Long,
    val title: String,
    val isChannel: Boolean,
    val preview: String,
    val dateSeconds: Int,
    val unreadCount: Int,
    val isPinned: Boolean,
    val isMuted: Boolean,
    val order: Long
)

/**
 * Maintains the main chat list the way TDLib intends: loadChats
 * pulls chats into the local database, they arrive and change via
 * updates, and this store keeps the raw chat objects, patches them
 * on each update, and publishes a sorted snapshot. Sorting is by
 * (order, id) descending, exactly as TDLib positions dictate — the
 * UI never re-sorts by time.
 */
object ChatStore {
    private val _chats = MutableStateFlow<List<ChatItem>>(emptyList())
    val chats: StateFlow<List<ChatItem>> = _chats

    private val _allLoaded = MutableStateFlow(false)
    val allLoaded: StateFlow<Boolean> = _allLoaded

    private val cache = ConcurrentHashMap<Long, JSONObject>()

    @Volatile
    private var attached = false

    fun attach() {
        if (attached) return
        attached = true
        TdRepository.addUpdateListener { update -> handleUpdate(update) }
    }

    fun onReady() {
        attach()
        loadMore()
    }

    fun loadMore() {
        if (_allLoaded.value) return
        val query = TdClient.request("loadChats").apply {
            put("chat_list", JSONObject().put("@type", "chatListMain"))
            put("limit", 50)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response) && response.optInt("code") == 404) {
                _allLoaded.value = true
            }
            recompute()
        }
    }

    private fun handleUpdate(update: JSONObject) {
        val chatId = update.optLong("chat_id", 0L)
        when (update.optString("@type")) {
            "updateNewChat" -> {
                val chat = update.optJSONObject("chat") ?: return
                cache[chat.optLong("id")] = chat
            }
            "updateChatLastMessage" -> {
                val chat = cache[chatId] ?: return
                if (update.has("last_message")) {
                    chat.put("last_message", update.opt("last_message"))
                }
                if (update.has("positions")) {
                    chat.put("positions", update.opt("positions"))
                }
            }
            "updateChatPosition" -> {
                val chat = cache[chatId] ?: return
                val position = update.optJSONObject("position") ?: return
                patchPosition(chat, position)
            }
            "updateChatTitle" -> {
                val chat = cache[chatId] ?: return
                chat.put("title", update.optString("title"))
            }
            "updateChatReadInbox" -> {
                val chat = cache[chatId] ?: return
                chat.put("unread_count", update.optInt("unread_count"))
                chat.put(
                    "last_read_inbox_message_id",
                    update.optLong("last_read_inbox_message_id")
                )
            }
            "updateChatDraftMessage" -> {
                val chat = cache[chatId] ?: return
                if (update.has("positions")) {
                    chat.put("positions", update.opt("positions"))
                }
                if (update.has("draft_message")) {
                    chat.put("draft_message", update.opt("draft_message"))
                }
            }
            "updateChatRemovedFromList" -> {
                val list = update.optJSONObject("chat_list") ?: return
                if (list.optString("@type") == "chatListMain") {
                    cache.remove(chatId)
                }
            }
            else -> return
        }
        recompute()
    }

    private fun patchPosition(chat: JSONObject, position: JSONObject) {
        val listType = position.optJSONObject("list")?.optString("@type") ?: return
        val positions = chat.optJSONArray("positions") ?: JSONArray()
        val next = JSONArray()
        var replaced = false
        for (i in 0 until positions.length()) {
            val p = positions.optJSONObject(i) ?: continue
            if (p.optJSONObject("list")?.optString("@type") == listType) {
                next.put(position)
                replaced = true
            } else {
                next.put(p)
            }
        }
        if (!replaced) next.put(position)
        chat.put("positions", next)
    }

    private fun mainPosition(chat: JSONObject): JSONObject? {
        val positions = chat.optJSONArray("positions") ?: return null
        for (i in 0 until positions.length()) {
            val p = positions.optJSONObject(i) ?: continue
            if (p.optJSONObject("list")?.optString("@type") == "chatListMain") return p
        }
        return null
    }

    private fun recompute() {
        val items = ArrayList<ChatItem>()
        for (chat in cache.values) {
            val position = mainPosition(chat) ?: continue
            val order = position.optLong("order")
            if (order == 0L) continue
            items.add(toItem(chat, position, order))
        }
        items.sortWith(compareByDescending<ChatItem> { it.order }.thenByDescending { it.id })
        _chats.value = items
    }

    private fun toItem(chat: JSONObject, position: JSONObject, order: Long): ChatItem {
        val type = chat.optJSONObject("type")
        val isChannel = type?.optString("@type") == "chatTypeSupergroup" &&
            type.optBoolean("is_channel")
        val last = if (chat.has("last_message") && !chat.isNull("last_message")) {
            chat.optJSONObject("last_message")
        } else {
            null
        }
        val muteFor = chat.optJSONObject("notification_settings")
            ?.optInt("mute_for", 0) ?: 0
        return ChatItem(
            id = chat.optLong("id"),
            title = chat.optString("title"),
            isChannel = isChannel,
            preview = previewOf(last),
            dateSeconds = last?.optInt("date", 0) ?: 0,
            unreadCount = chat.optInt("unread_count"),
            isPinned = position.optBoolean("is_pinned"),
            isMuted = muteFor > 0,
            order = order
        )
    }

    private fun previewOf(message: JSONObject?): String {
        if (message == null) return ""
        val content = message.optJSONObject("content")
        val body = when (content?.optString("@type")) {
            "messageText" -> content.optJSONObject("text")?.optString("text") ?: ""
            "messagePhoto" -> {
                val caption = content.optJSONObject("caption")?.optString("text") ?: ""
                if (caption.isEmpty()) "[图片]" else "[图片] $caption"
            }
            null -> ""
            else -> "[消息]"
        }
        if (body.isEmpty()) return ""
        return if (message.optBoolean("is_outgoing")) "你：$body" else body
    }
}
