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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
 *
 * Playback extras (v0.9.52, W2): a 1x/1.5x/2x speed chip and a
 * mute toggle ride the MediaPlayer the VideoView prepares
 * (playbackParams / setVolume — platform APIs, no new
 * dependency), and a rotate button flips the activity to
 * landscape and back on the user's word only; the orientation
 * is released when the player closes. Round video notes play
 * here too (they open through the media viewer), so they get
 * the same speed control as regular videos.
 */
@Composable
fun VideoPlayerScreen(path: String, title: String, onClose: () -> Unit) {
    var view by remember { mutableStateOf<VideoView?>(null) }
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var seeking by remember { mutableStateOf(false) }
    var mediaPlayer by remember {
        mutableStateOf<android.media.MediaPlayer?>(null)
    }
    var speed by remember { mutableFloatStateOf(1f) }
    var muted by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = remember(context) {
        var c: android.content.Context? = context
        while (c is android.content.ContextWrapper &&
            c !is android.app.Activity
        ) {
            c = c.baseContext
        }
        c as? android.app.Activity
    }

    fun applySpeed(mp: android.media.MediaPlayer, value: Float) {
        try {
            mp.playbackParams = mp.playbackParams.setSpeed(value)
        } catch (t: Throwable) {
            // Same stance as the voice player: a ROM that
            // rejects the params keeps the old speed instead
            // of failing playback.
        }
    }

    fun applyMute(mp: android.media.MediaPlayer, on: Boolean) {
        try {
            if (on) mp.setVolume(0f, 0f) else mp.setVolume(1f, 1f)
        } catch (t: Throwable) {
        }
    }

    BackHandler { onClose() }
    DisposableEffect(Unit) {
        onDispose {
            view?.stopPlayback()
            // Release the orientation the rotate button took.
            activity?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    fun toggleRotate() {
        landscape = !landscape
        activity?.requestedOrientation = if (landscape) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
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
                        mediaPlayer = mp
                        durationMs = mp.duration
                        applySpeed(mp, speed)
                        applyMute(mp, muted)
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
                // Accessibility (v0.9.60, E2): the full-screen
                // tap toggles playback — name it for readers.
                .semantics {
                    contentDescription = if (playing) "暂停" else "播放"
                    role = Role.Button
                }
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
            PlayerGlyphButton(
                label = if (playing) "暂停" else "播放",
                glyph = if (playing) "⏸" else "▶",
                fontSize = 20
            ) { togglePlay() }
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
            Spacer(Modifier.width(10.dp))
            // Mute toggle (v0.9.52, W2).
            PlayerGlyphButton(
                label = if (muted) "取消静音" else "静音",
                glyph = if (muted) "🔇" else "🔊",
                fontSize = 16
            ) {
                muted = !muted
                mediaPlayer?.let { applyMute(it, muted) }
            }
            // Speed chip (v0.9.52, W2): cycles 1x -> 1.5x -> 2x.
            PlayerGlyphButton(
                label = "播放速度，当前 " +
                    if (speed == speed.toInt().toFloat()) {
                        "${speed.toInt()} 倍"
                    } else {
                        "$speed 倍"
                    },
                glyph = if (speed == speed.toInt().toFloat()) {
                    "${speed.toInt()}x"
                } else {
                    "${speed}x"
                },
                fontSize = 13
            ) {
                speed = when (speed) {
                    1f -> 1.5f
                    1.5f -> 2f
                    else -> 1f
                }
                mediaPlayer?.let { applySpeed(it, speed) }
            }
            // Rotate toggle (v0.9.52, W2): the only way the
            // player changes orientation — never automatic.
            PlayerGlyphButton(
                label = "旋转画面",
                glyph = "⟳",
                fontSize = 17
            ) { toggleRotate() }
        }
    }
}

/** One control-bar glyph button (v0.9.60, E2): the bar's
 *  controls used to be bare clickable glyph Texts — hit
 *  areas around 30dp and nothing a screen reader could
 *  name. Each now sits in a 48dp box with a spoken label;
 *  the glyph itself is decorative inside it. */
@Composable
private fun PlayerGlyphButton(
    label: String,
    glyph: String,
    fontSize: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable { onClick() }
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph,
            color = Color.White,
            fontSize = fontSize.sp
        )
    }
}
