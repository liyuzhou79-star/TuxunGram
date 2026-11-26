package com.tuxungram.android.td

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** In-app update download state (v0.9.62, G3). [status] is
 *  idle / downloading / done / error; byte counts ride
 *  along for the progress line (totalBytes -1 while the
 *  server has not said). */
data class UpdateDownloadState(
    val status: String = "idle",
    val version: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val filePath: String? = null,
    val error: String? = null
)

/**
 * Downloads a release APK inside the app (v0.9.62, G3) —
 * the same GitHub release asset the update check found,
 * fetched with plain HttpURLConnection like UpdateChecker
 * (no new dependency), with progress and cancel, into
 * cacheDir/updates. Installing hands the file to the
 * system installer through the existing FileProvider; the
 * REQUEST_INSTALL_PACKAGES permission is only exercised
 * at install time — when the system says this app may not
 * install yet, the UI routes the user to the system's
 * allow-installs page and keeps the downloaded file, so
 * allowing it and tapping 安装 again finishes the job.
 * A cancelled or failed download deletes its partial
 * file; starting a new download sweeps older ones.
 */
object UpdateDownloader {
    private val _state = MutableStateFlow(UpdateDownloadState())
    val state: StateFlow<UpdateDownloadState> = _state

    @Volatile
    private var running = false

    @Volatile
    private var cancelled = false

    fun start(context: Context, version: String, url: String) {
        if (running) return
        running = true
        cancelled = false
        val appContext = context.applicationContext
        _state.value = UpdateDownloadState(
            status = "downloading", version = version
        )
        Thread {
            val dir = File(appContext.cacheDir, "updates")
            val file = File(dir, "TuxunGram-v$version.apk")
            var conn: HttpURLConnection? = null
            try {
                if (dir.exists()) {
                    dir.listFiles()?.forEach { it.delete() }
                }
                dir.mkdirs()
                conn = URL(url).openConnection()
                    as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "TuxunGram")
                if (conn.responseCode != 200) {
                    throw IllegalStateException(
                        "HTTP ${conn.responseCode}"
                    )
                }
                val total = conn.contentLengthLong
                var done = 0L
                var lastReported = 0L
                conn.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (cancelled) break
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (done - lastReported >= 256 * 1024) {
                                lastReported = done
                                _state.value =
                                    UpdateDownloadState(
                                        status = "downloading",
                                        version = version,
                                        downloadedBytes = done,
                                        totalBytes = total
                                    )
                            }
                        }
                    }
                }
                when {
                    cancelled -> {
                        file.delete()
                        _state.value = UpdateDownloadState(
                            status = "idle", version = version
                        )
                    }
                    total > 0 && done != total -> {
                        file.delete()
                        _state.value = UpdateDownloadState(
                            status = "error",
                            version = version,
                            error = "下载没下完（网络中断），" +
                                "再试一次。"
                        )
                    }
                    else -> {
                        _state.value = UpdateDownloadState(
                            status = "done",
                            version = version,
                            downloadedBytes = done,
                            totalBytes = if (total > 0) {
                                total
                            } else {
                                done
                            },
                            filePath = file.absolutePath
                        )
                    }
                }
            } catch (t: Throwable) {
                file.delete()
                if (cancelled) {
                    _state.value = UpdateDownloadState(
                        status = "idle", version = version
                    )
                } else {
                    _state.value = UpdateDownloadState(
                        status = "error",
                        version = version,
                        error = "下载失败：连不上 GitHub 或网络" +
                            "中断，检查网络（或代理）再试一次。"
                    )
                }
            } finally {
                conn?.disconnect()
                running = false
            }
        }.start()
    }

    fun cancel() {
        cancelled = true
    }

    /** May this app hand an APK to the system installer
     *  right now? Below API 26 there is no per-app gate. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 ||
            context.packageManager.canRequestPackageInstalls()

    /** The system's "allow installs from this app" page
     *  for our own package. */
    fun unknownSourcesIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** ACTION_VIEW for the downloaded APK via the existing
     *  FileProvider (cache-path is already in file_paths). */
    fun installIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "com.tuxungram.android.fileprovider",
            file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                uri, "application/vnd.android.package-archive"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
