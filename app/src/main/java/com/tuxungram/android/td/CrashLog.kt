package com.tuxungram.android.td

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash-log fallback (v0.9.6, P5): a global uncaught-
 * exception recorder so a crash on a user's phone leaves
 * something readable behind instead of vanishing.
 *
 * Contract, deliberately narrow:
 * - It RECORDS and then gets out of the way: the previous
 *   default handler still runs, so the crash itself behaves
 *   exactly as it would without this class (same dialog,
 *   same process death). Nothing is caught-and-continued.
 * - Storage is the app's private directory only, the newest
 *   3 crashes, each truncated — and nothing is ever uploaded
 *   anywhere. The settings 关于 page says so in plain words
 *   next to the entry that views them.
 */
object CrashLog {

    private const val DIR_NAME = "crash-logs"
    private const val MAX_FILES = 3
    private const val MAX_CHARS = 16_000

    @Volatile
    private var installed = false

    fun install(context: Context, versionName: String) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                write(appContext, versionName, thread, error)
            } catch (t: Throwable) {
                // Recording must never make a crash worse.
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            }
        }
    }

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun write(
        context: Context,
        versionName: String,
        thread: Thread,
        error: Throwable
    ) {
        val now = System.currentTimeMillis()
        val stamp = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss", Locale.US
        ).format(Date(now))
        val text = buildString {
            append("TuxunGram ").append(versionName).append('\n')
            append("time: ").append(stamp).append('\n')
            append("thread: ").append(thread.name).append("\n\n")
            append(Log.getStackTraceString(error))
        }.take(MAX_CHARS)
        val folder = dir(context)
        File(folder, "crash-$now.txt").writeText(text)
        // Keep only the newest few; crash logs are a fallback,
        // not an archive.
        val files = folder.listFiles()
            ?.filter { it.name.startsWith("crash-") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        for (old in files.drop(MAX_FILES)) {
            try {
                old.delete()
            } catch (t: Throwable) {
            }
        }
    }

    /** Recorded crashes, newest first. */
    fun files(context: Context): List<File> {
        val folder = dir(context)
        return folder.listFiles()
            ?.filter { it.name.startsWith("crash-") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun read(file: File): String = try {
        file.readText().take(MAX_CHARS)
    } catch (t: Throwable) {
        ""
    }

    fun clear(context: Context) {
        val folder = dir(context)
        folder.listFiles()?.forEach {
            try {
                it.delete()
            } catch (t: Throwable) {
            }
        }
    }

    // ---- Unseen-record marker (v0.9.42, K4) ----
    // A record counts as unseen while the newest file on
    // disk is newer than the watermark the user last
    // acknowledged by opening the records dialog. The
    // watermark is a plain timestamp in this app's own
    // preferences — nothing about the records leaves the
    // device, and there is no popup: the settings crash
    // row and the Me page's About entry just carry a dot.

    private const val SEEN_PREFS = "crash_log_seen"
    private const val KEY_SEEN_TS = "seen_ts"

    /** True when a crash was recorded since the user last
     *  looked at the records. */
    fun hasUnseen(context: Context): Boolean {
        val newest = files(context).firstOrNull() ?: return false
        val seen = context
            .getSharedPreferences(SEEN_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_SEEN_TS, 0L)
        return newest.lastModified() > seen
    }

    /** Acknowledge everything currently on disk (called
     *  when the records dialog opens and after a clear). */
    fun markSeen(context: Context) {
        context.getSharedPreferences(SEEN_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SEEN_TS, System.currentTimeMillis())
            .apply()
    }
}
