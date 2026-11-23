package com.tuxungram.android.ui

import androidx.compose.ui.text.style.TextOverflow
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.MediaContextStore
import com.tuxungram.android.td.MsgItem
import com.tuxungram.android.td.MsgKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Full-screen media viewer (v0.7.0, F2; whole-conversation
 * context v0.9.31, X2), modelled on the mature clients:
 * swipe left/right through the chat's photos AND videos,
 * pinch or double-tap to zoom a photo, drag up/down to
 * dismiss, sender + time in the header, caption at the
 * bottom, and a 第 n / 共 m 张 counter up top.
 *
 * The media stream comes from MediaContextStore
 * (searchChatMessages PhotoAndVideo paging over the whole
 * conversation), not from the chat's loaded window, so
 * media far outside the visible history is reachable;
 * swiping near the oldest loaded end pulls the next page
 * automatically. Display order: when the tapped message
 * belongs to an album, the album group comes first (in
 * chat order), then every other media item in chat order —
 * the group is what the user was looking at, the rest of
 * the conversation follows. Video pages show the still
 * with a play button; playback itself stays in the in-app
 * VideoPlayerScreen, opened over this viewer. Thread and
 * topic surfaces are out of scope and keep their players.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(
    chatId: Long,
    anchor: MsgItem,
    albumGroup: List<MsgItem>,
    onClose: () -> Unit,
    onPlayVideo: (MsgItem) -> Unit
) {
    LaunchedEffect(chatId, anchor.id) {
        MediaContextStore.open(chatId, anchor)
    }
    val contextItems by MediaContextStore.items.collectAsState()
    val exhausted by MediaContextStore.exhausted.collectAsState()
    MediaPager(
        contextItems = contextItems,
        exhausted = exhausted,
        anchor = anchor,
        albumGroup = albumGroup,
        onLoadMore = { MediaContextStore.loadMore() },
        onClose = onClose,
        onPlayVideo = onPlayVideo
    )
}

/** The same cross-message viewer fed from an explicit
 *  item list instead of a chat-wide search (v0.9.32, Y6):
 *  topic pages and comment threads open media with the
 *  context limited to THEIR loaded window — the host
 *  passes its messages' media items, whether older pages
 *  remain, and its own load-older callback, which the
 *  pager calls near the oldest end exactly like the chat
 *  version calls the store. */
@Composable
fun MediaViewerListScreen(
    items: List<MsgItem>,
    anchor: MsgItem,
    albumGroup: List<MsgItem>,
    exhausted: Boolean,
    onLoadMore: () -> Unit,
    onClose: () -> Unit,
    onPlayVideo: (MsgItem) -> Unit
) {
    MediaPager(
        contextItems = items,
        exhausted = exhausted,
        anchor = anchor,
        albumGroup = albumGroup,
        onLoadMore = onLoadMore,
        onClose = onClose,
        onPlayVideo = onPlayVideo
    )
}

/** The shared pager body: album group first (in id
 *  order), then the rest of the context; paging near the
 *  oldest end asks the source for more. */
@Composable
private fun MediaPager(
    contextItems: List<MsgItem>,
    exhausted: Boolean,
    anchor: MsgItem,
    albumGroup: List<MsgItem>,
    onLoadMore: () -> Unit,
    onClose: () -> Unit,
    onPlayVideo: (MsgItem) -> Unit
) {
    val display = remember(contextItems, albumGroup) {
        val groupIds = albumGroup.map { it.id }.toSet()
        val group = albumGroup.sortedBy { it.id }
        val rest = contextItems
            .filter { it.id !in groupIds }
            .sortedBy { it.id }
        group + rest
    }
    if (display.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val initialPage = display.indexOfFirst { it.id == anchor.id }
        .coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialPage
    ) { display.size }
    // The item currently on screen, tracked by id: paging
    // in older media prepends pages and shifts every index,
    // so the pager is re-anchored to the same item whenever
    // the display list changes under it.
    var currentItemId by remember {
        mutableLongStateOf(anchor.id)
    }
    LaunchedEffect(pagerState.currentPage, display) {
        display.getOrNull(pagerState.currentPage)?.let {
            currentItemId = it.id
        }
    }
    LaunchedEffect(display) {
        val idx = display.indexOfFirst { it.id == currentItemId }
        if (idx >= 0 && idx != pagerState.currentPage &&
            !pagerState.isScrollInProgress
        ) {
            pagerState.scrollToPage(idx)
        }
    }
    // Near the oldest loaded end: pull the next page.
    LaunchedEffect(pagerState.currentPage, display.size) {
        if (pagerState.currentPage <= 5) {
            onLoadMore()
        }
    }
    // The current page's zoom level gates pager swiping (a
    // zoomed photo pans instead of flipping pages).
    var currentScale by remember { mutableFloatStateOf(1f) }
    // Unzoomed vertical drag distance; drives both the
    // photo's offset and the backdrop fade, and closes past
    // a threshold.
    var closeDrag by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(pagerState.currentPage) {
        currentScale = 1f
        closeDrag = 0f
    }
    BackHandler { onClose() }

    val fade = (abs(closeDrag) / 700f).coerceIn(0f, 0.55f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 1f - fade))
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = currentScale <= 1.01f,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = display[page]
            if (item.kind == MsgKind.VIDEO) {
                VideoPage(item = item, onPlayVideo = onPlayVideo)
            } else {
                PhotoPage(
                    item = item,
                    onScaleChange = { scale ->
                        if (page == pagerState.currentPage) {
                            currentScale = scale
                        }
                    },
                    closeDrag = closeDrag,
                    onCloseDragChange = { closeDrag = it },
                    onClose = onClose
                )
            }
        }

        val current = display.getOrNull(pagerState.currentPage)
        // Header: back, sender + time, page counter.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "关闭",
                    tint = Color.White
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        current == null -> ""
                        current.outgoing -> "我"
                        current.senderName.isNotEmpty() ->
                            current.senderName
                        else -> "图片"
                    },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
                if (current != null) {
                    Text(
                        "${formatDayLabel(current.dateSeconds)} " +
                            formatMsgTime(current.dateSeconds),
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp
                    )
                }
            }
            if (display.size > 1) {
                Text(
                    "${pagerState.currentPage + 1} / " +
                        "${display.size}" +
                        if (exhausted) "" else "+",
                    color = Color(0xFFB0BEC5),
                    fontSize = 13.sp
                )
                Spacer(Modifier.width(12.dp))
            }
        }

        // Caption of the current item, if it has one —
        // rendered through the shared rich-text builder like
        // every other surface (regression fix R4, v0.9.6), in
        // white to stay readable over the media.
        val captionMsg = current?.takeIf { it.text.isNotEmpty() }
        if (captionMsg != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                RichMessageText(
                    captionMsg,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 3
                )
            }
        }
    }
}

/** One video page of the viewer (X2): the video's still
 *  (its thumbnail, the minithumbnail until that lands)
 *  with a central play button. A video whose file is not
 *  on disk yet downloads first — the button says so —
 *  through the download center's queue; once the file
 *  lands the same button plays it in the player screen. */
@Composable
private fun VideoPage(
    item: MsgItem,
    onPlayVideo: (MsgItem) -> Unit
) {
    val stillPath = item.thumbPath
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, stillPath, item.miniThumbData
    ) {
        value = withContext(Dispatchers.IO) {
            when {
                stillPath != null -> decodeSampled(stillPath)
                item.miniThumbData.isNotEmpty() ->
                    decodeMiniThumb(item.miniThumbData)
                else -> null
            }
        }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color(0x66000000), CircleShape)
                    .clickable {
                        if (item.filePath != null) {
                            onPlayVideo(item)
                        } else {
                            MediaContextStore.downloadVideo(item)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    item.filePath != null ->
                        formatDuration(item.durationSec)
                    item.fileDownloading -> "正在下载…"
                    else -> "点按下载视频 · " +
                        formatDuration(item.durationSec)
                },
                color = Color.White,
                fontSize = 13.sp
            )
        }
    }
}

/** Decode a minithumbnail (base64 JPEG bytes as TDLib
 *  ships them) — the same tiny decode the chat's album
 *  cells use, kept local to this file. */
private fun decodeMiniThumb(
    data: String
): android.graphics.Bitmap? {
    return try {
        val bytes = android.util.Base64.decode(
            data, android.util.Base64.DEFAULT
        )
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (t: Throwable) {
        null
    }
}

@Composable
private fun PhotoPage(
    item: MsgItem,
    onScaleChange: (Float) -> Unit,
    closeDrag: Float,
    onCloseDragChange: (Float) -> Unit,
    onClose: () -> Unit
) {
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var panX by remember(item.id) { mutableFloatStateOf(0f) }
    var panY by remember(item.id) { mutableFloatStateOf(0f) }
    var dragY by remember(item.id) { mutableFloatStateOf(0f) }
    val path = item.photoPath
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) { decodeSampled(path) }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(item.id) {
                // Hand-rolled transform handling (the callback
                // overload of detectTransformGestures is not in
                // this Compose version): pinch zooms, pan moves a
                // zoomed photo, and a plain one-finger vertical
                // pan while unzoomed becomes the drag-to-close
                // gesture, decided when the gesture ends.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (scale > 1.01f || zoom != 1f) {
                            val newScale = (scale * zoom)
                                .coerceIn(1f, 5f)
                            scale = newScale
                            onScaleChange(newScale)
                            if (newScale > 1.01f) {
                                panX = (panX + pan.x)
                                    .coerceIn(-1200f, 1200f)
                                panY = (panY + pan.y)
                                    .coerceIn(-1200f, 1200f)
                            } else {
                                panX = 0f
                                panY = 0f
                            }
                            dragY = 0f
                            onCloseDragChange(0f)
                            event.changes.forEach { it.consume() }
                        } else if (event.changes.size == 1) {
                            dragY += pan.y
                            onCloseDragChange(dragY)
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale <= 1.01f && abs(dragY) > 140f) {
                        onClose()
                    } else {
                        dragY = 0f
                        onCloseDragChange(0f)
                    }
                }
            }
            .pointerInput(item.id) {
                detectTapGestures(
                    onDoubleTap = {
                        val target = if (scale > 1.01f) 1f else 2.5f
                        scale = target
                        onScaleChange(target)
                        if (target == 1f) {
                            panX = 0f
                            panY = 0f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(
                            panX.roundToInt(),
                            (panY + dragY).roundToInt()
                        )
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale)
            )
        } else {
            Text("图片加载中…", color = Color(0xFFB0BEC5), fontSize = 14.sp)
        }
    }
}

/** Decode a photo for the viewer, downsampled so a huge camera
 *  original cannot OOM the viewer (2048 px on the long edge is
 *  plenty for a phone screen). */
private fun decodeSampled(
    path: String,
    maxDim: Int = 2048
): android.graphics.Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxDim ||
            bounds.outHeight / sample > maxDim
        ) {
            sample *= 2
        }
        BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    } catch (t: Throwable) {
        null
    }
}
