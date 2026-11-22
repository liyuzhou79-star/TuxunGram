package com.tuxungram.android.td

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** One recorded auto-failover outcome (v0.9.14, U6): when
 *  it happened and what was done, in plain words. Shown on
 *  the proxy settings section so a switch is never silent
 *  in hindsight. */
data class ProxySwitchRecord(
    val timeMillis: Long,
    val text: String
)

/** One saved proxy (v0.6.0, E4: the single-proxy setting grew
 *  into a list). [type] is "socks5" or "mtproto"; the secret
 *  only applies to MTProto, username/password only to SOCKS5.
 *  [id] is a local identifier (the server-side added-proxy id
 *  is not needed: re-adding the same proxy is deduplicated by
 *  TDLib, and enable/disable is driven from here). */
data class ProxyConfig(
    val id: Long = 0L,
    val name: String = "",
    val type: String = "socks5",
    val server: String = "",
    val port: Int = 1080,
    val username: String = "",
    val password: String = "",
    val secret: String = ""
)

/**
 * Owns the proxy list end to end: persistence in
 * SharedPreferences, applying the active entry to TDLib
 * (addProxy / disableProxy, signatures verified against
 * td_api.tl), a pingProxy connectivity check, and re-applying
 * the active entry once per client start so users in
 * restricted networks do not depend on a system-wide VPN.
 * Pre-v0.6.0 single-proxy settings are migrated into the list
 * on first init.
 */
object ProxyStore {

    /** A proxy link (t.me/proxy / t.me/socks) waiting to be
     *  reviewed in the editor (v0.9.9, W3): SettingsScreen
     *  opens the editor with this config and clears it. The
     *  user still confirms before anything is saved. */
    val pendingPrefill = MutableStateFlow<ProxyConfig?>(null)
    private const val PREFS = "proxy"
    private const val KEY_LIST = "list_json"
    private const val KEY_ACTIVE = "active_id"
    // Auto-failover (v0.9.14, U6).
    private const val KEY_AUTO_FAILOVER = "auto_failover"
    private const val KEY_SWITCH_LOG = "switch_log_json"
    private const val FAILOVER_CHANNEL = "proxy_failover"
    // Legacy single-proxy keys (v0.2.0-v0.5.0), migration only.
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TYPE = "type"
    private const val KEY_SERVER = "server"
    private const val KEY_PORT = "port"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_SECRET = "secret"

    private val _proxies = MutableStateFlow<List<ProxyConfig>>(emptyList())
    val proxies: StateFlow<List<ProxyConfig>> = _proxies

    private val _activeId = MutableStateFlow(0L)
    val activeId: StateFlow<Long> = _activeId

    private val _autoFailover = MutableStateFlow(false)
    val autoFailover: StateFlow<Boolean> = _autoFailover

    private val _switchLog = MutableStateFlow<List<ProxySwitchRecord>>(
        emptyList()
    )
    val switchLog: StateFlow<List<ProxySwitchRecord>> = _switchLog

    /** One failover walk at a time, and one start-of-client
     *  check per client (reset alongside [appliedThisClient]). */
    private val failoverRunning = AtomicBoolean(false)
    private val failoverCheckedThisClient = AtomicBoolean(false)

    @Volatile
    private var appContext: Context? = null

    /** Re-applying on every authorization update would spam
     *  addProxy; once per client instance is enough (reset on
     *  restart). */
    private val appliedThisClient = AtomicBoolean(false)

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _autoFailover.value = prefs.getBoolean(KEY_AUTO_FAILOVER, false)
        _switchLog.value = parseSwitchLog(
            prefs.getString(KEY_SWITCH_LOG, null)
        )
        val raw = prefs.getString(KEY_LIST, null)
        if (raw != null) {
            _proxies.value = parseList(raw)
            _activeId.value = prefs.getLong(KEY_ACTIVE, 0L)
            // K2 migration: a list written by v0.9.0 or earlier
            // holds plaintext passwords/secrets; rewrite it in
            // encrypted form once (persist encrypts, see below).
            if (legacyPlaintextSeen) {
                legacyPlaintextSeen = false
                persist()
            }
        } else {
            migrateLegacy(prefs)
        }
    }

    /** Set by parseList when a stored password/secret was not
     *  in ProxyCrypto's encrypted form yet. */
    @Volatile
    private var legacyPlaintextSeen = false

    private fun migrateLegacy(prefs: android.content.SharedPreferences) {
        val server = prefs.getString(KEY_SERVER, "") ?: ""
        if (server.isEmpty()) {
            _proxies.value = emptyList()
            _activeId.value = 0L
            return
        }
        val entry = ProxyConfig(
            id = 1L,
            name = "",
            type = prefs.getString(KEY_TYPE, "socks5") ?: "socks5",
            server = server,
            port = prefs.getInt(KEY_PORT, 1080),
            username = prefs.getString(KEY_USERNAME, "") ?: "",
            password = prefs.getString(KEY_PASSWORD, "") ?: "",
            secret = prefs.getString(KEY_SECRET, "") ?: ""
        )
        _proxies.value = listOf(entry)
        _activeId.value =
            if (prefs.getBoolean(KEY_ENABLED, false)) entry.id else 0L
        persist()
    }

    private fun parseList(raw: String): List<ProxyConfig> {
        val out = ArrayList<ProxyConfig>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val rawPassword = o.optString("password")
                val rawSecret = o.optString("secret")
                if ((rawPassword.isNotEmpty() &&
                        !ProxyCrypto.isEncrypted(rawPassword)) ||
                    (rawSecret.isNotEmpty() &&
                        !ProxyCrypto.isEncrypted(rawSecret))
                ) {
                    legacyPlaintextSeen = true
                }
                out.add(
                    ProxyConfig(
                        id = o.optLong("id"),
                        name = o.optString("name"),
                        type = o.optString("type", "socks5"),
                        server = o.optString("server"),
                        port = o.optInt("port", 1080),
                        username = o.optString("username"),
                        password = ProxyCrypto.decrypt(rawPassword),
                        secret = ProxyCrypto.decrypt(rawSecret)
                    )
                )
            }
        } catch (t: Throwable) {
            // Corrupt persisted list: start empty rather than crash.
        }
        return out
    }

    private fun persist() {
        val ctx = appContext ?: return
        val arr = JSONArray()
        for (entry in _proxies.value) {
            arr.put(JSONObject().apply {
                put("id", entry.id)
                put("name", entry.name)
                put("type", entry.type)
                put("server", entry.server)
                put("port", entry.port)
                put("username", entry.username)
                // Passwords and MTProto secrets are stored
                // encrypted at rest (v0.9.1, audit K2); the
                // in-memory ProxyConfig stays plaintext because
                // the settings UI edits it and addProxy needs it.
                put("password", ProxyCrypto.encrypt(entry.password))
                put("secret", ProxyCrypto.encrypt(entry.secret))
            })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LIST, arr.toString())
            .putLong(KEY_ACTIVE, _activeId.value)
            .apply()
    }

    fun activeEntry(): ProxyConfig? =
        _proxies.value.firstOrNull { it.id == _activeId.value }

    fun add(
        name: String,
        type: String,
        server: String,
        port: Int,
        username: String,
        password: String,
        secret: String
    ): ProxyConfig {
        val entry = ProxyConfig(
            id = System.currentTimeMillis(),
            name = name,
            type = type,
            server = server,
            port = port,
            username = username,
            password = password,
            secret = secret
        )
        _proxies.value = _proxies.value + entry
        persist()
        return entry
    }

    fun update(entry: ProxyConfig) {
        _proxies.value = _proxies.value.map {
            if (it.id == entry.id) entry else it
        }
        persist()
        // Editing the live proxy: re-apply so the change counts.
        if (entry.id == _activeId.value) sendAddProxy(entry)
    }

    fun remove(id: Long) {
        if (id == _activeId.value) {
            _activeId.value = 0L
            TdRepository.sendRaw(TdClient.request("disableProxy")) {}
        }
        _proxies.value = _proxies.value.filter { it.id != id }
        persist()
    }

    /** Enable one entry (addProxy with enable=true also
     *  disables whichever proxy was active server-side). */
    fun setActive(id: Long) {
        val entry = _proxies.value.firstOrNull { it.id == id } ?: return
        _activeId.value = id
        persist()
        appliedThisClient.set(true)
        sendAddProxy(entry)
    }

    /** Turn proxying off entirely. */
    fun disableActive() {
        _activeId.value = 0L
        persist()
        appliedThisClient.set(true)
        TdRepository.sendRaw(TdClient.request("disableProxy")) {}
    }

    /** Re-apply the active entry once per client start, after
     *  the client reaches Ready. Adding the same proxy twice
     *  is harmless: TDLib returns the already-added proxy. */
    fun applySavedOnStart() {
        if (!appliedThisClient.compareAndSet(false, true)) return
        val entry = activeEntry() ?: return
        sendAddProxy(entry)
    }

    fun resetForRestart() {
        appliedThisClient.set(false)
        failoverCheckedThisClient.set(false)
    }

    // ---- Auto-failover (v0.9.14, U6) ----
    // Signal inventory (existing only, no new polling): the
    // checks run (a) once per client start, shortly after
    // applySavedOnStart, and (b) when a manual ping of the
    // ACTIVE entry fails in settings. A walk pings the
    // active entry, then each other saved entry once, in
    // list order; the first that answers becomes active.
    // All-fail stops and records — never a retry loop.

    fun setAutoFailover(enabled: Boolean) {
        _autoFailover.value = enabled
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_FAILOVER, enabled)
            .apply()
    }

    /** Called from TdRepository's Ready branch right after
     *  the saved proxy is applied; delayed so addProxy has
     *  time to settle before the probe. */
    fun scheduleStartFailoverCheck() {
        if (!_autoFailover.value) return
        if (!failoverCheckedThisClient.compareAndSet(false, true)) {
            return
        }
        Handler(Looper.getMainLooper()).postDelayed({
            checkActiveAndFailover("启动时检测")
        }, 8000)
    }

    fun checkActiveAndFailover(reason: String) {
        if (!_autoFailover.value) return
        val active = activeEntry() ?: return
        val others = _proxies.value.filter { it.id != active.id }
        if (others.isEmpty()) return
        if (!failoverRunning.compareAndSet(false, true)) return
        ping(active) { seconds ->
            if (seconds != null) {
                failoverRunning.set(false)
                return@ping
            }
            walkCandidates(active, others, 0, reason)
        }
    }

    private fun walkCandidates(
        failed: ProxyConfig,
        candidates: List<ProxyConfig>,
        index: Int,
        reason: String
    ) {
        if (index >= candidates.size) {
            failoverRunning.set(false)
            val text = "所有代理都连不上（$reason），" +
                "已保持当前代理「${labelOf(failed)}」。"
            recordSwitch(text)
            notifySwitch(text)
            return
        }
        val candidate = candidates[index]
        ping(candidate) { seconds ->
            if (seconds != null) {
                failoverRunning.set(false)
                setActive(candidate.id)
                val text = "代理「${labelOf(failed)}」连不上（$reason），" +
                    "已自动切换到「${labelOf(candidate)}」。"
                recordSwitch(text)
                notifySwitch(text)
            } else {
                walkCandidates(failed, candidates, index + 1, reason)
            }
        }
    }

    private fun labelOf(entry: ProxyConfig): String =
        entry.name.ifEmpty { "${entry.server}:${entry.port}" }

    private fun recordSwitch(text: String) {
        val record = ProxySwitchRecord(
            timeMillis = System.currentTimeMillis(),
            text = text
        )
        _switchLog.value = (listOf(record) + _switchLog.value).take(10)
        val ctx = appContext ?: return
        val arr = JSONArray()
        for (item in _switchLog.value) {
            arr.put(JSONObject().apply {
                put("time", item.timeMillis)
                put("text", item.text)
            })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SWITCH_LOG, arr.toString())
            .apply()
    }

    fun clearSwitchLog() {
        _switchLog.value = emptyList()
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_SWITCH_LOG)
            .apply()
    }

    private fun parseSwitchLog(
        raw: String?
    ): List<ProxySwitchRecord> {
        if (raw == null) return emptyList()
        val out = ArrayList<ProxySwitchRecord>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    ProxySwitchRecord(
                        timeMillis = o.optLong("time"),
                        text = o.optString("text")
                    )
                )
            }
        } catch (t: Throwable) {
            // Corrupt log: start empty rather than crash.
        }
        return out
    }

    private fun notifySwitch(text: String) {
        val ctx = appContext ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                FAILOVER_CHANNEL,
                "代理切换",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val intent = Intent(ctx, com.tuxungram.android.MainActivity::class.java)
            .apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        val pending = PendingIntent.getActivity(
            ctx, 91014, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(ctx, FAILOVER_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("代理自动切换")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(91014, notification)
    }

    /** Connectivity check (pingProxy, verified in td_api.tl):
     *  the callback receives the round-trip seconds, or null
     *  when the proxy did not answer. */
    fun ping(entry: ProxyConfig, callback: (Double?) -> Unit) {
        val query = TdClient.request("pingProxy").apply {
            put("proxy", proxyJson(entry))
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                callback(null)
            } else {
                // tl: seconds seconds:double = Seconds.
                callback(response.optDouble("seconds", -1.0).takeIf { it >= 0 })
            }
        }
    }

    private fun proxyJson(config: ProxyConfig): JSONObject {
        val type = when (config.type) {
            "mtproto" -> JSONObject().apply {
                put("@type", "proxyTypeMtproto")
                put("secret", config.secret)
            }
            else -> JSONObject().apply {
                put("@type", "proxyTypeSocks5")
                put("username", config.username)
                put("password", config.password)
            }
        }
        return JSONObject().apply {
            put("@type", "proxy")
            put("server", config.server)
            put("port", config.port)
            put("type", type)
        }
    }

    /** The addProxy query for the active entry, or null when
     *  no proxy is active. The foreground client applies it
     *  through [sendAddProxy]; background-account clients
     *  (v0.9.10) send this query through their own client —
     *  the proxy configuration is app-global, so every
     *  account's client needs the same entry applied. */
    fun activeProxyQuery(): JSONObject? {
        val entry = activeEntry() ?: return null
        return TdClient.request("addProxy").apply {
            put("proxy", proxyJson(entry))
            put("enable", true)
            put("comment", entry.name)
        }
    }

    private fun sendAddProxy(config: ProxyConfig) {
        val query = TdClient.request("addProxy").apply {
            put("proxy", proxyJson(config))
            put("enable", true)
            put("comment", config.name)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                TdRepository.reportError(
                    response.optInt("code") to response.optString("message")
                )
            }
        }
    }
}
