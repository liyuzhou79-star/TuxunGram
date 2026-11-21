package com.tuxungram.android.td

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import androidx.core.content.ContextCompat
import com.tuxungram.android.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/** Where a notification tap wants the UI to go. MainActivity
 *  translates its launch intent into this flow; ReadyHost
 *  consumes it and opens the chat. */
object DeepLink {
    val pendingChat = MutableStateFlow<Long?>(null)
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

    private fun post(ctx: Context, chatId: Long, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_chat_id", chatId)
        }
        val pending = PendingIntent.getActivity(
            ctx, chatId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(ctx, KeepAliveService.CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(chatId.hashCode(), notif)
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
