package com.tuxungram.android.td

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Result of an update check (v0.9.5, O5). [state] is one
 *  of idle / checking / latest / available / error; the rest
 *  is filled for "available" (and latestVersion for
 *  "latest"). */
data class UpdateInfo(
    val state: String = "idle",
    val latestVersion: String = "",
    val notes: String = "",
    val url: String = ""
)

/**
 * In-app update check (v0.9.5, O5): compares the running
 * versionName against the newest GitHub release of
 * liyuzhou79-star/TuxunGram (tags as a fallback when no
 * release exists yet). This is the app's ONLY network call
 * outside TDLib, and it happens only when the user taps
 * 检查更新 on the About block — nothing polls in the
 * background. Plain HttpURLConnection, no new dependency.
 */
object UpdateChecker {
    private const val REPO = "liyuzhou79-star/TuxunGram"

    private val _state = MutableStateFlow(UpdateInfo())
    val state: StateFlow<UpdateInfo> = _state

    @Volatile
    private var checking = false

    fun check(currentVersion: String) {
        if (checking) return
        checking = true
        _state.value = UpdateInfo(state = "checking")
        Thread {
            val result = try {
                fetch(currentVersion)
            } catch (t: Throwable) {
                UpdateInfo(state = "error")
            }
            Handler(Looper.getMainLooper()).post {
                checking = false
                _state.value = result
            }
        }.start()
    }

    private fun fetch(currentVersion: String): UpdateInfo {
        val release = getJson(
            "https://api.github.com/repos/$REPO/releases/latest"
        )
        if (release != null) {
            val tag = release.optString("tag_name").removePrefix("v")
            if (tag.isNotEmpty()) {
                return compare(
                    currentVersion, tag,
                    release.optString("body"),
                    release.optString("html_url")
                )
            }
        }
        val tags = getJsonArray("https://api.github.com/repos/$REPO/tags")
        val tag = tags?.optJSONObject(0)?.optString("name")
            ?.removePrefix("v") ?: ""
        if (tag.isEmpty()) return UpdateInfo(state = "error")
        return compare(
            currentVersion, tag, "",
            "https://github.com/$REPO/releases"
        )
    }

    private fun compare(
        currentVersion: String,
        latest: String,
        notes: String,
        url: String
    ): UpdateInfo =
        if (isNewer(latest, currentVersion)) {
            UpdateInfo(
                state = "available",
                latestVersion = latest,
                notes = notes.trim().take(600),
                url = url
            )
        } else {
            UpdateInfo(state = "latest", latestVersion = latest)
        }

    /** Numeric dotted-version compare ("0.9.10" > "0.9.5"). */
    private fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split(".").map { it.toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun getJson(url: String): JSONObject? {
        val text = getText(url) ?: return null
        return try {
            JSONObject(text)
        } catch (t: Throwable) {
            null
        }
    }

    private fun getJsonArray(url: String): JSONArray? {
        val text = getText(url) ?: return null
        return try {
            JSONArray(text)
        } catch (t: Throwable) {
            null
        }
    }

    private fun getText(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "TuxunGram")
            conn.setRequestProperty(
                "Accept", "application/vnd.github+json"
            )
            if (conn.responseCode != 200) {
                null
            } else {
                conn.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (t: Throwable) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
