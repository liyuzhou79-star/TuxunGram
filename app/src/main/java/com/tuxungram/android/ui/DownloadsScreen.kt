package com.tuxungram.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.DownloadEntryUi
import com.tuxungram.android.td.DownloadStore

/** Download manager (v0.9.12, S5): the files this app knows
 *  about — documents / videos / voice notes / GIFs from chats
 *  you have opened, with live progress for the ones still
 *  downloading and a cancel action; completed files open
 *  with the same viewer path as the storage page. The list
 *  is an aggregation (TDLib has no global download list), so
 *  the empty state says plainly where entries come from. */
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val entries by DownloadStore.entries.collectAsState()
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
                "照片不在这里——照片算媒体缓存，在设置的存储空间里管。",
            color = Color.Gray, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(10.dp))
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
private fun DownloadRow(entry: DownloadEntryUi) {
    val context = LocalContext.current
    val path = entry.path
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (entry.completed && path != null) {
                    Modifier.clickable {
                        openDownloadedFile(context, path)
                    }
                } else {
                    Modifier
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
            if (entry.active) {
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
}

private fun downloadStatusText(entry: DownloadEntryUi): String =
    when {
        entry.active && entry.totalSize > 0 -> {
            val pct = (entry.downloadedSize * 100 /
                entry.totalSize).toInt()
            "下载中 $pct% · ${formatBytes(entry.downloadedSize)} / " +
                formatBytes(entry.totalSize)
        }
        entry.active -> "下载中 · 已下 ${formatBytes(entry.downloadedSize)}"
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
            "未开始 · ${formatBytes(entry.totalSize)}"
        else -> "未开始"
    }
