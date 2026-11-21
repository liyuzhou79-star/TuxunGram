package com.tuxungram.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.ForumStore
import com.tuxungram.android.td.ForumTopicUi

/**
 * Forum screens (v0.8.0, G2): the topic list shown when a
 * forum supergroup is opened, and one topic's message stream.
 * Both live inside the chat screen's scaffold (top bar stays
 * the chat's), mirroring how mature clients treat topics as
 * sub-chats of the group.
 */

@Composable
fun ForumTopicsList(
    chatId: Long,
    onOpenTopic: (ForumTopicUi) -> Unit
) {
    val topics by ForumStore.topics.collectAsState()
    val loading by ForumStore.topicsLoading.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(chatId) {
        ForumStore.loadTopics(chatId)
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
    if (topics.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator()
            } else {
                Text("这个群组还没有话题", color = Color.Gray)
            }
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .lazyListScrollbar(listState)
    ) {
        items(items = topics, key = { it.topicId }) { topic ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenTopic(topic) }
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
                    Text(
                        topic.name.trim().firstOrNull()?.toString() ?: "#",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            topic.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
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
                            .padding(horizontal = 7.dp, vertical = 2.dp),
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

@Composable
fun ForumTopicView(
    chatId: Long,
    topic: ForumTopicUi
) {
    val messages by ForumStore.messages.collectAsState()
    val error by ForumStore.error.collectAsState()
    val replyTarget by ForumStore.replyTarget.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    DisposableEffect(chatId, topic.topicId) {
        ForumStore.openTopic(chatId, topic.topicId)
        onDispose { ForumStore.closeTopic() }
    }
    LaunchedEffect(listState, messages.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastVisible ->
            if (messages.isNotEmpty() && lastVisible >= messages.size - 3) {
                ForumStore.loadOlder()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
                        }
                    )
                }
            }
        }
        val target = replyTarget
        if (target != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5))
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
}
