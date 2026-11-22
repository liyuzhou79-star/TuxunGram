package com.tuxungram.android.td

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * The single global receive dispatcher (v0.9.8, Phase 1 of
 * simultaneous multi-account).
 *
 * Why this exists: libtdjson's td_receive is ONE global queue —
 * it returns the next event from ANY client, each event tagged
 * with "@client_id". Until now the app ran exactly one client,
 * so TdClient could own a private receiver thread and treat
 * every event as its own. With several clients alive at once
 * that breaks twice over: an event for client A can be consumed
 * by client B's racing receiver thread, and both clients number
 * their "@extra" correlation ids from 1, so a response could
 * even fire the wrong client's callback.
 *
 * The hub owns the ONLY receiver thread. It routes each event
 * to the TdClient registered for its "@client_id"; that client
 * then does its own "@extra" correlation. Events for a client
 * that has been closed and unregistered are dropped here —
 * the transport-level half of the A6 stale-client guard (the
 * generation check in TdRepository remains as the second half).
 *
 * Threading is unchanged from the single-client days: exactly
 * one receiver thread delivers every callback sequentially,
 * which is what all stores already assume.
 */
object TdHub {
    private val clients = ConcurrentHashMap<Int, TdClient>()

    @Volatile
    private var loopStarted = false

    fun register(clientId: Int, client: TdClient) {
        clients[clientId] = client
        ensureLoop()
    }

    fun unregister(clientId: Int) {
        clients.remove(clientId)
    }

    private fun ensureLoop() {
        if (loopStarted) return
        synchronized(this) {
            if (loopStarted) return
            loopStarted = true
        }
        Thread({
            while (true) {
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
                val clientId = obj.optInt("@client_id", -1)
                val target = clients[clientId]
                    // Defensive fallback: an event with no
                    // client id goes to the sole registered
                    // client, matching the pre-hub behavior of
                    // the single-client app exactly. With two
                    // or more clients there is no safe guess,
                    // so it is dropped.
                    ?: if (clientId == -1 && clients.size == 1) {
                        clients.values.firstOrNull()
                    } else {
                        null
                    }
                target?.deliver(obj)
            }
        }, "td-receiver").apply {
            isDaemon = true
            start()
        }
    }
}
