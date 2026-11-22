package com.tuxungram.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.AccountStore
import com.tuxungram.android.td.AppLock
import com.tuxungram.android.td.AuthState
import com.tuxungram.android.td.ChatItem
import com.tuxungram.android.td.ChatStore
import com.tuxungram.android.td.MessageStore
import com.tuxungram.android.td.MsgItem
import com.tuxungram.android.td.MsgKind
import com.tuxungram.android.td.ContactsStore
import com.tuxungram.android.td.CrashLog
import com.tuxungram.android.td.DeepLink
import com.tuxungram.android.td.LinkRouter
import com.tuxungram.android.td.ProxyConfig
import com.tuxungram.android.td.ProxyStore
import com.tuxungram.android.td.TdClient
import com.tuxungram.android.td.TdRepository
import com.tuxungram.android.td.UiSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

import com.tuxungram.android.ui.ArchiveScreen
import com.tuxungram.android.ui.BlockedScreen
import com.tuxungram.android.ui.ChangelogScreen
import com.tuxungram.android.ui.GuideScreen
import com.tuxungram.android.ui.WhatsNewScreen
import com.tuxungram.android.ui.ChatListScreen
import com.tuxungram.android.ui.AvatarImage
import com.tuxungram.android.ui.MeScreen
import com.tuxungram.android.ui.ChatBubbleIcon
import com.tuxungram.android.ui.PeopleIcon
import com.tuxungram.android.ui.ContactsScreen
import com.tuxungram.android.ui.EditProfileScreen
import com.tuxungram.android.ui.GroupPermissionsScreen
import com.tuxungram.android.ui.LockScreen
import com.tuxungram.android.ui.MembersScreen
import com.tuxungram.android.ui.Motion
import com.tuxungram.android.ui.ChatScreen
import com.tuxungram.android.ui.ClosedScreen
import com.tuxungram.android.ui.CodeScreen
import com.tuxungram.android.ui.CreateChatScreen
import com.tuxungram.android.ui.ForwardPickerScreen
import com.tuxungram.android.ui.PasswordScreen
import com.tuxungram.android.ui.PhoneScreen
import com.tuxungram.android.ui.ProfileScreen
import com.tuxungram.android.ui.RegistrationScreen
import com.tuxungram.android.ui.SessionsScreen
import com.tuxungram.android.ui.SettingsScreen
import com.tuxungram.android.ui.SharedMediaScreen
import com.tuxungram.android.ui.SplashScreen
import com.tuxungram.android.ui.StickerSetsScreen
import com.tuxungram.android.ui.StoryViewerScreen
import com.tuxungram.android.ui.ThreadScreen
import com.tuxungram.android.ui.TransitionScreen

// Plain ComponentActivity: the app lock's fingerprint prompt
// uses the framework BiometricPrompt since v0.9.0 (H3), which
// needs no FragmentActivity — that dropped the androidx
// fragment dependency tree from the package.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Crash recorder (v0.9.6, P5): installed before
        // anything else so even a startup crash leaves a
        // record. It only writes the stack trace to the app's
        // private directory and then hands the crash to the
        // default handler — the crash itself is unchanged,
        // and nothing is ever uploaded.
        CrashLog.install(applicationContext, BuildConfig.VERSION_NAME)
        // App lock (v0.6.0, E2): read the persisted state before
        // the UI composes; a set PIN means the app starts locked.
        AppLock.init(applicationContext)
        // Motion system (v0.9.11, A1): read the system
        // animator duration scale once, so every transition
        // in the app honours the reduced-motion setting.
        Motion.init(applicationContext)
        // Edge-to-edge (Phase D, D2): content draws behind the
        // system bars; every screen gets its safe distance from
        // the windowInsetsPadding applied in AppRoot/ReadyHost,
        // so nothing hides under the status bar or the gesture
        // bar on any page.
        enableEdgeToEdge()
        handleOpenChatIntent(intent, coldStart = true)
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
            // Font size (v0.7.0, F3): a density fontScale over
            // the whole UI — message text and list text scale
            // together, the way mature clients do it.
            val fontIndex by UiSettings.fontSize.collectAsState()
            val baseDensity = LocalDensity.current
            val density = remember(baseDensity, fontIndex) {
                Density(
                    baseDensity.density,
                    UiSettings.FONT_SCALES[fontIndex]
                )
            }
            CompositionLocalProvider(LocalDensity provides density) {
                MaterialTheme(colorScheme = scheme) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppRoot()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenChatIntent(intent, coldStart = false)
    }

    override fun onStart() {
        super.onStart()
        AppLock.onForeground()
    }

    override fun onStop() {
        AppLock.onBackground()
        super.onStop()
    }

    /** Notification taps carry the chat to open (Batch 3, C1).
     *  A tap on a BACKGROUND account's notification (v0.9.10)
     *  also carries its slot, and the chat must open under
     *  THAT account — feeding another account's chat id to
     *  the running client is the cross-account mixup R1
     *  banned. The (slot, chat) is persisted as a pending
     *  open: on a cold start the process simply boots into
     *  that slot (nothing is running yet, so no restart is
     *  needed), on a warm start the restart-based switch
     *  takes us there, and TdRepository opens the chat once
     *  that account reaches Ready. */
    private fun handleOpenChatIntent(intent: Intent?, coldStart: Boolean) {
        val chatId = intent?.getLongExtra("open_chat_id", 0L) ?: 0L
        if (chatId == 0L) return
        val slot = intent?.getIntExtra("open_chat_slot", -1) ?: -1
        if (slot < 0 || slot == AccountStore.currentSlot()) {
            DeepLink.pendingChat.value = chatId
            return
        }
        // AccountStore may not be initialized yet on the
        // cold-start path (TdRepository.start does it later
        // in onCreate); init is idempotent.
        AccountStore.init(applicationContext)
        if (slot == AccountStore.currentSlot()) {
            DeepLink.pendingChat.value = chatId
            return
        }
        AccountStore.setPendingOpen(slot, chatId)
        if (coldStart) {
            AccountStore.setCurrent(slot)
        } else {
            TdRepository.switchAccount(slot)
        }
    }
}

@Composable
private fun AppRoot() {
    // App lock gate (v0.6.0, E2): when locked, the lock screen
    // covers everything — chat list, login flow, all of it.
    val locked by AppLock.locked.collectAsState()
    // First-run onboarding (v0.9.6, P2): on the very first
    // launch (no version ever marked seen) the full guide
    // comes before everything else; finishing or skipping
    // marks the running version as seen. Later launches and
    // updates skip this — updates get the what's-new page in
    // ReadyHost instead.
    var guideDone by remember {
        mutableStateOf(UiSettings.seenVersion().isNotEmpty())
    }
    // Gate transitions (v0.9.11, A1): the lock screen, the
    // guide and the main flow used to replace each other in a
    // hard cut; they now crossfade through one AnimatedContent.
    val gate = when {
        locked -> 0
        !guideDone -> 1
        else -> 2
    }
    AnimatedContent(
        targetState = gate,
        transitionSpec = {
            fadeIn(Motion.tween(260)) togetherWith
                fadeOut(Motion.tween(180))
        },
        label = "appGate"
    ) { g ->
        when (g) {
            0 -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                LockScreen()
            }
            1 -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                GuideScreen(onDone = {
                    UiSettings.markSeenVersion(BuildConfig.VERSION_NAME)
                    guideDone = true
                })
            }
            else -> AuthHost()
        }
    }
}

@Composable
private fun AuthHost() {
    val authState by TdRepository.authState.collectAsState()
    var enteredPhone by remember { mutableStateOf("") }
    var forcePhone by remember { mutableStateOf(false) }

    // Ready has its own insets handling in ReadyHost (the story
    // viewer bleeds full-screen); every other state gets the
    // standard system-bars safe distance here (Phase D, D2).
    if (authState is AuthState.Ready) {
        ReadyHost()
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Soft gradient wash behind the whole auth flow
            // (v0.9.11, A2): seed colour at the top fading
            // through the background to a tertiary hint at the
            // bottom — program-drawn, no image assets.
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary
                            .copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.tertiary
                            .copy(alpha = 0.08f)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Auth-flow transitions (v0.9.11, A1): splash -> phone
        // -> code -> password used to hard-cut; crossfade now.
        AnimatedContent(
            targetState = authState,
            transitionSpec = {
                fadeIn(Motion.tween(280)) togetherWith
                    fadeOut(Motion.tween(200))
            },
            label = "authState"
        ) { state ->
            when (state) {
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
                is AuthState.WaitPassword ->
                    PasswordScreen(hint = state.hint)
                is AuthState.WaitRegistration -> RegistrationScreen()
                is AuthState.Ready -> ReadyHost()
                is AuthState.LoggingOut -> TransitionScreen("正在退出…")
                is AuthState.Closed -> ClosedScreen()
                is AuthState.Other -> TransitionScreen("正在处理…")
            }
        }
    }
}

/** Route snapshot for ReadyHost's animated navigation
 *  (v0.9.11, A1): the host's display branches collapse into
 *  one value so AnimatedContent can transition between them
 *  instead of hard-cutting. [depth] ranks how deep a route
 *  sits — home 0, pages opened from home 1, pages opened
 *  from those 2 — and picks the slide direction: deeper
 *  slides in from the right, back slides the page out to
 *  the right, same-depth crossfades. */
private sealed interface Route {
    val depth: Int

    data object Home : Route {
        override val depth = 0
    }

    data object Changelog : Route {
        override val depth = 1
    }

    data object GuideReplay : Route {
        override val depth = 1
    }

    data object EditProfile : Route {
        override val depth = 1
    }

    data object Blocked : Route {
        override val depth = 1
    }

    data object Sessions : Route {
        override val depth = 1
    }

    data object StickerSets : Route {
        override val depth = 1
    }

    data object SettingsOverlay : Route {
        override val depth = 1
    }

    data object Archive : Route {
        override val depth = 1
    }

    data object ContactsOverlay : Route {
        override val depth = 1
    }

    data object Create : Route {
        override val depth = 1
    }

    data class Profile(val chatId: Long) : Route {
        override val depth = 1
    }

    data class Chat(val chatId: Long) : Route {
        override val depth = 1
    }

    data class Members(val chatId: Long) : Route {
        override val depth = 2
    }

    data class SharedMedia(val chatId: Long) : Route {
        override val depth = 2
    }

    data class GroupSettings(val chatId: Long) : Route {
        override val depth = 2
    }

    data class Thread(val chatId: Long, val postId: Long) : Route {
        override val depth = 2
    }

    data class Forward(
        val fromChatId: Long,
        val messageIds: List<Long>
    ) : Route {
        override val depth = 2
    }
}

@Composable
private fun ReadyHost() {
    // Saveable (v0.9.9, W5): a rotation recreates the
    // activity; without this the open chat and the home tab
    // were lost and the user landed back on the chat list.
    var openChatId by rememberSaveable { mutableStateOf<Long?>(null) }
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
    // What's-new (v0.9.6, P2/P3): the first time the main UI
    // is reached on a version the user has not seen yet, show
    // that version's changelog entry once. It lives behind
    // Ready, so it can never block the login flow; closing it
    // marks the version seen. A first install never lands
    // here (the full guide in AppRoot marks the version).
    var newsDone by remember {
        val seen = UiSettings.seenVersion()
        mutableStateOf(seen.isEmpty() || seen == BuildConfig.VERSION_NAME)
    }
    var showChangelog by remember { mutableStateOf(false) }
    var showGuideReplay by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }
    var showBlocked by remember { mutableStateOf(false) }
    var showSessions by remember { mutableStateOf(false) }
    var showStickerSets by remember { mutableStateOf(false) }
    var showArchive by remember { mutableStateOf(false) }
    var showContacts by remember { mutableStateOf(false) }
    var profileChatId by remember { mutableStateOf<Long?>(null) }
    // Members page (v0.9.2, L4): opened from a group/channel
    // profile; Back returns to that profile.
    var membersChatId by remember { mutableStateOf<Long?>(null) }
    // Shared media page (v0.9.3, M4): same pattern — opened
    // from a profile, Back returns to it.
    var sharedMediaChatId by remember { mutableStateOf<Long?>(null) }
    // Group settings page (v0.9.5, O2): opened from a group
    // profile's 管理 section; Back returns to that profile.
    var groupSettingsChatId by remember { mutableStateOf<Long?>(null) }
    // Rich-text mention taps (v0.9.2, L1): a mentionName carries
    // the user id (resolve via createPrivateChat), a plain
    // @mention carries the username (resolve via
    // searchPublicChat, verified in td_api.tl). Both land on
    // the profile page.
    val pendingProfileUser by DeepLink.pendingProfileUser.collectAsState()
    LaunchedEffect(pendingProfileUser) {
        val uid = pendingProfileUser ?: return@LaunchedEffect
        DeepLink.pendingProfileUser.value = null
        ContactsStore.openPrivateChat(uid) { cid ->
            if (cid != null) profileChatId = cid
        }
    }
    val pendingProfileName by DeepLink.pendingProfileName.collectAsState()
    LaunchedEffect(pendingProfileName) {
        val name = pendingProfileName ?: return@LaunchedEffect
        DeepLink.pendingProfileName.value = null
        val query = TdClient.request("searchPublicChat").apply {
            put("username", name)
        }
        TdRepository.sendRaw(query) { response ->
            if (!TdClient.isError(response)) {
                val cid = response.optLong("id")
                if (cid != 0L) profileChatId = cid
            }
        }
    }
    var showCreate by remember { mutableStateOf(false) }
    // Bottom navigation (v0.9.9, W4): 0 chats / 1 contacts /
    // 2 settings / 3 me. Only the home level carries the bar;
    // opening a chat or any sub-page replaces the whole host
    // as before. The chat list's LazyListState is hoisted
    // here so tab switches keep its scroll position.
    var homeTab by rememberSaveable { mutableStateOf(0) }
    val homeListState = rememberLazyListState()
    val allChats by ChatStore.chats.collectAsState()
    val homeUnread = allChats.sumOf { it.unreadCount }
    val unreadCap by UiSettings.unreadCap.collectAsState()
    // Proxy links (v0.9.9, W3) ask for the Settings tab,
    // where the proxy editor opens prefilled.
    val openSettingsWanted by DeepLink.pendingOpenSettings.collectAsState()
    LaunchedEffect(openSettingsWanted) {
        if (openSettingsWanted) {
            DeepLink.pendingOpenSettings.value = false
            openChatId = null
            homeTab = 2
        }
    }
    // Invite-link previews (W3) wait here for the join
    // decision, whichever screen raised them.
    val invitePreview by LinkRouter.pendingInvite.collectAsState()
    var storyChatId by remember { mutableStateOf<Long?>(null) }
    // Forward flow: (fromChatId, messageIds) plus where to return
    // to. A list since v0.7.0 — multi-select forwards in one call.
    var forwardReq by remember { mutableStateOf<Pair<Long, List<Long>>?>(null) }
    var forwardReturnTo by remember { mutableStateOf<Long?>(null) }
    // Comment thread (v0.8.0, G1): (channel chat id, post id).
    var threadReq by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val chatId = openChatId
    val req = forwardReq
    val thread = threadReq
    if (!newsDone) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            WhatsNewScreen(onClose = {
                UiSettings.markSeenVersion(BuildConfig.VERSION_NAME)
                newsDone = true
            })
        }
        return
    }
    // The story viewer bleeds full-screen behind the bars and
    // pads its own header (Phase D, D2); everything else gets
    // the standard system-bars safe distance.
    if (storyChatId != null) {
        StoryViewerScreen(
            chatId = storyChatId!!,
            onBack = { storyChatId = null }
        )
        return
    }
    // The route snapshot: the same priority order the old
    // `when` chain used, resolved once into a value the
    // transition can animate between.
    val route: Route = when {
        showChangelog -> Route.Changelog
        showGuideReplay -> Route.GuideReplay
        thread != null -> Route.Thread(thread.first, thread.second)
        showEditProfile -> Route.EditProfile
        showBlocked -> Route.Blocked
        showSessions -> Route.Sessions
        showStickerSets -> Route.StickerSets
        showSettings -> Route.SettingsOverlay
        showArchive -> Route.Archive
        showContacts -> Route.ContactsOverlay
        groupSettingsChatId != null ->
            Route.GroupSettings(groupSettingsChatId!!)
        sharedMediaChatId != null ->
            Route.SharedMedia(sharedMediaChatId!!)
        membersChatId != null -> Route.Members(membersChatId!!)
        profileChatId != null -> Route.Profile(profileChatId!!)
        showCreate -> Route.Create
        req != null -> Route.Forward(req.first, req.second)
        chatId != null -> Route.Chat(chatId)
        else -> Route.Home
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                when {
                    targetState.depth > initialState.depth ->
                        (slideInHorizontally(Motion.tween(280)) {
                            it
                        } + fadeIn(Motion.tween(280))) togetherWith
                            (slideOutHorizontally(Motion.tween(280)) {
                                -it / 3
                            } + fadeOut(Motion.tween(200)))
                    targetState.depth < initialState.depth ->
                        (slideInHorizontally(Motion.tween(280)) {
                            -it / 3
                        } + fadeIn(Motion.tween(280))) togetherWith
                            (slideOutHorizontally(Motion.tween(280)) {
                                it
                            } + fadeOut(Motion.tween(200)))
                    else ->
                        fadeIn(Motion.tween(220)) togetherWith
                            fadeOut(Motion.tween(180))
                }
            },
            label = "route",
            modifier = Modifier.fillMaxSize()
        ) { r ->
            when (r) {
                is Route.Changelog -> ChangelogScreen(
                    onBack = { showChangelog = false }
                )
                is Route.GuideReplay -> GuideScreen(
                    onDone = { showGuideReplay = false }
                )
                is Route.Thread -> ThreadScreen(
                    sourceChatId = r.chatId,
                    postId = r.postId,
                    onBack = { threadReq = null }
                )
                is Route.EditProfile -> EditProfileScreen(
                    onBack = { showEditProfile = false }
                )
                is Route.Blocked -> BlockedScreen(
                    onBack = { showBlocked = false }
                )
                is Route.Sessions -> SessionsScreen(
                    onBack = { showSessions = false }
                )
                is Route.StickerSets -> StickerSetsScreen(
                    onBack = { showStickerSets = false }
                )
                is Route.SettingsOverlay -> SettingsScreen(
            onBack = { showSettings = false },
            onOpenSessions = {
                showSettings = false
                showSessions = true
            },
            onOpenStickerSets = {
                showSettings = false
                showStickerSets = true
            },
            onOpenEditProfile = {
                showSettings = false
                showEditProfile = true
            },
            onOpenBlocked = {
                showSettings = false
                showBlocked = true
            },
            onOpenChangelog = {
                showSettings = false
                showChangelog = true
            },
            onOpenGuide = {
                showSettings = false
                showGuideReplay = true
            }
        )
                is Route.Archive -> ArchiveScreen(
                    onBack = { showArchive = false },
                    onOpenChat = {
                        showArchive = false
                        openChatId = it
                    }
                )
                is Route.ContactsOverlay -> ContactsScreen(
                    onBack = { showContacts = false },
                    onOpenChat = {
                        showContacts = false
                        openChatId = it
                    }
                )
                is Route.GroupSettings -> GroupPermissionsScreen(
                    chatId = r.chatId,
                    onBack = { groupSettingsChatId = null }
                )
                is Route.SharedMedia -> SharedMediaScreen(
                    chatId = r.chatId,
                    onBack = { sharedMediaChatId = null }
                )
                is Route.Members -> MembersScreen(
                    chatId = r.chatId,
                    onBack = { membersChatId = null }
                )
                is Route.Profile -> ProfileScreen(
                    chatId = r.chatId,
                    onBack = { profileChatId = null },
                    onOpenSecretChat = { newChatId ->
                        profileChatId = null
                        openChatId = newChatId
                    },
                    onOpenMembers = { membersChatId = r.chatId },
                    onOpenSharedMedia = { sharedMediaChatId = r.chatId },
                    onOpenGroupSettings = {
                        groupSettingsChatId = r.chatId
                    }
                )
                is Route.Create -> CreateChatScreen(
                    onBack = { showCreate = false },
                    onCreated = {
                        showCreate = false
                        openChatId = it
                    }
                )
                is Route.Forward -> ForwardPickerScreen(
                    fromChatId = r.fromChatId,
                    messageIds = r.messageIds,
                    onDone = {
                        forwardReq = null
                        openChatId = forwardReturnTo
                    }
                )
                is Route.Home -> Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                // Tab transitions (v0.9.11, A1): switching the
                // bottom-nav tabs used to hard-cut; now a short
                // fade with a slight horizontal drift.
                AnimatedContent(
                    targetState = homeTab,
                    transitionSpec = {
                        (fadeIn(Motion.tween(220)) +
                            slideInHorizontally(Motion.tween(220)) {
                                it / 8
                            }) togetherWith fadeOut(Motion.tween(150))
                    },
                    label = "homeTab"
                ) { tab ->
                    when (tab) {
                        1 -> ContactsScreen(
                            onBack = { homeTab = 0 },
                            onOpenChat = { openChatId = it }
                        )
                        2 -> SettingsScreen(
                            onBack = { homeTab = 0 },
                            onOpenSessions = { showSessions = true },
                            onOpenStickerSets = { showStickerSets = true },
                            onOpenEditProfile = { showEditProfile = true },
                            onOpenBlocked = { showBlocked = true },
                            onOpenChangelog = { showChangelog = true },
                            onOpenGuide = { showGuideReplay = true }
                        )
                        3 -> MeScreen(
                            onEditProfile = { showEditProfile = true },
                            onOpenSavedChat = { openChatId = it },
                            onOpenSettingsTab = { homeTab = 2 },
                            onOpenChangelog = { showChangelog = true }
                        )
                        else -> ChatListScreen(
                            onOpenChat = { openChatId = it },
                            onOpenSettings = { homeTab = 2 },
                            onOpenChatLocated = { cid, mid ->
                                MessageStore.pendingLocateId = mid
                                openChatId = cid
                            },
                            onOpenArchive = { showArchive = true },
                            onOpenCreate = { showCreate = true },
                            onOpenStory = { storyChatId = it },
                            onOpenContacts = { homeTab = 1 },
                            listState = homeListState
                        )
                    }
                }
            }
            NavigationBar {
                NavigationBarItem(
                    selected = homeTab == 0,
                    onClick = { homeTab = 0 },
                    icon = {
                        BadgedBox(badge = {
                            if (homeUnread > 0) {
                                Badge {
                                    Text(
                                        if (homeUnread > unreadCap) {
                                            "$unreadCap+"
                                        } else {
                                            homeUnread.toString()
                                        }
                                    )
                                }
                            }
                        }) {
                            Icon(ChatBubbleIcon, contentDescription = "对话")
                        }
                    },
                    label = { Text("对话") }
                )
                NavigationBarItem(
                    selected = homeTab == 1,
                    onClick = { homeTab = 1 },
                    icon = { Icon(PeopleIcon, contentDescription = "联系人") },
                    label = { Text("联系人") }
                )
                NavigationBarItem(
                    selected = homeTab == 2,
                    onClick = { homeTab = 2 },
                    icon = {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    },
                    label = { Text("设置") }
                )
                NavigationBarItem(
                    selected = homeTab == 3,
                    onClick = { homeTab = 3 },
                    icon = {
                        Icon(
                            Icons.Filled.AccountCircle,
                            contentDescription = "我的"
                        )
                    },
                    label = { Text("我的") }
                )
            }
        }
                is Route.Chat -> ChatScreen(
                    chatId = r.chatId,
                    onBack = { openChatId = null },
                    onForward = { msgs ->
                        forwardReturnTo = r.chatId
                        forwardReq = r.chatId to msgs.map { it.id }
                        openChatId = null
                    },
                    onOpenProfile = { profileChatId = r.chatId },
                    onOpenThread = { postId ->
                        threadReq = r.chatId to postId
                    }
                )
            }
        }
        val invite = invitePreview
        if (invite != null) {
            AlertDialog(
                onDismissRequest = { LinkRouter.pendingInvite.value = null },
                title = { Text(invite.title) },
                text = {
                    Column {
                        val path = invite.photoPath
                        if (path != null) {
                            AvatarImage(path, 56)
                        }
                        if (invite.memberCount > 0) {
                            Text(
                                "${invite.memberCount} 名成员",
                                color = Color.Gray
                            )
                        }
                        if (invite.description.isNotEmpty()) {
                            Text(invite.description)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        LinkRouter.joinInvite(hostContext, invite)
                    }) {
                        Text(if (invite.createsJoinRequest) "申请加入" else "加入")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        LinkRouter.pendingInvite.value = null
                    }) { Text("取消") }
                }
            )
        }
    }
}

