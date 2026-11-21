package com.tuxungram.android.ui


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.AuthState
import com.tuxungram.android.td.ChatItem
import com.tuxungram.android.td.ChatStore
import com.tuxungram.android.td.MessageStore
import com.tuxungram.android.td.MsgItem
import com.tuxungram.android.td.MsgKind
import com.tuxungram.android.td.ProxyConfig
import com.tuxungram.android.td.ProxyStore
import com.tuxungram.android.td.TdRepository
import com.tuxungram.android.td.UiSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay


fun formatMsgTime(dateSeconds: Int): String {
    if (dateSeconds <= 0) return ""
    val cal = java.util.Calendar.getInstance().apply {
        timeInMillis = dateSeconds * 1000L
    }
    return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(cal.time)
}

@Composable
fun ChatScreen(chatId: Long, onBack: () -> Unit, onForward: (MsgItem) -> Unit) {
    val context = LocalContext.current
    val chats by ChatStore.chats.collectAsState()
    val chat = chats.firstOrNull { it.id == chatId }
    val messages by MessageStore.messages.collectAsState()
    val sendError by MessageStore.sendError.collectAsState()
    var input by remember { mutableStateOf("") }
    var pendingPhoto by remember { mutableStateOf<String?>(null) }
    var photoCaption by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    DisposableEffect(chatId) {
        MessageStore.open(chatId)
        onDispose { MessageStore.close() }
    }
    BackHandler { onBack() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val file = File(context.cacheDir, "pick_${System.currentTimeMillis()}.jpg")
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    file.outputStream().use { output -> inputStream.copyTo(output) }
                }
                pendingPhoto = file.absolutePath
                photoCaption = ""
            } catch (t: Throwable) {
                // Picker copy failed: stay on the chat screen silently.
            }
        }
    }

    var attachMenuOpen by remember { mutableStateOf(false) }
    // (path, mimeType, displayName) of a picked file awaiting confirm.
    var pendingFile by remember { mutableStateOf<Triple<String, String, String>?>(null) }
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
                val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val file = File(
                    context.cacheDir,
                    "pick_${System.currentTimeMillis()}_$safeName"
                )
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    file.outputStream().use { output -> inputStream.copyTo(output) }
                }
                pendingFile = Triple(file.absolutePath, mime, displayName)
            } catch (t: Throwable) {
                // Picker copy failed: stay on the chat screen silently.
            }
        }
    }

    LaunchedEffect(listState, messages.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastVisible ->
            if (messages.isNotEmpty() && lastVisible >= messages.size - 3) {
                MessageStore.loadOlder()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            if (chat?.photoPath != null) {
                AvatarImage(path = chat.photoPath, sizeDp = 36)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                chat?.title ?: "对话",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (chat?.isChannel == true) {
                Spacer(Modifier.width(6.dp))
                Text("频道", color = Color.Gray, fontSize = 12.sp)
            }
        }

        val photoPath = pendingPhoto
        if (photoPath != null) {
            PhotoPreview(
                path = photoPath,
                caption = photoCaption,
                onCaptionChange = { photoCaption = it },
                onSend = {
                    MessageStore.sendPhoto(photoPath, photoCaption.trim())
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
                        MessageStore.clearSendError()
                        MessageStore.sendFile(
                            fileToSend.first, fileToSend.second, fileToSend.third
                        )
                        pendingFile = null
                    }) { Text("发送") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingFile = null }) { Text("取消") }
                }
            )
        }

        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            items(items = messages.asReversed(), key = { it.id }) { msg ->
                MessageBubble(msg, isGroup = chat?.isGroup == true, onForward = onForward)
            }
        }

        if (sendError != null) {
            Text(
                sendError!!,
                color = Color(0xFFC62828),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        val canBasic = chat?.canSendBasic ?: true
        val canPhotos = chat?.canSendPhotos ?: true
        if (!canBasic && !canPhotos) {
            Text(
                "你不能在这个对话里发言",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            val replyTarget by MessageStore.replyTarget.collectAsState()
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
                            target.text.ifEmpty {
                                if (target.kind == MsgKind.PHOTO) "[图片]" else "原消息"
                            },
                            color = Color.Gray,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = { MessageStore.setReplyTarget(null) }) {
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
                if (canPhotos) {
                    Box {
                        IconButton(onClick = { attachMenuOpen = true }) {
                            Icon(
                                // AttachFile is not in material-icons-core;
                                // a "+" attach button is the standard
                                // pattern in several mature clients.
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
                                    picker.launch("image/*")
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
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("消息") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (canBasic && input.isNotBlank()) {
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
                                MessageStore.clearSendError()
                                MessageStore.sendText(text)
                                input = ""
                            }
                        },
                        enabled = canBasic && input.isNotBlank()
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

@Composable
fun PhotoPreview(
    path: String,
    caption: String,
    onCaptionChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        value = withContext(Dispatchers.IO) {
            try {
                BitmapFactory.decodeFile(path)
            } catch (t: Throwable) {
                null
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("发送图片", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFFEEEEEE)),
                contentAlignment = Alignment.Center
            ) {
                Text("图片读不出来", color = Color.Gray)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = caption,
            onValueChange = onCaptionChange,
            label = { Text("说明（可空）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("取消")
            }
            Button(onClick = onSend, modifier = Modifier.weight(1f)) {
                Text("发送")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(msg: MsgItem, isGroup: Boolean, onForward: (MsgItem) -> Unit) {
    val outgoing = msg.outgoing
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var reactionOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Column(
                modifier = Modifier
                    .background(
                        if (outgoing) Color(0xFFD8EDCB) else Color(0xFFF1F1F1),
                        RoundedCornerShape(12.dp)
                    )
                    .combinedClickable(
                        onClick = {
                            // File-like messages: tap opens the
                            // downloaded file, or starts the download.
                            if (msg.fileId != 0) {
                                val path = msg.filePath
                                if (path != null) {
                                    openFileWithSystem(context, path, msg.fileMime)
                                } else {
                                    MessageStore.downloadFileFor(msg)
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
                            .background(Color(0x14000000), RoundedCornerShape(6.dp))
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
                    MsgKind.TEXT -> Text(msg.text, fontSize = 15.sp)
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
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            Color(0x14000000)
                                        },
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { MessageStore.toggleReaction(msg, chip.emoji) }
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text("${chip.emoji} ${chip.count}", fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(5.dp))
                        }
                    }
                }
                Row {
                    Text(formatMsgTime(msg.dateSeconds), color = Color.Gray, fontSize = 11.sp)
                    when (msg.readState) {
                        "read" -> Text(
                            "  ✓✓",
                            color = Color(0xFF5B7C99),
                            fontSize = 11.sp
                        )
                        "sent" -> Text(
                            "  ✓",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
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
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("回复") },
                    onClick = {
                        menuOpen = false
                        MessageStore.setReplyTarget(msg)
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
                if (outgoing && msg.kind == MsgKind.TEXT && msg.sendingState.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = {
                            menuOpen = false
                            editOpen = true
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("添加回应") },
                    onClick = {
                        menuOpen = false
                        reactionOpen = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("转发") },
                    onClick = {
                        menuOpen = false
                        onForward(msg)
                    }
                )
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
    if (editOpen) {
        var draft by remember(msg.id) { mutableStateOf(msg.text) }
        AlertDialog(
            onDismissRequest = { editOpen = false },
            title = { Text("编辑消息") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val text = draft.trim()
                        if (text.isNotEmpty() && text != msg.text) {
                            MessageStore.clearSendError()
                            MessageStore.editText(msg.id, text)
                        }
                        editOpen = false
                    },
                    enabled = draft.isNotBlank()
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editOpen = false }) { Text("取消") }
            }
        )
    }
    if (reactionOpen) {
        AlertDialog(
            onDismissRequest = { reactionOpen = false },
            title = { Text("选个回应") },
            text = {
                Row {
                    for (emoji in MessageStore.COMMON_REACTIONS) {
                        TextButton(onClick = {
                            MessageStore.toggleReaction(msg, emoji)
                            reactionOpen = false
                        }) {
                            Text(emoji, fontSize = 22.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { reactionOpen = false }) { Text("取消") }
            }
        )
    }
    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text("删除这条消息？") },
            text = {
                Text(
                    if (msg.outgoing) "会同时为所有人删除。" else "只在你这边删除。"
                )
            },
            confirmButton = {
                Button(onClick = {
                    MessageStore.deleteMessage(msg)
                    deleteOpen = false
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) { Text("取消") }
            }
        )
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes}B" else String.format("%.1f%s", value, units[unit])
}

fun formatDuration(totalSeconds: Int): String {
    if (totalSeconds <= 0) return ""
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

/** Hand a downloaded file to whatever app can open it, via
 *  FileProvider (the file lives in TDLib's private directory). */
fun openFileWithSystem(context: android.content.Context, path: String, mime: String) {
    try {
        val file = File(path)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "com.tuxungram.android.fileprovider", file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime.ifEmpty { "*/*" })
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (t: Throwable) {
        // No viewer installed or provider mismatch: nothing sensible
        // to do from the bubble; the file stays downloaded.
    }
}

@Composable
fun FileBubbleContent(msg: MsgItem) {
    val tag = when (msg.kind) {
        MsgKind.VIDEO -> "▶"
        MsgKind.VOICE -> "语音"
        else -> msg.fileName.substringAfterLast('.', "").uppercase()
            .take(4).ifEmpty { "文件" }
    }
    val meta = buildList {
        if (msg.durationSec > 0) add(formatDuration(msg.durationSec))
        val size = formatFileSize(msg.fileSize)
        if (size.isNotEmpty()) add(size)
        add(
            when {
                msg.filePath != null -> "点击打开"
                msg.fileDownloading -> "下载中…"
                else -> "点击下载"
            }
        )
    }.joinToString(" · ")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(Color(0xFF5B7C99), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(tag, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(9.dp))
        Column {
            Text(
                msg.fileName.ifEmpty { "文件" },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )
            Text(meta, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun PhotoBubbleContent(msg: MsgItem) {
    val path = msg.photoPath
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                try {
                    BitmapFactory.decodeFile(path)
                } catch (t: Throwable) {
                    null
                }
            }
        }
    }
    val ratio = if (msg.photoWidth > 0 && msg.photoHeight > 0) {
        msg.photoHeight.toFloat() / msg.photoWidth.toFloat()
    } else {
        0.75f
    }
    val heightDp = (220f * ratio).coerceIn(110f, 300f)
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 220.dp, height = heightDp.dp)
        )
    } else {
        Box(
            modifier = Modifier
                .size(width = 220.dp, height = heightDp.dp)
                .background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center
        ) {
            Text("图片加载中…", color = Color.Gray, fontSize = 13.sp)
        }
    }
}

