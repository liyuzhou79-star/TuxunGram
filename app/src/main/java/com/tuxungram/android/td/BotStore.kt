package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** One bot command as shown in the slash popup (v0.9.9, W2):
 *  the bare command (no leading slash) and its description,
 *  from botInfo.commands / botCommands (td_api.tl lines 858 /
 *  861, verified). */
data class BotCommandUi(val command: String, val description: String)

/** A bot present in a group, with its username (needed for
 *  the /command@botname form) and its command list, from
 *  basicGroupFullInfo.bot_commands / supergroupFullInfo
 *  .bot_commands. */
data class GroupBotUi(
    val userId: Long,
    val username: String,
    val commands: List<BotCommandUi>
)

/** Bot surface of the currently inspected chat. For a private
 *  chat with a bot: isBot + the bot's own commands. For a
 *  group: every bot's commands in groupBots. */
data class BotUi(
    val chatId: Long,
    val isBot: Boolean = false,
    val botUserId: Long = 0L,
    val username: String = "",
    val commands: List<BotCommandUi> = emptyList(),
    val groupBots: List<GroupBotUi> = emptyList()
)

/**
 * Bot adaptation data (v0.9.9, W2): slash-command lists for
 * the chat screen. Private bot chats read userFullInfo
 * .bot_info.commands (getUserFullInfo, td_api.tl botInfo at
 * line 2931 / bot_info field on userFullInfo); groups read
 * the bot_commands arrays of the group full info. One chat
 * is inspected at a time (the open one); ensure() is a no-op
 * when the state already covers that chat.
 */
object BotStore {

    private val _state = MutableStateFlow<BotUi?>(null)
    val state: StateFlow<BotUi?> = _state

    @Volatile
    private var loadingChat = 0L

    fun ensure(chatId: Long) {
        if (chatId == 0L) return
        if (_state.value?.chatId == chatId) return
        if (loadingChat == chatId) return
        loadingChat = chatId
        val query = TdClient.request("getChat").apply {
            put("chat_id", chatId)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                loadingChat = 0L
                return@sendRaw
            }
            val type = response.optJSONObject("type")
            when (type?.optString("@type")) {
                "chatTypePrivate" -> loadPrivateBot(
                    chatId, type.optLong("user_id")
                )
                "chatTypeBasicGroup" -> loadGroupBots(
                    chatId,
                    TdClient.request("getBasicGroupFullInfo").apply {
                        put("basic_group_id", type.optLong("basic_group_id"))
                    }
                )
                "chatTypeSupergroup" -> {
                    if (type.optBoolean("is_channel")) {
                        publish(BotUi(chatId = chatId))
                    } else {
                        loadGroupBots(
                            chatId,
                            TdClient.request("getSupergroupFullInfo").apply {
                                put("supergroup_id", type.optLong("supergroup_id"))
                            }
                        )
                    }
                }
                else -> publish(BotUi(chatId = chatId))
            }
        }
    }

    private fun publish(ui: BotUi) {
        loadingChat = 0L
        _state.value = ui
    }

    private fun loadPrivateBot(chatId: Long, userId: Long) {
        if (userId == 0L) {
            publish(BotUi(chatId = chatId))
            return
        }
        val query = TdClient.request("getUser").apply {
            put("user_id", userId)
        }
        TdRepository.sendRaw(query) { user ->
            if (TdClient.isError(user) ||
                user.optJSONObject("type")?.optString("@type") != "userTypeBot"
            ) {
                publish(BotUi(chatId = chatId))
                return@sendRaw
            }
            val username = user.optJSONObject("usernames")
                ?.optJSONArray("active_usernames")?.optString(0) ?: ""
            val full = TdClient.request("getUserFullInfo").apply {
                put("user_id", userId)
            }
            TdRepository.sendRaw(full) { info ->
                val commands = if (TdClient.isError(info)) {
                    emptyList()
                } else {
                    parseCommands(
                        info.optJSONObject("bot_info")
                            ?.optJSONArray("commands")
                    )
                }
                publish(
                    BotUi(
                        chatId = chatId,
                        isBot = true,
                        botUserId = userId,
                        username = username,
                        commands = commands
                    )
                )
            }
        }
    }

    private fun loadGroupBots(chatId: Long, fullInfoQuery: JSONObject) {
        TdRepository.sendRaw(fullInfoQuery) { info ->
            if (TdClient.isError(info)) {
                publish(BotUi(chatId = chatId))
                return@sendRaw
            }
            val sets = info.optJSONArray("bot_commands")
            if (sets == null || sets.length() == 0) {
                publish(BotUi(chatId = chatId))
                return@sendRaw
            }
            val bots = ArrayList<GroupBotUi>()
            var pending = 0
            for (i in 0 until sets.length()) {
                val set = sets.optJSONObject(i) ?: continue
                val botId = set.optLong("bot_user_id")
                val commands = parseCommands(set.optJSONArray("commands"))
                if (botId == 0L) continue
                pending++
                val query = TdClient.request("getUser").apply {
                    put("user_id", botId)
                }
                TdRepository.sendRaw(query) { user ->
                    val username = if (TdClient.isError(user)) {
                        ""
                    } else {
                        user.optJSONObject("usernames")
                            ?.optJSONArray("active_usernames")
                            ?.optString(0) ?: ""
                    }
                    val done = synchronized(bots) {
                        bots.add(GroupBotUi(botId, username, commands))
                        pending--
                        pending == 0
                    }
                    if (done) {
                        publish(BotUi(chatId = chatId, groupBots = bots))
                    }
                }
            }
            if (pending == 0) publish(BotUi(chatId = chatId))
        }
    }

    private fun parseCommands(arr: JSONArray?): List<BotCommandUi> {
        if (arr == null) return emptyList()
        val out = ArrayList<BotCommandUi>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val command = item.optString("command")
            if (command.isEmpty()) continue
            out.add(BotCommandUi(command, item.optString("description")))
        }
        return out
    }
}
