package com.tuxungram.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.tuxungram.android.td.EventLogStore
import com.tuxungram.android.td.EventUi

/** Administrator log page (v0.9.14, U1): the group's
 *  getChatEventLog stream as readable rows — who did what,
 *  when — with the tl's own filter categories as chips on
 *  top. Only reachable from an admin's profile page; a
 *  server refusal lands as a plain sentence from the store. */
@Composable
fun EventLogScreen(chatId: Long, onBack: () -> Unit) {
    BackHandler { onBack() }
    val events by EventLogStore.events.collectAsState()
    val loading by EventLogStore.loading.collectAsState()
    val loadingMore by EventLogStore.loadingMore.collectAsState()
    val error by EventLogStore.error.collectAsState()
    val endReached by EventLogStore.endReached.collectAsState()
    var filterKey by remember { mutableStateOf("all") }
    LaunchedEffect(chatId, filterKey) {
        EventLogStore.load(chatId, filterKey)
    }
    val listState = rememberLazyListState()

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
                "管理员日志",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            for (filter in EventLogStore.FILTERS) {
                val selected = filter.key == filterKey
                Box(
                    modifier = Modifier
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { filterKey = filter.key }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        filter.label,
                        fontSize = 13.sp,
                        color = if (selected) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
        }
        when {
            loading -> CenterColumn {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在拉日志…", color = Color.Gray)
            }
            error != null -> CenterColumn {
                Text(
                    error!!,
                    color = Color.Gray, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            events.isEmpty() -> CenterColumn {
                EmptyState(
                    icon = Icons.Filled.List,
                    title = "暂无记录",
                    subtitle = "这个类别下还没有管理员操作记录。"
                )
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .lazyListScrollbar(listState)
            ) {
                items(events, key = { it.id }) { event ->
                    EventRow(event)
                }
                if (!endReached) {
                    item(key = "more") {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (loadingMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .size(22.dp)
                                )
                            } else {
                                TextButton(
                                    onClick = { EventLogStore.loadMore() }
                                ) {
                                    Text("加载更早的记录")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: EventUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (event.actorPhoto != null) {
            AvatarImage(path = event.actorPhoto, sizeDp = 38)
        } else {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color(0xFF90A4AE), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    event.actorName.trim().firstOrNull()?.toString()
                        ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                event.actorName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                event.text,
                fontSize = 13.sp,
                color = Color(0xFF455A64)
            )
            Text(
                formatEventTime(event.dateSec),
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

private fun formatEventTime(dateSeconds: Int): String {
    val cal = java.util.Calendar.getInstance().apply {
        timeInMillis = dateSeconds * 1000L
    }
    val thisYear = java.util.Calendar.getInstance()
        .get(java.util.Calendar.YEAR)
    val pattern = if (cal.get(java.util.Calendar.YEAR) == thisYear) {
        "M月d日 HH:mm"
    } else {
        "yyyy年M月d日 HH:mm"
    }
    return java.text.SimpleDateFormat(
        pattern, java.util.Locale.getDefault()
    ).format(cal.time)
}
