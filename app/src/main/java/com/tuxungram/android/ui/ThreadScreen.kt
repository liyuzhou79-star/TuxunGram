package com.tuxungram.android.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.MsgItem
import com.tuxungram.android.td.MsgKind
import com.tuxungram.android.td.ThreadStore

/**
 * A bubble for the thread / forum-topic screens (v0.8.0). The
 * main chat screen's MessageBubble is wired to MessageStore
 * (the one open chat), so these secondary message lists use
 * this simpler bubble: same content renderers, but reply /
 * delete / download / reaction actions go through callbacks
 * into the owning store. Polls render as a read-only summary
 * here (voting stays a main-chat feature for now).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SimpleMessageBubble(
    msg: MsgItem,
    chatId: Long,
    isGroup: Boolean,
    onReply: (MsgItem) -> Unit,
    onDelete: (MsgItem) -> Unit,
    onDownloadFile: (MsgItem) -> Unit,
    onToggleReaction: (MsgItem, String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    val outgoing = msg.outgoing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = if (outgoing) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        Box {
            Column(
                modifier = Modifier
                    .background(
                        if (msg.kind == MsgKind.STICKER) {
                            Color.Transparent
                        } else if (outgoing) {
                            Color(0xFFD8EDCB)
                        } else {
                            Color(0xFFF1F1F1)
                        },
                        RoundedCornerShape(18.dp)
                    )
                    .combinedClickable(
                        onClick = {
                            if (msg.fileId != 0 &&
                                msg.kind != MsgKind.STICKER
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
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                if (isGroup && !outgoing && msg.senderName.isNotEmpty()) {
                    Text(
                        msg.senderName,
                        color = Color(0xFF5B7C99),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (msg.replyToId != 0L) {
                    Row(
                        modifier = Modifier
                            .background(
                                Color(0x14000000), RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "回复：${msg.replySummary.ifEmpty { "原消息" }}",
                            color = Color(0xFF546E7A),
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                when (msg.kind) {
                    MsgKind.TEXT -> {
                        Text(msg.text, fontSize = 15.sp)
                        if (msg.lpTitle.isNotEmpty() ||
                            msg.lpSiteName.isNotEmpty()
                        ) {
                            Spacer(Modifier.height(6.dp))
                            LinkPreviewCard(msg)
                        }
                    }
                    MsgKind.STICKER -> StickerBubbleContent(msg)
                    MsgKind.ANIMATION -> {
                        AnimationBubbleContent(msg)
                        if (msg.text.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(msg.text, fontSize = 15.sp)
                        }
                    }
                    MsgKind.POLL -> {
                        val poll = msg.poll
                        if (poll != null) {
                            Text(
                                poll.question,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            for (opt in poll.options) {
                                Text(
                                    "· ${opt.text}（${opt.percentage}%）",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                            }
                            Text(
                                "投票（只读显示，在群里可投票）",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        } else {
                            Text("[投票]", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                    MsgKind.PHOTO -> {
                        PhotoBubbleContent(msg)
                        if (msg.text.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(msg.text, fontSize = 15.sp)
                        }
                    }
                    MsgKind.DOCUMENT, MsgKind.VIDEO, MsgKind.VOICE -> {
                        FileBubbleContent(msg)
                        if (msg.text.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(msg.text, fontSize = 15.sp)
                        }
                    }
                    MsgKind.OTHER -> Text(
                        "[消息]",
                        color = Color.Gray,
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
                Row {
                    Text(
                        formatMsgTime(msg.dateSeconds),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                    when (msg.sendingState) {
                        "pending" -> Text(
                            "  发送中…",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                        "failed" -> Text(
                            "  发送失败",
                            color = Color(0xFFC62828),
                            fontSize = 11.sp
                        )
                    }
                }
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("回复") },
                    onClick = {
                        menuOpen = false
                        onReply(msg)
                    }
                )
                if (msg.text.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text("复制") },
                        onClick = {
                            menuOpen = false
                            clipboard.setText(AnnotatedString(msg.text))
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("删除", color = Color(0xFFC62828)) },
                    onClick = {
                        menuOpen = false
                        deleteOpen = true
                    }
                )
            }
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

/**
 * Comment thread of one channel post (v0.8.0, G1): the post's
 * discussion-group thread, paged and live via ThreadStore.
 * Text comments, replies, reactions and deletes work; sending
 * photos/files from here is not wired (use the group itself).
 */
@Composable
fun ThreadScreen(
    sourceChatId: Long,
    postId: Long,
    onBack: () -> Unit
) {
    val messages by ThreadStore.messages.collectAsState()
    val error by ThreadStore.error.collectAsState()
    val ready by ThreadStore.ready.collectAsState()
    val replyTarget by ThreadStore.replyTarget.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
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
                        }
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
