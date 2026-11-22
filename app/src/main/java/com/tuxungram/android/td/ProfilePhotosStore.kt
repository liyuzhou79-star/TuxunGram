package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** One entry of a user's profile-photo history (v0.9.12,
 *  S3), flattened from chatPhoto in td_api.tl (line 1062):
 *  [id] is the profile photo id deleteProfilePhoto takes,
 *  [thumbFileId] the smallest photoSize, [fullFileId] the
 *  largest. Local paths resolve through AvatarStore's generic
 *  file-id map. */
data class ProfilePhotoUi(
    val id: Long,
    val addedDate: Int,
    val thumbFileId: Int,
    val fullFileId: Int
)

data class ProfilePhotosUi(
    val userId: Long = 0L,
    val loaded: Boolean = false,
    val failed: Boolean = false,
    val totalCount: Int = 0,
    val photos: List<ProfilePhotoUi> = emptyList(),
    /** Bumped when AvatarStore lands a new path so the wall
     *  and the viewer recompose. */
    val tick: Int = 0
)

/**
 * Profile photo gallery (v0.9.12, S3): getUserProfilePhotos
 * (td_api.tl line 15898) for the user whose profile page is
 * open. Thumbnails download eagerly (they are small); the
 * full size is only requested when the viewer opens that
 * page. Deleting one of your own history photos goes through
 * deleteProfilePhoto (line 16127) and reloads the list. The
 * tl exposes no equivalent history call for group/channel
 * chats, so the wall exists on user profiles only.
 */
object ProfilePhotosStore {

    private val _state = MutableStateFlow(ProfilePhotosUi())
    val state: StateFlow<ProfilePhotosUi> = _state

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    init {
        AvatarStore.addListener {
            val s = _state.value
            if (s.loaded) _state.value = s.copy(tick = s.tick + 1)
        }
    }

    fun load(userId: Long) {
        if (userId == 0L) return
        val s = _state.value
        if (s.userId == userId && (s.loaded || !s.failed)) {
            if (s.loaded) return
        }
        _state.value = ProfilePhotosUi(userId = userId)
        val query = TdClient.request("getUserProfilePhotos").apply {
            put("user_id", userId)
            put("offset", 0)
            put("limit", 100)
        }
        TdRepository.sendRaw(query) { response ->
            if (_state.value.userId != userId) return@sendRaw
            if (TdClient.isError(response)) {
                _state.value = ProfilePhotosUi(
                    userId = userId, failed = true
                )
                return@sendRaw
            }
            val photos = ArrayList<ProfilePhotoUi>()
            val arr = response.optJSONArray("photos")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val photo = arr.optJSONObject(i) ?: continue
                    val sizes = photo.optJSONArray("sizes")
                        ?: continue
                    var thumbId = 0
                    var fullId = 0
                    var thumbW = Int.MAX_VALUE
                    var fullW = -1
                    for (j in 0 until sizes.length()) {
                        val size = sizes.optJSONObject(j) ?: continue
                        val file = size.optJSONObject("photo")
                            ?: continue
                        AvatarStore.noteFile(file)
                        val fid = file.optInt("id", 0)
                        if (fid == 0) continue
                        val w = size.optInt("width", 0)
                        if (w < thumbW) {
                            thumbW = w
                            thumbId = fid
                        }
                        if (w > fullW) {
                            fullW = w
                            fullId = fid
                        }
                    }
                    if (thumbId == 0) continue
                    if (fullId == 0) fullId = thumbId
                    AvatarStore.ensureDownload(thumbId)
                    photos.add(
                        ProfilePhotoUi(
                            id = photo.optLong("id"),
                            addedDate = photo.optInt("added_date"),
                            thumbFileId = thumbId,
                            fullFileId = fullId
                        )
                    )
                }
            }
            _state.value = ProfilePhotosUi(
                userId = userId, loaded = true,
                totalCount = response.optInt(
                    "total_count", photos.size
                ),
                photos = photos
            )
        }
    }

    /** Kick the full-size download when the viewer shows a
     *  page; the path arrives through AvatarStore as usual. */
    fun ensureFull(photo: ProfilePhotoUi) {
        AvatarStore.ensureDownload(photo.fullFileId)
    }

    fun delete(photo: ProfilePhotoUi) {
        val userId = _state.value.userId
        val query = TdClient.request("deleteProfilePhoto").apply {
            put("profile_photo_id", photo.id)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _notice.value = "这张头像没删掉，再试一次。"
            } else {
                _notice.value = "已删除这张历史头像。"
                _state.value = ProfilePhotosUi()
                load(userId)
            }
        }
    }

    fun clearNotice() {
        _notice.value = null
    }
}
