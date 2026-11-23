package com.tuxungram.android.td

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One downloaded file on disk, for the storage page list. */
data class DownloadedEntry(
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long
)

/** Disk usage split the way the settings page shows it:
 *  the TDLib database (chat history index — never touched by
 *  cleanup) versus the downloaded-files cache (safe to clear;
 *  TDLib simply downloads a file again when it is viewed). */
data class StorageInfo(
    val databaseBytes: Long,
    val filesBytes: Long,
    val fileCount: Int,
    /** Bytes held by the OTHER account slots' directory
     *  pairs (v0.9.41, H4): the two numbers above measure
     *  only the current account — the same directories
     *  sendParameters writes, so they are exact for it —
     *  but the app as a whole occupies more when other
     *  accounts are signed in on this phone. Reported
     *  separately, never merged into the current
     *  account's bar: cleanup here must not reach into
     *  another account's data. */
    val otherAccountsBytes: Long = 0L
)

/**
 * Storage page data (Batch 3, C2). Everything is measured by
 * walking the two TDLib directories directly — that is the
 * ground truth for what the app occupies on disk. Cleanup only
 * ever deletes inside the files directory; the database
 * directory (and with it the chat history) is never touched.
 */
object StorageStore {

    fun measure(context: Context): StorageInfo {
        val dbBytes = dirSize(TdRepository.databaseDir(context))
        val root = TdRepository.downloadDir(context)
        var bytes = 0L
        var count = 0
        if (root.exists()) {
            root.walkTopDown().forEach { f ->
                if (f.isFile) {
                    bytes += f.length()
                    count++
                }
            }
        }
        var otherBytes = 0L
        val current = AccountStore.currentSlot()
        for (slot in AccountStore.slots.value) {
            if (slot == current) continue
            otherBytes += dirSize(
                File(context.filesDir, AccountStore.dbDirName(slot))
            )
            otherBytes += dirSize(
                File(context.filesDir, AccountStore.filesDirName(slot))
            )
        }
        return StorageInfo(dbBytes, bytes, count, otherBytes)
    }

    /** Newest first, capped so a huge cache cannot flood the
     *  settings list. */
    fun listDownloaded(context: Context, limit: Int = 200): List<DownloadedEntry> {
        val root = TdRepository.downloadDir(context)
        if (!root.exists()) return emptyList()
        val out = ArrayList<DownloadedEntry>()
        root.walkTopDown().forEach { f ->
            if (f.isFile && f.length() > 0) {
                out.add(
                    DownloadedEntry(
                        f.absolutePath, f.name, f.length(), f.lastModified()
                    )
                )
            }
        }
        out.sortByDescending { it.modified }
        return out.take(limit)
    }

    /** Delete every downloaded file (the database directory is
     *  not touched). Returns the freed bytes. Files re-download
     *  on demand the next time they are viewed. */
    fun clearFileCache(context: Context): Long {
        val root = TdRepository.downloadDir(context)
        if (!root.exists()) return 0L
        var freed = 0L
        val files = ArrayList<File>()
        root.walkTopDown().forEach { f -> if (f.isFile) files.add(f) }
        for (f in files) {
            val size = f.length()
            if (f.delete()) freed += size
        }
        return freed
    }

    fun deleteOne(path: String): Boolean {
        val f = File(path)
        return f.exists() && f.delete()
    }

    // ---- Per-chat breakdown (v0.9.46) ----
    // The directory walk above measures what THIS app put
    // on disk; TDLib's own getStorageStatistics answers a
    // different question — how the downloaded-file cache
    // splits across chats and file types — and is the
    // accounting the official client's storage page shows.
    // The settings page presents both and labels which is
    // which; the two totals are not expected to match.

    /** One chat's share of the file cache. */
    data class ChatStorageUi(
        val chatId: Long,
        val sizeBytes: Long,
        val fileCount: Int
    )

    /** The whole breakdown: per-chat rows (largest first)
     *  and per-file-type totals across all chats (largest
     *  first, tail folded into 其他 by the loader). */
    data class StorageBreakdown(
        val byChat: List<ChatStorageUi>,
        val byType: List<Pair<String, Long>>,
        val totalBytes: Long,
        val chatCount: Int
    )

    private val _breakdown = MutableStateFlow<StorageBreakdown?>(null)
    val breakdown: StateFlow<StorageBreakdown?> = _breakdown

    /** 0 idle, 1 loading, 2 loaded, 3 failed. */
    private val _breakdownState = MutableStateFlow(0)
    val breakdownState: StateFlow<Int> = _breakdownState

    fun loadBreakdown() {
        if (_breakdownState.value == 1) return
        _breakdownState.value = 1
        val query = TdClient.request("getStorageStatistics").apply {
            put("chat_limit", 30)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _breakdownState.value = 3
                return@sendRaw
            }
            val byChat = ArrayList<ChatStorageUi>()
            val typeTotals = LinkedHashMap<String, Long>()
            val chats = response.optJSONArray("by_chat")
            if (chats != null) {
                for (i in 0 until chats.length()) {
                    val c = chats.optJSONObject(i) ?: continue
                    val size = c.optLong("size", 0)
                    if (size <= 0) continue
                    byChat.add(
                        ChatStorageUi(
                            chatId = c.optLong("chat_id"),
                            sizeBytes = size,
                            fileCount = c.optInt("count", 0)
                        )
                    )
                    val types = c.optJSONArray("by_file_type")
                    if (types != null) {
                        for (j in 0 until types.length()) {
                            val t = types.optJSONObject(j)
                                ?: continue
                            val tSize = t.optLong("size", 0)
                            if (tSize <= 0) continue
                            val label = fileTypeLabel(
                                t.optJSONObject("file_type")
                                    ?.optString("@type")
                                    ?: ""
                            )
                            typeTotals[label] =
                                (typeTotals[label] ?: 0L) + tSize
                        }
                    }
                }
            }
            byChat.sortByDescending { it.sizeBytes }
            val sortedTypes = typeTotals.entries
                .sortedByDescending { it.value }
                .map { it.key to it.value }
            val foldedTypes =
                if (sortedTypes.size > 5) {
                    sortedTypes.take(4) +
                        ("其他" to sortedTypes.drop(4)
                            .sumOf { it.second })
                } else {
                    sortedTypes
                }
            _breakdown.value = StorageBreakdown(
                byChat = byChat,
                byType = foldedTypes,
                totalBytes = response.optLong("size", 0),
                chatCount = byChat.size
            )
            _breakdownState.value = 2
        }
    }

    /** Chinese label for a storage-statistics file type
     *  (the tl FileType constructors), folding the rare
     *  ones into readable buckets. */
    private fun fileTypeLabel(type: String): String = when (type) {
        "fileTypePhoto", "fileTypeSelfDestructingPhoto" -> "图片"
        "fileTypeVideo", "fileTypeSelfDestructingVideo",
        "fileTypeLivePhotoVideo",
        "fileTypeSelfDestructingLivePhotoVideo" -> "视频"
        "fileTypeDocument" -> "文件"
        "fileTypeAudio" -> "音乐"
        "fileTypeVoiceNote",
        "fileTypeSelfDestructingVoiceNote" -> "语音"
        "fileTypeVideoNote",
        "fileTypeSelfDestructingVideoNote" -> "圆形视频"
        "fileTypeAnimation" -> "动图"
        "fileTypeSticker" -> "贴纸"
        "fileTypeProfilePhoto" -> "头像"
        "fileTypePhotoStory", "fileTypeVideoStory" -> "动态"
        "fileTypeWallpaper" -> "壁纸"
        else -> "其他"
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { f -> if (f.isFile) total += f.length() }
        return total
    }
}
