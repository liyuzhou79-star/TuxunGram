package com.tuxungram.android.td

import android.content.Context
import android.text.format.DateFormat
import java.util.Date

/** Time-of-day formatting that follows the system 12/24-hour
 *  setting (v0.9.51, V5). Before this, every chat-surface clock
 *  was hardcoded "HH:mm", so a phone set to 12-hour time still
 *  showed 24-hour times here. [DateFormat.getTimeFormat] is
 *  the platform formatter that honours that setting (and the
 *  locale's AM/PM placement); without a context (never in
 *  practice after init) we fall back to the old 24-hour shape.
 *  All timestamps are epoch-based and rendered through
 *  java.util.Date/Calendar in the device's current timezone,
 *  so travelling across timezones needs no conversion here —
 *  the phone's zone is the display zone, like the official
 *  clients. */
object TimeFmt {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** "15:04" or "3:04 PM", per the system setting. */
    fun timeOfDay(dateSeconds: Long): String {
        val date = Date(dateSeconds * 1000L)
        val ctx = appContext
        return if (ctx != null) {
            DateFormat.getTimeFormat(ctx).format(date)
        } else {
            java.text.SimpleDateFormat(
                "HH:mm", java.util.Locale.getDefault()
            ).format(date)
        }
    }
}
