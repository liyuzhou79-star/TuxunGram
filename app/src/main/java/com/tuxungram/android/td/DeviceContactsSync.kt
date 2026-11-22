package com.tuxungram.android.td

import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * One-shot sync of the phone's own address book into the
 * Telegram contact list (v0.9.13, T4), over importContacts
 * (verified in td_api.tl). Strictly manual: nothing here
 * runs unless the user taps the sync entry and confirms
 * the explanation dialog — there is no automatic or
 * background sync. The device read happens on a worker
 * thread; the import itself is one TDLib call whose
 * user_ids answer (parallel to the submitted vector, 0 =
 * not a Telegram user) drives the honest result line:
 * how many matched, how many of those were new to the
 * Telegram book, how many were already in it.
 */
object DeviceContactsSync {
    /** The device book can be huge; the import is capped so
     *  one tap cannot fire an unbounded request. The result
     *  line says so when the cap bites. */
    private const val MAX_CONTACTS = 2000

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    /** The permission was refused: stop right there and say
     *  so — nothing was read and nothing was sent. */
    fun notePermissionDenied() {
        _status.value =
            "没拿到通讯录权限，这次没有同步。想同步时再点一次，系统会再问一遍。"
    }

    fun sync(context: Context) {
        if (_busy.value) return
        _busy.value = true
        _status.value = "正在读取手机通讯录…"
        Thread {
            try {
                val device = readDeviceContacts(context)
                if (device.isEmpty()) {
                    _status.value =
                        "手机通讯录里没读到带手机号的联系人，这次没有同步。"
                    _busy.value = false
                    return@Thread
                }
                // Snapshot of the Telegram book BEFORE the
                // import, for the new / already-there split.
                val before = ContactsStore.contacts.value
                    .map { it.userId }.toSet()
                val capped = device.take(MAX_CONTACTS)
                _status.value = "正在和 Telegram 匹配…"
                val arr = JSONArray()
                for (contact in capped) {
                    arr.put(JSONObject().apply {
                        put("phone_number", contact.phone)
                        put("first_name", contact.firstName)
                        put("last_name", contact.lastName)
                    })
                }
                val query = TdClient.request("importContacts").apply {
                    put("contacts", arr)
                }
                TdRepository.sendRaw(query) { response ->
                    _busy.value = false
                    if (TdClient.isError(response)) {
                        _status.value =
                            "同步失败，再试一次。（错误码 ${response.optInt("code")}）"
                        return@sendRaw
                    }
                    val ids = response.optJSONArray("user_ids")
                    var matched = 0
                    var added = 0
                    if (ids != null) {
                        for (i in 0 until ids.length()) {
                            val id = ids.optLong(i)
                            if (id != 0L) {
                                matched++
                                if (id !in before) added++
                            }
                        }
                    }
                    _status.value = buildString {
                        append(
                            "同步完成：手机里 ${device.size} 个带号码的" +
                                "联系人，其中 $matched 个是 Telegram " +
                                "用户，新加了 $added 个，" +
                                "原来就在通讯录里的 ${matched - added} 个。"
                        )
                        if (device.size > capped.size) {
                            append(
                                "（联系人太多，这次先同步了前 " +
                                    "${capped.size} 个。）"
                            )
                        }
                    }
                    if (matched > 0) ContactsStore.refreshAfterImport()
                }
            } catch (t: Throwable) {
                _busy.value = false
                _status.value =
                    "读取手机通讯录时出了问题，这次没有同步。"
            }
        }.start()
    }

    private data class DeviceContact(
        val phone: String,
        val firstName: String,
        val lastName: String
    )

    /** Read every (number, display name) pair from the
     *  device book, de-duplicated by the number's digit
     *  string so the same person with three numbers formats
     *  only collapses when the digits truly match. Numbers
     *  keep a leading + when the book has one; TDLib's own
     *  parser does the rest of the normalisation. */
    private fun readDeviceContacts(context: Context): List<DeviceContact> {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            null, null, null
        ) ?: return emptyList()
        val byDigits = LinkedHashMap<String, DeviceContact>()
        cursor.use { c ->
            val numIdx = c.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val nameIdx = c.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            if (numIdx < 0) return emptyList()
            while (c.moveToNext()) {
                val raw = c.getString(numIdx)?.trim().orEmpty()
                if (raw.isEmpty()) continue
                val digits = raw.filter { it.isDigit() }
                if (digits.length < 5) continue
                if (byDigits.containsKey(digits)) continue
                val normalized =
                    if (raw.startsWith("+")) "+$digits" else digits
                val display = if (nameIdx >= 0) {
                    c.getString(nameIdx)?.trim().orEmpty()
                } else {
                    ""
                }
                val parts = display.split(" ", limit = 2)
                byDigits[digits] = DeviceContact(
                    phone = normalized,
                    firstName = parts.getOrNull(0) ?: "",
                    lastName = parts.getOrNull(1) ?: ""
                )
            }
        }
        return byDigits.values.toList()
    }
}
