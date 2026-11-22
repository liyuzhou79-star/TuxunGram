package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One inline result row (v0.9.12, S2), flattened from the
 *  inlineQueryResult* family in td_api.tl (lines 8326–8393).
 *  [thumbFileId] is whatever small file represents the result
 *  visually (article thumbnail, photo's smallest size, an
 *  animation's/sticker's thumbnail); its local path resolves
 *  through AvatarStore's generic file-id map. */
data class InlineResultUi(
    val id: String,
    val kind: String,
    val title: String,
    val description: String = "",
    val thumbFileId: Int = 0
)

/** Live state of the inline-query panel for the open chat. */
data class InlineUi(
    val chatId: Long = 0L,
    val active: Boolean = false,
    val username: String = "",
    val botUserId: Long = 0L,
    val placeholder: String = "",
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val results: List<InlineResultUi> = emptyList(),
    val queryId: Long = 0L,
    val buttonText: String = "",
    /** Bumped whenever AvatarStore lands a new file path, so
     *  thumbnails recompose (StateFlow drops equal values, so
     *  a bare copy() would never emit). */
    val thumbTick: Int = 0
)

/**
 * Inline bot queries (v0.9.12, S2): typing `@botname <query>`
 * in any chat asks that bot for inline results via
 * getInlineQueryResults (td_api.tl line 14226) and sends the
 * tapped one via sendInlineQueryResultMessage (line 13389).
 * The bot is resolved through searchPublicChat -> getUser and
 * must be a bot with userTypeBot.is_inline (line 847);
 * anything else gets a plain-language panel message instead
 * of a silent no-op. Queries are debounced by the chat screen
 * and generation-guarded here, so a slow answer for an older
 * query can never overwrite a newer one.
 */
object InlineStore {

    private val _state = MutableStateFlow(InlineUi())
    val state: StateFlow<InlineUi> = _state

    /** username (lowercase) -> (bot user id, placeholder);
     *  a null value marks "resolved, but not an inline bot"
     *  together with [resolveErrors]. */
    private val resolved = ConcurrentHashMap<String, Pair<Long, String>>()
    private val resolveErrors = ConcurrentHashMap<String, String>()
    private val resolving = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var generation = 0

    /** The "chatId:username" the user dismissed with the
     *  panel's close button; the same key will not reactivate
     *  until the input is cleared or the username changes. */
    @Volatile
    private var dismissedKey: String? = null

    init {
        AvatarStore.addListener {
            val s = _state.value
            if (s.active) {
                _state.value = s.copy(thumbTick = s.thumbTick + 1)
            }
        }
    }

    fun dismiss() {
        val s = _state.value
        dismissedKey = "${s.chatId}:${s.username.lowercase()}"
        generation++
        _state.value = InlineUi(chatId = s.chatId)
    }

    /** Feed the current input shape into the store. Called
     *  (debounced) from the chat screen: [username] == null
     *  means the input no longer looks like an inline query. */
    fun syncInput(chatId: Long, username: String?, query: String?) {
        if (username == null || query == null) {
            if (_state.value.active || _state.value.chatId != chatId) {
                generation++
                _state.value = InlineUi(chatId = chatId)
            }
            if (username == null) dismissedKey = null
            return
        }
        val key = "$chatId:${username.lowercase()}"
        if (dismissedKey == key) return
        val s = _state.value
        if (s.active && s.chatId == chatId &&
            s.username.equals(username, ignoreCase = true) &&
            s.query == query
        ) {
            return
        }
        val gen = ++generation
        val hit = resolved[username.lowercase()]
        val err = resolveErrors[username.lowercase()]
        when {
            hit != null -> {
                _state.value = InlineUi(
                    chatId = chatId, active = true,
                    username = username, botUserId = hit.first,
                    placeholder = hit.second, query = query,
                    loading = true
                )
                runQuery(gen, chatId, hit.first, query)
            }
            err != null -> {
                _state.value = InlineUi(
                    chatId = chatId, active = true,
                    username = username, query = query, error = err
                )
            }
            else -> {
                _state.value = InlineUi(
                    chatId = chatId, active = true,
                    username = username, query = query, loading = true
                )
                resolveBot(gen, chatId, username, query)
            }
        }
    }

    private fun resolveBot(
        gen: Int, chatId: Long, username: String, query: String
    ) {
        val lower = username.lowercase()
        if (!resolving.add(lower)) return
        val fail: (String) -> Unit = { message ->
            resolving.remove(lower)
            resolveErrors[lower] = message
            if (gen == generation && _state.value.username
                    .equals(username, ignoreCase = true)
            ) {
                _state.value = _state.value.copy(
                    loading = false, error = message
                )
            }
        }
        val search = TdClient.request("searchPublicChat").apply {
            put("username", username)
        }
        TdRepository.sendRaw(search) { chat ->
            if (TdClient.isError(chat)) {
                fail("找不到 @$username 这个机器人。")
                return@sendRaw
            }
            val userId = chat.optJSONObject("type")
                ?.takeIf { it.optString("@type") == "chatTypePrivate" }
                ?.optLong("user_id") ?: 0L
            if (userId == 0L) {
                fail("@$username 不是一个机器人。")
                return@sendRaw
            }
            val getUser = TdClient.request("getUser").apply {
                put("user_id", userId)
            }
            TdRepository.sendRaw(getUser) { user ->
                val type = user.optJSONObject("type")
                if (TdClient.isError(user) ||
                    type?.optString("@type") != "userTypeBot"
                ) {
                    fail("@$username 不是一个机器人。")
                    return@sendRaw
                }
                if (!type.optBoolean("is_inline")) {
                    fail("这个机器人不支持内联模式。")
                    return@sendRaw
                }
                resolving.remove(lower)
                val placeholder =
                    type.optString("inline_query_placeholder")
                resolved[lower] = userId to placeholder
                if (gen == generation) {
                    _state.value = _state.value.copy(
                        botUserId = userId, placeholder = placeholder,
                        loading = true, error = null
                    )
                    runQuery(gen, chatId, userId, query)
                }
            }
        }
    }

    private fun runQuery(
        gen: Int, chatId: Long, botUserId: Long, query: String
    ) {
        val request = TdClient.request("getInlineQueryResults").apply {
            put("bot_user_id", botUserId)
            put("chat_id", chatId)
            put("user_location", JSONObject.NULL)
            put("query", query)
            put("offset", "")
        }
        TdRepository.sendRaw(request) { response ->
            if (gen != generation) return@sendRaw
            if (TdClient.isError(response)) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "查询失败，检查网络后再试一次。",
                    results = emptyList()
                )
                return@sendRaw
            }
            val results = ArrayList<InlineResultUi>()
            val arr = response.optJSONArray("results")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    parseResult(item)?.let { results.add(it) }
                }
            }
            _state.value = _state.value.copy(
                loading = false, error = null, results = results,
                queryId = response.optLong("inline_query_id"),
                buttonText = response.optJSONObject("button")
                    ?.optString("text") ?: ""
            )
        }
    }

    private fun parseResult(item: JSONObject): InlineResultUi? {
        val id = item.optString("id")
        if (id.isEmpty()) return null
        fun thumbOf(obj: JSONObject?): Int {
            val file = obj?.optJSONObject("file") ?: return 0
            AvatarStore.noteFile(file)
            val fid = file.optInt("id", 0)
            if (fid != 0) AvatarStore.ensureDownload(fid)
            return fid
        }
        return when (item.optString("@type")) {
            "inlineQueryResultArticle" -> InlineResultUi(
                id, "article",
                item.optString("title"),
                item.optString("description"),
                thumbOf(item.optJSONObject("thumbnail"))
            )
            "inlineQueryResultPhoto" -> {
                val sizes = item.optJSONObject("photo")
                    ?.optJSONArray("sizes")
                var thumbId = 0
                if (sizes != null && sizes.length() > 0) {
                    val file = sizes.optJSONObject(0)
                        ?.optJSONObject("photo")
                    if (file != null) {
                        AvatarStore.noteFile(file)
                        thumbId = file.optInt("id", 0)
                        if (thumbId != 0) {
                            AvatarStore.ensureDownload(thumbId)
                        }
                    }
                }
                InlineResultUi(
                    id, "photo",
                    item.optString("title"),
                    item.optString("description"),
                    thumbId
                )
            }
            "inlineQueryResultAnimation" -> {
                val anim = item.optJSONObject("animation")
                val thumb = thumbOf(
                    anim?.optJSONObject("thumbnail")
                )
                InlineResultUi(
                    id, "animation",
                    item.optString("title").ifEmpty { "GIF" },
                    "", thumb
                )
            }
            "inlineQueryResultSticker" -> {
                val sticker = item.optJSONObject("sticker")
                val thumb = thumbOf(
                    sticker?.optJSONObject("thumbnail")
                )
                InlineResultUi(id, "sticker", "贴纸", "", thumb)
            }
            "inlineQueryResultVideo" -> {
                val video = item.optJSONObject("video")
                InlineResultUi(
                    id, "video", item.optString("title"), "",
                    thumbOf(video?.optJSONObject("thumbnail"))
                )
            }
            "inlineQueryResultAudio" -> {
                val audio = item.optJSONObject("audio")
                val title = audio?.optString("title")
                    ?.ifEmpty { null }
                    ?: audio?.optString("file_name")
                    ?: "音频"
                InlineResultUi(id, "audio", title, "")
            }
            "inlineQueryResultDocument" -> InlineResultUi(
                id, "document",
                item.optString("title"),
                item.optString("description")
            )
            "inlineQueryResultVoiceNote" -> InlineResultUi(
                id, "voiceNote",
                item.optString("title").ifEmpty { "语音" }
            )
            "inlineQueryResultContact" -> {
                val contact = item.optJSONObject("contact")
                val name = listOf(
                    contact?.optString("first_name") ?: "",
                    contact?.optString("last_name") ?: ""
                ).joinToString(" ").trim()
                InlineResultUi(
                    id, "contact",
                    name.ifEmpty { "联系人" },
                    contact?.optString("phone_number") ?: "",
                    thumbOf(item.optJSONObject("thumbnail"))
                )
            }
            "inlineQueryResultLocation" -> InlineResultUi(
                id, "location",
                item.optString("title").ifEmpty { "位置" },
                "",
                thumbOf(item.optJSONObject("thumbnail"))
            )
            "inlineQueryResultVenue" -> {
                val venue = item.optJSONObject("venue")
                InlineResultUi(
                    id, "venue",
                    venue?.optString("title")?.ifEmpty { "地点" }
                        ?: "地点",
                    venue?.optString("address") ?: "",
                    thumbOf(item.optJSONObject("thumbnail"))
                )
            }
            "inlineQueryResultGame" -> InlineResultUi(
                id, "game",
                item.optJSONObject("game")?.optString("title")
                    ?.ifEmpty { "游戏" } ?: "游戏"
            )
            else -> null
        }
    }

    /** Send the tapped result into the chat the query ran in.
     *  [onSent] fires only on success so the screen can clear
     *  the input; a refusal keeps the panel and says so. */
    fun send(result: InlineResultUi, onSent: () -> Unit) {
        val s = _state.value
        if (!s.active || s.queryId == 0L) return
        val query = TdClient.request("sendInlineQueryResultMessage")
            .apply {
                put("chat_id", s.chatId)
                put("query_id", s.queryId)
                put("result_id", result.id)
                put("hide_via_bot", false)
            }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _state.value = _state.value.copy(
                    error = "这条结果没发出去，再试一次。"
                )
            } else {
                generation++
                _state.value = InlineUi(chatId = s.chatId)
                onSent()
            }
        }
    }
}
