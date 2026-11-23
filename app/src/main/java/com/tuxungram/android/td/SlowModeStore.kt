package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * Slow-mode countdown for the open chat (v0.9.32, Y4).
 *
 * The numbers come from the server, never from a local
 * guess: supergroupFullInfo carries slow_mode_delay (the
 * group's setting) and slow_mode_delay_expires_in (the
 * seconds TDLib itself computes as left before THIS
 * account may post again, derived server-side from the
 * last sent message). watch() resolves the chat's
 * supergroup id and reads the full info; every own
 * updateMessageSendSucceeded for the watched chat
 * re-reads it, because the tl notes that no
 * updateSupergroupFullInfo is pushed when expires_in
 * changes — polling the full info on the events that can
 * move it (opening the chat, sending a message) is the
 * documented way to stay exact. Changes to the delay
 * itself DO arrive as updateSupergroupFullInfo pushes,
 * and since v0.9.45 (P3) those are applied live too.
 *
 * Scope, per td_api.tl: the delay binds NON-administrator
 * supergroup members. Broadcast channels are excluded
 * (only admins post there, and they are exempt), basic
 * groups have no slow mode at all, and the UI hides the
 * countdown for creators/administrators via AdminStore's
 * caps, which come from the account's own chatMember
 * status.
 */
object SlowModeStore {
    /** The watched chat's slow_mode_delay in seconds;
     *  0 = slow mode off (or not a supergroup). */
    private val _delaySec = MutableStateFlow(0)
    val delaySec: StateFlow<Int> = _delaySec

    /** Wall-clock moment (ms) until which sending is
     *  blocked, anchored on the server's expires_in at
     *  fetch time; 0 = not blocked. */
    private val _waitUntilMs = MutableStateFlow(0L)
    val waitUntilMs: StateFlow<Long> = _waitUntilMs

    @Volatile
    var chatId = 0L
        private set

    @Volatile
    private var supergroupId = 0L

    @Volatile
    private var attached = false

    private fun attach() {
        if (attached) return
        attached = true
        TdRepository.addUpdateListener { update ->
            when (update.optString("@type")) {
                "updateMessageSendSucceeded" -> {
                    // A message of mine just landed in the
                    // watched chat: the server's remaining
                    // wait starts now, so re-read it instead
                    // of assuming delaySec.
                    if (update.optLong("chat_id") == chatId &&
                        supergroupId != 0L
                    ) {
                        fetchFullInfo()
                    }
                }
                "updateSupergroupFullInfo" -> {
                    // The group's full info changed while
                    // the chat is open (v0.9.45, P3) — an
                    // admin raising or lowering the slow-mode
                    // delay is exactly such a change, and the
                    // tl pushes the whole object for it (only
                    // expires_in drifting on its own is exempt
                    // from pushing). Apply the pushed object
                    // instead of waiting for the next send or
                    // re-entry to notice.
                    if (supergroupId != 0L &&
                        update.optLong("supergroup_id") == supergroupId
                    ) {
                        update.optJSONObject("supergroup_full_info")
                            ?.let { applyFullInfo(it) }
                    }
                }
            }
        }
    }

    /** Point the store at a chat (called when a chat
     *  opens). Non-supergroups simply stay at delay 0. */
    fun watch(forChatId: Long) {
        attach()
        chatId = forChatId
        supergroupId = 0L
        _delaySec.value = 0
        _waitUntilMs.value = 0L
        val query = TdClient.request("getChat").apply {
            put("chat_id", forChatId)
        }
        TdRepository.sendRaw(query) { chat ->
            if (TdClient.isError(chat) || chatId != forChatId) {
                return@sendRaw
            }
            val type = chat.optJSONObject("type")
                ?: return@sendRaw
            if (type.optString("@type") != "chatTypeSupergroup" ||
                type.optBoolean("is_channel")
            ) {
                return@sendRaw
            }
            supergroupId = type.optLong("supergroup_id")
            if (supergroupId != 0L) fetchFullInfo()
        }
    }

    private fun fetchFullInfo() {
        val forChat = chatId
        val sgId = supergroupId
        if (sgId == 0L) return
        val query = TdClient.request("getSupergroupFullInfo").apply {
            put("supergroup_id", sgId)
        }
        TdRepository.sendRaw(query) { info ->
            if (TdClient.isError(info) || chatId != forChat) {
                return@sendRaw
            }
            applyFullInfo(info)
        }
    }

    /** Fold one supergroupFullInfo object into the two
     *  countdown flows: the group's delay, and my personal
     *  wait anchored on the server's expires_in at the
     *  moment the object was produced. Shared by the
     *  fetch path and the pushed-update path (v0.9.45,
     *  P3) so both read the fields the same way. */
    private fun applyFullInfo(info: JSONObject) {
        _delaySec.value = info.optInt("slow_mode_delay")
        val expiresIn = info.optDouble(
            "slow_mode_delay_expires_in", 0.0
        )
        _waitUntilMs.value = if (expiresIn > 0.0) {
            System.currentTimeMillis() +
                (expiresIn * 1000.0).toLong()
        } else {
            0L
        }
    }
}
