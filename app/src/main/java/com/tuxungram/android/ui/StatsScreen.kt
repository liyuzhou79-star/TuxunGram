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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.ChatStatsStore
import com.tuxungram.android.td.StatCard
import com.tuxungram.android.td.TopSenderUi

/** Statistics page (v0.9.13, T2): the headline numbers
 *  getChatStatistics returns for a supergroup or channel,
 *  as cards, plus the group's most active senders as drawn
 *  bars. The entry only exists when the chat's FullInfo
 *  says statistics are available; a server refusal (not an
 *  admin, chat too small) lands here as a plain sentence
 *  from the store. */
@Composable
fun StatsScreen(chatId: Long, onBack: () -> Unit) {
    BackHandler { onBack() }
    val loading by ChatStatsStore.loading.collectAsState()
    val stats by ChatStatsStore.stats.collectAsState()
    val error by ChatStatsStore.error.collectAsState()
    LaunchedEffect(chatId) { ChatStatsStore.load(chatId) }

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
                "统计",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        when {
            loading -> CenterColumn {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在拉统计…", color = Color.Gray)
            }
            error != null -> CenterColumn {
                Text(
                    error!!,
                    color = Color.Gray, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            stats == null -> CenterColumn {
                Text("暂时没有统计数据。", color = Color.Gray)
            }
            else -> {
                val s = stats!!
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .columnScrollbar(scrollState)
                        .padding(horizontal = 20.dp)
                ) {
                    if (s.periodStart > 0 && s.periodEnd > 0) {
                        Text(
                            "统计周期：${formatStatsDay(s.periodStart)}" +
                                " – ${formatStatsDay(s.periodEnd)}",
                            fontSize = 12.sp, color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    for (rowCards in s.cards.chunked(2)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for ((index, card) in rowCards.withIndex()) {
                                if (index > 0) Spacer(Modifier.width(10.dp))
                                StatCardView(
                                    card,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowCards.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (s.notificationsPercent >= 0) {
                        Text(
                            "开了消息通知的订阅者约占 " +
                                "${formatStatNumber(s.notificationsPercent, isMean = true)}%",
                            fontSize = 13.sp, color = Color.Gray
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    if (s.topSenders.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "最活跃的发言者",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
                        val maxSent = s.topSenders.maxOf { it.sentCount }
                            .coerceAtLeast(1)
                        for (sender in s.topSenders) {
                            TopSenderRow(sender, maxSent)
                        }
                    }
                    Text(
                        "数字由 Telegram 服务器统计，和官方客户端里看到的是同一份。",
                        fontSize = 12.sp, color = Color.Gray,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun StatCardView(card: StatCard, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(card.label, fontSize = 12.sp, color = Color.Gray)
        Text(
            formatStatNumber(card.value, card.isMean),
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp)
        )
        val growth = card.growthPercent
        if (growth != 0.0) {
            val up = growth > 0
            Text(
                (if (up) "▲ " else "▼ ") +
                    "较上期 " +
                    (if (up) "+" else "") +
                    formatStatNumber(growth, isMean = true) + "%",
                fontSize = 11.sp,
                color = if (up) Color(0xFF43A047) else Color(0xFFC62828),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun TopSenderRow(sender: TopSenderUi, maxSent: Int) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (sender.photoPath != null) {
                AvatarImage(path = sender.photoPath, sizeDp = 32)
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFFB0BEC5), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        sender.name.trim().firstOrNull()?.toString()
                            ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                sender.name.ifEmpty { "…" },
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Text(
                "${sender.sentCount} 条" +
                    if (sender.avgChars > 0) {
                        " · 平均 ${sender.avgChars} 字"
                    } else {
                        ""
                    },
                fontSize = 12.sp, color = Color.Gray
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 42.dp, top = 5.dp)
                .height(5.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(3.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(
                        (sender.sentCount.toFloat() / maxSent)
                            .coerceIn(0f, 1f)
                    )
                    .height(5.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}

/** Counts render whole; means and percentages keep one
 *  decimal. Thousands are grouped for readability. */
private fun formatStatNumber(value: Double, isMean: Boolean): String {
    return if (isMean) {
        String.format(java.util.Locale.getDefault(), "%.1f", value)
    } else {
        String.format(
            java.util.Locale.getDefault(), "%,d", value.toLong()
        )
    }
}

private fun formatStatsDay(dateSeconds: Int): String {
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
