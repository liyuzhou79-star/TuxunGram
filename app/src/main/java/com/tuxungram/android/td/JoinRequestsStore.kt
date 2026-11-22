package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One pending join request (v0.9.14, U2), from
 *  chatJoinRequest in td_api.tl: who asks, since when, and
 *  the bio they wrote on their own profile. */
data class JoinRequestUi(
    val userId: Long,
    val name: String,
    val photoPath: String?,
    val dateSec: Int,
    val bio: String
)

/**
 * Join-request approval for one chat (v0.9.14, U2), over
 * getChatJoinRequests / processChatJoinRequest /
 * processChatJoinRequests (verified in td_api.tl — this tl
 * has no approve/decline-named calls; process* with an
 * approve flag is the real surface). The pending count for
 * the profile entry rides chat.pending_join_requests and is
 * peeked with a plain getChat. Names and avatars resolve
 * lazily through getUser + AvatarStore, the ViewersStore
 * pattern. A processed request leaves the list at once; the
 * server count is re-read after bulk actions.
 */
object JoinRequestsStore {
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _requests = MutableStateFlow<List<JoinRequestUi>>(emptyList())
    val requests: StateFlow<List<JoinRequestUi>> = _requests

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    /** Pending count per chat for entry badges, filled by
     *  [peekCount]; -1/absent = not peeked yet. */
    private val _pendingByChat = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val pendingByChat: StateFlow<Map<Long, Int>> = _pendingByChat

    @Volatile
    private var forChat: Long = 0L

    private val nameByUser = ConcurrentHashMap<Long, String>()
    private val namePending = ConcurrentHashMap.newKeySet<Long>()

    @Volatile
    private var listenerAttached = false

    private fun attachListener() {
        if (listenerAttached) return
        listenerAttached = true
        AvatarStore.addListener { refreshPhotos() }
    }

    /** Read chat.pending_join_requests.total_count so the
     *  profile entry can show how many people are waiting. */
    fun peekCount(chatId: Long) {
        val query = TdClient.request("getChat").apply {
            put("chat_id", chatId)
        }
        TdRepository.sendRaw(query) { chat ->
            if (TdClient.isError(chat)) return@sendRaw
            val count = chat.optJSONObject("pending_join_requests")
                ?.optInt("total_count", 0) ?: 0
            _pendingByChat.value = _pendingByChat.value + (chatId to count)
        }
    }

    fun load(chatId: Long) {
        attachListener()
        forChat = chatId
        _loading.value = true
        _requests.value = emptyList()
        _error.value = null
        _notice.value = null
        fetchPage(chatId, offset = null, append = false)
    }

    fun loadMore() {
        if (_loading.value) return
        val last = _requests.value.lastOrNull() ?: return
        if (_requests.value.size >= _totalCount.value &&
            _totalCount.value > 0
        ) {
            return
        }
        _loading.value = true
        val offset = JSONObject().apply {
            put("@type", "chatJoinRequest")
            put("user_id", last.userId)
            put("date", last.dateSec)
            put("bio", last.bio)
        }
        fetchPage(forChat, offset = offset, append = true)
    }

    private fun fetchPage(chatId: Long, offset: JSONObject?, append: Boolean) {
        val query = TdClient.request("getChatJoinRequests").apply {
            put("chat_id", chatId)
            put("invite_link", "")
            put("query", "")
            if (offset != null) put("offset_request", offset)
            put("limit", 100)
        }
        TdRepository.sendRaw(query) { response ->
            if (forChat != chatId) return@sendRaw
            _loading.value = false
            if (TdClient.isError(response)) {
                if (!append) _error.value = errorText(response)
                return@sendRaw
            }
            _totalCount.value = response.optInt("total_count", 0)
            val arr = response.optJSONArray("requests") ?: JSONArray()
            val page = ArrayList<JoinRequestUi>()
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                val userId = r.optLong("user_id")
                if (userId == 0L) continue
                page.add(
                    JoinRequestUi(
                        userId = userId,
                        name = nameByUser[userId] ?: "",
                        photoPath = AvatarStore.pathForUser(userId),
                        dateSec = r.optInt("date"),
                        bio = r.optString("bio")
                    )
                )
            }
            _requests.value = if (append) {
                (_requests.value + page)
                    .distinctBy { it.userId }
            } else {
                page
            }
            for (request in page) {
                if (request.name.isEmpty()) resolveName(request.userId)
            }
        }
    }

    /** Approve (true) or decline (false) one request. The
     *  row leaves the list only when the server agrees. */
    fun process(chatId: Long, userId: Long, approve: Boolean) {
        val query = TdClient.request("processChatJoinRequest").apply {
            put("chat_id", chatId)
            put("user_id", userId)
            put("approve", approve)
        }
        TdRepository.sendRaw(query) { response ->
            if (forChat != chatId) return@sendRaw
            if (TdClient.isError(response)) {
                _notice.value = errorText(response)
                return@sendRaw
            }
            _requests.value = _requests.value.filter {
                it.userId != userId
            }
            _totalCount.value = (_totalCount.value - 1).coerceAtLeast(0)
            _pendingByChat.value =
                _pendingByChat.value + (chatId to _totalCount.value)
            _notice.value = if (approve) "已批准。" else "已拒绝。"
        }
    }

    /** Approve or decline every pending request of the chat
     *  (the bulk process call, invite_link empty = all). */
    fun processAll(chatId: Long, approve: Boolean) {
        val query = TdClient.request("processChatJoinRequests").apply {
            put("chat_id", chatId)
            put("invite_link", "")
            put("approve", approve)
        }
        TdRepository.sendRaw(query) { response ->
            if (forChat != chatId) return@sendRaw
            if (TdClient.isError(response)) {
                _notice.value = errorText(response)
                return@sendRaw
            }
            _notice.value = if (approve) {
                "已全部批准。"
            } else {
                "已全部拒绝。"
            }
            load(chatId)
            peekCount(chatId)
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
        _requests.value = _requests.value.map { request ->
            if (request.userId == userId &&
                (request.name != name || request.photoPath != path)
            ) {
                request.copy(name = name, photoPath = path)
            } else {
                request
            }
        }
    }

    private fun refreshPhotos() {
        _requests.value = _requests.value.map { request ->
            val path = AvatarStore.pathForUser(request.userId)
            if (path != request.photoPath) {
                request.copy(photoPath = path)
            } else {
                request
            }
        }
    }

    private fun errorText(error: JSONObject): String {
        val message = error.optString("message")
        return when {
            message.contains("ADMIN", ignoreCase = true) ||
                message.contains("not enough rights", ignoreCase = true) ||
                message.contains("RIGHT", ignoreCase = true) ->
                "只有管理员能处理入群申请。"
            message.contains("USER_NOT_FOUND", ignoreCase = true) ||
                message.contains("not found", ignoreCase = true) ->
                "这条申请已经不存在了（对方可能取消了申请）。"
            message.contains("HIDE_REQUESTER", ignoreCase = true) ||
                message.contains("not available", ignoreCase = true) ->
                "这个群没有开启入群申请，或申请列表看不了。"
            else ->
                "没处理成，再试一次。（错误码 ${error.optInt("code")}）"
        }
    }
}
