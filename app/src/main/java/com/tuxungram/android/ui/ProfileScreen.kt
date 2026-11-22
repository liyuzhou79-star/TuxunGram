package com.tuxungram.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.AdminStore
import com.tuxungram.android.td.AppLock
import com.tuxungram.android.td.AvatarStore
import com.tuxungram.android.td.BlockStore
import com.tuxungram.android.td.ProfilePhotoUi
import com.tuxungram.android.td.ProfilePhotosStore
import com.tuxungram.android.td.TdClient
import com.tuxungram.android.td.TdRepository
import org.json.JSONObject
import com.tuxungram.android.td.InviteLinkUi
import com.tuxungram.android.td.ChatStore
import com.tuxungram.android.td.MeStore
import com.tuxungram.android.td.MemberEntry
import com.tuxungram.android.td.ProfileStore
import com.tuxungram.android.td.SecretChatStore

/** Profile page (B5): user / group / channel info plus the
 *  member list where the chat allows reading it. Opened by
 *  tapping the chat screen's top bar. */
@Composable
fun ProfileScreen(
    chatId: Long,
    onBack: () -> Unit,
    onOpenSecretChat: (Long) -> Unit = {},
    onOpenMembers: () -> Unit = {},
    onOpenSharedMedia: () -> Unit = {},
    onOpenGroupSettings: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenEventLog: () -> Unit = {},
    onOpenJoinRequests: () -> Unit = {}
) {
    BackHandler { onBack() }
    val profile by ProfileStore.profile.collectAsState()
    val chatItem = ChatStore.itemOf(chatId)
    // Admin tools (Batch 3, C4): entries appear only when the
    // signed-in user actually holds the matching right here.
    val caps by AdminStore.caps.collectAsState()
    val adminNotice by AdminStore.notice.collectAsState()
    var titleEditOpen by remember { mutableStateOf(false) }
    var descEditOpen by remember { mutableStateOf(false) }
    // Invite links (v0.9.5, O1).
    val inviteLinks by AdminStore.inviteLinks.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var inviteCreateOpen by remember { mutableStateOf(false) }
    var revokeTarget by remember { mutableStateOf<InviteLinkUi?>(null) }
    val canInvite = caps.canInvite
    LaunchedEffect(chatId, canInvite) {
        if (canInvite) AdminStore.loadInviteLinks(chatId)
    }
    // Group photo picker (v0.9.5, O3): the pick is copied
    // into cacheDir first (TDLib reads the file
    // asynchronously), then handed to AdminStore.setChatPhoto.
    var photoNotice by remember { mutableStateOf<String?>(null) }
    val groupPhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        Thread {
            val path = try {
                val file = File(
                    context.cacheDir,
                    "group_photo_${System.currentTimeMillis()}.jpg"
                )
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                file.absolutePath
            } catch (t: Throwable) {
                null
            }
            if (path == null) {
                photoNotice = "这张图片读不出来，换一张试试。"
            } else {
                AdminStore.setChatPhoto(chatId, path)
            }
        }.start()
    }
    var memberAction by remember { mutableStateOf<MemberEntry?>(null) }
    // Secret chats (v0.6.0, E1): encryption state + fingerprint.
    val secretInfo by SecretChatStore.info.collectAsState()
    val secretNotice by SecretChatStore.notice.collectAsState()
    var closeSecretConfirm by remember { mutableStateOf(false) }
    // Block / unblock (v0.9.3, M3): shown on other users'
    // profiles; my own profile (saved messages) hides it.
    val me by MeStore.me.collectAsState()
    var blockConfirm by remember { mutableStateOf(false) }
    var blockNotice by remember { mutableStateOf<String?>(null) }
    // Profile photo wall + viewer (v0.9.12, S3).
    val profilePhotos by ProfilePhotosStore.state.collectAsState()
    val wallNotice by ProfilePhotosStore.notice.collectAsState()
    var photoViewerIndex by remember { mutableStateOf<Int?>(null) }
    var photoDeleteTarget by remember {
        mutableStateOf<ProfilePhotoUi?>(null)
    }
    // Chat export (v0.9.12, S4): dialog state + the system
    // save picker that receives the finished temp file.
    val exportState by com.tuxungram.android.td.ChatExporter.state
        .collectAsState()
    var exportDialogOpen by remember { mutableStateOf(false) }
    var exportAll by remember { mutableStateOf(false) }
    // Per-chat theme (v0.9.12, S6).
    var themeDialogOpen by remember { mutableStateOf(false) }
    var themeNotice by remember { mutableStateOf<String?>(null) }
    // Message auto-delete for regular chats (v0.9.13, T3).
    var autoDeleteDialogOpen by remember { mutableStateOf(false) }
    var autoDeleteNotice by remember { mutableStateOf<String?>(null) }
    val applyTheme: (String?) -> Unit = { emoji ->
        themeDialogOpen = false
        val query = TdClient.request("setChatTheme").apply {
            put("chat_id", chatId)
            if (emoji == null) {
                put("theme", JSONObject.NULL)
            } else {
                put("theme", JSONObject().apply {
                    put("@type", "inputChatThemeEmoji")
                    put("name", emoji)
                })
            }
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                themeNotice =
                    "这个主题没换上，换一个试试（有些主题要对方客户端支持）。"
            } else {
                themeNotice = if (emoji == null) {
                    "已恢复默认主题。"
                } else {
                    "已换成 $emoji 主题。"
                }
                ProfileStore.load(chatId)
            }
        }
    }
    var exportSavedNotice by remember { mutableStateOf<String?>(null) }
    val exportSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val donePath = com.tuxungram.android.td.ChatExporter
            .state.value.donePath
        if (uri != null && donePath != null) {
            Thread {
                try {
                    java.io.FileInputStream(File(donePath)).use { input ->
                        context.contentResolver
                            .openOutputStream(uri)?.use { output ->
                                input.copyTo(output)
                            }
                    }
                    exportSavedNotice = "已保存到你选的位置。"
                } catch (t: Throwable) {
                    exportSavedNotice = "保存失败，再试一次。"
                }
                com.tuxungram.android.td.ChatExporter.reset()
            }.start()
        }
    }
    LaunchedEffect(chatId) {
        ProfileStore.load(chatId)
        AdminStore.loadCaps(chatId)
    }
    val isSecretProfile = profile?.chatId == chatId && profile?.isSecretChat == true
    LaunchedEffect(isSecretProfile, chatId) {
        if (isSecretProfile) SecretChatStore.loadForChat(chatId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("资料", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        val p = profile
        if (p == null || p.chatId != chatId) {
            CenterColumn {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在加载资料…", color = Color.Gray)
            }
            return@Column
        }
        LaunchedEffect(p.userId) {
            if (p.userId != 0L) ProfilePhotosStore.load(p.userId)
        }
        val profileListState = rememberLazyListState()
        LazyColumn(
            state = profileListState,
            modifier = Modifier
                .fillMaxSize()
                .lazyListScrollbar(profileListState)
        ) {
            item(key = "head") {
                // Graphical header banner (v0.9.11, A2): same
                // seed-gradient banner as the Me tab, identity
                // in white over the gradient.
                HeaderBanner {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (chatItem?.photoPath != null) {
                            AvatarImage(path = chatItem.photoPath, sizeDp = 84)
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .background(
                                        Color.White.copy(alpha = 0.25f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    p.title.trim().firstOrNull()?.toString() ?: "?",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 30.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            p.title,
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            p.kindLabel,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                        for (line in p.infoLines) {
                            Text(
                                line,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
            if (p.description.isNotEmpty()) {
                item(key = "desc") {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Info, contentDescription = null,
                                tint = Color.Gray, modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "简介",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Gray
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(p.description, fontSize = 15.sp)
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
            if (p.userId != 0L &&
                profilePhotos.userId == p.userId &&
                profilePhotos.loaded &&
                profilePhotos.photos.isNotEmpty()
            ) {
                item(key = "photo_wall") {
                    // Profile photo history (v0.9.12, S3):
                    // getUserProfilePhotos — user profiles
                    // only, the tl has no equivalent call for
                    // group/channel chats.
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                    ) {
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Icon(
                                ImageIcon, contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (profilePhotos.totalCount >
                                    profilePhotos.photos.size
                                ) {
                                    "历史头像（最近 " +
                                        "${profilePhotos.photos.size} 张，" +
                                        "共 ${profilePhotos.totalCount} 张）"
                                } else {
                                    "历史头像（${profilePhotos.totalCount} 张）"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Gray
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement =
                                Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                count = profilePhotos.photos.size,
                                key = { idx ->
                                    profilePhotos.photos[idx].id
                                }
                            ) { idx ->
                                ProfilePhotoThumb(
                                    photo = profilePhotos.photos[idx]
                                ) { photoViewerIndex = idx }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
            item(key = "shared_media") {
                // Shared media (v0.9.3, M4): photos / videos /
                // files / links of this chat on their own page.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSharedMedia() }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        ImageIcon, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "共享媒体",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "图片 · 视频 · 文件 · 链接",
                        color = Color.Gray, fontSize = 13.sp
                    )
                    Icon(
                        Icons.Filled.KeyboardArrowRight,
                        contentDescription = null, tint = Color.Gray,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            item(key = "export") {
                // Chat export (v0.9.12, S4): text-only .txt
                // via ChatExporter; the dialog lives at the
                // bottom of this screen.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportDialogOpen = true }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        DownloadIcon, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "导出聊天记录",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "存成文本文件",
                        color = Color.Gray, fontSize = 13.sp
                    )
                    Icon(
                        Icons.Filled.KeyboardArrowRight,
                        contentDescription = null, tint = Color.Gray,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            if (!p.isSecretChat) {
                item(key = "auto_delete") {
                    // Message auto-delete (v0.9.13, T3): the
                    // chat-wide timer for regular chats; secret
                    // chats keep their own finer-grained picker
                    // inside the encryption section below. The
                    // value shown is the one the last getChat
                    // reported, so what another client set is
                    // echoed faithfully too.
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { autoDeleteDialogOpen = true }
                                .padding(
                                    horizontal = 20.dp, vertical = 12.dp
                                ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Icon(
                                ClockIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme
                                    .primary,
                                modifier = Modifier.size(21.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "消息自动删除",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                ProfileStore.autoDeleteLabel(
                                    p.autoDeleteSeconds
                                ),
                                color = Color.Gray, fontSize = 14.sp
                            )
                            Icon(
                                Icons.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        if (autoDeleteNotice != null) {
                            Text(
                                autoDeleteNotice!!,
                                color = Color.Gray, fontSize = 12.5.sp,
                                modifier = Modifier.padding(
                                    start = 20.dp, end = 20.dp,
                                    bottom = 8.dp
                                )
                            )
                        }
                    }
                }
            }
            if (p.userId != 0L) {
                item(key = "chat_theme") {
                    // Per-chat theme (v0.9.12, S6):
                    // setChatTheme supports private and
                    // secret chats only (td_api.tl), which is
                    // exactly the userId != 0 population here.
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { themeDialogOpen = true }
                                .padding(
                                    horizontal = 20.dp, vertical = 12.dp
                                ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Icon(
                                PaletteIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme
                                    .primary,
                                modifier = Modifier.size(21.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "对话主题",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                when {
                                    p.themeIsGift -> "礼物主题"
                                    p.themeName.isNotEmpty() ->
                                        p.themeName
                                    else -> "默认"
                                },
                                color = Color.Gray, fontSize = 14.sp
                            )
                            Icon(
                                Icons.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        if (themeNotice != null) {
                            Text(
                                themeNotice!!,
                                color = Color.Gray, fontSize = 12.5.sp,
                                modifier = Modifier.padding(
                                    start = 20.dp, end = 20.dp,
                                    bottom = 8.dp
                                )
                            )
                        }
                    }
                }
            }
            if (p.userId != 0L && !p.isSecretChat) {
                item(key = "secret_start") {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Button(
                            onClick = {
                                SecretChatStore.create(p.userId) { newChatId ->
                                    onOpenSecretChat(newChatId)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Filled.Lock, contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("发起秘密聊天")
                        }
                        Text(
                            "端到端加密，只有你们两台设备能看到内容。",
                            fontSize = 12.sp, color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
            if (p.userId != 0L && p.userId != me.id) {
                item(key = "block") {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        TextButton(onClick = {
                            if (p.isBlocked) {
                                BlockStore.setUserBlocked(p.userId, false) { error ->
                                    blockNotice = error ?: "已取消拉黑。"
                                    if (error == null) ProfileStore.load(chatId)
                                }
                            } else {
                                blockConfirm = true
                            }
                        }) {
                            Text(
                                if (p.isBlocked) "取消拉黑" else "拉黑",
                                color = if (p.isBlocked) {
                                    Color(0xFF1976D2)
                                } else {
                                    Color(0xFFC62828)
                                }
                            )
                        }
                        Text(
                            if (p.isBlocked) {
                                "你已拉黑对方：对方发来的消息你收不到。在设置 → 隐私 → 已拉黑名单里也能管理。"
                            } else {
                                "拉黑后，对方再给你发消息你收不到，对方也不会知道。"
                            },
                            fontSize = 12.sp, color = Color.Gray
                        )
                        if (blockNotice != null) {
                            Text(
                                blockNotice!!,
                                fontSize = 13.sp, color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
            if (p.isSecretChat) {
                item(key = "encryption") {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            "加密",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
                        val info =
                            if (secretInfo?.chatId == chatId) secretInfo else null
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                tint = Color(0xFF43A047),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text("端到端加密", fontSize = 15.sp)
                        }
                        Text(
                            when (info?.state) {
                                "ready" -> "状态：已建立，可以正常收发"
                                "pending" -> "状态：等待对方上线完成密钥交换"
                                "closed" -> "状态：已关闭"
                                else -> "状态：读取中…"
                            },
                            fontSize = 13.sp, color = Color.Gray
                        )
                        if (info != null && info.keyHashHex.isNotEmpty()) {
                            Text(
                                "密钥指纹（和对方 App 里显示的对一下，一致才说明没有中间人）：",
                                fontSize = 12.sp, color = Color.Gray,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Text(
                                info.keyHashHex.chunked(4)
                                    .joinToString(" "),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        if (info != null && info.layer > 0) {
                            Text(
                                "协议层：layer ${info.layer}",
                                fontSize = 12.sp, color = Color.Gray,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "阅后即焚：${SecretChatStore.ttlLabel(p.autoDeleteSeconds)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row {
                            for (option in SecretChatStore.TTL_OPTIONS) {
                                TextButton(
                                    onClick = {
                                        SecretChatStore.setSelfDestruct(
                                            chatId, option
                                        )
                                    },
                                    enabled = p.autoDeleteSeconds != option
                                ) {
                                    RadioText(
                                        p.autoDeleteSeconds == option,
                                        SecretChatStore.ttlLabel(option),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        if (secretNotice != null) {
                            Text(
                                secretNotice!!,
                                fontSize = 13.sp, color = Color.Gray
                            )
                        }
                        Text(
                            "一句实话：秘密聊天的消息只存在于你们两台设备上，" +
                                "不同步到云端、不会出现在你的其他设备上，" +
                                "换手机或卸载后也找不回来。这是秘密聊天协议" +
                                "本身的设计，不是 App 的毛病。",
                            fontSize = 12.sp, color = Color.Gray,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        if (info?.state != "closed") {
                            TextButton(onClick = { closeSecretConfirm = true }) {
                                Text(
                                    "关闭秘密聊天",
                                    color = Color(0xFFC62828)
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
            if (caps.canManage) {
                item(key = "admin") {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Build, contentDescription = null,
                                tint = Color.Gray, modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "管理",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Gray
                            )
                        }
                        if (adminNotice != null) {
                            Text(
                                adminNotice!!,
                                fontSize = 13.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        if (caps.canChangeInfo) {
                            Row {
                                TextButton(onClick = { titleEditOpen = true }) {
                                    Icon(
                                        Icons.Filled.Edit, contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text("改名")
                                }
                                TextButton(onClick = { descEditOpen = true }) {
                                    Icon(
                                        Icons.Filled.Edit, contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text("改简介")
                                }
                                TextButton(onClick = {
                                    AppLock.noteExternalLaunch()
                                    groupPhotoPicker.launch("image/*")
                                }) {
                                    Icon(
                                        ImageIcon, contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text("设置群头像")
                                }
                            }
                            if (photoNotice != null) {
                                Text(
                                    photoNotice!!,
                                    fontSize = 13.sp, color = Color.Gray
                                )
                            }
                        }
                        if (caps.canRestrict) {
                            Text(
                                "点成员可以禁言、解禁或封禁。",
                                fontSize = 12.sp, color = Color.Gray
                            )
                        }
                        if (caps.canRestrict || caps.isCreator) {
                            // Group settings page (v0.9.5, O2):
                            // permissions / slow mode / content
                            // protection, each row gated there.
                            TextButton(onClick = { onOpenGroupSettings() }) {
                                Icon(
                                    ShieldIcon, contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text("群权限与慢速模式")
                            }
                        }
                        // Administrator log (v0.9.14, U1): the
                        // server's own event stream for this
                        // chat, readable only by admins — the
                        // entry lives in the admin section and
                        // the page translates refusals.
                        TextButton(onClick = { onOpenEventLog() }) {
                            Icon(
                                Icons.Filled.List,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("管理员日志")
                        }
                        // Join requests (v0.9.14, U2): the
                        // count rides chat.pending_join_requests
                        // (peeked by the store); the page
                        // itself approves/declines.
                        val pendingCounts by
                            com.tuxungram.android.td.JoinRequestsStore
                                .pendingByChat.collectAsState()
                        val pendingCount = pendingCounts[chatId] ?: 0
                        LaunchedEffect(chatId) {
                            com.tuxungram.android.td.JoinRequestsStore
                                .peekCount(chatId)
                        }
                        TextButton(onClick = { onOpenJoinRequests() }) {
                            Icon(
                                PeopleIcon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if (pendingCount > 0) {
                                    "入群申请（$pendingCount 待处理）"
                                } else {
                                    "入群申请"
                                }
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
            if (p.canGetStatistics) {
                // Statistics entry (v0.9.13, T2): shown only
                // when the chat's FullInfo says statistics can
                // be received for it (an admin-scale supergroup
                // or channel); the page itself translates a
                // server refusal into plain language.
                item(key = "stats") {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        TextButton(onClick = { onOpenStats() }) {
                            Icon(
                                PollIcon, contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("查看统计")
                        }
                        Text(
                            "成员、消息、浏览这些数字，和官方客户端里看到的是同一份服务器统计。",
                            fontSize = 12.sp, color = Color.Gray
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
            if (caps.canInvite) {
                // Invite-link management (v0.9.5, O1): the
                // primary link plus every link I created here,
                // active and revoked.
                item(key = "invite") {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                LinkIcon, contentDescription = null,
                                tint = Color.Gray, modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "邀请链接",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Gray
                            )
                        }
                        if (p.primaryInviteLink.isNotEmpty()) {
                            Text(
                                "主邀请链接",
                                fontSize = 12.sp, color = Color.Gray,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Text(
                                p.primaryInviteLink,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Row {
                                TextButton(onClick = {
                                    clipboard.setText(
                                        AnnotatedString(p.primaryInviteLink)
                                    )
                                }) { Text("复制") }
                                TextButton(onClick = {
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            p.primaryInviteLink
                                        )
                                    }
                                    context.startActivity(
                                        Intent.createChooser(share, "分享邀请链接")
                                    )
                                }) { Text("分享") }
                            }
                        } else {
                            Text(
                                "还没有主邀请链接，新建一个就会出现在这里。",
                                fontSize = 12.sp, color = Color.Gray,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        for (link in inviteLinks) {
                            Column(
                                modifier = Modifier.padding(vertical = 5.dp)
                            ) {
                                Text(
                                    buildString {
                                        append(
                                            link.name.ifEmpty { "未命名链接" }
                                        )
                                        if (link.isPrimary) append(" · 主链接")
                                        if (link.isRevoked) append(" · 已撤销")
                                        append(" · 已有 ${link.memberCount} 人通过它加入")
                                    },
                                    fontSize = 13.sp,
                                    color = if (link.isRevoked) {
                                        Color.Gray
                                    } else {
                                        Color.Unspecified
                                    }
                                )
                                Text(
                                    link.link,
                                    fontSize = 12.sp, color = Color.Gray,
                                    maxLines = 1
                                )
                                if (!link.isRevoked) {
                                    Row {
                                        TextButton(onClick = {
                                            clipboard.setText(
                                                AnnotatedString(link.link)
                                            )
                                        }) { Text("复制") }
                                        TextButton(onClick = {
                                            val share =
                                                Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(
                                                        Intent.EXTRA_TEXT,
                                                        link.link
                                                    )
                                                }
                                            context.startActivity(
                                                Intent.createChooser(
                                                    share, "分享邀请链接"
                                                )
                                            )
                                        }) { Text("分享") }
                                        TextButton(onClick = {
                                            revokeTarget = link
                                        }) {
                                            Text(
                                                "撤销",
                                                color = Color(0xFFC62828)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        TextButton(onClick = { inviteCreateOpen = true }) {
                            Text("＋ 新建邀请链接")
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
            if (p.canGetMembers) {
                // Dedicated members page (v0.9.2, L4): paged,
                // searchable, administrators pinned — the
                // embedded preview below stays as a shortcut.
                item(key = "members_entry") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenMembers() }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            PeopleIcon, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "成员",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "查看全部",
                            color = Color.Gray, fontSize = 13.sp
                        )
                        Icon(
                            Icons.Filled.KeyboardArrowRight,
                            contentDescription = null, tint = Color.Gray,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
            if (p.members.isNotEmpty()) {
                item(key = "members_header") {
                    Text(
                        "成员",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }
                items(items = p.members, key = { it.userId }) { member ->
                    val actionable = caps.canRestrict &&
                        member.statusKind in
                        listOf("member", "restricted", "banned")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (actionable) {
                                    Modifier.clickable { memberAction = member }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 20.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(0xFFB0BEC5), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                member.name.trim().firstOrNull()?.toString() ?: "?",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            member.name.ifEmpty { "用户 ${member.userId}" },
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(member.statusText, color = Color.Gray, fontSize = 12.sp)
                    }
                }
                if (!p.membersExhausted) {
                    item(key = "members_more") {
                        TextButton(
                            onClick = { ProfileStore.loadMoreMembers() },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text("加载更多成员")
                        }
                    }
                }
            } else if (p.canGetMembers) {
                item(key = "members_loading") {
                    Text(
                        "正在加载成员…",
                        color = Color.Gray, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }
    }

    // ---- Admin dialogs (Batch 3, C4) ----
    val current = profile
    if (titleEditOpen && current != null) {
        var draft by remember(current.chatId) { mutableStateOf(current.title) }
        AlertDialog(
            onDismissRequest = { titleEditOpen = false },
            title = { Text("改名") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val t = draft.trim()
                        if (t.isNotEmpty() && t != current.title) {
                            AdminStore.setTitle(chatId, t)
                        }
                        titleEditOpen = false
                    },
                    enabled = draft.isNotBlank()
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { titleEditOpen = false }) {
                    Text("取消")
                }
            }
        )
    }
    if (descEditOpen && current != null) {
        var draft by remember(current.chatId) {
            mutableStateOf(current.description)
        }
        AlertDialog(
            onDismissRequest = { descEditOpen = false },
            title = { Text("改简介") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        AdminStore.setDescription(chatId, draft.trim())
                        descEditOpen = false
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { descEditOpen = false }) {
                    Text("取消")
                }
            }
        )
    }
    val target = memberAction
    if (target != null) {
        AlertDialog(
            onDismissRequest = { memberAction = null },
            title = {
                Text(target.name.ifEmpty { "用户 ${target.userId}" })
            },
            text = {
                Column {
                    when (target.statusKind) {
                        "member" -> {
                            TextButton(onClick = {
                                AdminStore.restrictMember(chatId, target.userId)
                                memberAction = null
                            }) { Text("禁言（不能发言）") }
                            TextButton(onClick = {
                                AdminStore.banMember(chatId, target.userId)
                                memberAction = null
                            }) {
                                Text(
                                    "封禁（移出并禁止再进）",
                                    color = Color(0xFFC62828)
                                )
                            }
                        }
                        "restricted" -> {
                            TextButton(onClick = {
                                AdminStore.unrestrictMember(chatId, target.userId)
                                memberAction = null
                            }) { Text("解除禁言") }
                            TextButton(onClick = {
                                AdminStore.banMember(chatId, target.userId)
                                memberAction = null
                            }) {
                                Text(
                                    "封禁（移出并禁止再进）",
                                    color = Color(0xFFC62828)
                                )
                            }
                        }
                        "banned" -> {
                            TextButton(onClick = {
                                AdminStore.unbanMember(chatId, target.userId)
                                memberAction = null
                            }) { Text("解除封禁") }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { memberAction = null }) {
                    Text("取消")
                }
            }
        )
    }
    // Theme picker (v0.9.12, S6): a small curated emoji set;
    // the name IS the theme id for inputChatThemeEmoji.
    if (themeDialogOpen && current != null &&
        current.userId != 0L
    ) {
        AlertDialog(
            onDismissRequest = { themeDialogOpen = false },
            title = { Text("对话主题") },
            text = {
                Column {
                    Text(
                        "选一个表情作为这个对话的主题，对方那边也会变。" +
                            "恢复默认选最下面一项。",
                        color = Color.Gray, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    val options = listOf(
                        "❤️", "🔥", "🎉", "👍", "😂", "😎",
                        "🌙", "⭐", "🎈", "🌸", "⚡", "🐱"
                    )
                    for (rowEmojis in options.chunked(6)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (emoji in rowEmojis) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            applyTheme(emoji)
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 26.sp)
                                }
                            }
                        }
                    }
                    TextButton(onClick = { applyTheme(null) }) {
                        Text("默认（无主题）")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    themeDialogOpen = false
                }) { Text("取消") }
            }
        )
    }
    // Auto-delete dialog (v0.9.13, T3): the regular-chat
    // presets; a current value outside the presets (set
    // from another client) is listed first so it stays
    // visible and selectable-as-is.
    if (autoDeleteDialogOpen) {
        val current = profile?.autoDeleteSeconds ?: 0
        val presets = listOf(0, 86400, 604800, 2592000)
        AlertDialog(
            onDismissRequest = { autoDeleteDialogOpen = false },
            title = { Text("消息自动删除") },
            text = {
                Column {
                    Text(
                        "打开后，这个对话里新发的消息过一段时间会被自动删掉，双方都删。群里改这一项需要管理员权限。",
                        color = Color.Gray, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    if (current !in presets) {
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 9.dp)
                        ) {
                            RadioText(
                                true,
                                "当前：${ProfileStore.autoDeleteLabel(current)}" +
                                    "（在别的客户端设的）"
                            )
                        }
                    }
                    for (option in presets) {
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    autoDeleteDialogOpen = false
                                    ProfileStore.setAutoDeleteTime(
                                        chatId, option
                                    ) { _, text ->
                                        autoDeleteNotice = text
                                    }
                                }
                                .padding(vertical = 9.dp)
                        ) {
                            RadioText(
                                current == option,
                                if (option == 0) {
                                    "关闭"
                                } else {
                                    "${ProfileStore.autoDeleteLabel(option)}后删除"
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    autoDeleteDialogOpen = false
                }) { Text("取消") }
            }
        )
    }
    // Chat export dialog (v0.9.12, S4): range choice, then
    // live progress from ChatExporter, then hand the temp
    // file to the system save picker.
    if (exportDialogOpen) {
        val es = exportState
        AlertDialog(
            onDismissRequest = {
                if (!es.running) {
                    exportDialogOpen = false
                    exportSavedNotice = null
                    com.tuxungram.android.td.ChatExporter.reset()
                }
            },
            title = { Text("导出聊天记录") },
            text = {
                Column {
                    when {
                        es.running -> {
                            Text(
                                "正在拉取历史记录，已拿到 ${es.count} 条…"
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "消息多的群拉全部记录要花点时间，可以随时取消，取消不会生成文件。",
                                color = Color.Gray, fontSize = 12.5.sp
                            )
                        }
                        es.donePath != null -> Text(
                            "导出完成，共 ${es.doneCount} 条。" +
                                "点「保存到…」选个位置把它存下来。"
                        )
                        es.error != null -> Text(es.error!!)
                        exportSavedNotice != null ->
                            Text(exportSavedNotice!!)
                        else -> {
                            Text(
                                "把这个对话的文字记录存成一个 .txt 文件：" +
                                    "每条一行，带时间和发送者。" +
                                    "图片、视频、文件只留一行占位说明，不导文件本身。"
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { exportAll = false }
                                    .padding(vertical = 6.dp),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                RadioText(!exportAll, "最近 1000 条")
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { exportAll = true }
                                    .padding(vertical = 6.dp),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                RadioText(exportAll, "全部记录")
                            }
                            if (exportAll) {
                                Text(
                                    "全部记录：消息特别多的群可能要几分钟，期间请别关 App。",
                                    color = Color.Gray,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                when {
                    es.running -> TextButton(onClick = {
                        com.tuxungram.android.td.ChatExporter.cancel()
                    }) { Text("取消导出") }
                    es.donePath != null -> TextButton(onClick = {
                        exportSaver.launch(
                            exportFileName(current?.title ?: "")
                        )
                    }) { Text("保存到…") }
                    es.error != null ||
                        exportSavedNotice != null -> TextButton(
                        onClick = {
                            exportDialogOpen = false
                            exportSavedNotice = null
                            com.tuxungram.android.td.ChatExporter
                                .reset()
                        }
                    ) { Text("关闭") }
                    else -> TextButton(onClick = {
                        val cid = current?.chatId
                            ?: return@TextButton
                        com.tuxungram.android.td.ChatExporter.start(
                            context, cid, current?.title ?: "",
                            if (exportAll) 0 else 1000
                        )
                    }) { Text("开始导出") }
                }
            },
            dismissButton = {
                if (!es.running && es.donePath == null) {
                    TextButton(onClick = {
                        exportDialogOpen = false
                        exportSavedNotice = null
                        com.tuxungram.android.td.ChatExporter.reset()
                    }) { Text("取消") }
                }
            }
        )
    }
    // Photo viewer (v0.9.12, S3): full-screen pager over the
    // profile-photo history. On your own profile each page
    // offers delete (deleteProfilePhoto); the viewer closes
    // after a delete and the wall reloads underneath.
    val viewerIndex = photoViewerIndex
    if (viewerIndex != null && profilePhotos.loaded &&
        profilePhotos.photos.isNotEmpty() &&
        profilePhotos.userId != 0L &&
        profilePhotos.userId == current?.userId
    ) {
        Dialog(
            onDismissRequest = { photoViewerIndex = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            val pagerState = rememberPagerState(
                initialPage = viewerIndex.coerceIn(
                    0, profilePhotos.photos.lastIndex
                )
            ) { profilePhotos.photos.size }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val photo = profilePhotos.photos[page]
                    LaunchedEffect(page) {
                        ProfilePhotosStore.ensureFull(photo)
                    }
                    val fullPath =
                        AvatarStore.pathForFile(photo.fullFileId)
                            ?: AvatarStore.pathForFile(
                                photo.thumbFileId
                            )
                    val bitmap by produceState<
                        android.graphics.Bitmap?
                    >(null, fullPath) {
                        value = fullPath?.let {
                            kotlinx.coroutines.withContext(
                                kotlinx.coroutines.Dispatchers.IO
                            ) { decodeCached(it, 1600) }
                        }
                    }
                    val bmp = bitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        photoViewerIndex = null
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "关闭",
                            tint = Color.White
                        )
                    }
                    val currentPhoto = profilePhotos.photos[
                        pagerState.currentPage.coerceIn(
                            0, profilePhotos.photos.lastIndex
                        )
                    ]
                    Column {
                        Text(
                            "${pagerState.currentPage + 1} / " +
                                "${profilePhotos.photos.size}",
                            color = Color.White, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (currentPhoto.addedDate > 0) {
                            Text(
                                java.text.SimpleDateFormat(
                                    "yyyy年M月d日",
                                    java.util.Locale.getDefault()
                                ).format(
                                    currentPhoto.addedDate * 1000L
                                ),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (profilePhotos.userId == me.id) {
                        IconButton(onClick = {
                            photoDeleteTarget = currentPhoto
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除这张头像",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
    if (photoDeleteTarget != null) {
        AlertDialog(
            onDismissRequest = { photoDeleteTarget = null },
            title = { Text("删除这张历史头像？") },
            text = {
                Text("删的是服务器上的这张历史头像，删掉就恢复不了了。")
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = photoDeleteTarget
                    photoDeleteTarget = null
                    photoViewerIndex = null
                    if (target != null) {
                        ProfilePhotosStore.delete(target)
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = {
                    photoDeleteTarget = null
                }) { Text("取消") }
            }
        )
    }
    if (wallNotice != null) {
        AlertDialog(
            onDismissRequest = { ProfilePhotosStore.clearNotice() },
            title = { Text("历史头像") },
            text = { Text(wallNotice!!) },
            confirmButton = {
                TextButton(onClick = {
                    ProfilePhotosStore.clearNotice()
                }) { Text("知道了") }
            }
        )
    }
    if (blockConfirm && current != null && current.userId != 0L) {
        AlertDialog(
            onDismissRequest = { blockConfirm = false },
            title = { Text("拉黑 ${current.title}？") },
            text = {
                Text("拉黑后你收不到对方发来的消息，对方不会收到通知。之后可以在对方资料页或设置 → 隐私 → 已拉黑名单里取消。")
            },
            confirmButton = {
                TextButton(onClick = {
                    blockConfirm = false
                    BlockStore.setUserBlocked(current.userId, true) { error ->
                        blockNotice = error ?: "已拉黑。"
                        if (error == null) ProfileStore.load(chatId)
                    }
                }) {
                    Text("拉黑", color = Color(0xFFC62828))
                }
            },
            dismissButton = {
                TextButton(onClick = { blockConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
    if (inviteCreateOpen) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { inviteCreateOpen = false },
            title = { Text("新建邀请链接") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("名称（方便你自己认，可留空）") }
                    )
                    Text(
                        "新链接长期有效、人数不限，进群不用审批。",
                        fontSize = 12.sp, color = Color.Gray,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    AdminStore.createInviteLink(chatId, name.trim())
                    inviteCreateOpen = false
                }) { Text("新建") }
            },
            dismissButton = {
                TextButton(onClick = { inviteCreateOpen = false }) {
                    Text("取消")
                }
            }
        )
    }
    val revoke = revokeTarget
    if (revoke != null) {
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            title = { Text("撤销这个邀请链接？") },
            text = {
                Text("撤销后，拿到这个链接的人就不能再通过它进群了。已经进群的人不受影响。")
            },
            confirmButton = {
                TextButton(onClick = {
                    revokeTarget = null
                    AdminStore.revokeInviteLink(chatId, revoke.link)
                }) {
                    Text("撤销", color = Color(0xFFC62828))
                }
            },
            dismissButton = {
                TextButton(onClick = { revokeTarget = null }) {
                    Text("取消")
                }
            }
        )
    }
    if (closeSecretConfirm) {
        AlertDialog(
            onDismissRequest = { closeSecretConfirm = false },
            title = { Text("关闭秘密聊天？") },
            text = {
                Text("关闭后这个秘密聊天就结束了，双方都不能再往里面发消息。聊天记录只留在这台设备上。")
            },
            confirmButton = {
                TextButton(onClick = {
                    closeSecretConfirm = false
                    val info = secretInfo
                    if (info != null && info.chatId == chatId) {
                        SecretChatStore.close(chatId, info.secretChatId)
                    }
                }) {
                    Text("关闭", color = Color(0xFFC62828))
                }
            },
            dismissButton = {
                TextButton(onClick = { closeSecretConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/** One thumbnail of the profile photo wall (v0.9.12, S3):
 *  the smallest photoSize, decoded through the shared cache;
 *  an image glyph stands in until the file lands. */
@Composable
private fun ProfilePhotoThumb(
    photo: ProfilePhotoUi,
    onClick: () -> Unit
) {
    val path = AvatarStore.pathForFile(photo.thumbFileId)
    val bitmap by produceState<android.graphics.Bitmap?>(
        null, path
    ) {
        value = path?.let {
            kotlinx.coroutines.withContext(
                kotlinx.coroutines.Dispatchers.IO
            ) { decodeCached(it, 192) }
        }
    }
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                ImageIcon, contentDescription = null,
                tint = Color.Gray, modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** Suggested save-picker name for a chat export (v0.9.12,
 *  S4): the chat title with filesystem-hostile characters
 *  folded to underscores. */
private fun exportFileName(title: String): String {
    val cleaned = buildString {
        for (ch in title) {
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_') {
                append(ch)
            } else {
                append('_')
            }
        }
    }.trim('_').take(40)
    val base = if (cleaned.isEmpty()) "对话" else cleaned
    return "聊天记录-$base.txt"
}
