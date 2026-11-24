package com.tuxungram.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.tuxungram.android.td.AdminStore
import com.tuxungram.android.td.GroupPerms
import com.tuxungram.android.td.ProfileStore

/** Group settings page (v0.9.5, O2): default member
 *  permissions, slow mode and content protection for one
 *  group/channel. Every control submits the moment it
 *  changes; a server refusal rolls the control back to the
 *  value ProfileStore last read and says why in plain
 *  language. Sections render only when the viewer holds the
 *  right td_api.tl names for them — permissions and slow
 *  mode need can_restrict_members, content protection is
 *  owner-only. */
@Composable
fun GroupPermissionsScreen(chatId: Long, onBack: () -> Unit) {
    BackHandler { onBack() }
    val profile by ProfileStore.profile.collectAsState()
    val caps by AdminStore.caps.collectAsState()
    LaunchedEffect(chatId) {
        ProfileStore.load(chatId)
        AdminStore.loadCaps(chatId)
    }
    val p = profile?.takeIf { it.chatId == chatId }

    var perms by remember(chatId) { mutableStateOf<GroupPerms?>(null) }
    var slowMode by remember(chatId) { mutableStateOf<Int?>(null) }
    var protectedOn by remember(chatId) { mutableStateOf<Boolean?>(null) }
    var antiSpamOn by remember(chatId) { mutableStateOf<Boolean?>(null) }
    var membersHiddenOn by remember(chatId) { mutableStateOf<Boolean?>(null) }
    var busy by remember(chatId) { mutableStateOf(false) }
    var notice by remember(chatId) { mutableStateOf<String?>(null) }

    // Follow the server values whenever no change is in
    // flight; a failed change snaps back through the same
    // path once [busy] clears.
    LaunchedEffect(
        p?.groupPerms, p?.slowModeDelay, p?.hasProtectedContent,
        p?.antiSpamEnabled, p?.membersHidden, busy
    ) {
        if (!busy && p != null) {
            perms = p.groupPerms
            slowMode = p.slowModeDelay
            protectedOn = p.hasProtectedContent
            antiSpamOn = p.antiSpamEnabled
            membersHiddenOn = p.membersHidden
        }
    }

    fun applyPerms(next: GroupPerms) {
        perms = next
        busy = true
        AdminStore.setPermissions(chatId, next) { ok, text ->
            notice = text
            busy = false
            if (!ok && p != null) perms = p.groupPerms
        }
    }
    fun applySlowMode(next: Int) {
        slowMode = next
        busy = true
        AdminStore.setSlowMode(chatId, next) { ok, text ->
            notice = text
            busy = false
            if (!ok && p != null) slowMode = p.slowModeDelay
        }
    }
    fun applyProtected(next: Boolean) {
        protectedOn = next
        busy = true
        AdminStore.setProtectedContent(chatId, next) { ok, text ->
            notice = text
            busy = false
            if (!ok && p != null) protectedOn = p.hasProtectedContent
        }
    }
    fun applyAntiSpam(next: Boolean) {
        antiSpamOn = next
        busy = true
        AdminStore.setAntiSpam(
            p?.supergroupId ?: 0L, chatId, next
        ) { ok, text ->
            notice = text
            busy = false
            if (!ok && p != null) antiSpamOn = p.antiSpamEnabled
        }
    }
    fun applyHiddenMembers(next: Boolean) {
        membersHiddenOn = next
        busy = true
        AdminStore.setHiddenMembers(
            p?.supergroupId ?: 0L, chatId, next
        ) { ok, text ->
            notice = text
            busy = false
            if (!ok && p != null) membersHiddenOn = p.membersHidden
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "群权限与慢速模式",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (p == null) {
            CenterColumn {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在读取群设置…", color = Color.Gray)
            }
            return@Column
        }
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .columnScrollbar(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            if (notice != null) {
                Text(
                    notice!!,
                    fontSize = 13.sp, color = Color.Gray,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
            val current = perms
            if (caps.canRestrict && current != null && p.kindLabel != "频道") {
                Text(
                    "成员默认权限",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "关掉哪项，普通成员就不能发哪类内容；管理员不受影响。改一项立刻生效。",
                    fontSize = 12.sp, color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
                PermSwitch("发送消息", current.canSendBasic) {
                    applyPerms(current.copy(canSendBasic = it))
                }
                PermSwitch("发送照片", current.canSendPhotos) {
                    applyPerms(current.copy(canSendPhotos = it))
                }
                PermSwitch("发送视频", current.canSendVideos) {
                    applyPerms(current.copy(canSendVideos = it))
                }
                PermSwitch("发送文件", current.canSendDocuments) {
                    applyPerms(current.copy(canSendDocuments = it))
                }
                PermSwitch("发送音频", current.canSendAudios) {
                    applyPerms(current.copy(canSendAudios = it))
                }
                PermSwitch("发送语音消息", current.canSendVoiceNotes) {
                    applyPerms(current.copy(canSendVoiceNotes = it))
                }
                PermSwitch("发送视频消息", current.canSendVideoNotes) {
                    applyPerms(current.copy(canSendVideoNotes = it))
                }
                PermSwitch("发送投票", current.canSendPolls) {
                    applyPerms(current.copy(canSendPolls = it))
                }
                PermSwitch("发送贴纸与 GIF", current.canSendOther) {
                    applyPerms(current.copy(canSendOther = it))
                }
                PermSwitch("给链接加预览", current.canAddLinkPreviews) {
                    applyPerms(current.copy(canAddLinkPreviews = it))
                }
            }
            val slow = slowMode
            if (caps.canRestrict && p.isSupergroup && slow != null) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "慢速模式",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray
                )
                Text(
                    "普通成员发完一条，要等这么久才能发下一条；管理员不受影响。",
                    fontSize = 12.sp, color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
                // v0.9.53 (X4): a delay set by another
                // client that is not one of the offered
                // tiers (e.g. 5 seconds) is folded into
                // the list so the current value stays
                // visible and selected instead of leaving
                // every radio unselected.
                val slowOptions =
                    if (slow in AdminStore.SLOW_MODE_OPTIONS) {
                        AdminStore.SLOW_MODE_OPTIONS
                    } else {
                        (AdminStore.SLOW_MODE_OPTIONS + slow).sorted()
                    }
                for (option in slowOptions) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !busy) { applySlowMode(option) }
                            .padding(vertical = 9.dp)
                    ) {
                        RadioText(
                            slow == option,
                            AdminStore.slowModeLabel(option)
                        )
                    }
                }
            }
            val prot = protectedOn
            if (caps.isCreator && prot != null) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "内容保护",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray
                )
                PermSwitch("禁止保存、转发和复制", prot) {
                    applyProtected(it)
                }
                Text(
                    "开启后，成员不能把这里的消息转发出去、复制文本或把图片文件存到本地。只有群主能改这一项。",
                    fontSize = 12.sp, color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // Group hygiene (v0.9.13, T6): the two supergroup
            // toggles, each shown only when supergroupFullInfo
            // says this admin may flip it.
            val spam = antiSpamOn
            val hidden = membersHiddenOn
            if (p.isSupergroup &&
                (p.canToggleAntiSpam || p.canHideMembers) &&
                spam != null && hidden != null
            ) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "群管理",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray
                )
                if (p.canToggleAntiSpam) {
                    PermSwitch("反垃圾（自动删广告）", spam) {
                        applyAntiSpam(it)
                    }
                    Text(
                        "开启后，Telegram 服务器会自动删掉疑似广告和垃圾的消息。",
                        fontSize = 12.sp, color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (p.canHideMembers) {
                    PermSwitch("隐藏成员名单", hidden) {
                        applyHiddenMembers(it)
                    }
                    Text(
                        "开启后，普通成员看不到完整成员名单；管理员不受影响。",
                        fontSize = 12.sp, color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (!caps.canRestrict && !caps.isCreator &&
                !p.canToggleAntiSpam && !p.canHideMembers
            ) {
                Text(
                    "你在这个群里没有改这些设置的权限。",
                    color = Color.Gray, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermSwitch(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp)
    ) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
