package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** One search result row: enough to render and to navigate to
 *  the message (open the chat, then locate the message). */
data class SearchHit(
    val chatId: Long,
    val chatTitle: String,
    val messageId: Long,
    val summary: String,
    val dateSeconds: Int,
    val outgoing: Boolean
)

/** One public-chat search result (searchPublicChats,
 *  v0.9.36, C4): a chat the user may not have joined
 *  yet. The function returns a Chats object — a total
 *  count plus chat ids — so the display fields are read
 *  back from ChatStore's cache, which the accompanying
 *  updateNewChat objects feed. [kind] is "user",
 *  "group" or "channel" from the flattened ChatItem. */
data class PublicHit(
    val chatId: Long,
    val title: String,
    val kind: String
)

/**
 * Global message search (searchMessages, verified in
 * td_api.tl): paging uses TDLib's opaque string offset, which is
 * passed back verbatim — it is NOT the int53 cursor the
 * per-chat search uses.
 */
object SearchStore {
    private val _results = MutableStateFlow<List<SearchHit>>(emptyList())
    val results: StateFlow<List<SearchHit>> = _results

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    private val _exhausted = MutableStateFlow(true)
    val exhausted: StateFlow<Boolean> = _exhausted

    private val _publicResults =
        MutableStateFlow<List<PublicHit>>(emptyList())
    val publicResults: StateFlow<List<PublicHit>> = _publicResults

    private val _publicSearching = MutableStateFlow(false)
    val publicSearching: StateFlow<Boolean> = _publicSearching

    /** Total public matches as the server reported them
     *  (Chats.total_count); 0 = unknown. The id list can
     *  be shorter than this — the UI words it that way. */
    private val _publicTotal = MutableStateFlow(0)
    val publicTotal: StateFlow<Int> = _publicTotal

    private var query = ""
    private var nextOffset = ""

    /** The searchMessages filter the message paging runs
     *  with (v0.9.53, X5): the search page's category tabs
     *  switch it between Empty (all messages) and the
     *  media kinds — PhotoAndVideo / Document / Url /
     *  VoiceNote. All four are plain searchMessages
     *  filters, the same ones the in-chat search and the
     *  shared-media page already run against the server. */
    private var filterType = "searchMessagesFilterEmpty"

    @Volatile
    private var generation = 0

    /** Switch the message filter: only the message
     *  paging restarts (the chat / contact / public
     *  sections do not depend on it and stay as they
     *  are). A no-op when the filter is unchanged. */
    fun setFilter(type: String) {
        if (type == filterType) return
        filterType = type
        generation++
        nextOffset = ""
        _results.value = emptyList()
        _exhausted.value = query.isEmpty()
        if (query.isNotEmpty()) loadPage(generation)
    }

    fun search(newQuery: String) {
        generation++
        query = newQuery.trim()
        nextOffset = ""
        _results.value = emptyList()
        _publicResults.value = emptyList()
        _publicTotal.value = 0
        _exhausted.value = query.isEmpty()
        if (query.isEmpty()) {
            _searching.value = false
            _publicSearching.value = false
            return
        }
        loadPage(generation)
        loadPublic(generation)
    }

    /** Public chats/channels/bots matching the query
     *  (searchPublicChats, verified in td_api.tl: query +
     *  a SearchChatTypeFilter; null asks for every type).
     *  The result is a Chats object (total_count +
     *  chat_ids); each id's display data is read from
     *  ChatStore's cache, fed by the updateNewChat
     *  objects TDLib sends alongside. An id whose object
     *  has not landed is skipped rather than rendered
     *  blank. */
    private fun loadPublic(gen: Int) {
        _publicSearching.value = true
        val q = TdClient.request("searchPublicChats").apply {
            put("query", query)
            put("type_filter", JSONObject.NULL)
        }
        TdRepository.sendRaw(q) { response ->
            if (gen != generation) return@sendRaw
            _publicSearching.value = false
            if (TdClient.isError(response)) {
                return@sendRaw
            }
            _publicTotal.value = response.optInt("total_count")
            val arr = response.optJSONArray("chat_ids")
                ?: return@sendRaw
            val hits = ArrayList<PublicHit>()
            val seen = HashSet<Long>()
            for (i in 0 until arr.length()) {
                val chatId = arr.optLong(i)
                if (chatId == 0L || !seen.add(chatId)) continue
                val kind = ChatStore.kindOfChat(chatId) ?: continue
                val title = ChatStore.titleOf(chatId)
                if (title.isEmpty()) continue
                hits.add(
                    PublicHit(
                        chatId = chatId,
                        title = title,
                        kind = kind
                    )
                )
            }
            _publicResults.value = hits
        }
    }

    fun loadMore() {
        if (_searching.value || _exhausted.value || query.isEmpty()) return
        loadPage(generation)
    }

    private fun loadPage(gen: Int) {
        _searching.value = true
        val q = TdClient.request("searchMessages").apply {
            put("chat_list", JSONObject().put("@type", "chatListMain"))
            put("query", query)
            put("offset", nextOffset)
            put("limit", 30)
            put("filter", JSONObject().put("@type", filterType))
            put("chat_type_filter", JSONObject.NULL)
            put("min_date", 0)
            put("max_date", 0)
        }
        TdRepository.sendRaw(q) { response ->
            if (gen != generation) return@sendRaw
            _searching.value = false
            if (TdClient.isError(response)) {
                // An error is not the end of the results
                // (v0.9.41, H1①): exhausted stays false so
                // scrolling retries the same page instead of
                // silently ending this search session.
                return@sendRaw
            }
            nextOffset = response.optString("next_offset")
            if (nextOffset.isEmpty()) _exhausted.value = true
            val arr = response.optJSONArray("messages")
            if (arr == null || arr.length() == 0) {
                _exhausted.value = true
                return@sendRaw
            }
            val hits = ArrayList<SearchHit>()
            for (i in 0 until arr.length()) {
                val msg = arr.optJSONObject(i) ?: continue
                val chatId = msg.optLong("chat_id")
                val content = msg.optJSONObject("content") ?: continue
                hits.add(
                    SearchHit(
                        chatId = chatId,
                        chatTitle = ChatStore.titleOf(chatId),
                        messageId = msg.optLong("id"),
                        summary = MessageStore.summarizeContent(content),
                        dateSeconds = msg.optInt("date"),
                        outgoing = msg.optBoolean("is_outgoing")
                    )
                )
            }
            _results.value = _results.value + hits
        }
    }
}
