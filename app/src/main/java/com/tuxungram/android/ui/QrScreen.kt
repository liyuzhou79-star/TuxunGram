package com.tuxungram.android.ui

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.tuxungram.android.td.MeStore
import com.tuxungram.android.td.QrCode
import java.io.File

/** Render [text] as a QR bitmap (v0.9.24, Q2): modules from
 *  the in-repo encoder, a four-module quiet zone, black on
 *  white exactly like every other client's code card. Null
 *  when the payload fits no supported version. */
fun qrBitmap(text: String, modulePx: Int = 14): Bitmap? {
    val matrix = QrCode.encode(text) ?: return null
    val size = matrix.size
    val border = 4
    val px = (size + border * 2) * modulePx
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(AndroidColor.WHITE)
    for (y in 0 until size) {
        for (x in 0 until size) {
            if (!matrix[y][x]) continue
            for (dy in 0 until modulePx) {
                for (dx in 0 until modulePx) {
                    bitmap.setPixel(
                        (x + border) * modulePx + dx,
                        (y + border) * modulePx + dy,
                        AndroidColor.BLACK
                    )
                }
            }
        }
    }
    return bitmap
}

/** A QR code for [text] at a fixed display size; a plain
 *  fallback line when the payload cannot be encoded. */
@Composable
fun QrImage(text: String, sizeDp: Int) {
    val bitmap = remember(text) { qrBitmap(text) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "二维码",
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(12.dp))
        )
    } else {
        Text(
            "二维码生成失败，链接太长了。",
            color = Color.Gray, fontSize = 13.sp
        )
    }
}

/**
 * "My QR code" page (v0.9.24, Q2), opened from the Me tab:
 * the code encodes the account's own https://t.me/<username>
 * link, generated fully on-device (td/QrCode.kt — no library,
 * no network). Save writes a PNG into Pictures/TuxunGram via
 * MediaStore; share goes through the app's FileProvider.
 * Without a username there is no public link to encode, so
 * the page says so and points at the profile editor instead
 * of inventing a code.
 */
@Composable
fun MyQrScreen(onBack: () -> Unit, onEditProfile: () -> Unit) {
    BackHandler { onBack() }
    val palette = OfficialTheme.current()
    val context = LocalContext.current
    val me by MeStore.me.collectAsState()
    val username = me.username
    val link = if (username.isNotEmpty()) "https://t.me/$username" else ""
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.actionBarBg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = palette.actionBarIcon
                )
            }
            Text(
                "我的二维码",
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                color = palette.actionBarTitle
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(palette.divider)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (username.isEmpty()) {
                Text(
                    "你还没有用户名，别人没法通过链接找到你，" +
                        "也就没有二维码可生成。",
                    color = palette.textPrimary, fontSize = 15.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "先去设置一个用户名，回来这里就能看到你的二维码。",
                    color = Color.Gray, fontSize = 13.sp
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onEditProfile) { Text("去设置用户名") }
            } else {
                Text(
                    me.displayName,
                    color = palette.textPrimary,
                    fontSize = 19.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "@$username",
                    color = palette.accentText, fontSize = 14.sp
                )
                Spacer(Modifier.height(20.dp))
                QrImage(link, 240)
                Spacer(Modifier.height(16.dp))
                Text(
                    link,
                    color = Color.Gray, fontSize = 13.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "别人用 Telegram 扫这个码，就能打开你的主页。",
                    color = Color.Gray, fontSize = 13.sp
                )
                Spacer(Modifier.height(24.dp))
                Row {
                    Button(
                        onClick = {
                            val bmp = qrBitmap(link)
                            val ok = bmp != null && saveQrToGallery(
                                context, bmp
                            )
                            Toast.makeText(
                                context,
                                if (ok) {
                                    "已保存到相册（Pictures/TuxunGram）"
                                } else {
                                    "保存失败，再试一次。"
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("保存到相册") }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = { shareQr(context, link) },
                        modifier = Modifier.weight(1f)
                    ) { Text("分享") }
                }
            }
        }
    }
}

private fun saveQrToGallery(
    context: android.content.Context,
    bitmap: Bitmap
): Boolean {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "tuxungram-qr.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(
            MediaStore.Images.Media.RELATIVE_PATH,
            "Pictures/TuxunGram"
        )
    }
    val uri = context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
    ) ?: return false
    return try {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: false
    } catch (e: Exception) {
        false
    }
}

private fun shareQr(context: android.content.Context, link: String) {
    val bitmap = qrBitmap(link)
    if (bitmap == null) {
        Toast.makeText(
            context, "二维码生成失败，分享不了。", Toast.LENGTH_SHORT
        ).show()
        return
    }
    try {
        val file = File(context.cacheDir, "my-qr.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(
            context, "com.tuxungram.android.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "分享我的二维码")
        )
    } catch (e: Exception) {
        Toast.makeText(
            context, "分享失败，再试一次。", Toast.LENGTH_SHORT
        ).show()
    }
}
