package com.tuxungram.android.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Official-style palette (v0.9.17, X1): the visual layer's
 * single source for the Telegram Android default themes.
 *
 * Every value was read from the official client's own theme
 * definitions — the bundled day.attheme / night.attheme files
 * and the Theme.java key table of the official Android source
 * (design values and resource specs only; no official UI code
 * is ported). The per-key provenance is recorded in the
 * project checkpoint. Keys a bundled theme does not override
 * fall back to the official shared palette (the color_* set
 * in day.attheme) and are marked as such where used.
 *
 * [OfficialTheme.dark] is set by MainActivity whenever the
 * effective dark mode flips; composables read [palette]
 * during composition and the whole tree recomposes with the
 * Material scheme, so the flag is always current on screen.
 */
data class OfficialPalette(
    val dark: Boolean,
    /** windowBackgroundWhite / windowBackgroundWhite (night). */
    val windowBg: Color,
    /** actionBarDefault. */
    val actionBarBg: Color,
    /** actionBarDefaultTitle. */
    val actionBarTitle: Color,
    /** actionBarDefaultSubtitle. */
    val actionBarSubtitle: Color,
    /** actionBarDefaultIcon. */
    val actionBarIcon: Color,
    /** divider. */
    val divider: Color,
    /** graySection. */
    val graySection: Color,
    /** key_graySectionText. */
    val graySectionText: Color,
    /** windowBackgroundWhiteBlackText. */
    val textPrimary: Color,
    /** windowBackgroundWhiteGrayText (day) / chats_message (night). */
    val textSecondary: Color,
    /** chat_messagePanelSend — the official accent blue. */
    val accent: Color,
    /** windowBackgroundWhiteBlueText. */
    val accentText: Color,
    /** chat_messageLinkIn. */
    val linkIn: Color,
    /** chat_inBubble. */
    val inBubble: Color,
    /** chat_outBubble. */
    val outBubble: Color,
    /** chat_outBubbleGradient (top stop of the out gradient). */
    val outBubbleGradient: Color,
    /** chat_inBubbleSelected. */
    val inBubbleSelected: Color,
    /** chat_outBubbleSelected. */
    val outBubbleSelected: Color,
    /** chat_messageTextIn. */
    val inText: Color,
    /** chat_messageTextOut. */
    val outText: Color,
    /** chat_inTimeText. */
    val inTime: Color,
    /** chat_outTimeText. */
    val outTime: Color,
    /** chat_inReplyLine. */
    val inReplyLine: Color,
    /** chat_inReplyNameText. */
    val inReplyName: Color,
    /** chat_outReplyLine. */
    val outReplyLine: Color,
    /** chats_unreadCounter. */
    val unreadBadge: Color,
    /** chats_unreadCounterMuted (night) / shared gray (day). */
    val unreadBadgeMuted: Color,
    /** chats_onlineCircle. */
    val onlineDot: Color,
    /** chat_messagePanelBackground. */
    val panelBg: Color,
    /** chat_serviceBackground. */
    val serviceBg: Color,
    /** chat_serviceText (day) / chat_unreadMessagesStartText (night). */
    val serviceText: Color,
    /** chats_draft, alpha dropped (rows render it opaque). */
    val draftRed: Color,
    /** chat_wallpaper: the official default chat backdrop tint. */
    val chatWallpaper: Color,
    /** avatar_nameInMessage*, order Red/Orange/Violet/Green/Cyan/Blue/Pink. */
    val nameColors: List<Color>,
    /** avatar_background* gradient pairs, same order as nameColors. */
    val avatarColors: List<Pair<Color, Color>>,
    /** avatar_backgroundSaved / avatar_background2Saved. */
    val savedColors: Pair<Color, Color>
)

object OfficialTheme {

    /** Effective dark mode, maintained by MainActivity. */
    @Volatile
    var dark: Boolean = false

    fun palette(dark: Boolean): OfficialPalette =
        if (dark) NIGHT else DAY

    /** The palette for the mode currently on screen. */
    fun current(): OfficialPalette = palette(dark)

    /** Official color index: abs(id % 7) — AvatarDrawable.getColorIndex. */
    fun colorIndex(id: Long): Int {
        val m = id % 7
        return (if (m < 0) -m else m).toInt()
    }

    /** Sender-name color for a peer id (avatar_nameInMessage*). */
    fun nameColor(id: Long, dark: Boolean): Color =
        palette(dark).nameColors[colorIndex(id)]

    /** Avatar placeholder gradient for a peer id (avatar_background*). */
    fun avatarColors(id: Long, dark: Boolean): Pair<Color, Color> =
        palette(dark).avatarColors[colorIndex(id)]

    /** Material3 scheme built from the official palette, used
     *  when the official preset is the active appearance. */
    fun materialScheme(dark: Boolean): ColorScheme {
        val p = palette(dark)
        return if (dark) {
            darkColorScheme(
                primary = Color(0xFF459CE3), // switchTrackBlueChecked (night)
                onPrimary = Color.White,
                primaryContainer = p.inBubbleSelected, // chat_inBubbleSelected (night)
                onPrimaryContainer = Color.White,
                secondary = Color(0xFF459CE3),
                onSecondary = Color.White,
                background = p.windowBg,
                onBackground = Color.White,
                surface = Color(0xFF1E1E1E), // dialogBackground (night)
                onSurface = p.inText,
                surfaceVariant = p.graySection,
                onSurfaceVariant = p.graySectionText,
                outline = p.divider,
                error = Color(0xFFF6574E) // chats_sentError (night)
            )
        } else {
            lightColorScheme(
                primary = p.accent, // chat_messagePanelSend (day)
                onPrimary = Color.White,
                primaryContainer = Color(0xFFD6EBFC), // derived tint of the accent
                onPrimaryContainer = p.textPrimary,
                secondary = p.accent,
                onSecondary = Color.White,
                background = p.windowBg,
                onBackground = p.textPrimary,
                surface = p.windowBg,
                onSurface = p.textPrimary,
                surfaceVariant = p.graySection,
                onSurfaceVariant = Color(0xFF808384), // windowBackgroundWhiteGrayText (day)
                outline = p.divider,
                error = Color(0xFFE6555E) // text_RedBold (day)
            )
        }
    }

    private val DAY = OfficialPalette(
        dark = false,
        windowBg = Color(0xFFFFFFFF), // windowBackgroundWhite (Theme.java default)
        actionBarBg = Color(0xFFFFFFFF), // actionBarDefault (day)
        actionBarTitle = Color(0xFF333333), // actionBarDefaultTitle (day)
        actionBarSubtitle = Color(0xFF79817E), // actionBarDefaultSubtitle (day)
        actionBarIcon = Color(0xFF404E56), // actionBarDefaultIcon (day)
        divider = Color(0xFFE6E6E6), // divider (day)
        graySection = Color(0xFFF6F6F6), // graySection (day)
        graySectionText = Color(0xFF84878A), // key_graySectionText (day)
        textPrimary = Color(0xFF333333), // windowBackgroundWhiteBlackText (day)
        textSecondary = Color(0xFF808384), // windowBackgroundWhiteGrayText (day)
        accent = Color(0xFF229AF0), // chat_messagePanelSend (day)
        accentText = Color(0xFF238AE3), // windowBackgroundWhiteBlueText (day)
        linkIn = Color(0xFF127ACA), // chat_messageLinkIn (day)
        inBubble = Color(0xFFF0F0F0), // chat_inBubble (day)
        outBubble = Color(0xFF2D7ED5), // chat_outBubble (day)
        outBubbleGradient = Color(0xFF5CB6E0), // chat_outBubbleGradient (day)
        inBubbleSelected = Color(0xFFEAEAEA), // chat_inBubbleSelected (day)
        outBubbleSelected = Color(0xFF4280D1), // chat_outBubbleSelected (day)
        inText = Color(0xFF333333), // windowBackgroundWhiteBlackText (day)
        outText = Color(0xFFFFFFFF), // chat_messageTextOut (day)
        inTime = Color(0xFF939599), // chat_inTimeText (day)
        outTime = Color(0xFFC7E6FF), // chat_outTimeText (day)
        inReplyLine = Color(0xFF3E91E0), // chat_inReplyLine (day)
        inReplyName = Color(0xFF3186DF), // chat_inReplyNameText (day)
        outReplyLine = Color(0xFFFFFFFF), // chat_outReplyLine (day)
        unreadBadge = Color(0xFF24AEF7), // chats_unreadCounter (day)
        unreadBadgeMuted = Color(0xFFB9B9B9), // shared gray (day fallback)
        onlineDot = Color(0xFF29A8E7), // chats_onlineCircle (day)
        panelBg = Color(0xFFFFFFFF), // chat_messagePanelBackground (day default)
        serviceBg = Color(0xF4FFFFFF), // chat_serviceBackground (day)
        serviceText = Color(0xFF383838), // chat_serviceText (day)
        draftRed = Color(0xFFE32246), // chats_draft (day)
        chatWallpaper = Color(0xFFFFFFFF), // chat_wallpaper (day)
        nameColors = listOf(
            Color(0xFFE05356), // color_red (day shared palette)
            Color(0xFFF28C39), // color_orange (day shared palette)
            Color(0xFF836FE6), // avatar_nameInMessageViolet (day)
            Color(0xFF61C752), // color_green (day shared palette)
            Color(0xFF2FA3C9), // avatar_nameInMessageCyan (bluebubbles)
            Color(0xFF2586D6), // avatar_nameInMessageBlue (day)
            Color(0xFFCB4F86) // avatar_nameInMessagePink (day)
        ),
        avatarColors = listOf(
            // Light placeholders are flat: the day theme file
            // only overrides avatar_backgroundBlue/Saved, the
            // other hues ride the shared color_* palette (day).
            Color(0xFFE05356) to Color(0xFFE05356),
            Color(0xFFF28C39) to Color(0xFFF28C39),
            Color(0xFF836FE6) to Color(0xFF836FE6),
            Color(0xFF61C752) to Color(0xFF61C752),
            Color(0xFF2FA3C9) to Color(0xFF2FA3C9),
            Color(0xFF50A5E6) to Color(0xFF50A5E6), // avatar_backgroundBlue (day)
            Color(0xFFCB4F86) to Color(0xFFCB4F86)
        ),
        savedColors = Color(0xFF45B6FF) to Color(0xFF45B6FF) // avatar_backgroundSaved (day)
    )

    private val NIGHT = OfficialPalette(
        dark = true,
        windowBg = Color(0xFF181819), // windowBackgroundWhite (night)
        actionBarBg = Color(0xFF232326), // actionBarDefault (night)
        actionBarTitle = Color(0xFFFFFFFF), // actionBarDefaultTitle (night)
        actionBarSubtitle = Color(0x74FFFFFF), // actionBarDefaultSubtitle (night, alpha in source)
        actionBarIcon = Color(0xFFFFFFFF), // actionBarDefaultIcon (night)
        divider = Color(0xFF000000), // divider (night)
        graySection = Color(0xFF0B0B0C), // graySection (night)
        graySectionText = Color(0xFF838384), // key_graySectionText (night)
        textPrimary = Color(0xFFFFFFFF), // windowBackgroundWhiteBlackText (night)
        textSecondary = Color(0xFF828282), // chats_message (night)
        accent = Color(0xFF229AF0), // chat_messagePanelSend (night)
        accentText = Color(0xFF64B5EF), // windowBackgroundWhiteBlueText (night)
        linkIn = Color(0xFF79C4FC), // chat_messageLinkIn (night)
        inBubble = Color(0xFF1F2123), // chat_inBubble (night)
        outBubble = Color(0xFF366CAF), // chat_outBubble (night)
        outBubbleGradient = Color(0xFF3B8CB9), // chat_outBubbleGradient (night)
        inBubbleSelected = Color(0xFF314A61), // chat_inBubbleSelected (night)
        outBubbleSelected = Color(0xFF4B7DAF), // chat_outBubbleSelected (night)
        inText = Color(0xFFFAFAFA), // chat_messageTextIn (night)
        outText = Color(0xFFFAFAFA), // chat_messageTextOut (night)
        inTime = Color(0xFF7D7F81), // chat_inTimeText (night)
        outTime = Color(0xFF94D3F6), // chat_outTimeText (night)
        inReplyLine = Color(0xFF79C4FC), // chat_inReplyLine (night)
        inReplyName = Color(0xFF79C4FC), // chat_inReplyNameText (night)
        outReplyLine = Color(0xFF7ECEFF), // chat_outReplyLine (night)
        unreadBadge = Color(0xFF399EF3), // chats_unreadCounter (night)
        unreadBadgeMuted = Color(0xFF454545), // chats_unreadCounterMuted (night)
        onlineDot = Color(0xFF37A4F9), // chats_onlineCircle (night)
        panelBg = Color(0xFF1E1E1F), // chat_messagePanelBackground (night)
        serviceBg = Color(0x96373737), // chat_serviceBackground (night)
        serviceText = Color(0xDAFFFFFF), // chat_unreadMessagesStartText (night)
        draftRed = Color(0xFFFC474A), // chats_draft (night)
        chatWallpaper = Color(0xFF0F0F10), // chat_wallpaper (night)
        nameColors = listOf(
            Color(0xFFE88878), // avatar_nameInMessageRed (night)
            Color(0xFFE8AB5A), // avatar_nameInMessageOrange (night)
            Color(0xFF8C8DEA), // avatar_nameInMessageViolet (night)
            Color(0xFF7DD071), // avatar_nameInMessageGreen (night)
            Color(0xFF53CCC6), // avatar_nameInMessageCyan (night)
            Color(0xFF5CADEA), // avatar_nameInMessageBlue (night)
            Color(0xFFE8749A) // avatar_nameInMessagePink (night)
        ),
        avatarColors = listOf(
            // Gradient pairs where night.attheme defines both
            // avatar_background* and avatar_background2*; the
            // rest are flat in the night hues (see checkpoint).
            Color(0xFFDC805B) to Color(0xFFD9614F), // Red (night pair)
            Color(0xFFF2BC64) to Color(0xFFE58943), // Orange (night pair)
            Color(0xFF8C8DEA) to Color(0xFF8C8DEA), // Violet (night name hue)
            Color(0xFF7DD071) to Color(0xFF7DD071), // Green (night name hue)
            Color(0xFF53CCC6) to Color(0xFF53CCC6), // Cyan (night name hue)
            Color(0xFF549CDD) to Color(0xFF549CDD), // avatar_backgroundInProfileBlue (night)
            Color(0xFFE8749A) to Color(0xFFE8749A) // Pink (night name hue)
        ),
        savedColors = Color(0xFF4FA1E8) to Color(0xFF1A88E7) // Saved (night pair)
    )
}
