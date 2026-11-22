package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

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

/** One row of a chat's boost list (chatBoost in td_api.tl,
 *  listed by getChatBoosts; v0.9.34, A3). */
data class BoostEntryUi(
    val id: String,
    /** How many identical boosts this entry stands for. */
    val count: Int,
    /** "premium" / "gift_code" / "giveaway"; "" = unknown. */
    val sourceKind: String,
    /** The user behind the boost; 0 when the source has
     *  none (an unclaimed giveaway, say). */
    val userId: Long,
    val name: String,
    val photoPath: String?,
    /** Unix seconds when the boost was applied. */
    val startDate: Int,
    /** Unix seconds when the boost expires. */
    val expirationDate: Int
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
 *
 * The boost LIST (who boosted, v0.9.34 A3) comes from
 * getChatBoosts (offset/limit paging over foundChatBoosts,
 * all verified in td_api.tl). The server only answers it
 * for chat administrators; a refusal hides the list
 * section entirely instead of showing a dead end.
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

    // ---- Boost list (v0.9.34, A3) ----

    private val _boosts = MutableStateFlow<List<BoostEntryUi>>(
        emptyList()
    )
    val boosts: StateFlow<List<BoostEntryUi>> = _boosts

    /** Total boosts applied to the chat (the list's own
     *  total_count; counts boosts, not list rows). */
    private val _boostsTotal = MutableStateFlow(0)
    val boostsTotal: StateFlow<Int> = _boostsTotal

    /** True only once getChatBoosts has answered at least
     *  one page: the server answers it for administrators,
     *  and anyone else gets the section hidden, not an
     *  error wall. */
    private val _boostsVisible = MutableStateFlow(false)
    val boostsVisible: StateFlow<Boolean> = _boostsVisible

    private val _boostsHasMore = MutableStateFlow(false)
    val boostsHasMore: StateFlow<Boolean> = _boostsHasMore

    @Volatile
    private var boostsOffset = ""

    @Volatile
    private var boostsLoading = false

    private val boostNameByUser = ConcurrentHashMap<Long, String>()
    private val boostNamePending =
        ConcurrentHashMap.newKeySet<Long>()

    private var currentChatId = 0L

    fun load(chatId: Long) {
        currentChatId = chatId
        _status.value = null
        _slots.value = emptyList()
        _loaded.value = false
        _error.value = null
        _boosts.value = emptyList()
        _boostsTotal.value = 0
        _boostsVisible.value = false
        _boostsHasMore.value = false
        boostsOffset = ""
        loadBoostsPage(chatId)
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

    /** One page of the boost list. The first page decides
     *  visibility: an error there (not an administrator,
     *  in practice) hides the section; an error on a later
     *  page just stops paging where it is. */
    private fun loadBoostsPage(chatId: Long) {
        if (boostsLoading) return
        boostsLoading = true
        val offset = boostsOffset
        val query = TdClient.request("getChatBoosts").apply {
            put("chat_id", chatId)
            put("only_gift_codes", false)
            put("offset", offset)
            put("limit", 50)
        }
        TdRepository.sendRaw(query) { response ->
            boostsLoading = false
            if (currentChatId != chatId) return@sendRaw
            if (TdClient.isError(response)) {
                if (offset.isEmpty()) {
                    _boostsVisible.value = false
                }
                _boostsHasMore.value = false
                return@sendRaw
            }
            _boostsVisible.value = true
            _boostsTotal.value = response.optInt("total_count")
            boostsOffset = response.optString("next_offset")
            _boostsHasMore.value = boostsOffset.isNotEmpty()
            val arr = response.optJSONArray("boosts") ?: JSONArray()
            val page = ArrayList<BoostEntryUi>()
            for (i in 0 until arr.length()) {
                val boost = arr.optJSONObject(i) ?: continue
                val source = boost.optJSONObject("source")
                val kind = when (source?.optString("@type")) {
                    "chatBoostSourcePremium" -> "premium"
                    "chatBoostSourceGiftCode" -> "gift_code"
                    "chatBoostSourceGiveaway" -> "giveaway"
                    else -> ""
                }
                val userId = source?.optLong("user_id") ?: 0L
                page.add(
                    BoostEntryUi(
                        id = boost.optString("id"),
                        count = boost.optInt("count"),
                        sourceKind = kind,
                        userId = userId,
                        name = boostNameByUser[userId] ?: "",
                        photoPath = if (userId != 0L) {
                            AvatarStore.pathForUser(userId)
                        } else {
                            null
                        },
                        startDate = boost.optInt("start_date"),
                        expirationDate =
                            boost.optInt("expiration_date")
                    )
                )
            }
            _boosts.value = if (offset.isEmpty()) {
                page
            } else {
                _boosts.value + page
            }
            for (entry in page) {
                if (entry.userId != 0L && entry.name.isEmpty()) {
                    resolveBoostName(entry.userId)
                }
            }
        }
    }

    /** Next page of the boost list, when the server said
     *  there is one. */
    fun loadMoreBoosts() {
        val chatId = currentChatId
        if (chatId == 0L || boostsOffset.isEmpty()) return
        loadBoostsPage(chatId)
    }

    /** Booster names resolve lazily through getUser, the
     *  same pattern as the message viewers list. */
    private fun resolveBoostName(userId: Long) {
        if (!boostNamePending.add(userId)) return
        val query = TdClient.request("getUser").apply {
            put("user_id", userId)
        }
        TdRepository.sendRaw(query) { user ->
            boostNamePending.remove(userId)
            if (TdClient.isError(user)) return@sendRaw
            AvatarStore.noteUser(user)
            val name = listOf(
                user.optString("first_name"),
                user.optString("last_name")
            ).filter { it.isNotEmpty() }.joinToString(" ")
            if (name.isNotEmpty()) boostNameByUser[userId] = name
            patchBoostUser(userId)
        }
    }

    private fun patchBoostUser(userId: Long) {
        val name = boostNameByUser[userId] ?: ""
        val path = AvatarStore.pathForUser(userId)
        _boosts.value = _boosts.value.map { entry ->
            if (entry.userId == userId &&
                (entry.name != name || entry.photoPath != path)
            ) {
                entry.copy(name = name, photoPath = path)
            } else {
                entry
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
