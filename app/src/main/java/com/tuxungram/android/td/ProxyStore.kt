package com.tuxungram.android.td

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

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

    @Volatile
    private var appContext: Context? = null

    /** Re-applying on every authorization update would spam
     *  addProxy; once per client instance is enough (reset on
     *  restart). */
    private val appliedThisClient = AtomicBoolean(false)

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
