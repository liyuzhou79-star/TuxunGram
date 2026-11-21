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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Checkbox
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


/** Chat-list-as-picker for forwarding one message. Stays open until
 *  TDLib answers, so a failure is shown here instead of vanishing. */
@Composable
fun ForwardPickerScreen(
    fromChatId: Long,
    messageId: Long,
    onDone: () -> Unit
) {
    val chats by ChatStore.chats.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pickerListState = rememberLazyListState()
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
                items(items = chats, key = { it.id }) { chat ->
                    ChatRow(chat, onClick = {
                        busy = true
                        error = null
                        MessageStore.forwardMessage(
                            fromChatId, messageId, chat.id
                        ) { failure ->
                            if (failure == null) {
                                onDone()
                            } else {
                                busy = false
                                error = failure
                            }
                        }
                    })
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
        sameDay -> java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(then.time)
        isYesterday -> "昨天"
        now.timeInMillis - then.timeInMillis < 7L * 24 * 3600 * 1000 ->
            java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(then.time)
        now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) ->
            java.text.SimpleDateFormat("M/d", java.util.Locale.getDefault()).format(then.time)
        else -> java.text.SimpleDateFormat("yyyy/M/d", java.util.Locale.getDefault())
            .format(then.time)
    }
}

@Composable
fun ChatListScreen(
    onOpenChat: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChatLocated: (Long, Long) -> Unit,
    onOpenArchive: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenStory: (Long) -> Unit
) {
    val chats by ChatStore.chats.collectAsState()
    val allLoaded by ChatStore.allLoaded.collectAsState()
    val folders by ChatStore.folders.collectAsState()
    val selectedFolder by ChatStore.selectedFolder.collectAsState()
    val folderChats by ChatStore.folderChats.collectAsState()
    val folderLoaded by ChatStore.folderLoaded.collectAsState()
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val baseList = if (selectedFolder == null) chats else folderChats
    val shown = if (query.isBlank()) baseList else baseList.filter {
        it.title.contains(query.trim(), ignoreCase = true)
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

    // Global message search, debounced; the local title filter
    // above stays instant, server results stream in below it.
    val searchHits by SearchStore.results.collectAsState()
    val searching by SearchStore.searching.collectAsState()
    val searchExhausted by SearchStore.exhausted.collectAsState()
    // Collected so the contacts section refreshes when the
    // address book finishes loading.
    val allContacts by ContactsStore.contacts.collectAsState()
    LaunchedEffect(query) {
        if (query.isBlank()) {
            SearchStore.search("")
        } else {
            delay(500)
            SearchStore.search(query)
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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "TuxunGram",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenCreate) {
                Icon(Icons.Filled.Add, contentDescription = "新建群组或频道")
            }
            TextButton(onClick = onOpenArchive) {
                Text("归档")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "设置")
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        // Folder tabs (B7 + editor): selecting one swaps the
        // list below to that folder's chats; long-press opens
        // the editor (rename / members / delete), and the
        // trailing chip creates a new folder.
        var folderEditTarget by remember { mutableStateOf<Int?>(null) }
        var folderCreateOpen by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FolderChip(
                label = "全部",
                selected = selectedFolder == null,
                onClick = { ChatStore.selectFolder(null) }
            )
            for (folder in folders) {
                FolderChip(
                    label = folder.name.ifEmpty { "文件夹" },
                    selected = selectedFolder == folder.id,
                    onClick = { ChatStore.selectFolder(folder.id) },
                    onLongClick = { folderEditTarget = folder.id }
                )
            }
            FolderChip(
                label = "＋ 新建",
                selected = false,
                onClick = { folderCreateOpen = true }
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
        // Stories row (Batch 3, C5): one ring per chat with
        // active stories; colored ring = unread, gray = seen.
        val storyRings by StoryStore.rings.collectAsState()
        if (query.isBlank() && selectedFolder == null && storyRings.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
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
                            maxLines = 1,
                            modifier = Modifier.width(60.dp)
                        )
                    }
                }
            }
        }
        if (shown.isEmpty() && query.isBlank()) {
            CenterColumn {
                if (selectedFolder != null && !folderLoaded) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在加载文件夹…", color = Color.Gray)
                } else if (selectedFolder != null) {
                    Text("这个文件夹里还没有对话", color = Color.Gray)
                } else if (!allLoaded) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在加载对话…", color = Color.Gray)
                } else {
                    Text(
                        "还没有对话。在 Telegram 里有聊天后，这里会自动出现。",
                        color = Color.Gray
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
                items(
                    items = shown,
                    key = { it.id }
                ) { chat ->
                    ChatRow(chat, onClick = { onOpenChat(chat.id) })
                }
                if (query.isNotBlank() && selectedFolder == null) {
                    val contactHits = ContactsStore.search(query)
                    if (contactHits.isNotEmpty()) {
                        item(key = "contacts_header") {
                            Text(
                                "联系人",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Gray,
                                modifier = Modifier.padding(
                                    horizontal = 16.dp, vertical = 8.dp
                                )
                            )
                        }
                        items(
                            items = contactHits,
                            key = { "contact_${it.userId}" }
                        ) { contact ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        ContactsStore.openPrivateChat(
                                            contact.userId
                                        ) { chatId ->
                                            if (chatId != null) onOpenChat(chatId)
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    contact.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                if (contact.username.isNotEmpty()) {
                                    Text(
                                        "@${contact.username}",
                                        color = Color.Gray, fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                    item(key = "hits_header") {
                        Text(
                            "消息",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray,
                            modifier = Modifier.padding(
                                horizontal = 16.dp, vertical = 8.dp
                            )
                        )
                    }
                    items(
                        items = searchHits,
                        key = { "hit_${it.chatId}_${it.messageId}" }
                    ) { hit ->
                        SearchHitRow(hit) {
                            onOpenChatLocated(hit.chatId, hit.messageId)
                        }
                    }
                    if (searching) {
                        item(key = "hits_loading") {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 16.dp, vertical = 10.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("搜索中…", color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    } else if (!searchExhausted && searchHits.isNotEmpty()) {
                        item(key = "hits_more") {
                            TextButton(
                                onClick = { SearchStore.loadMore() },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text("加载更多结果")
                            }
                        }
                    } else if (searchHits.isEmpty()) {
                        item(key = "hits_empty") {
                            Text(
                                "没有搜到消息",
                                color = Color.Gray, fontSize = 13.sp,
                                modifier = Modifier.padding(
                                    horizontal = 16.dp, vertical = 10.dp
                                )
                            )
                        }
                    }
                }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatRow(chat: ChatItem, onClick: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true }
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        val hue = (chat.id % 8 + 8) % 8
        val colors = listOf(
            Color(0xFFEF9A9A), Color(0xFFF48FB1), Color(0xFFCE93D8), Color(0xFF90CAF9),
            Color(0xFF80CBC4), Color(0xFFA5D6A7), Color(0xFFFFCC80), Color(0xFFBCAAA4)
        )
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
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chat.isSecret) {
                    Text("🔒", fontSize = 13.sp)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    chat.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (chat.isChannel) {
                    Spacer(Modifier.width(6.dp))
                    Text("频道", color = Color.Gray, fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(formatChatTime(chat.dateSeconds), color = Color.Gray, fontSize = 12.sp)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.preview,
                    color = Color.Gray,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (chat.isPinned) {
                    Text("置顶", color = Color.Gray, fontSize = 11.sp)
                }
                if (chat.unreadCount > 0 || chat.isMarkedAsUnread) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                if (chat.isMuted) Color(0xFFBDBDBD)
                                else MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 1.dp),
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
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (chat.isPinned) "取消置顶" else "置顶") },
                onClick = {
                    menuOpen = false
                    ChatStore.togglePin(chat)
                }
            )
            DropdownMenuItem(
                text = { Text(if (chat.isMuted) "取消静音" else "静音") },
                onClick = {
                    menuOpen = false
                    ChatStore.toggleMute(chat)
                }
            )
            if (chat.unreadCount > 0 || chat.isMarkedAsUnread) {
                DropdownMenuItem(
                    text = { Text("标为已读") },
                    onClick = {
                        menuOpen = false
                        ChatStore.markRead(chat)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("归档") },
                onClick = {
                    menuOpen = false
                    ChatStore.archive(chat)
                }
            )
        }
    }
}


/** The archive list (chatListArchive): chats the user archived
 *  from the main list live here; tapping opens the chat, and
 *  each row can move its chat back to the main list. */
@Composable
fun ArchiveScreen(onBack: () -> Unit, onOpenChat: (Long) -> Unit) {
    BackHandler { onBack() }
    val archived by ChatStore.archivedChats.collectAsState()
    val archiveLoaded by ChatStore.archiveLoaded.collectAsState()
    LaunchedEffect(Unit) { ChatStore.loadArchived() }
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
                    Text("没有归档的对话", color = Color.Gray)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(chat.id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (chat.photoPath != null) {
                            AvatarImage(path = chat.photoPath, sizeDp = 48)
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFF90CAF9), CircleShape),
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
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                chat.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                maxLines = 1
                            )
                            Text(
                                chat.preview,
                                color = Color.Gray,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
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

/** One folder tab in the chat-list header row. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun FolderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .background(
                if (selected) {
                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                } else {
                    Color(0xFFE8E8E8)
                },
                RoundedCornerShape(16.dp)
            )
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
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.White else Color(0xFF424242),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
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

    LaunchedEffect(folderId) {
        if (folderId != null) {
            FolderStore.loadIncluded(folderId) { included ->
                selected = included.toSet()
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
                                .clickable {
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
                                onCheckedChange = { checked ->
                                    selected = if (checked) {
                                        selected + chat.id
                                    } else {
                                        selected - chat.id
                                    }
                                }
                            )
                            Text(chat.title, fontSize = 14.sp, maxLines = 1)
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
                enabled = !busy && name.isNotBlank(),
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
