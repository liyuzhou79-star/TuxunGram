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
import androidx.compose.material.icons.Icons
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
    Text(
        text = friendlyError(e),
        color = Color(0xFFC62828),
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 12.dp)
    )
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

@Composable
fun FormColumn(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        content()
        ErrorText()
    }
}

/** Round avatar from a downloaded TDLib photo file; the small
 *  renditions are tiny, so a plain decode is fine. */
@Composable
fun AvatarImage(path: String, sizeDp: Int) {
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        value = withContext(Dispatchers.IO) {
            try {
                BitmapFactory.decodeFile(path)
            } catch (t: Throwable) {
                null
            }
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

