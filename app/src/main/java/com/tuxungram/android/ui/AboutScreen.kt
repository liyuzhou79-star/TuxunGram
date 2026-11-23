package com.tuxungram.android.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.BuildConfig
import com.tuxungram.android.R

/**
 * About page (v0.9.22, S3): opened from the bottom row of
 * the Me page, below the changelog. The layout is the
 * owner's own sketch, top to bottom: the name "Tuxun"
 * centred large, the support image, a centred
 * "-支持我们-" caption under it, then three plain info
 * lines — version (read live from the build), author and
 * the GitHub address, which opens in the browser. Colours
 * follow the shared OfficialTheme palette; nothing here
 * talks to the network by itself.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val palette = OfficialTheme.current()
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().background(palette.windowBg)) {
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
                "关于",
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))
            Text(
                "Tuxun",
                fontSize = 34.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color = palette.textPrimary
            )
            Spacer(Modifier.height(28.dp))
            Image(
                painter = painterResource(R.drawable.about_support_qr),
                contentDescription = "支持我们",
                modifier = Modifier
                    .width(280.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "-支持我们-",
                fontSize = 15.sp,
                letterSpacing = 1.sp,
                color = palette.textSecondary
            )
            Spacer(Modifier.height(36.dp))
            // Info lines (spacing / type polish only,
            // v0.9.46): the layout is the owner's own —
            // same order, same lines — but each line now
            // reads label (secondary) + value (primary)
            // the way the Github line already did, so the
            // three lines share one hierarchy.
            Column(modifier = Modifier.fillMaxWidth()) {
                Row {
                    Text(
                        "版本号：",
                        fontSize = 15.sp,
                        color = palette.textSecondary
                    )
                    Text(
                        BuildConfig.VERSION_NAME,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    Text(
                        "我们的作者：",
                        fontSize = 15.sp,
                        color = palette.textSecondary
                    )
                    Text(
                        "LZQ",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    Text(
                        "Github：",
                        fontSize = 15.sp,
                        color = palette.textSecondary
                    )
                    Text(
                        "https://github.com/liyuzhou79-star/TuxunGram",
                        fontSize = 15.sp,
                        color = palette.accentText,
                        modifier = Modifier.clickable {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(
                                        "https://github.com/liyuzhou79-star/TuxunGram"
                                    )
                                )
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.height(36.dp))
        }
    }
}
