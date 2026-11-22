package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/** One member row of the @ suggestion panel (v0.9.15, V6). */
data class SuggestUser(
    val userId: Long,
    val name: String,
    val username: String,
    val photoPath: String?
)

/**
 * Input suggestions (v0.9.15, V6): @-mentions and #-hashtags
 * for the main chat's input box.
 *
 * Mentions ride searchChatMembers(chat_id, query, limit,
 * chatMembersFilterMembers) — the same call the members
 * page search uses (verified in td_api.tl) — followed by a
 * getUser per result for the display name and username;
 * members without a username are inserted as a name with a
 * textEntityTypeMentionName entity (also verified) by the
 * caller. Hashtags ride searchHashtags(prefix, limit)
 * (verified): the answer lists tags the user has used
 * before, each possibly carrying a leading '#', which is
 * normalized away here.
 *
 * A separate store (not MemberListStore) on purpose: that
 * store's state belongs to the members page and typing in
 * the chat must never disturb it. Every search bumps a
 * generation counter so a slow answer for an older fragment
 * can never overwrite a newer one.
 */
object SuggestStore {

    private val _mentionResults =
        MutableStateFlow<List<SuggestUser>>(emptyList())
    val mentionResults: StateFlow<List<SuggestUser>> = _mentionResults

    private val _hashtagResults =
        MutableStateFlow<List<String>>(emptyList())
    val hashtagResults: StateFlow<List<String>> = _hashtagResults

    @Volatile
    private var generation = 0

    fun clear() {
        generation++
        _mentionResults.value = emptyList()
        _hashtagResults.value = emptyList()
    }

    fun searchMentions(chatId: Long, query: String) {
        if (chatId == 0L) return
        val gen = ++generation
        _hashtagResults.value = emptyList()
        val request = TdClient.request("searchChatMembers").apply {
            put("chat_id", chatId)
            put("query", query)
            put("limit", 20)
            put(
                "filter",
                JSONObject().put("@type", "chatMembersFilterMembers")
            )
        }
        TdRepository.sendRaw(request) { response ->
            if (gen != generation) return@sendRaw
            if (TdClient.isError(response)) {
                _mentionResults.value = emptyList()
                return@sendRaw
            }
            val arr = response.optJSONArray("members")
            val ids = ArrayList<Long>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val member = arr.optJSONObject(i) ?: continue
                    val sender = member.optJSONObject("member_id")
                        ?: continue
                    if (sender.optString("@type") !=
                        "messageSenderUser"
                    ) {
                        continue
                    }
                    val userId = sender.optLong("user_id")
                    if (userId != 0L && userId !in ids) ids.add(userId)
                }
            }
            if (ids.isEmpty()) {
                _mentionResults.value = emptyList()
                return@sendRaw
            }
            val resolved = arrayOfNulls<SuggestUser>(ids.size)
            val remaining = AtomicInteger(ids.size)
            ids.forEachIndexed { index, userId ->
                val userQuery = TdClient.request("getUser").apply {
                    put("user_id", userId)
                }
                TdRepository.sendRaw(userQuery) { user ->
                    if (!TdClient.isError(user)) {
                        AvatarStore.noteUser(user)
                        val name = listOf(
                            user.optString("first_name"),
                            user.optString("last_name")
                        ).filter { it.isNotEmpty() }.joinToString(" ")
                        val username = user.optJSONObject("usernames")
                            ?.optJSONArray("active_usernames")
                            ?.optString(0) ?: ""
                        resolved[index] = SuggestUser(
                            userId = userId,
                            name = name.ifEmpty { username },
                            username = username,
                            photoPath = AvatarStore.pathForUser(userId)
                        )
                    }
                    if (remaining.decrementAndGet() == 0 &&
                        gen == generation
                    ) {
                        _mentionResults.value =
                            resolved.filterNotNull()
                    }
                }
            }
        }
    }

    fun searchHashtags(prefix: String) {
        val gen = ++generation
        _mentionResults.value = emptyList()
        val request = TdClient.request("searchHashtags").apply {
            put("prefix", prefix)
            put("limit", 20)
        }
        TdRepository.sendRaw(request) { response ->
            if (gen != generation) return@sendRaw
            if (TdClient.isError(response)) {
                _hashtagResults.value = emptyList()
                return@sendRaw
            }
            val arr = response.optJSONArray("hashtags")
            val tags = ArrayList<String>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val tag = arr.optString(i)
                        .removePrefix("#")
                        .trim()
                    if (tag.isNotEmpty() && tag !in tags) {
                        tags.add(tag)
                    }
                }
            }
            _hashtagResults.value = tags
        }
    }
}
