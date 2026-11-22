package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One headline number from chatStatistics* (v0.9.13, T2):
 *  a statisticalValue flattened — the current [value] and
 *  its [growthPercent] against the previous period. */
data class StatCard(
    val label: String,
    val value: Double,
    val growthPercent: Double,
    /** Mean values (channel averages) render with a decimal;
     *  counts render as whole numbers. */
    val isMean: Boolean = false
)

/** One row of a supergroup's top-senders list. */
data class TopSenderUi(
    val userId: Long,
    val name: String,
    val photoPath: String?,
    val sentCount: Int,
    val avgChars: Int
)

/** One parsed statistics graph (v0.9.25, R4): a single
 *  numeric series from a statisticalGraphData payload.
 *  [bars] mirrors the graph JSON's own type for the series
 *  ("bar" vs "line"); anything unparseable never becomes a
 *  StatGraphUi — the page then just keeps its number cards. */
data class StatGraphUi(
    val title: String,
    val values: List<Double>,
    val bars: Boolean
)

/** Everything the statistics page renders, flattened from
 *  chatStatisticsSupergroup / chatStatisticsChannel. */
data class ChatStatsUi(
    val isChannel: Boolean,
    val periodStart: Int,
    val periodEnd: Int,
    val cards: List<StatCard>,
    val topSenders: List<TopSenderUi>,
    /** Channel only: share of subscribers with notifications
     *  enabled, 0..100; -1 for groups (not reported). */
    val notificationsPercent: Double = -1.0,
    /** Parsed member/join graphs (v0.9.25, R4); empty when
     *  the server sent no usable graph data. */
    val graphs: List<StatGraphUi> = emptyList()
)

/**
 * Group / channel statistics (v0.9.13, T2), over
 * getChatStatistics (verified in td_api.tl). Only chats
 * whose supergroupFullInfo carries can_get_statistics get
 * the entry at all; the server still refuses chats that are
 * too small or where the viewer is not an administrator,
 * and those refusals are translated into plain language.
 * The headline numbers render as cards and the top senders
 * as drawn bars. Since v0.9.25 (R4) the member_count_graph /
 * join_graph payloads are also parsed and drawn (see
 * parseGraph); async graphs resolve through
 * getStatisticalGraph, error/absent graphs are skipped —
 * nothing is ever drawn from invented data.
 */
object ChatStatsStore {
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _stats = MutableStateFlow<ChatStatsUi?>(null)
    val stats: StateFlow<ChatStatsUi?> = _stats

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    @Volatile
    private var forChatId = 0L

    private val nameByUser = ConcurrentHashMap<Long, String>()
    private val namePending = ConcurrentHashMap.newKeySet<Long>()

    fun load(chatId: Long) {
        forChatId = chatId
        _loading.value = true
        _stats.value = null
        _error.value = null
        val query = TdClient.request("getChatStatistics").apply {
            put("chat_id", chatId)
            put("is_dark", false)
        }
        TdRepository.sendRaw(query) { response ->
            if (forChatId != chatId) return@sendRaw
            _loading.value = false
            if (TdClient.isError(response)) {
                _error.value = errorText(response)
                return@sendRaw
            }
            _stats.value = parse(response)
            val parsed = _stats.value ?: return@sendRaw
            for (sender in parsed.topSenders) {
                if (sender.name.isEmpty()) resolveName(sender.userId)
            }
            // Async graphs (statisticalGraphAsync) resolve in
            // a second round-trip and patch themselves in.
            for ((title, token) in pendingAsyncGraphs(response)) {
                resolveAsyncGraph(chatId, title, token)
            }
        }
    }

    private fun statValue(obj: JSONObject?, field: String): StatCard? {
        val v = obj?.optJSONObject(field) ?: return null
        return StatCard(
            label = "",
            value = v.optDouble("value"),
            growthPercent = v.optDouble("growth_rate_percentage")
        )
    }

    private fun parse(response: JSONObject): ChatStatsUi? {
        val period = response.optJSONObject("period")
        val start = period?.optInt("start_date") ?: 0
        val end = period?.optInt("end_date") ?: 0
        return when (response.optString("@type")) {
            "chatStatisticsSupergroup" -> {
                val cards = listOfNotNull(
                    statValue(response, "member_count")
                        ?.copy(label = "成员数"),
                    statValue(response, "message_count")
                        ?.copy(label = "消息数"),
                    statValue(response, "viewer_count")
                        ?.copy(label = "浏览人次"),
                    statValue(response, "sender_count")
                        ?.copy(label = "发言人数")
                )
                val senders = ArrayList<TopSenderUi>()
                val arr = response.optJSONArray("top_senders")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val s = arr.optJSONObject(i) ?: continue
                        val userId = s.optLong("user_id")
                        if (userId == 0L) continue
                        senders.add(
                            TopSenderUi(
                                userId = userId,
                                name = nameByUser[userId] ?: "",
                                photoPath =
                                    AvatarStore.pathForUser(userId),
                                sentCount = s.optInt("sent_message_count"),
                                avgChars =
                                    s.optInt("average_character_count")
                            )
                        )
                    }
                }
                ChatStatsUi(
                    isChannel = false,
                    periodStart = start, periodEnd = end,
                    cards = cards, topSenders = senders,
                    graphs = listOfNotNull(
                        parseGraph(response, "member_count_graph", "成员数"),
                        parseGraph(response, "join_graph", "新加入")
                    )
                )
            }
            "chatStatisticsChannel" -> {
                val cards = listOfNotNull(
                    statValue(response, "member_count")
                        ?.copy(label = "订阅者"),
                    statValue(response, "mean_message_view_count")
                        ?.copy(label = "平均每条浏览", isMean = true),
                    statValue(response, "mean_message_share_count")
                        ?.copy(label = "平均每条转发", isMean = true),
                    statValue(response, "mean_message_reaction_count")
                        ?.copy(label = "平均每条回应", isMean = true),
                    statValue(response, "mean_story_view_count")
                        ?.copy(label = "平均每条动态浏览", isMean = true),
                    statValue(response, "mean_story_share_count")
                        ?.copy(label = "平均每条动态转发", isMean = true),
                    statValue(response, "mean_story_reaction_count")
                        ?.copy(label = "平均每条动态回应", isMean = true)
                )
                ChatStatsUi(
                    isChannel = true,
                    periodStart = start, periodEnd = end,
                    cards = cards, topSenders = emptyList(),
                    graphs = listOfNotNull(
                        parseGraph(response, "member_count_graph", "订阅者"),
                        parseGraph(response, "join_graph", "新加入")
                    ),
                    notificationsPercent =
                        response.optDouble(
                            "enabled_notifications_percentage", -1.0
                        )
                )
            }
            else -> null
        }
    }

    private fun resolveName(userId: Long) {
        if (!namePending.add(userId)) return
        val query = TdClient.request("getUser").apply {
            put("user_id", userId)
        }
        TdRepository.sendRaw(query) { user ->
            namePending.remove(userId)
            if (TdClient.isError(user)) return@sendRaw
            AvatarStore.noteUser(user)
            val name = listOf(
                user.optString("first_name"), user.optString("last_name")
            ).filter { it.isNotEmpty() }.joinToString(" ")
            if (name.isNotEmpty()) nameByUser[userId] = name
            val current = _stats.value ?: return@sendRaw
            _stats.value = current.copy(
                topSenders = current.topSenders.map { sender ->
                    if (sender.userId == userId) {
                        sender.copy(
                            name = nameByUser[userId] ?: sender.name,
                            photoPath =
                                AvatarStore.pathForUser(userId)
                                    ?: sender.photoPath
                        )
                    } else {
                        sender
                    }
                }
            )
        }
    }

    /** Parse one graph field of the statistics object.
     *  statisticalGraphData carries the payload inline;
     *  statisticalGraphAsync / statisticalGraphError yield
     *  null here (async is resolved separately, errors are
     *  simply not drawn). */
    private fun parseGraph(
        response: JSONObject, field: String, title: String
    ): StatGraphUi? {
        val graph = response.optJSONObject(field) ?: return null
        if (graph.optString("@type") != "statisticalGraphData") {
            return null
        }
        return parseGraphJson(graph.optString("json_data"), title)
    }

    /** The graph JSON shape TDLib ships (same one the
     *  official clients render): {"columns":[["x",ts...],
     *  ["y0",v...],...], "types":{"y0":"line"|"bar",...}}.
     *  The first non-x numeric column becomes the series;
     *  anything else (empty, string columns, broken JSON)
     *  returns null so the page keeps its number cards. */
    private fun parseGraphJson(
        jsonData: String, title: String
    ): StatGraphUi? {
        if (jsonData.isEmpty()) return null
        return try {
            val root = JSONObject(jsonData)
            val columns = root.optJSONArray("columns")
                ?: return null
            val types = root.optJSONObject("types")
            for (i in 0 until columns.length()) {
                val col = columns.optJSONArray(i) ?: continue
                if (col.length() < 2) continue
                val colId = col.optString(0)
                if (colId == "x" || colId.isEmpty()) continue
                // Only numeric series qualify; a string-typed
                // first element after the id means this column
                // is labels, not data.
                val first = col.opt(1)
                if (first !is Number) continue
                val values = ArrayList<Double>(col.length() - 1)
                for (j in 1 until col.length()) {
                    values.add(col.optDouble(j, 0.0))
                }
                if (values.size < 2) return null
                return StatGraphUi(
                    title = title,
                    values = values,
                    bars = types?.optString(colId) == "bar"
                )
            }
            null
        } catch (t: Throwable) {
            null
        }
    }

    /** (title, token) pairs for graph fields that arrived as
     *  statisticalGraphAsync and still need their data. */
    private fun pendingAsyncGraphs(
        response: JSONObject
    ): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val fields = listOf(
            "member_count_graph" to
                if (response.optString("@type") ==
                    "chatStatisticsChannel"
                ) {
                    "订阅者"
                } else {
                    "成员数"
                },
            "join_graph" to "新加入"
        )
        for ((field, title) in fields) {
            val graph = response.optJSONObject(field) ?: continue
            if (graph.optString("@type") == "statisticalGraphAsync") {
                val token = graph.optString("token")
                if (token.isNotEmpty()) out.add(title to token)
            }
        }
        return out
    }

    private fun resolveAsyncGraph(
        chatId: Long, title: String, token: String
    ) {
        val query = TdClient.request("getStatisticalGraph").apply {
            put("chat_id", chatId)
            put("token", token)
            put("x", 0L)
        }
        TdRepository.sendRaw(query) { response ->
            if (forChatId != chatId) return@sendRaw
            if (TdClient.isError(response)) return@sendRaw
            if (response.optString("@type") !=
                "statisticalGraphData"
            ) {
                return@sendRaw
            }
            val graph = parseGraphJson(
                response.optString("json_data"), title
            ) ?: return@sendRaw
            val current = _stats.value ?: return@sendRaw
            if (current.graphs.any { it.title == title }) {
                return@sendRaw
            }
            _stats.value = current.copy(
                graphs = current.graphs + graph
            )
        }
    }

    /** Plain-language version of a getChatStatistics refusal:
     *  not-an-admin and too-few-members are the two scenes a
     *  user can actually hit from the profile page. */
    private fun errorText(error: JSONObject): String {
        val message = error.optString("message")
        return when {
            message.contains("ADMIN", ignoreCase = true) ->
                "只有这个群/频道的管理员能看统计。"
            message.contains("member", ignoreCase = true) ||
                message.contains("PARTICIPANT", ignoreCase = true) ->
                "成员数还不够：服务器只给规模达到要求的群和频道出统计，数字以服务器为准。"
            message.contains("STATISTIC", ignoreCase = true) ->
                "这个对话暂时没有统计数据，过段时间再来看看。"
            else ->
                "统计没拉到，再试一次。（错误码 ${error.optInt("code")}）"
        }
    }
}
