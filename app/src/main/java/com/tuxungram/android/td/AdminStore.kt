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
    val canRestrict: Boolean = false,
    /** Promote/demote administrators (v0.9.2, L4): the
     *  creator always can; an administrator needs the
     *  can_promote_members right. */
    val canPromote: Boolean = false,
    /** Invite-link management (v0.9.5, O1): creator, or an
     *  administrator with the can_invite_users right. */
    val canInvite: Boolean = false,
    /** Group settings pages (v0.9.5, O2/O3: permissions,
     *  slow mode, content protection, group photo): creator,
     *  or an administrator with the can_change_info right. */
    val canManageSettings: Boolean = false
)

/** One invite link row for the admin invite section
 *  (v0.9.5, O1), flattened from chatInviteLink. */
data class InviteLinkUi(
    val link: String,
    val name: String,
    val isPrimary: Boolean,
    val isRevoked: Boolean,
    val memberCount: Int
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
                        AdminCaps(
                            true, true, true, true, true,
                            canInvite = true, canManageSettings = true
                        )
                    "chatMemberStatusAdministrator" -> {
                        val rights = status.optJSONObject("rights")
                        AdminCaps(
                            canManage = true,
                            canChangeInfo =
                                rights?.optBoolean("can_change_info") == true,
                            canDeleteAny =
                                rights?.optBoolean("can_delete_messages") == true,
                            canRestrict =
                                rights?.optBoolean("can_restrict_members") == true,
                            canPromote =
                                rights?.optBoolean("can_promote_members") == true,
                            canInvite =
                                rights?.optBoolean("can_invite_users") == true,
                            canManageSettings =
                                rights?.optBoolean("can_change_info") == true
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
        act(query, "已封禁。") {
            ProfileStore.load(chatId)
            MemberListStore.refreshIf(chatId)
        }
    }

    /** Promote a member to administrator (v0.9.2, L4). This
     *  tl has no promoteChatMember function — promotion is
     *  setChatMemberStatus with a chatMemberStatusAdministrator
     *  status (verified in td_api.tl). The granted set is the
     *  usual working set for a group admin; the server remains
     *  the final judge and errors surface as the plain notice. */
    fun promoteMember(chatId: Long, userId: Long) {
        val rights = JSONObject().put("@type", "chatAdministratorRights")
        for (field in ADMIN_RIGHT_FIELDS) rights.put(field, false)
        rights.put("can_manage_chat", true)
        rights.put("can_delete_messages", true)
        rights.put("can_invite_users", true)
        rights.put("can_restrict_members", true)
        rights.put("can_pin_messages", true)
        rights.put("can_manage_topics", true)
        val status = JSONObject()
            .put("@type", "chatMemberStatusAdministrator")
            .put("can_be_edited", false)
            .put("rights", rights)
        setMemberStatus(chatId, userId, status, "已设为管理员。")
    }

    /** Demote an administrator back to a plain member. */
    fun demoteMember(chatId: Long, userId: Long) {
        val status = JSONObject()
            .put("@type", "chatMemberStatusMember")
            .put("member_until_date", 0)
        setMemberStatus(chatId, userId, status, "已取消管理员。")
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

    // ---- Invite links (v0.9.5, O1) ----
    // getChatInviteLinks is creator-scoped: the profile page
    // lists the links the signed-in admin created (the primary
    // link additionally rides the FullInfo, see ProfileStore).
    // createChatInviteLink / revokeChatInviteLink verified in
    // td_api.tl.

    private val _inviteLinks = MutableStateFlow<List<InviteLinkUi>>(emptyList())
    val inviteLinks: StateFlow<List<InviteLinkUi>> = _inviteLinks

    @Volatile
    private var inviteChatId = 0L

    fun loadInviteLinks(chatId: Long) {
        inviteChatId = chatId
        _inviteLinks.value = emptyList()
        TdRepository.sendRaw(TdClient.request("getMe")) { me ->
            if (TdClient.isError(me) || inviteChatId != chatId) {
                return@sendRaw
            }
            val myId = me.optLong("id")
            val collected = ArrayList<InviteLinkUi>()
            var pending = 2
            fun fetch(revoked: Boolean) {
                val query = TdClient.request("getChatInviteLinks").apply {
                    put("chat_id", chatId)
                    put("creator_user_id", myId)
                    put("is_revoked", revoked)
                    put("offset_date", 0)
                    put("offset_invite_link", "")
                    put("limit", 50)
                }
                TdRepository.sendRaw(query) { response ->
                    if (!TdClient.isError(response)) {
                        val links = response.optJSONArray("invite_links")
                        if (links != null) {
                            for (i in 0 until links.length()) {
                                val l = links.optJSONObject(i) ?: continue
                                collected.add(
                                    InviteLinkUi(
                                        link = l.optString("invite_link"),
                                        name = l.optString("name"),
                                        isPrimary = l.optBoolean("is_primary"),
                                        isRevoked = l.optBoolean("is_revoked"),
                                        memberCount = l.optInt("member_count")
                                    )
                                )
                            }
                        }
                    }
                    pending--
                    if (pending == 0 && inviteChatId == chatId) {
                        _inviteLinks.value = collected.sortedWith(
                            compareBy<InviteLinkUi> { it.isRevoked }
                                .thenByDescending { it.isPrimary }
                        )
                    }
                }
            }
            fetch(revoked = false)
            fetch(revoked = true)
        }
    }

    fun createInviteLink(chatId: Long, name: String) {
        val query = TdClient.request("createChatInviteLink").apply {
            put("chat_id", chatId)
            put("name", name)
            put("expiration_date", 0)
            put("member_limit", 0)
            put("creates_join_request", false)
        }
        act(query, "已新建邀请链接。") { loadInviteLinks(chatId) }
    }

    fun revokeInviteLink(chatId: Long, link: String) {
        val query = TdClient.request("revokeChatInviteLink").apply {
            put("chat_id", chatId)
            put("invite_link", link)
        }
        act(query, "已撤销这个邀请链接，用它的人不能再通过它进群。") {
            loadInviteLinks(chatId)
            ProfileStore.load(chatId)
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
        act(query, okText) {
            ProfileStore.load(chatId)
            MemberListStore.refreshIf(chatId)
        }
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

    /** All chatAdministratorRights fields (td_api.tl line
     *  1124), so a promote request names every field
     *  explicitly instead of relying on server defaults. */
    private val ADMIN_RIGHT_FIELDS = listOf(
        "can_manage_chat", "can_change_info", "can_post_messages",
        "can_edit_messages", "can_delete_messages",
        "can_invite_users", "can_restrict_members",
        "can_pin_messages", "can_manage_topics",
        "can_promote_members", "can_manage_video_chats",
        "can_post_stories", "can_edit_stories",
        "can_delete_stories", "can_manage_direct_messages",
        "can_manage_tags", "can_send_welcome_messages",
        "is_anonymous"
    )

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
