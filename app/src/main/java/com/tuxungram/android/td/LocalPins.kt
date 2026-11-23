package com.tuxungram.android.td

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/**
 * Locally pinned chats (v0.9.23, P1) — a convenience feature,
 * not a server-rule workaround. Telegram caps server pins per
 * list (getOption("pinned_chat_count_max") /
 * "pinned_archived_chat_count_max", 5 for free accounts), and
 * that cap is the server's to set: this app never tries to
 * exceed it. Once the server pins in a list are full, further
 * pins the user asks for are kept HERE instead — an ordered
 * list of chat ids stored on this phone only, shown in the
 * pinned area right after the real server pins and badged
 * 本地 so the two kinds are never confused. Local pins do
 * not sync to other devices and do not exist server-side.
 *
 * Storage is per account: the key carries the account slot
 * (AccountStore), because chat ids are only meaningful inside
 * one account. Switching accounts restarts the process, so
 * the list is loaded once at start for the foreground slot.
 */
object LocalPins {
    private const val PREFS = "local_pins"

    private val _ids = MutableStateFlow<List<Long>>(emptyList())
    val ids: StateFlow<List<Long>> = _ids

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var slot = 0

    private fun key() = "pins_$slot"

    fun init(context: Context) {
        appContext = context.applicationContext
        slot = AccountStore.currentSlot()
        _ids.value = load()
    }

    private fun load(): List<Long> {
        val ctx = appContext ?: return emptyList()
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(), "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Long>(arr.length())
            for (i in 0 until arr.length()) {
                val id = arr.optLong(i, 0L)
                if (id != 0L && id !in out) out.add(id)
            }
            out
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun persist() {
        val ctx = appContext ?: return
        val arr = JSONArray()
        for (id in _ids.value) arr.put(id)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(), arr.toString())
            .apply()
    }

    fun contains(chatId: Long): Boolean = chatId in _ids.value

    /** Append a chat to the local pin order (no-op if it is
     *  already there). New local pins go last in the local
     *  section, in the order the user pinned them. */
    fun add(chatId: Long) {
        if (chatId == 0L || contains(chatId)) return
        _ids.value = _ids.value + chatId
        persist()
    }

    fun remove(chatId: Long) {
        if (!contains(chatId)) return
        _ids.value = _ids.value - chatId
        persist()
    }

    /** Drop entries the chat list no longer knows about
     *  (deleted chats, chats removed from every list);
     *  called by ChatStore with the ids it can prove are
     *  gone — never with "not loaded yet" ids. */
    fun prune(gone: Set<Long>) {
        if (gone.isEmpty()) return
        val next = _ids.value.filter { it !in gone }
        if (next.size != _ids.value.size) {
            _ids.value = next
            persist()
        }
    }

    /** Replace the whole list (settings import). */
    fun replaceAll(list: List<Long>) {
        _ids.value = list.filter { it != 0L }.distinct()
        persist()
    }

    /** Drop this slot's whole list (logout, v0.9.41 H4):
     *  local pins name the outgoing account's chats, so
     *  they leave with the account instead of lingering
     *  for whoever logs into this slot next. */
    fun clear() {
        _ids.value = emptyList()
        persist()
    }
}
