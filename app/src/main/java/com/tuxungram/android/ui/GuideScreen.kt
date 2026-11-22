package com.tuxungram.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.BuildConfig
import com.tuxungram.android.td.Changelog
import kotlinx.coroutines.launch

/** One onboarding page: a big indicative icon, a title and
 *  one plain sentence. */
private data class GuidePage(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val body: String
)

private val GUIDE_PAGES = listOf(
    GuidePage(
        Icons.AutoMirrored.Filled.Send, "欢迎使用 TuxunGram",
        "一个免费、开源的第三方 Telegram 客户端，日常功能尽量向官方版看齐。"
    ),
    GuidePage(
        GlobeIcon, "连不上？先看代理",
        "连不上 Telegram 时，先去「设置 → 代理」填一个代理，填好就能连，不用开系统 VPN。"
    ),
    GuidePage(
        Icons.Filled.Refresh, "登录后会自动同步",
        "新登录会把全部对话、群组、频道和历史消息同步下来，内容多时要等一会儿；同步完就和官方版一样全，往上滑能一直翻到最早的消息。"
    ),
    GuidePage(
        Icons.Filled.Lock, "隐私三件套",
        "秘密聊天（端到端加密）、应用锁（PIN / 指纹）、已登录设备管理都在设置里，重要的账号建议全开。"
    ),
    GuidePage(
        Icons.Filled.Face, "聊天不只是文字",
        "输入框左边是表情 / 贴纸 / GIF 面板，点一下就能发贴纸；长按消息可以回复、翻译、置顶，滑动消息也能回复。"
    )
)

/**
 * First-run onboarding (v0.9.6, P2): a five-page pager shown
 * once on the very first launch (and re-openable in full
 * from 设置 → 关于）. Skippable from every page; finishing or
 * skipping both count as seen.
 */
@Composable
fun GuideScreen(onDone: () -> Unit) {
    BackHandler { onDone() }
    val pagerState = rememberPagerState(pageCount = { GUIDE_PAGES.size })
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == GUIDE_PAGES.size - 1
    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            val p = GUIDE_PAGES[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(124.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        p.icon, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(58.dp)
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    p.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    p.body,
                    fontSize = 15.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDone) { Text("跳过") }
            Spacer(Modifier.weight(1f))
            Row {
                for (i in GUIDE_PAGES.indices) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (i == pagerState.currentPage) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == pagerState.currentPage) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Gray.copy(alpha = 0.4f)
                                }
                            )
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    if (last) {
                        onDone()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                pagerState.currentPage + 1
                            )
                        }
                    }
                }
            ) {
                Text(if (last) "开始使用" else "下一页")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * "What's new" page (v0.9.6, P2/P3): shown once when the app
 * first reaches the main UI on a version the user has not
 * seen yet. Just the running version's changelog entry and a
 * close button — it never blocks the login flow (it lives
 * behind Ready) and closing marks the version as seen.
 */
@Composable
fun WhatsNewScreen(onClose: () -> Unit) {
    BackHandler { onClose() }
    val notes = Changelog.notesFor(BuildConfig.VERSION_NAME)
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭")
            }
            Text(
                "本版新功能",
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold
            )
        }
        if (notes == null) {
            Text(
                "这一版主要是修问题和打磨细节，完整记录在设置 → 关于 → 更新日志里。",
                color = Color.Gray, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            Text(
                "TuxunGram ${notes.version} · ${notes.title}",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .lazyListScrollbar(listState)
                    .padding(horizontal = 16.dp)
            ) {
                items(notes.items) { item ->
                    Row(modifier = Modifier.padding(vertical = 5.dp)) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp, end = 8.dp)
                                .size(6.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                        )
                        Text(item, fontSize = 14.sp)
                    }
                }
            }
        }
        Button(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("知道了")
        }
    }
}

/**
 * Full changelog (v0.9.6, P3): every version's notes from
 * the local Changelog data, grouped by version. Opened from
 * 设置 → 关于 → 更新日志.
 */
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "更新日志",
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold
            )
        }
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .lazyListScrollbar(listState)
                .padding(horizontal = 16.dp)
        ) {
            for (release in Changelog.releases) {
                item(key = "head-${release.version}") {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${release.version} · ${release.title}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                items(release.items) { line ->
                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp, end = 8.dp)
                                .size(6.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                        )
                        Text(line, fontSize = 14.sp)
                    }
                }
            }
            item(key = "tail") { Spacer(Modifier.height(20.dp)) }
        }
    }
}
