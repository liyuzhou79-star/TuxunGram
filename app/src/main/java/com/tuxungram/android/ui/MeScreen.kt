package com.tuxungram.android.ui

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.BuildConfig
import com.tuxungram.android.td.ContactsStore
import com.tuxungram.android.td.MeStore

/**
 * "My profile" tab (v0.9.9, W4): the bottom navigation's
 * fourth tab. A big personal header (avatar, name, username,
 * phone, bio — all from MeStore) plus the shortcuts a user
 * expects on a Me page: edit profile, Saved Messages, jump
 * to the settings tab and the changelog. Layout follows the
 * mature third-party clients' Me pages (Nekogram / official
 * app): header card first, grouped action card below.
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (onBack != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
                Text(
                    "我的",
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
        // Graphical header banner (v0.9.11, A2): the Me tab
        // opens with a seed-colour gradient banner (deco
        // circles + plane watermark, all program-drawn)
        // instead of a plain card — avatar and identity sit
        // on the banner in white.
        HeaderBanner {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val path = me.photoPath
                if (path != null) {
                    AvatarImage(path, 84)
                } else {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            me.displayName.take(1).ifEmpty { "?" },
                            color = Color.White, fontSize = 34.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    me.displayName.ifEmpty { "我" },
                    color = Color.White,
                    fontSize = 20.sp, fontWeight = FontWeight.SemiBold
                )
                if (me.username.isNotEmpty()) {
                    Text(
                        "@${me.username}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp
                    )
                }
                if (me.phone.isNotEmpty()) {
                    Text(
                        "+${me.phone}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp
                    )
                }
                if (me.bio.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        me.bio,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GroupCard {
            IconRow(
                Icons.Filled.Edit, "编辑资料",
                "名字、简介、头像、用户名",
                onClick = onEditProfile
            )
            IconRow(
                Icons.Filled.Star, "已保存消息",
                "发给自己的收藏夹",
                onClick = {
                    ContactsStore.openSavedMessages { id ->
                        if (id != null) onOpenSavedChat(id)
                    }
                }
            )
            IconRow(
                Icons.Filled.Settings, "设置",
                "代理、外观、隐私、存储都在这里",
                onClick = onOpenSettingsTab
            )
            IconRow(
                Icons.Filled.List, "更新日志",
                "每个版本都改了什么",
                onClick = onOpenChangelog
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "TuxunGram ${BuildConfig.VERSION_NAME}",
            color = Color.Gray, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(20.dp))
    }
}
