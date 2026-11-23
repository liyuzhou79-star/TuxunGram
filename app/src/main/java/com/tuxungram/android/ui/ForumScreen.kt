package com.tuxungram.android.ui

import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import com.tuxungram.android.td.AppLock
import com.tuxungram.android.td.CustomEmojiStore
import com.tuxungram.android.td.ForumStore
import com.tuxungram.android.td.ForumTopicUi
import com.tuxungram.android.td.MsgItem
import com.tuxungram.android.td.MsgKind
import com.tuxungram.android.td.asPaidPhotoItem
import com.tuxungram.android.td.asPaidVideoItem
import java.io.File

/**
 * Forum screens (v0.8.0, G2): the topic list shown when a
 * forum supergroup is opened, and one topic's message stream.
 * Both live inside the chat screen's scaffold (top bar stays
 * the chat's), mirroring how mature clients treat topics as
 * sub-chats of the group.
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ForumTopicsList(
    chatId: Long,
    onOpenTopic: (ForumTopicUi) -> Unit
) {
    val topics by ForumStore.topics.collectAsState()
    val loading by ForumStore.topicsLoading.collectAsState()
    val canCreate by ForumStore.canCreateTopics.collectAsState()
    val canManage by ForumStore.canManageTopics.collectAsState()
    val manageError by ForumStore.manageError.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var createOpen by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ForumTopicUi?>(null) }
    var renameName by remember { mutableStateOf("") }
    var closeTarget by remember { mutableStateOf<ForumTopicUi?>(null) }
    var deleteTarget by remember { mutableStateOf<ForumTopicUi?>(null) }
    // Topic icon picker (v0.9.35, B3).
    var iconPickerTarget by remember {
        mutableStateOf<ForumTopicUi?>(null)
    }
    LaunchedEffect(chatId) {
        ForumStore.loadTopics(chatId)
        ForumStore.loadViewerRights(chatId)
    }
    LaunchedEffect(manageError) {
        val text = manageError ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        ForumStore.clearManageError()
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

    if (createOpen) {
        AlertDialog(
            onDismissRequest = { createOpen = false },
            title = { Text("新建话题") },
            text = {
                OutlinedTextField(
                    value = createName,
                    onValueChange = { createName = it },
                    placeholder = { Text("话题名字") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        ForumStore.createTopic(createName)
                        createOpen = false
                        createName = ""
                    },
                    enabled = createName.isNotBlank()
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { createOpen = false }) {
                    Text("取消")
                }
            }
        )
    }
    val toRename = renameTarget
    if (toRename != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名话题") },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        ForumStore.renameTopic(toRename, renameName)
                        renameTarget = null
                    },
                    enabled = renameName.isNotBlank()
                ) { Text("改名") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("取消")
                }
            }
        )
    }
    val toClose = closeTarget
    if (toClose != null) {
        AlertDialog(
            onDismissRequest = { closeTarget = null },
            title = { Text("关闭话题？") },
            text = {
                Text(
                    "关闭后大家不能再在「${toClose.name}」里发新消息，" +
                        "之前的消息还在，之后可以重新打开。"
                )
            },
            confirmButton = {
                Button(onClick = {
                    ForumStore.setTopicClosed(toClose, true)
                    closeTarget = null
                }) { Text("关闭") }
            },
            dismissButton = {
                TextButton(onClick = { closeTarget = null }) {
                    Text("取消")
                }
            }
        )
    }
    val toDelete = deleteTarget
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除话题？") },
            text = {
                Text(
                    "「${toDelete.name}」和里面的消息会一起删掉，删了就找不回来。"
                )
            },
            confirmButton = {
                Button(onClick = {
                    ForumStore.deleteTopic(toDelete)
                    deleteTarget = null
                }) { Text("删除", color = Color(0xFFC62828)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            }
        )
    }
    // Topic icon picker (v0.9.35, B3): the account's
    // installed custom-emoji sets supply the choices —
    // the same pool the reaction/message emoji come from.
    // A choice calls editForumTopic through ForumStore;
    // "恢复默认" returns the topic to its colored initial.
    val iconTopic = iconPickerTarget
    if (iconTopic != null) {
        var iconChoices by remember(iconTopic.topicId) {
            mutableStateOf<List<Long>>(emptyList())
        }
        var iconChoicesLoaded by remember(iconTopic.topicId) {
            mutableStateOf(false)
        }
        LaunchedEffect(iconTopic.topicId) {
            val setsQuery = com.tuxungram.android.td.TdClient
                .request("getInstalledStickerSets").apply {
                    put(
                        "sticker_type",
                        JSONObject().apply {
                            put(
                                "@type",
                                "stickerTypeCustomEmoji"
                            )
                        }
                    )
                }
            com.tuxungram.android.td.TdRepository.sendRaw(
                setsQuery
            ) { sets ->
                val setIds = sets.optJSONArray("sets")
                if (com.tuxungram.android.td.TdClient
                        .isError(sets) || setIds == null
                ) {
                    iconChoicesLoaded = true
                    return@sendRaw
                }
                val ids = ArrayList<Long>()
                for (i in 0 until setIds.length()) {
                    setIds.optJSONObject(i)?.optLong("id")
                        ?.let { ids.add(it) }
                }
                if (ids.isEmpty()) {
                    iconChoicesLoaded = true
                    return@sendRaw
                }
                var pending = ids.size
                val collected = ArrayList<Long>()
                for (setId in ids) {
                    val setQuery = com.tuxungram.android.td
                        .TdClient.request("getStickerSet")
                        .apply { put("set_id", setId) }
                    com.tuxungram.android.td.TdRepository
                        .sendRaw(setQuery) { set ->
                            val stickers =
                                set.optJSONArray("stickers")
                            if (stickers != null) {
                                for (j in 0 until
                                    stickers.length()) {
                                    val sticker =
                                        stickers.optJSONObject(j)
                                            ?: continue
                                    val emojiId = sticker
                                        .optJSONObject("full_type")
                                        ?.optLong(
                                            "custom_emoji_id"
                                        ) ?: 0L
                                    if (emojiId != 0L &&
                                        emojiId !in collected
                                    ) {
                                        collected.add(emojiId)
                                    }
                                }
                            }
                            pending--
                            if (pending == 0) {
                                iconChoices = collected
                                iconChoicesLoaded = true
                                // Rendering resolves through
                                // the shared store: it fetches
                                // and downloads the sticker
                                // files for these ids.
                                CustomEmojiStore.ensure(collected)
                            }
                        }
                }
            }
        }
        val iconPaths by CustomEmojiStore.paths.collectAsState()
        AlertDialog(
            onDismissRequest = { iconPickerTarget = null },
            title = { Text("给「${iconTopic.name}」换图标") },
            text = {
                Column {
                    if (iconTopic.iconCustomEmojiId != 0L) {
                        TextButton(onClick = {
                            ForumStore.setTopicIcon(iconTopic, 0L)
                            iconPickerTarget = null
                        }) { Text("恢复默认（彩色首字母）") }
                    }
                    when {
                        !iconChoicesLoaded -> Text(
                            "正在加载你装好的自定义表情…",
                            fontSize = 13.5.sp, color = Color.Gray
                        )
                        iconChoices.isEmpty() -> Text(
                            "你还没有装任何自定义表情包，所以暂时没得选。" +
                                "先在聊天里装一套自定义表情，再回来换图标。",
                            fontSize = 13.5.sp, color = Color.Gray
                        )
                        else -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        ) {
                            androidx.compose.foundation.lazy
                                .grid.LazyVerticalGrid(
                                    columns = androidx.compose
                                        .foundation.lazy.grid
                                        .GridCells.Fixed(6),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(
                                        iconChoices.size
                                    ) { index ->
                                        val emojiId =
                                            iconChoices[index]
                                        Box(
                                            modifier = Modifier
                                                .padding(4.dp)
                                                .clickable {
                                                    ForumStore
                                                        .setTopicIcon(
                                                            iconTopic,
                                                            emojiId
                                                        )
                                                    iconPickerTarget =
                                                        null
                                                },
                                            contentAlignment =
                                                Alignment.Center
                                        ) {
                                            val path =
                                                iconPaths[emojiId]
                                            if (path != null) {
                                                CustomEmojiGlyph(
                                                    path = path,
                                                    sizeDp = 34
                                                )
                                            } else {
                                                Text(
                                                    "…",
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
            },
            confirmButton = { },
            dismissButton = {
                TextButton(onClick = {
                    iconPickerTarget = null
                }) { Text("取消") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Create entry (v0.9.15, V5): shown when the chat's
        // member right or an admin right allows new topics;
        // the server still has the final say and refusals
        // surface as a toast via manageError.
        if (canCreate || canManage) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        createName = ""
                        createOpen = true
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "新建话题",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color(0x14000000))
            )
        }
        if (topics.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    CircularProgressIndicator()
                } else {
                    Text("这个群组还没有话题", color = Color.Gray)
                }
            }
            return@Column
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .lazyListScrollbar(listState)
        ) {
            items(items = topics, key = { it.topicId }) { topic ->
                var menuOpen by remember(topic.topicId) {
                    mutableStateOf(false)
                }
                val manageable = ForumStore.canManageTopic(topic)
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onOpenTopic(topic) },
                                onLongClick = {
                                    if (manageable) menuOpen = true
                                }
                            )
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
                            // Custom-emoji icon (v0.9.35, B3):
                            // rendered over the colored tile
                            // once its sticker file is local;
                            // the initial letter shows until
                            // then (and for emoji-less topics).
                            val topicEmojiPaths by
                                CustomEmojiStore.paths
                                    .collectAsState()
                            if (topic.iconCustomEmojiId != 0L) {
                                LaunchedEffect(
                                    topic.iconCustomEmojiId
                                ) {
                                    CustomEmojiStore.ensure(
                                        listOf(
                                            topic.iconCustomEmojiId
                                        )
                                    )
                                }
                            }
                            val topicEmojiPath =
                                if (topic.iconCustomEmojiId != 0L) {
                                    topicEmojiPaths[
                                        topic.iconCustomEmojiId
                                    ]
                                } else {
                                    null
                                }
                            // The initial letter stays as
                            // the base layer; the emoji glyph
                            // draws over it once decoded.
                            Text(
                                topic.name.trim().firstOrNull()
                                    ?.toString() ?: "#",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (topicEmojiPath != null) {
                                CustomEmojiGlyph(
                                    path = topicEmojiPath,
                                    sizeDp = 28
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Text(
                                    topic.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    overflow = TextOverflow.Ellipsis,
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
                                    overflow = TextOverflow.Ellipsis,
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
                                    .padding(
                                        horizontal = 7.dp,
                                        vertical = 2.dp
                                    ),
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
                    // Topic management menu (v0.9.15, V5):
                    // rename / close / reopen / delete, gated
                    // by canManageTopic; close and delete ask
                    // once more before firing.
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            onClick = {
                                menuOpen = false
                                renameName = topic.name
                                renameTarget = topic
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("换图标") },
                            onClick = {
                                menuOpen = false
                                iconPickerTarget = topic
                            }
                        )
                        if (topic.isClosed) {
                            DropdownMenuItem(
                                text = { Text("重新打开") },
                                onClick = {
                                    menuOpen = false
                                    ForumStore.setTopicClosed(
                                        topic, false
                                    )
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("关闭话题") },
                                onClick = {
                                    menuOpen = false
                                    closeTarget = topic
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "删除话题",
                                    color = Color(0xFFC62828)
                                )
                            },
                            onClick = {
                                menuOpen = false
                                deleteTarget = topic
                            }
                        )
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
    // Photo editor / video trim overlays (v0.9.35, B4):
    // the same pre-send editing the main chat has, wired
    // into this topic view's pending-photo / pending-file
    // chain.
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
    val forumAllLoaded by ForumStore.allLoadedFlow.collectAsState()

    DisposableEffect(chatId, topic.topicId) {
        ForumStore.openTopic(chatId, topic.topicId)
        onDispose { ForumStore.closeTopic() }
    }
    LaunchedEffect(listState, messages.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastVisible ->
            // One-page-ahead prefetch (v0.9.28, U2), same
            // rule as the chat list.
            if (messages.isNotEmpty() &&
                messages.size - 1 - lastVisible <= 50
            ) {
                ForumStore.loadOlder()
            }
        }
    }

    // Back-to-latest (v0.9.28, U1): a topic's loaded window
    // always reaches its newest message, so a tap only needs
    // the animated scroll. No unread badge — TDLib keeps no
    // per-topic unread counter.
    val forumHistoryLoading by ForumStore.historyLoading.collectAsState()
    val forumScope = rememberCoroutineScope()
    // At-bottom behaviour (v0.9.44, N5): same rule as the
    // main chat and threads — button as soon as the newest
    // message leaves the screen, auto-scroll only from the
    // bottom (own sends always).
    var forumAtBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex <= 2 }
            .collect { forumAtBottom = it }
    }
    val forumNewest = messages.lastOrNull()
    LaunchedEffect(forumNewest?.id) {
        val newest = forumNewest ?: return@LaunchedEffect
        if (forumAtBottom || newest.outgoing) {
            listState.animateScrollToItem(0)
        }
    }
    val forumFarFromLatest by remember {
        derivedStateOf { !forumAtBottom }
    }
    // Tag search (v0.9.45, P1): results for the tag the
    // user tapped in a bubble; the panel overlays the
    // topic while the query is non-empty.
    val tagQuery by ForumStore.searchQuery.collectAsState()
    val tagResults by ForumStore.searchResults.collectAsState()
    val tagTotal by ForumStore.searchTotal.collectAsState()
    val tagExhausted by ForumStore.searchExhausted.collectAsState()
    val forumDetached by ForumStore.windowDetached.collectAsState()
    // Scroll the tag-search jump target into view when
    // its window lands (v0.9.45, P1): same locate
    // mechanism as the main chat, plus clearing the flag
    // so a second jump to the same message scrolls again.
    val forumLocateId by ForumStore.locateId.collectAsState()
    LaunchedEffect(forumLocateId, messages.size) {
        if (forumLocateId != 0L && messages.isNotEmpty()) {
            val ascIndex = messages.indexOfFirst {
                it.id == forumLocateId
            }
            if (ascIndex >= 0) {
                listState.scrollToItem(messages.size - 1 - ascIndex)
                ForumStore.clearLocate()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                        onOpenMedia = { m -> mediaViewer = m },
                        onBotCommand = { ForumStore.sendText(it) },
                        onHashtag = { tag ->
                            ForumStore.searchByTag(tag)
                        },
                        onToggleChecklistTask = { m, task ->
                            ForumStore.markChecklistTask(m, task)
                        },
                        onResend = { ForumStore.resend(it) },
                        showTail = msg.id in tailIds
                    )
                }
                if (forumHistoryLoading && messages.isNotEmpty()) {
                    item(key = "paging-spinner") { PagingSpinnerRow() }
                }
            }
            ScrollToLatestButton(
                // After a tag-search jump the window no
                // longer reaches the newest message
                // (v0.9.45, P1): stay visible while detached
                // even at the jump window's own top, and
                // reload the window instead of scrolling.
                visible = forumFarFromLatest || forumDetached,
                unreadCount = 0,
                onClick = {
                    if (forumDetached) {
                        ForumStore.backToLatest()
                    } else {
                        forumScope.launch {
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
        val target = replyTarget
        if (target != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
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
    // topic left composed underneath. The context is
    // this topic's loaded window only; paging to the
    // oldest end pulls the topic's own older pages.
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
                exhausted = forumAllLoaded,
                onLoadMore = {
                    if (!forumAllLoaded) ForumStore.loadOlder()
                },
                onClose = { mediaViewer = null },
                onPlayVideo = { item ->
                    mediaViewer = null
                    videoItem = item
                }
            )
        }
    }
    // Tag search panel (v0.9.45, P1): overlays the topic
    // while a tag query is active; picking a result jumps
    // the window to that message.
    if (tagQuery.isNotEmpty()) {
        TagSearchPanel(
            query = tagQuery,
            results = tagResults,
            total = tagTotal,
            exhausted = tagExhausted,
            onLoadMore = {
                ForumStore.searchByTag(tagQuery, append = true)
            },
            onPick = { item ->
                ForumStore.exitSearch()
                ForumStore.jumpTo(item.id)
            },
            onClose = { ForumStore.exitSearch() }
        )
    }
    }
}

/** A downloaded custom-emoji sticker file drawn at a
 *  fixed size (v0.9.35, B3). Same decode path as the
 *  inline custom emoji in message text (decodeCached);
 *  while the file is missing or undecodable (animated
 *  formats), nothing is drawn and the caller's fallback
 *  stays visible. */
@Composable
private fun CustomEmojiGlyph(path: String?, sizeDp: Int) {
    if (path == null) return
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        value = withContext(Dispatchers.IO) {
            decodeCached(path, 128)
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(sizeDp.dp)
        )
    }
}
