package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One address-book contact, flattened from getUser.
 *  [statusText] is the presence line (userStatus* in
 *  td_api.tl, flattened by [ContactsStore.statusOf]);
 *  [isOnline] drives its accent color. */
data class ContactEntry(
    val userId: Long,
    val name: String,
    val username: String,
    val statusText: String = "",
    val isOnline: Boolean = false,
    /** Local path of the small profile photo once AvatarStore
     *  has it on disk (v0.9.9, W0); null = initial-letter disc. */
    val photoPath: String? = null
)

/**
 * The Telegram address book: getContacts once per process after
 * login (verified in td_api.tl), then a getUser per contact id
 * for display data. Powers the contacts section of global
 * search and, since v0.9.1 (J4), the standalone contacts page
 * (letter groups, search, presence). Presence stays current
 * through updateUser: TDLib pushes the full user object when a
 * status flips, and known contacts are re-flattened from it.
 */
object ContactsStore {
    private val _contacts = MutableStateFlow<List<ContactEntry>>(emptyList())
    val contacts: StateFlow<List<ContactEntry>> = _contacts

    /** True once a getContacts answer has landed, even an
     *  empty one (v0.9.9, W5): the contacts page showed its
     *  loading spinner forever for an account that simply
     *  has no contacts, because empty list was its only
     *  "not loaded yet" signal. */
    private val _loadFinished = MutableStateFlow(false)
    val loadFinished: StateFlow<Boolean> = _loadFinished

    private val byId = ConcurrentHashMap<Long, ContactEntry>()

    @Volatile
    private var loaded = false

    @Volatile
    private var attached = false

    private fun attach() {
        if (attached) return
        attached = true
        // Avatar paths resolve asynchronously in AvatarStore;
        // republish so rows swap letters for photos as they land.
        AvatarStore.addListener { publish() }
        TdRepository.addUpdateListener { update ->
            if (update.optString("@type") != "updateUser") {
                return@addUpdateListener
            }
            val user = update.optJSONObject("user")
                ?: return@addUpdateListener
            val userId = user.optLong("id")
            if (!byId.containsKey(userId)) return@addUpdateListener
            val entry = entryFromUser(user) ?: return@addUpdateListener
            byId[userId] = entry
            publish()
        }
    }

    /** Re-run getContacts after an importContacts sync
     *  (v0.9.13, T4): the one-shot [loaded] guard would
     *  otherwise keep the newly added contacts out of the
     *  book until the next process start. Existing entries
     *  are kept and refreshed in place by fetchUser. */
    fun refreshAfterImport() {
        loaded = false
        loadContacts()
    }

    fun loadContacts() {
        attach()
        if (loaded) return
        loaded = true
        TdRepository.sendRaw(TdClient.request("getContacts")) { response ->
            if (TdClient.isError(response)) {
                loaded = false
                return@sendRaw
            }
            val ids = response.optJSONArray("user_ids") ?: return@sendRaw
            _loadFinished.value = true
            for (i in 0 until ids.length()) {
                fetchUser(ids.optLong(i))
            }
        }
    }

    private fun fetchUser(userId: Long) {
        val query = TdClient.request("getUser").apply { put("user_id", userId) }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) return@sendRaw
            val entry = entryFromUser(response) ?: return@sendRaw
            byId[userId] = entry
            publish()
        }
    }

    /** Flatten a full user object (from getUser or updateUser)
     *  into a contact row; null when it carries neither a name
     *  nor a username worth listing. */
    private fun entryFromUser(user: JSONObject): ContactEntry? {
        val userId = user.optLong("id")
        AvatarStore.noteUser(user)
        val first = user.optString("first_name")
        val last = user.optString("last_name")
        val name = listOf(first, last).filter { it.isNotEmpty() }
            .joinToString(" ")
        val usernames = user.optJSONObject("usernames")
        val username = usernames?.optJSONArray("active_usernames")
            ?.optString(0) ?: ""
        if (name.isEmpty() && username.isEmpty()) return null
        val (statusText, online) = statusOf(user.optJSONObject("status"))
        return ContactEntry(userId, name, username, statusText, online)
    }

    /** Presence line from a userStatus object (types verified
     *  in td_api.tl: userStatusOnline{expires}, userStatus
     *  Offline{was_online}, userStatusRecently / LastWeek /
     *  LastMonth, userStatusEmpty). The exact last-seen time is
     *  only exposed in the Offline case; the rest are the
     *  coarse buckets the user's own privacy settings allow. */
    fun statusOf(status: JSONObject?): Pair<String, Boolean> {
        if (status == null) return "" to false
        return when (status.optString("@type")) {
            "userStatusOnline" -> "在线" to true
            "userStatusOffline" -> {
                val wasOnline = status.optInt("was_online", 0)
                val text = if (wasOnline > 0) {
                    "上次在线 ${lastSeenText(wasOnline)}"
                } else {
                    "上次在线很久以前"
                }
                text to false
            }
            "userStatusRecently" -> "最近来过" to false
            "userStatusLastWeek" -> "一周内来过" to false
            "userStatusLastMonth" -> "一个月内来过" to false
            else -> "" to false
        }
    }

    private fun lastSeenText(wasOnlineSeconds: Int): String {
        val then = java.util.Calendar.getInstance().apply {
            timeInMillis = wasOnlineSeconds * 1000L
        }
        val now = java.util.Calendar.getInstance()
        val time = TimeFmt.timeOfDay(wasOnlineSeconds.toLong())
        val sameDay = now.get(java.util.Calendar.YEAR) ==
            then.get(java.util.Calendar.YEAR) &&
            now.get(java.util.Calendar.DAY_OF_YEAR) ==
            then.get(java.util.Calendar.DAY_OF_YEAR)
        val yesterday = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        val isYesterday = yesterday.get(java.util.Calendar.YEAR) ==
            then.get(java.util.Calendar.YEAR) &&
            yesterday.get(java.util.Calendar.DAY_OF_YEAR) ==
            then.get(java.util.Calendar.DAY_OF_YEAR)
        return when {
            sameDay -> time
            isYesterday -> "昨天 $time"
            now.get(java.util.Calendar.YEAR) ==
                then.get(java.util.Calendar.YEAR) ->
                java.text.SimpleDateFormat(
                    "M月d日", java.util.Locale.getDefault()
                ).format(then.time)
            else -> java.text.SimpleDateFormat(
                "yyyy年M月d日", java.util.Locale.getDefault()
            ).format(then.time)
        }
    }

    private fun publish() {
        _contacts.value = byId.values
            .map { entry ->
                val path = AvatarStore.pathForUser(entry.userId)
                if (path != entry.photoPath) {
                    entry.copy(photoPath = path)
                } else {
                    entry
                }
            }
            .sortedBy { it.name.lowercase() }
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

    /** My own user id, once getMe has answered (v0.9.1, J5). */
    @Volatile
    private var myId = 0L

    /** Open Saved Messages (v0.9.1, J5). Saved Messages is not
     *  a special chat type: it is the private chat with
     *  oneself, so the route is getMe -> createPrivateChat(my
     *  own id) — the standard TDLib idiom. Sending, reading and
     *  history then behave exactly like any private chat,
     *  because it is one. The id is cached after the first
     *  resolution. */
    fun openSavedMessages(onResult: (Long?) -> Unit) {
        val cached = myId
        if (cached != 0L) {
            openPrivateChat(cached, onResult)
            return
        }
        TdRepository.sendRaw(TdClient.request("getMe")) { me ->
            if (TdClient.isError(me)) {
                onResult(null)
                return@sendRaw
            }
            val id = me.optLong("id")
            if (id == 0L) {
                onResult(null)
                return@sendRaw
            }
            myId = id
            openPrivateChat(id, onResult)
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
