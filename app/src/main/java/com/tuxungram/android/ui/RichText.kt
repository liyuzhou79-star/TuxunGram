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
 * and cashtags are accent-colored without an action.
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
    // Secondary surfaces (shared-media rows, the photo
    // viewer's caption — regression fix R4, v0.9.6) render the
    // same built text but in their own color and line budget.
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE
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
        Text(msg.text, fontSize = fontSize)
        return
    }
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
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
                                style = SpanStyle(color = linkColor)
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
                                style = SpanStyle(color = linkColor)
                            )
                        ) {
                            openExternal(context, target)
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
                                    style = SpanStyle(color = linkColor)
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
                                    style = SpanStyle(color = linkColor)
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
                                style = SpanStyle(color = linkColor)
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
                                style = SpanStyle(color = linkColor)
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
                                style = SpanStyle(color = linkColor)
                            )
                        ) {
                            openExternal(context, "tel:$rangeText")
                        },
                        start, end
                    )
                }
                "hashtag", "cashtag" -> addStyle(
                    SpanStyle(color = linkColor), start, end
                )
                // customEmoji: rendered as an inline image
                // through appendInlineContent above, no span
                // styling. "plain": nothing to do.
                else -> Unit
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
