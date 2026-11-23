package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** Chat kinds the attachment-menu filter distinguishes
 *  (v0.9.38, E4): they map one-to-one onto the
 *  attachmentMenuBot supports_*_chats flags. */
const val ATTACH_CHAT_USER = 0
const val ATTACH_CHAT_BOT = 1
const val ATTACH_CHAT_GROUP = 2
const val ATTACH_CHAT_CHANNEL = 3

/** One bot from the attachment-menu list (v0.9.38, E4),
 *  flattened from a tl attachmentMenuBot object. */
data class AttachBotUi(
    val botUserId: Long,
    val name: String,
    val isAdded: Boolean,
    val showInAttachmentMenu: Boolean,
    val supportsUserChats: Boolean,
    val supportsBotChats: Boolean,
    val supportsGroupChats: Boolean,
    val supportsChannelChats: Boolean,
    val requestWriteAccess: Boolean
)

/**
 * Attachment-menu bots (v0.9.38, E4) — the last Web App
 * entry point 0.9.37 left unconnected.
 *
 * Data source, verified in this repo's td_api.tl: there
 * is NO getter for the list; TDLib pushes it as
 * updateAttachmentMenuBots (the full list, replaced
 * wholesale on every update) once the client is
 * authorized. getAttachmentMenuBot answers for one bot
 * (used by link flows, not needed here), and
 * toggleBotIsAddedToAttachmentMenu adds/removes one.
 * Opening goes through openWebApp with an empty URL —
 * the tl names the attachment menu as one of its four
 * launch sources — via WebAppStore's shared chain.
 *
 * Add-before-first-launch: the tl's own guidance for
 * attachment-menu bots is that a bot the user has not
 * added yet must be confirmed and added first
 * (is_added=false → toggleBotIsAddedToAttachmentMenu),
 * so the chat UI asks once and only then opens.
 */
object AttachBotsStore {
    private val _bots = MutableStateFlow<List<AttachBotUi>>(emptyList())
    val bots: StateFlow<List<AttachBotUi>> = _bots

    @Volatile
    private var attached = false

    fun attach() {
        if (attached) return
        attached = true
        TdRepository.addUpdateListener { update ->
            if (update.optString("@type") !=
                "updateAttachmentMenuBots"
            ) {
                return@addUpdateListener
            }
            val arr = update.optJSONArray("bots") ?: return@addUpdateListener
            val parsed = ArrayList<AttachBotUi>()
            for (i in 0 until arr.length()) {
                val bot = arr.optJSONObject(i) ?: continue
                val botUserId = bot.optLong("bot_user_id")
                if (botUserId == 0L) continue
                parsed.add(
                    AttachBotUi(
                        botUserId = botUserId,
                        name = bot.optString("name"),
                        isAdded = bot.optBoolean("is_added"),
                        showInAttachmentMenu =
                            bot.optBoolean("show_in_attachment_menu"),
                        supportsUserChats =
                            bot.optBoolean("supports_user_chats"),
                        supportsBotChats =
                            bot.optBoolean("supports_bot_chats"),
                        supportsGroupChats =
                            bot.optBoolean("supports_group_chats"),
                        supportsChannelChats =
                            bot.optBoolean("supports_channel_chats"),
                        requestWriteAccess =
                            bot.optBoolean("request_write_access")
                    )
                )
            }
            _bots.value = parsed
        }
    }

    /** Whether this bot supports chats of [chatKind]
     *  (its own supports_*_chats flags). */
    fun AttachBotUi.supportsChatKind(chatKind: Int): Boolean =
        when (chatKind) {
            ATTACH_CHAT_USER -> supportsUserChats
            ATTACH_CHAT_BOT -> supportsBotChats
            ATTACH_CHAT_GROUP -> supportsGroupChats
            ATTACH_CHAT_CHANNEL -> supportsChannelChats
            else -> false
        }

    /** The bots the attachment menu of a chat of
     *  [chatKind] may offer: flagged for the attachment
     *  menu by the bot itself and supporting this kind
     *  of chat. */
    fun botsFor(chatKind: Int): List<AttachBotUi> =
        _bots.value.filter { bot ->
            bot.showInAttachmentMenu && bot.supportsChatKind(chatKind)
        }

    /** Add a bot to the attachment menu
     *  (toggleBotIsAddedToAttachmentMenu). The update
     *  that follows refreshes is_added on its own; the
     *  local copy is patched immediately so a second tap
     *  does not ask again while the update is in flight. */
    fun addToMenu(bot: AttachBotUi, onDone: (String?) -> Unit) {
        val query = TdClient.request(
            "toggleBotIsAddedToAttachmentMenu"
        ).apply {
            put("bot_user_id", bot.botUserId)
            put("is_added", true)
            put("allow_write_access", bot.requestWriteAccess)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                onDone(
                    "没加上，再试一次。（错误码 " +
                        "${response.optInt("code")}）"
                )
            } else {
                _bots.value = _bots.value.map {
                    if (it.botUserId == bot.botUserId) {
                        it.copy(isAdded = true)
                    } else {
                        it
                    }
                }
                onDone(null)
            }
        }
    }
}
