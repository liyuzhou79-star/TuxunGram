package com.tuxungram.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.CustomEmojiStore
import com.tuxungram.android.td.DeepLink
import com.tuxungram.android.td.MsgItem
import com.tuxungram.android.td.TextEntityUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Rich message text (v0.9.2, L1): renders a message's text or
 * caption with its TDLib formatting entities as a Compose
 * AnnotatedString — bold/italic/underline/strikethrough, code
 * and pre blocks in monospace, quotes tinted, spoilers masked
 * until tapped, and the link family (url, textUrl, mention,
 * mentionName, botCommand, email, phone) clickable in the
 * accent color, the way mature clients render them. Hashtags
 * and cashtags are clickable too (v0.9.44, N8): the host
 * supplies [onHashtag], which opens the in-chat search for
 * the tag — the official behaviour for a tapped tag.
 *
 * One builder for every surface: the main chat, comment
 * threads, forum topics and any preview that shows a MsgItem's
 * text all call this, so formatting behaves identically
 * everywhere. [onBotCommand] sends the tapped command in the
 * surface's own chat (each store has its own send path).
 *
 * Entity offsets are UTF-16 code units (td_api.tl), which is
 * exactly how Kotlin String indices count.
 */
@Composable
fun RichMessageText(
    msg: MsgItem,
    fontSize: TextUnit = 15.sp,
    onBotCommand: (String) -> Unit = {},
    // Hashtag / cashtag tap (v0.9.44, N8): the host opens
    // its in-chat search for the tag. Preview surfaces keep
    // the default no-op, so list previews stay inert.
    onHashtag: (String) -> Unit = {},
    // Secondary surfaces (shared-media rows, the photo
    // viewer's caption — regression fix R4, v0.9.6) render the
    // same built text but in their own color and line budget.
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    // Link / mention / hashtag color override (v0.9.17, X2):
    // bubbles whose background is the official out-blue need
    // their links in the out-text color, not the accent.
    linkColor: Color = Color.Unspecified,
    // In-chat search highlight (v0.9.31, X3): when non-empty,
    // every case-insensitive occurrence of this query in the
    // text gets a warm highlight background, mapped through
    // the same custom-emoji offset shift as the entities.
    highlightQuery: String = ""
) {
    // Custom emoji (L2): kick off the sticker fetch for this
    // message's ids and watch the downloaded paths; the inline
    // images swap in as files land.
    val customs = remember(msg.entities) {
        msg.entities
            .filter { it.kind == "customEmoji" && it.customEmojiId != 0L }
            .sortedBy { it.offset }
    }
    val cePaths by CustomEmojiStore.paths.collectAsState()
    LaunchedEffect(msg.id, customs) {
        if (customs.isNotEmpty()) {
            CustomEmojiStore.ensure(customs.map { it.customEmojiId })
        }
    }
    if (msg.entities.isEmpty()) {
        if (highlightQuery.isEmpty()) {
            Text(msg.text, fontSize = fontSize, color = color)
        } else {
            Text(
                highlightedPlain(msg.text, highlightQuery),
                fontSize = fontSize, color = color
            )
        }
        return
    }
    val context = LocalContext.current
    val effectiveLinkColor = if (linkColor == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        linkColor
    }
    // Spoiler reveal state, scoped to this message instance:
    // entity offset -> revealed. Hidden spoilers render as a
    // solid mask; tapping one reveals it in place.
    val revealed = remember(msg.id) { mutableStateMapOf<Int, Boolean>() }
    val text = msg.text
    // Each custom emoji collapses its (usually 2-unit) alt text
    // into ONE placeholder character in the built string, so
    // every later entity offset shifts left by (length - 1)
    // per preceding custom emoji. removedBefore() computes
    // that shift for a position in the original text.
    fun removedBefore(pos: Int): Int {
        var removed = 0
        for (ce in customs) {
            if (ce.offset + ce.length <= pos) removed += ce.length - 1
        }
        return removed
    }
    val annotated = buildAnnotatedString {
        if (customs.isEmpty()) {
            append(text)
        } else {
            var cursor = 0
            for (ce in customs) {
                if (ce.offset < cursor) continue
                if (ce.offset > cursor) {
                    append(text.substring(cursor, ce.offset))
                }
                appendInlineContent(
                    "ce_${ce.customEmojiId}",
                    text.substring(
                        ce.offset,
                        minOf(ce.offset + ce.length, text.length)
                    )
                )
                cursor = ce.offset + ce.length
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
        for (entity in msg.entities) {
            val rawStart = entity.offset.coerceIn(0, text.length)
            val rawEnd = (entity.offset + entity.length)
                .coerceIn(rawStart, text.length)
            if (rawEnd <= rawStart) continue
            val start = rawStart - removedBefore(rawStart)
            val end = if (entity.kind == "customEmoji") {
                start + 1
            } else {
                rawEnd - removedBefore(rawEnd)
            }
            if (end <= start) continue
            val rangeText = text.substring(rawStart, rawEnd)
            when (entity.kind) {
                "bold" -> addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold), start, end
                )
                "italic" -> addStyle(
                    SpanStyle(fontStyle = FontStyle.Italic), start, end
                )
                "underline" -> addStyle(
                    SpanStyle(textDecoration = TextDecoration.Underline),
                    start, end
                )
                "strike" -> addStyle(
                    SpanStyle(textDecoration = TextDecoration.LineThrough),
                    start, end
                )
                "code", "pre" -> addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0x14000000)
                    ),
                    start, end
                )
                "quote" -> addStyle(
                    SpanStyle(
                        background = Color(0x14000000),
                        fontStyle = FontStyle.Italic
                    ),
                    start, end
                )
                "spoiler" -> {
                    val isRevealed = revealed[entity.offset] == true
                    if (!isRevealed) {
                        addStyle(
                            SpanStyle(
                                color = Color.Transparent,
                                background = Color(0xFF9E9E9E)
                            ),
                            start, end
                        )
                    }
                    addLink(
                        LinkAnnotation.Clickable(
                            tag = "spoiler:${entity.offset}",
                            styles = TextLinkStyles(
                                style = SpanStyle(color = effectiveLinkColor)
                            )
                        ) {
                            revealed[entity.offset] = true
                        },
                        start, end
                    )
                }
                "url", "textUrl" -> {
                    val raw = if (entity.kind == "textUrl") {
                        entity.url
                    } else {
                        rangeText
                    }
                    val target = if (raw.contains("://") ||
                        raw.startsWith("mailto:") || raw.startsWith("tel:")
                    ) {
                        raw
                    } else {
                        "https://$raw"
                    }
                    addLink(
                        LinkAnnotation.Clickable(
                            tag = "url",
                            styles = TextLinkStyles(
                                style = SpanStyle(color = effectiveLinkColor)
                            )
                        ) {
                            // Telegram links resolve in-app
                            // (v0.9.9, W3); everything else
                            // still goes to the browser.
                            com.tuxungram.android.td.LinkRouter
                                .open(context, target)
                        },
                        start, end
                    )
                }
                "mentionName" -> {
                    if (entity.userId != 0L) {
                        addLink(
                            LinkAnnotation.Clickable(
                                tag = "user",
                                styles = TextLinkStyles(
                                    style = SpanStyle(color = effectiveLinkColor)
                                )
                            ) {
                                DeepLink.pendingProfileUser.value =
                                    entity.userId
                            },
                            start, end
                        )
                    }
                }
                "mention" -> {
                    val name = rangeText.removePrefix("@")
                    if (name.isNotEmpty()) {
                        addLink(
                            LinkAnnotation.Clickable(
                                tag = "mention",
                                styles = TextLinkStyles(
                                    style = SpanStyle(color = effectiveLinkColor)
                                )
                            ) {
                                DeepLink.pendingProfileName.value = name
                            },
                            start, end
                        )
                    }
                }
                "botCommand" -> {
                    addLink(
                        LinkAnnotation.Clickable(
                            tag = "cmd",
                            styles = TextLinkStyles(
                                style = SpanStyle(color = effectiveLinkColor)
                            )
                        ) {
                            onBotCommand(rangeText)
                        },
                        start, end
                    )
                }
                "email" -> {
                    addLink(
                        LinkAnnotation.Clickable(
                            tag = "email",
                            styles = TextLinkStyles(
                                style = SpanStyle(color = effectiveLinkColor)
                            )
                        ) {
                            openExternal(context, "mailto:$rangeText")
                        },
                        start, end
                    )
                }
                "phone" -> {
                    addLink(
                        LinkAnnotation.Clickable(
                            tag = "tel",
                            styles = TextLinkStyles(
                                style = SpanStyle(color = effectiveLinkColor)
                            )
                        ) {
                            openExternal(context, "tel:$rangeText")
                        },
                        start, end
                    )
                }
                "hashtag", "cashtag" -> {
                    // Tappable tags (v0.9.44, N8): the tap
                    // hands the tag text (with its # / $) to
                    // the host, which searches this chat for
                    // it — the official client's behaviour,
                    // and the user's channel-post complaint.
                    addLink(
                        LinkAnnotation.Clickable(
                            tag = "tag",
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = effectiveLinkColor
                                )
                            )
                        ) {
                            onHashtag(rangeText)
                        },
                        start, end
                    )
                }
                // customEmoji: rendered as an inline image
                // through appendInlineContent above, no span
                // styling. "plain": nothing to do.
                else -> Unit
            }
        }
        // Search highlight (v0.9.31, X3): background spans
        // over every occurrence of the query, in built-string
        // coordinates via the same removedBefore() shift the
        // entities use; clamped to the built length so an
        // exotic case-fold can never push a span out of range.
        if (highlightQuery.isNotEmpty()) {
            val builtLength = text.length - removedBefore(text.length)
            val needle = highlightQuery.lowercase()
            val hay = text.lowercase()
            var from = 0
            while (needle.isNotEmpty()) {
                val idx = hay.indexOf(needle, from)
                if (idx < 0) break
                val rawEnd = idx + needle.length
                val start = (idx - removedBefore(idx))
                    .coerceIn(0, builtLength)
                val end = (rawEnd - removedBefore(rawEnd))
                    .coerceIn(start, builtLength)
                if (end > start) {
                    addStyle(
                        SpanStyle(background = Color(0x73FFC400)),
                        start, end
                    )
                }
                from = rawEnd
            }
        }
    }
    val inlineContent = remember(customs, cePaths) {
        customs.distinctBy { it.customEmojiId }.associate { ce ->
            val alt = text.substring(
                ce.offset.coerceIn(0, text.length),
                minOf(ce.offset + ce.length, text.length)
            )
            "ce_${ce.customEmojiId}" to InlineTextContent(
                Placeholder(
                    width = fontSize * 1.35,
                    height = fontSize * 1.35,
                    placeholderVerticalAlign =
                        PlaceholderVerticalAlign.TextCenter
                )
            ) {
                CustomEmojiInline(
                    path = cePaths[ce.customEmojiId],
                    alt = alt,
                    fontSize = fontSize
                )
            }
        }
    }
    Text(
        annotated,
        inlineContent = inlineContent,
        fontSize = fontSize,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * Render already-flattened text + entities without a
 * MsgItem (v0.9.26, S1): Instant View converts its richText
 * trees into exactly this shape, then rides the same
 * builder so links, mentions and styling behave like in
 * chat. The throwaway MsgItem is keyed by the text hash so
 * spoiler state stays scoped to the block.
 */
@Composable
fun RichEntityText(
    text: String,
    entities: List<TextEntityUi>,
    fontSize: TextUnit = 15.sp,
    color: Color = Color.Unspecified,
    linkColor: Color = Color.Unspecified,
    onHashtag: (String) -> Unit = {}
) {
    val carrier = remember(text, entities) {
        MsgItem(
            id = text.hashCode().toLong(),
            outgoing = false,
            dateSeconds = 0,
            kind = com.tuxungram.android.td.MsgKind.TEXT,
            text = text,
            entities = entities,
            senderName = "",
            senderUserId = 0L,
            photoFileId = 0,
            photoPath = null,
            photoWidth = 0,
            photoHeight = 0,
            sendingState = ""
        )
    }
    RichMessageText(
        carrier,
        fontSize = fontSize,
        color = color,
        linkColor = linkColor,
        onHashtag = onHashtag
    )
}

/** One inline custom emoji: the sticker image once its file
 *  is downloaded, the alt emoji character until then (L2 —
 *  "取不到或还没下载完时先显示其 alt emoji 字符占位"). */
@Composable
private fun CustomEmojiInline(
    path: String?,
    alt: String,
    fontSize: TextUnit
) {
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) { decodeCached(path, 128) }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = alt,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Text(alt, fontSize = fontSize)
    }
}

/** Plain-text search highlight (v0.9.31, X3): the
 *  no-entities fast path of RichMessageText still owes the
 *  user visible matches when a search navigation landed on
 *  the message. Same warm background as the entity path. */
private fun highlightedPlain(
    text: String,
    query: String
): AnnotatedString {
    val needle = query.lowercase()
    if (needle.isEmpty()) return AnnotatedString(text)
    val hay = text.lowercase()
    return buildAnnotatedString {
        append(text)
        var from = 0
        while (true) {
            val idx = hay.indexOf(needle, from)
            if (idx < 0) break
            val end = idx + needle.length
            if (end <= text.length) {
                addStyle(
                    SpanStyle(background = Color(0x73FFC400)),
                    idx, end
                )
            }
            from = end
        }
    }
}

/** Open a URL / mailto / tel target in the matching system
 *  app; a missing handler is a no-op, never a crash. */
private fun openExternal(context: android.content.Context, target: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (t: Throwable) {
        // No browser / dialer / mail app: nothing sensible to do.
    }
}
