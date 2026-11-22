package com.tuxungram.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.DownloadEntryUi
import com.tuxungram.android.td.DownloadStore

/** Download center (v0.9.12, S5; queue upgrade v0.9.23, P2):
 *  the files this app knows about — documents / videos /
 *  voice notes / GIFs from chats you have opened, with live
 *  progress and speed, a concurrency queue the user controls
 *  (how many files TDLib is asked to fetch at once), a
 *  boost action per file, and automatic retries for downloads
 *  that die on their own. Since v0.9.27 (T2) the rows are
 *  backed by TDLib's persistent download list (user-started
 *  files enter it when the queue releases them), merged with
 *  the aggregation of files from chats opened this session;
 *  the intro says plainly where entries come from and what
 *  the queue does not cover (media TDLib fetches by itself —
 *  those follow the auto-download settings, one tap away at
 *  the top). */
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenAutoDownloadSettings: () -> Unit = {}
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val entries by DownloadStore.entries.collectAsState()
    val maxConcurrent by DownloadStore.maxConcurrent.collectAsState()
    val retryLimit by DownloadStore.retryLimit.collectAsState()
    val totalSpeed by DownloadStore.totalSpeedBps.collectAsState()
    LaunchedEffect(Unit) { DownloadStore.refresh(context) }

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
                "下载管理",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                DownloadStore.refresh(context)
            }) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "刷新"
                )
            }
        }
        Text(
            "这里汇总的是你在聊天里打开过、下载过的文件（文档、视频、语音、GIF）。" +
                "下载列表会存进 Telegram 的持久下载列表，重启 App 后没下完的还在、会接着下。" +
                "照片不在这里——照片算媒体缓存，在设置的存储空间里管。",
            color = Color.Gray, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(12.dp))
        // Queue controls (v0.9.23, P2): how many of the files
        // you start are fetched at the same time, and whether
        // a download that dies on its own is retried. Both are
        // enforced by this page's own queue — see the note
        // under the controls.
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "同时下载", fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(92.dp)
                )
                for (n in DownloadStore.MAX_CONCURRENT_CHOICES) {
                    ChoiceChip(
                        label = "$n 个",
                        selected = maxConcurrent == n,
                        onClick = { DownloadStore.setMaxConcurrent(n) }
                    )
                    Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "失败重试", fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(92.dp)
                )
                for (n in DownloadStore.RETRY_CHOICES) {
                    ChoiceChip(
                        label = if (n == 0) "关" else "$n 次",
                        selected = retryLimit == n,
                        onClick = { DownloadStore.setRetryLimit(n) }
                    )
                    Spacer(Modifier.width(6.dp))
                }
            }
            if (totalSpeed > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "总速度 ${formatBytes(totalSpeed)}/s",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "排队只管你亲手点下的文件下载；图片、头像这类自动加载的走下面的自动下载设置。",
                color = Color.Gray, fontSize = 12.5.sp
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenAutoDownloadSettings() }
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("自动下载设置", fontSize = 14.5.sp)
                Text(
                    "什么网络下、多大的图片视频文件自动下载（在设置里调）",
                    color = Color.Gray, fontSize = 12.5.sp
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null, tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        if (entries.isEmpty()) {
            EmptyState(
                icon = DownloadIcon,
                title = "还没有下载记录",
                subtitle = "在聊天里点开一个文件或视频开始下载，它就会出现在这里。"
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries, key = { it.key }) { entry ->
                    DownloadRow(entry)
                }
            }
        }
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = 13.sp,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) {
                FontWeight.SemiBold
            } else {
                FontWeight.Normal
            }
        )
    }
}

@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
private fun DownloadRow(entry: DownloadEntryUi) {
    val context = LocalContext.current
    val path = entry.path
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (entry.completed && path != null) {
                            openDownloadedFile(context, path)
                        }
                    },
                    onLongClick = {
                        if (entry.fileId != 0) menuOpen = true
                    }
                )
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    DownloadIcon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.name,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        downloadStatusText(entry),
                        fontSize = 12.5.sp, color = Color.Gray
                    )
                }
                if (entry.failed) {
                    TextButton(onClick = {
                        DownloadStore.retry(entry.fileId)
                    }) {
                        Text("重试")
                    }
                } else if (entry.active || entry.queued) {
                    TextButton(onClick = {
                        DownloadStore.cancel(entry.fileId)
                    }) {
                        Text("取消")
                    }
                }
            }
            if (!entry.completed && entry.totalSize > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = {
                        (entry.downloadedSize.toFloat() /
                            entry.totalSize.toFloat())
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        // Long-press actions (v0.9.23, P2): boost this file to
        // the front of the queue, start it if it never began,
        // retry it if it failed, or cancel it.
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            if (!entry.completed && !entry.failed) {
                DropdownMenuItem(
                    text = { Text("优先下载") },
                    onClick = {
                        menuOpen = false
                        DownloadStore.enqueue(
                            entry.fileId, boost = true
                        )
                    }
                )
            }
            if (entry.failed) {
                DropdownMenuItem(
                    text = { Text("重新下载") },
                    onClick = {
                        menuOpen = false
                        DownloadStore.retry(entry.fileId)
                    }
                )
            }
            if (entry.active || entry.queued) {
                DropdownMenuItem(
                    text = { Text("取消下载") },
                    onClick = {
                        menuOpen = false
                        DownloadStore.cancel(entry.fileId)
                    }
                )
            }
            if (entry.inList && !entry.active && !entry.queued) {
                DropdownMenuItem(
                    text = { Text("从下载列表移除") },
                    onClick = {
                        menuOpen = false
                        DownloadStore.removeFromList(entry.fileId)
                    }
                )
            }
        }
    }
}

private fun downloadStatusText(entry: DownloadEntryUi): String =
    when {
        entry.failed ->
            "下载失败" +
                if (entry.downloadedSize > 0) {
                    " · 已下 ${formatBytes(entry.downloadedSize)}" +
                        if (entry.totalSize > 0) {
                            " / ${formatBytes(entry.totalSize)}"
                        } else {
                            ""
                        }
                } else {
                    ""
                }
        entry.active && entry.totalSize > 0 -> {
            val pct = (entry.downloadedSize * 100 /
                entry.totalSize).toInt()
            "下载中 $pct%" +
                if (entry.speedBps > 0) {
                    " · ${formatBytes(entry.speedBps)}/s"
                } else {
                    ""
                } +
                " · ${formatBytes(entry.downloadedSize)} / " +
                formatBytes(entry.totalSize)
        }
        entry.active -> {
            "下载中" +
                if (entry.speedBps > 0) {
                    " · ${formatBytes(entry.speedBps)}/s"
                } else {
                    ""
                } +
                " · 已下 ${formatBytes(entry.downloadedSize)}"
        }
        entry.queued -> "排队中，轮到它会自动开始"
        entry.completed ->
            "已完成 · ${formatBytes(entry.totalSize)}，点开打开"
        entry.downloadedSize > 0 ->
            "已暂停 · 已下 ${formatBytes(entry.downloadedSize)}" +
                if (entry.totalSize > 0) {
                    " / ${formatBytes(entry.totalSize)}"
                } else {
                    ""
                }
        entry.totalSize > 0 ->
            "未开始 · ${formatBytes(entry.totalSize)}（长按可开始下载）"
        else -> "未开始（长按可开始下载）"
    }
