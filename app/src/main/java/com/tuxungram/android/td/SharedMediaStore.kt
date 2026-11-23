package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** The four shared-media tabs and the searchMessagesFilter*
 *  each maps to (verified in td_api.tl). */
enum class MediaTab(val filterType: String, val label: String) {
    PHOTO("searchMessagesFilterPhoto", "图片"),
    VIDEO("searchMessagesFilterVideo", "视频"),
    FILE("searchMessagesFilterDocument", "文件"),
    LINK("searchMessagesFilterUrl", "链接")
}

/**
 * Shared media page data (v0.9.3, M4): per-tab paged
 * searchChatMessages with the tab's filter. Deliberately a
 * store of its own — nothing here touches MessageStore's open
 * chat, so browsing a profile's media can never disturb the
 * conversation behind it. Message parsing reuses
 * MessageStore.parseMessage (the one shared parser); file
 * downloads and their updateFile completions are tracked
 * locally for this page's items only.
 */
object SharedMediaStore {
    private val _items = MutableStateFlow<Map<MediaTab, List<MsgItem>>>(emptyMap())
    val items: StateFlow<Map<MediaTab, List<MsgItem>>> = _items

    private val _exhausted = MutableStateFlow<Set<MediaTab>>(emptySet())
    val exhausted: StateFlow<Set<MediaTab>> = _exhausted

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var chatId = 0L

    /** Window generation (v0.9.41, H1②): bumped every time
     *  [open] actually switches chats. A loadMore response
     *  carries the generation it was sent under; the bare
     *  chatId check alone could not tell a stale page from
     *  a previous visit to the SAME chat (A→B→A) apart from
     *  a fresh one, and the stale page would land in the new
     *  window with its old cursor. */
    private var openGeneration = 0

    /** The chat the current lists belong to (read by the
     *  download center to locate files' messages, v0.9.27). */
    val mediaChatId: Long
        get() = chatId

    private val lists = HashMap<MediaTab, ArrayList<MsgItem>>()
    private val nextFrom = HashMap<MediaTab, Long>()
    private val loading = HashSet<MediaTab>()

    @Volatile
    private var listenerRegistered = false

    private fun registerListener() {
        if (listenerRegistered) return
        listenerRegistered = true
        TdRepository.addUpdateListener { update ->
            // Deleted messages leave the snapshot too
            // (v0.9.41, H1④): before this, a message deleted
            // elsewhere stayed in the grid until the page was
            // reopened on another chat.
            if (update.optString("@type") == "updateDeleteMessages") {
                if (chatId != 0L &&
                    update.optLong("chat_id") == chatId &&
                    !update.optBoolean("from_cache")
                ) {
                    val ids = update.optJSONArray("message_ids")
                    if (ids != null && ids.length() > 0) {
                        val gone = HashSet<Long>()
                        for (i in 0 until ids.length()) {
                            gone.add(ids.optLong(i))
                        }
                        var changed = false
                        for ((_, list) in lists) {
                            if (list.removeAll { it.id in gone }) {
                                changed = true
                            }
                        }
                        if (changed) publish()
                    }
                }
                return@addUpdateListener
            }
            if (update.optString("@type") != "updateFile") return@addUpdateListener
            val file = update.optJSONObject("file") ?: return@addUpdateListener
            val fileId = file.optInt("id")
            if (fileId == 0) return@addUpdateListener
            val local = file.optJSONObject("local") ?: return@addUpdateListener
            val completed = local.optBoolean("is_downloading_completed")
            val active = local.optBoolean("is_downloading_active")
            if (!completed && !active) return@addUpdateListener
            val path = local.optString("path")
            var changed = false
            for ((_, list) in lists) {
                for (i in list.indices) {
                    val item = list[i]
                    val matches = item.fileId == fileId || item.photoFileId == fileId
                    if (!matches) continue
                    list[i] = when {
                        completed && path.isNotEmpty() -> item.copy(
                            filePath = if (item.kind == MsgKind.PHOTO) {
                                item.filePath
                            } else {
                                path
                            },
                            photoPath = if (item.kind == MsgKind.PHOTO) {
                                path
                            } else {
                                item.photoPath
                            },
                            fileDownloading = false
                        )
                        active -> item.copy(fileDownloading = true)
                        else -> item
                    }
                    changed = true
                }
            }
            if (changed) publish()
        }
    }

    /** Point the store at a chat. A different chat resets
     *  every tab; re-opening the same chat keeps what loaded. */
    fun open(forChatId: Long) {
        registerListener()
        if (chatId == forChatId) return
        chatId = forChatId
        openGeneration++
        lists.clear()
        nextFrom.clear()
        loading.clear()
        _items.value = emptyMap()
        _exhausted.value = emptySet()
        _error.value = null
    }

    fun loadMore(tab: MediaTab) {
        val forChat = chatId
        if (forChat == 0L) return
        if (loading.contains(tab)) return
        if (_exhausted.value.contains(tab)) return
        loading.add(tab)
        val gen = openGeneration
        val query = TdClient.request("searchChatMessages").apply {
            put("chat_id", forChat)
            put("topic_id", JSONObject.NULL)
            put("query", "")
            put("sender_id", JSONObject.NULL)
            put("from_message_id", nextFrom[tab] ?: 0L)
            put("offset", 0)
            put("limit", 50)
            put("filter", JSONObject().put("@type", tab.filterType))
        }
        TdRepository.sendRaw(query) { response ->
            loading.remove(tab)
            if (chatId != forChat || gen != openGeneration) {
                return@sendRaw
            }
            if (TdClient.isError(response)) {
                // An error is not the end of the list
                // (v0.9.41, H1①): until this fix the tab was
                // marked exhausted here, so the "try again"
                // in the error text could never actually be
                // tried — loadMore early-returned forever.
                // The tab stays loadable; the next scroll or
                // button tap retries the same page.
                _error.value = "这一类没加载出来，再试一次。（错误码 ${response.optInt("code")}）"
                return@sendRaw
            }
            val next = response.optLong("next_from_message_id")
            nextFrom[tab] = next
            val arr = response.optJSONArray("messages")
            val list = lists.getOrPut(tab) { ArrayList() }
            var added = 0
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val msg = arr.optJSONObject(i) ?: continue
                    val item = MessageStore.parseMessage(msg)
                    if (list.any { it.id == item.id }) continue
                    list.add(item)
                    added++
                }
            }
            if (next == 0L || added == 0) {
                _exhausted.value = _exhausted.value + tab
            }
            publish()
            // Photos auto-download so the grid fills in, the
            // same behaviour as the chat screen (Batch 1, S3).
            if (tab == MediaTab.PHOTO) {
                for (item in lists[tab] ?: emptyList()) {
                    if (item.photoPath == null && item.photoFileId != 0) {
                        download(item)
                    }
                }
            }
        }
    }

    /** Start (or resume) downloading one item's file; the
     *  updateFile listener patches the path in when it lands.
     *  Non-photo files are user downloads and go through the
     *  download center's queue, boosted (v0.9.23, P2); photos
     *  are media cache and keep the direct low-priority call. */
    fun download(item: MsgItem) {
        val fileId = if (item.kind == MsgKind.PHOTO) item.photoFileId else item.fileId
        if (fileId == 0) return
        if (item.kind == MsgKind.PHOTO) {
            TdRepository.sendRaw(TdClient.request("downloadFile").apply {
                put("file_id", fileId)
                put("priority", 1)
                put("offset", 0L)
                put("limit", 0L)
                put("synchronous", false)
            }) {}
        } else {
            DownloadStore.enqueue(
                fileId, boost = true,
                chatId = chatId, messageId = item.id
            )
        }
        for ((_, list) in lists) {
            for (i in list.indices) {
                if (list[i].id == item.id) {
                    list[i] = list[i].copy(fileDownloading = true)
                }
            }
        }
        publish()
    }

    private fun publish() {
        val snapshot = HashMap<MediaTab, List<MsgItem>>()
        for ((tab, list) in lists) snapshot[tab] = ArrayList(list)
        _items.value = snapshot
    }
}
