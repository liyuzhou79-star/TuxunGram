package com.tuxungram.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.BuildConfig
import com.tuxungram.android.td.AccountStore
import com.tuxungram.android.td.BackgroundAccounts
import com.tuxungram.android.td.ChatStore
import com.tuxungram.android.td.ContactsStore
import com.tuxungram.android.td.MeStore
import com.tuxungram.android.td.TdRepository
import com.tuxungram.android.td.UiSettings
import java.io.File

/**
 * The official-style navigation drawer (v0.9.19, Z2): an
 * account header (big avatar, name, phone — tapping it
 * opens the 我的 tab), an expandable account list reusing
 * the multi-account switcher as-is (per-account unread
 * counts included), the official menu order for the
 * entries this app really has, and a night-mode switch
 * wired to the existing theme mode. Entries with no real
 * feature behind them are not listed at all — no calls row
 * (calls are not built), no placeholders (recorded in the
 * checkpoint).
 */
@Composable
fun HomeDrawerContent(
    onClose: () -> Unit,
    onOpenMe: () -> Unit,
    onOpenContactsTab: () -> Unit,
    onOpenSettingsTab: () -> Unit,
    onOpenCreate: (String) -> Unit,
    onOpenSavedChat: (Long) -> Unit
) {
    val palette = OfficialTheme.current()
    val me by MeStore.me.collectAsState()
    var accountsExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp)
    ) {
        // ---- Account header ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onClose()
                    onOpenMe()
                }
                .padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 12.dp)
        ) {
            val path = me.photoPath
            if (path != null) {
                AvatarImage(path, 64)
            } else {
                val pair = OfficialTheme.avatarColors(
                    me.id, OfficialTheme.dark
                )
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(pair.first, pair.second)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        me.displayName.trim().firstOrNull()?.toString()
                            ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        me.displayName.ifEmpty { "我" },
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1
                    )
                    if (me.phone.isNotEmpty()) {
                        Text(
                            "+${me.phone}",
                            color = palette.textSecondary,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                    if (me.username.isNotEmpty()) {
                        Text(
                            "@${me.username}",
                            color = palette.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                }
                IconButton(onClick = { accountsExpanded = !accountsExpanded }) {
                    Icon(
                        if (accountsExpanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                        contentDescription = if (accountsExpanded) {
                            "收起账号列表"
                        } else {
                            "展开账号列表"
                        },
                        tint = palette.textSecondary
                    )
                }
            }
        }
        // ---- Account list (official: expands inside the
        // drawer; same data and unread rules as the settings
        // account rows) ----
        AnimatedVisibility(
            visible = accountsExpanded,
            enter = expandVertically(Motion.tween(220)) +
                fadeIn(Motion.tween(220)),
            exit = shrinkVertically(Motion.tween(180)) +
                fadeOut(Motion.tween(180))
        ) {
            Column {
                val slots by AccountStore.slots.collectAsState()
                val currentSlot by AccountStore.current.collectAsState()
                val labels by AccountStore.labels.collectAsState()
                val avatars by AccountStore.avatars.collectAsState()
                val bgUnread by BackgroundAccounts.unreadBySlot
                    .collectAsState()
                val fgChats by ChatStore.chats.collectAsState()
                for (slot in slots) {
                    val label = labels[slot] ?: "账号 ${slot + 1}"
                    val slotPath = (
                        if (slot == currentSlot) {
                            me.photoPath ?: avatars[slot]
                        } else {
                            avatars[slot]
                        }
                        )?.takeIf { File(it).exists() }
                    val unread = if (slot == currentSlot) {
                        fgChats.sumOf { it.unreadCount }
                    } else {
                        bgUnread[slot] ?: 0
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (slot != currentSlot) {
                                    TdRepository.switchAccount(slot)
                                }
                                onClose()
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (slotPath != null) {
                            AvatarImage(path = slotPath, sizeDp = 40)
                        } else {
                            val pair = OfficialTheme.avatarColors(
                                slot.toLong() + 1, OfficialTheme.dark
                            )
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(pair.first, pair.second)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label.trim().firstOrNull()?.toString()
                                        ?: "?",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            label,
                            color = palette.textPrimary,
                            fontSize = 15.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (unread > 0) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        palette.unreadBadge,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    UiSettings.badgeText(unread),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        if (slot == currentSlot) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "当前账号",
                                tint = palette.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                DrawerItem(Icons.Filled.Add, "添加账号") {
                    TdRepository.addAccount()
                }
            }
        }
        DrawerDivider()
        // ---- Menu, official order, real entries only ----
        DrawerItem(PeopleIcon, "新建群组") {
            onClose()
            onOpenCreate("group")
        }
        DrawerItem(MegaphoneIcon, "新建频道") {
            onClose()
            onOpenCreate("channel")
        }
        DrawerItem(Icons.Filled.Person, "联系人") {
            onClose()
            onOpenContactsTab()
        }
        DrawerItem(Icons.Filled.Star, "收藏") {
            onClose()
            ContactsStore.openSavedMessages { id ->
                if (id != null) onOpenSavedChat(id)
            }
        }
        DrawerItem(Icons.Filled.Settings, "设置") {
            onClose()
            onOpenSettingsTab()
        }
        DrawerDivider()
        // ---- Night mode (official drawer footer switch) ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                MoonIcon,
                contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(20.dp))
            Text(
                "夜间模式",
                color = palette.textPrimary,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = OfficialTheme.dark,
                onCheckedChange = { dark ->
                    UiSettings.setThemeMode(
                        if (dark) {
                            UiSettings.THEME_DARK
                        } else {
                            UiSettings.THEME_LIGHT
                        }
                    )
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "TuxunGram ${BuildConfig.VERSION_NAME}",
            color = palette.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val palette = OfficialTheme.current()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = palette.textSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(20.dp))
        Text(label, color = palette.textPrimary, fontSize = 15.sp)
    }
}

@Composable
private fun DrawerDivider() {
    val palette = OfficialTheme.current()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(0.5.dp)
            .background(palette.divider)
    )
}
