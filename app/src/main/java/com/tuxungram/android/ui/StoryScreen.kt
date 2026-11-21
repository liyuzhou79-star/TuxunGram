package com.tuxungram.android.ui

import android.graphics.BitmapFactory
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tuxungram.android.td.ChatStore
import com.tuxungram.android.td.StoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Full-screen story viewer (Batch 3, C5): tap right/left to move
 * between one chat's active stories, photos advance on their
 * own after a few seconds, videos play with the platform
 * player. Viewing only — publishing is not implemented.
 */
@Composable
fun StoryViewerScreen(chatId: Long, onBack: () -> Unit) {
    BackHandler { onBack() }
    val stories by StoryStore.viewerStories.collectAsState()
    val chat = ChatStore.itemOf(chatId)
    var index by remember(chatId) { mutableIntStateOf(0) }

    LaunchedEffect(chatId) { StoryStore.openChat(chatId) }

    val current = stories.getOrNull(index)
    // Photos advance automatically once they are on screen.
    LaunchedEffect(index, current?.localPath, current?.isVideo) {
        if (current != null && !current.isVideo && current.localPath != null) {
            delay(5000)
            if (index < stories.lastIndex) index++ else onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            current == null -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
            current.localPath == null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text("正在加载…", color = Color.White, fontSize = 13.sp)
                }
            }
            current.isVideo -> {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoPath(current.localPath)
                            setOnPreparedListener { it.isLooping = true }
                            start()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                val bitmap by produceState<android.graphics.Bitmap?>(
                    initialValue = null, current.localPath
                ) {
                    value = withContext(Dispatchers.IO) {
                        decodeCached(current.localPath, 2048)
                    }
                }
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }
            }
        }

        // Tap zones: left third = previous, rest = next.
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable {
                        if (index > 0) index-- else onBack()
                    }
            )
            Box(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxSize()
                    .clickable {
                        if (index < stories.lastIndex) index++ else onBack()
                    }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            // Segment bars, one per story in this chat.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (i in stories.indices) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(
                                if (i <= index) Color.White
                                else Color.White.copy(alpha = 0.35f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    chat?.title ?: "",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
