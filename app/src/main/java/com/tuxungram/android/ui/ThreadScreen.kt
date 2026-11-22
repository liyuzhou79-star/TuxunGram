package com.tuxungram.android.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.AppLock
import com.tuxungram.android.td.DeepLink
import com.tuxungram.android.td.UiSettings
import com.tuxungram.android.td.MsgItem
import com.tuxungram.android.td.MsgKind
import com.tuxungram.android.td.PollUi
import com.tuxungram.android.td.MessageStore
import com.tuxungram.android.td.ThreadStore
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
    onBotCommand: (String) -> Unit = {},
    // Grouped-run tail (v0.9.18, Y3): the caller marks the
    // last message of a same-sender run; its own-side bottom
    // corner shrinks to the tail radius, like the main chat.
    showTail: Boolean = false
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    val outgoing = msg.outgoing
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
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
                            if (msg.kind == MsgKind.VIDEO &&
                                msg.filePath != null
                            ) {
                                // In-app player (v0.9.1, J3).
                                onOpenVideo(msg)
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
                                msg.kind != MsgKind.GAME
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
                        onLongClick = { menuOpen = true }
                    )
                    .padding(horizontal = 11.dp, vertical = 8.dp)
            ) {
                if (isGroup && !outgoing && msg.senderName.isNotEmpty()) {
                    Text(
                        msg.senderName,
                        color = OfficialTheme.nameColor(
                            msg.senderUserId, OfficialTheme.dark
                        ),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = if (msg.senderUserId != 0L) {
                            Modifier.clickable {
                                DeepLink.pendingProfileUser.value =
                                    msg.senderUserId
                            }
                        } else {
                            Modifier
                        }
                    )
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
                            msg, onBotCommand = onBotCommand,
                            color = if (outgoing) palette.outText else palette.inText,
                            linkColor = if (outgoing) palette.outText else palette.linkIn
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
                            msg, onBotCommand = onBotCommand,
                            color = if (outgoing) palette.outText else palette.inText,
                            linkColor = if (outgoing) palette.outText else palette.linkIn
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
                            msg, onBotCommand = onBotCommand,
                            color = if (outgoing) palette.outText else palette.inText,
                            linkColor = if (outgoing) palette.outText else palette.linkIn
                        )
                        }
                    }
                    MsgKind.DOCUMENT, MsgKind.VIDEO -> {
                        FileBubbleContent(msg)
                        if (msg.text.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            RichMessageText(
                            msg, onBotCommand = onBotCommand,
                            color = if (outgoing) palette.outText else palette.inText,
                            linkColor = if (outgoing) palette.outText else palette.linkIn
                        )
                        }
                    }
                    MsgKind.VOICE -> {
                        VoiceBubbleContent(msg)
                        if (msg.text.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            RichMessageText(
                            msg, onBotCommand = onBotCommand,
                            color = if (outgoing) palette.outText else palette.inText,
                            linkColor = if (outgoing) palette.outText else palette.linkIn
                        )
                        }
                    }
                    MsgKind.LOCATION -> LocationBubbleContent(msg)
                    MsgKind.CONTACT -> ContactBubbleContent(msg)
                    MsgKind.VIDEO_NOTE -> VideoNoteBubbleContent(msg)
                    MsgKind.DICE -> DiceBubbleContent(msg)
                    MsgKind.GAME -> GameBubbleContent(msg)
                    // Checklists render read-only here
                    // (v0.9.24, Q7): toggling lives in the
                    // main chat, whose store owns the open
                    // chat id markChecklistTasksAsDone needs.
                    MsgKind.CHECKLIST -> Text(
                        "[清单] ${msg.checklistTitle}",
                        color = if (outgoing) {
                            palette.outText
                        } else {
                            palette.inText
                        },
                        fontSize = 14.sp
                    )
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
                        "pending" -> Text(
                            "  发送中…",
                            // Footer palette (v0.9.18, Y5).
                            color = if (outgoing) {
                                palette.outTime
                            } else {
                                palette.inTime
                            },
                            fontSize = 11.sp
                        )
                        "failed" -> Text(
                            "  发送失败",
                            color = Color(0xFFC62828),
                            fontSize = 11.sp
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
    // In-app video player target (v0.9.1, J3).
    var videoItem by remember { mutableStateOf<MsgItem?>(null) }

    DisposableEffect(sourceChatId, postId) {
        ThreadStore.open(sourceChatId, postId)
        onDispose { ThreadStore.close() }
    }
    BackHandler { onBack() }
    LaunchedEffect(listState, messages.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastVisible ->
            if (messages.isNotEmpty() && lastVisible >= messages.size - 3) {
                ThreadStore.loadOlder()
            }
        }
    }

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
                onCancel = { pendingPhoto = null }
            )
            return@Column
        }
        val fileToSend = pendingFile
        if (fileToSend != null) {
            AlertDialog(
                onDismissRequest = { pendingFile = null },
                title = { Text("发送文件") },
                text = { Text("要发送「${fileToSend.third}」吗？") },
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
                Text("还没有评论，来发第一条", color = Color.Gray)
            }
        } else {
            // Run tails for the bubbles below (v0.9.18, Y3).
            val tailIds = remember(messages) { tailIdsOf(messages) }
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
                        onBotCommand = { ThreadStore.sendText(it) },
                        showTail = msg.id in tailIds
                    )
                }
            }
        }
        if (ready) {
            val target = replyTarget
            if (target != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5))
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
}
