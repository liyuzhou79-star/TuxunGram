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
    val statusKind: String = "member"
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
    val autoDeleteSeconds: Int = 0
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
    private var supergroupId = 0L
    private var memberOffset = 0
    private var membersLoading = false
    private val memberList = ArrayList<MemberEntry>()

    fun load(chatId: Long) {
        currentChatId = chatId
        currentUserId = 0L
        currentIsSecret = false
        currentAutoDelete = 0
        supergroupId = 0L
        memberOffset = 0
        membersLoading = false
        memberList.clear()
        _profile.value = null
        val query = TdClient.request("getChat").apply { put("chat_id", chatId) }
        TdRepository.sendRaw(query) { chat ->
            if (TdClient.isError(chat) || currentChatId != chatId) return@sendRaw
            val title = chat.optString("title")
            currentAutoDelete = chat.optInt("message_auto_delete_time")
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
            ArrayList(memberList), canGetMembers, membersExhausted,
            userId = currentUserId,
            isSecretChat = currentIsSecret,
            autoDeleteSeconds = currentAutoDelete
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
                listOf("成员 ${memberList.size} 人"),
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
                membersExhausted = added == 0 || memberList.size >= total
            )
        }
    }

    private fun publishKeeping(current: ProfileUi, membersExhausted: Boolean) {
        _profile.value = current.copy(
            members = ArrayList(memberList),
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
        if (memberList.any { it.userId == userId }) return false
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
        memberList.add(
            MemberEntry(userId, nameByUser[userId] ?: "", status, kind)
        )
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
            var changed = false
            for (i in memberList.indices) {
                if (memberList[i].userId == userId && memberList[i].name != name) {
                    memberList[i] = memberList[i].copy(name = name)
                    changed = true
                }
            }
            val current = _profile.value
            if (changed && current != null && current.chatId == currentChatId) {
                _profile.value = current.copy(members = ArrayList(memberList))
            }
        }
    }
}
