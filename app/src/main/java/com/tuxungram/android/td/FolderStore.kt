package com.tuxungram.android.td

import org.json.JSONArray
import org.json.JSONObject

/**
 * Chat folder editing (B7 completion): create / rename /
 * set members / delete. All four functions were verified in
 * td_api.tl (createChatFolder, editChatFolder, deleteChatFolder,
 * getChatFolder). Edits fetch the full folder first and mutate
 * only the name and the included list, so the folder's icon,
 * color and filter flags survive untouched. The folder tabs on
 * the home screen refresh by themselves — TDLib pushes
 * updateChatFolders after every change.
 */
object FolderStore {

    /** The folder's explicitly included chat ids. */
    fun loadIncluded(folderId: Int, onResult: (List<Long>) -> Unit) {
        val query = TdClient.request("getChatFolder").apply {
            put("chat_folder_id", folderId)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                onResult(emptyList())
                return@sendRaw
            }
            val arr = response.optJSONArray("included_chat_ids")
            val out = ArrayList<Long>()
            if (arr != null) {
                for (i in 0 until arr.length()) out.add(arr.optLong(i))
            }
            onResult(out)
        }
    }

    fun create(name: String, included: List<Long>, onDone: (String?) -> Unit) {
        val folder = JSONObject().apply {
            put("@type", "chatFolder")
            put("name", nameJson(name))
            put("is_shareable", false)
            put("pinned_chat_ids", JSONArray())
            put("included_chat_ids", JSONArray(included))
            put("excluded_chat_ids", JSONArray())
            put("exclude_muted", false)
            put("exclude_read", false)
            put("exclude_archived", false)
            put("include_contacts", false)
            put("include_non_contacts", false)
            put("include_bots", false)
            put("include_groups", false)
            put("include_channels", false)
        }
        val query = TdClient.request("createChatFolder").apply {
            put("folder", folder)
        }
        TdRepository.sendRaw(query) { response ->
            onDone(
                if (TdClient.isError(response)) {
                    errorText(response)
                } else {
                    null
                }
            )
        }
    }

    fun save(
        folderId: Int,
        name: String,
        included: List<Long>,
        onDone: (String?) -> Unit
    ) {
        val fetch = TdClient.request("getChatFolder").apply {
            put("chat_folder_id", folderId)
        }
        TdRepository.sendRaw(fetch) { folder ->
            if (TdClient.isError(folder)) {
                onDone(errorText(folder))
                return@sendRaw
            }
            // Mutate only what the editor owns; icon, color and
            // the include/exclude flags ride along unchanged.
            folder.put("name", nameJson(name))
            folder.put("included_chat_ids", JSONArray(included))
            val query = TdClient.request("editChatFolder").apply {
                put("chat_folder_id", folderId)
                put("folder", folder)
            }
            TdRepository.sendRaw(query) { response ->
                onDone(
                    if (TdClient.isError(response)) {
                        errorText(response)
                    } else {
                        null
                    }
                )
            }
        }
    }

    fun delete(folderId: Int, onDone: (String?) -> Unit) {
        val query = TdClient.request("deleteChatFolder").apply {
            put("chat_folder_id", folderId)
        }
        TdRepository.sendRaw(query) { response ->
            onDone(
                if (TdClient.isError(response)) {
                    errorText(response)
                } else {
                    null
                }
            )
        }
    }

    /** Plain-language failure for the folder editor — TDLib's
     *  raw error text never reaches the screen (audit fix A5;
     *  the numeric code stays so a screenshot remains useful). */
    private fun errorText(error: JSONObject): String {
        val message = error.optString("message")
        return when {
            message.contains("FLOOD_WAIT") ->
                "操作太频繁了，等一会儿再试。"
            message.contains("TOO_MANY") ->
                "文件夹太多了，先删掉一个不用的再试。"
            else ->
                "操作失败，再试一次。（错误码 ${error.optInt("code")}）"
        }
    }

    private fun nameJson(name: String): JSONObject =
        JSONObject().apply {
            put("@type", "chatFolderName")
            put(
                "text",
                JSONObject().apply {
                    put("@type", "formattedText")
                    put("text", name)
                    put("entities", JSONArray())
                }
            )
            put("animate_custom_emoji", true)
        }
}
