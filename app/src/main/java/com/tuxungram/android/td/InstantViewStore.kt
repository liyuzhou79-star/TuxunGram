package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Flattened richText: plain text + entities in the same
 *  shape the chat renderer consumes (UTF-16 offsets, which
 *  is how Kotlin String indices count). */
data class IvText(
    val text: String,
    val entities: List<TextEntityUi> = emptyList()
)

/** One list entry of a pageBlockList. */
data class IvListItem(
    val label: String,
    val blocks: List<IvBlock>,
    // Non-null when the item carries a checkbox state.
    val checked: Boolean? = null
)

/**
 * One rendered unit of an Instant View page. Kinds:
 * title / subtitle / author / kicker / header / subheader /
 * paragraph / pre / footer / divider / quote / pullquote /
 * list / photo / details / group (cover, collage, slideshow,
 * embedded post, blockquote children) / table / article
 * (related-article link row) / file (audio, voice, document,
 * video poster rides photo) / weblink (embedded content).
 */
data class IvBlock(
    val kind: String,
    val rich: IvText? = null,
    val credit: IvText? = null,
    val children: List<IvBlock> = emptyList(),
    val listItems: List<IvListItem> = emptyList(),
    val photoFileId: Int = 0,
    val caption: IvText? = null,
    val tableRows: List<List<IvText>> = emptyList(),
    val linkUrl: String = "",
    val dateSeconds: Int = 0
)

/** Load state of the article page. */
sealed interface IvState {
    data object Idle : IvState
    data class Loading(val url: String) : IvState
    data class Loaded(
        val url: String,
        val blocks: List<IvBlock>,
        val viewCount: Int
    ) : IvState

    data class Failed(val url: String, val reason: String) : IvState
}

/**
 * Instant View (v0.9.26, S1): getWebPageInstantView serves
 * the server-prepared article (blocks of richText) for URLs
 * whose link preview carries instant_view_version > 0 — the
 * UI only offers the entry then, so a failure here means the
 * cached version went stale; the page reports it plainly
 * and offers the browser instead. Blocks are parsed once
 * into [IvBlock]s; photos download through the usual
 * downloadFile/updateFile pair and swap in as paths land.
 */
object InstantViewStore {
    private val _state = MutableStateFlow<IvState>(IvState.Idle)
    val state: StateFlow<IvState> = _state

    private val _photoPaths =
        MutableStateFlow<Map<Int, String>>(emptyMap())
    val photoPaths: StateFlow<Map<Int, String>> = _photoPaths

    private val pendingDownloads =
        ConcurrentHashMap<Int, Boolean>()

    @Volatile
    private var attached = false

    @Volatile
    private var currentUrl: String = ""

    fun open(url: String) {
        attach()
        currentUrl = url
        _photoPaths.value = emptyMap()
        _state.value = IvState.Loading(url)
        val query = TdClient.request("getWebPageInstantView").apply {
            put("url", url)
            put("only_local", false)
        }
        TdRepository.sendRaw(query) { response ->
            if (currentUrl != url) return@sendRaw
            if (TdClient.isError(response)) {
                _state.value = IvState.Failed(
                    url,
                    "这篇文章现在拿不到即时预览" +
                        "（错误码 ${response.optInt("code")}），" +
                        "可以用浏览器打开原文。"
                )
                return@sendRaw
            }
            val blocks = ArrayList<IvBlock>()
            val arr = response.optJSONArray("blocks")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    parseBlock(arr.optJSONObject(i))?.let {
                        blocks.add(it)
                    }
                }
            }
            if (blocks.isEmpty()) {
                _state.value = IvState.Failed(
                    url,
                    "这篇文章没有即时预览内容，可以用浏览器打开原文。"
                )
            } else {
                _state.value = IvState.Loaded(
                    url, blocks, response.optInt("view_count")
                )
            }
        }
    }

    fun close() {
        currentUrl = ""
        _state.value = IvState.Idle
    }

    private fun attach() {
        if (attached) return
        attached = true
        TdRepository.addUpdateListener { update ->
            if (update.optString("@type") == "updateFile") {
                handleFile(update.optJSONObject("file"))
            }
        }
    }

    private fun handleFile(file: JSONObject?) {
        if (file == null) return
        val fileId = file.optInt("id")
        if (!pendingDownloads.containsKey(fileId)) return
        val local = file.optJSONObject("local") ?: return
        if (!local.optBoolean("is_downloading_completed")) return
        val path = local.optString("path")
        if (path.isEmpty()) return
        pendingDownloads.remove(fileId)
        _photoPaths.value = _photoPaths.value + (fileId to path)
    }

    /** Register a photo file from a block: seed the path when
     *  it is already on disk, otherwise start the download. */
    private fun registerPhoto(file: JSONObject?): Int {
        if (file == null) return 0
        val fileId = file.optInt("id")
        if (fileId == 0) return 0
        val local = file.optJSONObject("local")
        val done = local?.optBoolean("is_downloading_completed")
            ?: false
        val path = local?.optString("path") ?: ""
        if (done && path.isNotEmpty()) {
            _photoPaths.value = _photoPaths.value + (fileId to path)
        } else if (!pendingDownloads.containsKey(fileId)) {
            pendingDownloads[fileId] = true
            val query = TdClient.request("downloadFile").apply {
                put("file_id", fileId)
                put("priority", 8)
                put("offset", 0)
                put("limit", 0)
                put("synchronous", false)
            }
            TdRepository.sendRaw(query) { }
        }
        return fileId
    }

    /** The largest size's file of a photo object. */
    private fun largestPhotoFile(photo: JSONObject?): JSONObject? {
        val sizes = photo?.optJSONArray("sizes") ?: return null
        var best: JSONObject? = null
        for (i in 0 until sizes.length()) {
            val s = sizes.optJSONObject(i) ?: continue
            if (best == null ||
                s.optInt("width") > best.optInt("width")
            ) {
                best = s
            }
        }
        return best?.optJSONObject("photo")
    }

    private fun captionOf(block: JSONObject): IvText? {
        val caption = block.optJSONObject("caption") ?: return null
        val text = caption.optJSONObject("text") ?: return null
        return richToText(text)
    }

    /** Parse a PageBlock array. Public since v0.9.40 (G4):
     *  messageRichMessage carries the very same PageBlock
     *  list as an Instant View page, so MessageStore parses
     *  rich messages through this one entry point instead of
     *  growing a second block parser. Photo blocks register
     *  their file for download as a side effect (harmless for
     *  chat use: the file lands in TDLib's cache either way). */
    fun parseBlocks(arr: JSONArray?): List<IvBlock> {
        if (arr == null) return emptyList()
        val out = ArrayList<IvBlock>()
        for (i in 0 until arr.length()) {
            parseBlock(arr.optJSONObject(i))?.let { out.add(it) }
        }
        return out
    }

    /** Plain-text skeleton of a block list (v0.9.40, G4):
     *  every block's own text, captions, credits and table
     *  cells, depth-first, one block per line. Rich messages
     *  store this as their MsgItem.text so the chat-list
     *  preview, reply quotes and search hits show the words
     *  instead of a bare placeholder. Capped — a rich post
     *  can be article-length and nothing downstream needs
     *  more than the head. */
    fun blocksPlainText(blocks: List<IvBlock>): String {
        val sb = StringBuilder()
        fun add(text: String?) {
            val t = text?.trim().orEmpty()
            if (t.isEmpty()) return
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(t)
        }
        fun walk(block: IvBlock) {
            add(block.rich?.text)
            add(block.caption?.text)
            add(block.credit?.text)
            for (row in block.tableRows) {
                add(row.joinToString(" ") { it.text })
            }
            for (item in block.listItems) {
                item.blocks.forEach(::walk)
            }
            block.children.forEach(::walk)
            if (sb.length > 8192) return
        }
        blocks.forEach(::walk)
        return sb.toString().take(4096)
    }

    private fun parseBlock(block: JSONObject?): IvBlock? {
        if (block == null) return null
        return when (block.optString("@type")) {
            "pageBlockTitle" -> IvBlock(
                "title", rich = richToText(block.optJSONObject("title"))
            )
            "pageBlockSubtitle" -> IvBlock(
                "subtitle",
                rich = richToText(block.optJSONObject("subtitle"))
            )
            "pageBlockAuthorDate" -> IvBlock(
                "author",
                rich = richToText(block.optJSONObject("author")),
                dateSeconds = block.optInt("publish_date")
            )
            "pageBlockHeader" -> IvBlock(
                "header",
                rich = richToText(block.optJSONObject("header"))
            )
            "pageBlockSubheader" -> IvBlock(
                "subheader",
                rich = richToText(block.optJSONObject("subheader"))
            )
            "pageBlockSectionHeading" -> IvBlock(
                "header",
                rich = richToText(block.optJSONObject("text"))
            )
            "pageBlockKicker" -> IvBlock(
                "kicker",
                rich = richToText(block.optJSONObject("kicker"))
            )
            "pageBlockParagraph" -> IvBlock(
                "paragraph",
                rich = richToText(block.optJSONObject("text"))
            )
            "pageBlockThinking" -> IvBlock(
                "paragraph",
                rich = richToText(block.optJSONObject("text"))
            )
            "pageBlockPreformatted" -> IvBlock(
                "pre",
                rich = richToText(block.optJSONObject("text"))
            )
            "pageBlockFooter" -> IvBlock(
                "footer",
                rich = richToText(block.optJSONObject("footer"))
            )
            "pageBlockDivider" -> IvBlock("divider")
            "pageBlockBlockQuote" -> IvBlock(
                "quote",
                credit = block.optJSONObject("credit")
                    ?.let { richToText(it) },
                children = parseBlocks(block.optJSONArray("blocks"))
            )
            "pageBlockExpandableBlockQuote" -> IvBlock(
                "quote",
                rich = richToText(block.optJSONObject("text")),
                credit = block.optJSONObject("credit")
                    ?.let { richToText(it) }
            )
            "pageBlockPullQuote" -> IvBlock(
                "pullquote",
                rich = richToText(block.optJSONObject("text")),
                credit = block.optJSONObject("credit")
                    ?.let { richToText(it) }
            )
            "pageBlockList" -> {
                val items = ArrayList<IvListItem>()
                val arr = block.optJSONArray("items")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        items.add(
                            IvListItem(
                                label = item.optString("label"),
                                blocks = parseBlocks(
                                    item.optJSONArray("blocks")
                                ),
                                checked = if (
                                    item.optBoolean("has_checkbox")
                                ) {
                                    item.optBoolean("is_checked")
                                } else {
                                    null
                                }
                            )
                        )
                    }
                }
                IvBlock("list", listItems = items)
            }
            "pageBlockPhoto" -> IvBlock(
                "photo",
                photoFileId = registerPhoto(
                    largestPhotoFile(block.optJSONObject("photo"))
                ),
                caption = captionOf(block)
            )
            "pageBlockAnimation" -> {
                val thumb = block.optJSONObject("animation")
                    ?.optJSONObject("thumbnail")
                IvBlock(
                    "photo",
                    photoFileId = registerPhoto(
                        largestPhotoFile(thumb)
                    ),
                    caption = captionOf(block)
                )
            }
            "pageBlockVideo" -> {
                val thumb = block.optJSONObject("video")
                    ?.optJSONObject("thumbnail")
                IvBlock(
                    "photo",
                    photoFileId = registerPhoto(
                        largestPhotoFile(thumb)
                    ),
                    caption = captionOf(block)
                )
            }
            "pageBlockCover" -> IvBlock(
                "group",
                children = listOfNotNull(
                    parseBlock(block.optJSONObject("cover"))
                )
            )
            "pageBlockCollage", "pageBlockSlideshow" -> IvBlock(
                "group",
                children = parseBlocks(block.optJSONArray("blocks")),
                caption = captionOf(block)
            )
            "pageBlockEmbeddedPost" -> IvBlock(
                "group",
                children = parseBlocks(block.optJSONArray("blocks")),
                caption = captionOf(block)
            )
            "pageBlockDetails" -> IvBlock(
                "details",
                rich = richToText(block.optJSONObject("header")),
                children = parseBlocks(block.optJSONArray("blocks"))
            )
            "pageBlockTable" -> {
                val rows = ArrayList<List<IvText>>()
                val cells = block.optJSONArray("cells")
                if (cells != null) {
                    for (r in 0 until cells.length()) {
                        val rowArr = cells.optJSONArray(r) ?: continue
                        val row = ArrayList<IvText>()
                        for (c in 0 until rowArr.length()) {
                            val cell = rowArr.optJSONObject(c)
                                ?: continue
                            row.add(
                                richToText(cell.optJSONObject("text"))
                                    ?: IvText("")
                            )
                        }
                        rows.add(row)
                    }
                }
                IvBlock(
                    "table",
                    rich = richToText(block.optJSONObject("caption")),
                    tableRows = rows
                )
            }
            "pageBlockRelatedArticles" -> {
                val children = ArrayList<IvBlock>()
                richToText(block.optJSONObject("header"))?.let {
                    children.add(IvBlock("subheader", rich = it))
                }
                val articles = block.optJSONArray("articles")
                if (articles != null) {
                    for (i in 0 until articles.length()) {
                        val a = articles.optJSONObject(i) ?: continue
                        children.add(
                            IvBlock(
                                "article",
                                rich = IvText(
                                    a.optString("title").ifEmpty {
                                        a.optString("url")
                                    }
                                ),
                                linkUrl = a.optString("url")
                            )
                        )
                    }
                }
                IvBlock("group", children = children)
            }
            "pageBlockEmbedded" -> IvBlock(
                "weblink",
                caption = captionOf(block),
                linkUrl = block.optString("url")
            )
            "pageBlockChatLink" -> IvBlock(
                "paragraph",
                rich = IvText(
                    buildString {
                        append(block.optString("title"))
                        val username = block.optString("username")
                        if (username.isNotEmpty()) {
                            append(" (@").append(username).append(")")
                        }
                    }
                )
            )
            "pageBlockAudio", "pageBlockVoiceNote",
            "pageBlockDocument" -> {
                val media = block.optJSONObject("audio")
                    ?: block.optJSONObject("voice_note")
                    ?: block.optJSONObject("document")
                val name = media?.optString("file_name")
                    ?.ifEmpty { null } ?: "附件"
                IvBlock(
                    "file",
                    rich = IvText("📎 $name"),
                    caption = captionOf(block)
                )
            }
            "pageBlockMap" -> {
                val loc = block.optJSONObject("location")
                IvBlock(
                    "paragraph",
                    rich = IvText(
                        "📍 地图位置：" +
                            "${loc?.optDouble("latitude") ?: 0.0}, " +
                            "${loc?.optDouble("longitude") ?: 0.0}"
                    ),
                    caption = captionOf(block)
                )
            }
            // Anchors, button rows, math expressions and
            // unsupported blocks carry no readable body text
            // of their own here; they are skipped rather than
            // rendered as empty gaps.
            else -> null
        }
    }

    /**
     * Flatten a richText tree into plain text + entities.
     * Nested styles stack (bold inside a link yields both
     * entities over the same span), matching how the chat
     * renderer layers spans.
     */
    fun richToText(node: JSONObject?): IvText? {
        if (node == null) return null
        val builder = StringBuilder()
        val entities = ArrayList<TextEntityUi>()
        appendRich(node, builder, entities)
        return IvText(builder.toString(), entities)
    }

    private fun appendRich(
        node: JSONObject,
        out: StringBuilder,
        entities: MutableList<TextEntityUi>
    ) {
        when (node.optString("@type")) {
            "richTextPlain" -> out.append(node.optString("text"))
            "richTexts" -> {
                val arr = node.optJSONArray("texts") ?: return
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let {
                        appendRich(it, out, entities)
                    }
                }
            }
            "richTextCustomEmoji" -> {
                val alt = node.optString("alternative_text")
                val start = out.length
                out.append(alt)
                entities.add(
                    TextEntityUi(
                        offset = start,
                        length = alt.length,
                        kind = "customEmoji",
                        customEmojiId = node.optLong("custom_emoji_id")
                    )
                )
            }
            "richTextAnchor" -> Unit
            "richTextIcon" -> Unit
            else -> {
                val child = node.optJSONObject("text")
                if (child == null) {
                    // Unknown wrapper without a text child:
                    // nothing readable to append.
                    return
                }
                val start = out.length
                appendRich(child, out, entities)
                val length = out.length - start
                if (length <= 0) return
                val kind = when (node.optString("@type")) {
                    "richTextBold" -> "bold"
                    "richTextItalic" -> "italic"
                    "richTextUnderline" -> "underline"
                    "richTextStrikethrough" -> "strike"
                    "richTextSpoiler" -> "spoiler"
                    "richTextFixed" -> "code"
                    "richTextMarked" -> "quote"
                    "richTextUrl" -> "textUrl"
                    "richTextMention" -> "mention"
                    "richTextMentionName" -> "mentionName"
                    "richTextHashtag" -> "hashtag"
                    "richTextCashtag" -> "cashtag"
                    "richTextBotCommand" -> "botCommand"
                    "richTextEmailAddress" -> "email"
                    "richTextPhoneNumber" -> "phone"
                    "richTextAnchorLink",
                    "richTextReferenceLink" -> "textUrl"
                    else -> ""
                }
                if (kind.isNotEmpty()) {
                    entities.add(
                        TextEntityUi(
                            offset = start,
                            length = length,
                            kind = kind,
                            url = when (kind) {
                                "textUrl" -> node.optString("url")
                                else -> ""
                            },
                            userId = if (kind == "mentionName") {
                                node.optLong("user_id")
                            } else {
                                0L
                            }
                        )
                    )
                }
            }
        }
    }
}
