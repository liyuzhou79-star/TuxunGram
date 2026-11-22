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
    val isPinned: Boolean = false
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
            val storyId = pendingDownloads[fileId]
                ?: return@addUpdateListener
            val local = file.optJSONObject("local")
                ?: return@addUpdateListener
            if (!local.optBoolean("is_downloading_completed")) {
                return@addUpdateListener
            }
            val path = local.optString("path")
            if (path.isEmpty()) return@addUpdateListener
            pendingDownloads.remove(fileId)
            _stories.value = _stories.value.map { story ->
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
        } else {
            TdRepository.sendRaw(TdClient.request("getMe")) { me ->
                if (TdClient.isError(me)) {
                    _notice.value = "账号状态还没就绪，稍后再试。"
                    _exhausted.value = true
                    return@sendRaw
                }
                myId = me.optLong("id")
                fetchPage()
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
            isPinned = storyId in pinnedIds
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
}
