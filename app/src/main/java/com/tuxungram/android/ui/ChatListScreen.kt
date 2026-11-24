package com.tuxungram.android.ui


import androidx.compose.ui.text.style.TextOverflow
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import com.tuxungram.android.td.AppLock
import com.tuxungram.android.td.AuthState
import com.tuxungram.android.td.ChatItem
import com.tuxungram.android.td.ChatStore
import com.tuxungram.android.td.LocalLabels
import com.tuxungram.android.td.MessageStore
import com.tuxungram.android.td.MsgItem
import com.tuxungram.android.td.MsgKind
import com.tuxungram.android.td.ProxyConfig
import com.tuxungram.android.td.ProxyStore
import androidx.compose.foundation.border
import com.tuxungram.android.td.ContactsStore
import com.tuxungram.android.td.FolderStore
import com.tuxungram.android.td.SearchHit
import com.tuxungram.android.td.SearchStore
import com.tuxungram.android.td.StoryStore
import com.tuxungram.android.td.TdRepository
import com.tuxungram.android.td.UiSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay


/** The Saved Messages entry row (v0.9.1, J5): a star badge,
 *  「收藏」 title and a one-line description. Shared by the
 *  chat list's pinned entry and the forward picker's top row. */
@Composable
fun SavedMessagesRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    MaterialTheme.colorScheme.primary, CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Star, contentDescription = null,
                tint = Color.White, modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "收藏",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
            Text(
                "发给自己的消息和文件",
                color = Color.Gray,
                fontSize = 13.sp,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        }
    }
}

/** Chat-list-as-picker for forwarding one message. Stays open until
 *  TDLib answers, so a failure is shown here instead of vanishing. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ForwardPickerScreen(
    fromChatId: Long,
    messageIds: List<Long>,
    onDone: () -> Unit
) {
    val chats by ChatStore.chats.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Forward options (v0.9.31, X4): forwardMessages' own
    // send_copy / remove_caption flags (verified in td_api.tl).
    // remove_caption is ignored by the server unless send_copy
    // is set, so its switch disables itself while copy mode is
    // off instead of pretending to work alone.
    var sendCopy by remember { mutableStateOf(false) }
    var removeCaption by remember { mutableStateOf(false) }
    val pickerListState = rememberLazyListState()
    // Saved Messages pinned at the top (v0.9.1, J5): forwarding
    // to oneself is the most common forward target.
    var savedChatId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        ContactsStore.openSavedMessages { savedChatId = it }
    }
    fun forwardTo(targetId: Long) {
        busy = true
        error = null
        MessageStore.forwardMessages(
            fromChatId, messageIds, targetId,
            sendCopy = sendCopy, removeCaption = removeCaption
        ) { failure ->
            if (failure == null) {
                onDone()
            } else {
                busy = false
                error = failure
            }
        }
    }
    BackHandler { if (!busy) onDone() }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (!busy) onDone() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("转发到…", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        if (!busy) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("隐藏原发送者", fontSize = 14.5.sp)
                    Text(
                        "以复制方式发送，不显示来源",
                        color = Color.Gray, fontSize = 12.sp
                    )
                }
                Switch(
                    checked = sendCopy,
                    onCheckedChange = { sendCopy = it }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "去掉原说明文字",
                        fontSize = 14.5.sp,
                        color = if (sendCopy) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            Color.Gray
                        }
                    )
                    Text(
                        if (sendCopy) {
                            "媒体的说明文字不带过去"
                        } else {
                            "需先打开「隐藏原发送者」"
                        },
                        color = Color.Gray, fontSize = 12.sp
                    )
                }
                Switch(
                    checked = removeCaption && sendCopy,
                    onCheckedChange = { removeCaption = it },
                    enabled = sendCopy
                )
            }
        }
        if (error != null) {
            Text(
                error!!, color = Color(0xFFC62828), fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        if (busy) {
            CenterColumn {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在转发…", color = Color.Gray)
            }
        } else {
            LazyColumn(
                state = pickerListState,
                modifier = Modifier
                    .fillMaxSize()
                    .lazyListScrollbar(pickerListState)
            ) {
                val savedId = savedChatId
                if (savedId != null) {
                    item(key = "saved_messages") {
                        SavedMessagesRow(onClick = { forwardTo(savedId) })
                    }
                }
                items(items = chats, key = { it.id }) { chat ->
                    Box(
                        modifier = Modifier.animateItemPlacement(
                            Motion.tween(Motion.LIST_PLACEMENT)
                        )
                    ) {
                        ChatRow(chat, onClick = { forwardTo(chat.id) })
                    }
                }
            }
        }
    }
}

/** Chat-list-as-picker for content shared INTO the app from
 *  another app's share sheet (v0.9.52, W5). Mirrors the
 *  forward picker's shape — Saved Messages pinned on top,
 *  the same rows — and stays open until TDLib answers, so a
 *  failure is shown here instead of vanishing. On success
 *  [onSent] opens the target chat so the user sees the
 *  content land. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SharePickerScreen(
    content: com.tuxungram.android.td.SharedContent,
    onCancel: () -> Unit,
    onSent: (Long) -> Unit
) {
    val chats by ChatStore.chats.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pickerListState = rememberLazyListState()
    var savedChatId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        ContactsStore.openSavedMessages { savedChatId = it }
    }
    fun sendTo(targetId: Long) {
        busy = true
        error = null
        com.tuxungram.android.td.MessageStore.sendSharedTo(
            targetId, content.text, content.files
        ) { failure ->
            if (failure == null) {
                onSent(targetId)
            } else {
                busy = false
                error = failure
            }
        }
    }
    BackHandler { if (!busy) onCancel() }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (!busy) onCancel() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "取消"
                )
            }
            Text(
                "分享到…",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            when {
                content.files.isEmpty() -> "一段文字"
                content.text.isEmpty() ->
                    "${content.files.size} 个文件"
                else ->
                    "${content.files.size} 个文件 + 一段文字"
            },
            color = Color.Gray,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
        if (error != null) {
            Text(
                error!!,
                color = Color(0xFFC62828),
                fontSize = 14.sp,
                modifier = Modifier.padding(
                    horizontal = 16.dp, vertical = 6.dp
                )
            )
        }
        if (busy) {
            CenterColumn {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在发送…", color = Color.Gray)
            }
        } else {
            LazyColumn(
                state = pickerListState,
                modifier = Modifier
                    .fillMaxSize()
                    .lazyListScrollbar(pickerListState)
            ) {
                val savedId = savedChatId
                if (savedId != null) {
                    item(key = "saved_messages") {
                        SavedMessagesRow(onClick = { sendTo(savedId) })
                    }
                }
                items(items = chats, key = { it.id }) { chat ->
                    Box(
                        modifier = Modifier.animateItemPlacement(
                            Motion.tween(Motion.LIST_PLACEMENT)
                        )
                    ) {
                        ChatRow(chat, onClick = { sendTo(chat.id) })
                    }
                }
            }
        }
    }
}

fun formatChatTime(dateSeconds: Int): String {
    if (dateSeconds <= 0) return ""
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply {
        timeInMillis = dateSeconds * 1000L
    }
    val sameDay = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    val yesterday = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_YEAR, -1)
    }
    val isYesterday = yesterday.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        yesterday.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameDay -> com.tuxungram.android.td.TimeFmt.timeOfDay(
            dateSeconds.toLong()
        )
        isYesterday -> "昨天"
        now.timeInMillis - then.timeInMillis < 7L * 24 * 3600 * 1000 ->
            java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(then.time)
        now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) ->
            java.text.SimpleDateFormat("M/d", java.util.Locale.getDefault()).format(then.time)
        else -> java.text.SimpleDateFormat("yyyy/M/d", java.util.Locale.getDefault())
            .format(then.time)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
/** Duration in seconds of a local video file, 0 when
 *  unreadable — the story publish path's metadata read,
 *  used for picked videos and again after trimming
 *  (v0.9.55, Z4). */
private fun storyVideoDurationSec(path: String): Double {
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        (retriever.extractMetadata(
            android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
        )?.toLongOrNull() ?: 0L) / 1000.0
    } catch (t: Throwable) {
        0.0
    } finally {
        try {
            retriever.release()
        } catch (t: Throwable) {
            // Nothing to release cleanly; the duration
            // just stays 0.
        }
    }
}

fun ChatListScreen(
    onOpenChat: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChatLocated: (Long, Long) -> Unit,
    onOpenArchive: () -> Unit,
    onOpenSavedTopics: () -> Unit,
    onOpenCreate: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenStory: (Long) -> Unit,
    onOpenContacts: () -> Unit,
    onOpenSearch: () -> Unit,
    // Hoisted by the host (v0.9.9, W4) so switching bottom-nav
    // tabs away and back keeps the list scroll position.
    listState: LazyListState = rememberLazyListState()
) {
    val chats by ChatStore.chats.collectAsState()
    val allLoaded by ChatStore.allLoaded.collectAsState()
    // Archive entry row (v0.9.19, Z1): the official list-top
    // archive row shows only when something is archived, so
    // the archive list loads with this screen now (it used
    // to load only when the archive page itself opened).
    val archived by ChatStore.archivedChats.collectAsState()
    LaunchedEffect(Unit) { ChatStore.loadArchived() }
    val folders by ChatStore.folders.collectAsState()
    val folderUnread by ChatStore.folderUnread.collectAsState()
    val allUnreadChats by ChatStore.allUnreadChats.collectAsState()
    val selectedFolder by ChatStore.selectedFolder.collectAsState()
    val folderChats by ChatStore.folderChats.collectAsState()
    val folderLoaded by ChatStore.folderLoaded.collectAsState()
    // Swipe actions (v0.9.2, L3): the chat id whose row is
    // currently swiped open, so only one row shows its action
    // panel at a time.
    var swipeOpenId by remember { mutableStateOf<Long?>(null) }

    // Pin notices (v0.9.23, P1): a pin that landed locally or
    // hit the total cap explains itself once, as a Toast —
    // the StoryStore.postNotice pattern.
    val pinNoticeContext = LocalContext.current
    val pinNotice by ChatStore.pinNotice.collectAsState()
    LaunchedEffect(pinNotice) {
        val notice = pinNotice
        if (notice != null) {
            Toast.makeText(pinNoticeContext, notice, Toast.LENGTH_LONG)
                .show()
            ChatStore.clearPinNotice()
        }
    }

    // Search lives on its own full-screen page since v0.9.19
    // (Z4); this screen only ever shows the plain list now.
    val baseList = if (selectedFolder == null) chats else folderChats
    // Local label filter (v0.9.25, R5): a purely local lens —
    // picking a label narrows the list to chats carrying it
    // on THIS phone. Labels never reach the server.
    val localLabels by LocalLabels.labels.collectAsState()
    val labelAssignments by LocalLabels.assignments.collectAsState()
    var activeLabelId by remember { mutableStateOf<Long?>(null) }
    val activeLabel = localLabels.firstOrNull { it.id == activeLabelId }
    val shown = if (activeLabelId != null) {
        val withLabel = LocalLabels.chatsWith(activeLabelId!!)
        baseList.filter { it.id in withLabel }
    } else {
        baseList
    }

    LaunchedEffect(listState, shown.size, folderLoaded, selectedFolder) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastVisible ->
            if (selectedFolder != null && !folderLoaded &&
                shown.isNotEmpty() && lastVisible >= shown.size - 5
            ) {
                ChatStore.loadFolderMore()
            }
        }
    }

    LaunchedEffect(listState, shown.size, allLoaded) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastVisible ->
            if (!allLoaded && shown.isNotEmpty() && lastVisible >= shown.size - 5) {
                ChatStore.loadMore()
            }
        }
    }

    // Root is a Box since v0.9.19 (Z1) so the official
    // pencil FAB can float over the list's bottom end.
    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Official single-row top bar (v0.9.19, Z1): drawer
        // button, title, search icon, overflow — the old
        // three-part header (big title + text-link row +
        // resident search box) is gone. Its entries moved,
        // none dropped: create -> drawer / overflow / FAB,
        // contacts -> drawer / Duck bar, archive -> list-top
        // row / overflow, settings -> drawer / Duck bar /
        // overflow (entry map in the checkpoint).
        val topPalette = OfficialTheme.current()
        var topMenuOpen by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier.background(topPalette.actionBarBg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        Icons.Filled.Menu, contentDescription = "菜单",
                        tint = topPalette.actionBarIcon
                    )
                }
                Text(
                    "TuxunGram",
                    color = topPalette.actionBarTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        SearchIcon, contentDescription = "搜索",
                        tint = topPalette.actionBarIcon
                    )
                }
                Box {
                    IconButton(onClick = { topMenuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "更多",
                            tint = topPalette.actionBarIcon
                        )
                    }
                    DropdownMenu(
                        expanded = topMenuOpen,
                        onDismissRequest = { topMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("新建群组") },
                            onClick = {
                                topMenuOpen = false
                                onOpenCreate("group")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("新建频道") },
                            onClick = {
                                topMenuOpen = false
                                onOpenCreate("channel")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("归档") },
                            onClick = {
                                topMenuOpen = false
                                onOpenArchive()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("设置") },
                            onClick = {
                                topMenuOpen = false
                                onOpenSettings()
                            }
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(topPalette.divider)
            )
        }
        // Folder tabs (B7 + editor): selecting one swaps the
        // list below to that folder's chats; long-press opens
        // the editor (rename / members / delete), and the
        // trailing button creates a new folder. Since v0.9.19
        // (Z3) these are the official text tabs — accent
        // semibold label with an animated underline — not
        // pills; the archive is not a tab (it has its own
        // list-top row, Z1), as in the official client.
        var folderEditTarget by remember { mutableStateOf<Int?>(null) }
        var folderCreateOpen by remember { mutableStateOf(false) }
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                FolderTab(
                    label = "全部",
                    selected = selectedFolder == null,
                    badge = allUnreadChats,
                    onClick = { ChatStore.selectFolder(null) }
                )
                for (folder in folders) {
                    FolderTab(
                        label = folder.name.ifEmpty { "文件夹" },
                        selected = selectedFolder == folder.id,
                        badge = folderUnread[folder.id] ?: 0,
                        onClick = { ChatStore.selectFolder(folder.id) },
                        onLongClick = { folderEditTarget = folder.id }
                    )
                }
                IconButton(
                    onClick = { folderCreateOpen = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.Add, contentDescription = "新建文件夹",
                        tint = OfficialTheme.current().textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(OfficialTheme.current().divider)
            )
        }
        if (folderCreateOpen) {
            FolderEditorDialog(
                folderId = null,
                initialName = "",
                onDismiss = { folderCreateOpen = false }
            )
        }
        val editTarget = folderEditTarget
        if (editTarget != null) {
            FolderEditorDialog(
                folderId = editTarget,
                initialName = folders.firstOrNull { it.id == editTarget }
                    ?.name ?: "",
                onDismiss = { folderEditTarget = null }
            )
        }
        // Local label filter chips (v0.9.25, R5): shown only
        // when the user has made at least one label. The row
        // title states the active filter so a narrowed list
        // never looks like lost chats.
        if (localLabels.isNotEmpty() && selectedFolder == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (activeLabel != null) {
                        "本地标签「${activeLabel.name}」"
                    } else {
                        "本地标签"
                    },
                    fontSize = 12.sp, color = Color.Gray
                )
                Spacer(Modifier.width(8.dp))
                for (label in localLabels) {
                    val selected = activeLabelId == label.id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme
                                        .primaryContainer
                                } else {
                                    MaterialTheme.colorScheme
                                        .surfaceVariant
                                },
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                activeLabelId =
                                    if (selected) null else label.id
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(
                                    Color(
                                        LocalLabels.PALETTE[
                                            label.colorIndex
                                        ]
                                    ),
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(label.name, fontSize = 12.sp)
                    }
                }
            }
        }
        // First-sync banner: while TDLib is still pulling the
        // account's chats in, say so plainly — a half-filled list
        // must not look like chats were lost.
        val syncing by ChatStore.syncing.collectAsState()
        if (syncing && shown.isNotEmpty() && selectedFolder == null) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text("正在同步全部对话…", color = Color.Gray, fontSize = 12.sp)
            }
        }
        // Story publishing (v0.8.0, G5): pick one photo, confirm
        // (the dialog states the privacy plainly), then
        // StoryStore runs canPostStory -> postStory. Results
        // come back as one-line notices, shown as toasts.
        val storyContext = LocalContext.current
        var pendingStoryPhoto by remember { mutableStateOf<String?>(null) }
        // Video stories (v0.9.26, S3): the picked file's path
        // plus its duration in seconds (inputStoryContentVideo
        // needs it; read from the file's own metadata).
        var pendingStoryVideo by remember {
            mutableStateOf<Pair<String, Double>?>(null)
        }
        // In-app camera for stories (v0.9.55, Z2): the
        // chooser's 相机 option opens it; a taken photo
        // fills pendingStoryPhoto like the picker does.
        var storyCameraOpen by remember { mutableStateOf(false) }
        // Story video trimming (v0.9.55, Z4): a picked
        // video detours through the shared trim screen
        // before it becomes pendingStoryVideo.
        var storyTrimPath by remember { mutableStateOf<String?>(null) }
        var storyKindChooser by remember { mutableStateOf(false) }
        val storyPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                try {
                    val file = File(
                        storyContext.cacheDir,
                        "story_${System.currentTimeMillis()}.jpg"
                    )
                    storyContext.contentResolver.openInputStream(uri)
                        ?.use { inputStream ->
                            file.outputStream().use { output ->
                                inputStream.copyTo(output)
                            }
                        }
                    pendingStoryPhoto = file.absolutePath
                } catch (t: Throwable) {
                    // Picker copy failed: stay silent, nothing posted.
                }
            }
        }
        val storyVideoPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                try {
                    val file = File(
                        storyContext.cacheDir,
                        "story_${System.currentTimeMillis()}.mp4"
                    )
                    storyContext.contentResolver.openInputStream(uri)
                        ?.use { inputStream ->
                            file.outputStream().use { output ->
                                inputStream.copyTo(output)
                            }
                        }
                    // Trim first (v0.9.55, Z4): the copied
                    // video goes through the shared trim
                    // screen; whichever path comes back
                    // (trimmed, or the original for non-MP4 /
                    // back-out) becomes pendingStoryVideo
                    // with a freshly read duration.
                    storyTrimPath = file.absolutePath
                } catch (t: Throwable) {
                    // Picker copy failed: stay silent, nothing posted.
                }
            }
        }
        if (storyKindChooser) {
            AlertDialog(
                onDismissRequest = { storyKindChooser = false },
                title = { Text("发布动态") },
                text = {
                    Column {
                        Text(
                            "选照片、视频，或者直接用相机拍。" +
                                "视频会先过一遍剪辑页，可以不剪直接发。"
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            storyKindChooser = false
                            storyCameraOpen = true
                        }) { Text("用相机拍") }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        storyKindChooser = false
                        AppLock.noteExternalLaunch()
                        storyPicker.launch("image/*")
                    }) { Text("照片") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        storyKindChooser = false
                        AppLock.noteExternalLaunch()
                        storyVideoPicker.launch("video/*")
                    }) { Text("视频") }
                }
            )
        }
        if (storyCameraOpen) {
            // In-app camera (v0.9.55, Z2): full-screen
            // dialog window, same host shape as the chat
            // screen's camera.
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { storyCameraOpen = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                CameraScreen(
                    onTaken = { path ->
                        storyCameraOpen = false
                        pendingStoryPhoto = path
                    },
                    onCancel = { storyCameraOpen = false }
                )
            }
        }
        val trimPath = storyTrimPath
        if (trimPath != null) {
            // Story video trim (v0.9.55, Z4): the shared
            // trim screen in a full-screen dialog. onDone
            // brings the trimmed file; onCancel is the
            // trim screen's "back / send the original"
            // path (non-MP4 explanation included), so the
            // original becomes the pending video either
            // way the user leaves — the publish confirm
            // dialog still gets the final say.
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { storyTrimPath = null },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                VideoTrimScreen(
                    path = trimPath,
                    fileName = File(trimPath).name,
                    onDone = { newPath ->
                        storyTrimPath = null
                        pendingStoryVideo =
                            newPath to storyVideoDurationSec(newPath)
                    },
                    onCancel = {
                        storyTrimPath = null
                        pendingStoryVideo =
                            trimPath to storyVideoDurationSec(trimPath)
                    }
                )
            }
        }
        val postNotice by StoryStore.postNotice.collectAsState()
        LaunchedEffect(postNotice) {
            val notice = postNotice
            if (notice != null) {
                Toast.makeText(storyContext, notice, Toast.LENGTH_SHORT)
                    .show()
                StoryStore.clearPostNotice()
            }
        }
        if (pendingStoryPhoto != null || pendingStoryVideo != null) {
            // Publish privacy (v0.9.25, R3): who can see the
            // story is chosen here and passed to postStory's
            // privacy_settings; the default stays everyone.
            var storyPrivacy by remember(
                pendingStoryPhoto, pendingStoryVideo
            ) {
                mutableStateOf("everyone")
            }
            // "Specified users" tier (v0.9.27, T3): the
            // picked contacts ride to postStory as
            // storyPrivacySettingsSelectedUsers.
            var storySelectedUsers by remember(
                pendingStoryPhoto, pendingStoryVideo
            ) {
                mutableStateOf<Set<Long>>(emptySet())
            }
            var storyPickerOpen by remember(
                pendingStoryPhoto, pendingStoryVideo
            ) {
                mutableStateOf(false)
            }
            val privacyOptions = listOf(
                "everyone" to "所有人",
                "contacts" to "我的联系人",
                "close_friends" to "亲密朋友",
                "selected" to "指定用户"
            )
            AlertDialog(
                onDismissRequest = {
                    pendingStoryPhoto = null
                    pendingStoryVideo = null
                },
                title = { Text("发布动态？") },
                text = {
                    Column {
                        Text(
                            (if (pendingStoryVideo != null) {
                                "这段视频会发到你的动态，"
                            } else {
                                "这张照片会发到你的动态，"
                            }) +
                                "24 小时后自动归档。谁能看："
                        )
                        Spacer(Modifier.height(6.dp))
                        for ((value, label) in privacyOptions) {
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        storyPrivacy = value
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                RadioButton(
                                    selected = storyPrivacy == value,
                                    onClick = { storyPrivacy = value }
                                )
                                Text(label, fontSize = 14.sp)
                            }
                        }
                        if (storyPrivacy == "selected") {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        storyPickerOpen = true
                                    }
                                    .padding(vertical = 6.dp)
                            ) {
                                Text(
                                    if (storySelectedUsers.isEmpty()) {
                                        "点这里选能看的人（还没选）"
                                    } else {
                                        "已选 ${storySelectedUsers.size} 人" +
                                            "，点这里改"
                                    },
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme
                                        .primary
                                )
                            }
                            if (storySelectedUsers.isEmpty()) {
                                Text(
                                    "一个人都没选时不能发布，先选至少一位。",
                                    fontSize = 12.5.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = storyPrivacy != "selected" ||
                            storySelectedUsers.isNotEmpty(),
                        onClick = {
                            val photoPath = pendingStoryPhoto
                            val video = pendingStoryVideo
                            val picked = storySelectedUsers.toList()
                            pendingStoryPhoto = null
                            pendingStoryVideo = null
                            if (video != null) {
                                StoryStore.postVideoStory(
                                    video.first, video.second,
                                    storyPrivacy, picked
                                )
                            } else if (photoPath != null) {
                                StoryStore.postPhotoStory(
                                    photoPath, storyPrivacy, picked
                                )
                            }
                        }
                    ) { Text("发布") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pendingStoryPhoto = null
                        pendingStoryVideo = null
                    }) {
                        Text("取消")
                    }
                }
            )
            if (storyPickerOpen) {
                StoryUserPickerDialog(
                    initial = storySelectedUsers,
                    onDismiss = { storyPickerOpen = false },
                    onConfirm = { picked ->
                        storySelectedUsers = picked
                        storyPickerOpen = false
                    }
                )
            }
        }
        // Stories row (Batch 3, C5): one ring per chat with
        // active stories; colored ring = unread, gray = seen.
        // The first cell is the publish entry (G5) — the row
        // shows even when nobody has an active story, so the
        // entry is always reachable.
        val storyRings by StoryStore.rings.collectAsState()
        if (selectedFolder == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                // Publish entry (G5): a dashed-looking + cell,
                // always first in the row.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(end = 14.dp)
                        .clickable {
                            storyKindChooser = true
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .border(
                                2.5.dp,
                                MaterialTheme.colorScheme.primary,
                                CircleShape
                            )
                            .padding(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(
                                    MaterialTheme.colorScheme
                                        .primaryContainer,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "发布动态",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        "发动态",
                        fontSize = 11.sp,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        modifier = Modifier.width(60.dp)
                    )
                }
                for (ring in storyRings) {
                    val item = ChatStore.itemOf(ring.chatId)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(end = 14.dp)
                            .clickable { onOpenStory(ring.chatId) }
                    ) {
                        Box(
                            modifier = Modifier
                                .border(
                                    2.5.dp,
                                    if (ring.hasUnread) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color(0xFFBDBDBD)
                                    },
                                    CircleShape
                                )
                                .padding(3.dp)
                        ) {
                            if (item?.photoPath != null) {
                                AvatarImage(path = item.photoPath, sizeDp = 52)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(
                                            Color(0xFF90CAF9), CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        item?.title?.trim()
                                            ?.firstOrNull()?.toString()
                                            ?: "?",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 19.sp
                                    )
                                }
                            }
                        }
                        Text(
                            item?.title ?: "",
                            fontSize = 11.sp,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            modifier = Modifier.width(60.dp)
                        )
                    }
                }
            }
        }
        // Saved Messages pinned entry (v0.9.1, J5): always at
        // the top of the main list, resolving the self chat on
        // tap (getMe -> createPrivateChat with one's own id).
        if (selectedFolder == null) {
            SavedMessagesRow(onClick = {
                ContactsStore.openSavedMessages { chatId ->
                    if (chatId != null) onOpenChat(chatId)
                }
            })
            // Saved Messages topics (v0.9.25, R1): the topics
            // Telegram auto-creates inside Saved Messages.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSavedTopics() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "收藏话题",
                    fontSize = 14.sp,
                    color = OfficialTheme.current().accentText
                )
                Text(
                    "按来源自动归类的收藏",
                    fontSize = 12.sp, color = Color.Gray,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
        if (shown.isEmpty()) {
            CenterColumn {
                if (selectedFolder != null && !folderLoaded) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在加载文件夹…", color = Color.Gray)
                } else if (selectedFolder != null) {
                    EmptyState(
                        FolderIcon, "这个文件夹里还没有对话",
                        "把对话移进这个文件夹后会出现在这里。"
                    )
                } else if (!allLoaded) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在加载对话…", color = Color.Gray)
                } else {
                    EmptyState(
                        Icons.AutoMirrored.Filled.Send, "还没有对话",
                        "在 Telegram 里有聊天后，这里会自动出现。"
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .lazyListScrollbar(listState)
            ) {
                if (selectedFolder == null && archived.isNotEmpty()) {
                    item(key = "archive_entry") {
                        ArchiveEntryRow(
                            // Muted chats stay out of the
                            // counter (v0.9.52, W4), the same
                            //口径 as the folder tab badges.
                            unread = archived
                                .filter { !it.isMuted }
                                .sumOf { it.unreadCount },
                            onClick = onOpenArchive
                        )
                    }
                }
                items(
                    items = shown,
                    key = { it.id }
                ) { chat ->
                    // Placement animation (v0.9.11, A1): rows
                    // glide to their new position when a pin,
                    // an archive or a fresh message reorders
                    // the list instead of teleporting.
                    Box(
                        modifier = Modifier.animateItemPlacement(
                            Motion.tween(Motion.LIST_PLACEMENT)
                        )
                    ) {
                        SwipeableChatRow(
                            chat = chat,
                            inArchive = false,
                            openId = swipeOpenId,
                            onOpenChange = { swipeOpenId = it },
                            onClick = { onOpenChat(chat.id) }
                        )
                    }
                }
            }
        }
    }
    // Official pencil FAB (v0.9.19, Z1): floats over the
    // list's bottom end — this screen fills the host's
    // weighted box above the Duck bar, so BottomEnd here
    // sits just above the bar, no overlap. It opens the
    // create screen (group/channel is chosen there); the
    // drawer carries the two direct entries. Shape is a
    // true circle (v0.9.22, AC1b): the official compose
    // button is round — the Material default FAB shape
    // (16 dp corners) read as a rounded square on device.
    // Size/elevation stay the Material defaults (56 dp /
    // 6 dp), matching the official button's footprint.
    FloatingActionButton(
        onClick = { onOpenCreate("group") },
        shape = CircleShape,
        containerColor = OfficialTheme.current().accent,
        contentColor = Color.White,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(16.dp)
    ) {
        Icon(Icons.Filled.Edit, contentDescription = "新建")
    }
    }
}

/** The archive entry at the top of the main list (v0.9.19,
 *  Z1), the official placement: a gray archive glyph in a
 *  circle, 「归档」, and the archived unread total when any.
 *  Shown only while at least one chat is archived. */
@Composable
fun ArchiveEntryRow(unread: Int, onClick: () -> Unit) {
    val palette = OfficialTheme.current()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(palette.graySection, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                ArchiveIcon, contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "归档",
            color = palette.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.weight(1f))
        if (unread > 0) {
            val cap by UiSettings.unreadCap.collectAsState()
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .defaultMinSize(minWidth = 20.dp)
                    .background(
                        palette.unreadBadge, RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (unread > cap) "$cap+" else unread.toString(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** One global-search hit: which chat, a content summary, when.
 *  Tapping opens the chat located at that message. */
@Composable
fun SearchHitRow(hit: SearchHit, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                hit.chatTitle.ifEmpty { "对话" },
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatChatTime(hit.dateSeconds),
                color = Color.Gray, fontSize = 12.sp
            )
        }
        Text(
            hit.summary,
            color = Color.Gray, fontSize = 14.sp, maxLines = 2
        )
    }
}

/** One action cell behind a swiped chat row (v0.9.2, L3). */
data class SwipeAction(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val bg: Color,
    val run: () -> Unit
)

/**
 * A chat row with swipe-revealed actions (v0.9.2, L3), the
 * Nekogram / Telegram X pattern: swipe right for archive +
 * read/unread, swipe left for pin + mute — the same actions
 * the long-press menu offers, so gestures and menu never
 * disagree. The panel stays open until an action is tapped,
 * the row itself is tapped, or another row opens. In the
 * archive list the archive action becomes unarchive and the
 * pin action is trimmed in THIS row widget, per the
 * context-trimming rule — the archive screen builds its own
 * swipe panel and carries the pin action there (v0.9.23, P1).
 */
@Composable
fun SwipeableChatRow(
    chat: ChatItem,
    inArchive: Boolean,
    openId: Long?,
    onOpenChange: (Long?) -> Unit,
    onClick: () -> Unit
) {
    val hasUnread = chat.unreadCount > 0 || chat.isMarkedAsUnread
    val leftActions = listOf(
        if (inArchive) {
            SwipeAction("取消归档", ArchiveIcon, Color(0xFF78909C)) {
                ChatStore.unarchive(chat)
            }
        } else {
            SwipeAction("归档", ArchiveIcon, Color(0xFF78909C)) {
                ChatStore.archive(chat)
            }
        },
        if (hasUnread) {
            SwipeAction("标已读", Icons.Filled.Check, Color(0xFF5B7C99)) {
                ChatStore.markRead(chat)
            }
        } else {
            SwipeAction("标未读", Icons.Filled.MailOutline, Color(0xFF5B7C99)) {
                ChatStore.markUnread(chat)
            }
        }
    )
    val rightActions = if (inArchive) {
        listOf(
            SwipeAction(
                if (chat.isMuted) "取消静音" else "静音",
                MuteBellIcon, Color(0xFF7E57C2)
            ) { ChatStore.toggleMute(chat) }
        )
    } else {
        listOf(
            SwipeAction(
                if (chat.isPinned || chat.isLocalPinned) {
                    "取消置顶"
                } else {
                    "置顶"
                },
                PinIcon, Color(0xFF8D6E63)
            ) { ChatStore.togglePin(chat) },
            SwipeAction(
                if (chat.isMuted) "取消静音" else "静音",
                MuteBellIcon, Color(0xFF7E57C2)
            ) { ChatStore.toggleMute(chat) }
        )
    }
    SwipeActionsContainer(
        leftActions = leftActions,
        rightActions = rightActions,
        openKey = chat.id,
        openId = openId,
        onOpenChange = onOpenChange
    ) {
        ChatRow(chat, onClick = onClick)
    }
}

/** The drag/reveal machinery behind [SwipeableChatRow]:
 *  action panels sit behind an opaque foreground that follows
 *  the finger horizontally and springs open past 45% of a
 *  panel's width, or back shut below it. */
@Composable
fun SwipeActionsContainer(
    leftActions: List<SwipeAction>,
    rightActions: List<SwipeAction>,
    openKey: Long,
    openId: Long?,
    onOpenChange: (Long?) -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val anim = remember(openKey) { Animatable(0f) }
    val cellWidth = 76.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val leftPx = with(density) { (cellWidth * leftActions.size).toPx() }
    val rightPx = with(density) { (cellWidth * rightActions.size).toPx() }
    val isOpen = openId == openKey
    LaunchedEffect(openId) {
        if (!isOpen && anim.value != 0f) {
            anim.animateTo(0f)
        }
    }
    fun close() {
        scope.launch { anim.animateTo(0f) }
        if (isOpen) onOpenChange(null)
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.matchParentSize()) {
            Row {
                for (action in leftActions) {
                    SwipeActionCell(action, cellWidth) { close() }
                }
            }
            Spacer(Modifier.weight(1f))
            Row {
                for (action in rightActions) {
                    SwipeActionCell(action, cellWidth) { close() }
                }
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(anim.value.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(openKey) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            if (openId != null && openId != openKey) {
                                onOpenChange(null)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val next = (anim.value + dragAmount)
                                .coerceIn(-rightPx, leftPx)
                            scope.launch { anim.snapTo(next) }
                        },
                        onDragEnd = {
                            val v = anim.value
                            when {
                                v > leftPx * 0.45f -> {
                                    scope.launch { anim.animateTo(leftPx) }
                                    onOpenChange(openKey)
                                }
                                v < -rightPx * 0.45f -> {
                                    scope.launch { anim.animateTo(-rightPx) }
                                    onOpenChange(openKey)
                                }
                                else -> close()
                            }
                        },
                        onDragCancel = { close() }
                    )
                }
        ) {
            content()
        }
        // While open, a tap on the row closes the panel
        // instead of opening the chat.
        if (isOpen) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset { IntOffset(anim.value.roundToInt(), 0) }
                    .clickable { close() }
            )
        }
    }
}

@Composable
private fun SwipeActionCell(
    action: SwipeAction,
    width: androidx.compose.ui.unit.Dp,
    onDone: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(action.bg)
            .clickable {
                action.run()
                onDone()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                action.icon, contentDescription = null,
                tint = Color.White, modifier = Modifier.size(22.dp)
            )
            Text(action.label, color = Color.White, fontSize = 11.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatRow(chat: ChatItem, onClick: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var labelDialogOpen by remember { mutableStateOf(false) }
    var folderDialogOpen by remember { mutableStateOf(false) }
    // Label dots data (v0.9.28, U4): the color indexes of
    // this chat's local labels, resolved only when the
    // Appearance switch for them is on.
    val labelDotsOn by UiSettings.labelDots.collectAsState()
    val allAssignments by LocalLabels.assignments.collectAsState()
    val rowLabelDots = if (labelDotsOn) {
        (allAssignments[chat.id] ?: emptyList())
            .mapNotNull { LocalLabels.labelOf(it)?.colorIndex }
            .take(3)
    } else {
        emptyList()
    }
    // Official list layout (v0.9.17, X3; metrics
    // re-measured v0.9.22, S2): 52 dp avatar,
    // official placeholder gradient (avatar_background*),
    // online dot in chats_onlineCircle at the avatar's
    // bottom-right, hairline divider starting after the
    // avatar (the official dialogs separator inset).
    val palette = OfficialTheme.current()
    val avatarPair = OfficialTheme.avatarColors(chat.id, OfficialTheme.dark)
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true }
                )
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Box {
            if (chat.photoPath != null) {
                AvatarImage(path = chat.photoPath, sizeDp = 52)
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(avatarPair.first, avatarPair.second)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        chat.title.trim().firstOrNull()?.toString() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }
            if (chat.isOnline) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(15.dp)
                        .border(
                            2.dp,
                            palette.windowBg,
                            CircleShape
                        )
                        .background(palette.onlineDot, CircleShape)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chat.isSecret) {
                    Icon(
                        Icons.Filled.Lock, contentDescription = "秘密聊天",
                        tint = Color(0xFF43A047),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    chat.title,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (chat.isChannel) {
                    Spacer(Modifier.width(6.dp))
                    Text("频道", color = Color.Gray, fontSize = 11.sp)
                }
                if (chat.isMuted) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        MuteBellIcon, contentDescription = "已静音",
                        tint = Color.Gray, modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    formatChatTime(chat.dateSeconds),
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (rowLabelDots.isNotEmpty()) {
                    // Local label dots (v0.9.28, U4): up to
                    // three quiet 8dp dots ahead of the preview.
                    for (colorIndex in rowLabelDots) {
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(8.dp)
                                .background(
                                    Color(
                                        LocalLabels.PALETTE[colorIndex]
                                    ),
                                    CircleShape
                                )
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                }
                if (chat.draftText.isNotEmpty()) {
                    // Draft preview (v0.9.16, W2): a red
                    // 草稿： prefix plus the draft text replaces
                    // the last-message preview (and its media
                    // thumb) while a draft exists.
                    Text(
                        "草稿：",
                        // chats_draft (v0.9.17, X3 recalibration).
                        color = palette.draftRed,
                        fontSize = 16.sp,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                    Text(
                        chat.draftText,
                        color = palette.textSecondary,
                        fontSize = 16.sp,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    if (chat.previewThumbPath != null) {
                        MediaThumbChip(chat.previewThumbPath)
                        Spacer(Modifier.width(6.dp))
                    }
                    if (chat.previewSenderName.isNotEmpty()) {
                        // Sender prefix (v0.9.18, Y4): the
                        // official name color for the sender,
                        // the preview body stays secondary gray.
                        Text(
                            "${chat.previewSenderName}：",
                            color = OfficialTheme.nameColor(
                                chat.previewSenderId,
                                OfficialTheme.dark
                            ),
                            fontSize = 16.sp,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            modifier = Modifier.widthIn(max = 120.dp)
                        )
                    }
                    Text(
                        chat.preview,
                        color = palette.textSecondary,
                        fontSize = 16.sp,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (chat.isPinned || chat.isLocalPinned) {
                    Icon(
                        PinIcon,
                        contentDescription = if (chat.isLocalPinned) {
                            "本地置顶"
                        } else {
                            "置顶"
                        },
                        tint = palette.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    if (chat.isLocalPinned) {
                        // Local-pin marker (v0.9.23, P1): a
                        // small quiet label next to the pin so
                        // a pin that lives only on this phone
                        // is never mistaken for a server pin.
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "本地",
                            color = palette.textSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
                if (chat.unreadCount > 0 || chat.isMarkedAsUnread) {
                    Spacer(Modifier.width(6.dp))
                    // Unread capsule (v0.9.17, X3): official
                    // pill — chats_unreadCounter, gray
                    // (chats_unreadCounterMuted) when muted,
                    // min 20 dp, white semibold count.
                    Box(
                        modifier = Modifier
                            .height(20.dp)
                            .defaultMinSize(minWidth = 20.dp)
                            .background(
                                if (chat.isMuted) {
                                    palette.unreadBadgeMuted
                                } else {
                                    palette.unreadBadge
                                },
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val unreadCap by UiSettings.unreadCap.collectAsState()
                        Text(
                            when {
                                chat.unreadCount == 0 -> "•"
                                chat.unreadCount > unreadCap -> "$unreadCap+"
                                else -> chat.unreadCount.toString()
                            },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        }
        // Hairline divider (v0.9.17, X3): starts after the
        // avatar column (16 + 52 + 12 = 80 dp), official
        // divider color.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 80.dp)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(palette.divider)
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                leadingIcon = {
                    Icon(PinIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                text = {
                    Text(
                        if (chat.isPinned || chat.isLocalPinned) {
                            "取消置顶"
                        } else {
                            "置顶"
                        }
                    )
                },
                onClick = {
                    menuOpen = false
                    ChatStore.togglePin(chat)
                }
            )
            DropdownMenuItem(
                leadingIcon = {
                    Icon(MuteBellIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                text = { Text(if (chat.isMuted) "取消静音" else "静音") },
                onClick = {
                    menuOpen = false
                    ChatStore.toggleMute(chat)
                }
            )
            if (chat.unreadCount > 0 || chat.isMarkedAsUnread) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    text = { Text("标为已读") },
                    onClick = {
                        menuOpen = false
                        ChatStore.markRead(chat)
                    }
                )
            }
            DropdownMenuItem(
                leadingIcon = {
                    Icon(ArchiveIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                text = { Text("归档") },
                onClick = {
                    menuOpen = false
                    ChatStore.archive(chat)
                }
            )
            DropdownMenuItem(
                leadingIcon = {
                    Icon(FolderIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                text = { Text("添加到文件夹") },
                onClick = {
                    menuOpen = false
                    folderDialogOpen = true
                }
            )
            DropdownMenuItem(
                text = { Text("标签（本地）") },
                onClick = {
                    menuOpen = false
                    labelDialogOpen = true
                }
            )
        }
        if (folderDialogOpen) {
            FolderPickerDialog(
                chatId = chat.id,
                onDismiss = { folderDialogOpen = false }
            )
        }
        if (labelDialogOpen) {
            LabelPickerDialog(
                chatId = chat.id,
                onDismiss = { labelDialogOpen = false }
            )
        }
    }
}

/** Folder picker (v0.9.33, Z3): which chat folders this
 *  chat belongs to, as checkboxes. Membership comes from
 *  getChatListsToAddChat — a folder the chat can still be
 *  added to is one it is not in yet — so the initial state
 *  is the server's, never a guess; if that load fails the
 *  boxes stay disabled behind an honest error line. Adding
 *  goes through addChatToList; removing mirrors it through
 *  FolderStore.removeChatFromFolder (this tl has no
 *  removeChatFromList). Both directions are server state,
 *  so other devices see the same folders. */
@Composable
fun FolderPickerDialog(chatId: Long, onDismiss: () -> Unit) {
    val folders by ChatStore.folders.collectAsState()
    // folderId -> member; null while the state is unknown.
    var membership by remember(chatId) {
        mutableStateOf<Map<Int, Boolean>?>(null)
    }
    var loadFailed by remember(chatId) { mutableStateOf(false) }
    var notice by remember(chatId) { mutableStateOf<String?>(null) }
    LaunchedEffect(chatId) {
        FolderStore.loadAddableFolders(chatId) { addable ->
            if (addable == null) {
                loadFailed = true
            } else {
                membership = folders.associate { tab ->
                    tab.id to (tab.id !in addable)
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加到文件夹") },
        text = {
            // Scrollable body (v0.9.39, F4): the folder
            // rows are rendered inline, so a long folder
            // list could push the dialog's button off a
            // short screen — the column now scrolls with
            // a height cap instead.
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when {
                    folders.isEmpty() -> Text(
                        "还没有文件夹。在主页顶部的文件夹条" +
                            "末尾点「+」就能新建一个，建好后回到" +
                            "这里把对话加进去。",
                        fontSize = 13.sp, color = Color.Gray
                    )
                    loadFailed -> Text(
                        "文件夹状态没加载出来，先关掉重开试试。",
                        fontSize = 13.sp, color = Color.Gray
                    )
                    membership == null -> Text(
                        "正在加载…",
                        fontSize = 13.sp, color = Color.Gray
                    )
                    else -> folders.forEach { tab ->
                        val member = membership?.get(tab.id) == true
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val target = !member
                                    membership =
                                        (membership ?: emptyMap()) +
                                            (tab.id to target)
                                    val done: (String?) -> Unit =
                                        { error ->
                                            if (error != null) {
                                                membership =
                                                    (membership
                                                        ?: emptyMap()) +
                                                        (tab.id to member)
                                                notice = error
                                            } else {
                                                notice = null
                                            }
                                        }
                                    if (target) {
                                        FolderStore.addChatToFolder(
                                            chatId, tab.id, done
                                        )
                                    } else {
                                        FolderStore
                                            .removeChatFromFolder(
                                                chatId, tab.id, done
                                            )
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = member,
                                onCheckedChange = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                tab.name.ifEmpty { "文件夹" },
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                if (notice != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        notice!!,
                        fontSize = 12.sp, color = Color(0xFFC62828)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

/** Label colour disc (v0.9.46): the label's colour as a
 *  soft disc with a solid core, leading the label rows in
 *  the picker and manager dialogs — replacing the bare
 *  10 dp dot they used to lead with. */
@Composable
private fun LabelDisc(colorIndex: Int) {
    val color = Color(LocalLabels.PALETTE[colorIndex])
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(color.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
    }
}

/** Local label picker (v0.9.25, R5): checkboxes for every
 *  label plus create/delete. Everything here is local-only —
 *  the dialog says so in plain words, because Telegram's own
 *  chat labels are a server Business feature this does NOT
 *  touch. */
@Composable
fun LabelPickerDialog(chatId: Long, onDismiss: () -> Unit) {
    val labels by LocalLabels.labels.collectAsState()
    val assignments by LocalLabels.assignments.collectAsState()
    var newName by remember { mutableStateOf("") }
    var newColor by remember { mutableStateOf(5) }
    // Inline rename (v0.9.28, U4).
    var renamingId by remember { mutableStateOf(0L) }
    var renameText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本地标签") },
        text = {
            // Scrollable body (v0.9.39, F4): label rows
            // are rendered inline and labels are
            // user-created without a cap — cap the height
            // and scroll instead of overflowing.
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "标签只存在这台手机上，仅本机可见，" +
                        "不会同步到其他设备，也不是 Telegram " +
                        "的商务标签。",
                    fontSize = 12.sp, color = Color.Gray
                )
                Spacer(Modifier.height(8.dp))
                if (labels.isEmpty()) {
                    EmptyStateMini(
                        icon = PaletteIcon,
                        title = "还没有标签",
                        subtitle = "在下面建一个，就能给对话分类了。"
                    )
                }
                for (label in labels) {
                    val checked = LocalLabels.has(chatId, label.id)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LocalLabels.toggle(chatId, label.id)
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                LocalLabels.toggle(chatId, label.id)
                            }
                        )
                        LabelDisc(label.colorIndex)
                        Spacer(Modifier.width(10.dp))
                        if (renamingId == label.id) {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                enabled = renameText.isNotBlank(),
                                onClick = {
                                    LocalLabels.rename(
                                        label.id, renameText
                                    )
                                    renamingId = 0L
                                }
                            ) {
                                Text("保存", fontSize = 12.sp)
                            }
                        } else {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label.name, fontSize = 14.5.sp)
                                Text(
                                    "${assignments.values.count { label.id in it }} 个对话",
                                    fontSize = 11.5.sp,
                                    color = Color.Gray
                                )
                            }
                            IconButton(onClick = {
                                renamingId = label.id
                                renameText = label.name
                            }) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "改名",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        IconButton(onClick = {
                            LocalLabels.delete(label.id)
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新标签名，如：重要") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for ((index, argb) in
                        LocalLabels.PALETTE.withIndex()) {
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(if (newColor == index) 22.dp else 18.dp)
                                .background(Color(argb), CircleShape)
                                .clickable { newColor = index }
                        )
                    }
                }
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        LocalLabels.create(newName, newColor)
                        newName = ""
                    }
                ) { Text("新建标签") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}


/** Standalone label management (v0.9.28, U4), opened from
 *  Settings → Appearance: rename or delete labels without
 *  opening a chat first. Deleting removes only the label
 *  and its assignments — never a chat. Creating labels
 *  stays in the per-chat picker, where the new label can be
 *  applied immediately. */
@Composable
fun LabelManageDialog(onDismiss: () -> Unit) {
    val labels by LocalLabels.labels.collectAsState()
    val assignments by LocalLabels.assignments.collectAsState()
    var renamingId by remember { mutableStateOf(0L) }
    var renameText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本地标签管理") },
        text = {
            // Scrollable body (v0.9.39, F4): same inline
            // label rows as the picker dialog — height
            // capped and scrollable.
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "标签只存在这台手机上。改名立刻生效；" +
                        "删除只删标签本身，不会删掉任何对话。",
                    fontSize = 12.sp, color = Color.Gray
                )
                Spacer(Modifier.height(8.dp))
                if (labels.isEmpty()) {
                    EmptyStateMini(
                        icon = PaletteIcon,
                        title = "还没有标签",
                        subtitle = "长按任意对话，选「标签（本地）」可以新建。"
                    )
                }
                for (label in labels) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        LabelDisc(label.colorIndex)
                        Spacer(Modifier.width(10.dp))
                        if (renamingId == label.id) {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                enabled = renameText.isNotBlank(),
                                onClick = {
                                    LocalLabels.rename(
                                        label.id, renameText
                                    )
                                    renamingId = 0L
                                }
                            ) {
                                Text("保存", fontSize = 12.sp)
                            }
                        } else {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label.name, fontSize = 14.5.sp)
                                Text(
                                    "${assignments.values.count { label.id in it }} 个对话",
                                    fontSize = 11.5.sp,
                                    color = Color.Gray
                                )
                            }
                            IconButton(onClick = {
                                renamingId = label.id
                                renameText = label.name
                            }) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "改名",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        IconButton(onClick = {
                            LocalLabels.delete(label.id)
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}


/** The archive list (chatListArchive): chats the user archived
 *  from the main list live here; tapping opens the chat, and
 *  each row can move its chat back to the main list. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ArchiveScreen(onBack: () -> Unit, onOpenChat: (Long) -> Unit) {
    BackHandler { onBack() }
    val archived by ChatStore.archivedChats.collectAsState()
    val archiveLoaded by ChatStore.archiveLoaded.collectAsState()
    var swipeOpenId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) { ChatStore.loadArchived() }
    // Pin notices, same Toast pattern as the main list
    // (v0.9.23, P1): archive pins explain themselves too.
    val archivePinNoticeContext = LocalContext.current
    val archivePinNotice by ChatStore.pinNotice.collectAsState()
    LaunchedEffect(archivePinNotice) {
        val notice = archivePinNotice
        if (notice != null) {
            Toast.makeText(
                archivePinNoticeContext, notice, Toast.LENGTH_LONG
            ).show()
            ChatStore.clearPinNotice()
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("归档", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        if (archived.isEmpty()) {
            CenterColumn {
                if (!archiveLoaded) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在加载归档…", color = Color.Gray)
                } else {
                    EmptyState(
                        ArchiveIcon, "没有归档的对话",
                        "在对话列表长按一个对话可以把它归档到这里。"
                    )
                }
            }
        } else {
            val archiveListState = rememberLazyListState()
            LazyColumn(
                state = archiveListState,
                modifier = Modifier
                    .fillMaxSize()
                    .lazyListScrollbar(archiveListState)
            ) {
                items(items = archived, key = { it.id }) { chat ->
                    // Placement animation, matching the main
                    // list (v0.9.13, T6; 0.9.11 debt): the
                    // swipe container rides inside the same
                    // animating Box the main list uses.
                    Box(
                        modifier = Modifier.animateItemPlacement(
                            Motion.tween(Motion.LIST_PLACEMENT)
                        )
                    ) {
                    SwipeActionsContainer(
                        leftActions = listOf(
                            SwipeAction("取消归档", ArchiveIcon, Color(0xFF78909C)) {
                                ChatStore.unarchive(chat)
                            },
                            if (chat.unreadCount > 0 ||
                                chat.isMarkedAsUnread
                            ) {
                                SwipeAction("标已读", Icons.Filled.Check, Color(0xFF5B7C99)) {
                                    ChatStore.markRead(chat)
                                }
                            } else {
                                SwipeAction("标未读", Icons.Filled.MailOutline, Color(0xFF5B7C99)) {
                                    ChatStore.markUnread(chat)
                                }
                            }
                        ),
                        rightActions = listOf(
                            // Pinning in the archive (v0.9.23,
                            // P1): the archive list has its own
                            // server allowance and takes local
                            // pins the same way the main list
                            // does, so the action joins the
                            // swipe panel here.
                            SwipeAction(
                                if (chat.isPinned ||
                                    chat.isLocalPinned
                                ) {
                                    "取消置顶"
                                } else {
                                    "置顶"
                                },
                                PinIcon, Color(0xFF8D6E63)
                            ) {
                                ChatStore.togglePin(
                                    chat, archive = true
                                )
                            },
                            SwipeAction(
                                if (chat.isMuted) "取消静音" else "静音",
                                MuteBellIcon, Color(0xFF7E57C2)
                            ) { ChatStore.toggleMute(chat) }
                        ),
                        openKey = chat.id,
                        openId = swipeOpenId,
                        onOpenChange = { swipeOpenId = it }
                    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(chat.id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Same avatar treatment as the main
                        // list (v0.9.9, W5 parity): per-chat
                        // fallback colour and the online dot.
                        val hue = (chat.id % 8 + 8) % 8
                        val colors = listOf(
                            Color(0xFFEF9A9A), Color(0xFFF48FB1),
                            Color(0xFFCE93D8), Color(0xFF90CAF9),
                            Color(0xFF80CBC4), Color(0xFFA5D6A7),
                            Color(0xFFFFCC80), Color(0xFFBCAAA4)
                        )
                        Box {
                            if (chat.photoPath != null) {
                                AvatarImage(path = chat.photoPath, sizeDp = 48)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            colors[hue.toInt()],
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        chat.title.trim().firstOrNull()?.toString() ?: "?",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                            if (chat.isOnline) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(13.dp)
                                        .border(
                                            2.dp,
                                            MaterialTheme.colorScheme.background,
                                            CircleShape
                                        )
                                        .background(
                                            Color(0xFF4CAF50),
                                            CircleShape
                                        )
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    chat.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (chat.isMuted) {
                                    Spacer(Modifier.width(5.dp))
                                    Icon(
                                        MuteBellIcon,
                                        contentDescription = "已静音",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (chat.draftText.isNotEmpty()) {
                                    // Draft preview (v0.9.16,
                                    // W2), same as the main row.
                                    Text(
                                        "草稿：",
                                        color = Color(0xFFC62828),
                                        fontSize = 14.sp,
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 1
                                    )
                                    Text(
                                        chat.draftText,
                                        color = Color.Gray,
                                        fontSize = 14.sp,
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 1
                                    )
                                } else {
                                    if (chat.previewThumbPath != null) {
                                        MediaThumbChip(chat.previewThumbPath)
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    if (chat.previewSenderName.isNotEmpty()) {
                                        // Sender prefix (v0.9.18,
                                        // Y4), same as the main row.
                                        Text(
                                            "${chat.previewSenderName}：",
                                            color = OfficialTheme.nameColor(
                                                chat.previewSenderId,
                                                OfficialTheme.dark
                                            ),
                                            fontSize = 14.sp,
                                            overflow = TextOverflow.Ellipsis,
                                            maxLines = 1,
                                            modifier = Modifier.widthIn(
                                                max = 110.dp
                                            )
                                        )
                                    }
                                    Text(
                                        chat.preview,
                                        color = Color.Gray,
                                        fontSize = 14.sp,
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        if (chat.isPinned || chat.isLocalPinned) {
                            Icon(
                                PinIcon,
                                contentDescription = if (chat.isLocalPinned) {
                                    "本地置顶"
                                } else {
                                    "置顶"
                                },
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                            if (chat.isLocalPinned) {
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    "本地",
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        if (chat.unreadCount > 0) {
                            val cap by UiSettings.unreadCap.collectAsState()
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (chat.isMuted) {
                                            Color(0xFFBDBDBD)
                                        } else {
                                            Color(0xFF4CAF50)
                                        },
                                        CircleShape
                                    )
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (chat.unreadCount > cap) {
                                        "$cap+"
                                    } else {
                                        chat.unreadCount.toString()
                                    },
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        TextButton(onClick = { ChatStore.unarchive(chat) }) {
                            Text("取消归档")
                        }
                    }
                    }
                    }
                }
            }
        }
    }
}

/** One folder tab in the chat-list tab strip (v0.9.19,
 *  Z3): the official text tab — secondary-gray label, accent
 *  semibold when selected, and an accent underline that
 *  grows in on selection (durations run through Motion, so
 *  the system reduced-motion setting still wins). A
 *  long-press opens the folder editor, as the old pill
 *  chips did. Replaces FolderChip. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun FolderTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    // Unread-chat counter (v0.9.52, W4): the official folder
    // tab badge — number of chats with unread in the folder,
    // muted chats excluded (ChatStore.recomputeTabUnread).
    badge: Int = 0
) {
    val palette = OfficialTheme.current()
    // Selection underline progress (v0.9.22, AC1b): the
    // Z3 AnimatedVisibility version was invisible on
    // device — the tab strip is a horizontally scrolling
    // Row, which measures children with unbounded width,
    // so the underline's fillMaxWidth() collapsed to the
    // minimum constraint (0) and the bar was never seen.
    // Two fixes: the Column takes IntrinsicSize.Max width
    // (the label's own width — a finite constraint the
    // underline can actually fill), and the grow animation
    // is a graphicsLayer scale/alpha driven by state
    // instead of a size animation inside a wrap-content
    // parent.
    val underlineProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = Motion.tween(Motion.TAB_UNDERLINE),
        label = "folderTabUnderline"
    )
    Column(
        modifier = Modifier
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier.clickable { onClick() }
                }
            )
            .padding(horizontal = 14.dp)
            .width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = if (selected) {
                    palette.accent
                } else {
                    palette.textSecondary
                },
                fontSize = 15.sp,
                fontWeight = if (selected) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                // 12dp (v0.9.33, Z5): tab touch height lands at
                // ~47dp including the underline, up from ~43dp.
                modifier = Modifier.padding(vertical = 12.dp)
            )
            if (badge > 0) {
                Spacer(Modifier.width(5.dp))
                // Same pill as the chat rows' unread capsule
                // (X3): accent fill, white semibold count,
                // the user's badge cap applies.
                Box(
                    modifier = Modifier
                        .height(18.dp)
                        .defaultMinSize(minWidth = 18.dp)
                        .background(
                            palette.unreadBadge,
                            RoundedCornerShape(9.dp)
                        )
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        UiSettings.badgeText(badge),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        // Official indicator: accent, 3 dp, flush with the
        // strip's bottom edge (the hairline divider sits
        // directly below the strip).
        Box(modifier = Modifier.fillMaxWidth().height(3.dp)) {
            if (underlineProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = underlineProgress
                            alpha = underlineProgress
                            transformOrigin =
                                TransformOrigin(0.5f, 0.5f)
                        }
                        .background(
                            palette.accent, RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}

/**
 * Folder editor (B7 completion): create a folder or edit an
 * existing one — name plus the explicitly included chats.
 * Filter-style rules (contacts/groups/… flags) are preserved
 * on edit but not offered here; the dialog says so.
 */
@Composable
fun FolderEditorDialog(
    folderId: Int?,
    initialName: String,
    onDismiss: () -> Unit
) {
    val chats by ChatStore.chats.collectAsState()
    var name by remember { mutableStateOf(initialName) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Membership load state (regression fix R2, v0.9.6): 0 =
    // loading, 1 = loaded, 2 = failed. Saving is only possible
    // in state 1 — before the server's included list lands (or
    // after it failed to load), the selection here is not the
    // folder's real membership, and writing it back would wipe
    // members the user never touched. A brand-new folder has
    // nothing to load, so it starts in state 1.
    var loadState by remember {
        mutableStateOf(if (folderId == null) 1 else 0)
    }

    LaunchedEffect(folderId) {
        if (folderId != null) {
            FolderStore.loadIncluded(folderId) { included, ok ->
                if (ok) {
                    selected = included.toSet()
                    loadState = 1
                } else {
                    loadState = 2
                    error = "文件夹成员没加载出来，先别保存，关掉重开试试。"
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(if (folderId == null) "新建文件夹" else "编辑文件夹")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("文件夹名字") },
                    leadingIcon = {
                        Icon(FolderIcon, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "勾选要放进这个文件夹的对话：",
                    fontSize = 13.sp, color = Color.Gray
                )
                if (folderId != null) {
                    Text(
                        "按联系人/群组等规则自动包含的对话不在这里改，原样保留。",
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
                if (loadState == 0) {
                    Text(
                        "正在加载文件夹成员…",
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
                val editorListState = rememberLazyListState()
                LazyColumn(
                    state = editorListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .lazyListScrollbar(editorListState)
                ) {
                    items(items = chats, key = { it.id }) { chat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = loadState == 1) {
                                    selected = if (chat.id in selected) {
                                        selected - chat.id
                                    } else {
                                        selected + chat.id
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = chat.id in selected,
                                enabled = loadState == 1,
                                onCheckedChange = { checked ->
                                    selected = if (checked) {
                                        selected + chat.id
                                    } else {
                                        selected - chat.id
                                    }
                                }
                            )
                            Text(chat.title, fontSize = 14.sp, overflow = TextOverflow.Ellipsis, maxLines = 1)
                        }
                    }
                }
                if (error != null) {
                    Text(
                        error!!, color = Color(0xFFC62828),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (folderId != null) {
                    TextButton(
                        onClick = {
                            if (!confirmDelete) {
                                confirmDelete = true
                            } else if (!busy) {
                                busy = true
                                FolderStore.delete(folderId) { failure ->
                                    busy = false
                                    if (failure == null) {
                                        onDismiss()
                                    } else {
                                        error = failure
                                    }
                                }
                            }
                        }
                    ) {
                        Text(
                            if (confirmDelete) "再点一次确认删除" else "删除文件夹",
                            color = Color(0xFFC62828)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && name.isNotBlank() && loadState == 1,
                onClick = {
                    busy = true
                    error = null
                    val done: (String?) -> Unit = { failure ->
                        busy = false
                        if (failure == null) {
                            onDismiss()
                        } else {
                            error = failure
                        }
                    }
                    if (folderId == null) {
                        FolderStore.create(name.trim(), selected.toList(), done)
                    } else {
                        FolderStore.save(
                            folderId, name.trim(), selected.toList(), done
                        )
                    }
                }
            ) {
                Text(if (folderId == null) "创建" else "保存")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss
            ) { Text("取消") }
        }
    )
}

/** Multi-select contact picker for the story "specified
 *  users" privacy tier (v0.9.27, T3). Same shape as the
 *  group-creation member picker (CreateChatScreen):
 *  ContactsStore's address book with checkbox rows and a
 *  name/@username filter. The selection is local until the
 *  caller confirms; an empty result is allowed here — the
 *  publish dialog itself refuses to post with nobody
 *  picked. */
@Composable
fun StoryUserPickerDialog(
    initial: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit
) {
    val contacts by ContactsStore.contacts.collectAsState()
    var picked by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { ContactsStore.loadContacts() }
    val shown = remember(contacts, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            contacts
        } else {
            contacts.filter {
                it.name.lowercase().contains(q) ||
                    it.username.lowercase().contains(q)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("谁能看这条动态（已选 ${picked.size} 人）") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜联系人") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (shown.isEmpty()) {
                    Text(
                        "联系人还没同步好，或者没有联系人。",
                        color = Color.Gray, fontSize = 13.sp
                    )
                }
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(items = shown, key = { it.userId }) { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    picked =
                                        if (contact.userId in picked) {
                                            picked - contact.userId
                                        } else {
                                            picked + contact.userId
                                        }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = contact.userId in picked,
                                onCheckedChange = { checked ->
                                    picked = if (checked) {
                                        picked + contact.userId
                                    } else {
                                        picked - contact.userId
                                    }
                                }
                            )
                            Column {
                                Text(contact.name, fontSize = 15.sp)
                                if (contact.username.isNotEmpty()) {
                                    Text(
                                        "@${contact.username}",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(picked) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
