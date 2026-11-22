package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** One of my own boost slots (chatBoostSlot in td_api.tl). */
data class BoostSlotUi(
    val slotId: Int,
    /** Chat this slot is currently boosting; 0 = unused. */
    val boostedChatId: Long,
    /** Unix seconds when the current boost expires. */
    val expirationDate: Int,
    /** Unix seconds before which the slot can't move. */
    val cooldownUntilDate: Int
)

/** chatBoostStatus flattened for the profile page. */
data class BoostStatusUi(
    val level: Int,
    val boostCount: Int,
    /** Boosts that were needed to reach the current level. */
    val currentLevelBoostCount: Int,
    /** Boosts needed to reach the next level; 0 = no next
     *  level (already at the maximum). */
    val nextLevelBoostCount: Int,
    val boostUrl: String,
    /** Slots of MINE already applied to this chat. */
    val appliedSlotIds: List<Int>,
    val giftCodeBoostCount: Int,
    /** Admin-only on the server; 0 for regular members. */
    val premiumMemberCount: Int,
    val premiumMemberPercentage: Double
)

/**
 * Chat boost status page data (v0.9.33, Z2). All three
 * functions verified in td_api.tl: getChatBoostStatus
 * (supergroups and channels only), getAvailableChatBoostSlots
 * (my slots — empty for accounts without Premium, which the
 * page states plainly instead of showing a dead button) and
 * boostChat (the apply call is named boostChat in this tl,
 * not applyChatBoost). Boosting spends the CURRENT user's
 * own Premium slots and is server-gated; a refusal is
 * translated, never papered over.
 */
object BoostStore {
    private val _status = MutableStateFlow<BoostStatusUi?>(null)
    val status: StateFlow<BoostStatusUi?> = _status

    private val _slots = MutableStateFlow<List<BoostSlotUi>>(emptyList())
    val slots: StateFlow<List<BoostSlotUi>> = _slots

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var currentChatId = 0L

    fun load(chatId: Long) {
        currentChatId = chatId
        _status.value = null
        _slots.value = emptyList()
        _loaded.value = false
        _error.value = null
        val query = TdClient.request("getChatBoostStatus").apply {
            put("chat_id", chatId)
        }
        TdRepository.sendRaw(query) { response ->
            if (currentChatId != chatId) return@sendRaw
            if (TdClient.isError(response)) {
                _error.value =
                    "助力状态没拿到：${errorText(response)}"
                _loaded.value = true
                return@sendRaw
            }
            val applied = ArrayList<Int>()
            val arr = response.optJSONArray("applied_slot_ids")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    applied.add(arr.optInt(i))
                }
            }
            _status.value = BoostStatusUi(
                level = response.optInt("level"),
                boostCount = response.optInt("boost_count"),
                currentLevelBoostCount =
                    response.optInt("current_level_boost_count"),
                nextLevelBoostCount =
                    response.optInt("next_level_boost_count"),
                boostUrl = response.optString("boost_url"),
                appliedSlotIds = applied,
                giftCodeBoostCount =
                    response.optInt("gift_code_boost_count"),
                premiumMemberCount =
                    response.optInt("premium_member_count"),
                premiumMemberPercentage =
                    response.optDouble("premium_member_percentage")
            )
            _loaded.value = true
        }
        TdRepository.sendRaw(
            TdClient.request("getAvailableChatBoostSlots")
        ) { response ->
            if (currentChatId != chatId) return@sendRaw
            if (TdClient.isError(response)) return@sendRaw
            _slots.value = parseSlots(response)
        }
    }

    /** Apply the chosen slots of mine to this chat. The
     *  answer is the refreshed slot list; the status is
     *  reloaded so counts and level are the server's. */
    fun boost(
        chatId: Long,
        slotIds: List<Int>,
        onDone: (String?) -> Unit
    ) {
        val query = TdClient.request("boostChat").apply {
            put("chat_id", chatId)
            put(
                "slot_ids",
                JSONArray().apply { slotIds.forEach { put(it) } }
            )
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                onDone(boostErrorText(response))
            } else {
                if (currentChatId == chatId) {
                    _slots.value = parseSlots(response)
                }
                onDone(null)
                load(chatId)
            }
        }
    }

    private fun parseSlots(response: JSONObject): List<BoostSlotUi> {
        val arr = response.optJSONArray("slots") ?: return emptyList()
        val out = ArrayList<BoostSlotUi>()
        for (i in 0 until arr.length()) {
            val slot = arr.optJSONObject(i) ?: continue
            out.add(
                BoostSlotUi(
                    slotId = slot.optInt("slot_id"),
                    boostedChatId =
                        slot.optLong("currently_boosted_chat_id"),
                    expirationDate = slot.optInt("expiration_date"),
                    cooldownUntilDate =
                        slot.optInt("cooldown_until_date")
                )
            )
        }
        return out
    }

    private fun errorText(error: JSONObject): String {
        val message = error.optString("message")
        return when {
            message.contains("BOOST", ignoreCase = true) &&
                message.contains("NOT", ignoreCase = true) ->
                "这个对话还不能被助力。"
            else ->
                "服务器拒绝了（错误码 ${error.optInt("code")}），" +
                    "等一会儿再试。"
        }
    }

    private fun boostErrorText(error: JSONObject): String {
        val message = error.optString("message")
        return when {
            message.contains("PREMIUM", ignoreCase = true) ->
                "助力是 Telegram Premium 的权益，你的账号" +
                    "目前用不了助力。"
            message.contains("COOLDOWN", ignoreCase = true) ->
                "选中的助力还在冷却期，到期后才能换地方用。"
            message.contains("FLOOD", ignoreCase = true) ->
                "操作太频繁了，等一会儿再试。"
            else ->
                "助力没成功：服务器拒绝了" +
                    "（错误码 ${error.optInt("code")}）。"
        }
    }
}
