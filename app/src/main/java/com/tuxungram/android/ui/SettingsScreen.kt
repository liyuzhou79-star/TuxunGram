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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.tuxungram.android.td.BackgroundAccounts
import com.tuxungram.android.td.AppLock
import com.tuxungram.android.td.MeStore
import com.tuxungram.android.td.PrivacySettingDef
import com.tuxungram.android.td.PrivacyStore
import com.tuxungram.android.td.PrivacyTier
import com.tuxungram.android.td.KeepAliveService
import com.tuxungram.android.td.CrashLog
import com.tuxungram.android.td.SettingsBackup
import com.tuxungram.android.td.StorageStore
import com.tuxungram.android.td.ProxyConfig
import com.tuxungram.android.td.ProxyStore
import com.tuxungram.android.td.ScopeNotifyStore
import com.tuxungram.android.td.ScopeNotifyUi
import com.tuxungram.android.td.TdRepository
import com.tuxungram.android.td.UiSettings
import com.tuxungram.android.td.UpdateChecker
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
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSessions: () -> Unit = {},
    onOpenStickerSets: () -> Unit = {},
    onOpenEditProfile: () -> Unit = {},
    onOpenBlocked: () -> Unit = {},
    onOpenChangelog: () -> Unit = {},
    onOpenGuide: () -> Unit = {}
) {
    BackHandler { onBack() }
    val settingsContext = LocalContext.current
    // Proxy list (v0.6.0, E4): entries live in ProxyStore; the
    // editor dialog below creates or edits one entry.
    val proxyEntries by ProxyStore.proxies.collectAsState()
    val activeProxyId by ProxyStore.activeId.collectAsState()
    var proxyEditor by remember { mutableStateOf<ProxyConfig?>(null) }
    var proxyEditorOpen by remember { mutableStateOf(false) }
    // Proxy-link prefill (v0.9.9, W3): a t.me/proxy or
    // t.me/socks tap lands here with the editor already open
    // on the parsed parameters; saving stays a manual tap.
    val proxyPrefill by ProxyStore.pendingPrefill.collectAsState()
    LaunchedEffect(proxyPrefill) {
        val pre = proxyPrefill
        if (pre != null) {
            ProxyStore.pendingPrefill.value = null
            proxyEditor = pre
            proxyEditorOpen = true
        }
    }
    var proxyDeleteTarget by remember { mutableStateOf<ProxyConfig?>(null) }
    var pingResults by remember {
        mutableStateOf<Map<Long, String>>(emptyMap())
    }
    // Chat wallpaper picture (v0.9.4, N4): the picked image
    // is copied into app storage so the system picker grant
    // expiring cannot take the wallpaper with it.
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val target = java.io.File(
                    settingsContext.filesDir, "chat_wallpaper.jpg"
                )
                settingsContext.contentResolver.openInputStream(uri)
                    ?.use { inputStream ->
                        target.outputStream().use { output ->
                            inputStream.copyTo(output)
                        }
                    }
                UiSettings.setChatBgImage(target.absolutePath)
            } catch (t: Throwable) {
                // Copy failed: keep the previous wallpaper.
            }
        }
    }

    // Settings backup (v0.9.6, P4): export writes the
    // SettingsBackup JSON into a document the user picks;
    // import reads one back and applies it all-or-nothing.
    // The export warning (proxy passwords are NOT in the
    // file) is shown before the document picker opens.
    var exportWarnOpen by remember { mutableStateOf(false) }
    var backupResult by remember { mutableStateOf<String?>(null) }
    val backupScope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            backupScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        settingsContext.contentResolver
                            .openOutputStream(uri)?.use { out ->
                                out.write(
                                    SettingsBackup.exportJson(
                                        BuildConfig.VERSION_NAME
                                    ).toByteArray(Charsets.UTF_8)
                                )
                            }
                        true
                    } catch (t: Throwable) {
                        false
                    }
                }
                backupResult = if (ok) {
                    "设置已导出。提醒一句：代理密码没有进备份文件，导入后需要重填一次。"
                } else {
                    "导出失败：文件没写成，再试一次。"
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            backupScope.launch {
                backupResult = withContext(Dispatchers.IO) {
                    try {
                        val text = settingsContext.contentResolver
                            .openInputStream(uri)?.use { input ->
                                input.readBytes()
                                    .toString(Charsets.UTF_8)
                            }
                        if (text == null) {
                            "这个文件读不出来，设置没有改动。"
                        } else {
                            SettingsBackup.importJson(text)
                                ?: "设置已导入。备份里的代理没有密码，需要时会去代理列表里补上密码。"
                        }
                    } catch (t: Throwable) {
                        "这个文件读不出来，设置没有改动。"
                    }
                }
            }
        }
    }
    if (exportWarnOpen) {
        AlertDialog(
            onDismissRequest = { exportWarnOpen = false },
            title = { Text("导出设置") },
            text = {
                Text(
                    "导出的文件包含主题、外观设置和代理列表。代理密码和 MTProto 密钥不会写进文件（留空），导入后需要重填一次。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    exportWarnOpen = false
                    AppLock.noteExternalLaunch()
                    exportLauncher.launch("tuxungram-settings.json")
                }) { Text("继续导出") }
            },
            dismissButton = {
                TextButton(onClick = { exportWarnOpen = false }) {
                    Text("取消")
                }
            }
        )
    }
    if (backupResult != null) {
        AlertDialog(
            onDismissRequest = { backupResult = null },
            title = { Text("设置备份") },
            text = { Text(backupResult!!) },
            confirmButton = {
                TextButton(onClick = { backupResult = null }) {
                    Text("知道了")
                }
            }
        )
    }
    // Crash records (v0.9.6, P5): the count is read once when
    // the page opens (a record can only exist from a previous
    // launch anyway); the dialog offers view / copy / clear.
    var crashDialogOpen by remember { mutableStateOf(false) }
    var crashCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        crashCount = CrashLog.files(settingsContext).size
    }
    val crashClipboard = LocalClipboardManager.current
    if (crashDialogOpen) {
        val newest = CrashLog.files(settingsContext).firstOrNull()
        val crashScroll = rememberScrollState()
        AlertDialog(
            onDismissRequest = { crashDialogOpen = false },
            title = { Text("崩溃记录") },
            text = {
                Column(
                    modifier = Modifier
                        .height(300.dp)
                        .verticalScroll(crashScroll)
                ) {
                    Text(
                        if (newest != null) {
                            CrashLog.read(newest)
                        } else {
                            "没有记录。"
                        },
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newest != null) {
                        crashClipboard.setText(
                            AnnotatedString(CrashLog.read(newest))
                        )
                    }
                }) { Text("复制") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        CrashLog.clear(settingsContext)
                        crashCount = 0
                        crashDialogOpen = false
                    }) { Text("清除") }
                    TextButton(onClick = { crashDialogOpen = false }) {
                        Text("关闭")
                    }
                }
            }
        )
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
        // Personal card (v0.9.3, M1): my avatar + name +
        // @username; tapping opens the edit-profile page.
        // Everything renders from MeStore, so an edit shows
        // here the moment it lands.
        val me by MeStore.me.collectAsState()
        GroupCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenEditProfile() }
                .padding(vertical = 10.dp)
        ) {
            if (me.photoPath != null) {
                AvatarImage(path = me.photoPath!!, sizeDp = 52)
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color(0xFF90CAF9), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        me.displayName.trim().firstOrNull()?.toString() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)) {
                Text(
                    me.displayName.ifEmpty { "我的资料" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    when {
                        me.username.isNotEmpty() -> "@${me.username}"
                        me.phone.isNotEmpty() -> me.phone
                        else -> "点这里编辑资料"
                    },
                    color = Color.Gray, fontSize = 13.sp
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
        }
        Spacer(Modifier.height(8.dp))
        SectionTitle(GlobeIcon, "代理")
        GroupCard {
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
                Icon(
                    GlobeIcon, contentDescription = null,
                    tint = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Gray
                    },
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.width(10.dp))
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
                                RadioText(type == "socks5", "SOCKS5", fontSize = 14.sp)
                            }
                            TextButton(
                                onClick = { type = "mtproto" },
                                enabled = type != "mtproto"
                            ) {
                                RadioText(type == "mtproto", "MTProto", fontSize = 14.sp)
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
        }
        Spacer(Modifier.height(28.dp))
        SectionTitle(Icons.Filled.List, "显示")
        GroupCard {
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
                    RadioText(unreadCap == cap, "$cap+", fontSize = 14.sp)
                }
            }
        }
        }
        Spacer(Modifier.height(28.dp))
        SectionTitle(PaletteIcon, "外观")
        GroupCard {
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
                    RadioText(themeMode == mode, label, fontSize = 14.sp)
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
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
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
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.DarkGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(UiSettings.CHAT_BG_NAMES[index], fontSize = 12.sp)
                Spacer(Modifier.width(10.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "聊天背景图片",
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "从相册选一张图当所有聊天的背景，和上面的纯色二选一；" +
                "显示时会自动压暗一层，保证消息文字看得清。",
            color = Color.Gray, fontSize = 13.sp
        )
        val chatBgImage by UiSettings.chatBgImage.collectAsState()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp)
        ) {
            TextButton(onClick = {
                AppLock.noteExternalLaunch()
                wallpaperPicker.launch("image/*")
            }) {
                Text(if (chatBgImage.isEmpty()) "选择图片" else "换一张")
            }
            if (chatBgImage.isNotEmpty()) {
                TextButton(onClick = { UiSettings.setChatBgImage("") }) {
                    Text("恢复纯色")
                }
                Text(
                    "已设置背景图片",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                )
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
                    RadioText(bubbleStyle == style, label, fontSize = 14.sp)
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
                    RadioText(fontSize == index, label, fontSize = 14.sp)
                }
            }
        }
        }
        Spacer(Modifier.height(28.dp))
        SectionTitle(Icons.Filled.AccountCircle, "账号")
        GroupCard {
        Spacer(Modifier.height(4.dp))
        Text(
            "可以登录多个 Telegram 账号，每个账号的数据各自独立。其他账号会保持在线：来了新消息会带着账号名通知你，每个账号的未读数显示在这一行右边。切换账号时 App 会自动重启一下，稍等片刻就好。",
            color = Color.Gray, fontSize = 13.sp
        )
        val accountSlots by AccountStore.slots.collectAsState()
        val currentSlot by AccountStore.current.collectAsState()
        val accountLabels by AccountStore.labels.collectAsState()
        val accountAvatars by AccountStore.avatars.collectAsState()
        // Unread totals per account (v0.9.10): background
        // accounts publish theirs from TDLib's
        // updateUnreadMessageCount; the running account's
        // total is the live sum over its chat list.
        val bgUnread by BackgroundAccounts.unreadBySlot.collectAsState()
        val fgChats by ChatStore.chats.collectAsState()
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
                // Avatar per slot (v0.9.9, W0): the running
                // account's photo comes from MeStore live; the
                // other slots show the path remembered while
                // they were last active (their clients are not
                // running). A stale path whose file is gone
                // falls back to the initial letter.
                val slotLabel = accountLabels[slot] ?: "账号 ${slot + 1}"
                val slotPath = (
                    if (slot == currentSlot) {
                        me.photoPath ?: accountAvatars[slot]
                    } else {
                        accountAvatars[slot]
                    }
                    )?.takeIf { java.io.File(it).exists() }
                if (slotPath != null) {
                    AvatarImage(path = slotPath, sizeDp = 40)
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF90CAF9), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            slotLabel.trim().firstOrNull()?.toString()
                                ?: "?",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(slotLabel, fontSize = 15.sp)
                if (slot == currentSlot) {
                    Text(
                        "（当前）",
                        color = Color.Gray, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                val slotUnread = if (slot == currentSlot) {
                    fgChats.sumOf { it.unreadCount }
                } else {
                    bgUnread[slot] ?: 0
                }
                if (slotUnread > 0) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            UiSettings.badgeText(slotUnread),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
        TextButton(onClick = { TdRepository.addAccount() }) {
            Text("添加账号")
        }
        }
        Spacer(Modifier.height(28.dp))
        SectionTitle(Icons.Filled.Lock, "安全")
        GroupCard {
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
                Icon(
                    Icons.Filled.Lock, contentDescription = null,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(8.dp))
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
                Icon(
                    Icons.Filled.Face, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(23.dp)
                )
                Spacer(Modifier.width(14.dp))
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
                        RadioText(
                            autoLockMinutes == option,
                            AppLock.autoLockLabel(option),
                            fontSize = 14.sp
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
        IconRow(
            icon = Icons.Filled.Phone,
            title = "已登录设备（会话管理）",
            subtitle = "看哪些设备登着你的账号，不认识的可以踢下线。",
            onClick = onOpenSessions,
            trailing = {
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = null, tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        PrivacySection()
        Spacer(Modifier.height(10.dp))
        IconRow(
            icon = BlockIcon,
            title = "已拉黑名单",
            subtitle = "被你拉黑的人都在这里，可以逐个解除。",
            onClick = onOpenBlocked,
            trailing = {
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = null, tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
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
        }
        Spacer(Modifier.height(28.dp))
        SectionTitle(Icons.Filled.Notifications, "通知")
        GroupCard {
        Spacer(Modifier.height(4.dp))
        Text(
            "打开后，App 切到后台也会留一个服务继续收消息，有新消息就弹通知，点通知直接进对话。",
            color = Color.Gray, fontSize = 13.sp
        )
        val keepAlive by UiSettings.keepAlive.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Notifications, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(23.dp)
            )
            Spacer(Modifier.width(14.dp))
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
        }
        Spacer(Modifier.height(16.dp))
        GroupCard {
        Spacer(Modifier.height(4.dp))
        Text(
            "按类别同步的通知偏好（与服务器同步）",
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold
        )
        Text(
            "下面三组存在 Telegram 服务器上，在这里改，官方客户端那边也跟着变；反过来也一样。" +
                "和上面的「后台接收消息」不是一回事：那个是这台手机上的总开关，它关了，下面设得再细也不会弹通知。",
            color = Color.Gray, fontSize = 13.sp
        )
        // Server-synced scope settings (v0.9.12, S1):
        // getScopeNotificationSettings /
        // setScopeNotificationSettings per scope.
        val scopeStates by ScopeNotifyStore.scopes.collectAsState()
        val scopeNotice by ScopeNotifyStore.notice.collectAsState()
        LaunchedEffect(Unit) { ScopeNotifyStore.loadAll() }
        if (scopeNotice != null) {
            Text(
                scopeNotice!!, color = Color(0xFFC62828), fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        for ((scopeType, scopeLabel) in listOf(
            ScopeNotifyStore.SCOPE_PRIVATE to "私聊",
            ScopeNotifyStore.SCOPE_GROUPS to "群组",
            ScopeNotifyStore.SCOPE_CHANNELS to "频道"
        )) {
            val st = scopeStates[scopeType]
            Spacer(Modifier.height(14.dp))
            Text(
                scopeLabel, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            when {
                st == null || (!st.loaded && !st.failed) ->
                    Text(
                        "正在从服务器读取…",
                        color = Color.Gray, fontSize = 13.sp
                    )
                st.failed -> Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "读取失败。", color = Color.Gray, fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        ScopeNotifyStore.load(scopeType)
                    }) { Text("重试") }
                }
                else -> ScopeNotifyEditor(st)
            }
        }
        }
        Spacer(Modifier.height(28.dp))
        SectionTitle(Icons.Filled.Face, "贴纸包")
        GroupCard {
        Spacer(Modifier.height(4.dp))
        Text(
            "查看已装的贴纸包、移除不用的，或者用链接添加新的。",
            color = Color.Gray, fontSize = 13.sp
        )
        Button(
            onClick = onOpenStickerSets,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        ) {
            Icon(
                Icons.Filled.Face, contentDescription = null,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("管理贴纸包")
        }
        }
        Spacer(Modifier.height(28.dp))
        SectionTitle(StorageIcon, "存储空间")
        GroupCard {
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
            IconRow(
                icon = StorageIcon,
                title = "聊天数据库",
                subtitle = "${formatBytes(storageInfo.databaseBytes)}（聊天记录的索引，不会清）"
            )
            IconRow(
                icon = FolderIcon,
                title = "下载的文件",
                subtitle = "${formatBytes(storageInfo.filesBytes)}，共 ${storageInfo.fileCount} 个"
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
            Icon(
                Icons.Filled.Delete, contentDescription = null,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(8.dp))
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
        // One delete-confirm target for the whole file list
        // (audit fix A7): a per-row remember() inside the loop
        // below is positional state — after the list changes it
        // can attach the confirm dialog to a different file
        // than the one the user long-pressed.
        var fileDeleteTarget by remember {
            mutableStateOf<com.tuxungram.android.td.DownloadedEntry?>(null)
        }
        if (downloaded.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "最近下载的文件（点按打开，长按删除）",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = Color.Gray
            )
            for (entry in downloaded.take(50)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { openDownloadedFile(settingsContext, entry.path) },
                            onLongClick = { fileDeleteTarget = entry }
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
            }
            val targetFile = fileDeleteTarget
            if (targetFile != null) {
                AlertDialog(
                    onDismissRequest = { fileDeleteTarget = null },
                    title = { Text("删除这个文件？") },
                    text = { Text(targetFile.name) },
                    confirmButton = {
                        TextButton(onClick = {
                            fileDeleteTarget = null
                            StorageStore.deleteOne(targetFile.path)
                            storageTick++
                        }) { Text("删除") }
                    },
                    dismissButton = {
                        TextButton(onClick = { fileDeleteTarget = null }) {
                            Text("取消")
                        }
                    }
                )
            }
            if (downloaded.size > 50) {
                Text(
                    "只列最近 50 个，其余 ${downloaded.size - 50} 个可用上面的清理按钮一并清掉。",
                    color = Color.Gray, fontSize = 12.sp
                )
            }
        }
        }
        Spacer(Modifier.height(28.dp))
        // About + update check (v0.9.5, O5). The check is
        // the app's only network call outside TDLib and runs
        // only when this button is tapped — stated on the
        // page itself, in small print under the button.
        SectionTitle(Icons.Filled.Info, "关于")
        GroupCard {
        val updateInfo by UpdateChecker.state.collectAsState()
        TextButton(
            onClick = { UpdateChecker.check(BuildConfig.VERSION_NAME) },
            enabled = updateInfo.state != "checking"
        ) {
            Icon(
                Icons.Filled.Refresh, contentDescription = null,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (updateInfo.state == "checking") {
                    "正在检查…"
                } else {
                    "检查更新"
                }
            )
        }
        when (updateInfo.state) {
            "latest" -> Text(
                "已是最新版本（${updateInfo.latestVersion}）。",
                color = Color.Gray, fontSize = 13.sp
            )
            "available" -> Column {
                Text(
                    "有新版本：${updateInfo.latestVersion}",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
                if (updateInfo.notes.isNotEmpty()) {
                    Text(
                        updateInfo.notes,
                        color = Color.Gray, fontSize = 12.sp,
                        maxLines = 8,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                TextButton(onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse(updateInfo.url)
                    )
                    settingsContext.startActivity(intent)
                }) {
                    Text("去下载")
                }
            }
            "error" -> Text(
                "检查失败：连不上 GitHub。检查一下网络（或代理）再试一次。",
                color = Color(0xFFC62828), fontSize = 13.sp
            )
        }
        Text(
            "检查更新只在你点上面这一项时才会联网，去 GitHub 看一眼有没有新版；平时不会自己联网。",
            color = Color.Gray, fontSize = 12.sp
        )
        // Changelog + guide replay (v0.9.6, P2/P3).
        IconRow(
            icon = Icons.Filled.List,
            title = "更新日志",
            onClick = onOpenChangelog,
            trailing = {
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = null, tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        IconRow(
            icon = Icons.Filled.Info,
            title = "新手引导（再看一遍）",
            onClick = onOpenGuide,
            trailing = {
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = null, tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        // Settings backup (v0.9.6, P4).
        IconRow(
            icon = Icons.Filled.Share,
            title = "导出设置（备份）",
            onClick = { exportWarnOpen = true },
            trailing = {
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = null, tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        IconRow(
            icon = DownloadIcon,
            title = "导入设置",
            onClick = {
                AppLock.noteExternalLaunch()
                importLauncher.launch("application/json")
            },
            trailing = {
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = null, tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "TuxunGram ${BuildConfig.VERSION_NAME}",
            color = Color.Gray, fontSize = 13.sp
        )
        // Crash records (v0.9.6, P5): the entry only appears
        // when a crash was recorded on this phone. Records
        // live only on this device and are never uploaded.
        if (crashCount > 0) {
            IconRow(
                icon = Icons.Filled.Warning,
                title = "上次崩溃记录（$crashCount）",
                onClick = { crashDialogOpen = true },
                trailing = {
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = null, tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
            )
            Text(
                "崩溃记录只保存在这台手机上，不会上传。",
                color = Color.Gray, fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { TdRepository.logOut() }) {
            Icon(
                Icons.Filled.ExitToApp, contentDescription = null,
                tint = Color(0xFFC62828),
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("退出登录", color = Color(0xFFC62828))
        }
        }
    }
}


/** Privacy settings (v0.9.3, M2), rendered inside the settings
 *  page's 安全 section. One row per userPrivacySetting*: the
 *  value shown is the tier PrivacyStore actually read back
 *  from the server — while a read is outstanding the row says
 *  读取中， and a failed read surfaces as an error with a
 *  retry, never as a guessed tier. Tapping a row opens the
 *  three-tier picker （所有人 / 联系人 / 没人）. */
@Composable
fun PrivacySection() {
    val tiers by PrivacyStore.tiers.collectAsState()
    val loadError by PrivacyStore.loadError.collectAsState()
    LaunchedEffect(Unit) { PrivacyStore.load() }
    var editing by remember { mutableStateOf<PrivacySettingDef?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    Spacer(Modifier.height(18.dp))
    SectionTitle(EyeIcon, "隐私")
    Spacer(Modifier.height(4.dp))
    Text(
        "这些是账号级设置，在哪个客户端改都算数。点一项就能改。",
        color = Color.Gray, fontSize = 13.sp
    )
    if (loadError != null) {
        Text(
            loadError!!,
            color = Color(0xFFC62828), fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        TextButton(onClick = { PrivacyStore.load() }) { Text("重试") }
    }
    for (def in PrivacyStore.SETTINGS) {
        val tier = tiers[def.type] ?: PrivacyTier.UNKNOWN
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { editing = def }
                .padding(vertical = 9.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(def.label, fontSize = 15.sp)
                Text(def.hint, color = Color.Gray, fontSize = 12.sp)
            }
            Text(
                PrivacyStore.tierLabel(tier),
                color = Color.Gray, fontSize = 13.sp
            )
        }
    }
    if (notice != null) {
        Text(
            notice!!, color = Color.Gray, fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    val def = editing
    if (def != null) {
        val current = tiers[def.type] ?: PrivacyTier.UNKNOWN
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(def.label) },
            text = {
                Column {
                    Text(def.hint, color = Color.Gray, fontSize = 13.sp)
                    if (current == PrivacyTier.CUSTOM) {
                        Text(
                            "现在是「自定义」：你在其他客户端单独设过例外名单。改成下面三档之一会把那些例外清掉。",
                            color = Color.Gray, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    for (tier in listOf(
                        PrivacyTier.EVERYONE,
                        PrivacyTier.CONTACTS,
                        PrivacyTier.NOBODY
                    )) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editing = null
                                    PrivacyStore.set(def, tier) { error ->
                                        notice = error
                                            ?: "「${def.label}」已设为${PrivacyStore.tierLabel(tier)}。"
                                    }
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            RadioText(
                                current == tier,
                                PrivacyStore.tierLabel(tier)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("取消") }
            }
        )
    }
}

/** Editor for one server-synced notification scope
 *  (v0.9.12, S1). Every control writes through immediately,
 *  like the rest of this page; the store re-reads the scope
 *  from the server if a save is refused. mute_for is a raw
 *  seconds count from the server — the presets write 0 /
 *  1 h / 8 h / forever, and a leftover timed mute from
 *  another client is shown as its remaining time instead of
 *  being silently bucketed. */
@Composable
private fun ScopeNotifyEditor(st: ScopeNotifyUi) {
    val muteOptions = listOf(
        0 to "不静音",
        3600 to "静音 1 小时",
        28800 to "静音 8 小时",
        ScopeNotifyUi.MUTE_FOREVER to "一直静音"
    )
    val matchedPreset = muteOptions.any { it.first == st.muteFor }
    Text(
        "静音", fontSize = 13.sp, color = Color.Gray,
        modifier = Modifier.padding(top = 6.dp)
    )
    for ((value, label) in muteOptions) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    ScopeNotifyStore.save(st.copy(muteFor = value))
                }
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioText(st.muteFor == value, label, fontSize = 14.sp)
        }
    }
    if (!matchedPreset && st.muteFor > 0) {
        Text(
            "当前是限时静音（还剩约 ${formatMuteRemaining(st.muteFor)}，" +
                "可能是在别的客户端设的），选上面任意一项就会改掉它。",
            color = Color.Gray, fontSize = 12.5.sp
        )
    }
    ScopeSwitchRow("通知里显示消息预览", st.showPreview) { on ->
        ScopeNotifyStore.save(st.copy(showPreview = on))
    }
    ScopeSwitchRow("有人 @ 我时通知", !st.disableMentions) { on ->
        ScopeNotifyStore.save(st.copy(disableMentions = !on))
    }
    ScopeSwitchRow("置顶消息通知", !st.disablePinned) { on ->
        ScopeNotifyStore.save(st.copy(disablePinned = !on))
    }
    Text(
        "动态通知", fontSize = 13.sp, color = Color.Gray,
        modifier = Modifier.padding(top = 8.dp)
    )
    val storyMode = when {
        st.muteStoriesUseDefault -> 0
        st.muteStories -> 1
        else -> 2
    }
    for ((mode, label) in listOf(
        0 to "跟随默认", 1 to "静音", 2 to "正常提醒"
    )) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    ScopeNotifyStore.save(
                        when (mode) {
                            0 -> st.copy(muteStoriesUseDefault = true)
                            1 -> st.copy(
                                muteStoriesUseDefault = false,
                                muteStories = true
                            )
                            else -> st.copy(
                                muteStoriesUseDefault = false,
                                muteStories = false
                            )
                        }
                    )
                }
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioText(storyMode == mode, label, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ScopeSwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp)
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun formatMuteRemaining(seconds: Int): String = when {
    seconds >= 86400 -> "${seconds / 86400} 天"
    seconds >= 3600 -> "${seconds / 3600} 小时"
    seconds >= 60 -> "${seconds / 60} 分钟"
    else -> "$seconds 秒"
}
