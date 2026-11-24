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
    /** The member's tag (chatMember.tag, v0.9.39, F2):
     *  for an administrator this IS their custom title.
     *  Kept separately from [statusText] (which displays
     *  it) so the title editor can prefill the raw value. */
    val tag: String = "",
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
    val themeIsGift: Boolean = false,
    /** supergroupFullInfo.can_get_statistics (v0.9.13, T2):
     *  TDLib's own signal that getChatStatistics can answer
     *  for this chat; gates the profile page's 统计 entry. */
    val canGetStatistics: Boolean = false,
    /** The supergroup_id out of chat.type (v0.9.13, T6): the
     *  supergroup toggles address the group by this id, not
     *  by chat_id. 0 for non-supergroups. */
    val supergroupId: Long = 0,
    /** supergroupFullInfo.has_aggressive_anti_spam_enabled
     *  and its gate can_toggle_aggressive_anti_spam
     *  (v0.9.13, T6). */
    val antiSpamEnabled: Boolean = false,
    val canToggleAntiSpam: Boolean = false,
    /** supergroupFullInfo.has_hidden_members and its gate
     *  can_hide_members (v0.9.13, T6). */
    val membersHidden: Boolean = false,
    val canHideMembers: Boolean = false,
    /** Structured info fields (v0.9.20, AA2): read-only
     *  display data for the official label-above-value info
     *  rows on the profile page. phone only ever carries a
     *  value when the server hands it out (contacts);
     *  infoLines remains the header's compact summary. */
    val username: String = "",
    val phone: String = "",
    /** userFullInfo.birthdate formatted for display
     *  (v0.9.33, Z4); empty when the server hands out no
     *  birthdate — the row simply never shows, nothing is
     *  invented. */
    val birthday: String = "",
    /** userFullInfo.note (v0.9.38, E2): my private note
     *  about this user, server-saved via setUserNote and
     *  visible only to me. It annotates the profile; it
     *  never replaces the user's name anywhere. Empty =
     *  no note (the row then only offers to add one). */
    val note: String = "",
    /** user.is_contact (v0.9.33, Z4): the 删除联系人 entry
     *  only exists while this is true. */
    val isContact: Boolean = false,
    /** user.type == userTypeBot and its has_main_web_app
     *  flag (v0.9.37, D1): the profile page's 打开应用
     *  entry exists only when both hold — the flag is
     *  the server's own word that getMainWebApp can
     *  answer for this bot. */
    val isBot: Boolean = false,
    val botHasMainWebApp: Boolean = false,
    val commonGroups: Int = 0,
    val memberCount: Int = 0,
    val adminCount: Int = 0
)

/**
 * Profile page data (Batch 2, B5). The three FullInfo functions
 * take DIFFERENT ids — user_id / basic_group_id / supergroup_id
 * out of chat.type, never the chat_id (verified in td_api.tl) —
 * so load() resolves the chat type first, then branches.
 * Supergroup members page via getSupergroupMembers offset/limit;
 * basic-group members are embedded in the FullInfo itself.
 */
/** One row of the common-groups list (v0.9.54, Y2). */
data class CommonGroupUi(val chatId: Long, val title: String)

object ProfileStore {
    private val _profile = MutableStateFlow<ProfileUi?>(null)
    val profile: StateFlow<ProfileUi?> = _profile

    // ---- Common groups (v0.9.54, Y2) ----------------------
    // The profile's 共同群组 row used to be a bare count with
    // no way through. getGroupsInCommon (verified in
    // td_api.tl) returns the chat ids; titles resolve through
    // ChatStore — a group this account has never listed is
    // fetched once and flattened into the resolved cache, so
    // the row can show its real name instead of an id.

    private val _commonGroups =
        MutableStateFlow<List<CommonGroupUi>>(emptyList())
    val commonGroups: StateFlow<List<CommonGroupUi>> = _commonGroups

    private val _commonGroupsLoaded = MutableStateFlow(false)
    val commonGroupsLoaded: StateFlow<Boolean> = _commonGroupsLoaded

    @Volatile
    private var commonGroupsFor = 0L

    fun loadCommonGroups(userId: Long) {
        if (userId == 0L) return
        commonGroupsFor = userId
        _commonGroups.value = emptyList()
        _commonGroupsLoaded.value = false
        val query = TdClient.request("getGroupsInCommon").apply {
            put("user_id", userId)
            put("offset_chat_id", 0L)
            put("limit", 100)
        }
        TdRepository.sendRaw(query) { response ->
            if (commonGroupsFor != userId) return@sendRaw
            if (TdClient.isError(response)) {
                _commonGroupsLoaded.value = true
                return@sendRaw
            }
            val arr = response.optJSONArray("chat_ids")
            val ids = ArrayList<Long>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val id = arr.optLong(i)
                    if (id != 0L) ids.add(id)
                }
            }
            publishCommonGroups(ids)
            _commonGroupsLoaded.value = true
            for (id in ids) {
                if (ChatStore.itemOf(id) != null) continue
                val getQuery = TdClient.request("getChat").apply {
                    put("chat_id", id)
                }
                TdRepository.sendRaw(getQuery) { chatResponse ->
                    if (TdClient.isError(chatResponse)) {
                        return@sendRaw
                    }
                    ChatStore.noteResolvedChat(chatResponse)
                    if (commonGroupsFor == userId) {
                        publishCommonGroups(ids)
                    }
                }
            }
        }
    }

    private fun publishCommonGroups(ids: List<Long>) {
        _commonGroups.value = ids.map { id ->
            CommonGroupUi(
                chatId = id,
                title = ChatStore.itemOf(id)?.title ?: ""
            )
        }
    }

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
    private var currentCanStats = false
    private var currentAntiSpam = false
    private var currentCanAntiSpam = false
    private var currentMembersHidden = false
    private var currentCanHideMembers = false
    private var currentUsername = ""
    private var currentPhone = ""
    private var currentBirthday = ""
    private var currentNote = ""
    private var currentIsContact = false
    private var currentIsBot = false
    private var currentBotHasMainWebApp = false
    private var currentCommonGroups = 0
    private var currentMemberCount = 0
    private var currentAdminCount = 0
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
        currentCanStats = false
        currentAntiSpam = false
        currentCanAntiSpam = false
        currentMembersHidden = false
        currentCanHideMembers = false
        currentUsername = ""
        currentPhone = ""
        currentBirthday = ""
        currentNote = ""
        currentIsContact = false
        currentIsBot = false
        currentBotHasMainWebApp = false
        currentCommonGroups = 0
        currentMemberCount = 0
        currentAdminCount = 0
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
            themeIsGift = currentThemeIsGift,
            canGetStatistics = currentCanStats,
            supergroupId = supergroupId,
            antiSpamEnabled = currentAntiSpam,
            canToggleAntiSpam = currentCanAntiSpam,
            membersHidden = currentMembersHidden,
            canHideMembers = currentCanHideMembers,
            username = currentUsername,
            phone = currentPhone,
            birthday = currentBirthday,
            note = currentNote,
            isContact = currentIsContact,
            isBot = currentIsBot,
            botHasMainWebApp = currentBotHasMainWebApp,
            commonGroups = currentCommonGroups,
            memberCount = currentMemberCount,
            adminCount = currentAdminCount
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
                currentUsername = username
                // phone_number is only present when the
                // server shares it (contacts); absent stays
                // empty and the info row simply never shows.
                currentPhone = user.optString("phone_number")
                currentIsContact = user.optBoolean("is_contact")
                // Bot + main Web App flag (v0.9.37, D1):
                // both ride the user object itself.
                val userType = user.optJSONObject("type")
                currentIsBot = userType?.optString("@type") ==
                    "userTypeBot"
                currentBotHasMainWebApp = currentIsBot &&
                    userType?.optBoolean("has_main_web_app") == true
            }
            done()
        }
        TdRepository.sendRaw(TdClient.request("getUserFullInfo").apply {
            put("user_id", userId)
        }) { full ->
            if (!TdClient.isError(full)) {
                bio = full.optJSONObject("bio")?.optString("text") ?: ""
                commonGroups = full.optInt("group_in_common_count")
                currentCommonGroups = commonGroups
                currentBlocked =
                    full.optJSONObject("block_list")?.optString("@type") ==
                    "blockListMain"
                // Note (v0.9.38, E2): userFullInfo.note is
                // a formattedText holding MY private note
                // about this user (setUserNote writes it;
                // the tl has no other note field on users).
                currentNote = full.optJSONObject("note")
                    ?.optString("text") ?: ""
                // Birthdate (v0.9.33, Z4): userFullInfo
                // .birthdate {day, month, year}; year 0 means
                // the year is hidden. Absent object = no row.
                val bd = full.optJSONObject("birthdate")
                currentBirthday = if (bd == null) {
                    ""
                } else {
                    val day = bd.optInt("day")
                    val month = bd.optInt("month")
                    val year = bd.optInt("year")
                    when {
                        day <= 0 || month <= 0 -> ""
                        year > 0 -> "$year 年 $month 月 $day 日"
                        else -> "$month 月 $day 日"
                    }
                }
            }
            done()
        }
    }

    /** Remove a user from my contacts (v0.9.33, Z4):
     *  removeContacts — this tl's name for what other
     *  clients call deleteContacts. It drops the address-
     *  book entry only; the chat and its history stay. */
    fun removeContact(userId: Long, onDone: (String?) -> Unit) {
        val query = TdClient.request("removeContacts").apply {
            put(
                "user_ids",
                org.json.JSONArray().apply { put(userId) }
            )
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                onDone("删除失败，再试一次。（错误码 ${response.optInt("code")}）")
            } else {
                onDone(null)
            }
        }
    }

    /** Save (or, with an empty text, clear) my private
     *  note about a user (v0.9.38, E2): setUserNote with a
     *  plain formattedText — verified in td_api.tl; the
     *  note comes back in userFullInfo.note and is visible
     *  only to me. On success the open profile is updated
     *  in place; on failure nothing changes locally. */
    fun setUserNote(userId: Long, text: String, onDone: (String?) -> Unit) {
        val trimmed = text.trim()
        val note = org.json.JSONObject().apply {
            put("@type", "formattedText")
            put("text", trimmed)
            put("entities", org.json.JSONArray())
        }
        val query = TdClient.request("setUserNote").apply {
            put("user_id", userId)
            put("note", note)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                val message = response.optString("message")
                onDone(
                    when {
                        message.contains("NOTE", ignoreCase = true) &&
                            message.contains("LONG", ignoreCase = true) ->
                            "备注太长了，缩短一点再保存。"
                        else ->
                            "备注没保存上，再试一次。（错误码 " +
                                "${response.optInt("code")}）"
                    }
                )
            } else {
                if (currentUserId == userId) {
                    currentNote = trimmed
                    val current = _profile.value
                    if (current != null && current.userId == userId) {
                        publishKeeping(
                            current.copy(note = trimmed),
                            current.membersExhausted
                        )
                    }
                }
                onDone(null)
            }
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
            currentMemberCount = memberSnapshot().size
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
            currentCanStats = full.optBoolean("can_get_statistics")
            currentAntiSpam = full.optBoolean(
                "has_aggressive_anti_spam_enabled"
            )
            currentCanAntiSpam = full.optBoolean(
                "can_toggle_aggressive_anti_spam"
            )
            currentMembersHidden = full.optBoolean(
                "has_hidden_members"
            )
            currentCanHideMembers = full.optBoolean(
                "can_hide_members"
            )
            currentSlowMode = full.optInt("slow_mode_delay")
            val memberCount = full.optInt("member_count")
            val adminCount = full.optInt("administrator_count")
            currentMemberCount = memberCount
            currentAdminCount = adminCount
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

    /** Change the chat's message auto-delete time
     *  (setChatMessageAutoDeleteTime, v0.9.13 T3 — the same
     *  call secret chats use for their self-destruct timer,
     *  here for every chat type). On success the profile is
     *  reloaded so the row echoes the value the server
     *  actually holds (chat.message_auto_delete_time), never
     *  just the value that was asked for. A refusal — in a
     *  group this needs administrator rights — is translated
     *  into plain language. */
    fun setAutoDeleteTime(
        chatId: Long,
        seconds: Int,
        onDone: (Boolean, String) -> Unit
    ) {
        val query = TdClient.request("setChatMessageAutoDeleteTime").apply {
            put("chat_id", chatId)
            put("message_auto_delete_time", seconds)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                val message = response.optString("message")
                onDone(
                    false,
                    when {
                        message.contains("ADMIN", ignoreCase = true) ||
                            message.contains("rights", ignoreCase = true) ->
                            "你没有改这个设置的权限（群里要管理员）。"
                        else ->
                            "没改上，再试一次。（错误码 ${response.optInt("code")}）"
                    }
                )
            } else {
                onDone(
                    true,
                    if (seconds == 0) {
                        "自动删除已关闭。"
                    } else {
                        "已设为 ${autoDeleteLabel(seconds)}后自动删除。"
                    }
                )
                load(chatId)
            }
        }
    }

    /** Label for an auto-delete duration (v0.9.13, T3): the
     *  regular-chat presets plus a plain fallback for values
     *  another client may have set. */
    fun autoDeleteLabel(seconds: Int): String = when (seconds) {
        0 -> "关闭"
        86400 -> "1 天"
        604800 -> "1 周"
        2592000 -> "1 个月"
        else -> when {
            seconds % 86400 == 0 -> "${seconds / 86400} 天"
            seconds % 3600 == 0 -> "${seconds / 3600} 小时"
            seconds % 60 == 0 -> "${seconds / 60} 分钟"
            else -> "$seconds 秒"
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
        // Member tag (v0.9.39, F2): an administrator's
        // tag is their custom title and replaces the
        // plain role word, as in the member list.
        val tag = member.optString("tag")
        val status = when (statusType) {
            "chatMemberStatusCreator" ->
                tag.ifEmpty { "群主" }
            "chatMemberStatusAdministrator" ->
                tag.ifEmpty { "管理员" }
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
                MemberEntry(
                    userId, nameByUser[userId] ?: "", status, kind,
                    tag = tag
                )
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
