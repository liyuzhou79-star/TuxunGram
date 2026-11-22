package com.tuxungram.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared graphical building blocks (v0.9.9, W1). Until now the
 * app was styled text on a plain background; these pieces give
 * every screen the same visual language: section titles with an
 * icon chip, grouped cards, icon-led rows, illustrated empty
 * states and a paper-plane brand mark. Layout reference:
 * Nekogram / Nagram settings grouping and row rhythm (design
 * only); the color chips follow this app's own seed themes.
 */

/** A radio-choice label (v0.9.9, W1 sweep): the selected
 *  state is a drawn radio glyph (ring + dot) instead of the
 *  text bullets the option rows used before. */
@Composable
fun RadioText(selected: Boolean, text: String, fontSize: TextUnit = 15.sp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(
                    2.dp,
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Gray
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = fontSize)
    }
}

/** A panel tab label (v0.9.9, W1 sweep): a small filled dot
 *  marks the active tab instead of a text bullet prefix. */
@Composable
fun TabDotText(selected: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(text)
    }
}

/** A section title: the section's icon in a tinted rounded
 *  square, then the title. Used at the head of every settings
 *  group and profile section. */
@Composable
fun SectionTitle(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A grouped card: one settings/profile section lives on a
 *  rounded surface instead of floating on the background. */
@Composable
fun GroupCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            content()
        }
    }
}

/** One icon-led row: leading icon, title, optional gray
 *  subtitle, optional trailing content (current value, switch,
 *  chevron). The whole row is tappable when [onClick] is set. */
@Composable
fun IconRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 9.dp)
    ) {
        Icon(
            icon, contentDescription = null,
            tint = iconTint ?: MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(23.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (subtitle != null) {
                Text(subtitle, color = Color.Gray, fontSize = 12.5.sp)
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

/** Illustrated empty state: a large soft icon in a circle, a
 *  title line and one plain sentence. For empty chat lists,
 *  search results, member lists, file lists and the like. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp)
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(42.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
        )
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle, color = Color.Gray, fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** The brand mark: a white paper plane on a gradient disc —
 *  the same plane as the launcher icon, drawn from the Send
 *  glyph so no image asset is needed. Heads the auth screens
 *  and the guide pages. */
@Composable
fun PlaneBadge(sizeDp: Int = 76) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary
    )
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size((sizeDp * 0.52f).dp)
        )
    }
}

/** Delivery ticks as real graphics (v0.9.9): a single drawn
 *  check for sent, a double check for read — replacing the
 *  "✓" / "✓✓" text glyphs the bubbles used before. Drawn with
 *  canvas strokes so the two checks overlap the way mature
 *  clients draw them. */
@Composable
fun TicksIcon(read: Boolean, color: Color, sizeDp: Int = 16) {
    Canvas(modifier = Modifier.size(sizeDp.dp)) {
        val stroke = size.minDimension * 0.11f
        val h = size.height
        val w = size.width
        fun check(xOff: Float) {
            drawLine(
                color, Offset(xOff + w * 0.04f, h * 0.55f),
                Offset(xOff + w * 0.36f, h * 0.82f),
                strokeWidth = stroke, cap = StrokeCap.Round
            )
            drawLine(
                color, Offset(xOff + w * 0.36f, h * 0.82f),
                Offset(xOff + w * 0.78f, h * 0.22f),
                strokeWidth = stroke, cap = StrokeCap.Round
            )
        }
        if (read) {
            check(-w * 0.16f)
            check(w * 0.22f)
        } else {
            check(w * 0.1f)
        }
    }
}
