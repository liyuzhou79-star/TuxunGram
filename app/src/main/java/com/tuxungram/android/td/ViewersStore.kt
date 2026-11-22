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
