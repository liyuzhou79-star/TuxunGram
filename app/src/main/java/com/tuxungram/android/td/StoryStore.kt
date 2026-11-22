package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
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
 *  path filled in when the download lands. The two page flags
 *  (v0.9.24, Q5) come straight from the story object:
 *  is_posted_to_chat_page says the story also lives on the
 *  profile page (a precondition for pinning it there), and
 *  can_toggle_is_posted_to_chat_page says this account may
 *  change that. */
data class StoryContentUi(
    val storyId: Int,
    val isVideo: Boolean,
    val fileId: Int,
    val localPath: String? = null,
    val isPostedToPage: Boolean = false,
    val canTogglePosted: Boolean = false,
    // Viewer interactions (v0.9.25, R3): can_be_replied gates
    // the reply entry; hasReaction mirrors a non-null
    // chosen_reaction_type on the story object.
    val canBeReplied: Boolean = false,
    val hasReaction: Boolean = false
)

/**
 * Stories (viewing from Batch 3, C5; publishing added in
 * v0.8.0, G5 after its API was verified in td_api.tl). The row
 * data comes from updateChatActiveStories after a
 * loadActiveStories kick (storyListMain); single stories are
 * fetched with getStory and marked read with openStory.
 * Publishing: canPostStory gates postStory; both verified.
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
            val posted = story.optBoolean("is_posted_to_chat_page")
            val canToggle =
                story.optBoolean("can_toggle_is_posted_to_chat_page")
            val canReply = story.optBoolean("can_be_replied")
            val hasReaction =
                story.optJSONObject("chosen_reaction_type") != null
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
                    StoryContentUi(
                        storyId, false, fileId, path, posted, canToggle,
                        canReply, hasReaction
                    )
                }
                "storyContentVideo" -> {
                    val file = content.optJSONObject("video")
                        ?.optJSONObject("video")
                    StoryContentUi(
                        storyId, true,
                        file?.optInt("id") ?: 0,
                        downloadedPath(file), posted, canToggle,
                        canReply, hasReaction
                    )
                }
                else -> StoryContentUi(
                    storyId, false, 0, null, posted, canToggle,
                    canReply, hasReaction
                )
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

    // ---- Publishing (v0.8.0, G5) ----

    /** One-line result of a publish attempt, for the UI toast. */
    private val _postNotice = MutableStateFlow<String?>(null)
    val postNotice: StateFlow<String?> = _postNotice

    fun clearPostNotice() {
        _postNotice.value = null
    }

    /** Post one photo as my own story. The chain is verified in
     *  td_api.tl end to end: getMe (whose id is the poster chat
     *  for personal stories) -> canPostStory (the gate; its
     *  result variants map to plain-language refusals) ->
     *  postStory with inputStoryContentPhoto, privacy
     *  storyPrivacySettingsEveryone, active_period 86400 (the
     *  only period non-Premium accounts may use). */
    /** Privacy choices offered at publish time (v0.9.25,
     *  R3), mapped to storyPrivacySettings* in doPostStory:
     *  everyone / contacts / close_friends; v0.9.27 (T3)
     *  adds "selected" — storyPrivacySettingsSelectedUsers
     *  with the picked user_ids (verified in td_api.tl:
     *  user_ids:vector<int53>). An empty selection is
     *  refused here as well as in the UI: posting to nobody
     *  would silently waste the story. */
    fun postPhotoStory(
        path: String,
        privacy: String = "everyone",
        selectedUserIds: List<Long> = emptyList()
    ) {
        val content = JSONObject().apply {
            put("@type", "inputStoryContentPhoto")
            put("photo", JSONObject().apply {
                put("@type", "inputFileLocal")
                put("path", path)
            })
            put("added_sticker_file_ids", JSONArray())
        }
        startPost(content, privacy, selectedUserIds)
    }

    /** Post one video as my own story (v0.9.26, S3):
     *  inputStoryContentVideo verified in td_api.tl — the
     *  video file plus its duration in seconds (the caller
     *  reads it from the picked file's metadata); no cover
     *  frame offset, not an animation. Length/format limits
     *  are the server's call and its refusal surfaces through
     *  the same notice path as photos. */
    fun postVideoStory(
        path: String,
        durationSec: Double,
        privacy: String = "everyone",
        selectedUserIds: List<Long> = emptyList()
    ) {
        val content = JSONObject().apply {
            put("@type", "inputStoryContentVideo")
            put("video", JSONObject().apply {
                put("@type", "inputFileLocal")
                put("path", path)
            })
            put("added_sticker_file_ids", JSONArray())
            put("duration", durationSec)
            put("cover_frame_timestamp", 0.0)
            put("is_animation", false)
        }
        startPost(content, privacy, selectedUserIds)
    }

    private fun startPost(
        content: JSONObject,
        privacy: String,
        selectedUserIds: List<Long> = emptyList()
    ) {
        if (privacy == "selected" && selectedUserIds.isEmpty()) {
            _postNotice.value =
                "「指定用户」还没选人，先选至少一位能看的人。"
            return
        }
        _postNotice.value = "正在发布动态…"
        TdRepository.sendRaw(TdClient.request("getMe")) { me ->
            if (TdClient.isError(me)) {
                _postNotice.value = "发布失败：账号状态还没就绪。"
                return@sendRaw
            }
            val myId = me.optLong("id")
            val gate = TdClient.request("canPostStory").apply {
                put("chat_id", myId)
            }
            TdRepository.sendRaw(gate) { result ->
                if (TdClient.isError(result)) {
                    _postNotice.value = "发布失败，再试一次。"
                    return@sendRaw
                }
                when (result.optString("@type")) {
                    "canPostStoryResultOk" ->
                        doPostStory(
                            myId, content, privacy, selectedUserIds
                        )
                    "canPostStoryResultPremiumNeeded" ->
                        _postNotice.value =
                            "发动态需要 Premium，当前账号暂时发不了。"
                    "canPostStoryResultBoostNeeded" ->
                        _postNotice.value =
                            "这个账号的助力等级还不够，暂时发不了动态。"
                    "canPostStoryResultActiveStoryLimitExceeded" ->
                        _postNotice.value =
                            "已有一条动态在展示中，等它过期后再发。"
                    "canPostStoryResultWeeklyLimitExceeded",
                    "canPostStoryResultMonthlyLimitExceeded" ->
                        _postNotice.value =
                            "最近发动态的次数用完了，过一阵再试。"
                    "canPostStoryResultLiveStoryIsActive" ->
                        _postNotice.value =
                            "正在直播动态中，结束后再发。"
                    else -> _postNotice.value = "现在发不了动态。"
                }
            }
        }
    }

    private fun doPostStory(
        myId: Long,
        content: JSONObject,
        privacy: String,
        selectedUserIds: List<Long> = emptyList()
    ) {
        val query = TdClient.request("postStory").apply {
            put("chat_id", myId)
            put("content", content)
            put("areas", JSONObject().apply {
                put("@type", "inputStoryAreas")
                put("areas", JSONArray())
            })
            put("caption", JSONObject.NULL)
            put("music", JSONObject.NULL)
            put("privacy_settings", when (privacy) {
                "contacts" -> JSONObject().apply {
                    put("@type", "storyPrivacySettingsContacts")
                    put("except_user_ids", JSONArray())
                }
                "close_friends" -> JSONObject().apply {
                    put("@type", "storyPrivacySettingsCloseFriends")
                }
                "selected" -> JSONObject().apply {
                    put(
                        "@type",
                        "storyPrivacySettingsSelectedUsers"
                    )
                    put("user_ids", JSONArray().apply {
                        for (id in selectedUserIds) put(id)
                    })
                }
                else -> JSONObject().apply {
                    put("@type", "storyPrivacySettingsEveryone")
                    put("except_user_ids", JSONArray())
                }
            })
            put("album_ids", JSONArray())
            put("active_period", 86400)
            put("from_story_full_id", JSONObject.NULL)
            put("is_posted_to_chat_page", false)
            put("protect_content", false)
        }
        TdRepository.sendRaw(query) { response ->
            _postNotice.value = if (TdClient.isError(response)) {
                // Server refusals (video too long, unsupported
                // format, limits) surface with their code and
                // message instead of a fake success.
                val detail = response.optString("message")
                "发布失败，再试一次。（错误码 " +
                    "${response.optInt("code")}）" +
                    if (detail.isNotEmpty()) " $detail" else ""
            } else {
                val who = when (privacy) {
                    "contacts" -> "联系人可见"
                    "close_friends" -> "亲密朋友可见"
                    "selected" ->
                        "指定的 ${selectedUserIds.size} 人可见"
                    else -> "所有人可见"
                }
                "动态已发布，$who，24 小时后自动归档。"
            }
        }
    }

    // ---- Viewer interactions (v0.9.25, R3) ----

    /** Toggle a ❤️ reaction on the story being viewed
     *  (setStoryReaction, verified in td_api.tl: pass a null
     *  reaction_type to remove the chosen reaction). The
     *  local flag flips only after the server accepts. */
    fun toggleStoryReaction(
        posterChatId: Long, story: StoryContentUi
    ) {
        val query = TdClient.request("setStoryReaction").apply {
            put("story_poster_chat_id", posterChatId)
            put("story_id", story.storyId)
            if (story.hasReaction) {
                put("reaction_type", JSONObject.NULL)
            } else {
                put("reaction_type", JSONObject().apply {
                    put("@type", "reactionTypeEmoji")
                    put("emoji", "❤️")
                })
            }
            put("update_recent_reactions", true)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _postNotice.value =
                    "回应没发出去，再试一次。（错误码 " +
                        "${response.optInt("code")}）"
                return@sendRaw
            }
            val current = _viewerStories.value.toMutableList()
            val index =
                current.indexOfFirst { it.storyId == story.storyId }
            if (index >= 0) {
                current[index] = current[index].copy(
                    hasReaction = !current[index].hasReaction
                )
                _viewerStories.value = current
            }
        }
    }

    /** Reply to a story with a text message
     *  (sendMessage + inputMessageReplyToStory, verified in
     *  td_api.tl). The reply lands in the poster's chat. */
    fun replyToStory(posterChatId: Long, storyId: Int, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val query = TdClient.request("sendMessage").apply {
            put("chat_id", posterChatId)
            put("reply_to", JSONObject().apply {
                put("@type", "inputMessageReplyToStory")
                put("story_poster_chat_id", posterChatId)
                put("story_id", storyId)
            })
            put("input_message_content", JSONObject().apply {
                put("@type", "inputMessageText")
                put("text", JSONObject().apply {
                    put("@type", "formattedText")
                    put("text", trimmed)
                    put("entities", JSONArray())
                })
                put("link_preview_options", JSONObject.NULL)
                put("clear_draft", false)
            })
        }
        TdRepository.sendRaw(query) { response ->
            _postNotice.value = if (TdClient.isError(response)) {
                "回复没发出去，再试一次。（错误码 " +
                    "${response.optInt("code")}）"
            } else {
                "已回复对方的动态。"
            }
        }
    }

    // ---- Pinning my stories to my profile page (v0.9.24, Q5) ----

    /** Story ids currently pinned on my profile page, read
     *  back from getChatPostedToChatPageStories'
     *  pinned_story_ids. There is no per-story pin toggle in
     *  TDLib: setChatPinnedStories replaces the whole list,
     *  and only stories already posted to the page can be in
     *  it (posting one is toggleStoryIsPostedToChatPage). */
    private val _myPinnedStoryIds =
        MutableStateFlow<Set<Int>>(emptySet())
    val myPinnedStoryIds: StateFlow<Set<Int>> = _myPinnedStoryIds

    fun loadMyPinned() {
        val myId = MeStore.me.value.id
        if (myId == 0L) return
        val query = TdClient.request(
            "getChatPostedToChatPageStories"
        ).apply {
            put("chat_id", myId)
            put("offset", 0)
            put("limit", 100)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) return@sendRaw
            val arr = response.optJSONArray("pinned_story_ids")
                ?: return@sendRaw
            val ids = LinkedHashSet<Int>()
            for (i in 0 until arr.length()) ids.add(arr.optInt(i))
            _myPinnedStoryIds.value = ids
        }
    }

    /** Pin [story] to my profile page, or unpin it when it is
     *  already pinned. Pinning first posts the story to the
     *  page when needed (the pinned list can only hold page
     *  stories) and respects the server cap from the
     *  pinned_story_count_max option. Results land in
     *  [postNotice] like publish results. */
    fun togglePinStory(posterChatId: Long, story: StoryContentUi) {
        val pinned = _myPinnedStoryIds.value
        if (story.storyId in pinned) {
            applyPinned(posterChatId, pinned - story.storyId) {
                _postNotice.value = "已取消置顶。"
            }
            return
        }
        val capQuery = TdClient.request("getOption").apply {
            put("name", "pinned_story_count_max")
        }
        TdRepository.sendRaw(capQuery) { capResponse ->
            val cap = if (TdClient.isError(capResponse)) {
                0
            } else {
                capResponse.optLong("value").toInt()
            }
            if (cap > 0 && pinned.size >= cap) {
                _postNotice.value =
                    "置顶动态已达上限（$cap 条），先取消一条再置顶新的。"
                return@sendRaw
            }
            if (story.isPostedToPage) {
                applyPinned(posterChatId, pinned + story.storyId) {
                    _postNotice.value = "已置顶到资料页。"
                }
            } else if (story.canTogglePosted) {
                val post = TdClient.request(
                    "toggleStoryIsPostedToChatPage"
                ).apply {
                    put("story_poster_chat_id", posterChatId)
                    put("story_id", story.storyId)
                    put("is_posted_to_chat_page", true)
                }
                TdRepository.sendRaw(post) { postResponse ->
                    if (TdClient.isError(postResponse)) {
                        _postNotice.value =
                            "置顶失败，再试一次。（错误码 " +
                                "${postResponse.optInt("code")}）"
                        return@sendRaw
                    }
                    markViewerStoryPosted(story.storyId)
                    applyPinned(posterChatId, pinned + story.storyId) {
                        _postNotice.value = "已置顶到资料页。"
                    }
                }
            } else {
                _postNotice.value =
                    "这条动态不能放到资料页，置顶不了。"
            }
        }
    }

    private fun applyPinned(
        posterChatId: Long,
        ids: Set<Int>,
        onOk: () -> Unit
    ) {
        val arr = JSONArray()
        for (id in ids) arr.put(id)
        val query = TdClient.request("setChatPinnedStories").apply {
            put("chat_id", posterChatId)
            put("story_ids", arr)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _postNotice.value =
                    "置顶失败，再试一次。（错误码 " +
                        "${response.optInt("code")}）"
            } else {
                _myPinnedStoryIds.value = ids
                onOk()
            }
        }
    }

    private fun markViewerStoryPosted(storyId: Int) {
        val current = _viewerStories.value.toMutableList()
        val index = current.indexOfFirst { it.storyId == storyId }
        if (index < 0) return
        current[index] = current[index].copy(isPostedToPage = true)
        _viewerStories.value = current
    }
}
