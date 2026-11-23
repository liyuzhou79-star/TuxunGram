package com.tuxungram.android.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.AppLock
import com.tuxungram.android.td.DeepLink
import com.tuxungram.android.td.UiSettings
import com.tuxungram.android.td.ChecklistTaskUi
import com.tuxungram.android.td.MsgItem
import com.tuxungram.android.td.MsgKind
import com.tuxungram.android.td.PollUi
import com.tuxungram.android.td.asPaidPhotoItem
import com.tuxungram.android.td.asPaidVideoItem
import com.tuxungram.android.td.MessageStore
import com.tuxungram.android.td.ThreadStore
import com.tuxungram.android.td.summaryText
import java.io.File

/**
 * A bubble for the thread / forum-topic screens (v0.8.0). The
 * main chat screen's MessageBubble is wired to MessageStore
 * (the one open chat), so these secondary message lists use
 * this simpler bubble: same content renderers, but reply /
 * delete / download / reaction actions go through callbacks
 * into the owning store. Polls are votable since v0.9.1 (J2)
 * through [onVotePoll], with the same semantics as the main
 * chat's poll bubble.
 */
/** Same-sender run tails, shared rule (v0.9.18, Y3): the
 *  ids of the messages that END a run — the chronologically
 *  next message changes direction or sender, or opens a new
 *  day. The main chat computes the same set inline (its
 *  day-break rides the separator set); the thread / topic
 *  lists use this helper. Input is ascending by time. */
fun tailIdsOf(messages: List<MsgItem>): Set<Long> {
    val show = HashSet<Long>()
    for (i in messages.indices) {
        val m = messages[i]
        val n = messages.getOrNull(i + 1)
        if (n == null || n.outgoing != m.outgoing ||
            n.senderUserId != m.senderUserId ||
            dayKey(n.dateSeconds) != dayKey(m.dateSeconds)
        ) {
            show.add(m.id)
        }
    }
    return show
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SimpleMessageBubble(
    msg: MsgItem,
    chatId: Long,
    isGroup: Boolean,
    onReply: (MsgItem) -> Unit,
    onDelete: (MsgItem) -> Unit,
    onDownloadFile: (MsgItem) -> Unit,
    onToggleReaction: (MsgItem, String) -> Unit,
    onVotePoll: (MsgItem, List<Int>) -> Unit,
    onOpenVideo: (MsgItem) -> Unit,
    // Cross-message media viewer (v0.9.32, Y6): photos
    // and videos open it, scoped to the hosting thread /
    // topic. The default keeps any other caller on the
    // old behaviour.
    onOpenMedia: (MsgItem) -> Unit = {},
    onBotCommand: (String) -> Unit = {},
    // Hashtag / cashtag tap (v0.9.45, P1): the hosting
    // screen opens its topic-scoped tag search. Until
    // this version the two simple-bubble hosts never
    // passed it, so tags rendered highlighted but inert
    // here (the v0.9.44 N8 downgrade this closes).
    onHashtag: (String) -> Unit = {},
    // Checklist toggling (v0.9.30, W4): the hosting store
    // (thread / forum topic) marks tasks against its own
    // chat id; the default keeps any other caller read-only.
    onToggleChecklistTask: (MsgItem, ChecklistTaskUi) -> Unit =
        { _, _ -> },
    // Failed-send retry (v0.9.31, X5): the hosting store
    // resends against its own chat id. The default keeps any
    // other caller read-only, like the checklist toggle.
    onResend: (MsgItem) -> Unit = {},
    // Grouped-run tail (v0.9.18, Y3): the caller marks the
    // last message of a same-sender run; its own-side bottom
    // corner shrinks to the tail radius, like the main chat.
    showTail: Boolean = false
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }
    // In-bubble text selection (v0.9.47, R1): same division
    // as the main chat — a long press that began on the body
    // text belongs to the text's SelectionContainer; the
    // menu opens for long presses anywhere else.
    var textPressed by remember(msg.id) { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    // Select-text page (v0.9.44, N7).
    var selectTextOpen by remember { mutableStateOf(false) }
    // Per-user reaction list (v0.9.39, F1).
    var addedReactionsOpen by remember { mutableStateOf(false) }
    val outgoing = msg.outgoing
    // Message text size (v0.9.39, F5): same Appearance
    // slider as the main chat, body text and captions
    // of this bubble only.
    val msgTextSize by UiSettings.msgTextSize.collectAsState()
    val bubbleTextSize = msgTextSize.sp
    // Official palette (v0.9.17, X2): same bubble language
    // as the main chat, including the grouped-run tail
    // corner since v0.9.18 (Y3) — the caller computes the
    // runs with tailIdsOf and passes showTail.
    val palette = OfficialTheme.current()
    val bubbleStyle by UiSettings.bubbleStyle.collectAsState()
    val corner = if (bubbleStyle == UiSettings.BUBBLE_CLASSIC) {
        8.dp
    } else {
        18.dp
    }
    val tailCorner = if (bubbleStyle == UiSettings.BUBBLE_CLASSIC) {
        8.dp
    } else {
        5.dp
    }
    val bubbleShape = if (outgoing) {
        RoundedCornerShape(
            topStart = corner, topEnd = corner,
            bottomStart = corner,
            bottomEnd = if (showTail) tailCorner else corner
        )
    } else {
        RoundedCornerShape(
            topStart = corner, topEnd = corner,
            bottomEnd = corner,
            bottomStart = if (showTail) tailCorner else corner
        )
    }
    val threadBubbleBg = when {
        msg.kind == MsgKind.STICKER ||
            msg.kind == MsgKind.ANIMATED_EMOJI -> Modifier
        outgoing -> Modifier.background(
            Brush.verticalGradient(
                listOf(palette.outBubbleGradient, palette.outBubble)
            ),
            bubbleShape
        )
        else -> Modifier.background(palette.inBubble, bubbleShape)
    }
    // Service line (v0.9.37, D4): centered chip, no
    // bubble, no menu — same as the main chat.
    if (msg.kind == MsgKind.SERVICE) {
        ServiceChipRow(msg)
        return
    }
    // Swipe-to-reply (v0.9.43, M2): the main chat has had
    // this since v0.7.0; thread / topic bubbles now share
    // the gesture — drag left past the threshold, a haptic
    // tick fires and the hosting store's reply target is
    // set via onReply, then the row springs back (a snap
    // when the system animator scale is 0, i.e. reduce
    // motion). Same thresholds as the main chat.
    val swipeAnim = remember(msg.id) { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    var swipeFired by remember(msg.id) { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        if (swipeAnim.value < -8f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .size(34.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("↩", fontSize = 17.sp)
            }
        }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .offset { IntOffset(swipeAnim.value.roundToInt(), 0) }
            .pointerInput(msg.id) {
                val maxSwipe = 88.dp.toPx()
                val threshold = 60.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { swipeFired = false },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val next = (swipeAnim.value + dragAmount)
                            .coerceIn(-maxSwipe, 0f)
                        swipeScope.launch { swipeAnim.snapTo(next) }
                        if (!swipeFired && next <= -threshold) {
                            swipeFired = true
                            com.tuxungram.android.td.Haptics.tick()
                            onReply(msg)
                        }
                    },
                    onDragEnd = {
                        swipeScope.launch {
                            if (Motion.durationScale == 0f) {
                                swipeAnim.snapTo(0f)
                            } else {
                                swipeAnim.animateTo(
                                    0f, Motion.tween(180)
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        swipeScope.launch { swipeAnim.snapTo(0f) }
                    }
                )
            },
        horizontalArrangement = if (outgoing) {
            Arrangement.End
        } else {
            Arrangement.Start
        },
        verticalAlignment = Alignment.Bottom
    ) {
        // Sender avatar (v0.9.9, W0): comments are a group-like
        // context — every incoming comment carries its author's
        // photo (or initial disc), tapping it opens the profile.
        if (isGroup && !outgoing && msg.senderUserId != 0L) {
            SenderAvatarBubble(msg = msg) { userId ->
                DeepLink.pendingProfileUser.value = userId
            }
            Spacer(Modifier.width(8.dp))
        }
        Box {
            Column(
                modifier = threadBubbleBg
                    .combinedClickable(
                        onClick = {
                            if (msg.kind == MsgKind.PHOTO ||
                                msg.kind == MsgKind.VIDEO
                            ) {
                                // Cross-message viewer
                                // (v0.9.32, Y6); its own
                                // play button reaches the
                                // in-app player.
                                onOpenMedia(msg)
                            } else if (msg.kind == MsgKind.VIDEO_NOTE) {
                                if (msg.filePath != null) {
                                    onOpenVideo(msg)
                                } else {
                                    onDownloadFile(msg)
                                }
                            } else if (msg.fileId != 0 &&
                                msg.kind != MsgKind.STICKER &&
                                msg.kind != MsgKind.ANIMATED_EMOJI &&
                                msg.kind != MsgKind.VOICE &&
                                msg.kind != MsgKind.DICE &&
                                msg.kind != MsgKind.GAME &&
                                // Same exclusions as the main
                                // chat (v0.9.37, D3/D4): giveaway
                                // stickers / audio files are not
                                // open-with-system targets.
                                msg.kind != MsgKind.GIVEAWAY &&
                                msg.kind != MsgKind.AUDIO
                            ) {
                                val path = msg.filePath
                                if (path != null) {
                                    openFileWithSystem(
                                        context, path, msg.fileMime
                                    )
                                } else {
                                    onDownloadFile(msg)
                                }
                            }
                        },
                        onLongClick = {
                            if (textPressed) {
                                // On the body text: selection
                                // owns this press (v0.9.47, R1).
                                textPressed = false
                            } else {
                                // Touch feedback (v0.9.30, W6).
                                com.tuxungram.android.td.Haptics.tick()
                                menuOpen = true
                            }
                        }
                    )
                    .padding(horizontal = 11.dp, vertical = 8.dp)
            ) {
                // Bubble header row (v0.9.39, F3 + F2):
                // same layout as the main chat — sender
                // name, "via @bot" provenance, and the
                // sender's administrator custom title at
                // the right edge; each part only when its
                // data exists.
                val showSenderName = isGroup && !outgoing &&
                    msg.senderName.isNotEmpty()
                val showVia = msg.viaBotLabel.isNotEmpty() &&
                    msg.viaBotUserId != msg.senderUserId
                if (showSenderName || showVia ||
                    msg.senderTag.isNotEmpty()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showSenderName) {
                            Text(
                                msg.senderName,
                                color = OfficialTheme.nameColor(
                                    msg.senderUserId,
                                    OfficialTheme.dark
                                ),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = (
                                    if (msg.senderUserId != 0L) {
                                        Modifier.clickable {
                                            DeepLink
                                                .pendingProfileUser
                                                .value =
                                                msg.senderUserId
                                        }
                                    } else {
                                        Modifier
                                    }
                                    ).then(
                                        if (msg.senderTag.isNotEmpty()) {
                                            Modifier.weight(
                                                1f, fill = false
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                            )
                        }
                        if (showVia) {
                            if (showSenderName) {
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(
                                "via ${msg.viaBotLabel}",
                                color = if (outgoing) {
                                    palette.outTime
                                } else {
                                    palette.inTime
                                },
                                fontSize = 11.sp,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                modifier = Modifier.clickable {
                                    DeepLink.pendingProfileUser
                                        .value = msg.viaBotUserId
                                }
                            )
                        }
                        if (msg.senderTag.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                msg.senderTag,
                                color = if (outgoing) {
                                    palette.outTime
                                } else {
                                    palette.inTime
                                },
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                if (msg.replyToId != 0L) {
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(
                                    if (outgoing) {
                                        palette.outReplyLine
                                    } else {
                                        palette.inReplyLine
                                    },
                                    RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "回复：${msg.replySummary.ifEmpty { "原消息" }}",
                            color = if (outgoing) {
                                palette.outText.copy(alpha = 0.85f)
                            } else {
                                palette.textSecondary
                            },
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                when (msg.kind) {
                    MsgKind.TEXT -> {
                        RichMessageText(
                            msg, onBotCommand = onBotCommand, onHashtag = onHashtag,
                            color = if (outgoing) palette.outText else palette.inText,
                            linkColor = if (outgoing) palette.outText else palette.linkIn,
                            fontSize = bubbleTextSize,
                            selectable = true,
                            onTextPress = { textPressed = it }
                        )
                        if (msg.lpTitle.isNotEmpty() ||
                            msg.lpSiteName.isNotEmpty()
                        ) {
                            Spacer(Modifier.height(6.dp))
                            LinkPreviewCard(msg)
                        }
                    }
                    MsgKind.STICKER -> StickerBubbleContent(msg)
                    MsgKind.ANIMATED_EMOJI ->
                        // Static rendering only (v0.9.16, W4):
                        // the click sound/effect lives in the
                        // main chat, whose store owns the
                        // clickAnimatedEmojiMessage call.
                        AnimatedEmojiBubbleContent(msg, null)
                    MsgKind.ANIMATION -> {
                        AnimationBubbleContent(msg)
                        if (msg.text.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            RichMessageText(
                            msg, onBotCommand = onBotCommand, onHashtag = onHashtag,
                            color = if (outgoing) palette.outText else palette.inText,
                            linkColor = if (outgoing) palette.outText else palette.linkIn,
                            fontSize = bubbleTextSize,
                            selectable = true,
                            onTextPress = { textPressed = it }
                        )
                        }
                    }
                    MsgKind.POLL -> {
                        val poll = msg.poll
                        if (poll != null) {
                            SimplePollContent(msg, poll, onVotePoll)
                        } else {
                            Text(
                                "[投票]",
                                color = if (outgoing) {
                                    palette.outTime
                                } else {
                                    palette.inTime
                                },
                                fontSize = 14.sp
                            )
                        }
                    }
                    MsgKind.PHOTO -> {
                        PhotoBubbleContent(msg)
                        if (msg.text.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            RichMessageText(
                            msg, onBotCommand = onBotCommand, onHashtag = onHashtag,
                            color = if (outgoing) palette.outText else palette.inText,
                            linkColor = if (outgoing) palette.outText else palette.linkIn,
                            fontSize = bubbleTextSize,
                            selectable = true,
                            onTextPress = { textPressed = it }
                        )
                        }
                    }
                    MsgKind.PAID_MEDIA -> {
                        // Paid media (v0.9.35, B1): purchased
                        // entries open through the host's media
                        // viewer (its item list includes the
                        // re-shaped copies); locked entries get
                        // the shared plain-words dialog.
                        PaidMediaBubbleContent(
                            msg,
                            onOpenPhotoItem = { onOpenMedia(it) },
                            onOpenVideoItem = { onOpenMedia(it) }
                        )
                        if (msg.text.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            RichMessageText(
                            msg, onBotCommand = onBotCommand, onHashtag = onHashtag,
                            color = if (outgoing) palette.outText else palette.inText,
                            linkColor = if (outgoing) palette.outText else palette.linkIn,
                            fontSize = bubbleTextSize,
                            selectable = true,
                            onTextPress = { textPressed = it }
                        )
                        }
                    }
                    MsgKind.DOCUMENT, MsgKind.VIDEO -> {
                        FileBubbleContent(msg)
                        if (msg.text.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            RichMessageText(
                            msg, onBotCommand = onBotCommand, onHashtag = onHashtag,
                            color = if (outgoing) palette.outText else palette.inText,
                            linkColor = if (outgoing) palette.outText else palette.linkIn,
                            fontSize = bubbleTextSize,
                            selectable = true,
                            onTextPress = { textPressed = it }
                        )
                        }
                    }
                    MsgKind.VOICE -> {
                        VoiceBubbleContent(msg)
                        if (msg.text.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            RichMessageText(
                            msg, onBotCommand = onBotCommand, onHashtag = onHashtag,
                            color = if (outgoing) palette.outText else palette.inText,
                            linkColor = if (outgoing) palette.outText else palette.linkIn,
                            fontSize = bubbleTextSize,
                            selectable = true,
                            onTextPress = { textPressed = it }
                        )
                        }
                    }
                    MsgKind.LOCATION -> LocationBubbleContent(msg)
                    MsgKind.CONTACT -> ContactBubbleContent(msg)
                    MsgKind.VIDEO_NOTE -> VideoNoteBubbleContent(msg)
                    MsgKind.DICE -> DiceBubbleContent(msg)
                    MsgKind.GAME -> GameBubbleContent(msg)
                    // Checklists are interactive here too
                    // (v0.9.30, W4; was a read-only summary
                    // line): title, done/total line and one
                    // tappable row per task, same rendering
                    // rules as the main chat's checklist.
                    MsgKind.CHECKLIST -> SimpleChecklistContent(
                        msg = msg,
                        onToggleTask = onToggleChecklistTask
                    )
                    // Giveaway / audio / invoice / shared
                    // cards (v0.9.37, D3/D4) render with the
                    // main chat's composables; the giveaway
                    // status action stays off here — the
                    // message lives in its own channel, not
                    // in this hosting chat.
                    MsgKind.GIVEAWAY -> GiveawayBubbleContent(
                        msg, showStatusAction = false
                    )
                    // Rich messages (v0.9.40, G4) render
                    // with the main chat's block renderer.
                    MsgKind.RICH_MESSAGE -> RichBlocksBubbleContent(
                        msg, bubbleTextSize
                    )
                    MsgKind.AUDIO -> AudioBubbleContent(msg)
                    MsgKind.INVOICE -> InvoiceBubbleContent(msg)
                    MsgKind.SHARED -> SharedBubbleContent(msg)
                    MsgKind.SERVICE -> ServiceChipRow(msg)
                    MsgKind.OTHER -> Text(
                        "[消息]",
                        color = if (outgoing) {
                            palette.outTime
                        } else {
                            palette.inTime
                        },
                        fontSize = 14.sp
                    )
                }
                if (msg.reactions.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    Row {
                        for (chip in msg.reactions) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (chip.chosen) {
                                            MaterialTheme.colorScheme
                                                .primaryContainer
                                        } else {
                                            Color(0x14000000)
                                        },
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        onToggleReaction(msg, chip.emoji)
                                    }
                                    .padding(
                                        horizontal = 7.dp, vertical = 2.dp
                                    )
                            ) {
                                Text(
                                    "${chip.emoji} ${chip.count}",
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(Modifier.width(5.dp))
                        }
                    }
                }
                if (msg.buttons.isNotEmpty()) {
                    InlineButtonGrid(msg, chatId)
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (msg.edited) {
                        Text(
                            "已编辑 ",
                            color = if (outgoing) {
                                palette.outTime
                            } else {
                                palette.inTime
                            },
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        formatMsgTime(msg.dateSeconds),
                        color = if (outgoing) {
                            palette.outTime
                        } else {
                            palette.inTime
                        },
                        fontSize = 11.sp
                    )
                    when (msg.sendingState) {
                        // Sending state (v0.9.31, X5): the
                        // pending state gets the clock glyph
                        // the official client uses, and a
                        // failed send is tappable to resend —
                        // before this version the red flag here
                        // was display-only.
                        "pending" -> {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                ClockIcon, contentDescription = null,
                                tint = if (outgoing) {
                                    palette.outTime
                                } else {
                                    palette.inTime
                                },
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                " 发送中…",
                                color = if (outgoing) {
                                    palette.outTime
                                } else {
                                    palette.inTime
                                },
                                fontSize = 11.sp
                            )
                        }
                        "failed" -> FailedSendNote(
                            msg = msg,
                            onResend = onResend,
                            onDelete = { deleteOpen = true }
                        )
                    }
                }
                TranslationArea(chatId, msg)
            }
            // Official floating menu (v0.9.20, AA4): same
            // shared component as the main chat, this
            // bubble's own entry set, no reaction strip.
            OfficialMessageMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                anchorOnRight = outgoing,
                entries = buildList {
                    add(MessageMenuEntry(ReplyIcon, "回复") {
                        menuOpen = false
                        onReply(msg)
                    })
                    if (msg.text.isNotEmpty()) {
                        add(MessageMenuEntry(CopyIcon, "复制") {
                            menuOpen = false
                            clipboard.setText(
                                AnnotatedString(msg.text)
                            )
                        })
                        // Select part of the text (v0.9.44,
                        // N7): same full-text selection page
                        // as the main chat.
                        add(MessageMenuEntry(TextSizeIcon, "选择文字") {
                            menuOpen = false
                            selectTextOpen = true
                        })
                    }
                    if (msg.sendingState == "failed") {
                        // Resend from the menu (v0.9.44, N9):
                        // same retryable gate as the footer.
                        val failKind = com.tuxungram.android.td
                            .SendFailure.classify(
                                msg.failCode, msg.failId,
                                msg.paidStarsRequired
                            )
                        if (com.tuxungram.android.td.SendFailure
                                .retryable(failKind, msg.canRetry)
                        ) {
                            add(MessageMenuEntry(
                                Icons.Filled.Refresh, "重发"
                            ) {
                                menuOpen = false
                                onResend(msg)
                            })
                        }
                    }
                    if (msg.text.isNotEmpty() &&
                        msg.sendingState.isEmpty()
                    ) {
                        // Translate (v0.9.5, O4): same shared
                        // MessageStore path as the main chat.
                        add(MessageMenuEntry(GlobeIcon, "翻译") {
                            menuOpen = false
                            MessageStore.clearTranslateError()
                            MessageStore.translateMessage(chatId, msg)
                        })
                    }
                    if (msg.kind == MsgKind.VIDEO &&
                        msg.filePath != null
                    ) {
                        add(MessageMenuEntry(
                            Icons.Filled.PlayArrow, "用其他播放器打开"
                        ) {
                            menuOpen = false
                            openFileWithSystem(
                                context, msg.filePath!!, msg.fileMime
                            )
                        })
                    }
                    if (msg.reactions.isNotEmpty() &&
                        msg.canGetAddedReactions
                    ) {
                        // Per-user reaction list (v0.9.39,
                        // F1): same shared dialog as the
                        // main chat.
                        add(MessageMenuEntry(FaceIcon, "回应名单") {
                            menuOpen = false
                            addedReactionsOpen = true
                            com.tuxungram.android.td.ViewersStore
                                .loadAddedReactions(chatId, msg.id)
                        })
                    }
                    add(MessageMenuEntry(
                        Icons.Filled.Delete, "删除", danger = true
                    ) {
                        menuOpen = false
                        deleteOpen = true
                    })
                }
            )
        }
    }
    } // swipe-to-reply wrapper Box (v0.9.43, M2)
    if (selectTextOpen) {
        SelectableTextDialog(
            text = msg.text,
            onDismiss = { selectTextOpen = false }
        )
    }
    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text("删除这条消息？") },
            text = {
                Text(
                    if (msg.outgoing) {
                        "会同时为所有人删除。"
                    } else {
                        "只在你这边删除。"
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    onDelete(msg)
                    deleteOpen = false
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) {
                    Text("取消")
                }
            }
        )
    }
    if (addedReactionsOpen) {
        AddedReactionsDialog {
            addedReactionsOpen = false
        }
    }
}

/** Votable poll for the thread / forum bubbles (v0.9.1, J2):
 *  same semantics as the main chat's PollBubbleContent —
 *  single-answer polls vote on tap (tap again to retract),
 *  multiple-answer polls collect a local selection and submit
 *  together — but the vote goes through the owning store's
 *  callback instead of MessageStore. Results re-render from
 *  TDLib's poll object (updatePoll in the owning store). */
@Composable
fun SimplePollContent(
    msg: MsgItem,
    poll: PollUi,
    onVotePoll: (MsgItem, List<Int>) -> Unit
) {
    var selected by remember(msg.id) {
        mutableStateOf(
            poll.options.indices.filter { poll.options[it].chosen }.toSet()
        )
    }
    LaunchedEffect(poll) {
        selected = poll.options.indices.filter { poll.options[it].chosen }.toSet()
    }
    val voted = poll.options.any { it.chosen }
    val showResults = voted || poll.isClosed
    // Sub-bubble palette (v0.9.18, Y5), outgoing-aware —
    // mirrors the main chat's poll bubble.
    val palette = OfficialTheme.current()
    val outgoing = msg.outgoing
    val mainText = if (outgoing) palette.outText else palette.inText
    val subText = if (outgoing) palette.outTime else palette.inTime
    Column(modifier = Modifier.width(240.dp)) {
        Text(
            poll.question,
            color = mainText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            when {
                poll.isClosed -> "投票已结束"
                poll.isQuiz -> "测验"
                poll.isAnonymous -> "匿名投票"
                else -> "公开投票"
            } + if (poll.allowsMultiple) " · 可多选" else "",
            color = subText, fontSize = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        poll.options.forEachIndexed { index, option ->
            val pct = option.percentage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .background(
                        if (outgoing) {
                            Color.White.copy(alpha = 0.14f)
                        } else {
                            Color.Black.copy(alpha = 0.05f)
                        },
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(enabled = !poll.isClosed) {
                        if (poll.allowsMultiple) {
                            selected = if (index in selected) {
                                selected - index
                            } else {
                                selected + index
                            }
                        } else {
                            onVotePoll(
                                msg,
                                if (option.chosen) emptyList()
                                else listOf(index)
                            )
                        }
                    }
                    .padding(horizontal = 9.dp, vertical = 7.dp)
            ) {
                if (showResults) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(pct / 100f)
                            .height(30.dp)
                            .background(
                                if (option.chosen ||
                                    (poll.isQuiz && option.isCorrect)
                                ) {
                                    if (outgoing) {
                                        Color.White.copy(alpha = 0.32f)
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    }
                                } else if (outgoing) {
                                    Color.White.copy(alpha = 0.16f)
                                } else {
                                    Color.Black.copy(alpha = 0.08f)
                                },
                                RoundedCornerShape(8.dp)
                            )
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        option.text,
                        color = mainText,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (showResults) {
                        Text(
                            "$pct%",
                            color = subText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (index in selected && poll.allowsMultiple) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "已选",
                            tint = if (outgoing) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
        if (poll.allowsMultiple && !poll.isClosed) {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { onVotePoll(msg, selected.sorted()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (voted) "更新投票" else "投票")
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "共 ${poll.totalVoters} 人参与",
            color = subText, fontSize = 12.sp
        )
        if (poll.isQuiz && voted && poll.explanation.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(poll.explanation, color = subText, fontSize = 12.sp)
        }
    }
}

/**
 * Comment thread of one channel post (v0.8.0, G1): the post's
 * discussion-group thread, paged and live via ThreadStore.
 * Text comments, replies, reactions and deletes work; photos
 * and files send from here too since v0.9.1 (J2), through the
 * main chat's attachment pipeline.
 */
@Composable
fun ThreadScreen(
    sourceChatId: Long,
    postId: Long,
    onBack: () -> Unit
) {
    val messages by ThreadStore.messages.collectAsState()
    TranslateErrorDialog()
    val error by ThreadStore.error.collectAsState()
    val ready by ThreadStore.ready.collectAsState()
    val replyTarget by ThreadStore.replyTarget.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Attachment pipeline (v0.9.1, J2): the same pickers and
    // copy-to-cache flow as the main chat screen; the picked
    // photo goes through the shared PhotoPreview, files through
    // a confirm dialog, and both send via ThreadStore.
    var pendingPhoto by remember { mutableStateOf<String?>(null) }
    var photoCaption by remember { mutableStateOf("") }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val file = File(
                context.cacheDir, "pick_${System.currentTimeMillis()}.jpg"
            )
            try {
                context.contentResolver.openInputStream(uri)
                    ?.use { inputStream ->
                        file.outputStream().use { output ->
                            inputStream.copyTo(output)
                        }
                    }
                pendingPhoto = file.absolutePath
                photoCaption = ""
            } catch (t: Throwable) {
                // Picker copy failed: stay on the thread silently.
            }
        }
    }
    var pendingFile by remember {
        mutableStateOf<Triple<String, String, String>?>(null)
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                var displayName = "file"
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        if (!name.isNullOrBlank()) displayName = name
                    }
                }
                val mime = context.contentResolver.getType(uri) ?: ""
                val safeName = displayName.replace(
                    Regex("[^A-Za-z0-9._-]"), "_"
                )
                val file = File(
                    context.cacheDir,
                    "pick_${System.currentTimeMillis()}_$safeName"
                )
                context.contentResolver.openInputStream(uri)
                    ?.use { inputStream ->
                        file.outputStream().use { output ->
                            inputStream.copyTo(output)
                        }
                    }
                pendingFile = Triple(file.absolutePath, mime, displayName)
            } catch (t: Throwable) {
                // Picker copy failed: stay on the thread silently.
            }
        }
    }
    var attachMenuOpen by remember { mutableStateOf(false) }
    // Photo editor / video trim overlays (v0.9.35, B4):
    // the same pre-send editing the main chat has had
    // since v0.9.27/v0.9.33, wired into this page's own
    // pending-photo / pending-file chain.
    var editingPhotoPath by remember {
        mutableStateOf<String?>(null)
    }
    var trimmingVideo by remember {
        mutableStateOf<Triple<String, String, String>?>(null)
    }
    // In-app video player target (v0.9.1, J3).
    var videoItem by remember { mutableStateOf<MsgItem?>(null) }
    // Cross-message media viewer anchor (v0.9.32, Y6).
    var mediaViewer by remember { mutableStateOf<MsgItem?>(null) }
    val threadAllLoaded by ThreadStore.allLoadedFlow.collectAsState()

    DisposableEffect(sourceChatId, postId) {
        ThreadStore.open(sourceChatId, postId)
        onDispose { ThreadStore.close() }
    }
    BackHandler { onBack() }
    LaunchedEffect(listState, messages.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastVisible ->
            // One-page-ahead prefetch (v0.9.28, U2), same
            // rule as the chat list.
            if (messages.isNotEmpty() &&
                messages.size - 1 - lastVisible <= 50
            ) {
                ThreadStore.loadOlder()
            }
        }
    }

    // Back-to-latest (v0.9.28, U1): a thread's loaded window
    // always reaches its newest comment, so a tap only needs
    // the animated scroll. No unread badge — TDLib keeps no
    // per-thread unread counter.
    val threadHistoryLoading by ThreadStore.historyLoading.collectAsState()
    val threadScope = rememberCoroutineScope()
    // At-bottom behaviour (v0.9.44, N5): same rule as the
    // main chat — the button appears as soon as the newest
    // comment leaves the screen (not after a full screen of
    // scrolling), and a new comment auto-scrolls only when
    // the user was already at the bottom (own sends always).
    var threadAtBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex <= 2 }
            .collect { threadAtBottom = it }
    }
    val threadNewest = messages.lastOrNull()
    LaunchedEffect(threadNewest?.id) {
        val newest = threadNewest ?: return@LaunchedEffect
        if (threadAtBottom || newest.outgoing) {
            listState.animateScrollToItem(0)
        }
    }
    val threadFarFromLatest by remember {
        derivedStateOf { !threadAtBottom }
    }
    // Tag search (v0.9.45, P1): results for the tag the
    // user tapped in a bubble; the panel overlays the
    // thread while the query is non-empty.
    val tagQuery by ThreadStore.searchQuery.collectAsState()
    val tagResults by ThreadStore.searchResults.collectAsState()
    val tagTotal by ThreadStore.searchTotal.collectAsState()
    val tagExhausted by ThreadStore.searchExhausted.collectAsState()
    val threadDetached by ThreadStore.windowDetached.collectAsState()
    // Scroll the tag-search jump target into view when
    // its window lands (v0.9.45, P1): same locate
    // mechanism as the main chat, plus clearing the flag
    // so a second jump to the same message scrolls again.
    val threadLocateId by ThreadStore.locateId.collectAsState()
    LaunchedEffect(threadLocateId, messages.size) {
        if (threadLocateId != 0L && messages.isNotEmpty()) {
            val ascIndex = messages.indexOfFirst {
                it.id == threadLocateId
            }
            if (ascIndex >= 0) {
                listState.scrollToItem(messages.size - 1 - ascIndex)
                ThreadStore.clearLocate()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        val photoPath = pendingPhoto
        if (photoPath != null) {
            PhotoPreview(
                path = photoPath,
                caption = photoCaption,
                onCaptionChange = { photoCaption = it },
                onSend = {
                    ThreadStore.clearError()
                    ThreadStore.sendPhoto(photoPath, photoCaption.trim())
                    pendingPhoto = null
                },
                onCancel = { pendingPhoto = null },
                // Photo editor (v0.9.35, B4): the edited
                // file replaces the pending photo and the
                // preview reopens on it.
                onEdit = { editingPhotoPath = photoPath }
            )
            return@Column
        }
        // The photo editor is an overlay at the root Box
        // level since v0.9.45 (P2) — see the end of this
        // composable. It used to sit here, behind the
        // preview's early return above, which made it
        // unreachable: the preview returns whenever a
        // pending photo exists, and editing starts from
        // exactly that state.
        val fileToSend = pendingFile
        if (fileToSend != null) {
            AlertDialog(
                onDismissRequest = { pendingFile = null },
                title = { Text("发送文件") },
                text = {
                    Column {
                        Text("要发送「${fileToSend.third}」吗？")
                        // Video trim (v0.9.35, B4): same
                        // entry and limits as the main chat —
                        // MP4 only, cut without re-encoding.
                        if (fileToSend.second == "video/mp4") {
                            TextButton(onClick = {
                                trimmingVideo = fileToSend
                                pendingFile = null
                            }) { Text("剪辑视频（选一段发）") }
                        } else if (
                            fileToSend.second.startsWith("video/")
                        ) {
                            Text(
                                "这个格式暂时不能剪辑（剪辑只支持 MP4），" +
                                    "可以照原样发送。",
                                fontSize = 12.5.sp,
                                color = Color.Gray
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        ThreadStore.clearError()
                        ThreadStore.sendFile(
                            fileToSend.first, fileToSend.second,
                            fileToSend.third
                        )
                        pendingFile = null
                    }) { Text("发送") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingFile = null }) {
                        Text("取消")
                    }
                }
            )
        }
        val trimTarget = trimmingVideo
        if (trimTarget != null) {
            VideoTrimScreen(
                path = trimTarget.first,
                fileName = trimTarget.third,
                onDone = { trimmedPath ->
                    trimmingVideo = null
                    val base = trimTarget.third
                        .substringBeforeLast('.')
                    pendingFile = Triple(
                        trimmedPath, "video/mp4", base + "_trim.mp4"
                    )
                },
                onCancel = {
                    trimmingVideo = null
                    pendingFile = trimTarget
                }
            )
            return@Column
        }
        // The media viewer is an overlay at the root Box
        // level since v0.9.45 (P2) — see the end of this
        // composable (it was a full-screen replacement
        // here until then).
        val vItem = videoItem
        if (vItem?.filePath != null) {
            VideoPlayerScreen(
                path = vItem.filePath!!,
                title = vItem.fileName,
                onClose = { videoItem = null }
            )
            return@Column
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                "评论",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        val err = error
        if (err != null) {
            Text(
                err,
                color = Color(0xFFC62828),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        if (!ready && err == null) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (ready && messages.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateMini(
                    icon = ChatBubbleIcon,
                    title = "还没有评论",
                    subtitle = "来发第一条评论吧。"
                )
            }
        } else {
            // Run tails for the bubbles below (v0.9.18, Y3).
            val tailIds = remember(messages) { tailIdsOf(messages) }
            // Box wrapper so the back-to-latest button can
            // float over the list (v0.9.28, U1).
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .lazyListScrollbar(listState, reverse = true)
            ) {
                items(items = messages.asReversed(), key = { it.id }) { msg ->
                    SimpleMessageBubble(
                        msg = msg,
                        chatId = ThreadStore.threadChatId,
                        isGroup = true,
                        onReply = { ThreadStore.setReplyTarget(it) },
                        onDelete = { ThreadStore.deleteMessage(it) },
                        onDownloadFile = { ThreadStore.downloadFileFor(it) },
                        onToggleReaction = { m, emoji ->
                            ThreadStore.toggleReaction(m, emoji)
                        },
                        onVotePoll = { m, ids ->
                            ThreadStore.setPollAnswer(m, ids)
                        },
                        onOpenVideo = { m -> videoItem = m },
                        onOpenMedia = { m -> mediaViewer = m },
                        onBotCommand = { ThreadStore.sendText(it) },
                        onHashtag = { tag ->
                            ThreadStore.searchByTag(tag)
                        },
                        onToggleChecklistTask = { m, task ->
                            ThreadStore.markChecklistTask(m, task)
                        },
                        onResend = { ThreadStore.resend(it) },
                        showTail = msg.id in tailIds
                    )
                }
                if (threadHistoryLoading && messages.isNotEmpty()) {
                    item(key = "paging-spinner") { PagingSpinnerRow() }
                }
            }
            ScrollToLatestButton(
                // After a tag-search jump the window no
                // longer reaches the newest comment
                // (v0.9.45, P1): stay visible while detached
                // even at the jump window's own top, and
                // reload the window instead of scrolling.
                visible = threadFarFromLatest || threadDetached,
                unreadCount = 0,
                onClick = {
                    if (threadDetached) {
                        ThreadStore.backToLatest()
                    } else {
                        threadScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 12.dp)
            )
            }
        }
        if (ready) {
            val target = replyTarget
            if (target != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OfficialTheme.current().panelBg)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "回复 ${target.senderName.ifEmpty { if (target.outgoing) "我" else "" }}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            target.text.ifEmpty { "原消息" },
                            color = Color.Gray,
                            fontSize = 13.sp,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = { ThreadStore.setReplyTarget(null) }) {
                        Icon(Icons.Filled.Close, contentDescription = "取消回复")
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(onClick = { attachMenuOpen = true }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "发送附件",
                            tint = Color.Gray
                        )
                    }
                    DropdownMenu(
                        expanded = attachMenuOpen,
                        onDismissRequest = { attachMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("发图片") },
                            onClick = {
                                attachMenuOpen = false
                                AppLock.noteExternalLaunch()
                                photoPicker.launch("image/*")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("发文件 / 视频 / 语音") },
                            onClick = {
                                attachMenuOpen = false
                                AppLock.noteExternalLaunch()
                                filePicker.launch("*/*")
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("写评论…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (input.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color(0xFFBDBDBD)
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            val text = input.trim()
                            if (text.isNotEmpty()) {
                                ThreadStore.clearError()
                                ThreadStore.sendText(text)
                                input = ""
                            }
                        },
                        enabled = input.isNotBlank()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
    // Photo editor overlay (animated v0.9.45, P2): the
    // Motion crossfade pair with the 0.97 scale, exactly
    // the main chat media viewer's spec — the photo
    // preview stays composed underneath and the editor
    // fades back out onto it, instead of the old
    // full-screen replacement hard cut. Moving it here
    // also fixes its reachability (see the note where
    // the block used to sit).
    val editPath = editingPhotoPath
    AnimatedVisibility(
        visible = editPath != null,
        enter = fadeIn(Motion.tween(Motion.CROSSFADE_IN)) +
            scaleIn(
                initialScale = 0.97f,
                animationSpec = Motion.tween(Motion.CROSSFADE_IN)
            ),
        exit = fadeOut(Motion.tween(Motion.CROSSFADE_OUT)) +
            scaleOut(
                targetScale = 0.97f,
                animationSpec = Motion.tween(Motion.CROSSFADE_OUT)
            )
    ) {
        editPath?.let { path ->
            PhotoEditorScreen(
                path = path,
                onDone = { edited ->
                    editingPhotoPath = null
                    pendingPhoto = edited
                },
                onCancel = { editingPhotoPath = null }
            )
        }
    }
    // Media viewer overlay (animated v0.9.45, P2): same
    // spec as the main chat's viewer (v0.9.44, N6), the
    // thread left composed underneath. The context is
    // this thread's loaded window only; paging to the
    // oldest end pulls the thread's own older pages.
    val viewerAnchor = mediaViewer
    AnimatedVisibility(
        visible = viewerAnchor != null,
        enter = fadeIn(Motion.tween(Motion.CROSSFADE_IN)) +
            scaleIn(
                initialScale = 0.97f,
                animationSpec = Motion.tween(Motion.CROSSFADE_IN)
            ),
        exit = fadeOut(Motion.tween(Motion.CROSSFADE_OUT)) +
            scaleOut(
                targetScale = 0.97f,
                animationSpec = Motion.tween(Motion.CROSSFADE_OUT)
            )
    ) {
        viewerAnchor?.let { anchor ->
            // Purchased paid-media entries join the viewer
            // as re-shaped copies (v0.9.35, B1); locked
            // previews never enter it.
            val mediaItems = messages.flatMap { m ->
                when (m.kind) {
                    MsgKind.PHOTO, MsgKind.VIDEO -> listOf(m)
                    MsgKind.PAID_MEDIA -> m.paidMedia.mapNotNull { e ->
                        when (e.kind) {
                            "photo" -> m.asPaidPhotoItem(e)
                            "video" -> m.asPaidVideoItem(e)
                            else -> null
                        }
                    }
                    else -> emptyList()
                }
            }
            val album = if (anchor.mediaAlbumId != 0L) {
                messages.filter {
                    it.mediaAlbumId == anchor.mediaAlbumId
                }
            } else {
                emptyList()
            }
            MediaViewerListScreen(
                items = mediaItems,
                anchor = anchor,
                albumGroup = album,
                exhausted = threadAllLoaded,
                onLoadMore = {
                    if (!threadAllLoaded) ThreadStore.loadOlder()
                },
                onClose = { mediaViewer = null },
                onPlayVideo = { item ->
                    mediaViewer = null
                    videoItem = item
                }
            )
        }
    }
    // Tag search panel (v0.9.45, P1): overlays the thread
    // while a tag query is active; picking a result jumps
    // the window to that comment.
    if (tagQuery.isNotEmpty()) {
        TagSearchPanel(
            query = tagQuery,
            results = tagResults,
            total = tagTotal,
            exhausted = tagExhausted,
            onLoadMore = {
                ThreadStore.searchByTag(tagQuery, append = true)
            },
            onPick = { item ->
                ThreadStore.exitSearch()
                ThreadStore.jumpTo(item.id)
            },
            onClose = { ThreadStore.exitSearch() }
        )
    }
    }
}

/** Interactive checklist for the simple bubble hosts
 *  (v0.9.30, W4): the same rendering as the main chat's
 *  checklist — title, a live done/total line and one
 *  tappable row per task — with toggling delegated to the
 *  hosting store, which owns the chat id
 *  markChecklistTasksAsDone needs. Rows only respond when
 *  the server's can_mark flag allows this account; the
 *  fresh state arrives with updateMessageContent and
 *  re-renders title and count with it. */
@Composable
fun SimpleChecklistContent(
    msg: MsgItem,
    onToggleTask: (MsgItem, ChecklistTaskUi) -> Unit
) {
    val palette = OfficialTheme.current()
    val outgoing = msg.outgoing
    val mainText = if (outgoing) palette.outText else palette.inText
    val subText = if (outgoing) palette.outTime else palette.inTime
    val doneCount = msg.checklistTasks.count { it.completed }
    Column(modifier = Modifier.width(240.dp)) {
        if (msg.checklistTitle.isNotEmpty()) {
            Text(
                msg.checklistTitle,
                color = mainText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
        }
        Text(
            "清单 · $doneCount/${msg.checklistTasks.size} 已完成",
            color = subText, fontSize = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        for (task in msg.checklistTasks) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = msg.checklistCanMark) {
                        onToggleTask(msg, task)
                    }
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            if (task.completed) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            },
                            RoundedCornerShape(5.dp)
                        )
                        .border(
                            1.5.dp,
                            if (task.completed) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                subText
                            },
                            RoundedCornerShape(5.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.completed) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    task.text,
                    color = if (task.completed) subText else mainText,
                    fontSize = 14.sp,
                    style = TextStyle(
                        textDecoration = if (task.completed) {
                            TextDecoration.LineThrough
                        } else {
                            null
                        }
                    )
                )
            }
        }
        if (!msg.checklistCanMark && msg.checklistTasks.isNotEmpty()) {
            Text(
                "只有清单作者能勾选",
                color = subText, fontSize = 11.sp
            )
        }
    }
}

/** Topic-scoped tag search panel (v0.9.45, P1), shared
 *  by the thread screen and the forum-topic screen: a
 *  full-screen overlay listing the hosting store's
 *  searchChatMessages results for one tag, newest first.
 *  Tapping a row asks the host to jump its window to that
 *  message (the host wires onPick to its store's jumpTo)
 *  and closes the panel. Rows stay plain — sender, day,
 *  one summary line — the same information the main
 *  chat's search rows carry, minus navigation chrome
 *  (a topic window has no search cursor to step). */
@Composable
fun TagSearchPanel(
    query: String,
    results: List<MsgItem>,
    total: Int,
    exhausted: Boolean,
    onLoadMore: () -> Unit,
    onPick: (MsgItem) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    query,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when {
                        total > 0 -> "共 $total 条"
                        total == 0 -> "没有结果"
                        else -> "正在搜索…"
                    },
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (exhausted) {
                    EmptyState(
                        icon = SearchIcon,
                        title = "这里还没有带这个标签的消息",
                        subtitle = "等这个范围里有人用上这个标签，" +
                            "消息就会出现在这里。"
                    )
                } else {
                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "正在搜索…",
                            color = Color.Gray, fontSize = 14.sp
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(items = results, key = { it.id }) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(item) }
                            .padding(
                                horizontal = 16.dp, vertical = 10.dp
                            )
                    ) {
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Text(
                                item.senderName.ifEmpty {
                                    if (item.outgoing) "我" else ""
                                },
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                formatDayLabel(item.dateSeconds),
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            item.summaryText(),
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(
                                MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
                if (!exhausted) {
                    item(key = "tag_search_more") {
                        TextButton(
                            onClick = onLoadMore,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("加载更多") }
                    }
                }
            }
        }
    }
}
