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
 */
object SettingsBackup {

    private const val KIND = "tuxungram-settings"

    fun exportJson(fromVersion: String): String {
        val ui = JSONObject().apply {
            put("unread_cap", UiSettings.unreadCap.value)
            put("theme_mode", UiSettings.themeMode.value)
            put("seed_index", UiSettings.seedIndex.value)
            put("chat_bg", UiSettings.chatBg.value)
            put("bubble_style", UiSettings.bubbleStyle.value)
            put("font_size", UiSettings.fontSize.value)
            put("voice_speed", UiSettings.voiceSpeed.value.toDouble())
        }
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
            put("proxies", proxies)
            put("active_proxy", activeIndex)
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
        var chatBg: Int? = null
        var bubbleStyle: Int? = null
        var fontSize: Int? = null
        var voiceSpeed: Float? = null
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
            } catch (t: Throwable) {
                return "备份里的设置部分格式不对，设置没有改动。"
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
        chatBg?.let { UiSettings.setChatBg(it) }
        bubbleStyle?.let { UiSettings.setBubbleStyle(it) }
        fontSize?.let { UiSettings.setFontSize(it) }
        voiceSpeed?.let { UiSettings.setVoiceSpeed(it) }

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
