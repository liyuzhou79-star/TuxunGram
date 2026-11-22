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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tuxungram.android.td.UiSettings
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

/** The settings-tile palette (v0.9.11, A2): Nekogram-style
 *  coloured icon tiles — every settings/profile row and
 *  section head carries its icon on a saturated rounded
 *  square instead of a bare glyph. The colours are design
 *  constants in the same family the reference clients use
 *  for their preference tiles; a row without an explicit
 *  colour gets a stable one picked by its title, so the
 *  page reads colourful but never random between visits. */
val TILE_PALETTE: List<Color> = listOf(
    Color(0xFF5B8DEF), // blue
    Color(0xFF61C167), // green
    Color(0xFFF2994A), // orange
    Color(0xFF9B6DD6), // purple
    Color(0xFFE95E8B), // pink
    Color(0xFF3DB6C6), // teal
    Color(0xFFE0B34C), // amber
    Color(0xFF7A8CA3)  // slate
)

fun tileColorFor(key: String): Color =
    TILE_PALETTE[(key.hashCode() and Int.MAX_VALUE) %
        TILE_PALETTE.size]

/** A section title: the section's icon in a coloured tile,
 *  then the title. Used at the head of every settings group
 *  and profile section. */
@Composable
fun SectionTitle(
    icon: ImageVector,
    text: String,
    tileColor: Color? = null
) {
    val tile = tileColor ?: tileColorFor(text)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tile.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, contentDescription = null,
                tint = tile,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        // Section label (v0.9.17, X7): under the official
        // preset it takes the official blue-header color
        // (windowBackgroundWhiteBlueText family) at the
        // official section size.
        val accentMode by UiSettings.accentMode.collectAsState()
        Text(
            text,
            fontSize = if (accentMode == UiSettings.ACCENT_OFFICIAL) {
                15.sp
            } else {
                17.sp
            },
            fontWeight = FontWeight.SemiBold,
            color = if (accentMode == UiSettings.ACCENT_OFFICIAL) {
                OfficialTheme.current().accentText
            } else {
                Color.Unspecified
            }
        )
    }
}

/** A grouped card: one settings/profile section lives on a
 *  rounded surface instead of floating on the background.
 *  Under the official preset (v0.9.17, X7) the card takes
 *  the official window background so sections read as the
 *  official white blocks on the graySection page band. */
@Composable
fun GroupCard(content: @Composable () -> Unit) {
    val accentMode by UiSettings.accentMode.collectAsState()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (accentMode == UiSettings.ACCENT_OFFICIAL) {
            OfficialTheme.current().windowBg
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            content()
        }
    }
}

/** One icon-led row: the icon sits in a coloured rounded
 *  tile (v0.9.11, A2 — the Nekogram preference-row look),
 *  then title, optional gray subtitle, optional trailing
 *  content (current value, switch, chevron). The whole row
 *  is tappable when [onClick] is set. [tileColor] pins the
 *  tile colour; an explicit [iconTint] from older call sites
 *  is honoured as the tile colour; otherwise the colour is
 *  picked stably from the title. */
@Composable
fun IconRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color? = null,
    tileColor: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val tile = tileColor ?: iconTint ?: tileColorFor(title)
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
            .padding(vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(tile.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, contentDescription = null,
                tint = tile,
                modifier = Modifier.size(20.dp)
            )
        }
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

/** Illustrated empty state (v0.9.11, A2): a composed
 *  illustration instead of one flat glyph — a soft halo, the
 *  main disc with the large icon, and a small brand badge
 *  overlapping the disc's corner — then a title line and one
 *  plain sentence. For empty chat lists, search results,
 *  member lists, file lists and the like. */
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
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primary
                            .copy(alpha = 0.07f)
                    )
            )
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
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
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

/** A graphical header banner (v0.9.11, A2): a seed-colour
 *  gradient wash with program-drawn deco circles and a faint
 *  oversized plane watermark — no image assets. Heads the Me
 *  tab and the profile page; [content] is laid out over the
 *  banner and owns its padding and text colours. */
@Composable
fun HeaderBanner(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(primary, tertiary)))
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                Color.White.copy(alpha = 0.10f),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.92f, size.height * 0.05f)
            )
            drawCircle(
                Color.White.copy(alpha = 0.07f),
                radius = size.minDimension * 0.38f,
                center = Offset(size.width * 0.06f, size.height * 1.05f)
            )
            drawCircle(
                Color.White.copy(alpha = 0.05f),
                radius = size.minDimension * 0.22f,
                center = Offset(size.width * 0.68f, size.height * 0.95f)
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.13f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp)
                .size(110.dp)
        )
        content()
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
        // Thinner stroke since v0.9.17 (X6): the official
        // ticks are light line checks, not bold marks.
        val stroke = size.minDimension * 0.09f
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
