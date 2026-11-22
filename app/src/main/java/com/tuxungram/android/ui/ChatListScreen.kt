package com.tuxungram.android.ui


import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
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
import com.tuxungram.android.td.AppLock
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
                maxLines = 1
            )
            Text(
                "发给自己的消息和文件",
                color = Color.Gray,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
    }
}

/** Chat-list-as-picker for forwarding one message. Stays open until
 *  TDLib answers, so a failure is shown here instead of vanishing. */
@Composable
fun ForwardPickerScreen(
    fromChatId: Long,
    messageIds: List<Long>,
    onDone: () -> Unit
) {
    val chats by ChatStore.chats.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
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
            fromChatId, messageIds, targetId
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
                            Motion.tween(250)
                        )
                    ) {
                        ChatRow(chat, onClick = { forwardTo(chat.id) })
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
    onOpenStory: (Long) -> Unit,
    onOpenContacts: () -> Unit,
    // Hoisted by the host (v0.9.9, W4) so switching bottom-nav
    // tabs away and back keeps the list scroll position.
    listState: LazyListState = rememberLazyListState()
) {
    val chats by ChatStore.chats.collectAsState()
    val allLoaded by ChatStore.allLoaded.collectAsState()
    val folders by ChatStore.folders.collectAsState()
    val selectedFolder by ChatStore.selectedFolder.collectAsState()
    val folderChats by ChatStore.folderChats.collectAsState()
    val folderLoaded by ChatStore.folderLoaded.collectAsState()
    var query by remember { mutableStateOf("") }
    // Swipe actions (v0.9.2, L3): the chat id whose row is
    // currently swiped open, so only one row shows its action
    // panel at a time.
    var swipeOpenId by remember { mutableStateOf<Long?>(null) }

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
            TextButton(onClick = onOpenContacts) {
                Text("通讯录")
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
        // Story publishing (v0.8.0, G5): pick one photo, confirm
        // (the dialog states the privacy plainly), then
        // StoryStore runs canPostStory -> postStory. Results
        // come back as one-line notices, shown as toasts.
        val storyContext = LocalContext.current
        var pendingStoryPhoto by remember { mutableStateOf<String?>(null) }
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
        val postNotice by StoryStore.postNotice.collectAsState()
        LaunchedEffect(postNotice) {
            val notice = postNotice
            if (notice != null) {
                Toast.makeText(storyContext, notice, Toast.LENGTH_SHORT)
                    .show()
                StoryStore.clearPostNotice()
            }
        }
        if (pendingStoryPhoto != null) {
            AlertDialog(
                onDismissRequest = { pendingStoryPhoto = null },
                title = { Text("发布动态？") },
                text = {
                    Text(
                        "这张照片会发到你的动态，所有人可见，" +
                            "24 小时后自动归档。"
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        val path = pendingStoryPhoto
                        pendingStoryPhoto = null
                        if (path != null) {
                            StoryStore.postPhotoStory(path)
                        }
                    }) { Text("发布") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingStoryPhoto = null }) {
                        Text("取消")
                    }
                }
            )
        }
        // Stories row (Batch 3, C5): one ring per chat with
        // active stories; colored ring = unread, gray = seen.
        // The first cell is the publish entry (G5) — the row
        // shows even when nobody has an active story, so the
        // entry is always reachable.
        val storyRings by StoryStore.rings.collectAsState()
        if (query.isBlank() && selectedFolder == null) {
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
                            AppLock.noteExternalLaunch()
                            storyPicker.launch("image/*")
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
        if (query.isBlank() && selectedFolder == null) {
            SavedMessagesRow(onClick = {
                ContactsStore.openSavedMessages { chatId ->
                    if (chatId != null) onOpenChat(chatId)
                }
            })
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
        if (shown.isEmpty() && query.isBlank()) {
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
        } else if (shown.isEmpty()) {
            // Search (or a filter) matched nothing: say so with
            // a graphic instead of a blank page (W1/W5).
            CenterColumn {
                EmptyState(
                    Icons.Filled.Search, "没有找到对话",
                    "换个关键词试试，或者检查一下拼写。"
                )
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
                    // Placement animation (v0.9.11, A1): rows
                    // glide to their new position when a pin,
                    // an archive or a fresh message reorders
                    // the list instead of teleporting.
                    Box(
                        modifier = Modifier.animateItemPlacement(
                            Motion.tween(250)
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
 * pin action is trimmed (pinning is a main-list concept
 * there), per the context-trimming rule.
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
                if (chat.isPinned) "取消置顶" else "置顶",
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
                        .background(Color(0xFF4CAF50), CircleShape)
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
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
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
                    Icon(
                        PinIcon, contentDescription = "置顶",
                        tint = Color.Gray, modifier = Modifier.size(13.dp)
                    )
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
                leadingIcon = {
                    Icon(PinIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                text = { Text(if (chat.isPinned) "取消置顶" else "置顶") },
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
    var swipeOpenId by remember { mutableStateOf<Long?>(null) }
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
                            Text(
                                chat.preview,
                                color = Color.Gray,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (label != "全部") {
                Icon(
                    FolderIcon, contentDescription = null,
                    tint = if (selected) Color.White else Color(0xFF757575),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                label,
                color = if (selected) Color.White else Color(0xFF424242),
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
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
