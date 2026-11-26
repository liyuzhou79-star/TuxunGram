package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** One Saved Messages tag, flattened from savedMessagesTag
 *  (verified in td_api.tl): the tag itself is a ReactionType
 *  — in Saved Messages, the reactions on a message ARE its
 *  tags. [label] is the user-given name (Premium-only to
 *  set, may be empty); [count] is how many saved messages
 *  carry the tag. */
data class SavedTagUi(
    val emoji: String,
    val customEmojiId: Long,
    val label: String,
    val count: Int
) {
    val key: String
        get() = if (customEmojiId != 0L) {
            "c$customEmojiId"
        } else {
            "e$emoji"
        }

    /** Chip text: the glyph plus its label when it has one,
     *  else its use count. Custom-emoji tags have no text
     *  glyph here; the label (or a plain word) stands in —
     *  never a substitute emoji. */
    val display: String
        get() {
            val glyph = when {
                emoji.isNotEmpty() -> emoji
                label.isNotEmpty() -> label
                else -> "自定义表情标签"
            }
            return when {
                emoji.isNotEmpty() && label.isNotEmpty() ->
                    "$emoji $label"
                emoji.isNotEmpty() -> "$emoji $count"
                label.isNotEmpty() -> label
                else -> glyph
            }
        }

    /** The ReactionType JSON this tag round-trips as. */
    fun toJson(): JSONObject =
        if (customEmojiId != 0L) {
            JSONObject().apply {
                put("@type", "reactionTypeCustomEmoji")
                put("custom_emoji_id", customEmojiId)
            }
        } else {
            JSONObject().apply {
                put("@type", "reactionTypeEmoji")
                put("emoji", emoji)
            }
        }
}

/**
 * Saved Messages tags (v0.9.62, G2). The whole chain is
 * verified in this build's td_api.tl: getSavedMessagesTags
 * lists the tags, searchSavedMessages filters the saved
 * list by one tag, setSavedMessagesTagLabel renames a tag
 * (the tl states it is for Telegram Premium users only —
 * a refusal is surfaced in plain words, never hidden), and
 * updateSavedMessagesTags keeps the list fresh. Tagging a
 * message needs no new call: in the saved chat it is the
 * ordinary addMessageReaction / removeMessageReaction the
 * bubble menu already uses.
 */
object SavedTagsStore {
    private val _tags = MutableStateFlow<List<SavedTagUi>>(emptyList())
    val tags: StateFlow<List<SavedTagUi>> = _tags

    /** The Saved Messages chat id, resolved through the
     *  same getMe -> createPrivateChat route the chat list
     *  uses; 0 until resolved. The chat screen compares its
     *  own chat id against this to know it is showing the
     *  saved chat. */
    private val _savedChatId = MutableStateFlow(0L)
    val savedChatId: StateFlow<Long> = _savedChatId

    private val _selected = MutableStateFlow<SavedTagUi?>(null)
    val selected: StateFlow<SavedTagUi?> = _selected

    private val _results = MutableStateFlow<List<MsgItem>>(emptyList())
    val results: StateFlow<List<MsgItem>> = _results

    private val _resultsTotal = MutableStateFlow(-1)
    val resultsTotal: StateFlow<Int> = _resultsTotal

    private val _resultsExhausted = MutableStateFlow(true)
    val resultsExhausted: StateFlow<Boolean> = _resultsExhausted

    @Volatile
    private var attached = false

    @Volatile
    private var generation = 0

    private var nextFromId = 0L

    @Volatile
    private var paging = false

    private fun attach() {
        if (attached) return
        attached = true
        TdRepository.addUpdateListener { update ->
            if (update.optString("@type") ==
                "updateSavedMessagesTags" &&
                update.optLong("saved_messages_topic_id") == 0L
            ) {
                _tags.value =
                    parseTags(update.optJSONObject("tags"))
            }
        }
    }

    fun resolveSavedChatId() {
        attach()
        if (_savedChatId.value != 0L) return
        ContactsStore.openSavedMessages { id ->
            if (id != null && id != 0L) _savedChatId.value = id
        }
    }

    fun load() {
        attach()
        val query = TdClient.request("getSavedMessagesTags").apply {
            put("saved_messages_topic_id", 0L)
        }
        TdRepository.sendRaw(query) { response ->
            if (!TdClient.isError(response)) {
                _tags.value = parseTags(response)
            }
        }
    }

    private fun parseTags(root: JSONObject?): List<SavedTagUi> {
        val arr = root?.optJSONArray("tags") ?: return emptyList()
        val out = ArrayList<SavedTagUi>(arr.length())
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            val tag = entry.optJSONObject("tag") ?: continue
            val ui = when (tag.optString("@type")) {
                "reactionTypeEmoji" -> SavedTagUi(
                    emoji = tag.optString("emoji"),
                    customEmojiId = 0L,
                    label = entry.optString("label"),
                    count = entry.optInt("count")
                )
                "reactionTypeCustomEmoji" -> SavedTagUi(
                    emoji = "",
                    customEmojiId = tag.optLong("custom_emoji_id"),
                    label = entry.optString("label"),
                    count = entry.optInt("count")
                )
                else -> continue
            }
            if (ui.emoji.isEmpty() && ui.customEmojiId == 0L) {
                continue
            }
            out.add(ui)
        }
        return out
    }

    /** Select a tag to filter by (null clears the filter)
     *  and start paging its messages. */
    fun select(tag: SavedTagUi?) {
        generation++
        _selected.value = tag
        _results.value = emptyList()
        _resultsTotal.value = -1
        nextFromId = 0L
        paging = false
        _resultsExhausted.value = tag == null
        if (tag != null) pageResults()
    }

    fun loadMoreResults() {
        if (_selected.value == null ||
            _resultsExhausted.value || paging
        ) {
            return
        }
        pageResults()
    }

    private fun pageResults() {
        val tag = _selected.value ?: return
        val gen = generation
        paging = true
        val query = TdClient.request("searchSavedMessages").apply {
            put("saved_messages_topic_id", 0L)
            put("tag", tag.toJson())
            put("query", "")
            put("from_message_id", nextFromId)
            put("offset", 0)
            put("limit", 30)
        }
        TdRepository.sendRaw(query) { response ->
            if (gen != generation) return@sendRaw
            paging = false
            if (TdClient.isError(response)) {
                _resultsExhausted.value = true
                return@sendRaw
            }
            val total = response.optInt("total_count", -1)
            if (total >= 0) _resultsTotal.value = total
            val next = response.optLong("next_from_message_id")
            nextFromId = next
            if (next == 0L) _resultsExhausted.value = true
            val arr = response.optJSONArray("messages")
            if (arr == null || arr.length() == 0) {
                _resultsExhausted.value = true
                return@sendRaw
            }
            val items = ArrayList<MsgItem>(arr.length())
            for (i in 0 until arr.length()) {
                val msg = arr.optJSONObject(i) ?: continue
                items.add(MessageStore.parseMessage(msg))
            }
            _results.value = _results.value + items
        }
    }

    /** Rename a tag. The tl gates this to Premium users;
     *  a refusal is reported to [onDone] in plain words and
     *  the tag keeps its old label — nothing pretends the
     *  rename happened. */
    fun setLabel(
        tag: SavedTagUi,
        label: String,
        onDone: (String?) -> Unit
    ) {
        val query = TdClient.request("setSavedMessagesTagLabel")
            .apply {
                put("tag", tag.toJson())
                put("label", label.trim())
            }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                val message = response.optString("message")
                onDone(
                    if (
                        message.contains(
                            "PREMIUM", ignoreCase = true
                        )
                    ) {
                        "给标签改名是 Telegram Premium 的功能，" +
                            "当前账号用不了，标签名没有改动。"
                    } else {
                        "改名失败，再试一次。（错误码 " +
                            "${response.optInt("code")}）"
                    }
                )
            } else {
                load()
                onDone(null)
            }
        }
    }
}
