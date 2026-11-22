package com.tuxungram.android.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val scale by animateFloatAsState(
        if (shown) 1f else 0.85f, Motion.tween(150),
        label = "menuScale"
    )
    val alpha by animateFloatAsState(
        if (shown) 1f else 0f, Motion.tween(150),
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
                                    .size(36.dp)
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
