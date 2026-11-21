package com.tuxungram.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import com.tuxungram.android.td.DeepLink
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
import com.tuxungram.android.ui.CreateChatScreen
import com.tuxungram.android.ui.ForwardPickerScreen
import com.tuxungram.android.ui.PasswordScreen
import com.tuxungram.android.ui.PhoneScreen
import com.tuxungram.android.ui.ProfileScreen
import com.tuxungram.android.ui.RegistrationScreen
import com.tuxungram.android.ui.SettingsScreen
import com.tuxungram.android.ui.SplashScreen
import com.tuxungram.android.ui.StoryViewerScreen
import com.tuxungram.android.ui.TransitionScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleOpenChatIntent(intent)
        TdRepository.start(applicationContext)
        setContent {
            // App theme (Batch 3, C3): persisted dark/light mode
            // plus an accent seed color, applied live.
            val themeMode by UiSettings.themeMode.collectAsState()
            val seedIndex by UiSettings.seedIndex.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val dark = when (themeMode) {
                UiSettings.THEME_LIGHT -> false
                UiSettings.THEME_DARK -> true
                else -> systemDark
            }
            val seed = Color(UiSettings.SEED_COLORS[seedIndex])
            val scheme = if (dark) {
                darkColorScheme(primary = seed, secondary = seed)
            } else {
                lightColorScheme(primary = seed, secondary = seed)
            }
            MaterialTheme(colorScheme = scheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenChatIntent(intent)
    }

    /** Notification taps carry the chat to open (Batch 3, C1). */
    private fun handleOpenChatIntent(intent: Intent?) {
        val chatId = intent?.getLongExtra("open_chat_id", 0L) ?: 0L
        if (chatId != 0L) {
            DeepLink.pendingChat.value = chatId
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
    // Notification taps land here (Batch 3, C1).
    val pendingChat by DeepLink.pendingChat.collectAsState()
    LaunchedEffect(pendingChat) {
        val id = pendingChat
        if (id != null) {
            openChatId = id
            DeepLink.pendingChat.value = null
        }
    }
    // Android 13+ needs a runtime grant before any notification
    // (keep-alive or new-message) can appear.
    val hostContext = LocalContext.current
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                hostContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var showSettings by remember { mutableStateOf(false) }
    var showArchive by remember { mutableStateOf(false) }
    var profileChatId by remember { mutableStateOf<Long?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var storyChatId by remember { mutableStateOf<Long?>(null) }
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
        profileChatId != null -> ProfileScreen(
            chatId = profileChatId!!,
            onBack = { profileChatId = null }
        )
        showCreate -> CreateChatScreen(
            onBack = { showCreate = false },
            onCreated = {
                showCreate = false
                openChatId = it
            }
        )
        storyChatId != null -> StoryViewerScreen(
            chatId = storyChatId!!,
            onBack = { storyChatId = null }
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
            onOpenArchive = { showArchive = true },
            onOpenCreate = { showCreate = true },
            onOpenStory = { storyChatId = it }
        )
        else -> ChatScreen(
            chatId = chatId,
            onBack = { openChatId = null },
            onForward = { msg ->
                forwardReturnTo = chatId
                forwardReq = chatId to msg.id
                openChatId = null
            },
            onOpenProfile = { profileChatId = chatId }
        )
    }
}

