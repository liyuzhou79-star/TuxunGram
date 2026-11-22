package com.tuxungram.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.SessionEntry
import com.tuxungram.android.td.SessionStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Logged-in devices page (v0.6.0, E3): every session on this
 *  Telegram account, current device marked, each other session
 *  terminable, plus a "log out everywhere else" action. */
@Composable
fun SessionsScreen(
    onBack: () -> Unit,
    onScanQr: () -> Unit = {}
) {
    BackHandler { onBack() }
    val sessions by SessionStore.sessions.collectAsState()
    val loading by SessionStore.loading.collectAsState()
    val notice by SessionStore.notice.collectAsState()
    var terminateTarget by remember { mutableStateOf<SessionEntry?>(null) }
    var terminateOthersConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { SessionStore.load() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("已登录设备", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "这些设备都登着你的 Telegram 账号。不认识的设备，点「结束会话」把它踢下线。",
            color = Color.Gray, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        // Scan-to-confirm (v0.9.30, W1): authorize a NEW
        // device by scanning the QR code it displays.
        Button(
            onClick = onScanQr,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("扫描二维码登录其他设备")
        }
        if (notice != null) {
            Text(
                notice!!, color = Color.Gray, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        if (loading && sessions.isEmpty()) {
            CenterColumn {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在读取设备列表…", color = Color.Gray)
            }
            return@Column
        }
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .lazyListScrollbar(listState)
        ) {
            items(items = sessions, key = { it.id }) { session ->
                // Device card (v0.9.29, V3): each session sits
                // on its own card, and the glyph says what kind
                // of device it is (phone / desktop / web)
                // instead of every row wearing the same phone.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                ) {
                GroupCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            sessionIcon(session), contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(23.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            sessionTitle(session),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (session.isCurrent) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "当前设备",
                                color = Color(0xFF2E7D32),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(
                        sessionMeta(session),
                        color = Color.Gray, fontSize = 12.sp
                    )
                    if (session.isUnconfirmed) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = Color(0xFFC62828),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "这个会话还没确认过，留意是不是你本人登的",
                                color = Color(0xFFC62828), fontSize = 12.sp
                            )
                        }
                    }
                    if (!session.isCurrent) {
                        TextButton(onClick = { terminateTarget = session }) {
                            Text("结束会话", color = Color(0xFFC62828))
                        }
                    }
                }
                }
            }
            if (sessions.count { !it.isCurrent } > 0) {
                item(key = "terminate_others") {
                    Button(
                        onClick = { terminateOthersConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            Icons.Filled.ExitToApp, contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("退出全部其他设备")
                    }
                }
            }
        }
    }

    val target = terminateTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { terminateTarget = null },
            title = { Text("结束这个会话？") },
            text = {
                Text(
                    "「${sessionTitle(target)}」会被退出登录。" +
                        "如果那是你的另一台设备，下次打开它要重新登录。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    terminateTarget = null
                    SessionStore.terminate(target.id)
                }) {
                    Text("结束会话", color = Color(0xFFC62828))
                }
            },
            dismissButton = {
                TextButton(onClick = { terminateTarget = null }) {
                    Text("取消")
                }
            }
        )
    }
    if (terminateOthersConfirm) {
        AlertDialog(
            onDismissRequest = { terminateOthersConfirm = false },
            title = { Text("退出全部其他设备？") },
            text = {
                Text("除了这台设备，其他所有登着你账号的设备都会被退出登录。")
            },
            confirmButton = {
                TextButton(onClick = {
                    terminateOthersConfirm = false
                    SessionStore.terminateOthers()
                }) {
                    Text("全部退出", color = Color(0xFFC62828))
                }
            },
            dismissButton = {
                TextButton(onClick = { terminateOthersConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/** The glyph for a session's device kind (v0.9.29, V3):
 *  judged from the platform / type / model strings TDLib
 *  reports — web and browser sessions get the globe, phones
 *  the handset, everything else the desktop monitor. */
private fun sessionIcon(
    session: SessionEntry
): androidx.compose.ui.graphics.vector.ImageVector {
    val haystack = listOf(
        session.platform, session.deviceTypeLabel,
        session.deviceModel, session.applicationName
    ).joinToString(" ").lowercase()
    return when {
        "web" in haystack || "browser" in haystack -> GlobeIcon
        "android" in haystack || "iphone" in haystack ||
            "ios" in haystack || "phone" in haystack -> Icons.Filled.Phone
        else -> DesktopIcon
    }
}

private fun sessionTitle(session: SessionEntry): String {
    val app = session.applicationName.ifEmpty { "Telegram" }
    val device = session.deviceModel
    return if (device.isNotEmpty()) "$app · $device" else app
}

private fun sessionMeta(session: SessionEntry): String {
    val parts = ArrayList<String>()
    val platformLine = listOf(
        session.deviceTypeLabel, session.platform, session.systemVersion
    ).filter { it.isNotEmpty() }.joinToString(" ")
    if (platformLine.isNotEmpty()) parts.add(platformLine)
    val place = listOf(session.location, session.ip)
        .filter { it.isNotEmpty() }.joinToString(" · ")
    if (place.isNotEmpty()) parts.add(place)
    if (session.logInDate > 0) {
        parts.add("登录于 ${formatSessionTime(session.logInDate)}")
    }
    if (session.lastActiveDate > 0) {
        parts.add("最后活跃 ${formatSessionTime(session.lastActiveDate)}")
    }
    val caps = ArrayList<String>()
    if (session.canAcceptSecretChats) caps.add("可收秘密聊天")
    if (session.canAcceptCalls) caps.add("可接通话")
    if (caps.isNotEmpty()) parts.add(caps.joinToString("、"))
    return parts.joinToString("\n")
}

private fun formatSessionTime(seconds: Int): String {
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return format.format(Date(seconds * 1000L))
}
