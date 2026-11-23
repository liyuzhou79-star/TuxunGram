package com.tuxungram.android.ui


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.AuthState
import com.tuxungram.android.td.ChatItem
import com.tuxungram.android.td.ChatStore
import com.tuxungram.android.td.MessageStore
import com.tuxungram.android.td.MsgItem
import com.tuxungram.android.td.MsgKind
import com.tuxungram.android.td.ProxyConfig
import com.tuxungram.android.td.ProxyStore
import com.tuxungram.android.td.TdRepository
import com.tuxungram.android.td.UiSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay


/**
 * Slim vertical scroll indicator drawn over a lazy list's right
 * edge (Phase D, D4) — Compose has no built-in scrollbar, and
 * the user explicitly missed one. The thumb's size/position
 * come from the visible window's share of the whole list;
 * [reverse] flips it for reverseLayout lists (chat history),
 * where the highest index sits at the visual top.
 */
fun Modifier.lazyListScrollbar(
    state: LazyListState,
    reverse: Boolean = false
): Modifier = drawWithContent {
    drawContent()
    val info = state.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo
    if (total == 0 || visible.isEmpty()) return@drawWithContent
    val firstIndex = visible.first().index
    val lastIndex = visible.last().index
    val visibleCount = lastIndex - firstIndex + 1
    if (visibleCount >= total) return@drawWithContent
    val thumbHeight = (size.height * visibleCount / total)
        .coerceAtLeast(24.dp.toPx())
    // Position within the scrollable index range; for reverse
    // lists the window at index 0 is the visual bottom.
    val range = (total - visibleCount).coerceAtLeast(1)
    val topFraction = if (reverse) {
        1f - firstIndex.toFloat() / range
    } else {
        firstIndex.toFloat() / range
    }.coerceIn(0f, 1f)
    val thumbTop = (size.height - thumbHeight) * topFraction
    val width = 4.dp.toPx()
    drawRoundRect(
        color = Color(0x66000000),
        topLeft = Offset(size.width - width - 2.dp.toPx(), thumbTop),
        size = Size(width, thumbHeight),
        cornerRadius = CornerRadius(width / 2, width / 2)
    )
}

/** Same indicator for plain scrollable columns (settings). */
fun Modifier.columnScrollbar(state: ScrollState): Modifier =
    drawWithContent {
        drawContent()
        if (state.maxValue <= 0) return@drawWithContent
        val viewport = size.height
        val total = viewport + state.maxValue
        val thumbHeight = (viewport * viewport / total)
            .coerceAtLeast(24.dp.toPx())
        val thumbTop = (viewport - thumbHeight) *
            (state.value.toFloat() / state.maxValue)
        val width = 4.dp.toPx()
        drawRoundRect(
            color = Color(0x66000000),
            topLeft = Offset(size.width - width - 2.dp.toPx(), thumbTop),
            size = Size(width, thumbHeight),
            cornerRadius = CornerRadius(width / 2, width / 2)
        )
    }

/** Translate a TDLib error into one plain sentence for the UI.
 *  Raw codes are never shown except the numeric code on the
 *  unknown-error line, so a user screenshot stays useful. */
/** People counts the way the app's Chinese UI writes them
 *  (v0.9.29, V4): thousands grouped below ten thousand, and
 *  万 units above it (12345 → "1.2 万", 123456 → "12.3 万"),
 *  matching how the official client's Chinese UI presents
 *  member and subscriber counts. Used by the chat header
 *  subtitle and profile pages, which used to print the raw
 *  number. */
fun formatCount(count: Int): String {
    if (count < 0) return "0"
    if (count < 10_000) {
        return String.format(
            java.util.Locale.getDefault(), "%,d", count
        )
    }
    val wan = count / 10_000.0
    val text = String.format(java.util.Locale.US, "%.1f", wan)
        .removeSuffix(".0")
    return "$text 万"
}

fun friendlyError(error: Pair<Int, String>): String {
    val (code, message) = error
    val flood = Regex("FLOOD_WAIT_(\\d+)").find(message)
    return when {
        flood != null -> {
            val seconds = flood.groupValues[1].toIntOrNull() ?: 0
            if (seconds >= 120) "操作太频繁了，Telegram 让你等 ${seconds / 60} 分钟再试。"
            else "操作太频繁了，Telegram 让你等一会儿再试。"
        }
        message.contains("PHONE_CODE_INVALID") -> "验证码不对，再对一遍重新填。"
        message.contains("PHONE_CODE_EXPIRED") -> "验证码过期了，点下面重新发一个。"
        message.contains("PASSWORD_HASH_INVALID") -> "密码不对，再试一次。注意大小写。"
        message.contains("PHONE_NUMBER_INVALID") -> "这个手机号 Telegram 不认，检查区号和号码。"
        message.contains("API_ID_INVALID") || message.contains("API_HASH") ->
            "App 还没配好（凭证问题），这不是你的操作问题。"
        else -> "出了点问题（错误码 $code），再试一次；还不行就重启 App 试试。"
    }
}

@Composable
fun ErrorText() {
    val error by TdRepository.lastError.collectAsState()
    val e = error ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 12.dp)
    ) {
        Icon(
            Icons.Filled.Warning, contentDescription = null,
            tint = Color(0xFFC62828), modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = friendlyError(e),
            color = Color(0xFFC62828),
            fontSize = 14.sp
        )
    }
}

@Composable
fun CenterColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) { content() }
}

// The old auth form shell (FormColumn: centered column
// with the plane badge over outlined fields) was retired
// in v0.9.21 (AB3) once every auth page had moved to
// AuthFormShell in AuthScreens.kt; it had no other
// callers.

/** Decode an image file downsampled so its long edge stays
 *  under [maxDim] px. A full-size decode of a camera original
 *  (6000x4000 and up) allocates a ~100 MB bitmap and can OOM
 *  the app; anything shown on a phone screen loses nothing at
 *  2048 px. Same approach as the media viewer's decoder. */
fun decodeDownsampled(path: String, maxDim: Int = 2048): android.graphics.Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxDim ||
            bounds.outHeight / sample > maxDim
        ) {
            sample *= 2
        }
        BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    } catch (t: Throwable) {
        null
    }
}

/** Shared decoded-bitmap cache (H2-2): before this, every
 *  avatar / photo bubble / sticker decode ran from disk again
 *  each time its row scrolled back into view. One LRU cache
 *  keyed by file path, sized by bitmap byte count and capped
 *  at 24 MB — several screens' worth of bubbles, still polite
 *  on low-memory devices; LRU eviction handles the rest. */
private val bitmapCache =
    object : android.util.LruCache<String, android.graphics.Bitmap>(
        24 * 1024 * 1024
    ) {
        override fun sizeOf(
            key: String,
            value: android.graphics.Bitmap
        ): Int = value.byteCount
    }

/** Decode [path] through the shared bitmap cache, optionally
 *  downsampled to [maxDim] px on the long edge first. Call
 *  from a background dispatcher. The cache key carries the
 *  requested size (v0.9.28, U3): keyed by path alone, a
 *  downsampled decode could satisfy a later full-size
 *  request (blurry viewer image) and a full-size decode
 *  could satisfy a thumbnail request (wasted memory). */
fun decodeCached(path: String, maxDim: Int = 0): android.graphics.Bitmap? {
    val key = if (maxDim > 0) "$path@$maxDim" else path
    bitmapCache.get(key)?.let { return it }
    val bmp = if (maxDim > 0) {
        decodeDownsampled(path, maxDim)
    } else {
        try {
            BitmapFactory.decodeFile(path)
        } catch (t: Throwable) {
            null
        }
    }
    if (bmp != null) bitmapCache.put(key, bmp)
    return bmp
}

/** Small rounded media thumbnail for list rows (v0.9.11,
 *  A2): the chat list's last-message media chip. Decodes
 *  through the same cache as avatars; renders nothing
 *  while the file is not on disk yet. The decode is capped
 *  at 96 px (v0.9.47, R4): the chip is 22 dp, and the
 *  source can be a full photo size. */
@Composable
fun MediaThumbChip(path: String, sizeDp: Int = 22) {
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        value = withContext(Dispatchers.IO) { decodeCached(path, 96) }
    }
    val bmp = bitmap ?: return
    Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(5.dp))
    )
}

/** Round avatar from a downloaded TDLib photo file. The
 *  decode is capped at 3x the display size (v0.9.47, R4):
 *  avatar files can be 640 px renditions shown at 52 dp,
 *  and the shared bitmap cache keys by requested size, so
 *  the cap costs nothing when a bigger rendition of the
 *  same file is genuinely needed elsewhere. */
@Composable
fun AvatarImage(path: String, sizeDp: Int) {
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        value = withContext(Dispatchers.IO) {
            decodeCached(path, (sizeDp * 3).coerceIn(96, 512))
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(Color(0xFFE0E0E0))
        )
    } else {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .background(Color(0xFFE0E0E0), CircleShape)
        )
    }
}

/** Floating "back to the newest message" button (v0.9.28,
 *  U1), shared by the chat, thread and forum-topic lists.
 *  The caller decides visibility (scrolled more than a
 *  screen away from the newest end, or the loaded window
 *  does not reach it) and what a tap does; this only draws:
 *  a 40dp surface circle with a down chevron and, when the
 *  chat has unread messages, the unread count as a small
 *  accent capsule at its top-right (capped by the user's
 *  badge setting, like every other badge in the app).
 *  Threads and topics pass unreadCount = 0 — TDLib keeps no
 *  per-thread unread counter, so no badge is faked there. */
@Composable
fun ScrollToLatestButton(
    visible: Boolean,
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.tween(Motion.SCROLL_BTN_IN)) +
            scaleIn(Motion.tween(Motion.SCROLL_BTN_IN), initialScale = 0.7f),
        exit = fadeOut(Motion.tween(Motion.SCROLL_BTN_OUT)) +
            scaleOut(Motion.tween(Motion.SCROLL_BTN_OUT), targetScale = 0.7f),
        modifier = modifier
    ) {
        // Touch target (v0.9.33, Z5): the visible circle
        // stays 40dp, but the tappable area is the full
        // 48dp box around it — 40dp was under the minimum
        // comfortable target size.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "回到最新消息",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            }
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(9.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        com.tuxungram.android.td.UiSettings.badgeText(
                            unreadCount
                        ),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** Quiet spinner shown at the top of a message list while
 *  an older history page is in flight (v0.9.28, U2). In the
 *  reversed lists it is emitted as the LAST item, which is
 *  the visual top. */
@Composable
fun PagingSpinnerRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}


/** The failed-send note under a bubble (v0.9.44, N1/N2),
 *  shared by the main chat, threads and forum topics.
 *  What it offers follows the typed classification of the
 *  failure (see SendFailure): a failure TDLib says can be
 *  resent gets the classic 「发送失败，点这里重发」 tap; one
 *  that can never succeed on a resend (no posting rights,
 *  a paid send) shows the short reason instead, and its
 *  tap opens a detail dialog with the full explanation
 *  and a delete action — never a fake retry. */
@Composable
fun FailedSendNote(
    msg: com.tuxungram.android.td.MsgItem,
    onResend: (com.tuxungram.android.td.MsgItem) -> Unit,
    onDelete: (com.tuxungram.android.td.MsgItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val kind = com.tuxungram.android.td.SendFailure.classify(
        msg.failCode, msg.failId, msg.paidStarsRequired
    )
    val retryable = com.tuxungram.android.td.SendFailure
        .retryable(kind, msg.canRetry)
    var detailOpen by remember(msg.id) { mutableStateOf(false) }
    Text(
        if (retryable) {
            "  发送失败，点这里重发"
        } else {
            "  发送失败：" + com.tuxungram.android.td.SendFailure
                .shortReason(kind, msg.retryAfterSec)
        },
        color = Color(0xFFC62828),
        fontSize = 11.sp,
        modifier = modifier.clickable {
            if (retryable) {
                onResend(msg)
            } else {
                detailOpen = true
            }
        }
    )
    if (detailOpen) {
        AlertDialog(
            onDismissRequest = { detailOpen = false },
            title = { Text("发送失败") },
            text = {
                Text(
                    com.tuxungram.android.td.SendFailure
                        .fullText(
                            kind, msg.retryAfterSec,
                            msg.failCode, msg.failId
                        )
                )
            },
            confirmButton = {
                TextButton(onClick = { detailOpen = false }) {
                    Text("知道了")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    detailOpen = false
                    onDelete(msg)
                }) {
                    Text("删除这条", color = Color(0xFFC62828))
                }
            }
        )
    }
}

/** Full-text selection page (v0.9.44, N7): the message's
 *  whole text inside a SelectionContainer, so the user
 *  can drag the system handles and copy any portion —
 *  the bubble itself only offers whole-message copy.
 *  In-place selection inside the scrolling bubble list
 *  was not verifiable without a device (selection drags
 *  fight the list's scroll and the swipe-to-reply drag),
 *  so the deterministic page is the shipped path in all
 *  three hosts; the long-press menu entry opens it. */
@Composable
fun SelectableTextDialog(
    text: String,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择文字") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                androidx.compose.foundation.text.selection
                    .SelectionContainer {
                        Text(text, fontSize = 15.sp)
                    }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(
                    androidx.compose.ui.text.AnnotatedString(text)
                )
                onDismiss()
            }) { Text("复制全文") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
