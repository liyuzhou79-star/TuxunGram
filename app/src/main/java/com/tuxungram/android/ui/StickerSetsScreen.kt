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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.tuxungram.android.td.StickerSetManageUi
import com.tuxungram.android.td.StickerSetStore

/**
 * Sticker-set management page (v0.8.0, G3): installed sets
 * with covers and counts, remove with confirmation, and add
 * by t.me/addstickers link or bare set name. Reached from
 * settings; the chat sticker panel reads the same installed
 * list, refreshed on every change.
 */
@Composable
fun StickerSetsScreen(onBack: () -> Unit) {
    val sets by StickerSetStore.sets.collectAsState()
    val notice by StickerSetStore.notice.collectAsState()
    var linkInput by remember { mutableStateOf("") }
    var pendingRemove by remember {
        mutableStateOf<StickerSetManageUi?>(null)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        StickerSetStore.load()
    }
    BackHandler { onBack() }

    // Create-my-own-set sub-view (v0.9.26, S4): replaces the
    // management list while open; its own BackHandler steps
    // back here.
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        CreateStickerSetView(onBack = { creating = false })
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                "贴纸包管理",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                "添加贴纸包：把 t.me/addstickers/ 开头的链接（或包名）粘到下面。",
                color = Color.Gray,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = linkInput,
                    onValueChange = { linkInput = it },
                    placeholder = { Text("t.me/addstickers/…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        StickerSetStore.addByInput(linkInput)
                        linkInput = ""
                    },
                    enabled = linkInput.isNotBlank()
                ) {
                    Text("添加")
                }
            }
            val tip = notice
            if (tip != null) {
                Text(
                    tip,
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { creating = true }) {
                Text("创建自己的贴纸包")
            }
        }
        if (sets.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    Icons.Filled.Face, "还没有装任何贴纸包",
                    "把贴纸包链接粘到上面添加，或在聊天里点一张贴纸添加整包。"
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .lazyListScrollbar(listState)
            ) {
                items(items = sets, key = { it.id }) { set ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (set.thumbPath != null) {
                            AvatarImage(path = set.thumbPath, sizeDp = 46)
                        } else {
                            Box(
                                modifier = Modifier
                                    .width(46.dp)
                                    .height(46.dp)
                                    .background(Color(0xFFE0E0E0)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("…", color = Color.Gray)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                set.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                "${set.count} 张贴纸",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = { pendingRemove = set }) {
                            Text("移除", color = Color(0xFFC62828))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Color(0x14000000))
                    )
                }
            }
        }
    }

    val removing = pendingRemove
    if (removing != null) {
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("移除「${removing.title}」？") },
            text = {
                Text(
                    "移除后这个包不会再出现在贴纸面板里；" +
                        "以后用链接或包名还能再加回来。"
                )
            },
            confirmButton = {
                Button(onClick = {
                    StickerSetStore.remove(removing)
                    pendingRemove = null
                }) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text("取消")
                }
            }
        )
    }
}
