package com.tuxungram.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.TwoFactorStore

/**
 * Settings → 隐私与安全 → 两步验证 (v0.9.13, T5). One row
 * summarising the server-held state (from getPasswordState)
 * plus a dialog flow for every management action:
 * enable / change / disable the cloud password (setPassword),
 * set or change the recovery email (setRecoveryEmailAddress
 * + the code from checkRecoveryEmailAddressCode), and reveal
 * the current recovery email (getRecoveryEmailAddress).
 *
 * Every password is typed into a masked field, lives only in
 * this dialog's state while one request runs, and is dropped
 * when the dialog closes. Nothing is persisted or logged.
 */
@Composable
fun TwoFactorSection() {
    val state by TwoFactorStore.state.collectAsState()
    val notice by TwoFactorStore.notice.collectAsState()
    var open by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { TwoFactorStore.load() }

    val s = state
    val subtitle = when {
        s == null ->
            "正在读取当前状态…"
        s.hasPassword && s.hasRecoveryEmail ->
            "已开启，恢复邮箱也设好了。"
        s.hasPassword ->
            "已开启，还没设恢复邮箱。"
        else ->
            "未开启。开启后，新设备登录除了验证码还要输这个密码。"
    }
    IconRow(
        icon = Icons.Filled.Lock,
        title = "两步验证（Telegram 密码）",
        subtitle = subtitle,
        onClick = {
            TwoFactorStore.load()
            open = true
        },
        trailing = {
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null, tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    )
    if (notice != null) {
        Text(
            notice!!, color = Color.Gray, fontSize = 12.5.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
        )
    }
    if (open) {
        TwoFactorDialog(onClose = { open = false })
    }
}

@Composable
private fun TwoFactorDialog(onClose: () -> Unit) {
    val state by TwoFactorStore.state.collectAsState()
    val s = state
    // mode: main | setpw | disable | email | emailcode | reveal
    var mode by remember { mutableStateOf("main") }
    var currentPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun backToMain() {
        mode = "main"
        currentPw = ""
        newPw = ""
        confirmPw = ""
        hint = ""
        email = ""
        code = ""
        revealed = null
        error = null
        busy = false
    }

    fun finish(ok: Boolean, text: String) {
        busy = false
        if (ok) {
            done = text
            backToMain()
            done = text
        } else {
            error = text
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("两步验证") },
        text = {
            Column {
                if (s == null) {
                    Text("正在读取当前状态…")
                } else when (mode) {
                    "main" -> {
                        Text(
                            if (s.hasPassword) {
                                "状态：已开启"
                            } else {
                                "状态：未开启"
                            },
                            fontSize = 14.5.sp
                        )
                        if (s.hasPassword && s.hint.isNotEmpty()) {
                            Text(
                                "密码提示：${s.hint}",
                                fontSize = 13.sp, color = Color.Gray
                            )
                        }
                        Text(
                            when {
                                s.recoveryEmailPending ->
                                    "恢复邮箱：验证码已发到 " +
                                        "${s.recoveryEmailPattern}，" +
                                        "等你输验证码确认"
                                s.hasRecoveryEmail ->
                                    "恢复邮箱：已设置"
                                else ->
                                    "恢复邮箱：未设置（忘了密码时" +
                                        "靠它找回，建议设上）"
                            },
                            fontSize = 13.sp, color = Color.Gray
                        )
                        if (s.loginEmailPattern.isNotEmpty()) {
                            Text(
                                "登录邮箱：${s.loginEmailPattern}",
                                fontSize = 13.sp, color = Color.Gray
                            )
                        }
                        if (done != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                done!!, fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme
                                    .primary
                            )
                        }
                    }
                    "setpw" -> {
                        if (s.hasPassword) {
                            PasswordField(
                                "现在的密码", currentPw
                            ) { currentPw = it }
                            Spacer(Modifier.height(8.dp))
                        }
                        PasswordField("新密码", newPw) {
                            newPw = it
                        }
                        Spacer(Modifier.height(8.dp))
                        PasswordField("再输一次新密码", confirmPw) {
                            confirmPw = it
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = hint,
                            onValueChange = { hint = it },
                            label = { Text("密码提示（可选）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "disable" -> {
                        Text(
                            "关掉后，新设备登录只靠短信/应用内" +
                                "验证码。确定要关，先输现在的密码：",
                            fontSize = 13.5.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        PasswordField("现在的密码", currentPw) {
                            currentPw = it
                        }
                    }
                    "email" -> {
                        Text(
                            "恢复邮箱用来在忘记密码时找回账号。" +
                                "提交后服务器会给新邮箱发一个" +
                                "验证码，输对了才算设好。",
                            fontSize = 13.5.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        PasswordField("现在的密码", currentPw) {
                            currentPw = it
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("新恢复邮箱") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "emailcode" -> {
                        Text(
                            "验证码已经发到" +
                                (if (s.recoveryEmailPattern
                                        .isNotEmpty()
                                ) {
                                    " ${s.recoveryEmailPattern}"
                                } else {
                                    "新邮箱"
                                }) +
                                "，把邮箱里收到的验证码填进来：",
                            fontSize = 13.5.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text("邮箱验证码") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "reveal" -> {
                        if (revealed == null) {
                            Text(
                                "输现在的密码，就能看到当前" +
                                    "的恢复邮箱完整地址：",
                                fontSize = 13.5.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            PasswordField("现在的密码", currentPw) {
                                currentPw = it
                            }
                        } else {
                            Text(
                                "当前恢复邮箱：",
                                fontSize = 13.sp, color = Color.Gray
                            )
                            Text(revealed!!, fontSize = 15.sp)
                        }
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error!!, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            when (mode) {
                "main" -> {
                    TextButton(onClick = onClose) {
                        Text("关闭")
                    }
                }
                "setpw" -> {
                    Button(
                        enabled = !busy,
                        onClick = {
                            error = null
                            when {
                                s?.hasPassword == true &&
                                    currentPw.isEmpty() ->
                                    error = "先输现在的密码。"
                                newPw.isEmpty() ->
                                    error = "新密码不能为空。"
                                newPw != confirmPw ->
                                    error = "两次输的新密码不一样。"
                                else -> {
                                    busy = true
                                    TwoFactorStore.setPassword(
                                        currentPw, newPw, hint
                                    ) { ok, text ->
                                        finish(ok, text)
                                    }
                                }
                            }
                        }
                    ) { Text("保存") }
                }
                "disable" -> {
                    Button(
                        enabled = !busy && currentPw.isNotEmpty(),
                        onClick = {
                            error = null
                            busy = true
                            TwoFactorStore.disablePassword(
                                currentPw
                            ) { ok, text -> finish(ok, text) }
                        }
                    ) { Text("关闭两步验证") }
                }
                "email" -> {
                    Button(
                        enabled = !busy &&
                            currentPw.isNotEmpty() &&
                            email.contains("@"),
                        onClick = {
                            error = null
                            busy = true
                            TwoFactorStore.setRecoveryEmail(
                                currentPw, email.trim()
                            ) { ok, text ->
                                busy = false
                                if (ok) {
                                    done = text
                                    error = null
                                    mode = "emailcode"
                                    currentPw = ""
                                } else {
                                    error = text
                                }
                            }
                        }
                    ) { Text("发送验证码") }
                }
                "emailcode" -> {
                    Button(
                        enabled = !busy && code.isNotEmpty(),
                        onClick = {
                            error = null
                            busy = true
                            TwoFactorStore
                                .confirmRecoveryEmailCode(code) {
                                        ok, text ->
                                    finish(ok, text)
                                }
                        }
                    ) { Text("确认") }
                }
                "reveal" -> {
                    if (revealed == null) {
                        Button(
                            enabled = !busy &&
                                currentPw.isNotEmpty(),
                            onClick = {
                                error = null
                                busy = true
                                TwoFactorStore.revealRecoveryEmail(
                                    currentPw
                                ) { ok, text ->
                                    busy = false
                                    if (ok) {
                                        revealed = text
                                        currentPw = ""
                                    } else {
                                        error = text
                                    }
                                }
                            }
                        ) { Text("查看") }
                    } else {
                        TextButton(onClick = { backToMain() }) {
                            Text("好")
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (mode != "main") {
                TextButton(
                    enabled = !busy,
                    onClick = { backToMain() }
                ) { Text("返回") }
            } else if (s != null) {
                Column {
                    if (!s.hasPassword) {
                        TextButton(onClick = {
                            error = null
                            done = null
                            mode = "setpw"
                        }) { Text("开启两步验证") }
                    } else {
                        TextButton(onClick = {
                            error = null
                            done = null
                            mode = "setpw"
                        }) { Text("修改密码") }
                        TextButton(onClick = {
                            error = null
                            done = null
                            mode = "disable"
                        }) { Text("关闭两步验证") }
                        if (s.hasRecoveryEmail) {
                            TextButton(onClick = {
                                error = null
                                done = null
                                revealed = null
                                mode = "reveal"
                            }) { Text("查看当前恢复邮箱") }
                        }
                    }
                    if (s.recoveryEmailPending) {
                        TextButton(onClick = {
                            error = null
                            done = null
                            mode = "emailcode"
                        }) { Text("输入邮箱验证码") }
                        TextButton(onClick = {
                            TwoFactorStore
                                .resendRecoveryEmailCode {
                                        ok, text ->
                                    if (ok) done = text
                                    else error = text
                                }
                        }) { Text("重发验证码") }
                    } else if (s.hasPassword) {
                        TextButton(onClick = {
                            error = null
                            done = null
                            mode = "email"
                        }) {
                            Text(
                                if (s.hasRecoveryEmail) {
                                    "更换恢复邮箱"
                                } else {
                                    "设置恢复邮箱"
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun PasswordField(
    label: String,
    value: String,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
