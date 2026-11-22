package com.tuxungram.android.td

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The one avatar pipeline (v0.9.9, W0). Every avatar surface —
 * chat list, chat top bar, profile pages, contacts, members,
 * group-message sender avatars, my own settings card — resolves
 * its local path through here, and the path map is fed by THREE
 * independent sources so no single TDLib timing can starve it:
 *
 * 1. File objects seen anywhere (chat.photo, user.profile_photo,
 *    getUser/getMe responses): if the object already carries a
 *    completed local file, its path is recorded on sight.
 * 2. The downloadFile RESPONSE. This is the feed the old S6
 *    pipeline missed, and the miss was fatal: per td_api.tl the
 *    response IS the File object ("return file state
 *    immediately"), and TDLib emits updateFile only when a
 *    file's state CHANGES — re-asking for an already-downloaded
 *    file changes nothing, so no updateFile ever arrives and the
 *    completed path exists only in that response. Avatar small
 *    files are exactly the files that sit completed in the
 *    persisted file database across restarts and app updates,
 *    so on a real phone the response was the normal path, the
 *    old code discarded it with an empty callback, and avatars
 *    never rendered (real-device report, 2026-11-22).
 * 3. updateFile, for downloads that genuinely start empty.
 *
 * A failed downloadFile clears its pending mark in the response
 * callback, so a later pass can retry — the old ChatStore set
 * never cleared on error and blackholed the id for the process
 * lifetime. Listeners fire only when a NEW path lands; stores
 * register a republish hook and re-read paths from here.
 */
object AvatarStore {
    private val pathByFileId = ConcurrentHashMap<Int, String>()
    private val fileIdByUser = ConcurrentHashMap<Long, Int>()
    private val downloadPending = ConcurrentHashMap.newKeySet<Int>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    private var attached = false

    /** Stores subscribe a cheap republish hook; invoked on the
     *  receiver thread whenever a previously unknown path is
     *  recorded. */
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    private fun fireChanged() {
        for (listener in listeners) {
            try {
                listener()
            } catch (t: Throwable) {
                // One store's republish must never break the
                // others or the receiver loop.
            }
        }
    }

    /** Own subscription to the raw update stream: updateFile
     *  completions and updateUser photo changes feed the map
     *  even for users no store has asked about yet. Idempotent. */
    fun attach() {
        if (attached) return
        attached = true
        TdRepository.addUpdateListener { update ->
            when (update.optString("@type")) {
                "updateFile" -> {
                    update.optJSONObject("file")?.let { noteFile(it) }
                }
                "updateUser" -> {
                    update.optJSONObject("user")?.let { noteUser(it) }
                }
            }
        }
    }

    /** Offer any File object (td_api.tl `file`): records its
     *  local path when the download is already complete.
     *  Returns the known path for this file id afterwards. */
    fun noteFile(file: JSONObject): String? {
        val fileId = file.optInt("id", 0)
        if (fileId == 0) return null
        val local = file.optJSONObject("local") ?: return pathByFileId[fileId]
        if (local.optBoolean("is_downloading_completed")) {
            val path = local.optString("path")
            if (path.isNotEmpty()) {
                val isNew = pathByFileId.put(fileId, path) == null
                downloadPending.remove(fileId)
                if (isNew) fireChanged()
            }
        }
        return pathByFileId[fileId]
    }

    /** Kick a download for [fileId] unless its path is known or
     *  a request is already in flight. The response is READ:
     *  an already-downloaded file answers with the completed
     *  File object and no updateFile ever follows. */
    fun ensureDownload(fileId: Int) {
        if (fileId == 0) return
        if (pathByFileId.containsKey(fileId)) return
        if (!downloadPending.add(fileId)) return
        TdRepository.sendRaw(TdClient.request("downloadFile").apply {
            put("file_id", fileId)
            put("priority", 4)
            put("offset", 0L)
            put("limit", 0L)
            put("synchronous", false)
        }) { response ->
            if (TdClient.isError(response)) {
                // Retryable: a later ensureDownload (the next
                // list recompute asks again) starts a fresh try.
                downloadPending.remove(fileId)
            } else {
                noteFile(response)
            }
        }
    }

    /** Register a user object's profile photo (profilePhoto
     *  .small, td_api.tl line 784): remembers which file belongs
     *  to the user, records a completed path on sight, and
     *  starts the download otherwise. Returns the known path. */
    fun noteUser(user: JSONObject): String? {
        val userId = user.optLong("id", 0L)
        if (userId == 0L) return null
        val small = user.optJSONObject("profile_photo")
            ?.optJSONObject("small") ?: return pathForUser(userId)
        val fileId = small.optInt("id", 0)
        if (fileId != 0) {
            fileIdByUser[userId] = fileId
            noteFile(small)
            ensureDownload(fileId)
        }
        return pathForUser(userId)
    }

    /** Resolve from a bare File object (a chat's photo.small):
     *  record on sight, download if needed, return the path if
     *  it is already on disk. */
    fun pathForFileObj(file: JSONObject?): String? {
        if (file == null) return null
        val fileId = file.optInt("id", 0)
        if (fileId == 0) return null
        val known = noteFile(file)
        if (known == null) ensureDownload(fileId)
        return pathByFileId[fileId]
    }

    fun pathForFile(fileId: Int): String? =
        if (fileId == 0) null else pathByFileId[fileId]

    fun pathForUser(userId: Long): String? {
        val fileId = fileIdByUser[userId] ?: return null
        return pathByFileId[fileId]
    }

    /** True once any user object for [userId] has been noted —
     *  stores use it to decide whether a getUser round-trip is
     *  still needed to learn the photo file id. */
    fun hasUser(userId: Long): Boolean = fileIdByUser.containsKey(userId)
}
