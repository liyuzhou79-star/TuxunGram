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

import com.tuxungram.android.ui.ArchiveScreen
import com.tuxungram.android.ui.ChatListScreen
import com.tuxungram.android.ui.ChatScreen
import com.tuxungram.android.ui.ClosedScreen
import com.tuxungram.android.ui.CodeScreen
import com.tuxungram.android.ui.ForwardPickerScreen
import com.tuxungram.android.ui.PasswordScreen
import com.tuxungram.android.ui.PhoneScreen
import com.tuxungram.android.ui.RegistrationScreen
import com.tuxungram.android.ui.SettingsScreen
import com.tuxungram.android.ui.SplashScreen
import com.tuxungram.android.ui.TransitionScreen

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
private fun ReadyHost() {
    var openChatId by remember { mutableStateOf<Long?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showArchive by remember { mutableStateOf(false) }
    // Forward flow: (fromChatId, messageId) plus where to return to.
    var forwardReq by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var forwardReturnTo by remember { mutableStateOf<Long?>(null) }
    val chatId = openChatId
    val req = forwardReq
    when {
        showSettings -> SettingsScreen(onBack = { showSettings = false })
        showArchive -> ArchiveScreen(
            onBack = { showArchive = false },
            onOpenChat = {
                showArchive = false
                openChatId = it
            }
        )
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
            onOpenSettings = { showSettings = true },
            onOpenChatLocated = { cid, mid ->
                MessageStore.pendingLocateId = mid
                openChatId = cid
            },
            onOpenArchive = { showArchive = true }
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

