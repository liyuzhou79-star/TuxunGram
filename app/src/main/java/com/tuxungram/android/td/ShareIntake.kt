package com.tuxungram.android.td

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** One file handed to the app by another app's share sheet
 *  (v0.9.52, W5), already copied into our cache: the sender's
 *  content URI is only guaranteed readable while the share
 *  intent is being handled, so the bytes are ours before the
 *  user even picks a chat. */
data class SharedFile(
    val path: String,
    val mimeType: String,
    val fileName: String
)

/** External content waiting for a target chat (v0.9.52, W5):
 *  plain text, files, or both. */
data class SharedContent(
    val text: String,
    val files: List<SharedFile>
)

/**
 * Inbound system sharing (v0.9.52, W5). Before this version
 * the manifest carried no ACTION_SEND filter at all, so the
 * app never appeared in other apps' share sheets and shared
 * content had nowhere to land. [capture] runs from the
 * activity's onCreate / onNewIntent: it reads the text extra
 * directly and copies every stream URI into cacheDir on a
 * background thread, then publishes [pending]; ReadyHost
 * shows the chat picker and MessageStore.sendSharedTo does
 * the sending. A capture with neither text nor a readable
 * file publishes nothing.
 */
object ShareIntake {

    val pending = MutableStateFlow<SharedContent?>(null)

    fun capture(context: Context, intent: Intent?): Boolean {
        val action = intent?.action ?: return false
        if (action != Intent.ACTION_SEND &&
            action != Intent.ACTION_SEND_MULTIPLE
        ) {
            return false
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?.trim().orEmpty()
        val uris = ArrayList<Uri>()
        @Suppress("DEPRECATION")
        if (action == Intent.ACTION_SEND) {
            val one = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (one != null) uris.add(one)
        } else {
            val many =
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            if (many != null) uris.addAll(many)
        }
        if (text.isEmpty() && uris.isEmpty()) return false
        val appContext = context.applicationContext
        val intentType = intent.type ?: ""
        Thread {
            sweepOldCopies(appContext)
            val files = ArrayList<SharedFile>()
            for (uri in uris) {
                copyOne(appContext, uri, intentType)?.let {
                    files.add(it)
                }
            }
            if (text.isEmpty() && files.isEmpty()) {
                return@Thread
            }
            pending.value = SharedContent(text, files)
        }
        return true
    }

    private fun copyOne(
        context: Context,
        uri: Uri,
        intentType: String
    ): SharedFile? {
        return try {
            val resolver = context.contentResolver
            var name = ""
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )
                if (idx >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(idx).orEmpty()
                }
            }
            if (name.isEmpty()) {
                name = "shared_" + System.currentTimeMillis()
            }
            var mime = resolver.getType(uri)
                ?: intentType.ifEmpty { null }
                ?: ""
            if (mime.isEmpty() || mime.endsWith("/*")) {
                val ext = name.substringAfterLast('.', "")
                    .lowercase()
                mime = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(ext)
                    ?: "application/octet-stream"
            }
            val dir = File(context.cacheDir, "share_in").apply {
                mkdirs()
            }
            // The timestamp prefix keeps two shares of the
            // same file name from overwriting each other
            // before the first one is sent.
            val out = File(
                dir,
                System.currentTimeMillis().toString() + "_" + name
            )
            val copied = resolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output ->
                    input.copyTo(output)
                }
                true
            } ?: false
            if (!copied || !out.exists() || out.length() == 0L) {
                null
            } else {
                SharedFile(out.absolutePath, mime, name)
            }
        } catch (t: Throwable) {
            null
        }
    }

    fun clear() {
        pending.value = null
    }

    /** The user backed out of the picker without sending
     *  (v0.9.56, A3): the copied files were never handed to
     *  TDLib, so they are deleted outright. Sent content
     *  goes through [clear] instead — TDLib keeps reading
     *  the local path while the upload runs, so a sent
     *  share's copies are only reclaimed by the age sweep
     *  in [sweepOldCopies]. Before this split, NOTHING ever
     *  deleted the copies and share_in grew forever. */
    fun discard() {
        val content = pending.value
        pending.value = null
        if (content != null && content.files.isNotEmpty()) {
            Thread {
                for (file in content.files) {
                    try {
                        File(file.path).delete()
                    } catch (t: Throwable) {
                        // Already gone — nothing to reclaim.
                    }
                }
            }
        }
    }

    /** Reclaim copies of shares that were sent long ago:
     *  anything in share_in older than 48 hours has had
     *  far more time than an upload needs, and once an
     *  upload finishes TDLib keeps its own copy in its file
     *  store anyway. Runs on capture's background thread.
     *  The files of the share currently waiting in the
     *  picker are never swept (v0.9.57, B2) — they are
     *  still waiting to be sent by definition. One edge
     *  stays accepted
     *  and is recorded here honestly: a copy already
     *  handed to TDLib whose upload is somehow still
     *  queued past the 48-hour line when a later capture
     *  runs the sweep; an upload pending that long has in
     *  practice already failed (and a failed send can be
     *  re-shared from the source app), while the picker
     *  case above is the one a user can actually hit. */
    private fun sweepOldCopies(context: Context) {
        try {
            val dir = File(context.cacheDir, "share_in")
            val cutoff = System.currentTimeMillis() -
                48L * 60 * 60 * 1000
            val keep = pending.value?.files
                ?.map { it.path }?.toSet() ?: emptySet()
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoff &&
                    file.absolutePath !in keep
                ) {
                    file.delete()
                }
            }
        } catch (t: Throwable) {
            // A failed sweep only delays the reclaim.
        }
    }
}
