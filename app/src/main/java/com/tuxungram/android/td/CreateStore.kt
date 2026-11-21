package com.tuxungram.android.td

import org.json.JSONArray
import org.json.JSONObject

/**
 * Create a group or a channel (Batch 2, B6). Signatures verified
 * in td_api.tl: createNewBasicGroupChat returns a
 * createdBasicGroupChat (chat_id inside); createNewSupergroupChat
 * returns the chat itself, with is_channel distinguishing
 * channels from supergroups.
 */
object CreateStore {

    fun createGroup(
        title: String,
        userIds: List<Long>,
        onResult: (chatId: Long?, error: String?) -> Unit
    ) {
        val ids = JSONArray()
        for (id in userIds) ids.put(id)
        val query = TdClient.request("createNewBasicGroupChat").apply {
            put("user_ids", ids)
            put("title", title)
            put("message_auto_delete_time", 0)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                onResult(null, errorText(response))
            } else {
                onResult(
                    response.optLong("chat_id").takeIf { it != 0L },
                    null
                )
            }
        }
    }

    fun createSupergroupOrChannel(
        title: String,
        description: String,
        isChannel: Boolean,
        onResult: (chatId: Long?, error: String?) -> Unit
    ) {
        val query = TdClient.request("createNewSupergroupChat").apply {
            put("title", title)
            put("is_forum", false)
            put("is_channel", isChannel)
            put("description", description)
            put("location", JSONObject.NULL)
            put("message_auto_delete_time", 0)
            put("for_import", false)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                onResult(null, errorText(response))
            } else {
                onResult(
                    response.optLong("id").takeIf { it != 0L },
                    null
                )
            }
        }
    }

    private fun errorText(error: JSONObject): String {
        val message = error.optString("message")
        return when {
            message.contains("FLOOD_WAIT") -> "操作太频繁了，等一会儿再试。"
            message.contains("USER_RESTRICTED") ||
                message.contains("PEER_FLOOD") ->
                "账号暂时被限制创建，先歇一会儿。"
            else -> "创建失败，再试一次。（错误码 ${error.optInt("code")}）"
        }
    }
}
