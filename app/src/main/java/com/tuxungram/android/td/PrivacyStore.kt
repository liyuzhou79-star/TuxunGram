package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** The three tiers the privacy page offers, matching how the
 *  mature clients present these settings. CUSTOM means the
 *  account carries rules this page cannot express (exception
 *  lists set in another client); UNKNOWN means not read yet. */
enum class PrivacyTier { EVERYONE, CONTACTS, NOBODY, CUSTOM, UNKNOWN }

data class PrivacySettingDef(
    /** The userPrivacySetting* @type from td_api.tl. */
    val type: String,
    val label: String,
    val hint: String
)

/**
 * Privacy settings (v0.9.3, M2): every userPrivacySetting* in
 * td_api.tl, read and written through
 * getUserPrivacySettingRules / setUserPrivacySettingRules
 * (verified in td_api.tl). Each setting collapses to one of
 * the three tiers — everyone = a single AllowAll rule,
 * contacts = AllowContacts, nobody = RestrictAll. A read that
 * fails is reported as a failure; the page never shows a
 * guessed state.
 */
object PrivacyStore {
    val SETTINGS: List<PrivacySettingDef> = listOf(
        PrivacySettingDef(
            "userPrivacySettingShowStatus", "上次在线时间",
            "谁能看到你最后上线的时间和是否在线"
        ),
        PrivacySettingDef(
            "userPrivacySettingShowPhoneNumber", "电话号码",
            "谁能看到你的手机号码"
        ),
        PrivacySettingDef(
            "userPrivacySettingShowProfilePhoto", "头像",
            "谁能看到你的头像"
        ),
        PrivacySettingDef(
            "userPrivacySettingShowBio", "简介",
            "谁能看到你的个人简介"
        ),
        PrivacySettingDef(
            "userPrivacySettingShowBirthdate", "生日",
            "谁能看到你的生日"
        ),
        PrivacySettingDef(
            "userPrivacySettingShowProfileAudio", "主页音乐",
            "谁能看到你主页上的音乐"
        ),
        PrivacySettingDef(
            "userPrivacySettingShowLinkInForwardedMessages", "转发时附上我的链接",
            "别人转发你的消息时，要不要附上指向你主页的链接"
        ),
        PrivacySettingDef(
            "userPrivacySettingAllowFindingByPhoneNumber", "用手机号找到我",
            "知道你手机号的人能不能搜到你的账号"
        ),
        PrivacySettingDef(
            "userPrivacySettingAllowChatInvites", "拉我进群和频道",
            "谁可以把你拉进群组或邀请进频道"
        ),
        PrivacySettingDef(
            "userPrivacySettingAllowCalls", "来电",
            "谁可以给你打电话（账号级设置，在支持通话的客户端上生效）"
        ),
        PrivacySettingDef(
            "userPrivacySettingAllowPeerToPeerCalls", "通话直连",
            "通话时要不要和对方直连（直连会让对方看到你的 IP）"
        ),
        PrivacySettingDef(
            "userPrivacySettingAllowPrivateVoiceAndVideoNoteMessages",
            "私密语音和视频留言",
            "谁可以给你发语音和视频留言"
        ),
        PrivacySettingDef(
            "userPrivacySettingAutosaveGifts", "自动保存收到的礼物",
            "收到的礼物要不要自动展示在主页上"
        ),
        PrivacySettingDef(
            "userPrivacySettingAllowUnpaidMessages", "陌生人免费消息",
            "没有你联系方式的人能不能免费给你发消息"
        )
    )

    private val _tiers = MutableStateFlow<Map<String, PrivacyTier>>(emptyMap())
    val tiers: StateFlow<Map<String, PrivacyTier>> = _tiers

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    private var loading = false

    fun tierLabel(tier: PrivacyTier): String = when (tier) {
        PrivacyTier.EVERYONE -> "所有人"
        PrivacyTier.CONTACTS -> "联系人"
        PrivacyTier.NOBODY -> "没人"
        PrivacyTier.CUSTOM -> "自定义"
        PrivacyTier.UNKNOWN -> "读取中…"
    }

    /** Read every setting. Partial failures keep the values
     *  that did arrive and say so; a total failure leaves the
     *  map empty and sets [loadError] — never a fake state. */
    fun load() {
        if (loading) return
        loading = true
        _loadError.value = null
        val results = HashMap<String, PrivacyTier>()
        var failed = 0
        var remaining = SETTINGS.size
        for (def in SETTINGS) {
            val query = TdClient.request("getUserPrivacySettingRules").apply {
                put("setting", JSONObject().put("@type", def.type))
            }
            TdRepository.sendRaw(query) { response ->
                if (TdClient.isError(response)) {
                    failed++
                } else {
                    results[def.type] = tierOf(response)
                }
                remaining--
                if (remaining == 0) {
                    loading = false
                    _tiers.value = _tiers.value + results
                    _loadError.value = when {
                        failed == SETTINGS.size ->
                            "隐私设置一条都没读到，检查网络后点重试。"
                        failed > 0 ->
                            "有 $failed 项没读到，可能不准，点重试再读一次。"
                        else -> null
                    }
                }
            }
        }
    }

    /** Collapse a rule list to a tier: AllowAll present =
     *  everyone (individual exceptions may exist on top),
     *  AllowContacts = contacts, RestrictAll with no allow
     *  rules = nobody, anything else (per-user lists) = custom. */
    private fun tierOf(response: JSONObject): PrivacyTier {
        val rules = response.optJSONArray("rules") ?: return PrivacyTier.CUSTOM
        var allowAll = false
        var allowContacts = false
        var restrictAll = false
        var anyAllow = false
        for (i in 0 until rules.length()) {
            when (rules.optJSONObject(i)?.optString("@type")) {
                "userPrivacySettingRuleAllowAll" -> {
                    allowAll = true
                    anyAllow = true
                }
                "userPrivacySettingRuleAllowContacts" -> {
                    allowContacts = true
                    anyAllow = true
                }
                "userPrivacySettingRuleRestrictAll" -> restrictAll = true
                "userPrivacySettingRuleAllowUsers",
                "userPrivacySettingRuleAllowChatMembers",
                "userPrivacySettingRuleAllowBots",
                "userPrivacySettingRuleAllowPremiumUsers" -> anyAllow = true
            }
        }
        return when {
            allowAll -> PrivacyTier.EVERYONE
            allowContacts -> PrivacyTier.CONTACTS
            restrictAll && !anyAllow -> PrivacyTier.NOBODY
            else -> PrivacyTier.CUSTOM
        }
    }

    /** Write one setting as a single-rule tier. Picking a tier
     *  replaces any exception lists the account had — the UI
     *  says so before the user confirms. */
    fun set(def: PrivacySettingDef, tier: PrivacyTier, cb: (String?) -> Unit) {
        val ruleType = when (tier) {
            PrivacyTier.EVERYONE -> "userPrivacySettingRuleAllowAll"
            PrivacyTier.CONTACTS -> "userPrivacySettingRuleAllowContacts"
            PrivacyTier.NOBODY -> "userPrivacySettingRuleRestrictAll"
            else -> {
                cb("这一档设置不了。")
                return
            }
        }
        val query = TdClient.request("setUserPrivacySettingRules").apply {
            put("setting", JSONObject().put("@type", def.type))
            put("rules", JSONObject().apply {
                put("@type", "userPrivacySettingRules")
                put("rules", JSONArray().put(
                    JSONObject().put("@type", ruleType)
                ))
            })
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                val message = response.optString("message")
                cb(
                    if (message.contains("FLOOD_WAIT")) {
                        "操作太频繁了，等一会儿再试。"
                    } else {
                        "保存失败，再试一次。（错误码 ${response.optInt("code")}）"
                    }
                )
            } else {
                _tiers.value = _tiers.value + (def.type to tier)
                cb(null)
            }
        }
    }
}
