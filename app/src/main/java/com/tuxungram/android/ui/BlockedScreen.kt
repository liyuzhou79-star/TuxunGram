package com.tuxungram.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.BlockStore

/** Blocked senders (v0.9.3, M3): everyone on the account's
 *  block list (getBlockedMessageSenders, paged), each row
 *  unblockable in place. Reached from 设置 → 隐私. */
@Composable
fun BlockedScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val blocked by BlockStore.blocked.collectAsState()
    val exhausted by BlockStore.exhausted.collectAsState()
    val loadError by BlockStore.loadError.collectAsState()
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { BlockStore.load() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("已拉黑名单", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "拉黑的人给你发消息你收不到，对方不会知道。解除后立刻恢复。",
            color = Color.Gray, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        if (loadError != null) {
            Text(
                loadError!!,
                color = Color(0xFFC62828), fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            TextButton(
                onClick = { BlockStore.load() },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) { Text("重试") }
        }
        if (notice != null) {
            Text(
                notice!!, color = Color.Gray, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        if (blocked.isEmpty() && loadError == null) {
            CenterColumn {
                if (exhausted) {
                    EmptyState(
                        BlockIcon, "没有拉黑任何人",
                        "在某个人的资料页可以拉黑他，拉黑后他发来的消息你收不到。"
                    )
                } else {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在加载…", color = Color.Gray)
                }
            }
            return@Column
        }
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .lazyListScrollbar(listState)
        ) {
            items(items = blocked, key = { "${it.senderKind}:${it.senderId}" }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Official gradient placeholder avatar
                    // (v0.9.29, V3): same colour-by-id rule as
                    // the chat list, replacing the flat
                    // gray-blue disc this page kept.
                    val avatarPair = OfficialTheme.avatarColors(
                        entry.senderId, OfficialTheme.dark
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                androidx.compose.ui.graphics.Brush
                                    .verticalGradient(
                                        listOf(
                                            avatarPair.first,
                                            avatarPair.second
                                        )
                                    ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            entry.name.trim().firstOrNull()?.toString() ?: "?",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.name.ifEmpty {
                                if (entry.senderKind == "user") {
                                    "用户 ${entry.senderId}"
                                } else {
                                    "对话 ${entry.senderId}"
                                }
                            },
                            fontSize = 15.sp
                        )
                        if (entry.senderKind == "chat") {
                            Text("频道/群组", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = {
                        BlockStore.setBlocked(
                            entry.senderKind, entry.senderId, false
                        ) { error ->
                            notice = error ?: "已解除拉黑。"
                        }
                    }) {
                        Text("解除拉黑")
                    }
                }
            }
            if (!exhausted) {
                item(key = "more") {
                    TextButton(
                        onClick = { BlockStore.loadMore() },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text("加载更多")
                    }
                }
            }
        }
    }
}
