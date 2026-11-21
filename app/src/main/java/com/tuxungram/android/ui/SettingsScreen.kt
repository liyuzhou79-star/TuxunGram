package com.tuxungram.android.ui


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.AuthState
import com.tuxungram.android.td.ChatItem
import com.tuxungram.android.td.ChatStore
import com.tuxungram.android.td.MessageStore
import com.tuxungram.android.td.MsgItem
import com.tuxungram.android.td.MsgKind
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.content.FileProvider
import com.tuxungram.android.td.AccountStore
import com.tuxungram.android.td.AppLock
import com.tuxungram.android.td.KeepAliveService
import com.tuxungram.android.td.StorageStore
import com.tuxungram.android.td.ProxyConfig
import com.tuxungram.android.td.ProxyStore
import com.tuxungram.android.td.TdRepository
import com.tuxungram.android.td.UiSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

import com.tuxungram.android.BuildConfig

/** Human-readable byte size for the storage page. */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}

/** Open a downloaded file with a system viewer via FileProvider
 *  (same sharing path the chat screen uses). */
fun openDownloadedFile(context: android.content.Context, path: String) {
    val file = java.io.File(path)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(
        context, "com.tuxungram.android.fileprovider", file
    )
    val ext = file.extension.lowercase()
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_ACTIVITY_NEW_TASK
        )
    }
    try {
        context.startActivity(
            Intent.createChooser(intent, "打开文件")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (t: Throwable) {
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenSessions: () -> Unit = {}) {
    BackHandler { onBack() }
    val settingsContext = LocalContext.current
    // Proxy list (v0.6.0, E4): entries live in ProxyStore; the
    // editor dialog below creates or edits one entry.
    val proxyEntries by ProxyStore.proxies.collectAsState()
    val activeProxyId by ProxyStore.activeId.collectAsState()
    var proxyEditor by remember { mutableStateOf<ProxyConfig?>(null) }
    var proxyEditorOpen by remember { mutableStateOf(false) }
    var proxyDeleteTarget by remember { mutableStateOf<ProxyConfig?>(null) }
    var pingResults by remember {
        mutableStateOf<Map<Long, String>>(emptyMap())
    }

    val settingsScroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .columnScrollbar(settingsScroll)
            .verticalScroll(settingsScroll)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("设置", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text("代理", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "连不上 Telegram 时用。可以存多个代理，点哪个就用哪个，立刻生效，下次打开 App 也会自动用上次那个，不用再靠全局 VPN。",
            color = Color.Gray, fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        if (proxyEntries.isEmpty()) {
            Text(
                "还没有代理，点下面添加一个。",
                color = Color.Gray, fontSize = 13.sp
            )
        }
        for (entry in proxyEntries) {
            val isActive = entry.id == activeProxyId
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        TdRepository.clearError()
                        ProxyStore.setActive(entry.id)
                    }
                    .padding(vertical = 8.dp)
            ) {
                Text(if (isActive) "● " else "○ ")
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.name.ifEmpty { entry.server },
                        fontSize = 15.sp,
                        fontWeight = if (isActive) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        }
                    )
                    Text(
                        "${if (entry.type == "mtproto") "MTProto" else "SOCKS5"} · " +
                            "${entry.server}:${entry.port}" +
                            (pingResults[entry.id]?.let { " · $it" } ?: ""),
                        color = Color.Gray, fontSize = 12.sp
                    )
                }
                if (isActive) {
                    Text(
                        "使用中",
                        color = Color(0xFF2E7D32), fontSize = 12.sp
                    )
                }
                TextButton(onClick = {
                    pingResults = pingResults + (entry.id to "测速中…")
                    ProxyStore.ping(entry) { seconds ->
                        pingResults = pingResults + (entry.id to
                            if (seconds == null) {
                                "连不上"
                            } else {
                                "延迟 ${(seconds * 1000).toInt()} ms"
                            })
                    }
                }) { Text("测速", fontSize = 13.sp) }
                TextButton(onClick = {
                    proxyEditor = entry
                    proxyEditorOpen = true
                }) { Text("编辑", fontSize = 13.sp) }
                TextButton(onClick = { proxyDeleteTarget = entry }) {
                    Text("删除", fontSize = 13.sp, color = Color(0xFFC62828))
                }
            }
        }
        Row {
            Button(onClick = {
                proxyEditor = null
                proxyEditorOpen = true
            }) {
                Text("添加代理")
            }
            if (activeProxyId != 0L) {
                Spacer(Modifier.width(10.dp))
                TextButton(onClick = { ProxyStore.disableActive() }) {
                    Text("停用代理（直连）")
                }
            }
        }
        ErrorText()
        if (proxyEditorOpen) {
            val editing = proxyEditor
            var name by remember(editing?.id) {
                mutableStateOf(editing?.name ?: "")
            }
            var type by remember(editing?.id) {
                mutableStateOf(editing?.type ?: "socks5")
            }
            var server by remember(editing?.id) {
                mutableStateOf(editing?.server ?: "")
            }
            var port by remember(editing?.id) {
                mutableStateOf((editing?.port ?: 1080).toString())
            }
            var username by remember(editing?.id) {
                mutableStateOf(editing?.username ?: "")
            }
            var password by remember(editing?.id) {
                mutableStateOf(editing?.password ?: "")
            }
            var secret by remember(editing?.id) {
                mutableStateOf(editing?.secret ?: "")
            }
            var editorError by remember(editing?.id) {
                mutableStateOf<String?>(null)
            }
            AlertDialog(
                onDismissRequest = { proxyEditorOpen = false },
                title = {
                    Text(if (editing == null) "添加代理" else "编辑代理")
                },
                text = {
                    Column {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("名称（可空）") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("类型：")
                            TextButton(
                                onClick = { type = "socks5" },
                                enabled = type != "socks5"
                            ) {
                                Text(if (type == "socks5") "● SOCKS5" else "○ SOCKS5")
                            }
                            TextButton(
                                onClick = { type = "mtproto" },
                                enabled = type != "mtproto"
                            ) {
                                Text(if (type == "mtproto") "● MTProto" else "○ MTProto")
                            }
                        }
                        OutlinedTextField(
                            value = server,
                            onValueChange = { server = it },
                            label = { Text("服务器地址") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = port,
                            onValueChange = {
                                port = it.filter { ch -> ch.isDigit() }
                            },
                            label = { Text("端口") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            )
                        )
                        if (type == "socks5") {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("用户名（可空）") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("密码（可空）") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation =
                                    PasswordVisualTransformation()
                            )
                        } else {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = secret,
                                onValueChange = { secret = it },
                                label = { Text("密钥 secret（十六进制）") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        if (editorError != null) {
                            Text(
                                editorError!!,
                                color = Color(0xFFC62828),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val portNum = port.toIntOrNull()
                        if (server.isBlank() || portNum == null ||
                            portNum !in 1..65535
                        ) {
                            editorError = "地址或端口不对，检查一下"
                        } else {
                            TdRepository.clearError()
                            if (editing == null) {
                                val created = ProxyStore.add(
                                    name = name.trim(),
                                    type = type,
                                    server = server.trim(),
                                    port = portNum,
                                    username = username.trim(),
                                    password = password,
                                    secret = secret.trim()
                                )
                                // First proxy ever: turn it on
                                // right away, like the old form.
                                if (proxyEntries.isEmpty()) {
                                    ProxyStore.setActive(created.id)
                                }
                            } else {
                                ProxyStore.update(
                                    editing.copy(
                                        name = name.trim(),
                                        type = type,
                                        server = server.trim(),
                                        port = portNum,
                                        username = username.trim(),
                                        password = password,
                                        secret = secret.trim()
                                    )
                                )
                            }
                            proxyEditorOpen = false
                        }
                    }) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { proxyEditorOpen = false }) {
                        Text("取消")
                    }
                }
            )
        }
        val deleteTarget = proxyDeleteTarget
        if (deleteTarget != null) {
            AlertDialog(
                onDismissRequest = { proxyDeleteTarget = null },
                title = { Text("删除这个代理？") },
                text = {
                    Text(
                        deleteTarget.name.ifEmpty { deleteTarget.server } +
                            "（${deleteTarget.server}:${deleteTarget.port}）" +
                            if (deleteTarget.id == activeProxyId) {
                                "。它正在使用中，删掉会同时停用代理。"
                            } else {
                                ""
                            }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        ProxyStore.remove(deleteTarget.id)
                        proxyDeleteTarget = null
                    }) {
                        Text("删除", color = Color(0xFFC62828))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { proxyDeleteTarget = null }) {
                        Text("取消")
                    }
                }
            )
        }
        Spacer(Modifier.height(28.dp))
        Text("显示", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "未读数上限：对话很多时，角标数字太长不好看。超过上限就显示成「上限+」。",
            color = Color.Gray, fontSize = 13.sp
        )
        val unreadCap by UiSettings.unreadCap.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (cap in UiSettings.UNREAD_CAPS) {
                TextButton(
                    onClick = { UiSettings.setUnreadCap(cap) },
                    enabled = unreadCap != cap
                ) {
                    Text(if (unreadCap == cap) "● $cap+" else "○ $cap+")
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("外观", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "深浅模式和主题色，选完立刻生效，不用重启。",
            color = Color.Gray, fontSize = 13.sp
        )
        val themeMode by UiSettings.themeMode.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically) {
            val modes = listOf(
                UiSettings.THEME_SYSTEM to "跟随系统",
                UiSettings.THEME_LIGHT to "浅色",
                UiSettings.THEME_DARK to "深色"
            )
            for ((mode, label) in modes) {
                TextButton(
                    onClick = { UiSettings.setThemeMode(mode) },
                    enabled = themeMode != mode
                ) {
                    Text(if (themeMode == mode) "● $label" else "○ $label")
                }
            }
        }
        val seedIndex by UiSettings.seedIndex.collectAsState()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            for ((index, argb) in UiSettings.SEED_COLORS.withIndex()) {
                val selected = seedIndex == index
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(4.dp)
                        .background(Color(argb), CircleShape)
                        .then(
                            if (selected) {
                                Modifier.border(
                                    2.dp,
                                    MaterialTheme.colorScheme.onSurface,
                                    CircleShape
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable { UiSettings.setSeedIndex(index) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Text("✓", color = Color.White, fontSize = 15.sp)
                    }
                }
                Text(UiSettings.SEED_NAMES[index], fontSize = 12.sp)
                Spacer(Modifier.width(10.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("聊天背景", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "只换聊天页的底色，浅色和深色模式各有一套配好的颜色。",
            color = Color.Gray, fontSize = 13.sp
        )
        val chatBg by UiSettings.chatBg.collectAsState()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 4.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            for ((index, pair) in UiSettings.CHAT_BG_COLORS.withIndex()) {
                val selected = chatBg == index
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(4.dp)
                        .background(
                            if (index == 0) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                Color(pair.first)
                            },
                            CircleShape
                        )
                        .then(
                            if (selected) {
                                Modifier.border(
                                    2.dp,
                                    MaterialTheme.colorScheme.onSurface,
                                    CircleShape
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable { UiSettings.setChatBg(index) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Text("✓", color = Color.DarkGray, fontSize = 15.sp)
                    }
                }
                Text(UiSettings.CHAT_BG_NAMES[index], fontSize = 12.sp)
                Spacer(Modifier.width(10.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("气泡样式", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        val bubbleStyle by UiSettings.bubbleStyle.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically) {
            for ((style, label) in listOf(
                UiSettings.BUBBLE_MODERN to "现代（圆润）",
                UiSettings.BUBBLE_CLASSIC to "经典（方正）"
            )) {
                TextButton(
                    onClick = { UiSettings.setBubbleStyle(style) },
                    enabled = bubbleStyle != style
                ) {
                    Text(if (bubbleStyle == style) "● $label" else "○ $label")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("字体大小", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "消息文字和列表文字一起缩放，选完立刻生效。",
            color = Color.Gray, fontSize = 13.sp
        )
        val fontSize by UiSettings.fontSize.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically) {
            for ((index, label) in UiSettings.FONT_NAMES.withIndex()) {
                TextButton(
                    onClick = { UiSettings.setFontSize(index) },
                    enabled = fontSize != index
                ) {
                    Text(if (fontSize == index) "● $label" else "○ $label")
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("账号", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "可以登录多个 Telegram 账号，每个账号的数据各自独立。切换账号时 App 会自动重启一下，稍等片刻就好。",
            color = Color.Gray, fontSize = 13.sp
        )
        val accountSlots by AccountStore.slots.collectAsState()
        val currentSlot by AccountStore.current.collectAsState()
        val accountLabels by AccountStore.labels.collectAsState()
        for (slot in accountSlots) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (slot != currentSlot) {
                            TdRepository.switchAccount(slot)
                        }
                    }
                    .padding(vertical = 8.dp)
            ) {
                Text(if (slot == currentSlot) "● " else "○ ")
                Text(
                    accountLabels[slot] ?: "账号 ${slot + 1}",
                    fontSize = 15.sp
                )
                if (slot == currentSlot) {
                    Text(
                        "（当前）",
                        color = Color.Gray, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
        TextButton(onClick = { TdRepository.addAccount() }) {
            Text("添加账号")
        }
        Spacer(Modifier.height(28.dp))
        Text("安全", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "应用锁：设一个 4-6 位数字密码，打开 App 和切回来时要先解锁。密码只存哈希，不存明文，Telegram 那边也看不到。",
            color = Color.Gray, fontSize = 13.sp
        )
        val pinSet by AppLock.pinSet.collectAsState()
        val biometricEnabled by AppLock.biometricEnabled.collectAsState()
        val autoLockMinutes by AppLock.autoLockMinutes.collectAsState()
        var pinDialog by remember { mutableStateOf<String?>(null) }
        var lockNotice by remember { mutableStateOf<String?>(null) }
        if (!pinSet) {
            Button(
                onClick = { pinDialog = "set" },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                Text("设置应用锁密码")
            }
        } else {
            Row(modifier = Modifier.padding(top = 6.dp)) {
                TextButton(onClick = { pinDialog = "change" }) {
                    Text("修改密码")
                }
                TextButton(onClick = { pinDialog = "clear" }) {
                    Text("关闭应用锁", color = Color(0xFFC62828))
                }
            }
            val biometricOk = remember {
                AppLock.biometricAvailable(settingsContext)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("指纹解锁")
                    if (!biometricOk) {
                        Text(
                            "这台设备没有可用的指纹（没录入或不支持），所以用不了。",
                            color = Color.Gray, fontSize = 12.sp
                        )
                    }
                }
                Switch(
                    checked = biometricEnabled && biometricOk,
                    onCheckedChange = { AppLock.setBiometricEnabled(it) },
                    enabled = biometricOk
                )
            }
            Text(
                "自动锁定：切到后台多久以后，再回来要重新解锁。",
                color = Color.Gray, fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                for (option in AppLock.AUTO_LOCK_OPTIONS) {
                    TextButton(
                        onClick = { AppLock.setAutoLockMinutes(option) },
                        enabled = autoLockMinutes != option
                    ) {
                        Text(
                            (if (autoLockMinutes == option) "● " else "○ ") +
                                AppLock.autoLockLabel(option)
                        )
                    }
                }
            }
        }
        if (lockNotice != null) {
            Text(
                lockNotice!!, color = Color.Gray, fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onOpenSessions) {
            Text("已登录设备（会话管理）")
        }
        Text(
            "看哪些设备登着你的账号，不认识的可以踢下线。",
            color = Color.Gray, fontSize = 12.sp
        )
        val dialogMode = pinDialog
        if (dialogMode != null) {
            var currentPin by remember(dialogMode) { mutableStateOf("") }
            var newPin by remember(dialogMode) { mutableStateOf("") }
            var confirmPin by remember(dialogMode) { mutableStateOf("") }
            var dialogError by remember(dialogMode) { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { pinDialog = null },
                title = {
                    Text(
                        when (dialogMode) {
                            "set" -> "设置应用锁密码"
                            "change" -> "修改应用锁密码"
                            else -> "关闭应用锁"
                        }
                    )
                },
                text = {
                    Column {
                        if (dialogMode != "set") {
                            OutlinedTextField(
                                value = currentPin,
                                onValueChange = {
                                    currentPin = it.filter { ch -> ch.isDigit() }.take(6)
                                },
                                label = { Text("现在的密码") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (dialogMode != "clear") {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = newPin,
                                onValueChange = {
                                    newPin = it.filter { ch -> ch.isDigit() }.take(6)
                                },
                                label = { Text("新密码（4-6 位数字）") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = confirmPin,
                                onValueChange = {
                                    confirmPin = it.filter { ch -> ch.isDigit() }.take(6)
                                },
                                label = { Text("再输一次新密码") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (dialogError != null) {
                            Text(
                                dialogError!!,
                                color = Color(0xFFC62828),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        when (dialogMode) {
                            "set" -> {
                                if (newPin.length !in 4..6) {
                                    dialogError = "密码要 4-6 位数字"
                                } else if (newPin != confirmPin) {
                                    dialogError = "两次输入不一样"
                                } else {
                                    AppLock.setPin(newPin)
                                    lockNotice = "应用锁已打开。"
                                    pinDialog = null
                                }
                            }
                            "change" -> {
                                if (!AppLock.verifyPin(currentPin)) {
                                    dialogError = "现在的密码不对"
                                } else if (newPin.length !in 4..6) {
                                    dialogError = "新密码要 4-6 位数字"
                                } else if (newPin != confirmPin) {
                                    dialogError = "两次输入不一样"
                                } else {
                                    AppLock.setPin(newPin)
                                    lockNotice = "密码已修改。"
                                    pinDialog = null
                                }
                            }
                            else -> {
                                if (!AppLock.verifyPin(currentPin)) {
                                    dialogError = "密码不对"
                                } else {
                                    AppLock.clearPin()
                                    lockNotice = "应用锁已关闭。"
                                    pinDialog = null
                                }
                            }
                        }
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { pinDialog = null }) {
                        Text("取消")
                    }
                }
            )
        }
        Spacer(Modifier.height(28.dp))
        Text("通知", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "打开后，App 切到后台也会留一个服务继续收消息，有新消息就弹通知，点通知直接进对话。",
            color = Color.Gray, fontSize = 13.sp
        )
        val keepAlive by UiSettings.keepAlive.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("后台接收消息", modifier = Modifier.weight(1f))
            Switch(
                checked = keepAlive,
                onCheckedChange = { on ->
                    UiSettings.setKeepAlive(on)
                    if (on) {
                        KeepAliveService.start(settingsContext)
                    } else {
                        KeepAliveService.stop(settingsContext)
                    }
                }
            )
        }
        Text(
            "一句实话：有些手机系统管后台管得严（比如魅族），光在这里打开还不够——请再去系统设置里给 TuxunGram 开「自启动」，电池用量选「不限制」，不然把 App 从最近任务里划掉后，系统可能还是会把它冻住，通知就迟到甚至收不到。这一层是系统说了算，App 里改不了。",
            color = Color.Gray, fontSize = 13.sp
        )
        Spacer(Modifier.height(28.dp))
        Text("存储空间", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        var storageTick by remember { mutableIntStateOf(0) }
        val storageState = produceState<Pair<com.tuxungram.android.td.StorageInfo?, List<com.tuxungram.android.td.DownloadedEntry>>>(
            initialValue = null to emptyList(),
            key1 = storageTick
        ) {
            value = withContext(Dispatchers.IO) {
                StorageStore.measure(settingsContext) to
                    StorageStore.listDownloaded(settingsContext)
            }
        }
        val storageInfo = storageState.value.first
        val downloaded = storageState.value.second
        if (storageInfo == null) {
            Text("正在统计占用…", color = Color.Gray, fontSize = 13.sp)
        } else {
            Text(
                "聊天数据库 ${formatBytes(storageInfo.databaseBytes)}（聊天记录的索引，不会清）",
                fontSize = 14.sp
            )
            Text(
                "下载的文件 ${formatBytes(storageInfo.filesBytes)}，共 ${storageInfo.fileCount} 个",
                fontSize = 14.sp
            )
        }
        var confirmClear by remember { mutableStateOf(false) }
        var clearedTip by remember { mutableStateOf<String?>(null) }
        Button(
            onClick = { confirmClear = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        ) {
            Text("清理下载的文件缓存")
        }
        if (clearedTip != null) {
            Text(
                clearedTip!!, color = Color.Gray, fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text("清理文件缓存？") },
                text = {
                    Text(
                        "只删已经下载到手机上的图片、视频、文件这些缓存，不删聊天记录和数据库。" +
                            "以后再打开这些内容时会自动重新下载（会花一点流量）。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmClear = false
                        clearedTip = "正在清理…"
                        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
                        scope.launch {
                            val freed = StorageStore.clearFileCache(settingsContext)
                            withContext(Dispatchers.Main) {
                                clearedTip = "已清理，释放了 ${formatBytes(freed)}。"
                                storageTick++
                            }
                        }
                    }) { Text("清理") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) {
                        Text("取消")
                    }
                }
            )
        }
        if (downloaded.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "最近下载的文件（点按打开，长按删除）",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = Color.Gray
            )
            for (entry in downloaded.take(50)) {
                var askDelete by remember(entry.path) { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { openDownloadedFile(settingsContext, entry.path) },
                            onLongClick = { askDelete = true }
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.name, fontSize = 14.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            formatBytes(entry.size),
                            color = Color.Gray, fontSize = 12.sp
                        )
                    }
                }
                if (askDelete) {
                    AlertDialog(
                        onDismissRequest = { askDelete = false },
                        title = { Text("删除这个文件？") },
                        text = { Text(entry.name) },
                        confirmButton = {
                            TextButton(onClick = {
                                askDelete = false
                                StorageStore.deleteOne(entry.path)
                                storageTick++
                            }) { Text("删除") }
                        },
                        dismissButton = {
                            TextButton(onClick = { askDelete = false }) {
                                Text("取消")
                            }
                        }
                    )
                }
            }
            if (downloaded.size > 50) {
                Text(
                    "只列最近 50 个，其余 ${downloaded.size - 50} 个可用上面的清理按钮一并清掉。",
                    color = Color.Gray, fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "TuxunGram ${BuildConfig.VERSION_NAME}",
            color = Color.Gray, fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { TdRepository.logOut() }) {
            Text("退出登录", color = Color(0xFFC62828))
        }
    }
}

