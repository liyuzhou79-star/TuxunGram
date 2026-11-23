package com.tuxungram.android.ui

import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.tuxungram.android.td.CustomEmojiStore
import com.tuxungram.android.td.ViewersStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One row of the floating message menu (v0.9.20, AA4). */
data class MessageMenuEntry(
    val icon: ImageVector,
    val label: String,
    val danger: Boolean = false,
    val onClick: () -> Unit
)

/**
 * The official-style floating message menu (v0.9.20, AA4),
 * replacing the Material dropdown the bubbles used before.
 * It pops next to the long-pressed bubble — under it when
 * there is room, flipped above near the bottom of the
 * screen, aligned to the bubble's own side — on a white
 * 14 dp-rounded card with a soft shadow. When the chat
 * offers quick reactions they ride on top of the menu as
 * the official reaction strip (the same set the old
 * reaction dialog offered, one tap toggles and closes);
 * the entries below are single icon + label rows, danger
 * rows in the theme's error color. Entry click handlers
 * close the menu themselves, exactly like the dropdown
 * items did, so every existing action keeps its behavior.
 * Structure and measurements follow the official client's
 * message popup (design only, no official code).
 */
@Composable
fun OfficialMessageMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorOnRight: Boolean,
    reactions: List<String> = emptyList(),
    onReaction: (String) -> Unit = {},
    entries: List<MessageMenuEntry>
) {
    if (!expanded) return
    val palette = OfficialTheme.current()
    val density = LocalDensity.current
    val provider = remember(anchorOnRight, density) {
        MessageMenuPositionProvider(
            anchorOnRight,
            with(density) { 4.dp.roundToPx() }
        )
    }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    // Popup motion (v0.9.29, V2): the card scales up from
    // 0.85 at its anchor by the bubble and fades in on the
    // shared MENU spec (in slower than out, like the
    // official client's message menu).
    val menuSpec: TweenSpec<Float> = if (shown) {
        Motion.tween(Motion.MENU_IN)
    } else {
        Motion.tween(Motion.MENU_OUT)
    }
    val scale by animateFloatAsState(
        if (shown) 1f else 0.85f, menuSpec,
        label = "menuScale"
    )
    val alpha by animateFloatAsState(
        if (shown) 1f else 0f, menuSpec,
        label = "menuAlpha"
    )
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = palette.windowBg,
            shadowElevation = 8.dp,
            modifier = Modifier
                .width(236.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    transformOrigin = TransformOrigin(
                        if (anchorOnRight) 1f else 0f, 0.5f
                    )
                }
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                if (reactions.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {
                        reactions.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    // 40dp (v0.9.33, Z5): up
                                    // from 36dp; the strip's
                                    // space-between layout caps
                                    // how far this can grow.
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable { onReaction(emoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 22.sp)
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 14.dp, vertical = 4.dp
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(palette.divider)
                        )
                    }
                }
                entries.forEach { entry ->
                    val tint = if (entry.danger) {
                        MaterialTheme.colorScheme.error
                    } else {
                        palette.textPrimary
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { entry.onClick() }
                            .padding(
                                horizontal = 14.dp, vertical = 10.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            entry.icon, contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            entry.label,
                            fontSize = 15.sp,
                            color = tint
                        )
                    }
                }
            }
        }
    }
}

/**
 * Anchors the floating menu to the bubble's side: below
 * the bubble when it fits, flipped above it near the
 * screen bottom, always kept inside the window margins.
 */
private class MessageMenuPositionProvider(
    private val anchorOnRight: Boolean,
    private val marginPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maxX = (
            windowSize.width - popupContentSize.width - marginPx
            ).coerceAtLeast(marginPx)
        val x = if (anchorOnRight) {
            anchorBounds.right - popupContentSize.width
        } else {
            anchorBounds.left
        }.coerceIn(marginPx, maxX)
        val below = anchorBounds.bottom + marginPx
        val y = if (
            below + popupContentSize.height <=
            windowSize.height - marginPx
        ) {
            below
        } else {
            (anchorBounds.top - marginPx - popupContentSize.height)
                .coerceAtLeast(marginPx)
        }
        return IntOffset(x, y)
    }
}

/**
 * Per-user reaction list dialog (v0.9.39, F1), shared by
 * the main chat bubble and the thread / forum bubble: who
 * reacted to one message, with what, and when — over
 * ViewersStore's getMessageAddedReactions paging. The
 * caller opens it only when the message's
 * can_get_added_reactions flag is set and kicks the load
 * itself. Reaction glyphs stay honest: plain emoji render
 * as text, custom emoji render their downloaded sticker
 * file (a text label until it lands — never a substitute
 * emoji), and paid reactions are labelled "付费回应"
 * with a star instead of a drawn glyph.
 */
@Composable
fun AddedReactionsDialog(onDismiss: () -> Unit) {
    val rows by ViewersStore.addedReactions.collectAsState()
    val total by ViewersStore.addedTotal.collectAsState()
    val loading by ViewersStore.addedLoading.collectAsState()
    val error by ViewersStore.addedError.collectAsState()
    val exhausted by ViewersStore.addedExhausted.collectAsState()
    val cePaths by CustomEmojiStore.paths.collectAsState()
    LaunchedEffect(rows) {
        val ids = rows
            .filter { it.kind == "custom" && it.customEmojiId != 0L }
            .map { it.customEmojiId }
            .distinct()
        if (ids.isNotEmpty()) CustomEmojiStore.ensure(ids)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("回应名单") },
        text = {
            when {
                loading && rows.isEmpty() -> Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("正在查…", fontSize = 14.sp)
                }
                error != null && rows.isEmpty() -> Text(
                    error!!, fontSize = 14.sp
                )
                rows.isEmpty() -> Text(
                    "还没有人给这条消息加回应。",
                    fontSize = 14.sp
                )
                else -> Column {
                    if (total > 0) {
                        Text(
                            "共 $total 条回应",
                            fontSize = 12.sp, color = Color.Gray,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 380.dp)
                    ) {
                        items(
                            rows,
                            key = {
                                "${it.senderId}_${it.kind}_" +
                                    "${it.emoji}_${it.customEmojiId}"
                            }
                        ) { row ->
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                            ) {
                                if (row.photoPath != null) {
                                    AvatarImage(
                                        path = row.photoPath!!,
                                        sizeDp = 36
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                Color(0xFFB0BEC5),
                                                CircleShape
                                            ),
                                        contentAlignment =
                                            Alignment.Center
                                    ) {
                                        Text(
                                            row.name.trim()
                                                .firstOrNull()
                                                ?.toString() ?: "?",
                                            color = Color.White,
                                            fontWeight =
                                                androidx.compose.ui.text.font.FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        row.name.ifEmpty { "…" },
                                        fontSize = 15.sp,
                                        maxLines = 1
                                    )
                                    if (row.date > 0) {
                                        Text(
                                            formatViewDate(row.date),
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                when (row.kind) {
                                    "emoji" -> Text(
                                        row.emoji, fontSize = 20.sp
                                    )
                                    "custom" -> {
                                        val path = cePaths[
                                            row.customEmojiId
                                        ]
                                        if (path != null) {
                                            val bitmap by produceState<
                                                android.graphics.Bitmap?
                                            >(initialValue = null, path) {
                                                value = withContext(
                                                    Dispatchers.IO
                                                ) {
                                                    decodeCached(
                                                        path, 128
                                                    )
                                                }
                                            }
                                            val bmp = bitmap
                                            if (bmp != null) {
                                                Image(
                                                    bitmap =
                                                        bmp.asImageBitmap(),
                                                    contentDescription =
                                                        null,
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                )
                                            } else {
                                                Text(
                                                    "自定义表情",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        } else {
                                            Text(
                                                "自定义表情",
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                    else -> Text(
                                        "⭐ 付费回应",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                        if (!exhausted) {
                            item(key = "added_more") {
                                TextButton(
                                    onClick = {
                                        ViewersStore
                                            .loadMoreAddedReactions()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (loading) {
                                            "正在加载…"
                                        } else {
                                            "加载更多"
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (error != null) {
                        Text(
                            error!!,
                            fontSize = 12.sp, color = Color.Gray,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
