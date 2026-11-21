package com.tuxungram.android.td

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Transport layer over libtdjson: owns one client instance, a receiver
 * thread, and request/response correlation via "@extra".
 *
 * Responses (objects whose "@extra" matches a pending request) go to
 * that request's callback; everything else is an update and goes to
 * [onUpdate]. All callbacks run on the receiver thread — UI code must
 * hop to the main thread itself.
 */
class TdClient(
    private val onUpdate: (JSONObject) -> Unit
) {
    private val clientId: Int = TdNative.createClientId()
    private val extraSeq = AtomicLong(1)
    private val callbacks = ConcurrentHashMap<String, (JSONObject) -> Unit>()

    @Volatile
    private var running = false
    private var receiver: Thread? = null

    fun start() {
        if (running) return
        running = true
        receiver = Thread({
            while (running) {
                val bytes = try {
                    TdNative.receive(2.0)
                } catch (t: Throwable) {
                    null
                } ?: continue
                val obj = try {
                    JSONObject(String(bytes, Charsets.UTF_8))
                } catch (t: Throwable) {
                    continue
                }
                val extra = obj.optString("@extra", "")
                val callback = if (extra.isNotEmpty()) callbacks.remove(extra) else null
                if (callback != null) {
                    callback(obj)
                } else {
                    onUpdate(obj)
                }
            }
        }, "td-receiver").apply {
            isDaemon = true
            start()
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
