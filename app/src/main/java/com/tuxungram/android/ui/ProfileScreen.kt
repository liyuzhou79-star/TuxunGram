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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.tuxungram.android.td.BlockStore
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
    onOpenGroupSettings: () -> Unit = {}
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
        val profileListState = rememberLazyListState()
        LazyColumn(
            state = profileListState,
            modifier = Modifier
                .fillMaxSize()
                .lazyListScrollbar(profileListState)
        ) {
            item(key = "head") {
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
                                .background(Color(0xFF90CAF9), CircleShape),
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
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(p.kindLabel, color = Color.Gray, fontSize = 13.sp)
                    for (line in p.infoLines) {
                        Text(line, color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
            if (p.description.isNotEmpty()) {
                item(key = "desc") {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            "简介",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(p.description, fontSize = 15.sp)
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
                    Text(
                        "共享媒体",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "图片 · 视频 · 文件 · 链接 ›",
                        color = Color.Gray, fontSize = 13.sp
                    )
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
                            Text("🔒 发起秘密聊天")
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
                        Text(
                            "🔒 端到端加密",
                            fontSize = 15.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
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
                                    Text(
                                        (if (p.autoDeleteSeconds == option) "● " else "○ ") +
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
                        Text(
                            "管理",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
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
                                    Text("改名")
                                }
                                TextButton(onClick = { descEditOpen = true }) {
                                    Text("改简介")
                                }
                                TextButton(onClick = {
                                    AppLock.noteExternalLaunch()
                                    groupPhotoPicker.launch("image/*")
                                }) {
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
                                Text("群权限与慢速模式")
                            }
                        }
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
                        Text(
                            "邀请链接",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
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
                        Text(
                            "成员",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "查看全部 ›",
                            color = Color.Gray, fontSize = 13.sp
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
