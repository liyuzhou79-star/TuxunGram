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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.AdminStore
import com.tuxungram.android.td.AuthState
import com.tuxungram.android.td.ChatItem
import com.tuxungram.android.td.ChatStore
import com.tuxungram.android.td.MediaEntry
import com.tuxungram.android.td.MediaStore
import com.tuxungram.android.td.MessageStore
import com.tuxungram.android.td.MsgItem
import com.tuxungram.android.td.MsgKind
import com.tuxungram.android.td.Notifier
import com.tuxungram.android.td.PollUi
import com.tuxungram.android.td.ProxyConfig
import com.tuxungram.android.td.ProxyStore
import com.tuxungram.android.td.SecretChatStore
import com.tuxungram.android.td.TdRepository
import com.tuxungram.android.td.UiSettings
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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

/** Day-separator label for the message list: 今天 / 昨天 /
 *  M月d日 (with the year when it differs). */
fun formatDayLabel(dateSeconds: Int): String {
    if (dateSeconds <= 0) return ""
    val cal = java.util.Calendar.getInstance().apply {
        timeInMillis = dateSeconds * 1000L
    }
    val today = java.util.Calendar.getInstance()
    fun sameDay(a: java.util.Calendar, b: java.util.Calendar) =
        a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
            a.get(java.util.Calendar.DAY_OF_YEAR) ==
            b.get(java.util.Calendar.DAY_OF_YEAR)
    if (sameDay(cal, today)) return "今天"
    today.add(java.util.Calendar.DAY_OF_YEAR, -1)
    if (sameDay(cal, today)) return "昨天"
    val pattern = if (
        cal.get(java.util.Calendar.YEAR) ==
        java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    ) {
        "M月d日"
    } else {
        "yyyy年M月d日"
    }
    return java.text.SimpleDateFormat(
        pattern, java.util.Locale.getDefault()
    ).format(cal.time)
}

/** Local day key (year * 1000 + day-of-year) for separator
 *  computation. */
fun dayKey(dateSeconds: Int): Int {
    if (dateSeconds <= 0) return -1
    val cal = java.util.Calendar.getInstance().apply {
        timeInMillis = dateSeconds * 1000L
    }
    return cal.get(java.util.Calendar.YEAR) * 1000 +
        cal.get(java.util.Calendar.DAY_OF_YEAR)
}

@Composable
fun ChatScreen(
    chatId: Long,
    onBack: () -> Unit,
    onForward: (List<MsgItem>) -> Unit,
    onOpenProfile: () -> Unit
) {
    val context = LocalContext.current
    val chats by ChatStore.chats.collectAsState()
    val adminCaps by AdminStore.caps.collectAsState()
    val chat = chats.firstOrNull { it.id == chatId }
    // Secret-chat state (v0.6.0, E1): drives the encryption
    // banner under the top bar.
    val secretInfo by SecretChatStore.info.collectAsState()
    LaunchedEffect(chatId, chat?.isSecret) {
        if (chat?.isSecret == true) SecretChatStore.loadForChat(chatId)
    }
    val messages by MessageStore.messages.collectAsState()
    val sendError by MessageStore.sendError.collectAsState()
    val clipboard = LocalClipboardManager.current
    // Multi-select mode (v0.7.0, F1): long-press a message and
    // choose 多选, then tap messages to add/remove them; the top
    // bar shows the count and a bottom bar offers the batch
    // actions. Empty set = not in selection mode.
    var selectedIds by remember(chatId) { mutableStateOf<Set<Long>>(emptySet()) }
    val selectionMode = selectedIds.isNotEmpty()
    var pendingDelete by remember { mutableStateOf<List<MsgItem>?>(null) }
    // Full-screen photo viewer (v0.7.0, F2): the photo message
    // id the viewer opens at; null = closed.
    var viewerId by remember { mutableStateOf<Long?>(null) }
    var input by remember { mutableStateOf("") }
    var pendingPhoto by remember { mutableStateOf<String?>(null) }
    var photoCaption by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Draft restore (v0.7.0, F5): once the saved draft has been
    // read, put it back into the input box (only if the user has
    // not already started typing).
    val draftLoaded by MessageStore.draftLoaded.collectAsState()
    LaunchedEffect(draftLoaded) {
        if (draftLoaded && input.isEmpty()) {
            input = MessageStore.draftText.value
        }
    }
    // Draft saving + own typing action: typing sends
    // sendChatAction (throttled inside the store); the draft is
    // saved on a 700 ms debounce. Guarded by draftLoaded so
    // opening a chat can never wipe an unseen cloud draft.
    LaunchedEffect(input, draftLoaded) {
        if (!draftLoaded) return@LaunchedEffect
        if (input.isNotBlank()) MessageStore.sendTypingAction()
        delay(700)
        MessageStore.saveDraft(input)
    }
    // Typing subtitle expiry safety net (F5).
    val peerAction by MessageStore.peerAction.collectAsState()
    LaunchedEffect(peerAction) {
        if (peerAction.isNotEmpty()) {
            delay(6500)
            MessageStore.expirePeerActions()
        }
    }
    val latestInput by rememberUpdatedState(input)
    DisposableEffect(chatId) {
        MessageStore.open(chatId)
        // Admin capabilities for this chat (Batch 3, C4): drives
        // the "delete for everyone" entry on other people's
        // messages.
        AdminStore.loadCaps(chatId)
        // The notifier must not post notifications for the chat
        // the user is already looking at (Batch 3, C1).
        Notifier.foregroundChatId = chatId
        // Opening the chat also withdraws any notification it
        // had raised (v0.7.0, F4).
        Notifier.dismissForChat(context, chatId)
        onDispose {
            // Flush a draft typed just before leaving, before
            // close() zeroes the current chat (F5).
            MessageStore.saveDraft(latestInput)
            MessageStore.close()
            Notifier.foregroundChatId = 0L
        }
    }
    // In selection mode Back first leaves the mode, like in
    // mature clients; only a second Back leaves the chat.
    BackHandler {
        if (selectionMode) selectedIds = emptySet() else onBack()
    }

    // Arriving from a search result: jump to that message once.
    LaunchedEffect(chatId) {
        val target = MessageStore.pendingLocateId
        if (target != 0L) {
            MessageStore.pendingLocateId = 0L
            MessageStore.jumpTo(target)
        }
    }
    // Scroll the located message into view when it lands.
    val locateId by MessageStore.locateId.collectAsState()
    LaunchedEffect(locateId, messages.size) {
        if (locateId != 0L && messages.isNotEmpty()) {
            val ascIndex = messages.indexOfFirst { it.id == locateId }
            if (ascIndex >= 0) {
                listState.scrollToItem(messages.size - 1 - ascIndex)
            }
        }
    }

    var chatSearchOpen by remember { mutableStateOf(false) }
    var chatSearchQuery by remember { mutableStateOf("") }
    val chatSearchResults by MessageStore.searchResults.collectAsState()
    val chatSearchExhausted by MessageStore.searchExhausted.collectAsState()

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
    // Emoji/sticker/GIF panel docked above the keyboard area
    // (steer: the input bar must not be a bare text box).
    var stickerPanelOpen by remember { mutableStateOf(false) }

    // Voice recording (MediaRecorder -> OGG/Opus, TDLib's native
    // voice format). The recorder lives outside composition; the
    // UI only mirrors its state.
    val voiceRecorder = remember { VoiceRecorder() }
    var recording by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableIntStateOf(0) }
    fun startRecording() {
        if (voiceRecorder.start(context)) {
            recording = true
            recordSeconds = 0
        }
    }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
    }
    LaunchedEffect(recording) {
        while (recording) {
            delay(200)
            voiceRecorder.sample()
            recordSeconds = voiceRecorder.elapsedSeconds()
        }
    }
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

    // Root Box so the photo viewer (F2) can overlay the whole
    // chat screen without unmounting it (unmounting would close
    // MessageStore and empty the viewer's photo list).
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // The bottom dock (input bar + sticker panel) rides
            // above the keyboard instead of being covered by it
            // (Phase D, D5); the navigation-bar distance comes
            // from ReadyHost's systemBars padding.
            .imePadding()
    ) {
        if (selectionMode) {
            // Selection-mode header (v0.7.0, F1): count + exit.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedIds = emptySet() }) {
                    Icon(Icons.Filled.Close, contentDescription = "退出多选")
                }
                Text(
                    "已选 ${selectedIds.size} 条",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Row(
                modifier = Modifier
                    .clickable { onOpenProfile() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (chat?.photoPath != null) {
                    AvatarImage(path = chat.photoPath, sizeDp = 36)
                    Spacer(Modifier.width(10.dp))
                }
                Column {
                    Text(
                        chat?.title ?: "对话",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    // Peer typing / recording subtitle (F5).
                    if (peerAction.isNotEmpty()) {
                        Text(
                            peerAction,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }
            if (chat?.isChannel == true) {
                Spacer(Modifier.width(6.dp))
                Text("频道", color = Color.Gray, fontSize = 12.sp)
            }
            if (chat?.isSecret == true) {
                Spacer(Modifier.width(6.dp))
                Text("🔒 秘密聊天", color = Color.Gray, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { chatSearchOpen = !chatSearchOpen }) {
                Icon(Icons.Filled.Search, contentDescription = "搜索聊天记录")
            }
        }
        }

        if (chat?.isSecret == true) {
            val info = if (secretInfo?.chatId == chatId) secretInfo else null
            Text(
                when (info?.state) {
                    "pending" ->
                        "🔒 端到端加密 · 正在等对方上线完成密钥交换，对方上线前消息可能发不出去"
                    "closed" -> "🔒 这个秘密聊天已关闭，不能再发消息"
                    else ->
                        "🔒 端到端加密 · 消息只保存在这台设备上，不同步到云端"
                },
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 5.dp)
            )
        }

        if (chatSearchOpen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = chatSearchQuery,
                    onValueChange = { chatSearchQuery = it },
                    placeholder = { Text("在这个对话里搜索") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    MessageStore.searchInChat(chatSearchQuery)
                }) {
                    Text("搜索")
                }
            }
            if (chatSearchResults.isNotEmpty()) {
                val chatSearchListState = rememberLazyListState()
                LazyColumn(
                    state = chatSearchListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(Color(0xFFF7F7F7))
                        .lazyListScrollbar(chatSearchListState)
                ) {
                    items(
                        items = chatSearchResults,
                        key = { "csr_${it.id}" }
                    ) { hit ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    chatSearchOpen = false
                                    MessageStore.jumpTo(hit.id)
                                }
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Text(
                                hit.text.ifEmpty {
                                    when (hit.kind) {
                                        MsgKind.PHOTO -> "[图片]"
                                        MsgKind.VIDEO -> "[视频]"
                                        MsgKind.VOICE -> "[语音]"
                                        MsgKind.DOCUMENT -> "[文件]"
                                        MsgKind.STICKER -> "[贴纸]"
                                        MsgKind.ANIMATION -> "[动图]"
                                        MsgKind.POLL -> "[投票]"
                                        else -> "[消息]"
                                    }
                                },
                                fontSize = 14.sp, maxLines = 2
                            )
                            Text(
                                formatMsgTime(hit.dateSeconds),
                                color = Color.Gray, fontSize = 11.sp
                            )
                        }
                    }
                    if (!chatSearchExhausted) {
                        item(key = "csr_more") {
                            TextButton(onClick = {
                                MessageStore.searchInChat(chatSearchQuery, append = true)
                            }) {
                                Text("加载更多")
                            }
                        }
                    }
                }
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



        val toDelete = pendingDelete
        if (toDelete != null) {
            // Multi-delete confirm (v0.7.0, F1). Admins with the
            // delete right revoke everything; otherwise own
            // messages are revoked and other people's are only
            // removed locally — the dialog says which.
            val allOwn = toDelete.all { it.outgoing }
            val adminRevoke = !allOwn && adminCaps.canDeleteAny
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("删除选中的 ${toDelete.size} 条消息？") },
                text = {
                    Text(
                        when {
                            allOwn -> "会同时为所有人删除。"
                            adminRevoke ->
                                "你是管理员，所选消息会为所有人删除。"
                            toDelete.any { it.outgoing } ->
                                "你发的会为所有人删除，对方发的只在你这边删除。"
                            else -> "只在你这边删除。"
                        }
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        MessageStore.deleteSelected(toDelete, adminRevoke)
                        pendingDelete = null
                        selectedIds = emptySet()
                    }) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text("取消")
                    }
                }
            )
        }

        // Date separators (details pass): the first message of
        // each day gets a centered day chip above it. Computed
        // here — remember() is not allowed inside the lazy
        // list's content lambda.
        val separatorIds = HashSet<Long>().apply {
            var prevKey = Int.MIN_VALUE
            for (m in messages) {
                val key = dayKey(m.dateSeconds)
                if (key != prevKey) {
                    add(m.id)
                    prevKey = key
                }
            }
        }
        // Chat wallpaper (v0.7.0, F3): a solid color behind the
        // message area only; the top bar and the dock keep the
        // theme surface. 默认 leaves it transparent.
        val themeMode by UiSettings.themeMode.collectAsState()
        val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
        val darkNow = when (themeMode) {
            UiSettings.THEME_LIGHT -> false
            UiSettings.THEME_DARK -> true
            else -> systemDark
        }
        val chatBgArgb = UiSettings.chatBackgroundArgb(darkNow)
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    if (chatBgArgb != null) Color(chatBgArgb)
                    else Color.Transparent
                )
                .padding(horizontal = 12.dp)
                .lazyListScrollbar(listState, reverse = true)
        ) {
            items(items = messages.asReversed(), key = { it.id }) { msg ->
                Column {
                    if (msg.id in separatorIds) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                formatDayLabel(msg.dateSeconds),
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .background(
                                        Color(0x99000000),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                    MessageBubble(
                        msg,
                        isGroup = chat?.isGroup == true,
                        // Secret chats have no reactions; an
                        // empty list hides the picker entry.
                        allowedReactions =
                            if (chat?.isSecret == true) {
                                emptyList()
                            } else {
                                chat?.allowedReactions
                            },
                        onForward = onForward,
                        chatId = chatId,
                        canDeleteAny = adminCaps.canDeleteAny,
                        selectionMode = selectionMode,
                        selected = msg.id in selectedIds,
                        onToggleSelect = { m ->
                            selectedIds = if (m.id in selectedIds) {
                                selectedIds - m.id
                            } else {
                                selectedIds + m.id
                            }
                        },
                        onEnterSelection = { m ->
                            selectedIds = setOf(m.id)
                        },
                        onOpenPhoto = { m -> viewerId = m.id }
                    )
                }
            }
        }

        // Hairline above the bottom dock (Phase D, D5).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
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
        if (selectionMode) {
            // Batch action bar (v0.7.0, F1) replaces the input
            // dock while selecting: forward / copy / delete the
            // whole selection at once.
            val selectedItems = messages.filter { it.id in selectedIds }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        onForward(selectedItems.sortedBy { it.id })
                        selectedIds = emptySet()
                    },
                    enabled = selectedItems.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("转发") }
                TextButton(
                    onClick = {
                        // Copies in time order; media messages
                        // contribute their one-line summary.
                        val text = selectedItems.sortedBy { it.id }
                            .joinToString("\n") { m ->
                                m.text.ifEmpty {
                                    when (m.kind) {
                                        MsgKind.PHOTO -> "[图片]"
                                        MsgKind.VIDEO -> "[视频]"
                                        MsgKind.VOICE -> "[语音]"
                                        MsgKind.DOCUMENT ->
                                            "[文件] ${m.fileName}"
                                        MsgKind.STICKER -> "[贴纸]"
                                        MsgKind.ANIMATION -> "[动图]"
                                        MsgKind.POLL -> "[投票]"
                                        else -> "[消息]"
                                    }
                                }
                            }
                        if (text.isNotEmpty()) {
                            clipboard.setText(AnnotatedString(text))
                        }
                        selectedIds = emptySet()
                    },
                    enabled = selectedItems.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("复制") }
                TextButton(
                    onClick = { pendingDelete = selectedItems },
                    enabled = selectedItems.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("删除", color = Color(0xFFC62828)) }
            }
        } else if (!canBasic && !canPhotos) {
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
            if (recording) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFFC62828), CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "录音中 ${formatDuration(recordSeconds)}",
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        voiceRecorder.cancel()
                        recording = false
                    }) {
                        Text("取消")
                    }
                    Button(onClick = {
                        val result = voiceRecorder.stop()
                        recording = false
                        if (result != null) {
                            MessageStore.clearSendError()
                            MessageStore.sendVoiceNote(
                                result.first, result.second, result.third
                            )
                        }
                    }) {
                        Text("发送")
                    }
                }
            }
            if (!recording) Row(
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
                            DropdownMenuItem(
                                text = { Text("贴纸 / GIF") },
                                onClick = {
                                    attachMenuOpen = false
                                    MediaStore.load()
                                    stickerPanelOpen = true
                                }
                            )
                        }
                    }
                }
                // Sticker/emoji panel toggle: the input bar's
                // left-side entry, like in mature clients.
                IconButton(onClick = {
                    stickerPanelOpen = !stickerPanelOpen
                    if (stickerPanelOpen) MediaStore.load()
                }) {
                    Text(
                        if (stickerPanelOpen) "⌨" else "☺",
                        fontSize = 22.sp,
                        color = Color.Gray
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("消息") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                if (canBasic && input.isBlank()) {
                    // Empty input: the round button is the voice
                    // recorder, like in other Telegram clients.
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFBDBDBD), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = {
                            val granted = androidx.core.content.ContextCompat
                                .checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                startRecording()
                            } else {
                                micPermission.launch(
                                    android.Manifest.permission.RECORD_AUDIO
                                )
                            }
                        }) {
                            // Mic is not in material-icons-core;
                            // an emoji glyph keeps the button clear.
                            Text("🎤", fontSize = 20.sp)
                        }
                    }
                } else {
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
            if (stickerPanelOpen && !recording) {
                MediaPanel(
                    onEmoji = { emoji -> input += emoji },
                    onSendEntry = { entry ->
                        MessageStore.clearSendError()
                        if (entry.isAnimation) {
                            MessageStore.sendAnimationFile(
                                entry.fileId, entry.durationSec,
                                entry.width, entry.height
                            )
                        } else {
                            MessageStore.sendStickerFile(
                                entry.fileId, entry.emoji
                            )
                        }
                    }
                )
            }
        }
    }
        val vid = viewerId
        if (vid != null) {
            MediaViewerScreen(
                initialId = vid,
                onClose = { viewerId = null }
            )
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
fun MessageBubble(
    msg: MsgItem,
    isGroup: Boolean,
    allowedReactions: List<String>?,
    onForward: (List<MsgItem>) -> Unit,
    chatId: Long = 0L,
    canDeleteAny: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: (MsgItem) -> Unit = {},
    onEnterSelection: (MsgItem) -> Unit = {},
    onOpenPhoto: (MsgItem) -> Unit = {}
) {
    val outgoing = msg.outgoing
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var reactionOpen by remember { mutableStateOf(false) }
    // Swipe-to-reply (v0.7.0, F1): drag a bubble left; past the
    // threshold a haptic tick fires and the message becomes the
    // reply target, then the bubble springs back. The mature
    // clients all use this gesture; direction and feel follow
    // theirs.
    val swipeAnim = remember(msg.id) { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    var swipeFired by remember(msg.id) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    Color.Transparent
                }
            )
    ) {
        if (!selectionMode && swipeAnim.value < -8f) {
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
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 1.dp)
                    .size(22.dp)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Text("✓", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .offset { IntOffset(swipeAnim.value.roundToInt(), 0) }
            .pointerInput(msg.id, selectionMode) {
                if (!selectionMode) {
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
                                haptics.performHapticFeedback(
                                    HapticFeedbackType.LongPress
                                )
                                MessageStore.setReplyTarget(msg)
                            }
                        },
                        onDragEnd = {
                            swipeScope.launch { swipeAnim.animateTo(0f) }
                        },
                        onDragCancel = {
                            swipeScope.launch { swipeAnim.animateTo(0f) }
                        }
                    )
                }
            },
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start
    ) {
        Box {
            // Bubble corner style (v0.7.0, F3): 现代/经典 from
            // settings, applied live.
            val bubbleStyle by UiSettings.bubbleStyle.collectAsState()
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
                        RoundedCornerShape(
                            if (bubbleStyle == UiSettings.BUBBLE_CLASSIC) {
                                8.dp
                            } else {
                                18.dp
                            }
                        )
                    )
                    .combinedClickable(
                        onClick = {
                            if (selectionMode) {
                                onToggleSelect(msg)
                            } else if (msg.kind == MsgKind.PHOTO) {
                                // Full-screen viewer (v0.7.0, F2).
                                onOpenPhoto(msg)
                            } else if (msg.fileId != 0 && msg.kind != MsgKind.STICKER) {
                                // File-like messages: tap opens the
                                // downloaded file, or starts the
                                // download. (Stickers are display-only.)
                                val path = msg.filePath
                                if (path != null) {
                                    openFileWithSystem(context, path, msg.fileMime)
                                } else {
                                    MessageStore.downloadFileFor(msg)
                                }
                            }
                        },
                        onDoubleClick = {
                            // Double-tap reaction (v0.7.0, F1):
                            // the chat's first available reaction,
                            // heart preferred — the shortcut every
                            // mature client has. Toggle semantics
                            // come from MessageStore.toggleReaction;
                            // an empty allowed list (secret chats,
                            // reactions off) makes it a no-op.
                            if (!selectionMode) {
                                val emoji = when {
                                    allowedReactions == null -> "❤️"
                                    "❤️" in allowedReactions -> "❤️"
                                    else -> allowedReactions.firstOrNull()
                                }
                                if (emoji != null) {
                                    MessageStore.toggleReaction(msg, emoji)
                                }
                            }
                        },
                        onLongClick = {
                            if (selectionMode) {
                                onToggleSelect(msg)
                            } else {
                                menuOpen = true
                            }
                        }
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
                    MsgKind.TEXT -> {
                        Text(msg.text, fontSize = 15.sp)
                        if (msg.lpTitle.isNotEmpty() || msg.lpSiteName.isNotEmpty()) {
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
                            PollBubbleContent(msg, poll)
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
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            Color(0x14000000)
                                        },
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        if (!chip.chosen && allowedReactions != null &&
                                            chip.emoji !in allowedReactions
                                        ) {
                                            MessageStore.reportReactionBlocked()
                                        } else {
                                            MessageStore.toggleReaction(msg, chip.emoji)
                                        }
                                    }
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
                // Channels commonly restrict reactions to a
                // short admin-picked list, or turn them off
                // entirely; with an empty allowed list the entry
                // is not shown at all.
                if (allowedReactions == null || allowedReactions.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text("添加回应") },
                        onClick = {
                            menuOpen = false
                            reactionOpen = true
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("多选") },
                    onClick = {
                        menuOpen = false
                        onEnterSelection(msg)
                    }
                )
                DropdownMenuItem(
                    text = { Text("转发") },
                    onClick = {
                        menuOpen = false
                        onForward(listOf(msg))
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
        // Only offer what this chat actually allows: chats can
        // restrict reactions to a list (or none), and the server
        // answers 400 for anything outside it.
        val pickerEmojis = MessageStore.COMMON_REACTIONS.filter {
            allowedReactions == null || it in allowedReactions
        }
        AlertDialog(
            onDismissRequest = { reactionOpen = false },
            title = { Text("选个回应") },
            text = {
                if (allowedReactions != null && allowedReactions.isEmpty()) {
                    Text("这个对话不允许回应。")
                } else if (pickerEmojis.isEmpty()) {
                    Text("这个对话只允许它自己的专属回应，本 App 暂时还选不了。")
                } else {
                    Row {
                        for (emoji in pickerEmojis) {
                            TextButton(onClick = {
                                MessageStore.toggleReaction(msg, emoji)
                                reactionOpen = false
                            }) {
                                Text(emoji, fontSize = 22.sp)
                            }
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
        // Admins with the delete right revoke other people's
        // messages for everyone too (Batch 3, C4).
        val adminRevoke = !msg.outgoing && canDeleteAny && chatId != 0L
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text("删除这条消息？") },
            text = {
                Text(
                    when {
                        msg.outgoing -> "会同时为所有人删除。"
                        adminRevoke -> "你是管理员，这条会为所有人删除。"
                        else -> "只在你这边删除。"
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (adminRevoke) {
                        AdminStore.deleteForEveryone(chatId, msg.id)
                    } else {
                        MessageStore.deleteMessage(msg)
                    }
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


/** Sticker image (webp decoded straight from the TDLib file);
 *  shown at a fixed chat-friendly size, no bubble chrome. */
@Composable
private fun StickerBubbleContent(msg: MsgItem) {
    val path = msg.filePath
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
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = msg.stickerEmoji,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(150.dp)
        )
    } else {
        Box(
            modifier = Modifier.size(150.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                msg.stickerEmoji.ifEmpty { "贴纸" },
                fontSize = 40.sp
            )
        }
    }
}

/** GIF/animation bubble: first frame pulled from the downloaded
 *  mp4 via MediaMetadataRetriever, with a GIF tag; tapping the
 *  bubble (handled by the generic file tap) opens the player. */
@Composable
private fun AnimationBubbleContent(msg: MsgItem) {
    val path = msg.filePath
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(path)
                    val frame = retriever.frameAtTime
                    retriever.release()
                    frame
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
    Box {
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
                Text(
                    if (msg.filePath == null) "动图下载中…" else "动图",
                    color = Color.Gray, fontSize = 13.sp
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(6.dp)
                .background(Color(0x99000000), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text("GIF", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Poll bubble: question, tappable options with result bars.
 *  Single-answer polls vote on tap; multiple-answer polls collect
 *  a local selection and submit together. State always re-renders
 *  from TDLib's poll object (updatePoll keeps it current). */
@Composable
private fun PollBubbleContent(msg: MsgItem, poll: PollUi) {
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
    Column(modifier = Modifier.width(240.dp)) {
        Text(poll.question, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            when {
                poll.isClosed -> "投票已结束"
                poll.isQuiz -> "测验"
                poll.isAnonymous -> "匿名投票"
                else -> "公开投票"
            } + if (poll.allowsMultiple) " · 可多选" else "",
            color = Color.Gray, fontSize = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        poll.options.forEachIndexed { index, option ->
            val pct = option.percentage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .background(Color(0x0D000000), RoundedCornerShape(8.dp))
                    .clickable(enabled = !poll.isClosed) {
                        if (poll.allowsMultiple) {
                            selected = if (index in selected) {
                                selected - index
                            } else {
                                selected + index
                            }
                        } else {
                            MessageStore.clearSendError()
                            MessageStore.setPollAnswer(
                                msg,
                                if (option.chosen) emptyList() else listOf(index)
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
                                if (option.chosen || (poll.isQuiz && option.isCorrect)) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color(0x14000000)
                                },
                                RoundedCornerShape(8.dp)
                            )
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        option.text,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (showResults) {
                        Text(
                            "$pct%",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (index in selected && poll.allowsMultiple) {
                        Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                    }
                }
            }
        }
        if (poll.allowsMultiple && !poll.isClosed) {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    MessageStore.clearSendError()
                    MessageStore.setPollAnswer(msg, selected.sorted())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (voted) "更新投票" else "投票")
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "共 ${poll.totalVoters} 人参与",
            color = Color.Gray, fontSize = 12.sp
        )
        if (poll.isQuiz && voted && poll.explanation.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(poll.explanation, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

/** Link preview card under a text message, rendered from the
 *  link_preview object TDLib ships with the message. Tapping
 *  opens the link in the browser. */
@Composable
private fun LinkPreviewCard(msg: MsgItem) {
    val context = LocalContext.current
    val photoPath = msg.lpPhotoPath
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, photoPath
    ) {
        value = if (photoPath == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                try {
                    BitmapFactory.decodeFile(photoPath)
                } catch (t: Throwable) {
                    null
                }
            }
        }
    }
    Column(
        modifier = Modifier
            .width(240.dp)
            .background(Color(0x0D000000), RoundedCornerShape(8.dp))
            .clickable {
                try {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(msg.lpUrl)
                        ).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                } catch (t: Throwable) {
                    // No browser / bad URL: nothing sensible to do.
                }
            }
            .padding(9.dp)
    ) {
        if (msg.lpSiteName.isNotEmpty()) {
            Text(
                msg.lpSiteName,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (msg.lpTitle.isNotEmpty()) {
            Text(msg.lpTitle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
        if (msg.lpDescription.isNotEmpty()) {
            Text(msg.lpDescription, color = Color.Gray, fontSize = 12.sp, maxLines = 3)
        }
        val bmp = bitmap
        if (bmp != null) {
            Spacer(Modifier.height(6.dp))
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        }
    }
}

/** Sticker / GIF picker: three tabs (recent stickers, favorite
 *  stickers, saved GIFs) fed by MediaStore; tapping an entry
 *  sends it immediately. */
/** Emoji offered by the panel's emoji tab; tapping one appends
 *  it to the input text, like in mature clients. */
private val PANEL_EMOJIS = listOf(
    "😀", "😁", "😂", "🤣", "😊", "😍", "😘", "😎",
    "🤔", "😐", "😑", "🙄", "😴", "😷", "🤤", "😋",
    "😛", "😜", "🤪", "😇", "🙂", "🙃", "😉", "😌",
    "😆", "😅", "😭", "😱", "😳", "🥺", "😤", "😡",
    "👍", "👎", "👏", "🙏", "💪", "🔥", "❤️", "💔",
    "💯", "🎉", "🎂", "🎁", "🌹", "☀️", "🌙", "⭐",
    "⚡", "❄️", "☕", "🍺", "🍕", "🍔", "🍎", "🍉",
    "⚽", "🎮", "🎧", "🚗", "✈️", "🏠", "💰", "📱",
    "💻", "⏰", "✅", "❌", "❓", "💤", "💢", "👀"
)

/**
 * The docked emoji / sticker / GIF panel that opens under the
 * input bar (keyboard-area position, like mature clients):
 * emoji tab appends to the text; sticker tab browses recent
 * stickers first, then favorites and every installed set;
 * GIF tab shows saved animations. Tapping a sticker or GIF
 * sends it immediately and the panel stays open.
 */
@Composable
private fun MediaPanel(
    onEmoji: (String) -> Unit,
    onSendEntry: (com.tuxungram.android.td.MediaEntry) -> Unit
) {
    var tab by remember { mutableIntStateOf(1) }
    // null = recent, -1 = favorites, otherwise a set id.
    var setSel by remember { mutableStateOf<Long?>(null) }
    val recent by MediaStore.recentStickers.collectAsState()
    val favorites by MediaStore.favoriteStickers.collectAsState()
    val animations by MediaStore.savedAnimations.collectAsState()
    val sets by MediaStore.stickerSets.collectAsState()
    val setStickers by MediaStore.setStickers.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(268.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp)) {
            for ((index, label) in listOf("表情", "贴纸", "GIF").withIndex()) {
                TextButton(
                    onClick = { tab = index },
                    enabled = tab != index
                ) {
                    Text(if (tab == index) "● $label" else label)
                }
            }
        }
        when (tab) {
            0 -> LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier.fillMaxSize()
            ) {
                gridItems(PANEL_EMOJIS) { emoji ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable { onEmoji(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 24.sp)
                    }
                }
            }
            1 -> Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp)
                ) {
                    TextButton(onClick = { setSel = null }) {
                        Text(if (setSel == null) "● 最近" else "最近")
                    }
                    TextButton(onClick = { setSel = -1L }) {
                        Text(if (setSel == -1L) "● 收藏" else "收藏")
                    }
                    for (set in sets) {
                        TextButton(onClick = {
                            setSel = set.id
                            MediaStore.loadSet(set.id)
                        }) {
                            Text(
                                if (setSel == set.id) "● ${set.title}"
                                else set.title
                            )
                        }
                    }
                }
                val entries = when {
                    setSel == null -> recent
                    setSel == -1L -> favorites
                    else -> setStickers
                }
                if (entries.isEmpty()) {
                    Text(
                        "这里还没有内容。在 Telegram 里用过贴纸后，这里就会有。",
                        color = Color.Gray, fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        gridItems(entries, key = { it.fileId }) { entry ->
                            MediaPickerCell(entry) { onSendEntry(entry) }
                        }
                    }
                }
            }
            else -> {
                if (animations.isEmpty()) {
                    Text(
                        "还没有保存的 GIF。在 Telegram 里存过 GIF 后会出现在这里。",
                        color = Color.Gray, fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        gridItems(animations, key = { it.fileId }) { entry ->
                            MediaPickerCell(entry) { onSendEntry(entry) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaPickerCell(entry: MediaEntry, onClick: () -> Unit) {
    val path = entry.previewPath
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
    Box(
        modifier = Modifier
            .size(72.dp)
            .clickable { onClick() }
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = entry.emoji,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else if (entry.isAnimation) {
            Text("GIF", fontWeight = FontWeight.Bold, color = Color.Gray)
        } else {
            Text(entry.emoji.ifEmpty { "…" }, fontSize = 28.sp)
        }
        if (entry.isAnimation) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color(0x99000000), RoundedCornerShape(3.dp))
                    .padding(horizontal = 3.dp)
            ) {
                Text("GIF", color = Color.White, fontSize = 8.sp)
            }
        }
    }
}

/** Minimal voice recorder: MediaRecorder into an OGG/Opus file
 *  (Telegram's native voice-note format), sampling maxAmplitude
 *  a few times a second for the waveform. All failures are
 *  swallowed into "no recording" — the chat screen just stays. */
private class VoiceRecorder {
    private var recorder: android.media.MediaRecorder? = null
    private var file: File? = null
    private val amplitudes = ArrayList<Int>()
    private var startedAt = 0L

    fun start(context: android.content.Context): Boolean {
        return try {
            val f = File(context.cacheDir, "voice_${System.currentTimeMillis()}.ogg")
            @Suppress("DEPRECATION")
            val r = android.media.MediaRecorder()
            r.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(android.media.MediaRecorder.OutputFormat.OGG)
            r.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.OPUS)
            r.setAudioSamplingRate(48000)
            r.setAudioEncodingBitRate(32000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = f
            amplitudes.clear()
            startedAt = System.currentTimeMillis()
            true
        } catch (t: Throwable) {
            recorder = null
            false
        }
    }

    fun sample() {
        try {
            recorder?.maxAmplitude?.let { amplitudes.add(it) }
        } catch (t: Throwable) {
            // Recorder died mid-way; stop() will sort it out.
        }
    }

    fun elapsedSeconds(): Int =
        if (startedAt == 0L) 0 else ((System.currentTimeMillis() - startedAt) / 1000).toInt()

    /** Stop and keep the recording: (path, durationSec, waveform
     *  base64), or null when it was too short / broken. */
    fun stop(): Triple<String, Int, String>? {
        val r = recorder ?: return null
        val seconds = elapsedSeconds()
        try {
            r.stop()
        } catch (t: Throwable) {
            // Too short to stop cleanly counts as cancelled.
        }
        try {
            r.release()
        } catch (t: Throwable) {
        }
        recorder = null
        val f = file
        file = null
        if (f == null || seconds < 1 || !f.exists() || f.length() == 0L) {
            f?.delete()
            return null
        }
        return Triple(f.absolutePath, seconds, packWaveform(amplitudes))
    }

    fun cancel() {
        val r = recorder
        recorder = null
        if (r != null) {
            try {
                r.stop()
            } catch (t: Throwable) {
            }
            try {
                r.release()
            } catch (t: Throwable) {
            }
        }
        file?.delete()
        file = null
    }
}

/** TDLib's waveform: 100 amplitude values of 5 bits each
 *  (0..31), packed LSB-first, base64-encoded. Resampled from
 *  whatever sampling the recorder produced. */
private fun packWaveform(samples: List<Int>): String {
    if (samples.isEmpty()) return ""
    val values = IntArray(100)
    for (i in 0 until 100) {
        val from = i * samples.size / 100
        val to = maxOf(from + 1, (i + 1) * samples.size / 100)
        var sum = 0L
        var count = 0
        for (j in from until minOf(to, samples.size)) {
            sum += samples[j]
            count++
        }
        val avg = if (count > 0) sum / count else 0L
        values[i] = (avg * 31 / 32767L).toInt().coerceIn(0, 31)
    }
    val bytes = ArrayList<Byte>()
    var acc = 0
    var bits = 0
    for (v in values) {
        acc = acc or (v shl bits)
        bits += 5
        while (bits >= 8) {
            bytes.add((acc and 0xFF).toByte())
            acc = acc ushr 8
            bits -= 8
        }
    }
    if (bits > 0) bytes.add((acc and 0xFF).toByte())
    val arr = ByteArray(bytes.size) { bytes[it] }
    return android.util.Base64.encodeToString(arr, android.util.Base64.NO_WRAP)
}
