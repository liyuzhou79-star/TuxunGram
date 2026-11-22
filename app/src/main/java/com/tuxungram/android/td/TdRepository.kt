package com.tuxungram.android.td

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tuxungram.android.ApiConfig
import com.tuxungram.android.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Authorization states the UI renders, mapped from TDLib's
 *  updateAuthorizationState. Anything unrecognised lands in [Other]
 *  so the UI can show a plain "unknown state" instead of hanging. */
sealed interface AuthState {
    data object WaitTdlibParameters : AuthState
    data object WaitPhone : AuthState
    data object WaitCode : AuthState
    data class WaitPassword(val hint: String) : AuthState
    data object WaitRegistration : AuthState
    data object Ready : AuthState
    data object LoggingOut : AuthState
    data object Closed : AuthState
    data class Other(val type: String) : AuthState
}

/**
 * Process-wide owner of the TDLib client and the authorization flow.
 * The UI observes [authState] and calls the set/check methods;
 * request errors surface through [lastError] as (code, message) —
 * the UI translates them into plain language, raw codes never
 * reach the screen.
 */
object TdRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.WaitTdlibParameters)
    val authState: StateFlow<AuthState> = _authState

    private val _lastError = MutableStateFlow<Pair<Int, String>?>(null)
    val lastError: StateFlow<Pair<Int, String>?> = _lastError

    @Volatile
    private var client: TdClient? = null

    /** setTdlibParameters may only be sent once per client instance:
     *  both the pushed updateAuthorizationState and the activation
     *  probe below can report WaitTdlibParameters for the same client. */
    private val parametersSent = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Bumped on every restart: updates and probe responses are
     *  tagged with the generation of the client that produced
     *  them, and anything from an older client is dropped. A
     *  closed client's receiver thread lives for up to ~2 more
     *  seconds and could otherwise push a stale Ready / error
     *  into the fresh session (audit fix A6). */
    private val clientGeneration =
        java.util.concurrent.atomic.AtomicInteger(0)

    @Volatile
    private var appContext: Context? = null

    private val updateListeners =
        java.util.concurrent.CopyOnWriteArrayList<(JSONObject) -> Unit>()

    /** Other stores (chat list, messages) subscribe to the raw
     *  update stream here. Listeners run on the receiver thread. */
    fun addUpdateListener(listener: (JSONObject) -> Unit) {
        updateListeners.add(listener)
    }

    /** Send with a raw response callback (no error translation);
     *  used by stores that treat some errors as signals (e.g.
     *  loadChats answering 404 when the list is exhausted). */
    fun sendRaw(query: JSONObject, callback: (JSONObject) -> Unit) {
        client?.send(query, callback)
    }

    /** Surface an error from a store that sends its own queries
     *  (the UI translates it into plain language). */
    fun reportError(error: Pair<Int, String>) {
        _lastError.value = error
    }

    /** True while a client is being constructed on the init
     *  thread; start() is a no-op meanwhile. */
    @Volatile
    private var starting = false

    fun start(context: Context) {
        if (client != null || starting) return
        starting = true
        appContext = context.applicationContext
        AccountStore.init(context)
        ProxyStore.init(context)
        UiSettings.init(context)
        val gen = clientGeneration.get()
        // H2-1 (performance): constructing the client loads the
        // ~32 MB libtdjson.so through TdNative's static init —
        // the heaviest single blocking item on the cold-start
        // path, previously done on the main thread inside
        // onCreate. It runs on this background thread now; the
        // main thread only does the three small
        // SharedPreferences reads above before the first frame.
        // Authorization states arrive asynchronously exactly
        // as they did when TDLib pushed them, so nothing
        // downstream can tell the difference.
        Thread({
            try {
                val c = TdClient { update ->
                    if (gen == clientGeneration.get()) {
                        handleUpdate(update)
                        for (listener in updateListeners) listener(update)
                    }
                }
                if (gen != clientGeneration.get()) {
                    // A restart superseded this start while the
                    // library was loading: abandon this client
                    // (never activated, holds nothing) and
                    // re-run start for the current generation
                    // instead of stranding the session.
                    starting = false
                    appContext?.let { start(it) }
                    return@Thread
                }
                client = c
                starting = false
                c.start()
                // A TDLib client created via td_create_client_id stays INACTIVE
                // until the first request is sent to it: it never emits the
                // initial updateAuthorizationState on its own. Waiting for that
                // update before sending anything deadlocks the app on the
                // splash screen (shipped in v0.1.0, hung forever at
                // "connecting"). Probe with getAuthorizationState to activate
                // the client, and feed its response through the same state
                // handling as a pushed update.
                c.send(TdClient.request("getAuthorizationState")) { response ->
                    if (gen != clientGeneration.get()) {
                        // Answer from a client a restart already replaced.
                        return@send
                    }
                    if (TdClient.isError(response)) {
                        _lastError.value =
                            response.optInt("code") to response.optString("message")
                    } else {
                        handleUpdate(
                            JSONObject()
                                .put("@type", "updateAuthorizationState")
                                .put("authorization_state", response)
                        )
                    }
                }
            } catch (t: Throwable) {
                starting = false
                _lastError.value = 0 to "TDLib failed to load"
            }
        }, "td-init").start()
    }

    private fun handleUpdate(update: JSONObject) {
        if (update.optString("@type") != "updateAuthorizationState") return
        val state = update.optJSONObject("authorization_state") ?: return
        when (state.optString("@type")) {
            "authorizationStateWaitTdlibParameters" -> {
                _authState.value = AuthState.WaitTdlibParameters
                sendParameters()
            }
            "authorizationStateWaitPhoneNumber" -> _authState.value = AuthState.WaitPhone
            "authorizationStateWaitCode" -> _authState.value = AuthState.WaitCode
            "authorizationStateWaitPassword" -> _authState.value =
                AuthState.WaitPassword(state.optString("password_hint"))
            "authorizationStateWaitRegistration" -> _authState.value = AuthState.WaitRegistration
            "authorizationStateReady" -> {
                _authState.value = AuthState.Ready
                // The saved proxy must be (re-)applied before chats
                // start loading so the first sync already uses it.
                ProxyStore.applySavedOnStart()
                // Background reception (Batch 3, C1): foreground
                // keep-alive service + message notifications.
                appContext?.let { ctx ->
                    Notifier.init(ctx)
                    // This account may have posted background
                    // notifications in a previous process
                    // generation (v0.9.10): it is the
                    // foreground now, so withdraw them — its
                    // messages follow the foreground rules
                    // from here on.
                    Notifier.sweepBackgroundForSlot(
                        ctx, AccountStore.currentSlot()
                    )
                    if (UiSettings.keepAlive.value) {
                        KeepAliveService.start(ctx)
                    }
                }
                ChatStore.onReady()
                ContactsStore.loadContacts()
                StoryStore.onReady()
                // My own profile for the settings card / edit
                // page / blocked-list self check (v0.9.3, M1).
                MeStore.refresh()
                // Background accounts online (v0.9.10): every
                // other logged-in slot gets its own client for
                // notifications + unread counts. Idempotent —
                // later Ready events find the sessions running.
                appContext?.let { ctx -> BackgroundAccounts.startAll(ctx) }
                // A background account's notification tap
                // persisted the (slot, chat) it wants opened
                // before switching to this account: land there
                // now, through DeepLink so the app-lock gate
                // still applies first.
                AccountStore.consumePendingOpen()?.let { (slot, chatId) ->
                    if (slot == AccountStore.currentSlot()) {
                        DeepLink.pendingChat.value = chatId
                    }
                }
                // Remember who this account is, for the account
                // switcher in settings (Batch 3, C6).
                sendRaw(TdClient.request("getMe")) { me ->
                    if (!TdClient.isError(me)) {
                        val name = listOf(
                            me.optString("first_name"),
                            me.optString("last_name")
                        ).filter { it.isNotEmpty() }.joinToString(" ")
                        AccountStore.setLabel(
                            AccountStore.currentSlot(),
                            name.ifEmpty { me.optString("phone_number") }
                        )
                    }
                }
            }
            "authorizationStateLoggingOut" -> _authState.value = AuthState.LoggingOut
            "authorizationStateClosed" -> _authState.value = AuthState.Closed
            else -> _authState.value = AuthState.Other(state.optString("@type"))
        }
    }

    /** The current account's TDLib database directory. Single
     *  source for the on-disk layout: sendParameters writes it,
     *  the storage page measures it. Names come from
     *  [AccountStore] — slot 0 keeps the legacy names so an
     *  existing login survives (Batch 3, C6). */
    fun databaseDir(context: Context): File =
        File(context.filesDir, AccountStore.dbDirName()).apply { mkdirs() }

    /** The current account's downloaded-files directory. */
    fun downloadDir(context: Context): File =
        File(context.filesDir, AccountStore.filesDirName()).apply { mkdirs() }

    private fun sendParameters() {
        if (!parametersSent.compareAndSet(false, true)) return
        val ctx = appContext ?: return
        val dbDir = databaseDir(ctx)
        val filesDir = downloadDir(ctx)
        val params = TdClient.request("setTdlibParameters").apply {
            put("use_test_dc", false)
            put("database_directory", dbDir.absolutePath)
            put("files_directory", filesDir.absolutePath)
            put("database_encryption_key", "")
            put("use_file_database", true)
            put("use_chat_info_database", true)
            put("use_message_database", true)
            // Secret chats are a shipped feature since v0.6.0
            // (createNewSecretChat / getSecretChat in td_api.tl);
            // with this off TDLib would not even accept incoming
            // secret-chat requests.
            put("use_secret_chats", true)
            put("api_id", ApiConfig.apiId)
            put("api_hash", ApiConfig.apiHash)
            put("system_language_code", Locale.getDefault().toLanguageTag())
            put("device_model", Build.MODEL ?: "Android")
            put("system_version", Build.VERSION.RELEASE ?: "")
            put("application_version", BuildConfig.VERSION_NAME)
        }
        send(params)
    }

    fun setPhone(phoneNumber: String) {
        send(TdClient.request("setAuthenticationPhoneNumber").apply {
            put("phone_number", phoneNumber)
        })
    }

    fun checkCode(code: String) {
        send(TdClient.request("checkAuthenticationCode").apply {
            put("code", code)
        })
    }

    fun resendCode() {
        send(TdClient.request("resendAuthenticationCode"))
    }

    fun checkPassword(password: String) {
        send(TdClient.request("checkAuthenticationPassword").apply {
            put("password", password)
        })
    }

    fun registerUser(firstName: String, lastName: String) {
        send(TdClient.request("registerUser").apply {
            put("first_name", firstName)
            put("last_name", lastName)
        })
    }

    fun logOut() {
        send(TdClient.request("logOut"))
    }

    /** Switch to another account slot (Batch 3, C6). Every store
     *  in the app is a process-wide singleton built for one
     *  account, so the only safe switch is a process restart:
     *  persist the slot, schedule a relaunch, exit. The new
     *  process reads the slot and opens that account's own
     *  database directory. */
    fun switchAccount(slot: Int) {
        val ctx = appContext ?: return
        if (slot == AccountStore.currentSlot()) return
        AccountStore.setCurrent(slot)
        restartProcess(ctx)
    }

    /** Add a new account: reserve a fresh slot and restart into
     *  its (empty) login flow. */
    fun addAccount() {
        val ctx = appContext ?: return
        val slot = AccountStore.addSlot()
        AccountStore.setCurrent(slot)
        restartProcess(ctx)
    }

    private fun restartProcess(context: Context) {
        // Account switches land here (regression fix R1,
        // v0.9.6): the outgoing account's notifications carry
        // its chat ids and must be withdrawn before the process
        // restarts into the other account.
        Notifier.clearAll(context)
        try {
            client?.close()
        } catch (t: Throwable) {
        }
        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
            val pending = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )
            val alarm = context.getSystemService(AlarmManager::class.java)
            alarm?.set(
                AlarmManager.RTC,
                System.currentTimeMillis() + 400, pending
            )
        }
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /** Drop the current client and start a fresh one (used from the
     *  Closed screen's reconnect button). */
    fun restart(context: Context) {
        clientGeneration.incrementAndGet()
        client?.close()
        client = null
        parametersSent.set(false)
        ProxyStore.resetForRestart()
        _authState.value = AuthState.WaitTdlibParameters
        start(context)
    }

    fun clearError() {
        _lastError.value = null
    }

    private fun send(query: JSONObject) {
        client?.send(query) { response ->
            if (TdClient.isError(response)) {
                _lastError.value = response.optInt("code") to response.optString("message")
            }
        }
    }
}
