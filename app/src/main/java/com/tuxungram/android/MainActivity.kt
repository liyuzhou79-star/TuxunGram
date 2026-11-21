package com.tuxungram.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import CircleShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.AuthState
import com.tuxungram.android.td.ChatItem
import com.tuxungram.android.td.ChatStore
import com.tuxungram.android.td.TdRepository
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
        is AuthState.Ready -> ReadyScreen()
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
private fun ReadyScreen() {
    ChatListScreen()
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
private fun ChatListScreen() {
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
        Text(
            "TuxunGram",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
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
                    ChatRow(chat)
                }
            }
        }
    }
}

@Composable
private fun ChatRow(chat: ChatItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                        Text(
                            if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
