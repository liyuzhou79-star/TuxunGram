package com.tuxungram.android.td

import org.json.JSONArray
import org.json.JSONObject

/**
 * Settings export / import (v0.9.6, P4): the display
 * preferences and the proxy list as one small JSON file the
 * user can carry to another phone or keep for a reinstall.
 *
 * What is NOT in the file, by design: proxy passwords and
 * MTProto secrets are exported as EMPTY strings (the export
 * dialog says so before anything is written) — a backup
 * file gets shared and stored in places a credential must
 * never land. After an import, proxies with credentials
 * need their password / secret typed in once more.
 *
 * Import is all-or-nothing: the whole file is validated
 * into a staging area first, and only a fully valid file is
 * applied — a malformed file changes nothing and says why.
 *
 * Wallpapers (v0.9.16, W5): the backup carries the global
 * wallpaper's path and the per-chat override map (chat id
 * -> path) — the picture FILES themselves are never in the
 * backup (they live in the app's private storage and can be
 * megabytes each). On the same phone the paths resolve and
 * the wallpapers come back; on another phone they dangle,
 * and UiSettings treats a missing file as "no override", so
 * the chat falls back to the global wallpaper there. The
 * export dialog says this out loud before writing.
 *
 * Completeness audit (v0.9.36, C5): every UiSettings key
 * added since v0.9.24 was checked against this file —
 * label_dots (v0.9.28) and the download center's
 * concurrency / retry settings (DownloadStore's own
 * prefs, v0.9.23) were missing and are now in. Deliberately
 * still out, with reasons: the app-lock PIN material
 * (a credential — never exported, like proxy passwords);
 * the auto-download tier labels in AutoDownloadStore
 * (a local mirror of server-side state — the real setting
 * lives on the account via setAutoDownloadSettings and
 * follows the account, not the backup); account slots and
 * background-service state (runtime state, not settings);
 * UiSettings.seen_version (a what's-new watermark, not a
 * preference).
 */
object SettingsBackup {

    private const val KIND = "tuxungram-settings"

    fun exportJson(fromVersion: String): String {
        val ui = JSONObject().apply {
            put("unread_cap", UiSettings.unreadCap.value)
            put("theme_mode", UiSettings.themeMode.value)
            put("seed_index", UiSettings.seedIndex.value)
            put("accent_mode", UiSettings.accentMode.value)
            put("chat_bg", UiSettings.chatBg.value)
            put("bubble_style", UiSettings.bubbleStyle.value)
            put("font_size", UiSettings.fontSize.value)
            put("voice_speed", UiSettings.voiceSpeed.value.toDouble())
            // Wallpaper paths only — never the picture files
            // (see the header note).
            put("chat_bg_image", UiSettings.chatBgImage.value)
            // Background keep-alive (v0.9.16, W6 audit: a
            // local setting older than the backup itself
            // that was never included).
            put("keep_alive", UiSettings.keepAlive.value)
            // In-app message sounds (v0.9.29, V1).
            put("sound_fx", UiSettings.soundFx.value)
            // Bottom navigation bar switch (v0.9.19, Z5).
            put("duck_bar", UiSettings.duckBar.value)
            // Chat-row label dots (v0.9.28, U4; backup
            // inclusion v0.9.36, C5).
            put("label_dots", UiSettings.labelDots.value)
        }
        val chatWallpapers = JSONObject()
        for ((chatId, path) in UiSettings.chatWallpapers.value) {
            chatWallpapers.put(chatId.toString(), path)
        }
        // Local pins (v0.9.23, P1): the ordered chat ids.
        // Like the wallpaper map these are chat ids of THIS
        // account — on another account they simply match
        // nothing and are pruned away by the chat list.
        val localPins = JSONArray()
        for (chatId in LocalPins.ids.value) localPins.put(chatId)
        val proxies = JSONArray()
        val list = ProxyStore.proxies.value
        var activeIndex = -1
        list.forEachIndexed { index, entry ->
            if (entry.id == ProxyStore.activeId.value) {
                activeIndex = index
            }
            proxies.put(JSONObject().apply {
                put("name", entry.name)
                put("type", entry.type)
                put("server", entry.server)
                put("port", entry.port)
                put("username", entry.username)
                // Credentials never travel (see the header).
                put("password", "")
                put("secret", "")
            })
        }
        return JSONObject().apply {
            put("kind", KIND)
            put("version", 1)
            put("from_version", fromVersion)
            put("ui", ui)
            put("chat_wallpapers", chatWallpapers)
            put("local_pins", localPins)
            // Local labels (v0.9.25, R5): definitions plus
            // chat assignments, same account-scoping caveat
            // as local pins.
            put("local_labels", LocalLabels.exportJson())
            put("proxies", proxies)
            put("active_proxy", activeIndex)
            // Proxy auto-failover switch (v0.9.16, W6 audit:
            // added in 0.9.14 but never included here).
            put(
                "proxy_auto_failover",
                ProxyStore.autoFailover.value
            )
            // Download center settings (v0.9.23, P2; backup
            // inclusion v0.9.36, C5): how many files may
            // download at once and the automatic retry
            // budget. They live in DownloadStore's own
            // prefs, not UiSettings.
            put("download", JSONObject().apply {
                put(
                    "max_concurrent",
                    DownloadStore.maxConcurrent.value
                )
                put("retry_limit", DownloadStore.retryLimit.value)
            })
        }.toString(2)
    }

    private data class StagedProxy(
        val name: String,
        val type: String,
        val server: String,
        val port: Int,
        val username: String,
        val password: String,
        val secret: String
    )

    /** Apply a backup file's text. Returns null on success,
     *  or a plain-language reason nothing was changed. */
    fun importJson(text: String): String? {
        val root = try {
            JSONObject(text)
        } catch (t: Throwable) {
            return "这个文件打不开（不是有效的 JSON），设置没有改动。"
        }
        if (root.optString("kind") != KIND) {
            return "这不是 TuxunGram 的设置备份文件，设置没有改动。"
        }

        // ---- Stage and validate EVERYTHING first. ----
        var unreadCap: Int? = null
        var themeMode: Int? = null
        var seedIndex: Int? = null
        var accentMode: Int? = null
        var chatBg: Int? = null
        var bubbleStyle: Int? = null
        var fontSize: Int? = null
        var voiceSpeed: Float? = null
        var chatBgImage: String? = null
        var chatWallpapers: Map<Long, String>? = null
        var keepAlive: Boolean? = null
        var soundFx: Boolean? = null
        var duckBar: Boolean? = null
        var labelDots: Boolean? = null
        var localPins: List<Long>? = null
        var localLabels: JSONObject? = null
        var proxyAutoFailover: Boolean? = null
        var downloadMaxConcurrent: Int? = null
        var downloadRetryLimit: Int? = null
        val uiObj = root.optJSONObject("ui")
        if (uiObj != null) {
            try {
                if (uiObj.has("unread_cap")) {
                    val v = uiObj.getInt("unread_cap")
                    if (v !in UiSettings.UNREAD_CAPS) {
                        return "备份里的未读上限取值不对，设置没有改动。"
                    }
                    unreadCap = v
                }
                if (uiObj.has("theme_mode")) {
                    val v = uiObj.getInt("theme_mode")
                    if (v !in UiSettings.THEME_SYSTEM..UiSettings.THEME_DARK) {
                        return "备份里的主题模式取值不对，设置没有改动。"
                    }
                    themeMode = v
                }
                if (uiObj.has("seed_index")) {
                    val v = uiObj.getInt("seed_index")
                    if (v !in UiSettings.SEED_COLORS.indices) {
                        return "备份里的主题色取值不对，设置没有改动。"
                    }
                    seedIndex = v
                }
                if (uiObj.has("accent_mode")) {
                    val v = uiObj.getInt("accent_mode")
                    if (v !in UiSettings.ACCENT_OFFICIAL..UiSettings.ACCENT_CUSTOM) {
                        return "备份里的外观预设取值不对，设置没有改动。"
                    }
                    accentMode = v
                }
                if (uiObj.has("chat_bg")) {
                    val v = uiObj.getInt("chat_bg")
                    if (v !in UiSettings.CHAT_BG_COLORS.indices) {
                        return "备份里的聊天背景取值不对，设置没有改动。"
                    }
                    chatBg = v
                }
                if (uiObj.has("bubble_style")) {
                    val v = uiObj.getInt("bubble_style")
                    if (v !in UiSettings.BUBBLE_MODERN..UiSettings.BUBBLE_CLASSIC) {
                        return "备份里的气泡样式取值不对，设置没有改动。"
                    }
                    bubbleStyle = v
                }
                if (uiObj.has("font_size")) {
                    val v = uiObj.getInt("font_size")
                    if (v !in UiSettings.FONT_SCALES.indices) {
                        return "备份里的字体大小取值不对，设置没有改动。"
                    }
                    fontSize = v
                }
                if (uiObj.has("voice_speed")) {
                    val v = uiObj.getDouble("voice_speed").toFloat()
                    if (v !in UiSettings.VOICE_SPEEDS) {
                        return "备份里的语音倍速取值不对，设置没有改动。"
                    }
                    voiceSpeed = v
                }
                if (uiObj.has("chat_bg_image")) {
                    chatBgImage = uiObj.getString("chat_bg_image")
                }
                if (uiObj.has("keep_alive")) {
                    keepAlive = uiObj.getBoolean("keep_alive")
                }
                if (uiObj.has("sound_fx")) {
                    soundFx = uiObj.getBoolean("sound_fx")
                }
                if (uiObj.has("duck_bar")) {
                    duckBar = uiObj.getBoolean("duck_bar")
                }
                if (uiObj.has("label_dots")) {
                    labelDots = uiObj.getBoolean("label_dots")
                }
            } catch (t: Throwable) {
                return "备份里的设置部分格式不对，设置没有改动。"
            }
        }
        if (root.has("download")) {
            val dlObj = root.optJSONObject("download")
                ?: return "备份里的下载设置部分格式不对，设置没有改动。"
            try {
                if (dlObj.has("max_concurrent")) {
                    val v = dlObj.getInt("max_concurrent")
                    if (v !in DownloadStore.MAX_CONCURRENT_CHOICES) {
                        return "备份里的下载并发取值不对，设置没有改动。"
                    }
                    downloadMaxConcurrent = v
                }
                if (dlObj.has("retry_limit")) {
                    val v = dlObj.getInt("retry_limit")
                    if (v !in DownloadStore.RETRY_CHOICES) {
                        return "备份里的下载重试取值不对，设置没有改动。"
                    }
                    downloadRetryLimit = v
                }
            } catch (t: Throwable) {
                return "备份里的下载设置部分格式不对，设置没有改动。"
            }
        }
        if (root.has("chat_wallpapers")) {
            val obj = root.optJSONObject("chat_wallpapers")
                ?: return "备份里的专属壁纸部分格式不对，设置没有改动。"
            val staged = HashMap<Long, String>()
            try {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val id = key.toLongOrNull()
                        ?: return "备份里的专属壁纸部分格式不对，设置没有改动。"
                    val path = obj.getString(key)
                    if (path.isEmpty()) {
                        return "备份里的专属壁纸部分格式不对，设置没有改动。"
                    }
                    staged[id] = path
                }
            } catch (t: Throwable) {
                return "备份里的专属壁纸部分格式不对，设置没有改动。"
            }
            chatWallpapers = staged
        }
        if (root.has("local_pins")) {
            val arr = root.optJSONArray("local_pins")
                ?: return "备份里的本地置顶部分格式不对，设置没有改动。"
            val staged = ArrayList<Long>()
            try {
                for (i in 0 until arr.length()) {
                    val id = arr.getLong(i)
                    if (id == 0L) {
                        return "备份里的本地置顶部分格式不对，设置没有改动。"
                    }
                    if (id !in staged) staged.add(id)
                }
            } catch (t: Throwable) {
                return "备份里的本地置顶部分格式不对，设置没有改动。"
            }
            localPins = staged
        }
        if (root.has("local_labels")) {
            val obj = root.optJSONObject("local_labels")
                ?: return "备份里的本地标签部分格式不对，设置没有改动。"
            // Structural check only; LocalLabels.replaceAll
            // drops anything it cannot parse per-entry.
            if (obj.optJSONArray("labels") == null) {
                return "备份里的本地标签部分格式不对，设置没有改动。"
            }
            localLabels = obj
        }
        if (root.has("proxy_auto_failover")) {
            proxyAutoFailover = try {
                root.getBoolean("proxy_auto_failover")
            } catch (t: Throwable) {
                return "备份里的代理设置部分格式不对，设置没有改动。"
            }
        }

        var stagedProxies: List<StagedProxy>? = null
        var activeIndex = -1
        if (root.has("proxies")) {
            val arr = root.optJSONArray("proxies")
                ?: return "备份里的代理列表格式不对，设置没有改动。"
            val staged = ArrayList<StagedProxy>()
            try {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val type = o.getString("type")
                    if (type != "socks5" && type != "mtproto") {
                        return "备份里有代理的类型不对，设置没有改动。"
                    }
                    val server = o.getString("server")
                    if (server.isBlank()) {
                        return "备份里有代理没写服务器地址，设置没有改动。"
                    }
                    val port = o.getInt("port")
                    if (port !in 1..65535) {
                        return "备份里有代理的端口不对，设置没有改动。"
                    }
                    staged.add(
                        StagedProxy(
                            name = o.optString("name"),
                            type = type,
                            server = server,
                            port = port,
                            username = o.optString("username"),
                            password = o.optString("password"),
                            secret = o.optString("secret")
                        )
                    )
                }
            } catch (t: Throwable) {
                return "备份里的代理列表格式不对，设置没有改动。"
            }
            stagedProxies = staged
            if (root.has("active_proxy")) {
                val v = try {
                    root.getInt("active_proxy")
                } catch (t: Throwable) {
                    return "备份里的启用代理标记不对，设置没有改动。"
                }
                if (v !in -1 until staged.size) {
                    return "备份里的启用代理标记不对，设置没有改动。"
                }
                activeIndex = v
            }
        }

        // ---- Everything validated: apply. ----
        unreadCap?.let { UiSettings.setUnreadCap(it) }
        themeMode?.let { UiSettings.setThemeMode(it) }
        seedIndex?.let { UiSettings.setSeedIndex(it) }
        accentMode?.let { UiSettings.setAccentMode(it) }
        chatBg?.let { UiSettings.setChatBg(it) }
        bubbleStyle?.let { UiSettings.setBubbleStyle(it) }
        fontSize?.let { UiSettings.setFontSize(it) }
        voiceSpeed?.let { UiSettings.setVoiceSpeed(it) }
        chatBgImage?.let { path ->
            // A wallpaper path is applied only when it is a
            // clear ("") or the picture is actually on this
            // phone; a foreign backup's dangling path must
            // not wipe a working wallpaper here.
            if (path.isEmpty() || java.io.File(path).exists()) {
                UiSettings.setChatBgImage(path)
            }
        }
        chatWallpapers?.let { UiSettings.replaceChatWallpapers(it) }
        localPins?.let {
            LocalPins.replaceAll(it)
            ChatStore.onLocalPinsChanged()
        }
        localLabels?.let { LocalLabels.replaceAll(it) }
        keepAlive?.let { UiSettings.setKeepAlive(it) }
        soundFx?.let { UiSettings.setSoundFx(it) }
        duckBar?.let { UiSettings.setDuckBar(it) }
        labelDots?.let { UiSettings.setLabelDots(it) }
        proxyAutoFailover?.let { ProxyStore.setAutoFailover(it) }
        downloadMaxConcurrent?.let {
            DownloadStore.setMaxConcurrent(it)
        }
        downloadRetryLimit?.let { DownloadStore.setRetryLimit(it) }

        val proxies = stagedProxies
        if (proxies != null) {
            // Replace the list wholesale. Removing the active
            // entry also disables proxying (ProxyStore.remove
            // handles that); the staged active entry is
            // re-enabled at the end.
            for (old in ProxyStore.proxies.value.toList()) {
                ProxyStore.remove(old.id)
            }
            val newIds = ArrayList<Long>()
            var lastId = 0L
            for (p in proxies) {
                // ProxyStore.add keys entries by current time
                // in milliseconds; two adds inside one
                // millisecond would collide, so pace them.
                while (System.currentTimeMillis() <= lastId) {
                    try {
                        Thread.sleep(1)
                    } catch (t: InterruptedException) {
                        break
                    }
                }
                val added = ProxyStore.add(
                    p.name, p.type, p.server, p.port,
                    p.username, p.password, p.secret
                )
                lastId = added.id
                newIds.add(added.id)
            }
            if (activeIndex in newIds.indices) {
                ProxyStore.setActive(newIds[activeIndex])
            }
        }
        return null
    }
}
