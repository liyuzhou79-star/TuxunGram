package com.tuxungram.android.ui


import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
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
import android.provider.ContactsContract
import com.tuxungram.android.td.AdminStore
import com.tuxungram.android.td.AppLock
import com.tuxungram.android.td.DeepLink
import com.tuxungram.android.td.AuthState
import com.tuxungram.android.td.ChatItem
import com.tuxungram.android.td.ChatStore
import com.tuxungram.android.td.ForumStore
import com.tuxungram.android.td.ForumTopicUi
import com.tuxungram.android.td.InlineBtn
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
import com.tuxungram.android.td.StickerSetStore
import com.tuxungram.android.td.TrendingSetUi
import com.tuxungram.android.td.SendModifiers
import com.tuxungram.android.td.AvatarStore
import com.tuxungram.android.td.BotStore
import com.tuxungram.android.td.InlineResultUi
import com.tuxungram.android.td.InlineStore
import com.tuxungram.android.td.ViewersStore
import com.tuxungram.android.td.ReplyKb
import com.tuxungram.android.td.TdClient
import com.tuxungram.android.td.TdRepository
import com.tuxungram.android.td.ThreadStore
import com.tuxungram.android.td.UiSettings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

/** When a member saw my message (v0.9.13, T1): today shows
 *  the time, yesterday and older days prefix the day label. */
fun formatViewDate(dateSeconds: Int): String {
    if (dateSeconds <= 0) return ""
    val day = formatDayLabel(dateSeconds)
    val time = formatMsgTime(dateSeconds)
    return if (day == "今天") time else "$day $time"
}

/** Inline-query input shape (v0.9.12, S2): "@botname" at the
 *  very start, at least one whitespace character, then the
 *  query text (possibly empty). Mirrors the official clients:
 *  no space after the username, no inline mode. */
private val INLINE_QUERY_REGEX =
    Regex("^@([A-Za-z0-9_]{3,})\\s([\\s\\S]*)$")

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    chatId: Long,
    onBack: () -> Unit,
    onForward: (List<MsgItem>) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenThread: (Long) -> Unit = {}
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
    // Comment threads (v0.8.0, G1): a channel's posts get a
    // 评论 entry only when the channel has a linked discussion
    // group; ThreadStore resolves it once per channel.
    val linkedChatId by ThreadStore.linkedChatId.collectAsState()
    LaunchedEffect(chatId, chat?.isChannel) {
        if (chat?.isChannel == true) ThreadStore.loadLinkedChat(chatId)
    }
    val messages by MessageStore.messages.collectAsState()
    val sendError by MessageStore.sendError.collectAsState()
    TranslateErrorDialog()
    val clipboard = LocalClipboardManager.current
    // Multi-select mode (v0.7.0, F1): long-press a message and
    // choose 多选, then tap messages to add/remove them; the top
    // bar shows the count and a bottom bar offers the batch
    // actions. Empty set = not in selection mode.
    var selectedIds by remember(chatId) { mutableStateOf<Set<Long>>(emptySet()) }
    val selectionMode = selectedIds.isNotEmpty()
    // Forum groups (v0.8.0, G2): opening one lands on its
    // topic list; a chosen topic replaces the message area.
    val isForum by ForumStore.isForum.collectAsState()
    var forumTopic by remember(chatId) { mutableStateOf<ForumTopicUi?>(null) }
    LaunchedEffect(chatId) {
        ForumStore.checkForum(chatId)
    }
    var pendingDelete by remember { mutableStateOf<List<MsgItem>?>(null) }
    // Full-screen photo viewer (v0.7.0, F2): the photo message
    // id the viewer opens at; null = closed.
    var viewerId by remember { mutableStateOf<Long?>(null) }
    // In-app video player target (v0.9.1, J3).
    var videoItem by remember { mutableStateOf<MsgItem?>(null) }
    var input by remember { mutableStateOf("") }
    // Send-button long-press menu (v0.9.3, M5): silent and
    // scheduled sending.
    var sendMenuOpen by remember { mutableStateOf(false) }
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
        BotStore.ensure(chatId)
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
            // close() zeroes the current chat (F5). Guarded by
            // the still-current chat (v0.9.11, A1): during an
            // animated chat-to-chat transition the incoming
            // screen has already opened the store for its own
            // chat — this screen must not save its draft into
            // that chat, close the store under it, or clear
            // its foreground marker.
            if (MessageStore.currentChatId == chatId) {
                MessageStore.saveDraft(latestInput)
                MessageStore.close()
            }
            if (Notifier.foregroundChatId == chatId) {
                Notifier.foregroundChatId = 0L
            }
        }
    }
    // In selection mode Back first leaves the mode, like in
    // mature clients; only a second Back leaves the chat. In a
    // forum, Back first returns from a topic to the topic list.
    BackHandler {
        if (forumTopic != null) {
            forumTopic = null
        } else if (selectionMode) {
            selectedIds = emptySet()
        } else {
            onBack()
        }
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

    // Bot adaptation (v0.9.9, W2): the bot surface of this
    // chat (slash commands / group bots) and the live reply
    // keyboard state. kbDismissedSource hides a one-time
    // keyboard locally after its first tap, until the bot
    // sends a newer keyboard (a different source message).
    val botUi by BotStore.state.collectAsState()
    val replyKb by MessageStore.replyKb.collectAsState()
    var kbDismissedSource by remember(chatId) { mutableStateOf(0L) }
    // Inline bot queries (v0.9.12, S2): an input shaped like
    // "@botname <query>" feeds InlineStore (debounced — every
    // keystroke restarts the effect, so the query only leaves
    // after a short pause); any other shape closes the panel.
    val inlineUi by InlineStore.state.collectAsState()
    val inlineMatch = remember(input) {
        INLINE_QUERY_REGEX.find(input)
    }
    LaunchedEffect(
        chatId,
        inlineMatch?.groupValues?.get(1),
        inlineMatch?.groupValues?.get(2)
    ) {
        val m = inlineMatch
        if (m == null) {
            InlineStore.syncInput(chatId, null, null)
        } else {
            delay(350)
            InlineStore.syncInput(
                chatId, m.groupValues[1], m.groupValues[2]
            )
        }
    }
    // Pinned bar state (v0.9.4, N3): tapping the bar cycles
    // through the pinned messages; 全部 opens the full list.
    val pinnedMessages by MessageStore.pinnedMessages.collectAsState()
    // Empty-chat graphic (v0.9.9, W1): only after a short
    // grace period, so it never flashes while history loads.
    var showEmptyHistory by remember(chatId) { mutableStateOf(false) }
    LaunchedEffect(chatId, messages.isEmpty()) {
        showEmptyHistory = false
        if (messages.isEmpty()) {
            kotlinx.coroutines.delay(900)
            showEmptyHistory = true
        }
    }
    var pinCycle by remember(chatId) { mutableIntStateOf(0) }
    var pinnedListOpen by remember { mutableStateOf(false) }
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

    // Round video notes (v0.9.14, U4): recorded with the
    // system camera app through ACTION_VIDEO_CAPTURE (the
    // caller needs no camera permission and no camera
    // library); the result is copied into cacheDir, its
    // duration/size read back, and sent as a video note.
    val videoCapture = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK &&
            uri != null
        ) {
            try {
                val file = File(
                    context.cacheDir,
                    "videonote_${System.currentTimeMillis()}.mp4"
                )
                context.contentResolver.openInputStream(uri)
                    ?.use { inputStream ->
                        file.outputStream().use { output ->
                            inputStream.copyTo(output)
                        }
                    }
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                val durationMs = retriever.extractMetadata(
                    android.media.MediaMetadataRetriever
                        .METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 0L
                val width = retriever.extractMetadata(
                    android.media.MediaMetadataRetriever
                        .METADATA_KEY_VIDEO_WIDTH
                )?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(
                    android.media.MediaMetadataRetriever
                        .METADATA_KEY_VIDEO_HEIGHT
                )?.toIntOrNull() ?: 0
                retriever.release()
                val side = minOf(width, height)
                if (durationMs > 0 && side > 0) {
                    MessageStore.clearSendError()
                    MessageStore.sendVideoNote(
                        file.absolutePath,
                        (durationMs / 1000).toInt(),
                        side
                    )
                } else {
                    Toast.makeText(
                        context,
                        "这段视频读不出时长，没有发出去。",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (t: Throwable) {
                Toast.makeText(
                    context,
                    "视频保存失败，没有发出去。",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    var attachMenuOpen by remember { mutableStateOf(false) }
    // Create-a-poll sheet (v0.9.4, N1).
    var pollCreateOpen by remember { mutableStateOf(false) }
    // Location sharing (v0.9.4, N2): a fetched fix waits here
    // for the confirm dialog before it is sent.
    var pendingLocation by remember {
        mutableStateOf<Pair<Double, Double>?>(null)
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            fetchOneLocation(context) { fix ->
                if (fix != null) {
                    pendingLocation = fix
                } else {
                    Toast.makeText(
                        context,
                        "没取到位置，检查定位开关再试一次。",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            Toast.makeText(
                context,
                "没有定位权限，发不了位置。",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    // Contact sharing (v0.9.4, N2): pick from the system
    // address book, read name + first phone number, send the
    // card. A contact without a number cannot be shared.
    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri != null) {
            val picked = readPickedContact(context, uri)
            if (picked == null) {
                Toast.makeText(
                    context,
                    "这个联系人没有电话号码，发不了。",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                MessageStore.clearSendError()
                MessageStore.sendContact(picked.first, picked.second, picked.third)
            }
        }
    }
    // Emoji/sticker/GIF panel docked above the keyboard area
    // (steer: the input bar must not be a bare text box).
    var stickerPanelOpen by remember { mutableStateOf(false) }

    // Voice recording (MediaRecorder -> OGG/Opus, TDLib's native
    // voice format). The recorder lives outside composition; the
    // UI only mirrors its state.
    val voiceRecorder = remember { VoiceRecorder() }
    // Leaving the screen mid-recording must not leave the
    // microphone running: cancel the recorder with the screen
    // (audit fix A2 — it previously kept recording for the
    // rest of the process lifetime).
    DisposableEffect(Unit) {
        onDispose { voiceRecorder.cancel() }
    }
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
        // Header swap animation (v0.9.13, T6; 0.9.11 debt):
        // the normal top bar and the selection-mode bar
        // cross-fade with a slight vertical slide through
        // Motion instead of cutting.
        AnimatedContent(
            targetState = selectionMode,
            transitionSpec = {
                (fadeIn(Motion.tween()) +
                    slideInVertically(Motion.tween()) {
                        if (targetState) it / 3 else -it / 3
                    }) togetherWith
                    (fadeOut(Motion.tween()) +
                        slideOutVertically(Motion.tween()) {
                            if (targetState) -it / 3 else it / 3
                        })
            },
            label = "chatHeader"
        ) { selecting ->
        if (selecting) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Lock, contentDescription = null,
                        tint = Color.Gray, modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text("秘密聊天", color = Color.Gray, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { chatSearchOpen = !chatSearchOpen }) {
                Icon(Icons.Filled.Search, contentDescription = "搜索聊天记录")
            }
        }
        }
        }

        if (isForum) {
            // Forum group (v0.8.0, G2): the topic list (or one
            // open topic) fills the whole area under the top
            // bar; the normal message list and dock do not
            // apply — a forum's messages live inside topics.
            val topic = forumTopic
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (topic == null) {
                    ForumTopicsList(
                        chatId = chatId,
                        onOpenTopic = { forumTopic = it }
                    )
                } else {
                    ForumTopicView(chatId = chatId, topic = topic)
                }
            }
            return@Column
        }

        if (chat?.isSecret == true) {
            val info = if (secretInfo?.chatId == chatId) secretInfo else null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Lock, contentDescription = null,
                    tint = Color.Gray, modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    when (info?.state) {
                        "pending" ->
                            "端到端加密 · 正在等对方上线完成密钥交换，对方上线前消息可能发不出去"
                        "closed" -> "这个秘密聊天已关闭，不能再发消息"
                        else ->
                            "端到端加密 · 消息只保存在这台设备上，不同步到云端"
                    },
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
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
                                        MsgKind.LOCATION -> "[位置]"
                                        MsgKind.CONTACT -> "[联系人]"
                                        MsgKind.VIDEO_NOTE -> "[视频消息]"
                                        MsgKind.DICE -> "[骰子]"
                                        MsgKind.GAME -> "[游戏]"
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
                onSendOptions = { mods ->
                    MessageStore.clearSendError()
                    MessageStore.sendPhoto(photoPath, photoCaption.trim(), mods)
                    pendingPhoto = null
                },
                allowWhenOnline = chat != null && !chat.isGroup &&
                    !chat.isChannel && !chat.isSecret,
                onCancel = { pendingPhoto = null }
            )
            return@Column
        }

        val locationToSend = pendingLocation
        if (locationToSend != null) {
            AlertDialog(
                onDismissRequest = { pendingLocation = null },
                title = { Text("发送当前位置？") },
                text = {
                    Text(
                        "纬度 ${formatCoord(locationToSend.first)}，" +
                            "经度 ${formatCoord(locationToSend.second)}"
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        MessageStore.clearSendError()
                        MessageStore.sendLocation(
                            locationToSend.first, locationToSend.second
                        )
                        pendingLocation = null
                    }) { Text("发送") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingLocation = null }) {
                        Text("取消")
                    }
                }
            )
        }

        if (pollCreateOpen) {
            PollCreateDialog(
                onDismiss = { pollCreateOpen = false },
                onSend = { q, opts, anon, multi, correct, expl ->
                    MessageStore.clearSendError()
                    MessageStore.sendPoll(q, opts, anon, multi, correct, expl)
                    pollCreateOpen = false
                }
            )
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
        // Unread divider (v0.9.11, A2): the first incoming
        // message past the read position captured when this
        // chat was opened gets the "unread messages" capsule
        // above it. Computed per composition (the read
        // position lands asynchronously after open).
        val unreadCut = MessageStore.lastReadInboxAtOpen
        val unreadFirstId = if (unreadCut == 0L) {
            0L
        } else {
            messages.firstOrNull {
                it.id > unreadCut && !it.outgoing
            }?.id ?: 0L
        }
        // Sender-avatar runs (v0.9.9, W0): in a group, an
        // incoming message carries its sender's avatar only
        // when it opens a run — the chronologically previous
        // message (`messages` is ascending) is outgoing, from
        // another sender, or on an earlier day. The rest of the
        // run leaves the avatar slot blank so the bubbles stay
        // aligned, the way mature clients group them.
        val avatarShowIds = remember(messages, chat?.isGroup) {
            val show = HashSet<Long>()
            if (chat?.isGroup == true) {
                var prev: MsgItem? = null
                for (m in messages) {
                    if (!m.outgoing && m.senderUserId != 0L) {
                        val p = prev
                        if (p == null || p.outgoing ||
                            p.senderUserId != m.senderUserId ||
                            m.id in separatorIds
                        ) {
                            show.add(m.id)
                        }
                    }
                    prev = m
                }
            }
            show
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
        // Custom wallpaper picture (v0.9.4, N4): when a
        // picture is set it replaces the solid wallpaper
        // behind the message area (the two are alternatives).
        val chatBgImage by UiSettings.chatBgImage.collectAsState()
        val wallpaperBitmap by produceState<android.graphics.Bitmap?>(
            initialValue = null, chatBgImage
        ) {
            value = if (chatBgImage.isEmpty()) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    decodeCached(chatBgImage, maxDim = 1080)
                }
            }
        }
        if (pinnedMessages.isNotEmpty()) {
            // Pinned bar (v0.9.4, N3): the newest pinned
            // message's summary; tapping jumps to it and
            // advances to the next one, like mature clients.
            val pinned = pinnedMessages[
                pinCycle % pinnedMessages.size
            ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        MessageStore.jumpTo(pinned.id)
                        pinCycle += 1
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    PinIcon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "置顶消息" +
                            if (pinnedMessages.size > 1) {
                                "（${pinCycle % pinnedMessages.size + 1}/${pinnedMessages.size}）"
                            } else {
                                ""
                            },
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        messageSummaryText(pinned),
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
                TextButton(onClick = { pinnedListOpen = true }) {
                    Text("全部")
                }
            }
        }
        if (pinnedListOpen) {
            AlertDialog(
                onDismissRequest = { pinnedListOpen = false },
                title = { Text("全部置顶消息") },
                text = {
                    val pinnedListState = rememberLazyListState()
                    LazyColumn(
                        state = pinnedListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .lazyListScrollbar(pinnedListState)
                    ) {
                        items(
                            items = pinnedMessages,
                            key = { "pin_${it.id}" }
                        ) { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        pinnedListOpen = false
                                        MessageStore.jumpTo(item.id)
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    messageSummaryText(item),
                                    fontSize = 14.sp, maxLines = 2
                                )
                                Text(
                                    formatMsgTime(item.dateSeconds),
                                    color = Color.Gray, fontSize = 11.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { pinnedListOpen = false }) {
                        Text("关闭")
                    }
                }
            )
        }
        // Message area with its wallpaper behind it (solid
        // color since F3; a picked picture since N4 — the
        // picture gets a theme-tinted scrim so text outside
        // bubbles stays readable on any photo).
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val wpBitmap = wallpaperBitmap
            if (chatBgImage.isNotEmpty() && wpBitmap != null) {
                Image(
                    bitmap = wpBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.background.copy(
                                alpha = if (darkNow) 0.45f else 0.35f
                            )
                        )
                )
            } else if (chatBgArgb != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(chatBgArgb))
                )
            }
        if (messages.isEmpty() && showEmptyHistory) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    Icons.AutoMirrored.Filled.Send,
                    "还没有消息",
                    "发条消息打个招呼吧。"
                )
            }
        }
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .lazyListScrollbar(listState, reverse = true)
        ) {
            items(items = messages.asReversed(), key = { it.id }) { msg ->
                // Entry motion (v0.9.11, A1): a message that
                // arrives while the chat is open — just sent by
                // me or just received — fades and settles in
                // instead of popping. Gated on the message's
                // own date, so paging through history (whose
                // messages are old) never animates.
                val entryFresh = remember(msg.id) {
                    kotlin.math.abs(
                        System.currentTimeMillis() / 1000 -
                            msg.dateSeconds
                    ) < 120
                }
                val entry = remember(msg.id) {
                    Animatable(if (entryFresh) 0f else 1f)
                }
                LaunchedEffect(msg.id) {
                    if (entryFresh) {
                        entry.animateTo(1f, Motion.tween(260))
                    }
                }
                Column(
                    modifier = Modifier.graphicsLayer {
                        alpha = entry.value
                        val s = 0.96f + 0.04f * entry.value
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                ) {
                    if (msg.id in separatorIds) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                formatDayLabel(msg.dateSeconds),
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme
                                            .surfaceVariant,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme
                                            .outlineVariant,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (unreadFirstId != 0L && msg.id == unreadFirstId) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "未读消息",
                                color = MaterialTheme.colorScheme
                                    .onPrimaryContainer,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme
                                            .primaryContainer,
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
                        onOpenPhoto = { m -> viewerId = m.id },
                        onOpenVideo = { m -> videoItem = m },
                        canComment = chat?.isChannel == true &&
                            linkedChatId != 0L && msg.isChannelPost,
                        onOpenThread = { m -> onOpenThread(m.id) },
                        canGetViewers = msg.outgoing &&
                            msg.sendingState.isEmpty() &&
                            (chat?.isGroup == true ||
                                chat?.isChannel == true),
                        showSenderAvatar = msg.id in avatarShowIds,
                        onOpenSenderProfile = { userId ->
                            DeepLink.pendingProfileUser.value = userId
                        }
                    )
                }
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
        // Batch action bar (v0.7.0, F1) replaces the input
        // dock while selecting: forward / copy / delete the
        // whole selection at once. Animated (v0.9.11, A1): it
        // rises and fades in instead of swapping in a hard
        // cut; the dock below composes only when not selecting.
        val selectedItems = messages.filter { it.id in selectedIds }
        AnimatedVisibility(
            visible = selectionMode,
            enter = expandVertically(Motion.tween(200)) +
                fadeIn(Motion.tween(200)),
            exit = shrinkVertically(Motion.tween(160)) +
                fadeOut(Motion.tween(160))
        ) {
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
                                        MsgKind.LOCATION -> "[位置]"
                                        MsgKind.CONTACT -> "[联系人]"
                                        MsgKind.VIDEO_NOTE -> "[视频消息]"
                                        MsgKind.DICE -> "[骰子]"
                                        MsgKind.GAME -> "[游戏]"
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
        }
        if (!selectionMode) {
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
            if (!recording) {
                // Bot surfaces (v0.9.9, W2), directly above the
                // input row: the /start button, the force-reply
                // hint, the slash-command popup and the reply
                // keyboard grid.
                val bot = botUi?.takeIf { it.chatId == chatId }
                val kb = replyKb
                // Panel mutex (v0.9.12, S2): the inline panel,
                // the slash popup and the reply keyboard never
                // stack — inline wins while it is active, the
                // slash popup next, the keyboard otherwise.
                val inlineActive = inlineUi.active &&
                    inlineUi.chatId == chatId
                AnimatedVisibility(
                    visible = bot?.isBot == true &&
                        messages.none { it.outgoing } &&
                        (messages.isNotEmpty() || showEmptyHistory),
                    enter = expandVertically(Motion.tween(200)) +
                        fadeIn(Motion.tween(200)),
                    exit = shrinkVertically(Motion.tween(160)) +
                        fadeOut(Motion.tween(160))
                ) {
                    Button(
                        onClick = {
                            // sendBotStartMessage (td_api.tl):
                            // the canonical /start path; an empty
                            // parameter is a plain start.
                            val query = TdClient.request(
                                "sendBotStartMessage"
                            ).apply {
                                put("bot_user_id", bot?.botUserId ?: 0L)
                                put("chat_id", chatId)
                                put("parameter", "")
                            }
                            TdRepository.sendRaw(query) {}
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            BotIcon, contentDescription = null,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("开始")
                    }
                }
                val forceHint = when (kb) {
                    is ReplyKb.ForceReply -> kb.placeholder
                    is ReplyKb.Shown ->
                        if (kb.forceReply) kb.placeholder else null
                    else -> null
                }
                AnimatedVisibility(
                    visible = forceHint != null,
                    enter = expandVertically(Motion.tween(180)) +
                        fadeIn(Motion.tween(180)),
                    exit = shrinkVertically(Motion.tween(140)) +
                        fadeOut(Motion.tween(140))
                ) {
                    if (forceHint != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme
                                    .surfaceVariant
                            )
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            ReplyIcon, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            forceHint.ifEmpty { "机器人要求回复一条消息" },
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                    }
                }
                // Inline panel (v0.9.12, S2): results for the
                // "@bot query" in the input, directly above the
                // input row. Header names the bot; the body is
                // loading / plain-language error / empty / the
                // result rows; tapping a row sends it.
                AnimatedVisibility(
                    visible = inlineActive,
                    enter = expandVertically(
                        Motion.tween(200),
                        expandFrom = Alignment.Bottom
                    ) + fadeIn(Motion.tween(200)),
                    exit = shrinkVertically(
                        Motion.tween(160),
                        shrinkTowards = Alignment.Bottom
                    ) + fadeOut(Motion.tween(160))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme
                                    .surfaceContainerLow
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 14.dp, end = 4.dp,
                                    top = 4.dp, bottom = 4.dp
                                ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Icon(
                                BotIcon, contentDescription = null,
                                tint = MaterialTheme.colorScheme
                                    .primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "@${inlineUi.username}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            val hint = when {
                                inlineUi.error != null -> ""
                                inlineUi.placeholder.isNotEmpty() ->
                                    inlineUi.placeholder
                                inlineUi.buttonText.isNotEmpty() ->
                                    inlineUi.buttonText
                                else -> "内联结果"
                            }
                            if (hint.isNotEmpty()) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    hint, fontSize = 12.5.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            IconButton(onClick = {
                                InlineStore.dismiss()
                            }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "收起",
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        when {
                            inlineUi.error != null -> Text(
                                inlineUi.error!!,
                                fontSize = 13.5.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(
                                    horizontal = 14.dp, vertical = 10.dp
                                )
                            )
                            inlineUi.loading -> Row(
                                modifier = Modifier.padding(
                                    horizontal = 14.dp, vertical = 10.dp
                                ),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "正在向机器人查询…",
                                    fontSize = 13.5.sp,
                                    color = Color.Gray
                                )
                            }
                            inlineUi.results.isEmpty() -> Text(
                                "没有结果，换个关键词试试。",
                                fontSize = 13.5.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(
                                    horizontal = 14.dp, vertical = 10.dp
                                )
                            )
                            else -> LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 264.dp)
                            ) {
                                items(
                                    items = inlineUi.results,
                                    key = { it.id }
                                ) { result ->
                                    InlineResultRow(result) {
                                        InlineStore.send(result) {
                                            input = ""
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Slash popup: while the input is a bare /prefix
                // (no space yet), list the matching commands of
                // this chat's bot(s) above the input.
                val slashItems = ArrayList<Triple<String, String, String>>()
                if (bot?.isBot == true) {
                    for (c in bot.commands) {
                        slashItems.add(
                            Triple(
                                "/${c.command} ",
                                "/${c.command}",
                                c.description
                            )
                        )
                    }
                } else if (bot != null) {
                    for (gb in bot.groupBots) {
                        for (c in gb.commands) {
                            val shown = if (gb.username.isEmpty()) {
                                "/${c.command}"
                            } else {
                                "/${c.command}@${gb.username}"
                            }
                            slashItems.add(
                                Triple("$shown ", shown, c.description)
                            )
                        }
                    }
                }
                val slashQuery = input.trim()
                val slashMatches = if (
                    slashQuery.startsWith("/") &&
                    !slashQuery.contains(" ") &&
                    slashItems.isNotEmpty()
                ) {
                    slashItems.filter {
                        it.second.startsWith(slashQuery)
                    }.take(6)
                } else {
                    emptyList()
                }
                // The popup keeps the last non-empty match
                // list for its exit animation (v0.9.11, A1):
                // the live list empties the moment the input
                // stops matching, which would blank the popup
                // before it can sink away.
                var lastSlash by remember {
                    mutableStateOf<List<Triple<String, String, String>>>(
                        emptyList()
                    )
                }
                LaunchedEffect(slashMatches) {
                    if (slashMatches.isNotEmpty()) {
                        lastSlash = slashMatches
                    }
                }
                AnimatedVisibility(
                    visible = slashMatches.isNotEmpty() && !inlineActive,
                    enter = expandVertically(
                        Motion.tween(180),
                        expandFrom = Alignment.Bottom
                    ) + fadeIn(Motion.tween(180)),
                    exit = shrinkVertically(
                        Motion.tween(140),
                        shrinkTowards = Alignment.Bottom
                    ) + fadeOut(Motion.tween(140))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme
                                    .surfaceContainerLow
                            )
                    ) {
                        for (item in lastSlash) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { input = item.first }
                                    .padding(
                                        horizontal = 14.dp,
                                        vertical = 9.dp
                                    ),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Icon(
                                    BotIcon, contentDescription = null,
                                    tint = MaterialTheme.colorScheme
                                        .primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    item.second,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (item.third.isNotEmpty()) {
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        item.third,
                                        fontSize = 12.5.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
                // Reply keyboard grid: rows straight from the
                // markup; a text button sends its label as a
                // message, other button types render disabled.
                AnimatedVisibility(
                    visible = kb is ReplyKb.Shown &&
                        kbDismissedSource != kb.sourceMessageId &&
                        !inlineActive && slashMatches.isEmpty(),
                    enter = expandVertically(
                        Motion.tween(200),
                        expandFrom = Alignment.Bottom
                    ) + fadeIn(Motion.tween(200)),
                    exit = shrinkVertically(
                        Motion.tween(160),
                        shrinkTowards = Alignment.Bottom
                    ) + fadeOut(Motion.tween(160))
                ) {
                    if (kb is ReplyKb.Shown) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        for (row in kb.rows) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                for (btn in row) {
                                    Button(
                                        enabled = btn.isText,
                                        onClick = {
                                            MessageStore.clearSendError()
                                            MessageStore.sendText(btn.text)
                                            if (kb.oneTime) {
                                                kbDismissedSource =
                                                    kb.sourceMessageId
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(3.dp)
                                    ) {
                                        Text(btn.text, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
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
                                // Hand-drawn paperclip (AppIcons):
                                // the core material set has none.
                                ClipIcon,
                                contentDescription = "发送附件",
                                tint = Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = attachMenuOpen,
                            onDismissRequest = { attachMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(ImageIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                                },
                                text = { Text("发图片") },
                                onClick = {
                                    attachMenuOpen = false
                                    AppLock.noteExternalLaunch()
                                    picker.launch("image/*")
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(ClipIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                                },
                                text = { Text("发文件 / 视频 / 语音") },
                                onClick = {
                                    attachMenuOpen = false
                                    AppLock.noteExternalLaunch()
                                    filePicker.launch("*/*")
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                },
                                text = { Text("圆形视频") },
                                onClick = {
                                    attachMenuOpen = false
                                    val intent = android.content.Intent(
                                        android.provider.MediaStore
                                            .ACTION_VIDEO_CAPTURE
                                    ).apply {
                                        putExtra(
                                            android.provider.MediaStore
                                                .EXTRA_DURATION_LIMIT, 60
                                        )
                                        putExtra(
                                            android.provider.MediaStore
                                                .EXTRA_VIDEO_QUALITY, 0
                                        )
                                    }
                                    if (intent.resolveActivity(
                                            context.packageManager
                                        ) == null
                                    ) {
                                        Toast.makeText(
                                            context,
                                            "这台设备上没有可用的相机应用。",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        AppLock.noteExternalLaunch()
                                        videoCapture.launch(intent)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Filled.Face, contentDescription = null, modifier = Modifier.size(20.dp))
                                },
                                text = { Text("贴纸 / GIF") },
                                onClick = {
                                    attachMenuOpen = false
                                    MediaStore.load()
                                    stickerPanelOpen = true
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(PollIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                                },
                                text = { Text("投票") },
                                onClick = {
                                    attachMenuOpen = false
                                    pollCreateOpen = true
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp))
                                },
                                text = { Text("位置") },
                                onClick = {
                                    attachMenuOpen = false
                                    val granted = androidx.core.content.ContextCompat
                                        .checkSelfPermission(
                                            context,
                                            android.Manifest.permission
                                                .ACCESS_FINE_LOCATION
                                        ) == android.content.pm.PackageManager
                                        .PERMISSION_GRANTED ||
                                        androidx.core.content.ContextCompat
                                            .checkSelfPermission(
                                                context,
                                                android.Manifest.permission
                                                    .ACCESS_COARSE_LOCATION
                                            ) == android.content.pm.PackageManager
                                            .PERMISSION_GRANTED
                                    if (granted) {
                                        fetchOneLocation(context) { fix ->
                                            if (fix != null) {
                                                pendingLocation = fix
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "没取到位置，检查定位开关再试一次。",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    } else {
                                        locationPermission.launch(
                                            arrayOf(
                                                android.Manifest.permission
                                                    .ACCESS_FINE_LOCATION,
                                                android.Manifest.permission
                                                    .ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                                },
                                text = { Text("联系人") },
                                onClick = {
                                    attachMenuOpen = false
                                    AppLock.noteExternalLaunch()
                                    contactPicker.launch(null)
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
                    Icon(
                        if (stickerPanelOpen) KeyboardIcon else Icons.Filled.Face,
                        contentDescription = "表情与贴纸",
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
                if (botUi?.takeIf { it.chatId == chatId }
                        ?.let { it.commands.isNotEmpty() || it.groupBots.isNotEmpty() } == true
                ) {
                    // Slash entry (W2): one tap puts a "/" in the
                    // box, which raises the command popup.
                    IconButton(onClick = { if (!input.startsWith("/")) input = "/$input" }) {
                        Text(
                            "/",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = {
                        Text(
                            when (val kbNow = replyKb) {
                                is ReplyKb.Shown ->
                                    kbNow.placeholder.ifEmpty { "消息" }
                                is ReplyKb.ForceReply ->
                                    kbNow.placeholder.ifEmpty { "消息" }
                                else -> "消息"
                            }
                        )
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                // Send-button press feedback (v0.9.11, A1):
                // the round action button dips slightly while
                // pressed instead of only rippling.
                val sendInteraction = remember {
                    MutableInteractionSource()
                }
                val sendPressed by sendInteraction
                    .collectIsPressedAsState()
                val sendScale by animateFloatAsState(
                    if (sendPressed) 0.86f else 1f,
                    Motion.tween(120),
                    label = "sendScale"
                )
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
                            Icon(
                                MicIcon,
                                contentDescription = "录语音",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                } else {
                    // Send button (v0.9.3, M5): tap sends now,
                    // long-press opens the silent / scheduled
                    // menu — the mature-client pattern.
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                scaleX = sendScale
                                scaleY = sendScale
                            }
                            .background(
                                if (canBasic && input.isNotBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color(0xFFBDBDBD)
                                },
                                CircleShape
                            )
                            .combinedClickable(
                                enabled = canBasic && input.isNotBlank(),
                                interactionSource = sendInteraction,
                                indication = androidx.compose.foundation
                                    .LocalIndication.current,
                                onClick = {
                                    val text = input.trim()
                                    if (text.isNotEmpty()) {
                                        MessageStore.clearSendError()
                                        MessageStore.sendText(text)
                                        input = ""
                                    }
                                },
                                onLongClick = { sendMenuOpen = true }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = Color.White
                        )
                        DropdownMenu(
                            expanded = sendMenuOpen,
                            onDismissRequest = { sendMenuOpen = false }
                        ) {
                            SendOptionMenuItems(
                                allowWhenOnline = chat != null &&
                                    !chat.isGroup && !chat.isChannel &&
                                    !chat.isSecret,
                                onPick = { mods ->
                                    sendMenuOpen = false
                                    val text = input.trim()
                                    if (text.isNotEmpty()) {
                                        MessageStore.clearSendError()
                                        MessageStore.sendText(text, mods)
                                        input = ""
                                    }
                                },
                                onCustom = {
                                    sendMenuOpen = false
                                    pickCustomSendTime(context) { sec ->
                                        val text = input.trim()
                                        if (text.isNotEmpty()) {
                                            MessageStore.clearSendError()
                                            MessageStore.sendText(
                                                text,
                                                SendModifiers(
                                                    scheduleAtSec = sec
                                                )
                                            )
                                            input = ""
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            // Sticker panel (Phase D steer; v0.9.11, A1):
            // rises out of the keyboard area and sinks back
            // instead of popping in and out.
            AnimatedVisibility(
                visible = stickerPanelOpen && !recording,
                enter = expandVertically(Motion.tween(240)) +
                    fadeIn(Motion.tween(240)),
                exit = shrinkVertically(Motion.tween(200)) +
                    fadeOut(Motion.tween(200))
            ) {
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
    }
        val vid = viewerId
        if (vid != null) {
            MediaViewerScreen(
                initialId = vid,
                onClose = { viewerId = null }
            )
        }
        val vItem = videoItem
        if (vItem?.filePath != null) {
            VideoPlayerScreen(
                path = vItem.filePath!!,
                title = vItem.fileName,
                onClose = { videoItem = null }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoPreview(
    path: String,
    caption: String,
    onCaptionChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onSendOptions: (SendModifiers) -> Unit = {},
    allowWhenOnline: Boolean = false
) {
    val previewContext = LocalContext.current
    var photoMenuOpen by remember { mutableStateOf(false) }
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        // The picked photo is a full-size camera original:
        // decode it downsampled or a huge shot can OOM here.
        value = withContext(Dispatchers.IO) { decodeDownsampled(path) }
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
            // Send (v0.9.3, M5): tap sends now; long-press
            // opens the same silent / scheduled menu as the
            // text send button.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(20.dp)
                    )
                    .combinedClickable(
                        onClick = onSend,
                        onLongClick = { photoMenuOpen = true }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("发送", color = Color.White)
                DropdownMenu(
                    expanded = photoMenuOpen,
                    onDismissRequest = { photoMenuOpen = false }
                ) {
                    SendOptionMenuItems(
                        allowWhenOnline = allowWhenOnline,
                        onPick = { mods ->
                            photoMenuOpen = false
                            onSendOptions(mods)
                        },
                        onCustom = {
                            photoMenuOpen = false
                            pickCustomSendTime(previewContext) { sec ->
                                onSendOptions(
                                    SendModifiers(scheduleAtSec = sec)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

/** Items of the send long-press menu (v0.9.3, M5): silent
 *  send plus scheduled presets and a custom time. Shared by
 *  the chat input's send button and the photo preview. */
@Composable
fun SendOptionMenuItems(
    allowWhenOnline: Boolean,
    onPick: (SendModifiers) -> Unit,
    onCustom: () -> Unit
) {
    DropdownMenuItem(
        text = { Text("静默发送（不提醒对方）") },
        onClick = { onPick(SendModifiers(silent = true)) }
    )
    DropdownMenuItem(
        text = {
            Text(
                "定时发送",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        enabled = false,
        onClick = {}
    )
    val nowSec = System.currentTimeMillis() / 1000
    val at22 = nextTimeOfDay(22, 0)
    val at9 = nextTimeOfDay(9, 0)
    val presets = listOf(
        "1 小时后" to nowSec + 3600,
        (if (isToday(at22)) "今晚 22:00" else "明晚 22:00") to at22,
        (if (isToday(at9)) "今天 9:00" else "明早 9:00") to at9
    )
    for ((label, atSec) in presets) {
        DropdownMenuItem(
            text = { Text("$label · ${formatScheduleWhen(atSec)}") },
            onClick = { onPick(SendModifiers(scheduleAtSec = atSec)) }
        )
    }
    if (allowWhenOnline) {
        DropdownMenuItem(
            text = { Text("对方上线时发送") },
            onClick = { onPick(SendModifiers(whenOnline = true)) }
        )
    }
    DropdownMenuItem(
        text = { Text("自定义时间…") },
        onClick = onCustom
    )
}

/** Next occurrence of hour:minute (today if still ahead). */
fun nextTimeOfDay(hour: Int, minute: Int): Long {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
    cal.set(java.util.Calendar.MINUTE, minute)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    if (cal.timeInMillis <= System.currentTimeMillis()) {
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
    }
    return cal.timeInMillis / 1000
}

fun isToday(epochSec: Long): Boolean {
    val a = java.util.Calendar.getInstance()
    val b = java.util.Calendar.getInstance().apply {
        timeInMillis = epochSec * 1000
    }
    return a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
        a.get(java.util.Calendar.DAY_OF_YEAR) ==
        b.get(java.util.Calendar.DAY_OF_YEAR)
}

/** "M月d日 HH:mm" for menu labels and bubble schedule lines. */
fun formatScheduleWhen(epochSec: Long): String =
    java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA)
        .format(java.util.Date(epochSec * 1000))

/** Framework date + time pickers for a custom schedule time
 *  (no new dependencies). A picked time that is not safely in
 *  the future is refused with a plain toast. */
fun pickCustomSendTime(
    context: android.content.Context,
    onPicked: (Long) -> Unit
) {
    val now = java.util.Calendar.getInstance()
    val dateDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, day ->
            android.app.TimePickerDialog(
                context,
                { _, hour, minute ->
                    val cal = java.util.Calendar.getInstance()
                    cal.set(year, month, day, hour, minute, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                    val sec = cal.timeInMillis / 1000
                    if (sec > System.currentTimeMillis() / 1000 + 30) {
                        onPicked(sec)
                    } else {
                        Toast.makeText(
                            context,
                            "这个时间已经过了，换一个晚一点的时间。",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                now.get(java.util.Calendar.HOUR_OF_DAY),
                now.get(java.util.Calendar.MINUTE),
                true
            ).show()
        },
        now.get(java.util.Calendar.YEAR),
        now.get(java.util.Calendar.MONTH),
        now.get(java.util.Calendar.DAY_OF_MONTH)
    )
    dateDialog.datePicker.minDate = System.currentTimeMillis()
    dateDialog.show()
}

@OptIn(ExperimentalFoundationApi::class)
/** Inline keyboard under a bot message (v0.8.0, G4): rows of
 *  buttons from replyMarkupInlineKeyboard. URL buttons open the
 *  browser; callback buttons go through getCallbackQueryAnswer
 *  and the bot's answer is shown as a toast, an alert dialog
 *  (show_alert), or opened as a URL; callback-with-password
 *  buttons first ask for the account's 2FA password. Types this
 *  app cannot honour yet (switch-inline, games, payments,
 *  user-profile buttons) answer with an honest hint instead of
 *  silently doing nothing. Reused by the thread/forum screens. */
@Composable
fun InlineButtonGrid(msg: MsgItem, chatId: Long) {
    if (msg.buttons.isEmpty()) return
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var alertText by remember { mutableStateOf<String?>(null) }
    var passwordBtn by remember { mutableStateOf<InlineBtn?>(null) }
    var password by remember { mutableStateOf("") }
    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
    fun openUrl(url: String) {
        // All URL taps route through the link router (W3):
        // Telegram links stay in the app, the rest go to the
        // browser as before.
        com.tuxungram.android.td.LinkRouter.open(context, url)
    }
    fun onAnswer(text: String, showAlert: Boolean, url: String) {
        when {
            url.isNotEmpty() -> openUrl(url)
            showAlert && text.isNotEmpty() -> alertText = text
            text.isNotEmpty() -> toast(text)
        }
    }
    fun press(btn: InlineBtn) {
        when (btn.kind) {
            "url" -> if (btn.url.isNotEmpty()) openUrl(btn.url)
            "copy" -> {
                clipboard.setText(AnnotatedString(btn.copyText))
                toast("已复制")
            }
            "callback" -> MessageStore.answerCallback(
                chatId, msg.id, btn.data, null,
                onAnswer = ::onAnswer,
                onError = { toast(it) }
            )
            "callbackPw" -> {
                password = ""
                passwordBtn = btn
            }
            "switch" ->
                toast("这个按钮要在输入框里配合机器人使用，暂未支持")
            "game" -> MessageStore.answerCallbackGame(
                chatId, msg.id, msg.gameShortName,
                onAnswer = ::onAnswer,
                onError = { toast(it) }
            )
            "buy" -> toast("付款按钮暂未支持")
            "user" -> toast("暂未支持从按钮打开对方主页")
            else -> toast("这个按钮暂不可用")
        }
    }
    Column(modifier = Modifier.padding(top = 6.dp)) {
        for (row in msg.buttons) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (btn in row) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { press(btn) }
                            .padding(horizontal = 6.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            btn.text,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
    val alert = alertText
    if (alert != null) {
        AlertDialog(
            onDismissRequest = { alertText = null },
            title = { Text("机器人回复") },
            text = { Text(alert) },
            confirmButton = {
                TextButton(onClick = { alertText = null }) {
                    Text("知道了")
                }
            }
        )
    }
    val pwTarget = passwordBtn
    if (pwTarget != null) {
        AlertDialog(
            onDismissRequest = { passwordBtn = null },
            title = { Text("需要两步验证密码") },
            text = {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("输入你的两步验证密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pw = password
                        passwordBtn = null
                        if (pw.isNotEmpty()) {
                            MessageStore.answerCallback(
                                chatId, msg.id, pwTarget.data, pw,
                                onAnswer = ::onAnswer,
                                onError = { toast(it) }
                            )
                        }
                    },
                    enabled = password.isNotEmpty()
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { passwordBtn = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/** Sender avatar beside a group bubble (v0.9.9, W0): the
 *  resolved photo, or a colored initial disc while it is still
 *  downloading / the user has none. Tapping opens the sender's
 *  profile page through the caller's route. */
@Composable
fun SenderAvatarBubble(
    msg: MsgItem,
    onOpenSenderProfile: (Long) -> Unit
) {
    val hue = (msg.senderUserId % 8 + 8) % 8
    val colors = listOf(
        Color(0xFFEF9A9A), Color(0xFFF48FB1), Color(0xFFCE93D8),
        Color(0xFF90CAF9), Color(0xFF80CBC4), Color(0xFFA5D6A7),
        Color(0xFFFFCC80), Color(0xFFBCAAA4)
    )
    Box(
        modifier = Modifier.clickable {
            onOpenSenderProfile(msg.senderUserId)
        }
    ) {
        val path = msg.senderPhotoPath
        if (path != null) {
            AvatarImage(path = path, sizeDp = 32)
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(colors[hue.toInt()], CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    msg.senderName.trim().firstOrNull()?.toString()
                        ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
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
    onOpenPhoto: (MsgItem) -> Unit = {},
    onOpenVideo: (MsgItem) -> Unit = {},
    canComment: Boolean = false,
    onOpenThread: (MsgItem) -> Unit = {},
    // Read-by list (v0.9.13, T1): the caller enables it for
    // own sent messages in groups/channels — the scenes
    // getMessageViewers can answer for.
    canGetViewers: Boolean = false,
    // Group sender avatar (v0.9.9, W0): the caller decides
    // from the whole list whether this message opens a
    // same-sender run; the tap route opens the sender's
    // profile page.
    showSenderAvatar: Boolean = false,
    onOpenSenderProfile: (Long) -> Unit = {}
) {
    val outgoing = msg.outgoing
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var reactionOpen by remember { mutableStateOf(false) }
    var viewersOpen by remember { mutableStateOf(false) }
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
                    Icon(
                        Icons.Filled.Check, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(14.dp)
                    )
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
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Sender avatar slot (v0.9.9, W0): group incoming
        // messages reserve a 32 dp column at the left — the
        // photo on the first message of a same-sender run, a
        // blank spacer on the rest, so bubbles align.
        if (isGroup && !outgoing) {
            if (showSenderAvatar && msg.senderUserId != 0L) {
                SenderAvatarBubble(
                    msg = msg,
                    onOpenSenderProfile = onOpenSenderProfile
                )
            } else {
                Spacer(Modifier.width(32.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
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
                            } else if (msg.kind == MsgKind.VIDEO &&
                                msg.filePath != null
                            ) {
                                // In-app player (v0.9.1, J3); an
                                // undownloaded video falls through
                                // to the download branch below.
                                onOpenVideo(msg)
                            } else if (msg.kind == MsgKind.VIDEO_NOTE) {
                                // Round video (v0.9.14, U4):
                                // play in-app once downloaded,
                                // otherwise start the download.
                                if (msg.filePath != null) {
                                    onOpenVideo(msg)
                                } else {
                                    MessageStore.downloadFileFor(msg)
                                }
                            } else if (msg.fileId != 0 && msg.kind != MsgKind.STICKER &&
                                msg.kind != MsgKind.VOICE &&
                                msg.kind != MsgKind.DICE &&
                                msg.kind != MsgKind.GAME
                            ) {
                                // File-like messages: tap opens the
                                // downloaded file, or starts the
                                // download. (Stickers are display-only;
                                // voice plays in its own bubble, L5.)
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
                        fontWeight = FontWeight.SemiBold,
                        modifier = if (msg.senderUserId != 0L) {
                            Modifier.clickable {
                                onOpenSenderProfile(msg.senderUserId)
                            }
                        } else {
                            Modifier
                        }
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
                        RichMessageText(
                            msg,
                            onBotCommand = { MessageStore.sendText(it) }
                        )
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
                            RichMessageText(
                                msg,
                                onBotCommand = { MessageStore.sendText(it) }
                            )
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
                            RichMessageText(
                                msg,
                                onBotCommand = { MessageStore.sendText(it) }
                            )
                        }
                    }
                    MsgKind.DOCUMENT, MsgKind.VIDEO -> {
                        FileBubbleContent(msg)
                        if (msg.text.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            RichMessageText(
                                msg,
                                onBotCommand = { MessageStore.sendText(it) }
                            )
                        }
                    }
                    MsgKind.VOICE -> {
                        VoiceBubbleContent(msg)
                        if (msg.text.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            RichMessageText(
                                msg,
                                onBotCommand = { MessageStore.sendText(it) }
                            )
                        }
                    }
                    MsgKind.LOCATION -> LocationBubbleContent(msg)
                    MsgKind.CONTACT -> ContactBubbleContent(msg)
                    MsgKind.VIDEO_NOTE -> VideoNoteBubbleContent(msg)
                    MsgKind.DICE -> DiceBubbleContent(msg)
                    MsgKind.GAME -> GameBubbleContent(msg)
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
                if (msg.buttons.isNotEmpty()) {
                    InlineButtonGrid(msg, chatId)
                }
                Row {
                    Text(formatMsgTime(msg.dateSeconds), color = Color.Gray, fontSize = 11.sp)
                    when (msg.readState) {
                        "read" -> {
                            Spacer(Modifier.width(4.dp))
                            TicksIcon(
                                read = true,
                                color = Color(0xFF5B7C99),
                                sizeDp = 15
                            )
                        }
                        "sent" -> {
                            Spacer(Modifier.width(4.dp))
                            TicksIcon(
                                read = false,
                                color = Color.Gray,
                                sizeDp = 15
                            )
                        }
                    }
                    when (msg.sendingState) {
                        "pending" -> Text(
                            "  发送中…",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                        "failed" -> Text(
                            "  发送失败，点这里重发",
                            color = Color(0xFFC62828),
                            fontSize = 11.sp,
                            modifier = Modifier.clickable {
                                MessageStore.resend(msg.id)
                            }
                        )
                    }
                }
                // Scheduled-send label (v0.9.3, M5): the
                // bubble stays in the chat with its send time
                // written under it, as TDLib returns it.
                if (msg.scheduledAtSec > 0 || msg.scheduledWhenOnline) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            ClockIcon, contentDescription = null,
                            tint = Color.Gray, modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            if (msg.scheduledAtSec > 0) {
                                "定时 · ${formatScheduleWhen(msg.scheduledAtSec.toLong())} 发送"
                            } else {
                                "定时 · 对方上线时发送"
                            },
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
                TranslationArea(chatId, msg)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(ReplyIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    text = { Text("回复") },
                    onClick = {
                        menuOpen = false
                        MessageStore.setReplyTarget(msg)
                    }
                )
                if (canComment) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(Icons.Filled.List, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        text = { Text("评论") },
                        onClick = {
                            menuOpen = false
                            onOpenThread(msg)
                        }
                    )
                }
                if (msg.text.isNotEmpty()) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(CopyIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        text = { Text("复制") },
                        onClick = {
                            menuOpen = false
                            clipboard.setText(AnnotatedString(msg.text))
                        }
                    )
                }
                if (msg.sendingState.isEmpty()) {
                    // Copy message link (v0.9.15, V1): the
                    // store asks TDLib for the link; public
                    // chats/channels get a t.me link on the
                    // clipboard, everywhere else the answer is
                    // a plain sentence, never a raw error.
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(LinkIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        text = { Text("复制链接") },
                        onClick = {
                            menuOpen = false
                            MessageStore.fetchMessageLink(
                                msg,
                                onLink = { link ->
                                    clipboard.setText(
                                        AnnotatedString(link)
                                    )
                                    Toast.makeText(
                                        context, "链接已复制",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onFail = { why ->
                                    Toast.makeText(
                                        context, why,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    )
                }
                if (msg.text.isNotEmpty() && msg.sendingState.isEmpty()) {
                    // Translate (v0.9.5, O4): the result lands
                    // under the bubble via TranslationArea.
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(GlobeIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        text = { Text("翻译") },
                        onClick = {
                            menuOpen = false
                            MessageStore.clearTranslateError()
                            MessageStore.translateMessage(chatId, msg)
                        }
                    )
                }
                if (outgoing && msg.kind == MsgKind.TEXT && msg.sendingState.isEmpty()) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
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
                        leadingIcon = {
                            Icon(Icons.Filled.Face, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        text = { Text("添加回应") },
                        onClick = {
                            menuOpen = false
                            reactionOpen = true
                        }
                    )
                }
                if (msg.kind == MsgKind.VIDEO && msg.filePath != null) {
                    // The system player stays available here now
                    // that a tap opens the in-app player (J3).
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        text = { Text("用其他播放器打开") },
                        onClick = {
                            menuOpen = false
                            openFileWithSystem(
                                context, msg.filePath!!, msg.fileMime
                            )
                        }
                    )
                }
                if (canGetViewers) {
                    // Read-by list (v0.9.13, T1): who in this
                    // group/channel has seen my message. The
                    // server refuses where it cannot answer
                    // (too old, privacy); ViewersStore turns
                    // that into a plain sentence in the dialog.
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(EyeIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        text = { Text("谁看过") },
                        onClick = {
                            menuOpen = false
                            viewersOpen = true
                            ViewersStore.load(chatId, msg.id)
                        }
                    )
                }
                if (msg.sendingState.isEmpty()) {
                    // Pin / unpin (v0.9.4, N3). The server
                    // refuses without rights; the store turns
                    // that into a plain sentence.
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(PinIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        text = { Text(if (msg.isPinned) "取消置顶" else "置顶") },
                        onClick = {
                            menuOpen = false
                            MessageStore.clearSendError()
                            if (msg.isPinned) {
                                MessageStore.unpinMessage(msg)
                            } else {
                                MessageStore.pinMessage(msg)
                            }
                        }
                    )
                }
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    text = { Text("多选") },
                    onClick = {
                        menuOpen = false
                        onEnterSelection(msg)
                    }
                )
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(ForwardIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    text = { Text("转发") },
                    onClick = {
                        menuOpen = false
                        onForward(listOf(msg))
                    }
                )
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(20.dp))
                    },
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
    if (viewersOpen) {
        // Read-by list (v0.9.13, T1): members who have seen
        // this message, newest view first; the store's error
        // text covers the scenes the server will not answer.
        val viewersLoading by ViewersStore.loading.collectAsState()
        val viewers by ViewersStore.viewers.collectAsState()
        val viewersError by ViewersStore.error.collectAsState()
        AlertDialog(
            onDismissRequest = { viewersOpen = false },
            title = { Text("谁看过这条消息") },
            text = {
                when {
                    viewersLoading -> Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("正在查…", fontSize = 14.sp)
                    }
                    viewersError != null -> Text(
                        viewersError!!, fontSize = 14.sp
                    )
                    viewers.isEmpty() -> Text(
                        "还没有人看过这条消息。", fontSize = 14.sp
                    )
                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 380.dp)
                    ) {
                        items(viewers, key = { it.userId }) { viewer ->
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                            ) {
                                if (viewer.photoPath != null) {
                                    AvatarImage(
                                        path = viewer.photoPath,
                                        sizeDp = 36
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                Color(0xFFB0BEC5),
                                                CircleShape
                                            ),
                                        contentAlignment =
                                            Alignment.Center
                                    ) {
                                        Text(
                                            viewer.name.trim()
                                                .firstOrNull()
                                                ?.toString() ?: "?",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    viewer.name.ifEmpty { "…" },
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                Text(
                                    formatViewDate(viewer.viewDate),
                                    fontSize = 12.sp, color = Color.Gray
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewersOpen = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

/** Translation block under a bubble (v0.9.5, O4): a
 *  "正在翻译…" placeholder while MessageStore waits on
 *  translateMessageText, then the translated text under a
 *  hairline with a 译文 marker and a collapse button. Shared
 *  by the main chat bubble and the thread/forum bubble. */
@Composable
fun TranslationArea(chatId: Long, msg: MsgItem) {
    val translations by MessageStore.translations.collectAsState()
    val translating by MessageStore.translating.collectAsState()
    val key = chatId to msg.id
    val t = translations[key]
    if (key in translating) {
        Text(
            "正在翻译…",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    } else if (t != null) {
        Column(modifier = Modifier.padding(top = 5.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x22000000))
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "译文 · ${t.langLabel}",
                    color = Color(0xFF5B7C99),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { MessageStore.hideTranslation(chatId, msg.id) }
                ) {
                    Text("收起", fontSize = 11.sp)
                }
            }
            Text(t.text, fontSize = 14.sp)
        }
    }
}

/** One-shot dialog for a translation refusal (v0.9.5, O4):
 *  the store already turned the server error into plain
 *  language (Premium requirement, secret chat, ...). Placed
 *  once at each screen root that offers 翻译. */
@Composable
fun TranslateErrorDialog() {
    val error by MessageStore.translateError.collectAsState()
    if (error != null) {
        AlertDialog(
            onDismissRequest = { MessageStore.clearTranslateError() },
            title = { Text("翻译") },
            text = { Text(error!!) },
            confirmButton = {
                TextButton(
                    onClick = { MessageStore.clearTranslateError() }
                ) { Text("知道了") }
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
/** One-line summary of a message for the pinned bar and
 *  the pinned list (v0.9.4, N3): its text, or the standard
 *  bracket label for media kinds. */
fun messageSummaryText(msg: MsgItem): String =
    msg.text.ifEmpty {
        when (msg.kind) {
            MsgKind.PHOTO -> "[图片]"
            MsgKind.VIDEO -> "[视频]"
            MsgKind.VOICE -> "[语音]"
            MsgKind.DOCUMENT -> "[文件] ${msg.fileName}"
            MsgKind.STICKER -> "[贴纸]"
            MsgKind.ANIMATION -> "[动图]"
            MsgKind.POLL -> "[投票] ${msg.poll?.question ?: ""}"
            MsgKind.LOCATION -> "[位置]"
            MsgKind.CONTACT -> "[联系人] ${msg.contactName}"
            MsgKind.VIDEO_NOTE -> "[视频消息]"
            MsgKind.DICE -> "[骰子] ${msg.diceEmoji}"
            MsgKind.GAME -> "[游戏] ${msg.gameTitle}"
            else -> "[消息]"
        }
    }

/** One coordinate, five decimals — plenty for a shared pin
 *  and short enough to read (v0.9.4, N2). */
fun formatCoord(value: Double): String =
    String.format(java.util.Locale.US, "%.5f", value)

/** One-shot location from the system LocationManager
 *  (v0.9.4, N2) — deliberately no Play services dependency.
 *  A fresh-enough last-known fix wins; otherwise a single
 *  update is requested with a 12 s timeout. Null = no fix
 *  (providers off, permission missing, or timeout), which
 *  the UI reports honestly instead of sending anything. */
fun fetchOneLocation(
    context: android.content.Context,
    onResult: (Pair<Double, Double>?) -> Unit
) {
    try {
        val manager = context.getSystemService(
            android.content.Context.LOCATION_SERVICE
        ) as android.location.LocationManager
        val providers = listOf(
            android.location.LocationManager.GPS_PROVIDER,
            android.location.LocationManager.NETWORK_PROVIDER
        ).filter { provider ->
            try {
                manager.isProviderEnabled(provider)
            } catch (t: Throwable) {
                false
            }
        }
        if (providers.isEmpty()) {
            onResult(null)
            return
        }
        val now = System.currentTimeMillis()
        val last = providers.mapNotNull { provider ->
            try {
                manager.getLastKnownLocation(provider)
            } catch (t: Throwable) {
                null
            }
        }.maxByOrNull { it.time }
        if (last != null && now - last.time < 5 * 60 * 1000) {
            onResult(last.latitude to last.longitude)
            return
        }
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var settled = false
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                if (!settled) {
                    settled = true
                    onResult(location.latitude to location.longitude)
                }
            }
        }
        mainHandler.postDelayed({
            if (!settled) {
                settled = true
                try {
                    manager.removeUpdates(listener)
                } catch (t: Throwable) {
                }
                onResult(last?.let { it.latitude to it.longitude })
            }
        }, 12_000)
        @Suppress("DEPRECATION")
        manager.requestSingleUpdate(
            providers.first(), listener, android.os.Looper.getMainLooper()
        )
    } catch (t: Throwable) {
        onResult(null)
    }
}

/** Read (phone, first name, last name) for a contact picked
 *  from the system address book; null when the contact has
 *  no phone number (v0.9.4, N2). */
fun readPickedContact(
    context: android.content.Context,
    uri: Uri
): Triple<String, String, String>? {
    return try {
        var contactId = ""
        var displayName = ""
        context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME
            ),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                contactId = cursor.getString(0) ?: ""
                displayName = cursor.getString(1) ?: ""
            }
        }
        if (contactId.isEmpty()) return null
        var phone = ""
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                phone = cursor.getString(0) ?: ""
            }
        }
        if (phone.isBlank()) {
            null
        } else {
            val first = displayName.substringBefore(' ').ifEmpty { displayName }
            val last = displayName.substringAfter(' ', "")
            Triple(phone, first, last)
        }
    } catch (t: Throwable) {
        null
    }
}

/** Location card (v0.9.4, N2): a static map-like tile with
 *  the pin and coordinates — no map SDK in the app; tapping
 *  hands the coordinates to the system map app via a geo:
 *  intent. */
@Composable
fun LocationBubbleContent(msg: MsgItem) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(208.dp)
            .clickable {
                val uri = Uri.parse(
                    "geo:${msg.latitude},${msg.longitude}" +
                        "?q=${msg.latitude},${msg.longitude}"
                )
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (t: Throwable) {
                    Toast.makeText(
                        context, "这台手机上没有能打开地图的应用。",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .background(Color(0xFFD9EAD3), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("📍", fontSize = 36.sp)
        }
        Spacer(Modifier.height(5.dp))
        Text("位置", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "${formatCoord(msg.latitude)}, ${formatCoord(msg.longitude)}",
            color = Color.Gray, fontSize = 12.sp
        )
        Text(
            "点开在地图里看",
            color = MaterialTheme.colorScheme.primary, fontSize = 12.sp
        )
    }
}

/** Contact card (v0.9.4, N2): name + phone, a profile entry
 *  when the card carries a Telegram user id, and a save
 *  entry that hands the contact to the system address book
 *  editor (ACTION_INSERT) instead of writing silently. */
@Composable
fun ContactBubbleContent(msg: MsgItem) {
    val context = LocalContext.current
    Column(modifier = Modifier.width(208.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primary, CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    msg.contactName.take(1).ifEmpty { "?" },
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(9.dp))
            Column {
                Text(
                    msg.contactName.ifEmpty { "联系人" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(msg.contactPhone, color = Color.Gray, fontSize = 13.sp)
            }
        }
        Row {
            if (msg.contactUserId != 0L) {
                TextButton(onClick = {
                    DeepLink.pendingProfileUser.value = msg.contactUserId
                }) {
                    Text("查看资料", fontSize = 13.sp)
                }
            }
            TextButton(onClick = {
                val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                    type = ContactsContract.RawContacts.CONTENT_TYPE
                    putExtra(
                        ContactsContract.Intents.Insert.NAME, msg.contactName
                    )
                    putExtra(
                        ContactsContract.Intents.Insert.PHONE, msg.contactPhone
                    )
                }
                try {
                    context.startActivity(intent)
                } catch (t: Throwable) {
                    Toast.makeText(
                        context, "这台手机上没有通讯录应用。",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }) {
                Text("存到通讯录", fontSize = 13.sp)
            }
        }
    }
}

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
            withContext(Dispatchers.IO) { decodeCached(path) }
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


/** Decode a minithumbnail's inline bytes (base64, as
 *  TDLib's JSON interface ships bytes fields). */
private fun decodeMiniThumb(data: String): android.graphics.Bitmap? {
    return try {
        val bytes = android.util.Base64.decode(data, android.util.Base64.DEFAULT)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (t: Throwable) {
        null
    }
}

/** Round video message (v0.9.14, U4): a circle-cropped
 *  thumbnail with the duration on it; the tap (handled by
 *  the bubble's click chain) plays it in the in-app player
 *  once the video file is down, or starts the download. */
@Composable
fun VideoNoteBubbleContent(msg: MsgItem) {
    val thumb = msg.thumbPath
    val mini = msg.miniThumbData
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, thumb, mini
    ) {
        value = when {
            thumb != null ->
                withContext(Dispatchers.IO) { decodeCached(thumb) }
            mini.isNotEmpty() ->
                withContext(Dispatchers.IO) { decodeMiniThumb(mini) }
            else -> null
        }
    }
    Box(
        modifier = Modifier
            .size(200.dp)
            .clip(CircleShape)
            .background(Color(0xFF37474F)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "视频消息",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0x66000000)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (msg.filePath != null) {
                    Icons.Filled.PlayArrow
                } else {
                    DownloadIcon
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
                .background(Color(0x66000000), RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(
                formatDurationClock(msg.durationSec),
                color = Color.White, fontSize = 11.sp
            )
        }
    }
}

/** Dice message (v0.9.14, U5): the final state's static
 *  sticker when it decodes (animated .tgs files cannot —
 *  then the big emoji is the face), with the rolled value
 *  under it. A value of 0 means the throw has no result
 *  yet and renders as an ellipsis. */
@Composable
fun DiceBubbleContent(msg: MsgItem) {
    val path = msg.filePath
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) { decodeCached(path) }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = msg.diceEmoji,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(120.dp)
            )
        } else {
            Text(
                msg.diceEmoji.ifEmpty { "🎲" },
                fontSize = 56.sp
            )
        }
        Text(
            if (msg.diceValue > 0) {
                "点数 ${msg.diceValue}"
            } else {
                "…"
            },
            fontSize = 13.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** Game message (v0.9.14, U5): cover, title, description
 *  and a start button. Starting rides the same callback
 *  the message's own game button uses
 *  (answerCallbackGame); the bot's answer opens as a link
 *  or shows as text/a dialog, like other bot answers. */
@Composable
fun GameBubbleContent(msg: MsgItem) {
    val context = LocalContext.current
    var alertText by remember { mutableStateOf<String?>(null) }
    val path = msg.photoPath
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) { decodeCached(path) }
        }
    }
    Column(modifier = Modifier.width(220.dp)) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = msg.gameTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 220.dp, height = 124.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            msg.gameTitle.ifEmpty { "游戏" },
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (msg.gameDescription.isNotEmpty()) {
            Text(
                msg.gameDescription,
                fontSize = 12.sp,
                color = Color.Gray,
                maxLines = 3,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Button(
            onClick = {
                MessageStore.answerCallbackGame(
                    MessageStore.currentChatId, msg.id,
                    msg.gameShortName,
                    onAnswer = { text, showAlert, url ->
                        when {
                            url.isNotEmpty() ->
                                com.tuxungram.android.td.LinkRouter
                                    .open(context, url)
                            showAlert && text.isNotEmpty() ->
                                alertText = text
                            text.isNotEmpty() -> Toast.makeText(
                                context, text, Toast.LENGTH_SHORT
                            ).show()
                            else -> Toast.makeText(
                                context, "游戏没有打开。",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onError = {
                        Toast.makeText(
                            context, it, Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("开始游戏")
        }
    }
    val alert = alertText
    if (alert != null) {
        AlertDialog(
            onDismissRequest = { alertText = null },
            title = { Text(msg.gameTitle.ifEmpty { "游戏" }) },
            text = { Text(alert) },
            confirmButton = {
                TextButton(onClick = { alertText = null }) {
                    Text("好")
                }
            }
        )
    }
}

private fun formatDurationClock(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

/** Sticker image (webp decoded straight from the TDLib file);
 *  shown at a fixed chat-friendly size, no bubble chrome. */
@Composable
fun StickerBubbleContent(msg: MsgItem) {
    val path = msg.filePath
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) { decodeCached(path) }
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
fun AnimationBubbleContent(msg: MsgItem) {
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
                        Icon(
                            Icons.Filled.Check, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
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
fun LinkPreviewCard(msg: MsgItem) {
    val context = LocalContext.current
    val photoPath = msg.lpPhotoPath
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, photoPath
    ) {
        value = if (photoPath == null) {
            null
        } else {
            withContext(Dispatchers.IO) { decodeCached(photoPath, 2048) }
        }
    }
    Column(
        modifier = Modifier
            .width(240.dp)
            .background(Color(0x0D000000), RoundedCornerShape(8.dp))
            .clickable {
                try {
                    com.tuxungram.android.td.LinkRouter
                        .open(context, msg.lpUrl)
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
    // Sticker discovery (v0.9.4, N5): trending sets row,
    // emoji search, and a whole-set preview dialog.
    val trending by MediaStore.trendingSets.collectAsState()
    val searchResults by MediaStore.searchResults.collectAsState()
    val previewStickers by MediaStore.previewStickers.collectAsState()
    var searchText by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var previewSet by remember { mutableStateOf<TrendingSetUi?>(null) }
    var previewAdded by remember { mutableStateOf(false) }
    LaunchedEffect(previewSet) {
        val set = previewSet
        if (set != null) {
            previewAdded = false
            MediaStore.loadPreviewSet(set.id)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(288.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp)) {
            for ((index, label) in listOf("表情", "贴纸", "GIF").withIndex()) {
                TextButton(
                    onClick = { tab = index },
                    enabled = tab != index
                ) {
                    TabDotText(tab == index, label)
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
                // Emoji search (N5): type an emoji, get the
                // matching stickers from Telegram's catalog,
                // tap one to send it directly.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        placeholder = { Text("输入 emoji 搜贴纸，如 😂") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        if (searchText.isNotBlank()) {
                            searchActive = true
                            MediaStore.searchByEmoji(searchText)
                        }
                    }) {
                        Text("搜索")
                    }
                    if (searchActive) {
                        TextButton(onClick = {
                            searchActive = false
                            searchText = ""
                            MediaStore.clearSearch()
                        }) {
                            Text("清除")
                        }
                    }
                }
                if (searchActive) {
                    if (searchResults.isEmpty()) {
                        Text(
                            "没搜到贴纸，换个 emoji 再试试。",
                            color = Color.Gray, fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            gridItems(
                                searchResults, key = { it.fileId }
                            ) { entry ->
                                MediaPickerCell(entry) { onSendEntry(entry) }
                            }
                        }
                    }
                    return@Column
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp)
                ) {
                    TextButton(onClick = { setSel = null }) {
                        TabDotText(setSel == null, "最近")
                    }
                    TextButton(onClick = { setSel = -1L }) {
                        TabDotText(setSel == -1L, "收藏")
                    }
                    for (set in sets) {
                        TextButton(onClick = {
                            setSel = set.id
                            MediaStore.loadSet(set.id)
                        }) {
                            TabDotText(setSel == set.id, set.title)
                        }
                    }
                }
                if (setSel == null && trending.isNotEmpty()) {
                    // Trending discovery row (N5): horizontal
                    // covers; tapping one previews the whole
                    // set with a one-tap add.
                    Text(
                        "热门贴纸包",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(
                            start = 16.dp, top = 2.dp
                        )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        for (set in trending) {
                            TrendingSetCell(set) { previewSet = set }
                            Spacer(Modifier.width(6.dp))
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
    // Trending set preview (v0.9.4, N5): the whole set in a
    // grid — tap a sticker to send it — plus a one-tap add
    // that installs the set through the G3 route.
    val preview = previewSet
    if (preview != null) {
        AlertDialog(
            onDismissRequest = { previewSet = null },
            title = { Text(preview.title) },
            text = {
                Column {
                    if (preview.isInstalled || previewAdded) {
                        Text(
                            "已在你的贴纸里",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    } else {
                        Button(onClick = {
                            StickerSetStore.installById(preview.id) { ok ->
                                if (ok) previewAdded = true
                            }
                        }) {
                            Text("添加这个贴纸包（${preview.count} 张）")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (previewStickers.isEmpty()) {
                        Text(
                            "正在加载这一包贴纸…",
                            color = Color.Gray, fontSize = 13.sp
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(264.dp)
                        ) {
                            gridItems(
                                previewStickers, key = { it.fileId }
                            ) { entry ->
                                MediaPickerCell(entry) {
                                    onSendEntry(entry)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { previewSet = null }) {
                    Text("关闭")
                }
            }
        )
    }
}

/** One trending set cover in the discovery row (v0.9.4,
 *  N5): cover sticker (or the title's first character while
 *  it downloads) plus the set title underneath. */
@Composable
private fun TrendingSetCell(set: TrendingSetUi, onClick: () -> Unit) {
    val path = set.thumbPath
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) { decodeCached(path) }
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(60.dp)
            .clickable { onClick() }
            .padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = set.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    set.title.take(1).ifEmpty { "?" },
                    fontSize = 22.sp, color = Color.Gray
                )
            }
        }
        Text(
            set.title,
            fontSize = 10.sp,
            maxLines = 1,
            color = Color.Gray
        )
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
            withContext(Dispatchers.IO) { decodeCached(path) }
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

/**
 * Voice-note playback (v0.9.2, L5): one MediaPlayer for the
 * whole app, keyed by the voice file's path. Speed comes from
 * UiSettings (one global 1x/1.5x/2x choice) and is applied
 * through playbackParams — while preparing and while playing;
 * a paused player picks the new speed up on resume, which
 * sidesteps the ROMs that reject param changes mid-pause.
 */
object VoicePlayer {
    private var player: android.media.MediaPlayer? = null
    private val handler =
        android.os.Handler(android.os.Looper.getMainLooper())

    private val _activePath = MutableStateFlow<String?>(null)
    val activePath: StateFlow<String?> = _activePath

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    private val _positionMs = MutableStateFlow(0)
    val positionMs: StateFlow<Int> = _positionMs

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs

    private val ticker = object : Runnable {
        override fun run() {
            val p = player
            if (p != null && _playing.value) {
                try {
                    _positionMs.value = p.currentPosition
                } catch (t: Throwable) {
                    // Player died under us; the next toggle
                    // restarts it.
                }
                handler.postDelayed(this, 200)
            }
        }
    }

    fun toggle(path: String) {
        if (player != null && _activePath.value == path) {
            if (_playing.value) pause() else resume()
        } else {
            start(path)
        }
    }

    private fun start(path: String) {
        releasePlayer()
        try {
            val p = android.media.MediaPlayer()
            p.setDataSource(path)
            p.setOnCompletionListener {
                _playing.value = false
                _positionMs.value = 0
                try {
                    it.seekTo(0)
                } catch (t: Throwable) {
                }
            }
            p.setOnPreparedListener {
                _durationMs.value = it.duration
                applySpeed(it)
                it.start()
                _playing.value = true
                handler.removeCallbacks(ticker)
                handler.post(ticker)
            }
            p.prepareAsync()
            player = p
            _activePath.value = path
        } catch (t: Throwable) {
            releasePlayer()
        }
    }

    private fun pause() {
        try {
            player?.pause()
        } catch (t: Throwable) {
        }
        _playing.value = false
    }

    private fun resume() {
        val p = player ?: return
        try {
            applySpeed(p)
            p.start()
            _playing.value = true
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        } catch (t: Throwable) {
        }
    }

    /** Seek by waveform fraction (tap position on the bars). */
    fun seekToFraction(fraction: Float) {
        val p = player ?: return
        val dur = _durationMs.value
        if (dur <= 0) return
        val target = (dur * fraction.coerceIn(0f, 1f)).toInt()
        try {
            p.seekTo(target)
            _positionMs.value = target
        } catch (t: Throwable) {
        }
    }

    /** Cycle 1x -> 1.5x -> 2x; persisted globally, applied to
     *  the live player when it is playing. */
    fun cycleSpeed() {
        val speeds = UiSettings.VOICE_SPEEDS
        val current = UiSettings.voiceSpeed.value
        val next = speeds[(speeds.indexOf(current) + 1) % speeds.size]
        UiSettings.setVoiceSpeed(next)
        val p = player
        if (p != null && _playing.value) applySpeed(p)
    }

    private fun applySpeed(p: android.media.MediaPlayer) {
        try {
            p.playbackParams =
                p.playbackParams.setSpeed(UiSettings.voiceSpeed.value)
        } catch (t: Throwable) {
            // Some ROMs reject params in odd states; playback
            // continues at the old speed rather than failing.
        }
    }

    private fun releasePlayer() {
        handler.removeCallbacks(ticker)
        try {
            player?.release()
        } catch (t: Throwable) {
        }
        player = null
        _playing.value = false
        _positionMs.value = 0
        _durationMs.value = 0
        _activePath.value = null
    }
}

/** Voice-note bubble (v0.9.2, L5): play/pause, the waveform
 *  with tap-to-seek, and the speed chip while this voice is
 *  the active one. An undownloaded voice keeps the file-row
 *  behavior (tap downloads) via FileBubbleContent. */
@Composable
fun VoiceBubbleContent(msg: MsgItem) {
    val path = msg.filePath
    if (path == null) {
        FileBubbleContent(msg)
        return
    }
    val activePath by VoicePlayer.activePath.collectAsState()
    val playing by VoicePlayer.playing.collectAsState()
    val positionMs by VoicePlayer.positionMs.collectAsState()
    val durationMs by VoicePlayer.durationMs.collectAsState()
    val speed by UiSettings.voiceSpeed.collectAsState()
    val active = activePath == path
    val totalMs = if (active && durationMs > 0) {
        durationMs
    } else {
        msg.durationSec * 1000
    }
    val fraction = if (active && totalMs > 0) {
        positionMs.toFloat() / totalMs
    } else {
        0f
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(Color(0xFF5B7C99), CircleShape)
                .clickable { VoicePlayer.toggle(path) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (active && playing) "❚❚" else "▶",
                color = Color.White, fontSize = 14.sp
            )
        }
        Spacer(Modifier.width(9.dp))
        Column {
            WaveformBars(
                waveform = msg.waveform,
                fraction = fraction,
                seekable = active
            ) { f -> VoicePlayer.seekToFraction(f) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (active) {
                        "${formatDuration(positionMs / 1000)} / " +
                            formatDuration(totalMs / 1000)
                    } else {
                        formatDuration(msg.durationSec)
                    },
                    color = Color.Gray, fontSize = 12.sp
                )
                if (active) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                Color(0x14000000), RoundedCornerShape(8.dp)
                            )
                            .clickable { VoicePlayer.cycleSpeed() }
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "${trimSpeed(speed)}x",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private fun trimSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) {
        speed.toInt().toString()
    } else {
        speed.toString()
    }

/** The waveform as a row of bars, resampled from TDLib's
 *  amplitudes; the played share is tinted. Tapping seeks
 *  (only meaningful for the active voice). */
@Composable
private fun WaveformBars(
    waveform: List<Int>,
    fraction: Float,
    seekable: Boolean,
    onSeek: (Float) -> Unit
) {
    val bars = 36
    val heights = remember(waveform) {
        if (waveform.isEmpty()) {
            List(bars) { 0.35f }
        } else {
            List(bars) { i ->
                val from = i * waveform.size / bars
                val to = maxOf(from + 1, (i + 1) * waveform.size / bars)
                var maxV = 0
                for (j in from until minOf(to, waveform.size)) {
                    maxV = maxOf(maxV, waveform[j])
                }
                0.15f + (maxV / 31f) * 0.85f
            }
        }
    }
    Row(
        modifier = Modifier
            .height(30.dp)
            .pointerInput(seekable) {
                if (seekable) {
                    detectTapGestures { offset ->
                        if (size.width > 0) {
                            onSeek(offset.x / size.width)
                        }
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEachIndexed { i, h ->
            val played = (i + 0.5f) / bars <= fraction
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((30 * h).dp)
                    .background(
                        if (played) Color(0xFF5B7C99) else Color(0xFFBDBDBD),
                        RoundedCornerShape(2.dp)
                    )
            )
            if (i < bars - 1) Spacer(Modifier.width(2.dp))
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

/** One row of the inline-results panel (v0.9.12, S2): the
 *  result's thumbnail (resolved through AvatarStore's generic
 *  file map, decoded through the shared cache) or a per-kind
 *  icon, then title + description. GIF results carry a small
 *  GIF tag like the message bubbles do. */
@Composable
private fun InlineResultRow(
    result: InlineResultUi,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val thumbPath = if (result.thumbFileId != 0) {
            AvatarStore.pathForFile(result.thumbFileId)
        } else {
            null
        }
        val bitmap by produceState<android.graphics.Bitmap?>(
            null, thumbPath
        ) {
            value = thumbPath?.let {
                withContext(Dispatchers.IO) { decodeCached(it, 144) }
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Icon(
                    // Fallback glyphs come from the core icon
                    // set + the hand-drawn AppIcons: Description
                    // / Image / MusicNote live only in the
                    // extended set this app does not depend on.
                    when (result.kind) {
                        "photo", "animation", "sticker" ->
                            ImageIcon
                        "video" -> Icons.Filled.PlayArrow
                        "audio" -> ClipIcon
                        "voiceNote" -> MicIcon
                        "contact" -> Icons.Filled.Person
                        "location", "venue" ->
                            Icons.Filled.LocationOn
                        "game" -> Icons.Filled.Face
                        "article" -> LinkIcon
                        else -> ClipIcon
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (result.kind == "animation") {
                Text(
                    "GIF",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(
                            Color.Black.copy(alpha = 0.55f),
                            RoundedCornerShape(3.dp)
                        )
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.title.ifEmpty { "（无标题）" },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (result.description.isNotEmpty()) {
                Text(
                    result.description,
                    fontSize = 12.5.sp,
                    color = Color.Gray,
                    maxLines = 1
                )
            }
        }
    }
}
