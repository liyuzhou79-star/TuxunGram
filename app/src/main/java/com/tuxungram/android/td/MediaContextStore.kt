package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * Whole-conversation media context for the full-screen
 * viewer (v0.9.31, X2): the chat's photos and videos as one
 * paged stream, from searchChatMessages with the combined
 * searchMessagesFilterPhotoAndVideo filter (verified in
 * td_api.tl). Before this version the viewer paged only
 * through the photos inside MessageStore's loaded window,
 * so anything outside that window was unreachable and
 * videos never entered the viewer at all.
 *
 * Items are held newest-first (the search's own order);
 * the viewer assembles its display order from this list
 * plus the tapped message's album group. Paging follows
 * the shared searchChatMessages scheme: from_message_id
 * cursor + next_from_message_id, a page that adds nothing
 * new ends the stream. Photos and video thumbnails
 * auto-download as pages land (media cache, like the
 * shared-media page); video files themselves download
 * only on demand.
 *
 * Deliberately independent of MessageStore's open chat
 * window: browsing media can never disturb the
 * conversation's history state. Main chat only — thread
 * and topic viewers keep their existing players.
 */
object MediaContextStore {
    private val _items = MutableStateFlow<List<MsgItem>>(emptyList())
    val items: StateFlow<List<MsgItem>> = _items

    private val _exhausted = MutableStateFlow(true)
    val exhausted: StateFlow<Boolean> = _exhausted

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private var chatId = 0L
    private var nextFrom = 0L
    private var generation = 0
    private val byId = LinkedHashMap<Long, MsgItem>()

    @Volatile
    private var listenerRegistered = false

    private fun registerListener() {
        if (listenerRegistered) return
        listenerRegistered = true
        TdRepository.addUpdateListener { update ->
            if (update.optString("@type") != "updateFile") {
                return@addUpdateListener
            }
            val file = update.optJSONObject("file")
                ?: return@addUpdateListener
            val fileId = file.optInt("id")
            if (fileId == 0) return@addUpdateListener
            val local = file.optJSONObject("local")
                ?: return@addUpdateListener
            val completed = local.optBoolean("is_downloading_completed")
            val active = local.optBoolean("is_downloading_active")
            if (!completed && !active) return@addUpdateListener
            val path = local.optString("path")
            var changed = false
            for ((id, item) in byId) {
                val isPhoto = item.photoFileId == fileId
                val isThumb = item.thumbFileId == fileId &&
                    item.thumbFileId != 0
                val isFile = item.fileId == fileId && item.fileId != 0
                if (!isPhoto && !isThumb && !isFile) continue
                byId[id] = when {
                    completed && path.isNotEmpty() -> item.copy(
                        photoPath = if (isPhoto) path else item.photoPath,
                        thumbPath = if (isThumb) path else item.thumbPath,
                        filePath = if (isFile) path else item.filePath,
                        fileDownloading = if (isFile) {
                            false
                        } else {
                            item.fileDownloading
                        }
                    )
                    active && isFile ->
                        item.copy(fileDownloading = true)
                    else -> item
                }
                changed = true
            }
            if (changed) publish()
        }
    }

    /** Point the context at [forChatId], anchored on the
     *  tapped message. Reopening the same chat keeps the
     *  pages already loaded. The anchor is seeded into the
     *  list at its id-sorted position immediately, so the
     *  viewer can open on it even before its page arrives;
     *  paging then runs until the anchor's own page lands
     *  (its parsed copy replaces the seed) or the stream
     *  ends — capped at 20 pages so a pathological chat
     *  cannot spin here. */
    fun open(forChatId: Long, anchor: MsgItem) {
        registerListener()
        if (chatId != forChatId) {
            chatId = forChatId
            generation++
            byId.clear()
            nextFrom = 0L
            _exhausted.value = false
            _items.value = emptyList()
        }
        if (!byId.containsKey(anchor.id)) {
            byId[anchor.id] = anchor
            publish()
        }
        loadMore(untilAnchor = anchor.id, pagesLeft = 20)
    }

    fun loadMore() = loadMore(untilAnchor = 0L, pagesLeft = 1)

    private fun loadMore(untilAnchor: Long, pagesLeft: Int) {
        val forChat = chatId
        if (forChat == 0L) return
        if (_loading.value || _exhausted.value) return
        if (pagesLeft <= 0) return
        _loading.value = true
        val gen = generation
        val query = TdClient.request("searchChatMessages").apply {
            put("chat_id", forChat)
            put("topic_id", JSONObject.NULL)
            put("query", "")
            put("sender_id", JSONObject.NULL)
            put("from_message_id", nextFrom)
            put("offset", 0)
            put("limit", 50)
            put(
                "filter",
                JSONObject().put(
                    "@type", "searchMessagesFilterPhotoAndVideo"
                )
            )
        }
        TdRepository.sendRaw(query) { response ->
            _loading.value = false
            if (chatId != forChat || gen != generation) {
                return@sendRaw
            }
            if (TdClient.isError(response)) {
                _exhausted.value = true
                return@sendRaw
            }
            val next = response.optLong("next_from_message_id")
            nextFrom = next
            val arr = response.optJSONArray("messages")
            var added = 0
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val msg = arr.optJSONObject(i) ?: continue
                    val item = MessageStore.parseMessage(msg)
                    if (item.kind != MsgKind.PHOTO &&
                        item.kind != MsgKind.VIDEO
                    ) {
                        continue
                    }
                    if (!byId.containsKey(item.id)) added++
                    byId[item.id] = item
                }
            }
            if (next == 0L || added == 0) {
                _exhausted.value = true
            }
            publish()
            // Fill the stills in: photos and video thumbnails
            // are media cache and download on sight, the same
            // behaviour as the shared-media page.
            for (item in byId.values) {
                if (item.kind == MsgKind.PHOTO &&
                    item.photoPath == null && item.photoFileId != 0
                ) {
                    downloadFile(item.photoFileId)
                }
                if (item.kind == MsgKind.VIDEO &&
                    item.thumbPath == null && item.thumbFileId != 0
                ) {
                    downloadFile(item.thumbFileId)
                }
            }
            if (untilAnchor != 0L && !_exhausted.value &&
                pagesLeft > 1
            ) {
                // Keep paging toward the anchor's era while
                // the oldest PARSED item is still newer than
                // the anchor (the seeded anchor itself does
                // not count — it is in byId from the start).
                // This fills the context around an old anchor
                // reached by a jump; open()'s cap bounds it.
                val oldest = byId.keys
                    .filter { it != untilAnchor }
                    .minOrNull() ?: Long.MAX_VALUE
                if (oldest > untilAnchor) {
                    loadMore(untilAnchor, pagesLeft - 1)
                }
            }
        }
    }

    /** Download one video's file on demand (the viewer page's
     *  play tap when the file is not on disk yet): through
     *  the download center's queue, boosted, like every
     *  user-initiated file download. */
    fun downloadVideo(item: MsgItem) {
        if (item.fileId == 0 || item.filePath != null) return
        DownloadStore.enqueue(
            item.fileId, boost = true,
            chatId = chatId, messageId = item.id
        )
        val existing = byId[item.id] ?: return
        byId[item.id] = existing.copy(fileDownloading = true)
        publish()
    }

    private fun downloadFile(fileId: Int) {
        TdRepository.sendRaw(TdClient.request("downloadFile").apply {
            put("file_id", fileId)
            put("priority", 1)
            put("offset", 0L)
            put("limit", 0L)
            put("synchronous", false)
        }) {}
    }

    private fun publish() {
        _items.value = byId.values.sortedByDescending { it.id }
    }
}
