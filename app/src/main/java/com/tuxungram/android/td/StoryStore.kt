package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One chat's entry in the stories row on the home screen. */
data class StoryRing(
    val chatId: Long,
    val storyIds: List<Int>,
    val maxReadId: Int,
    val order: Long
) {
    val hasUnread: Boolean get() = storyIds.any { it > maxReadId }
}

/** One story being viewed: content kind + file, with the local
 *  path filled in when the download lands. */
data class StoryContentUi(
    val storyId: Int,
    val isVideo: Boolean,
    val fileId: Int,
    val localPath: String? = null
)

/**
 * Stories, viewing only (Batch 3, C5). The row data comes from
 * updateChatActiveStories after a loadActiveStories kick
 * (storyListMain); single stories are fetched with getStory and
 * marked read with openStory — all verified in td_api.tl.
 * Publishing is deliberately not implemented (its API was not
 * part of the verified set).
 */
object StoryStore {
    private val _rings = MutableStateFlow<List<StoryRing>>(emptyList())
    val rings: StateFlow<List<StoryRing>> = _rings

    private val ringMap = LinkedHashMap<Long, StoryRing>()

    private val _viewerStories = MutableStateFlow<List<StoryContentUi>>(emptyList())
    val viewerStories: StateFlow<List<StoryContentUi>> = _viewerStories

    /** fileId -> storyId for downloads this store started. */
    private val pendingDownloads = ConcurrentHashMap<Int, Int>()

    @Volatile
    private var attached = false

    @Volatile
    private var loaded = false

    fun onReady() {
        attach()
        if (!loaded) {
            loaded = true
            val query = TdClient.request("loadActiveStories").apply {
                put(
                    "story_list",
                    JSONObject().put("@type", "storyListMain")
                )
            }
            TdRepository.sendRaw(query) { }
        }
    }

    private fun attach() {
        if (attached) return
        attached = true
        TdRepository.addUpdateListener { update ->
            when (update.optString("@type")) {
                "updateChatActiveStories" -> handleActive(update)
                "updateFile" -> handleFile(update.optJSONObject("file"))
            }
        }
    }

    private fun handleActive(update: JSONObject) {
        val active = update.optJSONObject("active_stories") ?: return
        if (active.optJSONObject("list")?.optString("@type") != "storyListMain") {
            return
        }
        val chatId = active.optLong("chat_id")
        val stories = active.optJSONArray("stories")
        val ids = ArrayList<Int>()
        if (stories != null) {
            for (i in 0 until stories.length()) {
                val info = stories.optJSONObject(i) ?: continue
                ids.add(info.optInt("story_id"))
            }
        }
        synchronized(ringMap) {
            if (ids.isEmpty()) {
                ringMap.remove(chatId)
            } else {
                ringMap[chatId] = StoryRing(
                    chatId, ids,
                    active.optInt("max_read_story_id"),
                    active.optLong("order")
                )
            }
            _rings.value = ringMap.values.sortedByDescending { it.order }
        }
    }

    /** Open the viewer for one chat: fetch every active story's
     *  content and start its download; mark the newest as read. */
    fun openChat(chatId: Long) {
        val ring = synchronized(ringMap) { ringMap[chatId] } ?: return
        _viewerStories.value = ring.storyIds.map {
            StoryContentUi(it, isVideo = false, fileId = 0)
        }
        for (storyId in ring.storyIds) {
            fetchStory(chatId, storyId)
        }
        ring.storyIds.maxOrNull()?.let { latest ->
            val query = TdClient.request("openStory").apply {
                put("story_poster_chat_id", chatId)
                put("story_id", latest)
            }
            TdRepository.sendRaw(query) { }
        }
    }

    private fun fetchStory(chatId: Long, storyId: Int) {
        val query = TdClient.request("getStory").apply {
            put("story_poster_chat_id", chatId)
            put("story_id", storyId)
            put("only_local", false)
        }
        TdRepository.sendRaw(query) { story ->
            if (TdClient.isError(story)) return@sendRaw
            val content = story.optJSONObject("content") ?: return@sendRaw
            val parsed = when (content.optString("@type")) {
                "storyContentPhoto" -> {
                    val sizes = content.optJSONObject("photo")
                        ?.optJSONArray("sizes")
                    var fileId = 0
                    var path: String? = null
                    if (sizes != null && sizes.length() > 0) {
                        val file = sizes.optJSONObject(sizes.length() - 1)
                            ?.optJSONObject("photo")
                        fileId = file?.optInt("id") ?: 0
                        path = downloadedPath(file)
                    }
                    StoryContentUi(storyId, false, fileId, path)
                }
                "storyContentVideo" -> {
                    val file = content.optJSONObject("video")
                        ?.optJSONObject("video")
                    StoryContentUi(
                        storyId, true,
                        file?.optInt("id") ?: 0,
                        downloadedPath(file)
                    )
                }
                else -> StoryContentUi(storyId, false, 0, null)
            }
            val current = _viewerStories.value.toMutableList()
            val index = current.indexOfFirst { it.storyId == storyId }
            if (index < 0) return@sendRaw
            current[index] = parsed
            _viewerStories.value = current
            if (parsed.localPath == null && parsed.fileId != 0) {
                pendingDownloads[parsed.fileId] = storyId
                val dl = TdClient.request("downloadFile").apply {
                    put("file_id", parsed.fileId)
                    put("priority", 16)
                    put("offset", 0)
                    put("limit", 0)
                    put("synchronous", false)
                }
                TdRepository.sendRaw(dl) { }
            }
        }
    }

    private fun handleFile(file: JSONObject?) {
        if (file == null) return
        val fileId = file.optInt("id")
        val storyId = pendingDownloads[fileId] ?: return
        val local = file.optJSONObject("local") ?: return
        if (!local.optBoolean("is_downloading_completed")) return
        val path = local.optString("path")
        if (path.isEmpty()) return
        pendingDownloads.remove(fileId)
        val current = _viewerStories.value.toMutableList()
        val index = current.indexOfFirst { it.storyId == storyId }
        if (index < 0) return
        current[index] = current[index].copy(localPath = path)
        _viewerStories.value = current
    }

    private fun downloadedPath(file: JSONObject?): String? {
        val local = file?.optJSONObject("local") ?: return null
        if (!local.optBoolean("is_downloading_completed")) return null
        return local.optString("path").ifEmpty { null }
    }
}
