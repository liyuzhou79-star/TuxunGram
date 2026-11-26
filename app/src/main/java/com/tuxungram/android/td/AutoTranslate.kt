package com.tuxungram.android.td

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/**
 * Whole-chat auto-translate (v0.9.62, G1) — a client-side
 * automation over the per-message translateMessageText call
 * that has existed since v0.9.5. The tl has no chat-wide
 * translation call: the official client reaches the same
 * result by translating message by message server-side, and
 * the server gates the call behind Premium. This store only
 * decides WHEN to ask: a per-chat switch (kept on this
 * phone, per account slot, like LocalPins), a throttled
 * one-at-a-time queue, and an honest stop: the first gate
 * refusal (Premium / secret chat) pauses the chat and
 * surfaces ONE plain-language notice — nothing is ever
 * shown as translated that the server did not translate.
 *
 * Scope: the main chat window (MessageStore). New arrivals
 * are fed from its updateNewMessage handler; opening a chat
 * (or flipping the switch on) offers the newest slice of
 * the loaded window. Thread / forum-topic hosts keep the
 * manual long-press translation they already had.
 */
object AutoTranslate {
    private const val PREFS = "auto_translate"

    /** Window pass bounds: at most this many newest
     *  untranslated messages are offered per pass, and the
     *  queue never holds more than QUEUE_CAP jobs — a huge
     *  chat cannot turn the switch into a request flood. */
    private const val WINDOW_TAKE = 40
    private const val QUEUE_CAP = 120

    /** Pause between two requests, on top of the round
     *  trip itself. */
    private const val GAP_MS = 350L

    private val _enabled = MutableStateFlow<Set<Long>>(emptySet())
    val enabled: StateFlow<Set<Long>> = _enabled

    /** One-shot plain-language notice (a chat's
     *  auto-translate paused itself); the chat screen shows
     *  it once and clears it. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var slot = 0

    /** Chats paused for this process run after a gate
     *  refusal; flipping the switch off and on clears the
     *  pause (the user may have gained Premium meanwhile). */
    private val paused = ConcurrentHashMap.newKeySet<Long>()

    /** Chats whose pause notice already fired this run —
     *  the promise is "told once", not once per message. */
    private val noticed = ConcurrentHashMap.newKeySet<Long>()

    private data class Job(val chatId: Long, val item: MsgItem)

    private val queue = ArrayDeque<Job>()
    private val queuedKeys = HashSet<Pair<Long, Long>>()

    @Volatile
    private var processing = false

    private val handler = Handler(Looper.getMainLooper())

    private fun key() = "chats_$slot"

    fun init(context: Context) {
        appContext = context.applicationContext
        slot = AccountStore.currentSlot()
        _enabled.value = load()
    }

    private fun load(): Set<Long> {
        val ctx = appContext ?: return emptySet()
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(), "") ?: ""
        if (raw.isEmpty()) return emptySet()
        return try {
            val arr = JSONArray(raw)
            val out = HashSet<Long>()
            for (i in 0 until arr.length()) {
                val id = arr.optLong(i, 0L)
                if (id != 0L) out.add(id)
            }
            out
        } catch (t: Throwable) {
            emptySet()
        }
    }

    private fun persist() {
        val ctx = appContext ?: return
        val arr = JSONArray()
        for (id in _enabled.value) arr.put(id)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(), arr.toString())
            .apply()
    }

    fun isEnabled(chatId: Long): Boolean = chatId in _enabled.value

    fun setEnabled(chatId: Long, on: Boolean) {
        if (chatId == 0L) return
        if (on) {
            paused.remove(chatId)
            if (!isEnabled(chatId)) {
                _enabled.value = _enabled.value + chatId
                persist()
            }
        } else {
            if (isEnabled(chatId)) {
                _enabled.value = _enabled.value - chatId
                persist()
            }
            dropQueued(chatId)
        }
    }

    /** Logout (same rule as LocalPins.clear): the switch
     *  list names the outgoing account's chats, so it
     *  leaves with the account. */
    fun clear() {
        _enabled.value = emptySet()
        persist()
        synchronized(queue) {
            queue.clear()
            queuedKeys.clear()
        }
        paused.clear()
        noticed.clear()
    }

    fun clearNotice() {
        _notice.value = null
    }

    /** The user opened a (possibly different) chat: queued
     *  work for any other chat is dropped — switching away
     *  stops that chat's automatic translation. */
    fun onOpenChat(chatId: Long) {
        synchronized(queue) {
            val it = queue.iterator()
            while (it.hasNext()) {
                val job = it.next()
                if (job.chatId != chatId) {
                    queuedKeys.remove(job.chatId to job.item.id)
                    it.remove()
                }
            }
        }
    }

    /** A genuinely new incoming message in the open chat. */
    fun onIncoming(chatId: Long, item: MsgItem) {
        if (!isEnabled(chatId) || chatId in paused) return
        if (!eligible(chatId, item)) return
        enqueue(listOf(Job(chatId, item)))
    }

    /** Offer the loaded window of an enabled chat (on open
     *  and on switch-on): the newest [WINDOW_TAKE] eligible
     *  messages, oldest first so translations land top-down
     *  in reading order. Already translated / in-flight /
     *  queued messages are skipped, so repeat calls are
     *  cheap no-ops. */
    fun onWindow(chatId: Long, items: List<MsgItem>) {
        if (!isEnabled(chatId) || chatId in paused) return
        val jobs = items
            .filter { eligible(chatId, it) }
            .sortedByDescending { it.id }
            .take(WINDOW_TAKE)
            .sortedBy { it.id }
            .map { Job(chatId, it) }
        if (jobs.isNotEmpty()) enqueue(jobs)
    }

    private fun eligible(chatId: Long, item: MsgItem): Boolean {
        if (item.outgoing) return false
        if (item.kind != MsgKind.TEXT) return false
        if (item.text.isBlank()) return false
        // A message still sending / failed has no stable
        // server text to translate yet.
        if (item.sendingState.isNotEmpty()) return false
        val key = chatId to item.id
        if (key in MessageStore.translations.value) return false
        if (key in MessageStore.translating.value) return false
        synchronized(queue) {
            if (key in queuedKeys) return false
        }
        return true
    }

    private fun enqueue(jobs: List<Job>) {
        synchronized(queue) {
            for (job in jobs) {
                if (queue.size >= QUEUE_CAP) break
                val key = job.chatId to job.item.id
                if (queuedKeys.add(key)) queue.addLast(job)
            }
        }
        kick()
    }

    private fun dropQueued(chatId: Long) {
        synchronized(queue) {
            val it = queue.iterator()
            while (it.hasNext()) {
                val job = it.next()
                if (job.chatId == chatId) {
                    queuedKeys.remove(job.chatId to job.item.id)
                    it.remove()
                }
            }
        }
    }

    private fun kick() {
        synchronized(queue) {
            if (processing) return
            processing = true
        }
        handler.post { processNext() }
    }

    private fun processNext() {
        val job = synchronized(queue) {
            if (queue.isEmpty()) {
                processing = false
                null
            } else {
                val next = queue.removeFirst()
                queuedKeys.remove(next.chatId to next.item.id)
                next
            }
        } ?: return
        if (!isEnabled(job.chatId) || job.chatId in paused) {
            // Switched off or paused while queued: skip
            // without spending a request.
            handler.post { processNext() }
            return
        }
        MessageStore.translateAuto(job.chatId, job.item) { gate ->
            if (gate) pauseChat(job.chatId)
            handler.postDelayed({ processNext() }, GAP_MS)
        }
    }

    /** The server refused for a gate reason: stop asking
     *  for this chat, drop its queue, and tell the user
     *  once, in plain words. The switch itself stays as the
     *  user set it; the pause is what stops the requests,
     *  and flipping the switch off/on retries deliberately. */
    private fun pauseChat(chatId: Long) {
        paused.add(chatId)
        dropQueued(chatId)
        if (noticed.add(chatId)) {
            val title = ChatStore.itemOf(chatId)?.title
                ?.ifEmpty { null } ?: "这个对话"
            _notice.value =
                "「$title」的自动翻译已暂停：翻译是 Telegram " +
                    "Premium 的功能，当前账号用不了，服务器拒绝了" +
                    "自动翻译。这个提示只会出现一次；单条消息仍可" +
                    "长按试一下「翻译」。"
        }
    }
}
