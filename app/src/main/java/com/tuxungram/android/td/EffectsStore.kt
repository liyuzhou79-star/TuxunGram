package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One sendable message effect, flattened from messageEffect
 *  (v0.9.24, Q8). The full-screen animation itself is played
 *  by the receiving client; this app picks effects for
 *  sending and labels received ones, which is what the data
 *  TDLib exposes allows. */
data class EffectUi(
    val id: Long,
    val emoji: String,
    val isPremium: Boolean
)

/**
 * Message effects (v0.9.24, Q8): the available-effect id list
 * arrives via updateAvailableMessageEffects (reaction +
 * sticker effect ids); each id is resolved once through
 * getMessageEffect into an emoji + premium flag. Effects are
 * attached to outgoing private-chat messages through
 * messageSendOptions.effect_id, and read back from
 * message.effect_id on received ones. Premium effects are
 * listed (the official picker shows them too) but the server
 * refuses them for free accounts — the send error is mapped
 * to plain language by MessageStore like every other refusal.
 */
object EffectsStore {
    private val _effects = MutableStateFlow<List<EffectUi>>(emptyList())
    val effects: StateFlow<List<EffectUi>> = _effects

    private val byId = ConcurrentHashMap<Long, EffectUi>()

    /** Ids from the latest updateAvailableMessageEffects. */
    private val availableIds = LinkedHashSet<Long>()

    /** effect id -> emoji, for bubble markers; filled lazily
     *  as effects are resolved. */
    private val _emojiById =
        MutableStateFlow<Map<Long, String>>(emptyMap())
    val emojiById: StateFlow<Map<Long, String>> = _emojiById

    @Volatile
    private var attached = false

    fun attach() {
        if (attached) return
        attached = true
        TdRepository.addUpdateListener { update ->
            if (update.optString("@type") !=
                "updateAvailableMessageEffects"
            ) {
                return@addUpdateListener
            }
            val ids = LinkedHashSet<Long>()
            for (field in listOf(
                "reaction_effect_ids", "sticker_effect_ids"
            )) {
                val arr = update.optJSONArray(field) ?: continue
                for (i in 0 until arr.length()) {
                    ids.add(arr.optLong(i))
                }
            }
            synchronized(availableIds) {
                availableIds.clear()
                availableIds.addAll(ids)
            }
            for (id in ids) ensureEffect(id)
            publishList()
        }
    }

    private fun publishList() {
        val snapshot = synchronized(availableIds) {
            availableIds.toList()
        }
        _effects.value = snapshot.mapNotNull { byId[it] }
            .sortedBy { it.emoji }
    }

    /** Resolve one effect id (picker list or a received
     *  message's marker); a no-op once it is known. A 404
     *  just leaves the id unknown — the bubble then shows a
     *  generic sparkle instead of an emoji. */
    fun ensureEffect(id: Long) {
        if (id == 0L || byId.containsKey(id)) return
        val query = TdClient.request("getMessageEffect").apply {
            put("effect_id", id)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) return@sendRaw
            val effect = EffectUi(
                id = response.optLong("id", id),
                emoji = response.optString("emoji"),
                isPremium = response.optBoolean("is_premium")
            )
            byId[effect.id] = effect
            _emojiById.value = _emojiById.value +
                (effect.id to effect.emoji)
            publishList()
        }
    }
}
