package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Everything the dedicated members page renders (v0.9.2,
 *  L4): the administrator section (pinned on top), the paged
 *  member list, and the search mode flag. */
data class MemberListUi(
    val chatId: Long = 0L,
    val admins: List<MemberEntry> = emptyList(),
    val members: List<MemberEntry> = emptyList(),
    val totalCount: Int = 0,
    val exhausted: Boolean = true,
    /** False when the server says this chat's members cannot
     *  be listed at all (can_get_members off / hidden). */
    val canList: Boolean = false,
    val searching: Boolean = false,
    val loading: Boolean = false
)

/**
 * The members page data (v0.9.2, L4). All calls verified in
 * td_api.tl:
 * - administrators: getChatAdministrators(chat_id) — works
 *   for basic groups and supergroups alike;
 * - supergroups/channels page through getSupergroupMembers
 *   (supergroupMembersFilterRecent, offset/limit), gated on
 *   supergroupFullInfo.can_get_members;
 * - basic groups have no paging call — their members are
 *   embedded in getBasicGroupFullInfo, loaded whole;
 * - search is searchChatMembers(chat_id, query, limit,
 *   chatMembersFilterMembers), one shot per query.
 *
 * Member rows reuse [MemberEntry] and the profile page's
 * status wording, so both surfaces describe roles the same
 * way. The member list proper excludes the administrators —
 * they live in their own pinned section.
 */
object MemberListStore {
    private val _state = MutableStateFlow(MemberListUi())
    val state: StateFlow<MemberListUi> = _state

    private val nameByUser = ConcurrentHashMap<Long, String>()
    private val nameFetchPending = ConcurrentHashMap.newKeySet<Long>()

    @Volatile
    private var currentChatId = 0L

    @Volatile
    private var supergroupId = 0L

    private var offset = 0
    private var loadingPage = false
    private val adminIds = HashSet<Long>()
    private val memberList = ArrayList<MemberEntry>()
    private val membersLock = Any()

    fun load(chatId: Long) {
        registerAvatarListener()
        currentChatId = chatId
        supergroupId = 0L
        offset = 0
        loadingPage = false
        synchronized(membersLock) {
            memberList.clear()
            adminIds.clear()
        }
        _state.value = MemberListUi(chatId = chatId, loading = true)
        loadAdmins(chatId)
        val query = TdClient.request("getChat").apply {
            put("chat_id", chatId)
        }
        TdRepository.sendRaw(query) { chat ->
            if (TdClient.isError(chat) || currentChatId != chatId) {
                return@sendRaw
            }
            val type = chat.optJSONObject("type") ?: return@sendRaw
            when (type.optString("@type")) {
                "chatTypeBasicGroup" -> loadBasicMembers(
                    chatId, type.optLong("basic_group_id")
                )
                "chatTypeSupergroup" -> {
                    supergroupId = type.optLong("supergroup_id")
                    loadSupergroupHead(chatId, supergroupId)
                }
                else -> _state.value = _state.value.copy(
                    loading = false, canList = false, exhausted = true
                )
            }
        }
    }

    /** Reload after a member action (ban/promote/…) when the
     *  page is showing this chat. */
    fun refreshIf(chatId: Long) {
        if (currentChatId == chatId && chatId != 0L) load(chatId)
    }

    private fun loadAdmins(chatId: Long) {
        val query = TdClient.request("getChatAdministrators").apply {
            put("chat_id", chatId)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response) || currentChatId != chatId) {
                return@sendRaw
            }
            val arr = response.optJSONArray("administrators")
                ?: return@sendRaw
            val admins = ArrayList<MemberEntry>()
            synchronized(membersLock) { adminIds.clear() }
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                val userId = a.optLong("user_id")
                if (userId == 0L) continue
                val isOwner = a.optBoolean("is_owner")
                val title = a.optString("custom_title")
                synchronized(membersLock) { adminIds.add(userId) }
                admins.add(
                    MemberEntry(
                        userId = userId,
                        name = nameByUser[userId] ?: "",
                        statusText = when {
                            title.isNotEmpty() -> title
                            isOwner -> "群主"
                            else -> "管理员"
                        },
                        statusKind = if (isOwner) {
                            "creator"
                        } else {
                            "administrator"
                        }
                    )
                )
                if (!nameByUser.containsKey(userId) || !AvatarStore.hasUser(userId)) fetchName(userId)
            }
            if (currentChatId != chatId) return@sendRaw
            _state.value = _state.value.copy(
                admins = admins.map { withPhoto(it) }
            )
            publishMembers()
        }
    }

    private fun loadBasicMembers(chatId: Long, basicGroupId: Long) {
        val query = TdClient.request("getBasicGroupFullInfo").apply {
            put("basic_group_id", basicGroupId)
        }
        TdRepository.sendRaw(query) { response ->
            if (currentChatId != chatId) return@sendRaw
            if (TdClient.isError(response)) {
                _state.value = _state.value.copy(
                    loading = false, canList = false, exhausted = true
                )
                return@sendRaw
            }
            val arr = response.optJSONArray("members")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    addMember(m)
                }
            }
            _state.value = _state.value.copy(
                loading = false,
                canList = true,
                totalCount = memberSnapshot().size,
                exhausted = true
            )
            publishMembers()
        }
    }

    private fun loadSupergroupHead(chatId: Long, sgId: Long) {
        val query = TdClient.request("getSupergroupFullInfo").apply {
            put("supergroup_id", sgId)
        }
        TdRepository.sendRaw(query) { response ->
            if (currentChatId != chatId) return@sendRaw
            if (TdClient.isError(response)) {
                _state.value = _state.value.copy(
                    loading = false, canList = false, exhausted = true
                )
                return@sendRaw
            }
            val canGet = response.optBoolean("can_get_members")
            _state.value = _state.value.copy(
                totalCount = response.optInt("member_count"),
                canList = canGet,
                loading = false,
                exhausted = !canGet
            )
            if (canGet) pageMembers(chatId)
        }
    }

    fun loadMore() {
        val chatId = currentChatId
        if (chatId == 0L || supergroupId == 0L) return
        if (_state.value.searching || _state.value.exhausted) return
        pageMembers(chatId)
    }

    private fun pageMembers(chatId: Long) {
        val sgId = supergroupId
        if (loadingPage || sgId == 0L) return
        loadingPage = true
        val query = TdClient.request("getSupergroupMembers").apply {
            put("supergroup_id", sgId)
            put(
                "filter",
                JSONObject().put("@type", "supergroupMembersFilterRecent")
            )
            put("offset", offset)
            put("limit", 100)
        }
        TdRepository.sendRaw(query) { response ->
            loadingPage = false
            if (currentChatId != chatId) return@sendRaw
            if (TdClient.isError(response)) {
                _state.value = _state.value.copy(exhausted = true)
                return@sendRaw
            }
            val arr = response.optJSONArray("members")
            var added = 0
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    if (addMember(m)) added++
                }
            }
            offset += added
            val total = response.optInt("total_count")
            _state.value = _state.value.copy(
                totalCount = if (total > 0) total else _state.value.totalCount,
                exhausted = added == 0 ||
                    memberSnapshot().size >= total
            )
            publishMembers()
        }
    }

    /** Search members by name (searchChatMembers). An empty
     *  query leaves search mode and restores the paged list
     *  from scratch, so the two modes never mix rows. */
    fun search(chatId: Long, rawQuery: String) {
        if (currentChatId != chatId) return
        val q = rawQuery.trim()
        if (q.isEmpty()) {
            load(chatId)
            return
        }
        val query = TdClient.request("searchChatMembers").apply {
            put("chat_id", chatId)
            put("query", q)
            put("limit", 100)
            put(
                "filter",
                JSONObject().put("@type", "chatMembersFilterMembers")
            )
        }
        TdRepository.sendRaw(query) { response ->
            if (currentChatId != chatId) return@sendRaw
            synchronized(membersLock) { memberList.clear() }
            if (!TdClient.isError(response)) {
                val arr = response.optJSONArray("members")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val m = arr.optJSONObject(i) ?: continue
                        addMember(m)
                    }
                }
            }
            _state.value = _state.value.copy(
                searching = true, exhausted = true
            )
            publishMembers()
        }
    }

    private fun memberSnapshot(): List<MemberEntry> =
        synchronized(membersLock) { ArrayList(memberList) }

    /** Fill a row's avatar path from the shared resolver (W0);
     *  the path is re-read at every publish, so a photo that
     *  lands late simply appears on the next publish. */
    private fun withPhoto(entry: MemberEntry): MemberEntry {
        val path = AvatarStore.pathForUser(entry.userId)
        return if (path != entry.photoPath) {
            entry.copy(photoPath = path)
        } else {
            entry
        }
    }

    @Volatile
    private var avatarListenerRegistered = false

    private fun registerAvatarListener() {
        if (avatarListenerRegistered) return
        avatarListenerRegistered = true
        AvatarStore.addListener {
            if (currentChatId == 0L) return@addListener
            _state.value = _state.value.copy(
                admins = _state.value.admins.map { withPhoto(it) }
            )
            publishMembers()
        }
    }

    /** Publish the member rows minus the administrators —
     *  they are pinned in their own section above. */
    private fun publishMembers() {
        val ids = synchronized(membersLock) { HashSet(adminIds) }
        val rows = memberSnapshot()
            .filter { it.userId !in ids }
            .map { withPhoto(it) }
        _state.value = _state.value.copy(members = rows)
    }

    /** Append one chatMember (same status wording as the
     *  profile page). Returns false for non-user members and
     *  duplicates. */
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
        if (!nameByUser.containsKey(userId) || !AvatarStore.hasUser(userId)) fetchName(userId)
        return true
    }

    private fun fetchName(userId: Long) {
        if (!nameFetchPending.add(userId)) return
        val query = TdClient.request("getUser").apply {
            put("user_id", userId)
        }
        TdRepository.sendRaw(query) { user ->
            nameFetchPending.remove(userId)
            if (TdClient.isError(user)) return@sendRaw
            AvatarStore.noteUser(user)
            val name = listOf(
                user.optString("first_name"), user.optString("last_name")
            ).filter { it.isNotEmpty() }.joinToString(" ")
            if (name.isEmpty()) return@sendRaw
            nameByUser[userId] = name
            var changed = false
            synchronized(membersLock) {
                for (i in memberList.indices) {
                    if (memberList[i].userId == userId &&
                        memberList[i].name != name
                    ) {
                        memberList[i] = memberList[i].copy(name = name)
                        changed = true
                    }
                }
            }
            val st = _state.value
            val admins = st.admins.map {
                if (it.userId == userId && it.name != name) {
                    changed = true
                    it.copy(name = name)
                } else {
                    it
                }
            }
            if (changed) {
                _state.value = st.copy(admins = admins)
                publishMembers()
            }
        }
    }
}
