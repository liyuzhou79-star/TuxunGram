package com.tuxungram.android.td

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Transport layer over libtdjson: owns one client instance and
 * request/response correlation via "@extra".
 *
 * Receiving is NOT done here: td_receive is a single global
 * queue shared by every client, so [TdHub] owns the one
 * receiver thread and routes each event to the client named
 * by its "@client_id" (see TdHub for why per-client receiver
 * threads corrupt each other's traffic). This client only
 * registers with the hub in [start] and receives its events
 * through [deliver].
 *
 * Responses (objects whose "@extra" matches a pending request) go to
 * that request's callback; everything else is an update and goes to
 * [onUpdate]. All callbacks run on the hub's receiver thread — UI
 * code must hop to the main thread itself.
 */
class TdClient(
    private val onUpdate: (JSONObject) -> Unit
) {
    private val clientId: Int = TdNative.createClientId()
    private val extraSeq = AtomicLong(1)
    private val callbacks = ConcurrentHashMap<String, (JSONObject) -> Unit>()

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        TdHub.register(clientId, this)
    }

    /** One event routed to this client by [TdHub]. Runs on the
     *  hub's receiver thread. */
    internal fun deliver(obj: JSONObject) {
        if (!running) return
        val extra = obj.optString("@extra", "")
        val callback = if (extra.isNotEmpty()) callbacks.remove(extra) else null
        if (callback != null) {
            callback(obj)
        } else {
            onUpdate(obj)
        }
    }

    /** Fire-and-forget when [callback] is null; otherwise the response
     *  (or an "error" object) is delivered to [callback] exactly once. */
    fun send(query: JSONObject, callback: ((JSONObject) -> Unit)? = null) {
        if (callback != null) {
            val extra = extraSeq.getAndIncrement().toString()
            query.put("@extra", extra)
            callbacks[extra] = callback
        }
        TdNative.send(clientId, query.toString().toByteArray(Charsets.UTF_8))
    }

    /** Synchronous request executed on the current thread (for simple
     *  local queries like setLogVerbosityLevel). */
    fun execute(query: JSONObject): JSONObject? {
        val bytes = TdNative.execute(query.toString().toByteArray(Charsets.UTF_8))
            ?: return null
        return try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (t: Throwable) {
            null
        }
    }

    fun close() {
        running = false
        // Unregister first: from here on the hub drops every
        // event still queued for this client, so nothing stale
        // can reach onUpdate or a callback after close.
        TdHub.unregister(clientId)
        // Audit K3 (v0.9.1): requests still in flight on a dying
        // client can never be answered once the receiver stops;
        // drop their callbacks now instead of leaving them in
        // the map for the rest of this client's lifetime. They
        // are NOT invoked — callers of a closing client must not
        // be handed a fake answer (a restart builds a fresh
        // client and the stores re-issue what they need).
        callbacks.clear()
        send(JSONObject().put("@type", "close"))
    }

    companion object {
        fun request(type: String): JSONObject = JSONObject().put("@type", type)

        fun isError(obj: JSONObject): Boolean = obj.optString("@type") == "error"
    }
}
