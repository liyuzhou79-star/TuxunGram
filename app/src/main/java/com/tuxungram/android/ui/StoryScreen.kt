package com.tuxungram.android.ui

import android.graphics.BitmapFactory
import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.ui.viewinterop.AndroidView
import com.tuxungram.android.td.ChatStore
import com.tuxungram.android.td.MeStore
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
    // Pinning my own stories to my profile page (v0.9.24,
    // Q5): only when the poster chat is my own account (for
    // personal stories the poster chat id IS the user id).
    val context = LocalContext.current
    val me by MeStore.me.collectAsState()
    val isOwn = me.id != 0L && chatId == me.id
    val pinnedIds by StoryStore.myPinnedStoryIds.collectAsState()
    val postNotice by StoryStore.postNotice.collectAsState()

    LaunchedEffect(chatId) {
        StoryStore.openChat(chatId)
        if (isOwn) StoryStore.loadMyPinned()
    }
    LaunchedEffect(postNotice) {
        val notice = postNotice ?: return@LaunchedEffect
        Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
        StoryStore.clearPostNotice()
    }

    val current = stories.getOrNull(index)
    // Reply draft (v0.9.25, R3): typing pauses the photo
    // auto-advance so the viewer does not slide away mid-reply.
    var replyText by remember(chatId) { mutableStateOf("") }
    // Photos advance automatically once they are on screen.
    LaunchedEffect(index, current?.localPath, current?.isVideo, replyText) {
        if (current != null && !current.isVideo && current.localPath != null &&
            replyText.isEmpty()
        ) {
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
                if (isOwn && current != null) {
                    val isPinned = current.storyId in pinnedIds
                    TextButton(
                        onClick = {
                            StoryStore.togglePinStory(chatId, current)
                        }
                    ) {
                        Icon(
                            PinIcon,
                            contentDescription = null,
                            tint = if (isPinned) {
                                Color(0xFFFFD54F)
                            } else {
                                Color.White
                            },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(
                            if (isPinned) "已置顶" else "置顶到资料页",
                            color = Color.White, fontSize = 13.sp
                        )
                    }
                }
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = Color.White
                    )
                }
            }
        }

        // Bottom interaction bar (v0.9.25, R3): a ❤️ reaction
        // toggle for every story, plus a reply field when the
        // story allows replies and it is not my own. Replies
        // are plain messages with inputMessageReplyToStory —
        // they land in the poster's chat.
        if (current != null && !isOwn) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // v0.9.53 (X3): this viewer is a
                    // full-bleed overlay outside the host's
                    // system-bars padding; the header pads
                    // the status bar but the bottom bar did
                    // not pad the navigation bar, so on
                    // gesture / three-key navigation it sat
                    // under the system bar.
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 14.dp)
            ) {
                TextButton(onClick = {
                    StoryStore.toggleStoryReaction(chatId, current)
                }) {
                    Text(
                        if (current.hasReaction) "❤️" else "🤍",
                        fontSize = 22.sp
                    )
                }
                if (current.canBeReplied) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        placeholder = {
                            Text(
                                "回复这条动态…",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor =
                                Color.White.copy(alpha = 0.5f),
                            cursorColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        enabled = replyText.isNotBlank(),
                        onClick = {
                            StoryStore.replyToStory(
                                chatId, current.storyId, replyText
                            )
                            replyText = ""
                        }
                    ) {
                        Text("发送", color = Color.White, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
