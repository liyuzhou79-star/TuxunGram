package com.tuxungram.android.td

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/** An invite-link preview waiting for the user's decision
 *  (v0.9.9, W3): filled by checkChatInviteLink, shown as a
 *  dialog by the host, joined through joinChatByInviteLink. */
data class InvitePreview(
    val link: String,
    val title: String,
    val memberCount: Int,
    val description: String,
    val photoPath: String?,
    val createsJoinRequest: Boolean
)

/**
 * Telegram link router (v0.9.9, W3): every URL tap in the
 * app — rich text, link previews, profile links, the shared
 * media link tab — goes through here first. Links on the
 * Telegram hosts (t.me / telegram.me / telegram.dog) and
 * tg:// links resolve INSIDE the app; anything else falls
 * back to the system browser exactly as before.
 *
 * Routes (all functions verified in td_api.tl):
 *  - t.me/<username>            -> searchPublicChat, open chat
 *  - t.me/<username>/<msgId>    -> open + jump to the message
 *  - t.me/+<hash>, t.me/joinchat/<hash>
 *      -> checkChatInviteLink preview dialog, then
 *         joinChatByInviteLink (chatJoinResultSuccess carries
 *         the chat id; RequestSent means approval is pending)
 *  - t.me/<bot>?start=<param>   -> open the bot chat and
 *         sendBotStartMessage with the parameter
 *  - t.me/addstickers/<set>     -> the sticker-set add flow
 *  - t.me/proxy?... / t.me/socks?... -> prefill the proxy
 *         editor (ProxyStore.pendingPrefill) and ask the host
 *         to open Settings (DeepLink.pendingOpenSettings)
 *  - tg://resolve / join / addstickers / proxy equivalents
 * Unparseable Telegram links say so honestly and fall back
 * to the browser.
 */
object LinkRouter {

    val pendingInvite = MutableStateFlow<InvitePreview?>(null)

    private val HOSTS = setOf(
        "t.me", "telegram.me", "telegram.dog",
        "www.t.me", "www.telegram.me"
    )

    fun open(context: Context, raw: String) {
        val url = raw.trim()
        if (url.isEmpty()) return
        val uri = try {
            Uri.parse(url)
        } catch (t: Throwable) {
            null
        }
        if (uri == null) {
            openExternal(context, url)
            return
        }
        val host = uri.host?.lowercase()
        when {
            uri.scheme?.lowercase() == "tg" -> handleTg(context, uri, url)
            host != null && host in HOSTS -> handleWeb(context, uri, url)
            else -> openExternal(context, url)
        }
    }

    /** The pre-router behaviour, kept for non-Telegram links:
     *  open in the matching system app; no handler = no-op. */
    fun openExternal(context: Context, target: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (t: Throwable) {
            // No browser / dialer / mail app: nothing to do.
        }
    }

    private fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    // ---- tg:// ------------------------------------------------

    private fun handleTg(context: Context, uri: Uri, raw: String) {
        when (uri.host?.lowercase()) {
            "resolve" -> {
                val domain = uri.getQueryParameter("domain")
                    ?.removePrefix("@") ?: ""
                val post = uri.getQueryParameter("post")
                    ?.toLongOrNull()
                val start = uri.getQueryParameter("start")
                if (domain.isEmpty()) {
                    toast(context, "这个电报链接看不懂，已用浏览器打开")
                    openExternal(context, raw)
                } else {
                    openPublic(context, domain, post, start, raw)
                }
            }
            "join" -> {
                val invite = uri.getQueryParameter("invite") ?: ""
                if (invite.isEmpty()) {
                    openExternal(context, raw)
                } else {
                    checkInvite(context, "https://t.me/+$invite")
                }
            }
            "addstickers" -> {
                val set = uri.getQueryParameter("set") ?: ""
                if (set.isEmpty()) {
                    openExternal(context, raw)
                } else {
                    addStickerSet(context, set)
                }
            }
            "proxy", "socks" -> proxyPrefill(
                context, uri, mtproto = uri.host.equals("proxy", true)
            )
            else -> {
                toast(context, "这个电报链接暂时不支持，已用浏览器打开")
                openExternal(context, raw)
            }
        }
    }

    // ---- https://t.me/... --------------------------------------

    private fun handleWeb(context: Context, uri: Uri, raw: String) {
        var segments = uri.pathSegments.filter { it.isNotEmpty() }
        // t.me/s/<name>/... is the public-preview mirror of
        // t.me/<name>/... — same target.
        if (segments.firstOrNull() == "s") {
            segments = segments.drop(1)
        }
        if (segments.isEmpty()) {
            openExternal(context, raw)
            return
        }
        val first = segments[0]
        when {
            first == "addstickers" && segments.size >= 2 ->
                addStickerSet(context, segments[1])
            first == "proxy" -> proxyPrefill(context, uri, mtproto = true)
            first == "socks" -> proxyPrefill(context, uri, mtproto = false)
            first == "joinchat" && segments.size >= 2 ->
                checkInvite(context, raw)
            first.startsWith("+") -> checkInvite(context, raw)
            first == "c" -> {
                toast(context, "私有频道链接暂时打不开，已用浏览器打开")
                openExternal(context, raw)
            }
            else -> {
                val username = first.removePrefix("@")
                val msgId = segments.getOrNull(1)?.toLongOrNull()
                val start = uri.getQueryParameter("start")
                    ?: uri.getQueryParameter("startgroup")
                if (!username.matches(Regex("[A-Za-z0-9_]{3,64}"))) {
                    toast(context, "这个电报链接看不懂，已用浏览器打开")
                    openExternal(context, raw)
                } else {
                    openPublic(context, username, msgId, start, raw)
                }
            }
        }
    }

    // ---- routes -------------------------------------------------

    private fun openPublic(
        context: Context,
        username: String,
        msgId: Long?,
        startParam: String?,
        fallback: String
    ) {
        val query = TdClient.request("searchPublicChat").apply {
            put("username", username)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                toast(context, "没找到这个对话，已用浏览器打开")
                openExternal(context, fallback)
                return@sendRaw
            }
            val chatId = response.optLong("id")
            if (chatId == 0L) {
                toast(context, "没找到这个对话，已用浏览器打开")
                openExternal(context, fallback)
                return@sendRaw
            }
            if (startParam != null) {
                val userId = response.optJSONObject("type")
                    ?.optLong("user_id") ?: 0L
                if (userId != 0L) {
                    val start = TdClient.request("sendBotStartMessage")
                        .apply {
                            put("bot_user_id", userId)
                            put("chat_id", chatId)
                            put("parameter", startParam)
                        }
                    TdRepository.sendRaw(start) {}
                }
            }
            if (msgId != null && msgId != 0L) {
                MessageStore.pendingLocateId = msgId
            }
            DeepLink.pendingChat.value = chatId
        }
    }

    private fun checkInvite(context: Context, link: String) {
        val query = TdClient.request("checkChatInviteLink").apply {
            put("invite_link", link)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                toast(context, "邀请链接打不开，已用浏览器打开")
                openExternal(context, link)
                return@sendRaw
            }
            val memberOf = response.optLong("chat_id")
            if (memberOf != 0L) {
                DeepLink.pendingChat.value = memberOf
                return@sendRaw
            }
            val photo = response.optJSONObject("photo")
            photo?.optJSONObject("small")?.let { AvatarStore.noteFile(it) }
            pendingInvite.value = InvitePreview(
                link = link,
                title = response.optString("title").ifEmpty { "邀请" },
                memberCount = response.optInt("member_count"),
                description = response.optString("description"),
                photoPath = if (photo != null) {
                    AvatarStore.pathForFileObj(photo.optJSONObject("small"))
                } else {
                    null
                },
                createsJoinRequest =
                    response.optBoolean("creates_join_request")
            )
        }
    }

    /** Join the previewed invite; called by the host dialog. */
    fun joinInvite(context: Context, preview: InvitePreview) {
        pendingInvite.value = null
        val query = TdClient.request("joinChatByInviteLink").apply {
            put("invite_link", preview.link)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                toast(context, "加入失败，链接可能已经失效")
                return@sendRaw
            }
            when (response.optString("@type")) {
                "chatJoinResultSuccess" -> {
                    val chatId = response.optLong("chat_id")
                    if (chatId != 0L) DeepLink.pendingChat.value = chatId
                }
                "chatJoinResultRequestSent" ->
                    toast(context, "已发送加入申请，等管理员通过")
                else -> toast(context, "已处理这个邀请")
            }
        }
    }

    private fun addStickerSet(context: Context, setName: String) {
        StickerSetStore.addByInput(setName)
        toast(context, "正在添加贴纸包，在贴纸面板里查看")
    }

    private fun proxyPrefill(context: Context, uri: Uri, mtproto: Boolean) {
        val server = uri.getQueryParameter("server") ?: ""
        val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 0
        if (server.isEmpty() || port == 0) {
            toast(context, "代理链接参数不全，打不开")
            return
        }
        val config = if (mtproto) {
            ProxyConfig(
                name = "链接导入的代理",
                type = "mtproto",
                server = server,
                port = port,
                secret = uri.getQueryParameter("secret") ?: ""
            )
        } else {
            ProxyConfig(
                name = "链接导入的代理",
                type = "socks5",
                server = server,
                port = port,
                username = uri.getQueryParameter("user") ?: "",
                password = uri.getQueryParameter("pass") ?: ""
            )
        }
        ProxyStore.pendingPrefill.value = config
        DeepLink.pendingOpenSettings.value = true
        toast(context, "代理参数已带到设置页，确认后保存")
    }
}
