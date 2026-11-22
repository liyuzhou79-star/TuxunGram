package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One row of the blocked-senders list. Senders can be users
 *  or chats (channels), so the row carries the sender kind
 *  alongside the id — unblocking rebuilds the exact
 *  MessageSender from the pair. */
data class BlockedEntry(
    /** "user" or "chat". */
    val senderKind: String,
    val senderId: Long,
    val name: String
)

/**
 * Block list (v0.9.3, M3). This td_api.tl has no
 * blockMessageSender/unblockMessageSender — blocking is
 * setMessageSenderBlockList(sender_id, block_list) with
 * blockListMain to block and null to unblock (verified in
 * td_api.tl), and the list itself is
 * getBlockedMessageSenders(blockListMain, offset, limit).
 */
object BlockStore {
    private val _blocked = MutableStateFlow<List<BlockedEntry>>(emptyList())
    val blocked: StateFlow<List<BlockedEntry>> = _blocked

    private val _exhausted = MutableStateFlow(false)
    val exhausted: StateFlow<Boolean> = _exhausted

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    private var loading = false
    private var offset = 0
    private val entries = ArrayList<BlockedEntry>()
    private val nameBySender = ConcurrentHashMap<String, String>()
    private val nameFetchPending = ConcurrentHashMap.newKeySet<String>()

    private fun plainError(error: JSONObject, what: String): String {
        val message = error.optString("message")
        return if (message.contains("FLOOD_WAIT")) {
            "操作太频繁了，等一会儿再试。"
        } else {
            "$what 失败，再试一次。（错误码 ${error.optInt("code")}）"
        }
    }

    private fun senderJson(kind: String, id: Long): JSONObject =
        JSONObject().apply {
            if (kind == "user") {
                put("@type", "messageSenderUser")
                put("user_id", id)
            } else {
                put("@type", "messageSenderChat")
                put("chat_id", id)
            }
        }

    /** Block or unblock one sender. [kind] is "user" for a
     *  user profile; chat senders come from the list itself. */
    fun setBlocked(
        kind: String,
        id: Long,
        blocked: Boolean,
        cb: (String?) -> Unit
    ) {
        val query = TdClient.request("setMessageSenderBlockList").apply {
            put("sender_id", senderJson(kind, id))
            if (blocked) {
                put("block_list", JSONObject().put("@type", "blockListMain"))
            } else {
                put("block_list", JSONObject.NULL)
            }
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                cb(plainError(response, if (blocked) "拉黑" else "取消拉黑"))
            } else {
                if (!blocked) {
                    synchronized(entries) {
                        entries.removeAll {
                            it.senderKind == kind && it.senderId == id
                        }
                    }
                    publish()
                }
                cb(null)
            }
        }
    }

    /** Convenience for the user-profile page. */
    fun setUserBlocked(userId: Long, blocked: Boolean, cb: (String?) -> Unit) =
        setBlocked("user", userId, blocked, cb)

    /** (Re)load the blocked list from the first page. */
    fun load() {
        synchronized(entries) { entries.clear() }
        offset = 0
        _exhausted.value = false
        _loadError.value = null
        _blocked.value = emptyList()
        loadMore()
    }

    fun loadMore() {
        if (loading || _exhausted.value) return
        loading = true
        val query = TdClient.request("getBlockedMessageSenders").apply {
            put("block_list", JSONObject().put("@type", "blockListMain"))
            put("offset", offset)
            put("limit", 50)
        }
        TdRepository.sendRaw(query) { response ->
            loading = false
            if (TdClient.isError(response)) {
                _loadError.value = plainError(response, "读取黑名单")
                return@sendRaw
            }
            val senders = response.optJSONArray("senders")
            var added = 0
            if (senders != null) {
                for (i in 0 until senders.length()) {
                    val sender = senders.optJSONObject(i) ?: continue
                    val (kind, id) = when (sender.optString("@type")) {
                        "messageSenderUser" -> "user" to sender.optLong("user_id")
                        "messageSenderChat" -> "chat" to sender.optLong("chat_id")
                        else -> continue
                    }
                    if (id == 0L) continue
                    val key = "$kind:$id"
                    synchronized(entries) {
                        if (entries.any {
                                it.senderKind == kind && it.senderId == id
                            }
                        ) {
                            continue
                        }
                        entries.add(
                            BlockedEntry(kind, id, nameBySender[key] ?: "")
                        )
                    }
                    added++
                    if (!nameBySender.containsKey(key)) fetchName(kind, id)
                }
            }
            offset += added
            val total = response.optInt("total_count")
            val size = synchronized(entries) { entries.size }
            _exhausted.value = added == 0 || size >= total
            publish()
        }
    }

    private fun publish() {
        _blocked.value = synchronized(entries) { ArrayList(entries) }
    }

    /** Resolve a display name lazily (getUser / getChat), then
     *  patch the row in place. */
    private fun fetchName(kind: String, id: Long) {
        val key = "$kind:$id"
        if (!nameFetchPending.add(key)) return
        val query = if (kind == "user") {
            TdClient.request("getUser").apply { put("user_id", id) }
        } else {
            TdClient.request("getChat").apply { put("chat_id", id) }
        }
        TdRepository.sendRaw(query) { response ->
            nameFetchPending.remove(key)
            if (TdClient.isError(response)) return@sendRaw
            val name = if (kind == "user") {
                listOf(
                    response.optString("first_name"),
                    response.optString("last_name")
                ).filter { it.isNotEmpty() }.joinToString(" ")
            } else {
                response.optString("title")
            }
            if (name.isEmpty()) return@sendRaw
            nameBySender[key] = name
            synchronized(entries) {
                for (i in entries.indices) {
                    if (entries[i].senderKind == kind &&
                        entries[i].senderId == id
                    ) {
                        entries[i] = entries[i].copy(name = name)
                    }
                }
            }
            publish()
        }
    }
}
