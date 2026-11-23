package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One of my own stories posted to my profile page
 *  (v0.9.32, Y2), flattened from the story object of
 *  getChatPostedToChatPageStories. [previewPath] is the
 *  grid image: the photo itself for photo stories, the
 *  video's thumbnail for video stories. Counts come from
 *  the story's interaction_info — the server's numbers,
 *  never estimated. */
data class MyStoryUi(
    val storyId: Int,
    val dateSeconds: Int,
    val isVideo: Boolean,
    val previewFileId: Int,
    val previewPath: String? = null,
    val viewCount: Int = 0,
    val reactionCount: Int = 0,
    val canDelete: Boolean = false,
    val canGetInteractions: Boolean = false,
    /** story.has_expired_viewers (v0.9.38, E3): some
     *  viewers' records have aged out server-side, so an
     *  empty detail list under a positive view count is
     *  explained instead of looking like a bug. */
    val hasExpiredViewers: Boolean = false,
    val isPinned: Boolean = false,
    // Story albums (v0.9.35, B2): the albums this story
    // already belongs to (story.album_ids, manageable
    // stories only) and whether the server allows adding
    // it to an album at all (can_be_added_to_album).
    val albumIds: List<Int> = emptyList(),
    val canBeAddedToAlbum: Boolean = false
)

/** One story album of mine (storyAlbum, v0.9.35 B2).
 *  [iconPath] is the album's cover still — its photo
 *  icon, or its video icon's thumbnail — once downloaded. */
data class StoryAlbumUi(
    val id: Int,
    val name: String,
    val iconFileId: Int = 0,
    val iconPath: String? = null
)

/** Interaction kinds of StoryInteractionUi (v0.9.38, E3),
 *  one per StoryInteractionType variant in the tl. */
const val STORY_KIND_VIEW = 0
const val STORY_KIND_FORWARD = 1
const val STORY_KIND_REPOST = 2

/** One interaction with one of my stories (Y2 as views
 *  only; v0.9.38, E3, completed to the tl's full
 *  StoryInteractionType set): a view
 *  (storyInteractionTypeView — its chosen_reaction_type,
 *  when present, is the reaction the viewer left), a
 *  forward as a message (storyInteractionTypeForward),
 *  or a repost as a story (storyInteractionTypeRepost).
 *  The actor is a user ([userId] set) or a chat
 *  ([actorChatId] set). A custom-emoji reaction has no
 *  plain emoji to show — [customReaction] flags it
 *  instead of inventing a glyph. */
data class StoryInteractionUi(
    val userId: Long,
    val actorChatId: Long = 0L,
    val kind: Int = STORY_KIND_VIEW,
    val name: String,
    val photoPath: String?,
    val dateSeconds: Int,
    val reactionEmoji: String?,
    val customReaction: Boolean = false
)

/** The server's own totals for one story's interaction
 *  list (v0.9.38, E3): storyInteractions.total_count /
 *  total_forward_count / total_reaction_count from the
 *  first page. These are the numbers of record — the
 *  detail rows below them are only what the API hands
 *  out, and can be fewer (expired viewers, privacy). */
data class StoryInteractionTotals(
    val totalCount: Int,
    val forwardCount: Int,
    val reactionCount: Int
)

/**
 * "My stories" (v0.9.32, Y2): the stories I posted to my
 * profile page, their viewer lists, and deletion.
 *
 * Interfaces verified against this repo's td_api.tl —
 * note the names differ from the first guess: the list
 * call is getChatPostedToChatPageStories (chat_id,
 * from_story_id, limit -> Stories{total_count, stories,
 * pinned_story_ids}), interactions come from
 * getStoryInteractions (story_id only — it answers for
 * the account's OWN stories — with a string offset and
 * next_offset paging; v0.9.38 E3 lists all three
 * interaction types and keeps the response's server
 * totals alongside), and deletion is deleteStory
 * (story_poster_chat_id, story_id). Data the interface
 * does not carry is not invented: a story whose
 * can_get_interactions is false simply gets no
 * interaction entry in the UI.
 */
object MyStoriesStore {
    private val _stories = MutableStateFlow<List<MyStoryUi>>(emptyList())
    val stories: StateFlow<List<MyStoryUi>> = _stories

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _exhausted = MutableStateFlow(true)
    val exhausted: StateFlow<Boolean> = _exhausted

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    private var myId = 0L
    private var fromStoryId = 0
    private var totalCount = 0
    private val pinnedIds = LinkedHashSet<Int>()

    /** preview fileId -> storyId for downloads started here. */
    private val pendingDownloads = ConcurrentHashMap<Int, Int>()

    /** album icon fileId -> albumId (v0.9.35, B2). */
    private val albumIconDownloads = ConcurrentHashMap<Int, Int>()

    @Volatile
    private var attached = false

    private fun attach() {
        if (attached) return
        attached = true
        TdRepository.addUpdateListener { update ->
            if (update.optString("@type") != "updateFile") {
                return@addUpdateListener
            }
            val file = update.optJSONObject("file") ?: return@addUpdateListener
            val fileId = file.optInt("id")
            val local = file.optJSONObject("local")
                ?: return@addUpdateListener
            if (!local.optBoolean("is_downloading_completed")) {
                return@addUpdateListener
            }
            val path = local.optString("path")
            if (path.isEmpty()) return@addUpdateListener
            val albumId = albumIconDownloads.remove(fileId)
            if (albumId != null) {
                _albums.value = _albums.value.map { album ->
                    if (album.id == albumId) {
                        album.copy(iconPath = path)
                    } else {
                        album
                    }
                }
            }
            val storyId = pendingDownloads[fileId]
                ?: return@addUpdateListener
            pendingDownloads.remove(fileId)
            _stories.value = _stories.value.map { story ->
                if (story.storyId == storyId) {
                    story.copy(previewPath = path)
                } else {
                    story
                }
            }
            _albumStories.value = _albumStories.value.map { story ->
                if (story.storyId == storyId) {
                    story.copy(previewPath = path)
                } else {
                    story
                }
            }
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun load() {
        attach()
        _stories.value = emptyList()
        fromStoryId = 0
        totalCount = 0
        pinnedIds.clear()
        _exhausted.value = false
        val known = MeStore.me.value.id
        if (known != 0L) {
            myId = known
            fetchPage()
            loadAlbums()
        } else {
            TdRepository.sendRaw(TdClient.request("getMe")) { me ->
                if (TdClient.isError(me)) {
                    _notice.value = "账号状态还没就绪，稍后再试。"
                    _exhausted.value = true
                    return@sendRaw
                }
                myId = me.optLong("id")
                fetchPage()
                loadAlbums()
            }
        }
    }

    fun loadMore() {
        if (_loading.value || _exhausted.value) return
        fetchPage()
    }

    private fun fetchPage() {
        if (myId == 0L) return
        _loading.value = true
        val query = TdClient.request(
            "getChatPostedToChatPageStories"
        ).apply {
            put("chat_id", myId)
            put("from_story_id", fromStoryId)
            put("limit", 100)
        }
        TdRepository.sendRaw(query) { response ->
            _loading.value = false
            if (TdClient.isError(response)) {
                // An error is not the end of the list
                // (v0.9.41, H1①): exhausted stayed false so
                // the "try again" in the notice is real —
                // scrolling (loadMore) retries this page.
                _notice.value =
                    "动态列表没拉到，再试一次。（错误码 " +
                        "${response.optInt("code")}）"
                return@sendRaw
            }
            totalCount = response.optInt("total_count")
            val pinned = response.optJSONArray("pinned_story_ids")
            if (pinned != null) {
                for (i in 0 until pinned.length()) {
                    pinnedIds.add(pinned.optInt(i))
                }
            }
            val arr = response.optJSONArray("stories")
            val page = ArrayList<MyStoryUi>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val story = arr.optJSONObject(i) ?: continue
                    page.add(parseStory(story))
                }
            }
            if (page.isNotEmpty()) {
                fromStoryId = page.last().storyId
            }
            val merged = _stories.value + page
            _stories.value = merged
            if (page.isEmpty() ||
                (totalCount > 0 && merged.size >= totalCount)
            ) {
                _exhausted.value = true
            }
            for (story in page) {
                if (story.previewPath == null &&
                    story.previewFileId != 0
                ) {
                    pendingDownloads[story.previewFileId] =
                        story.storyId
                    val dl = TdClient.request("downloadFile").apply {
                        put("file_id", story.previewFileId)
                        put("priority", 8)
                        put("offset", 0)
                        put("limit", 0)
                        put("synchronous", false)
                    }
                    TdRepository.sendRaw(dl) { }
                }
            }
        }
    }

    private fun parseStory(story: JSONObject): MyStoryUi {
        val content = story.optJSONObject("content")
        var isVideo = false
        var previewFileId = 0
        var previewPath: String? = null
        when (content?.optString("@type")) {
            "storyContentPhoto" -> {
                val sizes = content.optJSONObject("photo")
                    ?.optJSONArray("sizes")
                if (sizes != null && sizes.length() > 0) {
                    val file = sizes.optJSONObject(sizes.length() - 1)
                        ?.optJSONObject("photo")
                    previewFileId = file?.optInt("id") ?: 0
                    previewPath = downloadedPath(file)
                }
            }
            "storyContentVideo" -> {
                isVideo = true
                val thumb = content.optJSONObject("video")
                    ?.optJSONObject("thumbnail")
                    ?.optJSONObject("file")
                previewFileId = thumb?.optInt("id") ?: 0
                previewPath = downloadedPath(thumb)
            }
        }
        val interaction = story.optJSONObject("interaction_info")
        val storyId = story.optInt("id")
        val albumArr = story.optJSONArray("album_ids")
        val albumIds = ArrayList<Int>()
        if (albumArr != null) {
            for (i in 0 until albumArr.length()) {
                albumIds.add(albumArr.optInt(i))
            }
        }
        return MyStoryUi(
            storyId = storyId,
            dateSeconds = story.optInt("date"),
            isVideo = isVideo,
            previewFileId = previewFileId,
            previewPath = previewPath,
            viewCount = interaction?.optInt("view_count") ?: 0,
            reactionCount = interaction?.optInt("reaction_count") ?: 0,
            canDelete = story.optBoolean("can_be_deleted"),
            canGetInteractions =
                story.optBoolean("can_get_interactions"),
            hasExpiredViewers =
                story.optBoolean("has_expired_viewers"),
            isPinned = storyId in pinnedIds,
            albumIds = albumIds,
            canBeAddedToAlbum =
                story.optBoolean("can_be_added_to_album")
        )
    }

    private fun downloadedPath(file: JSONObject?): String? {
        val local = file?.optJSONObject("local") ?: return null
        if (!local.optBoolean("is_downloading_completed")) return null
        return local.optString("path").ifEmpty { null }
    }

    /** Delete one of my stories everywhere (deleteStory,
     *  verified in td_api.tl). The row leaves the list only
     *  after the server accepts. */
    fun delete(storyId: Int) {
        if (myId == 0L) return
        val query = TdClient.request("deleteStory").apply {
            put("story_poster_chat_id", myId)
            put("story_id", storyId)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _notice.value =
                    "删除失败，再试一次。（错误码 " +
                        "${response.optInt("code")}）"
                return@sendRaw
            }
            _stories.value = _stories.value.filter {
                it.storyId != storyId
            }
            pinnedIds.remove(storyId)
            _notice.value = "这条动态已删除。"
        }
    }

    // ---- Interactions of one story ----
    // getStoryInteractions (verified in td_api.tl): the
    // response carries the server totals (total_count /
    // total_forward_count / total_reaction_count) plus
    // detail rows whose type is one of the three
    // StoryInteractionType variants. Until v0.9.38 only
    // views were listed and forwards/reposts were dropped
    // silently; now every row the API hands out is shown
    // as what it is, and views vs reactions vs forwards
    // are counted separately — views and reactions are
    // NOT one number: a reaction rides on a view row (its
    // chosen_reaction_type), while the reaction total is
    // the server's own total_reaction_count.

    private val _interactions =
        MutableStateFlow<List<StoryInteractionUi>>(emptyList())
    val interactions: StateFlow<List<StoryInteractionUi>> =
        _interactions

    private val _interactionsLoading = MutableStateFlow(false)
    val interactionsLoading: StateFlow<Boolean> =
        _interactionsLoading

    private val _interactionsError = MutableStateFlow<String?>(null)
    val interactionsError: StateFlow<String?> = _interactionsError

    private val _interactionsExhausted = MutableStateFlow(true)
    val interactionsExhausted: StateFlow<Boolean> =
        _interactionsExhausted

    private val _interactionTotals =
        MutableStateFlow<StoryInteractionTotals?>(null)
    val interactionTotals: StateFlow<StoryInteractionTotals?> =
        _interactionTotals

    @Volatile
    private var interactionsStoryId = 0

    private var interactionsOffset = ""

    private val nameByUser = ConcurrentHashMap<Long, String>()
    private val namePending = ConcurrentHashMap.newKeySet<Long>()
    private val nameByChat = ConcurrentHashMap<Long, String>()
    private val chatNamePending = ConcurrentHashMap.newKeySet<Long>()

    fun loadInteractions(storyId: Int) {
        interactionsStoryId = storyId
        interactionsOffset = ""
        _interactions.value = emptyList()
        _interactionsError.value = null
        _interactionsExhausted.value = false
        _interactionTotals.value = null
        fetchInteractionsPage()
    }

    fun loadMoreInteractions() {
        if (_interactionsLoading.value ||
            _interactionsExhausted.value
        ) {
            return
        }
        if (interactionsStoryId == 0) return
        fetchInteractionsPage()
    }

    private fun fetchInteractionsPage() {
        val forStory = interactionsStoryId
        val firstPage = interactionsOffset.isEmpty() &&
            _interactions.value.isEmpty()
        _interactionsLoading.value = true
        val query = TdClient.request("getStoryInteractions").apply {
            put("story_id", forStory)
            put("query", "")
            put("only_contacts", false)
            put("prefer_forwards", false)
            put("prefer_with_reaction", false)
            put("offset", interactionsOffset)
            put("limit", 50)
        }
        TdRepository.sendRaw(query) { response ->
            if (interactionsStoryId != forStory) return@sendRaw
            _interactionsLoading.value = false
            if (TdClient.isError(response)) {
                _interactionsError.value =
                    "互动记录没查到，再试一次。（错误码 " +
                        "${response.optInt("code")}）"
                _interactionsExhausted.value = true
                return@sendRaw
            }
            if (firstPage) {
                _interactionTotals.value = StoryInteractionTotals(
                    totalCount = response.optInt("total_count"),
                    forwardCount =
                        response.optInt("total_forward_count"),
                    reactionCount =
                        response.optInt("total_reaction_count")
                )
            }
            val arr = response.optJSONArray("interactions")
                ?: JSONArray()
            val page = ArrayList<StoryInteractionUi>()
            for (i in 0 until arr.length()) {
                val interaction = arr.optJSONObject(i) ?: continue
                val type = interaction.optJSONObject("type")
                    ?: continue
                val kind = when (type.optString("@type")) {
                    "storyInteractionTypeView" -> STORY_KIND_VIEW
                    "storyInteractionTypeForward" ->
                        STORY_KIND_FORWARD
                    "storyInteractionTypeRepost" -> STORY_KIND_REPOST
                    else -> continue
                }
                val actor = interaction.optJSONObject("actor_id")
                    ?: continue
                var reactionEmoji: String? = null
                var customReaction = false
                if (kind == STORY_KIND_VIEW) {
                    val reaction = type.optJSONObject(
                        "chosen_reaction_type"
                    )
                    when (reaction?.optString("@type")) {
                        "reactionTypeEmoji" ->
                            reactionEmoji = reaction.optString("emoji")
                                .ifEmpty { null }
                        "reactionTypeCustomEmoji" ->
                            customReaction = true
                        "reactionTypePaid" -> customReaction = true
                    }
                }
                val date = interaction.optInt("interaction_date")
                when (actor.optString("@type")) {
                    "messageSenderUser" -> {
                        val userId = actor.optLong("user_id")
                        if (userId == 0L) continue
                        page.add(
                            StoryInteractionUi(
                                userId = userId,
                                kind = kind,
                                name = nameByUser[userId] ?: "",
                                photoPath =
                                    AvatarStore.pathForUser(userId),
                                dateSeconds = date,
                                reactionEmoji = reactionEmoji,
                                customReaction = customReaction
                            )
                        )
                    }
                    "messageSenderChat" -> {
                        val chatId = actor.optLong("chat_id")
                        if (chatId == 0L) continue
                        page.add(
                            StoryInteractionUi(
                                userId = 0L,
                                actorChatId = chatId,
                                kind = kind,
                                name = nameByChat[chatId] ?: "",
                                photoPath = null,
                                dateSeconds = date,
                                reactionEmoji = reactionEmoji,
                                customReaction = customReaction
                            )
                        )
                    }
                    else -> continue
                }
            }
            _interactions.value = _interactions.value + page
            val next = response.optString("next_offset")
            if (next.isEmpty() || arr.length() == 0) {
                _interactionsExhausted.value = true
            } else {
                interactionsOffset = next
            }
            for (row in page) {
                if (row.name.isNotEmpty()) continue
                if (row.userId != 0L) {
                    resolveName(row.userId)
                } else if (row.actorChatId != 0L) {
                    resolveChatName(row.actorChatId)
                }
            }
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
            val path = AvatarStore.pathForUser(userId)
            _interactions.value = _interactions.value.map { row ->
                if (row.userId == userId) {
                    row.copy(
                        name = nameByUser[userId] ?: row.name,
                        photoPath = path ?: row.photoPath
                    )
                } else {
                    row
                }
            }
        }
    }

    private fun resolveChatName(chatId: Long) {
        if (!chatNamePending.add(chatId)) return
        val query = TdClient.request("getChat").apply {
            put("chat_id", chatId)
        }
        TdRepository.sendRaw(query) { chat ->
            chatNamePending.remove(chatId)
            if (TdClient.isError(chat)) return@sendRaw
            val title = chat.optString("title")
            if (title.isNotEmpty()) nameByChat[chatId] = title
            val path = AvatarStore.pathForFileObj(
                chat.optJSONObject("photo")?.optJSONObject("small")
            )
            _interactions.value = _interactions.value.map { row ->
                if (row.actorChatId == chatId) {
                    row.copy(
                        name = nameByChat[chatId] ?: row.name,
                        photoPath = path ?: row.photoPath
                    )
                } else {
                    row
                }
            }
        }
    }

    // ---- Story albums (v0.9.35, B2) ----
    // Interfaces verified in this repo's td_api.tl:
    // getChatStoryAlbums, getStoryAlbumStories (int offset
    // paging), createStoryAlbum (name 1-12 chars, story_ids
    // may be empty), setStoryAlbumName, deleteStoryAlbum,
    // addStoryAlbumStories / removeStoryAlbumStories. The
    // albums belong to my own poster chat (myId), which is
    // the only album owner this page manages.

    private val _albums = MutableStateFlow<List<StoryAlbumUi>>(
        emptyList()
    )
    val albums: StateFlow<List<StoryAlbumUi>> = _albums

    private val _albumStories =
        MutableStateFlow<List<MyStoryUi>>(emptyList())
    val albumStories: StateFlow<List<MyStoryUi>> = _albumStories

    private val _albumStoriesLoading = MutableStateFlow(false)
    val albumStoriesLoading: StateFlow<Boolean> =
        _albumStoriesLoading

    private val _albumStoriesExhausted = MutableStateFlow(true)
    val albumStoriesExhausted: StateFlow<Boolean> =
        _albumStoriesExhausted

    @Volatile
    private var albumStoriesAlbumId = 0

    private var albumStoriesOffset = 0

    fun loadAlbums() {
        if (myId == 0L) return
        val query = TdClient.request("getChatStoryAlbums").apply {
            put("chat_id", myId)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                // Albums are an extra section of the page;
                // a failure just leaves it empty, the story
                // grid below is unaffected.
                _albums.value = emptyList()
                return@sendRaw
            }
            val arr = response.optJSONArray("albums")
                ?: JSONArray()
            val parsed = ArrayList<StoryAlbumUi>()
            for (i in 0 until arr.length()) {
                val album = arr.optJSONObject(i) ?: continue
                parsed.add(parseAlbum(album))
            }
            _albums.value = parsed
            for (album in parsed) {
                if (album.iconPath == null &&
                    album.iconFileId != 0
                ) {
                    albumIconDownloads[album.iconFileId] = album.id
                    val dl = TdClient.request("downloadFile").apply {
                        put("file_id", album.iconFileId)
                        put("priority", 8)
                        put("offset", 0)
                        put("limit", 0)
                        put("synchronous", false)
                    }
                    TdRepository.sendRaw(dl) { }
                }
            }
        }
    }

    private fun parseAlbum(album: JSONObject): StoryAlbumUi {
        var iconFile: JSONObject? = null
        val photoIcon = album.optJSONObject("photo_icon")
        if (photoIcon != null) {
            val sizes = photoIcon.optJSONArray("sizes")
            if (sizes != null && sizes.length() > 0) {
                iconFile = sizes.optJSONObject(sizes.length() - 1)
                    ?.optJSONObject("photo")
            }
        }
        if (iconFile == null) {
            iconFile = album.optJSONObject("video_icon")
                ?.optJSONObject("thumbnail")
                ?.optJSONObject("file")
        }
        return StoryAlbumUi(
            id = album.optInt("id"),
            name = album.optString("name"),
            iconFileId = iconFile?.optInt("id") ?: 0,
            iconPath = downloadedPath(iconFile)
        )
    }

    /** Create an album (name 1-12 characters per the tl);
     *  [storyIds] may be empty — stories can be added
     *  afterwards from the album page. */
    fun createAlbum(name: String, storyIds: List<Int>) {
        val trimmed = name.trim()
        if (myId == 0L || trimmed.isEmpty()) return
        val ids = JSONArray()
        for (id in storyIds) ids.put(id)
        val query = TdClient.request("createStoryAlbum").apply {
            put("story_poster_chat_id", myId)
            put("name", trimmed)
            put("story_ids", ids)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _notice.value =
                    "相册没建好：名字要 1–12 个字，或者服务器" +
                        "拒绝了。（错误码 ${response.optInt("code")}）"
                return@sendRaw
            }
            val album = parseAlbum(response)
            _albums.value = _albums.value + album
            patchAlbumMembership(storyIds, album.id, add = true)
            _notice.value = "相册「${album.name}」建好了。"
        }
    }

    fun renameAlbum(album: StoryAlbumUi, name: String) {
        val trimmed = name.trim()
        if (myId == 0L || trimmed.isEmpty()) return
        val query = TdClient.request("setStoryAlbumName").apply {
            put("chat_id", myId)
            put("story_album_id", album.id)
            put("name", trimmed)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _notice.value =
                    "改名没成功：名字要 1–12 个字，或者服务器" +
                        "拒绝了。（错误码 ${response.optInt("code")}）"
                return@sendRaw
            }
            val updated = parseAlbum(response)
            _albums.value = _albums.value.map {
                if (it.id == album.id) {
                    updated.copy(iconPath = it.iconPath)
                } else {
                    it
                }
            }
        }
    }

    fun deleteAlbum(album: StoryAlbumUi) {
        if (myId == 0L) return
        val query = TdClient.request("deleteStoryAlbum").apply {
            put("chat_id", myId)
            put("story_album_id", album.id)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _notice.value =
                    "相册没删掉，再试一次。（错误码 " +
                        "${response.optInt("code")}）"
                return@sendRaw
            }
            val memberIds = _albumStories.value
                .filter { album.id in it.albumIds }
                .map { it.storyId }
            _albums.value = _albums.value.filter { it.id != album.id }
            if (albumStoriesAlbumId == album.id) {
                albumStoriesAlbumId = 0
                _albumStories.value = emptyList()
            }
            patchAlbumMembership(memberIds, album.id, add = false)
            _notice.value = "相册「${album.name}」已删除，里面的动态还在。"
        }
    }

    /** Reorder the album list (reorderStoryAlbums,
     *  verified in td_api.tl: the full ordered id list).
     *  The local list moves first; a server refusal puts
     *  it back by reloading. */
    fun reorderAlbums(orderedIds: List<Int>) {
        if (myId == 0L) return
        val byId = _albums.value.associateBy { it.id }
        val reordered = orderedIds.mapNotNull { byId[it] }
        if (reordered.size != _albums.value.size) return
        _albums.value = reordered
        val ids = JSONArray()
        for (id in orderedIds) ids.put(id)
        val query = TdClient.request("reorderStoryAlbums").apply {
            put("chat_id", myId)
            put("story_album_ids", ids)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _notice.value =
                    "相册顺序没保存上，已经恢复成原来的顺序。" +
                        "（错误码 ${response.optInt("code")}）"
                loadAlbums()
            }
        }
    }

    /** Load EVERY story of the open album before the
     *  reorder mode starts: reorderStoryAlbumStories
     *  takes the complete ordered id list, so reordering
     *  a half-loaded prefix would silently scramble the
     *  tail. Pages are fetched into the published list
     *  directly; the cap (50 pages × 100) keeps a runaway
     *  album from looping forever — hitting it reports
     *  failure instead of entering reorder mode. */
    fun loadAllAlbumStories(albumId: Int, onDone: (Boolean) -> Unit) {
        if (albumStoriesAlbumId != albumId) {
            openAlbum(albumId)
        }
        val mainHandler = android.os.Handler(
            android.os.Looper.getMainLooper()
        )
        var pages = 0
        fun finish(ok: Boolean) {
            mainHandler.post { onDone(ok) }
        }
        // fetchAlbumStoriesPage has no completion callback,
        // so each step waits out the loading flag on a
        // scratch thread: wait idle -> pull one page ->
        // wait idle -> repeat until the store says the
        // album is exhausted.
        fun step() {
            Thread {
                fun waitIdle(): Boolean {
                    var waited = 0
                    while (_albumStoriesLoading.value) {
                        if (++waited > 150) return false
                        try {
                            Thread.sleep(100)
                        } catch (t: InterruptedException) {
                            return false
                        }
                    }
                    return true
                }
                if (!waitIdle()) {
                    finish(false)
                    return@Thread
                }
                if (_albumStoriesExhausted.value) {
                    finish(true)
                    return@Thread
                }
                if (++pages > 50) {
                    finish(false)
                    return@Thread
                }
                val before = _albumStories.value.size
                loadMoreAlbumStories()
                if (!waitIdle()) {
                    finish(false)
                    return@Thread
                }
                if (!_albumStoriesExhausted.value &&
                    _albumStories.value.size == before
                ) {
                    finish(false)
                    return@Thread
                }
                step()
            }.start()
        }
        step()
    }

    /** Reorder stories inside one album
     *  (reorderStoryAlbumStories, verified in td_api.tl:
     *  the complete ordered story id list). The local
     *  list moves first; a server refusal reloads the
     *  album from scratch. */
    fun reorderAlbumStories(albumId: Int, orderedIds: List<Int>) {
        if (myId == 0L) return
        if (albumStoriesAlbumId == albumId) {
            val byId = _albumStories.value.associateBy { it.storyId }
            val reordered = orderedIds.mapNotNull { byId[it] }
            if (reordered.size == _albumStories.value.size) {
                _albumStories.value = reordered
            }
        }
        val ids = JSONArray()
        for (id in orderedIds) ids.put(id)
        val query =
            TdClient.request("reorderStoryAlbumStories").apply {
                put("chat_id", myId)
                put("story_album_id", albumId)
                put("story_ids", ids)
            }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _notice.value =
                    "相册内顺序没保存上，已经恢复成原来的顺序。" +
                        "（错误码 ${response.optInt("code")}）"
                if (albumStoriesAlbumId == albumId) {
                    openAlbum(albumId)
                }
            }
        }
    }

    fun openAlbum(albumId: Int) {
        albumStoriesAlbumId = albumId
        albumStoriesOffset = 0
        _albumStories.value = emptyList()
        _albumStoriesExhausted.value = false
        fetchAlbumStoriesPage()
    }

    fun loadMoreAlbumStories() {
        if (_albumStoriesLoading.value ||
            _albumStoriesExhausted.value
        ) {
            return
        }
        fetchAlbumStoriesPage()
    }

    private fun fetchAlbumStoriesPage() {
        val forAlbum = albumStoriesAlbumId
        if (myId == 0L || forAlbum == 0) return
        _albumStoriesLoading.value = true
        val query = TdClient.request("getStoryAlbumStories").apply {
            put("chat_id", myId)
            put("story_album_id", forAlbum)
            put("offset", albumStoriesOffset)
            put("limit", 100)
        }
        TdRepository.sendRaw(query) { response ->
            if (albumStoriesAlbumId != forAlbum) {
                return@sendRaw
            }
            _albumStoriesLoading.value = false
            if (TdClient.isError(response)) {
                _albumStoriesExhausted.value = true
                _notice.value =
                    "相册里的动态没拉到，再试一次。（错误码 " +
                        "${response.optInt("code")}）"
                return@sendRaw
            }
            val arr = response.optJSONArray("stories")
            val page = ArrayList<MyStoryUi>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val story = arr.optJSONObject(i) ?: continue
                    page.add(parseStory(story))
                }
            }
            albumStoriesOffset += page.size
            _albumStories.value = _albumStories.value + page
            if (page.isEmpty()) {
                _albumStoriesExhausted.value = true
            }
            for (story in page) {
                if (story.previewPath == null &&
                    story.previewFileId != 0
                ) {
                    pendingDownloads[story.previewFileId] =
                        story.storyId
                    val dl = TdClient.request("downloadFile").apply {
                        put("file_id", story.previewFileId)
                        put("priority", 8)
                        put("offset", 0)
                        put("limit", 0)
                        put("synchronous", false)
                    }
                    TdRepository.sendRaw(dl) { }
                }
            }
        }
    }

    fun addToAlbum(albumId: Int, storyIds: List<Int>) {
        if (myId == 0L || storyIds.isEmpty()) return
        val ids = JSONArray()
        for (id in storyIds) ids.put(id)
        val query = TdClient.request("addStoryAlbumStories").apply {
            put("chat_id", myId)
            put("story_album_id", albumId)
            put("story_ids", ids)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _notice.value =
                    "没加进去：可能超过相册容量上限，或者服务器" +
                        "拒绝了。（错误码 ${response.optInt("code")}）"
                return@sendRaw
            }
            patchAlbumMembership(storyIds, albumId, add = true)
            if (albumStoriesAlbumId == albumId) {
                openAlbum(albumId)
            }
            _notice.value = "已加入相册。"
        }
    }

    fun removeFromAlbum(albumId: Int, storyIds: List<Int>) {
        if (myId == 0L || storyIds.isEmpty()) return
        val ids = JSONArray()
        for (id in storyIds) ids.put(id)
        val query = TdClient.request(
            "removeStoryAlbumStories"
        ).apply {
            put("chat_id", myId)
            put("story_album_id", albumId)
            put("story_ids", ids)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _notice.value =
                    "没移出来，再试一次。（错误码 " +
                        "${response.optInt("code")}）"
                return@sendRaw
            }
            patchAlbumMembership(storyIds, albumId, add = false)
            if (albumStoriesAlbumId == albumId) {
                _albumStories.value = _albumStories.value.filter {
                    it.storyId !in storyIds
                }
            }
            _notice.value = "已移出相册，动态本身还在。"
        }
    }

    /** Keep the loaded story lists' album membership in
     *  step after an album change the server accepted. */
    private fun patchAlbumMembership(
        storyIds: List<Int>,
        albumId: Int,
        add: Boolean
    ) {
        if (storyIds.isEmpty()) return
        fun patch(story: MyStoryUi): MyStoryUi {
            if (story.storyId !in storyIds) return story
            val ids = if (add) {
                if (albumId in story.albumIds) {
                    story.albumIds
                } else {
                    story.albumIds + albumId
                }
            } else {
                story.albumIds - albumId
            }
            return story.copy(albumIds = ids)
        }
        _stories.value = _stories.value.map { patch(it) }
        _albumStories.value = _albumStories.value.map { patch(it) }
    }
}
