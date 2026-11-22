package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** One scope's notification preferences as the settings page
 *  edits them (v0.9.12, S1). Mirrors scopeNotificationSettings
 *  in td_api.tl (line 2715): mute_for / sound_id / show_preview
 *  / use_default_mute_stories / mute_stories / story_sound_id /
 *  show_story_poster / disable_pinned_message_notifications /
 *  disable_mention_notifications. Fields this page does not
 *  expose (sound_id, story_sound_id, show_story_poster) are
 *  carried verbatim from the loaded server object and written
 *  back untouched on every save — a settings page must never
 *  silently reset what it does not show. */
data class ScopeNotifyUi(
    val scopeType: String,
    val loaded: Boolean = false,
    val failed: Boolean = false,
    val muteFor: Int = 0,
    val showPreview: Boolean = true,
    val muteStoriesUseDefault: Boolean = true,
    val muteStories: Boolean = false,
    val disablePinned: Boolean = false,
    val disableMentions: Boolean = false,
    val soundId: Long = 0L,
    val storySoundId: Long = 0L,
    val showStoryPoster: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("@type", "scopeNotificationSettings")
        put("mute_for", muteFor)
        put("sound_id", soundId)
        put("show_preview", showPreview)
        put("use_default_mute_stories", muteStoriesUseDefault)
        put("mute_stories", muteStories)
        put("story_sound_id", storySoundId)
        put("show_story_poster", showStoryPoster)
        put("disable_pinned_message_notifications", disablePinned)
        put("disable_mention_notifications", disableMentions)
    }

    companion object {
        /** "Mute forever" as the official clients encode it:
         *  mute_for is a seconds count, and Int.MAX_VALUE is
         *  the conventional forever. */
        const val MUTE_FOREVER = Int.MAX_VALUE

        fun fromJson(scopeType: String, o: JSONObject): ScopeNotifyUi =
            ScopeNotifyUi(
                scopeType = scopeType,
                loaded = true,
                muteFor = o.optInt("mute_for", 0),
                showPreview = o.optBoolean("show_preview", true),
                muteStoriesUseDefault =
                    o.optBoolean("use_default_mute_stories", true),
                muteStories = o.optBoolean("mute_stories", false),
                disablePinned = o.optBoolean(
                    "disable_pinned_message_notifications", false
                ),
                disableMentions = o.optBoolean(
                    "disable_mention_notifications", false
                ),
                soundId = o.optLong("sound_id", 0L),
                storySoundId = o.optLong("story_sound_id", 0L),
                showStoryPoster = o.optBoolean("show_story_poster", true)
            )
    }
}

/**
 * Server-synced notification settings (v0.9.12, S1):
 * getScopeNotificationSettings / setScopeNotificationSettings
 * (td_api.tl lines 14938 / 14943) for the three scopes —
 * private chats, group chats, channel chats. These live on
 * Telegram's servers and are shared with the official clients:
 * change a scope here and the official app sees the same value.
 * The settings page's local "后台接收消息" switch is a
 * different layer (it gates this device's keep-alive service)
 * and stays independent.
 */
object ScopeNotifyStore {

    const val SCOPE_PRIVATE = "notificationSettingsScopePrivateChats"
    const val SCOPE_GROUPS = "notificationSettingsScopeGroupChats"
    const val SCOPE_CHANNELS = "notificationSettingsScopeChannelChats"

    val SCOPES = listOf(SCOPE_PRIVATE, SCOPE_GROUPS, SCOPE_CHANNELS)

    private val _scopes = MutableStateFlow<Map<String, ScopeNotifyUi>>(
        SCOPES.associateWith { ScopeNotifyUi(scopeType = it) }
    )
    val scopes: StateFlow<Map<String, ScopeNotifyUi>> = _scopes

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    fun loadAll() {
        for (scope in SCOPES) load(scope)
    }

    fun load(scopeType: String) {
        val current = _scopes.value[scopeType]
        if (current != null && current.loaded) return
        val query = TdClient.request("getScopeNotificationSettings")
            .apply {
                put(
                    "scope",
                    JSONObject().put("@type", scopeType)
                )
            }
        TdRepository.sendRaw(query) { response ->
            val map = _scopes.value.toMutableMap()
            if (TdClient.isError(response)) {
                map[scopeType] = ScopeNotifyUi(
                    scopeType = scopeType, failed = true
                )
            } else {
                map[scopeType] =
                    ScopeNotifyUi.fromJson(scopeType, response)
            }
            _scopes.value = map
        }
    }

    /** Save one edited scope. The UI passes the full edited
     *  object (built from the loaded one), so unexposed fields
     *  ride along verbatim. On failure the previous server
     *  state is re-read rather than leaving the page showing a
     *  value the server never took. */
    fun save(updated: ScopeNotifyUi) {
        val query = TdClient.request("setScopeNotificationSettings")
            .apply {
                put(
                    "scope",
                    JSONObject().put("@type", updated.scopeType)
                )
                put("notification_settings", updated.toJson())
            }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _notice.value = "保存失败，检查网络后再试一次。"
                val map = _scopes.value.toMutableMap()
                map[updated.scopeType] = ScopeNotifyUi(
                    scopeType = updated.scopeType
                )
                _scopes.value = map
                load(updated.scopeType)
            } else {
                _notice.value = null
                val map = _scopes.value.toMutableMap()
                map[updated.scopeType] = updated.copy(loaded = true)
                _scopes.value = map
            }
        }
    }
}
