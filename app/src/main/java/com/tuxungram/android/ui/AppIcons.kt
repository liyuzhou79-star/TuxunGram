package com.tuxungram.android.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn semantic icons (v0.9.9, W1). The app's only icon
 * dependency is material-icons-core, whose Filled set covers
 * barely fifty glyphs — no pin, no bell-slash, no paperclip,
 * no poll bars, no people. Rather than pulling the heavy
 * extended icon library, the missing glyphs are drawn here as
 * plain ImageVector paths on the same 24x24 grid, in the same
 * filled style. They cost a few hundred bytes each and R8
 * keeps only the ones actually referenced.
 *
 * Layout references for where these are used: Nekogram /
 * Nagram settings rows and exteraGram chat rows (design only).
 */
private fun icon(
    name: String,
    evenOdd: Boolean = false,
    body: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit
): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
            pathBuilder = body
        )
    }.build()

/** Pushpin (pinned chat / pin message). */
val PinIcon: ImageVector = icon("Pin") {
    moveTo(15f, 2.5f)
    lineTo(21.5f, 9f)
    lineTo(20f, 10.5f)
    lineTo(17f, 7.5f)
    lineTo(17f, 12.5f)
    lineTo(19f, 14.5f)
    lineTo(19f, 16.5f)
    lineTo(13f, 16.5f)
    lineTo(13f, 21.5f)
    lineTo(11f, 21.5f)
    lineTo(11f, 16.5f)
    lineTo(5f, 16.5f)
    lineTo(5f, 14.5f)
    lineTo(7f, 12.5f)
    lineTo(7f, 7.5f)
    lineTo(4f, 10.5f)
    lineTo(2.5f, 9f)
    close()
}

/** Bell with a slash (muted chat). */
val MuteBellIcon: ImageVector = icon("MuteBell") {
    moveTo(12f, 3.2f)
    arcTo(5.3f, 5.3f, 0f, false, true, 17.3f, 8.5f)
    lineTo(17.3f, 11.6f)
    lineTo(19.3f, 15.5f)
    lineTo(4.7f, 15.5f)
    lineTo(6.7f, 11.6f)
    lineTo(6.7f, 8.5f)
    arcTo(5.3f, 5.3f, 0f, false, true, 12f, 3.2f)
    close()
    moveTo(9.8f, 18.3f)
    horizontalLineTo(14.2f)
    arcTo(2.3f, 2.3f, 0f, false, true, 9.8f, 18.3f)
    close()
    moveTo(3.2f, 2.4f)
    lineTo(21.6f, 19.2f)
    lineTo(20.2f, 20.8f)
    lineTo(1.8f, 4f)
    close()
}

/** Archive box. */
val ArchiveIcon: ImageVector = icon("Archive", evenOdd = true) {
    moveTo(3f, 3f)
    horizontalLineTo(21f)
    verticalLineTo(8f)
    horizontalLineTo(3f)
    close()
    moveTo(4f, 9.5f)
    horizontalLineTo(20f)
    verticalLineTo(19f)
    arcTo(2f, 2f, 0f, false, true, 18f, 21f)
    horizontalLineTo(6f)
    arcTo(2f, 2f, 0f, false, true, 4f, 19f)
    close()
    moveTo(10f, 12.5f)
    horizontalLineTo(14f)
    verticalLineTo(15.5f)
    horizontalLineTo(10f)
    close()
}

/** Folder. */
val FolderIcon: ImageVector = icon("Folder") {
    moveTo(3f, 5f)
    arcTo(2f, 2f, 0f, false, true, 5f, 3f)
    horizontalLineTo(9.5f)
    lineTo(11.5f, 5f)
    horizontalLineTo(19f)
    arcTo(2f, 2f, 0f, false, true, 21f, 7f)
    verticalLineTo(18f)
    arcTo(2f, 2f, 0f, false, true, 19f, 20f)
    horizontalLineTo(5f)
    arcTo(2f, 2f, 0f, false, true, 3f, 18f)
    close()
}

/** Picture / photo. */
val ImageIcon: ImageVector = icon("Image", evenOdd = true) {
    moveTo(4f, 4f)
    horizontalLineTo(20f)
    arcTo(2f, 2f, 0f, false, true, 22f, 6f)
    verticalLineTo(18f)
    arcTo(2f, 2f, 0f, false, true, 20f, 20f)
    horizontalLineTo(4f)
    arcTo(2f, 2f, 0f, false, true, 2f, 18f)
    verticalLineTo(6f)
    arcTo(2f, 2f, 0f, false, true, 4f, 4f)
    close()
    moveTo(8.5f, 7f)
    arcTo(1.8f, 1.8f, 0f, false, true, 8.5f, 10.6f)
    arcTo(1.8f, 1.8f, 0f, false, true, 8.5f, 7f)
    close()
    moveTo(19f, 17.5f)
    lineTo(15f, 12.5f)
    lineTo(12f, 16f)
    lineTo(9.5f, 13f)
    lineTo(5f, 17.5f)
    close()
}

/** Paperclip (attach file). */
val ClipIcon: ImageVector = icon("Clip") {
    moveTo(20.8f, 12.2f)
    lineTo(12.6f, 20.4f)
    arcTo(5.6f, 5.6f, 0f, true, true, 4.7f, 12.5f)
    lineTo(12.8f, 4.4f)
    arcTo(3.7f, 3.7f, 0f, true, true, 18f, 9.6f)
    lineTo(10.2f, 17.4f)
    arcTo(1.85f, 1.85f, 0f, true, true, 7.6f, 14.8f)
    lineTo(14.9f, 7.5f)
    lineTo(16.2f, 8.8f)
    lineTo(8.9f, 16.1f)
    arcTo(3.7f, 3.7f, 0f, true, false, 14.1f, 21.3f)
    lineTo(22.2f, 13.2f)
    close()
}

/** Microphone (voice messages / recording). */
val MicIcon: ImageVector = icon("Mic") {
    moveTo(12f, 14.5f)
    arcTo(3f, 3f, 0f, false, true, 15f, 11.5f)
    verticalLineTo(6f)
    arcTo(3f, 3f, 0f, false, false, 9f, 6f)
    verticalLineTo(11.5f)
    arcTo(3f, 3f, 0f, false, true, 12f, 14.5f)
    close()
    moveTo(17f, 11f)
    arcTo(5f, 5f, 0f, false, true, 7f, 11f)
    horizontalLineTo(5f)
    arcTo(7f, 7f, 0f, false, false, 11f, 17.9f)
    verticalLineTo(21f)
    horizontalLineTo(13f)
    verticalLineTo(17.9f)
    arcTo(7f, 7f, 0f, false, false, 19f, 11f)
    close()
}

/** Poll (bar chart). */
val PollIcon: ImageVector = icon("Poll") {
    moveTo(4f, 11f)
    horizontalLineTo(7.5f)
    verticalLineTo(19f)
    horizontalLineTo(4f)
    close()
    moveTo(10.25f, 5f)
    horizontalLineTo(13.75f)
    verticalLineTo(19f)
    horizontalLineTo(10.25f)
    close()
    moveTo(16.5f, 8f)
    horizontalLineTo(20f)
    verticalLineTo(19f)
    horizontalLineTo(16.5f)
    close()
}

/** Chain link. */
val LinkIcon: ImageVector = icon("Link") {
    moveTo(10.6f, 13.4f)
    arcTo(4f, 4f, 0f, false, false, 16.3f, 13.4f)
    lineTo(19.3f, 10.4f)
    arcTo(4f, 4f, 0f, true, false, 13.6f, 4.7f)
    lineTo(12.1f, 6.2f)
    lineTo(13.5f, 7.6f)
    lineTo(15f, 6.1f)
    arcTo(2f, 2f, 0f, true, true, 17.9f, 9f)
    lineTo(14.9f, 12f)
    arcTo(2f, 2f, 0f, false, true, 12f, 12f)
    close()
    moveTo(13.4f, 10.6f)
    arcTo(4f, 4f, 0f, false, false, 7.7f, 10.6f)
    lineTo(4.7f, 13.6f)
    arcTo(4f, 4f, 0f, true, false, 10.4f, 19.3f)
    lineTo(11.9f, 17.8f)
    lineTo(10.5f, 16.4f)
    lineTo(9f, 17.9f)
    arcTo(2f, 2f, 0f, true, true, 6.1f, 15f)
    lineTo(9.1f, 12f)
    arcTo(2f, 2f, 0f, false, true, 12f, 12f)
    close()
}

/** Download arrow into a tray. */
val DownloadIcon: ImageVector = icon("Download") {
    moveTo(11f, 3f)
    horizontalLineTo(13f)
    verticalLineTo(12.2f)
    lineTo(16.3f, 8.9f)
    lineTo(17.7f, 10.3f)
    lineTo(12f, 16f)
    lineTo(6.3f, 10.3f)
    lineTo(7.7f, 8.9f)
    lineTo(11f, 12.2f)
    close()
    moveTo(5f, 18f)
    horizontalLineTo(19f)
    verticalLineTo(21f)
    horizontalLineTo(5f)
    close()
}

/** Eye (privacy / visibility). */
val EyeIcon: ImageVector = icon("Eye", evenOdd = true) {
    moveTo(12f, 4.5f)
    curveTo(6f, 4.5f, 1.7f, 12f, 1.7f, 12f)
    curveTo(1.7f, 12f, 6f, 19.5f, 12f, 19.5f)
    curveTo(18f, 19.5f, 22.3f, 12f, 22.3f, 12f)
    curveTo(22.3f, 12f, 18f, 4.5f, 12f, 4.5f)
    close()
    moveTo(12f, 7f)
    arcTo(5f, 5f, 0f, false, true, 12f, 17f)
    arcTo(5f, 5f, 0f, false, true, 12f, 7f)
    close()
    moveTo(12f, 9.5f)
    arcTo(2.5f, 2.5f, 0f, false, true, 12f, 14.5f)
    arcTo(2.5f, 2.5f, 0f, false, true, 12f, 9.5f)
    close()
}

/** Shield (security / admin rights). */
val ShieldIcon: ImageVector = icon("Shield", evenOdd = true) {
    moveTo(12f, 2f)
    lineTo(20f, 4.8f)
    verticalLineTo(11f)
    curveTo(20f, 16f, 16.6f, 20.3f, 12f, 21.9f)
    curveTo(7.4f, 20.3f, 4f, 16f, 4f, 11f)
    verticalLineTo(4.8f)
    close()
    moveTo(10.8f, 15.6f)
    lineTo(7.6f, 12.4f)
    lineTo(9f, 11f)
    lineTo(10.8f, 12.8f)
    lineTo(15.4f, 8.2f)
    lineTo(16.8f, 9.6f)
    close()
}

/** Two people (group / members). */
val PeopleIcon: ImageVector = icon("People") {
    moveTo(8.2f, 11.4f)
    arcTo(3.3f, 3.3f, 0f, false, true, 8.2f, 4.8f)
    arcTo(3.3f, 3.3f, 0f, false, true, 8.2f, 11.4f)
    close()
    moveTo(1.8f, 19.5f)
    curveTo(2.4f, 16.2f, 5f, 14.4f, 8.2f, 14.4f)
    curveTo(11.4f, 14.4f, 14f, 16.2f, 14.6f, 19.5f)
    verticalLineTo(20.5f)
    horizontalLineTo(1.8f)
    close()
    moveTo(15.9f, 11.3f)
    arcTo(3.3f, 3.3f, 0f, false, false, 15.7f, 4.9f)
    arcTo(4.9f, 4.9f, 0f, false, true, 15.9f, 11.3f)
    close()
    moveTo(17.2f, 14.6f)
    curveTo(19.4f, 15.1f, 21.3f, 16.7f, 21.7f, 19.5f)
    verticalLineTo(20.5f)
    horizontalLineTo(18.4f)
    verticalLineTo(19.5f)
    curveTo(18.4f, 17.7f, 18f, 16f, 17.2f, 14.6f)
    close()
}

/** Megaphone (channel). */
val MegaphoneIcon: ImageVector = icon("Megaphone") {
    moveTo(3f, 10.5f)
    arcTo(1.5f, 1.5f, 0f, false, false, 4.5f, 15f)
    horizontalLineTo(6f)
    verticalLineTo(9f)
    horizontalLineTo(4.5f)
    arcTo(1.5f, 1.5f, 0f, false, false, 3f, 10.5f)
    close()
    moveTo(7.5f, 8.4f)
    verticalLineTo(15.6f)
    lineTo(19f, 19f)
    verticalLineTo(5f)
    close()
    moveTo(20.3f, 8.6f)
    arcTo(4.4f, 4.4f, 0f, false, true, 20.3f, 15.4f)
    lineTo(19.2f, 14.1f)
    arcTo(3f, 3f, 0f, false, false, 19.2f, 9.9f)
    close()
    moveTo(7.6f, 16.8f)
    lineTo(9.4f, 21f)
    horizontalLineTo(12f)
    lineTo(10.6f, 16.8f)
    close()
}

/** Clock (scheduled / slow mode / history). */
val ClockIcon: ImageVector = icon("Clock", evenOdd = true) {
    moveTo(12f, 2f)
    arcTo(10f, 10f, 0f, false, true, 12f, 22f)
    arcTo(10f, 10f, 0f, false, true, 12f, 2f)
    close()
    moveTo(12f, 4.5f)
    arcTo(7.5f, 7.5f, 0f, false, true, 12f, 19.5f)
    arcTo(7.5f, 7.5f, 0f, false, true, 12f, 4.5f)
    close()
    moveTo(13f, 7f)
    horizontalLineTo(11f)
    verticalLineTo(13.2f)
    lineTo(16.2f, 16.3f)
    lineTo(17.6f, 14.9f)
    lineTo(13f, 11.6f)
    close()
}

/** Reply arrow. */
val ReplyIcon: ImageVector = icon("Reply") {
    moveTo(9.5f, 4f)
    lineTo(3f, 10.5f)
    lineTo(9.5f, 17f)
    verticalLineTo(12.8f)
    curveTo(16f, 12.8f, 19.3f, 14.7f, 21f, 18.1f)
    curveTo(21.8f, 11f, 19.3f, 4.6f, 9.5f, 4.3f)
    close()
}

/** Forward arrow. */
val ForwardIcon: ImageVector = icon("Forward") {
    moveTo(14.5f, 4f)
    lineTo(21f, 10.5f)
    lineTo(14.5f, 17f)
    verticalLineTo(12.8f)
    curveTo(8f, 12.8f, 4.7f, 14.7f, 3f, 18.1f)
    curveTo(2.2f, 11f, 4.7f, 4.6f, 14.5f, 4.3f)
    close()
}

/** Copy (two pages). */
val CopyIcon: ImageVector = icon("Copy") {
    moveTo(9f, 4f)
    horizontalLineTo(20f)
    arcTo(1f, 1f, 0f, false, true, 21f, 5f)
    verticalLineTo(18f)
    horizontalLineTo(18.5f)
    verticalLineTo(6.5f)
    horizontalLineTo(9f)
    close()
    moveTo(5f, 8f)
    horizontalLineTo(15.5f)
    arcTo(1.5f, 1.5f, 0f, false, true, 17f, 9.5f)
    verticalLineTo(20f)
    arcTo(1.5f, 1.5f, 0f, false, true, 15.5f, 21.5f)
    horizontalLineTo(5f)
    arcTo(1.5f, 1.5f, 0f, false, true, 3.5f, 20f)
    verticalLineTo(9.5f)
    arcTo(1.5f, 1.5f, 0f, false, true, 5f, 8f)
    close()
}

/** Globe (language / proxy / web). */
val GlobeIcon: ImageVector = icon("Globe", evenOdd = true) {
    moveTo(12f, 2f)
    arcTo(10f, 10f, 0f, false, true, 12f, 22f)
    arcTo(10f, 10f, 0f, false, true, 12f, 2f)
    close()
    moveTo(19.9f, 11f)
    horizontalLineTo(16.8f)
    curveTo(16.6f, 8.9f, 16.1f, 7f, 15.5f, 5.6f)
    arcTo(8f, 8f, 0f, false, true, 19.9f, 11f)
    close()
    moveTo(12f, 4.1f)
    curveTo(12.9f, 5.3f, 14f, 7.5f, 14.3f, 11f)
    horizontalLineTo(9.7f)
    curveTo(10f, 7.5f, 11.1f, 5.3f, 12f, 4.1f)
    close()
    moveTo(8.5f, 5.6f)
    curveTo(7.9f, 7f, 7.4f, 8.9f, 7.2f, 11f)
    horizontalLineTo(4.1f)
    arcTo(8f, 8f, 0f, false, true, 8.5f, 5.6f)
    close()
    moveTo(4.1f, 13f)
    horizontalLineTo(7.2f)
    curveTo(7.4f, 15f, 7.9f, 17f, 8.5f, 18.4f)
    arcTo(8f, 8f, 0f, false, true, 4.1f, 13f)
    close()
    moveTo(9.7f, 13f)
    horizontalLineTo(14.3f)
    curveTo(14.1f, 15f, 13.6f, 17f, 13f, 18.4f)
    arcTo(8f, 8f, 0f, false, true, 11f, 18.4f)
    curveTo(10.4f, 17f, 9.9f, 15f, 9.7f, 13f)
    close()
    moveTo(16.8f, 13f)
    horizontalLineTo(19.9f)
    arcTo(8f, 8f, 0f, false, true, 15.5f, 18.4f)
    curveTo(16.1f, 17f, 16.6f, 15f, 16.8f, 13f)
    close()
}

/** Palette (appearance). */
val PaletteIcon: ImageVector = icon("Palette", evenOdd = true) {
    moveTo(12f, 3f)
    curveTo(6.5f, 3f, 3f, 7f, 3f, 12f)
    curveTo(3f, 16.9f, 6.9f, 21f, 12f, 21f)
    curveTo(13.1f, 21f, 14f, 20.1f, 14f, 19f)
    curveTo(14f, 18.4f, 13.8f, 18f, 13.4f, 17.6f)
    curveTo(13.1f, 17.2f, 12.8f, 16.9f, 12.8f, 16.3f)
    curveTo(12.8f, 15.4f, 13.5f, 14.7f, 14.5f, 14.7f)
    horizontalLineTo(17f)
    arcTo(5f, 5f, 0f, false, false, 22f, 9.7f)
    curveTo(21.6f, 6f, 17.3f, 3f, 12f, 3f)
    close()
    moveTo(7.5f, 11f)
    arcTo(1.8f, 1.8f, 0f, false, true, 7.5f, 7.4f)
    arcTo(1.8f, 1.8f, 0f, false, true, 7.5f, 11f)
    close()
    moveTo(11f, 10.1f)
    arcTo(1.8f, 1.8f, 0f, false, true, 11f, 6.5f)
    arcTo(1.8f, 1.8f, 0f, false, true, 11f, 10.1f)
    close()
    moveTo(15f, 11f)
    arcTo(1.8f, 1.8f, 0f, false, true, 15f, 7.4f)
    arcTo(1.8f, 1.8f, 0f, false, true, 15f, 11f)
    close()
}

/** Database cylinder (storage). */
val StorageIcon: ImageVector = icon("Storage") {
    moveTo(12f, 3f)
    curveTo(16.4f, 3f, 20f, 4.1f, 20f, 5.5f)
    curveTo(20f, 6.9f, 16.4f, 8f, 12f, 8f)
    curveTo(7.6f, 8f, 4f, 6.9f, 4f, 5.5f)
    curveTo(4f, 4.1f, 7.6f, 3f, 12f, 3f)
    close()
    moveTo(4f, 7.6f)
    curveTo(5.9f, 8.8f, 8.8f, 9.5f, 12f, 9.5f)
    curveTo(15.2f, 9.5f, 18.1f, 8.8f, 20f, 7.6f)
    verticalLineTo(12f)
    curveTo(20f, 13.4f, 16.4f, 14.5f, 12f, 14.5f)
    curveTo(7.6f, 14.5f, 4f, 13.4f, 4f, 12f)
    close()
    moveTo(4f, 14.1f)
    curveTo(5.9f, 15.3f, 8.8f, 16f, 12f, 16f)
    curveTo(15.2f, 16f, 18.1f, 15.3f, 20f, 14.1f)
    verticalLineTo(18.5f)
    curveTo(20f, 19.9f, 16.4f, 21f, 12f, 21f)
    curveTo(7.6f, 21f, 4f, 19.9f, 4f, 18.5f)
    close()
}

/** No-entry circle (block list). */
val BlockIcon: ImageVector = icon("Block", evenOdd = true) {
    moveTo(12f, 2f)
    arcTo(10f, 10f, 0f, false, true, 12f, 22f)
    arcTo(10f, 10f, 0f, false, true, 12f, 2f)
    close()
    moveTo(12f, 4.4f)
    arcTo(7.6f, 7.6f, 0f, false, false, 6.4f, 17.6f)
    lineTo(17.6f, 6.4f)
    arcTo(7.6f, 7.6f, 0f, false, false, 12f, 4.4f)
    close()
}

/** Text-size "T". */
val TextSizeIcon: ImageVector = icon("TextSize") {
    moveTo(4f, 3f)
    horizontalLineTo(20f)
    verticalLineTo(7f)
    horizontalLineTo(18f)
    verticalLineTo(5f)
    horizontalLineTo(13f)
    verticalLineTo(19f)
    horizontalLineTo(16f)
    verticalLineTo(21f)
    horizontalLineTo(8f)
    verticalLineTo(19f)
    horizontalLineTo(11f)
    verticalLineTo(5f)
    horizontalLineTo(6f)
    verticalLineTo(7f)
    horizontalLineTo(4f)
    close()
}

/** Hashtag (forum topics). */
val HashIcon: ImageVector = icon("Hash") {
    moveTo(9.6f, 3f)
    lineTo(8.1f, 21f)
    horizontalLineTo(5.9f)
    lineTo(7.4f, 3f)
    close()
    moveTo(17.1f, 3f)
    lineTo(15.6f, 21f)
    horizontalLineTo(13.4f)
    lineTo(14.9f, 3f)
    close()
    moveTo(4.2f, 9.3f)
    horizontalLineTo(20.7f)
    verticalLineTo(11.6f)
    horizontalLineTo(3.7f)
    close()
    moveTo(3.4f, 14.4f)
    horizontalLineTo(19.9f)
    verticalLineTo(16.7f)
    horizontalLineTo(2.9f)
    close()
}

/** Robot head (bots). */
val BotIcon: ImageVector = icon("Bot", evenOdd = true) {
    moveTo(11.2f, 4f)
    horizontalLineTo(12.8f)
    verticalLineTo(6.5f)
    horizontalLineTo(11.2f)
    close()
    moveTo(12f, 0.8f)
    arcTo(1.7f, 1.7f, 0f, false, true, 12f, 4.2f)
    arcTo(1.7f, 1.7f, 0f, false, true, 12f, 0.8f)
    close()
    moveTo(5f, 8f)
    horizontalLineTo(19f)
    arcTo(2f, 2f, 0f, false, true, 21f, 10f)
    verticalLineTo(17f)
    arcTo(2f, 2f, 0f, false, true, 19f, 19f)
    horizontalLineTo(5f)
    arcTo(2f, 2f, 0f, false, true, 3f, 17f)
    verticalLineTo(10f)
    arcTo(2f, 2f, 0f, false, true, 5f, 8f)
    close()
    moveTo(9f, 14.6f)
    arcTo(1.8f, 1.8f, 0f, false, true, 9f, 11f)
    arcTo(1.8f, 1.8f, 0f, false, true, 9f, 14.6f)
    close()
    moveTo(15f, 14.6f)
    arcTo(1.8f, 1.8f, 0f, false, true, 15f, 11f)
    arcTo(1.8f, 1.8f, 0f, false, true, 15f, 14.6f)
    close()
}

/** Reply keyboard grid. */
/** Speech bubble (bottom-nav chats tab, v0.9.9 W4). */
val ChatBubbleIcon: ImageVector = icon("ChatBubble") {
    moveTo(5f, 21.5f)
    lineTo(8.2f, 17.5f)
    lineTo(18.5f, 17.5f)
    quadraticBezierTo(21f, 17.5f, 21f, 15f)
    lineTo(21f, 5.5f)
    quadraticBezierTo(21f, 3f, 18.5f, 3f)
    lineTo(5.5f, 3f)
    quadraticBezierTo(3f, 3f, 3f, 5.5f)
    lineTo(3f, 15f)
    quadraticBezierTo(3f, 17.5f, 5.5f, 17.5f)
    lineTo(5f, 17.5f)
    close()
}

val KeyboardIcon: ImageVector = icon("Keyboard", evenOdd = true) {
    moveTo(3f, 6f)
    horizontalLineTo(21f)
    arcTo(1f, 1f, 0f, false, true, 22f, 7f)
    verticalLineTo(17f)
    arcTo(1f, 1f, 0f, false, true, 21f, 18f)
    horizontalLineTo(3f)
    arcTo(1f, 1f, 0f, false, true, 2f, 17f)
    verticalLineTo(7f)
    arcTo(1f, 1f, 0f, false, true, 3f, 6f)
    close()
    moveTo(5f, 9f)
    horizontalLineTo(7f)
    verticalLineTo(11f)
    horizontalLineTo(5f)
    close()
    moveTo(9f, 9f)
    horizontalLineTo(11f)
    verticalLineTo(11f)
    horizontalLineTo(9f)
    close()
    moveTo(13f, 9f)
    horizontalLineTo(15f)
    verticalLineTo(11f)
    horizontalLineTo(13f)
    close()
    moveTo(17f, 9f)
    horizontalLineTo(19f)
    verticalLineTo(11f)
    horizontalLineTo(17f)
    close()
    moveTo(5f, 13.5f)
    horizontalLineTo(19f)
    verticalLineTo(15.5f)
    horizontalLineTo(5f)
    close()
}
