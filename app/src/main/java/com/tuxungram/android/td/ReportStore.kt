package com.tuxungram.android.td

import org.json.JSONArray
import org.json.JSONObject

/** One selectable reason in the report flow. [id] is the
 *  reportOption.id exactly as TDLib's JSON interface ships
 *  it — bytes fields travel as base64 strings and are
 *  passed back to reportChat verbatim. */
data class ReportOptionUi(val id: String, val text: String)

/** The server's answer to one reportChat step, flattened
 *  so the UI can drive the multi-step flow without touching
 *  JSON (reportChat / reportChatResult* verified in
 *  td_api.tl: the initial call carries an empty option_id
 *  and no message ids; the result then says whether the
 *  report is complete, needs a reason option, needs extra
 *  text, or needs the user to pick messages). */
sealed class ReportStep {
    data object Done : ReportStep()
    data class NeedOption(val options: List<ReportOptionUi>) : ReportStep()
    data class NeedText(
        val optionId: String,
        val optional: Boolean
    ) : ReportStep()
    data object NeedMessages : ReportStep()
    data class Failed(val message: String) : ReportStep()
}

/**
 * Chat reporting + the two other stranger-bar primitives
 * (v0.9.54, Y1). reportChat is the only reporting entry in
 * this td_api.tl; blocking lives in BlockStore
 * (setMessageSenderBlockList), adding a contact is
 * addContact with an importedContact, and sharing my own
 * number with a mutual contact is sharePhoneNumber — all
 * names verified in td_api.tl before use.
 */
object ReportStore {

    private fun plainError(error: JSONObject, what: String): String {
        val message = error.optString("message")
        return if (message.contains("FLOOD_WAIT")) {
            "操作太频繁了，等一会儿再试。"
        } else {
            "$what 失败，再试一次。（错误码 ${error.optInt("code")}）"
        }
    }

    /** One reportChat step. [optionId] is empty for the
     *  initial request, then the chosen reportOption.id (or
     *  the option_id a TextRequired answer handed back). */
    fun reportChat(
        chatId: Long,
        optionId: String = "",
        messageIds: List<Long> = emptyList(),
        text: String = "",
        cb: (ReportStep) -> Unit
    ) {
        val query = TdClient.request("reportChat").apply {
            put("chat_id", chatId)
            put("option_id", optionId)
            val ids = JSONArray()
            for (id in messageIds) ids.put(id)
            put("message_ids", ids)
            put("text", text)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                cb(ReportStep.Failed(plainError(response, "举报")))
                return@sendRaw
            }
            when (response.optString("@type")) {
                "reportChatResultOk" -> cb(ReportStep.Done)
                "reportChatResultOptionRequired" -> {
                    val options = ArrayList<ReportOptionUi>()
                    val arr = response.optJSONArray("options")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            options.add(
                                ReportOptionUi(
                                    id = o.optString("id"),
                                    text = o.optString("text")
                                )
                            )
                        }
                    }
                    if (options.isEmpty()) {
                        cb(ReportStep.Done)
                    } else {
                        cb(ReportStep.NeedOption(options))
                    }
                }
                "reportChatResultTextRequired" -> cb(
                    ReportStep.NeedText(
                        optionId = response.optString("option_id"),
                        optional = response.optBoolean("is_optional")
                    )
                )
                "reportChatResultMessagesRequired" ->
                    cb(ReportStep.NeedMessages)
                else -> cb(ReportStep.Done)
            }
        }
    }

    /** addContact (td_api.tl): the contact is an
     *  importedContact; [sharePhone] maps to
     *  addContact.share_phone_number — the action bar adds
     *  without sharing, like the official client. */
    fun addContact(
        userId: Long,
        phone: String,
        firstName: String,
        lastName: String,
        cb: (String?) -> Unit
    ) {
        val query = TdClient.request("addContact").apply {
            put("user_id", userId)
            put("contact", JSONObject().apply {
                put("@type", "importedContact")
                put("phone_number", phone)
                put("first_name", firstName)
                put("last_name", lastName)
                put("note", JSONObject().apply {
                    put("@type", "formattedText")
                    put("text", "")
                    put("entities", JSONArray())
                })
            })
            put("share_phone_number", false)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                cb(plainError(response, "添加联系人"))
            } else {
                cb(null)
            }
        }
    }

    /** sharePhoneNumber (td_api.tl): shares my number with
     *  this one user — the chatActionBarSharePhoneNumber
     *  action. */
    fun sharePhoneNumber(userId: Long, cb: (String?) -> Unit) {
        val query = TdClient.request("sharePhoneNumber").apply {
            put("user_id", userId)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                cb(plainError(response, "分享手机号"))
            } else {
                cb(null)
            }
        }
    }

    /** Name + phone prefill for the add-contact form: the
     *  user object carries the phone only when the server
     *  hands it out (contacts / privacy), so the form may
     *  legitimately open with an empty phone field. */
    fun fetchUserBasics(
        userId: Long,
        cb: (firstName: String, lastName: String, phone: String) -> Unit
    ) {
        val query = TdClient.request("getUser").apply {
            put("user_id", userId)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                cb("", "", "")
            } else {
                cb(
                    response.optString("first_name"),
                    response.optString("last_name"),
                    response.optString("phone_number")
                )
            }
        }
    }
}
