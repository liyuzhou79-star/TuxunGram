package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Custom emoji images (v0.9.2, L2). A textEntityTypeCustomEmoji
 * carries only an id; the picture is a sticker fetched with
 * getCustomEmojiStickers (verified in td_api.tl). The response
 * arrives "in arbitrary order" per the tl docs, so each sticker
 * is matched back through its full_type.custom_emoji_id
 * (stickerFullTypeCustomEmoji, also tl-verified).
 *
 * Static formats (webp) render from the sticker file itself;
 * animated (tgs) and video (webm) custom emoji cannot decode
 * through BitmapFactory, so they render from the sticker's
 * static thumbnail (webp/jpeg) instead — a still frame, which
 * is what the inline size would show anyway.
 *
 * [paths] publishes customEmojiId -> downloaded file path;
 * the rich-text builder observes it and swaps the alt emoji
 * character for the image as soon as the file lands.
 */
object CustomEmojiStore {
    private val _paths = MutableStateFlow<Map<Long, String>>(emptyMap())
    val paths: StateFlow<Map<Long, String>> = _paths

    private val pathById = ConcurrentHashMap<Long, String>()
    private val emojiByFileId = ConcurrentHashMap<Int, Long>()
    private val fetchPending = ConcurrentHashMap.newKeySet<Long>()
    private val fetched = ConcurrentHashMap.newKeySet<Long>()

    @Volatile
    private var attached = false

    private fun attach() {
        if (attached) return
        attached = true
        TdRepository.addUpdateListener { update ->
            if (update.optString("@type") != "updateFile") {
                return@addUpdateListener
            }
            val file = update.optJSONObject("file")
                ?: return@addUpdateListener
            val local = file.optJSONObject("local")
                ?: return@addUpdateListener
            if (!local.optBoolean("is_downloading_completed")) {
                return@addUpdateListener
            }
            val emojiId = emojiByFileId[file.optInt("id")]
                ?: return@addUpdateListener
            val path = local.optString("path")
            if (path.isNotEmpty()) {
                pathById[emojiId] = path
                publish()
            }
        }
    }

    private fun publish() {
        _paths.value = HashMap(pathById)
    }

    /** Fetch (and download) the stickers for [ids] that are not
     *  already on disk. Safe to call on every composition of a
     *  message containing custom emoji: known ids are skipped. */
    fun ensure(ids: Collection<Long>) {
        attach()
        val missing = ids.filter {
            it != 0L && !pathById.containsKey(it) &&
                !fetched.contains(it) && fetchPending.add(it)
        }
        if (missing.isEmpty()) return
        val arr = JSONArray()
        for (id in missing) arr.put(id)
        val query = TdClient.request("getCustomEmojiStickers").apply {
            put("custom_emoji_ids", arr)
        }
        TdRepository.sendRaw(query) { response ->
            for (id in missing) fetchPending.remove(id)
            if (TdClient.isError(response)) return@sendRaw
            val stickers = response.optJSONArray("stickers")
                ?: return@sendRaw
            for (i in 0 until stickers.length()) {
                val sticker = stickers.optJSONObject(i) ?: continue
                val emojiId = sticker.optJSONObject("full_type")
                    ?.takeIf {
                        it.optString("@type") ==
                            "stickerFullTypeCustomEmoji"
                    }
                    ?.optLong("custom_emoji_id") ?: continue
                fetched.add(emojiId)
                val file = pickRenderFile(sticker) ?: continue
                val fileId = file.optInt("id", 0)
                if (fileId == 0) continue
                val local = file.optJSONObject("local")
                if (local?.optBoolean("is_downloading_completed") == true) {
                    val path = local.optString("path")
                    if (path.isNotEmpty()) {
                        pathById[emojiId] = path
                        publish()
                    }
                } else {
                    emojiByFileId[fileId] = emojiId
                    TdRepository.sendRaw(
                        TdClient.request("downloadFile").apply {
                            put("file_id", fileId)
                            put("priority", 8)
                            put("offset", 0L)
                            put("limit", 0L)
                            put("synchronous", false)
                        }
                    ) {}
                }
            }
        }
    }

    /** The file to render inline for one custom emoji sticker:
     *  the sticker file for static webp, the static thumbnail
     *  for animated/video formats. */
    private fun pickRenderFile(sticker: JSONObject): JSONObject? {
        val format = sticker.optJSONObject("format")
            ?.optString("@type")
        if (format == "stickerFormatWebp") {
            return sticker.optJSONObject("sticker")
        }
        val thumbFile = sticker.optJSONObject("thumbnail")
            ?.optJSONObject("file")
        return thumbFile ?: sticker.optJSONObject("sticker")
    }
}
