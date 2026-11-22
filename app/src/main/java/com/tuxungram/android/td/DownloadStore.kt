package com.tuxungram.android.td

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One row of the download manager (v0.9.12, S5). [fileId]
 *  == 0 marks an on-disk-only entry (a completed file found
 *  by walking the downloads directory; it can be opened but
 *  not cancelled — there is nothing in flight). The queue
 *  fields are v0.9.23 (P2): [queued] = waiting for a free
 *  concurrency slot in the download center's own queue,
 *  [failed] = stopped and out of automatic retries,
 *  [speedBps] = the live rate from updateFile deltas. */
data class DownloadEntryUi(
    val fileId: Int,
    val name: String,
    val totalSize: Long = 0L,
    val downloadedSize: Long = 0L,
    val completed: Boolean = false,
    val active: Boolean = false,
    val path: String? = null,
    val mime: String = "",
    val speedBps: Long = 0L,
    val queued: Boolean = false,
    val failed: Boolean = false,
    // Persistent-list bookkeeping (v0.9.27, T2): where the
    // file's message lives (needed by addFileToDownloads),
    // whether the file sits in TDLib's persistent download
    // list, and whether that list already knows it finished
    // (updateFile may not have replayed it this session).
    val chatId: Long = 0L,
    val messageId: Long = 0L,
    val inList: Boolean = false,
    val listCompleted: Boolean = false
) {
    val key: String
        get() = if (fileId != 0) "f$fileId" else "p${path ?: name}"
}

/**
 * Download manager aggregation (v0.9.12, S5; persistent
 * list merged in v0.9.27, T2 — see the last section of
 * this comment). The page aggregates what the app knows —
 *  1. files of messages the app has rendered (the open chat
 *     via MessageStore, the last shared-media page via
 *     SharedMediaStore): documents, videos, voice notes,
 *     audio and animations register with their real names;
 *  2. live state for those files from updateFile (progress,
 *     completion, local path);
 *  3. completed files found on disk (StorageStore), minus
 *     any already covered by 1.
 * Photos are deliberately not listed: they are media cache
 * (the storage page manages them), not user downloads.
 * Nothing is invented — a file the app has never seen does
 * not appear, and the page's empty state says exactly that.
 * Cancelling goes through cancelDownloadFile with
 * only_if_pending=false (td_api.tl line 15284: stops an
 * in-flight download; a completed file is unaffected).
 *
 * Download queue (v0.9.23, P2). What TDLib really offers
 * (verified in td_api.tl): downloadFile takes a priority
 * 1–32 — higher downloads earlier, and for equal priorities
 * the most recent downloadFile call goes first; updateFile
 * reports each file's downloaded_size / is_downloading_active
 * / is_downloading_completed. There is NO TDLib knob for
 * "at most N files at once", so the concurrency setting is
 * enforced HERE, at release time: user-started file
 * downloads (tapping a document / video / voice note in a
 * chat, a comment thread, a forum topic or the shared-media
 * page, and everything started from the download center
 * itself) go through [enqueue]; only the first
 * [maxConcurrent] are handed to TDLib via downloadFile, the
 * rest wait in [pendingQueue], and every completion or final
 * failure releases the next one. Boosting a file moves it to
 * the front of the queue; boosting one already handed over
 * re-calls downloadFile with priority 32, which is how the
 * tl says an in-flight file is reprioritised. Downloads
 * TDLib starts on its own (media auto-download, avatars,
 * stickers, thumbnails) do NOT pass through this queue —
 * they are governed by the auto-download settings, and the
 * downloads page says so.
 *
 * Failure handling: TDLib reports no error state on a file;
 * a download that dies simply stops being active without
 * completing. The center treats exactly that (after a short
 * debounce, and only for files it released itself) as a
 * failed attempt and re-issues downloadFile up to
 * [retryLimit] times. A user cancel removes the file from
 * every queue structure BEFORE cancelDownloadFile is sent,
 * so a cancel is never mistaken for a failure.
 *
 * Persistent download list (v0.9.27, T2). TDLib keeps its
 * own download list (addFileToDownloads / searchFileDownloads
 * / removeFileFromDownloads, all verified in td_api.tl) which
 * survives app restarts when the message database is used —
 * the tl says so verbatim. The split of duties: the list
 * remembers WHICH files are downloads (including unfinished
 * ones from before a restart, which TDLib itself resumes),
 * while the queue above still decides WHEN a user-started
 * file is handed over. Concretely: a file enters the
 * persistent list at the moment the queue releases it
 * (startDownload also calls addFileToDownloads with the
 * message's chat/message ids, remembered at enqueue /
 * register time); [refresh] merges the list back in via
 * searchFileDownloads so a restarted app shows the same
 * center; cancel also removes the file from the list (the
 * tl notes a list download is independent of downloadFile,
 * so cancelDownloadFile alone would NOT stop it). If the
 * list calls fail (old state, not ready), the center simply
 * keeps the pre-T2 aggregation behaviour — nothing is lost.
 */
object DownloadStore {

    private val registry = ConcurrentHashMap<Int, DownloadEntryUi>()
    private val fileState = ConcurrentHashMap<Int, JSONObject>()

    // Persistent-list state (v0.9.27, T2): fileId ->
    // (chatId, messageId) of the message the file belongs
    // to; file ids registered from rendered content; file
    // ids present in TDLib's persistent download list; file
    // ids we already handed to addFileToDownloads.
    private val origins =
        ConcurrentHashMap<Int, Pair<Long, Long>>()
    private val contentIds = ConcurrentHashMap.newKeySet<Int>()
    private val listIds = ConcurrentHashMap.newKeySet<Int>()
    private val addedToList = ConcurrentHashMap.newKeySet<Int>()

    @Volatile
    private var listLoading = false

    private val _entries = MutableStateFlow<List<DownloadEntryUi>>(
        emptyList()
    )
    val entries: StateFlow<List<DownloadEntryUi>> = _entries

    @Volatile
    private var attached = false

    // ---- Settings (own prefs; not display preferences, so
    // they stay out of UiSettings and out of the settings
    // backup, like the proxy list's runtime state). ----

    private const val PREFS = "download_center"
    private const val KEY_MAX_CONCURRENT = "max_concurrent"
    private const val KEY_RETRY_LIMIT = "retry_limit"

    val MAX_CONCURRENT_CHOICES = listOf(1, 2, 3, 4, 5)
    val RETRY_CHOICES = listOf(0, 1, 3, 5)

    private val _maxConcurrent = MutableStateFlow(3)
    val maxConcurrent: StateFlow<Int> = _maxConcurrent

    private val _retryLimit = MutableStateFlow(0)
    val retryLimit: StateFlow<Int> = _retryLimit

    private val _totalSpeedBps = MutableStateFlow(0L)
    val totalSpeedBps: StateFlow<Long> = _totalSpeedBps

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(
            PREFS, Context.MODE_PRIVATE
        )
        val max = prefs.getInt(KEY_MAX_CONCURRENT, 3)
        _maxConcurrent.value =
            if (max in MAX_CONCURRENT_CHOICES) max else 3
        val retry = prefs.getInt(KEY_RETRY_LIMIT, 0)
        _retryLimit.value =
            if (retry in RETRY_CHOICES) retry else 0
        attach()
    }

    private fun persistSettings() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_MAX_CONCURRENT, _maxConcurrent.value)
            .putInt(KEY_RETRY_LIMIT, _retryLimit.value)
            .apply()
    }

    fun setMaxConcurrent(n: Int) {
        if (n !in MAX_CONCURRENT_CHOICES) return
        _maxConcurrent.value = n
        persistSettings()
        pump()
        publish()
    }

    fun setRetryLimit(n: Int) {
        if (n !in RETRY_CHOICES) return
        _retryLimit.value = n
        persistSettings()
    }

    // ---- Queue state (guarded by queueLock; update
    // callbacks arrive on the TDLib receiver thread, UI
    // calls on the main thread). ----

    private val queueLock = Any()
    private val pendingQueue = ArrayDeque<Int>()
    private val released = HashSet<Int>()
    private val attempts = HashMap<Int, Int>()
    private val failedSet = HashSet<Int>()
    private val failureCheckPending = HashSet<Int>()

    private val mainHandler = Handler(Looper.getMainLooper())

    // ---- Speed sampling: (time, downloaded bytes) points
    // per file from updateFile; the rate is computed over
    // the last few seconds so it tracks the live speed
    // instead of the whole-download average. ----

    private data class Sample(val atMs: Long, val bytes: Long)

    private val samples = ConcurrentHashMap<Int, ArrayDeque<Sample>>()
    private val speeds = ConcurrentHashMap<Int, Long>()

    /** Subscribe to updateFile once (called from ChatStore's
     *  Ready path next to AvatarStore.attach). Raw file
     *  objects are remembered for every file; they only turn
     *  into rows for file ids a content source registered. */
    fun attach() {
        if (attached) return
        attached = true
        TdRepository.addUpdateListener { update ->
            when (update.optString("@type")) {
                "updateFile" -> {
                    val file = update.optJSONObject("file")
                        ?: return@addUpdateListener
                    val fileId = file.optInt("id", 0)
                    if (fileId == 0) return@addUpdateListener
                    fileState[fileId] = file
                    onFileUpdated(fileId, file)
                    if (registry.containsKey(fileId)) publish()
                }
                "updateFileAddedToDownloads" -> {
                    // Ours or another client's addition: the
                    // file belongs in the center from now on.
                    val fd = update.optJSONObject("file_download")
                        ?: return@addUpdateListener
                    val entry = parseListEntry(fd)
                        ?: return@addUpdateListener
                    listIds.add(entry.fileId)
                    addedToList.add(entry.fileId)
                    mergeListEntry(entry)
                    publish()
                }
                "updateFileRemovedFromDownloads" -> {
                    val fileId = update.optInt("file_id", 0)
                    if (fileId == 0) return@addUpdateListener
                    listIds.remove(fileId)
                    addedToList.remove(fileId)
                    // A row that only the list kept alive
                    // goes with it; content rows stay.
                    if (fileId !in contentIds) {
                        registry.remove(fileId)
                    }
                    publish()
                }
                "updateFileDownload" -> {
                    val fileId = update.optInt("file_id", 0)
                    if (fileId == 0) return@addUpdateListener
                    if (update.optInt("complete_date", 0) > 0) {
                        registry[fileId]?.let { existing ->
                            if (!existing.listCompleted) {
                                registry[fileId] = existing.copy(
                                    listCompleted = true
                                )
                            }
                        }
                    }
                    if (registry.containsKey(fileId)) publish()
                }
            }
        }
    }

    private fun onFileUpdated(fileId: Int, file: JSONObject) {
        val local = file.optJSONObject("local")
        val active = local?.optBoolean("is_downloading_active")
            ?: false
        val completed = local?.optBoolean(
            "is_downloading_completed"
        ) ?: false
        // Speed sampling only makes sense while bytes move.
        if (active && !completed) {
            val downloaded = local?.optLong("downloaded_size", 0L)
                ?: 0L
            val deque = samples.getOrPut(fileId) { ArrayDeque() }
            val now = System.currentTimeMillis()
            synchronized(deque) {
                deque.addLast(Sample(now, downloaded))
                while (deque.size > 2 &&
                    now - deque.first().atMs > 4_000L
                ) {
                    deque.removeFirst()
                }
                val first = deque.first()
                val dt = now - first.atMs
                speeds[fileId] = if (dt >= 800L) {
                    ((downloaded - first.bytes) * 1000L / dt)
                        .coerceAtLeast(0L)
                } else {
                    speeds[fileId] ?: 0L
                }
            }
        } else {
            samples.remove(fileId)
            speeds.remove(fileId)
        }
        var changed = false
        synchronized(queueLock) {
            if (completed) {
                if (pendingQueue.remove(fileId)) changed = true
                if (released.remove(fileId)) changed = true
                attempts.remove(fileId)
                if (failedSet.remove(fileId)) changed = true
            } else if (!active && fileId in released &&
                fileId !in failureCheckPending
            ) {
                // Released by us, now stopped without
                // completing: possibly a dead download. The
                // verdict is debounced — TDLib can flicker
                // inactive between parts, and a user cancel
                // removes the file from `released` first, so
                // whatever is still released after the pause
                // really did stop on its own.
                failureCheckPending.add(fileId)
                mainHandler.postDelayed({
                    verdictOnStopped(fileId)
                }, 2_500L)
            }
        }
        if (changed) {
            pump()
            if (registry.containsKey(fileId)) publish()
        }
    }

    private fun verdictOnStopped(fileId: Int) {
        var republish = false
        synchronized(queueLock) {
            failureCheckPending.remove(fileId)
            if (fileId !in released) return
            val local = fileState[fileId]?.optJSONObject("local")
            val active = local?.optBoolean(
                "is_downloading_active"
            ) ?: false
            val completed = local?.optBoolean(
                "is_downloading_completed"
            ) ?: false
            if (active || completed) return
            val used = attempts[fileId] ?: 0
            if (used < _retryLimit.value) {
                attempts[fileId] = used + 1
                startDownload(fileId, priority = 24)
            } else {
                released.remove(fileId)
                attempts.remove(fileId)
                failedSet.add(fileId)
                republish = true
            }
        }
        if (republish) {
            pump()
            publish()
        }
    }

    /** Ask for a file to be downloaded through the queue.
     *  [boost] puts it at the front (a tap on the file in a
     *  chat, or 优先下载 in the download center). Safe to
     *  call for files already downloading or done. [chatId] /
     *  [messageId] locate the owning message; they are what
     *  addFileToDownloads needs when the queue releases the
     *  file into the persistent list (v0.9.27, T2). */
    fun enqueue(
        fileId: Int,
        boost: Boolean = false,
        chatId: Long = 0L,
        messageId: Long = 0L
    ) {
        if (fileId == 0) return
        attach()
        if (chatId != 0L && messageId != 0L) {
            origins[fileId] = chatId to messageId
        }
        val local = fileState[fileId]?.optJSONObject("local")
        if (local?.optBoolean("is_downloading_completed") == true) {
            return
        }
        var startNow = false
        synchronized(queueLock) {
            failedSet.remove(fileId)
            if (fileId in released) {
                startNow = boost
            } else {
                pendingQueue.remove(fileId)
                if (boost) {
                    pendingQueue.addFirst(fileId)
                } else {
                    pendingQueue.addLast(fileId)
                }
            }
        }
        if (startNow) {
            // Already handed to TDLib: re-calling downloadFile
            // with the top priority is the tl's reprioritise.
            startDownload(fileId, priority = 32)
        }
        pump()
        publish()
    }

    /** Manual retry of a failed row: fresh attempt budget,
     *  straight to the front of the queue. */
    fun retry(fileId: Int) {
        synchronized(queueLock) {
            attempts.remove(fileId)
            failedSet.remove(fileId)
        }
        enqueue(fileId, boost = true)
    }

    /** Release queued files to TDLib until the concurrency
     *  cap is met. A released file occupies its slot until it
     *  completes, finally fails, or is cancelled — including
     *  while TDLib itself has it waiting internally. */
    private fun pump() {
        val toStart = ArrayList<Int>()
        synchronized(queueLock) {
            while (released.size < _maxConcurrent.value &&
                pendingQueue.isNotEmpty()
            ) {
                val next = pendingQueue.removeFirst()
                released.add(next)
                toStart.add(next)
            }
        }
        for (fileId in toStart) startDownload(fileId, priority = 24)
    }

    private fun startDownload(fileId: Int, priority: Int) {
        val query = TdClient.request("downloadFile").apply {
            put("file_id", fileId)
            put("priority", priority)
            put("offset", 0L)
            put("limit", 0L)
            put("synchronous", false)
        }
        TdRepository.sendRaw(query) {}
        maybeAddToList(fileId, priority)
    }

    /** Enter the file into TDLib's persistent download list
     *  the first time the queue releases it (v0.9.27, T2).
     *  Needs the owning message's ids; without them (a file
     *  the center only knows by id) the file simply stays a
     *  queue-only download, exactly as before T2. A failed
     *  add is not latched — the next release may retry. */
    private fun maybeAddToList(fileId: Int, priority: Int) {
        val origin = origins[fileId] ?: return
        if (!addedToList.add(fileId)) return
        val query = TdClient.request("addFileToDownloads").apply {
            put("file_id", fileId)
            put("chat_id", origin.first)
            put("message_id", origin.second)
            put("priority", priority)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                addedToList.remove(fileId)
                return@sendRaw
            }
            if (response.optInt("id", 0) == fileId) {
                fileState[fileId] = response
            }
            listIds.add(fileId)
            if (registry.containsKey(fileId)) publish()
        }
    }

    /** Rebuild the rows from every known source. Called when
     *  the downloads page opens and from its refresh button;
     *  cheap map work over lists the app already holds. */
    fun refresh(context: Context) {
        attach()
        for (item in MessageStore.messages.value) {
            register(item, MessageStore.currentChatId)
        }
        for ((_, list) in SharedMediaStore.items.value) {
            for (item in list) {
                register(item, SharedMediaStore.mediaChatId)
            }
        }
        publish()
        loadPersistentList()
        // On-disk completion seed runs off the caller's
        // thread; rows republish when it lands.
        Thread {
            val covered = registry.values
                .mapNotNull { it.path }
                .toHashSet()
            val disk = try {
                StorageStore.listDownloaded(context)
            } catch (t: Throwable) {
                emptyList()
            }
            val diskRows = disk
                .filter { it.path !in covered }
                .map { entry ->
                    DownloadEntryUi(
                        fileId = 0,
                        name = entry.name,
                        totalSize = entry.size,
                        downloadedSize = entry.size,
                        completed = true,
                        path = entry.path
                    )
                }
            publish(diskRows)
        }.start()
    }

    private fun register(item: MsgItem, chatId: Long = 0L) {
        if (item.fileId == 0) return
        when (item.kind) {
            MsgKind.DOCUMENT, MsgKind.VIDEO, MsgKind.VOICE,
            MsgKind.ANIMATION -> Unit
            else -> return
        }
        contentIds.add(item.fileId)
        if (chatId != 0L && item.id != 0L) {
            origins[item.fileId] = chatId to item.id
        }
        val name = when {
            item.fileName.isNotEmpty() -> item.fileName
            item.kind == MsgKind.VIDEO -> "视频"
            item.kind == MsgKind.VOICE -> "语音消息"
            item.kind == MsgKind.ANIMATION -> "GIF"
            else -> "文件"
        }
        val existing = registry[item.fileId]
        registry[item.fileId] = DownloadEntryUi(
            fileId = item.fileId,
            name = name,
            totalSize = if (item.fileSize > 0) {
                item.fileSize
            } else {
                existing?.totalSize ?: 0L
            },
            mime = item.fileMime,
            chatId = chatId,
            messageId = item.id,
            listCompleted = existing?.listCompleted ?: false
        )
    }

    // ---- Persistent download list (v0.9.27, T2) ----

    /** Flatten one fileDownload object (searchFileDownloads /
     *  updateFileAddedToDownloads) into a center row, or null
     *  when the message carries no file kind the center lists
     *  (photos and the rest stay out, as before). */
    private fun parseListEntry(fd: JSONObject): DownloadEntryUi? {
        val fileId = fd.optInt("file_id", 0)
        if (fileId == 0) return null
        val message = fd.optJSONObject("message") ?: return null
        val content = message.optJSONObject("content") ?: return null
        var holder: JSONObject? = null
        var file: JSONObject? = null
        var fallbackName = "文件"
        when (content.optString("@type")) {
            "messageDocument" -> {
                holder = content.optJSONObject("document")
                file = holder?.optJSONObject("document")
                fallbackName = "文件"
            }
            "messageVideo" -> {
                holder = content.optJSONObject("video")
                file = holder?.optJSONObject("video")
                fallbackName = "视频"
            }
            "messageVoiceNote" -> {
                holder = content.optJSONObject("voice_note")
                file = holder?.optJSONObject("voice")
                fallbackName = "语音消息"
            }
            "messageAnimation" -> {
                holder = content.optJSONObject("animation")
                file = holder?.optJSONObject("animation")
                fallbackName = "GIF"
            }
            "messageAudio" -> {
                holder = content.optJSONObject("audio")
                file = holder?.optJSONObject("audio")
                fallbackName = "音频"
            }
            else -> return null
        }
        val f = file ?: return null
        val size = f.optLong("size", 0L).let {
            if (it > 0) it else f.optLong("expected_size", 0L)
        }
        val name = holder?.optString("file_name")
            ?.ifEmpty { null } ?: fallbackName
        return DownloadEntryUi(
            fileId = fileId,
            name = name,
            totalSize = size,
            mime = holder?.optString("mime_type") ?: "",
            chatId = message.optLong("chat_id", 0L),
            messageId = message.optLong("id", 0L),
            inList = true,
            listCompleted = fd.optInt("complete_date", 0) > 0
        )
    }

    /** Fold a persistent-list row into the registry without
     *  clobbering richer data a content source already gave
     *  (real file name beats the fallback, and so on). */
    private fun mergeListEntry(entry: DownloadEntryUi) {
        if (entry.chatId != 0L && entry.messageId != 0L) {
            origins[entry.fileId] = entry.chatId to entry.messageId
        }
        val existing = registry[entry.fileId]
        registry[entry.fileId] = if (existing == null) {
            entry
        } else {
            existing.copy(
                chatId = if (existing.chatId != 0L) {
                    existing.chatId
                } else {
                    entry.chatId
                },
                messageId = if (existing.messageId != 0L) {
                    existing.messageId
                } else {
                    entry.messageId
                },
                listCompleted = existing.listCompleted ||
                    entry.listCompleted
            )
        }
    }

    /** Merge TDLib's persistent download list into the
     *  center (v0.9.27, T2): paged searchFileDownloads with
     *  an empty query returns the whole list. This is what
     *  makes the center survive a restart — entries the app
     *  has not rendered this session still show up, and
     *  TDLib resumes their downloads on its own. Any failure
     *  just skips the merge; the aggregated rows stay. */
    private fun loadPersistentList() {
        if (listLoading) return
        listLoading = true
        loadListPage("")
    }

    private fun loadListPage(offset: String) {
        val query = TdClient.request("searchFileDownloads").apply {
            put("query", "")
            put("only_active", false)
            put("only_completed", false)
            put("offset", offset)
            put("limit", 100)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response) ||
                response.optString("@type") != "foundFileDownloads"
            ) {
                listLoading = false
                return@sendRaw
            }
            val files = response.optJSONArray("files")
            val landed = ArrayList<Int>()
            if (files != null) {
                for (i in 0 until files.length()) {
                    val fd = files.optJSONObject(i) ?: continue
                    val entry = parseListEntry(fd) ?: continue
                    listIds.add(entry.fileId)
                    addedToList.add(entry.fileId)
                    mergeListEntry(entry)
                    landed.add(entry.fileId)
                }
            }
            publish()
            // Hydrate live state (path / progress) for the
            // rows TDLib has not sent an updateFile for yet.
            for (fileId in landed) {
                if (fileState.containsKey(fileId)) continue
                val get = TdClient.request("getFile").apply {
                    put("file_id", fileId)
                }
                TdRepository.sendRaw(get) { file ->
                    if (TdClient.isError(file)) return@sendRaw
                    if (file.optInt("id", 0) != fileId) {
                        return@sendRaw
                    }
                    fileState[fileId] = file
                    onFileUpdated(fileId, file)
                    if (registry.containsKey(fileId)) publish()
                }
            }
            val next = response.optString("next_offset", "")
            if (next.isNotEmpty() && next != offset) {
                loadListPage(next)
            } else {
                listLoading = false
            }
        }
    }

    /** Take a file out of the persistent download list
     *  (v0.9.27, T2). The cached file itself is kept —
     *  delete_from_cache stays false. A row the list alone
     *  kept alive disappears with it. */
    fun removeFromList(fileId: Int) {
        if (fileId == 0) return
        listIds.remove(fileId)
        addedToList.remove(fileId)
        val query = TdClient.request("removeFileFromDownloads")
            .apply {
                put("file_id", fileId)
                put("delete_from_cache", false)
            }
        TdRepository.sendRaw(query) {}
        if (fileId !in contentIds) registry.remove(fileId)
        publish()
    }

    private fun publish(diskRows: List<DownloadEntryUi> = emptyList()) {
        val queuedSnapshot: Set<Int>
        val failedSnapshot: Set<Int>
        synchronized(queueLock) {
            queuedSnapshot = pendingQueue.toSet()
            failedSnapshot = failedSet.toSet()
        }
        val rows = ArrayList<DownloadEntryUi>()
        for ((fileId, base) in registry) {
            val file = fileState[fileId]
            val local = file?.optJSONObject("local")
            val completed = local?.optBoolean(
                "is_downloading_completed"
            ) ?: (base.listCompleted || base.path != null)
            val active = local?.optBoolean("is_downloading_active")
                ?: false
            val path = local?.optString("path")?.ifEmpty { null }
                ?: base.path
            val total = when {
                file != null && file.optLong("size", 0L) > 0 ->
                    file.optLong("size")
                base.totalSize > 0 -> base.totalSize
                file != null -> file.optLong("expected_size", 0L)
                else -> 0L
            }
            val downloaded = when {
                completed && total > 0 -> total
                local != null -> local.optLong("downloaded_size", 0L)
                else -> 0L
            }
            val origin = origins[fileId]
            rows.add(
                base.copy(
                    totalSize = total,
                    downloadedSize = downloaded,
                    completed = completed,
                    active = active,
                    path = if (completed) path else null,
                    speedBps = if (active) {
                        speeds[fileId] ?: 0L
                    } else {
                        0L
                    },
                    queued = fileId in queuedSnapshot,
                    failed = fileId in failedSnapshot,
                    chatId = origin?.first ?: base.chatId,
                    messageId = origin?.second ?: base.messageId,
                    inList = fileId in listIds
                )
            )
        }
        rows.addAll(diskRows)
        // Downloading first, then queued, failed, idle and
        // completed; stable by name inside each group so rows
        // do not jump while watching progress.
        fun rank(row: DownloadEntryUi): Int = when {
            row.active -> 0
            row.queued -> 1
            row.failed -> 2
            row.completed -> 4
            else -> 3
        }
        rows.sortWith(
            compareBy<DownloadEntryUi> { rank(it) }.thenBy { it.name }
        )
        _entries.value = rows
        _totalSpeedBps.value = rows
            .filter { it.active }
            .sumOf { it.speedBps }
    }

    fun cancel(fileId: Int) {
        if (fileId == 0) return
        // Leave every queue structure FIRST, so the stopped
        // state TDLib is about to report is not mistaken for
        // a failure worth retrying.
        synchronized(queueLock) {
            pendingQueue.remove(fileId)
            released.remove(fileId)
            attempts.remove(fileId)
            failedSet.remove(fileId)
        }
        val query = TdClient.request("cancelDownloadFile").apply {
            put("file_id", fileId)
            put("only_if_pending", false)
        }
        TdRepository.sendRaw(query) {
            // The follow-up updateFile settles the row; if none
            // arrives (already stopped), republish anyway.
            publish()
        }
        // A list download is independent of downloadFile
        // (td_api.tl on addFileToDownloads): without this,
        // TDLib would keep fetching a file the user just
        // cancelled. The entry leaves the persistent list;
        // the row itself stays when a content source owns it.
        if (fileId in listIds) {
            listIds.remove(fileId)
            addedToList.remove(fileId)
            val remove = TdClient.request("removeFileFromDownloads")
                .apply {
                    put("file_id", fileId)
                    put("delete_from_cache", false)
                }
            TdRepository.sendRaw(remove) {}
        }
        pump()
    }
}
