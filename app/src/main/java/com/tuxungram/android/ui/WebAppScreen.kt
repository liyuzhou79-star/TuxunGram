package com.tuxungram.android.ui

import androidx.compose.ui.text.style.TextOverflow
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tuxungram.android.td.Haptics
import com.tuxungram.android.td.LinkRouter
import com.tuxungram.android.td.WebAppSession
import com.tuxungram.android.td.WebAppStore
import org.json.JSONObject

/** The TelegramWebviewProxy object the page's own
 *  telegram-web-app.js talks to: every Telegram.WebApp
 *  API call that needs the client arrives here as
 *  postEvent(type, dataJson). Calls land on a binder
 *  thread; the screen re-posts them to the main thread. */
private class WebAppBridge(
    private val onEvent: (String, String) -> Unit
) {
    @JavascriptInterface
    fun postEvent(eventType: String, eventData: String) {
        onEvent(eventType, eventData)
    }
}

/** State of one native button the page asked us to draw
 *  (web_app_setup_main_button / _secondary_button). */
private data class WebAppBtnState(
    val visible: Boolean = false,
    val active: Boolean = true,
    val text: String = "",
    val colorHex: String? = null,
    val textColorHex: String? = null,
    val progress: Boolean = false,
    val position: String = "left"
)

private fun parseHexColor(hex: String?): Color? {
    if (hex == null) return null
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6) return null
    val value = clean.toIntOrNull(16) ?: return null
    return Color(0xFF000000 or value.toLong())
}

/**
 * Bot Web App page (v0.9.36, C1, basic version): a WebView
 * carrying the URL TDLib minted, our own chrome (bot name,
 * close, optional settings/back buttons the page requests)
 * and the TelegramWebviewProxy bridge.
 *
 * Bridge coverage, honestly itemised (the checkpoint has
 * the same list):
 * SUPPORTED — web_app_ready (answers theme_changed +
 * viewport_changed), web_app_close, web_app_data_send
 * (keyboard launches only, via sendWebAppData; inline
 * launches have no data channel in the Bot API and the
 * event is dropped), web_app_expand / request_viewport,
 * request_theme, request_safe_area /
 * request_content_safe_area (zeros: the WebView starts
 * below our own top bar, so its content safe area really
 * is empty), setup_main_button / setup_secondary_button
 * (+ main_button_pressed / secondary_button_pressed),
 * setup_settings_button (+ settings_button_pressed),
 * setup_back_button (+ back_button_pressed), set_header_
 * color / set_background_color / set_bottom_bar_color,
 * open_link, open_tg_link, request_write_access
 * (allowBotToSendMessages -> write_access_requested),
 * invoke_custom_method (sendWebAppCustomRequest ->
 * custom_method_invoked), read_text_from_clipboard
 * (asks the user first, every time; denial answers with
 * data=null, the documented refusal shape),
 * trigger_haptic_feedback (mapped to the one tick this
 * app has).
 * NOT SUPPORTED (events ignored; pages degrade on their
 * own): biometrics, location / phone requests, QR scan
 * popups, share_message / share_to_story, inline-query
 * switching, device / secure storage, sensor streams,
 * home-screen shortcuts, orientation lock. Nothing is
 * faked: an unsupported call simply never gets an answer
 * event, exactly like a client that lacks the feature.
 *
 * WebView hardening: file access and content access are
 * off, file-URL universal access is off, multiple windows
 * are off, mixed content is never allowed. When TDLib
 * marks the URL require_same_origin, proxy events are
 * only accepted while the WebView still shows that
 * origin.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebAppScreen(session: WebAppSession, onClose: () -> Unit) {
    val context = LocalContext.current
    val palette = OfficialTheme.current()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadProgress by remember { mutableStateOf(0) }
    var mainBtn by remember { mutableStateOf(WebAppBtnState()) }
    var secondaryBtn by remember { mutableStateOf(WebAppBtnState()) }
    var settingsVisible by remember { mutableStateOf(false) }
    var backVisible by remember { mutableStateOf(false) }
    var headerColor by remember { mutableStateOf<Color?>(null) }
    var pageBg by remember { mutableStateOf<Color?>(null) }
    var bottomBarColor by remember { mutableStateOf<Color?>(null) }
    var clipboardReqId by remember { mutableStateOf<String?>(null) }

    fun close() {
        WebAppStore.close()
        onClose()
    }

    /** Push an event into the page. Always marshalled to
     *  the main thread: TDLib callbacks arrive on the
     *  receiver thread and WebView demands the UI thread. */
    fun sendEvent(type: String, data: JSONObject?) {
        val payload = data?.toString() ?: "null"
        mainHandler.post {
            val view = webView ?: return@post
            view.evaluateJavascript(
                "window.Telegram&&window.Telegram.WebView&&" +
                    "window.Telegram.WebView.receiveEvent(" +
                    "'$type', $payload);",
                null
            )
        }
    }

    fun themeChangedEvent() {
        sendEvent(
            "theme_changed",
            JSONObject().put(
                "theme_params",
                JSONObject(session.themeParamsJson)
            )
        )
    }

    fun viewportChangedEvent(view: WebView?) {
        val density = context.resources.displayMetrics.density
        val heightCss = if (view != null && view.height > 0) {
            (view.height / density).toInt()
        } else {
            (context.resources.displayMetrics.heightPixels /
                density).toInt()
        }
        sendEvent(
            "viewport_changed",
            JSONObject()
                .put("height", heightCss)
                .put("is_expanded", true)
                .put("is_state_stable", true)
        )
    }

    fun themedColor(key: String?): Color? = when (key) {
        "bg_color" -> palette.windowBg
        "secondary_bg_color" -> palette.graySection
        "bottom_bar_bg_color" -> palette.panelBg
        else -> null
    }

    fun handleBridgeEvent(type: String, rawData: String) {
        mainHandler.post {
            val data = try {
                JSONObject(rawData)
            } catch (t: Throwable) {
                JSONObject()
            }
            // Same-origin gate (webAppUrl.require_same_
            // origin): once the page navigates away from
            // the origin TDLib minted the URL for, its
            // proxy events no longer count.
            if (session.requireSameOrigin) {
                val initial = Uri.parse(session.url)
                val current = webView?.url?.let { Uri.parse(it) }
                val same = current != null &&
                    initial.scheme == current.scheme &&
                    initial.host == current.host &&
                    initial.port == current.port
                if (!same) return@post
            }
            when (type) {
                "web_app_ready" -> {
                    themeChangedEvent()
                    viewportChangedEvent(webView)
                }
                "web_app_close" -> close()
                "web_app_data_send" -> {
                    if (session.fromKeyboardButton) {
                        WebAppStore.sendData(
                            session, data.optString("data")
                        )
                        close()
                    }
                    // Inline launches: no data channel in
                    // the Bot API; dropped, not faked.
                }
                "web_app_expand" ->
                    viewportChangedEvent(webView)
                "web_app_request_viewport" ->
                    viewportChangedEvent(webView)
                "web_app_request_theme" -> themeChangedEvent()
                "web_app_request_safe_area" -> sendEvent(
                    "safe_area_changed",
                    JSONObject()
                        .put("top", 0).put("bottom", 0)
                        .put("left", 0).put("right", 0)
                )
                "web_app_request_content_safe_area" -> sendEvent(
                    "content_safe_area_changed",
                    JSONObject()
                        .put("top", 0).put("bottom", 0)
                        .put("left", 0).put("right", 0)
                )
                "web_app_setup_main_button" -> {
                    mainBtn = WebAppBtnState(
                        visible = data.optBoolean("is_visible"),
                        active = data.optBoolean("is_active", true),
                        text = data.optString("text"),
                        colorHex = data.optString("color")
                            .ifEmpty { null },
                        textColorHex = data.optString("text_color")
                            .ifEmpty { null },
                        progress =
                            data.optBoolean("is_progress_visible")
                    )
                }
                "web_app_setup_secondary_button" -> {
                    secondaryBtn = WebAppBtnState(
                        visible = data.optBoolean("is_visible"),
                        active = data.optBoolean("is_active", true),
                        text = data.optString("text"),
                        colorHex = data.optString("color")
                            .ifEmpty { null },
                        textColorHex = data.optString("text_color")
                            .ifEmpty { null },
                        progress =
                            data.optBoolean("is_progress_visible"),
                        position = data.optString("position", "left")
                    )
                }
                "web_app_setup_settings_button" ->
                    settingsVisible = data.optBoolean("is_visible")
                "web_app_setup_back_button" ->
                    backVisible = data.optBoolean("is_visible")
                "web_app_set_header_color" -> {
                    headerColor =
                        parseHexColor(data.optString("color"))
                            ?: themedColor(data.optString("color_key"))
                }
                "web_app_set_background_color" -> {
                    pageBg = parseHexColor(data.optString("color"))
                        ?: themedColor(data.optString("color_key"))
                    pageBg?.let { color ->
                        webView?.setBackgroundColor(
                            android.graphics.Color.rgb(
                                (color.red * 255).toInt(),
                                (color.green * 255).toInt(),
                                (color.blue * 255).toInt()
                            )
                        )
                    }
                }
                "web_app_set_bottom_bar_color" -> {
                    bottomBarColor =
                        parseHexColor(data.optString("color"))
                            ?: themedColor(data.optString("color_key"))
                }
                "web_app_open_link" -> {
                    val url = data.optString("url")
                    if (url.isNotEmpty()) {
                        LinkRouter.open(context, url)
                    }
                }
                "web_app_open_tg_link" -> {
                    val path = data.optString("path_full")
                    if (path.isNotEmpty()) {
                        LinkRouter.open(context, "https://t.me$path")
                    }
                }
                "web_app_request_write_access" -> {
                    WebAppStore.requestWriteAccess(session) { granted ->
                        sendEvent(
                            "write_access_requested",
                            JSONObject().put("is_granted", granted)
                        )
                    }
                }
                "web_app_invoke_custom_method" -> {
                    val reqId = data.optString("req_id")
                    val method = data.optString("method")
                    val params = data.optJSONObject("params")
                        ?.toString() ?: ""
                    WebAppStore.invokeCustomMethod(
                        session, method, params
                    ) { result, error ->
                        val payload = JSONObject().put("req_id", reqId)
                        if (error != null) {
                            payload.put("error", error)
                        } else {
                            payload.put(
                                "result",
                                try {
                                    JSONObject(result ?: "{}")
                                } catch (t: Throwable) {
                                    result ?: ""
                                }
                            )
                        }
                        sendEvent("custom_method_invoked", payload)
                    }
                }
                "web_app_read_text_from_clipboard" -> {
                    clipboardReqId = data.optString("req_id")
                }
                "web_app_trigger_haptic_feedback" -> Haptics.tick()
                else -> {
                    // Unsupported bridge surface (see the
                    // header list): no answer event is sent,
                    // the page degrades on its own.
                }
            }
        }
    }

    val bridge = remember(session.launchId, session.url) {
        WebAppBridge { type, data -> handleBridgeEvent(type, data) }
    }

    BackHandler {
        if (backVisible) {
            sendEvent("back_button_pressed", null)
        } else {
            close()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.let { view ->
                view.stopLoading()
                view.removeJavascriptInterface("TelegramWebviewProxy")
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            }
            webView = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg ?: palette.windowBg)
    ) {
        // Top bar: back (only when the page asked for a
        // back button) or close, the bot's name, and the
        // settings button when the page enabled it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerColor ?: palette.actionBarBg)
                // v0.9.53 (X3): this screen is a full-bleed
                // overlay outside the host's system-bars
                // padding — without this the bar (and its
                // close button) drew under the status bar.
                // The background stays above the padding in
                // the chain so the bar colour still covers
                // the status-bar area.
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (backVisible) {
                IconButton(onClick = {
                    sendEvent("back_button_pressed", null)
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = palette.actionBarIcon
                    )
                }
            } else {
                IconButton(onClick = { close() }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = palette.actionBarIcon
                    )
                }
            }
            Text(
                session.botName.ifEmpty { "小程序" },
                color = palette.actionBarTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (settingsVisible) {
                IconButton(onClick = {
                    sendEvent("settings_button_pressed", null)
                }) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "设置",
                        tint = palette.actionBarIcon
                    )
                }
            }
        }
        if (loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        @Suppress("DEPRECATION")
                        settings.allowFileAccessFromFileURLs = false
                        @Suppress("DEPRECATION")
                        settings.allowUniversalAccessFromFileURLs =
                            false
                        settings.setSupportMultipleWindows(false)
                        settings.javaScriptCanOpenWindowsAutomatically =
                            false
                        settings.mixedContentMode =
                            WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        addJavascriptInterface(
                            bridge, "TelegramWebviewProxy"
                        )
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url ?: return false
                                val scheme = url.scheme ?: ""
                                val host = url.host ?: ""
                                return when {
                                    scheme == "http" ||
                                        scheme == "https" -> {
                                        if (host == "t.me" ||
                                            host == "telegram.me"
                                        ) {
                                            LinkRouter.open(
                                                context, url.toString()
                                            )
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    scheme == "tg" -> {
                                        LinkRouter.open(
                                            context, url.toString()
                                        )
                                        true
                                    }
                                    else -> true
                                }
                            }

                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: Bitmap?
                            ) {
                                loadProgress = 5
                            }

                            override fun onPageFinished(
                                view: WebView?,
                                url: String?
                            ) {
                                loadProgress = 100
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(
                                view: WebView?,
                                newProgress: Int
                            ) {
                                loadProgress = newProgress
                            }
                        }
                        webView = this
                        loadUrl(session.url)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        // Native button bar the page controls: secondary
        // button sits left or right of the main one, per
        // its requested position.
        if (mainBtn.visible || secondaryBtn.visible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bottomBarColor ?: palette.panelBg)
                    // v0.9.53 (X3): same full-bleed overlay
                    // situation as the top bar — keep the
                    // page's main button clear of the
                    // navigation bar.
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val main: @Composable androidx.compose.foundation
                    .layout.RowScope.() -> Unit = {
                    if (mainBtn.visible) {
                        WebAppNativeButton(
                            state = mainBtn,
                            fallbackColor = palette.accent,
                            modifier = Modifier.weight(1f)
                        ) {
                            sendEvent("main_button_pressed", null)
                        }
                    }
                }
                val secondary: @Composable androidx.compose.foundation
                    .layout.RowScope.() -> Unit = {
                    if (secondaryBtn.visible) {
                        WebAppNativeButton(
                            state = secondaryBtn,
                            fallbackColor = palette.graySection,
                            modifier = Modifier.weight(1f)
                        ) {
                            sendEvent("secondary_button_pressed", null)
                        }
                    }
                }
                if (secondaryBtn.visible &&
                    secondaryBtn.position == "left"
                ) {
                    secondary()
                    if (mainBtn.visible) {
                        Spacer(Modifier.width(10.dp))
                    }
                    main()
                } else {
                    main()
                    if (secondaryBtn.visible) {
                        if (mainBtn.visible) {
                            Spacer(Modifier.width(10.dp))
                        }
                        secondary()
                    }
                }
            }
        }
    }

    // Clipboard reads always ask first (v0.9.36, C5):
    // a Web App never reads the clipboard silently.
    val reqId = clipboardReqId
    if (reqId != null) {
        AlertDialog(
            onDismissRequest = {
                clipboardReqId = null
                sendEvent(
                    "clipboard_text_received",
                    JSONObject()
                        .put("req_id", reqId)
                        .put("data", JSONObject.NULL)
                )
            },
            title = { Text("读取剪贴板") },
            text = {
                Text(
                    "「${session.botName.ifEmpty { "这个小程序" }}」" +
                        "想读取你的剪贴板内容。剪贴板里可能有你刚复制的" +
                        "密码或验证码，确认要给它吗？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardReqId = null
                    val cm = context.getSystemService(
                        android.content.Context.CLIPBOARD_SERVICE
                    ) as? android.content.ClipboardManager
                    val text = cm?.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                    sendEvent(
                        "clipboard_text_received",
                        JSONObject()
                            .put("req_id", reqId)
                            .put(
                                "data",
                                text ?: JSONObject.NULL
                            )
                    )
                }) {
                    Text("允许这一次")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    clipboardReqId = null
                    sendEvent(
                        "clipboard_text_received",
                        JSONObject()
                            .put("req_id", reqId)
                            .put("data", JSONObject.NULL)
                    )
                }) {
                    Text("拒绝")
                }
            }
        )
    }
}

@Composable
private fun WebAppNativeButton(
    state: WebAppBtnState,
    fallbackColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = parseHexColor(state.colorHex) ?: fallbackColor
    val fg = parseHexColor(state.textColorHex) ?: Color.White
    Button(
        onClick = onClick,
        enabled = state.active && !state.progress,
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = fg,
            disabledContainerColor = bg.copy(alpha = 0.5f),
            disabledContentColor = fg.copy(alpha = 0.7f)
        ),
        modifier = modifier.height(48.dp)
    ) {
        if (state.progress) {
            CircularProgressIndicator(
                color = fg,
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(state.text, fontSize = 15.sp, overflow = TextOverflow.Ellipsis, maxLines = 1)
        }
    }
}
