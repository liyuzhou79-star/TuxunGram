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
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.fillMaxSize
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

/** One icon-led row. Two renderings share this call shape:
 *  under the official preset (v0.9.20, AA1) the row is the
 *  official settings row — the icon is a bare outline glyph
 *  in the secondary text colour, no coloured tile (the
 *  official settings list carries no icon chips); under the
 *  custom accent mode it keeps the coloured rounded tile
 *  (v0.9.11, A2 — the Nekogram preference-row look).
 *  [tileColor] pins the tile colour in the custom rendering;
 *  an explicit [iconTint] from older call sites is honoured
 *  as the tile colour there and as the glyph colour in the
 *  official rendering; otherwise the tile colour is picked
 *  stably from the title. [danger] paints the title (and the
 *  official-mode glyph) in the error colour. */
@Composable
fun IconRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color? = null,
    tileColor: Color? = null,
    onClick: (() -> Unit)? = null,
    danger: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) {
    val accentMode by UiSettings.accentMode.collectAsState()
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(
            if (onClick != null) {
                Modifier.clickable { onClick() }
            } else {
                Modifier
            }
        )
        // Official settings cell height (v0.9.22, S2):
        // the official settings row measures 50 dp, so
        // the official rendering pads 13 dp around the
        // 24 dp glyph (13 + 24 + 13); the custom tile
        // rendering keeps its 10 dp padding.
        .padding(vertical = if (accentMode == UiSettings.ACCENT_OFFICIAL) 13.dp else 10.dp)
    if (accentMode == UiSettings.ACCENT_OFFICIAL) {
        val palette = OfficialTheme.current()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = rowModifier
        ) {
            Icon(
                icon, contentDescription = null,
                tint = if (danger) {
                    MaterialTheme.colorScheme.error
                } else {
                    iconTint ?: palette.textSecondary
                },
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title, fontSize = 16.sp,
                    color = if (danger) {
                        MaterialTheme.colorScheme.error
                    } else {
                        palette.textPrimary
                    }
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        color = palette.textSecondary,
                        fontSize = 12.5.sp
                    )
                }
            }
            if (trailing != null) {
                trailing()
            }
        }
        return
    }
    val tile = tileColor ?: iconTint ?: tileColorFor(title)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = rowModifier
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
            Text(
                title, fontSize = 15.sp,
                color = if (danger) {
                    MaterialTheme.colorScheme.error
                } else {
                    Color.Unspecified
                }
            )
            if (subtitle != null) {
                Text(subtitle, color = Color.Gray, fontSize = 12.5.sp)
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

/** Official settings section header (v0.9.20, AA1): the
 *  small accent label that sits on the page's gray band
 *  above a settings group — no icon, no tile (the official
 *  settings page heads its sections with a plain coloured
 *  caption). Under the custom accent mode the same slot
 *  renders as a plain bold caption in the theme's primary. */
@Composable
fun SettingsSectionHeader(text: String) {
    val accentMode by UiSettings.accentMode.collectAsState()
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (accentMode == UiSettings.ACCENT_OFFICIAL) {
            OfficialTheme.current().accentText
        } else {
            MaterialTheme.colorScheme.primary
        },
        modifier = Modifier.padding(
            start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp
        )
    )
}

/** Official settings group (v0.9.20, AA1): under the
 *  official preset a full-bleed white block on the page's
 *  graySection band — square corners, content padded 16 dp,
 *  rows inside separated by [SettingsDivider] hairlines.
 *  Under the custom accent mode it delegates to the older
 *  rounded [GroupCard] so the custom look keeps its cards. */
@Composable
fun SettingsGroup(content: @Composable () -> Unit) {
    val accentMode by UiSettings.accentMode.collectAsState()
    if (accentMode == UiSettings.ACCENT_OFFICIAL) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(OfficialTheme.current().windowBg)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            content()
        }
    } else {
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            GroupCard(content)
        }
    }
}

/** Hairline between two rows inside a settings group
 *  (v0.9.20, AA1): the official divider colour under the
 *  official preset, the Material outline elsewhere. */
@Composable
fun SettingsDivider() {
    val accentMode by UiSettings.accentMode.collectAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(
                if (accentMode == UiSettings.ACCENT_OFFICIAL) {
                    OfficialTheme.current().divider
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            )
    )
}

/** Profile info row (v0.9.20, AA2): the official profile
 *  info layout — an outline icon, then the label in small
 *  secondary text ABOVE the value (phone / username / bio
 *  on a profile page). Tappable when [onClick] is set. */
@Composable
fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueAccent: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val palette = OfficialTheme.current()
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
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Icon(
            icon, contentDescription = null,
            tint = palette.textSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                value, fontSize = 15.sp,
                color = if (valueAccent) {
                    palette.accentText
                } else {
                    palette.textPrimary
                }
            )
            Text(
                label,
                color = palette.textSecondary,
                fontSize = 12.5.sp
            )
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

/** Compact empty state (v0.9.46): the small sibling of
 *  [EmptyState] for dialogs, panels and other tight slots
 *  where the full illustration is too tall — a 56 dp disc
 *  with the icon, a title line and one plain sentence.
 *  Replaces the bare gray "nothing here" lines the label
 *  dialogs, the crash-log dialog, the tag-search panel and
 *  similar places used to show. */
@Composable
fun EmptyStateMini(
    icon: ImageVector,
    title: String,
    subtitle: String? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.primaryContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            title, fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle, color = Color.Gray, fontSize = 12.5.sp,
                textAlign = androidx.compose.ui.text.style
                    .TextAlign.Center
            )
        }
    }
}

/** A live-rate sparkline (v0.9.46): the recent samples of
 *  one number (the download centre's total speed) drawn as
 *  a filled polyline, so a rate reads as a curve instead of
 *  one digit that changes every second. The peak of the
 *  visible window sets the scale; an all-zero window draws
 *  a flat line at the bottom, which is the honest picture. */
@Composable
fun Sparkline(
    samples: List<Long>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier) {
        if (samples.size < 2) return@Canvas
        val maxV = (samples.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
        val topPad = 2.dp.toPx()
        val usable = (size.height - topPad * 2).coerceAtLeast(1f)
        val pts = samples.mapIndexed { i, v ->
            Offset(
                i * stepX,
                topPad + usable -
                    (v.toFloat() / maxV.toFloat()) * usable
            )
        }
        val fill = androidx.compose.ui.graphics.Path().apply {
            moveTo(pts.first().x, size.height)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, size.height)
            close()
        }
        drawPath(fill, color.copy(alpha = 0.15f))
        for (i in 0 until pts.size - 1) {
            drawLine(
                color, pts[i], pts[i + 1],
                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round
            )
        }
    }
}

/** A ring progress indicator (v0.9.46): track circle plus
 *  an accent arc for [fraction], with a free centre slot
 *  (a percentage, a level number). Used by the download
 *  centre's total progress and the profile boost level. */
@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    strokeWidthDp: Float = 7f,
    arcColor: Color = MaterialTheme.colorScheme.primary,
    center: @Composable () -> Unit = {}
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidthDp.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(
                size.width - stroke, size.height - stroke
            )
            val topLeft = Offset(inset, inset)
            drawArc(
                color = trackColor,
                startAngle = -90f, sweepAngle = 360f,
                useCenter = false, topLeft = topLeft, size = arcSize,
                style = androidx.compose.ui.graphics.drawscope
                    .Stroke(width = stroke)
            )
            val sweep = 360f * fraction.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(
                    color = arcColor,
                    startAngle = -90f, sweepAngle = sweep,
                    useCenter = false, topLeft = topLeft,
                    size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope
                        .Stroke(
                            width = stroke, cap = StrokeCap.Round
                        )
                )
            }
        }
        center()
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
