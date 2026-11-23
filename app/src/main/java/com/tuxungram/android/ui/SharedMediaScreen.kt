package com.tuxungram.android.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.ChatStore
import com.tuxungram.android.td.MediaTab
import com.tuxungram.android.td.MsgItem
import com.tuxungram.android.td.MsgKind
import com.tuxungram.android.td.SharedMediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared media page (v0.9.3, M4): a chat's photos, videos,
 *  files and links in four tabs, fed by SharedMediaStore's
 *  filtered searchChatMessages paging. Photos open a pager
 *  over this page's own photo list (the chat media viewer is
 *  bound to the open conversation, so this page carries its
 *  own); videos hand over to the in-app player once
 *  downloaded; files download then open; links jump to the
 *  browser. */
@Composable
fun SharedMediaScreen(chatId: Long, onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val allItems by SharedMediaStore.items.collectAsState()
    val exhausted by SharedMediaStore.exhausted.collectAsState()
    val error by SharedMediaStore.error.collectAsState()
    var tab by remember { mutableStateOf(MediaTab.PHOTO) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var videoItem by remember { mutableStateOf<MsgItem?>(null) }

    LaunchedEffect(chatId) { SharedMediaStore.open(chatId) }
    LaunchedEffect(chatId, tab) { SharedMediaStore.loadMore(tab) }

    val chatTitle = ChatStore.itemOf(chatId)?.title ?: ""
    val tabItems = allItems[tab] ?: emptyList()
    val tabExhausted = exhausted.contains(tab)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column {
                    Text("共享媒体", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    if (chatTitle.isNotEmpty()) {
                        Text(chatTitle, color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                for (candidate in MediaTab.entries) {
                    TextButton(
                        onClick = { tab = candidate },
                        enabled = tab != candidate
                    ) {
                        Icon(
                            when (candidate) {
                                MediaTab.PHOTO -> ImageIcon
                                MediaTab.VIDEO -> Icons.Filled.PlayArrow
                                MediaTab.FILE -> ClipIcon
                                MediaTab.LINK -> LinkIcon
                            },
                            contentDescription = null,
                            tint = if (tab == candidate) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Gray
                            },
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            candidate.label,
                            fontWeight = if (tab == candidate) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    }
                }
            }
            if (error != null) {
                Text(
                    error!!,
                    color = Color(0xFFC62828), fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            when (tab) {
                MediaTab.PHOTO -> {
                    if (tabItems.isEmpty()) {
                        EmptyOrLoading(
                        tabExhausted,
                        onRetry = if (error != null) {
                            { SharedMediaStore.loadMore(tab) }
                        } else {
                            null
                        }
                    )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(items = tabItems, key = { it.id }) { item ->
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .padding(1.dp)
                                        // Theme surface, not a
                                        // hard-coded near-white:
                                        // the light tile flashed
                                        // in dark mode (v0.9.41, H3).
                                        .background(
                                            MaterialTheme.colorScheme
                                                .surfaceVariant
                                        )
                                        .clickable {
                                            viewerIndex = tabItems.indexOf(item)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val path = item.photoPath
                                    if (path != null) {
                                        GridThumb(path = path)
                                    } else {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            if (!tabExhausted) {
                                item(key = "more") {
                                    TextButton(
                                        onClick = { SharedMediaStore.loadMore(tab) }
                                    ) { Text("加载更多") }
                                }
                            }
                        }
                    }
                }
                MediaTab.VIDEO, MediaTab.FILE -> {
                    if (tabItems.isEmpty()) {
                        EmptyOrLoading(
                        tabExhausted,
                        onRetry = if (error != null) {
                            { SharedMediaStore.loadMore(tab) }
                        } else {
                            null
                        }
                    )
                    } else {
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .lazyListScrollbar(listState)
                        ) {
                            items(items = tabItems, key = { it.id }) { item ->
                                FileRow(
                                    item = item,
                                    onTap = {
                                        val path = item.filePath
                                        when {
                                            path != null && item.kind == MsgKind.VIDEO ->
                                                videoItem = item
                                            path != null ->
                                                openDownloadedFile(context, path)
                                            else -> SharedMediaStore.download(item)
                                        }
                                    }
                                )
                            }
                            if (!tabExhausted) {
                                item(key = "more") {
                                    TextButton(
                                        onClick = { SharedMediaStore.loadMore(tab) },
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) { Text("加载更多") }
                                }
                            }
                        }
                    }
                }
                MediaTab.LINK -> {
                    if (tabItems.isEmpty()) {
                        EmptyOrLoading(
                        tabExhausted,
                        onRetry = if (error != null) {
                            { SharedMediaStore.loadMore(tab) }
                        } else {
                            null
                        }
                    )
                    } else {
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .lazyListScrollbar(listState)
                        ) {
                            items(items = tabItems, key = { it.id }) { item ->
                                val url = firstUrlOf(item)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = url != null) {
                                            if (url != null) {
                                                try {
                                                    com.tuxungram.android.td
                                                        .LinkRouter.open(
                                                            context, url
                                                        )
                                                } catch (t: Throwable) {
                                                }
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    if (url != null) {
                                        Text(
                                            url,
                                            color = Color(0xFF1976D2),
                                            fontSize = 14.sp
                                        )
                                    }
                                    RichMessageText(
                                        item,
                                        fontSize = 13.sp,
                                        color = Color.Gray,
                                        maxLines = 2
                                    )
                                }
                            }
                            if (!tabExhausted) {
                                item(key = "more") {
                                    TextButton(
                                        onClick = { SharedMediaStore.loadMore(tab) },
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) { Text("加载更多") }
                                }
                            }
                        }
                    }
                }
            }
        }

        val photos = allItems[MediaTab.PHOTO] ?: emptyList()
        val vIndex = viewerIndex
        if (vIndex != null && photos.isNotEmpty()) {
            SharedPhotoViewer(
                photos = photos,
                initialIndex = vIndex.coerceIn(0, photos.size - 1),
                onClose = { viewerIndex = null }
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

@Composable
private fun EmptyOrLoading(
    exhausted: Boolean,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (exhausted) {
            EmptyState(
                ImageIcon, "这一类还没有内容",
                "这个对话里还没有这一类的消息。"
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在加载…", color = Color.Gray)
                // After a failed first page the store no
                // longer marks the tab exhausted (v0.9.41,
                // H1①), so this state can be a failure
                // rather than a load in flight — give it the
                // retry the error text above promises.
                if (onRetry != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRetry) {
                        Text("重试")
                    }
                }
            }
        }
    }
}

@Composable
private fun GridThumb(path: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        value = withContext(Dispatchers.IO) { decodeCached(path) }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun FileRow(item: MsgItem, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (item.kind == MsgKind.VIDEO) {
                    Icons.Filled.PlayArrow
                } else {
                    ClipIcon
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.fileName.ifEmpty {
                    if (item.kind == MsgKind.VIDEO) "视频" else "文件"
                },
                fontSize = 15.sp
            )
            Text(
                buildString {
                    if (item.fileSize > 0) append(formatBytes(item.fileSize))
                    if (item.durationSec > 0) {
                        if (isNotEmpty()) append(" · ")
                        append(formatDuration(item.durationSec))
                    }
                    if (item.filePath == null) {
                        if (isNotEmpty()) append(" · ")
                        append(if (item.fileDownloading) "下载中…" else "点按下载")
                    }
                },
                color = Color.Gray, fontSize = 12.sp
            )
            if (item.text.isNotEmpty()) {
                RichMessageText(
                    item, color = Color.Gray, fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
        if (item.filePath == null && !item.fileDownloading) {
            Icon(
                DownloadIcon, contentDescription = "下载",
                tint = Color.Gray, modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** First tappable URL of a link-tab message: a textUrl
 *  entity's target, or the text span of a url entity. */
private fun firstUrlOf(item: MsgItem): String? {
    for (entity in item.entities) {
        when (entity.kind) {
            "textUrl" -> if (entity.url.isNotEmpty()) return entity.url
            "url" -> {
                val start = entity.offset
                val end = (entity.offset + entity.length)
                    .coerceAtMost(item.text.length)
                if (start in 0 until end) {
                    return item.text.substring(start, end)
                }
            }
        }
    }
    return null
}

/** Pager over this page's photos with pinch zoom — the
 *  shared-media counterpart of the chat media viewer (which
 *  is bound to the open conversation's MessageStore). */
@Composable
private fun SharedPhotoViewer(
    photos: List<MsgItem>,
    initialIndex: Int,
    onClose: () -> Unit
) {
    BackHandler { onClose() }
    val pagerState = rememberPagerState(
        initialPage = initialIndex
    ) { photos.size }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = photos[page]
            var scale by remember(item.id) { mutableFloatStateOf(1f) }
            var offsetX by remember(item.id) { mutableFloatStateOf(0f) }
            var offsetY by remember(item.id) { mutableFloatStateOf(0f) }
            val path = item.photoPath
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(item.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (path != null) {
                    val bitmap by produceState<android.graphics.Bitmap?>(
                        initialValue = null, path
                    ) {
                        value = withContext(Dispatchers.IO) {
                            decodeDownsampled(path)
                        }
                    }
                    val bmp = bitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                        )
                    }
                } else {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "关闭",
                    tint = Color.White
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${pagerState.currentPage + 1} / ${photos.size}",
                color = Color.White, fontSize = 14.sp
            )
        }
    }
}
