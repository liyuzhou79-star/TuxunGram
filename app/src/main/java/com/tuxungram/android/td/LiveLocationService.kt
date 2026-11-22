package com.tuxungram.android.td

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tuxungram.android.MainActivity
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** One of my live locations currently being shared. */
data class LiveShare(
    val chatId: Long,
    val messageId: Long,
    /** Epoch second at which the share can no longer be
     *  updated (message date + live_period). */
    val expiresAtSec: Long
)

/**
 * Live-location background updates (v0.9.30, W2), closing
 * the v0.9.25 gap where my shared point only moved while
 * its chat stayed open.
 *
 * The registry watches the raw TDLib update stream for my
 * own outgoing messageLiveLocation messages (send success,
 * new-message and content updates), tracks each share's
 * expiry, and keeps [LiveLocationService] running exactly
 * while at least one share is active. The service fetches
 * a fix about once a minute and pushes it with
 * editMessageLiveLocation; the in-chat loop from v0.9.25
 * defers to the registry (it skips shares the service owns)
 * so a point is never pushed twice.
 *
 * Honest limits: this is a foreground service, so the
 * system shows a persistent notification while sharing
 * (with a stop action); aggressive ROMs can still kill it
 * if the user denies autostart/battery access; and a fix
 * comes from the system's last-known/single-update APIs —
 * no continuous GPS tracking is done between pushes.
 *
 * Persistence (v0.9.35, B5): the registry used to live in
 * memory only, so a process restart (system kill, update,
 * crash) silently ended background updates for shares
 * that were still live server-side. Active shares are
 * now written to a small per-account-slot preference
 * (chat/message/expiry only) on every change and rebuilt
 * at attach time; expired entries are dropped, and a
 * share that was stopped from another client while this
 * app was dead is dropped on the service's first push
 * (the server answers 400, which removes the entry).
 */
object LiveShareRegistry {

    private const val PREFS = "live_shares"

    private val shares =
        ConcurrentHashMap<Pair<Long, Long>, LiveShare>()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var attached = false

    /** Account slot the persisted shares belong to. Read
     *  straight from the accounts preferences at attach
     *  time — attach runs before AccountStore.init in
     *  MainActivity, and the slot must be right anyway. */
    @Volatile
    private var slot = 0

    fun attach(context: Context) {
        appContext = context.applicationContext
        if (attached) return
        attached = true
        slot = try {
            context.getSharedPreferences("accounts", Context.MODE_PRIVATE)
                .getInt("current_slot", 0)
        } catch (t: Throwable) {
            0
        }
        restore()
        TdRepository.addUpdateListener { update -> handleUpdate(update) }
    }

    private fun prefs() =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun persistKey() = "shares_$slot"

    /** Write the active shares out after every change. */
    private fun persist() {
        val ctx = appContext ?: return
        try {
            val arr = org.json.JSONArray()
            for (share in shares.values) {
                arr.put(JSONObject().apply {
                    put("chat_id", share.chatId)
                    put("message_id", share.messageId)
                    put("expires_at", share.expiresAtSec)
                })
            }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(persistKey(), arr.toString()).apply()
        } catch (t: Throwable) {
            // Persistence is a recovery aid; failing to
            // write it must never break sharing itself.
        }
    }

    /** Rebuild the registry from the persisted shares at
     *  startup and restart the service for the ones still
     *  live. Entries at (or within a minute of) their
     *  expiry are dropped — the server ends those itself. */
    private fun restore() {
        val raw = try {
            prefs()?.getString(persistKey(), null)
        } catch (t: Throwable) {
            null
        } ?: return
        val nowSec = System.currentTimeMillis() / 1000
        try {
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val chatId = obj.optLong("chat_id")
                val messageId = obj.optLong("message_id")
                val expiresAt = obj.optLong("expires_at")
                if (chatId == 0L || messageId == 0L) continue
                if (expiresAt - nowSec <= 60) continue
                shares[chatId to messageId] =
                    LiveShare(chatId, messageId, expiresAt)
            }
        } catch (t: Throwable) {
            // A corrupt record restores nothing; sharing
            // itself is unaffected.
            shares.clear()
            return
        }
        if (shares.isNotEmpty()) {
            persist()
            syncService()
        }
    }

    fun isActive(chatId: Long, messageId: Long): Boolean =
        shares.containsKey(chatId to messageId)

    fun snapshot(): List<LiveShare> = shares.values.toList()

    private fun handleUpdate(update: JSONObject) {
        try {
            when (update.optString("@type")) {
                "updateNewMessage", "updateMessageSendSucceeded" -> {
                    val msg = update.optJSONObject("message")
                        ?: return
                    trackMessage(msg)
                }
                "updateMessageContent" -> {
                    val chatId = update.optLong("chat_id")
                    val messageId = update.optLong("message_id")
                    val content = update.optJSONObject("new_content")
                        ?: return
                    if (content.optString("@type") ==
                        "messageLiveLocation"
                    ) {
                        val live = content.optJSONObject("location")
                        val period = live?.optInt("live_period") ?: 0
                        val key = chatId to messageId
                        val existing = shares[key]
                        if (period > 0 && existing != null) {
                            // Point moved (by us or another
                            // client); expiry is unchanged.
                        } else if (period <= 0) {
                            remove(chatId, messageId)
                        }
                        // A live location appearing in a chat
                        // we did not track (sent from another
                        // client) has no date here; the next
                        // full message fetch would be needed.
                        // Our own sends are tracked at send
                        // time, which is the case this serves.
                    } else {
                        // Turned into a static location (the
                        // stop edit) or something else entirely.
                        remove(chatId, messageId)
                    }
                }
                "updateDeleteMessages" -> {
                    val chatId = update.optLong("chat_id")
                    val ids = update.optJSONArray("message_ids")
                        ?: return
                    for (i in 0 until ids.length()) {
                        remove(chatId, ids.optLong(i))
                    }
                }
            }
        } catch (t: Throwable) {
            // Registry bookkeeping must never break the
            // update pipeline.
        }
    }

    private fun trackMessage(msg: JSONObject) {
        if (!msg.optBoolean("is_outgoing")) return
        val content = msg.optJSONObject("content") ?: return
        if (content.optString("@type") != "messageLiveLocation") return
        val live = content.optJSONObject("location") ?: return
        val period = live.optInt("live_period")
        if (period <= 0) return
        val chatId = msg.optLong("chat_id")
        val messageId = msg.optLong("id")
        if (chatId == 0L || messageId == 0L) return
        val date = msg.optLong("date")
        val expiresAt = if (date > 0) {
            date + period
        } else {
            System.currentTimeMillis() / 1000 + period
        }
        shares[chatId to messageId] = LiveShare(
            chatId, messageId, expiresAt
        )
        persist()
        syncService()
    }

    fun remove(chatId: Long, messageId: Long) {
        if (shares.remove(chatId to messageId) != null) {
            persist()
            syncService()
        }
    }

    /** Stop one share for real: the TDLib stop edit (null
     *  location), then drop the entry. Used by the service's
     *  notification action and by expiry handling. The chat
     *  bubble's own stop button goes through MessageStore,
     *  whose content update lands here as a removal too. */
    fun stopShare(chatId: Long, messageId: Long) {
        val query = TdClient.request("editMessageLiveLocation").apply {
            put("chat_id", chatId)
            put("message_id", messageId)
            put("reply_markup", JSONObject.NULL)
            put("location", JSONObject.NULL)
        }
        TdRepository.sendRaw(query) { }
        remove(chatId, messageId)
    }

    fun stopAll() {
        for (share in snapshot()) {
            stopShare(share.chatId, share.messageId)
        }
    }

    /** Push one fresh point into a share. Remaining period
     *  is recomputed from the tracked expiry so an update
     *  never extends the share. A 400 answer means the
     *  message can no longer be edited (expired or deleted
     *  server-side) — drop the entry instead of retrying
     *  forever. */
    fun pushPoint(share: LiveShare, latitude: Double, longitude: Double) {
        val remaining = share.expiresAtSec -
            System.currentTimeMillis() / 1000
        if (remaining <= 60) {
            remove(share.chatId, share.messageId)
            return
        }
        val query = TdClient.request("editMessageLiveLocation").apply {
            put("chat_id", share.chatId)
            put("message_id", share.messageId)
            put("reply_markup", JSONObject.NULL)
            put("location", JSONObject().apply {
                put("@type", "liveLocation")
                put("location", JSONObject().apply {
                    put("@type", "location")
                    put("latitude", latitude)
                    put("longitude", longitude)
                    put("horizontal_accuracy", 0.0)
                })
                put("live_period", remaining.toInt())
                put("heading", 0)
                put("proximity_alert_radius", 0)
            })
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response) &&
                response.optInt("code") == 400
            ) {
                remove(share.chatId, share.messageId)
            }
        }
    }

    private fun syncService() {
        val ctx = appContext ?: return
        if (shares.isEmpty()) {
            try {
                ctx.stopService(
                    Intent(ctx, LiveLocationService::class.java)
                )
            } catch (t: Throwable) {
            }
        } else {
            try {
                ContextCompat.startForegroundService(
                    ctx, Intent(ctx, LiveLocationService::class.java)
                )
            } catch (t: Throwable) {
                // A refused start (background restrictions)
                // leaves the in-chat updates as the fallback;
                // the share itself is unaffected.
            }
        }
    }
}

/**
 * The foreground service that exists only while at least
 * one live location is being shared. Ticks about once a
 * minute on its own thread; stops itself when the registry
 * empties (share stopped from the chat, from the
 * notification action, or by expiry).
 */
class LiveLocationService : Service() {

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    @Volatile
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        val stopIntent = Intent(this, LiveLocationService::class.java)
            .apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("正在共享实时位置")
            .setContentText("每分钟更新一次你的位置，直到共享到期或你停下来。")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "停止共享", stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIF_ID, notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (t: Throwable) {
            // Without the location foreground type granted
            // there is no honest way to run this; stop rather
            // than pretend background updates are happening.
            stopSelf()
            return
        }
        running = true
        thread = HandlerThread("live-location").apply { start() }
        handler = Handler(thread!!.looper)
        scheduleTick(5_000)
    }

    override fun onStartCommand(
        intent: Intent?, flags: Int, startId: Int
    ): Int {
        if (intent?.action == ACTION_STOP) {
            LiveShareRegistry.stopAll()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        super.onDestroy()
    }

    private fun scheduleTick(delayMs: Long) {
        handler?.postDelayed({ tick() }, delayMs)
    }

    private fun tick() {
        if (!running) return
        val shares = LiveShareRegistry.snapshot()
        if (shares.isEmpty()) {
            stopSelf()
            return
        }
        val nowSec = System.currentTimeMillis() / 1000
        val live = shares.filter { share ->
            if (share.expiresAtSec - nowSec <= 60) {
                // At (or past) the end: the server expires
                // the share itself; just stop tracking it.
                LiveShareRegistry.remove(share.chatId, share.messageId)
                false
            } else {
                true
            }
        }
        if (live.isNotEmpty()) {
            val fix = fetchFix()
            if (fix != null) {
                for (share in live) {
                    LiveShareRegistry.pushPoint(
                        share, fix.first, fix.second
                    )
                }
            }
        }
        if (running && LiveShareRegistry.snapshot().isNotEmpty()) {
            scheduleTick(60_000)
        } else if (running) {
            stopSelf()
        }
    }

    /** One position fix: the freshest last-known location,
     *  or a single fresh update (10 s cap) when the cached
     *  one is stale. Runs on the service thread. */
    private fun fetchFix(): Pair<Double, Double>? {
        val manager = getSystemService(
            Context.LOCATION_SERVICE
        ) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).filter { provider ->
            try {
                manager.isProviderEnabled(provider)
            } catch (t: Throwable) {
                false
            }
        }
        if (providers.isEmpty()) return null
        val last = try {
            providers.mapNotNull { provider ->
                try {
                    @Suppress("MissingPermission")
                    manager.getLastKnownLocation(provider)
                } catch (t: Throwable) {
                    null
                }
            }.maxByOrNull { it.time }
        } catch (t: Throwable) {
            null
        }
        if (last != null &&
            System.currentTimeMillis() - last.time < 2 * 60 * 1000
        ) {
            return last.latitude to last.longitude
        }
        val latch = CountDownLatch(1)
        var fresh: Location? = null
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                fresh = location
                latch.countDown()
            }

            override fun onStatusChanged(
                provider: String?, status: Int, extras: Bundle?
            ) = Unit

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        try {
            for (provider in providers) {
                try {
                    @Suppress("MissingPermission")
                    manager.requestLocationUpdates(
                        provider, 0L, 0f, listener, Looper.myLooper()
                    )
                } catch (t: Throwable) {
                }
            }
            latch.await(10, TimeUnit.SECONDS)
        } catch (t: Throwable) {
        } finally {
            try {
                manager.removeUpdates(listener)
            } catch (t: Throwable) {
            }
        }
        val f = fresh
        return if (f != null) {
            f.latitude to f.longitude
        } else if (last != null) {
            last.latitude to last.longitude
        } else {
            null
        }
    }

    companion object {
        private const val CHANNEL_ID = "livelocation"
        private const val NOTIF_ID = 2
        const val ACTION_STOP = "com.tuxungram.android.STOP_LIVE_LOCATION"

        private fun createChannel(context: Context) {
            val nm = context.getSystemService(
                NotificationManager::class.java
            ) ?: return
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "实时位置共享",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }
}
