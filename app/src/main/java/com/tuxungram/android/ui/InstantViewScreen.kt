package com.tuxungram.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.InstantViewStore
import com.tuxungram.android.td.IvBlock
import com.tuxungram.android.td.IvState
import com.tuxungram.android.td.IvText
import com.tuxungram.android.td.LinkRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Instant View article page (v0.9.26, S1): renders the
 * blocks InstantViewStore parsed out of
 * getWebPageInstantView — title/author/paragraphs with the
 * chat rich-text renderer, photos inline, quotes and lists
 * structured. A load failure is stated plainly with a
 * browser fallback button; nothing here pretends to be the
 * article when it is not.
 */
@Composable
fun InstantViewScreen(url: String, onClose: () -> Unit) {
    val state by InstantViewStore.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(url) { InstantViewStore.open(url) }
    BackHandler {
        InstantViewStore.close()
        onClose()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                InstantViewStore.close()
                onClose()
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                "即时预览",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { LinkRouter.open(context, url) }) {
                Icon(
                    GlobeIcon,
                    contentDescription = "用浏览器打开"
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        when (val s = state) {
            is IvState.Loading, IvState.Idle -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "正在加载即时预览…",
                            color = MaterialTheme.colorScheme
                                .onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            is IvState.Failed -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {
                        Text(
                            s.reason,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme
                                .onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = {
                            LinkRouter.open(context, s.url)
                        }) {
                            Text("用浏览器打开原文")
                        }
                    }
                }
            }
            is IvState.Loaded -> {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .lazyListScrollbar(listState)
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    items(s.blocks) { block ->
                        IvBlockView(block)
                        Spacer(Modifier.height(10.dp))
                    }
                    if (s.viewCount > 0) {
                        item {
                            Text(
                                "阅读量 ${s.viewCount}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IvRich(
    rich: IvText?,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: androidx.compose.ui.graphics.Color =
        androidx.compose.ui.graphics.Color.Unspecified
) {
    if (rich == null || rich.text.isEmpty()) return
    RichEntityText(
        text = rich.text,
        entities = rich.entities,
        fontSize = fontSize,
        color = color
    )
}

@Composable
private fun IvBlockView(block: IvBlock) {
    val context = LocalContext.current
    when (block.kind) {
        "title" -> RichEntityText(
            block.rich?.text ?: "",
            block.rich?.entities ?: emptyList(),
            fontSize = 24.sp
        )
        "subtitle" -> RichEntityText(
            block.rich?.text ?: "",
            block.rich?.entities ?: emptyList(),
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        "author" -> {
            val dateText = if (block.dateSeconds > 0) {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(Date(block.dateSeconds * 1000L))
            } else {
                ""
            }
            val author = block.rich?.text ?: ""
            Text(
                listOf(author, dateText)
                    .filter { it.isNotEmpty() }.joinToString(" · "),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        "kicker" -> RichEntityText(
            block.rich?.text ?: "",
            block.rich?.entities ?: emptyList(),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary
        )
        "header" -> RichEntityText(
            block.rich?.text ?: "",
            block.rich?.entities ?: emptyList(),
            fontSize = 20.sp
        )
        "subheader" -> RichEntityText(
            block.rich?.text ?: "",
            block.rich?.entities ?: emptyList(),
            fontSize = 17.sp
        )
        "paragraph" -> IvRich(block.rich, 15.5.sp)
        "footer" -> RichEntityText(
            block.rich?.text ?: "",
            block.rich?.entities ?: emptyList(),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        "pre" -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp)
                )
                .padding(10.dp)
        ) {
            Text(
                block.rich?.text ?: "",
                fontSize = 13.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily
                    .Monospace
            )
        }
        "divider" -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        "quote", "pullquote" -> {
            // Accent bar stretches with the text (v0.9.41,
            // H2) — was a fixed 48.dp, same fix as the
            // rich-message bubble renderer.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(2.dp)
                        )
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    block.rich?.let { IvRich(it, 15.sp) }
                    block.children.forEach { child ->
                        IvBlockView(child)
                        Spacer(Modifier.height(6.dp))
                    }
                    block.credit?.let { credit ->
                        Text(
                            credit.text,
                            fontSize = 12.5.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme
                                .onSurfaceVariant
                        )
                    }
                }
            }
        }
        "list" -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                block.listItems.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val marker = when {
                            item.checked != null ->
                                if (item.checked) "☑ " else "☐ "
                            item.label.isNotEmpty() ->
                                item.label + " "
                            else -> "• "
                        }
                        Text(marker, fontSize = 15.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            item.blocks.forEach { child ->
                                IvBlockView(child)
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
        "photo" -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (block.photoFileId != 0) {
                    IvPhoto(block.photoFileId)
                }
                block.caption?.let { caption ->
                    Spacer(Modifier.height(4.dp))
                    RichEntityText(
                        caption.text,
                        caption.entities,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme
                            .onSurfaceVariant
                    )
                }
            }
        }
        "details" -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                block.rich?.let {
                    RichEntityText(it.text, it.entities, fontSize = 16.sp)
                }
                Spacer(Modifier.height(4.dp))
                block.children.forEach { child ->
                    IvBlockView(child)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        "group" -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                block.children.forEach { child ->
                    IvBlockView(child)
                    Spacer(Modifier.height(8.dp))
                }
                block.caption?.let { caption ->
                    RichEntityText(
                        caption.text,
                        caption.entities,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme
                            .onSurfaceVariant
                    )
                }
            }
        }
        "table" -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(8.dp)
            ) {
                block.rich?.let {
                    if (it.text.isNotEmpty()) {
                        RichEntityText(
                            it.text, it.entities, fontSize = 14.sp
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
                block.tableRows.forEach { row ->
                    Text(
                        row.joinToString(" ｜ ") { it.text },
                        fontSize = 13.5.sp
                    )
                    Spacer(Modifier.height(3.dp))
                }
            }
        }
        "article" -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (block.linkUrl.isNotEmpty()) {
                            LinkRouter.open(context, block.linkUrl)
                        }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📰 ",
                    fontSize = 15.sp
                )
                RichEntityText(
                    block.rich?.text ?: "",
                    block.rich?.entities ?: emptyList(),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        "weblink" -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "嵌入内容（点此在浏览器打开）",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        if (block.linkUrl.isNotEmpty()) {
                            LinkRouter.open(context, block.linkUrl)
                        }
                    }
                )
                block.caption?.let { caption ->
                    RichEntityText(
                        caption.text,
                        caption.entities,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme
                            .onSurfaceVariant
                    )
                }
            }
        }
        "file" -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(block.rich?.text ?: "📎 附件", fontSize = 14.sp)
                block.caption?.let { caption ->
                    RichEntityText(
                        caption.text,
                        caption.entities,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme
                            .onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** One article photo: decode the downloaded file (the store
 *  swaps paths in as downloads land), placeholder while it
 *  is still in flight. */
@Composable
private fun IvPhoto(fileId: Int) {
    val paths by InstantViewStore.photoPaths.collectAsState()
    val path = paths[fileId]
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null, path
    ) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) { decodeCached(path, 2048) }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "图片加载中…",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
