package com.tuxungram.android.ui

import androidx.compose.ui.text.style.TextOverflow
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.tuxungram.android.td.MyStoriesStore
import com.tuxungram.android.td.MyStoryUi
import com.tuxungram.android.td.StoryAlbumUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "My stories" page (v0.9.32, Y2): the stories I posted to
 * my profile page (getChatPostedToChatPageStories), each
 * with its server view/reaction counts; tapping one opens
 * its detail — counts, the viewer list
 * (getStoryInteractions — since v0.9.38 E3 all three
 * interaction types: views with their reactions badged,
 * forwards, reposts, under the server's own totals), and
 * deletion (deleteStory)
 * when the story object allows it. Stories the server
 * will not give interactions for show no viewer entry —
 * nothing is estimated or invented.
 */
@Composable
fun MyStoriesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val stories by MyStoriesStore.stories.collectAsState()
    val loading by MyStoriesStore.loading.collectAsState()
    val exhausted by MyStoriesStore.exhausted.collectAsState()
    val notice by MyStoriesStore.notice.collectAsState()
    val albums by MyStoriesStore.albums.collectAsState()
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var openAlbumId by remember { mutableStateOf<Int?>(null) }
    val albumStories by MyStoriesStore.albumStories.collectAsState()
    val selected = (stories + albumStories).firstOrNull {
        it.storyId == selectedId
    }
    val openAlbum = albums.firstOrNull { it.id == openAlbumId }

    LaunchedEffect(Unit) { MyStoriesStore.load() }
    LaunchedEffect(notice) {
        val text = notice ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        MyStoriesStore.clearNotice()
    }
    // Back walks out one level at a time: detail, then
    // album, then the page itself.
    BackHandler(enabled = selected != null || openAlbum != null) {
        if (selected != null) {
            selectedId = null
        } else {
            openAlbumId = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (selected != null) {
                    selectedId = null
                } else if (openAlbum != null) {
                    openAlbumId = null
                } else {
                    onBack()
                }
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                if (openAlbum != null && selected == null) {
                    openAlbum.name
                } else {
                    "我的动态"
                },
                fontSize = 19.sp, fontWeight = FontWeight.SemiBold
            )
        }
        val detail = selected
        if (detail != null) {
            StoryDetail(
                story = detail,
                onDeleted = { selectedId = null }
            )
        } else if (openAlbum != null) {
            AlbumDetail(
                album = openAlbum,
                allStories = stories,
                onOpenStory = { selectedId = it }
            )
        } else {
            when {
                loading && stories.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                stories.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AlbumsSection(
                        albums = albums,
                        allStories = stories,
                        onOpenAlbum = { openAlbumId = it }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "你还没有发到资料页的动态。\n" +
                                "发动态时把「发布到资料页」打开，" +
                                "或者在动态上置顶到资料页，" +
                                "它们就会列在这里。",
                            color = Color.Gray, fontSize = 14.sp
                        )
                    }
                }
                else -> Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AlbumsSection(
                        albums = albums,
                        allStories = stories,
                        onOpenAlbum = { openAlbumId = it }
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                    items(stories, key = { it.storyId }) { story ->
                        StoryGridCell(story) { selectedId = story.storyId }
                    }
                    if (!exhausted) {
                        item {
                            TextButton(
                                onClick = { MyStoriesStore.loadMore() },
                                enabled = !loading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (loading) "正在加载…" else "加载更多"
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryGridCell(story: MyStoryUi, onClick: () -> Unit) {
    val path = story.previewPath
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) { decodeCached(path, 512) }
        }
    }
    Box(
        modifier = Modifier
            .padding(1.5.dp)
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF263238))
            .clickable { onClick() }
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (story.isVideo) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "视频动态",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(30.dp)
            )
        }
        if (story.isPinned) {
            Text(
                "置顶",
                color = Color.White, fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(
                        Color(0x99000000), RoundedCornerShape(3.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color(0x66000000))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Icon(
                EyeIcon, contentDescription = null,
                tint = Color.White, modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                "${story.viewCount}",
                color = Color.White, fontSize = 11.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatDayLabel(story.dateSeconds),
                color = Color.White, fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun StoryDetail(story: MyStoryUi, onDeleted: () -> Unit) {
    var deleteConfirm by remember { mutableStateOf(false) }
    val interactions by MyStoriesStore.interactions.collectAsState()
    val interactionsLoading by
        MyStoriesStore.interactionsLoading.collectAsState()
    val interactionsError by
        MyStoriesStore.interactionsError.collectAsState()
    val interactionsExhausted by
        MyStoriesStore.interactionsExhausted.collectAsState()
    val interactionTotals by
        MyStoriesStore.interactionTotals.collectAsState()
    LaunchedEffect(story.storyId) {
        if (story.canGetInteractions) {
            MyStoriesStore.loadInteractions(story.storyId)
        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            val path = story.previewPath
            val bitmap by produceState<android.graphics.Bitmap?>(
                initialValue = null, path
            ) {
                value = if (path == null) {
                    null
                } else {
                    withContext(Dispatchers.IO) {
                        decodeCached(path, 1024)
                    }
                }
            }
            val bmp = bitmap
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF263238)),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (story.isVideo) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "视频动态",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "${formatDayLabel(story.dateSeconds)} " +
                    formatMsgTime(story.dateSeconds) +
                    if (story.isPinned) " · 已置顶到资料页" else "",
                fontSize = 13.sp, color = Color.Gray
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${story.viewCount} 次查看 · " +
                    "${story.reactionCount} 个回应",
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold
            )
            if (story.canDelete) {
                TextButton(onClick = { deleteConfirm = true }) {
                    Text("删除这条动态", color = Color(0xFFC62828))
                }
            }
            // Story albums (v0.9.35, B2): which of my albums
            // this story is in, with add/remove per album.
            // Stories the server will not let into albums
            // (can_be_added_to_album false and in no album)
            // get one honest line instead of dead buttons.
            val albums by MyStoriesStore.albums.collectAsState()
            Spacer(Modifier.height(8.dp))
            Text(
                "相册",
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            if (albums.isEmpty()) {
                EmptyStateMini(
                    icon = ImageIcon,
                    title = "还没有相册",
                    subtitle = "回列表顶部可以新建一个，把动态分门别类放好。"
                )
            } else if (!story.canBeAddedToAlbum &&
                story.albumIds.isEmpty()
            ) {
                Text(
                    "这条动态不能加入相册（服务器没有开放它）。",
                    fontSize = 13.5.sp, color = Color.Gray
                )
            } else {
                for (album in albums) {
                    val member = album.id in story.albumIds
                    if (!member && !story.canBeAddedToAlbum) {
                        continue
                    }
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            album.name,
                            fontSize = 14.5.sp,
                            modifier = Modifier.weight(1f),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                        if (member) {
                            Text(
                                "已加入",
                                fontSize = 12.5.sp, color = Color.Gray
                            )
                            TextButton(onClick = {
                                MyStoriesStore.removeFromAlbum(
                                    album.id, listOf(story.storyId)
                                )
                            }) { Text("移出") }
                        } else {
                            TextButton(onClick = {
                                MyStoriesStore.addToAlbum(
                                    album.id, listOf(story.storyId)
                                )
                            }) { Text("加入") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "互动记录",
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            // Server totals (v0.9.38, E3): the response's
            // own total_count / total_forward_count /
            // total_reaction_count, kept separate on
            // purpose — views, reactions and forwards are
            // three different numbers, and the detail rows
            // below are only what the API hands out (they
            // can be fewer than the totals).
            val totals = interactionTotals
            if (story.canGetInteractions && totals != null) {
                Text(
                    "服务器统计：共 ${totals.totalCount} 条互动 · " +
                        "转发 ${totals.forwardCount} · " +
                        "回应 ${totals.reactionCount}" +
                        "（下面是接口给出的明细，可能比总数少）",
                    fontSize = 12.5.sp, color = Color.Gray
                )
                Spacer(Modifier.height(4.dp))
            }
        }
        if (!story.canGetInteractions) {
            item {
                Text(
                    "这条动态看不了互动记录（服务器没有开放这条的互动数据）。",
                    fontSize = 13.5.sp, color = Color.Gray
                )
            }
        } else if (interactionsError != null && interactions.isEmpty()) {
            item {
                Text(interactionsError!!, fontSize = 13.5.sp)
            }
        } else if (interactionsLoading && interactions.isEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("正在查…", fontSize = 14.sp)
                }
            }
        } else if (interactions.isEmpty()) {
            item {
                Text(
                    when {
                        story.viewCount > 0 && story.hasExpiredViewers ->
                            "有 ${story.viewCount} 次查看，但较早的" +
                                "查看记录已经过期，服务器不再提供名单。"
                        story.viewCount > 0 ->
                            "有 ${story.viewCount} 次查看，但服务器" +
                                "这次没有给出名单明细（上面的数字是总数）。"
                        else -> "还没有人看过这条动态。"
                    },
                    fontSize = 13.5.sp, color = Color.Gray
                )
            }
        } else {
            items(
                interactions,
                key = {
                    "${it.kind}:${it.userId}:${it.actorChatId}:" +
                        it.dateSeconds
                }
            ) { row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                ) {
                    if (row.photoPath != null) {
                        AvatarImage(
                            path = row.photoPath, sizeDp = 38
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    Color(0xFFB0BEC5), CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                row.name.trim().firstOrNull()
                                    ?.toString() ?: "?",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            row.name.ifEmpty { "…" },
                            fontSize = 15.sp,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                        Text(
                            when (row.kind) {
                                com.tuxungram.android.td
                                    .STORY_KIND_FORWARD ->
                                    "转发了这条动态"
                                com.tuxungram.android.td
                                    .STORY_KIND_REPOST ->
                                    "转贴为自己的动态"
                                else -> "看过"
                            },
                            fontSize = 12.sp, color = Color.Gray
                        )
                    }
                    if (row.reactionEmoji != null) {
                        Text(
                            row.reactionEmoji,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    } else if (row.customReaction) {
                        Text(
                            "自定义表情回应",
                            fontSize = 11.5.sp, color = Color.Gray,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    Text(
                        formatViewDate(row.dateSeconds),
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
            }
            if (!interactionsExhausted) {
                item {
                    TextButton(
                        onClick = {
                            MyStoriesStore.loadMoreInteractions()
                        },
                        enabled = !interactionsLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (interactionsLoading) {
                                "正在加载…"
                            } else {
                                "加载更多"
                            }
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("删除这条动态？") },
            text = {
                Text("删掉后所有人都看不到了，动态的查看记录也会一起没掉。")
            },
            confirmButton = {
                Button(onClick = {
                    deleteConfirm = false
                    MyStoriesStore.delete(story.storyId)
                    onDeleted()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/** Story albums strip (v0.9.35, B2): my albums as a
 *  horizontal row of cover tiles over the story grid;
 *  long-press an album for rename/delete. The trailing
 *  tile creates a new album (name 1-12 characters, with
 *  an optional first batch of stories).
 *
 *  Reorder (v0.9.36, C3): the long-press menu's 调整顺序
 *  entry switches the strip into reorder mode — tiles
 *  become long-press-draggable horizontally (every tile
 *  has the same 72 dp pitch, so the drag offset maps to
 *  whole steps), each drop commits the full order via
 *  reorderStoryAlbums, and 完成 leaves the mode. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumsSection(
    albums: List<StoryAlbumUi>,
    allStories: List<MyStoryUi>,
    onOpenAlbum: (Int) -> Unit
) {
    var createOpen by remember { mutableStateOf(false) }
    var renameTarget by remember {
        mutableStateOf<StoryAlbumUi?>(null)
    }
    var deleteTarget by remember {
        mutableStateOf<StoryAlbumUi?>(null)
    }
    var reordering by remember { mutableStateOf(false) }
    var localAlbums by remember {
        mutableStateOf(albums)
    }
    var draggingAlbumId by remember {
        mutableStateOf<Int?>(null)
    }
    var albumDragDelta by remember { mutableStateOf(0f) }
    LaunchedEffect(albums) {
        if (!reordering) localAlbums = albums
    }
    val albumPitchPx = with(LocalDensity.current) {
        72.dp.toPx()
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = 16.dp, end = 8.dp, top = 10.dp, bottom = 4.dp
            )
        ) {
            Text(
                if (reordering) "拖动相册调整顺序" else "相册",
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (reordering) {
                TextButton(onClick = {
                    localAlbums = albums
                    reordering = false
                }) {
                    Text("取消")
                }
                TextButton(onClick = { reordering = false }) {
                    Text("完成")
                }
            } else {
                TextButton(onClick = { createOpen = true }) {
                    Text("新建相册")
                }
            }
        }
        if (albums.isEmpty()) {
            EmptyStateMini(
                icon = ImageIcon,
                title = "还没有相册",
                subtitle = "建一个，把发过的动态按主题收好，" +
                    "别人看你的资料页时能按相册翻。"
            )
        } else {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentPadding =
                    androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp
                    )
            ) {
                items(localAlbums, key = { it.id }) { album ->
                    var menuOpen by remember(album.id) {
                        mutableStateOf(false)
                    }
                    val isDragging = draggingAlbumId == album.id
                    Box {
                        Column(
                            horizontalAlignment =
                                Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationX =
                                        if (isDragging) {
                                            albumDragDelta
                                        } else {
                                            0f
                                        }
                                }
                                .then(
                                    if (reordering) {
                                        Modifier.pointerInput(
                                            album.id
                                        ) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggingAlbumId =
                                                        album.id
                                                    albumDragDelta = 0f
                                                },
                                                onDragEnd = {
                                                    draggingAlbumId =
                                                        null
                                                    albumDragDelta = 0f
                                                    MyStoriesStore
                                                        .reorderAlbums(
                                                            localAlbums
                                                                .map {
                                                                    it.id
                                                                }
                                                        )
                                                },
                                                onDragCancel = {
                                                    draggingAlbumId =
                                                        null
                                                    albumDragDelta = 0f
                                                }
                                            ) { change, dragAmount ->
                                                change.consume()
                                                albumDragDelta +=
                                                    dragAmount.x
                                                val shift =
                                                    (albumDragDelta /
                                                        albumPitchPx)
                                                        .roundToInt()
                                                if (shift != 0) {
                                                    val idx =
                                                        localAlbums
                                                            .indexOfFirst {
                                                                it.id ==
                                                                    album.id
                                                            }
                                                    if (idx >= 0) {
                                                        val target =
                                                            (idx + shift)
                                                                .coerceIn(
                                                                    0,
                                                                    localAlbums
                                                                        .lastIndex
                                                                )
                                                        if (target !=
                                                            idx
                                                        ) {
                                                            localAlbums =
                                                                localAlbums
                                                                    .toMutableList()
                                                                    .apply {
                                                                        add(
                                                                            target,
                                                                            removeAt(
                                                                                idx
                                                                            )
                                                                        )
                                                                    }
                                                            albumDragDelta -=
                                                                (target -
                                                                    idx) *
                                                                    albumPitchPx
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Modifier.combinedClickable(
                                            onClick = {
                                                onOpenAlbum(album.id)
                                            },
                                            onLongClick = {
                                                menuOpen = true
                                            }
                                        )
                                    }
                                )
                        ) {
                            AlbumCover(album)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                album.name,
                                fontSize = 12.sp, overflow = TextOverflow.Ellipsis, maxLines = 1,
                                modifier = Modifier.width(64.dp),
                                textAlign =
                                    androidx.compose.ui.text.style
                                        .TextAlign.Center
                            )
                        }
                        if (!reordering) {
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("调整顺序") },
                                    onClick = {
                                        menuOpen = false
                                        localAlbums = albums
                                        reordering = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("改名") },
                                    onClick = {
                                        menuOpen = false
                                        renameTarget = album
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "删除相册",
                                            color = Color(0xFFC62828)
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        deleteTarget = album
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (createOpen) {
        AlbumNameDialog(
            title = "新建相册",
            initial = "",
            allStories = allStories,
            confirmLabel = "创建",
            onDismiss = { createOpen = false },
            onConfirm = { name, storyIds ->
                createOpen = false
                MyStoriesStore.createAlbum(name, storyIds)
            }
        )
    }
    val toRename = renameTarget
    if (toRename != null) {
        AlbumNameDialog(
            title = "相册改名",
            initial = toRename.name,
            allStories = emptyList(),
            confirmLabel = "改名",
            onDismiss = { renameTarget = null },
            onConfirm = { name, _ ->
                renameTarget = null
                MyStoriesStore.renameAlbum(toRename, name)
            }
        )
    }
    val toDelete = deleteTarget
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除相册？") },
            text = {
                Text(
                    "「${toDelete.name}」会被删掉，里面的动态" +
                        "本身不会删，还在你的资料页里。"
                )
            },
            confirmButton = {
                Button(onClick = {
                    deleteTarget = null
                    MyStoriesStore.deleteAlbum(toDelete)
                }) { Text("删除", color = Color(0xFFC62828)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun AlbumCover(album: StoryAlbumUi) {
    val path = album.iconPath
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) { decodeCached(path, 256) }
        }
    }
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF37474F)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                album.name.trim().firstOrNull()?.toString() ?: "相",
                color = Color.White, fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Album name dialog (create / rename). When [allStories]
 *  is non-empty it also offers a first batch of stories
 *  to put into the new album. Names are 1-12 characters
 *  (the tl's limit), counted and enforced here. */
@Composable
private fun AlbumNameDialog(
    title: String,
    initial: String,
    allStories: List<MyStoryUi>,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, List<Int>) -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    var picked by remember {
        mutableStateOf<Set<Int>>(emptySet())
    }
    val addable = allStories.filter { it.canBeAddedToAlbum }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(12) },
                    placeholder = { Text("相册名字（1–12 个字）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (addable.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "顺便把这些动态放进去（可不选）：",
                        fontSize = 13.sp, color = Color.Gray
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        LazyColumn {
                            items(addable, key = { it.storyId }) {
                                story ->
                                Row(
                                    verticalAlignment =
                                        Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            picked =
                                                if (story.storyId in picked) {
                                                    picked - story.storyId
                                                } else {
                                                    picked + story.storyId
                                                }
                                        }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Checkbox(
                                        checked =
                                            story.storyId in picked,
                                        onCheckedChange = { checked ->
                                            picked =
                                                if (checked) {
                                                    picked + story.storyId
                                                } else {
                                                    picked - story.storyId
                                                }
                                        }
                                    )
                                    Text(
                                        formatDayLabel(
                                            story.dateSeconds
                                        ) + " " + formatMsgTime(
                                            story.dateSeconds
                                        ) + if (story.isVideo) {
                                            " · 视频"
                                        } else {
                                            " · 照片"
                                        },
                                        fontSize = 13.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, picked.toList()) },
                enabled = name.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** One album's page (v0.9.35, B2): its stories as the
 *  same grid as the main list, an add-stories picker
 *  (stories not in this album that the server allows),
 *  and per-story removal through the story detail's
 *  album section. */
@Composable
private fun AlbumDetail(
    album: StoryAlbumUi,
    allStories: List<MyStoryUi>,
    onOpenStory: (Int) -> Unit
) {
    val context = LocalContext.current
    val albumStories by MyStoriesStore.albumStories.collectAsState()
    val loading by
        MyStoriesStore.albumStoriesLoading.collectAsState()
    val exhausted by
        MyStoriesStore.albumStoriesExhausted.collectAsState()
    var addPickerOpen by remember(album.id) {
        mutableStateOf(false)
    }
    // Reorder mode (v0.9.36, C3): entered only after the
    // store has loaded EVERY story of the album —
    // reorderStoryAlbumStories takes the complete ordered
    // id list, so a half-loaded prefix must never reach
    // it. Rows are uniform 68 dp, dragged vertically with
    // the same pitch-step pattern as the album strip.
    var reordering by remember(album.id) {
        mutableStateOf(false)
    }
    var preparingReorder by remember(album.id) {
        mutableStateOf(false)
    }
    var localStories by remember(album.id) {
        mutableStateOf<List<MyStoryUi>>(emptyList())
    }
    var draggingStoryId by remember(album.id) {
        mutableStateOf<Int?>(null)
    }
    var storyDragDelta by remember(album.id) {
        mutableStateOf(0f)
    }
    val storyPitchPx = with(LocalDensity.current) {
        68.dp.toPx()
    }
    BackHandler(enabled = reordering) { reordering = false }
    LaunchedEffect(album.id) { MyStoriesStore.openAlbum(album.id) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp
            )
        ) {
            Text(
                if (reordering) {
                    "拖动动态调整顺序"
                } else {
                    "共 ${albumStories.size} 条"
                },
                fontSize = 13.sp, color = Color.Gray,
                modifier = Modifier.weight(1f)
            )
            if (reordering) {
                TextButton(onClick = { reordering = false }) {
                    Text("取消")
                }
                TextButton(onClick = {
                    MyStoriesStore.reorderAlbumStories(
                        album.id,
                        localStories.map { it.storyId }
                    )
                    reordering = false
                }) {
                    Text("完成")
                }
            } else {
                if (albumStories.size > 1) {
                    TextButton(
                        onClick = {
                            preparingReorder = true
                            MyStoriesStore.loadAllAlbumStories(
                                album.id
                            ) { ok ->
                                preparingReorder = false
                                if (ok) {
                                    localStories = albumStories
                                    reordering = true
                                } else {
                                    Toast.makeText(
                                        context,
                                        "这个相册的动态没能全部加载下来，" +
                                            "先不能调整顺序，检查网络后再试。",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        enabled = !preparingReorder
                    ) {
                        Text(
                            if (preparingReorder) {
                                "正在加载全部…"
                            } else {
                                "调整顺序"
                            }
                        )
                    }
                }
                TextButton(onClick = { addPickerOpen = true }) {
                    Text("添加动态")
                }
            }
        }
        when {
            reordering -> LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(localStories, key = { it.storyId }) { story ->
                    val isDragging = draggingStoryId == story.storyId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY =
                                    if (isDragging) {
                                        storyDragDelta
                                    } else {
                                        0f
                                    }
                            }
                            .background(
                                if (isDragging) {
                                    Color(0x1A000000)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .pointerInput(story.storyId) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingStoryId = story.storyId
                                        storyDragDelta = 0f
                                    },
                                    onDragEnd = {
                                        draggingStoryId = null
                                        storyDragDelta = 0f
                                    },
                                    onDragCancel = {
                                        draggingStoryId = null
                                        storyDragDelta = 0f
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    storyDragDelta += dragAmount.y
                                    val shift =
                                        (storyDragDelta /
                                            storyPitchPx)
                                            .roundToInt()
                                    if (shift != 0) {
                                        val idx = localStories
                                            .indexOfFirst {
                                                it.storyId ==
                                                    story.storyId
                                            }
                                        if (idx >= 0) {
                                            val target =
                                                (idx + shift)
                                                    .coerceIn(
                                                        0,
                                                        localStories
                                                            .lastIndex
                                                    )
                                            if (target != idx) {
                                                localStories =
                                                    localStories
                                                        .toMutableList()
                                                        .apply {
                                                            add(
                                                                target,
                                                                removeAt(
                                                                    idx
                                                                )
                                                            )
                                                        }
                                                storyDragDelta -=
                                                    (target - idx) *
                                                        storyPitchPx
                                            }
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp)
                    ) {
                        val path = story.previewPath
                        val bitmap by produceState<
                            android.graphics.Bitmap?
                        >(initialValue = null, path) {
                            value = if (path == null) {
                                null
                            } else {
                                withContext(Dispatchers.IO) {
                                    decodeCached(path, 256)
                                }
                            }
                        }
                        val bmp = bitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF37474F))
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                java.text.SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm",
                                    java.util.Locale.getDefault()
                                ).format(
                                    java.util.Date(
                                        story.dateSeconds * 1000L
                                    )
                                ),
                                fontSize = 14.sp
                            )
                            Text(
                                if (story.isVideo) {
                                    "视频动态"
                                } else {
                                    "图片动态"
                                },
                                fontSize = 12.sp, color = Color.Gray
                            )
                        }
                        Text(
                            "≡",
                            fontSize = 20.sp, color = Color.Gray
                        )
                    }
                }
            }
            loading && albumStories.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            albumStories.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "这个相册还是空的。点右上角「添加动态」把动态放进来，" +
                        "也可以打开任意一条动态，在它的相册区里加入。",
                    color = Color.Gray, fontSize = 14.sp
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize()
            ) {
                items(albumStories, key = { it.storyId }) { story ->
                    StoryGridCell(story) {
                        onOpenStory(story.storyId)
                    }
                }
                if (!exhausted) {
                    item {
                        TextButton(
                            onClick = {
                                MyStoriesStore.loadMoreAlbumStories()
                            },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (loading) "正在加载…" else "加载更多"
                            )
                        }
                    }
                }
            }
        }
    }
    if (addPickerOpen) {
        val candidates = allStories.filter {
            it.canBeAddedToAlbum && album.id !in it.albumIds
        }
        var picked by remember(album.id) {
            mutableStateOf<Set<Int>>(emptySet())
        }
        AlertDialog(
            onDismissRequest = { addPickerOpen = false },
            title = { Text("添加到「${album.name}」") },
            text = {
                if (candidates.isEmpty()) {
                    Text(
                        "暂时没有能加进来的动态：要么都已经在这个相册里，" +
                            "要么服务器没开放其余动态的加入。",
                        fontSize = 13.5.sp, color = Color.Gray
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    ) {
                        items(candidates, key = { it.storyId }) {
                            story ->
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        picked =
                                            if (story.storyId in picked) {
                                                picked - story.storyId
                                            } else {
                                                picked + story.storyId
                                            }
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                Checkbox(
                                    checked = story.storyId in picked,
                                    onCheckedChange = { checked ->
                                        picked =
                                            if (checked) {
                                                picked + story.storyId
                                            } else {
                                                picked - story.storyId
                                            }
                                    }
                                )
                                Text(
                                    formatDayLabel(story.dateSeconds) +
                                        " " + formatMsgTime(
                                            story.dateSeconds
                                        ) + if (story.isVideo) {
                                            " · 视频"
                                        } else {
                                            " · 照片"
                                        },
                                    fontSize = 13.5.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        addPickerOpen = false
                        MyStoriesStore.addToAlbum(
                            album.id, picked.toList()
                        )
                    },
                    enabled = picked.isNotEmpty()
                ) { Text("加入") }
            },
            dismissButton = {
                TextButton(onClick = { addPickerOpen = false }) {
                    Text("取消")
                }
            }
        )
    }
}
