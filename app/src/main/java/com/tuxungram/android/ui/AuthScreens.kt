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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
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


@Composable
fun SplashScreen() {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(20_000)
        slow = true
    }
    CenterColumn {
        PlaneBadge(84)
        Spacer(Modifier.height(16.dp))
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
fun TransitionScreen(text: String) {
    CenterColumn {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(text, color = Color.Gray)
    }
}

@Composable
fun ClosedScreen() {
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
fun PhoneScreen(onSubmit: (String) -> Unit) {
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
                leadingIcon = {
                    Icon(Icons.Filled.Phone, contentDescription = null)
                },
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
fun CodeScreen(phone: String, onChangePhone: () -> Unit) {
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
            leadingIcon = {
                Icon(Icons.Filled.MailOutline, contentDescription = null)
            },
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
fun PasswordScreen(hint: String) {
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
            leadingIcon = {
                Icon(Icons.Filled.Lock, contentDescription = null)
            },
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
fun RegistrationScreen() {
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
            leadingIcon = {
                Icon(Icons.Filled.Person, contentDescription = null)
            },
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

