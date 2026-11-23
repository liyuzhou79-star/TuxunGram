package com.tuxungram.android.td

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Gallery entry kinds (v0.9.38, E1), one per applicable
 *  BackgroundType variant of the tl. Chat-theme
 *  backgrounds are not wallpapers (setDefaultBackground
 *  refuses them) and never reach this list. */
const val BG_KIND_WALLPAPER = 0
const val BG_KIND_PATTERN = 1
const val BG_KIND_FILL = 2

/** One entry of the official wallpaper gallery
 *  (v0.9.38, E1), flattened from a tl background object.
 *  [typeJson] is the raw BackgroundType object, kept
 *  verbatim so applying can hand it back to
 *  setDefaultBackground exactly as the server shipped
 *  it. [fillArgb] is the display/apply color of fill
 *  and pattern entries (solid color, or a gradient's
 *  top color — the local renderer paints solids only,
 *  recorded in the version notes). */
data class BgUi(
    val id: Long,
    val name: String,
    val isDefault: Boolean,
    val isDark: Boolean,
    val kind: Int,
    val thumbFileId: Int = 0,
    val thumbPath: String? = null,
    val docFileId: Int = 0,
    val docPath: String? = null,
    val fillArgb: Long = 0L,
    val typeJson: String = ""
)

/**
 * Official wallpaper gallery (v0.9.38, E1).
 *
 * Interfaces verified against this repo's td_api.tl —
 * there is NO getBackgrounds in this tl version. The
 * gallery source is getInstalledBackgrounds
 * (for_dark_theme only changes ordering): the server's
 * default wallpapers (is_default) plus everything the
 * user has installed before. Applying is
 * setDefaultBackground — inputBackgroundRemote for
 * wallpaper/pattern entries, a null background plus the
 * fill type for fills — which also syncs the choice to
 * the account's other clients. deleteDefaultBackground
 * restores the server default.
 *
 * Local rendering rides the existing wallpaper chain:
 * an applied wallpaper/pattern's document is downloaded
 * through the normal file path, copied into the app's
 * files directory (TDLib's cache can be pruned; the copy
 * cannot), and handed to UiSettings as the global
 * wallpaper picture; an applied fill becomes the
 * UiSettings gallery fill color. Priority, as rendered
 * by the chat page: per-chat override > global picture
 * > gallery fill > preset solid > default doodle.
 * Pattern entries render locally as their pattern image
 * without the fill/intensity compositing the official
 * client does — an approximation, recorded here.
 */
object BackgroundStore {
    private const val GALLERY_FILE = "chat_wallpaper_gallery.jpg"

    private var appContext: Context? = null

    private val _backgrounds = MutableStateFlow<List<BgUi>>(emptyList())
    val backgrounds: StateFlow<List<BgUi>> = _backgrounds

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _applyingId = MutableStateFlow(0L)
    val applyingId: StateFlow<Long> = _applyingId

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    /** thumbnail fileId -> background id. */
    private val thumbDownloads = ConcurrentHashMap<Int, Long>()

    /** full document fileId -> background id, for an
     *  apply waiting on the download. */
    private val applyDownloads = ConcurrentHashMap<Int, Long>()

    @Volatile
    private var attached = false

    fun init(context: Context) {
        appContext = context.applicationContext
    }

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
            val path = local.optString("path")
            if (path.isEmpty()) return@addUpdateListener
            val fileId = file.optInt("id")
            thumbDownloads.remove(fileId)?.let { bgId ->
                _backgrounds.value = _backgrounds.value.map { bg ->
                    if (bg.id == bgId) {
                        bg.copy(thumbPath = path)
                    } else {
                        bg
                    }
                }
            }
            applyDownloads.remove(fileId)?.let { bgId ->
                _backgrounds.value = _backgrounds.value.map { bg ->
                    if (bg.id == bgId) {
                        bg.copy(docPath = path)
                    } else {
                        bg
                    }
                }
                finishApplyWithFile(bgId, path)
            }
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun load() {
        attach()
        _loading.value = true
        _error.value = null
        val merged = LinkedHashMap<Long, BgUi>()
        var pending = 2
        var failed = false
        fun done() {
            pending--
            if (pending > 0) return
            _loading.value = false
            val list = merged.values.toList()
            _backgrounds.value = list
            if (list.isEmpty()) {
                _error.value = if (failed) {
                    "图库没拉到，检查网络后再试一次。"
                } else {
                    "图库是空的。"
                }
            }
            for (bg in list) {
                if (bg.thumbPath == null && bg.thumbFileId != 0) {
                    thumbDownloads[bg.thumbFileId] = bg.id
                    download(bg.thumbFileId, priority = 8)
                }
            }
        }
        for (dark in listOf(false, true)) {
            val query = TdClient.request("getInstalledBackgrounds")
                .apply { put("for_dark_theme", dark) }
            TdRepository.sendRaw(query) { response ->
                if (TdClient.isError(response)) {
                    failed = true
                } else {
                    val arr = response.optJSONArray("backgrounds")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val bg = parseBackground(obj) ?: continue
                            if (!merged.containsKey(bg.id)) {
                                merged[bg.id] = bg
                            }
                        }
                    }
                }
                done()
            }
        }
    }

    private fun parseBackground(obj: JSONObject): BgUi? {
        val type = obj.optJSONObject("type") ?: return null
        val kind = when (type.optString("@type")) {
            "backgroundTypeWallpaper" -> BG_KIND_WALLPAPER
            "backgroundTypePattern" -> BG_KIND_PATTERN
            "backgroundTypeFill" -> BG_KIND_FILL
            // backgroundTypeChatTheme is not a wallpaper.
            else -> return null
        }
        val document = obj.optJSONObject("document")
        val docFile = document?.optJSONObject("document")
        val thumbFile = document?.optJSONObject("thumbnail")
            ?.optJSONObject("file")
        val fill = when (kind) {
            BG_KIND_FILL -> type.optJSONObject("fill")
            BG_KIND_PATTERN -> type.optJSONObject("fill")
            else -> null
        }
        return BgUi(
            id = obj.optLong("id"),
            name = obj.optString("name"),
            isDefault = obj.optBoolean("is_default"),
            isDark = obj.optBoolean("is_dark"),
            kind = kind,
            thumbFileId = thumbFile?.optInt("id") ?: 0,
            thumbPath = downloadedPath(thumbFile),
            docFileId = docFile?.optInt("id") ?: 0,
            docPath = downloadedPath(docFile),
            fillArgb = fill?.let { fillDisplayArgb(it) } ?: 0L,
            typeJson = type.toString()
        )
    }

    /** The one color a fill shows and applies locally:
     *  the solid color, a gradient's top color, or a
     *  freeform gradient's first color (the tl ships RGB
     *  ints; alpha is always opaque here). */
    private fun fillDisplayArgb(fill: JSONObject): Long {
        val rgb = when (fill.optString("@type")) {
            "backgroundFillSolid" -> fill.optInt("color")
            "backgroundFillGradient" -> fill.optInt("top_color")
            "backgroundFillFreeformGradient" ->
                fill.optJSONArray("colors")?.optInt(0) ?: 0
            else -> 0
        }
        return 0xFF000000L or (rgb.toLong() and 0xFFFFFFL)
    }

    private fun downloadedPath(file: JSONObject?): String? {
        val local = file?.optJSONObject("local") ?: return null
        if (!local.optBoolean("is_downloading_completed")) return null
        return local.optString("path").ifEmpty { null }
    }

    private fun download(fileId: Int, priority: Int) {
        val query = TdClient.request("downloadFile").apply {
            put("file_id", fileId)
            put("priority", priority)
            put("offset", 0)
            put("limit", 0)
            put("synchronous", false)
        }
        TdRepository.sendRaw(query) { response ->
            // The response IS the File object (the v0.9.9
            // lesson): a file that was already on disk
            // completes here without any updateFile.
            if (TdClient.isError(response)) return@sendRaw
            val local = response.optJSONObject("local")
                ?: return@sendRaw
            if (!local.optBoolean("is_downloading_completed")) {
                return@sendRaw
            }
            val path = local.optString("path")
            if (path.isEmpty()) return@sendRaw
            val id = response.optInt("id")
            thumbDownloads.remove(id)?.let { bgId ->
                _backgrounds.value = _backgrounds.value.map { bg ->
                    if (bg.id == bgId) {
                        bg.copy(thumbPath = path)
                    } else {
                        bg
                    }
                }
            }
            applyDownloads.remove(id)?.let { bgId ->
                finishApplyWithFile(bgId, path)
            }
        }
    }

    /** Apply one gallery entry as the default chat
     *  background. [forDark] chooses which theme's
     *  default is set on the server; the local picture /
     *  fill follows once the server accepts. */
    fun apply(bg: BgUi, forDark: Boolean) {
        attach()
        if (_applyingId.value != 0L) return
        _applyingId.value = bg.id
        val type = try {
            JSONObject(bg.typeJson)
        } catch (t: Throwable) {
            _applyingId.value = 0L
            _notice.value = "这张背景的数据不完整，换一张试试。"
            return
        }
        val query = TdClient.request("setDefaultBackground").apply {
            if (bg.kind == BG_KIND_FILL) {
                put("background", JSONObject.NULL)
            } else {
                put(
                    "background",
                    JSONObject().apply {
                        put("@type", "inputBackgroundRemote")
                        put("background_id", bg.id)
                    }
                )
            }
            put("type", type)
            put("for_dark_theme", forDark)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _applyingId.value = 0L
                _notice.value =
                    "没设上，服务器拒绝了。（错误码 " +
                        "${response.optInt("code")}）"
                return@sendRaw
            }
            if (bg.kind == BG_KIND_FILL) {
                UiSettings.setChatBgFill(bg.fillArgb)
                _applyingId.value = 0L
                _notice.value = "已设为聊天背景（纯色）。"
                return@sendRaw
            }
            // Wallpaper / pattern: the local picture is
            // the downloaded document, copied into our
            // own files directory.
            val known = bg.docPath
            if (known != null) {
                finishApplyWithFile(bg.id, known)
            } else if (bg.docFileId != 0) {
                applyDownloads[bg.docFileId] = bg.id
                download(bg.docFileId, priority = 32)
                _notice.value = "正在下载这张背景图…"
            } else {
                _applyingId.value = 0L
                _notice.value =
                    "服务器那边已经设好了，但这张图没有可下载的原图，" +
                        "本机先保持现在的背景。"
            }
        }
    }

    /** Copy a downloaded gallery document into the app
     *  files directory and make it the global wallpaper
     *  picture. Runs the copy off the callback thread. */
    private fun finishApplyWithFile(bgId: Long, sourcePath: String) {
        val ctx = appContext
        if (ctx == null) {
            _applyingId.value = 0L
            return
        }
        Thread {
            val dest = File(ctx.filesDir, GALLERY_FILE)
            val ok = try {
                File(sourcePath).copyTo(dest, overwrite = true)
                true
            } catch (t: Throwable) {
                false
            }
            android.os.Handler(
                android.os.Looper.getMainLooper()
            ).post {
                if (_applyingId.value == bgId) {
                    _applyingId.value = 0L
                }
                if (ok) {
                    UiSettings.setChatBgImage(dest.absolutePath)
                    _notice.value = "已设为聊天背景。"
                } else {
                    _notice.value =
                        "背景图下载好了但没能存到本机，服务器那边" +
                            "已经设好，本机先保持现在的背景。"
                }
            }
        }.start()
    }

    /** Restore the server default background (both
     *  themes) and drop the local gallery choice. A
     *  wallpaper the user picked from their own photos
     *  is NOT touched — only the gallery's own file and
     *  the gallery fill are ours to clear. */
    fun resetDefault() {
        attach()
        var pending = 2
        var failed = false
        fun done() {
            pending--
            if (pending > 0) return
            val image = UiSettings.chatBgImage.value
            if (image.endsWith(GALLERY_FILE)) {
                UiSettings.setChatBgImage("")
            }
            UiSettings.setChatBgFill(0L)
            _notice.value = if (failed) {
                "恢复默认时服务器有一项没应答，本机的图库背景已经清掉。"
            } else {
                "已恢复默认背景。"
            }
        }
        for (dark in listOf(false, true)) {
            val query = TdClient.request("deleteDefaultBackground")
                .apply { put("for_dark_theme", dark) }
            TdRepository.sendRaw(query) { response ->
                if (TdClient.isError(response)) failed = true
                done()
            }
        }
    }
}
