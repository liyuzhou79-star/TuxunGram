package com.tuxungram.android.ui

import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.graphics.Brush
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
import com.tuxungram.android.td.UiSettings
import org.json.JSONObject
import com.tuxungram.android.td.InviteLinkUi
import com.tuxungram.android.td.ChatStore
import com.tuxungram.android.td.MeStore
import com.tuxungram.android.td.MemberEntry
import com.tuxungram.android.td.ProfileStore
import com.tuxungram.android.td.SecretChatStore
import com.tuxungram.android.td.WebAppStore

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
    // Live chat item (v0.9.20, AA2): presence, member count
    // and mute state for the official header + action row
    // come from the chat-list flow so they stay current
    // while this page is open; itemOf is the first-frame
    // fallback.
    val allChats by ChatStore.chats.collectAsState()
    val liveItem = allChats.firstOrNull { it.id == chatId } ?: chatItem
    val profilePalette = OfficialTheme.current()
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
    // Restrict-duration picker (v0.9.53, X4): same flow
    // as the members page — muting asks for how long.
    var memberRestrict by remember { mutableStateOf<MemberEntry?>(null) }
    // Secret chats (v0.6.0, E1): encryption state + fingerprint.
    val secretInfo by SecretChatStore.info.collectAsState()
    val secretNotice by SecretChatStore.notice.collectAsState()
    var closeSecretConfirm by remember { mutableStateOf(false) }
    // Block / unblock (v0.9.3, M3): shown on other users'
    // profiles; my own profile (saved messages) hides it.
    val me by MeStore.me.collectAsState()
    var blockConfirm by remember { mutableStateOf(false) }
    var blockNotice by remember { mutableStateOf<String?>(null) }
    var deleteContactConfirm by remember { mutableStateOf(false) }
    var contactNotice by remember { mutableStateOf<String?>(null) }
    // User note editor (v0.9.38, E2): null = closed;
    // otherwise the draft text the dialog opened with.
    var noteDraft by remember { mutableStateOf<String?>(null) }
    var noteNotice by remember { mutableStateOf<String?>(null) }
    var boostExpanded by remember { mutableStateOf(false) }
    var boostSelection by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var boostNotice by remember { mutableStateOf<String?>(null) }
    val boostStatus by com.tuxungram.android.td.BoostStore.status
        .collectAsState()
    val boostSlots by com.tuxungram.android.td.BoostStore.slots
        .collectAsState()
    val boostLoaded by com.tuxungram.android.td.BoostStore.loaded
        .collectAsState()
    val boostError by com.tuxungram.android.td.BoostStore.error
        .collectAsState()
    val boostList by com.tuxungram.android.td.BoostStore.boosts
        .collectAsState()
    val boostListTotal by com.tuxungram.android.td.BoostStore
        .boostsTotal.collectAsState()
    val boostListVisible by com.tuxungram.android.td.BoostStore
        .boostsVisible.collectAsState()
    val boostListHasMore by com.tuxungram.android.td.BoostStore
        .boostsHasMore.collectAsState()
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
    // Per-chat wallpaper (v0.9.16, W5): a local picture that
    // overrides the global chat wallpaper for this chat
    // only. The pick is copied into filesDir under a stable
    // per-chat name, so re-picking replaces the old picture.
    val chatWallpapers by UiSettings.chatWallpapers.collectAsState()
    val hasOwnWallpaper = chatWallpapers.containsKey(chatId)
    var wallpaperDialogOpen by remember { mutableStateOf(false) }
    var wallpaperNotice by remember { mutableStateOf<String?>(null) }
    val chatWallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        Thread {
            val path = try {
                val file = File(
                    context.filesDir,
                    "chat_wallpaper_$chatId.jpg"
                )
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                file.absolutePath
            } catch (t: Throwable) {
                null
            }
            if (path == null) {
                wallpaperNotice = "这张图片读不出来，换一张试试。"
            } else {
                UiSettings.setChatWallpaper(chatId, path)
                wallpaperNotice = "已设为这个对话的专属壁纸。"
            }
        }.start()
    }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(profilePalette.graySection)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(profilePalette.actionBarBg)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = profilePalette.actionBarIcon
                )
            }
            Text(
                "资料", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                color = profilePalette.actionBarTitle
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(profilePalette.divider)
        )
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
                // Official profile header (v0.9.20, AA2):
                // the identity block sits on the plain bar
                // background (the A2 gradient banner is gone
                // from this page) — a 100 dp avatar (tap opens
                // the photo wall's viewer when the wall has
                // photos), the name, and one status line:
                // presence for users, member count for groups
                // and channels, the kind label as fallback.
                val wallReady = p.userId != 0L &&
                    profilePhotos.userId == p.userId &&
                    profilePhotos.photos.isNotEmpty()
                val statusLine = when {
                    p.isSecretChat -> "秘密聊天"
                    p.userId != 0L ->
                        liveItem?.presenceText?.takeIf {
                            it.isNotEmpty()
                        } ?: p.kindLabel
                    p.memberCount > 0 ->
                        if (p.kindLabel == "频道") {
                            "${formatCount(p.memberCount)} 位订阅者"
                        } else {
                            "${formatCount(p.memberCount)} 位成员"
                        }
                    (liveItem?.memberCount ?: 0) > 0 ->
                        if (p.kindLabel == "频道") {
                            "${formatCount(liveItem?.memberCount ?: 0)} 位订阅者"
                        } else {
                            "${formatCount(liveItem?.memberCount ?: 0)} 位成员"
                        }
                    else -> p.kindLabel
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(profilePalette.actionBarBg)
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = if (wallReady) {
                            Modifier.clickable { photoViewerIndex = 0 }
                        } else {
                            Modifier
                        }
                    ) {
                        if (liveItem?.photoPath != null) {
                            AvatarImage(path = liveItem.photoPath, sizeDp = 100)
                        } else {
                            val pair = if (liveItem != null) {
                                OfficialTheme.avatarColors(
                                    liveItem.id, OfficialTheme.dark
                                )
                            } else {
                                OfficialTheme.current().savedColors
                            }
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(pair.first, pair.second)
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    p.title.trim().firstOrNull()?.toString() ?: "?",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 36.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        p.title,
                        color = profilePalette.textPrimary,
                        // 20 sp matches the official profile
                        // header name (v0.9.49, T1 — was 22).
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        statusLine,
                        color = if (
                            p.userId != 0L && liveItem?.isOnline == true
                        ) {
                            profilePalette.accentText
                        } else {
                            profilePalette.textSecondary
                        },
                        fontSize = 14.sp
                    )
                }
            }
            item(key = "actions") {
                // Official action row (v0.9.20, AA2): the
                // icon-over-label shortcuts under the header.
                // Every slot is an entry that already exists
                // somewhere on this page or one tap back (the
                // chat itself); nothing new is wired, nothing
                // is only here.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(profilePalette.windowBg)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (p.userId != 0L) {
                        ProfileAction(
                            icon = PlaneIcon,
                            label = "消息",
                            modifier = Modifier.weight(1f),
                            onClick = onBack
                        )
                    }
                    if (p.isBot && p.botHasMainWebApp &&
                        p.userId != 0L
                    ) {
                        // Main Web App (v0.9.37, D1): the
                        // entry exists only while the
                        // server's has_main_web_app flag
                        // holds; it opens through the same
                        // WebAppStore chain as button-launched
                        // apps.
                        ProfileAction(
                            icon = GlobeIcon,
                            label = "打开应用",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                WebAppStore.openMainWebApp(
                                    chatId = p.chatId,
                                    botUserId = p.userId,
                                    botName = p.title,
                                    palette = profilePalette,
                                    onError = { err ->
                                        android.widget.Toast.makeText(
                                            context, err,
                                            android.widget.Toast
                                                .LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                        )
                    }
                    if (liveItem != null) {
                        ProfileAction(
                            icon = if (liveItem.isMuted) {
                                MuteBellIcon
                            } else {
                                Icons.Filled.Notifications
                            },
                            label = if (liveItem.isMuted) {
                                "已静音"
                            } else {
                                "静音"
                            },
                            modifier = Modifier.weight(1f),
                            onClick = {
                                ChatStore.toggleMute(liveItem)
                            }
                        )
                    }
                    ProfileAction(
                        icon = ImageIcon,
                        label = "共享媒体",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenSharedMedia
                    )
                    if (p.canGetMembers) {
                        ProfileAction(
                            icon = PeopleIcon,
                            label = "成员",
                            modifier = Modifier.weight(1f),
                            onClick = onOpenMembers
                        )
                    }
                }
            }
            item(key = "div_actions") { ProfileDivider() }
            val hasInfoRows = p.phone.isNotEmpty() ||
                p.username.isNotEmpty() ||
                p.birthday.isNotEmpty() ||
                p.description.isNotEmpty() ||
                (p.userId != 0L && p.userId != me.id &&
                    (p.note.isNotEmpty() || p.isContact)) ||
                p.commonGroups > 0 ||
                p.memberCount > 0 ||
                p.adminCount > 0 ||
                (p.primaryInviteLink.isNotEmpty() && !caps.canInvite)
            if (hasInfoRows) {
                item(key = "info") {
                    // Official info rows (v0.9.20, AA2): an
                    // outline icon, the value, and its label
                    // in small secondary text underneath —
                    // phone / username / bio for users,
                    // counts / link / bio for groups and
                    // channels. The old free-standing 简介
                    // block is one of these rows now. The
                    // link row (shown when the invite section
                    // below is not) copies on tap.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(profilePalette.windowBg)
                            .padding(vertical = 6.dp)
                    ) {
                        if (p.userId != 0L && p.phone.isNotEmpty()) {
                            ProfileInfoRow(
                                icon = Icons.Filled.Phone,
                                label = "手机号",
                                value = if (p.phone.startsWith("+")) {
                                    p.phone
                                } else {
                                    "+${p.phone}"
                                }
                            )
                        }
                        if (p.userId != 0L && p.username.isNotEmpty()) {
                            ProfileInfoRow(
                                icon = AtIcon,
                                label = "用户名",
                                value = "@${p.username}",
                                valueAccent = true
                            )
                        }
                        if (p.userId != 0L && p.birthday.isNotEmpty()) {
                            // Birthday (v0.9.33, Z4): shown
                            // only when userFullInfo actually
                            // carries a birthdate.
                            ProfileInfoRow(
                                icon = Icons.Filled.DateRange,
                                label = "生日",
                                value = p.birthday
                            )
                        }
                        if (p.memberCount > 0) {
                            ProfileInfoRow(
                                icon = PeopleIcon,
                                label = if (p.kindLabel == "频道") {
                                    "订阅者"
                                } else {
                                    "成员"
                                },
                                value = "${formatCount(p.memberCount)} 人"
                            )
                        }
                        if (p.adminCount > 0) {
                            ProfileInfoRow(
                                icon = ShieldIcon,
                                label = "管理员",
                                value = "${p.adminCount} 人"
                            )
                        }
                        if (p.userId != 0L && p.commonGroups > 0) {
                            ProfileInfoRow(
                                icon = PeopleIcon,
                                label = "共同群组",
                                value = "${p.commonGroups} 个"
                            )
                        }
                        if (p.primaryInviteLink.isNotEmpty() &&
                            !caps.canInvite
                        ) {
                            ProfileInfoRow(
                                icon = LinkIcon,
                                label = "链接",
                                value = p.primaryInviteLink,
                                onClick = {
                                    clipboard.setText(
                                        AnnotatedString(
                                            p.primaryInviteLink
                                        )
                                    )
                                }
                            )
                        }
                        if (p.description.isNotEmpty()) {
                            ProfileInfoRow(
                                icon = Icons.Filled.Info,
                                label = "简介",
                                value = p.description
                            )
                        }
                        if (p.userId != 0L && p.userId != me.id &&
                            (p.note.isNotEmpty() || p.isContact)
                        ) {
                            // Note (v0.9.38, E2): my private
                            // note about this user — an
                            // annotation under its own label,
                            // never a replacement for the
                            // name. Tap edits it on the
                            // server (setUserNote).
                            ProfileInfoRow(
                                icon = Icons.Filled.Edit,
                                label = "备注（只有你能看到）",
                                value = p.note.ifEmpty { "添加备注" },
                                valueAccent = p.note.isEmpty(),
                                onClick = { noteDraft = p.note }
                            )
                        }
                        if (noteNotice != null) {
                            Text(
                                noteNotice!!,
                                color = Color.Gray, fontSize = 12.5.sp,
                                modifier = Modifier.padding(
                                    start = 56.dp, end = 16.dp,
                                    top = 2.dp, bottom = 6.dp
                                )
                            )
                        }
                    }
                }
                item(key = "div_info") { ProfileDivider() }
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
                            .fillMaxWidth()
                            .background(profilePalette.windowBg)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Icon(
                                ImageIcon, contentDescription = null,
                                tint = profilePalette.accentText,
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
                        .background(profilePalette.windowBg)
                        .clickable { onOpenSharedMedia() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        .background(profilePalette.windowBg)
                        .clickable { exportDialogOpen = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(profilePalette.windowBg)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { autoDeleteDialogOpen = true }
                                .padding(
                                    horizontal = 16.dp, vertical = 12.dp
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
                                    start = 16.dp, end = 16.dp,
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(profilePalette.windowBg)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { themeDialogOpen = true }
                                .padding(
                                    horizontal = 16.dp, vertical = 12.dp
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
                                    start = 16.dp, end = 16.dp,
                                    bottom = 8.dp
                                )
                            )
                        }
                    }
                }
            }
            item(key = "chat_wallpaper") {
                // Per-chat wallpaper (v0.9.16, W5): a local
                // override of the global wallpaper picture,
                // for this chat only; purely local, nothing
                // is sent to the server or the peer.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(profilePalette.windowBg)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { wallpaperDialogOpen = true }
                            .padding(
                                horizontal = 16.dp, vertical = 12.dp
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Icon(
                            ImageIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme
                                .primary,
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "专属壁纸",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (hasOwnWallpaper) "已设置" else "跟随全局",
                            color = Color.Gray, fontSize = 14.sp
                        )
                        Icon(
                            Icons.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    if (wallpaperNotice != null) {
                        Text(
                            wallpaperNotice!!,
                            color = Color.Gray, fontSize = 12.5.sp,
                            modifier = Modifier.padding(
                                start = 16.dp, end = 16.dp,
                                bottom = 8.dp
                            )
                        )
                    }
                }
            }
            if (p.userId != 0L && !p.isSecretChat) {
                item(key = "secret_start") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(profilePalette.windowBg)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(profilePalette.windowBg)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
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
            if (p.userId != 0L && p.userId != me.id && p.isContact) {
                // Delete contact (v0.9.33, Z4): removes the
                // address-book entry only — the section says
                // so in plain words before the confirm dialog
                // repeats it.
                item(key = "delete_contact") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(profilePalette.windowBg)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        TextButton(onClick = {
                            deleteContactConfirm = true
                        }) {
                            Text(
                                "删除联系人",
                                color = Color(0xFFC62828)
                            )
                        }
                        Text(
                            "删的是通讯录里的这条联系人记录，" +
                                "你们的聊天和聊天记录都还在。",
                            fontSize = 12.sp, color = Color.Gray
                        )
                        if (contactNotice != null) {
                            Text(
                                contactNotice!!,
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(profilePalette.windowBg)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "加密",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = profilePalette.accentText
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
                        if (info != null &&
                            info.keyIdenticon.size >= 144
                        ) {
                            // Key identicon (v0.9.32, Y5): the
                            // 12x12 picture td_api.tl prescribes
                            // for key_hash, drawn next to the
                            // hex — two sides compare grids
                            // cell by cell, which is harder to
                            // misread than 64 hex digits.
                            Text(
                                "密钥图案（和对方 App 里的图案格对一下，144 个格子颜色全一致才对）：",
                                fontSize = 12.sp, color = Color.Gray,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            SecretKeyIdenticon(
                                indices = info.keyIdenticon,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(120.dp)
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(profilePalette.windowBg)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
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
                                color = profilePalette.accentText
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(profilePalette.windowBg)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
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
            if (p.isSupergroup) {
                // Boost status (v0.9.33, Z2): level, count
                // and the distance to the next level come
                // from getChatBoostStatus; my own slots from
                // getAvailableChatBoostSlots. Free accounts
                // simply have no slots — the section says so
                // and shows no dead button. Boosting spends
                // MY Premium slots via boostChat and stays
                // server-gated end to end.
                item(key = "boost") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(profilePalette.windowBg)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        TextButton(onClick = {
                            boostExpanded = !boostExpanded
                            if (boostExpanded) {
                                com.tuxungram.android.td.BoostStore
                                    .load(chatId)
                            }
                        }) {
                            Icon(
                                BoltIcon, contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("助力状态")
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if (boostExpanded) "收起" else "展开",
                                fontSize = 12.sp, color = Color.Gray
                            )
                        }
                        if (boostExpanded) {
                            val st = boostStatus
                            when {
                                boostError != null -> Text(
                                    boostError!!,
                                    fontSize = 13.sp, color = Color.Gray
                                )
                                !boostLoaded || st == null -> Text(
                                    "正在加载…",
                                    fontSize = 13.sp, color = Color.Gray
                                )
                                else -> {
                                    // Level ring (v0.9.46):
                                    // the level as a ring —
                                    // its arc is the way
                                    // through the current
                                    // level toward the next,
                                    // the level number sits
                                    // in the middle, and the
                                    // counts ride alongside
                                    // as captions. Replaces
                                    // the single thin bar
                                    // this section used to
                                    // show under the counts.
                                    val frac =
                                        if (st.nextLevelBoostCount > 0) {
                                            val span =
                                                st.nextLevelBoostCount -
                                                    st.currentLevelBoostCount
                                            val done =
                                                st.boostCount -
                                                    st.currentLevelBoostCount
                                            if (span > 0) {
                                                (done.toFloat() /
                                                    span)
                                                    .coerceIn(0f, 1f)
                                            } else {
                                                0f
                                            }
                                        } else {
                                            1f
                                        }
                                    Row(
                                        verticalAlignment =
                                            Alignment.CenterVertically
                                    ) {
                                        ProgressRing(
                                            fraction = frac,
                                            modifier = Modifier
                                                .size(76.dp),
                                            strokeWidthDp = 8f,
                                            arcColor =
                                                profilePalette.accent,
                                            description = "助力等级 " +
                                                "${st.level} 级，本级进度 " +
                                                "${(frac * 100).toInt()}%"
                                        ) {
                                            Column(
                                                horizontalAlignment =
                                                    Alignment
                                                        .CenterHorizontally
                                            ) {
                                                Text(
                                                    "${st.level}",
                                                    fontSize = 20.sp,
                                                    fontWeight =
                                                        FontWeight
                                                            .Bold
                                                )
                                                Text(
                                                    "级",
                                                    fontSize = 10.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(14.dp))
                                        Column {
                                            Text(
                                                "已有 ${st.boostCount} 次助力",
                                                fontSize = 14.5.sp,
                                                fontWeight =
                                                    FontWeight
                                                        .SemiBold
                                            )
                                            if (st
                                                    .nextLevelBoostCount > 0
                                            ) {
                                                Text(
                                                    "距离 " +
                                                        "${st.level + 1} 级还差 " +
                                                        "${st.nextLevelBoostCount - st.boostCount} 次助力。",
                                                    fontSize = 12.5.sp,
                                                    color = Color.Gray,
                                                    modifier =
                                                        Modifier
                                                            .padding(
                                                                top = 3.dp
                                                            )
                                                )
                                            } else {
                                                Text(
                                                    "已经是最高等级了。",
                                                    fontSize = 12.5.sp,
                                                    color = Color.Gray,
                                                    modifier =
                                                        Modifier
                                                            .padding(
                                                                top = 3.dp
                                                            )
                                                )
                                            }
                                            if (st
                                                    .giftCodeBoostCount > 0
                                            ) {
                                                Text(
                                                    "其中 " +
                                                        "${st.giftCodeBoostCount} 次" +
                                                        "来自礼物码和赠送活动。",
                                                    fontSize = 12.sp,
                                                    color = Color.Gray,
                                                    modifier =
                                                        Modifier
                                                            .padding(
                                                                top = 2.dp
                                                            )
                                                )
                                            }
                                        }
                                    }
                                    if (st.premiumMemberCount > 0) {
                                        Text(
                                            "群里约 ${st.premiumMemberCount} 位 " +
                                                "Premium 成员" +
                                                "（占 ${st.premiumMemberPercentage.toInt()}%）。",
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                        )
                                    }
                                    if (boostListVisible) {
                                        // Who boosted (v0.9.34,
                                        // A3): getChatBoosts rows.
                                        // The whole block exists
                                        // only when the server
                                        // answered the list —
                                        // administrators in
                                        // practice; a refusal
                                        // hides it (see
                                        // BoostStore).
                                        Text(
                                            "助力名单" +
                                                "（共 ${boostListTotal} 次）",
                                            fontSize = 13.sp,
                                            fontWeight =
                                                FontWeight.SemiBold,
                                            color =
                                                profilePalette.accentText,
                                            modifier = Modifier
                                                .padding(top = 10.dp)
                                        )
                                        if (boostList.isEmpty()) {
                                            Text(
                                                "还没有人助力过这个对话。",
                                                fontSize = 12.sp,
                                                color = Color.Gray,
                                                modifier = Modifier
                                                    .padding(top = 4.dp)
                                            )
                                        }
                                        val boostDateFmt =
                                            java.text.SimpleDateFormat(
                                                "yyyy-MM-dd",
                                                java.util.Locale
                                                    .getDefault()
                                            )
                                        boostList.forEach { entry ->
                                            val sourceLabel =
                                                when (entry.sourceKind) {
                                                    "premium" ->
                                                        "Premium 助力"
                                                    "gift_code" ->
                                                        "礼物码助力"
                                                    "giveaway" ->
                                                        "赠送活动助力"
                                                    else -> "助力"
                                                }
                                            Row(
                                                verticalAlignment =
                                                    Alignment
                                                        .CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 3.dp)
                                            ) {
                                                val entryPhoto =
                                                    entry.photoPath
                                                if (entryPhoto != null) {
                                                    AvatarImage(
                                                        path = entryPhoto,
                                                        sizeDp = 36
                                                    )
                                                } else {
                                                    val pair =
                                                        OfficialTheme
                                                            .avatarColors(
                                                                entry.userId,
                                                                OfficialTheme
                                                                    .dark
                                                            )
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .background(
                                                                Brush
                                                                    .verticalGradient(
                                                                        listOf(
                                                                            pair.first,
                                                                            pair.second
                                                                        )
                                                                    ),
                                                                CircleShape
                                                            ),
                                                        contentAlignment =
                                                            Alignment.Center
                                                    ) {
                                                        Text(
                                                            entry.name
                                                                .trim()
                                                                .firstOrNull()
                                                                ?.toString()
                                                                ?: "?",
                                                            color =
                                                                Color.White,
                                                            fontWeight =
                                                                FontWeight
                                                                    .Bold,
                                                            fontSize = 15.sp
                                                        )
                                                    }
                                                }
                                                Spacer(
                                                    Modifier.width(10.dp)
                                                )
                                                Column {
                                                    Text(
                                                        entry.name
                                                            .ifEmpty {
                                                                sourceLabel
                                                            },
                                                        fontSize = 14.sp
                                                    )
                                                    Text(
                                                        buildString {
                                                            if (entry.name
                                                                    .isNotEmpty()
                                                            ) {
                                                                append(
                                                                    sourceLabel
                                                                )
                                                                append(" · ")
                                                            }
                                                            if (entry.count > 1) {
                                                                append(
                                                                    "共 " +
                                                                        "${entry.count} 次 · "
                                                                )
                                                            }
                                                            append(
                                                                "到期 "
                                                            )
                                                            append(
                                                                boostDateFmt
                                                                    .format(
                                                                        java.util
                                                                            .Date(
                                                                                entry.expirationDate * 1000L
                                                                            )
                                                                    )
                                                            )
                                                        },
                                                        fontSize = 12.sp,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }
                                        }
                                        if (boostListHasMore) {
                                            TextButton(onClick = {
                                                com.tuxungram.android.td
                                                    .BoostStore
                                                    .loadMoreBoosts()
                                            }) {
                                                Text("加载更多助力")
                                            }
                                        }
                                    }
                                    if (st.boostUrl.isNotEmpty()) {
                                        Row(
                                            verticalAlignment =
                                                Alignment.CenterVertically,
                                            modifier = Modifier
                                                .padding(top = 6.dp)
                                        ) {
                                            Text(
                                                st.boostUrl,
                                                fontSize = 12.sp,
                                                color = Color.Gray,
                                                modifier = Modifier
                                                    .weight(1f)
                                            )
                                            TextButton(onClick = {
                                                clipboard.setText(
                                                    AnnotatedString(
                                                        st.boostUrl
                                                    )
                                                )
                                                boostNotice = "助力链接已复制。"
                                            }) {
                                                Text("复制链接")
                                            }
                                        }
                                    }
                                    Text(
                                        "我的助力",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = profilePalette.accentText,
                                        modifier = Modifier
                                            .padding(top = 10.dp)
                                    )
                                    if (boostSlots.isEmpty()) {
                                        Text(
                                            "你还没有可用的助力。助力是 " +
                                                "Telegram Premium 的权益：" +
                                                "订阅后会得到助力次数，" +
                                                "可以来这里用在" +
                                                "这个对话上。",
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                        )
                                    } else {
                                        val nowSec =
                                            System.currentTimeMillis() / 1000
                                        val dateFmt =
                                            java.text.SimpleDateFormat(
                                                "yyyy-MM-dd",
                                                java.util.Locale.getDefault()
                                            )
                                        boostSlots.forEach { slot ->
                                            val appliedHere =
                                                slot.slotId in
                                                    st.appliedSlotIds &&
                                                    slot.boostedChatId == chatId
                                            val usable = !appliedHere &&
                                                slot.boostedChatId == 0L &&
                                                slot.cooldownUntilDate <= nowSec
                                            Row(
                                                verticalAlignment =
                                                    Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp)
                                            ) {
                                                if (usable) {
                                                    Checkbox(
                                                        checked =
                                                            slot.slotId in
                                                                boostSelection,
                                                        onCheckedChange = {
                                                            checked ->
                                                            boostSelection =
                                                                if (checked) {
                                                                    boostSelection +
                                                                        slot.slotId
                                                                } else {
                                                                    boostSelection -
                                                                        slot.slotId
                                                                }
                                                        }
                                                    )
                                                }
                                                Text(
                                                    when {
                                                        appliedHere ->
                                                            "已助力这个对话" +
                                                                "（到期 " +
                                                                dateFmt.format(
                                                                    java.util.Date(
                                                                        slot.expirationDate * 1000L
                                                                    )
                                                                ) + "）"
                                                        slot.boostedChatId != 0L ->
                                                            "正用在其他对话上" +
                                                                "（到期 " +
                                                                dateFmt.format(
                                                                    java.util.Date(
                                                                        slot.expirationDate * 1000L
                                                                    )
                                                                ) + "）"
                                                        slot.cooldownUntilDate > nowSec ->
                                                            "冷却中（" +
                                                                dateFmt.format(
                                                                    java.util.Date(
                                                                        slot.cooldownUntilDate * 1000L
                                                                    )
                                                                ) + " 后可用）"
                                                        else -> "可用"
                                                    },
                                                    fontSize = 13.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                        if (boostSelection.isNotEmpty()) {
                                            Button(
                                                onClick = {
                                                    val ids =
                                                        boostSelection.toList()
                                                    com.tuxungram.android.td
                                                        .BoostStore.boost(
                                                            chatId, ids
                                                        ) { error ->
                                                            boostNotice = error
                                                                ?: "助力成功。"
                                                            if (error == null) {
                                                                boostSelection =
                                                                    emptySet()
                                                            }
                                                        }
                                                },
                                                modifier = Modifier
                                                    .padding(top = 6.dp)
                                            ) {
                                                Text(
                                                    "用选中的 ${boostSelection.size} 个助力"
                                                )
                                            }
                                        }
                                    }
                                    if (boostNotice != null) {
                                        Text(
                                            boostNotice!!,
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                        )
                                    }
                                }
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(profilePalette.windowBg)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
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
                                color = profilePalette.accentText
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
                                        append(" · 已有 ${formatCount(link.memberCount)} 人通过它加入")
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
                                    overflow = TextOverflow.Ellipsis,
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
                            .background(profilePalette.windowBg)
                            .clickable { onOpenMembers() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = profilePalette.accentText,
                        modifier = Modifier.padding(
                            start = 16.dp, end = 16.dp,
                            top = 14.dp, bottom = 6.dp
                        )
                    )
                }
                items(items = p.members, key = { it.userId }) { member ->
                    val actionable = caps.canRestrict &&
                        member.statusKind in
                        listOf("member", "restricted", "banned")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(profilePalette.windowBg)
                            .then(
                                if (actionable) {
                                    Modifier.clickable { memberAction = member }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val memberPair = OfficialTheme.avatarColors(
                            member.userId, OfficialTheme.dark
                        )
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            memberPair.first,
                                            memberPair.second
                                        )
                                    ),
                                    CircleShape
                                ),
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(profilePalette.windowBg)
                                .padding(horizontal = 12.dp)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(profilePalette.windowBg)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
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
                                memberRestrict = target
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
    // Restrict-duration picker (v0.9.53, X4).
    val mutingMember = memberRestrict
    if (mutingMember != null) {
        AlertDialog(
            onDismissRequest = { memberRestrict = null },
            title = { Text("禁言多久") },
            text = {
                Column {
                    Text(
                        mutingMember.name
                            .ifEmpty { "用户 ${mutingMember.userId}" },
                        color = Color.Gray, fontSize = 13.sp
                    )
                    for ((label, seconds) in
                        AdminStore.RESTRICT_DURATIONS) {
                        TextButton(onClick = {
                            AdminStore.restrictMember(
                                chatId, mutingMember.userId, seconds
                            )
                            memberRestrict = null
                        }) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { memberRestrict = null }) {
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
    // Per-chat wallpaper dialog (v0.9.16, W5): pick a
    // picture, or clear the override and fall back to the
    // global wallpaper.
    if (wallpaperDialogOpen) {
        AlertDialog(
            onDismissRequest = { wallpaperDialogOpen = false },
            title = { Text("专属壁纸") },
            text = {
                Text(
                    if (hasOwnWallpaper) {
                        "这个对话正在用自己的壁纸，不跟随全局设置。"
                    } else {
                        "给这个对话单独设一张壁纸，只在这个对话里生效；" +
                            "不设就跟随全局壁纸。"
                    },
                    color = Color.Gray, fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    wallpaperDialogOpen = false
                    AppLock.noteExternalLaunch()
                    chatWallpaperPicker.launch("image/*")
                }) {
                    Text(if (hasOwnWallpaper) "换一张" else "选图片")
                }
            },
            dismissButton = {
                if (hasOwnWallpaper) {
                    TextButton(onClick = {
                        wallpaperDialogOpen = false
                        UiSettings.clearChatWallpaper(chatId)
                        wallpaperNotice = "已清除专属壁纸，恢复跟随全局。"
                    }) { Text("清除专属壁纸") }
                } else {
                    TextButton(onClick = {
                        wallpaperDialogOpen = false
                    }) { Text("取消") }
                }
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
    if (deleteContactConfirm && current != null && current.userId != 0L) {
        AlertDialog(
            onDismissRequest = { deleteContactConfirm = false },
            title = { Text("删除联系人 ${current.title}？") },
            text = {
                Text("只删通讯录里的这条联系人记录。你们的聊天、聊天记录都不会删，对方也不会收到通知。")
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteContactConfirm = false
                    ProfileStore.removeContact(current.userId) { error ->
                        contactNotice = error ?: "已删除联系人。"
                        if (error == null) ProfileStore.load(chatId)
                    }
                }) {
                    Text("删除", color = Color(0xFFC62828))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteContactConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
    val noteDraftState = noteDraft
    if (noteDraftState != null && current != null &&
        current.userId != 0L
    ) {
        var text by remember(current.userId) {
            mutableStateOf(noteDraftState)
        }
        AlertDialog(
            onDismissRequest = { noteDraft = null },
            title = { Text("备注") },
            text = {
                Column {
                    Text(
                        "写给自己看的备注，存在服务器上、只有你" +
                            "能看到；它只是附注，不会改对方的名字。",
                        color = Color.Gray, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("比如：客户、同学…") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = current.userId
                    noteDraft = null
                    ProfileStore.setUserNote(target, text) { error ->
                        noteNotice = error
                            ?: if (text.isBlank()) {
                                "备注已清除。"
                            } else {
                                "备注已保存。"
                            }
                    }
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { noteDraft = null }) {
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

/** One slot of the profile action row (v0.9.20, AA2):
 *  an outline icon over a small label, the official
 *  profile page's shortcut shape. */
@Composable
private fun ProfileAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = OfficialTheme.current()
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon, contentDescription = null,
            tint = palette.accentText,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = palette.textSecondary)
    }
}

/** Hairline on the profile page's white blocks
 *  (v0.9.20, AA2): full-width white strip carrying a
 *  divider inset by the content margin. */
@Composable
private fun ProfileDivider() {
    val palette = OfficialTheme.current()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.windowBg)
            .padding(start = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(palette.divider)
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

/** The four colours td_api.tl names for the secret-chat
 *  key identicon, in 2-bit-group order: FFFFFF, D5E6F3,
 *  2D5775, 2F99C9. */
private val identiconColors = listOf(
    Color(0xFFFFFFFF), Color(0xFFD5E6F3),
    Color(0xFF2D5775), Color(0xFF2F99C9)
)

/** The secret-chat key picture (v0.9.32, Y5): the 12x12
 *  grid of [indices] (row-major, from
 *  SecretChatStore.keyIdenticon), drawn on a hairline
 *  frame so the white cells stay visible on the white
 *  page. Pure Canvas — no image assets. */
@Composable
private fun SecretKeyIdenticon(
    indices: List<Int>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cell = size.width / 12f
        for (i in 0 until 144) {
            val col = identiconColors[
                indices.getOrElse(i) { 0 }.coerceIn(0, 3)
            ]
            drawRect(
                color = col,
                topLeft = androidx.compose.ui.geometry.Offset(
                    (i % 12) * cell, (i / 12) * cell
                ),
                size = androidx.compose.ui.geometry.Size(
                    cell + 0.5f, cell + 0.5f
                )
            )
        }
        drawRect(
            color = Color(0xFFB0BEC5),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1.dp.toPx()
            )
        )
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
