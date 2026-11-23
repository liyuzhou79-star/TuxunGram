package com.tuxungram.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
        // Framework BiometricPrompt (platform API, minSdk 29
        // covers it): the androidx prompt needed a
        // FragmentActivity host and pulled the fragment tree
        // into the package for this one dialog (v0.9.0, H3).
        val prompt = android.hardware.biometrics.BiometricPrompt
            .Builder(context)
            .setTitle("解锁 TuxunGram")
            .setSubtitle("用指纹解锁")
            .setNegativeButton(
                "用密码",
                ContextCompat.getMainExecutor(context),
                android.content.DialogInterface.OnClickListener { _, _ -> }
            )
            .build()
        prompt.authenticate(
            android.os.CancellationSignal(),
            ContextCompat.getMainExecutor(context),
            object :
                android.hardware.biometrics.BiometricPrompt
                    .AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: android.hardware.biometrics.BiometricPrompt
                        .AuthenticationResult
                ) {
                    AppLock.unlock()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    // Platform BiometricPrompt error codes
                    // (documented constants): 10 = user
                    // canceled, 13 = negative button — both
                    // are quiet exits, not failures to report.
                    if (errorCode != 10 && errorCode != 13) {
                        error = errString.toString()
                    }
                }

                override fun onAuthenticationFailed() {
                    error = "指纹没对上，再试一次"
                }
            }
        )
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

    // Official-language alignment (v0.9.21, AB5): accent
    // hero disc, palette text colours, flat numeral keys
    // instead of filled buttons, the scheme error colour.
    // The lock logic — press(), the lockout countdown,
    // the biometric flow — is byte-for-byte the old code.
    val palette = OfficialTheme.current()
    // Short-screen safety (v0.9.39, F4): the pad is a
    // fixed ~490 dp stack; on landscape-height screens
    // it used to clip the last row. The column scrolls
    // now, with weighted spacers keeping the portrait
    // centering (they collapse to zero when the content
    // overflows, so nothing is ever unreachable). The
    // lock logic below is untouched.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Lock, contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(38.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "TuxunGram 已锁定",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "输入应用锁密码",
            color = palette.textSecondary, fontSize = 14.sp
        )
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
                                palette.textSecondary
                                    .copy(alpha = 0.35f)
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
                    color = MaterialTheme.colorScheme.error,
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
                            key == "⌫" -> Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        entered = entered.dropLast(1)
                                        error = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "⌫", fontSize = 22.sp,
                                    color = palette.textSecondary
                                )
                            }
                            key.isEmpty() -> Spacer(Modifier.size(1.dp))
                            else -> Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .clickable { press(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    key, fontSize = 26.sp,
                                    color = palette.textPrimary
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (biometricOn && biometricOk) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { tryBiometric() },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    Icons.Filled.Face, contentDescription = null,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text("用指纹解锁")
            }
        }
        Spacer(Modifier.weight(1f))
    }
}
