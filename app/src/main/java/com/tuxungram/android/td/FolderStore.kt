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
                    response.optString("message")
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
                onDone(folder.optString("message"))
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
                        response.optString("message")
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
                    response.optString("message")
                } else {
                    null
                }
            )
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
