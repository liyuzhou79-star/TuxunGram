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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.tuxungram.android.td.BG_KIND_FILL
import com.tuxungram.android.td.BG_KIND_PATTERN
import com.tuxungram.android.td.BackgroundStore
import com.tuxungram.android.td.BgUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Official wallpaper gallery (v0.9.38, E1): the
 * backgrounds the server ships and the user has
 * installed (getInstalledBackgrounds — this tl has no
 * getBackgrounds), in a grid; tapping one opens a
 * full-screen preview, and applying goes through
 * setDefaultBackground so the choice also lands on the
 * account's other clients. The page states the render
 * priority where the choice is made: per-chat override
 * > global picture (gallery or own photo) > gallery
 * fill > preset solid > default doodle.
 */
@Composable
fun WallpaperScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val backgrounds by BackgroundStore.backgrounds.collectAsState()
    val loading by BackgroundStore.loading.collectAsState()
    val error by BackgroundStore.error.collectAsState()
    val applyingId by BackgroundStore.applyingId.collectAsState()
    val notice by BackgroundStore.notice.collectAsState()
    var selected by remember { mutableStateOf<BgUi?>(null) }
    var resetConfirm by remember { mutableStateOf(false) }
    // Keep the preview pointed at the live entry (its
    // docPath fills in when an apply download lands).
    val liveSelected = selected?.let { sel ->
        backgrounds.firstOrNull { it.id == sel.id } ?: sel
    }

    LaunchedEffect(Unit) { BackgroundStore.load() }
    LaunchedEffect(notice) {
        val text = notice ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        BackgroundStore.clearNotice()
    }
    BackHandler(enabled = liveSelected != null) { selected = null }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (liveSelected != null) {
                    selected = null
                } else {
                    onBack()
                }
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                liveSelected?.name?.ifEmpty { "壁纸预览" }
                    ?: "官方壁纸图库",
                fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (liveSelected == null) {
                TextButton(onClick = { resetConfirm = true }) {
                    Text("恢复默认")
                }
            }
        }
        val preview = liveSelected
        if (preview != null) {
            WallpaperPreview(
                bg = preview,
                applying = applyingId == preview.id,
                anyApplying = applyingId != 0L,
                onApply = { forDark ->
                    BackgroundStore.apply(preview, forDark)
                }
            )
        } else {
            Text(
                "这里是 Telegram 官方图库和你用过的背景，点一张" +
                    "预览、设为全局聊天背景。优先级说清楚：单个" +
                    "对话的专属壁纸最高；其次是全局背景图片" +
                    "（这里选的、或在设置里从相册选的，两者只" +
                    "留一个）；再其次是图库纯色和设置里的预设" +
                    "纯色；都没设时用默认涂鸦背景。",
                fontSize = 13.sp, color = Color.Gray,
                modifier = Modifier.padding(
                    start = 16.dp, end = 16.dp, bottom = 8.dp
                )
            )
            when {
                loading && backgrounds.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                backgrounds.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        error ?: "图库是空的。",
                        fontSize = 14.sp, color = Color.Gray
                    )
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(backgrounds, key = { it.id }) { bg ->
                        WallpaperTile(bg) { selected = bg }
                    }
                }
            }
        }
    }
    if (resetConfirm) {
        AlertDialog(
            onDismissRequest = { resetConfirm = false },
            title = { Text("恢复默认背景？") },
            text = {
                Text(
                    "服务器的默认背景会恢复成官方最初的样子" +
                        "（浅色、深色各一份），本机从图库设的" +
                        "背景也会清掉。你在设置里从相册选的" +
                        "自定义图片和单聊专属壁纸不受影响。"
                )
            },
            confirmButton = {
                Button(onClick = {
                    resetConfirm = false
                    BackgroundStore.resetDefault()
                }) { Text("恢复默认") }
            },
            dismissButton = {
                TextButton(onClick = { resetConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun WallpaperTile(bg: BgUi, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(6.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.62f)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (bg.kind == BG_KIND_FILL && bg.fillArgb != 0L) {
                        Color(bg.fillArgb)
                    } else {
                        Color(0xFF37474F)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            val path = bg.thumbPath
            val bitmap by produceState<android.graphics.Bitmap?>(
                initialValue = null, path
            ) {
                value = if (path == null) {
                    null
                } else {
                    withContext(Dispatchers.IO) {
                        decodeCached(path, 384)
                    }
                }
            }
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (bg.kind != BG_KIND_FILL) {
                Text(
                    "图",
                    color = Color.White, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (bg.isDark) {
                Text(
                    "深色款",
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
        }
        Spacer(Modifier.height(3.dp))
        Text(
            bg.name.ifEmpty {
                when (bg.kind) {
                    BG_KIND_FILL -> "纯色背景"
                    BG_KIND_PATTERN -> "图案背景"
                    else -> "壁纸"
                }
            },
            fontSize = 11.5.sp, maxLines = 1
        )
        if (bg.isDefault) {
            Text(
                "官方默认",
                fontSize = 10.5.sp, color = Color.Gray
            )
        }
    }
}

@Composable
private fun WallpaperPreview(
    bg: BgUi,
    applying: Boolean,
    anyApplying: Boolean,
    onApply: (Boolean) -> Unit
) {
    // Which theme's server default slot this apply fills;
    // starts at the background's own recommendation.
    var forDark by remember(bg.id) { mutableStateOf(bg.isDark) }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    if (bg.kind == BG_KIND_FILL && bg.fillArgb != 0L) {
                        Color(bg.fillArgb)
                    } else {
                        Color(0xFF263238)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            val path = bg.docPath ?: bg.thumbPath
            val bitmap by produceState<android.graphics.Bitmap?>(
                initialValue = null, path
            ) {
                value = if (path == null) {
                    null
                } else {
                    withContext(Dispatchers.IO) {
                        decodeCached(path, 1080)
                    }
                }
            }
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (bg.kind != BG_KIND_FILL) {
                Text(
                    "预览图还没下载好",
                    color = Color.White, fontSize = 14.sp
                )
            }
        }
        Column(
            modifier = Modifier.padding(
                start = 16.dp, end = 16.dp,
                top = 12.dp, bottom = 16.dp
            )
        ) {
            if (bg.kind != BG_KIND_FILL && bg.docPath == null) {
                Text(
                    "现在看到的是缩略图，点下面按钮时才下载原图。",
                    fontSize = 12.5.sp, color = Color.Gray
                )
                Spacer(Modifier.height(6.dp))
            }
            if (bg.kind == BG_KIND_PATTERN) {
                Text(
                    "图案背景在本机按图案原图平铺显示，和官方的" +
                        "底色合成效果略有差别。",
                    fontSize = 12.5.sp, color = Color.Gray
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                "设到哪种模式的默认背景（服务器那边分开记）：",
                fontSize = 13.5.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { forDark = false },
                    enabled = forDark
                ) {
                    RadioText(!forDark, "浅色模式")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { forDark = true },
                    enabled = !forDark
                ) {
                    RadioText(forDark, "深色模式")
                }
            }
            Text(
                "本机聊天页不分模式、共用一张全局背景，设好立刻生效。",
                fontSize = 12.5.sp, color = Color.Gray
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onApply(forDark) },
                enabled = !anyApplying,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (applying) "正在设置…" else "设为聊天背景")
            }
        }
    }
}
