package com.tuxungram.android.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.MeStore
import java.io.File

/** Edit my own profile (v0.9.3, M1): name, bio, username
 *  (availability-checked) and profile photo. Everything saves
 *  through MeStore, which re-fetches getMe after each change,
 *  so the settings card and account labels follow at once. */
@Composable
fun EditProfileScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val me by MeStore.me.collectAsState()
    LaunchedEffect(Unit) { MeStore.refresh() }

    var firstName by remember { mutableStateOf<String?>(null) }
    var lastName by remember { mutableStateOf<String?>(null) }
    var bio by remember { mutableStateOf<String?>(null) }
    var username by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var deletePhotoConfirm by remember { mutableStateOf(false) }

    // Seed the fields from MeStore once it has data; user edits
    // (non-null local state) are never overwritten afterwards.
    val first = firstName ?: me.firstName
    val last = lastName ?: me.lastName
    val bioText = bio ?: me.bio
    val uname = username ?: me.username

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        notice = null
        // Copy the pick into cacheDir first (the pickers' house
        // idiom): TDLib reads the file asynchronously, so the
        // content URI alone is not enough.
        Thread {
            val path = try {
                val file = File(
                    context.cacheDir,
                    "avatar_${System.currentTimeMillis()}.jpg"
                )
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                file.absolutePath
            } catch (t: Throwable) {
                null
            }
            if (path == null) {
                busy = false
                notice = "这张图片读不出来，换一张试试。"
            } else {
                MeStore.setPhoto(path) { error ->
                    busy = false
                    notice = error ?: "头像已更新。"
                }
            }
        }.start()
    }

    val scroll = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("编辑资料", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .columnScrollbar(scroll)
                .verticalScroll(scroll)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (me.photoPath != null) {
                    AvatarImage(path = me.photoPath!!, sizeDp = 72)
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFF90CAF9), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            me.displayName.trim().firstOrNull()?.toString() ?: "?",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    TextButton(
                        onClick = { photoPicker.launch("image/*") },
                        enabled = !busy
                    ) { Text("换头像") }
                    if (me.photoId != 0L) {
                        TextButton(
                            onClick = { deletePhotoConfirm = true },
                            enabled = !busy
                        ) {
                            Text("删除头像", color = Color(0xFFC62828))
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = first,
                onValueChange = { firstName = it },
                label = { Text("名字") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = last,
                onValueChange = { lastName = it },
                label = { Text("姓氏（可空）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = bioText,
                onValueChange = { bio = it },
                label = { Text("简介（可空）") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val f = first.trim()
                    if (f.isEmpty()) {
                        notice = "名字不能为空。"
                        return@Button
                    }
                    busy = true
                    notice = null
                    MeStore.saveName(f, last.trim()) { nameError ->
                        if (nameError != null) {
                            busy = false
                            notice = nameError
                        } else {
                            MeStore.saveBio(bioText.trim()) { bioError ->
                                busy = false
                                notice = bioError ?: "已保存。"
                                if (bioError == null) {
                                    firstName = null
                                    lastName = null
                                    bio = null
                                }
                            }
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "保存中…" else "保存名字和简介")
            }
            Spacer(Modifier.height(24.dp))
            Text("用户名", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "别人可以通过 @用户名 找到你。保存前会先检查这个用户名能不能用；留空保存就是取消用户名。",
                color = Color.Gray, fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uname,
                onValueChange = { value ->
                    username = value.trim().removePrefix("@")
                        .filter { it.isLetterOrDigit() || it == '_' }
                },
                label = { Text("用户名") },
                prefix = { Text("@") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    busy = true
                    notice = null
                    MeStore.saveUsername(uname) { error ->
                        busy = false
                        notice = error ?: "用户名已保存。"
                        if (error == null) username = null
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存用户名")
            }
            if (notice != null) {
                Spacer(Modifier.height(14.dp))
                Text(notice!!, fontSize = 14.sp, color = Color.Gray)
            }
            if (me.phone.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "登录手机号：${me.phone}",
                    color = Color.Gray, fontSize = 13.sp
                )
            }
        }
    }

    if (deletePhotoConfirm) {
        AlertDialog(
            onDismissRequest = { deletePhotoConfirm = false },
            title = { Text("删除头像？") },
            text = { Text("删除后别人看到的将是没有头像的样子，之后随时可以再换一张。") },
            confirmButton = {
                TextButton(onClick = {
                    deletePhotoConfirm = false
                    busy = true
                    MeStore.deletePhoto { error ->
                        busy = false
                        notice = error ?: "头像已删除。"
                    }
                }) {
                    Text("删除", color = Color(0xFFC62828))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletePhotoConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}
