package com.tuxungram.android.ui

import androidx.compose.ui.text.style.TextOverflow
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/**
 * In-app video player (v0.9.1, J3). Tapping a downloaded video
 * bubble used to hand the file to whatever system player the
 * phone had; it now plays here, in a full-screen page with the
 * same shell language as the photo viewer (black backdrop,
 * padded header, back to close). The player itself is the
 * framework VideoView — no media dependency added — with basic
 * controls: play/pause (button or tap on the video), a
 * draggable progress bar, and close. The system player remains
 * available from the bubble's long-press menu ("用其他播放器
 * 打开").
 */
@Composable
fun VideoPlayerScreen(path: String, title: String, onClose: () -> Unit) {
    var view by remember { mutableStateOf<VideoView?>(null) }
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var seeking by remember { mutableStateOf(false) }

    BackHandler { onClose() }
    DisposableEffect(Unit) {
        onDispose { view?.stopPlayback() }
    }
    // Progress ticker: the bar follows the player unless the
    // user is currently dragging it.
    LaunchedEffect(view) {
        while (true) {
            delay(300)
            val v = view ?: continue
            if (!seeking) {
                positionMs = v.currentPosition.coerceAtLeast(0)
            }
            if (v.duration > 0) durationMs = v.duration
        }
    }

    fun togglePlay() {
        val v = view ?: return
        if (v.isPlaying) {
            v.pause()
            playing = false
        } else {
            v.start()
            playing = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setVideoPath(path)
                    setOnPreparedListener { mp ->
                        durationMs = mp.duration
                        start()
                        playing = true
                    }
                    setOnCompletionListener {
                        playing = false
                        positionMs = durationMs
                    }
                    view = this
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable { togglePlay() }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                Text(
                    title.ifEmpty { "视频" },
                    color = Color.White,
                    fontSize = 15.sp,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0x66000000))
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (playing) "⏸" else "▶",
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier
                    .clickable { togglePlay() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                formatDuration(positionMs / 1000),
                color = Color.White,
                fontSize = 12.sp
            )
            Slider(
                value = if (durationMs > 0) {
                    positionMs.toFloat() / durationMs.toFloat()
                } else {
                    0f
                },
                onValueChange = { fraction ->
                    seeking = true
                    if (durationMs > 0) {
                        positionMs = (fraction * durationMs).toInt()
                    }
                },
                onValueChangeFinished = {
                    if (durationMs > 0) {
                        view?.seekTo(positionMs)
                    }
                    seeking = false
                },
                modifier = Modifier.weight(1f)
            )
            Text(
                formatDuration(durationMs / 1000),
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}
