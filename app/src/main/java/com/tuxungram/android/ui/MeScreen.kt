package com.tuxungram.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.BuildConfig
import com.tuxungram.android.td.ContactsStore
import com.tuxungram.android.td.CrashLog
import com.tuxungram.android.td.LocalPremium
import com.tuxungram.android.td.MeStore

/**
 * "My profile" tab (v0.9.9, W4): the bottom navigation's
 * fourth tab. Since v0.9.20 (AA2) it shares the official
 * profile structure with the profile page: a plain header
 * (big avatar, name, status), label-above-value info rows
 * (phone / username / bio from MeStore) and one white
 * action group — edit profile, Saved Messages, settings,
 * changelog. Tapping the avatar opens the edit page (that
 * is where the photo is managed).
 */
@Composable
fun MeScreen(
    onEditProfile: () -> Unit,
    onOpenSavedChat: (Long) -> Unit,
    onOpenSettingsTab: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenAbout: () -> Unit,
    // My QR code page (v0.9.24, Q2): the code encodes the
    // account's own t.me link, generated on-device.
    onOpenMyQr: () -> Unit,
    // My stories page (v0.9.32, Y2): my profile-page
    // stories, their viewers and deletion.
    onOpenMyStories: () -> Unit,
    // Back target (v0.9.19, Z5): the host always passes
    // the chats tab, so the system Back key behaves here
    // exactly like on the contacts and settings tabs
    // (v0.9.22, AC1: this page used to install no
    // BackHandler at all, so with the bottom bar off —
    // where the page is reached from the drawer — the
    // system Back key left the app instead of returning
    // to the chat list). The visible back row above the
    // header is separate: it only shows when the bottom
    // bar is off (showBackRow), the way Z5 designed it.
    onBack: (() -> Unit)? = null,
    showBackRow: Boolean = false
) {
    BackHandler(enabled = onBack != null) { onBack?.invoke() }
    val me by MeStore.me.collectAsState()
    // Local premium (v0.9.63, H1): this page is the user's
    // own identity card, so it is the banner's home and one
    // of the decoration surfaces. Everything below renders
    // exactly as before while the master switch is off.
    val premiumOn by LocalPremium.enabled.collectAsState()
    val premiumBanner by LocalPremium.banner.collectAsState()
    val premiumIcons by LocalPremium.iconStyle.collectAsState()
    val bannerPair = LocalPremium.BANNER_COLORS
        .getOrNull(premiumBanner)
    val bannerActive = premiumOn && premiumBanner != 0 &&
        bannerPair != null && bannerPair.first != 0L
    val actionIconTint = if (premiumOn && premiumIcons) {
        LocalPremiumGold
    } else {
        null
    }
    // Unseen crash-record dot (v0.9.42, K4): the About
    // entry below carries a small red dot while a crash
    // recorded on this phone has not been looked at yet.
    // The records themselves live in Settings (the crash
    // row there carries the same dot); opening them there
    // is what clears it. Nothing pops up and nothing is
    // uploaded — this is only a marker.
    val meContext = LocalContext.current
    var crashUnseen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        crashUnseen = CrashLog.hasUnseen(meContext)
    }
    val palette = OfficialTheme.current()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.graySection)
            .verticalScroll(rememberScrollState())
    ) {
        if (showBackRow && onBack != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.actionBarBg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = palette.actionBarIcon
                    )
                }
                Text(
                    "我的",
                    fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    color = palette.actionBarTitle
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(palette.divider)
            )
        }
        // Official self-profile header: identity on the
        // plain bar background, no gradient banner. The one
        // exception is the user's own local-premium banner
        // (v0.9.63, H1): a gradient they picked, on this
        // page only, with white header text over it.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (bannerActive) {
                        Modifier.background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(bannerPair!!.first),
                                    Color(bannerPair.second)
                                )
                            )
                        )
                    } else {
                        Modifier.background(palette.actionBarBg)
                    }
                )
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.clickable { onEditProfile() }) {
            OwnAvatarBadge(sizeDp = 100) {
                val path = me.photoPath
                if (path != null) {
                    AvatarImage(path, 100)
                } else {
                    val pair = OfficialTheme.avatarColors(
                        me.id, OfficialTheme.dark
                    )
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
                            me.displayName.take(1).ifEmpty { "?" },
                            color = Color.White, fontSize = 36.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            }
            Spacer(Modifier.height(12.dp))
            DecoratedOwnName(
                name = me.displayName.ifEmpty { "我" },
                maxLines = 2,
                // White over a local-premium banner, the
                // plain primary colour otherwise.
                color = if (bannerActive) {
                    Color.White
                } else {
                    palette.textPrimary
                },
                // Same header family as the profile page:
                // 20 sp (v0.9.49, T1 — was 22).
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold
            )
            Text(
                "在线",
                color = if (bannerActive) {
                    Color.White.copy(alpha = 0.85f)
                } else {
                    palette.accentText
                },
                fontSize = 14.sp
            )
        }
        if (me.phone.isNotEmpty() || me.username.isNotEmpty() ||
            me.bio.isNotEmpty()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.windowBg)
                    .padding(vertical = 6.dp)
            ) {
                if (me.phone.isNotEmpty()) {
                    ProfileInfoRow(
                        icon = Icons.Filled.Phone,
                        label = "手机号",
                        value = if (me.phone.startsWith("+")) {
                            me.phone
                        } else {
                            "+${me.phone}"
                        }
                    )
                }
                if (me.username.isNotEmpty()) {
                    ProfileInfoRow(
                        icon = AtIcon,
                        label = "用户名",
                        value = "@${me.username}",
                        valueAccent = true
                    )
                }
                if (me.bio.isNotEmpty()) {
                    ProfileInfoRow(
                        icon = Icons.Filled.Info,
                        label = "简介",
                        value = me.bio
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.windowBg)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            IconRow(
                Icons.Filled.Edit, "编辑资料",
                "名字、简介、头像、用户名",
                iconTint = actionIconTint,
                onClick = onEditProfile
            )
            SettingsDivider()
            IconRow(
                QrIcon, "我的二维码",
                "扫码打开我的主页",
                iconTint = actionIconTint,
                onClick = onOpenMyQr
            )
            SettingsDivider()
            IconRow(
                ImageIcon, "我的动态",
                "发到资料页的动态、谁看过、删除",
                iconTint = actionIconTint,
                onClick = onOpenMyStories
            )
            SettingsDivider()
            IconRow(
                Icons.Filled.Star, "已保存消息",
                "发给自己的收藏夹",
                iconTint = actionIconTint,
                onClick = {
                    ContactsStore.openSavedMessages { id ->
                        if (id != null) onOpenSavedChat(id)
                    }
                }
            )
            SettingsDivider()
            IconRow(
                Icons.Filled.Settings, "设置",
                "代理、外观、隐私、存储都在这里",
                iconTint = actionIconTint,
                onClick = onOpenSettingsTab
            )
            SettingsDivider()
            IconRow(
                Icons.Filled.List, "更新日志",
                "每个版本都改了什么",
                iconTint = actionIconTint,
                onClick = onOpenChangelog
            )
            SettingsDivider()
            IconRow(
                Icons.Filled.Info, "关于",
                "关于我们这个软件",
                iconTint = actionIconTint,
                onClick = onOpenAbout,
                trailing = if (crashUnseen) {
                    {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    androidx.compose.material3
                                        .MaterialTheme.colorScheme.error,
                                    CircleShape
                                )
                        )
                    }
                } else {
                    null
                }
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "TuxunGram ${BuildConfig.VERSION_NAME}",
            color = palette.textSecondary, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(20.dp))
    }
}
