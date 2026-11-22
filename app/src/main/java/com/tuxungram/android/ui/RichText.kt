package com.tuxungram.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.DeepLink
import com.tuxungram.android.td.MsgItem

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
    onBotCommand: (String) -> Unit = {}
) {
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
    val annotated = buildAnnotatedString {
        append(text)
        for (entity in msg.entities) {
            val start = entity.offset.coerceIn(0, text.length)
            val end = (entity.offset + entity.length)
                .coerceIn(start, text.length)
            if (end <= start) continue
            val rangeText = text.substring(start, end)
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
                // customEmoji: the underlying text is the alt
                // emoji character; L2 replaces it with the
                // sticker image inline. "plain": nothing to do.
                else -> Unit
            }
        }
    }
    Text(annotated, fontSize = fontSize)
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
