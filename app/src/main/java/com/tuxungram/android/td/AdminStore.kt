package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** What the signed-in user may do in one group/channel, derived
 *  from their own chatMember status (getMe + getChatMember,
 *  verified in td_api.tl). The UI shows management entries only
 *  when the matching flag is set — never tap-first-error-later. */
data class AdminCaps(
    val canManage: Boolean = false,
    val canChangeInfo: Boolean = false,
    val canDeleteAny: Boolean = false,
    val canRestrict: Boolean = false
)

/**
 * Group/channel administration (Batch 3, C4): rename, edit
 * description, delete anyone's message, restrict/unrestrict and
 * ban/unban members. All functions are thin wrappers over the
 * td_api-verified calls (setChatTitle, setChatDescription,
 * setChatMemberStatus, banChatMember, deleteMessages); results
 * surface through [notice] as plain-language text on the
 * profile page.
 */
object AdminStore {
    private val _caps = MutableStateFlow(AdminCaps())
    val caps: StateFlow<AdminCaps> = _caps

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    @Volatile
    private var capsChatId = 0L

    fun capsFor(chatId: Long): AdminCaps =
        if (capsChatId == chatId) _caps.value else AdminCaps()

    fun loadCaps(chatId: Long) {
        capsChatId = chatId
        _caps.value = AdminCaps()
        _notice.value = null
        TdRepository.sendRaw(TdClient.request("getMe")) { me ->
            if (TdClient.isError(me)) return@sendRaw
            val myId = me.optLong("id")
            val query = TdClient.request("getChatMember").apply {
                put("chat_id", chatId)
                put(
                    "member_id",
                    JSONObject()
                        .put("@type", "messageSenderUser")
                        .put("user_id", myId)
                )
            }
            TdRepository.sendRaw(query) { member ->
                if (TdClient.isError(member) || capsChatId != chatId) {
                    return@sendRaw
                }
                val status = member.optJSONObject("status")
                    ?: return@sendRaw
                _caps.value = when (status.optString("@type")) {
                    "chatMemberStatusCreator" ->
                        AdminCaps(true, true, true, true)
                    "chatMemberStatusAdministrator" -> {
                        val rights = status.optJSONObject("rights")
                        AdminCaps(
                            canManage = true,
                            canChangeInfo =
                                rights?.optBoolean("can_change_info") == true,
                            canDeleteAny =
                                rights?.optBoolean("can_delete_messages") == true,
                            canRestrict =
                                rights?.optBoolean("can_restrict_members") == true
                        )
                    }
                    else -> AdminCaps()
                }
            }
        }
    }

    fun setTitle(chatId: Long, title: String) {
        val query = TdClient.request("setChatTitle").apply {
            put("chat_id", chatId)
            put("title", title)
        }
        act(query, "名称已更新。") { ProfileStore.load(chatId) }
    }

    fun setDescription(chatId: Long, description: String) {
        val query = TdClient.request("setChatDescription").apply {
            put("chat_id", chatId)
            put("description", description)
        }
        act(query, "简介已更新。") { ProfileStore.load(chatId) }
    }

    /** Mute a member: restricted status with every send
     *  permission off, no expiry. */
    fun restrictMember(chatId: Long, userId: Long) {
        val permissions = JSONObject().put("@type", "chatPermissions")
        for (field in PERMISSION_FIELDS) permissions.put(field, false)
        val status = JSONObject()
            .put("@type", "chatMemberStatusRestricted")
            .put("is_member", true)
            .put("restricted_until_date", 0)
            .put("permissions", permissions)
        setMemberStatus(chatId, userId, status, "已禁言。")
    }

    fun unrestrictMember(chatId: Long, userId: Long) {
        val status = JSONObject()
            .put("@type", "chatMemberStatusMember")
            .put("member_until_date", 0)
        setMemberStatus(chatId, userId, status, "已解除禁言。")
    }

    fun banMember(chatId: Long, userId: Long) {
        val query = TdClient.request("banChatMember").apply {
            put("chat_id", chatId)
            put(
                "member_id",
                JSONObject()
                    .put("@type", "messageSenderUser")
                    .put("user_id", userId)
            )
            put("banned_until_date", 0)
            put("revoke_messages", false)
        }
        act(query, "已封禁。") { ProfileStore.load(chatId) }
    }

    /** Unban = move a banned user back to "left": clears the ban
     *  without re-adding them to the chat. */
    fun unbanMember(chatId: Long, userId: Long) {
        val status = JSONObject().put("@type", "chatMemberStatusLeft")
        setMemberStatus(chatId, userId, status, "已解除封禁。")
    }

    /** Delete someone else's message for everyone (admin path;
     *  the bubble vanishes via updateDeleteMessages). */
    fun deleteForEveryone(chatId: Long, messageId: Long) {
        val query = TdClient.request("deleteMessages").apply {
            put("chat_id", chatId)
            put("message_ids", JSONArray().put(messageId))
            put("revoke", true)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                TdRepository.reportError(
                    response.optInt("code") to response.optString("message")
                )
            }
        }
    }

    private fun setMemberStatus(
        chatId: Long,
        userId: Long,
        status: JSONObject,
        okText: String
    ) {
        val query = TdClient.request("setChatMemberStatus").apply {
            put("chat_id", chatId)
            put(
                "member_id",
                JSONObject()
                    .put("@type", "messageSenderUser")
                    .put("user_id", userId)
            )
            put("status", status)
        }
        act(query, okText) { ProfileStore.load(chatId) }
    }

    private fun act(query: JSONObject, okText: String, onOk: () -> Unit) {
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _notice.value = "操作失败（错误码 ${response.optInt("code")}），" +
                    "可能是权限不够或对方身份比你高。"
            } else {
                _notice.value = okText
                onOk()
            }
        }
    }

    private val PERMISSION_FIELDS = listOf(
        "can_send_basic_messages", "can_send_audios",
        "can_send_documents", "can_send_photos", "can_send_videos",
        "can_send_video_notes", "can_send_voice_notes",
        "can_send_polls", "can_send_other_messages",
        "can_add_link_previews", "can_react_to_messages",
        "can_edit_tag", "can_change_info", "can_invite_users",
        "can_pin_messages", "can_create_topics"
    )
}
