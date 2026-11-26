package com.tuxungram.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.LocalPremium
import com.tuxungram.android.td.MeStore
import kotlin.math.cos
import kotlin.math.sin

/** The gold of the local-premium decorations (v0.9.63,
 *  H1): one colour family for the badge, the premium-style
 *  icons and the selected states, so the set reads as one
 *  design. Our own choice — no official asset or colour
 *  file is copied; gold-for-premium is a generic idea. */
val LocalPremiumGold = Color(0xFFE8A33D)

/** The self-drawn local-premium badge: a gold diamond with
 *  a white star, drawn on a Canvas from plain geometry —
 *  no image asset, no official artwork. Shown next to the
 *  user's own name and on the corner of the user's own
 *  avatar on the 我的 / own-profile headers when the badge
 *  switch is on. */
@Composable
fun LocalPremiumBadge(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val diamond = Path().apply {
            moveTo(w / 2f, 0f)
            lineTo(w, h / 2f)
            lineTo(w / 2f, h)
            lineTo(0f, h / 2f)
            close()
        }
        drawPath(
            diamond,
            brush = Brush.linearGradient(
                listOf(Color(0xFFF6C453), Color(0xFFE8890C)),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )
        drawPath(
            starPath(w / 2f, h / 2f, w * 0.21f, w * 0.088f),
            color = Color.White
        )
    }
}

/** A five-point star polygon around ([cx], [cy]). */
private fun starPath(
    cx: Float,
    cy: Float,
    outerR: Float,
    innerR: Float
): Path {
    val path = Path()
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) outerR else innerR
        val angle = Math.toRadians(-90.0 + i * 36.0)
        val x = cx + (radius * cos(angle)).toFloat()
        val y = cy + (radius * sin(angle)).toFloat()
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
    return path
}

/**
 * The signed-in user's own name with the local-premium
 * decorations (v0.9.63, H1): the chosen sticker character
 * and / or the self-drawn badge right after the name text.
 * This composable is the ONLY way decorations reach a
 * name, and it is only ever called with the user's own
 * name, on surfaces that print the user's own identity —
 * never for anyone else, and the decorated form exists
 * only at render time on this device (nothing decorated
 * is ever stored into a message, a profile field, a share
 * or an export). With the master switch off (or nothing
 * chosen) it renders exactly the plain name text it
 * replaces.
 */
@Composable
fun DecoratedOwnName(
    name: String,
    fontSize: TextUnit,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
    modifier: Modifier = Modifier,
    nameModifier: Modifier = Modifier
) {
    val enabled by LocalPremium.enabled.collectAsState()
    val sticker by LocalPremium.sticker.collectAsState()
    val badge by LocalPremium.badge.collectAsState()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            fontSize = fontSize,
            color = color,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = nameModifier
        )
        if (enabled && sticker.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(sticker, fontSize = fontSize * 0.95f)
        }
        if (enabled && badge) {
            Spacer(Modifier.width(4.dp))
            LocalPremiumBadge(
                size = (fontSize.value * 1.1f).dp
            )
        }
    }
}

/** The user's own avatar with the local-premium badge on
 *  its bottom-right corner (v0.9.63, H1), when the badge
 *  switch is on. [content] draws the avatar itself at
 *  [sizeDp]; the wrapper adds nothing otherwise. */
@Composable
fun OwnAvatarBadge(sizeDp: Int, content: @Composable () -> Unit) {
    val enabled by LocalPremium.enabled.collectAsState()
    val badge by LocalPremium.badge.collectAsState()
    Box {
        content()
        if (enabled && badge) {
            LocalPremiumBadge(
                size = (sizeDp * 0.36f).dp,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

/**
 * The 本地会员 settings section (v0.9.63, H1), rendered as
 * its own second-level page inside the settings screen.
 * The page states the scope up front, in the user's own
 * terms: decorations only you can see, on this phone, in
 * this app; nobody else sees any change; no Premium
 * feature is unlocked by any of this.
 */
@Composable
fun LocalPremiumSection() {
    val enabled by LocalPremium.enabled.collectAsState()
    val sticker by LocalPremium.sticker.collectAsState()
    val badge by LocalPremium.badge.collectAsState()
    val banner by LocalPremium.banner.collectAsState()
    val iconStyle by LocalPremium.iconStyle.collectAsState()
    val me by MeStore.me.collectAsState()
    SettingsSectionHeader("本地会员")
    SettingsGroup {
        Spacer(Modifier.height(4.dp))
        Text(
            "本地会员是纯装饰，只有你自己在这台手机、这个 App 里看得到：" +
                "对方看到的你没有任何变化，也不解锁任何会员功能。" +
                "换设备、清除数据或退出登录后，这些设置就不存在了。" +
                "每个账号分别保存，也会跟着设置备份走（备份文件仍只在你手里）。",
            color = Color.Gray, fontSize = 13.sp
        )
        if (me.isPremium) {
            Spacer(Modifier.height(8.dp))
            Text(
                "这个账号本身是 Telegram Premium（服务器那边的真实状态）：" +
                    "官方的会员标识照常按服务器数据显示，和这里的本地会员" +
                    "是两回事，互不影响。",
                color = Color.Gray, fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("开启本地会员", fontSize = 15.sp)
                Text(
                    "关掉后所有装饰都不显示，已选的设置保留。",
                    color = Color.Gray, fontSize = 12.sp
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { LocalPremium.setEnabled(it) }
            )
        }
        if (enabled) {
            Spacer(Modifier.height(14.dp))
            Text(
                "预览", fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            val bannerPair = LocalPremium.BANNER_COLORS
                .getOrNull(banner)
            val bannerActive = banner != 0 && bannerPair != null &&
                bannerPair.first != 0L
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
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
                            Modifier.background(
                                MaterialTheme.colorScheme
                                    .surfaceVariant
                            )
                        }
                    )
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OwnAvatarBadge(sizeDp = 48) {
                        val path = me.photoPath
                        if (path != null) {
                            AvatarImage(path = path, sizeDp = 48)
                        } else {
                            val pair = OfficialTheme.avatarColors(
                                me.id, OfficialTheme.dark
                            )
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                pair.first,
                                                pair.second
                                            )
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    me.displayName.trim()
                                        .firstOrNull()?.toString()
                                        ?: "?",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        DecoratedOwnName(
                            name = me.displayName
                                .ifEmpty { "我" },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (bannerActive) {
                                Color.White
                            } else {
                                Color.Unspecified
                            }
                        )
                        Text(
                            "在线",
                            color = if (bannerActive) {
                                Color.White.copy(alpha = 0.85f)
                            } else {
                                Color.Gray
                            },
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SettingsDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                "名字后面的贴纸", fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "选一个表情或符号，缀在你的名字后面显示。",
                color = Color.Gray, fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            val options = listOf("") + LocalPremium.STICKERS
            for (rowItems in options.chunked(6)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    for (option in rowItems) {
                        val selected = sticker == option
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme
                                            .primary
                                            .copy(alpha = 0.15f)
                                    } else {
                                        MaterialTheme.colorScheme
                                            .surfaceVariant
                                    }
                                )
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            2.dp,
                                            MaterialTheme
                                                .colorScheme.primary,
                                            RoundedCornerShape(10.dp)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable {
                                    LocalPremium.setSticker(option)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (option.isEmpty()) {
                                Text(
                                    "无", fontSize = 13.sp,
                                    color = Color.Gray
                                )
                            } else {
                                Text(option, fontSize = 20.sp)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SettingsDivider()
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("会员徽章", fontSize = 15.sp)
                    Text(
                        "一颗自绘的金色徽章，显示在你的名字旁；" +
                            "「我」页面和你自己的资料页上，头像角上也会有。",
                        color = Color.Gray, fontSize = 12.sp
                    )
                }
                Switch(
                    checked = badge,
                    onCheckedChange = { LocalPremium.setBadge(it) }
                )
            }
            Spacer(Modifier.height(16.dp))
            SettingsDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                "「我」页面横幅", fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "给「我」页面顶部换一个渐变横幅，只改这一处页面的样子。",
                color = Color.Gray, fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                for ((index, pair) in
                    LocalPremium.BANNER_COLORS.withIndex()) {
                    val selected = banner == index
                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 64.dp, height = 40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    if (index == 0) {
                                        Modifier.background(
                                            MaterialTheme
                                                .colorScheme
                                                .surfaceVariant
                                        )
                                    } else {
                                        Modifier.background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color(pair.first),
                                                    Color(pair.second)
                                                )
                                            )
                                        )
                                    }
                                )
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            2.dp,
                                            MaterialTheme
                                                .colorScheme.primary,
                                            RoundedCornerShape(8.dp)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable {
                                    LocalPremium.setBanner(index)
                                }
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            LocalPremium.BANNER_NAMES[index],
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            SettingsDivider()
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("会员风格图标", fontSize = 15.sp)
                    Text(
                        "底部导航栏的「我的」、抽屉头部和「我」页面里" +
                            "与你有关的图标换成金色。",
                        color = Color.Gray, fontSize = 12.sp
                    )
                }
                Switch(
                    checked = iconStyle,
                    onCheckedChange = {
                        LocalPremium.setIconStyle(it)
                    }
                )
            }
        }
    }
}
