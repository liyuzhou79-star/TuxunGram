package com.tuxungram.android.td

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.tuxungram.android.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/** Where a notification tap wants the UI to go. MainActivity
 *  translates its launch intent into this flow; ReadyHost
 *  consumes it and opens the chat. */
object DeepLink {
    val pendingChat = MutableStateFlow<Long?>(null)

    /** A mentionName tap in rich text (v0.9.2, L1): the user id
     *  whose profile page should open. ReadyHost resolves it
     *  through createPrivateChat, the same route contacts use. */
    val pendingProfileUser = MutableStateFlow<Long?>(null)

    /** A plain @mention tap (v0.9.2, L1): the username (without
     *  the @) whose profile page should open, resolved through
     *  searchPublicChat. */
    val pendingProfileName = MutableStateFlow<String?>(null)

    /** A t.me/proxy or t.me/socks link tap (v0.9.9, W3): the
     *  host should show the Settings tab, where the proxy
     *  editor opens prefilled from ProxyStore.pendingPrefill. */
    val pendingOpenSettings = MutableStateFlow(false)

    /** An Instant View open request (v0.9.26, S1): the URL
     *  whose article page ReadyHost should overlay. Set by
     *  link preview cards and the message menu; cleared when
     *  the page closes. */
    val pendingInstantView = MutableStateFlow<String?>(null)
}

/**
 * Foreground service whose only job is to keep the process —
 * and with it the TDLib receiver thread — alive while the app
 * sits in the background, so new messages keep arriving and
 * [Notifier] can post system notifications for them.
 *
 * Honest limit (also stated in the settings UI): on aggressive
 * ROMs (Flyme and friends) the system can still freeze or kill
 * the process when the user swipes the app away; only the
 * system's own autostart / battery settings can prevent that,
 * no app-side trick can.
 */
class KeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
        val notif = NotificationCompat.Builder(this, CHANNEL_KEEPALIVE)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("TuxunGram")
            .setContentText("正在后台接收消息")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    companion object {
        const val CHANNEL_KEEPALIVE = "keepalive"

        /** Legacy single message channel (pre-v0.9.43). Deleted
         *  on first channel creation after the upgrade: every
         *  message notification now lands on one of the four
         *  category channels below. Deleting also cancels any
         *  still-showing notifications posted on it — acceptable
         *  for a migration (the chats themselves keep their
         *  unread state in the list). */
        const val CHANNEL_MESSAGES = "messages"

        // Message category channels (v0.9.43, M1): private /
        // group / channel / other, each independently tunable
        // in system settings (sound, vibration, importance).
        // The four are SHARED by all accounts on purpose: a
        // per-account × per-category matrix would flood the
        // system settings page (accounts × 4 channels), and
        // the owning account is already named on each
        // notification itself (sub-text + separate group).
        const val CHANNEL_PRIVATE = "messages_private"
        const val CHANNEL_GROUPS = "messages_groups"
        const val CHANNEL_CHANNELS = "messages_channels"
        const val CHANNEL_OTHER = "messages_other"
        private const val NOTIF_ID = 1

        /** Category channel for a cached chat row. Secret
         *  chats ride OTHER (their bodies are hidden anyway),
         *  unknown rows fall back to OTHER as well. */
        fun channelFor(item: ChatItem?): String = when {
            item == null -> CHANNEL_OTHER
            item.isSecret -> CHANNEL_OTHER
            item.isChannel -> CHANNEL_CHANNELS
            item.isGroup -> CHANNEL_GROUPS
            else -> CHANNEL_PRIVATE
        }

        /** Category channel from a raw getChat response (the
         *  background-account path and the uncached-chat path,
         *  where no ChatItem exists). */
        fun channelForChatType(chat: JSONObject): String =
            when (chat.optJSONObject("type")?.optString("@type")) {
                "chatTypePrivate" -> CHANNEL_PRIVATE
                "chatTypeBasicGroup" -> CHANNEL_GROUPS
                "chatTypeSupergroup" ->
                    if (chat.optJSONObject("type")
                            ?.optBoolean("is_channel") == true
                    ) {
                        CHANNEL_CHANNELS
                    } else {
                        CHANNEL_GROUPS
                    }
                else -> CHANNEL_OTHER
            }

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
                ?: return
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_KEEPALIVE, "后台接收",
                    NotificationManager.IMPORTANCE_MIN
                )
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PRIVATE, "私聊消息",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_GROUPS, "群组消息",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_CHANNELS, "频道消息",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_OTHER, "其他消息",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
            // Migration (v0.9.43, M1): retire the legacy
            // single channel now that the categories exist.
            try {
                nm.deleteNotificationChannel(CHANNEL_MESSAGES)
            } catch (t: Throwable) {
            }
        }

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context, Intent(context, KeepAliveService::class.java)
                )
            } catch (t: Throwable) {
                // Some ROMs refuse foreground-service starts from
                // the background; the in-process receiver still
                // works while the process lives.
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, KeepAliveService::class.java))
            } catch (t: Throwable) {
            }
        }
    }
}

/**
 * Turns TDLib's updateNewMessage stream into system
 * notifications. Skips outgoing messages, the chat currently
 * on screen, and muted chats. Also keeps TDLib informed about
 * the network type (setNetworkType, verified in td_api.tl) so
 * it reconnects promptly when the network changes instead of
 * looking dead.
 */
object Notifier {
    /** Chat currently open on screen; set by the chat screen. */
    @Volatile
    var foregroundChatId: Long = 0L

    @Volatile
    private var inited = false

    private var appContext: Context? = null

    // ---- Notification actions + grouping (v0.7.0, F4) ----
    // All message notifications share one group with a summary
    // notification, so several busy chats stack instead of
    // flooding the shade. Each chat's notification carries a
    // RemoteInput reply action and a mark-read action, handled
    // by NotificationActionReceiver below.
    const val ACTION_REPLY = "com.tuxungram.android.action.NOTIF_REPLY"
    const val ACTION_MARK_READ = "com.tuxungram.android.action.NOTIF_READ"
    const val KEY_REPLY = "reply_text"
    private const val GROUP_KEY = "tuxungram.messages"
    private const val SUMMARY_ID = 2

    /** Latest (title, text) line per chat, for the summary. */
    private val linesByChat =
        java.util.concurrent.ConcurrentHashMap<Long, Pair<String, String>>()

    /** Recent message lines per chat (v0.9.49, T6): a chat
     *  whose notification is updated by several new messages
     *  shows them stacked (newest last, capped at 5) instead
     *  of only ever showing the newest one. Cleared wherever
     *  linesByChat is cleared. */
    private val recentLinesByChat =
        java.util.concurrent.ConcurrentHashMap<
            Long, java.util.ArrayDeque<String>>()

    /** Newest incoming message id per chat: viewMessages on it
     *  advances the chat's read position (mark-read action). */
    private val lastMessageIdByChat =
        java.util.concurrent.ConcurrentHashMap<Long, Long>()

    fun init(context: Context) {
        if (inited) return
        inited = true
        appContext = context.applicationContext
        KeepAliveService.createChannels(context)
        TdRepository.addUpdateListener { update ->
            if (update.optString("@type") == "updateNewMessage") {
                onNewMessage(update)
            }
        }
        registerNetworkCallback(context)
    }

    // ---- Mention detection for muted chats (v0.9.43, M3) ----
    // A muted GROUP still notifies when the message @-mentions
    // me — the official rule. Detection is entity-based only:
    // a textEntityTypeMentionName carrying my user id, or a
    // plain textEntityTypeMention whose text is my @username.
    // My id/username are resolved lazily via getMe; while
    // unknown, a muted chat stays silent (never the reverse).
    @Volatile
    private var myId = 0L

    @Volatile
    private var myUsername = ""

    private fun ensureMe() {
        if (myId != 0L) return
        TdRepository.sendRaw(TdClient.request("getMe")) { me ->
            if (TdClient.isError(me)) return@sendRaw
            myId = me.optLong("id")
            val direct = me.optString("username")
            myUsername = if (direct.isNotEmpty()) {
                direct
            } else {
                me.optJSONObject("usernames")
                    ?.optJSONArray("active_usernames")
                    ?.optString(0) ?: ""
            }
        }
    }

    private fun mentionsMe(content: JSONObject?): Boolean {
        if (content == null || myId == 0L) return false
        val texts = ArrayList<JSONObject>(2)
        content.optJSONObject("text")?.let { texts.add(it) }
        content.optJSONObject("caption")?.let { texts.add(it) }
        for (formatted in texts) {
            val body = formatted.optString("text")
            val entities = formatted.optJSONArray("entities")
                ?: continue
            for (i in 0 until entities.length()) {
                val type = entities.optJSONObject(i)
                    ?.optJSONObject("type") ?: continue
                when (type.optString("@type")) {
                    "textEntityTypeMentionName" ->
                        if (type.optLong("user_id") == myId) {
                            return true
                        }
                    "textEntityTypeMention" -> {
                        if (myUsername.isEmpty()) continue
                        val entity = entities.optJSONObject(i)
                            ?: continue
                        val start = entity.optInt("offset")
                        val length = entity.optInt("length")
                        if (start >= 0 && length > 0 &&
                            start + length <= body.length &&
                            body.substring(start, start + length)
                                .equals(
                                    "@$myUsername",
                                    ignoreCase = true
                                )
                        ) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    /** Generic body for notifications whose content must not
     *  leak onto the lock screen: secret chats, and anything
     *  arriving while the app lock is engaged (v0.9.43, M3). */
    private const val HIDDEN_TEXT = "你有一条新消息"

    private fun onNewMessage(update: JSONObject) {
        if (!UiSettings.keepAlive.value) return
        val ctx = appContext ?: return
        val message = update.optJSONObject("message") ?: return
        if (message.optBoolean("is_outgoing")) return
        val chatId = message.optLong("chat_id")
        if (chatId == foregroundChatId) return
        val item = ChatStore.itemOf(chatId)
        val content = message.optJSONObject("content")
        if (item?.isMuted == true) {
            // Muted chats stay silent — EXCEPT a group message
            // that @-mentions me (official rule, M3). Channels
            // and private chats have no mention bypass.
            ensureMe()
            val mentionBypass = item.isGroup &&
                !item.isChannel && mentionsMe(content)
            if (!mentionBypass) return
        }
        lastMessageIdByChat[chatId] = message.optLong("id")
        val locked = AppLock.pinSet.value && AppLock.locked.value
        val hideBody = locked || item?.isSecret == true
        val summary = if (hideBody) {
            HIDDEN_TEXT
        } else {
            summaryOf(content)
        }
        val title = item?.title
        if (title != null) {
            post(
                ctx, chatId, if (locked) "TuxunGram" else title,
                summary, KeepAliveService.channelFor(item)
            )
        } else {
            // Chat not in the local list cache yet (brand-new
            // chat): resolve its title first, then post.
            val query = TdClient.request("getChat").apply {
                put("chat_id", chatId)
            }
            TdRepository.sendRaw(query) { chat ->
                if (!TdClient.isError(chat)) {
                    val secret = chat.optJSONObject("type")
                        ?.optString("@type") == "chatTypeSecret"
                    post(
                        ctx, chatId,
                        if (locked) {
                            "TuxunGram"
                        } else {
                            chat.optString("title")
                        },
                        if (locked || secret) {
                            HIDDEN_TEXT
                        } else {
                            summary
                        },
                        KeepAliveService.channelForChatType(chat)
                    )
                }
            }
        }
    }

    /** One-line notification text for a message content
     *  object. Shared with BackgroundAccounts (v0.9.10),
     *  which summarizes background accounts' messages the
     *  exact same way. */
    fun summaryOf(content: JSONObject?): String {
        if (content == null) return "新消息"
        return when (content.optString("@type")) {
            "messageText" -> content.optJSONObject("text")
                ?.optString("text")?.take(100) ?: "新消息"
            "messagePhoto" -> content.optJSONObject("caption")
                ?.optString("text")?.ifEmpty { "[图片]" } ?: "[图片]"
            "messageVideo" -> "[视频]"
            "messageVoiceNote" -> "[语音]"
            "messageVideoNote" -> "[视频消息]"
            "messageDocument" -> "[文件]"
            "messageSticker" -> "[贴纸]"
            "messageAnimation" -> "[动图]"
            "messagePoll" -> "[投票]"
            "messageDice" -> "[骰子]"
            "messageGame" -> "[游戏]"
            else -> "新消息"
        }
    }

    private fun openChatIntent(ctx: Context, chatId: Long): Intent =
        Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_chat_id", chatId)
        }

    /** Channel of the most recently posted foreground
     *  notification; the group summary rides the same channel
     *  so its alert behaviour matches the newest message. */
    @Volatile
    private var lastChannel: String = KeepAliveService.CHANNEL_PRIVATE

    private fun post(
        ctx: Context,
        chatId: Long,
        title: String,
        text: String,
        channel: String
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val pending = PendingIntent.getActivity(
            ctx, chatId.hashCode(), openChatIntent(ctx, chatId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Reply action: RemoteInput needs a MUTABLE pending
        // intent so the typed text can ride back in it.
        val replyIntent = Intent(ctx, NotificationActionReceiver::class.java)
            .setAction(ACTION_REPLY)
            .putExtra("chat_id", chatId)
        val replyPending = PendingIntent.getBroadcast(
            ctx, chatId.hashCode() * 2 + 1, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send, "回复", replyPending
        ).addRemoteInput(
            RemoteInput.Builder(KEY_REPLY).setLabel("回复…").build()
        ).build()
        val readIntent = Intent(ctx, NotificationActionReceiver::class.java)
            .setAction(ACTION_MARK_READ)
            .putExtra("chat_id", chatId)
        val readPending = PendingIntent.getBroadcast(
            ctx, chatId.hashCode() * 2 + 2, readIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val readAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view, "标记已读", readPending
        ).build()
        // Per-chat line stack (v0.9.49, T6): same notification
        // id as before, so a new message UPDATES this chat's
        // notification instead of stacking shade entries; with
        // more than one unseen line the body becomes an inbox
        // of the recent lines rather than just the newest.
        val recent = recentLinesByChat.getOrPut(chatId) {
            java.util.ArrayDeque()
        }
        val recentSnapshot = synchronized(recent) {
            recent.addLast(text)
            while (recent.size > 5) recent.removeFirst()
            recent.toList()
        }
        val style = if (recentSnapshot.size > 1) {
            NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
                .setSummaryText("共 ${recentSnapshot.size} 条新消息")
                .also { s ->
                    recentSnapshot.forEach { s.addLine(it) }
                }
        } else {
            NotificationCompat.BigTextStyle().bigText(text)
        }
        val notif = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_KEY)
            .addAction(replyAction)
            .addAction(readAction)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(chatId.hashCode(), notif)
        } catch (t: Throwable) {
        }
        lastChannel = channel
        linesByChat[chatId] = title to text
        postSummary(ctx)
    }

    /** The group summary: one line per chat with unread
     *  notifications; cancelled when the last one goes away. */
    private fun postSummary(ctx: Context) {
        val nm = NotificationManagerCompat.from(ctx)
        if (linesByChat.isEmpty()) {
            try {
                nm.cancel(SUMMARY_ID)
            } catch (t: Throwable) {
            }
            return
        }
        val style = NotificationCompat.InboxStyle()
            .setSummaryText("共 ${linesByChat.size} 个对话有新消息")
        for ((_, line) in linesByChat.entries.take(5)) {
            style.addLine("${line.first}：${line.second}")
        }
        val summary = NotificationCompat.Builder(
            ctx, lastChannel
        )
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("TuxunGram")
            .setContentText("${linesByChat.size} 个对话有新消息")
            .setStyle(style)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(SUMMARY_ID, summary)
        } catch (t: Throwable) {
        }
    }

    /** Withdraw EVERY message notification (regression fix
     *  R1, v0.9.6): an account switch restarts the process into
     *  the other account, and the outgoing account's
     *  notifications must not survive it — their tap intents
     *  and reply/mark-read actions carry chat ids that belong
     *  to the other account and would open (or send into) the
     *  wrong conversation under the new account's client. */
    fun clearAll(ctx: Context) {
        val nm = NotificationManagerCompat.from(ctx)
        val ids = HashSet<Long>()
        ids.addAll(linesByChat.keys)
        ids.addAll(lastMessageIdByChat.keys)
        for (chatId in ids) {
            try {
                nm.cancel(chatId.hashCode())
            } catch (t: Throwable) {
            }
        }
        try {
            nm.cancel(SUMMARY_ID)
        } catch (t: Throwable) {
        }
        linesByChat.clear()
        recentLinesByChat.clear()
        lastMessageIdByChat.clear()
        // Background accounts (v0.9.10): the same rule
        // extended to every slot — an account switch restarts
        // the process, and NO account's notifications may
        // survive into another account's session.
        for ((_, id) in registeredBgIds(ctx)) {
            try {
                nm.cancel(id)
            } catch (t: Throwable) {
            }
        }
        ctx.getSharedPreferences(BG_PREFS, Context.MODE_PRIVATE)
            .edit().remove(BG_KEY_IDS).apply()
    }

    /** Withdraw a chat's notification (it was read in the app,
     *  marked read, or replied to) and refresh the summary. */
    fun dismissForChat(ctx: Context, chatId: Long) {
        linesByChat.remove(chatId)
        recentLinesByChat.remove(chatId)
        try {
            NotificationManagerCompat.from(ctx).cancel(chatId.hashCode())
        } catch (t: Throwable) {
        }
        postSummary(ctx)
    }

    private fun viewMessagesQuery(chatId: Long, messageId: Long): JSONObject =
        TdClient.request("viewMessages").apply {
            put("chat_id", chatId)
            put(
                "message_ids",
                org.json.JSONArray().put(messageId)
            )
            put(
                "source",
                JSONObject().put("@type", "messageSourceChatHistory")
            )
            put("force_read", true)
        }

    private fun sendTextQuery(chatId: Long, text: String): JSONObject =
        TdClient.request("sendMessage").apply {
            put("chat_id", chatId)
            put("input_message_content", JSONObject().apply {
                put("@type", "inputMessageText")
                put("text", JSONObject().apply {
                    put("@type", "formattedText")
                    put("text", text)
                    put("entities", org.json.JSONArray())
                })
                put("link_preview_options", JSONObject.NULL)
                put("clear_draft", false)
            })
        }

    /** Mark-read action: viewMessages on the newest known
     *  message advances the read position (same call the chat
     *  screen uses), then the notification goes away. */
    fun markReadFromNotification(ctx: Context, chatId: Long) {
        val messageId = lastMessageIdByChat[chatId]
        if (messageId != null && messageId != 0L) {
            TdRepository.sendRaw(viewMessagesQuery(chatId, messageId)) {}
        }
        dismissForChat(ctx, chatId)
    }

    /** Reply action: send the typed text straight through
     *  TDLib without opening the app. When the client is not
     *  ready (cold process) or the send fails, an honest
     *  fallback notification says so instead of pretending. */
    fun replyFromNotification(ctx: Context, chatId: Long, text: String) {
        if (TdRepository.authState.value !is AuthState.Ready) {
            postReplyFailure(ctx, chatId)
            return
        }
        val query = sendTextQuery(chatId, text)
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                postReplyFailure(ctx, chatId)
            } else {
                // Sent: the conversation is read up to the
                // message that triggered the notification.
                markReadFromNotification(ctx, chatId)
            }
        }
    }

    private fun postReplyFailure(ctx: Context, chatId: Long) {
        val pending = PendingIntent.getActivity(
            ctx, chatId.hashCode(), openChatIntent(ctx, chatId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(
            ctx, KeepAliveService.channelFor(ChatStore.itemOf(chatId))
        )
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("回复没有发出去")
            .setContentText("刚才那条回复发送失败，点开对话再试一次。")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(ctx)
                .notify(chatId.hashCode() xor 0x77, notif)
        } catch (t: Throwable) {
        }
    }

    // ---- Background-account notifications (v0.9.10) ----
    // Posted by BackgroundAccounts for every account that is
    // online in the background. Ids derive from (slot,
    // chatId) in a high range the foreground scheme (raw
    // chatId.hashCode()) is vanishingly unlikely to reach —
    // the same accepted-collision argument as audit K4.
    // Every posted id is registered in SharedPreferences,
    // because it must stay cancellable by a LATER process
    // generation: when the account becomes the foreground
    // one, TdRepository sweeps its registered ids at Ready;
    // an account switch cancels all of them via clearAll.
    // In-memory maps alone cannot do either across the
    // restart-based switch.
    private const val BG_PREFS = "bg_notifications"
    private const val BG_KEY_IDS = "ids"
    private const val BG_GROUP_PREFIX = "tuxungram.messages.acc"

    private fun bgNotifId(slot: Int, chatId: Long): Int =
        1_000_000 + slot * 100_003 + (chatId.hashCode() and 0xFFFF)

    private fun bgFailureId(slot: Int, chatId: Long): Int =
        bgNotifId(slot, chatId) xor 0x5A5A

    private fun registerBgId(ctx: Context, slot: Int, notifId: Int) {
        val prefs = ctx.getSharedPreferences(BG_PREFS, Context.MODE_PRIVATE)
        val ids = HashSet(
            prefs.getStringSet(BG_KEY_IDS, emptySet()) ?: emptySet()
        )
        if (ids.add("$slot:$notifId")) {
            prefs.edit().putStringSet(BG_KEY_IDS, ids).apply()
        }
    }

    private fun registeredBgIds(ctx: Context): List<Pair<Int, Int>> {
        val prefs = ctx.getSharedPreferences(BG_PREFS, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(BG_KEY_IDS, emptySet()) ?: emptySet()
        return ids.mapNotNull { entry ->
            val parts = entry.split(":")
            val slot = parts.getOrNull(0)?.toIntOrNull()
            val id = parts.getOrNull(1)?.toIntOrNull()
            if (slot != null && id != null) slot to id else null
        }
    }

    private fun removeBgRegistration(
        ctx: Context,
        slot: Int,
        ids: Set<Int>
    ) {
        if (ids.isEmpty()) return
        val prefs = ctx.getSharedPreferences(BG_PREFS, Context.MODE_PRIVATE)
        val current = HashSet(
            prefs.getStringSet(BG_KEY_IDS, emptySet()) ?: emptySet()
        )
        val removed = current.removeAll { entry ->
            val parts = entry.split(":")
            parts.getOrNull(0)?.toIntOrNull() == slot &&
                parts.getOrNull(1)?.toIntOrNull() in ids
        }
        if (removed) {
            prefs.edit().putStringSet(BG_KEY_IDS, current).apply()
        }
    }

    /** Post one background account's new-message
     *  notification. The account label rides along as the
     *  sub-text and in the per-account group, so the shade
     *  always says WHICH account a message belongs to. The
     *  tap intent carries the slot: MainActivity switches to
     *  that account before the chat opens (see
     *  AccountStore.setPendingOpen). Reply and mark-read
     *  carry the slot too and are routed to that account's
     *  own client by the receiver below. */
    fun postBackground(
        ctx: Context,
        slot: Int,
        label: String,
        chatId: Long,
        title: String,
        text: String,
        channel: String = KeepAliveService.CHANNEL_OTHER
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val id = bgNotifId(slot, chatId)
        val tapIntent = openChatIntent(ctx, chatId).apply {
            putExtra("open_chat_slot", slot)
        }
        val pending = PendingIntent.getActivity(
            ctx, id, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val replyIntent = Intent(ctx, NotificationActionReceiver::class.java)
            .setAction(ACTION_REPLY)
            .putExtra("chat_id", chatId)
            .putExtra("slot", slot)
        val replyPending = PendingIntent.getBroadcast(
            ctx, id * 2 + 1, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send, "回复", replyPending
        ).addRemoteInput(
            RemoteInput.Builder(KEY_REPLY).setLabel("回复…").build()
        ).build()
        val readIntent = Intent(ctx, NotificationActionReceiver::class.java)
            .setAction(ACTION_MARK_READ)
            .putExtra("chat_id", chatId)
            .putExtra("slot", slot)
        val readPending = PendingIntent.getBroadcast(
            ctx, id * 2 + 2, readIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val readAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view, "标记已读", readPending
        ).build()
        val notif = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(label)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(BG_GROUP_PREFIX + slot)
            .addAction(replyAction)
            .addAction(readAction)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, notif)
        } catch (t: Throwable) {
        }
        registerBgId(ctx, slot, id)
    }

    /** Withdraw one background chat's notifications (it was
     *  marked read or replied to). */
    fun dismissBackground(ctx: Context, slot: Int, chatId: Long) {
        val nm = NotificationManagerCompat.from(ctx)
        val ids = setOf(bgNotifId(slot, chatId), bgFailureId(slot, chatId))
        for (id in ids) {
            try {
                nm.cancel(id)
            } catch (t: Throwable) {
            }
        }
        removeBgRegistration(ctx, slot, ids)
    }

    /** Withdraw EVERY notification a slot posted while it
     *  was a background account. Called when that account
     *  becomes the foreground one: from here on its messages
     *  are the foreground's business, and the foreground
     *  notification rules (open-chat suppression, the shared
     *  summary) take over. */
    fun sweepBackgroundForSlot(ctx: Context, slot: Int) {
        val nm = NotificationManagerCompat.from(ctx)
        val mine = registeredBgIds(ctx).filter { it.first == slot }
        for ((_, id) in mine) {
            try {
                nm.cancel(id)
            } catch (t: Throwable) {
            }
        }
        removeBgRegistration(ctx, slot, mine.map { it.second }.toSet())
    }

    /** Background mark-read: viewMessages through the
     *  OWNING account's client, never the foreground one. */
    fun markReadFromBackground(ctx: Context, slot: Int, chatId: Long) {
        val messageId = BackgroundAccounts.lastMessageId(slot, chatId)
        if (messageId != null && messageId != 0L) {
            BackgroundAccounts.sendRaw(
                slot, viewMessagesQuery(chatId, messageId)
            ) {}
        }
        dismissBackground(ctx, slot, chatId)
    }

    /** Background reply: the text is sent by the OWNING
     *  account's background client. When that client is not
     *  live (the process was woken only for this broadcast),
     *  BackgroundAccounts answers with an error and the
     *  honest failure notice is posted — the same contract
     *  the foreground reply has always had. */
    fun replyFromBackground(ctx: Context, slot: Int, chatId: Long, text: String) {
        BackgroundAccounts.sendRaw(slot, sendTextQuery(chatId, text)) { response ->
            if (TdClient.isError(response)) {
                postBackgroundReplyFailure(ctx, slot, chatId)
            } else {
                markReadFromBackground(ctx, slot, chatId)
            }
        }
    }

    private fun postBackgroundReplyFailure(ctx: Context, slot: Int, chatId: Long) {
        val tapIntent = openChatIntent(ctx, chatId).apply {
            putExtra("open_chat_slot", slot)
        }
        val pending = PendingIntent.getActivity(
            ctx, bgFailureId(slot, chatId), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val label = AccountStore.labels.value[slot] ?: "账号 ${slot + 1}"
        val notif = NotificationCompat.Builder(
            ctx, KeepAliveService.CHANNEL_OTHER
        )
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("回复没有发出去")
            .setContentText("刚才那条回复发送失败，点开对话再试一次。")
            .setSubText(label)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(ctx)
                .notify(bgFailureId(slot, chatId), notif)
        } catch (t: Throwable) {
        }
        registerBgId(ctx, slot, bgFailureId(slot, chatId))
    }

    private fun registerNetworkCallback(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return
        try {
            cm.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) =
                        pushNetworkType(cm)

                    override fun onLost(network: Network) =
                        pushNetworkType(cm)

                    override fun onCapabilitiesChanged(
                        network: Network,
                        caps: NetworkCapabilities
                    ) = pushNetworkType(cm)
                }
            )
        } catch (t: Throwable) {
        }
    }

    private fun pushNetworkType(cm: ConnectivityManager) {
        val caps = try {
            cm.getNetworkCapabilities(cm.activeNetwork)
        } catch (t: Throwable) {
            null
        }
        val type = when {
            caps == null -> "networkTypeNone"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                "networkTypeWiFi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                "networkTypeMobile"
            else -> "networkTypeOther"
        }
        val query = TdClient.request("setNetworkType").apply {
            put("type", JSONObject().put("@type", type))
        }
        TdRepository.sendRaw(query) { }
    }
}

/**
 * Handles the two actions on message notifications (v0.7.0,
 * F4): quick reply via RemoteInput and mark-as-read. Declared
 * in the manifest as not exported; the pending intents that
 * reach it are created by [Notifier] itself.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getLongExtra("chat_id", 0L)
        if (chatId == 0L) return
        // Background-account actions (v0.9.10) carry the
        // owning slot and are routed to that account's own
        // client — acting through the foreground account on
        // another account's chat id is exactly the
        // cross-account mixup R1 banned. A slot that IS the
        // running account (its notification outlived the
        // switch) takes the normal foreground path.
        val slot = intent.getIntExtra("slot", -1)
        val forBackground = slot >= 0 && slot != AccountStore.currentSlot()
        when (intent.action) {
            Notifier.ACTION_MARK_READ ->
                if (forBackground) {
                    Notifier.markReadFromBackground(context, slot, chatId)
                } else {
                    Notifier.markReadFromNotification(context, chatId)
                }
            Notifier.ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(Notifier.KEY_REPLY)
                    ?.toString()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    if (forBackground) {
                        Notifier.replyFromBackground(context, slot, chatId, text)
                    } else {
                        Notifier.replyFromNotification(context, chatId, text)
                    }
                }
            }
        }
    }
}
