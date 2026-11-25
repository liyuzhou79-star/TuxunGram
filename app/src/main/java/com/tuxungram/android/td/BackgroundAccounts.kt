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
import java.util.concurrent.ConcurrentHashMap

/**
 * Simultaneous multi-account, completed (v0.9.10): every
 * logged-in slot besides the foreground one gets its OWN
 * [TdClient] (its own database/files directories, its own
 * setTdlibParameters), kept online for exactly three things —
 * authorization tracking, new-message notifications and the
 * account's total unread count.
 *
 * The isolation is structural: a background client's updates
 * are handled HERE and never forwarded to TdRepository's
 * listener fan-out, so ChatStore / MessageStore / every other
 * singleton store only ever sees the foreground account —
 * a background client cannot pollute them by construction.
 *
 * A slot whose stored session is unusable (login was never
 * finished there, or the session is gone) reports a
 * non-ready authorization state at startup; its client is
 * closed again immediately and the slot simply stays offline
 * until the user next brings it to the foreground.
 *
 * Sessions start from TdRepository's Ready branch, i.e. only
 * once the foreground account is fully up, and [startAll] is
 * idempotent — later Ready events for the same process find
 * the sessions already registered and do nothing. With only
 * one account the loop body never runs and startup is
 * exactly the pre-0.9.10 path.
 */
object BackgroundAccounts {

    private class Session(
        val slot: Int,
        val client: TdClient
    ) {
        @Volatile
        var ready: Boolean = false

        @Volatile
        var parametersSent: Boolean = false

        /** Newest incoming message id per chat, for the
         *  notification mark-read action (same role as
         *  Notifier's foreground map). */
        val lastMessageIdByChat = ConcurrentHashMap<Long, Long>()

        /** This account's own user id / username, for the
         *  muted-group mention bypass (v0.9.59, D3),
         *  resolved lazily through this session's own
         *  client. 0 / "" while unknown — and unknown means
         *  a muted chat stays silent, never the reverse
         *  (the foreground path's rule). */
        @Volatile
        var myId: Long = 0L

        @Volatile
        var myUsername: String = ""
    }

    private val sessions = ConcurrentHashMap<Int, Session>()

    private val _unreadBySlot = MutableStateFlow<Map<Int, Int>>(emptyMap())

    /** Total main-list unread count per background slot,
     *  driven by TDLib's updateUnreadMessageCount. Slots that
     *  are offline (or never started) are simply absent. */
    val unreadBySlot: StateFlow<Map<Int, Int>> = _unreadBySlot

    private val _onlineBackground = MutableStateFlow(0)

    /** How many background slots are fully online right
     *  now (their session reached authorizationStateReady).
     *  The settings account section shows the total so the
     *  memory cost of simultaneous online is stated in the
     *  open, not just implied (v0.9.40, G3). */
    val onlineBackgroundCount: StateFlow<Int> = _onlineBackground

    private fun refreshOnlineCount() {
        _onlineBackground.value =
            sessions.values.count { it.ready }
    }

    @Volatile
    private var appContext: Context? = null

    /** Start a background client for every slot except the
     *  foreground one. Safe to call on every foreground
     *  Ready; slots that already have a session are skipped. */
    fun startAll(context: Context) {
        appContext = context.applicationContext
        val current = AccountStore.currentSlot()
        for (slot in AccountStore.slots.value) {
            if (slot == current) continue
            if (sessions.containsKey(slot)) continue
            startSession(slot)
        }
    }

    private fun startSession(slot: Int) {
        // Construction on its own thread: TdClient's
        // constructor allocates a native client id, and the
        // caller here is the receiver thread, which must
        // never block (it feeds every client, foreground
        // included).
        Thread({
            val client = TdClient { update -> onUpdate(slot, update) }
            val session = Session(slot, client)
            if (sessions.putIfAbsent(slot, session) != null) {
                // Lost a race with another startAll: this
                // client was never started, holds nothing.
                return@Thread
            }
            client.start()
            // Same activation rule as the foreground client:
            // a client created via td_create_client_id stays
            // inactive until the first request is sent to it,
            // so probe with getAuthorizationState and feed the
            // answer through the normal update path.
            client.send(TdClient.request("getAuthorizationState")) { response ->
                if (!TdClient.isError(response)) {
                    onUpdate(
                        slot,
                        JSONObject()
                            .put("@type", "updateAuthorizationState")
                            .put("authorization_state", response)
                    )
                }
            }
        }, "td-bg-$slot").start()
    }

    private fun onUpdate(slot: Int, update: JSONObject) {
        // A dropped session's client is closed in
        // dropSession, and TdClient.deliver drops every
        // event for a closed client before it can reach
        // this lambda — so a session found here is the
        // live one for the slot.
        val session = sessions[slot] ?: return
        when (update.optString("@type")) {
            "updateAuthorizationState" -> {
                val state = update.optJSONObject("authorization_state")
                    ?: return
                onAuthState(session, state.optString("@type"))
            }
            "updateUnreadMessageCount" -> {
                val listType = update.optJSONObject("chat_list")
                    ?.optString("@type")
                if (listType == "chatListMain") {
                    _unreadBySlot.value = _unreadBySlot.value +
                        (slot to update.optInt("unread_count"))
                }
            }
            "updateNewMessage" -> onNewMessage(session, update)
        }
    }

    private fun onAuthState(session: Session, stateType: String) {
        when (stateType) {
            "authorizationStateWaitTdlibParameters" -> {
                if (!session.parametersSent) {
                    session.parametersSent = true
                    sendParameters(session)
                }
            }
            "authorizationStateReady" -> {
                session.ready = true
                refreshOnlineCount()
                // The proxy list is app-global by design; a
                // background client on this network needs the
                // same active proxy as the foreground one or
                // it will never reach a DC.
                ProxyStore.activeProxyQuery()?.let { query ->
                    session.client.send(query) {}
                }
                // Storage optimizer (v0.9.42, K2): the option
                // lives in each account's own database, so the
                // device-level choice is re-asserted here too —
                // a background database otherwise sits at
                // TDLib's built-in default (off) forever while
                // its files pile up from notification traffic.
                val optimizer = TdClient.request("setOption").apply {
                    put("name", "use_storage_optimizer")
                    put(
                        "value",
                        JSONObject()
                            .put("@type", "optionValueBoolean")
                            .put(
                                "value",
                                UiSettings.storageOptimizer.value
                            )
                    )
                }
                session.client.send(optimizer) {}
                // Kick the main chat list so TDLib syncs the
                // account and starts pushing unread totals.
                loadChatsChain(session, 0)
            }
            // Every other state means this slot has no usable
            // session right now: login was never finished in
            // it (WaitPhoneNumber / WaitCode / WaitPassword /
            // WaitRegistration), it is logging out, or its
            // session is gone (Closed). There is nothing to
            // keep online — close and forget the session; the
            // slot comes back here the next time it has been
            // logged in in the foreground.
            else -> dropSession(session)
        }
    }

    private fun dropSession(session: Session) {
        if (sessions.remove(session.slot, session)) {
            _unreadBySlot.value = _unreadBySlot.value - session.slot
            refreshOnlineCount()
            try {
                session.client.close()
            } catch (t: Throwable) {
            }
        }
    }

    /** loadChats until TDLib answers 404 (its "list
     *  complete" signal) — the same exhaustion rule the
     *  foreground chat list uses, so the unread total
     *  converges to the account's real value instead of the
     *  first page's. The iteration cap is only a stall
     *  guard for a client that keeps answering Ok without
     *  progress. */
    private fun loadChatsChain(session: Session, iteration: Int) {
        if (iteration > 50) return
        if (sessions[session.slot] !== session) return
        val query = TdClient.request("loadChats").apply {
            put(
                "chat_list",
                JSONObject().put("@type", "chatListMain")
            )
            put("limit", 100)
        }
        session.client.send(query) { response ->
            if (!TdClient.isError(response)) {
                loadChatsChain(session, iteration + 1)
            }
        }
    }

    private fun sendParameters(session: Session) {
        val ctx = appContext ?: return
        val dbDir = File(
            ctx.filesDir, AccountStore.dbDirName(session.slot)
        ).apply { mkdirs() }
        val filesDir = File(
            ctx.filesDir, AccountStore.filesDirName(session.slot)
        ).apply { mkdirs() }
        // Same parameter set as the foreground client
        // (TdRepository.sendParameters) — including secret
        // chats, so an incoming secret chat on a background
        // account is accepted and notified like any message.
        val params = TdClient.request("setTdlibParameters").apply {
            put("use_test_dc", false)
            put("database_directory", dbDir.absolutePath)
            put("files_directory", filesDir.absolutePath)
            put("database_encryption_key", "")
            put("use_file_database", true)
            put("use_chat_info_database", true)
            put("use_message_database", true)
            put("use_secret_chats", true)
            put("api_id", ApiConfig.apiId)
            put("api_hash", ApiConfig.apiHash)
            put("system_language_code", Locale.getDefault().toLanguageTag())
            put("device_model", Build.MODEL ?: "Android")
            put("system_version", Build.VERSION.RELEASE ?: "")
            put("application_version", BuildConfig.VERSION_NAME)
        }
        session.client.send(params) {}
    }

    /** Resolve this account's own identity through its own
     *  client (v0.9.59, D3), mirroring Notifier.ensureMe on
     *  the foreground path. Lazy: the first muted message
     *  pays the round trip and, exactly like the foreground,
     *  stays silent while the identity is still unknown. */
    private fun ensureMe(session: Session) {
        if (session.myId != 0L) return
        session.client.send(TdClient.request("getMe")) { me ->
            if (TdClient.isError(me)) return@send
            session.myId = me.optLong("id")
            val direct = me.optString("username")
            session.myUsername = if (direct.isNotEmpty()) {
                direct
            } else {
                me.optJSONObject("usernames")
                    ?.optJSONArray("active_usernames")
                    ?.optString(0) ?: ""
            }
        }
    }

    private fun onNewMessage(session: Session, update: JSONObject) {
        // The same master switch as the foreground: the
        // settings 通知 toggle governs background reception
        // as a whole, for every account.
        if (!UiSettings.keepAlive.value) return
        val ctx = appContext ?: return
        val message = update.optJSONObject("message") ?: return
        if (message.optBoolean("is_outgoing")) return
        val chatId = message.optLong("chat_id")
        session.lastMessageIdByChat[chatId] = message.optLong("id")
        val summary = Notifier.summaryOf(message.optJSONObject("content"))
        // Title + mute state come from THIS account's own
        // client (its getChat) — the foreground ChatStore
        // knows nothing about this account's chats. The mute
        // rule mirrors ChatStore's: the chat object's
        // resolved mute_for > 0 means muted.
        val query = TdClient.request("getChat").apply {
            put("chat_id", chatId)
        }
        session.client.send(query) { chat ->
            if (TdClient.isError(chat)) return@send
            val muteFor = chat.optJSONObject("notification_settings")
                ?.optInt("mute_for", 0) ?: 0
            if (muteFor > 0) {
                // Muted chats stay silent — EXCEPT a group
                // message that @-mentions this account (the
                // official rule). The foreground path has
                // applied it since v0.9.43 (M3); this path
                // recorded it as a known difference then,
                // and v0.9.59 (D3) closes it: the mention is
                // checked against THIS account's own identity
                // (see ensureMe). Channels and private chats
                // have no mention bypass.
                val type = chat.optJSONObject("type")
                val isGroup = when (type?.optString("@type")) {
                    "chatTypeBasicGroup" -> true
                    "chatTypeSupergroup" ->
                        type.optBoolean("is_channel") != true
                    else -> false
                }
                ensureMe(session)
                val mentionBypass = isGroup && Notifier.mentionsMe(
                    message.optJSONObject("content"),
                    session.myId, session.myUsername
                )
                if (!mentionBypass) return@send
            }
            val label = AccountStore.labels.value[session.slot]
                ?: "账号 ${session.slot + 1}"
            // Category channel + content hiding (v0.9.43,
            // M1/M3): the same rules as the foreground path —
            // secret chats and a locked app never put message
            // bodies on the lock screen, whichever account the
            // message belongs to.
            val secret = chat.optJSONObject("type")
                ?.optString("@type") == "chatTypeSecret"
            val locked = AppLock.pinSet.value && AppLock.locked.value
            Notifier.postBackground(
                ctx, session.slot, label, chatId,
                if (locked) {
                    "TuxunGram"
                } else {
                    chat.optString("title")
                },
                if (locked || secret) "你有一条新消息" else summary,
                KeepAliveService.channelForChatType(chat)
            )
        }
    }

    /** Send through a background slot's own client (the
     *  notification reply / mark-read route). When the slot
     *  has no live Ready client — e.g. the process was woken
     *  only to deliver the action broadcast — the callback
     *  receives a synthetic error so the caller's failure
     *  path runs honestly instead of the send vanishing. */
    fun sendRaw(
        slot: Int,
        query: JSONObject,
        callback: (JSONObject) -> Unit
    ) {
        val session = sessions[slot]
        if (session == null || !session.ready) {
            callback(
                JSONObject()
                    .put("@type", "error")
                    .put("code", 0)
                    .put("message", "background client not ready")
            )
            return
        }
        session.client.send(query, callback)
    }

    /** Newest known incoming message id for a chat on a
     *  background slot (mark-read bookkeeping). */
    fun lastMessageId(slot: Int, chatId: Long): Long? =
        sessions[slot]?.lastMessageIdByChat?.get(chatId)

    /** True while the slot has a live, authorized client. */
    fun isOnline(slot: Int): Boolean = sessions[slot]?.ready == true
}
