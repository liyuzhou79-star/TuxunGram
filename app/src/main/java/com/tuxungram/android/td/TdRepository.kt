package com.tuxungram.android.td

import android.content.Context
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

    fun start(context: Context) {
        if (client != null) return
        appContext = context.applicationContext
        ProxyStore.init(context)
        UiSettings.init(context)
        val c = TdClient { update ->
            handleUpdate(update)
            for (listener in updateListeners) listener(update)
        }
        client = c
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
                    if (UiSettings.keepAlive.value) {
                        KeepAliveService.start(ctx)
                    }
                }
                ChatStore.onReady()
                ContactsStore.loadContacts()
            }
            "authorizationStateLoggingOut" -> _authState.value = AuthState.LoggingOut
            "authorizationStateClosed" -> _authState.value = AuthState.Closed
            else -> _authState.value = AuthState.Other(state.optString("@type"))
        }
    }

    private fun sendParameters() {
        if (!parametersSent.compareAndSet(false, true)) return
        val ctx = appContext ?: return
        val dbDir = File(ctx.filesDir, "tdlib").apply { mkdirs() }
        val filesDir = File(ctx.filesDir, "tdlib-files").apply { mkdirs() }
        val params = TdClient.request("setTdlibParameters").apply {
            put("use_test_dc", false)
            put("database_directory", dbDir.absolutePath)
            put("files_directory", filesDir.absolutePath)
            put("database_encryption_key", "")
            put("use_file_database", true)
            put("use_chat_info_database", true)
            put("use_message_database", true)
            put("use_secret_chats", false)
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

    /** Drop the current client and start a fresh one (used from the
     *  Closed screen's reconnect button). */
    fun restart(context: Context) {
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
