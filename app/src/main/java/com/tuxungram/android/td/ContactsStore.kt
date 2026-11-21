package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One address-book contact, flattened from getUser. */
data class ContactEntry(
    val userId: Long,
    val name: String,
    val username: String
)

/**
 * The Telegram address book: getContacts once per process after
 * login (verified in td_api.tl), then a getUser per contact id
 * for display data. Powers the contacts section of global
 * search; a standalone contacts page can reuse this later.
 */
object ContactsStore {
    private val _contacts = MutableStateFlow<List<ContactEntry>>(emptyList())
    val contacts: StateFlow<List<ContactEntry>> = _contacts

    private val byId = ConcurrentHashMap<Long, ContactEntry>()

    @Volatile
    private var loaded = false

    fun loadContacts() {
        if (loaded) return
        loaded = true
        TdRepository.sendRaw(TdClient.request("getContacts")) { response ->
            if (TdClient.isError(response)) {
                loaded = false
                return@sendRaw
            }
            val ids = response.optJSONArray("user_ids") ?: return@sendRaw
            for (i in 0 until ids.length()) {
                fetchUser(ids.optLong(i))
            }
        }
    }

    private fun fetchUser(userId: Long) {
        val query = TdClient.request("getUser").apply { put("user_id", userId) }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) return@sendRaw
            val first = response.optString("first_name")
            val last = response.optString("last_name")
            val name = listOf(first, last).filter { it.isNotEmpty() }
                .joinToString(" ")
            val usernames = response.optJSONObject("usernames")
            val username = usernames?.optJSONArray("active_usernames")
                ?.optString(0) ?: ""
            if (name.isEmpty() && username.isEmpty()) return@sendRaw
            byId[userId] = ContactEntry(userId, name, username)
            publish()
        }
    }

    private fun publish() {
        _contacts.value = byId.values.sortedBy { it.name.lowercase() }
    }

    /** Local filter over the loaded address book (instant, no
     *  server round-trip needed for people already synced). */
    fun search(query: String): List<ContactEntry> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return _contacts.value.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.username.contains(q, ignoreCase = true)
        }
    }

    /** Open (creating if needed) the private chat with a user;
     *  createPrivateChat is the documented route from a user id
     *  to a chat id. */
    fun openPrivateChat(userId: Long, onResult: (Long?) -> Unit) {
        val query = TdClient.request("createPrivateChat").apply {
            put("user_id", userId)
            put("force", false)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                onResult(null)
            } else {
                onResult(response.optLong("id").takeIf { it != 0L })
            }
        }
    }
}
