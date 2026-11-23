package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One row of the "who saw this message" list (v0.9.13, T1):
 *  a viewer of one of my messages, from messageViewer in
 *  td_api.tl — [viewDate] is the approximate unix time the
 *  message was viewed. */
data class ViewerUi(
    val userId: Long,
    val name: String,
    val photoPath: String?,
    val viewDate: Int
)

/** One row of a poll option's voter list (v0.9.32, Y3),
 *  from pollVoter in td_api.tl. The voter is a message
 *  sender: usually a user, but a chat when a channel voted
 *  as itself — [isUser] says which, and [senderId] is the
 *  user id or the chat id accordingly. [voteDate] is when
 *  the vote was cast. */
data class PollVoterUi(
    val senderId: Long,
    val isUser: Boolean,
    val name: String,
    val photoPath: String?,
    val voteDate: Int
)

/** One row of a message's per-user reaction list
 *  (v0.9.39, F1), from addedReaction in td_api.tl. The
 *  reactor is a message sender: usually a user, but a
 *  chat when a channel reacted as itself. [kind] is the
 *  reaction family — "emoji" ([emoji] carries the
 *  character), "custom" ([customEmojiId] carries the
 *  custom emoji) or "paid" (a paid star reaction; the
 *  list labels it instead of drawing a fake glyph).
 *  [date] is when the reaction was added. */
data class AddedReactionUi(
    val senderId: Long,
    val isUser: Boolean,
    val name: String,
    val photoPath: String?,
    val kind: String,
    val emoji: String,
    val customEmojiId: Long,
    val isOutgoing: Boolean,
    val date: Int
)

/**
 * Read-by list for one message (v0.9.13, T1), over
 * getMessageViewers (verified in td_api.tl). The call only
 * answers in the scenes the server supports — own messages
 * in groups and channels; too-old messages, privacy settings
 * and unsupported chat types come back as errors, and those
 * are translated into plain language here instead of being
 * hidden. Names and avatars resolve lazily through getUser +
 * AvatarStore, the same machinery the member lists use.
 */
object ViewersStore {
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _viewers = MutableStateFlow<List<ViewerUi>>(emptyList())
    val viewers: StateFlow<List<ViewerUi>> = _viewers

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** The (chat, message) the current state belongs to; a
     *  late answer for a message the user has moved on from
     *  is dropped. */
    @Volatile
    private var forKey: Pair<Long, Long>? = null

    private val nameByUser = ConcurrentHashMap<Long, String>()
    private val namePending = ConcurrentHashMap.newKeySet<Long>()

    @Volatile
    private var listenerAttached = false

    private fun attachListener() {
        if (listenerAttached) return
        listenerAttached = true
        // Avatar downloads land asynchronously; refresh the
        // rows' photo paths as they complete.
        AvatarStore.addListener { refreshPhotos() }
    }

    fun load(chatId: Long, messageId: Long) {
        attachListener()
        forKey = chatId to messageId
        _loading.value = true
        _viewers.value = emptyList()
        _error.value = null
        val query = TdClient.request("getMessageViewers").apply {
            put("chat_id", chatId)
            put("message_id", messageId)
        }
        TdRepository.sendRaw(query) { response ->
            if (forKey != (chatId to messageId)) return@sendRaw
            _loading.value = false
            if (TdClient.isError(response)) {
                _error.value = errorText(response)
                return@sendRaw
            }
            val arr = response.optJSONArray("viewers") ?: JSONArray()
            val list = ArrayList<ViewerUi>()
            for (i in 0 until arr.length()) {
                val v = arr.optJSONObject(i) ?: continue
                val userId = v.optLong("user_id")
                if (userId == 0L) continue
                list.add(
                    ViewerUi(
                        userId = userId,
                        name = nameByUser[userId] ?: "",
                        photoPath = AvatarStore.pathForUser(userId),
                        viewDate = v.optInt("view_date")
                    )
                )
            }
            _viewers.value = list.sortedByDescending { it.viewDate }
            for (viewer in list) {
                if (viewer.name.isEmpty()) resolveName(viewer.userId)
            }
        }
    }

    private fun resolveName(userId: Long) {
        if (!namePending.add(userId)) return
        val query = TdClient.request("getUser").apply {
            put("user_id", userId)
        }
        TdRepository.sendRaw(query) { user ->
            namePending.remove(userId)
            if (TdClient.isError(user)) return@sendRaw
            AvatarStore.noteUser(user)
            val name = listOf(
                user.optString("first_name"), user.optString("last_name")
            ).filter { it.isNotEmpty() }.joinToString(" ")
            if (name.isNotEmpty()) nameByUser[userId] = name
            patch(userId)
        }
    }

    private fun patch(userId: Long) {
        val name = nameByUser[userId] ?: ""
        val path = AvatarStore.pathForUser(userId)
        _viewers.value = _viewers.value.map { viewer ->
            if (viewer.userId == userId &&
                (viewer.name != name || viewer.photoPath != path)
            ) {
                viewer.copy(name = name, photoPath = path)
            } else {
                viewer
            }
        }
    }

    private fun refreshPhotos() {
        _viewers.value = _viewers.value.map { viewer ->
            val path = AvatarStore.pathForUser(viewer.userId)
            if (path != viewer.photoPath) {
                viewer.copy(photoPath = path)
            } else {
                viewer
            }
        }
        _pollVoters.value = _pollVoters.value.map { voter ->
            if (!voter.isUser) {
                voter
            } else {
                val path = AvatarStore.pathForUser(voter.senderId)
                if (path != voter.photoPath) {
                    voter.copy(photoPath = path)
                } else {
                    voter
                }
            }
        }
        _addedReactions.value = _addedReactions.value.map { row ->
            if (!row.isUser) {
                row
            } else {
                val path = AvatarStore.pathForUser(row.senderId)
                if (path != row.photoPath) {
                    row.copy(photoPath = path)
                } else {
                    row
                }
            }
        }
    }

    // ---- Poll voters (v0.9.32, Y3) ----

    private val _pollVoters = MutableStateFlow<List<PollVoterUi>>(
        emptyList()
    )
    val pollVoters: StateFlow<List<PollVoterUi>> = _pollVoters

    private val _pollTotal = MutableStateFlow(0)
    val pollTotal: StateFlow<Int> = _pollTotal

    private val _pollLoading = MutableStateFlow(false)
    val pollLoading: StateFlow<Boolean> = _pollLoading

    private val _pollError = MutableStateFlow<String?>(null)
    val pollError: StateFlow<String?> = _pollError

    private val _pollExhausted = MutableStateFlow(true)
    val pollExhausted: StateFlow<Boolean> = _pollExhausted

    /** (chatId, messageId, optionId) the poll state belongs
     *  to; late answers for another option are dropped. */
    @Volatile
    private var pollKey: Triple<Long, Long, Int>? = null

    private var pollOffset = 0

    private val chatTitleById = ConcurrentHashMap<Long, String>()
    private val chatTitlePending = ConcurrentHashMap.newKeySet<Long>()

    /** Open the voter list of one poll option. Paging is by
     *  the tl's skip-count offset: getPollVoters may return
     *  fewer than the limit even mid-list, so the end is
     *  only an empty page or reaching total_count. */
    fun loadPollVoters(chatId: Long, messageId: Long, optionId: Int) {
        attachListener()
        pollKey = Triple(chatId, messageId, optionId)
        pollOffset = 0
        _pollVoters.value = emptyList()
        _pollTotal.value = 0
        _pollError.value = null
        _pollExhausted.value = false
        fetchPollPage()
    }

    fun loadMorePollVoters() {
        if (_pollLoading.value || _pollExhausted.value) return
        if (pollKey == null) return
        fetchPollPage()
    }

    private fun fetchPollPage() {
        val key = pollKey ?: return
        _pollLoading.value = true
        val query = TdClient.request("getPollVoters").apply {
            put("chat_id", key.first)
            put("message_id", key.second)
            put("option_id", key.third)
            put("offset", pollOffset)
            put("limit", 50)
        }
        TdRepository.sendRaw(query) { response ->
            if (pollKey != key) return@sendRaw
            _pollLoading.value = false
            if (TdClient.isError(response)) {
                // An error is not the end of the list
                // (v0.9.41, H1①): exhausted stays false so
                // "load more" can retry the same page.
                _pollError.value =
                    "投票者名单没查到，再试一次。（错误码 " +
                        "${response.optInt("code")}）"
                return@sendRaw
            }
            _pollTotal.value = response.optInt("total_count")
            val arr = response.optJSONArray("voters") ?: JSONArray()
            val page = ArrayList<PollVoterUi>()
            for (i in 0 until arr.length()) {
                val v = arr.optJSONObject(i) ?: continue
                val sender = v.optJSONObject("voter_id") ?: continue
                val row = when (sender.optString("@type")) {
                    "messageSenderUser" -> {
                        val uid = sender.optLong("user_id")
                        if (uid == 0L) {
                            null
                        } else {
                            PollVoterUi(
                                senderId = uid, isUser = true,
                                name = nameByUser[uid] ?: "",
                                photoPath = AvatarStore.pathForUser(uid),
                                voteDate = v.optInt("date")
                            )
                        }
                    }
                    "messageSenderChat" -> {
                        val cid = sender.optLong("chat_id")
                        if (cid == 0L) {
                            null
                        } else {
                            PollVoterUi(
                                senderId = cid, isUser = false,
                                name = chatTitleById[cid] ?: "",
                                photoPath = null,
                                voteDate = v.optInt("date")
                            )
                        }
                    }
                    else -> null
                }
                if (row != null) page.add(row)
            }
            pollOffset += arr.length()
            val merged = _pollVoters.value + page
            _pollVoters.value = merged
            if (arr.length() == 0 ||
                (_pollTotal.value > 0 && merged.size >= _pollTotal.value)
            ) {
                _pollExhausted.value = true
            }
            for (row in page) {
                if (row.name.isNotEmpty()) continue
                if (row.isUser) {
                    resolvePollUserName(row.senderId)
                } else {
                    resolvePollChatTitle(row.senderId)
                }
            }
        }
    }

    private fun resolvePollUserName(userId: Long) {
        if (!namePending.add(userId)) return
        val query = TdClient.request("getUser").apply {
            put("user_id", userId)
        }
        TdRepository.sendRaw(query) { user ->
            namePending.remove(userId)
            if (TdClient.isError(user)) return@sendRaw
            AvatarStore.noteUser(user)
            val name = listOf(
                user.optString("first_name"), user.optString("last_name")
            ).filter { it.isNotEmpty() }.joinToString(" ")
            if (name.isNotEmpty()) nameByUser[userId] = name
            patch(userId)
            patchPollUser(userId)
        }
    }

    private fun patchPollUser(userId: Long) {
        val name = nameByUser[userId] ?: ""
        val path = AvatarStore.pathForUser(userId)
        _pollVoters.value = _pollVoters.value.map { voter ->
            if (voter.isUser && voter.senderId == userId &&
                (voter.name != name || voter.photoPath != path)
            ) {
                voter.copy(name = name, photoPath = path)
            } else {
                voter
            }
        }
    }

    private fun resolvePollChatTitle(chatId: Long) {
        if (!chatTitlePending.add(chatId)) return
        val query = TdClient.request("getChat").apply {
            put("chat_id", chatId)
        }
        TdRepository.sendRaw(query) { chat ->
            chatTitlePending.remove(chatId)
            if (TdClient.isError(chat)) return@sendRaw
            val title = chat.optString("title")
            if (title.isNotEmpty()) {
                chatTitleById[chatId] = title
                _pollVoters.value = _pollVoters.value.map { voter ->
                    if (!voter.isUser && voter.senderId == chatId) {
                        voter.copy(name = title)
                    } else {
                        voter
                    }
                }
            }
        }
    }

    // ---- Added reactions (v0.9.39, F1) ----

    private val _addedReactions =
        MutableStateFlow<List<AddedReactionUi>>(emptyList())
    val addedReactions: StateFlow<List<AddedReactionUi>> =
        _addedReactions

    private val _addedTotal = MutableStateFlow(0)
    val addedTotal: StateFlow<Int> = _addedTotal

    private val _addedLoading = MutableStateFlow(false)
    val addedLoading: StateFlow<Boolean> = _addedLoading

    private val _addedError = MutableStateFlow<String?>(null)
    val addedError: StateFlow<String?> = _addedError

    private val _addedExhausted = MutableStateFlow(true)
    val addedExhausted: StateFlow<Boolean> = _addedExhausted

    /** (chatId, messageId) the reaction list belongs to;
     *  late answers for another message are dropped. */
    @Volatile
    private var addedKey: Pair<Long, Long>? = null

    private var addedOffset = ""

    /** Open the per-user reaction list of one message.
     *  Paging follows the tl's string offset
     *  (getMessageAddedReactions: reaction_type omitted =
     *  all types, next_offset chains pages); the end is an
     *  empty next_offset, an empty page, or reaching
     *  total_count. Only offered by the UI when the
     *  message's can_get_added_reactions flag is set. */
    fun loadAddedReactions(chatId: Long, messageId: Long) {
        attachListener()
        addedKey = chatId to messageId
        addedOffset = ""
        _addedReactions.value = emptyList()
        _addedTotal.value = 0
        _addedError.value = null
        _addedExhausted.value = false
        fetchAddedPage()
    }

    fun loadMoreAddedReactions() {
        if (_addedLoading.value || _addedExhausted.value) return
        if (addedKey == null) return
        fetchAddedPage()
    }

    private fun fetchAddedPage() {
        val key = addedKey ?: return
        _addedLoading.value = true
        val query = TdClient.request("getMessageAddedReactions").apply {
            put("chat_id", key.first)
            put("message_id", key.second)
            put("offset", addedOffset)
            put("limit", 100)
        }
        TdRepository.sendRaw(query) { response ->
            if (addedKey != key) return@sendRaw
            _addedLoading.value = false
            if (TdClient.isError(response)) {
                _addedError.value =
                    "回应名单没查到，再试一次。（错误码 " +
                        "${response.optInt("code")}）"
                _addedExhausted.value = true
                return@sendRaw
            }
            _addedTotal.value = response.optInt("total_count")
            val nextOffset = response.optString("next_offset")
            val arr = response.optJSONArray("reactions") ?: JSONArray()
            val page = ArrayList<AddedReactionUi>()
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                val type = r.optJSONObject("type") ?: continue
                val kind = when (type.optString("@type")) {
                    "reactionTypeEmoji" -> "emoji"
                    "reactionTypeCustomEmoji" -> "custom"
                    "reactionTypePaid" -> "paid"
                    else -> continue
                }
                val sender = r.optJSONObject("sender_id") ?: continue
                val row = when (sender.optString("@type")) {
                    "messageSenderUser" -> {
                        val uid = sender.optLong("user_id")
                        if (uid == 0L) {
                            null
                        } else {
                            AddedReactionUi(
                                senderId = uid, isUser = true,
                                name = nameByUser[uid] ?: "",
                                photoPath = AvatarStore.pathForUser(uid),
                                kind = kind,
                                emoji = type.optString("emoji"),
                                customEmojiId =
                                    type.optLong("custom_emoji_id"),
                                isOutgoing = r.optBoolean("is_outgoing"),
                                date = r.optInt("date")
                            )
                        }
                    }
                    "messageSenderChat" -> {
                        val cid = sender.optLong("chat_id")
                        if (cid == 0L) {
                            null
                        } else {
                            AddedReactionUi(
                                senderId = cid, isUser = false,
                                name = chatTitleById[cid] ?: "",
                                photoPath = null,
                                kind = kind,
                                emoji = type.optString("emoji"),
                                customEmojiId =
                                    type.optLong("custom_emoji_id"),
                                isOutgoing = r.optBoolean("is_outgoing"),
                                date = r.optInt("date")
                            )
                        }
                    }
                    else -> null
                }
                if (row != null) page.add(row)
            }
            addedOffset = nextOffset
            val merged = _addedReactions.value + page
            _addedReactions.value = merged
            if (nextOffset.isEmpty() || arr.length() == 0 ||
                (_addedTotal.value > 0 && merged.size >= _addedTotal.value)
            ) {
                _addedExhausted.value = true
            }
            for (row in page) {
                if (row.name.isNotEmpty()) continue
                if (row.isUser) {
                    resolveAddedUserName(row.senderId)
                } else {
                    resolveAddedChatTitle(row.senderId)
                }
            }
        }
    }

    private fun resolveAddedUserName(userId: Long) {
        if (!namePending.add(userId)) return
        val query = TdClient.request("getUser").apply {
            put("user_id", userId)
        }
        TdRepository.sendRaw(query) { user ->
            namePending.remove(userId)
            if (TdClient.isError(user)) return@sendRaw
            AvatarStore.noteUser(user)
            val name = listOf(
                user.optString("first_name"), user.optString("last_name")
            ).filter { it.isNotEmpty() }.joinToString(" ")
            if (name.isNotEmpty()) nameByUser[userId] = name
            patch(userId)
            patchAddedUser(userId)
        }
    }

    private fun patchAddedUser(userId: Long) {
        val name = nameByUser[userId] ?: ""
        val path = AvatarStore.pathForUser(userId)
        _addedReactions.value = _addedReactions.value.map { row ->
            if (row.isUser && row.senderId == userId &&
                (row.name != name || row.photoPath != path)
            ) {
                row.copy(name = name, photoPath = path)
            } else {
                row
            }
        }
    }

    private fun resolveAddedChatTitle(chatId: Long) {
        if (!chatTitlePending.add(chatId)) return
        val query = TdClient.request("getChat").apply {
            put("chat_id", chatId)
        }
        TdRepository.sendRaw(query) { chat ->
            chatTitlePending.remove(chatId)
            if (TdClient.isError(chat)) return@sendRaw
            val title = chat.optString("title")
            if (title.isNotEmpty()) {
                chatTitleById[chatId] = title
                _addedReactions.value =
                    _addedReactions.value.map { row ->
                        if (!row.isUser && row.senderId == chatId) {
                            row.copy(name = title)
                        } else {
                            row
                        }
                    }
            }
        }
    }

    /** Plain-language version of a getMessageViewers refusal:
     *  the scenes the server will not answer for get their own
     *  sentence, never a raw code on screen. */
    private fun errorText(error: JSONObject): String {
        val message = error.optString("message")
        return when {
            message.contains("too old", ignoreCase = true) ||
                message.contains("TOO_OLD") ->
                "这条消息发得太久了，看过它的人已经查不到了。"
            message.contains("privacy", ignoreCase = true) ->
                "因为你或对方的隐私设置，这条消息的已读名单看不了。"
            message.contains("ADMIN", ignoreCase = true) ->
                "只有管理员能看这条消息的已读名单。"
            message.contains("not available", ignoreCase = true) ||
                message.contains("VIEWER", ignoreCase = true) ||
                message.contains("not supported", ignoreCase = true) ->
                "这个对话里看不了「谁看过」。"
            else ->
                "已读名单没查到，再试一次。（错误码 ${error.optInt("code")}）"
        }
    }
}
