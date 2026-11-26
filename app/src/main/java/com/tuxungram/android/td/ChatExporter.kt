package com.tuxungram.android.td

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Export progress / result for the profile page's dialog
 *  (v0.9.12, S4). [donePath] is a temp file in cacheDir; the
 *  page copies it to the user-picked document and the temp
 *  file is disposable. */
data class ExportState(
    val chatId: Long = 0L,
    val running: Boolean = false,
    val count: Int = 0,
    val donePath: String? = null,
    val doneCount: Int = 0,
    val error: String? = null,
    // The export stopped at MAX_LINES while older history
    // remained (v0.9.62, G4): the file and the dialog both
    // say so — a truncated export never looks complete.
    val truncated: Boolean = false
)

private data class ExportLine(
    val dateSec: Int,
    val senderKey: String,
    val text: String,
    val system: Boolean = false
)

/**
 * Chat-history export (v0.9.12, S4): pages getChatHistory
 * under the same D1 stop rule as the message list (history
 * ends only when a page adds nothing older — a short page is
 * NOT the end), summarises every message to one text line
 * (media become bracketed placeholders; file bodies are never
 * copied), resolves sender names once per distinct sender,
 * and writes a plain .txt. Deliberately independent of
 * MessageStore: its parseMessage auto-kicks photo downloads,
 * which a text export must not do. Progress and cancellation
 * ride the state flow; a cancelled or failed export writes
 * nothing. v0.9.62 (G4): service messages reuse the chat's
 * own service wording via servicePreviewText (cache reads
 * only, no parsing), and the pull is capped at MAX_LINES
 * with the truncation stated in the file and the dialog.
 */
object ChatExporter {

    /** Hard line cap (v0.9.62, G4): even "全部记录" stops
     *  here. The lines are held in memory until written, so
     *  an unbounded pull of a million-message chat could
     *  take the process down; 100k lines is far beyond any
     *  realistic read-back and the truncation is stated in
     *  the file header and the dialog, never silent. */
    const val MAX_LINES = 100_000

    private val _state = MutableStateFlow(ExportState())
    val state: StateFlow<ExportState> = _state

    @Volatile
    private var cancelled = false

    @Volatile
    private var generation = 0

    fun cancel() {
        cancelled = true
    }

    fun reset() {
        generation++
        _state.value = ExportState()
    }

    fun start(
        context: Context,
        chatId: Long,
        title: String,
        limit: Int
    ) {
        val gen = ++generation
        cancelled = false
        _state.value = ExportState(chatId = chatId, running = true)
        val lines = ArrayList<ExportLine>()
        page(context, gen, chatId, title, limit, 0L, lines)
    }

    private fun page(
        context: Context,
        gen: Int,
        chatId: Long,
        title: String,
        limit: Int,
        fromId: Long,
        lines: ArrayList<ExportLine>
    ) {
        if (gen != generation) return
        if (cancelled) {
            _state.value = ExportState(
                chatId = chatId, error = "已取消导出，没有生成文件。"
            )
            return
        }
        val query = TdClient.request("getChatHistory").apply {
            put("chat_id", chatId)
            put("from_message_id", fromId)
            put("offset", 0)
            put("limit", 100)
            put("only_local", false)
        }
        TdRepository.sendRaw(query) { response ->
            if (gen != generation) return@sendRaw
            if (TdClient.isError(response)) {
                _state.value = ExportState(
                    chatId = chatId,
                    error = "导出失败：这个对话的历史暂时拉不下来，检查网络后再试。"
                )
                return@sendRaw
            }
            val arr = response.optJSONArray("messages")
            var addedOlder = false
            var oldest = fromId
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val msg = arr.optJSONObject(i) ?: continue
                    val id = msg.optLong("id")
                    if (fromId == 0L || id < fromId) addedOlder = true
                    if (oldest == 0L || id < oldest) oldest = id
                    summarize(msg)?.let { lines.add(it) }
                }
            }
            _state.value = ExportState(
                chatId = chatId, running = true, count = lines.size
            )
            val hitCap = lines.size >= MAX_LINES
            val reachedLimit =
                (limit > 0 && lines.size >= limit) || hitCap
            if (!addedOlder || reachedLimit || arr == null ||
                arr.length() == 0
            ) {
                // Truncated only when the cap — not the
                // user's own range choice and not the true
                // end of history — is what stopped the pull.
                finish(
                    context, gen, chatId, title, limit, lines,
                    truncated = hitCap && addedOlder &&
                        (limit == 0 || limit > MAX_LINES)
                )
            } else {
                page(context, gen, chatId, title, limit, oldest, lines)
            }
        }
    }

    /** Resolve each distinct sender once, then write the
     *  file newest-page collection reversed into chronological
     *  order, trimmed to the newest [limit] when limited. */
    private fun finish(
        context: Context,
        gen: Int,
        chatId: Long,
        title: String,
        limit: Int,
        lines: ArrayList<ExportLine>,
        truncated: Boolean
    ) {
        if (gen != generation) return
        if (cancelled) {
            _state.value = ExportState(
                chatId = chatId, error = "已取消导出，没有生成文件。"
            )
            return
        }
        val effectiveLimit = if (limit > 0) {
            minOf(limit, MAX_LINES)
        } else {
            MAX_LINES
        }
        val trimmed = if (lines.size > effectiveLimit) {
            // Pages arrived newest-first, so the head of the
            // list is the newest slice the user asked for.
            ArrayList(lines.subList(0, effectiveLimit))
        } else {
            lines
        }
        trimmed.reverse()
        val senderKeys = trimmed.map { it.senderKey }
            .filter { it.isNotEmpty() }.distinct()
        if (senderKeys.isEmpty()) {
            writeFile(
                context, gen, chatId, title, trimmed,
                emptyMap(), truncated
            )
            return
        }
        val names = HashMap<String, String>()
        val remaining = AtomicInteger(senderKeys.size)
        for (key in senderKeys) {
            val done: (String) -> Unit = { name ->
                synchronized(names) { names[key] = name }
                if (remaining.decrementAndGet() == 0) {
                    writeFile(
                        context, gen, chatId, title, trimmed,
                        names, truncated
                    )
                }
            }
            if (key.startsWith("u")) {
                val userId = key.drop(1).toLongOrNull() ?: 0L
                val query = TdClient.request("getUser").apply {
                    put("user_id", userId)
                }
                TdRepository.sendRaw(query) { user ->
                    val name = if (TdClient.isError(user)) {
                        "用户 $userId"
                    } else {
                        val full = listOf(
                            user.optString("first_name"),
                            user.optString("last_name")
                        ).joinToString(" ").trim()
                        full.ifEmpty { "用户 $userId" }
                    }
                    done(name)
                }
            } else {
                val senderChatId = key.drop(1).toLongOrNull() ?: 0L
                val query = TdClient.request("getChat").apply {
                    put("chat_id", senderChatId)
                }
                TdRepository.sendRaw(query) { chat ->
                    val name = if (TdClient.isError(chat)) {
                        "对话 $senderChatId"
                    } else {
                        chat.optString("title")
                            .ifEmpty { "对话 $senderChatId" }
                    }
                    done(name)
                }
            }
        }
    }

    private fun writeFile(
        context: Context,
        gen: Int,
        chatId: Long,
        title: String,
        lines: List<ExportLine>,
        names: Map<String, String>,
        truncated: Boolean
    ) {
        Thread {
            try {
                val file = File(
                    context.cacheDir,
                    "chat_export_${chatId}_${System.currentTimeMillis()}.txt"
                )
                val timeFmt = SimpleDateFormat(
                    "yyyy-MM-dd HH:mm", Locale.getDefault()
                )
                file.bufferedWriter().use { out ->
                    out.write("TuxunGram 聊天记录导出")
                    out.newLine()
                    out.write("对话：$title")
                    out.newLine()
                    out.write(
                        "导出时间：" + SimpleDateFormat(
                            "yyyy-MM-dd HH:mm", Locale.getDefault()
                        ).format(Date())
                    )
                    out.newLine()
                    out.write("共 ${lines.size} 条")
                    out.newLine()
                    if (truncated) {
                        out.write(
                            "注意：这个对话的记录超过了导出上限 " +
                                "$MAX_LINES 条，本文件只包含最近的 " +
                                "$MAX_LINES 条，更早的记录没有包含进来。"
                        )
                        out.newLine()
                    }
                    out.write(
                        "说明：图片、视频、文件等媒体只保留一行占位说明，" +
                            "不包含文件本身。"
                    )
                    out.newLine()
                    out.newLine()
                    for (line in lines) {
                        val stamp = timeFmt.format(
                            Date(line.dateSec * 1000L)
                        )
                        if (line.system) {
                            out.write("[$stamp] —— ${line.text}")
                        } else {
                            val name = names[line.senderKey]
                                ?: line.senderKey
                            out.write("[$stamp] $name：${line.text}")
                        }
                        out.newLine()
                    }
                }
                if (gen == generation && !cancelled) {
                    _state.value = ExportState(
                        chatId = chatId,
                        donePath = file.absolutePath,
                        doneCount = lines.size,
                        truncated = truncated
                    )
                } else if (gen == generation) {
                    file.delete()
                    _state.value = ExportState(
                        chatId = chatId,
                        error = "已取消导出，没有生成文件。"
                    )
                }
            } catch (t: Throwable) {
                if (gen == generation) {
                    _state.value = ExportState(
                        chatId = chatId,
                        error = "导出失败：文件写不出来（手机存储可能满了）。"
                    )
                }
            }
        }.start()
    }

    private fun formattedText(obj: JSONObject?, field: String): String =
        obj?.optJSONObject(field)?.optString("text") ?: ""

    /** One message -> one export line (or null to skip a
     *  content type that carries no exportable trace). Media
     *  are placeholders by design. */
    private fun summarize(msg: JSONObject): ExportLine? {
        val content = msg.optJSONObject("content") ?: return null
        val dateSec = msg.optInt("date")
        val sender = msg.optJSONObject("sender_id")
        val senderKey = when (sender?.optString("@type")) {
            "messageSenderUser" -> "u${sender.optLong("user_id")}"
            "messageSenderChat" -> "c${sender.optLong("chat_id")}"
            else -> ""
        }
        val type = content.optString("@type")
        val caption = formattedText(content, "caption")
        fun withCaption(base: String): String =
            if (caption.isEmpty()) base else "$base $caption"
        val text: String
        var system = false
        // Service family (v0.9.62, G4): the export line is
        // the same sentence the chat itself shows, composed
        // by MessageStore's service wording — until now the
        // export reduced almost every service message to a
        // bare 「服务消息」. That wording reads only name
        // caches (no parsing, no downloads), so the
        // exporter's independence rule still holds.
        if (MessageStore.isServiceContentType(type)) {
            val line = MessageStore.servicePreviewText(msg)
            return ExportLine(
                dateSec, senderKey,
                line.ifEmpty { "服务消息" }, system = true
            )
        }
        when (type) {
            "messageText" -> {
                text = formattedText(content, "text")
                if (text.isEmpty()) return null
            }
            "messagePhoto" -> text = withCaption("[图片]")
            "messageVideo" -> text = withCaption("[视频]")
            "messageVideoNote" -> text = "[视频消息]"
            "messageDice" -> {
                val emoji = content.optString("emoji")
                val value = content.optInt("value")
                text = if (value > 0) {
                    "[骰子 $emoji 点数 $value]"
                } else {
                    "[骰子 $emoji]"
                }
            }
            "messageGame" -> {
                val title = content.optJSONObject("game")
                    ?.optString("title") ?: ""
                text = if (title.isEmpty()) "[游戏]" else "[游戏 $title]"
            }
            "messageAnimation" -> text = withCaption("[GIF]")
            "messageDocument" -> {
                val name = content.optJSONObject("document")
                    ?.optString("file_name") ?: ""
                text = withCaption(
                    if (name.isEmpty()) "[文件]" else "[文件 $name]"
                )
            }
            "messageVoiceNote" -> {
                val dur = content.optJSONObject("voice_note")
                    ?.optInt("duration") ?: 0
                text = withCaption("[语音 ${dur}秒]")
            }
            "messageAudio" -> {
                val audioTitle = content.optJSONObject("audio")
                    ?.optString("title") ?: ""
                text = withCaption(
                    if (audioTitle.isEmpty()) {
                        "[音频]"
                    } else {
                        "[音频 $audioTitle]"
                    }
                )
            }
            "messageSticker" -> {
                val emoji = content.optJSONObject("sticker")
                    ?.optString("emoji") ?: ""
                text = if (emoji.isEmpty()) {
                    "[贴纸]"
                } else {
                    "[贴纸 $emoji]"
                }
            }
            "messagePoll" -> {
                val question = content.optJSONObject("poll")
                    ?.optJSONObject("question")
                    ?.optString("text") ?: ""
                text = if (question.isEmpty()) {
                    "[投票]"
                } else {
                    "[投票] $question"
                }
            }
            "messageLocation" -> {
                val loc = content.optJSONObject("location")
                text = "[位置 ${loc?.optDouble("latitude") ?: 0.0}, " +
                    "${loc?.optDouble("longitude") ?: 0.0}]"
            }
            "messageContact" -> {
                val contact = content.optJSONObject("contact")
                val name = listOf(
                    contact?.optString("first_name") ?: "",
                    contact?.optString("last_name") ?: ""
                ).joinToString(" ").trim()
                text = "[联系人 $name " +
                    "${contact?.optString("phone_number") ?: ""}]"
            }
            "messageCall" -> text = "[通话]"
            "messageChatChangeTitle" -> {
                system = true
                text = "群名改成了「${content.optString("title")}」"
            }
            "messageChatChangePhoto" -> {
                system = true
                text = "更换了群头像"
            }
            "messageChatAddMembers" -> {
                system = true
                text = "有新成员加入"
            }
            "messageChatJoinByLink" -> {
                system = true
                text = "通过邀请链接加入"
            }
            "messagePinMessage" -> {
                system = true
                text = "置顶了一条消息"
            }
            else -> {
                if (type.startsWith("messageChat") ||
                    type.startsWith("messageBasicGroup") ||
                    type.startsWith("messageSupergroup")
                ) {
                    system = true
                    text = "服务消息"
                } else {
                    text = "[其他消息]"
                }
            }
        }
        return ExportLine(dateSec, senderKey, text, system)
    }
}
