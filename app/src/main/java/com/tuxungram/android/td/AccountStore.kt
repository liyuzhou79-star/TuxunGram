package com.tuxungram.android.td

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Multi-account bookkeeping (Batch 3, C6). td_api.tl has no
 * multi-account functions — the TDLib way is one client instance
 * per account, each with its own database directory. This app
 * runs one client per process, so an account is a numbered slot
 * with its own directory pair; slot 0 keeps the pre-multi-account
 * directory names so an existing login survives the upgrade.
 * Switching slots persists the choice and restarts the process
 * (see TdRepository.switchAccount), which gives every store a
 * clean slate for the other account — nothing can leak across.
 */
object AccountStore {
    private const val PREFS = "accounts"
    private const val KEY_CURRENT = "current_slot"
    private const val KEY_COUNT = "slot_count"

    private const val KEY_PENDING_SLOT = "pending_open_slot"
    private const val KEY_PENDING_CHAT = "pending_open_chat"

    private fun labelKey(slot: Int) = "label_$slot"

    private fun avatarKey(slot: Int) = "avatar_$slot"

    @Volatile
    private var appContext: Context? = null

    private val _current = MutableStateFlow(0)
    val current: StateFlow<Int> = _current

    private val _slots = MutableStateFlow<List<Int>>(listOf(0))
    val slots: StateFlow<List<Int>> = _slots

    private val _labels = MutableStateFlow<Map<Int, String>>(emptyMap())
    val labels: StateFlow<Map<Int, String>> = _labels

    /** Downloaded avatar file path per slot (v0.9.9, W0): the
     *  account switcher can only show a non-current account's
     *  photo from a path remembered while that account was the
     *  running one — its TDLib client is not alive otherwise.
     *  The path points into that slot's own files directory,
     *  which persists across restarts. */
    private val _avatars = MutableStateFlow<Map<Int, String>>(emptyMap())
    val avatars: StateFlow<Map<Int, String>> = _avatars

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = prefs()
        val count = maxOf(1, prefs.getInt(KEY_COUNT, 1))
        _current.value = prefs.getInt(KEY_CURRENT, 0).coerceIn(0, count - 1)
        _slots.value = (0 until count).toList()
        _labels.value = _slots.value.mapNotNull { slot ->
            prefs.getString(labelKey(slot), null)?.let { slot to it }
        }.toMap()
        _avatars.value = _slots.value.mapNotNull { slot ->
            prefs.getString(avatarKey(slot), null)?.let { slot to it }
        }.toMap()
    }

    fun currentSlot(): Int = _current.value

    fun dbDirName(slot: Int = currentSlot()): String =
        if (slot == 0) "tdlib" else "tdlib-$slot"

    fun filesDirName(slot: Int = currentSlot()): String =
        if (slot == 0) "tdlib-files" else "tdlib-files-$slot"

    fun setLabel(slot: Int, label: String) {
        if (label.isEmpty()) return
        prefs().edit().putString(labelKey(slot), label).apply()
        _labels.value = _labels.value + (slot to label)
    }

    fun setAvatarPath(slot: Int, path: String) {
        if (path.isEmpty()) return
        if (_avatars.value[slot] == path) return
        prefs().edit().putString(avatarKey(slot), path).apply()
        _avatars.value = _avatars.value + (slot to path)
    }

    fun avatarPath(slot: Int): String? = _avatars.value[slot]

    /** Reserve a fresh slot; returns its index. The caller is
     *  expected to switch to it (which restarts the process into
     *  a clean login flow). */
    fun addSlot(): Int {
        val next = (_slots.value.maxOrNull() ?: 0) + 1
        val count = next + 1
        prefs().edit().putInt(KEY_COUNT, count).apply()
        _slots.value = (0 until count).toList()
        return next
    }

    fun setCurrent(slot: Int) {
        _current.value = slot
        prefs().edit().putInt(KEY_CURRENT, slot).apply()
    }

    /** A notification tap on a background account (v0.9.10):
     *  the (slot, chat) it wants to open, persisted because
     *  reaching that chat means switching accounts — on a
     *  warm start via the restart-based switch, on a cold
     *  start by booting straight into the slot. TdRepository
     *  consumes it once the target account reaches Ready and
     *  hands the chat to DeepLink, so the app-lock gate still
     *  applies before anything opens. */
    fun setPendingOpen(slot: Int, chatId: Long) {
        prefs().edit()
            .putInt(KEY_PENDING_SLOT, slot)
            .putLong(KEY_PENDING_CHAT, chatId)
            .apply()
    }

    /** Read and clear the pending open, if any. */
    fun consumePendingOpen(): Pair<Int, Long>? {
        val prefs = prefs()
        if (!prefs.contains(KEY_PENDING_SLOT)) return null
        val slot = prefs.getInt(KEY_PENDING_SLOT, -1)
        val chatId = prefs.getLong(KEY_PENDING_CHAT, 0L)
        prefs.edit()
            .remove(KEY_PENDING_SLOT)
            .remove(KEY_PENDING_CHAT)
            .apply()
        return if (slot >= 0 && chatId != 0L) slot to chatId else null
    }

    private fun prefs() =
        appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
