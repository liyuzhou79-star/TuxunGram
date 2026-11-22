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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.text.style.TextAlign
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
    val palette = OfficialTheme.current()
    // Official-style splash (v0.9.21, AB1): theme-blue
    // background, the round plane badge in white, the app
    // name and the connection status under it. The error
    // line and the slow-connection hint keep their old
    // triggers and copy — only their place and contrast
    // changed with the background.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.accent),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(50.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "TuxunGram", fontSize = 28.sp,
            fontWeight = FontWeight.Bold, color = Color.White
        )
        Spacer(Modifier.height(28.dp))
        CircularProgressIndicator(color = Color.White)
        Spacer(Modifier.height(12.dp))
        Text("正在连接…", color = Color.White.copy(alpha = 0.85f))
        // The splash must surface errors too: in v0.1.0 a rejected
        // setTdlibParameters would have hung here silently forever.
        ErrorText()
        if (slow) {
            Spacer(Modifier.height(8.dp))
            Text(
                "连接有点慢，检查一下网络（可能需要先开代理）",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp
            )
        }
    }
}

/** Official-style main button for the auth flow (v0.9.21,
 *  AB1): full width, 10 dp corners — the shape every auth
 *  page's primary action shares from this version on. */
@Composable
fun AuthMainButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text(text, fontSize = 16.sp)
    }
}

/** Official-style underline field (v0.9.21, AB1): the
 *  stock filled field with a transparent container, so
 *  only the floating label, the text and the bottom rule
 *  show — the official login form's look, built from the
 *  standard component rather than a hand-rolled input. */
@Composable
fun AuthUnderlineField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    prefix: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        prefix = prefix,
        trailingIcon = trailingIcon,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        )
    )
}

/** Page shell for the auth forms (v0.9.21, AB1): window
 *  background, the round plane badge, a centered title
 *  with an optional gray subtitle, the form content, the
 *  shared error line in its old place right under the
 *  form, then the primary button pinned to the bottom
 *  with an optional footer under it. Presentation only —
 *  every screen keeps its own state, validation and
 *  TdRepository calls exactly as before. */
@Composable
fun AuthFormShell(
    title: String,
    subtitle: String?,
    buttonText: String,
    buttonEnabled: Boolean,
    onButton: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val palette = OfficialTheme.current()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) { PlaneBadge(76) }
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                fontSize = 14.sp,
                color = palette.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(28.dp))
        content()
        ErrorText()
        Spacer(Modifier.weight(1f))
        AuthMainButton(buttonText, buttonEnabled, onButton)
        if (footer != null) {
            Spacer(Modifier.height(10.dp))
            footer()
        }
        Spacer(Modifier.height(20.dp))
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
    // Official form (v0.9.21, AB1): a country row (flag,
    // name, dial code) over an underline number field.
    // The starting country is inferred from the system
    // locale with the old +86 default as the fallback;
    // the submitted string is assembled exactly as
    // before — "+" + dial code + the typed digits — and
    // the validation and busy handling are unchanged.
    var country by remember { mutableStateOf(Countries.inferredDefault()) }
    var number by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val authState by TdRepository.authState.collectAsState()
    val error by TdRepository.lastError.collectAsState()
    LaunchedEffect(authState, error) { busy = false }

    if (picking) {
        CountryPickerScreen(
            onPick = { picked ->
                country = picked
                picking = false
            },
            onBack = { picking = false }
        )
        return
    }
    val palette = OfficialTheme.current()
    AuthFormShell(
        title = "登录 Telegram",
        subtitle = "确认国家区号，输入你的手机号。",
        buttonText = if (busy) "处理中…" else "获取验证码",
        buttonEnabled = !busy,
        onButton = {
            val digits = number.filter { it.isDigit() }
            val cc = country.dial
            if (digits.length < 5 || cc.isEmpty()) {
                localError = "手机号好像不对，检查一下"
            } else {
                localError = null
                TdRepository.clearError()
                busy = true
                onSubmit("+$cc$digits")
            }
        },
        footer = {
            Text(
                "验证码会发到你其他设备上的 Telegram 里，也可能走短信，以 Telegram 实际发送为准。",
                color = palette.textSecondary, fontSize = 13.sp
            )
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { picking = true }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(country.flag, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                country.zhName, fontSize = 16.sp,
                color = palette.textPrimary
            )
            Spacer(Modifier.weight(1f))
            Text(
                "+${country.dial}", fontSize = 16.sp,
                color = palette.textSecondary
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "选择国家",
                tint = palette.textSecondary
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(palette.divider)
        )
        AuthUnderlineField(
            value = number,
            onValueChange = {
                number = it.filter { ch -> ch.isDigit() || ch == ' ' }
            },
            label = "手机号",
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            prefix = {
                Text(
                    "+${country.dial}  ",
                    color = palette.textSecondary
                )
            }
        )
        if (localError != null) {
            Text(localError!!, color = Color(0xFFC62828), fontSize = 14.sp,
                modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/** Full-screen country picker (v0.9.21, AB1 basic form;
 *  AB2 adds the search field and the letter groups):
 *  an action-bar header over the bundled table, one row
 *  per country — flag, names, dial code. Picking a row
 *  writes the country (and with it the dial code) back
 *  to the phone page. */
@Composable
fun CountryPickerScreen(
    onPick: (Country) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val palette = OfficialTheme.current()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.actionBarBg)
                .padding(horizontal = 4.dp, vertical = 6.dp),
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
                "选择国家或地区",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = palette.actionBarTitle
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(palette.divider)
        )
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .lazyListScrollbar(listState)
        ) {
            items(Countries.all) { c ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(c) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(c.flag, fontSize = 20.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            c.zhName, fontSize = 16.sp,
                            color = palette.textPrimary
                        )
                        Text(
                            c.enName, fontSize = 12.sp,
                            color = palette.textSecondary
                        )
                    }
                    Text(
                        "+${c.dial}", fontSize = 15.sp,
                        color = palette.textSecondary
                    )
                }
            }
        }
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

