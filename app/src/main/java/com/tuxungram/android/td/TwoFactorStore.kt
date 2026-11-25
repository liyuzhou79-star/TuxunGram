package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Two-step verification (Telegram cloud password) state and
 * actions — v0.9.13 (T5), verified against td_api.tl:
 *
 *   getPasswordState() = PasswordState  → passwordState{
 *     has_password, password_hint, has_recovery_email_address,
 *     has_passport_data, recovery_email_address_code_info
 *       (non-null while a new recovery email awaits its code),
 *     login_email_address_pattern, pending_reset_date }
 *   setPassword(old_password, new_password, new_hint,
 *     set_recovery_email_address, new_recovery_email_address)
 *     — one call covers enable (old empty), change and
 *     disable (new empty). This app always passes
 *     set_recovery_email_address=false: the recovery email is
 *     managed through its own calls below so the password
 *     flow never tangles with an email-code wait.
 *   setRecoveryEmailAddress(password, new_recovery_email_address)
 *     → sends a confirmation code to the NEW address; the
 *     change lands after checkRecoveryEmailAddressCode(code).
 *   checkRecoveryEmailAddressCode(code) / resendRecoveryEmailAddressCode()
 *   getRecoveryEmailAddress(password) → recoveryEmailAddress{
 *     recovery_email_address } — reveals the current address
 *     after the user re-enters the password.
 *
 * Passwords exist only inside the calling dialog's state for
 * the duration of one request. Nothing here stores, logs or
 * echoes a password; the store keeps only the public state
 * (flags, hint, email pattern) the server itself returns.
 */
data class TwoFactorUi(
    val hasPassword: Boolean = false,
    val hint: String = "",
    val hasRecoveryEmail: Boolean = false,
    val recoveryEmailPending: Boolean = false,
    val recoveryEmailPattern: String = "",
    val loginEmailPattern: String = ""
)

object TwoFactorStore {

    private val _state = MutableStateFlow<TwoFactorUi?>(null)
    val state: StateFlow<TwoFactorUi?> = _state.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** One request through the repository's raw channel;
     *  the callback receives the response object, which is
     *  the error object itself when the server refused. */
    private fun send(
        query: JSONObject,
        cb: (JSONObject?) -> Unit
    ) {
        TdRepository.sendRaw(query) { cb(it) }
    }

    fun load() {
        send(
            JSONObject()
                .put("@type", "getPasswordState")
        ) { result ->
            if (result == null ||
                result.optString("@type") == "error"
            ) {
                _notice.value = "两步验证状态暂时读不到" +
                    "（错误码 ${result?.optInt("code") ?: 0}）。"
                return@send
            }
            applyState(result)
        }
    }

    private fun applyState(result: JSONObject) {
        val codeInfo = result.optJSONObject(
            "recovery_email_address_code_info"
        )
        _state.value = TwoFactorUi(
            hasPassword = result.optBoolean("has_password"),
            hint = result.optString("password_hint"),
            hasRecoveryEmail = result.optBoolean(
                "has_recovery_email_address"
            ),
            recoveryEmailPending = codeInfo != null,
            recoveryEmailPattern = codeInfo
                ?.optString("email_address_pattern")
                .orEmpty(),
            loginEmailPattern = result.optString(
                "login_email_address_pattern"
            )
        )
    }

    /** Enable (oldPw empty) or change the password. */
    fun setPassword(
        oldPw: String,
        newPw: String,
        hint: String,
        onDone: (Boolean, String) -> Unit
    ) {
        send(
            JSONObject()
                .put("@type", "setPassword")
                .put("old_password", oldPw)
                .put("new_password", newPw)
                .put("new_hint", hint)
                .put("set_recovery_email_address", false)
                .put("new_recovery_email_address", "")
        ) { result ->
            if (result == null ||
                result.optString("@type") == "error"
            ) {
                onDone(false, errorText(result, "改密码"))
                return@send
            }
            applyState(result)
            onDone(true, "两步验证密码已保存。")
        }
    }

    /** Disable: same call with an empty new password. */
    fun disablePassword(
        oldPw: String,
        onDone: (Boolean, String) -> Unit
    ) {
        send(
            JSONObject()
                .put("@type", "setPassword")
                .put("old_password", oldPw)
                .put("new_password", "")
                .put("new_hint", "")
                .put("set_recovery_email_address", false)
                .put("new_recovery_email_address", "")
        ) { result ->
            if (result == null ||
                result.optString("@type") == "error"
            ) {
                onDone(false, errorText(result, "关密码"))
                return@send
            }
            applyState(result)
            onDone(true, "两步验证已关闭。")
        }
    }

    /** Start a recovery-email change: the server mails a
     *  confirmation code to the new address. */
    fun setRecoveryEmail(
        password: String,
        newEmail: String,
        onDone: (Boolean, String) -> Unit
    ) {
        send(
            JSONObject()
                .put("@type", "setRecoveryEmailAddress")
                .put("password", password)
                .put("new_recovery_email_address", newEmail)
        ) { result ->
            if (result == null ||
                result.optString("@type") == "error"
            ) {
                onDone(false, errorText(result, "设恢复邮箱"))
                return@send
            }
            applyState(result)
            onDone(
                true,
                "验证码已经发到新邮箱了，把邮箱里收到的" +
                    "验证码填进来确认。"
            )
        }
    }

    fun confirmRecoveryEmailCode(
        code: String,
        onDone: (Boolean, String) -> Unit
    ) {
        send(
            JSONObject()
                .put("@type", "checkRecoveryEmailAddressCode")
                .put("code", code)
        ) { result ->
            if (result == null ||
                result.optString("@type") == "error"
            ) {
                onDone(false, errorText(result, "确认验证码"))
                return@send
            }
            applyState(result)
            onDone(true, "恢复邮箱已设置好。")
        }
    }

    fun resendRecoveryEmailCode(
        onDone: (Boolean, String) -> Unit
    ) {
        send(
            JSONObject()
                .put("@type", "resendRecoveryEmailAddressCode")
        ) { result ->
            if (result == null ||
                result.optString("@type") == "error"
            ) {
                onDone(false, errorText(result, "重发验证码"))
                return@send
            }
            applyState(result)
            onDone(true, "验证码已重新发送，去邮箱里看。")
        }
    }

    /** Reveal the current recovery email after the user
     *  re-enters the password. The address goes only to the
     *  dialog that asked — never stored here. */
    fun revealRecoveryEmail(
        password: String,
        onDone: (Boolean, String) -> Unit
    ) {
        send(
            JSONObject()
                .put("@type", "getRecoveryEmailAddress")
                .put("password", password)
        ) { result ->
            if (result == null ||
                result.optString("@type") == "error"
            ) {
                onDone(false, errorText(result, "看恢复邮箱"))
                return@send
            }
            onDone(
                true,
                result.optString("recovery_email_address")
            )
        }
    }

    private fun errorText(
        result: JSONObject?,
        what: String
    ): String {
        val raw = result?.optString("message").orEmpty()
        val upper = raw.uppercase()
        return when {
            upper.contains("PASSWORD") &&
                upper.contains("INVALID") ->
                "现在的密码不对，再核对一下。"
            upper.contains("PASSWORD_HASH_INVALID") ->
                "现在的密码不对，再核对一下。"
            upper.contains("EMAIL") &&
                (upper.contains("INVALID") ||
                    upper.contains("ADDRESS")) ->
                "这个邮箱地址服务器不认，检查一下格式。"
            upper.contains("CODE") &&
                upper.contains("INVALID") ->
                "验证码不对，再核对一下邮箱里收到的。"
            upper.contains("FLOOD") ->
                "试得太频繁了，服务器让先等一会儿再试。"
            else ->
                // Plain language only (v0.9.60, E3): the raw
                // server sentence used to be quoted verbatim
                // after this — it stays out of the UI now;
                // the numeric code remains for support.
                "$what 没成，再试一次。（错误码 " +
                    "${result?.optInt("code") ?: 0}）"
        }
    }
}
