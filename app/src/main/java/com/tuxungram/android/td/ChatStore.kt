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
    val isGroup: Boolean,
    val preview: String,
    val dateSeconds: Int,
    val unreadCount: Int,
    val isPinned: Boolean,
    val isMuted: Boolean,
    val canSendBasic: Boolean,
    val canSendPhotos: Boolean,
    val order: Long,
    val lastMessageId: Long = 0L,
    val isMarkedAsUnread: Boolean = false,
    val photoFileId: Int = 0,
    val photoPath: String? = null,
    // Reactions this chat allows, from chat.available_reactions
    // (verified in td_api.tl): null = all reactions allowed
    // (chatAvailableReactionsAll or unknown), empty = reactions
    // are off here, otherwise only these emojis may be added.
    // The server answers 400 for anything outside this list.
    val allowedReactions: List<String>? = null
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

    /** Avatar bookkeeping: downloaded small photos by TDLib file id,
     *  downloads already requested, and — for private chats, whose
     *  avatar is the other user's profile photo — chat id -> the
     *  file id of that user's small profile photo. */
    private val photoPathByFileId = ConcurrentHashMap<Int, String>()
    private val photoDownloadPending = ConcurrentHashMap.newKeySet<Int>()
    private val userPhotoFileByChat = ConcurrentHashMap<Long, Int>()
    private val chatIdByUserId = ConcurrentHashMap<Long, Long>()

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

    /** Best-known title for a chat id ("" when unknown); used by
     *  search results, whose messages only carry the chat id. */
    fun titleOf(chatId: Long): String =
        cache[chatId]?.optString("title") ?: ""

    /** A flattened item for any cached chat, whatever list it is
     *  on; used when navigating somewhere from a folder/search. */
    fun itemOf(chatId: Long): ChatItem? {
        val chat = cache[chatId] ?: return null
        val positions = chat.optJSONArray("positions") ?: return null
        for (i in 0 until positions.length()) {
            val p = positions.optJSONObject(i) ?: continue
            if (p.optLong("order") != 0L) return toItem(chat, p, p.optLong("order"))
        }
        return null
    }

    /** True while the main list has not been fully synced yet
     *  (drives the home screen's "syncing" banner). */
    private val _syncing = MutableStateFlow(true)
    val syncing: StateFlow<Boolean> = _syncing

    @Volatile
    private var loadingChats = false
    private var emptyLoadRetries = 0

    fun loadMore() {
        if (_allLoaded.value || loadingChats) return
        loadingChats = true
        val sizeBefore = cache.size
        val query = TdClient.request("loadChats").apply {
            put("chat_list", JSONObject().put("@type", "chatListMain"))
            put("limit", 100)
        }
        TdRepository.sendRaw(query) { response ->
            loadingChats = false
            if (TdClient.isError(response) && response.optInt("code") == 404) {
                // 404 is TDLib's "the list is complete" signal: every
                // chat, however old, has been pulled in.
                _allLoaded.value = true
            }
            recompute()
            _syncing.value = !_allLoaded.value
            // Keep pulling until TDLib says the list is complete, so
            // the chat list is whole even if the user never scrolls.
            // On a FRESH login the cache starts empty and the first
            // calls may bring nothing in while TDLib is still
            // fetching from the server — so the chain also retries
            // a few times without growth before pausing; scrolling
            // and updateNewChat re-trigger it afterwards. The retry
            // bound is a spin guard, not a data cap.
            if (!_allLoaded.value) {
                if (cache.size > sizeBefore) {
                    emptyLoadRetries = 0
                    loadMore()
                } else if (emptyLoadRetries < 5) {
                    emptyLoadRetries++
                    loadMore()
                }
            }
        }
    }

    // ---- Archive list (chatListArchive) ----

    private val _archivedChats = MutableStateFlow<List<ChatItem>>(emptyList())
    val archivedChats: StateFlow<List<ChatItem>> = _archivedChats

    private val _archiveLoaded = MutableStateFlow(false)
    val archiveLoaded: StateFlow<Boolean> = _archiveLoaded

    @Volatile
    private var loadingArchive = false
    private var emptyArchiveRetries = 0

    fun loadArchived() {
        if (_archiveLoaded.value || loadingArchive) return
        loadingArchive = true
        val countBefore = archiveCount()
        val query = TdClient.request("loadChats").apply {
            put(
                "chat_list",
                JSONObject().put("@type", "chatListArchive")
            )
            put("limit", 100)
        }
        TdRepository.sendRaw(query) { response ->
            loadingArchive = false
            if (TdClient.isError(response) && response.optInt("code") == 404) {
                _archiveLoaded.value = true
            }
            recomputeArchive()
            if (!_archiveLoaded.value) {
                if (archiveCount() > countBefore) {
                    emptyArchiveRetries = 0
                    loadArchived()
                } else if (emptyArchiveRetries < 5) {
                    emptyArchiveRetries++
                    loadArchived()
                }
            }
        }
    }

    private fun archivePosition(chat: JSONObject): JSONObject? {
        val positions = chat.optJSONArray("positions") ?: return null
        for (i in 0 until positions.length()) {
            val p = positions.optJSONObject(i) ?: continue
            if (p.optJSONObject("list")?.optString("@type") == "chatListArchive") {
                return p
            }
        }
        return null
    }

    private fun archiveCount(): Int {
        var n = 0
        for (chat in cache.values) {
            val p = archivePosition(chat) ?: continue
            if (p.optLong("order") != 0L) n++
        }
        return n
    }

    private fun recomputeArchive() {
        val items = ArrayList<ChatItem>()
        for (chat in cache.values) {
            val position = archivePosition(chat) ?: continue
            val order = position.optLong("order")
            if (order == 0L) continue
            items.add(toItem(chat, position, order))
        }
        items.sortWith(compareByDescending<ChatItem> { it.order }.thenByDescending { it.id })
        _archivedChats.value = items
    }

    /** Move a chat back out of the archive into the main list. */
    fun unarchive(chat: ChatItem) {
        val query = TdClient.request("addChatToList").apply {
            put("chat_id", chat.id)
            put("chat_list", JSONObject().put("@type", "chatListMain"))
        }
        TdRepository.sendRaw(query) { reportIfError(it) }
    }

    private fun handleUpdate(update: JSONObject) {
        val chatId = update.optLong("chat_id", 0L)
        when (update.optString("@type")) {
            "updateFile" -> {
                val file = update.optJSONObject("file") ?: return
                val local = file.optJSONObject("local") ?: return
                if (!local.optBoolean("is_downloading_completed")) return
                val fileId = file.optInt("id")
                photoDownloadPending.remove(fileId)
                photoPathByFileId[fileId] = local.optString("path")
            }
            "updateNewChat" -> {
                val chat = update.optJSONObject("chat") ?: return
                cache[chat.optLong("id")] = chat
                val type = chat.optJSONObject("type")
                if (type?.optString("@type") == "chatTypePrivate") {
                    val userId = type.optLong("user_id")
                    chatIdByUserId[userId] = chat.optLong("id")
                    fetchUserPhoto(chat.optLong("id"), userId)
                }
                // Fresh-login sync: chats arriving on their own
                // mean TDLib is actively fetching — keep the
                // loadChats chain going until it answers 404.
                if (!_allLoaded.value) {
                    emptyLoadRetries = 0
                    loadMore()
                }
            }
            "updateUser" -> {
                val user = update.optJSONObject("user") ?: return
                val chatIdForUser = chatIdByUserId[user.optLong("id")] ?: return
                val smallId = user.optJSONObject("profile_photo")
                    ?.optJSONObject("small")?.optInt("id", 0) ?: 0
                if (smallId != 0) {
                    userPhotoFileByChat[chatIdForUser] = smallId
                    ensurePhotoDownload(smallId)
                }
            }
            "updateChatPhoto" -> {
                val chat = cache[chatId] ?: return
                if (update.has("photo") && !update.isNull("photo")) {
                    chat.put("photo", update.opt("photo"))
                } else {
                    chat.remove("photo")
                }
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
            "updateChatReadOutbox" -> {
                val chat = cache[chatId] ?: return
                chat.put(
                    "last_read_outbox_message_id",
                    update.optLong("last_read_outbox_message_id")
                )
            }
            "updateChatNotificationSettings" -> {
                val chat = cache[chatId] ?: return
                if (update.has("notification_settings")) {
                    chat.put(
                        "notification_settings",
                        update.opt("notification_settings")
                    )
                }
            }
            "updateChatAvailableReactions" -> {
                val chat = cache[chatId] ?: return
                if (update.has("available_reactions")) {
                    chat.put(
                        "available_reactions",
                        update.opt("available_reactions")
                    )
                }
            }
            "updateChatIsMarkedAsUnread" -> {
                val chat = cache[chatId] ?: return
                chat.put(
                    "is_marked_as_unread",
                    update.optBoolean("is_marked_as_unread")
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
                // Removal from the archive list is reflected by
                // recomputeArchive() below; the chat itself stays
                // cached for the main list.
            }
            else -> return
        }
        recompute()
        recomputeArchive()
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
        val typeName = type?.optString("@type")
        val isChannel = typeName == "chatTypeSupergroup" &&
            type.optBoolean("is_channel")
        val isGroup = typeName == "chatTypeBasicGroup" ||
            (typeName == "chatTypeSupergroup" && !isChannel)
        val last = if (chat.has("last_message") && !chat.isNull("last_message")) {
            chat.optJSONObject("last_message")
        } else {
            null
        }
        val muteFor = chat.optJSONObject("notification_settings")
            ?.optInt("mute_for", 0) ?: 0
        val permissions = chat.optJSONObject("permissions")
        return ChatItem(
            id = chat.optLong("id"),
            title = chat.optString("title"),
            isChannel = isChannel,
            isGroup = isGroup,
            preview = previewOf(last),
            dateSeconds = last?.optInt("date", 0) ?: 0,
            unreadCount = chat.optInt("unread_count"),
            isPinned = position.optBoolean("is_pinned"),
            isMuted = muteFor > 0,
            canSendBasic = permissions?.optBoolean("can_send_basic_messages", true) ?: true,
            canSendPhotos = permissions?.optBoolean("can_send_photos", true) ?: true,
            order = order,
            lastMessageId = last?.optLong("id", 0L) ?: 0L,
            isMarkedAsUnread = chat.optBoolean("is_marked_as_unread"),
            photoFileId = avatarFileId(chat),
            photoPath = avatarFileId(chat).let { id ->
                if (id == 0) {
                    null
                } else {
                    ensurePhotoDownload(id)
                    photoPathByFileId[id]
                }
            },
            allowedReactions = allowedReactionsOf(chat)
        )
    }

    /** Parse chat.available_reactions: All (or a missing field)
     *  means unrestricted -> null; Some carries the explicit
     *  reactionType list, of which only plain emojis are usable
     *  in the picker. */
    private fun allowedReactionsOf(chat: JSONObject): List<String>? {
        val available = chat.optJSONObject("available_reactions") ?: return null
        return when (available.optString("@type")) {
            "chatAvailableReactionsSome" -> {
                val arr = available.optJSONArray("reactions") ?: JSONArray()
                val out = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val type = arr.optJSONObject(i) ?: continue
                    if (type.optString("@type") == "reactionTypeEmoji") {
                        val emoji = type.optString("emoji")
                        if (emoji.isNotEmpty()) out.add(emoji)
                    }
                }
                out
            }
            else -> null
        }
    }

    /** The chat's own small photo (chatPhotoInfo.small, verified in
     *  td_api.tl); private chats fall back to the other user's
     *  profile photo fetched via getUser. */
    private fun avatarFileId(chat: JSONObject): Int {
        val own = chat.optJSONObject("photo")
            ?.optJSONObject("small")?.optInt("id", 0) ?: 0
        if (own != 0) return own
        return userPhotoFileByChat[chat.optLong("id")] ?: 0
    }

    private fun fetchUserPhoto(chatId: Long, userId: Long) {
        if (userPhotoFileByChat.containsKey(chatId)) return
        val query = TdClient.request("getUser").apply { put("user_id", userId) }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) return@sendRaw
            val smallId = response.optJSONObject("profile_photo")
                ?.optJSONObject("small")?.optInt("id", 0) ?: 0
            if (smallId != 0) {
                userPhotoFileByChat[chatId] = smallId
                ensurePhotoDownload(smallId)
                recompute()
            }
        }
    }

    private fun ensurePhotoDownload(fileId: Int) {
        if (fileId == 0) return
        if (photoPathByFileId.containsKey(fileId)) return
        if (!photoDownloadPending.add(fileId)) return
        TdRepository.sendRaw(TdClient.request("downloadFile").apply {
            put("file_id", fileId)
            put("priority", 1)
            put("offset", 0L)
            put("limit", 0L)
            put("synchronous", false)
        }) {}
    }

    // ---- Chat-list long-press actions (all verified in td_api.tl) ----

    private fun reportIfError(response: JSONObject) {
        if (TdClient.isError(response)) {
            TdRepository.reportError(
                response.optInt("code") to response.optString("message")
            )
        }
    }

    fun togglePin(chat: ChatItem) {
        val query = TdClient.request("toggleChatIsPinned").apply {
            put("chat_list", JSONObject().put("@type", "chatListMain"))
            put("chat_id", chat.id)
            put("is_pinned", !chat.isPinned)
        }
        TdRepository.sendRaw(query) { reportIfError(it) }
    }

    /** Mute "forever" is a far-future mute_for; unmuting sets it to
     *  0. Every other setting stays on its default (use_default_*
     *  flags), per the chatNotificationSettings field list. */
    fun toggleMute(chat: ChatItem) {
        val settings = JSONObject().apply {
            put("@type", "chatNotificationSettings")
            put("use_default_mute_for", false)
            put("mute_for", if (chat.isMuted) 0 else Int.MAX_VALUE)
            put("use_default_sound", true)
            put("sound_id", 0L)
            put("use_default_show_preview", true)
            put("show_preview", false)
            put("use_default_mute_stories", true)
            put("mute_stories", false)
            put("use_default_story_sound", true)
            put("story_sound_id", 0L)
            put("use_default_show_story_poster", true)
            put("show_story_poster", false)
            put("use_default_disable_pinned_message_notifications", true)
            put("disable_pinned_message_notifications", false)
            put("use_default_disable_mention_notifications", true)
            put("disable_mention_notifications", false)
        }
        val query = TdClient.request("setChatNotificationSettings").apply {
            put("chat_id", chat.id)
            put("notification_settings", settings)
        }
        TdRepository.sendRaw(query) { reportIfError(it) }
    }

    /** Mark a chat read from the list: view its newest message with
     *  the chat-list source; unread counts then sync from TDLib. */
    fun markRead(chat: ChatItem) {
        if (chat.lastMessageId == 0L) return
        val query = TdClient.request("viewMessages").apply {
            put("chat_id", chat.id)
            put("message_ids", JSONArray().put(chat.lastMessageId))
            put("source", JSONObject().put("@type", "messageSourceChatList"))
            put("force_read", true)
        }
        TdRepository.sendRaw(query) { reportIfError(it) }
    }

    /** Archive: move the chat to the archive list (addChatToList
     *  with chatListArchive). TDLib then drops it from the main
     *  list positions and this store stops showing it. */
    fun archive(chat: ChatItem) {
        val query = TdClient.request("addChatToList").apply {
            put("chat_id", chat.id)
            put("chat_list", JSONObject().put("@type", "chatListArchive"))
        }
        TdRepository.sendRaw(query) { reportIfError(it) }
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
