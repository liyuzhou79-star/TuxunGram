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
    val notificationsPercent: Double = -1.0
)

/**
 * Group / channel statistics (v0.9.13, T2), over
 * getChatStatistics (verified in td_api.tl). Only chats
 * whose supergroupFullInfo carries can_get_statistics get
 * the entry at all; the server still refuses chats that are
 * too small or where the viewer is not an administrator,
 * and those refusals are translated into plain language.
 * The statisticalGraph* JSON payloads are deliberately not
 * rendered — the page shows the headline numbers as cards
 * and the top senders as drawn bars, nothing faked.
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
                    cards = cards, topSenders = senders
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
