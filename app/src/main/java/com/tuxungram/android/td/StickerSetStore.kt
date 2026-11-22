package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One installed sticker set on the management page, flattened
 *  from stickerSetInfo (verified in td_api.tl). */
data class StickerSetManageUi(
    val id: Long,
    val name: String,
    val title: String,
    val count: Int,
    val thumbFileId: Int,
    val thumbPath: String?
)

/**
 * Sticker-set management (v0.8.0, G3): list installed sets with
 * covers, remove a set, add one by its t.me/addstickers link or
 * bare name. Interfaces verified in td_api.tl:
 * getInstalledStickerSets, searchStickerSet (resolves a set
 * NAME to the full set), changeStickerSet (install/remove).
 * After any change the chat panel's set chips are refreshed via
 * MediaStore.refreshInstalledSets so both views agree.
 */
object StickerSetStore {
    private val _sets = MutableStateFlow<List<StickerSetManageUi>>(emptyList())
    val sets: StateFlow<List<StickerSetManageUi>> = _sets

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    private val pathByFileId = ConcurrentHashMap<Int, String>()
    private val downloadPending = ConcurrentHashMap.newKeySet<Int>()

    @Volatile
    private var attached = false

    private fun attach() {
        if (attached) return
        attached = true
        TdRepository.addUpdateListener { update ->
            if (update.optString("@type") != "updateFile") {
                return@addUpdateListener
            }
            val file = update.optJSONObject("file") ?: return@addUpdateListener
            val local = file.optJSONObject("local") ?: return@addUpdateListener
            if (!local.optBoolean("is_downloading_completed")) {
                return@addUpdateListener
            }
            val fileId = file.optInt("id")
            downloadPending.remove(fileId)
            pathByFileId[fileId] = local.optString("path")
            _sets.value = _sets.value.map { set ->
                if (set.thumbFileId == fileId && set.thumbPath == null) {
                    set.copy(thumbPath = local.optString("path"))
                } else {
                    set
                }
            }
        }
    }

    fun load() {
        attach()
        val query = TdClient.request("getInstalledStickerSets").apply {
            put(
                "sticker_type",
                JSONObject().put("@type", "stickerTypeRegular")
            )
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _notice.value = "贴纸包列表没拉下来，检查网络再试。"
                return@sendRaw
            }
            val arr = response.optJSONArray("sets")
            val out = ArrayList<StickerSetManageUi>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val set = arr.optJSONObject(i) ?: continue
                    out.add(parseSet(set))
                }
            }
            _sets.value = out
        }
    }

    private fun parseSet(set: JSONObject): StickerSetManageUi {
        // Cover: the set thumbnail if present, else the first
        // cover sticker's file.
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
        var path = pathByFileId[fileId]
        val local = thumbFile?.optJSONObject("local")
        if (local?.optBoolean("is_downloading_completed") == true) {
            path = local.optString("path")
            if (path != null) pathByFileId[fileId] = path
        } else if (fileId != 0) {
            ensureThumb(fileId)
        }
        return StickerSetManageUi(
            id = set.optLong("id"),
            name = set.optString("name"),
            title = set.optString("title"),
            count = set.optInt("size"),
            thumbFileId = fileId,
            thumbPath = path
        )
    }

    private fun ensureThumb(fileId: Int) {
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

    /** Remove an installed set (it stays in the archive-free
     *  "recently removed" state server-side; re-adding by name
     *  brings it back). */
    fun remove(set: StickerSetManageUi) {
        val query = TdClient.request("changeStickerSet").apply {
            put("set_id", set.id)
            put("is_installed", false)
            put("is_archived", false)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _notice.value = "移除「${set.title}」失败，再试一次。"
            } else {
                _notice.value = "已移除「${set.title}」。"
                load()
                MediaStore.refreshInstalledSets()
            }
        }
    }

    /** Add a set from a t.me/addstickers/NAME link, an
     *  addstickers URL, or the bare set name. */
    fun addByInput(raw: String) {
        val name = extractSetName(raw)
        if (name.isEmpty()) {
            _notice.value = "没认出贴纸包名，粘贴 t.me/addstickers/ 开头的链接试试。"
            return
        }
        _notice.value = "正在查找「$name」…"
        val query = TdClient.request("searchStickerSet").apply {
            put("name", name)
            put("ignore_cache", false)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _notice.value = "没找到叫「$name」的贴纸包，检查一下名字。"
                return@sendRaw
            }
            val setId = response.optLong("id")
            val title = response.optString("title")
            if (setId == 0L) {
                _notice.value = "没找到叫「$name」的贴纸包，检查一下名字。"
                return@sendRaw
            }
            val install = TdClient.request("changeStickerSet").apply {
                put("set_id", setId)
                put("is_installed", true)
                put("is_archived", false)
            }
            TdRepository.sendRaw(install) { done ->
                if (TdClient.isError(done)) {
                    _notice.value = "添加「$title」失败，再试一次。"
                } else {
                    _notice.value = "已添加「$title」，去贴纸面板就能用了。"
                    load()
                    MediaStore.refreshInstalledSets()
                }
            }
        }
    }

    /** Install a set whose id is already known (v0.9.4, N5:
     *  the trending preview's add button) — the same
     *  changeStickerSet call addByInput ends with, minus the
     *  name resolution. The result goes to the callback so
     *  the caller can update its own button state; the panel
     *  chips and trending row refresh either way. */
    fun installById(setId: Long, onDone: (Boolean) -> Unit) {
        val install = TdClient.request("changeStickerSet").apply {
            put("set_id", setId)
            put("is_installed", true)
            put("is_archived", false)
        }
        TdRepository.sendRaw(install) { done ->
            val ok = !TdClient.isError(done)
            if (ok) {
                MediaStore.refreshInstalledSets()
                MediaStore.loadTrending()
            }
            onDone(ok)
        }
    }

    private fun extractSetName(raw: String): String {
        val trimmed = raw.trim()
        val marker = "addstickers/"
        val idx = trimmed.indexOf(marker)
        val candidate = if (idx >= 0) {
            trimmed.substring(idx + marker.length)
        } else {
            trimmed
        }
        return candidate
            .substringBefore('?')
            .substringBefore('/')
            .substringBefore(' ')
            .trim()
    }

    fun clearNotice() {
        _notice.value = null
    }

    // ---- Creating a sticker set (v0.9.26, S4, basic) ----

    /** One image chosen for the new set, already validated by
     *  the caller (static: PNG/WebP, one side exactly
     *  512 px, the other at most 512; video: WebM ≤ 256 KB,
     *  ≤ 3 s, one side 512; animated: TGS ≤ 64 KB on a
     *  512×512 canvas — the server rejects anything else
     *  and this flow does not resize or transcode). */
    data class NewStickerItem(
        val path: String,
        /** "webp" (static, PNG rides here too), "webm"
         *  (video) or "tgs" (animated) — one set, one
         *  format; createSet refuses a mix. */
        val format: String,
        val emoji: String
    )

    private val _createResult = MutableStateFlow<String?>(null)

    /** Outcome of the last create attempt: "ok:<name>" on
     *  success, "err:<plain reason>" on failure. */
    val createResult: StateFlow<String?> = _createResult

    fun clearCreateResult() {
        _createResult.value = null
    }

    /** Create a regular sticker set (createNewStickerSet,
     *  verified in td_api.tl: user_id is my own id, stickers
     *  are newSticker{inputFileLocal, format, emojis}).
     *  Static PNG files ride format stickerFormatWebp —
     *  StickerFormat has no PNG variant and the server
     *  re-encodes uploads. Video (v0.9.36, C2) rides
     *  stickerFormatWebm and animated stickerFormatTgs
     *  through the exact same newSticker shape: the
     *  interface is isomorphic, only the format tag and
     *  the caller's validation differ. Nothing is
     *  transcoded here. On success the set is installed
     *  server-side right away; the management list reloads
     *  and the share link is t.me/addstickers/<name>. */
    fun createSet(
        title: String,
        name: String,
        items: List<NewStickerItem>
    ) {
        val myId = MeStore.me.value.id
        if (myId == 0L) {
            _createResult.value = "err:账号状态还没就绪，稍后再试。"
            return
        }
        if (items.isEmpty()) {
            _createResult.value = "err:先选至少一张图片。"
            return
        }
        val formatType = when (items.first().format) {
            "webm" -> "stickerFormatWebm"
            "tgs" -> "stickerFormatTgs"
            else -> "stickerFormatWebp"
        }
        if (items.any { it.format != items.first().format }) {
            _createResult.value =
                "err:一个贴纸包只能用同一种格式，请把静态图、" +
                    "视频和动图分开建包。"
            return
        }
        _createResult.value = null
        val stickers = org.json.JSONArray()
        for (item in items) {
            stickers.put(JSONObject().apply {
                put("@type", "newSticker")
                put("sticker", JSONObject().apply {
                    put("@type", "inputFileLocal")
                    put("path", item.path)
                })
                put(
                    "format",
                    JSONObject().put("@type", formatType)
                )
                put(
                    "emojis",
                    item.emoji.ifBlank { "😀" }
                )
                put("mask_position", JSONObject.NULL)
                put("keywords", org.json.JSONArray())
            })
        }
        val query = TdClient.request("createNewStickerSet").apply {
            put("user_id", myId)
            put("title", title)
            put("name", name)
            put(
                "sticker_type",
                JSONObject().put("@type", "stickerTypeRegular")
            )
            put("needs_repainting", false)
            put("stickers", stickers)
            put("source", "")
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                val message = response.optString("message")
                _createResult.value = "err:" + when {
                    message.contains("NAME_OCCUPIED", true) ||
                        message.contains("already", true) ->
                        "这个短名已经被占用了，换一个短名再试。"
                    message.contains("USERNAME", true) ->
                        "需要先给账号设置一个用户名，才能创建贴纸包。"
                    message.contains("STICKER", true) &&
                        message.contains("INVALID", true) ->
                        "有一张不符合贴纸要求：静态图要 PNG/WebP、" +
                            "一边 512 像素、另一边不超过 512；" +
                            "视频贴纸要 WebM、不超过 256 KB、" +
                            "3 秒以内、一边 512；动图要 TGS、" +
                            "不超过 64 KB、画布 512×512。换一张再试。"
                    message.contains("VIDEO", true) ->
                        "视频贴纸不符合服务器要求（WebM 格式、" +
                            "不超过 256 KB、3 秒以内、一边正好 " +
                            "512 像素），换一个文件再试。"
                    else ->
                        "创建失败，再试一次。（错误码 " +
                            "${response.optInt("code")}）" +
                            if (message.isNotEmpty()) " $message" else ""
                }
                return@sendRaw
            }
            _createResult.value = "ok:" + response.optString("name", name)
            load()
            MediaStore.refreshInstalledSets()
        }
    }
}
