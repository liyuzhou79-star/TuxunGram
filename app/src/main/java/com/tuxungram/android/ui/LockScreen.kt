package com.tuxungram.android.ui

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.tuxungram.android.td.AppLock
import kotlinx.coroutines.delay

/**
 * Lock screen (v0.6.0, E2): numeric PIN pad with dot
 * indicators; fingerprint unlock is offered automatically
 * when it is enabled and the device supports it. Shown by
 * AppRoot before anything else whenever AppLock is locked.
 */
@Composable
fun LockScreen() {
    val context = LocalContext.current
    val biometricOn by AppLock.biometricEnabled.collectAsState()
    val biometricOk = remember {
        AppLock.biometricAvailable(context)
    }
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val pinLen = remember { AppLock.pinLength() }
    // Wrong-PIN lockout (audit fix A3): while AppLock says
    // entry is throttled, the pad ignores presses and the
    // remaining wait is shown, ticking down.
    val lockoutUntil by AppLock.lockoutUntilMs.collectAsState()
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lockoutUntil) {
        while (AppLock.lockoutRemainingMs() > 0L) {
            nowMs = System.currentTimeMillis()
            delay(500)
        }
        nowMs = System.currentTimeMillis()
    }
    val lockoutLeftMs = maxOf(0L, lockoutUntil - nowMs)

    fun tryBiometric() {
        val activity = context as? FragmentActivity ?: return
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    AppLock.unlock()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        error = errString.toString()
                    }
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("解锁 TuxunGram")
            .setSubtitle("用指纹解锁")
            .setNegativeButtonText("用密码")
            .build()
        prompt.authenticate(info)
    }

    LaunchedEffect(Unit) {
        if (biometricOn && biometricOk) tryBiometric()
    }

    fun press(digit: String) {
        if (lockoutLeftMs > 0L) return
        if (entered.length >= 6) return
        entered += digit
        error = null
        if (entered.length == pinLen) {
            if (AppLock.verifyPin(entered)) {
                AppLock.unlock()
            } else {
                error = if (AppLock.lockoutRemainingMs() > 0L) {
                    "试错太多，先等一会儿再试"
                } else {
                    "密码不对，再试一次"
                }
                entered = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔒", fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "TuxunGram 已锁定",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text("输入应用锁密码", color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (i in 0 until pinLen) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(
                            if (i < entered.length) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color(0xFFBDBDBD)
                            },
                            CircleShape
                        )
                )
            }
        }
        Box(modifier = Modifier.height(28.dp)) {
            val shownError = if (lockoutLeftMs > 0L) {
                "试错太多，请等 ${(lockoutLeftMs + 999) / 1000} 秒再试"
            } else {
                error
            }
            if (shownError != null) {
                Text(
                    shownError,
                    color = Color(0xFFC62828),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫")
        )
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (key in row) {
                    Box(
                        modifier = Modifier.size(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            key == "⌫" -> TextButton(onClick = {
                                entered = entered.dropLast(1)
                                error = null
                            }) { Text("⌫", fontSize = 22.sp) }
                            key.isEmpty() -> Spacer(Modifier.size(1.dp))
                            else -> Button(
                                onClick = { press(key) },
                                shape = CircleShape,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Text(key, fontSize = 22.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (biometricOn && biometricOk) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { tryBiometric() }) {
                Text("用指纹解锁")
            }
        }
    }
}
