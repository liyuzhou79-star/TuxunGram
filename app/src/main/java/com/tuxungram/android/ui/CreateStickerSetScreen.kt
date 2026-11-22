package com.tuxungram.android.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.AppLock
import com.tuxungram.android.td.MeStore
import com.tuxungram.android.td.StickerSetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One image picked for the new set, with its validation
 *  outcome. Validation is honest and final: this basic
 *  flow never resizes or crops, so a wrong-sized image is
 *  flagged here and blocks submission until replaced. */
private data class PickedSticker(
    val path: String,
    val isWebp: Boolean,
    val emoji: String,
    val problem: String?
)

/**
 * Create a static sticker set (v0.9.26, S4, basic):
 * title + short name (the server requires the
 * "_by_<username>" suffix, appended automatically), pick
 * PNG/WebP images from the gallery, one emoji per image,
 * then createNewStickerSet. Animated/video stickers are
 * out of scope for this version. Success shows the
 * t.me/addstickers link.
 */
@Composable
fun CreateStickerSetView(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val me by MeStore.me.collectAsState()
    val createResult by StickerSetStore.createResult.collectAsState()
    var title by remember { mutableStateOf("") }
    var stem by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<PickedSticker>() }
    var submitting by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    BackHandler { onBack() }

    val finalName = remember(stem, me.username) {
        val clean = stem.filter {
            it.isLetterOrDigit() || it == '_'
        }
        if (clean.isEmpty() || me.username.isEmpty()) {
            clean
        } else {
            clean + "_by_" + me.username
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        for (uri in uris) {
            if (picked.size >= 50) break
            try {
                val mime = context.contentResolver.getType(uri) ?: ""
                val ext = if (mime == "image/webp") "webp" else "png"
                val file = File(
                    context.cacheDir,
                    "newsticker_${System.currentTimeMillis()}" +
                        "_${picked.size}.$ext"
                )
                context.contentResolver.openInputStream(uri)
                    ?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, options)
                val w = options.outWidth
                val h = options.outHeight
                val outMime = options.outMimeType ?: mime
                val isWebp = outMime == "image/webp"
                val problem = when {
                    outMime != "image/png" && !isWebp ->
                        "格式是 $outMime，需要 PNG 或 WebP。"
                    w <= 0 || h <= 0 ->
                        "这张图读不出来，换一张试试。"
                    !((w == 512 || h == 512) && w <= 512 && h <= 512) ->
                        "尺寸是 ${w}×${h}，贴纸需要一边正好 " +
                            "512 像素、另一边不超过 512（本版不自动裁剪）。"
                    else -> null
                }
                picked.add(
                    PickedSticker(
                        path = file.absolutePath,
                        isWebp = isWebp,
                        emoji = "😀",
                        problem = problem
                    )
                )
            } catch (t: Throwable) {
                // One bad pick must not sink the rest; it just
                // does not join the list.
            }
        }
    }

    val result = createResult
    if (result != null && result.startsWith("ok:")) {
        val name = result.removePrefix("ok:")
        val link = "https://t.me/addstickers/$name"
        AlertDialog(
            onDismissRequest = {
                StickerSetStore.clearCreateResult()
                onBack()
            },
            title = { Text("贴纸包建好了") },
            text = {
                Column {
                    Text("「$title」已经创建并装好了，分享链接：")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        link,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(link))
                }) { Text("复制链接") }
            },
            dismissButton = {
                TextButton(onClick = {
                    StickerSetStore.clearCreateResult()
                    onBack()
                }) { Text("完成") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                "创建贴纸包",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .lazyListScrollbar(listState)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            item {
                Text(
                    "本版只做静态贴纸：从相册选 PNG / WebP 图片，" +
                        "一边正好 512 像素、另一边不超过 512；" +
                        "尺寸不对的图不会自动裁剪，会标出来。" +
                        "动图和视频贴纸以后再做。",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("贴纸包标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = stem,
                    onValueChange = { stem = it },
                    label = { Text("短名（字母、数字、下划线）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    when {
                        me.username.isEmpty() ->
                            "你的账号还没有用户名：服务器要求贴纸包名" +
                                "带 _by_用户名 后缀，先去设置里设一个用户名。"
                        finalName.isEmpty() ->
                            "短名会拼成 <短名>_by_${me.username}，" +
                                "这就是 t.me/addstickers/ 后面那串。"
                        else ->
                            "链接将是 t.me/addstickers/$finalName"
                    },
                    color = Color.Gray,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        AppLock.noteExternalLaunch()
                        picker.launch("image/*")
                    }
                ) {
                    Text("从相册选图片")
                }
                Spacer(Modifier.height(6.dp))
            }
            itemsIndexed(picked, key = { _, item -> item.path }) {
                index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bitmap by produceState<android.graphics.Bitmap?>(
                        initialValue = null, item.path
                    ) {
                        value = withContext(Dispatchers.IO) {
                            decodeCached(item.path, 256)
                        }
                    }
                    val bmp = bitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color(0xFFE0E0E0))
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = item.emoji,
                            onValueChange = { value ->
                                picked[index] =
                                    item.copy(emoji = value.take(8))
                            },
                            label = { Text("这张贴纸的表情") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (item.problem != null) {
                            Text(
                                item.problem,
                                color = Color(0xFFC62828),
                                fontSize = 12.5.sp,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                    TextButton(onClick = { picked.removeAt(index) }) {
                        Text("移除", color = Color(0xFFC62828))
                    }
                }
            }
            item {
                Spacer(Modifier.height(10.dp))
                if (result != null && result.startsWith("err:")) {
                    Text(
                        result.removePrefix("err:"),
                        color = Color(0xFFC62828),
                        fontSize = 13.5.sp
                    )
                    Spacer(Modifier.height(8.dp))
                }
                val canSubmit = title.isNotBlank() &&
                    finalName.length >= 5 &&
                    me.username.isNotEmpty() &&
                    picked.isNotEmpty() &&
                    picked.all { it.problem == null } &&
                    !submitting
                Button(
                    onClick = {
                        submitting = true
                        StickerSetStore.clearCreateResult()
                        StickerSetStore.createSet(
                            title = title.trim(),
                            name = finalName,
                            items = picked.map {
                                StickerSetStore.NewStickerItem(
                                    path = it.path,
                                    isWebp = it.isWebp,
                                    emoji = it.emoji
                                )
                            }
                        )
                    },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (submitting) "正在创建…" else "创建贴纸包")
                }
                if (submitting && result == null) {
                    Text(
                        "正在向服务器提交…",
                        color = Color.Gray,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // A finished attempt (success handled by the dialog
    // above) re-arms the submit button.
    androidx.compose.runtime.LaunchedEffect(result) {
        if (result != null) submitting = false
    }
}
