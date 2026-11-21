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

    private var query = ""
    private var nextOffset = ""

    @Volatile
    private var generation = 0

    fun search(newQuery: String) {
        generation++
        query = newQuery.trim()
        nextOffset = ""
        _results.value = emptyList()
        _exhausted.value = query.isEmpty()
        if (query.isEmpty()) {
            _searching.value = false
            return
        }
        loadPage(generation)
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
            put("filter", JSONObject().put("@type", "searchMessagesFilterEmpty"))
            put("chat_type_filter", JSONObject.NULL)
            put("min_date", 0)
            put("max_date", 0)
        }
        TdRepository.sendRaw(q) { response ->
            if (gen != generation) return@sendRaw
            _searching.value = false
            if (TdClient.isError(response)) {
                _exhausted.value = true
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
