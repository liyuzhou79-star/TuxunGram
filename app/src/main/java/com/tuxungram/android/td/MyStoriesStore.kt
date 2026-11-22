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

/** One viewer of one of my stories (Y2), from
 *  storyInteraction with type storyInteractionTypeView —
 *  the only interaction type that is a view. A non-empty
 *  [reactionEmoji] means the viewer also reacted (the
 *  view's chosen_reaction_type). */
data class StoryViewerUi(
    val userId: Long,
    val name: String,
    val photoPath: String?,
    val dateSeconds: Int,
    val reactionEmoji: String?
)

/**
 * "My stories" (v0.9.32, Y2): the stories I posted to my
 * profile page, their viewer lists, and deletion.
 *
 * Interfaces verified against this repo's td_api.tl —
 * note the names differ from the first guess: the list
 * call is getChatPostedToChatPageStories (chat_id,
 * from_story_id, limit -> Stories{total_count, stories,
 * pinned_story_ids}), viewers come from
 * getStoryInteractions (story_id only — it answers for
 * the account's OWN stories — with a string offset and
 * next_offset paging), and deletion is deleteStory
 * (story_poster_chat_id, story_id). Data the interface
 * does not carry is not invented: a story whose
 * can_get_interactions is false simply gets no viewer
 * entry in the UI.
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
                _exhausted.value = true
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

    // ---- Viewers of one story ----

    private val _viewers = MutableStateFlow<List<StoryViewerUi>>(
        emptyList()
    )
    val viewers: StateFlow<List<StoryViewerUi>> = _viewers

    private val _viewersLoading = MutableStateFlow(false)
    val viewersLoading: StateFlow<Boolean> = _viewersLoading

    private val _viewersError = MutableStateFlow<String?>(null)
    val viewersError: StateFlow<String?> = _viewersError

    private val _viewersExhausted = MutableStateFlow(true)
    val viewersExhausted: StateFlow<Boolean> = _viewersExhausted

    @Volatile
    private var viewersStoryId = 0

    private var viewersOffset = ""

    private val nameByUser = ConcurrentHashMap<Long, String>()
    private val namePending = ConcurrentHashMap.newKeySet<Long>()

    fun loadViewers(storyId: Int) {
        viewersStoryId = storyId
        viewersOffset = ""
        _viewers.value = emptyList()
        _viewersError.value = null
        _viewersExhausted.value = false
        fetchViewersPage()
    }

    fun loadMoreViewers() {
        if (_viewersLoading.value || _viewersExhausted.value) return
        if (viewersStoryId == 0) return
        fetchViewersPage()
    }

    private fun fetchViewersPage() {
        val forStory = viewersStoryId
        _viewersLoading.value = true
        val query = TdClient.request("getStoryInteractions").apply {
            put("story_id", forStory)
            put("query", "")
            put("only_contacts", false)
            put("prefer_forwards", false)
            put("prefer_with_reaction", false)
            put("offset", viewersOffset)
            put("limit", 50)
        }
        TdRepository.sendRaw(query) { response ->
            if (viewersStoryId != forStory) return@sendRaw
            _viewersLoading.value = false
            if (TdClient.isError(response)) {
                _viewersError.value =
                    "看过的人没查到，再试一次。（错误码 " +
                        "${response.optInt("code")}）"
                _viewersExhausted.value = true
                return@sendRaw
            }
            val arr = response.optJSONArray("interactions")
                ?: JSONArray()
            val page = ArrayList<StoryViewerUi>()
            for (i in 0 until arr.length()) {
                val interaction = arr.optJSONObject(i) ?: continue
                val type = interaction.optJSONObject("type")
                    ?: continue
                // Only views are viewers; forwards and
                // reposts are separate interaction kinds and
                // are not silently counted as views.
                if (type.optString("@type") !=
                    "storyInteractionTypeView"
                ) {
                    continue
                }
                val actor = interaction.optJSONObject("actor_id")
                    ?: continue
                if (actor.optString("@type") != "messageSenderUser") {
                    continue
                }
                val userId = actor.optLong("user_id")
                if (userId == 0L) continue
                val reaction = type.optJSONObject(
                    "chosen_reaction_type"
                )
                val emoji = when (reaction?.optString("@type")) {
                    "reactionTypeEmoji" -> reaction.optString("emoji")
                    else -> null
                }
                page.add(
                    StoryViewerUi(
                        userId = userId,
                        name = nameByUser[userId] ?: "",
                        photoPath = AvatarStore.pathForUser(userId),
                        dateSeconds = interaction.optInt(
                            "interaction_date"
                        ),
                        reactionEmoji = emoji?.ifEmpty { null }
                    )
                )
            }
            _viewers.value = _viewers.value + page
            val next = response.optString("next_offset")
            if (next.isEmpty() || arr.length() == 0) {
                _viewersExhausted.value = true
            } else {
                viewersOffset = next
            }
            for (viewer in page) {
                if (viewer.name.isEmpty()) resolveName(viewer.userId)
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
            _viewers.value = _viewers.value.map { viewer ->
                if (viewer.userId == userId) {
                    viewer.copy(
                        name = nameByUser[userId] ?: viewer.name,
                        photoPath = path ?: viewer.photoPath
                    )
                } else {
                    viewer
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
