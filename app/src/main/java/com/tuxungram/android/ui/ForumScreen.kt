package com.tuxungram.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.ForumStore
import com.tuxungram.android.td.ForumTopicUi
import com.tuxungram.android.td.MsgItem
import java.io.File

/**
 * Forum screens (v0.8.0, G2): the topic list shown when a
 * forum supergroup is opened, and one topic's message stream.
 * Both live inside the chat screen's scaffold (top bar stays
 * the chat's), mirroring how mature clients treat topics as
 * sub-chats of the group.
 */

@Composable
fun ForumTopicsList(
    chatId: Long,
    onOpenTopic: (ForumTopicUi) -> Unit
) {
    val topics by ForumStore.topics.collectAsState()
    val loading by ForumStore.topicsLoading.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(chatId) {
        ForumStore.loadTopics(chatId)
    }
    LaunchedEffect(listState, topics.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastVisible ->
            if (topics.isNotEmpty() && lastVisible >= topics.size - 5) {
                ForumStore.loadMoreTopics()
            }
        }
    }
    if (topics.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator()
            } else {
                Text("这个群组还没有话题", color = Color.Gray)
            }
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .lazyListScrollbar(listState)
    ) {
        items(items = topics, key = { it.topicId }) { topic ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenTopic(topic) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            if (topic.iconColor != 0) {
                                Color(topic.iconColor)
                            } else {
                                Color(0xFF5B7C99)
                            },
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        topic.name.trim().firstOrNull()?.toString() ?: "#",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            topic.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        if (topic.isPinned) {
                            Text(
                                "  📌",
                                fontSize = 12.sp
                            )
                        }
                        if (topic.isClosed) {
                            Text(
                                "  已关闭",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (topic.lastText.isNotEmpty()) {
                        Text(
                            topic.lastText,
                            color = Color.Gray,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                }
                if (topic.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary,
                                CircleShape
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            topic.unreadCount.toString(),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color(0x14000000))
            )
        }
        if (loading) {
            item(key = "topics_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ForumTopicView(
    chatId: Long,
    topic: ForumTopicUi
) {
    val messages by ForumStore.messages.collectAsState()
    TranslateErrorDialog()
    val error by ForumStore.error.collectAsState()
    val replyTarget by ForumStore.replyTarget.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Attachment pipeline (v0.9.1, J2): the same pickers and
    // copy-to-cache flow as the main chat screen; the picked
    // photo goes through the shared PhotoPreview, files through
    // a confirm dialog, and both send via ForumStore.
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
                // Picker copy failed: stay in the topic silently.
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
                // Picker copy failed: stay in the topic silently.
            }
        }
    }
    var attachMenuOpen by remember { mutableStateOf(false) }
    // In-app video player target (v0.9.1, J3).
    var videoItem by remember { mutableStateOf<MsgItem?>(null) }

    DisposableEffect(chatId, topic.topicId) {
        ForumStore.openTopic(chatId, topic.topicId)
        onDispose { ForumStore.closeTopic() }
    }
    LaunchedEffect(listState, messages.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastVisible ->
            if (messages.isNotEmpty() && lastVisible >= messages.size - 3) {
                ForumStore.loadOlder()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val photoPath = pendingPhoto
        if (photoPath != null) {
            PhotoPreview(
                path = photoPath,
                caption = photoCaption,
                onCaptionChange = { photoCaption = it },
                onSend = {
                    ForumStore.clearError()
                    ForumStore.sendPhoto(photoPath, photoCaption.trim())
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
                        ForumStore.clearError()
                        ForumStore.sendFile(
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "# ${topic.name}",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (topic.isClosed) {
                Text("已关闭", color = Color.Gray, fontSize = 12.sp)
            }
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
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("这个话题还没有消息", color = Color.Gray)
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
                        chatId = chatId,
                        isGroup = true,
                        onReply = { ForumStore.setReplyTarget(it) },
                        onDelete = { ForumStore.deleteMessage(it) },
                        onDownloadFile = { ForumStore.downloadFileFor(it) },
                        onToggleReaction = { m, emoji ->
                            ForumStore.toggleReaction(m, emoji)
                        },
                        onVotePoll = { m, ids ->
                            ForumStore.setPollAnswer(m, ids)
                        },
                        onOpenVideo = { m -> videoItem = m },
                        onBotCommand = { ForumStore.sendText(it) }
                    )
                }
            }
        }
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
                IconButton(onClick = { ForumStore.setReplyTarget(null) }) {
                    Icon(Icons.Filled.Close, contentDescription = "取消回复")
                }
            }
        }
        if (!topic.isClosed) {
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
                                photoPicker.launch("image/*")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("发文件 / 视频 / 语音") },
                            onClick = {
                                attachMenuOpen = false
                                filePicker.launch("*/*")
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("在话题里发言…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val text = input.trim()
                        if (text.isNotEmpty()) {
                            ForumStore.clearError()
                            ForumStore.sendText(text)
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank()
                ) {
                    Text("发送", fontSize = 16.sp)
                }
            }
        } else {
            Text(
                "这个话题已关闭，不能发言",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
