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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.tuxungram.android.td.NetworkStatsStore

/** Traffic statistics page (v0.9.14, U3): TDLib's local
 *  counters (getNetworkStatistics) as readable rows — per
 *  file type and network, sent and received apart — plus
 *  the reset the tl offers, behind a confirmation. The
 *  copy on the page says plainly where the numbers come
 *  from: counted on this device since install / last
 *  reset, never synced. */
@Composable
fun NetworkStatsScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val loading by NetworkStatsStore.loading.collectAsState()
    val rows by NetworkStatsStore.rows.collectAsState()
    val sinceDate by NetworkStatsStore.sinceDate.collectAsState()
    val error by NetworkStatsStore.error.collectAsState()
    val notice by NetworkStatsStore.notice.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { NetworkStatsStore.load() }
    val scrollState = rememberScrollState()

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
                "流量统计",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        when {
            loading && rows.isEmpty() -> CenterColumn {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在读计数…", color = Color.Gray)
            }
            error != null -> CenterColumn {
                Text(
                    error!!,
                    color = Color.Gray, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .columnScrollbar(scrollState)
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    "这些数字由 TDLib 在这台设备上本地计数，" +
                        if (sinceDate > 0) {
                            "从 ${formatStatsDate(sinceDate)} 开始累计，不同步到任何地方。"
                        } else {
                            "从安装（或上次重置）开始累计，不同步到任何地方。"
                        },
                    fontSize = 12.sp, color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (notice != null) {
                    Text(
                        notice!!,
                        fontSize = 13.sp, color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                GroupCard {
                    IconRow(
                        icon = Icons.Filled.KeyboardArrowDown,
                        title = "总下载",
                        subtitle = formatBytes(
                            NetworkStatsStore.totalReceived()
                        )
                    )
                    IconRow(
                        icon = Icons.Filled.KeyboardArrowUp,
                        title = "总上传",
                        subtitle = formatBytes(
                            NetworkStatsStore.totalSent()
                        )
                    )
                    // Split bar (v0.9.29, V3): the download /
                    // upload share of all traffic as one bar,
                    // so the totals read at a glance, not only
                    // as two numbers.
                    val totalRx = NetworkStatsStore.totalReceived()
                    val totalTx = NetworkStatsStore.totalSent()
                    if (totalRx + totalTx > 0) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    MaterialTheme.colorScheme
                                        .surfaceVariant
                                )
                        ) {
                            if (totalRx > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(totalRx.toFloat())
                                        .height(8.dp)
                                        .background(
                                            MaterialTheme.colorScheme
                                                .primary
                                        )
                                )
                            }
                            if (totalTx > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(totalTx.toFloat())
                                        .height(8.dp)
                                        .background(
                                            MaterialTheme.colorScheme
                                                .tertiary
                                        )
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "下载", fontSize = 11.sp,
                                color = Color.Gray
                            )
                            Spacer(Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.tertiary,
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "上传", fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    // Category composition (v0.9.46): every
                    // traffic category (the rows below grouped
                    // by their file-type half, networks merged)
                    // as ONE stacked bar with a legend — the
                    // page's chart, where before the composition
                    // could only be worked out by reading every
                    // row's numbers. Past the top few, the tail
                    // folds into 其他 so the bar stays readable.
                    val categoryTotals = rows
                        .groupBy { it.label.substringBefore(" · ") }
                        .map { (name, rs) ->
                            name to rs.sumOf {
                                it.receivedBytes + it.sentBytes
                            }
                        }
                        .filter { it.second > 0 }
                        .sortedByDescending { it.second }
                    val grandTotal = categoryTotals.sumOf {
                        it.second
                    }
                    if (categoryTotals.size > 1 && grandTotal > 0) {
                        val shownCategories =
                            if (categoryTotals.size > 7) {
                                categoryTotals.take(6) +
                                    ("其他" to categoryTotals
                                        .drop(6)
                                        .sumOf { it.second })
                            } else {
                                categoryTotals
                            }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "按类型构成",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    MaterialTheme.colorScheme
                                        .surfaceVariant
                                )
                                // Decorative (v0.9.49, T3): the
                                // legend rows directly below carry
                                // every segment's name, percent
                                // and bytes as text.
                                .clearAndSetSemantics { }
                        ) {
                            shownCategories.forEachIndexed {
                                    index, (_, bytes) ->
                                Box(
                                    modifier = Modifier
                                        .weight(bytes.toFloat())
                                        .height(12.dp)
                                        .background(
                                            TILE_PALETTE[
                                                index %
                                                    TILE_PALETTE.size
                                            ]
                                        )
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        for ((index, pair) in
                            shownCategories.withIndex()) {
                            val (name, bytes) = pair
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            TILE_PALETTE[
                                                index %
                                                    TILE_PALETTE.size
                                            ],
                                            CircleShape
                                        )
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(name, fontSize = 12.5.sp)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${(bytes * 100 / grandTotal)}%" +
                                        " · ${formatBytes(bytes)}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
                if (rows.isEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    EmptyState(
                        icon = PollIcon,
                        title = "还没有记录到流量",
                        subtitle = "刷一会儿聊天、下载几个文件再回来看，" +
                            "这里会按类型和网络列出用量。"
                    )
                }
                val maxRowTotal = rows.maxOfOrNull {
                    it.receivedBytes + it.sentBytes
                }?.coerceAtLeast(1L) ?: 1L
                for (row in rows) {
                    Spacer(Modifier.height(10.dp))
                    GroupCard {
                        Column {
                            Text(
                                row.label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "下载 ${formatBytes(row.receivedBytes)} · " +
                                    "上传 ${formatBytes(row.sentBytes)}" +
                                    if (row.durationSec > 0) {
                                        " · 时长 ${formatDuration(row.durationSec)}"
                                    } else {
                                        ""
                                    },
                                fontSize = 12.sp, color = Color.Gray,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            // Share bar (v0.9.29, V3; split
                            // rx/tx v0.9.46): this row's traffic
                            // against the biggest row, so heavy
                            // categories stand out without
                            // reading the numbers — and the bar
                            // itself is split into its download
                            // (primary) and upload (tertiary)
                            // parts, matching the totals card.
                            val rowTotal =
                                row.receivedBytes + row.sentBytes
                            if (rowTotal > 0) {
                                Spacer(Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            MaterialTheme.colorScheme
                                                .surfaceVariant
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth(
                                                (rowTotal.toFloat() /
                                                    maxRowTotal)
                                                    .coerceIn(0f, 1f)
                                            )
                                            .height(5.dp)
                                    ) {
                                        if (row.receivedBytes > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(
                                                        row.receivedBytes
                                                            .toFloat()
                                                    )
                                                    .height(5.dp)
                                                    .background(
                                                        MaterialTheme
                                                            .colorScheme
                                                            .primary
                                                    )
                                            )
                                        }
                                        if (row.sentBytes > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(
                                                        row.sentBytes
                                                            .toFloat()
                                                    )
                                                    .height(5.dp)
                                                    .background(
                                                        MaterialTheme
                                                            .colorScheme
                                                            .tertiary
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { confirmReset = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("重置计数")
                }
                Text(
                    "重置后从零开始重新数，之前的数字找不回来。",
                    fontSize = 12.sp, color = Color.Gray,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("重置流量计数？") },
            text = {
                Text("当前的统计数字会被清零，从现在开始重新累计。这个操作撤不回来。")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    NetworkStatsStore.reset()
                }) {
                    Text("重置")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun formatStatsDate(dateSeconds: Int): String {
    val cal = java.util.Calendar.getInstance().apply {
        timeInMillis = dateSeconds * 1000L
    }
    return java.text.SimpleDateFormat(
        "yyyy年M月d日", java.util.Locale.getDefault()
    ).format(cal.time)
}

private fun formatDuration(seconds: Double): String {
    val total = seconds.toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h > 0 -> "$h 小时 $m 分"
        m > 0 -> "$m 分 $s 秒"
        else -> "$s 秒"
    }
}
