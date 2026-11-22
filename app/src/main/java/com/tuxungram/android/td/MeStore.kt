package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** My own profile, flattened from getMe + getUserFullInfo
 *  (v0.9.3, M1). One process-wide copy: the settings personal
 *  card, the edit-profile page and the account labels all read
 *  it, and every successful edit re-fetches it so the whole app
 *  shows the new name / avatar immediately. Kept current
 *  between refreshes by updateUser for my own user id. */
data class MeUi(
    val id: Long = 0L,
    val firstName: String = "",
    val lastName: String = "",
    val username: String = "",
    val phone: String = "",
    val bio: String = "",
    /** user.profile_photo.id — the id deleteProfilePhoto wants
     *  (NOT the small file's id). 0 = no photo set. */
    val photoId: Long = 0L,
    val photoPath: String? = null
) {
    val displayName: String
        get() = listOf(firstName, lastName)
            .filter { it.isNotEmpty() }.joinToString(" ")
            .ifEmpty { phone }
}

object MeStore {
    private val _me = MutableStateFlow(MeUi())
    val me: StateFlow<MeUi> = _me

    /** The small rendition's file id for the current photo;
     *  updateFile completions are matched against it. */
    @Volatile
    private var photoFileId = 0

    @Volatile
    private var photoDownloadPending = false

    @Volatile
    private var listenerRegistered = false

    private fun registerListener() {
        if (listenerRegistered) return
        listenerRegistered = true
        TdRepository.addUpdateListener { update ->
            when (update.optString("@type")) {
                "updateUser" -> {
                    val user = update.optJSONObject("user") ?: return@addUpdateListener
                    if (user.optLong("id") == _me.value.id && _me.value.id != 0L) {
                        applyUser(user, keepBio = true)
                    }
                }
                "updateFile" -> {
                    val file = update.optJSONObject("file") ?: return@addUpdateListener
                    if (file.optInt("id") != photoFileId) return@addUpdateListener
                    val local = file.optJSONObject("local") ?: return@addUpdateListener
                    if (local.optBoolean("is_downloading_completed")) {
                        photoDownloadPending = false
                        val path = local.optString("path")
                        if (path.isNotEmpty()) {
                            _me.value = _me.value.copy(photoPath = path)
                        }
                    }
                }
            }
        }
    }

    /** Fetch getMe (+ getUserFullInfo for the bio). Called on
     *  Ready and after every successful edit. */
    fun refresh() {
        registerListener()
        TdRepository.sendRaw(TdClient.request("getMe")) { user ->
            if (TdClient.isError(user)) return@sendRaw
            applyUser(user, keepBio = true)
            val myId = user.optLong("id")
            TdRepository.sendRaw(TdClient.request("getUserFullInfo").apply {
                put("user_id", myId)
            }) { full ->
                if (TdClient.isError(full)) return@sendRaw
                if (_me.value.id != myId) return@sendRaw
                _me.value = _me.value.copy(
                    bio = full.optJSONObject("bio")?.optString("text") ?: ""
                )
            }
        }
    }

    private fun applyUser(user: JSONObject, keepBio: Boolean) {
        val photo = user.optJSONObject("profile_photo")
        val small = photo?.optJSONObject("small")
        val smallFileId = small?.optInt("id") ?: 0
        val local = small?.optJSONObject("local")
        val localPath = if (
            local != null && local.optBoolean("is_downloading_completed")
        ) {
            local.optString("path").ifEmpty { null }
        } else {
            null
        }
        val previous = _me.value
        photoFileId = smallFileId
        _me.value = MeUi(
            id = user.optLong("id"),
            firstName = user.optString("first_name"),
            lastName = user.optString("last_name"),
            username = user.optJSONObject("usernames")
                ?.optJSONArray("active_usernames")?.optString(0) ?: "",
            phone = user.optString("phone_number"),
            bio = if (keepBio) previous.bio else "",
            photoId = photo?.optLong("id") ?: 0L,
            photoPath = localPath
                ?: if (previous.photoId == (photo?.optLong("id") ?: 0L)) {
                    previous.photoPath
                } else {
                    null
                }
        )
        if (localPath == null && smallFileId != 0) {
            ensurePhotoDownload(smallFileId)
        }
    }

    private fun ensurePhotoDownload(fileId: Int) {
        if (photoDownloadPending) return
        photoDownloadPending = true
        TdRepository.sendRaw(TdClient.request("downloadFile").apply {
            put("file_id", fileId)
            put("priority", 1)
            put("offset", 0L)
            put("limit", 0L)
            put("synchronous", false)
        }) { response ->
            if (TdClient.isError(response)) photoDownloadPending = false
        }
    }

    // ---- Edits (all verified in td_api.tl). Callbacks receive
    // null on success or a plain-language error sentence; raw
    // server text never reaches the screen. ----

    private fun plainError(error: JSONObject, what: String): String {
        val message = error.optString("message")
        return when {
            message.contains("FLOOD_WAIT") -> "操作太频繁了，等一会儿再试。"
            message.contains("USERNAME_NOT_MODIFIED") -> "和现在的一样，没有改动。"
            else -> "$what 失败，再试一次。（错误码 ${error.optInt("code")}）"
        }
    }

    fun saveName(first: String, last: String, cb: (String?) -> Unit) {
        TdRepository.sendRaw(TdClient.request("setName").apply {
            put("first_name", first)
            put("last_name", last)
        }) { response ->
            if (TdClient.isError(response)) {
                cb(plainError(response, "改名字"))
            } else {
                // The settings account tab shows this name too.
                AccountStore.setLabel(
                    AccountStore.currentSlot(),
                    listOf(first, last).filter { it.isNotEmpty() }
                        .joinToString(" ")
                )
                refresh()
                cb(null)
            }
        }
    }

    fun saveBio(bio: String, cb: (String?) -> Unit) {
        TdRepository.sendRaw(TdClient.request("setBio").apply {
            put("bio", bio)
        }) { response ->
            if (TdClient.isError(response)) {
                cb(plainError(response, "改简介"))
            } else {
                refresh()
                cb(null)
            }
        }
    }

    /** Username change with the availability check first
     *  (checkChatUsername against my own private chat, then
     *  setUsername). An empty username clears it directly —
     *  no check needed for giving one up. */
    fun saveUsername(username: String, cb: (String?) -> Unit) {
        if (username.isEmpty()) {
            applyUsername("", cb)
            return
        }
        val myId = _me.value.id
        if (myId == 0L) {
            cb("资料还没加载完，等一下再试。")
            return
        }
        TdRepository.sendRaw(TdClient.request("createPrivateChat").apply {
            put("user_id", myId)
            put("force", false)
        }) { chat ->
            if (TdClient.isError(chat)) {
                cb(plainError(chat, "改用户名"))
                return@sendRaw
            }
            val chatId = chat.optLong("id")
            TdRepository.sendRaw(TdClient.request("checkChatUsername").apply {
                put("chat_id", chatId)
                put("username", username)
            }) { check ->
                if (TdClient.isError(check)) {
                    cb(plainError(check, "检查用户名"))
                    return@sendRaw
                }
                when (check.optString("@type")) {
                    "checkChatUsernameResultOk" -> applyUsername(username, cb)
                    "checkChatUsernameResultUsernameOccupied" ->
                        cb("这个用户名已经被别人用了，换一个吧。")
                    "checkChatUsernameResultUsernameInvalid" ->
                        cb("用户名格式不对：只能用英文字母、数字和下划线，至少 5 个字符。")
                    "checkChatUsernameResultUsernamePurchasable" ->
                        cb("这个用户名是可购买的特殊用户名，在这里设置不了。")
                    "checkChatUsernameResultPublicChatsTooMany" ->
                        cb("你公开的群组/频道数量到上限了，暂时不能改用户名。")
                    "checkChatUsernameResultPublicGroupsUnavailable" ->
                        cb("这个账号目前不能设置用户名。")
                    else -> cb("检查用户名失败，再试一次。")
                }
            }
        }
    }

    private fun applyUsername(username: String, cb: (String?) -> Unit) {
        TdRepository.sendRaw(TdClient.request("setUsername").apply {
            put("username", username)
        }) { response ->
            if (TdClient.isError(response)) {
                cb(plainError(response, "改用户名"))
            } else {
                refresh()
                cb(null)
            }
        }
    }

    /** Replace the profile photo with a local image file
     *  (setProfilePhoto with inputChatPhotoStatic, verified in
     *  td_api.tl). The caller hands over a cacheDir copy it owns. */
    fun setPhoto(path: String, cb: (String?) -> Unit) {
        val query = TdClient.request("setProfilePhoto").apply {
            put("photo", JSONObject().apply {
                put("@type", "inputChatPhotoStatic")
                put("photo", JSONObject().apply {
                    put("@type", "inputFileLocal")
                    put("path", path)
                })
            })
            put("is_public", false)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                cb(plainError(response, "换头像"))
            } else {
                refresh()
                cb(null)
            }
        }
    }

    /** Delete the current profile photo (deleteProfilePhoto
     *  wants the profile_photo.id from getMe). */
    fun deletePhoto(cb: (String?) -> Unit) {
        val photoId = _me.value.photoId
        if (photoId == 0L) {
            cb("现在没有头像。")
            return
        }
        TdRepository.sendRaw(TdClient.request("deleteProfilePhoto").apply {
            put("profile_photo_id", photoId)
        }) { response ->
            if (TdClient.isError(response)) {
                cb(plainError(response, "删除头像"))
            } else {
                refresh()
                cb(null)
            }
        }
    }
}
