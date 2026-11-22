package com.tuxungram.android.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import com.tuxungram.android.td.JoinRequestUi
import com.tuxungram.android.td.JoinRequestsStore

/** Join-request approval page (v0.9.14, U2): everyone
 *  waiting to get into this group/channel, one tap to
 *  approve or decline each, bulk actions behind a confirm.
 *  Tapping a row opens the applicant's profile (resolved
 *  through createPrivateChat by the host, the same route
 *  mention taps use). */
@Composable
fun JoinRequestsScreen(
    chatId: Long,
    onBack: () -> Unit,
    onOpenUser: (Long) -> Unit
) {
    BackHandler { onBack() }
    val requests by JoinRequestsStore.requests.collectAsState()
    val totalCount by JoinRequestsStore.totalCount.collectAsState()
    val loading by JoinRequestsStore.loading.collectAsState()
    val error by JoinRequestsStore.error.collectAsState()
    val notice by JoinRequestsStore.notice.collectAsState()
    var bulkTarget by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(chatId) { JoinRequestsStore.load(chatId) }
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
                "入群申请",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (totalCount > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "待处理 $totalCount",
                    fontSize = 13.sp, color = Color.Gray
                )
            }
        }
        if (notice != null) {
            Text(
                notice!!,
                fontSize = 13.sp, color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        when {
            loading && requests.isEmpty() -> CenterColumn {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在拉申请列表…", color = Color.Gray)
            }
            error != null -> CenterColumn {
                Text(
                    error!!,
                    color = Color.Gray, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            requests.isEmpty() -> CenterColumn {
                EmptyState(
                    icon = PeopleIcon,
                    title = "暂无待处理的申请",
                    subtitle = "有人申请加入时会出现在这里。"
                )
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .lazyListScrollbar(listState)
                ) {
                    items(requests, key = { it.userId }) { request ->
                        JoinRequestRow(
                            request = request,
                            onOpen = { onOpenUser(request.userId) },
                            onApprove = {
                                JoinRequestsStore.process(
                                    chatId, request.userId, approve = true
                                )
                            },
                            onDecline = {
                                JoinRequestsStore.process(
                                    chatId, request.userId, approve = false
                                )
                            }
                        )
                    }
                    if (requests.size < totalCount) {
                        item(key = "more") {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                TextButton(
                                    onClick = {
                                        JoinRequestsStore.loadMore()
                                    }
                                ) {
                                    Text("加载更多")
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    TextButton(onClick = { bulkTarget = true }) {
                        Text("全部批准")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { bulkTarget = false }) {
                        Text(
                            "全部拒绝",
                            color = Color(0xFFC62828)
                        )
                    }
                }
            }
        }
    }
    val bulk = bulkTarget
    if (bulk != null) {
        AlertDialog(
            onDismissRequest = { bulkTarget = null },
            title = {
                Text(if (bulk) "全部批准？" else "全部拒绝？")
            },
            text = {
                Text(
                    if (bulk) {
                        "当前等着的 $totalCount 条申请会全部批准，申请人会直接进群。"
                    } else {
                        "当前等着的 $totalCount 条申请会全部拒绝。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    bulkTarget = null
                    JoinRequestsStore.processAll(chatId, bulk)
                }) {
                    Text(if (bulk) "全部批准" else "全部拒绝")
                }
            },
            dismissButton = {
                TextButton(onClick = { bulkTarget = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun JoinRequestRow(
    request: JoinRequestUi,
    onOpen: () -> Unit,
    onApprove: () -> Unit,
    onDecline: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (request.photoPath != null) {
            AvatarImage(path = request.photoPath, sizeDp = 42)
        } else {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF90A4AE), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    request.name.trim().firstOrNull()?.toString()
                        ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                request.name.ifEmpty { "…" },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (request.bio.isNotEmpty()) {
                Text(
                    request.bio,
                    fontSize = 12.sp, color = Color.Gray,
                    maxLines = 2
                )
            }
            Text(
                "申请于 ${formatEventDay(request.dateSec)}",
                fontSize = 11.sp, color = Color.Gray
            )
        }
        TextButton(onClick = onDecline) {
            Text("拒绝", color = Color(0xFFC62828), fontSize = 13.sp)
        }
        TextButton(onClick = onApprove) {
            Text("批准", fontSize = 13.sp)
        }
    }
}

private fun formatEventDay(dateSeconds: Int): String {
    val cal = java.util.Calendar.getInstance().apply {
        timeInMillis = dateSeconds * 1000L
    }
    val thisYear = java.util.Calendar.getInstance()
        .get(java.util.Calendar.YEAR)
    val pattern = if (cal.get(java.util.Calendar.YEAR) == thisYear) {
        "M月d日"
    } else {
        "yyyy年M月d日"
    }
    return java.text.SimpleDateFormat(
        pattern, java.util.Locale.getDefault()
    ).format(cal.time)
}
