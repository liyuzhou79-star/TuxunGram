package com.tuxungram.android.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.SavedTopicUi
import com.tuxungram.android.td.SavedTopicsStore
import com.tuxungram.android.td.summaryText

/**
 * Saved Messages topics (v0.9.25, R1). Telegram organises
 * Saved Messages into topics on its own: one "my notes"
 * topic, one per chat messages were saved from, one for
 * forwards whose author was hidden. There is no API to
 * invent new topics — forwarding a message into Saved
 * Messages is what creates its topic — so this page lists
 * what the server reports (loadSavedMessagesTopics +
 * updateSavedMessagesTopic), offers pinning, and opens each
 * topic's own message stream
 * (getSavedMessagesTopicHistory).
 */
@Composable
fun SavedTopicsScreen(onBack: () -> Unit) {
    var openTopic by remember { mutableStateOf<SavedTopicUi?>(null) }
    val topic = openTopic
    if (topic != null) {
        SavedTopicHistoryScreen(
            topic = topic,
            onBack = { openTopic = null }
        )
        return
    }
    BackHandler { onBack() }
    val topics by SavedTopicsStore.topics.collectAsState()
    val loading by SavedTopicsStore.loading.collectAsState()
    val notice by SavedTopicsStore.notice.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { SavedTopicsStore.load() }
    LaunchedEffect(notice) {
        val n = notice ?: return@LaunchedEffect
        Toast.makeText(context, n, Toast.LENGTH_SHORT).show()
        SavedTopicsStore.clearNotice()
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                "收藏话题",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            "转发消息到「收藏」时，Telegram 会按来源自动归入话题；" +
                "话题不能手动新建。长按话题可以置顶。",
            fontSize = 12.sp, color = Color.Gray,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        when {
            loading && topics.isEmpty() -> CenterColumn {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在加载收藏话题…", color = Color.Gray)
            }
            topics.isEmpty() -> CenterColumn {
                Text(
                    "还没有收藏话题。把任意消息转发到「收藏」，" +
                        "这里就会出现对应的话题。",
                    color = Color.Gray, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(topics, key = { it.id }) { item ->
                        SavedTopicRow(
                            topic = item,
                            onClick = { openTopic = item },
                            onTogglePin = {
                                SavedTopicsStore.togglePin(item)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedTopicRow(
    topic: SavedTopicUi,
    onClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        topic.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (topic.isPinned) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            PinIcon, contentDescription = "已置顶",
                            modifier = Modifier.size(14.dp),
                            tint = Color.Gray
                        )
                    }
                }
                if (topic.lastText.isNotEmpty()) {
                    Text(
                        topic.lastText,
                        fontSize = 13.sp, color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (topic.lastDate > 0) {
                Text(
                    formatDayLabel(topic.lastDate),
                    fontSize = 11.sp, color = Color.Gray
                )
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(if (topic.isPinned) "取消置顶" else "置顶")
                },
                onClick = {
                    menuOpen = false
                    onTogglePin()
                }
            )
        }
    }
}

@Composable
private fun SavedTopicHistoryScreen(
    topic: SavedTopicUi,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val history by SavedTopicsStore.history.collectAsState()
    val loading by SavedTopicsStore.historyLoading.collectAsState()
    val notice by SavedTopicsStore.notice.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(topic.id) { SavedTopicsStore.openTopic(topic.id) }
    LaunchedEffect(notice) {
        val n = notice ?: return@LaunchedEffect
        Toast.makeText(context, n, Toast.LENGTH_SHORT).show()
        SavedTopicsStore.clearNotice()
    }
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: -1
        }.collect { last ->
            if (last >= history.size - 5 && history.isNotEmpty()) {
                SavedTopicsStore.loadMoreHistory()
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                topic.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        when {
            loading && history.isEmpty() -> CenterColumn {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在加载…", color = Color.Gray)
            }
            history.isEmpty() -> CenterColumn {
                EmptyStateMini(
                    icon = ChatBubbleIcon,
                    title = "这个话题里还没有消息",
                    subtitle = "从这个对话收藏过来的消息会显示在这里。"
                )
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(history, key = { it.id }) { msg ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 16.dp, vertical = 8.dp
                                )
                        ) {
                            Text(
                                msg.summaryText(),
                                fontSize = 14.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                formatDayLabel(msg.dateSeconds),
                                fontSize = 11.sp, color = Color.Gray,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    if (loading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
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
        }
    }
}
