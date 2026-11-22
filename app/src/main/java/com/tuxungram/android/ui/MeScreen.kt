package com.tuxungram.android.ui

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.BuildConfig
import com.tuxungram.android.td.ContactsStore
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
    // Set only when the bottom navigation bar is off
    // (v0.9.19, Z5): the page is then reached from the
    // drawer and shows its own back row; as a bottom-bar
    // tab it stays null and nothing changes.
    onBack: (() -> Unit)? = null
) {
    val me by MeStore.me.collectAsState()
    val palette = OfficialTheme.current()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.graySection)
            .verticalScroll(rememberScrollState())
    ) {
        if (onBack != null) {
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
        // plain bar background, no gradient banner.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.actionBarBg)
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.clickable { onEditProfile() }) {
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
            Spacer(Modifier.height(12.dp))
            Text(
                me.displayName.ifEmpty { "我" },
                color = palette.textPrimary,
                fontSize = 22.sp, fontWeight = FontWeight.SemiBold
            )
            Text(
                "在线",
                color = palette.accentText,
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
                onClick = onEditProfile
            )
            SettingsDivider()
            IconRow(
                Icons.Filled.Star, "已保存消息",
                "发给自己的收藏夹",
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
                onClick = onOpenSettingsTab
            )
            SettingsDivider()
            IconRow(
                Icons.Filled.List, "更新日志",
                "每个版本都改了什么",
                onClick = onOpenChangelog
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
