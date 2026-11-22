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

/** One file picked for the new set, with its validation
 *  outcome. Validation is honest and final: this flow
 *  never resizes, crops or transcodes, so a file that
 *  misses the server spec is flagged here and blocks
 *  submission until replaced. [format] is "webp" for
 *  static images (PNG rides along), "webm" for video
 *  stickers and "tgs" for animated ones (v0.9.36, C2);
 *  a set must stay within one format. */
private data class PickedSticker(
    val path: String,
    val format: String,
    val emoji: String,
    val problem: String?
)

/** Local pre-check for a video sticker file (v0.9.36,
 *  C2), mirroring the server spec: WebM container,
 *  ≤ 256 KB, ≤ 3 seconds, one side exactly 512 px and
 *  the other at most 512. Metadata the platform cannot
 *  read is not held against the file — the server stays
 *  the final judge and its refusal is mapped to plain
 *  language in StickerSetStore. No transcoding happens
 *  here or anywhere in this flow. */
private fun validateWebmSticker(file: File): String? {
    if (file.length() > 256 * 1024L) {
        return "文件有 ${file.length() / 1024} KB，视频贴纸不能超过 256 KB。"
    }
    val retriever = android.media.MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        val durationMs = retriever.extractMetadata(
            android.media.MediaMetadataRetriever
                .METADATA_KEY_DURATION
        )?.toLongOrNull()
        if (durationMs != null && durationMs > 3000) {
            return "时长 ${(durationMs / 100).toDouble() / 10} 秒，" +
                "视频贴纸不能超过 3 秒。"
        }
        val w = retriever.extractMetadata(
            android.media.MediaMetadataRetriever
                .METADATA_KEY_VIDEO_WIDTH
        )?.toIntOrNull()
        val h = retriever.extractMetadata(
            android.media.MediaMetadataRetriever
                .METADATA_KEY_VIDEO_HEIGHT
        )?.toIntOrNull()
        if (w != null && h != null && w > 0 && h > 0 &&
            !((w == 512 || h == 512) && w <= 512 && h <= 512)
        ) {
            return "尺寸是 ${w}×${h}，视频贴纸需要一边正好 " +
                "512 像素、另一边不超过 512。"
        }
    } catch (t: Throwable) {
        // Unreadable metadata: the size check above still
        // stands; dimensions/duration go to the server.
    } finally {
        try {
            retriever.release()
        } catch (t: Throwable) {
            // Nothing to release cleanly; the file verdict
            // does not depend on it.
        }
    }
    return null
}

/** Local pre-check for an animated sticker file
 *  (v0.9.36, C2): TGS is a gzipped Lottie JSON — size
 *  ≤ 64 KB, and the JSON's canvas must be 512×512.
 *  A file that does not decompress or parse is flagged
 *  here instead of wasting a server round trip. */
private fun validateTgsSticker(file: File): String? {
    if (file.length() > 64 * 1024L) {
        return "文件有 ${file.length() / 1024} KB，动图贴纸不能超过 64 KB。"
    }
    return try {
        val json = java.util.zip.GZIPInputStream(
            file.inputStream()
        ).bufferedReader().use { it.readText() }
        val root = org.json.JSONObject(json)
        val w = root.optInt("w")
        val h = root.optInt("h")
        if (w != 512 || h != 512) {
            "画布是 ${w}×${h}，动图贴纸需要 512×512。"
        } else {
            null
        }
    } catch (t: Throwable) {
        "这个文件读不出 TGS 动画数据，换一个试试。"
    }
}

/**
 * Create a sticker set (v0.9.26, S4; video/animated
 * added v0.9.36, C2): title + short name (the server
 * requires the "_by_<username>" suffix, appended
 * automatically), pick files, one emoji per file, then
 * createNewStickerSet. Static sets take PNG/WebP images
 * (one side exactly 512 px); video sets take .webm
 * files as-is (≤ 256 KB, ≤ 3 s, one side 512 px);
 * animated sets take .tgs files as-is (≤ 64 KB,
 * 512×512 canvas). Nothing is transcoded — a file that
 * misses the spec is flagged, never fixed up. Success
 * shows the t.me/addstickers link.
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
                // The original file name decides webm/tgs:
                // their mime types are unreliable across
                // file managers (often octet-stream).
                var displayName = ""
                context.contentResolver.query(
                    uri,
                    arrayOf(
                        android.provider.OpenableColumns.DISPLAY_NAME
                    ),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        displayName =
                            cursor.getString(0) ?: ""
                    }
                }
                val nameExt = displayName
                    .substringAfterLast('.', "")
                    .lowercase()
                val kind = when {
                    nameExt == "webm" || mime == "video/webm" ->
                        "webm"
                    nameExt == "tgs" -> "tgs"
                    nameExt == "png" || nameExt == "webp" ||
                        mime.startsWith("image/") -> "webp"
                    else -> "other"
                }
                val file = File(
                    context.cacheDir,
                    "newsticker_${System.currentTimeMillis()}" +
                        "_${picked.size}." +
                        if (kind == "other") "bin" else kind
                )
                context.contentResolver.openInputStream(uri)
                    ?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                val problem: String?
                val format: String
                when (kind) {
                    "webm" -> {
                        format = "webm"
                        problem = validateWebmSticker(file)
                    }
                    "tgs" -> {
                        format = "tgs"
                        problem = validateTgsSticker(file)
                    }
                    "webp" -> {
                        format = "webp"
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeFile(
                            file.absolutePath, options
                        )
                        val w = options.outWidth
                        val h = options.outHeight
                        val outMime = options.outMimeType ?: mime
                        problem = when {
                            outMime != "image/png" &&
                                outMime != "image/webp" ->
                                "格式是 $outMime，需要 PNG 或 WebP。"
                            w <= 0 || h <= 0 ->
                                "这张图读不出来，换一张试试。"
                            !((w == 512 || h == 512) &&
                                w <= 512 && h <= 512) ->
                                "尺寸是 ${w}×${h}，贴纸需要一边正好 " +
                                    "512 像素、另一边不超过 512" +
                                    "（本版不自动裁剪）。"
                            else -> null
                        }
                    }
                    else -> {
                        format = "webp"
                        problem = "这个文件的格式不支持，" +
                            "需要 PNG、WebP、WebM 或 TGS。"
                    }
                }
                picked.add(
                    PickedSticker(
                        path = file.absolutePath,
                        format = format,
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
                    "三种贴纸都能建：静态选 PNG / WebP 图片" +
                        "（一边正好 512 像素、另一边不超过 512）；" +
                        "视频贴纸选 .webm 文件（不超过 256 KB、" +
                        "3 秒以内、一边 512）；动图选 .tgs 文件" +
                        "（不超过 64 KB、画布 512×512）。" +
                        "本页不裁剪、不转码，不合规格的文件会标出来；" +
                        "一个贴纸包只能用同一种格式。",
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
                Row {
                    Button(
                        onClick = {
                            AppLock.noteExternalLaunch()
                            picker.launch("image/*")
                        }
                    ) {
                        Text("从相册选图片")
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            AppLock.noteExternalLaunch()
                            picker.launch("*/*")
                        }
                    ) {
                        Text("选视频/动图文件")
                    }
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
                                .background(Color(0xFFE0E0E0)),
                            contentAlignment = Alignment.Center
                        ) {
                            // Video / animated files have no
                            // bitmap preview; the tile names
                            // the format instead of staying
                            // blank.
                            if (item.format != "webp") {
                                Text(
                                    item.format.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF616161)
                                )
                            }
                        }
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
                // One set, one format (v0.9.36, C2): mixing
                // static images with webm/tgs files is
                // refused here and again in the store.
                val mixedFormats = picked.isNotEmpty() &&
                    picked.any {
                        it.format != picked.first().format
                    }
                if (mixedFormats) {
                    Text(
                        "现在选的文件格式不统一：一个贴纸包只能用" +
                            "同一种格式，把静态图、视频、动图分开建包。",
                        color = Color(0xFFC62828),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                }
                val canSubmit = title.isNotBlank() &&
                    finalName.length >= 5 &&
                    me.username.isNotEmpty() &&
                    picked.isNotEmpty() &&
                    picked.all { it.problem == null } &&
                    !mixedFormats &&
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
                                    format = it.format,
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
