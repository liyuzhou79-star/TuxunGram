package com.tuxungram.android.td

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Small display preferences, persisted in SharedPreferences and
 * exposed as StateFlows so the UI re-renders the moment they change.
 * Currently: the unread-badge cap (99 / 999 / 9999, default 999) —
 * the same option mature third-party Telegram clients offer, since
 * channel-heavy accounts routinely blow past two digits.
 */
object UiSettings {
    private const val PREFS = "ui"
    private const val KEY_UNREAD_CAP = "unread_cap"

    val UNREAD_CAPS = listOf(99, 999, 9999)

    private val _unreadCap = MutableStateFlow(999)
    val unreadCap: StateFlow<Int> = _unreadCap

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getInt(KEY_UNREAD_CAP, 999)
        _unreadCap.value = if (saved in UNREAD_CAPS) saved else 999
    }

    fun setUnreadCap(cap: Int) {
        if (cap !in UNREAD_CAPS) return
        _unreadCap.value = cap
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_UNREAD_CAP, cap)
            .apply()
    }

    /** Badge text for an unread count under the current cap. */
    fun badgeText(unreadCount: Int): String {
        val cap = _unreadCap.value
        return if (unreadCount > cap) "$cap+" else unreadCount.toString()
    }
}
