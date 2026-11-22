package com.tuxungram.android.td

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One row of the download manager (v0.9.12, S5). [fileId]
 *  == 0 marks an on-disk-only entry (a completed file found
 *  by walking the downloads directory; it can be opened but
 *  not cancelled — there is nothing in flight). */
data class DownloadEntryUi(
    val fileId: Int,
    val name: String,
    val totalSize: Long = 0L,
    val downloadedSize: Long = 0L,
    val completed: Boolean = false,
    val active: Boolean = false,
    val path: String? = null,
    val mime: String = ""
) {
    val key: String
        get() = if (fileId != 0) "f$fileId" else "p${path ?: name}"
}

/**
 * Download manager aggregation (v0.9.12, S5). Honest scope:
 * TDLib has no "list all downloads" call, so the page
 * aggregates what the app actually knows —
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
 */
object DownloadStore {

    private val registry = ConcurrentHashMap<Int, DownloadEntryUi>()
    private val fileState = ConcurrentHashMap<Int, JSONObject>()

    private val _entries = MutableStateFlow<List<DownloadEntryUi>>(
        emptyList()
    )
    val entries: StateFlow<List<DownloadEntryUi>> = _entries

    @Volatile
    private var attached = false

    /** Subscribe to updateFile once (called from ChatStore's
     *  Ready path next to AvatarStore.attach). Raw file
     *  objects are remembered for every file; they only turn
     *  into rows for file ids a content source registered. */
    fun attach() {
        if (attached) return
        attached = true
        TdRepository.addUpdateListener { update ->
            if (update.optString("@type") != "updateFile") {
                return@addUpdateListener
            }
            val file = update.optJSONObject("file")
                ?: return@addUpdateListener
            val fileId = file.optInt("id", 0)
            if (fileId == 0) return@addUpdateListener
            fileState[fileId] = file
            if (registry.containsKey(fileId)) publish()
        }
    }

    /** Rebuild the rows from every known source. Called when
     *  the downloads page opens and from its refresh button;
     *  cheap map work over lists the app already holds. */
    fun refresh(context: Context) {
        attach()
        for (item in MessageStore.messages.value) register(item)
        for ((_, list) in SharedMediaStore.items.value) {
            for (item in list) register(item)
        }
        publish()
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

    private fun register(item: MsgItem) {
        if (item.fileId == 0) return
        when (item.kind) {
            MsgKind.DOCUMENT, MsgKind.VIDEO, MsgKind.VOICE,
            MsgKind.ANIMATION -> Unit
            else -> return
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
            mime = item.fileMime
        )
    }

    private fun publish(diskRows: List<DownloadEntryUi> = emptyList()) {
        val rows = ArrayList<DownloadEntryUi>()
        for ((fileId, base) in registry) {
            val file = fileState[fileId]
            val local = file?.optJSONObject("local")
            val completed = local?.optBoolean(
                "is_downloading_completed"
            ) ?: (base.path != null)
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
            rows.add(
                base.copy(
                    totalSize = total,
                    downloadedSize = downloaded,
                    completed = completed,
                    active = active,
                    path = if (completed) path else null
                )
            )
        }
        rows.addAll(diskRows)
        // In-flight first, then completed, newest name order
        // inside each group is not tracked — keep it stable
        // by name so rows do not jump while watching progress.
        rows.sortWith(
            compareByDescending<DownloadEntryUi> { it.active }
                .thenByDescending { it.completed }
                .thenBy { it.name }
        )
        _entries.value = rows
    }

    fun cancel(fileId: Int) {
        if (fileId == 0) return
        val query = TdClient.request("cancelDownloadFile").apply {
            put("file_id", fileId)
            put("only_if_pending", false)
        }
        TdRepository.sendRaw(query) {
            // The follow-up updateFile settles the row; if none
            // arrives (already stopped), republish anyway.
            publish()
        }
    }
}
