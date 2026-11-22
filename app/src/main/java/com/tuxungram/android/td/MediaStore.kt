package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One installed sticker set, for the panel's set chips
 *  (getInstalledStickerSets, verified in td_api.tl). */
data class StickerSetUi(
    val id: Long,
    val title: String
)

/** One trending sticker set for the discovery row
 *  (getTrendingStickerSets, v0.9.4 N5). */
data class TrendingSetUi(
    val id: Long,
    val title: String,
    val count: Int,
    val isInstalled: Boolean,
    val thumbFileId: Int,
    val thumbPath: String?
)

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

    private val _stickerSets = MutableStateFlow<List<StickerSetUi>>(emptyList())
    val stickerSets: StateFlow<List<StickerSetUi>> = _stickerSets

    /** Stickers of the set the panel is currently browsing. */
    private val _setStickers = MutableStateFlow<List<MediaEntry>>(emptyList())
    val setStickers: StateFlow<List<MediaEntry>> = _setStickers

    /** Trending sets for the discovery row (v0.9.4, N5). */
    private val _trendingSets = MutableStateFlow<List<TrendingSetUi>>(emptyList())
    val trendingSets: StateFlow<List<TrendingSetUi>> = _trendingSets

    /** Stickers of the trending set open in the preview
     *  dialog — a separate flow from setStickers so previewing
     *  never disturbs the set the panel is browsing. */
    private val _previewStickers = MutableStateFlow<List<MediaEntry>>(emptyList())
    val previewStickers: StateFlow<List<MediaEntry>> = _previewStickers

    /** Emoji-search results for the panel's search box
     *  (searchStickers, v0.9.4 N5). */
    private val _searchResults = MutableStateFlow<List<MediaEntry>>(emptyList())
    val searchResults: StateFlow<List<MediaEntry>> = _searchResults

    private val pathByFileId = ConcurrentHashMap<Int, String>()
    private val downloadPending = ConcurrentHashMap.newKeySet<Int>()

    @Volatile
    private var attached = false

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

    /** Load all three lists; the picker calls this every time
     *  it opens. Refetching on every open is deliberate (audit
     *  fix A9): all three are cheap local-database queries, and
     *  the old once-per-process snapshot went stale — stickers
     *  used in another client during the session never reached
     *  the panel until the app restarted. */
    fun load() {
        attach()
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
        fetchInstalledSets()
        loadTrending()
    }

    /** Trending sticker sets (getTrendingStickerSets,
     *  verified in td_api.tl) for the panel's discovery row;
     *  covers download through the same updateFile machinery
     *  as every other preview. */
    fun loadTrending() {
        attach()
        TdRepository.sendRaw(TdClient.request("getTrendingStickerSets").apply {
            put(
                "sticker_type",
                JSONObject().put("@type", "stickerTypeRegular")
            )
            put("offset", 0)
            put("limit", 20)
        }) { response ->
            if (TdClient.isError(response)) return@sendRaw
            val arr = response.optJSONArray("sets") ?: return@sendRaw
            val out = ArrayList<TrendingSetUi>()
            for (i in 0 until arr.length()) {
                val set = arr.optJSONObject(i) ?: continue
                var thumbFile = set.optJSONObject("thumbnail")
                    ?.optJSONObject("file")
                if (thumbFile == null) {
                    val covers = set.optJSONArray("covers")
                    if (covers != null && covers.length() > 0) {
                        thumbFile = covers.optJSONObject(0)
                            ?.optJSONObject("sticker")
                    }
                }
                val fileId = thumbFile?.optInt("id", 0) ?: 0
                val local = thumbFile?.optJSONObject("local")
                if (local?.optBoolean("is_downloading_completed") == true) {
                    pathByFileId[fileId] = local.optString("path")
                } else if (fileId != 0) {
                    ensurePreview(fileId)
                }
                out.add(
                    TrendingSetUi(
                        id = set.optLong("id"),
                        title = set.optString("title"),
                        count = set.optInt("size"),
                        isInstalled = set.optBoolean("is_installed"),
                        thumbFileId = fileId,
                        thumbPath = pathByFileId[fileId]
                    )
                )
            }
            _trendingSets.value = out
        }
    }

    /** Load a trending set's stickers into the preview flow
     *  (getStickerSet — the same call the installed-set
     *  browsing uses, on its own flow). */
    fun loadPreviewSet(setId: Long) {
        attach()
        _previewStickers.value = emptyList()
        TdRepository.sendRaw(TdClient.request("getStickerSet").apply {
            put("set_id", setId)
        }) { response ->
            if (!TdClient.isError(response)) {
                _previewStickers.value = parseStickers(response)
            }
        }
    }

    /** Search stickers by emoji (searchStickers, verified in
     *  td_api.tl). The result parses exactly like the
     *  recent/favorite lists, so results can be sent by file
     *  id straight from the grid. */
    fun searchByEmoji(emojis: String) {
        attach()
        val query = emojis.trim()
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        TdRepository.sendRaw(TdClient.request("searchStickers").apply {
            put(
                "sticker_type",
                JSONObject().put("@type", "stickerTypeRegular")
            )
            put("emojis", query)
            put("query", "")
            put("input_language_codes", org.json.JSONArray())
            put("offset", 0)
            // Client-chosen request size (v0.9.11, A3):
            // widened from 50 to 100 — a display limit of
            // ours, not a server rule.
            put("limit", 100)
        }) { response ->
            if (!TdClient.isError(response)) {
                _searchResults.value = parseStickers(response)
            }
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
    }

    /** Re-fetch the installed-set chips — the sticker-set
     *  management page (v0.8.0, G3) calls this after adding or
     *  removing a set so the panel and the manager agree. */
    fun refreshInstalledSets() {
        attach()
        fetchInstalledSets()
    }

    private fun fetchInstalledSets() {
        TdRepository.sendRaw(TdClient.request("getInstalledStickerSets").apply {
            put(
                "sticker_type",
                JSONObject().put("@type", "stickerTypeRegular")
            )
        }) { response ->
            if (!TdClient.isError(response)) {
                val arr = response.optJSONArray("sets")
                val out = ArrayList<StickerSetUi>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val set = arr.optJSONObject(i) ?: continue
                        out.add(
                            StickerSetUi(
                                set.optLong("id"),
                                set.optString("title")
                            )
                        )
                    }
                }
                _stickerSets.value = out
            }
        }
    }

    /** Load one installed set's stickers for the panel
     *  (getStickerSet; its stickers array parses exactly like
     *  the recent/favorite lists). */
    fun loadSet(setId: Long) {
        attach()
        _setStickers.value = emptyList()
        TdRepository.sendRaw(TdClient.request("getStickerSet").apply {
            put("set_id", setId)
        }) { response ->
            if (!TdClient.isError(response)) {
                _setStickers.value = parseStickers(response)
            }
        }
    }

    private fun republish() {
        _recentStickers.value = withPaths(_recentStickers.value)
        _favoriteStickers.value = withPaths(_favoriteStickers.value)
        _savedAnimations.value = withPaths(_savedAnimations.value)
        _setStickers.value = withPaths(_setStickers.value)
        _previewStickers.value = withPaths(_previewStickers.value)
        _searchResults.value = withPaths(_searchResults.value)
        _trendingSets.value = _trendingSets.value.map { set ->
            val path = pathByFileId[set.thumbFileId]
            if (path != null && set.thumbPath == null) {
                set.copy(thumbPath = path)
            } else {
                set
            }
        }
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
