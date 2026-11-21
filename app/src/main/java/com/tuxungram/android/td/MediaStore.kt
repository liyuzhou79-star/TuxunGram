package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One entry of the sticker / GIF picker: a sticker or a saved
 *  animation already known to TDLib, sendable by file id. */
data class MediaEntry(
    val isAnimation: Boolean,
    val fileId: Int,
    val emoji: String,
    val previewFileId: Int,
    val previewPath: String?,
    val width: Int,
    val height: Int,
    val durationSec: Int
)

/**
 * Catalog for the sticker/GIF picker: recent stickers, favorite
 * stickers and saved animations (getRecentStickers /
 * getFavoriteStickers / getSavedAnimations, verified in
 * td_api.tl). Previews are the sticker files themselves (webp)
 * or the animation thumbnails; both arrive via downloadFile and
 * updateFile like every other file in the app.
 */
object MediaStore {
    private val _recentStickers = MutableStateFlow<List<MediaEntry>>(emptyList())
    val recentStickers: StateFlow<List<MediaEntry>> = _recentStickers

    private val _favoriteStickers = MutableStateFlow<List<MediaEntry>>(emptyList())
    val favoriteStickers: StateFlow<List<MediaEntry>> = _favoriteStickers

    private val _savedAnimations = MutableStateFlow<List<MediaEntry>>(emptyList())
    val savedAnimations: StateFlow<List<MediaEntry>> = _savedAnimations

    private val pathByFileId = ConcurrentHashMap<Int, String>()
    private val downloadPending = ConcurrentHashMap.newKeySet<Int>()

    @Volatile
    private var attached = false

    @Volatile
    private var loaded = false

    private fun attach() {
        if (attached) return
        attached = true
        TdRepository.addUpdateListener { update ->
            if (update.optString("@type") != "updateFile") return@addUpdateListener
            val file = update.optJSONObject("file") ?: return@addUpdateListener
            val local = file.optJSONObject("local") ?: return@addUpdateListener
            if (!local.optBoolean("is_downloading_completed")) return@addUpdateListener
            val fileId = file.optInt("id")
            downloadPending.remove(fileId)
            pathByFileId[fileId] = local.optString("path")
            republish()
        }
    }

    /** Load (once per process) all three lists; the picker calls
     *  this when it opens. */
    fun load() {
        attach()
        if (loaded) return
        loaded = true
        TdRepository.sendRaw(TdClient.request("getRecentStickers").apply {
            put("is_attached", false)
        }) { response ->
            if (!TdClient.isError(response)) {
                _recentStickers.value = parseStickers(response)
            }
        }
        TdRepository.sendRaw(TdClient.request("getFavoriteStickers")) { response ->
            if (!TdClient.isError(response)) {
                _favoriteStickers.value = parseStickers(response)
            }
        }
        TdRepository.sendRaw(TdClient.request("getSavedAnimations")) { response ->
            if (!TdClient.isError(response)) {
                _savedAnimations.value = parseAnimations(response)
            }
        }
    }

    private fun republish() {
        _recentStickers.value = withPaths(_recentStickers.value)
        _favoriteStickers.value = withPaths(_favoriteStickers.value)
        _savedAnimations.value = withPaths(_savedAnimations.value)
    }

    private fun withPaths(list: List<MediaEntry>): List<MediaEntry> =
        list.map { entry ->
            val path = pathByFileId[entry.previewFileId]
            if (path != null && entry.previewPath == null) {
                entry.copy(previewPath = path)
            } else {
                entry
            }
        }

    private fun ensurePreview(fileId: Int) {
        if (fileId == 0) return
        if (pathByFileId.containsKey(fileId)) return
        if (!downloadPending.add(fileId)) return
        TdRepository.sendRaw(TdClient.request("downloadFile").apply {
            put("file_id", fileId)
            put("priority", 8)
            put("offset", 0L)
            put("limit", 0L)
            put("synchronous", false)
        }) {}
    }

    private fun parseStickers(response: JSONObject): List<MediaEntry> {
        val arr = response.optJSONArray("stickers") ?: return emptyList()
        val out = ArrayList<MediaEntry>()
        for (i in 0 until arr.length()) {
            val sticker = arr.optJSONObject(i) ?: continue
            val file = sticker.optJSONObject("sticker") ?: continue
            val fileId = file.optInt("id", 0)
            if (fileId == 0) continue
            val local = file.optJSONObject("local")
            if (local?.optBoolean("is_downloading_completed") == true) {
                pathByFileId[fileId] = local.optString("path")
            } else {
                ensurePreview(fileId)
            }
            out.add(
                MediaEntry(
                    isAnimation = false,
                    fileId = fileId,
                    emoji = sticker.optString("emoji"),
                    previewFileId = fileId,
                    previewPath = pathByFileId[fileId],
                    width = sticker.optInt("width"),
                    height = sticker.optInt("height"),
                    durationSec = 0
                )
            )
        }
        return out
    }

    private fun parseAnimations(response: JSONObject): List<MediaEntry> {
        val arr = response.optJSONArray("animations") ?: return emptyList()
        val out = ArrayList<MediaEntry>()
        for (i in 0 until arr.length()) {
            val animation = arr.optJSONObject(i) ?: continue
            val file = animation.optJSONObject("animation") ?: continue
            val fileId = file.optInt("id", 0)
            if (fileId == 0) continue
            val thumbFile = animation.optJSONObject("thumbnail")
                ?.optJSONObject("file")
            val previewId = thumbFile?.optInt("id", 0) ?: 0
            if (previewId != 0) {
                val local = thumbFile?.optJSONObject("local")
                if (local?.optBoolean("is_downloading_completed") == true) {
                    pathByFileId[previewId] = local.optString("path")
                } else {
                    ensurePreview(previewId)
                }
            }
            out.add(
                MediaEntry(
                    isAnimation = true,
                    fileId = fileId,
                    emoji = "",
                    previewFileId = previewId,
                    previewPath = pathByFileId[previewId],
                    width = animation.optInt("width"),
                    height = animation.optInt("height"),
                    durationSec = animation.optInt("duration")
                )
            )
        }
        return out
    }
}
