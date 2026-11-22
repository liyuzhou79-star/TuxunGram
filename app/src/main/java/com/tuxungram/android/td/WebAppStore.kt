package com.tuxungram.android.td

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.tuxungram.android.ui.OfficialPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** One live Web App session, ready for the WebView page.
 *  [url] is the URL TDLib returned, with the fragment
 *  completed by us (version / platform / theme params —
 *  see completeUrl). [launchId] is openWebApp's launch
 *  identifier for inline-button launches, 0 for keyboard
 *  launches (getWebAppUrl returns no launch id; those
 *  apps report data back through sendWebAppData instead). */
data class WebAppSession(
    val botUserId: Long,
    val botName: String,
    val url: String,
    val launchId: Long,
    val fromKeyboardButton: Boolean,
    val buttonText: String,
    val requireSameOrigin: Boolean,
    val themeParamsJson: String
)

/**
 * Bot Web Apps (v0.9.36, C1, basic version). The TDLib
 * chain, verified name by name against this repo's
 * td_api.tl:
 *
 * - inline button (inlineKeyboardButtonTypeWebApp):
 *   openWebApp(chat_id, bot_user_id, url, parameters)
 *   returns webAppInfo{launch_id, url:webAppUrl}; when the
 *   app is closed the client must call closeWebApp(
 *   launch_id), and updateWebAppMessageSent with the same
 *   launch id means "the app sent its message, close it".
 * - keyboard button (keyboardButtonTypeWebApp):
 *   getWebAppUrl(bot_user_id, url, parameters) returns
 *   webAppUrl{url, require_same_origin}; data the page
 *   sends back goes out via sendWebAppData(bot_user_id,
 *   button_text, data).
 * - webAppOpenParameters{theme:themeParameters,
 *   application_name, mode} carries our theme into both
 *   calls; getThemeParametersJsonString turns the same
 *   themeParameters into the JSON the page's
 *   tgWebAppThemeParams fragment field and theme_changed
 *   events expect.
 *
 * The initData itself (tgWebAppData in the fragment) is
 * minted by the server and rides inside the URL TDLib
 * returns; this store passes it through untouched and
 * never fabricates it. If a returned URL ever lacked it,
 * the page would simply run unauthenticated — recorded
 * here so nobody "fixes" it by inventing a hash.
 *
 * Only button entry points open a Web App: chat text
 * links never reach this store (the link router treats
 * them as ordinary links), per the version's safety
 * scope.
 */
object WebAppStore {

    private val _session = MutableStateFlow<WebAppSession?>(null)
    val session: StateFlow<WebAppSession?> = _session

    @Volatile
    private var attached = false

    private fun attach() {
        if (attached) return
        attached = true
        TdRepository.addUpdateListener { update ->
            if (update.optString("@type") ==
                "updateWebAppMessageSent"
            ) {
                val launchId = update.optLong("web_app_launch_id")
                val current = _session.value
                if (current != null && launchId != 0L &&
                    current.launchId == launchId
                ) {
                    close()
                }
            }
        }
    }

    /** themeParameters for [palette], field by field from
     *  the tl type: every value is an RGB int. */
    fun themeParameters(palette: OfficialPalette): JSONObject {
        fun rgb(color: Color): Int = color.toArgb() and 0xFFFFFF
        return JSONObject().apply {
            put("@type", "themeParameters")
            put("background_color", rgb(palette.windowBg))
            put("secondary_background_color", rgb(palette.graySection))
            put("header_background_color", rgb(palette.actionBarBg))
            put("bottom_bar_background_color", rgb(palette.panelBg))
            put("section_background_color", rgb(palette.panelBg))
            put("section_separator_color", rgb(palette.divider))
            put("text_color", rgb(palette.textPrimary))
            put("accent_text_color", rgb(palette.accentText))
            put(
                "section_header_text_color",
                rgb(palette.graySectionText)
            )
            put("subtitle_text_color", rgb(palette.textSecondary))
            put("destructive_text_color", rgb(palette.draftRed))
            put("hint_color", rgb(palette.textSecondary))
            put("link_color", rgb(palette.accent))
            put("button_color", rgb(palette.accent))
            put("button_text_color", rgb(Color.White))
        }
    }

    private fun openParameters(theme: JSONObject): JSONObject =
        JSONObject().apply {
            put("@type", "webAppOpenParameters")
            put("theme", theme)
            put("application_name", "TuxunGram")
            put(
                "mode",
                JSONObject().put("@type", "webAppOpenModeFullSize")
            )
        }

    /** Append the fragment fields the page's
     *  telegram-web-app.js needs when TDLib's URL does not
     *  already carry them. Existing fields are never
     *  overwritten — tgWebAppData above all. */
    private fun completeUrl(raw: String, themeJson: String): String {
        val hashIdx = raw.indexOf('#')
        val base = if (hashIdx >= 0) raw.substring(0, hashIdx) else raw
        val frag = if (hashIdx >= 0) raw.substring(hashIdx + 1) else ""
        val params = frag.split('&')
            .filter { it.isNotEmpty() }
            .toMutableList()
        fun has(key: String) = params.any { it.startsWith("$key=") }
        if (!has("tgWebAppVersion")) {
            params.add("tgWebAppVersion=8.0")
        }
        if (!has("tgWebAppPlatform")) {
            params.add("tgWebAppPlatform=android")
        }
        if (!has("tgWebAppThemeParams")) {
            params.add("tgWebAppThemeParams=" + Uri.encode(themeJson))
        }
        return base + "#" + params.joinToString("&")
    }

    private fun errorText(response: JSONObject): String {
        val message = response.optString("message")
        return when {
            message.contains("BOT_RESTRICTED", true) ||
                message.contains("restricted", true) ->
                "这个机器人当前不可用，暂时打不开它的小程序。"
            message.contains("CHAT", true) &&
                message.contains("SECRET", true) ->
                "秘密聊天里不能打开小程序。"
            else ->
                "小程序打开失败，再试一次。（错误码 " +
                    "${response.optInt("code")}）" +
                    if (message.isNotEmpty()) " $message" else ""
        }
    }

    /** Shared tail of both launch paths: turn the theme
     *  into its JSON string, then publish the session with
     *  the completed URL. */
    private fun publish(
        botUserId: Long,
        botName: String,
        rawUrl: String,
        launchId: Long,
        fromKeyboardButton: Boolean,
        buttonText: String,
        requireSameOrigin: Boolean,
        theme: JSONObject,
        onError: (String) -> Unit
    ) {
        val query = TdClient.request("getThemeParametersJsonString")
            .apply { put("theme", theme) }
        TdRepository.sendRaw(query) { response ->
            val themeJson = if (TdClient.isError(response)) {
                "{}"
            } else {
                response.optString("text").ifEmpty { "{}" }
            }
            _session.value = WebAppSession(
                botUserId = botUserId,
                botName = botName,
                url = completeUrl(rawUrl, themeJson),
                launchId = launchId,
                fromKeyboardButton = fromKeyboardButton,
                buttonText = buttonText,
                requireSameOrigin = requireSameOrigin,
                themeParamsJson = themeJson
            )
        }
    }

    /** Inline-button launch: openWebApp in the chat the
     *  button was pressed in. [botUserId] is the message's
     *  sender — a Web App button belongs to the bot that
     *  sent the message. */
    fun openFromInlineButton(
        chatId: Long,
        botUserId: Long,
        botName: String,
        url: String,
        palette: OfficialPalette,
        onError: (String) -> Unit
    ) {
        attach()
        if (botUserId == 0L) {
            onError("没认出这个按钮属于哪个机器人，打不开。")
            return
        }
        val theme = themeParameters(palette)
        val query = TdClient.request("openWebApp").apply {
            put("chat_id", chatId)
            put("bot_user_id", botUserId)
            put("url", url)
            put("topic_id", JSONObject.NULL)
            put("reply_to", JSONObject.NULL)
            put("parameters", openParameters(theme))
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                onError(errorText(response))
                return@sendRaw
            }
            val urlObj = response.optJSONObject("url")
            val rawUrl = urlObj?.optString("url") ?: ""
            if (rawUrl.isEmpty()) {
                onError("机器人没有返回小程序地址，打不开。")
                return@sendRaw
            }
            publish(
                botUserId = botUserId,
                botName = botName,
                rawUrl = rawUrl,
                launchId = response.optLong("launch_id"),
                fromKeyboardButton = false,
                buttonText = "",
                requireSameOrigin =
                    urlObj.optBoolean("require_same_origin"),
                theme = theme,
                onError = onError
            )
        }
    }

    /** Keyboard-button launch: getWebAppUrl with the
     *  button's URL; [buttonText] is remembered because
     *  sendWebAppData needs it when the page answers. */
    fun openFromKeyboardButton(
        botUserId: Long,
        botName: String,
        url: String,
        buttonText: String,
        palette: OfficialPalette,
        onError: (String) -> Unit
    ) {
        attach()
        if (botUserId == 0L) {
            onError("没认出这个按钮属于哪个机器人，打不开。")
            return
        }
        val theme = themeParameters(palette)
        val query = TdClient.request("getWebAppUrl").apply {
            put("bot_user_id", botUserId)
            put("url", url)
            put("parameters", openParameters(theme))
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                onError(errorText(response))
                return@sendRaw
            }
            val rawUrl = response.optString("url")
            if (rawUrl.isEmpty()) {
                onError("机器人没有返回小程序地址，打不开。")
                return@sendRaw
            }
            publish(
                botUserId = botUserId,
                botName = botName,
                rawUrl = rawUrl,
                launchId = 0L,
                fromKeyboardButton = true,
                buttonText = buttonText,
                requireSameOrigin =
                    response.optBoolean("require_same_origin"),
                theme = theme,
                onError = onError
            )
        }
    }

    /** Data a keyboard-launched page sends back
     *  (Telegram.WebApp.sendData): forwarded to the bot
     *  as a service message. Inline launches have no such
     *  channel in the Bot API — the page's own script is
     *  expected not to call it there. */
    fun sendData(session: WebAppSession, data: String) {
        if (!session.fromKeyboardButton) return
        val query = TdClient.request("sendWebAppData").apply {
            put("bot_user_id", session.botUserId)
            put("button_text", session.buttonText)
            put("data", data)
        }
        TdRepository.sendRaw(query) {}
    }

    /** web_app_request_write_access: ask TDLib to grant
     *  the bot permission to message the user, then the
     *  screen reports the outcome back to the page. */
    fun requestWriteAccess(
        session: WebAppSession,
        onResult: (Boolean) -> Unit
    ) {
        val query = TdClient.request("allowBotToSendMessages").apply {
            put("bot_user_id", session.botUserId)
        }
        TdRepository.sendRaw(query) { response ->
            onResult(!TdClient.isError(response))
        }
    }

    /** web_app_invoke_custom_method: relayed to
     *  sendWebAppCustomRequest; the result string (or the
     *  error) goes back to the page verbatim. */
    fun invokeCustomMethod(
        session: WebAppSession,
        method: String,
        params: String,
        onResult: (result: String?, error: String?) -> Unit
    ) {
        val query = TdClient.request("sendWebAppCustomRequest").apply {
            put("bot_user_id", session.botUserId)
            put("method", method)
            put("parameters", params)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                onResult(null, response.optString("message"))
            } else {
                onResult(response.optString("result"), null)
            }
        }
    }

    /** Close the live session, if any. Inline launches
     *  report the close to TDLib (closeWebApp); keyboard
     *  launches have no launch id and nothing to report. */
    fun close() {
        val current = _session.value ?: return
        _session.value = null
        if (current.launchId != 0L) {
            val query = TdClient.request("closeWebApp").apply {
                put("web_app_launch_id", current.launchId)
            }
            TdRepository.sendRaw(query) {}
        }
    }
}
