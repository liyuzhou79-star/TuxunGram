package com.tuxungram.android.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.MyStoriesStore
import com.tuxungram.android.td.MyStoryUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "My stories" page (v0.9.32, Y2): the stories I posted to
 * my profile page (getChatPostedToChatPageStories), each
 * with its server view/reaction counts; tapping one opens
 * its detail — counts, the viewer list
 * (getStoryInteractions, view-type interactions only,
 * reacted viewers badged), and deletion (deleteStory)
 * when the story object allows it. Stories the server
 * will not give interactions for show no viewer entry —
 * nothing is estimated or invented.
 */
@Composable
fun MyStoriesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val stories by MyStoriesStore.stories.collectAsState()
    val loading by MyStoriesStore.loading.collectAsState()
    val exhausted by MyStoriesStore.exhausted.collectAsState()
    val notice by MyStoriesStore.notice.collectAsState()
    var selectedId by remember { mutableStateOf<Int?>(null) }
    val selected = stories.firstOrNull { it.storyId == selectedId }

    LaunchedEffect(Unit) { MyStoriesStore.load() }
    LaunchedEffect(notice) {
        val text = notice ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        MyStoriesStore.clearNotice()
    }
    // In detail, Back returns to the grid first.
    BackHandler(enabled = selected != null) { selectedId = null }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (selected != null) selectedId = null else onBack()
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                "我的动态",
                fontSize = 19.sp, fontWeight = FontWeight.SemiBold
            )
        }
        val detail = selected
        if (detail != null) {
            StoryDetail(
                story = detail,
                onDeleted = { selectedId = null }
            )
        } else {
            when {
                loading && stories.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                stories.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "你还没有发到资料页的动态。\n" +
                            "发动态时把「发布到资料页」打开，" +
                            "或者在动态上置顶到资料页，" +
                            "它们就会列在这里。",
                        color = Color.Gray, fontSize = 14.sp
                    )
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(stories, key = { it.storyId }) { story ->
                        StoryGridCell(story) { selectedId = story.storyId }
                    }
                    if (!exhausted) {
                        item {
                            TextButton(
                                onClick = { MyStoriesStore.loadMore() },
                                enabled = !loading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (loading) "正在加载…" else "加载更多"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryGridCell(story: MyStoryUi, onClick: () -> Unit) {
    val path = story.previewPath
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) { decodeCached(path, 512) }
        }
    }
    Box(
        modifier = Modifier
            .padding(1.5.dp)
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF263238))
            .clickable { onClick() }
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (story.isVideo) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "视频动态",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(30.dp)
            )
        }
        if (story.isPinned) {
            Text(
                "置顶",
                color = Color.White, fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(
                        Color(0x99000000), RoundedCornerShape(3.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color(0x66000000))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Icon(
                EyeIcon, contentDescription = null,
                tint = Color.White, modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                "${story.viewCount}",
                color = Color.White, fontSize = 11.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatDayLabel(story.dateSeconds),
                color = Color.White, fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun StoryDetail(story: MyStoryUi, onDeleted: () -> Unit) {
    var deleteConfirm by remember { mutableStateOf(false) }
    val viewers by MyStoriesStore.viewers.collectAsState()
    val viewersLoading by MyStoriesStore.viewersLoading.collectAsState()
    val viewersError by MyStoriesStore.viewersError.collectAsState()
    val viewersExhausted by
        MyStoriesStore.viewersExhausted.collectAsState()
    LaunchedEffect(story.storyId) {
        if (story.canGetInteractions) {
            MyStoriesStore.loadViewers(story.storyId)
        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            val path = story.previewPath
            val bitmap by produceState<android.graphics.Bitmap?>(
                initialValue = null, path
            ) {
                value = if (path == null) {
                    null
                } else {
                    withContext(Dispatchers.IO) {
                        decodeCached(path, 1024)
                    }
                }
            }
            val bmp = bitmap
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF263238)),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (story.isVideo) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "视频动态",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "${formatDayLabel(story.dateSeconds)} " +
                    formatMsgTime(story.dateSeconds) +
                    if (story.isPinned) " · 已置顶到资料页" else "",
                fontSize = 13.sp, color = Color.Gray
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${story.viewCount} 次查看 · " +
                    "${story.reactionCount} 个回应",
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold
            )
            if (story.canDelete) {
                TextButton(onClick = { deleteConfirm = true }) {
                    Text("删除这条动态", color = Color(0xFFC62828))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "看过的人",
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
        }
        if (!story.canGetInteractions) {
            item {
                Text(
                    "这条动态看不了查看者名单（服务器没有开放这条的互动数据）。",
                    fontSize = 13.5.sp, color = Color.Gray
                )
            }
        } else if (viewersError != null && viewers.isEmpty()) {
            item {
                Text(viewersError!!, fontSize = 13.5.sp)
            }
        } else if (viewersLoading && viewers.isEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("正在查…", fontSize = 14.sp)
                }
            }
        } else if (viewers.isEmpty()) {
            item {
                Text(
                    "还没有人看过这条动态。",
                    fontSize = 13.5.sp, color = Color.Gray
                )
            }
        } else {
            items(viewers, key = { it.userId }) { viewer ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                ) {
                    if (viewer.photoPath != null) {
                        AvatarImage(
                            path = viewer.photoPath, sizeDp = 38
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    Color(0xFFB0BEC5), CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                viewer.name.trim().firstOrNull()
                                    ?.toString() ?: "?",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        viewer.name.ifEmpty { "…" },
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    if (viewer.reactionEmoji != null) {
                        Text(
                            viewer.reactionEmoji,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    Text(
                        formatViewDate(viewer.dateSeconds),
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
            }
            if (!viewersExhausted) {
                item {
                    TextButton(
                        onClick = { MyStoriesStore.loadMoreViewers() },
                        enabled = !viewersLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (viewersLoading) "正在加载…" else "加载更多"
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("删除这条动态？") },
            text = {
                Text("删掉后所有人都看不到了，动态的查看记录也会一起没掉。")
            },
            confirmButton = {
                Button(onClick = {
                    deleteConfirm = false
                    MyStoriesStore.delete(story.storyId)
                    onDeleted()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}
