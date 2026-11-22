package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One member row of a profile page. [statusKind] is the raw
 *  status family (creator/administrator/member/restricted/
 *  banned/left) the admin tools branch on. */
data class MemberEntry(
    val userId: Long,
    val name: String,
    val statusText: String,
    val statusKind: String = "member",
    /** Local path of the small profile photo once AvatarStore
     *  has it on disk (v0.9.9, W0); null = initial-letter disc. */
    val photoPath: String? = null
)

/** The chat's default member permissions, all 16
 *  chatPermissions fields from td_api.tl (v0.9.5, O2). The
 *  settings page exposes the content switches; the rest are
 *  carried verbatim so a save never silently resets them. */
data class GroupPerms(
    val canSendBasic: Boolean = false,
    val canSendAudios: Boolean = false,
    val canSendDocuments: Boolean = false,
    val canSendPhotos: Boolean = false,
    val canSendVideos: Boolean = false,
    val canSendVideoNotes: Boolean = false,
    val canSendVoiceNotes: Boolean = false,
    val canSendPolls: Boolean = false,
    val canSendOther: Boolean = false,
    val canAddLinkPreviews: Boolean = false,
    val canReact: Boolean = false,
    val canEditTag: Boolean = false,
    val canChangeInfo: Boolean = false,
    val canInviteUsers: Boolean = false,
    val canPinMessages: Boolean = false,
    val canCreateTopics: Boolean = false
)

/** Everything the profile page renders, flattened from the
 *  chat object plus the matching FullInfo. */
data class ProfileUi(
    val chatId: Long,
    val title: String,
    val kindLabel: String,
    val description: String,
    val infoLines: List<String>,
    val members: List<MemberEntry>,
    val canGetMembers: Boolean,
    val membersExhausted: Boolean,
    /** The other user's id for private and secret chats
     *  (0 otherwise); drives the "start secret chat" entry. */
    val userId: Long = 0L,
    /** True when this profile belongs to a secret chat. */
    val isSecretChat: Boolean = false,
    /** chat.message_auto_delete_time — in a secret chat this
     *  is the self-destruct timer (v0.6.0, E1). */
    val autoDeleteSeconds: Int = 0,
    /** True when this user sits on my block list
     *  (userFullInfo.block_list == blockListMain, v0.9.3 M3). */
    val isBlocked: Boolean = false,
    /** Primary invite link from the FullInfo's invite_link
     *  (v0.9.5, O1); empty when TDLib does not hand it out
     *  (no can_invite_users right, or a private group). */
    val primaryInviteLink: String = "",
    /** Default member permissions from chat.permissions
     *  (v0.9.5, O2); null for private/secret chats. */
    val groupPerms: GroupPerms? = null,
    /** chat.has_protected_content (v0.9.5, O2). */
    val hasProtectedContent: Boolean = false,
    /** supergroupFullInfo.slow_mode_delay in seconds
     *  (v0.9.5, O2); 0 = off / not a supergroup. */
    val slowModeDelay: Int = 0,
    /** True when the chat is a supergroup or channel
     *  (slow mode exists only there, per td_api.tl). */
    val isSupergroup: Boolean = false,
    /** chat.theme as an emoji name (v0.9.12, S6); empty when
     *  the chat carries no emoji theme. Gift themes set
     *  [themeIsGift] instead — they are not picked here. */
    val themeName: String = "",
    val themeIsGift: Boolean = false
)

/**
 * Profile page data (Batch 2, B5). The three FullInfo functions
 * take DIFFERENT ids — user_id / basic_group_id / supergroup_id
 * out of chat.type, never the chat_id (verified in td_api.tl) —
 * so load() resolves the chat type first, then branches.
 * Supergroup members page via getSupergroupMembers offset/limit;
 * basic-group members are embedded in the FullInfo itself.
 */
object ProfileStore {
    private val _profile = MutableStateFlow<ProfileUi?>(null)
    val profile: StateFlow<ProfileUi?> = _profile

    private val nameByUser = ConcurrentHashMap<Long, String>()
    private val nameFetchPending = ConcurrentHashMap.newKeySet<Long>()

    private var currentChatId = 0L
    private var currentUserId = 0L
    private var currentIsSecret = false
    private var currentAutoDelete = 0
    private var currentBlocked = false
    private var currentInviteLink = ""
    private var currentPerms: GroupPerms? = null
    private var currentProtected = false
    private var currentSlowMode = 0
    private var currentIsSupergroup = false
    private var currentThemeName = ""
    private var currentThemeIsGift = false
    private var supergroupId = 0L
    private var memberOffset = 0
    private var membersLoading = false
    private val memberList = ArrayList<MemberEntry>()

    /** Guards memberList (audit fix A10): it is mutated from
     *  TDLib receiver-thread callbacks (addMember / fetchName)
     *  while load() clears it on the main thread and the
     *  publish paths copy it — an unguarded copy racing a
     *  mutation can throw ConcurrentModificationException. */
    private val membersLock = Any()

    private fun memberSnapshot(): List<MemberEntry> =
        synchronized(membersLock) { ArrayList(memberList) }

    fun load(chatId: Long) {
        currentChatId = chatId
        currentUserId = 0L
        currentIsSecret = false
        currentAutoDelete = 0
        currentBlocked = false
        currentInviteLink = ""
        currentPerms = null
        currentProtected = false
        currentSlowMode = 0
        currentIsSupergroup = false
        currentThemeName = ""
        currentThemeIsGift = false
        supergroupId = 0L
        memberOffset = 0
        membersLoading = false
        synchronized(membersLock) { memberList.clear() }
        _profile.value = null
        val query = TdClient.request("getChat").apply { put("chat_id", chatId) }
        TdRepository.sendRaw(query) { chat ->
            if (TdClient.isError(chat) || currentChatId != chatId) return@sendRaw
            val title = chat.optString("title")
            // Chat theme (v0.9.12, S6): chat.theme is a
            // chatThemeEmoji{name} or chatThemeGift{...}.
            val themeObj = chat.optJSONObject("theme")
            currentThemeName = ""
            currentThemeIsGift = false
            when (themeObj?.optString("@type")) {
                "chatThemeEmoji" ->
                    currentThemeName = themeObj.optString("name")
                "chatThemeGift" -> currentThemeIsGift = true
            }
            currentAutoDelete = chat.optInt("message_auto_delete_time")
            currentProtected = chat.optBoolean("has_protected_content")
            val perms = chat.optJSONObject("permissions")
            if (perms != null) {
                currentPerms = GroupPerms(
                    canSendBasic = perms.optBoolean("can_send_basic_messages"),
                    canSendAudios = perms.optBoolean("can_send_audios"),
                    canSendDocuments = perms.optBoolean("can_send_documents"),
                    canSendPhotos = perms.optBoolean("can_send_photos"),
                    canSendVideos = perms.optBoolean("can_send_videos"),
                    canSendVideoNotes = perms.optBoolean("can_send_video_notes"),
                    canSendVoiceNotes = perms.optBoolean("can_send_voice_notes"),
                    canSendPolls = perms.optBoolean("can_send_polls"),
                    canSendOther = perms.optBoolean("can_send_other_messages"),
                    canAddLinkPreviews = perms.optBoolean("can_add_link_previews"),
                    canReact = perms.optBoolean("can_react_to_messages"),
                    canEditTag = perms.optBoolean("can_edit_tag"),
                    canChangeInfo = perms.optBoolean("can_change_info"),
                    canInviteUsers = perms.optBoolean("can_invite_users"),
                    canPinMessages = perms.optBoolean("can_pin_messages"),
                    canCreateTopics = perms.optBoolean("can_create_topics")
                )
            }
            val type = chat.optJSONObject("type") ?: return@sendRaw
            when (type.optString("@type")) {
                "chatTypePrivate" -> loadUserProfile(chatId, title, type.optLong("user_id"))
                "chatTypeSecret" -> loadUserProfile(
                    chatId, title, type.optLong("user_id"),
                    kindLabel = "秘密聊天", isSecret = true
                )
                "chatTypeBasicGroup" ->
                    loadBasicGroupProfile(chatId, title, type.optLong("basic_group_id"))
                "chatTypeSupergroup" -> loadSupergroupProfile(
                    chatId, title,
                    type.optLong("supergroup_id"),
                    type.optBoolean("is_channel")
                )
                else -> _profile.value = ProfileUi(
                    chatId, title, "对话", "", emptyList(),
                    emptyList(), canGetMembers = false, membersExhausted = true
                )
            }
        }
    }

    private fun publish(
        chatId: Long,
        title: String,
        kindLabel: String,
        description: String,
        infoLines: List<String>,
        canGetMembers: Boolean,
        membersExhausted: Boolean
    ) {
        if (currentChatId != chatId) return
        _profile.value = ProfileUi(
            chatId, title, kindLabel, description, infoLines,
            memberSnapshot(), canGetMembers, membersExhausted,
            userId = currentUserId,
            isSecretChat = currentIsSecret,
            autoDeleteSeconds = currentAutoDelete,
            isBlocked = currentBlocked,
            primaryInviteLink = currentInviteLink,
            groupPerms = currentPerms,
            hasProtectedContent = currentProtected,
            slowModeDelay = currentSlowMode,
            isSupergroup = currentIsSupergroup,
            themeName = currentThemeName,
            themeIsGift = currentThemeIsGift
        )
    }

    private fun loadUserProfile(
        chatId: Long,
        title: String,
        userId: Long,
        kindLabel: String = "私聊",
        isSecret: Boolean = false
    ) {
        currentUserId = userId
        currentIsSecret = isSecret
        var username = ""
        var bio = ""
        var commonGroups = 0
        var pending = 2
        fun done() {
            pending--
            if (pending > 0) return
            val lines = ArrayList<String>()
            if (username.isNotEmpty()) lines.add("@$username")
            if (commonGroups > 0) lines.add("共同群组 $commonGroups 个")
            publish(
                chatId, title, kindLabel, bio, lines,
                canGetMembers = false, membersExhausted = true
            )
        }
        TdRepository.sendRaw(TdClient.request("getUser").apply {
            put("user_id", userId)
        }) { user ->
            if (!TdClient.isError(user)) {
                username = user.optJSONObject("usernames")
                    ?.optJSONArray("active_usernames")?.optString(0) ?: ""
            }
            done()
        }
        TdRepository.sendRaw(TdClient.request("getUserFullInfo").apply {
            put("user_id", userId)
        }) { full ->
            if (!TdClient.isError(full)) {
                bio = full.optJSONObject("bio")?.optString("text") ?: ""
                commonGroups = full.optInt("group_in_common_count")
                currentBlocked =
                    full.optJSONObject("block_list")?.optString("@type") ==
                    "blockListMain"
            }
            done()
        }
    }

    private fun loadBasicGroupProfile(chatId: Long, title: String, basicGroupId: Long) {
        TdRepository.sendRaw(TdClient.request("getBasicGroupFullInfo").apply {
            put("basic_group_id", basicGroupId)
        }) { full ->
            if (currentChatId != chatId) return@sendRaw
            if (TdClient.isError(full)) {
                publish(chatId, title, "群组", "", emptyList(), false, true)
                return@sendRaw
            }
            currentInviteLink = full.optJSONObject("invite_link")
                ?.optString("invite_link") ?: ""
            val members = full.optJSONArray("members")
            if (members != null) {
                for (i in 0 until members.length()) {
                    val m = members.optJSONObject(i) ?: continue
                    addMember(m)
                }
            }
            publish(
                chatId, title, "群组",
                full.optString("description"),
                listOf("成员 ${memberSnapshot().size} 人"),
                canGetMembers = false, membersExhausted = true
            )
        }
    }

    private fun loadSupergroupProfile(
        chatId: Long,
        title: String,
        sgId: Long,
        isChannel: Boolean
    ) {
        supergroupId = sgId
        TdRepository.sendRaw(TdClient.request("getSupergroupFullInfo").apply {
            put("supergroup_id", sgId)
        }) { full ->
            if (currentChatId != chatId) return@sendRaw
            val kind = if (isChannel) "频道" else "超级群组"
            if (TdClient.isError(full)) {
                publish(chatId, title, kind, "", emptyList(), false, true)
                return@sendRaw
            }
            currentInviteLink = full.optJSONObject("invite_link")
                ?.optString("invite_link") ?: ""
            currentIsSupergroup = true
            currentSlowMode = full.optInt("slow_mode_delay")
            val memberCount = full.optInt("member_count")
            val adminCount = full.optInt("administrator_count")
            val lines = ArrayList<String>()
            if (memberCount > 0) {
                lines.add(
                    if (isChannel) "订阅者 $memberCount 人" else "成员 $memberCount 人"
                )
            }
            if (adminCount > 0) lines.add("管理员 $adminCount 人")
            val canMembers = full.optBoolean("can_get_members") &&
                !full.optBoolean("has_hidden_members")
            publish(
                chatId, title, kind,
                full.optString("description"),
                lines,
                canGetMembers = canMembers,
                membersExhausted = !canMembers
            )
            if (canMembers) loadMoreMembers()
        }
    }

    /** Next page of supergroup members (offset/limit paging,
     *  filter = recent). */
    fun loadMoreMembers() {
        val chatId = currentChatId
        val sgId = supergroupId
        if (chatId == 0L || sgId == 0L || membersLoading) return
        val current = _profile.value ?: return
        if (current.membersExhausted) return
        membersLoading = true
        val query = TdClient.request("getSupergroupMembers").apply {
            put("supergroup_id", sgId)
            put(
                "filter",
                JSONObject().put("@type", "supergroupMembersFilterRecent")
            )
            put("offset", memberOffset)
            put("limit", 50)
        }
        TdRepository.sendRaw(query) { response ->
            membersLoading = false
            if (currentChatId != chatId) return@sendRaw
            if (TdClient.isError(response)) {
                publishKeeping(current, membersExhausted = true)
                return@sendRaw
            }
            val members = response.optJSONArray("members")
            var added = 0
            if (members != null) {
                for (i in 0 until members.length()) {
                    val m = members.optJSONObject(i) ?: continue
                    if (addMember(m)) added++
                }
            }
            memberOffset += added
            val total = response.optInt("total_count")
            publishKeeping(
                current,
                membersExhausted = added == 0 ||
                    memberSnapshot().size >= total
            )
        }
    }

    private fun publishKeeping(current: ProfileUi, membersExhausted: Boolean) {
        _profile.value = current.copy(
            members = memberSnapshot(),
            membersExhausted = membersExhausted
        )
    }

    /** Append one chatMember; resolves the display name lazily
     *  (a getUser round-trip per unknown member, then republishes
     *  through the current profile). Returns false for non-user
     *  members (channels) and duplicates. */
    private fun addMember(member: JSONObject): Boolean {
        val sender = member.optJSONObject("member_id") ?: return false
        if (sender.optString("@type") != "messageSenderUser") return false
        val userId = sender.optLong("user_id")
        val statusType =
            member.optJSONObject("status")?.optString("@type") ?: ""
        val status = when (statusType) {
            "chatMemberStatusCreator" -> "群主"
            "chatMemberStatusAdministrator" -> "管理员"
            "chatMemberStatusRestricted" -> "受限"
            "chatMemberStatusBanned" -> "已封禁"
            "chatMemberStatusLeft" -> "已离开"
            else -> "成员"
        }
        val kind = when (statusType) {
            "chatMemberStatusCreator" -> "creator"
            "chatMemberStatusAdministrator" -> "administrator"
            "chatMemberStatusRestricted" -> "restricted"
            "chatMemberStatusBanned" -> "banned"
            "chatMemberStatusLeft" -> "left"
            else -> "member"
        }
        synchronized(membersLock) {
            if (memberList.any { it.userId == userId }) return false
            memberList.add(
                MemberEntry(userId, nameByUser[userId] ?: "", status, kind)
            )
        }
        if (!nameByUser.containsKey(userId)) fetchName(userId)
        return true
    }

    private fun fetchName(userId: Long) {
        if (!nameFetchPending.add(userId)) return
        val query = TdClient.request("getUser").apply { put("user_id", userId) }
        TdRepository.sendRaw(query) { user ->
            nameFetchPending.remove(userId)
            if (TdClient.isError(user)) return@sendRaw
            val name = listOf(
                user.optString("first_name"), user.optString("last_name")
            ).filter { it.isNotEmpty() }.joinToString(" ")
            if (name.isEmpty()) return@sendRaw
            nameByUser[userId] = name
            var snapshot: List<MemberEntry>? = null
            synchronized(membersLock) {
                var changed = false
                for (i in memberList.indices) {
                    if (memberList[i].userId == userId &&
                        memberList[i].name != name
                    ) {
                        memberList[i] = memberList[i].copy(name = name)
                        changed = true
                    }
                }
                if (changed) snapshot = ArrayList(memberList)
            }
            val current = _profile.value
            val snap = snapshot
            if (snap != null && current != null &&
                current.chatId == currentChatId
            ) {
                _profile.value = current.copy(members = snap)
            }
        }
    }
}
