package com.tuxungram.android.ui


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.AuthState
import com.tuxungram.android.td.ChatItem
import com.tuxungram.android.td.ChatStore
import com.tuxungram.android.td.MessageStore
import com.tuxungram.android.td.MsgItem
import com.tuxungram.android.td.MsgKind
import com.tuxungram.android.td.ProxyConfig
import com.tuxungram.android.td.ProxyStore
import com.tuxungram.android.td.TdRepository
import com.tuxungram.android.td.UiSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

import com.tuxungram.android.BuildConfig

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val saved = remember { ProxyStore.load() }
    var enabled by remember { mutableStateOf(saved?.enabled ?: false) }
    var type by remember { mutableStateOf(saved?.type ?: "socks5") }
    var server by remember { mutableStateOf(saved?.server ?: "") }
    var port by remember { mutableStateOf((saved?.port ?: 1080).toString()) }
    var username by remember { mutableStateOf(saved?.username ?: "") }
    var password by remember { mutableStateOf(saved?.password ?: "") }
    var secret by remember { mutableStateOf(saved?.secret ?: "") }
    var localError by remember { mutableStateOf<String?>(null) }
    var savedTip by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("设置", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text("代理", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "连不上 Telegram 时用。填你自己的代理服务器，保存后立刻生效，下次打开 App 也会自动用上，不用再靠全局 VPN。",
            color = Color.Gray, fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("使用代理", modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("类型：")
            TextButton(
                onClick = { type = "socks5" },
                enabled = type != "socks5"
            ) { Text(if (type == "socks5") "● SOCKS5" else "○ SOCKS5") }
            TextButton(
                onClick = { type = "mtproto" },
                enabled = type != "mtproto"
            ) { Text(if (type == "mtproto") "● MTProto" else "○ MTProto") }
        }
        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text("服务器地址") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { ch -> ch.isDigit() } },
            label = { Text("端口") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        if (type == "socks5") {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名（可空）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码（可空）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
        } else {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text("密钥 secret（十六进制）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        if (localError != null) {
            Text(
                localError!!, color = Color(0xFFC62828), fontSize = 14.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val portNum = port.toIntOrNull()
                if (enabled && (server.isBlank() || portNum == null || portNum !in 1..65535)) {
                    localError = "地址或端口不对，检查一下"
                    savedTip = false
                } else {
                    localError = null
                    val config = ProxyConfig(
                        enabled = enabled,
                        type = type,
                        server = server.trim(),
                        port = portNum ?: 1080,
                        username = username.trim(),
                        password = password,
                        secret = secret.trim()
                    )
                    ProxyStore.save(config)
                    TdRepository.clearError()
                    ProxyStore.applyNow(config)
                    savedTip = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存")
        }
        if (savedTip) {
            Text(
                "已保存。代理对不对，回到对话列表看消息能不能刷出来最准。",
                color = Color.Gray, fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        ErrorText()
        Spacer(Modifier.height(28.dp))
        Text("显示", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "未读数上限：对话很多时，角标数字太长不好看。超过上限就显示成「上限+」。",
            color = Color.Gray, fontSize = 13.sp
        )
        val unreadCap by UiSettings.unreadCap.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (cap in UiSettings.UNREAD_CAPS) {
                TextButton(
                    onClick = { UiSettings.setUnreadCap(cap) },
                    enabled = unreadCap != cap
                ) {
                    Text(if (unreadCap == cap) "● $cap+" else "○ $cap+")
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "TuxunGram ${BuildConfig.VERSION_NAME}",
            color = Color.Gray, fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { TdRepository.logOut() }) {
            Text("退出登录", color = Color(0xFFC62828))
        }
    }
}

