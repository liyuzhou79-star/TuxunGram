package com.tuxungram.android.td

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One row of the chat list, already flattened for the UI. */
data class ChatItem(
    val id: Long,
    val title: String,
    val isChannel: Boolean,
    val isGroup: Boolean,
    val preview: String,
    // Preview sender prefix (v0.9.18, Y4): for group chats,
    // the last message's sender split out of the preview so
    // the row can color the name with the official name
    // color while the body stays secondary gray. Empty for
    // private chats, channels and outgoing last messages
    // (those keep the 你： prefix inside preview itself).
    val previewSenderName: String = "",
    val previewSenderId: Long = 0L,
    val dateSeconds: Int,
    val unreadCount: Int,
    val isPinned: Boolean,
    // Locally pinned (v0.9.23, P1): pinned on this phone only
    // via LocalPins after the server pins in the list filled
    // up. Renders in the pinned area after the server pins,
    // badged 本地. Only ever set for the main and archive
    // lists — folder lists keep pure TDLib order.
    val isLocalPinned: Boolean = false,
    val isMuted: Boolean,
    val canSendBasic: Boolean,
    val canSendPhotos: Boolean,
    val order: Long,
    val lastMessageId: Long = 0L,
    val isMarkedAsUnread: Boolean = false,
    val photoFileId: Int = 0,
    val photoPath: String? = null,
    // End-to-end encrypted secret chat (chatTypeSecret); the
    // list row and the chat screen mark it with a lock.
    val isSecret: Boolean = false,
    // Private chats only: the other user is online right now
    // (userStatusOnline, tracked from updateUser /
    // updateUserStatus; v0.9.9 W1 online dot).
    val isOnline: Boolean = false,
    // Reactions this chat allows, from chat.available_reactions
    // (verified in td_api.tl): null = all reactions allowed
    // (chatAvailableReactionsAll or unknown), empty = reactions
    // are off here, otherwise only these emojis may be added.
    // The server answers 400 for anything outside this list.
    val allowedReactions: List<String>? = null,
    // Local path of the last message's media thumbnail
    // (v0.9.11, A2): photo / video / animation / sticker
    // previews render as a small image chip in the row
    // instead of text only. Null while downloading or for
    // text-only last messages.
    val previewThumbPath: String? = null,
    // Draft preview (v0.9.16, W2): the chat's draft text
    // from chat.draft_message, "" when there is no draft.
    // When non-empty the row shows a red 草稿： prefix plus
    // this text INSTEAD of the last-message preview, like
    // mature clients. The draft's effect on list ORDER is
    // not computed here: updateChatDraftMessage carries
    // fresh positions and this store sorts by them verbatim.
    val draftText: String = "",
    // Top-bar subtitle data (v0.9.18, Y6), display-only:
    // the peer's presence line for private chats (from the
    // same user objects that feed the online dot, flattened
    // by ContactsStore.statusOf) — "" when nothing is known,
    // and the header shows no subtitle rather than a guess.
    val presenceText: String = "",
    // Member count for groups and channels, from the
    // basicGroup / supergroup object's member_count (the
    // chat object itself carries none; verified in
    // td_api.tl). 0 = unknown, and the header hides it.
    val memberCount: Int = 0
)

/**
 * Maintains the main chat list the way TDLib intends: loadChats
 * pulls chats into the local database, they arrive and change via
 * updates, and this store keeps the raw chat objects, patches them
 * on each update, and publishes a sorted snapshot. Sorting is by
 * (order, id) descending, exactly as TDLib positions dictate — the
 * UI never re-sorts by time.
 */
object ChatStore {
    private val _chats = MutableStateFlow<List<ChatItem>>(emptyList())
    val chats: StateFlow<List<ChatItem>> = _chats

    private val _allLoaded = MutableStateFlow(false)
    val allLoaded: StateFlow<Boolean> = _allLoaded

    private val cache = ConcurrentHashMap<Long, JSONObject>()

    /** Avatar bookkeeping: local paths and downloads live in
     *  [AvatarStore] since v0.9.9 (W0) — this store only keeps
     *  the private-chat fallback mapping, chat id -> the file
     *  id of the other user's small profile photo. */
    private val userPhotoFileByChat = ConcurrentHashMap<Long, Int>()
    private val chatIdByUserId = ConcurrentHashMap<Long, Long>()
    private val onlineByChat = ConcurrentHashMap<Long, Boolean>()
    // User display names (v0.9.18, Y4): filled from every
    // user object the update stream and getUser answers
    // bring in; drives the chat-list preview sender prefix.
    private val userNameById = ConcurrentHashMap<Long, String>()
    private val userNameRequested = ConcurrentHashMap.newKeySet<Long>()
    // Header subtitle data (v0.9.18, Y6): presence lines by
    // chat id (private chats; fed by the same updateUser /
    // updateUserStatus stream as the online dot, plus the
    // getUser answer in fetchUserPhoto), and member counts
    // by chat id (groups/channels; fed by one-shot
    // getBasicGroup / getSupergroup answers and by
    // updateBasicGroup / updateSupergroup).
    private val presenceByChat = ConcurrentHashMap<Long, String>()
    private val memberCountByChat = ConcurrentHashMap<Long, Int>()
    private val memberCountRequested = ConcurrentHashMap.newKeySet<Long>()

    @Volatile
    private var attached = false

    // ---- Recompute throttling (v0.9.1, audit K1) ----
    // handleUpdate used to re-sort and republish all three
    // lists (main + archive + folder) on EVERY TDLib update,
    // on the receiver thread: a bulk first sync over thousands
    // of chats made that O(n log n) work per update and could
    // delay update processing itself. The published lists are
    // pure functions of the cache and StateFlow already
    // conflates intermediate values for the UI, so bursts are
    // now coalesced: at most one flush per RECOMPUTE_WINDOW_MS,
    // with a guaranteed trailing flush so the final state is
    // always published. The flush itself still runs on the
    // receiver thread (see requestRecompute), exactly where
    // every cache mutation happens — no new threading surface.
    private val recomputeHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var recomputeDirty = false

    @Volatile
    private var recomputeScheduled = false

    @Volatile
    private var lastRecomputeAt = 0L

    private const val RECOMPUTE_WINDOW_MS = 100L

    private fun requestRecompute() {
        recomputeDirty = true
        val now = System.currentTimeMillis()
        if (now - lastRecomputeAt >= RECOMPUTE_WINDOW_MS) {
            flushRecompute()
            return
        }
        if (recomputeScheduled) return
        recomputeScheduled = true
        recomputeHandler.postDelayed({
            recomputeScheduled = false
            // Route the trailing flush through a throwaway
            // request: its response callback runs on the
            // receiver thread, like every other cache access.
            // If the client is mid-restart the probe is dropped
            // silently — the dirty flag stays set and the next
            // update (window long past) flushes synchronously.
            TdRepository.sendRaw(
                TdClient.request("getAuthorizationState")
            ) {
                flushRecompute()
            }
        }, RECOMPUTE_WINDOW_MS)
    }

    private fun flushRecompute() {
        if (!recomputeDirty) return
        recomputeDirty = false
        lastRecomputeAt = System.currentTimeMillis()
        pruneLocalPins()
        recompute()
        recomputeArchive()
        recomputeFolder()
    }

    // ---- Local pins (v0.9.23, P1) ----

    /** Hard ceiling for server + local pins shown in one
     *  list, per the product decision: the server allowance
     *  (5 free) plus local pins filling up to 10 in total. */
    private const val MAX_TOTAL_PINS = 10

    // Server pin allowances per list, from getOption
    // ("pinned_chat_count_max" /
    // "pinned_archived_chat_count_max", td_api.tl
    // toggleChatIsPinned). 5 is the free-account value the
    // tl documents as the default; the fetched value wins
    // as soon as it lands (Premium raises it server-side).
    @Volatile
    private var mainPinCap = 5

    @Volatile
    private var archivePinCap = 5

    private fun fetchPinCaps() {
        for ((name, archive) in listOf(
            "pinned_chat_count_max" to false,
            "pinned_archived_chat_count_max" to true
        )) {
            val query = TdClient.request("getOption").apply {
                put("name", name)
            }
            TdRepository.sendRaw(query) { response ->
                if (TdClient.isError(response)) return@sendRaw
                val value = response.optLong("value", 0L)
                if (value > 0) {
                    if (archive) {
                        archivePinCap = value.toInt()
                    } else {
                        mainPinCap = value.toInt()
                    }
                }
            }
        }
    }

    /** One-shot plain-language pin notice (cap reached, or a
     *  pin landed as a local pin), consumed by the list
     *  screens as a Toast — the StoryStore.postNotice
     *  pattern. */
    private val _pinNotice = MutableStateFlow<String?>(null)
    val pinNotice: StateFlow<String?> = _pinNotice

    fun clearPinNotice() {
        _pinNotice.value = null
    }

    /** Settings import replaced the local pin list: republish
     *  so the pinned areas reflect it. */
    fun onLocalPinsChanged() {
        requestRecompute()
    }

    /** Remove local pins whose chat is provably gone: the
     *  chat is in the cache but holds no position in either
     *  the main or the archive list (deleted chats and chats
     *  removed from every list end up like that). Ids whose
     *  chat is not cached yet are kept — "not loaded" is not
     *  "gone". */
    private fun pruneLocalPins() {
        val localIds = LocalPins.ids.value
        if (localIds.isEmpty()) return
        val gone = HashSet<Long>()
        for (id in localIds) {
            val chat = cache[id] ?: continue
            val inMain = mainPosition(chat)?.optLong("order") ?: 0L
            val inArchive =
                archivePosition(chat)?.optLong("order") ?: 0L
            if (inMain == 0L && inArchive == 0L) gone.add(id)
        }
        LocalPins.prune(gone)
    }

    /** Flag and re-order one published list for local pins:
     *  server pins first (their TDLib order already puts
     *  them on top), then the locally pinned chats in the
     *  order the user pinned them, then everything else in
     *  its existing order. A chat that is server-pinned is
     *  never also flagged local. */
    private fun applyLocalPins(
        items: List<ChatItem>
    ): List<ChatItem> {
        val localIds = LocalPins.ids.value
        if (localIds.isEmpty()) return items
        val localSet = localIds.toSet()
        val flagged = items.map { item ->
            if (item.id in localSet && !item.isPinned) {
                item.copy(isLocalPinned = true)
            } else {
                item
            }
        }
        val serverPinned = flagged.filter { it.isPinned }
        val localPinned = localIds.mapNotNull { id ->
            flagged.firstOrNull { it.id == id && it.isLocalPinned }
        }
        val rest = flagged.filter { !it.isPinned && !it.isLocalPinned }
        return serverPinned + localPinned + rest
    }

    /** Server-pinned chats in one list; [secret] = null
     *  counts all of them, otherwise only the ones sharing
     *  the target's secret-ness (the tl grants secret chats
     *  their own allowance of the same size). */
    private fun serverPinCount(
        archive: Boolean,
        secret: Boolean?
    ): Int {
        var n = 0
        for (chat in cache.values) {
            val position = (
                if (archive) archivePosition(chat)
                else mainPosition(chat)
                ) ?: continue
            if (position.optLong("order") == 0L) continue
            if (!position.optBoolean("is_pinned")) continue
            if (secret != null) {
                val isSecret = chat.optJSONObject("type")
                    ?.optString("@type") == "chatTypeSecret"
                if (isSecret != secret) continue
            }
            n++
        }
        return n
    }

    /** Locally pinned chats currently shown in one list. */
    private fun localPinCountIn(archive: Boolean): Int {
        val localIds = LocalPins.ids.value
        if (localIds.isEmpty()) return 0
        var n = 0
        for (id in localIds) {
            val chat = cache[id] ?: continue
            val position = (
                if (archive) archivePosition(chat)
                else mainPosition(chat)
                ) ?: continue
            if (position.optLong("order") == 0L) continue
            if (position.optBoolean("is_pinned")) continue
            n++
        }
        return n
    }

    fun attach() {
        if (attached) return
        attached = true
        AvatarStore.attach()
        // Download manager (v0.9.12, S5) keeps raw file state
        // from the same update stream.
        DownloadStore.attach()
        // A path landing in the resolver (download finished, or
        // an already-downloaded file answered) republishes the
        // list with avatars filled in — no other update needed.
        AvatarStore.addListener { requestRecompute() }
        TdRepository.addUpdateListener { update -> handleUpdate(update) }
    }

    fun onReady() {
        attach()
        fetchPinCaps()
        loadMore()
    }

    /** Best-known title for a chat id ("" when unknown); used by
     *  search results, whose messages only carry the chat id. */
    fun titleOf(chatId: Long): String =
        cache[chatId]?.optString("title") ?: ""

    /** The other user's id for a private chat (from the
     *  cached chatTypePrivate), 0 for anything else or when
     *  the chat is not cached. Web App keyboard buttons in
     *  a private chat belong to this user (v0.9.36, C1). */
    fun userIdOfChat(chatId: Long): Long {
        val type = cache[chatId]?.optJSONObject("type")
            ?: return 0L
        return if (type.optString("@type") == "chatTypePrivate") {
            type.optLong("user_id")
        } else {
            0L
        }
    }

    /** The private chat id for a user id, 0 when no such
     *  chat has been seen yet. */
    fun chatIdOfUser(userId: Long): Long =
        chatIdByUserId[userId] ?: 0L

    /** Coarse kind of a cached chat straight from its
     *  type object — "user", "group" or "channel"; null
     *  when the chat is not cached. Works for chats that
     *  sit in no list (public search results, v0.9.36,
     *  C4), where itemOf has no position to flatten. */
    fun kindOfChat(chatId: Long): String? {
        val type = cache[chatId]?.optJSONObject("type")
            ?: return null
        return when (type.optString("@type")) {
            "chatTypePrivate", "chatTypeSecret" -> "user"
            "chatTypeBasicGroup" -> "group"
            "chatTypeSupergroup" ->
                if (type.optBoolean("is_channel")) {
                    "channel"
                } else {
                    "group"
                }
            else -> null
        }
    }

    /** A flattened item for any cached chat, whatever list it is
     *  on; used when navigating somewhere from a folder/search. */
    fun itemOf(chatId: Long): ChatItem? {
        val chat = cache[chatId] ?: return null
        val positions = chat.optJSONArray("positions") ?: return null
        for (i in 0 until positions.length()) {
            val p = positions.optJSONObject(i) ?: continue
            if (p.optLong("order") != 0L) return toItem(chat, p, p.optLong("order"))
        }
        return null
    }

    /** True while the main list has not been fully synced yet
     *  (drives the home screen's "syncing" banner). */
    private val _syncing = MutableStateFlow(true)
    val syncing: StateFlow<Boolean> = _syncing

    @Volatile
    private var loadingChats = false
    private var emptyLoadRetries = 0

    fun loadMore() {
        if (_allLoaded.value || loadingChats) return
        loadingChats = true
        val sizeBefore = cache.size
        val query = TdClient.request("loadChats").apply {
            put("chat_list", JSONObject().put("@type", "chatListMain"))
            put("limit", 100)
        }
        TdRepository.sendRaw(query) { response ->
            loadingChats = false
            if (TdClient.isError(response) && response.optInt("code") == 404) {
                // 404 is TDLib's "the list is complete" signal: every
                // chat, however old, has been pulled in.
                _allLoaded.value = true
            }
            requestRecompute()
            _syncing.value = !_allLoaded.value
            // Keep pulling until TDLib says the list is complete, so
            // the chat list is whole even if the user never scrolls.
            // On a FRESH login the cache starts empty and the first
            // calls may bring nothing in while TDLib is still
            // fetching from the server — so the chain also retries
            // a few times without growth before pausing; scrolling
            // and updateNewChat re-trigger it afterwards. The retry
            // bound is a spin guard, not a data cap.
            if (!_allLoaded.value) {
                if (cache.size > sizeBefore) {
                    emptyLoadRetries = 0
                    loadMore()
                } else if (emptyLoadRetries < 5) {
                    emptyLoadRetries++
                    loadMore()
                }
            }
        }
    }

    // ---- Archive list (chatListArchive) ----

    private val _archivedChats = MutableStateFlow<List<ChatItem>>(emptyList())
    val archivedChats: StateFlow<List<ChatItem>> = _archivedChats

    private val _archiveLoaded = MutableStateFlow(false)
    val archiveLoaded: StateFlow<Boolean> = _archiveLoaded

    @Volatile
    private var loadingArchive = false
    private var emptyArchiveRetries = 0

    fun loadArchived() {
        if (_archiveLoaded.value || loadingArchive) return
        loadingArchive = true
        val countBefore = archiveCount()
        val query = TdClient.request("loadChats").apply {
            put(
                "chat_list",
                JSONObject().put("@type", "chatListArchive")
            )
            put("limit", 100)
        }
        TdRepository.sendRaw(query) { response ->
            loadingArchive = false
            if (TdClient.isError(response) && response.optInt("code") == 404) {
                _archiveLoaded.value = true
            }
            requestRecompute()
            if (!_archiveLoaded.value) {
                if (archiveCount() > countBefore) {
                    emptyArchiveRetries = 0
                    loadArchived()
                } else if (emptyArchiveRetries < 5) {
                    emptyArchiveRetries++
                    loadArchived()
                }
            }
        }
    }

    private fun archivePosition(chat: JSONObject): JSONObject? {
        val positions = chat.optJSONArray("positions") ?: return null
        for (i in 0 until positions.length()) {
            val p = positions.optJSONObject(i) ?: continue
            if (p.optJSONObject("list")?.optString("@type") == "chatListArchive") {
                return p
            }
        }
        return null
    }

    private fun archiveCount(): Int {
        var n = 0
        for (chat in cache.values) {
            val p = archivePosition(chat) ?: continue
            if (p.optLong("order") != 0L) n++
        }
        return n
    }

    private fun recomputeArchive() {
        val items = ArrayList<ChatItem>()
        for (chat in cache.values) {
            val position = archivePosition(chat) ?: continue
            val order = position.optLong("order")
            if (order == 0L) continue
            items.add(toItem(chat, position, order))
        }
        items.sortWith(compareByDescending<ChatItem> { it.order }.thenByDescending { it.id })
        _archivedChats.value = applyLocalPins(items)
    }

    // ---- Chat folders (Batch 2, B7): tabs only, no editor ----

    data class FolderTab(val id: Int, val name: String)

    private val _folders = MutableStateFlow<List<FolderTab>>(emptyList())
    val folders: StateFlow<List<FolderTab>> = _folders

    private val _selectedFolder = MutableStateFlow<Int?>(null)
    val selectedFolder: StateFlow<Int?> = _selectedFolder

    private val _folderChats = MutableStateFlow<List<ChatItem>>(emptyList())
    val folderChats: StateFlow<List<ChatItem>> = _folderChats

    private val _folderLoaded = MutableStateFlow(false)
    val folderLoaded: StateFlow<Boolean> = _folderLoaded

    @Volatile
    private var loadingFolder = false
    private var emptyFolderRetries = 0

    fun selectFolder(folderId: Int?) {
        _selectedFolder.value = folderId
        _folderChats.value = emptyList()
        _folderLoaded.value = folderId == null
        emptyFolderRetries = 0
        if (folderId != null) loadFolderMore()
    }

    fun loadFolderMore() {
        val folderId = _selectedFolder.value ?: return
        if (_folderLoaded.value || loadingFolder) return
        loadingFolder = true
        val countBefore = folderCount(folderId)
        val query = TdClient.request("loadChats").apply {
            put(
                "chat_list",
                JSONObject().apply {
                    put("@type", "chatListFolder")
                    put("chat_folder_id", folderId)
                }
            )
            put("limit", 100)
        }
        TdRepository.sendRaw(query) { response ->
            loadingFolder = false
            if (TdClient.isError(response) && response.optInt("code") == 404) {
                _folderLoaded.value = true
            }
            requestRecompute()
            if (!_folderLoaded.value && _selectedFolder.value == folderId) {
                if (folderCount(folderId) > countBefore) {
                    emptyFolderRetries = 0
                    loadFolderMore()
                } else if (emptyFolderRetries < 5) {
                    emptyFolderRetries++
                    loadFolderMore()
                }
            }
        }
    }

    private fun folderPosition(chat: JSONObject, folderId: Int): JSONObject? {
        val positions = chat.optJSONArray("positions") ?: return null
        for (i in 0 until positions.length()) {
            val p = positions.optJSONObject(i) ?: continue
            val list = p.optJSONObject("list") ?: continue
            if (list.optString("@type") == "chatListFolder" &&
                list.optInt("chat_folder_id") == folderId
            ) {
                return p
            }
        }
        return null
    }

    private fun folderCount(folderId: Int): Int {
        var n = 0
        for (chat in cache.values) {
            val p = folderPosition(chat, folderId) ?: continue
            if (p.optLong("order") != 0L) n++
        }
        return n
    }

    private fun recomputeFolder() {
        val folderId = _selectedFolder.value ?: return
        val items = ArrayList<ChatItem>()
        for (chat in cache.values) {
            val position = folderPosition(chat, folderId) ?: continue
            val order = position.optLong("order")
            if (order == 0L) continue
            items.add(toItem(chat, position, order))
        }
        items.sortWith(compareByDescending<ChatItem> { it.order }.thenByDescending { it.id })
        _folderChats.value = items
    }

    /** Move a chat back out of the archive into the main list. */
    fun unarchive(chat: ChatItem) {
        val query = TdClient.request("addChatToList").apply {
            put("chat_id", chat.id)
            put("chat_list", JSONObject().put("@type", "chatListMain"))
        }
        TdRepository.sendRaw(query) { reportIfError(it) }
    }

    private fun handleUpdate(update: JSONObject) {
        val chatId = update.optLong("chat_id", 0L)
        when (update.optString("@type")) {
            "updateFile" -> {
                // Avatar completions land in the shared resolver
                // (v0.9.9, W0); the blanket recompute at the end
                // of handleUpdate republishes rows with the path.
                val file = update.optJSONObject("file") ?: return
                AvatarStore.noteFile(file)
            }
            "updateNewChat" -> {
                val chat = update.optJSONObject("chat") ?: return
                cache[chat.optLong("id")] = chat
                val type = chat.optJSONObject("type")
                val typeName = type?.optString("@type")
                if (typeName == "chatTypePrivate" || typeName == "chatTypeSecret") {
                    val userId = type.optLong("user_id")
                    chatIdByUserId[userId] = chat.optLong("id")
                    fetchUserPhoto(chat.optLong("id"), userId)
                }
                // Fresh-login sync: chats arriving on their own
                // mean TDLib is actively fetching — keep the
                // loadChats chain going until it answers 404.
                if (!_allLoaded.value) {
                    emptyLoadRetries = 0
                    loadMore()
                }
            }
            "updateUser" -> {
                val user = update.optJSONObject("user") ?: return
                AvatarStore.noteUser(user)
                val seenName = userNameOf(user)
                if (seenName.isNotEmpty()) {
                    userNameById[user.optLong("id")] = seenName
                }
                val chatIdForUser = chatIdByUserId[user.optLong("id")] ?: return
                onlineByChat[chatIdForUser] =
                    user.optJSONObject("status")?.optString("@type") ==
                    "userStatusOnline"
                presenceByChat[chatIdForUser] =
                    ContactsStore.statusOf(user.optJSONObject("status")).first
                val smallId = user.optJSONObject("profile_photo")
                    ?.optJSONObject("small")?.optInt("id", 0) ?: 0
                if (smallId != 0) {
                    userPhotoFileByChat[chatIdForUser] = smallId
                }
            }
            "updateUserStatus" -> {
                // Presence flips arrive on this lighter update
                // (td_api.tl line 11652); the chat id comes from
                // the private-chat mapping built on updateNewChat.
                val chatIdForUser =
                    chatIdByUserId[update.optLong("user_id")] ?: return
                onlineByChat[chatIdForUser] =
                    update.optJSONObject("status")?.optString("@type") ==
                    "userStatusOnline"
                presenceByChat[chatIdForUser] =
                    ContactsStore.statusOf(
                        update.optJSONObject("status")
                    ).first
            }
            "updateBasicGroup" -> {
                // Member-count freshness (v0.9.18, Y6): the
                // update carries the group object, not a chat
                // id — resolve through the cached chat types.
                val group = update.optJSONObject("basic_group") ?: return
                noteMemberCount(
                    basicGroupId = group.optLong("id"),
                    supergroupId = 0L,
                    count = group.optInt("member_count", 0)
                )
            }
            "updateSupergroup" -> {
                val group = update.optJSONObject("supergroup") ?: return
                noteMemberCount(
                    basicGroupId = 0L,
                    supergroupId = group.optLong("id"),
                    count = group.optInt("member_count", 0)
                )
            }
            "updateChatPhoto" -> {
                val chat = cache[chatId] ?: return
                if (update.has("photo") && !update.isNull("photo")) {
                    chat.put("photo", update.opt("photo"))
                } else {
                    chat.remove("photo")
                }
            }
            "updateChatLastMessage" -> {
                val chat = cache[chatId] ?: return
                if (update.has("last_message")) {
                    chat.put("last_message", update.opt("last_message"))
                }
                if (update.has("positions")) {
                    chat.put("positions", update.opt("positions"))
                }
            }
            "updateChatPosition" -> {
                val chat = cache[chatId] ?: return
                val position = update.optJSONObject("position") ?: return
                patchPosition(chat, position)
            }
            "updateChatTitle" -> {
                val chat = cache[chatId] ?: return
                chat.put("title", update.optString("title"))
            }
            "updateChatReadInbox" -> {
                val chat = cache[chatId] ?: return
                chat.put("unread_count", update.optInt("unread_count"))
                chat.put(
                    "last_read_inbox_message_id",
                    update.optLong("last_read_inbox_message_id")
                )
            }
            "updateChatReadOutbox" -> {
                val chat = cache[chatId] ?: return
                chat.put(
                    "last_read_outbox_message_id",
                    update.optLong("last_read_outbox_message_id")
                )
            }
            "updateChatNotificationSettings" -> {
                val chat = cache[chatId] ?: return
                if (update.has("notification_settings")) {
                    chat.put(
                        "notification_settings",
                        update.opt("notification_settings")
                    )
                }
            }
            "updateChatFolders" -> {
                val arr = update.optJSONArray("chat_folders") ?: return
                val tabs = ArrayList<FolderTab>()
                for (i in 0 until arr.length()) {
                    val f = arr.optJSONObject(i) ?: continue
                    val name = f.optJSONObject("name")
                        ?.optJSONObject("text")?.optString("text") ?: ""
                    tabs.add(FolderTab(f.optInt("id"), name))
                }
                _folders.value = tabs
                // Selected folder vanished on the server: fall
                // back to the main list rather than an empty tab.
                val selected = _selectedFolder.value
                if (selected != null && tabs.none { it.id == selected }) {
                    selectFolder(null)
                }
            }
            "updateChatAvailableReactions" -> {
                val chat = cache[chatId] ?: return
                if (update.has("available_reactions")) {
                    chat.put(
                        "available_reactions",
                        update.opt("available_reactions")
                    )
                }
            }
            "updateChatIsMarkedAsUnread" -> {
                val chat = cache[chatId] ?: return
                chat.put(
                    "is_marked_as_unread",
                    update.optBoolean("is_marked_as_unread")
                )
            }
            "updateChatDraftMessage" -> {
                val chat = cache[chatId] ?: return
                if (update.has("positions")) {
                    chat.put("positions", update.opt("positions"))
                }
                if (update.has("draft_message")) {
                    chat.put("draft_message", update.opt("draft_message"))
                }
            }
            "updateChatRemovedFromList" -> {
                val list = update.optJSONObject("chat_list") ?: return
                if (list.optString("@type") == "chatListMain") {
                    cache.remove(chatId)
                }
                // Removal from the archive list is reflected by
                // recomputeArchive() below; the chat itself stays
                // cached for the main list.
            }
            else -> return
        }
        requestRecompute()
    }

    private fun patchPosition(chat: JSONObject, position: JSONObject) {
        val listType = position.optJSONObject("list")?.optString("@type") ?: return
        val positions = chat.optJSONArray("positions") ?: JSONArray()
        val next = JSONArray()
        var replaced = false
        for (i in 0 until positions.length()) {
            val p = positions.optJSONObject(i) ?: continue
            if (p.optJSONObject("list")?.optString("@type") == listType) {
                next.put(position)
                replaced = true
            } else {
                next.put(p)
            }
        }
        if (!replaced) next.put(position)
        chat.put("positions", next)
    }

    private fun mainPosition(chat: JSONObject): JSONObject? {
        val positions = chat.optJSONArray("positions") ?: return null
        for (i in 0 until positions.length()) {
            val p = positions.optJSONObject(i) ?: continue
            if (p.optJSONObject("list")?.optString("@type") == "chatListMain") return p
        }
        return null
    }

    private fun recompute() {
        val items = ArrayList<ChatItem>()
        for (chat in cache.values) {
            val position = mainPosition(chat) ?: continue
            val order = position.optLong("order")
            if (order == 0L) continue
            items.add(toItem(chat, position, order))
        }
        items.sortWith(compareByDescending<ChatItem> { it.order }.thenByDescending { it.id })
        _chats.value = applyLocalPins(items)
    }

    private fun toItem(chat: JSONObject, position: JSONObject, order: Long): ChatItem {
        val type = chat.optJSONObject("type")
        val typeName = type?.optString("@type")
        val isChannel = typeName == "chatTypeSupergroup" &&
            type.optBoolean("is_channel")
        val isGroup = typeName == "chatTypeBasicGroup" ||
            (typeName == "chatTypeSupergroup" && !isChannel)
        val last = if (chat.has("last_message") && !chat.isNull("last_message")) {
            chat.optJSONObject("last_message")
        } else {
            null
        }
        val muteFor = chat.optJSONObject("notification_settings")
            ?.optInt("mute_for", 0) ?: 0
        val permissions = chat.optJSONObject("permissions")
        val senderPrefix = previewSenderOf(chat, last)
        // Header subtitle data (v0.9.18, Y6): groups and
        // channels kick off the one-shot count lookup while
        // it is unknown; private chats read the presence
        // cache the user updates feed.
        if (isGroup || isChannel) fetchMemberCount(chat)
        return ChatItem(
            id = chat.optLong("id"),
            title = chat.optString("title"),
            isChannel = isChannel,
            isGroup = isGroup,
            preview = previewOf(last),
            previewSenderName = senderPrefix.first,
            previewSenderId = senderPrefix.second,
            dateSeconds = last?.optInt("date", 0) ?: 0,
            unreadCount = chat.optInt("unread_count"),
            isPinned = position.optBoolean("is_pinned"),
            isMuted = muteFor > 0,
            canSendBasic = permissions?.optBoolean("can_send_basic_messages", true) ?: true,
            canSendPhotos = permissions?.optBoolean("can_send_photos", true) ?: true,
            order = order,
            lastMessageId = last?.optLong("id", 0L) ?: 0L,
            isSecret = typeName == "chatTypeSecret",
            isMarkedAsUnread = chat.optBoolean("is_marked_as_unread"),
            photoFileId = avatarFileId(chat),
            photoPath = avatarPath(chat),
            isOnline = onlineByChat[chat.optLong("id")] == true,
            allowedReactions = allowedReactionsOf(chat),
            previewThumbPath = AvatarStore.pathForFileObj(
                previewThumbFile(last)
            ),
            draftText = draftTextOf(chat),
            presenceText = presenceByChat[chat.optLong("id")] ?: "",
            memberCount = memberCountByChat[chat.optLong("id")] ?: 0
        )
    }

    /** The chat's draft as a one-line preview (v0.9.16, W2):
     *  chat.draft_message is a draftMessage whose content is
     *  a DraftMessageContent (td_api.tl L3886+). Text drafts
     *  preview as their text; media drafts preview as their
     *  type label, mirroring previewOf's vocabulary. A draft
     *  object whose content is missing or unrecognised still
     *  counts as a draft ("" would hide it), so it falls back
     *  to a bare label. */
    private fun draftTextOf(chat: JSONObject): String {
        if (!chat.has("draft_message") || chat.isNull("draft_message")) {
            return ""
        }
        val content = chat.optJSONObject("draft_message")
            ?.optJSONObject("content") ?: return "[消息]"
        return when (content.optString("@type")) {
            "draftMessageContentText" ->
                content.optJSONObject("text")?.optString("text")
                    ?.takeIf { it.isNotEmpty() } ?: "[消息]"
            "draftMessageContentVideoNote" -> "[视频消息]"
            "draftMessageContentVoiceNote" -> "[语音]"
            else -> "[消息]"
        }
    }

    /** The last message's media thumbnail file, if any
     *  (v0.9.11, A2; field shapes per td_api.tl): photo ->
     *  the smallest size's file, video / animation -> their
     *  thumbnail file, sticker -> its thumbnail or the
     *  sticker file itself. Resolved through AvatarStore
     *  exactly like avatars (note + download + path). */
    private fun previewThumbFile(message: JSONObject?): JSONObject? {
        val content = message?.optJSONObject("content") ?: return null
        return when (content.optString("@type")) {
            "messagePhoto" -> content.optJSONObject("photo")
                ?.optJSONArray("sizes")?.optJSONObject(0)
                ?.optJSONObject("photo")
            "messageVideo" -> content.optJSONObject("video")
                ?.optJSONObject("thumbnail")?.optJSONObject("file")
            "messageAnimation" -> content.optJSONObject("animation")
                ?.optJSONObject("thumbnail")?.optJSONObject("file")
            "messageSticker" -> {
                val sticker = content.optJSONObject("sticker")
                sticker?.optJSONObject("thumbnail")
                    ?.optJSONObject("file")
                    ?: sticker?.optJSONObject("sticker")
            }
            else -> null
        }
    }

    /** Parse chat.available_reactions: All (or a missing field)
     *  means unrestricted -> null; Some carries the explicit
     *  reactionType list, of which only plain emojis are usable
     *  in the picker. */
    private fun allowedReactionsOf(chat: JSONObject): List<String>? {
        val available = chat.optJSONObject("available_reactions") ?: return null
        return when (available.optString("@type")) {
            "chatAvailableReactionsSome" -> {
                val arr = available.optJSONArray("reactions") ?: JSONArray()
                val out = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val type = arr.optJSONObject(i) ?: continue
                    if (type.optString("@type") == "reactionTypeEmoji") {
                        val emoji = type.optString("emoji")
                        if (emoji.isNotEmpty()) out.add(emoji)
                    }
                }
                out
            }
            else -> null
        }
    }

    /** The chat's own small photo (chatPhotoInfo.small, verified in
     *  td_api.tl); private chats fall back to the other user's
     *  profile photo fetched via getUser. */
    private fun avatarFileId(chat: JSONObject): Int {
        val own = chat.optJSONObject("photo")
            ?.optJSONObject("small")?.optInt("id", 0) ?: 0
        if (own != 0) return own
        return userPhotoFileByChat[chat.optLong("id")] ?: 0
    }

    /** The chat's avatar path via the shared resolver (W0):
     *  the chat's own small photo first (the file object itself
     *  may already carry the completed path); private chats fall
     *  back to the other user's profile photo file. */
    private fun avatarPath(chat: JSONObject): String? {
        val own = chat.optJSONObject("photo")?.optJSONObject("small")
        val fromOwn = AvatarStore.pathForFileObj(own)
        if (fromOwn != null) return fromOwn
        val fallbackId = userPhotoFileByChat[chat.optLong("id")] ?: 0
        if (fallbackId != 0) {
            AvatarStore.ensureDownload(fallbackId)
            return AvatarStore.pathForFile(fallbackId)
        }
        return null
    }

    private fun fetchUserPhoto(chatId: Long, userId: Long) {
        if (userPhotoFileByChat.containsKey(chatId)) return
        val query = TdClient.request("getUser").apply { put("user_id", userId) }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) return@sendRaw
            AvatarStore.noteUser(response)
            val fetchedName = userNameOf(response)
            if (fetchedName.isNotEmpty()) {
                userNameById[response.optLong("id")] = fetchedName
            }
            // The same answer seeds presence for the header
            // subtitle (v0.9.18, Y6).
            val status = response.optJSONObject("status")
            onlineByChat[chatId] =
                status?.optString("@type") == "userStatusOnline"
            presenceByChat[chatId] =
                ContactsStore.statusOf(status).first
            val smallId = response.optJSONObject("profile_photo")
                ?.optJSONObject("small")?.optInt("id", 0) ?: 0
            if (smallId != 0) {
                userPhotoFileByChat[chatId] = smallId
                requestRecompute()
            }
        }
    }

    // ---- Chat-list long-press actions (all verified in td_api.tl) ----

    private fun reportIfError(response: JSONObject) {
        if (TdClient.isError(response)) {
            TdRepository.reportError(
                response.optInt("code") to response.optString("message")
            )
        }
    }

    /** The one pin action behind every 置顶 entry (long-press
     *  menu and swipe, main list and archive alike; v0.9.23,
     *  P1). Unpinning undoes whichever kind the chat has.
     *  Pinning goes to the server while the list's server
     *  allowance has room; once it is full the pin is kept
     *  locally instead (see LocalPins) — the server cap is
     *  never argued with. Server + local pins in one list
     *  are capped at MAX_TOTAL_PINS in total. */
    fun togglePin(chat: ChatItem) = togglePin(chat, archive = false)

    fun togglePin(chat: ChatItem, archive: Boolean) {
        if (chat.isPinned) {
            sendTogglePinned(chat.id, archive, pinned = false)
            return
        }
        if (LocalPins.contains(chat.id)) {
            LocalPins.remove(chat.id)
            requestRecompute()
            return
        }
        val total = serverPinCount(archive, secret = null) +
            localPinCountIn(archive)
        if (total >= MAX_TOTAL_PINS) {
            _pinNotice.value =
                "置顶最多 $MAX_TOTAL_PINS 个（服务器同步的和本地的加起来），" +
                    "先取消一个再置顶吧。"
            return
        }
        val cap = if (archive) archivePinCap else mainPinCap
        if (serverPinCount(archive, secret = chat.isSecret) < cap) {
            sendTogglePinned(chat.id, archive, pinned = true) {
                // The server refused although our count said
                // there was room (a stale cap, e.g. pins made
                // on another device a moment ago): land the
                // pin locally so the tap still does what the
                // user asked, and say so.
                LocalPins.add(chat.id)
                _pinNotice.value =
                    "服务器置顶满了，已改成本地置顶：" +
                        "只在这台手机上排前面。"
                requestRecompute()
            }
        } else {
            LocalPins.add(chat.id)
            _pinNotice.value =
                "服务器置顶满了，这个是本地置顶：" +
                    "只在这台手机上排前面，换设备不会跟过去。"
            requestRecompute()
        }
    }

    private fun sendTogglePinned(
        chatId: Long,
        archive: Boolean,
        pinned: Boolean,
        onLimitError: (() -> Unit)? = null
    ) {
        val query = TdClient.request("toggleChatIsPinned").apply {
            put(
                "chat_list",
                JSONObject().put(
                    "@type",
                    if (archive) "chatListArchive" else "chatListMain"
                )
            )
            put("chat_id", chatId)
            put("is_pinned", pinned)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                if (pinned && onLimitError != null &&
                    response.optInt("code") == 400
                ) {
                    onLimitError()
                } else {
                    reportIfError(response)
                }
            }
        }
    }

    /** Mute "forever" is a far-future mute_for; unmuting sets it to
     *  0. Every other setting stays on its default (use_default_*
     *  flags), per the chatNotificationSettings field list. */
    fun toggleMute(chat: ChatItem) {
        val settings = JSONObject().apply {
            put("@type", "chatNotificationSettings")
            put("use_default_mute_for", false)
            put("mute_for", if (chat.isMuted) 0 else Int.MAX_VALUE)
            put("use_default_sound", true)
            put("sound_id", 0L)
            put("use_default_show_preview", true)
            put("show_preview", false)
            put("use_default_mute_stories", true)
            put("mute_stories", false)
            put("use_default_story_sound", true)
            put("story_sound_id", 0L)
            put("use_default_show_story_poster", true)
            put("show_story_poster", false)
            put("use_default_disable_pinned_message_notifications", true)
            put("disable_pinned_message_notifications", false)
            put("use_default_disable_mention_notifications", true)
            put("disable_mention_notifications", false)
        }
        val query = TdClient.request("setChatNotificationSettings").apply {
            put("chat_id", chat.id)
            put("notification_settings", settings)
        }
        TdRepository.sendRaw(query) { reportIfError(it) }
    }

    /** Mark a chat read from the list: view its newest message with
     *  the chat-list source; unread counts then sync from TDLib. */
    fun markRead(chat: ChatItem) {
        if (chat.lastMessageId == 0L) return
        val query = TdClient.request("viewMessages").apply {
            put("chat_id", chat.id)
            put("message_ids", JSONArray().put(chat.lastMessageId))
            put("source", JSONObject().put("@type", "messageSourceChatList"))
            put("force_read", true)
        }
        TdRepository.sendRaw(query) { reportIfError(it) }
    }

    /** Mark a chat as unread from the list (v0.9.2, L3 swipe
     *  action): toggleChatIsMarkedAsUnread, verified in
     *  td_api.tl. The row then shows the dot badge through
     *  updateChatIsMarkedAsUnread, which ChatStore already
     *  patches. */
    fun markUnread(chat: ChatItem) {
        val query = TdClient.request("toggleChatIsMarkedAsUnread").apply {
            put("chat_id", chat.id)
            put("is_marked_as_unread", true)
        }
        TdRepository.sendRaw(query) { reportIfError(it) }
    }

    /** Archive: move the chat to the archive list (addChatToList
     *  with chatListArchive). TDLib then drops it from the main
     *  list positions and this store stops showing it. */
    fun archive(chat: ChatItem) {
        val query = TdClient.request("addChatToList").apply {
            put("chat_id", chat.id)
            put("chat_list", JSONObject().put("@type", "chatListArchive"))
        }
        TdRepository.sendRaw(query) { reportIfError(it) }
    }

    /** Display name of a user object (first + last name). */
    private fun userNameOf(user: JSONObject): String {
        val first = user.optString("first_name")
        val last = user.optString("last_name")
        return (first + " " + last).trim()
    }

    /** One-shot name lookup for a preview sender we have
     *  never seen a user object for (v0.9.18, Y4); the
     *  answer fills the name cache and republishes, so the
     *  prefix appears as soon as it lands. */
    private fun fetchUserName(userId: Long) {
        if (userNameById.containsKey(userId)) return
        if (!userNameRequested.add(userId)) return
        val query = TdClient.request("getUser").apply {
            put("user_id", userId)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) return@sendRaw
            AvatarStore.noteUser(response)
            val name = userNameOf(response)
            if (name.isNotEmpty()) {
                userNameById[userId] = name
                requestRecompute()
            }
        }
    }

    /** Record a member count against the chat whose type
     *  references the given group id (v0.9.18, Y6); counts
     *  of 0 mean "unknown" in the tl and are dropped, so a
     *  stale unknown never erases a known count. */
    private fun noteMemberCount(
        basicGroupId: Long,
        supergroupId: Long,
        count: Int
    ) {
        if (count <= 0) return
        for (chat in cache.values) {
            val type = chat.optJSONObject("type") ?: continue
            val matches = when (type.optString("@type")) {
                "chatTypeBasicGroup" ->
                    basicGroupId != 0L &&
                        type.optLong("basic_group_id") == basicGroupId
                "chatTypeSupergroup" ->
                    supergroupId != 0L &&
                        type.optLong("supergroup_id") == supergroupId
                else -> false
            }
            if (matches) {
                memberCountByChat[chat.optLong("id")] = count
                return
            }
        }
    }

    /** One-shot member-count lookup for a group/channel
     *  whose count is not known yet (v0.9.18, Y6): the chat
     *  object has no count field, so the group object is
     *  fetched once per session; updateBasicGroup /
     *  updateSupergroup keep it fresh afterwards. */
    private fun fetchMemberCount(chat: JSONObject) {
        val chatId = chat.optLong("id")
        if (memberCountByChat.containsKey(chatId)) return
        if (!memberCountRequested.add(chatId)) return
        val type = chat.optJSONObject("type") ?: return
        val query = when (type.optString("@type")) {
            "chatTypeBasicGroup" -> TdClient.request("getBasicGroup").apply {
                put("basic_group_id", type.optLong("basic_group_id"))
            }
            "chatTypeSupergroup" -> TdClient.request("getSupergroup").apply {
                put("supergroup_id", type.optLong("supergroup_id"))
            }
            else -> return
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) return@sendRaw
            val count = response.optInt("member_count", 0)
            if (count > 0) {
                memberCountByChat[chatId] = count
                requestRecompute()
            }
        }
    }

    /** The last message's sender as a (name, id) prefix for
     *  group rows (v0.9.18, Y4); ("", 0) when the row shows
     *  no prefix: private chats, channels, outgoing last
     *  messages, sender-less service messages, and senders
     *  whose name has not resolved yet (a getUser is kicked
     *  off in that case). Chat senders (anonymous admins
     *  posting as the chat) resolve through the cached
     *  chat title. */
    private fun previewSenderOf(
        chat: JSONObject,
        message: JSONObject?
    ): Pair<String, Long> {
        if (message == null || message.optBoolean("is_outgoing")) {
            return "" to 0L
        }
        val type = chat.optJSONObject("type")
        val typeName = type?.optString("@type")
        val isGroupChat = typeName == "chatTypeBasicGroup" ||
            (typeName == "chatTypeSupergroup" &&
                type?.optBoolean("is_channel") != true)
        if (!isGroupChat) return "" to 0L
        val sender = message.optJSONObject("sender_id")
            ?: return "" to 0L
        return when (sender.optString("@type")) {
            "messageSenderUser" -> {
                val userId = sender.optLong("user_id")
                if (userId == 0L) {
                    "" to 0L
                } else {
                    val name = userNameById[userId]
                    if (name.isNullOrEmpty()) {
                        fetchUserName(userId)
                        "" to 0L
                    } else {
                        name to userId
                    }
                }
            }
            "messageSenderChat" -> {
                val senderChatId = sender.optLong("chat_id")
                val name = if (senderChatId != 0L) {
                    titleOf(senderChatId)
                } else {
                    ""
                }
                if (name.isEmpty()) "" to 0L else name to senderChatId
            }
            else -> "" to 0L
        }
    }

    /** Chat-list preview line. Per-type labels since v0.9.1
     *  (audit K6 — previously every non-text/photo type rendered
     *  as the same "[消息]"); content type names and the fields
     *  read here are verified in td_api.tl (messageDocument
     *  .document.file_name, messageAudio.audio.title, message
     *  Sticker.sticker.emoji, messagePoll.poll.question). */
    private fun previewOf(message: JSONObject?): String {
        if (message == null) return ""
        val content = message.optJSONObject("content")
        fun caption(): String =
            content?.optJSONObject("caption")?.optString("text") ?: ""
        fun withCaption(label: String): String {
            val cap = caption()
            return if (cap.isEmpty()) label else "$label $cap"
        }
        val body = when (content?.optString("@type")) {
            "messageText" ->
                content.optJSONObject("text")?.optString("text") ?: ""
            "messagePhoto" -> withCaption("[图片]")
            "messageVideo" -> withCaption("[视频]")
            "messagePaidMedia" -> withCaption("[付费媒体]")
            "messageDocument" -> {
                val name = content.optJSONObject("document")
                    ?.optString("file_name") ?: ""
                when {
                    name.isNotEmpty() -> withCaption("[文件] $name")
                    else -> withCaption("[文件]")
                }
            }
            "messageVoiceNote" -> withCaption("[语音]")
            "messageAudio" -> {
                val title = content.optJSONObject("audio")
                    ?.optString("title") ?: ""
                if (title.isNotEmpty()) "[音频] $title"
                else withCaption("[音频]")
            }
            "messageAnimation" -> withCaption("[GIF]")
            "messageSticker" -> {
                val emoji = content.optJSONObject("sticker")
                    ?.optString("emoji") ?: ""
                if (emoji.isNotEmpty()) "[贴纸] $emoji" else "[贴纸]"
            }
            "messageAnimatedEmoji" ->
                // The emoji itself, like mature clients show
                // it in the list (v0.9.16, W4).
                content.optString("emoji").ifEmpty { "[动态表情]" }
            "messageVideoNote" -> "[视频消息]"
            "messagePoll" -> {
                val question = content.optJSONObject("poll")
                    ?.optJSONObject("question")?.optString("text") ?: ""
                if (question.isNotEmpty()) "[投票] $question" else "[投票]"
            }
            "messageLocation", "messageVenue" -> "[位置]"
            "messageContact" -> "[联系人]"
            "messageCall" -> "[通话]"
            "messageGame" -> "[游戏]"
            null -> ""
            else -> "[消息]"
        }
        if (body.isEmpty()) return ""
        return if (message.optBoolean("is_outgoing")) "你：$body" else body
    }
}
