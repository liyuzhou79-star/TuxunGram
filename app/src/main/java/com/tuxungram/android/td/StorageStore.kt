package com.tuxungram.android.td

import android.content.Context
import java.io.File

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

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { f -> if (f.isFile) total += f.length() }
        return total
    }
}
