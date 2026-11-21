package com.tuxungram.android.td

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** A user-configured proxy for TDLib, persisted in SharedPreferences.
 *  [type] is "socks5" or "mtproto"; the secret only applies to
 *  MTProto, username/password only to SOCKS5. */
data class ProxyConfig(
    val enabled: Boolean,
    val type: String,
    val server: String,
    val port: Int,
    val username: String,
    val password: String,
    val secret: String
)

/**
 * Owns the proxy setting end to end: persistence, applying it to
 * TDLib (addProxy / disableProxy, signatures verified against
 * td_api.tl), and re-applying it once per client start so users in
 * restricted networks do not depend on a system-wide VPN.
 */
object ProxyStore {
    private const val PREFS = "proxy"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TYPE = "type"
    private const val KEY_SERVER = "server"
    private const val KEY_PORT = "port"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_SECRET = "secret"

    @Volatile
    private var appContext: Context? = null

    /** Re-applying on every authorization update would spam addProxy;
     *  once per client instance is enough (reset on restart). */
    private val appliedThisClient = AtomicBoolean(false)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun load(): ProxyConfig? {
        val ctx = appContext ?: return null
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val server = prefs.getString(KEY_SERVER, "") ?: ""
        if (server.isEmpty()) return null
        return ProxyConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            type = prefs.getString(KEY_TYPE, "socks5") ?: "socks5",
            server = server,
            port = prefs.getInt(KEY_PORT, 1080),
            username = prefs.getString(KEY_USERNAME, "") ?: "",
            password = prefs.getString(KEY_PASSWORD, "") ?: "",
            secret = prefs.getString(KEY_SECRET, "") ?: ""
        )
    }

    fun save(config: ProxyConfig) {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putString(KEY_TYPE, config.type)
            .putString(KEY_SERVER, config.server)
            .putInt(KEY_PORT, config.port)
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PASSWORD, config.password)
            .putString(KEY_SECRET, config.secret)
            .apply()
    }

    /** Apply the saved config to the running client right now
     *  (used when the user saves in settings). */
    fun applyNow(config: ProxyConfig) {
        if (config.enabled) {
            sendAddProxy(config)
        } else {
            TdRepository.sendRaw(TdClient.request("disableProxy")) {}
        }
        appliedThisClient.set(true)
    }

    /** Re-apply the saved proxy once per client start, after the
     *  client reaches Ready. Adding the same proxy twice is harmless:
     *  TDLib returns the already-added proxy. */
    fun applySavedOnStart() {
        if (!appliedThisClient.compareAndSet(false, true)) return
        val config = load() ?: return
        if (config.enabled) sendAddProxy(config)
    }

    fun resetForRestart() {
        appliedThisClient.set(false)
    }

    private fun sendAddProxy(config: ProxyConfig) {
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
        val query = TdClient.request("addProxy").apply {
            put("proxy", JSONObject().apply {
                put("@type", "proxy")
                put("server", config.server)
                put("port", config.port)
                put("type", type)
            })
            put("enable", true)
            put("comment", "")
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
