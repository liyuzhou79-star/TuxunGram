package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One row of the administrator log (v0.9.14, U1): who did
 *  what, when. [text] is the plain-language rendering of the
 *  chatEvent action; names inside it resolve lazily and the
 *  row is rebuilt as they land. */
data class EventUi(
    val id: Long,
    val dateSec: Int,
    val actorName: String,
    val actorPhoto: String?,
    val text: String
)

/** One filter chip of the event log. [field] is the
 *  chatEventLogFilters Bool the chip turns on (td_api.tl
 *  L8719 — the filters object has exactly these 15 fields);
 *  null = no filters object at all, which is how "everything"
 *  is asked for. */
data class EventFilter(
    val key: String,
    val label: String,
    val field: String?
)

/**
 * Administrator event log for one group/channel (v0.9.14,
 * U1), over getChatEventLog (verified in td_api.tl). Paging
 * follows the same cursor shape as the rest of the app: the
 * next page starts from the oldest event id already held,
 * and a page that adds nothing ends the walk. Every
 * chatEvent* action subtype gets a plain-language line in
 * [describe]; a subtype with no mapping shows its raw type
 * name instead of an invented label. Actor (and target)
 * names/avatars resolve lazily through getUser + AvatarStore,
 * the ViewersStore pattern.
 */
object EventLogStore {

    val FILTERS: List<EventFilter> = listOf(
        EventFilter("all", "全部", null),
        EventFilter("edits", "编辑消息", "message_edits"),
        EventFilter("deletions", "删除消息", "message_deletions"),
        EventFilter("pins", "置顶", "message_pins"),
        EventFilter("joins", "加入", "member_joins"),
        EventFilter("leaves", "退出", "member_leaves"),
        EventFilter("invites", "邀请", "member_invites"),
        EventFilter("promotions", "管理员", "member_promotions"),
        EventFilter("restrictions", "限制成员", "member_restrictions"),
        EventFilter("tags", "成员标签", "member_tag_changes"),
        EventFilter("info", "群信息", "info_changes"),
        EventFilter("settings", "群设置", "setting_changes"),
        EventFilter("links", "邀请链接", "invite_link_changes"),
        EventFilter("videochat", "视频聊天", "video_chat_changes"),
        EventFilter("forum", "话题", "forum_changes"),
        EventFilter("subscriptions", "订阅", "subscription_extensions")
    )

    private data class RawEvent(
        val id: Long,
        val dateSec: Int,
        val actorUserId: Long,
        val actorChatId: Long,
        val action: JSONObject
    )

    private val _events = MutableStateFlow<List<EventUi>>(emptyList())
    val events: StateFlow<List<EventUi>> = _events

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _endReached = MutableStateFlow(false)
    val endReached: StateFlow<Boolean> = _endReached

    private val rawEvents = ArrayList<RawEvent>()

    @Volatile
    private var forChat: Long = 0L

    @Volatile
    private var forFilter: String = "all"

    private val nameByUser = ConcurrentHashMap<Long, String>()
    private val nameByChat = ConcurrentHashMap<Long, String>()
    private val namePending = ConcurrentHashMap.newKeySet<Long>()
    private val chatPending = ConcurrentHashMap.newKeySet<Long>()

    @Volatile
    private var listenerAttached = false

    private fun attachListener() {
        if (listenerAttached) return
        listenerAttached = true
        AvatarStore.addListener { publish() }
    }

    fun load(chatId: Long, filterKey: String) {
        attachListener()
        forChat = chatId
        forFilter = filterKey
        rawEvents.clear()
        _events.value = emptyList()
        _error.value = null
        _endReached.value = false
        _loading.value = true
        fetchPage(chatId, filterKey, fromEventId = 0L, first = true)
    }

    fun loadMore() {
        if (_loading.value || _loadingMore.value || _endReached.value) return
        val last = rawEvents.lastOrNull() ?: return
        _loadingMore.value = true
        fetchPage(forChat, forFilter, fromEventId = last.id, first = false)
    }

    private fun fetchPage(
        chatId: Long,
        filterKey: String,
        fromEventId: Long,
        first: Boolean
    ) {
        val filter = FILTERS.firstOrNull { it.key == filterKey }
        val query = TdClient.request("getChatEventLog").apply {
            put("chat_id", chatId)
            put("query", "")
            put("from_event_id", fromEventId)
            put("limit", 100)
            if (filter?.field != null) {
                put("filters", JSONObject().apply {
                    put("@type", "chatEventLogFilters")
                    put(filter.field, true)
                })
            }
            put("user_ids", JSONArray())
        }
        TdRepository.sendRaw(query) { response ->
            if (forChat != chatId || forFilter != filterKey) {
                return@sendRaw
            }
            _loading.value = false
            _loadingMore.value = false
            if (TdClient.isError(response)) {
                if (first) _error.value = errorText(response)
                return@sendRaw
            }
            val arr = response.optJSONArray("events") ?: JSONArray()
            var added = 0
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val id = e.optLong("id")
                if (id == 0L || rawEvents.any { it.id == id }) continue
                val member = e.optJSONObject("member_id")
                val action = e.optJSONObject("action") ?: continue
                rawEvents.add(
                    RawEvent(
                        id = id,
                        dateSec = e.optInt("date"),
                        actorUserId = if (
                            member?.optString("@type") ==
                            "messageSenderUser"
                        ) {
                            member.optLong("user_id")
                        } else {
                            0L
                        },
                        actorChatId = if (
                            member?.optString("@type") ==
                            "messageSenderChat"
                        ) {
                            member.optLong("chat_id")
                        } else {
                            0L
                        },
                        action = action
                    )
                )
                added++
            }
            if (added == 0) _endReached.value = true
            publish()
            // Resolve every name the new rows reference.
            for (event in rawEvents) {
                if (event.actorUserId != 0L) {
                    resolveUser(event.actorUserId)
                }
                if (event.actorChatId != 0L) {
                    resolveChat(event.actorChatId)
                }
                for (uid in referencedUserIds(event.action)) {
                    resolveUser(uid)
                }
            }
        }
    }

    private fun publish() {
        _events.value = rawEvents.map { event ->
            EventUi(
                id = event.id,
                dateSec = event.dateSec,
                actorName = when {
                    event.actorUserId != 0L ->
                        nameByUser[event.actorUserId] ?: "…"
                    event.actorChatId != 0L ->
                        nameByChat[event.actorChatId] ?: "频道/群组"
                    else -> "…"
                },
                actorPhoto = if (event.actorUserId != 0L) {
                    AvatarStore.pathForUser(event.actorUserId)
                } else {
                    null
                },
                text = describe(event.action)
            )
        }
    }

    private fun resolveUser(userId: Long) {
        if (nameByUser.containsKey(userId)) return
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
            if (name.isNotEmpty()) {
                nameByUser[userId] = name
                publish()
            }
        }
    }

    private fun resolveChat(chatId: Long) {
        if (nameByChat.containsKey(chatId)) return
        if (!chatPending.add(chatId)) return
        val query = TdClient.request("getChat").apply {
            put("chat_id", chatId)
        }
        TdRepository.sendRaw(query) { chat ->
            chatPending.remove(chatId)
            if (TdClient.isError(chat)) return@sendRaw
            val title = chat.optString("title")
            if (title.isNotEmpty()) {
                nameByChat[chatId] = title
                publish()
            }
        }
    }

    /** User ids an action talks about (invite/promote/restrict
     *  targets), so their names can be resolved for the text. */
    private fun referencedUserIds(action: JSONObject): Set<Long> {
        val out = HashSet<Long>()
        val direct = action.optLong("user_id", 0L)
        if (direct != 0L) out.add(direct)
        val member = action.optJSONObject("member_id")
        if (member?.optString("@type") == "messageSenderUser") {
            val uid = member.optLong("user_id")
            if (uid != 0L) out.add(uid)
        }
        val approver = action.optLong("approver_user_id", 0L)
        if (approver != 0L) out.add(approver)
        return out
    }

    private fun userName(userId: Long): String =
        nameByUser[userId] ?: "一位成员"

    private fun messageSummary(message: JSONObject?): String {
        val content = message?.optJSONObject("content") ?: return "一条消息"
        val summary = MessageStore.summarizeContent(content)
        return summary.ifEmpty { "一条消息" }
    }

    private fun autoDeleteLabel(seconds: Int): String = when (seconds) {
        0 -> "关"
        86400 -> "1 天"
        604800 -> "1 周"
        2592000 -> "1 个月"
        else -> "$seconds 秒"
    }

    /** Plain-language rendering of one chatEvent action.
     *  Every subtype in this tl is mapped below; anything
     *  unmapped falls through to the raw subtype name so the
     *  row stays honest instead of wearing an invented label. */
    private fun describe(action: JSONObject): String {
        return when (action.optString("@type")) {
            "chatEventMessageEdited" ->
                "编辑了消息：${messageSummary(action.optJSONObject("new_message"))}"
            "chatEventMessageDeleted" ->
                "删除了消息：${messageSummary(action.optJSONObject("message"))}"
            "chatEventMessagePinned" ->
                "置顶了消息：${messageSummary(action.optJSONObject("message"))}"
            "chatEventMessageUnpinned" ->
                "取消置顶了消息：${messageSummary(action.optJSONObject("message"))}"
            "chatEventPollStopped" ->
                "结束了投票：${messageSummary(action.optJSONObject("message"))}"
            "chatEventMemberJoined" -> "加入了群组"
            "chatEventMemberJoinedByInviteLink" -> "通过邀请链接加入了群组"
            "chatEventMemberJoinedByRequest" -> "经管理员批准入群申请后加入了群组"
            "chatEventMemberInvited" ->
                "邀请了 ${userName(action.optLong("user_id"))} 加入"
            "chatEventMemberLeft" -> "离开了群组"
            "chatEventMemberPromoted" ->
                "调整了 ${userName(action.optLong("user_id"))} 的管理员权限"
            "chatEventMemberRestricted" -> {
                val member = action.optJSONObject("member_id")
                val target = if (
                    member?.optString("@type") == "messageSenderUser"
                ) {
                    userName(member.optLong("user_id"))
                } else {
                    "一位成员"
                }
                "调整了对 $target 的限制"
            }
            "chatEventMemberTagChanged" ->
                "修改了 ${userName(action.optLong("user_id"))} 的成员标签"
            "chatEventMemberSubscriptionExtended" ->
                "延长了 ${userName(action.optLong("user_id"))} 的订阅"
            "chatEventTitleChanged" ->
                "把群名改成了「${action.optString("new_title")}」"
            "chatEventDescriptionChanged" -> "修改了简介"
            "chatEventPhotoChanged" ->
                if (action.optJSONObject("new_photo") != null) {
                    "更换了群头像"
                } else {
                    "移除了群头像"
                }
            "chatEventUsernameChanged" ->
                "把公开用户名改成了 @${action.optString("new_username")}"
            "chatEventPermissionsChanged" -> "修改了群成员权限"
            "chatEventSlowModeDelayChanged" ->
                "把慢速模式改成了 ${action.optInt("new_slow_mode_delay")} 秒"
            "chatEventMessageAutoDeleteTimeChanged" ->
                "把消息自动删除改成了" +
                    autoDeleteLabel(action.optInt("new_message_auto_delete_time"))
            "chatEventLinkedChatChanged" -> "修改了关联的讨论群"
            "chatEventLocationChanged" -> "修改了群组位置"
            "chatEventStickerSetChanged" -> "更换了群贴纸包"
            "chatEventCustomEmojiStickerSetChanged" -> "更换了群自定义表情包"
            "chatEventAvailableReactionsChanged" -> "修改了可用的回应"
            "chatEventBackgroundChanged" -> "更换了聊天背景"
            "chatEventEmojiStatusChanged" -> "修改了表情状态"
            "chatEventActiveUsernamesChanged" -> "修改了公开用户名列表"
            "chatEventAccentColorChanged",
            "chatEventProfileAccentColorChanged" -> "修改了主题颜色"
            "chatEventHasProtectedContentToggled" ->
                if (action.optBoolean("has_protected_content")) {
                    "开启了内容保护"
                } else {
                    "关闭了内容保护"
                }
            "chatEventInvitesToggled" ->
                if (action.optBoolean("can_invite_users")) {
                    "允许了成员邀请别人"
                } else {
                    "禁止了成员邀请别人"
                }
            "chatEventIsAllHistoryAvailableToggled" ->
                if (action.optBoolean("is_all_history_available")) {
                    "让新成员能看全部历史"
                } else {
                    "让新成员只能看加入后的消息"
                }
            "chatEventHasAggressiveAntiSpamEnabledToggled" ->
                if (action.optBoolean("has_aggressive_anti_spam_enabled")) {
                    "开启了强力反垃圾"
                } else {
                    "关闭了强力反垃圾"
                }
            "chatEventSignMessagesToggled" ->
                if (action.optBoolean("sign_messages")) {
                    "开启了消息署名"
                } else {
                    "关闭了消息署名"
                }
            "chatEventShowMessageSenderToggled" ->
                if (action.optBoolean("show_message_sender")) {
                    "开启了显示消息发送者"
                } else {
                    "关闭了显示消息发送者"
                }
            "chatEventAutomaticTranslationToggled" ->
                if (action.optBoolean("has_automatic_translation")) {
                    "开启了自动翻译"
                } else {
                    "关闭了自动翻译"
                }
            "chatEventInviteLinkEdited" -> "编辑了邀请链接"
            "chatEventInviteLinkRevoked" -> "撤销了邀请链接"
            "chatEventInviteLinkDeleted" -> "删除了邀请链接"
            "chatEventVideoChatCreated" -> "发起了视频聊天"
            "chatEventVideoChatEnded" -> "结束了视频聊天"
            "chatEventVideoChatMuteNewParticipantsToggled" -> "修改了视频聊天的新成员静音设置"
            "chatEventVideoChatParticipantIsMutedToggled" -> "修改了视频聊天参与者的静音状态"
            "chatEventVideoChatParticipantVolumeLevelChanged" -> "调整了视频聊天参与者的音量"
            "chatEventIsForumToggled" ->
                if (action.optBoolean("is_forum")) {
                    "开启了话题模式"
                } else {
                    "关闭了话题模式"
                }
            "chatEventForumTopicCreated" -> "新建了话题"
            "chatEventForumTopicEdited" -> "编辑了话题"
            "chatEventForumTopicToggleIsClosed" -> "关闭或重开了话题"
            "chatEventForumTopicToggleIsHidden" -> "隐藏或显示了话题"
            "chatEventForumTopicDeleted" -> "删除了话题"
            "chatEventForumTopicPinned" -> "调整了话题置顶"
            else -> {
                val raw = action.optString("@type")
                    .removePrefix("chatEvent")
                "其他操作（$raw）"
            }
        }
    }

    private fun errorText(error: JSONObject): String {
        val message = error.optString("message")
        return when {
            message.contains("ADMIN", ignoreCase = true) ||
                message.contains("not enough rights", ignoreCase = true) ||
                message.contains("RIGHT", ignoreCase = true) ->
                "只有管理员能看管理员日志。"
            message.contains("not available", ignoreCase = true) ||
                message.contains("CHANNEL_PRIVATE", ignoreCase = true) ->
                "这个对话的管理员日志看不了。"
            else ->
                "管理员日志没拉下来，再试一次。（错误码 ${error.optInt("code")}）"
        }
    }
}
