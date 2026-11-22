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
        const val CHANNEL_MESSAGES = "messages"
        private const val NOTIF_ID = 1

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
                    CHANNEL_MESSAGES, "新消息",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
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

    private fun onNewMessage(update: JSONObject) {
        if (!UiSettings.keepAlive.value) return
        val ctx = appContext ?: return
        val message = update.optJSONObject("message") ?: return
        if (message.optBoolean("is_outgoing")) return
        val chatId = message.optLong("chat_id")
        if (chatId == foregroundChatId) return
        val item = ChatStore.itemOf(chatId)
        if (item?.isMuted == true) return
        lastMessageIdByChat[chatId] = message.optLong("id")
        val summary = summaryOf(message.optJSONObject("content"))
        val title = item?.title
        if (title != null) {
            post(ctx, chatId, title, summary)
        } else {
            // Chat not in the local list cache yet (brand-new
            // chat): resolve its title first, then post.
            val query = TdClient.request("getChat").apply {
                put("chat_id", chatId)
            }
            TdRepository.sendRaw(query) { chat ->
                if (!TdClient.isError(chat)) {
                    post(ctx, chatId, chat.optString("title"), summary)
                }
            }
        }
    }

    private fun summaryOf(content: JSONObject?): String {
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

    private fun post(ctx: Context, chatId: Long, title: String, text: String) {
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
        val notif = NotificationCompat.Builder(ctx, KeepAliveService.CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
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
            ctx, KeepAliveService.CHANNEL_MESSAGES
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
        lastMessageIdByChat.clear()
    }

    /** Withdraw a chat's notification (it was read in the app,
     *  marked read, or replied to) and refresh the summary. */
    fun dismissForChat(ctx: Context, chatId: Long) {
        linesByChat.remove(chatId)
        try {
            NotificationManagerCompat.from(ctx).cancel(chatId.hashCode())
        } catch (t: Throwable) {
        }
        postSummary(ctx)
    }

    /** Mark-read action: viewMessages on the newest known
     *  message advances the read position (same call the chat
     *  screen uses), then the notification goes away. */
    fun markReadFromNotification(ctx: Context, chatId: Long) {
        val messageId = lastMessageIdByChat[chatId]
        if (messageId != null && messageId != 0L) {
            val query = TdClient.request("viewMessages").apply {
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
            TdRepository.sendRaw(query) {}
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
        val query = TdClient.request("sendMessage").apply {
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
            ctx, KeepAliveService.CHANNEL_MESSAGES
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
        when (intent.action) {
            Notifier.ACTION_MARK_READ ->
                Notifier.markReadFromNotification(context, chatId)
            Notifier.ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(Notifier.KEY_REPLY)
                    ?.toString()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    Notifier.replyFromNotification(context, chatId, text)
                }
            }
        }
    }
}
