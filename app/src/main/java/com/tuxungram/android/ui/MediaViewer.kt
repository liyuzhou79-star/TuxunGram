package com.tuxungram.android.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.tuxungram.android.td.MessageStore
import com.tuxungram.android.td.MsgItem
import com.tuxungram.android.td.MsgKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Full-screen photo viewer (v0.7.0, F2), modelled on the mature
 * clients: swipe left/right between the chat's photos, pinch or
 * double-tap to zoom, drag up/down to dismiss, sender + time in
 * the header, caption at the bottom. The list comes straight
 * from MessageStore's live flow, so a photo whose download
 * finishes while the viewer is open appears on its own.
 *
 * Videos deliberately do NOT come here: tapping a video bubble
 * hands the downloaded file to the system player (ACTION_VIEW),
 * which is the other half of the F2 requirement.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(initialId: Long, onClose: () -> Unit) {
    val messages by MessageStore.messages.collectAsState()
    val photos = messages.filter { it.kind == MsgKind.PHOTO }
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val initialPage = photos.indexOfFirst { it.id == initialId }
        .coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialPage
    ) { photos.size }
    // The current page's zoom level gates pager swiping (a zoomed
    // photo pans instead of flipping pages).
    var currentScale by remember { mutableFloatStateOf(1f) }
    // Unzoomed vertical drag distance; drives both the photo's
    // offset and the backdrop fade, and closes past a threshold.
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
            PhotoPage(
                item = photos[page],
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

        val current = photos.getOrNull(pagerState.currentPage)
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
            if (photos.size > 1) {
                Text(
                    "${pagerState.currentPage + 1} / ${photos.size}",
                    color = Color(0xFFB0BEC5),
                    fontSize = 13.sp
                )
                Spacer(Modifier.width(12.dp))
            }
        }

        // Caption of the current photo, if it has one.
        val caption = current?.text?.takeIf { it.isNotEmpty() }
        if (caption != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    caption,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 3
                )
            }
        }
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
