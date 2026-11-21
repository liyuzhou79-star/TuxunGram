package com.tuxungram.android

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
import androidx.compose.material.icons.filled.AttachFile
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TdRepository.start(applicationContext)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

/** Translate a TDLib error into one plain sentence for the UI.
 *  Raw codes are never shown except the numeric code on the
 *  unknown-error line, so a user screenshot stays useful. */
private fun friendlyError(error: Pair<Int, String>): String {
    val (code, message) = error
    val flood = Regex("FLOOD_WAIT_(\\d+)").find(message)
    return when {
        flood != null -> {
            val seconds = flood.groupValues[1].toIntOrNull() ?: 0
            if (seconds >= 120) "操作太频繁了，Telegram 让你等 ${seconds / 60} 分钟再试。"
            else "操作太频繁了，Telegram 让你等一会儿再试。"
        }
        message.contains("PHONE_CODE_INVALID") -> "验证码不对，再对一遍重新填。"
        message.contains("PHONE_CODE_EXPIRED") -> "验证码过期了，点下面重新发一个。"
        message.contains("PASSWORD_HASH_INVALID") -> "密码不对，再试一次。注意大小写。"
        message.contains("PHONE_NUMBER_INVALID") -> "这个手机号 Telegram 不认，检查区号和号码。"
        message.contains("API_ID_INVALID") || message.contains("API_HASH") ->
            "App 还没配好（凭证问题），这不是你的操作问题。"
        else -> "出了点问题（错误码 $code），再试一次；还不行就重启 App 试试。"
    }
}

@Composable
private fun ErrorText() {
    val error by TdRepository.lastError.collectAsState()
    val e = error ?: return
    Text(
        text = friendlyError(e),
        color = Color(0xFFC62828),
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 12.dp)
    )
}

@Composable
private fun AppRoot() {
    val authState by TdRepository.authState.collectAsState()
    var enteredPhone by remember { mutableStateOf("") }
    var forcePhone by remember { mutableStateOf(false) }

    when (val state = authState) {
        is AuthState.WaitTdlibParameters -> SplashScreen()
        is AuthState.WaitPhone -> PhoneScreen { fullNumber ->
            enteredPhone = fullNumber
            forcePhone = false
            TdRepository.setPhone(fullNumber)
        }
        is AuthState.WaitCode -> {
            if (forcePhone) {
                PhoneScreen { fullNumber ->
                    enteredPhone = fullNumber
                    forcePhone = false
                    TdRepository.setPhone(fullNumber)
                }
            } else {
                CodeScreen(
                    phone = enteredPhone,
                    onChangePhone = { forcePhone = true }
                )
            }
        }
        is AuthState.WaitPassword -> PasswordScreen(hint = state.hint)
        is AuthState.WaitRegistration -> RegistrationScreen()
        is AuthState.Ready -> ReadyHost()
        is AuthState.LoggingOut -> TransitionScreen("正在退出…")
        is AuthState.Closed -> ClosedScreen()
        is AuthState.Other -> TransitionScreen("正在处理…")
    }
}

@Composable
private fun SplashScreen() {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(20_000)
        slow = true
    }
    CenterColumn {
        Text("TuxunGram", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("正在连接…", color = Color.Gray)
        // The splash must surface errors too: in v0.1.0 a rejected
        // setTdlibParameters would have hung here silently forever.
        ErrorText()
        if (slow) {
            Spacer(Modifier.height(8.dp))
            Text(
                "连接有点慢，检查一下网络（可能需要先开代理）",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun TransitionScreen(text: String) {
    CenterColumn {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(text, color = Color.Gray)
    }
}

@Composable
private fun ClosedScreen() {
    val context = LocalContext.current
    CenterColumn {
        Text("已断开", fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { TdRepository.restart(context.applicationContext) }) {
            Text("重新连接")
        }
    }
}

@Composable
private fun CenterColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) { content() }
}

@Composable
private fun FormColumn(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        content()
        ErrorText()
    }
}

@Composable
private fun PhoneScreen(onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("+86") }
    var number by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val authState by TdRepository.authState.collectAsState()
    val error by TdRepository.lastError.collectAsState()
    LaunchedEffect(authState, error) { busy = false }

    FormColumn(title = "登录 Telegram") {
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("区号") },
                modifier = Modifier.width(110.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = number,
                onValueChange = { number = it.filter { ch -> ch.isDigit() || ch == ' ' } },
                label = { Text("手机号") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
        }
        if (localError != null) {
            Text(localError!!, color = Color(0xFFC62828), fontSize = 14.sp,
                modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                val digits = number.filter { it.isDigit() }
                val cc = code.filter { it.isDigit() }
                if (digits.length < 5 || cc.isEmpty()) {
                    localError = "手机号好像不对，检查一下"
                } else {
                    localError = null
                    TdRepository.clearError()
                    busy = true
                    onSubmit("+$cc$digits")
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "处理中…" else "获取验证码")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "验证码会发到你其他设备上的 Telegram 里，也可能走短信，以 Telegram 实际发送为准。",
            color = Color.Gray, fontSize = 13.sp
        )
    }
}

@Composable
private fun CodeScreen(phone: String, onChangePhone: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var resendLeft by remember { mutableIntStateOf(0) }
    val authState by TdRepository.authState.collectAsState()
    val error by TdRepository.lastError.collectAsState()
    LaunchedEffect(authState, error) { busy = false }
    LaunchedEffect(resendLeft) {
        if (resendLeft > 0) {
            delay(1_000)
            resendLeft -= 1
        }
    }

    FormColumn(title = "输入验证码") {
        if (phone.isNotEmpty()) {
            Text("已发送到 $phone", color = Color.Gray, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter { ch -> ch.isDigit() } },
            label = { Text("验证码") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                TdRepository.clearError()
                busy = true
                TdRepository.checkCode(code)
            },
            enabled = !busy && code.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "处理中…" else "登录")
        }
        Row {
            TextButton(
                onClick = {
                    TdRepository.resendCode()
                    resendLeft = 30
                },
                enabled = resendLeft == 0
            ) {
                Text(if (resendLeft > 0) "重新发送（${resendLeft}）" else "重新发送验证码")
            }
            TextButton(onClick = onChangePhone) {
                Text("换个手机号")
            }
        }
    }
}

@Composable
private fun PasswordScreen(hint: String) {
    var password by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val authState by TdRepository.authState.collectAsState()
    val error by TdRepository.lastError.collectAsState()
    LaunchedEffect(authState, error) { busy = false }

    FormColumn(title = "输入两步验证密码") {
        if (hint.isNotEmpty()) {
            Text("密码提示：$hint", color = Color.Gray, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = { show = !show }) {
                    Text(if (show) "隐藏" else "显示")
                }
            }
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                TdRepository.clearError()
                busy = true
                TdRepository.checkPassword(password)
                password = ""
            },
            enabled = !busy && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "处理中…" else "登录")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "忘了密码只能走 Telegram 官方的找回流程，本 App 第一版不做找回。",
            color = Color.Gray, fontSize = 13.sp
        )
    }
}

@Composable
private fun RegistrationScreen() {
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val authState by TdRepository.authState.collectAsState()
    val error by TdRepository.lastError.collectAsState()
    LaunchedEffect(authState, error) { busy = false }

    FormColumn(title = "这个号码还没注册") {
        OutlinedTextField(
            value = first,
            onValueChange = { first = it },
            label = { Text("名字") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = last,
            onValueChange = { last = it },
            label = { Text("姓氏（可空）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                TdRepository.clearError()
                busy = true
                TdRepository.registerUser(first.trim(), last.trim())
            },
            enabled = !busy && first.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "处理中…" else "注册")
        }
    }
}

@Composable
private fun ReadyHost() {
    var openChatId by remember { mutableStateOf<Long?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    // Forward flow: (fromChatId, messageId) plus where to return to.
    var forwardReq by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var forwardReturnTo by remember { mutableStateOf<Long?>(null) }
    val chatId = openChatId
    val req = forwardReq
    when {
        showSettings -> SettingsScreen(onBack = { showSettings = false })
        req != null -> ForwardPickerScreen(
            fromChatId = req.first,
            messageId = req.second,
            onDone = {
                forwardReq = null
                openChatId = forwardReturnTo
            }
        )
        chatId == null -> ChatListScreen(
            onOpenChat = { openChatId = it },
            onOpenSettings = { showSettings = true }
        )
        else -> ChatScreen(
            chatId = chatId,
            onBack = { openChatId = null },
            onForward = { msg ->
                forwardReturnTo = chatId
                forwardReq = chatId to msg.id
                openChatId = null
            }
        )
    }
}

/** Chat-list-as-picker for forwarding one message. Stays open until
 *  TDLib answers, so a failure is shown here instead of vanishing. */
@Composable
private fun ForwardPickerScreen(
    fromChatId: Long,
    messageId: Long,
    onDone: () -> Unit
) {
    val chats by ChatStore.chats.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler { if (!busy) onDone() }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (!busy) onDone() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("转发到…", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        if (error != null) {
            Text(
                error!!, color = Color(0xFFC62828), fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        if (busy) {
            CenterColumn {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在转发…", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = chats, key = { it.id }) { chat ->
                    ChatRow(chat, onClick = {
                        busy = true
                        error = null
                        MessageStore.forwardMessage(
                            fromChatId, messageId, chat.id
                        ) { failure ->
                            if (failure == null) {
                                onDone()
                            } else {
                                busy = false
                                error = failure
                            }
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
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

private fun formatChatTime(dateSeconds: Int): String {
    if (dateSeconds <= 0) return ""
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply {
        timeInMillis = dateSeconds * 1000L
    }
    val sameDay = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    val yesterday = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_YEAR, -1)
    }
    val isYesterday = yesterday.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        yesterday.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameDay -> java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(then.time)
        isYesterday -> "昨天"
        now.timeInMillis - then.timeInMillis < 7L * 24 * 3600 * 1000 ->
            java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(then.time)
        now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) ->
            java.text.SimpleDateFormat("M/d", java.util.Locale.getDefault()).format(then.time)
        else -> java.text.SimpleDateFormat("yyyy/M/d", java.util.Locale.getDefault())
            .format(then.time)
    }
}

@Composable
private fun ChatListScreen(onOpenChat: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val chats by ChatStore.chats.collectAsState()
    val allLoaded by ChatStore.allLoaded.collectAsState()
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val shown = if (query.isBlank()) chats else chats.filter {
        it.title.contains(query.trim(), ignoreCase = true)
    }

    LaunchedEffect(listState, shown.size, allLoaded) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastVisible ->
            if (!allLoaded && shown.isNotEmpty() && lastVisible >= shown.size - 5) {
                ChatStore.loadMore()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "TuxunGram",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "设置")
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        if (shown.isEmpty()) {
            CenterColumn {
                if (!allLoaded && query.isBlank()) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在加载对话…", color = Color.Gray)
                } else if (query.isNotBlank()) {
                    Text("没有找到", color = Color.Gray)
                } else {
                    Text(
                        "还没有对话。在 Telegram 里有聊天后，这里会自动出现。",
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = shown,
                    key = { it.id }
                ) { chat ->
                    ChatRow(chat, onClick = { onOpenChat(chat.id) })
                }
            }
        }
    }
}

@Composable
private fun ChatRow(chat: ChatItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val hue = (chat.id % 8 + 8) % 8
        val colors = listOf(
            Color(0xFFEF9A9A), Color(0xFFF48FB1), Color(0xFFCE93D8), Color(0xFF90CAF9),
            Color(0xFF80CBC4), Color(0xFFA5D6A7), Color(0xFFFFCC80), Color(0xFFBCAAA4)
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    colors[hue.toInt()],
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                chat.title.trim().firstOrNull()?.toString() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (chat.isChannel) {
                    Spacer(Modifier.width(6.dp))
                    Text("频道", color = Color.Gray, fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(formatChatTime(chat.dateSeconds), color = Color.Gray, fontSize = 12.sp)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.preview,
                    color = Color.Gray,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (chat.isPinned) {
                    Text("置顶", color = Color.Gray, fontSize = 11.sp)
                }
                if (chat.unreadCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                if (chat.isMuted) Color(0xFFBDBDBD)
                                else MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val unreadCap by UiSettings.unreadCap.collectAsState()
                        Text(
                            if (chat.unreadCount > unreadCap) {
                                "$unreadCap+"
                            } else {
                                chat.unreadCount.toString()
                            },
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatMsgTime(dateSeconds: Int): String {
    if (dateSeconds <= 0) return ""
    val cal = java.util.Calendar.getInstance().apply {
        timeInMillis = dateSeconds * 1000L
    }
    return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(cal.time)
}

@Composable
private fun ChatScreen(chatId: Long, onBack: () -> Unit, onForward: (MsgItem) -> Unit) {
    val context = LocalContext.current
    val chats by ChatStore.chats.collectAsState()
    val chat = chats.firstOrNull { it.id == chatId }
    val messages by MessageStore.messages.collectAsState()
    val sendError by MessageStore.sendError.collectAsState()
    var input by remember { mutableStateOf("") }
    var pendingPhoto by remember { mutableStateOf<String?>(null) }
    var photoCaption by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    DisposableEffect(chatId) {
        MessageStore.open(chatId)
        onDispose { MessageStore.close() }
    }
    BackHandler { onBack() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val file = File(context.cacheDir, "pick_${System.currentTimeMillis()}.jpg")
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    file.outputStream().use { output -> inputStream.copyTo(output) }
                }
                pendingPhoto = file.absolutePath
                photoCaption = ""
            } catch (t: Throwable) {
                // Picker copy failed: stay on the chat screen silently.
            }
        }
    }

    LaunchedEffect(listState, messages.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastVisible ->
            if (messages.isNotEmpty() && lastVisible >= messages.size - 3) {
                MessageStore.loadOlder()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                chat?.title ?: "对话",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (chat?.isChannel == true) {
                Spacer(Modifier.width(6.dp))
                Text("频道", color = Color.Gray, fontSize = 12.sp)
            }
        }

        val photoPath = pendingPhoto
        if (photoPath != null) {
            PhotoPreview(
                path = photoPath,
                caption = photoCaption,
                onCaptionChange = { photoCaption = it },
                onSend = {
                    MessageStore.sendPhoto(photoPath, photoCaption.trim())
                    pendingPhoto = null
                },
                onCancel = { pendingPhoto = null }
            )
            return@Column
        }

        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            items(items = messages.asReversed(), key = { it.id }) { msg ->
                MessageBubble(msg, isGroup = chat?.isGroup == true, onForward = onForward)
            }
        }

        if (sendError != null) {
            Text(
                sendError!!,
                color = Color(0xFFC62828),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        val canBasic = chat?.canSendBasic ?: true
        val canPhotos = chat?.canSendPhotos ?: true
        if (!canBasic && !canPhotos) {
            Text(
                "你不能在这个对话里发言",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            val replyTarget by MessageStore.replyTarget.collectAsState()
            val target = replyTarget
            if (target != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "回复 ${target.senderName.ifEmpty { if (target.outgoing) "我" else "" }}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            target.text.ifEmpty {
                                if (target.kind == MsgKind.PHOTO) "[图片]" else "原消息"
                            },
                            color = Color.Gray,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = { MessageStore.setReplyTarget(null) }) {
                        Icon(Icons.Filled.Close, contentDescription = "取消回复")
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canPhotos) {
                    IconButton(onClick = { picker.launch("image/*") }) {
                        Icon(
                            Icons.Filled.AttachFile,
                            contentDescription = "发图片",
                            tint = Color.Gray
                        )
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("消息") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (canBasic && input.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color(0xFFBDBDBD)
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            val text = input.trim()
                            if (text.isNotEmpty()) {
                                MessageStore.clearSendError()
                                MessageStore.sendText(text)
                                input = ""
                            }
                        },
                        enabled = canBasic && input.isNotBlank()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoPreview(
    path: String,
    caption: String,
    onCaptionChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        value = withContext(Dispatchers.IO) {
            try {
                BitmapFactory.decodeFile(path)
            } catch (t: Throwable) {
                null
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("发送图片", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFFEEEEEE)),
                contentAlignment = Alignment.Center
            ) {
                Text("图片读不出来", color = Color.Gray)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = caption,
            onValueChange = onCaptionChange,
            label = { Text("说明（可空）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("取消")
            }
            Button(onClick = onSend, modifier = Modifier.weight(1f)) {
                Text("发送")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(msg: MsgItem, isGroup: Boolean, onForward: (MsgItem) -> Unit) {
    val outgoing = msg.outgoing
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Column(
                modifier = Modifier
                    .background(
                        if (outgoing) Color(0xFFD8EDCB) else Color(0xFFF1F1F1),
                        RoundedCornerShape(12.dp)
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { menuOpen = true }
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                if (isGroup && !outgoing && msg.senderName.isNotEmpty()) {
                    Text(
                        msg.senderName,
                        color = Color(0xFF5B7C99),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (msg.replyToId != 0L) {
                    Row(
                        modifier = Modifier
                            .background(Color(0x14000000), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "回复：${msg.replySummary.ifEmpty { "原消息" }}",
                            color = Color(0xFF546E7A),
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                when (msg.kind) {
                    MsgKind.TEXT -> Text(msg.text, fontSize = 15.sp)
                    MsgKind.PHOTO -> {
                        PhotoBubbleContent(msg)
                        if (msg.text.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(msg.text, fontSize = 15.sp)
                        }
                    }
                    MsgKind.OTHER -> Text(
                        "[消息]",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
                Row {
                    Text(formatMsgTime(msg.dateSeconds), color = Color.Gray, fontSize = 11.sp)
                    when (msg.sendingState) {
                        "pending" -> Text(
                            "  发送中…",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                        "failed" -> Text(
                            "  发送失败",
                            color = Color(0xFFC62828),
                            fontSize = 11.sp
                        )
                    }
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("回复") },
                    onClick = {
                        menuOpen = false
                        MessageStore.setReplyTarget(msg)
                    }
                )
                if (msg.text.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text("复制") },
                        onClick = {
                            menuOpen = false
                            clipboard.setText(AnnotatedString(msg.text))
                        }
                    )
                }
                if (outgoing && msg.kind == MsgKind.TEXT && msg.sendingState.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = {
                            menuOpen = false
                            editOpen = true
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("转发") },
                    onClick = {
                        menuOpen = false
                        onForward(msg)
                    }
                )
                DropdownMenuItem(
                    text = { Text("删除", color = Color(0xFFC62828)) },
                    onClick = {
                        menuOpen = false
                        deleteOpen = true
                    }
                )
            }
        }
    }
    if (editOpen) {
        var draft by remember(msg.id) { mutableStateOf(msg.text) }
        AlertDialog(
            onDismissRequest = { editOpen = false },
            title = { Text("编辑消息") },
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
                        val text = draft.trim()
                        if (text.isNotEmpty() && text != msg.text) {
                            MessageStore.clearSendError()
                            MessageStore.editText(msg.id, text)
                        }
                        editOpen = false
                    },
                    enabled = draft.isNotBlank()
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editOpen = false }) { Text("取消") }
            }
        )
    }
    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text("删除这条消息？") },
            text = {
                Text(
                    if (msg.outgoing) "会同时为所有人删除。" else "只在你这边删除。"
                )
            },
            confirmButton = {
                Button(onClick = {
                    MessageStore.deleteMessage(msg)
                    deleteOpen = false
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PhotoBubbleContent(msg: MsgItem) {
    val path = msg.photoPath
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                try {
                    BitmapFactory.decodeFile(path)
                } catch (t: Throwable) {
                    null
                }
            }
        }
    }
    val ratio = if (msg.photoWidth > 0 && msg.photoHeight > 0) {
        msg.photoHeight.toFloat() / msg.photoWidth.toFloat()
    } else {
        0.75f
    }
    val heightDp = (220f * ratio).coerceIn(110f, 300f)
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 220.dp, height = heightDp.dp)
        )
    } else {
        Box(
            modifier = Modifier
                .size(width = 220.dp, height = heightDp.dp)
                .background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center
        ) {
            Text("图片加载中…", color = Color.Gray, fontSize = 13.sp)
        }
    }
}
