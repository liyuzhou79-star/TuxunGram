package com.tuxungram.android.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.VideoTrim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** mm:ss for the range labels. */
private fun trimTimeLabel(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/**
 * Pre-send video trimmer (v0.9.33, Z1): two handles pick
 * the start and end on a film strip; export is a packet
 * copy (see td/VideoTrim), so it is quick and lossless.
 * The screen says plainly that the cut snaps to keyframes
 * (about a second or two of slack) and which containers
 * can ride (MP4 family including MOV/3GP sources, and
 * WebM since v0.9.57) — a file in anything else gets the
 * explanation and a straight path back to sending the
 * original.
 */
@Composable
fun VideoTrimScreen(
    path: String,
    fileName: String,
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
    // Export-state mirror for hosts that clean up around
    // the screen (v0.9.56, A3): while an export runs, the
    // source file is being read and must not be reclaimed.
    onExportingChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info by produceState<VideoTrim.Info?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { VideoTrim.probe(path) }
    }
    val durationMs = info?.durationMs ?: 0L
    var range by remember { mutableStateOf(0f..1f) }
    // Drop the audio track on export (v0.9.34, A2): the
    // muxer simply never gets an audio track. Only offered
    // when the file actually has one (probe says so).
    var muteAudio by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(exporting) {
        onExportingChange(exporting)
    }
    var errorText by remember { mutableStateOf<String?>(null) }

    // Film strip: eight frames spread over the whole clip.
    val frames by produceState<List<Bitmap>>(
        initialValue = emptyList(), path, durationMs
    ) {
        value = if (durationMs <= 0) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                (0 until 8).mapNotNull { i ->
                    val at = (durationMs * (i + 0.5) / 8).toLong()
                    VideoTrim.frameAt(path, at, 144)
                }
            }
        }
    }
    // Big preview follows the start handle (bucketed so a
    // drag does not thrash the decoder).
    val startMs = (range.start * durationMs).toLong()
    val endMs = (range.endInclusive * durationMs).toLong()
    val previewBucket = startMs / 250
    val preview by produceState<Bitmap?>(
        initialValue = null, path, previewBucket, durationMs
    ) {
        value = if (durationMs <= 0) {
            null
        } else {
            withContext(Dispatchers.IO) {
                VideoTrim.frameAt(path, previewBucket * 250, 900)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111111))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }
            Text(
                "剪辑视频",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        val currentInfo = info
        when {
            currentInfo == null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
            currentInfo.family == VideoTrim.Family.OTHER -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp)
            ) {
                Text(
                    "这个视频的格式暂时不能剪辑。",
                    color = Color.White, fontSize = 15.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "剪辑目前支持 MP4、MOV、3GP 和 WebM，都是直接拷贝不重新编码。可以直接发送原视频，内容不受影响。",
                    color = Color(0xFFBDBDBD), fontSize = 13.sp
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onCancel) {
                    Text("返回，直接发送原视频")
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = preview
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "视频预览",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    if (frames.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            frames.forEach { frame ->
                                Image(
                                    bitmap = frame.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .padding(horizontal = 1.dp)
                                )
                            }
                        }
                    }
                    RangeSlider(
                        value = range,
                        onValueChange = { range = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "开始 ${trimTimeLabel(startMs)}",
                            color = Color.White, fontSize = 13.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "时长 ${trimTimeLabel(endMs - startMs)}",
                            color = Color(0xFFBDBDBD), fontSize = 13.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "结束 ${trimTimeLabel(endMs)}",
                            color = Color.White, fontSize = 13.sp
                        )
                    }
                    Text(
                        "剪辑不重新编码，画质不变、速度快；起止点会" +
                            "对齐到最近的关键帧，可能有一两秒误差。",
                        color = Color(0xFF9E9E9E), fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (currentInfo.hasAudio) {
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Text(
                                "去掉声音",
                                color = Color.White, fontSize = 14.sp
                            )
                            Spacer(Modifier.weight(1f))
                            Switch(
                                checked = muteAudio,
                                onCheckedChange = { muteAudio = it }
                            )
                        }
                    }
                    if (errorText != null) {
                        Text(
                            errorText!!,
                            color = Color(0xFFEF9A9A), fontSize = 13.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 8.dp)
                    ) {
                        TextButton(
                            onClick = onCancel,
                            enabled = !exporting
                        ) {
                            Text("取消", color = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            enabled = !exporting && endMs > startMs,
                            onClick = {
                                exporting = true
                                errorText = null
                                scope.launch {
                                    val outFile = File(
                                        context.cacheDir,
                                        "trim_${System.currentTimeMillis()}." +
                                            currentInfo.outExtension
                                    )
                                    val error = withContext(
                                        Dispatchers.IO
                                    ) {
                                        VideoTrim.trim(
                                            path,
                                            outFile.absolutePath,
                                            startMs,
                                            endMs,
                                            muteAudio = muteAudio
                                        )
                                    }
                                    exporting = false
                                    if (error == null) {
                                        onDone(outFile.absolutePath)
                                    } else {
                                        outFile.delete()
                                        errorText = error
                                    }
                                }
                            }
                        ) {
                            if (exporting) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier
                                        .width(16.dp)
                                        .height(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (exporting) "正在导出…" else "完成剪辑")
                        }
                    }
                }
            }
        }
    }
}
