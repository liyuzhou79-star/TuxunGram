package com.tuxungram.android.td

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

enum class MsgKind { TEXT, PHOTO, DOCUMENT, VIDEO, VOICE, STICKER, ANIMATION, POLL, LOCATION, CONTACT, VIDEO_NOTE, DICE, GAME, ANIMATED_EMOJI, CHECKLIST, PAID_MEDIA, SERVICE, GIVEAWAY, AUDIO, INVOICE, SHARED, RICH_MESSAGE, OTHER }

/** One poll option, flattened from pollOption. */
data class PollOptionUi(
    val text: String,
    val voterCount: Int,
    val percentage: Int,
    val chosen: Boolean,
    val isCorrect: Boolean = false
)

/** A poll message, flattened from the poll object; refreshed
 *  wholesale whenever TDLib pushes updatePoll. */
data class PollUi(
    val question: String,
    val options: List<PollOptionUi>,
    val totalVoters: Int,
    val isClosed: Boolean,
    val isAnonymous: Boolean,
    val allowsMultiple: Boolean,
    val isQuiz: Boolean,
    val explanation: String = "",
    /** poll.can_get_voters (v0.9.32, Y3): the server lets
     *  this account list an option's voters via
     *  getPollVoters. Always false for anonymous polls. */
    val canGetVoters: Boolean = false
)

/** One checklist task, flattened from checklistTask
 *  (v0.9.24, Q7). [completed] mirrors completion_date != 0;
 *  toggling goes through markChecklistTasksAsDone and the
 *  fresh state arrives with updateMessageContent. */
data class ChecklistTaskUi(
    val id: Int,
    val text: String,
    val entities: List<TextEntityUi> = emptyList(),
    val completed: Boolean = false
)

/** One entry of a paid-media message (messagePaidMedia,
 *  v0.9.35 B1; types verified in td_api.tl). [kind] is
 *  "photo" / "video" when this account has access — TDLib
 *  then ships the real files; "preview" when it has not —
 *  TDLib ships only the dimensions and a minithumbnail, the
 *  media itself stays locked server-side; "unsupported"
 *  for paidMediaUnsupported. Photo/video entries ride
 *  their own file fields (a paid message can hold several
 *  entries, so the MsgItem-level photo/file fields stay
 *  unused for this kind). */
data class PaidMediaUi(
    val kind: String,
    val photoFileId: Int = 0,
    val photoPath: String? = null,
    val photoWidth: Int = 0,
    val photoHeight: Int = 0,
    val videoFileId: Int = 0,
    val videoPath: String? = null,
    val videoThumbFileId: Int = 0,
    val videoThumbPath: String? = null,
    val videoMime: String = "",
    val videoSize: Long = 0L,
    val miniThumbData: String = "",
    val durationSec: Int = 0,
    val width: Int = 0,
    val height: Int = 0
)

/** Giveaway payload (v0.9.37, D3): messageGiveaway (the
 *  announcement post in a channel) and
 *  messageGiveawayWinners (the results post) share one
 *  card shape. Participation in a giveaway is automatic
 *  for eligible members — there is no join call in
 *  TDLib; the card's only action asks getGiveawayInfo
 *  for the current user's own status. */
data class GiveawayUi(
    /** "Telegram Premium 3 个月" / "⭐ 500 星星" style. */
    val prizeText: String,
    val prizeDescription: String = "",
    val winnerCount: Int = 0,
    /** Participation conditions, one plain line each. */
    val conditions: List<String> = emptyList(),
    /** messageGiveaway's winners_selection_date. */
    val deadlineSec: Int = 0,
    /** True for messageGiveawayWinners (results post). */
    val isWinners: Boolean = false,
    /** messageGiveawayWinners' actual selection date. */
    val winnersDateSec: Int = 0,
    val unclaimedCount: Int = 0,
    /** Winner names resolved from the parse-time user
     *  cache (up to 20); empty when none were cached —
     *  the card then shows the count alone, never a
     *  guessed name. */
    val winnerNames: List<String> = emptyList()
)

/** One entry of a messageUsersShared / messageChatShared
 *  card (v0.9.37, D4). Exactly one of [userId] / [chatId]
 *  is non-zero. */
data class SharedEntryUi(
    val name: String,
    val userId: Long = 0L,
    val chatId: Long = 0L
)

/** A copy of this message re-shaped as a plain PHOTO item
 *  around one purchased paid-media entry, so the existing
 *  full-screen viewer can open it unchanged. */
fun MsgItem.asPaidPhotoItem(entry: PaidMediaUi): MsgItem = copy(
    kind = MsgKind.PHOTO,
    text = "",
    photoFileId = entry.photoFileId,
    photoPath = entry.photoPath,
    photoWidth = entry.photoWidth,
    photoHeight = entry.photoHeight
)

/** Same re-shaping for a purchased paid-media video entry:
 *  the media viewer's video page offers download/playback
 *  from these fields exactly like a plain video message. */
fun MsgItem.asPaidVideoItem(entry: PaidMediaUi): MsgItem = copy(
    kind = MsgKind.VIDEO,
    text = "",
    fileId = entry.videoFileId,
    filePath = entry.videoPath,
    fileName = "付费视频",
    fileMime = entry.videoMime.ifEmpty { "video/mp4" },
    fileSize = entry.videoSize,
    durationSec = entry.durationSec,
    thumbFileId = entry.videoThumbFileId,
    thumbPath = entry.videoThumbPath,
    miniThumbData = entry.miniThumbData
)

/** Patch a downloaded file's path into this item's paid
 *  entries (updateFile backfill, shared by the three
 *  message stores). Returns the updated copy, or null when
 *  no entry was waiting for [fileId]. */
fun MsgItem.withPaidFile(fileId: Int, path: String): MsgItem? {
    if (paidMedia.isEmpty()) return null
    var changed = false
    val updated = paidMedia.map { entry ->
        when {
            entry.photoFileId == fileId && entry.photoPath == null -> {
                changed = true
                entry.copy(photoPath = path)
            }
            entry.videoFileId == fileId && entry.videoPath == null -> {
                changed = true
                entry.copy(videoPath = path)
            }
            entry.videoThumbFileId == fileId &&
                entry.videoThumbPath == null -> {
                changed = true
                entry.copy(videoThumbPath = path)
            }
            else -> entry
        }
    }
    return if (changed) copy(paidMedia = updated) else null
}

/** One finished message translation (v0.9.5, O4): the
 *  translated text plus the target language's display name,
 *  shown under the bubble until the user collapses it. */
data class TranslationUi(
    val text: String,
    val langLabel: String,
    /** Formatting entities of the translated text, as
     *  returned inside the translateMessageText
     *  formattedText (v0.9.57, B2): until this version
     *  only the raw string was kept, so links and styling
     *  in a translation rendered as dead plain text. */
    val entities: List<TextEntityUi> = emptyList()
)

/** One reaction shown under a bubble: the emoji, how many people
 *  used it, and whether I am one of them. */
data class ReactionChip(
    val emoji: String,
    val count: Int,
    val chosen: Boolean
)

/** One inline-keyboard button under a bot message, flattened
 *  from replyMarkupInlineKeyboard / inlineKeyboardButtonType*
 *  (verified in td_api.tl). [kind] is one of: url, callback,
 *  callbackPw (callback that needs the 2FA password), switch
 *  (switch-inline query), copy (copy-text), user, game, buy,
 *  webapp (inlineKeyboardButtonTypeWebApp, v0.9.36),
 *  disabled. [data] is the callback payload, base64 as TDLib's
 *  JSON interface ships bytes. */
/** One button of a bot reply keyboard (v0.9.9, W2).
 *  keyboardButtonTypeText buttons send their text when
 *  tapped; keyboardButtonTypeWebApp buttons (v0.9.36, C1)
 *  open a Web App through getWebAppUrl; the other types
 *  (request contact / location / poll ...) render greyed
 *  and do nothing yet. [kind] is "text", "webapp" or
 *  "other"; [url] is the Web App button's URL. */
data class KbButton(
    val text: String,
    val kind: String,
    val url: String = ""
) {
    val isText: Boolean get() = kind == "text"
    val isWebApp: Boolean get() = kind == "webapp"
}

/** Reply-keyboard state of the open chat (v0.9.9, W2),
 *  driven by chat.reply_markup_message_id at open time and
 *  updateChatReplyMarkup afterwards - the channel TDLib
 *  itself designates for this (the replyMarkupRemoveKeyboard
 *  note in td_api.tl says the keyboard never arrives inside
 *  a message; the update carries it). The markup types are
 *  this tl's replyMarkupShowKeyboard / replyMarkup
 *  RemoveKeyboard / replyMarkupForceReply (lines 4343 /
 *  4328 / 4333). */
sealed interface ReplyKb {
    object Hidden : ReplyKb
    data class Shown(
        val rows: List<List<KbButton>>,
        val oneTime: Boolean,
        val placeholder: String,
        val forceReply: Boolean,
        val sourceMessageId: Long
    ) : ReplyKb
    data class ForceReply(val placeholder: String) : ReplyKb
}

data class InlineBtn(
    val text: String,
    val kind: String,
    val url: String = "",
    val data: String = "",
    val query: String = "",
    val copyText: String = "",
    val userId: Long = 0L
)

/** One formatting entity of a message text / caption, flattened
 *  from formattedText.entities (v0.9.2, L1). [offset]/[length]
 *  are UTF-16 code units per td_api.tl, which is exactly how
 *  Kotlin String indices count, so the UI builder can use them
 *  verbatim. [kind] is a short internal tag: bold, italic,
 *  underline, strike, code, pre, quote, spoiler, url, textUrl,
 *  mention, mentionName, hashtag, cashtag, botCommand, email,
 *  phone, customEmoji, plain (recognized but rendered as-is). */
data class TextEntityUi(
    val offset: Int,
    val length: Int,
    val kind: String,
    val url: String = "",
    val userId: Long = 0L,
    val customEmojiId: Long = 0L
)

data class MsgItem(
    val id: Long,
    val outgoing: Boolean,
    val dateSeconds: Int,
    // Edited after sending (message.edit_date > 0, v0.9.29
    // V4): drives the "已编辑" marker before the time in the
    // bubble footer — before this version the app parsed no
    // edit state at all and edited messages looked untouched.
    val edited: Boolean = false,
    val kind: MsgKind,
    val text: String,
    // Formatting entities for [text] (message text or caption),
    // v0.9.2 L1. Empty for plain unformatted text.
    val entities: List<TextEntityUi> = emptyList(),
    val senderName: String,
    val senderUserId: Long,
    // Inline-bot provenance (v0.9.39, F3): message
    // .via_bot_user_id — non-zero when the message was
    // sent through an inline bot. [viaBotLabel] is the
    // resolved display ("@botname" when the bot has a
    // public username, else its display name), filled at
    // parse time from the user caches and backfilled by
    // publish() like the sender name.
    val viaBotUserId: Long = 0L,
    val viaBotLabel: String = "",
    // Sender's member tag at send time (message
    // .sender_tag, v0.9.39, F2 display side): in
    // supergroups this is the sender's administrator
    // custom title when they have one. Rendered at the
    // right of the bubble header row, as the official
    // client does; empty everywhere else.
    val senderTag: String = "",
    // Sender's small profile photo (group chats render it
    // beside the bubble, v0.9.9 W0); resolved through
    // AvatarStore and backfilled by publish() as it lands.
    val senderPhotoPath: String? = null,
    val photoFileId: Int,
    val photoPath: String?,
    val photoWidth: Int,
    val photoHeight: Int,
    val sendingState: String,
    val replyToId: Long = 0L,
    val replyChatId: Long = 0L,
    val replySummary: String = "",
    // Generic downloadable file (document / video / voice note).
    val fileId: Int = 0,
    val filePath: String? = null,
    val fileName: String = "",
    val fileSize: Long = 0L,
    val fileMime: String = "",
    val durationSec: Int = 0,
    // Video frame dimensions (messageVideo.width/height,
    // v0.9.54 Y3): the media bubble sizes its thumbnail box
    // from the real aspect instead of guessing.
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val fileDownloading: Boolean = false,
    val reactions: List<ReactionChip> = emptyList(),
    // Per-user reaction list availability (v0.9.39, F1):
    // messageReactions.can_get_added_reactions from the
    // same interaction_info object — the long-press menu
    // only offers the "who reacted" list when the server
    // says getMessageAddedReactions can answer for this
    // message, so there is never a dead entry.
    val canGetAddedReactions: Boolean = false,
    // Comment count (v0.9.55, Z1): interaction_info.reply_info
    // .reply_count of a channel post — how many comments its
    // discussion thread already holds (messageReplyInfo in
    // td_api.tl, verified). -1 when TDLib ships no reply_info
    // for this message at all.
    val commentCount: Int = -1,
    // Outgoing delivery state derived from the chat's
    // last_read_outbox_message_id: "" (not applicable / pending),
    // "sent" (delivered, unread), "read".
    val readState: String = "",
    // Sticker emoji (STICKER kind); the image itself rides the
    // generic fileId/filePath fields and renders from filePath.
    val stickerEmoji: String = "",
    // Voice-note waveform amplitudes 0..31 (VOICE kind),
    // decoded from voice_note.waveform's 5-bit packing
    // (v0.9.2, L5). Empty when TDLib ships none.
    val waveform: List<Int> = emptyList(),
    // Speech recognition (v0.9.55, Z3): voice_note
    // .speech_recognition_result as a state + text pair.
    // state: "" (nothing shipped / never requested),
    // "pending" (the server is recognizing; speechText
    // holds the partial result), "text" (final transcript
    // in speechText), "error" (the server tried and
    // failed). SpeechRecognitionResult in td_api.tl.
    val speechState: String = "",
    val speechText: String = "",
    // Poll payload (POLL kind).
    val pollId: Long = 0L,
    val poll: PollUi? = null,
    // Link preview card (TEXT kind), straight from messageText's
    // link_preview object — TDLib ships it with the message.
    val lpSiteName: String = "",
    val lpTitle: String = "",
    val lpDescription: String = "",
    val lpUrl: String = "",
    val lpPhotoFileId: Int = 0,
    val lpPhotoPath: String? = null,
    // Real width of the preview's largest photo size
    // (v0.9.54, Y3): a site icon (tens of px) renders as a
    // small side thumbnail in the card, a real photo as the
    // banner — the width is what tells them apart.
    val lpPhotoWidth: Int = 0,
    // Instant View availability (v0.9.26, S1): the
    // link_preview's instant_view_version — 0 when the page
    // has no Instant View, 1/2 when getWebPageInstantView can
    // serve it. The entry points (preview card + long-press
    // menu) only appear when this is positive, so there is
    // never a fake entry.
    val lpInstantView: Int = 0,
    // Inline keyboard from the message's reply_markup (v0.8.0,
    // G4); rows of buttons rendered under the bubble.
    val buttons: List<List<InlineBtn>> = emptyList(),
    // Channel post flag (message.is_channel_post); the comment
    // entry (G1) only makes sense on these.
    val isChannelPost: Boolean = false,
    // Pinned flag (message.is_pinned, v0.9.4 N3).
    val isPinned: Boolean = false,
    // Scheduled-send state from message.scheduling_state
    // (v0.9.3, M5): scheduledAtSec > 0 = the message goes out
    // at that unix time; scheduledWhenOnline = it goes out
    // when the other side comes online.
    val scheduledAtSec: Int = 0,
    val scheduledWhenOnline: Boolean = false,
    // Location payload (LOCATION kind, v0.9.4 N2).
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    // Live location (v0.9.25, R2): > 0 while a messageLiveLocation
    // can still be updated — the seconds left at parse time
    // (messageLiveLocation.expires_in). Static messageLocation
    // messages keep 0. TDLib does not push an update when this
    // counts down to 0; the card treats a stale value loosely
    // and the sender's stop/edit calls are what change state.
    val liveExpiresIn: Int = 0,
    // Contact payload (CONTACT kind, v0.9.4 N2): display name,
    // phone number, and the Telegram user id when the shared
    // contact is a Telegram user (0 = not known / not a user).
    val contactName: String = "",
    val contactPhone: String = "",
    val contactUserId: Long = 0L,
    // Video note payload (VIDEO_NOTE kind, v0.9.14 U4): the
    // round thumbnail rides [thumbFileId]/[thumbPath] (or the
    // inline [miniThumbData], base64 as TDLib ships bytes);
    // the video itself rides the generic file fields.
    val videoNoteLength: Int = 0,
    val thumbFileId: Int = 0,
    val thumbPath: String? = null,
    val miniThumbData: String = "",
    // Dice payload (DICE kind, v0.9.14 U5): the final state's
    // static sticker rides the generic file fields.
    val diceEmoji: String = "",
    val diceValue: Int = 0,
    // Game payload (GAME kind, v0.9.14 U5): the cover rides
    // the photo fields.
    val gameTitle: String = "",
    val gameDescription: String = "",
    val gameShortName: String = "",
    // Album membership (message.media_album_id, v0.9.15 V1/V4):
    // non-zero when this message belongs to a media album;
    // consecutive items sharing one id render as one grid.
    val mediaAlbumId: Long = 0L,
    // Animated emoji payload (ANIMATED_EMOJI kind, v0.9.16
    // W4): the big sticker itself rides the generic
    // fileId/filePath fields and its size rides
    // photoWidth/photoHeight; the click sound
    // (animatedEmoji.sound, an Opus/OGG file, may be absent)
    // rides these two.
    val animSoundFileId: Int = 0,
    val animSoundPath: String? = null,
    // Send effect (message.effect_id, v0.9.24 Q8): non-zero
    // when the message was sent with an effect in a private
    // chat. The bubble shows the effect's emoji as a marker;
    // the full-screen animation is the receiving client's
    // job and is not faked here.
    val effectId: Long = 0L,
    // Checklist payload (CHECKLIST kind, v0.9.24 Q7).
    val checklistTitle: String = "",
    val checklistTitleEntities: List<TextEntityUi> = emptyList(),
    val checklistTasks: List<ChecklistTaskUi> = emptyList(),
    // Whether THIS account may tick tasks on this checklist
    // (sender, or others_can_mark_tasks_as_done).
    val checklistCanMark: Boolean = false,
    // Post-send editing (v0.9.26, S2): can_add_tasks is the
    // server's own flag for whether THIS account may append
    // tasks via addChecklistTasks (Premium-gated); the two
    // others_* flags ride into editMessageChecklist when the
    // sender edits the title/settings.
    val checklistCanAdd: Boolean = false,
    val checklistOthersCanAdd: Boolean = false,
    val checklistOthersCanMark: Boolean = false,
    // Paid media payload (PAID_MEDIA kind, v0.9.35 B1):
    // the Telegram Stars price of the whole message and
    // its media entries. [paidPurchased] is read off the
    // entries themselves — TDLib ships real photo/video
    // entries only to accounts that have access, and
    // preview entries to everyone else; there is no
    // separate flag to trust.
    val paidStarCount: Long = 0L,
    val paidMedia: List<PaidMediaUi> = emptyList(),
    // Venue payload (LOCATION kind, v0.9.37 D4): a venue is
    // a location with a name and a street address; plain
    // locations keep both empty and the card renders as
    // before.
    val venueTitle: String = "",
    val venueAddress: String = "",
    // Audio payload (AUDIO kind, v0.9.37 D4): the file
    // itself rides the generic file fields and the length
    // rides durationSec; title/performer come from the
    // audio's own metadata, caption rides text/entities.
    val audioTitle: String = "",
    val audioPerformer: String = "",
    // Invoice payload (INVOICE kind, v0.9.37 D4): product
    // title/description from messageInvoice.product_info
    // and the formatted grand total (currency + amount,
    // formatted at parse time).
    val invoiceTitle: String = "",
    val invoiceDescription: String = "",
    val invoiceTotalText: String = "",
    // Giveaway payload (GIVEAWAY kind, v0.9.37 D3): the
    // sticker, when the giveaway carries one, rides the
    // generic fileId/filePath fields like a sticker
    // message's.
    val giveaway: GiveawayUi? = null,
    // Shared users / chat payload (SHARED kind, v0.9.37
    // D4): messageUsersShared / messageChatShared carry
    // the names inside the message itself, so the card
    // needs no lookups.
    val sharedHeader: String = "",
    val sharedEntries: List<SharedEntryUi> = emptyList(),
    // Rich message payload (RICH_MESSAGE kind, v0.9.40,
    // G4): messageRichMessage carries a PageBlock list —
    // the same block type Instant View pages use — parsed
    // once here through InstantViewStore.parseBlocks. The
    // bubble renders the text-bearing blocks with basic
    // styles; media and table blocks degrade to labelled
    // placeholder lines (see RichBlocksBubbleContent).
    // [text] holds the blocks' plain-text skeleton for
    // previews, quotes and search.
    val richBlocks: List<IvBlock> = emptyList(),
    // Failed-send detail (v0.9.44, N1/N2): the full
    // messageSendingStateFailed payload — error code +
    // protocol identifier, TDLib's own can_retry verdict,
    // the remaining slow-mode / flood wait and the Stars a
    // resend would require. Before this version the parse
    // kept only the word "failed" and the UI offered a
    // resend tap for failures TDLib says can never be
    // resent (can_retry = false), looping forever.
    // Classification into display text lives in
    // SendFailure; these are the raw typed inputs.
    val failCode: Int = 0,
    val failId: String = "",
    val canRetry: Boolean = false,
    val retryAfterSec: Double = 0.0,
    val paidStarsRequired: Long = 0L
) {
    val paidPurchased: Boolean
        get() = paidMedia.any {
            it.kind == "photo" || it.kind == "video"
        }
}

/** A click effect for one animated-emoji message
 *  (v0.9.16, W4): clickAnimatedEmojiMessage answered with a
 *  big sticker (fileId != 0 — shown as a static overlay,
 *  since the app has no Lottie player) or with a 404, which
 *  per td_api.tl means "play the usual animation" (fileId
 *  == 0 — the bubble bounces its base rendering instead).
 *  [nonce] distinguishes two clicks on the same message. */
data class AnimEffect(
    val messageId: Long,
    val fileId: Int,
    val nonce: Long
)

/** One picked photo/video for an album send (v0.9.15, V4). */
data class AlbumPart(
    val path: String,
    val mimeType: String,
    val fileName: String
)

/** How one send should deviate from the plain immediate send
 *  (v0.9.3, M5): silent (no notification for the other side)
 *  and/or scheduled (messageSendOptions.scheduling_state). */
data class SendModifiers(
    val silent: Boolean = false,
    val scheduleAtSec: Long = 0L,
    val whenOnline: Boolean = false,
    // Suppress the link preview for this one text message
    // (v0.9.24, Q3): rides inputMessageText.link_preview_options.
    val disableLinkPreview: Boolean = false,
    // Message effect for this send (v0.9.24, Q8): rides
    // messageSendOptions.effect_id; private chats only.
    val effectId: Long = 0L
) {
    val isDefault: Boolean
        get() = !silent && scheduleAtSec == 0L && !whenOnline &&
            effectId == 0L
}

/** In-chat search type filters (v0.9.31, X3), mapped to
 *  searchChatMessages' SearchMessagesFilter types (verified
 *  in td_api.tl). ALL keeps the historical Empty filter. */
enum class ChatSearchFilter(val type: String, val label: String) {
    ALL("searchMessagesFilterEmpty", "全部"),
    PHOTO("searchMessagesFilterPhoto", "照片"),
    DOCUMENT("searchMessagesFilterDocument", "文件"),
    URL("searchMessagesFilterUrl", "链接")
}

/**
 * State for the one chat currently open on screen: history paging,
 * live appends, send bookkeeping (pending/succeeded/failed via
 * TDLib's old_message_id reconciliation), photo downloads, read
 * marking, and a small user cache for sender names in groups.
 */
/** Bottom-dock / open-state of the chat page (v0.9.44,
 *  N3/N4). One value decides what sits under the message
 *  list and whether the list area shows messages, a
 *  spinner or an error page. */
enum class ChatAccessKind {
    /** Still resolving the chat (getChat / member status
     *  in flight). The page shows a loading state, never a
     *  fake empty chat. */
    LOADING,

    /** The chat could not be opened at all (deleted,
     *  private without an invite, resolution failed).
     *  [ChatAccess.detail] says why in plain language. */
    OPEN_ERROR,

    /** Member in good standing: the composer shows. */
    CAN_SEND,

    /** Not a member of a public group / channel: the join
     *  bar shows; readable content still lists above it. */
    JOINABLE,

    /** A join request is with the admins (approval-type
     *  group): the pending bar shows. */
    JOIN_REQUESTED,

    /** Member, but the group's default permissions forbid
     *  basic messages for everyone (admin-set all-mute). */
    NO_SEND_ALL_MUTED,

    /** Personally restricted by an admin: [ChatAccess]
     *  .untilSec carries the restriction's end when the
     *  server gives one (0 = indefinite). */
    NO_SEND_PERSONAL,

    /** Removed from the group (banned). */
    NO_SEND_BANNED,

    /** Member of a channel: only admins post there. */
    NO_SEND_CHANNEL
}

data class ChatAccess(
    val kind: ChatAccessKind,
    val detail: String = "",
    val untilSec: Int = 0,
    val isChannel: Boolean = false
)

/** The chat's action bar (chat.action_bar, v0.9.54 Y1):
 *  TDLib's own word on which safety strip the top of this
 *  chat must offer — the stranger-protection bar of the
 *  official client. Before this version the field was never
 *  parsed, so a stranger's first message arrived with no
 *  report / block / add-contact strip at all. [kind] is one
 *  of: report_spam, report_add_block, add_contact,
 *  share_phone, invite_members, join_request. */
data class ChatActionBarUi(
    val kind: String,
    /** The chat was auto-archived by the stranger filter
     *  and may be moved back to the main list (the tl spells
     *  out the recipe: addChatToList + default notification
     *  settings). */
    val canUnarchive: Boolean = false,
    /** chatActionBarJoinRequest payload. */
    val joinTitle: String = "",
    val joinIsChannel: Boolean = false,
    /** chatActionBarReportAddBlock's account_info, flattened
     *  for the anti-scam info line; 0 / empty = unknown. */
    val regYear: Int = 0,
    val regMonth: Int = 0,
    val phoneCountry: String = ""
)

/** Chats whose join request this account has sent and is
 *  still waiting on (v0.9.44, N4). TDLib gives the
 *  requester no dedicated "my request is pending" field —
 *  after joinChat on an approval-type group the member
 *  status simply stays Left — so the fact is remembered
 *  locally per account slot (chat ids are only meaningful
 *  inside one account) and cleared the moment the status
 *  comes back as a real membership. */
object JoinPending {
    private const val PREFS = "join_pending"

    @Volatile
    private var appContext: android.content.Context? = null

    @Volatile
    private var slot = 0

    private val ids = java.util.concurrent.ConcurrentHashMap
        .newKeySet<Long>()

    private fun key() = "pending_$slot"

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
        slot = AccountStore.currentSlot()
        ids.clear()
        val raw = appContext?.getSharedPreferences(
            PREFS, android.content.Context.MODE_PRIVATE
        )?.getString(key(), "") ?: ""
        if (raw.isNotEmpty()) {
            try {
                val arr = org.json.JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val id = arr.optLong(i, 0L)
                    if (id != 0L) ids.add(id)
                }
            } catch (t: Throwable) {
                // A corrupt record just means the pending
                // bars stop showing until the next request.
            }
        }
    }

    fun isPending(chatId: Long): Boolean = chatId in ids

    fun setPending(chatId: Long, pending: Boolean) {
        if (chatId == 0L) return
        val changed = if (pending) {
            ids.add(chatId)
        } else {
            ids.remove(chatId)
        }
        if (!changed) return
        val ctx = appContext ?: return
        val arr = org.json.JSONArray()
        for (id in ids) arr.put(id)
        ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString(key(), arr.toString()).apply()
    }
}

object MessageStore {
    private val _messages = MutableStateFlow<List<MsgItem>>(emptyList())
    val messages: StateFlow<List<MsgItem>> = _messages

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError

    /** The message the next send will reply to, if the user picked
     *  "reply" from the long-press menu. */
    private val _replyTarget = MutableStateFlow<MsgItem?>(null)
    val replyTarget: StateFlow<MsgItem?> = _replyTarget

    // Quote mode for the current reply target (v0.9.15, V2):
    // when true, the next send carries an inputTextQuote with
    // the target's text, not just a bare reply pointer.
    private val _replyQuote = MutableStateFlow(false)
    val replyQuote: StateFlow<Boolean> = _replyQuote

    // Server cap for a reply quote's length, in characters
    // (getOption("message_reply_quote_length_max") per the
    // inputTextQuote docs in td_api.tl; 1024 is TDLib's own
    // default, replaced by the live value on first use).
    @Volatile
    private var quoteLimit = 1024

    @Volatile
    private var quoteLimitFetched = false

    // The only entity kinds td_api.tl allows inside a reply
    // quote (DateTime has no flattened kind here — rich
    // messages are out of this app's quote scope).
    private val QUOTE_ENTITY_KINDS = setOf(
        "bold", "italic", "underline", "strike", "spoiler",
        "customEmoji"
    )

    // ---- Drafts + peer typing state (v0.7.0, F5) ----

    /** The chat's saved draft text (from the chat object's
     *  draft_message, which TDLib syncs across devices itself). */
    private val _draftText = MutableStateFlow("")
    val draftText: StateFlow<String> = _draftText

    /** True once the open chat's draft has been read; the UI
     *  must not save (or restore) before this, or opening a chat
     *  could wipe a cloud draft it has not seen yet. */
    private val _draftLoaded = MutableStateFlow(false)
    val draftLoaded: StateFlow<Boolean> = _draftLoaded

    /** Bumped when the open chat's draft changes on the
     *  server side (updateChatDraftMessage — e.g. edited on
     *  another device; v0.9.49, T2). The UI applies the new
     *  text only while the input box holds no unsaved local
     *  edits, so a remote draft can never clobber typing. */
    private val _draftRevision = MutableStateFlow(0)
    val draftRevision: StateFlow<Int> = _draftRevision

    @Volatile
    private var lastSavedDraft: String? = null

    /** What the other side is doing right now, for the top-bar
     *  subtitle: "正在输入…" / "正在录音…" (prefixed with the
     *  sender's name in groups). */
    private val _peerAction = MutableStateFlow("")
    val peerAction: StateFlow<String> = _peerAction

    /** One live peer action (v0.9.28, U5): kept structured
     *  (who + what + when) so the subtitle can aggregate
     *  several actors instead of showing one arbitrary
     *  entry. */
    private data class PeerActionEntry(
        val name: String,
        val label: String,
        val at: Long
    )

    private val peerActions =
        ConcurrentHashMap<Long, PeerActionEntry>()

    @Volatile
    private var lastActionAt = 0L

    @Volatile
    private var lastTypingSentAt = 0L

    /** In-flight reply-summary fetches, keyed "chatId:messageId"
     *  (message ids repeat across chats). */
    private val replyFetchPending = ConcurrentHashMap.newKeySet<String>()

    // ---- In-chat search + jump-to-message (Batch 2, B4) ----

    private val _searchResults = MutableStateFlow<List<MsgItem>>(emptyList())
    val searchResults: StateFlow<List<MsgItem>> = _searchResults

    private val _searchExhausted = MutableStateFlow(true)
    val searchExhausted: StateFlow<Boolean> = _searchExhausted

    /** Approximate total hits of the running search
     *  (foundChatMessages.total_count, v0.9.31 X3); -1 while
     *  unknown. Approximate by the server's own definition —
     *  the UI labels it as such through the loaded count. */
    private val _searchTotal = MutableStateFlow(-1)
    val searchTotal: StateFlow<Int> = _searchTotal

    /** Active type filter of the running search (X3). */
    private val _searchFilter =
        MutableStateFlow(ChatSearchFilter.ALL)
    val searchFilter: StateFlow<ChatSearchFilter> = _searchFilter

    /** Index (into [searchResults], newest first) of the hit
     *  the user is currently navigated to; -1 = none yet. */
    private val _searchNavIndex = MutableStateFlow(-1)
    val searchNavIndex: StateFlow<Int> = _searchNavIndex

    /** The navigated-to hit and the query that produced it:
     *  the bubble for this message highlights the query in
     *  its text (X3). Cleared with the search UI. */
    private val _searchHighlightId = MutableStateFlow(0L)
    val searchHighlightId: StateFlow<Long> = _searchHighlightId

    private val _searchHighlightQuery = MutableStateFlow("")
    val searchHighlightQuery: StateFlow<String> = _searchHighlightQuery

    private var searchQuery = ""
    private var searchFromId = 0L
    // In-chat search generation (v0.9.47, R7): bumped by
    // every fresh (non-append) search and by open(); a
    // response from an older generation is dropped, so a
    // slow answer to a superseded query or filter can no
    // longer append its hits into the current result list.
    // The global SearchStore has had the same guard since
    // its first version; this store was missing it.
    private var searchGeneration = 0
    /** A navigation target waiting for the next results page
     *  (X3): set when the user steps past the loaded end;
     *  consumed by the search response handler. */
    private var searchPendingNav = -1

    // ---- Pinned messages (v0.9.4, N3) ----

    /** All pinned messages of the open chat, newest first.
     *  This tl has no chat.pinned_message_id and no pinned
     *  update, so the list comes from searchChatMessages with
     *  the Pinned filter (verified in td_api.tl) and is
     *  reloaded on open and after every pin/unpin. */
    private val _pinnedMessages = MutableStateFlow<List<MsgItem>>(emptyList())
    val pinnedMessages: StateFlow<List<MsgItem>> = _pinnedMessages

    private val _replyKb = MutableStateFlow<ReplyKb>(ReplyKb.Hidden)
    val replyKb: StateFlow<ReplyKb> = _replyKb

    fun loadPinned(chatId: Long) {
        loadPinnedPage(chatId, 0L, ArrayList(), HashSet())
    }

    /** One page of the pinned list (v0.9.11, A3): the old
     *  loader stopped after a single searchChatMessages page
     *  of 100, silently dropping every older pinned message —
     *  a truncation in OUR code, since the page size is the
     *  client's choice. It now pages with the from_message_id
     *  cursor under the D1 stop rule (a page that adds no new
     *  message ends the walk) until the server has no more.
     *  This widens a client-side display limit only; the
     *  server's own rules are untouched. */
    private fun loadPinnedPage(
        chatId: Long,
        fromId: Long,
        acc: ArrayList<MsgItem>,
        seen: HashSet<Long>
    ) {
        val query = TdClient.request("searchChatMessages").apply {
            put("chat_id", chatId)
            put("topic_id", JSONObject.NULL)
            put("query", "")
            put("sender_id", JSONObject.NULL)
            put("from_message_id", fromId)
            put("offset", 0)
            put("limit", 100)
            put(
                "filter",
                JSONObject().put("@type", "searchMessagesFilterPinned")
            )
        }
        TdRepository.sendRaw(query) { response ->
            if (currentChatId != chatId || TdClient.isError(response)) {
                return@sendRaw
            }
            val arr = response.optJSONArray("messages") ?: JSONArray()
            var added = 0
            var lastId = fromId
            for (i in 0 until arr.length()) {
                val msg = arr.optJSONObject(i) ?: continue
                val item = parseMessage(msg)
                lastId = item.id
                if (seen.add(item.id)) {
                    acc.add(item)
                    added++
                }
            }
            _pinnedMessages.value = ArrayList(acc)
            if (added > 0 && lastId != fromId) {
                loadPinnedPage(chatId, lastId, acc, seen)
            }
        }
    }

    /** Pin a message for everyone in the chat (pinChatMessage,
     *  verified in td_api.tl). A refusal — typically missing
     *  rights in a group/channel — is translated to plain
     *  language, never a raw code. */
    fun pinMessage(item: MsgItem) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val query = TdClient.request("pinChatMessage").apply {
            put("chat_id", chatId)
            put("message_id", item.id)
            put("disable_notification", true)
            put("only_for_self", false)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = pinErrorText(response, unpin = false)
            } else {
                byId[item.id]?.let {
                    byId[item.id] = it.copy(isPinned = true)
                }
                publish()
                loadPinned(chatId)
            }
        }
    }

    /** Unpin a message (unpinChatMessage, verified in
     *  td_api.tl); same error treatment as pinMessage. */
    fun unpinMessage(item: MsgItem) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val query = TdClient.request("unpinChatMessage").apply {
            put("chat_id", chatId)
            put("message_id", item.id)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = pinErrorText(response, unpin = true)
            } else {
                byId[item.id]?.let {
                    byId[item.id] = it.copy(isPinned = false)
                }
                publish()
                loadPinned(chatId)
            }
        }
    }

    /** Copy-link support (v0.9.15, V1): getMessageLink is
     *  verified in td_api.tl — it takes media_timestamp /
     *  checklist_task_id / poll_option_id (all neutral here),
     *  for_album (true when the message belongs to an album,
     *  so the link opens the whole album) and
     *  in_message_thread (false: the main-chat surface asks
     *  for the chat-level link). The answer is
     *  messageLink{link, is_public}; a private link
     *  (is_public = false) only works inside the chat, and
     *  the server refuses outright where no link exists at
     *  all (private chats, secret chats) — both cases are
     *  reported in plain language through [onFail] instead of
     *  copying something useless. */
    fun fetchMessageLink(
        item: MsgItem,
        onLink: (String) -> Unit,
        onFail: (String) -> Unit
    ) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val query = TdClient.request("getMessageLink").apply {
            put("chat_id", chatId)
            put("message_id", item.id)
            put("media_timestamp", 0)
            put("checklist_task_id", 0)
            put("poll_option_id", "")
            put("for_album", item.mediaAlbumId != 0L)
            put("in_message_thread", false)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                onFail("这条消息没有可分享的链接。")
                return@sendRaw
            }
            val link = response.optString("link")
            when {
                link.isEmpty() ->
                    onFail("这条消息没有可分享的链接。")
                !response.optBoolean("is_public") ->
                    onFail("这条消息只能在对话内部打开，没有公开链接。")
                else -> onLink(link)
            }
        }
    }

    // ---- Message translation (v0.9.5, O4) ----
    // translateMessageText (verified in td_api.tl) works for
    // any chat the message belongs to, so the main chat, the
    // comment threads and the forum topics all share this one
    // implementation, keyed by (chatId, messageId). The tl
    // forbids it in secret chats; the server refuses there
    // and the refusal is translated below like any other.

    private val _translations =
        MutableStateFlow<Map<Pair<Long, Long>, TranslationUi>>(emptyMap())
    val translations: StateFlow<Map<Pair<Long, Long>, TranslationUi>> =
        _translations

    private val _translating =
        MutableStateFlow<Set<Pair<Long, Long>>>(emptySet())
    val translating: StateFlow<Set<Pair<Long, Long>>> = _translating

    private val _translateError = MutableStateFlow<String?>(null)
    val translateError: StateFlow<String?> = _translateError

    fun clearTranslateError() {
        _translateError.value = null
    }

    fun translateMessage(chatId: Long, msg: MsgItem) {
        val key = chatId to msg.id
        if (key in _translating.value) return
        _translating.value = _translating.value + key
        val locale = java.util.Locale.getDefault()
        val lang = locale.language.ifEmpty { "zh" }
        val query = TdClient.request("translateMessageText").apply {
            put("chat_id", chatId)
            put("message_id", msg.id)
            put("to_language_code", lang)
            put("tone", "")
        }
        TdRepository.sendRaw(query) { response ->
            _translating.value = _translating.value - key
            if (TdClient.isError(response)) {
                _translateError.value = translateErrorText(response)
            } else {
                val text = response.optString("text")
                if (text.isEmpty()) {
                    _translateError.value = "这条消息没有能翻译的文字。"
                } else {
                    _translations.value = _translations.value +
                        (key to TranslationUi(
                            text,
                            locale.getDisplayLanguage(locale),
                            entitiesOf(response)
                        ))
                }
            }
        }
    }

    fun hideTranslation(chatId: Long, messageId: Long) {
        _translations.value = _translations.value - (chatId to messageId)
    }

    /** Gate refusals (v0.9.62, G1): the server walls
     *  translation behind Premium, and the tl forbids it in
     *  secret chats. Both refusals are permanent for this
     *  account/chat pair, so an automatic queue must stop
     *  instead of retrying every message into the same wall.
     *  Same signals translateErrorText maps to words. */
    fun isTranslateGateError(error: JSONObject): Boolean {
        val message = error.optString("message")
        return message.contains("PREMIUM", ignoreCase = true) ||
            message.contains("secret", ignoreCase = true)
    }

    /** Auto-translate worker entry (v0.9.62, G1): the same
     *  translateMessageText call as [translateMessage], but
     *  the outcome is reported to the caller instead of the
     *  one-shot error dialog — a queue must not pop a dialog
     *  per failure. [onResult] receives true only for a gate
     *  refusal (see [isTranslateGateError]); a success lands
     *  in the same [translations] flow the manual path uses,
     *  so bubbles render both identically. */
    fun translateAuto(
        chatId: Long,
        msg: MsgItem,
        onResult: (Boolean) -> Unit
    ) {
        val key = chatId to msg.id
        if (key in _translating.value ||
            key in _translations.value
        ) {
            onResult(false)
            return
        }
        _translating.value = _translating.value + key
        val locale = java.util.Locale.getDefault()
        val lang = locale.language.ifEmpty { "zh" }
        val query = TdClient.request("translateMessageText").apply {
            put("chat_id", chatId)
            put("message_id", msg.id)
            put("to_language_code", lang)
            put("tone", "")
        }
        TdRepository.sendRaw(query) { response ->
            _translating.value = _translating.value - key
            when {
                TdClient.isError(response) ->
                    onResult(isTranslateGateError(response))
                response.optString("text").isEmpty() ->
                    onResult(false)
                else -> {
                    _translations.value = _translations.value +
                        (key to TranslationUi(
                            response.optString("text"),
                            locale.getDisplayLanguage(locale),
                            entitiesOf(response)
                        ))
                    onResult(false)
                }
            }
        }
    }

    // ---- Voice speech recognition (v0.9.55, Z3) ----
    // recognizeSpeech (verified in td_api.tl) asks the
    // server to transcribe a voice note. The result does
    // NOT come back on this call: TDLib rewrites the
    // message content with the voice note's
    // speech_recognition_result (pending -> text / error),
    // which the parser turns into MsgItem.speechState /
    // speechText and every host re-renders through
    // updateMessageContent. This call only fails up front
    // (the Premium gate, secret chats), and that refusal
    // is translated to plain language like the translation
    // refusals. The recognizing set covers the gap between
    // the tap and the first pending update; a finished
    // state clears it in the updateMessageContent handler.

    private val _recognizing =
        MutableStateFlow<Set<Pair<Long, Long>>>(emptySet())
    val recognizing: StateFlow<Set<Pair<Long, Long>>> = _recognizing

    private val _speechError = MutableStateFlow<String?>(null)
    val speechError: StateFlow<String?> = _speechError

    fun clearSpeechError() {
        _speechError.value = null
    }

    fun recognizeSpeech(chatId: Long, msg: MsgItem) {
        val key = chatId to msg.id
        if (key in _recognizing.value) return
        _recognizing.value = _recognizing.value + key
        val query = TdClient.request("recognizeSpeech").apply {
            put("chat_id", chatId)
            put("message_id", msg.id)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _recognizing.value = _recognizing.value - key
                _speechError.value = speechErrorText(response)
            }
        }
    }

    private fun speechErrorText(error: JSONObject): String {
        val message = error.optString("message")
        return when {
            message.contains("PREMIUM", ignoreCase = true) ->
                "语音转文字是 Telegram Premium 的功能，当前账号用不了。" +
                    "这条语音本身没有被改动。"
            message.contains("secret", ignoreCase = true) ->
                "秘密聊天里的语音不能转文字。"
            else ->
                "转文字没能开始，再试一次。（错误码 ${error.optInt("code")}）"
        }
    }

    private fun translateErrorText(error: JSONObject): String {
        val message = error.optString("message")
        return when {
            message.contains("PREMIUM", ignoreCase = true) ->
                "翻译是 Telegram Premium 的功能，当前账号用不了，消息本身没有被改动。"
            message.contains("secret", ignoreCase = true) ->
                "秘密聊天里的消息不能翻译。"
            else ->
                "翻译失败，再试一次。（错误码 ${error.optInt("code")}）"
        }
    }

    private fun pinErrorText(error: JSONObject, unpin: Boolean): String {
        val message = error.optString("message")
        return when {
            message.contains("CHAT_ADMIN_REQUIRED") ||
                message.contains("not enough rights") ||
                message.contains("have no rights") ->
                "你没有在这个对话里置顶消息的权限。"
            message.contains("PIN", ignoreCase = true) &&
                message.contains("not", ignoreCase = true) ->
                "这条消息不能置顶。"
            else ->
                if (unpin) {
                    "取消置顶失败，再试一次。（错误码 ${error.optInt("code")}）"
                } else {
                    "置顶失败，再试一次。（错误码 ${error.optInt("code")}）"
                }
        }
    }

    /** Set by navigation (global search / in-chat search) before
     *  or after opening a chat; the chat screen consumes it and
     *  jumps to that message. */
    @Volatile
    var pendingLocateId: Long = 0L

    private val _locateId = MutableStateFlow(0L)
    val locateId: StateFlow<Long> = _locateId

    /** searchChatMessages paging: from_message_id cursor plus
     *  next_from_message_id from the previous page (verified in
     *  td_api.tl — a different scheme from global search).
     *
     *  v0.9.31 (X3): [filter] narrows by content type; a fresh
     *  (non-append) call restarts paging, the total and the
     *  navigation cursor, and once its first page lands the
     *  newest hit is navigated to automatically, like the
     *  official client's search. Changing only the filter
     *  re-runs the same query under the new filter. */
    fun searchInChat(
        query: String,
        append: Boolean = false,
        filter: ChatSearchFilter = _searchFilter.value
    ) {
        val chatId = currentChatId
        if (chatId == 0L) return
        if (!append) {
            searchGeneration++
            searchQuery = query.trim()
            searchFromId = 0L
            _searchFilter.value = filter
            _searchResults.value = emptyList()
            _searchTotal.value = -1
            _searchNavIndex.value = -1
            searchPendingNav = -1
            _searchExhausted.value = searchQuery.isEmpty()
            if (searchQuery.isEmpty()) return
        }
        val gen = searchGeneration
        val q = TdClient.request("searchChatMessages").apply {
            put("chat_id", chatId)
            put("topic_id", JSONObject.NULL)
            put("query", searchQuery)
            put("sender_id", JSONObject.NULL)
            put("from_message_id", searchFromId)
            put("offset", 0)
            put("limit", 30)
            put(
                "filter",
                JSONObject().put("@type", _searchFilter.value.type)
            )
        }
        TdRepository.sendRaw(q) { response ->
            if (currentChatId != chatId) return@sendRaw
            // Stale search (v0.9.47, R7): a newer query or
            // filter superseded this request while it was in
            // flight; its hits belong to the old search.
            if (gen != searchGeneration) return@sendRaw
            if (TdClient.isError(response)) {
                _searchExhausted.value = true
                searchPendingNav = -1
                return@sendRaw
            }
            val total = response.optInt("total_count", -1)
            if (total >= 0) _searchTotal.value = total
            val next = response.optLong("next_from_message_id")
            searchFromId = next
            if (next == 0L) _searchExhausted.value = true
            val arr = response.optJSONArray("messages") ?: JSONArray()
            if (arr.length() == 0) {
                _searchExhausted.value = true
                searchPendingNav = -1
                return@sendRaw
            }
            val items = ArrayList<MsgItem>()
            for (i in 0 until arr.length()) {
                val msg = arr.optJSONObject(i) ?: continue
                items.add(parseMessage(msg))
            }
            _searchResults.value = _searchResults.value + items
            // Navigation follow-through (X3): a fresh search
            // lands on its newest hit; a step that ran past
            // the loaded end completes once its page arrives.
            val pending = searchPendingNav
            when {
                pending >= 0 -> {
                    searchPendingNav = -1
                    if (pending < _searchResults.value.size) {
                        navigateSearchTo(pending)
                    }
                }
                !append && _searchNavIndex.value == -1 &&
                    _searchResults.value.isNotEmpty() ->
                    navigateSearchTo(0)
            }
        }
    }

    /** Jump the chat window to the hit at [index] (X3) and
     *  arm its query highlight. Out-of-range indexes clamp;
     *  stepping past the loaded end pages more results in
     *  first (the response handler finishes the step). */
    fun navigateSearchTo(index: Int) {
        val results = _searchResults.value
        if (results.isEmpty() || index < 0) return
        if (index >= results.size) {
            if (_searchExhausted.value) return
            searchPendingNav = index
            searchInChat(searchQuery, append = true)
            return
        }
        _searchNavIndex.value = index
        val hit = results[index]
        _searchHighlightId.value = hit.id
        _searchHighlightQuery.value = searchQuery
        jumpTo(hit.id)
    }

    /** One step to the next older hit (X3). */
    fun searchNavOlder() {
        val next = _searchNavIndex.value + 1
        if (next < _searchResults.value.size || !_searchExhausted.value) {
            navigateSearchTo(next)
        }
    }

    /** One step back to the next newer hit (X3). */
    fun searchNavNewer() {
        val prev = _searchNavIndex.value - 1
        if (prev >= 0) navigateSearchTo(prev)
    }

    /** The search UI closed (X3): drop the highlight and the
     *  navigation cursor; results themselves stay cached
     *  until the next fresh search replaces them. */
    fun clearSearchNav() {
        _searchNavIndex.value = -1
        searchPendingNav = -1
        _searchHighlightId.value = 0L
        _searchHighlightQuery.value = ""
    }

    /** Replace the loaded history with the window around
     *  [messageId] (the message plus neighbours on both sides)
     *  and flag it for the UI to scroll to. getChatHistory with a
     *  negative offset reaches back from the anchor. */
    fun jumpTo(messageId: Long) {
        val chatId = currentChatId
        if (chatId == 0L || messageId == 0L) return
        historyGeneration++
        val gen = historyGeneration
        byId.clear()
        _messages.value = emptyList()
        allHistoryLoaded = false
        olderRetryAnchor = 0L
        _atLatest.value = false
        val query = TdClient.request("getChatHistory").apply {
            put("chat_id", chatId)
            put("from_message_id", messageId)
            put("offset", -25)
            put("limit", 50)
            put("only_local", false)
        }
        TdRepository.sendRaw(query) { response ->
            if (currentChatId != chatId) return@sendRaw
            if (gen != historyGeneration) return@sendRaw
            if (TdClient.isError(response)) return@sendRaw
            val arr = response.optJSONArray("messages") ?: JSONArray()
            // A short jump window says nothing about older
            // history (D1): leave the flag alone and let
            // loadOlder probe from the window's oldest message;
            // its no-new-older-messages rule settles the flag.
            if (arr.length() == 0) allHistoryLoaded = true
            for (i in 0 until arr.length()) {
                val msg = arr.optJSONObject(i) ?: continue
                val item = parseMessage(msg)
                byId[item.id] = item
            }
            publish()
            _locateId.value = messageId
        }
    }

    // ---- Jump to a date (v0.9.16, W3) ----
    // getChatMessageByDate returns the last message sent no
    // later than the given date, or a 404 error when there is
    // none (td_api.tl L13099). Three modes fall out of that:
    // A) a message exists before the picked day: the day's
    //    first message is its immediate successor, inside the
    //    normal jump window around it;
    // B) no message before the day but one on it: the day is
    //    the chat's first day — page older from the day's
    //    last message (D1 stop rule) to its first;
    // C) no message on or before the day at all: the whole
    //    chat is newer — probe forward exponentially for any
    //    message, then page older to the first one past the
    //    day, and say plainly that the day itself is empty.

    private val _dateJumpHint = MutableStateFlow<String?>(null)
    val dateJumpHint: StateFlow<String?> = _dateJumpHint

    fun clearDateJumpHint() {
        _dateJumpHint.value = null
    }

    private fun fetchMessageByDate(
        chatId: Long,
        dateSec: Int,
        cb: (JSONObject?) -> Unit
    ) {
        val gen = historyGeneration
        val query = TdClient.request("getChatMessageByDate").apply {
            put("chat_id", chatId)
            put("date", dateSec)
        }
        TdRepository.sendRaw(query) { response ->
            if (currentChatId != chatId) return@sendRaw
            // A newer window operation supersedes the whole
            // date-jump chain (v0.9.28, U2): every step of the
            // chain enters through here, so dropping here
            // starves the rest of the chain.
            if (gen != historyGeneration) return@sendRaw
            cb(if (TdClient.isError(response)) null else response)
        }
    }

    fun jumpToDate(dayStartSec: Int, dayEndSec: Int) {
        val chatId = currentChatId
        if (chatId == 0L) return
        // The whole date-jump chain replaces the window
        // (v0.9.28, U2): one generation bump covers every
        // step of it, and the window no longer reaches the
        // newest message until backToLatest reloads it.
        historyGeneration++
        olderRetryAnchor = 0L
        _atLatest.value = false
        fetchMessageByDate(chatId, dayStartSec - 1) { prev ->
            if (prev != null) {
                val prevId = prev.optLong("id")
                loadDateWindow(chatId, prevId) {
                    if (_messages.value.isEmpty()) {
                        _dateJumpHint.value =
                            "没跳过去，检查网络后再试一次。"
                        return@loadDateWindow
                    }
                    val target = _messages.value
                        .firstOrNull { it.dateSeconds >= dayStartSec }
                    when {
                        target == null -> {
                            _locateId.value = prevId
                            _dateJumpHint.value =
                                "那天之后还没有消息，已跳到之前最近的一条。"
                        }
                        target.dateSeconds > dayEndSec -> {
                            _locateId.value = target.id
                            _dateJumpHint.value =
                                "那天没有消息，已跳到最近的下一条。"
                        }
                        else -> _locateId.value = target.id
                    }
                }
            } else {
                fetchMessageByDate(chatId, dayEndSec) { lastOfDay ->
                    if (lastOfDay != null) {
                        pageOlderUntil(
                            chatId, lastOfDay,
                            continueWhileDateAtLeast = dayStartSec,
                            maxPages = 20
                        ) {
                            val target = _messages.value.firstOrNull {
                                it.dateSeconds >= dayStartSec
                            }
                            if (target != null) {
                                _locateId.value = target.id
                            }
                        }
                    } else {
                        probeAfterDay(chatId, dayEndSec)
                    }
                }
            }
        }
    }

    /** Mode C of jumpToDate: probe dates after the picked
     *  day with exponentially growing steps until some
     *  message answers, then page older to the first message
     *  past the day. */
    private fun probeAfterDay(chatId: Long, dayEndSec: Int) {
        val nowSec = (System.currentTimeMillis() / 1000).toInt()
        val probes = ArrayList<Int>()
        var deltaDays = 1
        while (true) {
            val t = dayEndSec.toLong() + deltaDays * 86400L
            if (t >= nowSec) {
                probes.add(nowSec)
                break
            }
            probes.add(t.toInt())
            if (deltaDays >= 512) {
                probes.add(nowSec)
                break
            }
            deltaDays *= 2
        }
        fun tryProbe(index: Int) {
            if (index >= probes.size) {
                _dateJumpHint.value = "这个对话还没有消息。"
                return
            }
            fetchMessageByDate(chatId, probes[index]) { msg ->
                if (msg == null) {
                    tryProbe(index + 1)
                } else {
                    pageOlderUntil(
                        chatId, msg,
                        continueWhileDateAtLeast = dayEndSec + 1,
                        maxPages = 20
                    ) {
                        val target = _messages.value.firstOrNull {
                            it.dateSeconds > dayEndSec
                        }
                        if (target != null) {
                            _locateId.value = target.id
                            _dateJumpHint.value =
                                "那天没有消息，已跳到最近的下一条。"
                        }
                    }
                }
            }
        }
        tryProbe(0)
    }

    /** The jumpTo window fetch with a completion callback
     *  (jumpTo itself stays untouched for its other callers). */
    private fun loadDateWindow(
        chatId: Long,
        anchorId: Long,
        done: () -> Unit
    ) {
        val gen = historyGeneration
        byId.clear()
        _messages.value = emptyList()
        allHistoryLoaded = false
        val query = TdClient.request("getChatHistory").apply {
            put("chat_id", chatId)
            put("from_message_id", anchorId)
            put("offset", -25)
            put("limit", 50)
            put("only_local", false)
        }
        TdRepository.sendRaw(query) { response ->
            if (currentChatId != chatId) return@sendRaw
            // Superseded by a newer window operation: abandon
            // the chain silently (done() would aim the locate
            // at a window that no longer exists).
            if (gen != historyGeneration) return@sendRaw
            if (!TdClient.isError(response)) {
                val arr = response.optJSONArray("messages") ?: JSONArray()
                if (arr.length() == 0) allHistoryLoaded = true
                for (i in 0 until arr.length()) {
                    val msg = arr.optJSONObject(i) ?: continue
                    val item = parseMessage(msg)
                    byId[item.id] = item
                }
                publish()
            }
            done()
        }
    }

    /** Replace the loaded history with [first] plus older
     *  pages (100 at a time), continuing while the oldest
     *  loaded message's date is still >=
     *  [continueWhileDateAtLeast] and the page budget lasts.
     *  A page that brings no older message ends the walk with
     *  the chat's first message loaded (the D1 stop rule). */
    private fun pageOlderUntil(
        chatId: Long,
        first: JSONObject,
        continueWhileDateAtLeast: Int,
        maxPages: Int,
        done: () -> Unit
    ) {
        val gen = historyGeneration
        byId.clear()
        _messages.value = emptyList()
        allHistoryLoaded = false
        val firstItem = parseMessage(first)
        byId[firstItem.id] = firstItem
        var pages = 0
        fun step() {
            val oldest = byId.values.minByOrNull { it.id }
            if (oldest == null ||
                oldest.dateSeconds < continueWhileDateAtLeast ||
                pages >= maxPages
            ) {
                publish()
                done()
                return
            }
            pages++
            val query = TdClient.request("getChatHistory").apply {
                put("chat_id", chatId)
                put("from_message_id", oldest.id)
                put("offset", 0)
                put("limit", 100)
                put("only_local", false)
            }
            TdRepository.sendRaw(query) { response ->
                if (currentChatId != chatId) return@sendRaw
                if (gen != historyGeneration) return@sendRaw
                if (TdClient.isError(response)) {
                    publish()
                    done()
                    return@sendRaw
                }
                val arr = response.optJSONArray("messages") ?: JSONArray()
                var addedOlder = false
                for (i in 0 until arr.length()) {
                    val msg = arr.optJSONObject(i) ?: continue
                    val item = parseMessage(msg)
                    if (item.id < oldest.id) addedOlder = true
                    byId[item.id] = item
                }
                if (!addedOlder) {
                    allHistoryLoaded = true
                    publish()
                    done()
                    return@sendRaw
                }
                step()
            }
        }
        step()
    }

    // ---- Animated emoji clicks (v0.9.16, W4) ----

    private val _animEffect = MutableStateFlow<AnimEffect?>(null)
    val animEffect: StateFlow<AnimEffect?> = _animEffect

    @Volatile
    private var animNonce = 0L

    /** A tap on an animated-emoji message: tell TDLib
     *  (clickAnimatedEmojiMessage) and publish the effect
     *  to play. A Sticker answer carries the big effect
     *  sticker (its file is noted with the shared resolver
     *  and pulled if needed); a 404 is the documented "play
     *  the usual animation" outcome, published as a
     *  fileId == 0 effect (the bubble bounces). Any other
     *  error stays silent — a decorative tap must never
     *  surface as a send failure. The effect clears itself
     *  after a few seconds, nonce-guarded so a fast second
     *  tap is not cut short by the first tap's timer. */
    fun clickAnimatedEmoji(msg: MsgItem) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val nonce = ++animNonce
        val query = TdClient.request("clickAnimatedEmojiMessage").apply {
            put("chat_id", chatId)
            put("message_id", msg.id)
        }
        TdRepository.sendRaw(query) { response ->
            if (currentChatId != chatId) return@sendRaw
            val effect = if (TdClient.isError(response)) {
                AnimEffect(msg.id, 0, nonce)
            } else {
                val file = response.optJSONObject("sticker")
                val fileId = file?.optInt("id", 0) ?: 0
                if (fileId != 0) {
                    AvatarStore.noteFile(file)
                    AvatarStore.ensureDownload(fileId)
                }
                AnimEffect(msg.id, fileId, nonce)
            }
            _animEffect.value = effect
            Handler(Looper.getMainLooper()).postDelayed({
                if (_animEffect.value?.nonce == nonce) {
                    _animEffect.value = null
                }
            }, 3000)
        }
    }

    @Volatile
    var currentChatId: Long = 0L
        private set

    /** Newest message the other side has read (from the chat's
     *  last_read_outbox_message_id, kept current by
     *  updateChatReadOutbox). Drives the sent/read ticks on bubbles. */
    @Volatile
    private var lastReadOutbox = 0L

    /** Newest incoming message already read when this chat
     *  was opened (last_read_inbox_message_id from the
     *  open-time getChat; v0.9.11, A2). The chat screen draws
     *  the "unread messages" divider above the first message
     *  past it. Captured once per open on purpose: messages
     *  read live while the chat is open must not move it. */
    @Volatile
    var lastReadInboxAtOpen = 0L
        private set

    @Volatile
    private var allHistoryLoaded = false

    @Volatile
    private var loadingHistory = false

    /** One delayed retry is allowed when the very first history
     *  page comes back empty — on a fresh login the local
     *  database may still be filling in (Phase D, D1). */
    @Volatile
    private var firstPageRetried = false

    // ---- Deep-paging hardening (v0.9.28, U2) ----

    /** Window generation: bumped every time the loaded window
     *  is replaced wholesale (open, jumpTo, the date-jump
     *  fetches, backToLatest, close). History responses carry
     *  the generation they were sent under; a response from a
     *  replaced window is dropped instead of leaking its
     *  messages into the new window (a seam corruption the
     *  plain chat-id check could not catch — same chat, wrong
     *  window). */
    @Volatile
    private var historyGeneration = 0

    /** The from_message_id anchor an older-page retry is
     *  already scheduled for; at most one delayed retry per
     *  anchor, cleared when a page lands with older messages. */
    @Volatile
    private var olderRetryAnchor = 0L

    /** True while any history page is in flight; the chat
     *  screen shows a quiet spinner at the top of the list. */
    private val _historyLoading = MutableStateFlow(false)
    val historyLoading: StateFlow<Boolean> = _historyLoading

    /** True when the loaded window reaches the chat's newest
     *  message. Jump windows (search / date) do not, and the
     *  scroll-to-latest button must reload instead of merely
     *  scrolling when this is false. */
    private val _atLatest = MutableStateFlow(true)
    val atLatest: StateFlow<Boolean> = _atLatest

    // ---- Chat access (v0.9.44, N3/N4) ----------------------
    // One state machine drives the bottom dock of the chat
    // page: composer, join bar, request-pending bar, or a
    // plain "cannot post" bar — plus the resolving / error
    // states of opening a chat this account has never seen.
    // Before this version the dock only knew the group's
    // default permissions (chat.permissions), so a member
    // muted personally by an admin still got a composer,
    // typed, sent, failed — and an unknown chat opened from
    // a link rendered as a fake empty private chat because
    // nothing resolved its title, type or membership.

    private val _access =
        MutableStateFlow(ChatAccess(ChatAccessKind.LOADING))
    val access: StateFlow<ChatAccess> = _access

    // ---- Chat action bar (v0.9.54, Y1) --------------------
    // chat.action_bar of the open chat, parsed from the
    // getChat response and kept current by
    // updateChatActionBar. Null = no strip. The close
    // button is a server action (removeChatActionBar), not
    // a local hide — TDLib clears the field itself and the
    // update lands here.

    private val _actionBar =
        MutableStateFlow<ChatActionBarUi?>(null)
    val actionBar: StateFlow<ChatActionBarUi?> = _actionBar

    /** The other user of a private / secret chat (from the
     *  chat type object); the action bar's block / contact /
     *  share-number actions address them. 0 elsewhere. */
    @Volatile
    var currentPeerUserId = 0L
        private set

    private fun parseActionBar(bar: JSONObject?): ChatActionBarUi? {
        if (bar == null) return null
        return when (bar.optString("@type")) {
            "chatActionBarReportSpam" -> ChatActionBarUi(
                kind = "report_spam",
                canUnarchive = bar.optBoolean("can_unarchive")
            )
            "chatActionBarReportAddBlock" -> {
                val info = bar.optJSONObject("account_info")
                ChatActionBarUi(
                    kind = "report_add_block",
                    canUnarchive = bar.optBoolean("can_unarchive"),
                    regYear = info?.optInt("registration_year") ?: 0,
                    regMonth = info?.optInt("registration_month") ?: 0,
                    phoneCountry = info?.optString(
                        "phone_number_country_code"
                    ) ?: ""
                )
            }
            "chatActionBarAddContact" -> ChatActionBarUi(
                kind = "add_contact"
            )
            "chatActionBarSharePhoneNumber" -> ChatActionBarUi(
                kind = "share_phone"
            )
            "chatActionBarInviteMembers" -> ChatActionBarUi(
                kind = "invite_members"
            )
            "chatActionBarJoinRequest" -> ChatActionBarUi(
                kind = "join_request",
                joinTitle = bar.optString("title"),
                joinIsChannel = bar.optBoolean("is_channel")
            )
            else -> null
        }
    }

    /** The strip's close button (removeChatActionBar,
     *  verified in td_api.tl): the server drops the bar;
     *  the local value clears on success and the update
     *  confirms it. */
    fun dismissActionBar() {
        val chatId = currentChatId
        if (chatId == 0L) return
        val query = TdClient.request("removeChatActionBar").apply {
            put("chat_id", chatId)
        }
        TdRepository.sendRaw(query) { response ->
            if (!TdClient.isError(response) &&
                currentChatId == chatId
            ) {
                _actionBar.value = null
            }
        }
    }

    /** Move an auto-archived stranger chat back to the main
     *  list, with the tl's own recipe (addChatToList +
     *  setChatNotificationSettings back to defaults), then
     *  drop the bar like the close button does. */
    fun unarchiveActionBarChat() {
        val chatId = currentChatId
        if (chatId == 0L) return
        val addQuery = TdClient.request("addChatToList").apply {
            put("chat_id", chatId)
            put("chat_list", JSONObject().put("@type", "chatListMain"))
        }
        TdRepository.sendRaw(addQuery) {}
        val settings = JSONObject().apply {
            put("@type", "chatNotificationSettings")
            put("use_default_mute_for", true)
            put("mute_for", 0)
            put("use_default_sound", true)
            put("sound_id", 0L)
            put("use_default_show_preview", true)
            put("show_preview", false)
            put("use_default_mute_stories", true)
            put("mute_stories", false)
            put("use_default_story_sound", true)
            put("story_sound_id", 0L)
            put("use_default_show_story_poster", true)
            put("show_story_poster", false)
            put("use_default_disable_pinned_message_notifications", true)
            put("disable_pinned_message_notifications", false)
            put("use_default_disable_mention_notifications", true)
            put("disable_mention_notifications", false)
        }
        val settingsQuery =
            TdClient.request("setChatNotificationSettings").apply {
                put("chat_id", chatId)
                put("notification_settings", settings)
            }
        TdRepository.sendRaw(settingsQuery) {}
        dismissActionBar()
    }

    /** True when the first history page of an unjoined chat
     *  came back forbidden: the content exists but only
     *  members may read it; the join bar says so. */
    private val _historyBlocked = MutableStateFlow(false)
    val historyBlocked: StateFlow<Boolean> = _historyBlocked

    /** Group ids of the open chat, captured while computing
     *  access so updateSupergroup / updateBasicGroup can be
     *  matched back to it for a recompute. */
    @Volatile
    private var currentSupergroupId = 0L

    @Volatile
    private var currentBasicGroupId = 0L

    /** The open chat's chat object, kept for access
     *  recomputes (its permissions half lives here; the
     *  member-status half is re-fetched each compute). */
    @Volatile
    private var currentChatObject: JSONObject? = null

    // ---- New-message tracking (v0.9.44, N5) ----------------
    // Whether the user is currently looking at the newest
    // end of the open chat, reported by the chat screen's
    // list state. The read marker and the new-message
    // counter both key off this — before this version every
    // incoming message in the open chat was force-read the
    // moment it arrived wherever the user was scrolled, so
    // the unread counter behind the scroll-to-latest button
    // was structurally always 0 and the button (and any
    // "new messages" signal) could never appear.

    @Volatile
    private var viewingLatest = true

    private val _pendingNew = MutableStateFlow(0)
    val pendingNewCount: StateFlow<Int> = _pendingNew

    /** The chat screen reports its at-bottom state here.
     *  Arriving at the bottom settles everything that piled
     *  up while scrolled away: the counter clears and the
     *  newest loaded message is marked read. */
    fun setViewingLatest(value: Boolean) {
        val was = viewingLatest
        viewingLatest = value
        if (value && !was) {
            _pendingNew.value = 0
            val newest = byId.keys.maxOrNull() ?: return
            if (currentChatId != 0L) markRead(currentChatId, newest)
        }
    }

    private val byId = ConcurrentHashMap<Long, MsgItem>()
    private val userNames = ConcurrentHashMap<Long, String>()

    /** Public usernames by user id (v0.9.39, F3): filled
     *  from the same getUser / updateUser payloads as
     *  [userNames]; the via-bot label prefers the handle
     *  ("via @bot") and falls back to the display name. */
    private val userHandles = ConcurrentHashMap<Long, String>()
    private val userFetchPending = ConcurrentHashMap.newKeySet<Long>()

    @Volatile
    private var attached = false

    private fun attach() {
        if (attached) return
        attached = true
        // Sender photos resolve in AvatarStore; republish so
        // group bubbles gain their avatars as paths land (W0).
        AvatarStore.addListener { publish() }
        TdRepository.addUpdateListener { update -> handleUpdate(update) }
    }

    fun open(chatId: Long) {
        attach()
        currentChatId = chatId
        // Auto-translate queue (v0.9.62, G1): work queued for
        // a chat the user has left is dropped — switching
        // away stops that chat's automatic translation.
        AutoTranslate.onOpenChat(chatId)
        // Access + new-message state start from a clean
        // slate per chat (v0.9.44, N3/N4/N5): no bar, badge
        // or read behaviour may leak across chats.
        _access.value = ChatAccess(ChatAccessKind.LOADING)
        _actionBar.value = null
        currentPeerUserId = 0L
        _historyBlocked.value = false
        _pendingNew.value = 0
        viewingLatest = true
        currentChatObject = null
        currentSupergroupId = 0L
        currentBasicGroupId = 0L
        byId.clear()
        _messages.value = emptyList()
        _sendError.value = null
        clearReplyTarget()
        _searchResults.value = emptyList()
        _searchExhausted.value = true
        _searchTotal.value = -1
        _searchFilter.value = ChatSearchFilter.ALL
        // Any in-flight search belongs to the previous chat
        // (v0.9.47, R7).
        searchGeneration++
        clearSearchNav()
        _pinnedMessages.value = emptyList()
        _replyKb.value = ReplyKb.Hidden
        searchQuery = ""
        searchFromId = 0L
        _locateId.value = 0L
        allHistoryLoaded = false
        firstPageRetried = false
        historyGeneration++
        olderRetryAnchor = 0L
        _atLatest.value = false
        lastReadOutbox = 0L
        lastReadInboxAtOpen = 0L
        _draftText.value = ""
        _draftLoaded.value = false
        lastSavedDraft = null
        peerActions.clear()
        _peerAction.value = ""
        lastTypingSentAt = 0L
        TdRepository.sendRaw(TdClient.request("openChat").apply {
            put("chat_id", chatId)
        }) {}
        // Seed the read position for bubble ticks; afterwards
        // updateChatReadOutbox keeps it current. The same response
        // carries the chat's draft (F5): restore it into the
        // input box once the UI sees draftLoaded flip.
        TdRepository.sendRaw(TdClient.request("getChat").apply {
            put("chat_id", chatId)
        }) { response ->
            if (TdClient.isError(response)) {
                // Opening an unknown chat ends here when the
                // chat cannot be resolved at all (v0.9.44,
                // N4): the page shows the reason instead of
                // a fake empty private chat. A chat that is
                // already on a list answers getChat from the
                // local database and never lands here.
                if (currentChatId == chatId &&
                    _access.value.kind == ChatAccessKind.LOADING
                ) {
                    _access.value = ChatAccess(
                        ChatAccessKind.OPEN_ERROR,
                        detail = openErrorText(response)
                    )
                }
                return@sendRaw
            }
            if (currentChatId == chatId) {
                // Unified open resolution (v0.9.44, N4):
                // every entry point lands in open(), and
                // this getChat response is the full server
                // chat object — record it so the header and
                // lists can flatten the chat even before it
                // appears on any chat list, then work out
                // what the bottom dock may offer.
                ChatStore.noteResolvedChat(response)
                currentChatObject = response
                _actionBar.value =
                    parseActionBar(response.optJSONObject("action_bar"))
                computeAccess(response)
            }
            if (!TdClient.isError(response) && currentChatId == chatId) {
                lastReadOutbox = response.optLong("last_read_outbox_message_id")
                lastReadInboxAtOpen =
                    response.optLong("last_read_inbox_message_id")
                val draftContent = response.optJSONObject("draft_message")
                    ?.optJSONObject("content")
                if (draftContent != null &&
                    draftContent.optString("@type") == "draftMessageContentText"
                ) {
                    val text = draftContent.optJSONObject("text")
                        ?.optString("text") ?: ""
                    _draftText.value = text
                    lastSavedDraft = text
                }
                _draftLoaded.value = true
                // Reply keyboard seed (W2): the chat object
                // names the message whose reply_markup is the
                // live keyboard; fetch just that message.
                val kbMessageId = response.optLong("reply_markup_message_id")
                if (kbMessageId != 0L) {
                    val kbQuery = TdClient.request("getMessage").apply {
                        put("chat_id", chatId)
                        put("message_id", kbMessageId)
                    }
                    TdRepository.sendRaw(kbQuery) { kbResponse ->
                        if (currentChatId == chatId &&
                            !TdClient.isError(kbResponse)
                        ) {
                            _replyKb.value = parseReplyKb(
                                kbResponse.optJSONObject("reply_markup"),
                                kbMessageId
                            )
                        }
                    }
                }
                publish()
            }
        }
        loadHistory(fromMessageId = 0L)
        loadScheduled(chatId)
        loadPinned(chatId)
    }

    /** Scheduled messages live outside the normal history in
     *  TDLib (getChatScheduledMessages, v0.9.3 M5): fetch and
     *  merge them so a timed message the user set earlier is
     *  still visible — with its schedule label — when the chat
     *  is reopened, instead of looking lost. */
    private fun loadScheduled(chatId: Long) {
        TdRepository.sendRaw(TdClient.request("getChatScheduledMessages").apply {
            put("chat_id", chatId)
        }) { response ->
            if (currentChatId != chatId || TdClient.isError(response)) {
                return@sendRaw
            }
            val arr = response.optJSONArray("messages") ?: return@sendRaw
            for (i in 0 until arr.length()) {
                val msg = arr.optJSONObject(i) ?: continue
                val item = parseMessage(msg)
                byId[item.id] = item
            }
            if (arr.length() > 0) publish()
        }
    }

    /** Plain-language reason a chat could not be opened
     *  (v0.9.44, N4), classified from the getChat error the
     *  same boundary way as send failures. */
    private fun openErrorText(error: JSONObject): String {
        val id = error.optString("message").uppercase()
        return when {
            id.contains("PRIVATE") ->
                "这是私密的群组或频道，需要邀请链接才能打开。"
            id.contains("NOT_FOUND") || id.contains("NOT FOUND") ||
                error.optInt("code") == 404 ->
                "这个对话不存在，或者已经被删除了。"
            else -> "打不开这个对话，检查网络后再试一次。" +
                "（错误码 ${error.optInt("code")}）"
        }
    }

    /** Decide the open chat's access state from its chat
     *  object (v0.9.44, N3/N4). The chat object carries the
     *  group's DEFAULT permissions; my own member status —
     *  the half that decides personal mutes — lives on the
     *  supergroup / basicGroup object, so group chats fetch
     *  it (a local-database read) before deciding. Private
     *  and secret chats need no fetch: the composer is
     *  always right there. */
    private fun computeAccess(chat: JSONObject) {
        val chatId = chat.optLong("id")
        if (chatId == 0L || chatId != currentChatId) return
        val type = chat.optJSONObject("type")
        when (type?.optString("@type")) {
            "chatTypePrivate", "chatTypeSecret" -> {
                currentPeerUserId = type.optLong("user_id")
                JoinPending.setPending(chatId, false)
                _access.value = ChatAccess(ChatAccessKind.CAN_SEND)
            }
            "chatTypeSupergroup" -> {
                val sgId = type.optLong("supergroup_id")
                currentSupergroupId = sgId
                val isChannel = type.optBoolean("is_channel")
                val query = TdClient.request("getSupergroup").apply {
                    put("supergroup_id", sgId)
                }
                TdRepository.sendRaw(query) { response ->
                    if (currentChatId != chatId) return@sendRaw
                    if (TdClient.isError(response)) {
                        // Without the status object the only
                        // honest fallback is the group-wide
                        // permission bit.
                        _access.value = accessFromPermissions(
                            chat, joined = true, isChannel = isChannel
                        )
                        return@sendRaw
                    }
                    decideGroupAccess(
                        chat,
                        status = response.optJSONObject("status"),
                        joinByRequest =
                            response.optBoolean("join_by_request"),
                        isChannel = isChannel
                    )
                }
            }
            "chatTypeBasicGroup" -> {
                val bgId = type.optLong("basic_group_id")
                currentBasicGroupId = bgId
                val query = TdClient.request("getBasicGroup").apply {
                    put("basic_group_id", bgId)
                }
                TdRepository.sendRaw(query) { response ->
                    if (currentChatId != chatId) return@sendRaw
                    if (TdClient.isError(response)) {
                        _access.value = accessFromPermissions(
                            chat, joined = true, isChannel = false
                        )
                        return@sendRaw
                    }
                    decideGroupAccess(
                        chat,
                        status = response.optJSONObject("status"),
                        joinByRequest = false,
                        isChannel = false
                    )
                }
            }
            else -> {
                _access.value = accessFromPermissions(
                    chat, joined = true, isChannel = false
                )
            }
        }
    }

    /** The member-status decision (v0.9.44, N3): personal
     *  state first (ban, restriction), membership next,
     *  group-wide defaults last — the order the official
     *  client applies, and the order that keeps a personal
     *  mute from being masked by a permissive group. */
    private fun decideGroupAccess(
        chat: JSONObject,
        status: JSONObject?,
        joinByRequest: Boolean,
        isChannel: Boolean
    ) {
        val chatId = chat.optLong("id")
        when (status?.optString("@type")) {
            "chatMemberStatusCreator",
            "chatMemberStatusAdministrator" -> {
                JoinPending.setPending(chatId, false)
                _access.value = ChatAccess(
                    ChatAccessKind.CAN_SEND, isChannel = isChannel
                )
            }
            "chatMemberStatusBanned" -> {
                JoinPending.setPending(chatId, false)
                _access.value = ChatAccess(
                    ChatAccessKind.NO_SEND_BANNED,
                    untilSec = status.optInt("banned_until_date"),
                    isChannel = isChannel
                )
            }
            "chatMemberStatusRestricted" -> {
                if (status.optBoolean("is_member")) {
                    val perms = status.optJSONObject("permissions")
                    if (perms?.optBoolean(
                            "can_send_basic_messages", true
                        ) == false
                    ) {
                        _access.value = ChatAccess(
                            ChatAccessKind.NO_SEND_PERSONAL,
                            untilSec = status.optInt(
                                "restricted_until_date"
                            ),
                            isChannel = isChannel
                        )
                    } else {
                        _access.value = accessFromPermissions(
                            chat, joined = true, isChannel = isChannel
                        )
                    }
                } else {
                    notJoinedAccess(chatId, joinByRequest, isChannel)
                }
            }
            "chatMemberStatusMember" -> {
                JoinPending.setPending(chatId, false)
                _access.value = if (isChannel) {
                    ChatAccess(
                        ChatAccessKind.NO_SEND_CHANNEL,
                        isChannel = true
                    )
                } else {
                    accessFromPermissions(
                        chat, joined = true, isChannel = false
                    )
                }
            }
            else -> notJoinedAccess(chatId, joinByRequest, isChannel)
        }
    }

    private fun notJoinedAccess(
        chatId: Long,
        joinByRequest: Boolean,
        isChannel: Boolean
    ) {
        _access.value = if (joinByRequest &&
            JoinPending.isPending(chatId)
        ) {
            ChatAccess(
                ChatAccessKind.JOIN_REQUESTED, isChannel = isChannel
            )
        } else {
            ChatAccess(
                ChatAccessKind.JOINABLE, isChannel = isChannel
            )
        }
    }

    /** The group-wide half of the decision: the chat's
     *  default permissions forbid basic messages for
     *  everyone (the admin all-mute) or they don't. */
    private fun accessFromPermissions(
        chat: JSONObject,
        joined: Boolean,
        isChannel: Boolean
    ): ChatAccess {
        if (!joined) {
            return ChatAccess(
                ChatAccessKind.JOINABLE, isChannel = isChannel
            )
        }
        val canBasic = chat.optJSONObject("permissions")
            ?.optBoolean("can_send_basic_messages", true) ?: true
        return if (canBasic) {
            ChatAccess(ChatAccessKind.CAN_SEND, isChannel = isChannel)
        } else {
            ChatAccess(
                ChatAccessKind.NO_SEND_ALL_MUTED,
                isChannel = isChannel
            )
        }
    }

    /** Recompute access after a chat / group update landed
     *  (v0.9.44, N3): permissions and member status can
     *  change while the page is open (an admin mutes the
     *  group, or un-mutes me), and the dock must follow
     *  without a re-entry. */
    private fun refreshAccess() {
        val chat = currentChatObject ?: return
        if (currentChatId == 0L) return
        computeAccess(chat)
    }

    /** Join the open group / subscribe to the open channel
     *  (v0.9.44, N4). On an approval-type group TDLib
     *  accepts the call and the status stays Left — that
     *  combination is exactly "request sent", remembered
     *  locally (see JoinPending) so the pending bar
     *  survives re-entry. */
    fun joinCurrentChat() {
        val chatId = currentChatId
        if (chatId == 0L) return
        val query = TdClient.request("joinChat").apply {
            put("chat_id", chatId)
        }
        TdRepository.sendRaw(query) { response ->
            if (currentChatId != chatId) return@sendRaw
            if (TdClient.isError(response)) {
                val id = response.optString("message").uppercase()
                _sendError.value = when {
                    id.contains("ALREADY") -> {
                        refreshAccess()
                        "你已经是成员了。"
                    }
                    id.contains("PRIVATE") ->
                        "这是私密的群组或频道，光靠加入按钮进不去，需要邀请链接。"
                    else -> "加入失败，再试一次。" +
                        "（错误码 ${response.optInt("code")}）"
                }
                return@sendRaw
            }
            // Re-read the chat and let the normal decision
            // run; if the status is still Left on an
            // approval-type group, the request is pending.
            TdRepository.sendRaw(TdClient.request("getChat").apply {
                put("chat_id", chatId)
            }) { fresh ->
                if (currentChatId != chatId ||
                    TdClient.isError(fresh)
                ) {
                    return@sendRaw
                }
                ChatStore.noteResolvedChat(fresh)
                currentChatObject = fresh
                val type = fresh.optJSONObject("type")
                val joinByRequest = type
                    ?.optString("@type") == "chatTypeSupergroup"
                if (joinByRequest) {
                    val sgId = type.optLong("supergroup_id")
                    TdRepository.sendRaw(
                        TdClient.request("getSupergroup").apply {
                            put("supergroup_id", sgId)
                        }
                    ) { sg ->
                        if (currentChatId != chatId) {
                            return@sendRaw
                        }
                        val stillLeft = TdClient.isError(sg) ||
                            sg.optJSONObject("status")
                                ?.optString("@type") ==
                                "chatMemberStatusLeft" ||
                            sg.optJSONObject("status") == null
                        if (!TdClient.isError(sg) &&
                            sg.optBoolean("join_by_request") &&
                            stillLeft
                        ) {
                            JoinPending.setPending(chatId, true)
                        }
                        computeAccess(fresh)
                    }
                } else {
                    computeAccess(fresh)
                }
            }
        }
    }

    /** Withdraw a pending join request (v0.9.44, N4):
     *  leaving the chat is how a request is cancelled;
     *  the local pending record goes with it. */
    fun cancelJoinRequest() {
        val chatId = currentChatId
        if (chatId == 0L) return
        JoinPending.setPending(chatId, false)
        val query = TdClient.request("leaveChat").apply {
            put("chat_id", chatId)
        }
        TdRepository.sendRaw(query) { response ->
            if (currentChatId != chatId) return@sendRaw
            if (TdClient.isError(response)) {
                _sendError.value = "取消申请失败，再试一次。"
            } else {
                refreshAccess()
            }
        }
    }

    fun close() {
        historyGeneration++
        _access.value = ChatAccess(ChatAccessKind.LOADING)
        _historyBlocked.value = false
        _pendingNew.value = 0
        viewingLatest = true
        currentChatObject = null
        currentSupergroupId = 0L
        currentBasicGroupId = 0L
        _actionBar.value = null
        currentPeerUserId = 0L
        val chatId = currentChatId
        if (chatId != 0L) {
            TdRepository.sendRaw(TdClient.request("closeChat").apply {
                put("chat_id", chatId)
            }) {}
        }
        currentChatId = 0L
        byId.clear()
        _replyKb.value = ReplyKb.Hidden
        _messages.value = emptyList()
        _pinnedMessages.value = emptyList()
    }

    fun loadOlder() {
        if (allHistoryLoaded || loadingHistory) return
        val oldest = byId.keys.minOrNull() ?: return
        loadHistory(fromMessageId = oldest)
    }

    /** Back to the chat's newest messages (v0.9.28, U1):
     *  replace whatever window is loaded (a search / date
     *  jump leaves only a slice around its anchor) with the
     *  first page again. The first page's landing marks the
     *  newest message read, exactly as opening the chat
     *  does, and flips [atLatest] back on. */
    fun backToLatest() {
        val chatId = currentChatId
        if (chatId == 0L) return
        historyGeneration++
        byId.clear()
        _messages.value = emptyList()
        allHistoryLoaded = false
        firstPageRetried = false
        olderRetryAnchor = 0L
        _atLatest.value = false
        _pendingNew.value = 0
        _locateId.value = 0L
        // An in-flight page from the old window would block
        // the reload through the loadingHistory guard; its
        // response is generation-stale and only resets the
        // flag, so clearing it here is safe.
        loadingHistory = false
        loadHistory(fromMessageId = 0L)
        loadScheduled(chatId)
    }

    /** One delayed retry for an older page that came back
     *  empty or with an error (v0.9.28, U2). Deep inside a
     *  huge channel TDLib can answer a page with nothing
     *  while that segment is still being fetched — reading
     *  that as end-of-history strands the user above a fake
     *  bottom. At most one retry per anchor (the caller
     *  passes isOlderRetry on the second attempt); a page
     *  that answers with the anchor itself is the genuine
     *  end and never retries. */
    private fun scheduleOlderRetry(chatId: Long, anchorId: Long, gen: Int) {
        if (olderRetryAnchor == anchorId) return
        olderRetryAnchor = anchorId
        Handler(Looper.getMainLooper()).postDelayed({
            if (currentChatId == chatId &&
                historyGeneration == gen &&
                !allHistoryLoaded && !loadingHistory &&
                byId.keys.minOrNull() == anchorId
            ) {
                loadHistory(fromMessageId = anchorId, isOlderRetry = true)
            }
        }, 1200)
    }

    private fun loadHistory(fromMessageId: Long, isOlderRetry: Boolean = false) {
        val chatId = currentChatId
        if (chatId == 0L || loadingHistory) return
        loadingHistory = true
        _historyLoading.value = true
        val gen = historyGeneration
        val query = TdClient.request("getChatHistory").apply {
            put("chat_id", chatId)
            put("from_message_id", fromMessageId)
            put("offset", 0)
            put("limit", 50)
            put("only_local", false)
        }
        TdRepository.sendRaw(query) { response ->
            loadingHistory = false
            _historyLoading.value = false
            if (currentChatId != chatId) return@sendRaw
            // Stale window (U2): a jump / reopen replaced the
            // window while this page was in flight; its
            // messages belong to the old window and must not
            // leak into the new one.
            if (gen != historyGeneration) return@sendRaw
            if (TdClient.isError(response)) {
                if (fromMessageId == 0L) {
                    // An unjoined chat whose history is
                    // members-only (v0.9.44, N4): the join
                    // bar explains that the content unlocks
                    // after joining. The chat screen shows
                    // the note only while the access state
                    // is a not-joined one, so a transient
                    // error on a normal chat never triggers
                    // it.
                    val eid = response.optString("message")
                        .uppercase()
                    if (eid.contains("PRIVATE") ||
                        eid.contains("FORBIDDEN")
                    ) {
                        _historyBlocked.value = true
                    }
                }
                if (fromMessageId != 0L && !isOlderRetry) {
                    scheduleOlderRetry(chatId, fromMessageId, gen)
                }
                return@sendRaw
            }
            if (fromMessageId == 0L) _historyBlocked.value = false
            val arr = response.optJSONArray("messages") ?: JSONArray()
            // D1 root-cause fix: td_api.tl states the returned
            // count "is chosen by TDLib and can be smaller than
            // the specified limit", so a short page must NOT be
            // read as end-of-history (the old `length < 50` check
            // froze paging whenever TDLib felt like returning a
            // short page — the reported "sometimes there,
            // sometimes not" missing history). History is over
            // only when a page adds nothing older than what we
            // already have (for an older-page request), or when
            // the first page is empty even after one retry.
            val previousOldest = byId.keys.minOrNull()
            var addedOlder = false
            var newestId = 0L
            for (i in 0 until arr.length()) {
                val msg = arr.optJSONObject(i) ?: continue
                val item = parseMessage(msg)
                byId[item.id] = item
                if (previousOldest == null || item.id < previousOldest) {
                    addedOlder = true
                }
                if (item.id > newestId) newestId = item.id
            }
            if (fromMessageId != 0L) {
                when {
                    addedOlder -> olderRetryAnchor = 0L
                    // A completely empty answer is suspect
                    // (U2): retry once before believing it.
                    arr.length() == 0 && !isOlderRetry ->
                        scheduleOlderRetry(chatId, fromMessageId, gen)
                    else -> allHistoryLoaded = true
                }
            } else if (arr.length() == 0) {
                if (!firstPageRetried) {
                    firstPageRetried = true
                    // The td layer is callback-based (no coroutine
                    // scope anywhere); a main-looper delay is the
                    // local idiom for "try again shortly".
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (currentChatId == chatId &&
                            historyGeneration == gen && byId.isEmpty()
                        ) {
                            loadHistory(fromMessageId = 0L)
                        }
                    }, 1500)
                } else {
                    allHistoryLoaded = true
                }
            }
            if (fromMessageId == 0L) _atLatest.value = true
            publish()
            if (fromMessageId == 0L && newestId != 0L) {
                markRead(chatId, newestId)
            }
        }
    }

    fun setReplyTarget(item: MsgItem?, quote: Boolean = false) {
        _replyTarget.value = item
        _replyQuote.value = quote && item != null &&
            item.text.isNotEmpty()
        if (_replyQuote.value) fetchQuoteLimit()
    }

    /** Drop the reply target and its quote mode together —
     *  every send path clears both so a quote can never leak
     *  into a later plain reply. */
    private fun clearReplyTarget() {
        _replyTarget.value = null
        _replyQuote.value = false
    }

    private fun fetchQuoteLimit() {
        if (quoteLimitFetched) return
        quoteLimitFetched = true
        val query = TdClient.request("getOption").apply {
            put("name", "message_reply_quote_length_max")
        }
        TdRepository.sendRaw(query) { response ->
            val value = response.optLong("value")
            if (!TdClient.isError(response) && value > 0) {
                quoteLimit = value.toInt()
            }
        }
    }

    /** The exact text a quote of [item] will carry: the
     *  message's own text, truncated to the server cap in
     *  whole characters (code points, so a surrogate pair is
     *  never split). The input preview bar shows this same
     *  string, so what the user sees is what gets sent. */
    fun quoteTextOf(item: MsgItem): String {
        val full = item.text
        if (full.isEmpty()) return ""
        val limit = quoteLimit
        if (full.codePointCount(0, full.length) <= limit) return full
        return full.substring(0, full.offsetByCodePoints(0, limit))
    }

    /** textEntity JSON for one flattened entity (the inverse
     *  of [entitiesOf]); null for kinds that must not be
     *  re-sent (plain/url/quote blocks and the like). Shared
     *  by the quote builder and the markdown send parser. */
    fun entityJson(e: TextEntityUi): JSONObject? {
        val type = when (e.kind) {
            "bold" -> JSONObject().put("@type", "textEntityTypeBold")
            "italic" -> JSONObject().put("@type", "textEntityTypeItalic")
            "underline" ->
                JSONObject().put("@type", "textEntityTypeUnderline")
            "strike" ->
                JSONObject().put("@type", "textEntityTypeStrikethrough")
            "code" -> JSONObject().put("@type", "textEntityTypeCode")
            "pre" -> JSONObject().put("@type", "textEntityTypePre")
            "spoiler" ->
                JSONObject().put("@type", "textEntityTypeSpoiler")
            "textUrl" -> JSONObject().apply {
                put("@type", "textEntityTypeTextUrl")
                put("url", e.url)
            }
            "mention" ->
                JSONObject().put("@type", "textEntityTypeMention")
            "mentionName" -> JSONObject().apply {
                put("@type", "textEntityTypeMentionName")
                put("user_id", e.userId)
            }
            "hashtag" ->
                JSONObject().put("@type", "textEntityTypeHashtag")
            "botCommand" ->
                JSONObject().put("@type", "textEntityTypeBotCommand")
            "customEmoji" -> JSONObject().apply {
                put("@type", "textEntityTypeCustomEmoji")
                put("custom_emoji_id", e.customEmojiId)
            }
            else -> null
        } ?: return null
        return JSONObject().apply {
            put("@type", "textEntity")
            put("offset", e.offset)
            put("length", e.length)
            put("type", type)
        }
    }

    /** inputTextQuote for [target] (v0.9.15, V2), verified
     *  in td_api.tl: the text is capped at
     *  getOption("message_reply_quote_length_max") characters
     *  and only Bold / Italic / Underline / Strikethrough /
     *  Spoiler / CustomEmoji / DateTime entities may be kept
     *  — the target's own entities of those kinds, clipped to
     *  the quoted range, ride along; position is 0 because
     *  the quote is the message text's prefix. */
    private fun quoteJson(target: MsgItem): JSONObject? {
        val quoteText = quoteTextOf(target)
        if (quoteText.isEmpty()) return null
        val entities = JSONArray()
        for (e in target.entities) {
            if (e.kind !in QUOTE_ENTITY_KINDS) continue
            if (e.offset < 0 ||
                e.offset + e.length > quoteText.length
            ) {
                continue
            }
            entityJson(e)?.let { entities.put(it) }
        }
        return JSONObject().apply {
            put("@type", "inputTextQuote")
            put("text", JSONObject().apply {
                put("@type", "formattedText")
                put("text", quoteText)
                put("entities", entities)
            })
            put("rich_texts", JSONArray())
            put("position", 0)
        }
    }

    /** Persist the input box as the chat's draft
     *  (setChatDraftMessage, verified in td_api.tl). TDLib
     *  stores drafts server-side and syncs them to the user's
     *  other devices on its own; an empty text deletes the
     *  draft (draft_message = null). No-ops when nothing changed
     *  since the last save, so the UI can call it on a debounce
     *  without spamming. */
    fun saveDraft(text: String) {
        val chatId = currentChatId
        if (chatId == 0L) return
        if (text == lastSavedDraft) return
        lastSavedDraft = text
        val draft: Any = if (text.isEmpty()) {
            JSONObject.NULL
        } else {
            JSONObject().apply {
                put("@type", "draftMessage")
                val target = _replyTarget.value
                if (target != null) {
                    put("reply_to", JSONObject().apply {
                        put("@type", "inputMessageReplyToMessage")
                        put("message_id", target.id)
                        put("quote", JSONObject.NULL)
                        put("checklist_task_id", 0)
                        put("poll_option_id", "")
                    })
                } else {
                    put("reply_to", JSONObject.NULL)
                }
                put("date", 0)
                put("content", JSONObject().apply {
                    put("@type", "draftMessageContentText")
                    put("text", formattedText(text))
                    put("link_preview_options", JSONObject.NULL)
                })
                put("effect_id", 0)
                put("suggested_post_info", JSONObject.NULL)
            }
        }
        val query = TdClient.request("setChatDraftMessage").apply {
            put("chat_id", chatId)
            put("topic_id", JSONObject.NULL)
            put("draft_message", draft)
        }
        TdRepository.sendRaw(query) {}
    }

    /** Tell the other side I am typing (sendChatAction with
     *  chatActionTyping, verified in td_api.tl), throttled to
     *  once per 5 seconds — the action itself expires on the
     *  receiving side after a few seconds. */
    fun sendTypingAction() {
        val chatId = currentChatId
        if (chatId == 0L) return
        val now = System.currentTimeMillis()
        if (now - lastTypingSentAt < 5000) return
        lastTypingSentAt = now
        val query = TdClient.request("sendChatAction").apply {
            put("chat_id", chatId)
            put("topic_id", JSONObject.NULL)
            put("business_connection_id", "")
            put("action", JSONObject().put("@type", "chatActionTyping"))
        }
        TdRepository.sendRaw(query) {}
    }

    /** Safety net for the typing subtitle: senders normally end
     *  with chatActionCancel, but a lost update must not leave
     *  "正在输入…" on screen forever. Called by the UI a few
     *  seconds after the last shown action. */
    fun expirePeerActions() {
        if (peerActions.isEmpty()) return
        if (System.currentTimeMillis() - lastActionAt > 6000) {
            peerActions.clear()
            _peerAction.value = ""
        }
    }

    /** Fold the live per-sender actions into the subtitle
     *  line (v0.9.28, U5). Private chats and a single actor
     *  keep the long-standing format ("正在输入…", or
     *  "名字 正在输入…" in a group). Several group members
     *  doing the SAME thing read "张三、李四 正在输入…" for
     *  two, "张三、李四 等 3 人正在输入…" beyond that; mixed
     *  actions show the most recent one. Senders whose name
     *  has not landed yet are skipped in the name list —
     *  fetchUser's completion re-renders when it arrives. */
    private fun renderPeerAction(chatId: Long) {
        val entries = peerActions.entries
            .sortedByDescending { it.value.at }
        if (entries.isEmpty()) {
            _peerAction.value = ""
            return
        }
        val isGroup = ChatStore.itemOf(chatId)?.isGroup == true
        val first = entries.first().value
        if (!isGroup || entries.size == 1) {
            _peerAction.value =
                if (isGroup && first.name.isNotEmpty()) {
                    "${first.name} ${first.label}"
                } else {
                    first.label
                }
            return
        }
        val sameLabel = entries.filter { it.value.label == first.label }
        val names = sameLabel.map { it.value.name }
            .filter { it.isNotEmpty() }
        _peerAction.value = when {
            sameLabel.size >= 2 && names.size >= 2 -> {
                val head = names.take(2).joinToString("、")
                if (sameLabel.size > 2) {
                    "$head 等 ${sameLabel.size} 人${first.label}"
                } else {
                    "$head ${first.label}"
                }
            }
            first.name.isNotEmpty() -> "${first.name} ${first.label}"
            else -> first.label
        }
    }

    /** sendMessage.reply_to for the current reply target, if any.
     *  Signature verified in td_api.tl: inputMessageReplyToMessage
     *  carries message_id plus optional quote fields. */
    private fun replyToJson(): JSONObject? {
        val target = _replyTarget.value ?: return null
        val quote = if (_replyQuote.value) {
            quoteJson(target)
        } else {
            null
        }
        return JSONObject().apply {
            put("@type", "inputMessageReplyToMessage")
            put("message_id", target.id)
            put("quote", quote ?: JSONObject.NULL)
            put("checklist_task_id", 0)
            put("poll_option_id", "")
        }
    }

    /** messageSendOptions for a non-default send (v0.9.3,
     *  M5): disable_notification for a silent send and/or a
     *  scheduling_state (messageSchedulingStateSendAtDate /
     *  SendWhenOnline — field shapes verified in td_api.tl).
     *  Null for a plain send, where the field stays absent
     *  exactly as before. */
    private fun sendOptionsJson(mods: SendModifiers): JSONObject? {
        if (mods.isDefault) return null
        return JSONObject().apply {
            put("@type", "messageSendOptions")
            put("disable_notification", mods.silent)
            put("from_background", false)
            put("protect_content", false)
            if (mods.effectId != 0L) put("effect_id", mods.effectId)
            when {
                mods.whenOnline -> put(
                    "scheduling_state",
                    JSONObject().put(
                        "@type", "messageSchedulingStateSendWhenOnline"
                    )
                )
                mods.scheduleAtSec > 0 -> put(
                    "scheduling_state",
                    JSONObject().apply {
                        put("@type", "messageSchedulingStateSendAtDate")
                        put("send_date", mods.scheduleAtSec.toInt())
                        put("repeat_period", 0)
                    }
                )
            }
        }
    }

    /** What the composer link-preview card shows (v0.9.24,
     *  Q3): the getLinkPreview answer for the first URL in the
     *  draft. [url] is the URL the preview belongs to — the
     *  card (and a "preview off" choice) only applies while
     *  the draft still contains exactly that URL. */
    data class ComposerPreview(
        val url: String,
        val siteName: String,
        val title: String,
        val description: String,
        val photoFileId: Int = 0,
        val photoPath: String? = null
    )

    private val _composerPreview =
        MutableStateFlow<ComposerPreview?>(null)
    val composerPreview: StateFlow<ComposerPreview?> =
        _composerPreview

    @Volatile
    private var composerPreviewUrl: String = ""

    /** Fetch (or clear) the composer link preview for the
     *  first URL in [text]. Called debounced from the chat
     *  screen; a response that arrives after the draft moved
     *  on to another URL is dropped. */
    fun refreshComposerPreview(text: String) {
        val url = firstUrlIn(text)
        if (url == null) {
            composerPreviewUrl = ""
            _composerPreview.value = null
            return
        }
        if (url == composerPreviewUrl &&
            _composerPreview.value != null
        ) {
            return
        }
        composerPreviewUrl = url
        _composerPreview.value = null
        val query = TdClient.request("getLinkPreview").apply {
            put("url", url)
            put("link_preview_options", JSONObject.NULL)
        }
        TdRepository.sendRaw(query) { response ->
            if (composerPreviewUrl != url) return@sendRaw
            if (TdClient.isError(response)) {
                // No preview for this link (or the fetch was
                // refused): the card simply stays away.
                if (composerPreviewUrl == url) {
                    composerPreviewUrl = ""
                }
                return@sendRaw
            }
            val type = response.optString("@type")
            if (type == "linkPreview") {
                val photo = lpPhotoFile(response)
                    ?.optJSONObject("photo")
                val photoId = photo?.optInt("id", 0) ?: 0
                if (photoId != 0 &&
                    downloadedPath(photo) == null
                ) {
                    requestDownload(photoId, priority = 1)
                }
                _composerPreview.value = ComposerPreview(
                    url = url,
                    siteName = response.optString("site_name"),
                    title = response.optString("title"),
                    description = response.optJSONObject("description")
                        ?.optString("text") ?: "",
                    photoFileId = photoId,
                    photoPath = downloadedPath(photo)
                )
            } else if (type == "webPageInstantView") {
                // Instant-view pages carry their title in the
                // first page block; showing just the link is
                // honest enough for the composer card.
                _composerPreview.value = ComposerPreview(
                    url = url,
                    siteName = "",
                    title = url,
                    description = ""
                )
            }
        }
    }

    fun clearComposerPreview() {
        composerPreviewUrl = ""
        _composerPreview.value = null
    }

    private fun firstUrlIn(text: String): String? {
        val match = Regex("""https?://\S+|www\.\S+|t\.me/\S+""")
            .find(text) ?: return null
        var url = match.value.trimEnd('.', ',', ')', ']', '}', '!', '?')
        if (url.startsWith("www.")) url = "https://$url"
        if (url.startsWith("t.me/")) url = "https://$url"
        return url
    }

    fun sendText(text: String) = sendText(text, SendModifiers())

    /** Re-send a message whose send failed (v0.9.9, W5;
     *  gating reworked in v0.9.44, N1/N2). td_api.tl allows
     *  resendMessages only when the failed state's can_retry
     *  is true and its retry_after has passed — before this
     *  version the call went out unconditionally, so a
     *  failure that can never be resent (no posting rights,
     *  a paid send) re-failed on every tap and the user got
     *  an endless 「重发失败，再试一次」 loop. Now the typed
     *  classification decides first: non-retryable failures
     *  never reach the API and the user hears the real
     *  reason instead; a resend the API still refuses is
     *  classified the same way, not flattened to a generic
     *  retry prompt. */
    fun resend(item: MsgItem) {
        val chatId = currentChatId
        if (chatId == 0L || item.id == 0L) return
        val kind = SendFailure.classify(
            item.failCode, item.failId, item.paidStarsRequired
        )
        if (!SendFailure.retryable(kind, item.canRetry)) {
            _sendError.value = SendFailure.fullText(
                kind, item.retryAfterSec,
                item.failCode, item.failId
            )
            return
        }
        val query = TdClient.request("resendMessages").apply {
            put("chat_id", chatId)
            put("message_ids", org.json.JSONArray().put(item.id))
            put("quote", JSONObject.NULL)
            put("paid_message_star_count", 0)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = SendFailure.fullTextOf(response)
            }
        }
    }

    fun sendText(text: String, mods: SendModifiers) =
        sendText(text, mods, emptyList())

    /** [extraEntities] are entities against the raw [text]
     *  (V6 mention names); the markdown parser remaps them
     *  into the sent text's coordinates. */
    fun sendText(
        text: String,
        mods: SendModifiers,
        extraEntities: List<TextEntityUi>
    ) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val replyTo = replyToJson()
        clearReplyTarget()
        val options = sendOptionsJson(mods)
        val query = TdClient.request("sendMessage").apply {
            put("chat_id", chatId)
            if (replyTo != null) put("reply_to", replyTo)
            if (options != null) put("options", options)
            put("input_message_content", JSONObject().apply {
                put("@type", "inputMessageText")
                put("text", richFormattedText(text, extraEntities))
                // Per-send preview switch (v0.9.24, Q3): the
                // composer's card close button arms
                // disableLinkPreview for this draft only.
                if (mods.disableLinkPreview) {
                    put("link_preview_options", JSONObject().apply {
                        put("@type", "linkPreviewOptions")
                        put("is_disabled", true)
                        put("url", "")
                        put("force_small_media", false)
                        put("force_large_media", false)
                        put("show_above_text", false)
                    })
                } else {
                    put("link_preview_options", JSONObject.NULL)
                }
                put("clear_draft", true)
            })
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = sendErrorText(response)
            } else {
                val item = parseMessage(response)
                byId[item.id] = item
                publish()
            }
        }
    }

    /** inputMessagePhoto for a local file (v0.9.1: shared with
     *  the thread / forum stores so every send surface builds
     *  the exact same content object). */
    fun photoContent(path: String, caption: String): JSONObject =
        JSONObject().apply {
            put("@type", "inputMessagePhoto")
            put("photo", JSONObject().apply {
                put("@type", "inputPhoto")
                put("photo", JSONObject().apply {
                    put("@type", "inputFileLocal")
                    put("path", path)
                })
                put("thumbnail", JSONObject.NULL)
                put("video", JSONObject.NULL)
                put("added_sticker_file_ids", JSONArray())
                put("width", 0)
                put("height", 0)
            })
            put("caption", formattedText(caption))
            put("show_caption_above_media", false)
            put("self_destruct_type", JSONObject.NULL)
            put("has_spoiler", false)
        }

    fun sendPhoto(path: String, caption: String) =
        sendPhoto(path, caption, SendModifiers())

    fun sendPhoto(path: String, caption: String, mods: SendModifiers) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val replyTo = replyToJson()
        clearReplyTarget()
        val options = sendOptionsJson(mods)
        val query = TdClient.request("sendMessage").apply {
            put("chat_id", chatId)
            if (replyTo != null) put("reply_to", replyTo)
            if (options != null) put("options", options)
            put("input_message_content", photoContent(path, caption))
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = sendErrorText(response)
            } else {
                val item = parseMessage(response)
                byId[item.id] = item
                publish()
            }
        }
    }

    /** Send 2+ picked photos/videos as one album
     *  (v0.9.15, V4): sendMessageAlbum is verified in
     *  td_api.tl (chat_id / topic_id / reply_to / options /
     *  input_message_contents -> messages). The caption rides
     *  on the FIRST item — that is where Telegram surfaces an
     *  album's caption. Photos reuse photoContent, videos the
     *  fileContent video branch; the reply target is honored
     *  and consumed exactly like a single send. The answer's
     *  messages are reconciled into byId like any other send;
     *  updateMessageSendSucceeded finishes them in place. */
    fun sendAlbum(parts: List<AlbumPart>, caption: String) {
        val chatId = currentChatId
        if (chatId == 0L || parts.size < 2) return
        val replyTo = replyToJson()
        clearReplyTarget()
        val contents = JSONArray()
        parts.forEachIndexed { index, part ->
            val first = index == 0
            val content = if (part.mimeType.startsWith("image/")) {
                photoContent(part.path, if (first) caption else "")
            } else {
                val built = fileContent(
                    part.path, part.mimeType, part.fileName
                )
                if (first && caption.isNotEmpty()) {
                    built.put("caption", formattedText(caption))
                }
                built
            }
            contents.put(content)
        }
        val query = TdClient.request("sendMessageAlbum").apply {
            put("chat_id", chatId)
            put("topic_id", JSONObject.NULL)
            if (replyTo != null) put("reply_to", replyTo)
            put("options", JSONObject.NULL)
            put("input_message_contents", contents)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = sendErrorText(response)
            } else {
                val arr = response.optJSONArray("messages")
                    ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val msg = arr.optJSONObject(i) ?: continue
                    val item = parseMessage(msg)
                    byId[item.id] = item
                }
                if (arr.length() > 0) publish()
            }
        }
    }

    /** The shared sendMessage boilerplate for content types
     *  that need no send modifiers (v0.9.4: polls, locations,
     *  contacts): reply target honored, result reconciled into
     *  byId exactly like text/photo sends. */
    private fun sendContent(content: JSONObject) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val replyTo = replyToJson()
        clearReplyTarget()
        val query = TdClient.request("sendMessage").apply {
            put("chat_id", chatId)
            if (replyTo != null) put("reply_to", replyTo)
            put("input_message_content", content)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = sendErrorText(response)
            } else {
                val item = parseMessage(response)
                byId[item.id] = item
                publish()
            }
        }
    }

    /** Create a poll in the open chat (v0.9.4, N1).
     *  inputMessagePoll / inputPollOption / inputPollTypeRegular
     *  / inputPollTypeQuiz all verified in td_api.tl. Quiz mode
     *  carries exactly one correct option plus an optional
     *  explanation; the server refuses multiple answers on
     *  quizzes, so the UI never offers both at once. The created
     *  poll renders through the normal POLL path and can be
     *  voted on / retracted via setPollAnswer like any other. */
    fun sendPoll(
        question: String,
        options: List<String>,
        isAnonymous: Boolean,
        allowsMultiple: Boolean,
        quizCorrectIndex: Int,
        quizExplanation: String
    ) {
        val optionArray = JSONArray()
        for (text in options) {
            optionArray.put(JSONObject().apply {
                put("@type", "inputPollOption")
                put("text", formattedText(text))
                put("media", JSONObject.NULL)
            })
        }
        val type = if (quizCorrectIndex >= 0) {
            JSONObject().apply {
                put("@type", "inputPollTypeQuiz")
                put("correct_option_ids", JSONArray().put(quizCorrectIndex))
                put("explanation", formattedText(quizExplanation))
                put("explanation_media", JSONObject.NULL)
            }
        } else {
            JSONObject().apply {
                put("@type", "inputPollTypeRegular")
                put("allow_adding_options", false)
            }
        }
        val content = JSONObject().apply {
            put("@type", "inputMessagePoll")
            put("question", formattedText(question))
            put("options", optionArray)
            put("description", JSONObject.NULL)
            put("media", JSONObject.NULL)
            put("is_anonymous", isAnonymous)
            put("allows_multiple_answers", allowsMultiple)
            put("allows_revoting", true)
            put("members_only", false)
            put("country_codes", JSONArray())
            put("shuffle_options", false)
            put("hide_results_until_closes", false)
            put("type", type)
            put("open_period", 0)
            put("close_date", 0)
            put("is_closed", false)
        }
        sendContent(content)
    }

    /** Share a one-shot location in the open chat (v0.9.4,
     *  N2; inputMessageLocation verified in td_api.tl). The
     *  coordinates come from the system LocationManager in the
     *  UI layer — no Play services involved. */
    fun sendLocation(latitude: Double, longitude: Double) {
        sendContent(JSONObject().apply {
            put("@type", "inputMessageLocation")
            put("location", JSONObject().apply {
                put("@type", "location")
                put("latitude", latitude)
                put("longitude", longitude)
                put("horizontal_accuracy", 0.0)
            })
        })
    }

    /** Share a LIVE location in the open chat (v0.9.25, R2;
     *  inputMessageLiveLocation + liveLocation verified in
     *  td_api.tl). [livePeriodSec] is how long the point can
     *  be updated for, counted from the send time. */
    fun sendLiveLocation(
        latitude: Double, longitude: Double, livePeriodSec: Int
    ) {
        sendContent(JSONObject().apply {
            put("@type", "inputMessageLiveLocation")
            put("location", liveLocationJson(
                latitude, longitude, livePeriodSec
            ))
        })
    }

    private fun liveLocationJson(
        latitude: Double, longitude: Double, livePeriodSec: Int
    ): JSONObject = JSONObject().apply {
        put("@type", "liveLocation")
        put("location", JSONObject().apply {
            put("@type", "location")
            put("latitude", latitude)
            put("longitude", longitude)
            put("horizontal_accuracy", 0.0)
        })
        put("live_period", livePeriodSec)
        put("heading", 0)
        put("proximity_alert_radius", 0)
    }

    /** Push a fresh point into one of my live locations
     *  (editMessageLiveLocation with a non-null location).
     *  The remaining period is recomputed from the message's
     *  own expiry so an edit never extends sharing by more
     *  than the API allows. */
    fun updateLiveLocation(msg: MsgItem, latitude: Double, longitude: Double) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val query = TdClient.request("editMessageLiveLocation").apply {
            put("chat_id", chatId)
            put("message_id", msg.id)
            put("reply_markup", JSONObject.NULL)
            put("location", liveLocationJson(
                latitude, longitude,
                msg.liveExpiresIn.coerceAtLeast(60)
            ))
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value =
                    "实时位置更新失败。（错误码 ${response.optInt("code")}）"
            }
        }
    }

    /** Stop sharing one of my live locations
     *  (editMessageLiveLocation with location = null, the
     *  documented stop in td_api.tl). The bubble turns into a
     *  static location when TDLib pushes the content update. */
    fun stopLiveLocation(messageId: Long) {
        val chatId = currentChatId
        if (chatId == 0L) return
        // Background sharing (v0.9.30, W2) stops tracking
        // this share at once; the content update would
        // remove it anyway, this just skips the wait.
        LiveShareRegistry.remove(chatId, messageId)
        val query = TdClient.request("editMessageLiveLocation").apply {
            put("chat_id", chatId)
            put("message_id", messageId)
            put("reply_markup", JSONObject.NULL)
            put("location", JSONObject.NULL)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value =
                    "停止共享没成功，再试一次。（错误码 ${response.optInt("code")}）"
            }
        }
    }

    /** Share a contact card in the open chat (v0.9.4, N2;
     *  inputMessageContact verified in td_api.tl). user_id
     *  stays 0 for contacts picked from the system address
     *  book — TDLib links the card to a Telegram account by
     *  phone number on its side. */
    fun sendContact(phoneNumber: String, firstName: String, lastName: String) {
        sendContent(JSONObject().apply {
            put("@type", "inputMessageContact")
            put("contact", JSONObject().apply {
                put("@type", "contact")
                put("phone_number", phoneNumber)
                put("first_name", firstName)
                put("last_name", lastName)
                put("vcard", "")
                put("user_id", 0L)
            })
        })
    }

    /** Sender user id of a message in the open chat's
     *  window (0 when unknown): how a reply keyboard finds
     *  the bot that sent it, for Web App keyboard buttons
     *  in group chats (v0.9.36, C1). */
    fun senderOfMessage(messageId: Long): Long =
        byId[messageId]?.senderUserId ?: 0L

    fun clearSendError() {
        _sendError.value = null
    }

    /** Edit one of my own text messages (editMessageText, verified
     *  in td_api.tl). The bubble updates when TDLib pushes
     *  updateMessageContent back. */
    fun editText(messageId: Long, newText: String) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val query = TdClient.request("editMessageText").apply {
            put("chat_id", chatId)
            put("message_id", messageId)
            put("reply_markup", JSONObject.NULL)
            put("input_message_content", JSONObject().apply {
                put("@type", "inputMessageText")
                put("text", formattedText(newText))
                put("link_preview_options", JSONObject.NULL)
                put("clear_draft", false)
            })
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = sendErrorText(response)
            }
        }
    }

    /** Delete a message. Own messages are revoked for everyone;
     *  other people's messages can only be removed locally
     *  (revoke = false). The bubble disappears when TDLib pushes
     *  updateDeleteMessages. */
    fun deleteMessage(item: MsgItem) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val query = TdClient.request("deleteMessages").apply {
            put("chat_id", chatId)
            put("message_ids", JSONArray().put(item.id))
            put("revoke", item.outgoing)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = sendErrorText(response)
            }
        }
    }

    /** Forward one message to another chat (forwardMessages,
     *  verified in td_api.tl). Independent of which chat is open,
     *  so the chat-list picker can call it after this store closed. */
    fun forwardMessage(
        fromChatId: Long,
        messageId: Long,
        toChatId: Long,
        onResult: (String?) -> Unit
    ) = forwardMessages(
        fromChatId, listOf(messageId), toChatId, onResult = onResult
    )

    /** Forward several messages in one call — forwardMessages
     *  takes a whole message_ids vector (verified in td_api.tl);
     *  the multi-select bar uses this (v0.7.0, F1). Ids are sent
     *  oldest-first so the target chat reads in order.
     *
     *  [sendCopy] / [removeCaption] (v0.9.31, X4) ride the
     *  request's own top-level flags (td_api.tl forwardMessages:
     *  send_copy / remove_caption): send_copy sends the content
     *  as a copy with no reference to the original sender;
     *  remove_caption strips media captions of the copies and
     *  is ignored by the server unless send_copy is set — the
     *  picker disables its switch in that case rather than
     *  pretending it works alone. */
    fun forwardMessages(
        fromChatId: Long,
        messageIds: List<Long>,
        toChatId: Long,
        sendCopy: Boolean = false,
        removeCaption: Boolean = false,
        onResult: (String?) -> Unit
    ) {
        if (messageIds.isEmpty()) {
            onResult(null)
            return
        }
        val ids = JSONArray()
        for (id in messageIds.sorted()) ids.put(id)
        val query = TdClient.request("forwardMessages").apply {
            put("chat_id", toChatId)
            put("topic_id", JSONObject.NULL)
            put("from_chat_id", fromChatId)
            put("message_ids", ids)
            put("options", JSONObject.NULL)
            put("send_copy", sendCopy)
            put("remove_caption", removeCaption && sendCopy)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                onResult(sendErrorText(response))
            } else {
                onResult(null)
            }
        }
    }

    /** Delete a multi-select batch (v0.7.0, F1). With
     *  [revokeAll] (admin with the delete right) everything is
     *  revoked for everyone in one call; otherwise own messages
     *  are revoked and other people's are removed locally only —
     *  the same split as the single-message delete. Bubbles
     *  disappear via updateDeleteMessages. */
    fun deleteSelected(items: List<MsgItem>, revokeAll: Boolean) {
        val chatId = currentChatId
        if (chatId == 0L || items.isEmpty()) return
        fun send(ids: List<Long>, revoke: Boolean) {
            if (ids.isEmpty()) return
            val arr = JSONArray()
            for (id in ids) arr.put(id)
            val query = TdClient.request("deleteMessages").apply {
                put("chat_id", chatId)
                put("message_ids", arr)
                put("revoke", revoke)
            }
            TdRepository.sendRaw(query) { response ->
                if (TdClient.isError(response)) {
                    _sendError.value = sendErrorText(response)
                }
            }
        }
        if (revokeAll) {
            send(items.map { it.id }, revoke = true)
        } else {
            send(items.filter { it.outgoing }.map { it.id }, revoke = true)
            send(items.filter { !it.outgoing }.map { it.id }, revoke = false)
        }
    }

    /** Plain-language send refusal (v0.9.44, N2): delegates
     *  to the single boundary classifier in SendFailure —
     *  the typed kind drives the wording, so a reworded or
     *  newly-worded server string can never silently change
     *  behaviour. This replaces the ad-hoc substring table
     *  that lived here (including its matches on English
     *  prose fragments like "have no rights"). */
    private fun sendErrorText(error: JSONObject): String =
        SendFailure.fullTextOf(error)

    private fun formattedText(text: String): JSONObject = JSONObject().apply {
        put("@type", "formattedText")
        put("text", text)
        put("entities", JSONArray())
    }

    /** formattedText with the lightweight send markdown
     *  applied (v0.9.15, V3; parser in Markdown.kt). [extra]
     *  carries entities computed against the RAW text (the
     *  V6 mention-name insertions) — they are remapped into
     *  clean-text coordinates through the parser's boundary
     *  map and dropped if their range did not survive. A
     *  parser failure of any kind falls back to the plain
     *  text, so a send can never lose characters to the
     *  formatter. Public because the thread / forum stores
     *  send through the same formatting as the main chat. */
    fun richFormattedText(
        raw: String,
        extra: List<TextEntityUi> = emptyList()
    ): JSONObject {
        val parsed = try {
            MarkdownSend.parse(raw)
        } catch (t: Throwable) {
            null
        } ?: return formattedText(raw)
        val entities = ArrayList<TextEntityUi>(parsed.entities)
        for (e in extra) {
            if (e.offset < 0 || e.offset + e.length > raw.length) {
                continue
            }
            val start = parsed.rawToClean[e.offset]
            val end = parsed.rawToClean[e.offset + e.length]
            if (end <= start) continue
            entities.add(
                e.copy(offset = start, length = end - start)
            )
        }
        if (entities.isEmpty() && parsed.text == raw) {
            return formattedText(raw)
        }
        val arr = JSONArray()
        for (e in entities) {
            entityJson(e)?.let { arr.put(it) }
        }
        return JSONObject().apply {
            put("@type", "formattedText")
            put("text", parsed.text)
            put("entities", arr)
        }
    }

    private fun markRead(chatId: Long, messageId: Long) {
        val query = TdClient.request("viewMessages").apply {
            put("chat_id", chatId)
            put("message_ids", JSONArray().put(messageId))
            put("source", JSONObject().put("@type", "messageSourceChatHistory"))
            put("force_read", true)
        }
        TdRepository.sendRaw(query) {}
    }

    private fun handleUpdate(update: JSONObject) {
        when (update.optString("@type")) {
            "updateChatActionBar" -> {
                // The server raises or clears the stranger
                // strip live (v0.9.54, Y1) — e.g. it drops
                // the moment the other side becomes a
                // contact, and removeChatActionBar answers
                // with the cleared value.
                if (update.optLong("chat_id") != currentChatId) return
                _actionBar.value =
                    parseActionBar(update.optJSONObject("action_bar"))
            }
            "updateNewMessage" -> {
                val msg = update.optJSONObject("message") ?: return
                if (msg.optLong("chat_id") != currentChatId) return
                val item = parseMessage(msg)
                byId[item.id] = item
                publish()
                if (!item.outgoing) {
                    // Read marking follows the user's eyes
                    // (v0.9.44, N5): only a user sitting at
                    // the newest end has "seen" a new
                    // arrival. Scrolled up, the message
                    // stays unread and counts toward the
                    // scroll-to-latest badge instead — the
                    // old unconditional force-read made that
                    // counter structurally always zero.
                    if (_atLatest.value && viewingLatest) {
                        markRead(currentChatId, item.id)
                    } else if (_atLatest.value) {
                        _pendingNew.value += 1
                    }
                    // Arrival tone (v0.9.29, V1): only for a
                    // genuinely new message in the chat that is
                    // open — not for muted chats, and not for
                    // old messages TDLib replays while syncing
                    // (the 120 s freshness window matches the
                    // entry animation's). SoundFx throttles
                    // bursts and checks switch + foreground.
                    val nowSec =
                        (System.currentTimeMillis() / 1000).toInt()
                    if (item.dateSeconds >= nowSec - 120 &&
                        ChatStore.itemOf(currentChatId)?.isMuted != true
                    ) {
                        SoundFx.arrival()
                    }
                    // Whole-chat auto-translate (v0.9.62,
                    // G1): a no-op unless this chat's switch
                    // is on; the store queues and throttles.
                    AutoTranslate.onIncoming(currentChatId, item)
                }
            }
            "updateMessageSendSucceeded" -> {
                val msg = update.optJSONObject("message") ?: return
                if (msg.optLong("chat_id") != currentChatId) return
                val oldId = update.optLong("old_message_id")
                val item = parseMessage(msg)
                byId.remove(oldId)
                byId[item.id] = item
                publish()
            }
            "updateMessageSendFailed" -> {
                val msg = update.optJSONObject("message") ?: return
                if (msg.optLong("chat_id") != currentChatId) return
                val oldId = update.optLong("old_message_id")
                val existing = byId[oldId] ?: byId[msg.optLong("id")] ?: return
                // Carry the failure's own payload (v0.9.44,
                // N1/N2): the update's message object holds
                // the full messageSendingStateFailed — error,
                // can_retry, retry_after. Before this the
                // handler kept only the word "failed", so the
                // UI could neither explain the failure nor
                // know a resend was impossible.
                byId[existing.id] = withFailedState(existing, msg)
                publish()
            }
            "updateMessageContent" -> {
                val chatId = update.optLong("chat_id")
                if (chatId != currentChatId) return
                val messageId = update.optLong("message_id")
                val existing = byId[messageId] ?: return
                val content = update.optJSONObject("new_content") ?: return
                val updated = parseContent(existing, content)
                byId[messageId] = updated
                // A finished transcription (v0.9.55, Z3)
                // ends the recognizing state the menu tap
                // started; the bubble area keys off the
                // parsed state from here on.
                if (updated.speechState == "text" ||
                    updated.speechState == "error"
                ) {
                    _recognizing.value = _recognizing.value -
                        (chatId to messageId)
                }
                publish()
            }
            "updateMessageEdited" -> {
                // An edit landed (from any device): flip the
                // "已编辑" marker on the open chat's copy right
                // away instead of waiting for a reload
                // (v0.9.29, V4). The new content itself arrives
                // as updateMessageContent just before this.
                val chatId = update.optLong("chat_id")
                if (chatId != currentChatId) return
                val messageId = update.optLong("message_id")
                val existing = byId[messageId] ?: return
                if (update.optInt("edit_date") > 0 && !existing.edited) {
                    byId[messageId] = existing.copy(edited = true)
                    publish()
                }
            }
            "updateMessageInteractionInfo" -> {
                val chatId = update.optLong("chat_id")
                if (chatId != currentChatId) return
                val messageId = update.optLong("message_id")
                val existing = byId[messageId] ?: return
                val info = update.optJSONObject("interaction_info")
                byId[messageId] = existing.copy(
                    reactions = reactionsOf(info),
                    // Comment count rides the same update
                    // (v0.9.55, Z1): a fresh reply_info wins;
                    // an update without one keeps the count
                    // the parse already had.
                    commentCount = info
                        ?.optJSONObject("reply_info")
                        ?.optInt("reply_count")
                        ?: existing.commentCount
                )
                publish()
            }
            "updateDeleteMessages" -> {
                // Deleted on any device (or by the other side / an
                // admin): the bubbles must disappear here too.
                val chatId = update.optLong("chat_id")
                if (chatId != currentChatId) return
                val ids = update.optJSONArray("message_ids") ?: return
                var changed = false
                for (i in 0 until ids.length()) {
                    if (byId.remove(ids.optLong(i)) != null) changed = true
                }
                if (changed) publish()
            }
            "updateChat" -> {
                // Access refresh (v0.9.44, N3): the chat
                // object changed — new default permissions,
                // a join landing, a title change. Keep the
                // resolved cache and the dock's decision in
                // step without a re-entry.
                val chat = update.optJSONObject("chat") ?: return
                if (chat.optLong("id") != currentChatId) return
                ChatStore.noteResolvedChat(chat)
                currentChatObject = chat
                refreshAccess()
            }
            "updateChatMember" -> {
                // A member's status changed in the open chat
                // (v0.9.44, N3): when it is MY status (muted,
                // unmuted, kicked, readmitted) the dock must
                // follow. The update does not say cheaply
                // whether it was me, and member changes in
                // one open chat are rare — re-decide from the
                // group object (a local read) on any of them.
                if (update.optLong("chat_id") != currentChatId) {
                    return
                }
                refreshAccess()
            }
            "updateChatPermissions" -> {
                // Default-permissions change (v0.9.44, N3):
                // TDLib delivers permission changes on this
                // dedicated update, not inside updateChat —
                // fold the new permissions into the stored
                // chat object and re-decide the dock, so an
                // admin opening or closing posting while the
                // chat is on screen flips the bar live.
                if (update.optLong("chat_id") != currentChatId) {
                    return
                }
                val perms = update.optJSONObject("permissions")
                    ?: return
                val chatObj = currentChatObject ?: return
                chatObj.put("permissions", perms)
                refreshAccess()
            }
            "updateSupergroup" -> {
                val sg = update.optJSONObject("supergroup")
                    ?: return
                if (currentSupergroupId != 0L &&
                    sg.optLong("id") == currentSupergroupId
                ) {
                    refreshAccess()
                }
            }
            "updateBasicGroup" -> {
                val bg = update.optJSONObject("basic_group")
                    ?: return
                if (currentBasicGroupId != 0L &&
                    bg.optLong("id") == currentBasicGroupId
                ) {
                    refreshAccess()
                }
            }
            "updateChatDraftMessage" -> {
                // Cross-device draft sync (v0.9.49, T2): the
                // draft changed on the server (edited or
                // cleared on another device). ChatStore already
                // refreshes the chat-list preview from the same
                // update; here the OPEN chat's input baseline
                // follows. Before draftLoaded the getChat in
                // open() is the authority, so early updates are
                // ignored. The UI decides whether the new text
                // may enter the box (only with no unsaved local
                // edits) — this store never touches the input.
                if (update.optLong("chat_id") != currentChatId) {
                    return
                }
                if (!_draftLoaded.value) return
                val content = update.optJSONObject("draft_message")
                    ?.optJSONObject("content")
                val newText = if (
                    content != null &&
                    content.optString("@type") ==
                    "draftMessageContentText"
                ) {
                    content.optJSONObject("text")
                        ?.optString("text") ?: ""
                } else {
                    ""
                }
                if (newText == _draftText.value) return
                _draftText.value = newText
                _draftRevision.value += 1
            }
            "updateChatReplyMarkup" -> {
                // The live reply keyboard changed (W2). A null
                // reply_markup_message means the keyboard was
                // removed (replyMarkupRemoveKeyboard arrives
                // exactly this way, per the tl note).
                if (update.optLong("chat_id") != currentChatId) return
                val kbMsg = update.optJSONObject("reply_markup_message")
                _replyKb.value = if (kbMsg == null) {
                    ReplyKb.Hidden
                } else {
                    parseReplyKb(
                        kbMsg.optJSONObject("reply_markup"),
                        kbMsg.optLong("id")
                    )
                }
            }
            "updateChatReadOutbox" -> {
                val chatId = update.optLong("chat_id")
                if (chatId != currentChatId) return
                lastReadOutbox = update.optLong("last_read_outbox_message_id")
                publish()
            }
            "updateChatAction" -> {
                // The other side typing / recording / uploading
                // (verified in td_api.tl: chat_id, topic_id,
                // sender_id, action). Keyed per sender so a
                // group can show who it is; cancel removes them.
                val chatId = update.optLong("chat_id")
                if (chatId != currentChatId) return
                // Topic filter (v0.9.29, V5): this store only
                // carries the chat's main timeline. An action
                // scoped to a forum topic (topic_id != 0)
                // belongs to that topic's own timeline (the
                // ForumStore's) and must not leak into the main
                // chat's subtitle. Non-forum updates carry no
                // topic_id, which reads as 0 here.
                if (update.optLong("topic_id") != 0L) return
                val sender = update.optJSONObject("sender_id") ?: return
                val actionType = update.optJSONObject("action")
                    ?.optString("@type") ?: return
                val isUser = sender.optString("@type") == "messageSenderUser"
                val key = when (sender.optString("@type")) {
                    "messageSenderUser" -> sender.optLong("user_id")
                    "messageSenderChat" -> sender.optLong("chat_id")
                    else -> 0L
                }
                if (key == 0L) return
                if (actionType == "chatActionCancel") {
                    peerActions.remove(key)
                } else {
                    val label = when (actionType) {
                        "chatActionTyping" -> "正在输入…"
                        "chatActionRecordingVoiceNote" -> "正在录音…"
                        "chatActionRecordingVideoNote" -> "正在录视频消息…"
                        "chatActionUploadingPhoto" -> "正在发送图片…"
                        "chatActionUploadingVideo" -> "正在发送视频…"
                        "chatActionUploadingDocument" -> "正在发送文件…"
                        "chatActionUploadingVoiceNote" -> "正在发送语音…"
                        "chatActionChoosingSticker" -> "正在选贴纸…"
                        else -> return
                    }
                    val name = if (isUser) {
                        userNames[key] ?: run {
                            fetchUser(key)
                            ""
                        }
                    } else {
                        ""
                    }
                    peerActions[key] = PeerActionEntry(
                        name = name,
                        label = label,
                        at = System.currentTimeMillis()
                    )
                }
                lastActionAt = System.currentTimeMillis()
                renderPeerAction(chatId)
            }
            "updatePoll" -> {
                // Full poll object pushed on every vote change;
                // match it to the open chat's poll message by id.
                val pollObj = update.optJSONObject("poll") ?: return
                val pollId = pollObj.optLong("id")
                var changed = false
                for ((id, item) in byId) {
                    if (item.pollId == pollId && item.pollId != 0L) {
                        byId[id] = item.copy(poll = parsePoll(pollObj))
                        changed = true
                    }
                }
                if (changed) publish()
            }
            "updateFile" -> {
                val file = update.optJSONObject("file") ?: return
                val local = file.optJSONObject("local") ?: return
                val fileId = file.optInt("id")
                val completed = local.optBoolean("is_downloading_completed")
                val active = local.optBoolean("is_downloading_active")
                val path = local.optString("path")
                var changed = false
                for ((id, item) in byId) {
                    if (completed && item.photoFileId == fileId && item.photoPath == null) {
                        byId[id] = item.copy(photoPath = path)
                        changed = true
                    }
                    if (completed && item.lpPhotoFileId == fileId && item.lpPhotoPath == null) {
                        byId[id] = item.copy(lpPhotoPath = path)
                        changed = true
                    }
                    if (completed && item.thumbFileId == fileId && item.thumbFileId != 0 && item.thumbPath == null) {
                        byId[id] = item.copy(thumbPath = path)
                        changed = true
                    }
                    if (completed && item.animSoundFileId == fileId &&
                        item.animSoundFileId != 0 && item.animSoundPath == null
                    ) {
                        byId[id] = item.copy(animSoundPath = path)
                        changed = true
                    }
                    if (item.fileId == fileId && item.fileId != 0) {
                        if (completed && item.filePath == null) {
                            byId[id] = item.copy(filePath = path, fileDownloading = false)
                            changed = true
                        } else if (!completed && active != item.fileDownloading) {
                            byId[id] = item.copy(fileDownloading = active)
                            changed = true
                        }
                    }
                    if (completed) {
                        // Paid media entries (v0.9.35, B1)
                        // ride their own file ids.
                        val paidPatched = item.withPaidFile(fileId, path)
                        if (paidPatched != null) {
                            byId[id] = paidPatched
                            changed = true
                        }
                    }
                }
                if (changed) publish()
            }
            "updateUser" -> {
                val user = update.optJSONObject("user") ?: return
                AvatarStore.noteUser(user)
                val handleChanged = noteUserHandle(user)
                val name = userNameOf(user)
                if (name.isNotEmpty()) {
                    userNames[user.optLong("id")] = name
                    publish()
                } else if (handleChanged) {
                    publish()
                }
            }
        }
    }

    private fun userNameOf(user: JSONObject): String {
        val first = user.optString("first_name")
        val last = user.optString("last_name")
        return listOf(first, last).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** Record a user's public handle (usernames
     *  .active_usernames[0]) into [userHandles]; returns
     *  true when the stored value changed (v0.9.39, F3). */
    private fun noteUserHandle(user: JSONObject): Boolean {
        val userId = user.optLong("id")
        if (userId == 0L) return false
        val handle = user.optJSONObject("usernames")
            ?.optJSONArray("active_usernames")
            ?.optString(0) ?: ""
        if (handle.isEmpty() || userHandles[userId] == handle) {
            return false
        }
        userHandles[userId] = handle
        return true
    }

    /** The via-bot label for one message (v0.9.39, F3):
     *  "@handle" when the bot has a public username (the
     *  official form), else its display name, else "" while
     *  a fetch is in flight — publish() backfills it once
     *  the user lands, exactly like sender names. */
    private fun viaBotLabelFor(viaBotUserId: Long): String {
        if (viaBotUserId == 0L) return ""
        userHandles[viaBotUserId]?.let { return "@$it" }
        userNames[viaBotUserId]?.let { return it }
        fetchUser(viaBotUserId)
        return ""
    }

    private fun senderNameFor(sender: JSONObject?): String {
        if (sender == null) return ""
        return when (sender.optString("@type")) {
            "messageSenderUser" -> {
                val userId = sender.optLong("user_id")
                userNames[userId] ?: run {
                    fetchUser(userId)
                    ""
                }
            }
            "messageSenderChat" -> ""
            else -> ""
        }
    }

    private fun fetchUser(userId: Long) {
        if (!userFetchPending.add(userId)) return
        val query = TdClient.request("getUser").apply { put("user_id", userId) }
        TdRepository.sendRaw(query) { response ->
            userFetchPending.remove(userId)
            if (TdClient.isError(response)) return@sendRaw
            AvatarStore.noteUser(response)
            noteUserHandle(response)
            val name = userNameOf(response)
            if (name.isNotEmpty()) {
                userNames[userId] = name
                // A typing subtitle may be waiting on this
                // name (v0.9.28, U5): fill it in and re-render
                // so "张三 正在输入…" appears without waiting
                // for the next action update.
                val pendingAction = peerActions[userId]
                if (pendingAction != null &&
                    pendingAction.name.isEmpty()
                ) {
                    peerActions[userId] =
                        pendingAction.copy(name = name)
                    renderPeerAction(currentChatId)
                }
                publish()
            }
        }
    }

    /** Flatten a formattedText object's entities (v0.9.2, L1).
     *  The full textEntityType set was verified against
     *  td_api.tl; types with no distinct rendering here map to
     *  "plain" and stay visible as ordinary text. Shared by the
     *  main chat, thread and forum stores through parseMessage. */
    fun entitiesOf(formatted: JSONObject?): List<TextEntityUi> {
        val arr = formatted?.optJSONArray("entities") ?: return emptyList()
        val out = ArrayList<TextEntityUi>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val type = e.optJSONObject("type") ?: continue
            val offset = e.optInt("offset", -1)
            val length = e.optInt("length", 0)
            if (offset < 0 || length <= 0) continue
            val kind = when (type.optString("@type")) {
                "textEntityTypeBold" -> "bold"
                "textEntityTypeItalic" -> "italic"
                "textEntityTypeUnderline" -> "underline"
                "textEntityTypeStrikethrough" -> "strike"
                "textEntityTypeCode" -> "code"
                "textEntityTypePre", "textEntityTypePreCode" -> "pre"
                "textEntityTypeBlockQuote",
                "textEntityTypeExpandableBlockQuote" -> "quote"
                "textEntityTypeSpoiler" -> "spoiler"
                "textEntityTypeUrl" -> "url"
                "textEntityTypeTextUrl" -> "textUrl"
                "textEntityTypeMention" -> "mention"
                "textEntityTypeMentionName" -> "mentionName"
                "textEntityTypeHashtag" -> "hashtag"
                "textEntityTypeCashtag" -> "cashtag"
                "textEntityTypeBotCommand" -> "botCommand"
                "textEntityTypeEmailAddress" -> "email"
                "textEntityTypePhoneNumber" -> "phone"
                "textEntityTypeCustomEmoji" -> "customEmoji"
                else -> "plain"
            }
            out.add(
                TextEntityUi(
                    offset = offset,
                    length = length,
                    kind = kind,
                    url = if (kind == "textUrl") {
                        type.optString("url")
                    } else {
                        ""
                    },
                    userId = if (kind == "mentionName") {
                        type.optLong("user_id")
                    } else {
                        0L
                    },
                    customEmojiId = if (kind == "customEmoji") {
                        type.optLong("custom_emoji_id")
                    } else {
                        0L
                    }
                )
            )
        }
        // Bare-link fill-in (v0.9.47, R2): links the server
        // shipped without an entity are detected locally over
        // the ranges no server entity covers. See
        // detectLocalLinks for the root cause and rules.
        val rawText = formatted?.optString("text") ?: ""
        if (rawText.isNotEmpty()) {
            out.addAll(detectLocalLinks(rawText, out))
        }
        return out
    }

    /** Bare-link detection for text the server left bare
     *  (v0.9.47, R2). Root cause: the server auto-detects
     *  entities in user-sent text, but bot-API messages sent
     *  without explicit entities arrive with NO url entity
     *  for their links — and a 1:1 entity mapping then
     *  renders those links as dead plain text (the user's
     *  screenshot: a t.me/<bot>?start=… link inside a bot
     *  message, fully visible but untappable and unstyled).
     *  The official clients linkify such text locally; this
     *  does the same at the app's single parse point, so
     *  every host picks the links up with the usual url
     *  styling and LinkRouter behaviour (t.me bot links run
     *  the start flow, other t.me links resolve in-app,
     *  external links go to the browser). Chat-list previews
     *  render plain summaries and stay inert, as before.
     *
     *  Rules: only ranges NOT covered by any server entity
     *  are scanned (server entities always win; nothing is
     *  detected inside code / pre / spoiler / link spans);
     *  a match glued to an ASCII token character, @, . , _
     *  or - on its left is the tail of a longer token (an
     *  email's domain part, a file name) and is skipped;
     *  trailing sentence punctuation (CJK included) is
     *  trimmed, and a closing parenthesis is trimmed
     *  unless the link opened one itself. Detected shapes:
     *  scheme URLs, www. hosts, bare t.me-family hosts
     *  with paths, and bare domains over a common-TLD list
     *  (so version strings, IPs and file names stay plain).
     *
     *  Boundary tightening (v0.9.48, S2 — a 30-case sample
     *  table was verified against this exact logic, see
     *  the checkpoint): matching is case-insensitive (T.ME
     *  and EXAMPLE.COM are links; LinkRouter lowercases
     *  the host anyway); the left-glue test only counts
     *  ASCII token characters, because a CJK character in
     *  front is prose, not part of the token (a link pasted
     *  straight after Chinese text still counts); a bare
     *  domain whose "TLD" is only the prefix of a longer
     *  label — example.commercial, x.com3, and the
     *  photo.jpg -> photo.jp misfire — is not a link at
     *  all; a CJK sentence punctuation inside a match is
     *  prose separation and ends the match, with scanning
     *  resuming at the cut so a second link after the
     *  comma is still found; a CJK ideograph ends a match
     *  in the authority part (a host glued to prose) and
     *  anywhere in a t.me-family match (its paths are
     *  ASCII by construction), while CJK in a generic path
     *  stays (the wiki-style case); CJK closing brackets
     *  join the trailing trim set.
     *
     *  parseTextEntities is deliberately not used: it is an
     *  asynchronous TDLib request whose documented parse
     *  modes cover markup entities rather than bare-URL
     *  detection, while parseMessage is a synchronous bulk
     *  path (a full history page at a time). */
    private fun detectLocalLinks(
        text: String,
        existing: List<TextEntityUi>
    ): List<TextEntityUi> {
        if (text.isEmpty()) return emptyList()
        val covered = existing.map {
            it.offset until (it.offset + it.length)
        }
        fun isCovered(start: Int, end: Int): Boolean =
            covered.any { start < it.last && it.first < end }
        val out = ArrayList<TextEntityUi>()
        var pos = 0
        while (pos < text.length) {
            val match = LOCAL_LINK_REGEX.find(text, pos) ?: break
            val start = match.range.first
            val rawEnd = match.range.last + 1
            // Where scanning resumes: past the raw match —
            // or, when a mid-match cut below shortened the
            // link, at the cut, so the prose after it (and
            // any second link) is still scanned.
            var resume = rawEnd
            var end = rawEnd
            val matched = match.value
            val prefixed =
                matched.startsWith("http://", true) ||
                    matched.startsWith("https://", true) ||
                    matched.startsWith("www.", true) ||
                    matched.startsWith("t.me/", true) ||
                    matched.startsWith("telegram.me/", true) ||
                    matched.startsWith("telegram.dog/", true)
            // Bare-domain prefix-of-a-label rejection
            // (v0.9.48, S2): a letter, digit or hyphen right
            // after the raw match means the TLD was only
            // the head of a longer label.
            if (!prefixed && rawEnd < text.length) {
                val nxt = text[rawEnd]
                if (nxt in 'a'..'z' || nxt in 'A'..'Z' ||
                    nxt in '0'..'9' || nxt == '-'
                ) {
                    pos = resume
                    continue
                }
            }
            // Left glue (v0.9.48, S2): ASCII token
            // characters only — see the rules above.
            if (start > 0) {
                val prev = text[start - 1]
                if (prev in 'a'..'z' || prev in 'A'..'Z' ||
                    prev in '0'..'9' || prev == '@' ||
                    prev == '.' || prev == '_' || prev == '-'
                ) {
                    pos = resume
                    continue
                }
            }
            // Mid-match cuts (v0.9.48, S2): CJK sentence
            // punctuation first (any shape), then the
            // ideograph rules for the prefixed shapes.
            var cut = end
            for (i in start until end) {
                if (text[i] in "，。；：！？、") {
                    cut = i
                    break
                }
            }
            if (prefixed) {
                // Bounded by the punctuation cut above: an
                // ideograph BEFORE that punctuation still
                // ends the link first.
                val lower = matched.lowercase()
                val isTme = lower.startsWith("t.me/") ||
                    lower.startsWith("telegram.me/") ||
                    lower.startsWith("telegram.dog/")
                if (isTme) {
                    for (i in start until cut) {
                        if (isCjkIdeograph(text[i])) {
                            cut = i
                            break
                        }
                    }
                } else {
                    val schemeEnd = lower.indexOf("://")
                    val hostStart = if (schemeEnd >= 0) {
                        start + schemeEnd + 3
                    } else {
                        start
                    }
                    val slash = text.indexOf('/', hostStart)
                    val authEnd = if (slash in start until cut) {
                        slash
                    } else {
                        cut
                    }
                    for (i in hostStart until authEnd) {
                        if (isCjkIdeograph(text[i])) {
                            cut = i
                            break
                        }
                    }
                }
            }
            if (cut < end) {
                end = cut
                resume = cut
            }
            while (end > start) {
                val c = text[end - 1]
                if (c in ".,;:!?，。；：！？、…·\"'」』】") {
                    end--
                } else if (c == ')' || c == '）') {
                    val seg = text.substring(start, end)
                    if (seg.count { it == '(' || it == '（' } >=
                        seg.count { it == ')' || it == '）' }
                    ) {
                        break
                    }
                    end--
                } else if (c == ']' || c == '}' || c == '>') {
                    end--
                } else {
                    break
                }
            }
            if (end - start >= 4 && !isCovered(start, end)) {
                out.add(
                    TextEntityUi(
                        offset = start,
                        length = end - start,
                        kind = "url"
                    )
                )
            }
            pos = resume
        }
        return out
    }

    /** CJK ideograph ranges for the link boundary rules
     *  (v0.9.48, S2): Han, Hiragana/Katakana, Hangul. */
    private fun isCjkIdeograph(c: Char): Boolean =
        c in '㐀'..'䶿' || c in '一'..'鿿' ||
            c in '぀'..'ヿ' || c in '가'..'힯'

    private val LOCAL_LINK_REGEX = Regex(
        "(?:https?://|www\\.|t\\.me/|telegram\\.me/|telegram\\.dog/)" +
            "[^\\s<>\"'\\u3000]+" +
            "|[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?" +
            "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*" +
            "\\.(?:com|net|org|io|me|co|app|dev|site|online|xyz|top|club|info|biz|tv|cc|cn|hk|tw|ru|uk|de|jp|kr|gg|ly|gl|link|live|pro|vip)" +
            "(?:/[^\\s<>\"'\\u3000]*)?",
        RegexOption.IGNORE_CASE
    )

    /** Decode a voice note's waveform (v0.9.2, L5): base64 of
     *  5-bit amplitudes packed LSB-first — the exact inverse
     *  of the packing the recorder uses when sending. A bad or
     *  missing waveform decodes to an empty list and the bubble
     *  falls back to flat bars; it never blocks rendering. */
    fun decodeWaveform(base64: String?): List<Int> {
        if (base64.isNullOrEmpty()) return emptyList()
        return try {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            val out = ArrayList<Int>()
            var acc = 0
            var bits = 0
            for (b in bytes) {
                acc = acc or ((b.toInt() and 0xFF) shl bits)
                bits += 8
                while (bits >= 5) {
                    out.add(acc and 0x1F)
                    acc = acc ushr 5
                    bits -= 5
                }
            }
            out
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /** Speech-recognition state of a voice note (v0.9.55,
     *  Z3): voice_note.speech_recognition_result's @type,
     *  flattened to the MsgItem speechState vocabulary. */
    private fun speechStateOf(voice: JSONObject?): String =
        when (
            voice?.optJSONObject("speech_recognition_result")
                ?.optString("@type")
        ) {
            "speechRecognitionResultPending" -> "pending"
            "speechRecognitionResultText" -> "text"
            "speechRecognitionResultError" -> "error"
            else -> ""
        }

    /** The transcript (or, while pending, the partial
     *  transcript) of a voice note (v0.9.55, Z3). */
    private fun speechTextOf(voice: JSONObject?): String {
        val result = voice?.optJSONObject("speech_recognition_result")
            ?: return ""
        return when (result.optString("@type")) {
            "speechRecognitionResultText" -> result.optString("text")
            "speechRecognitionResultPending" ->
                result.optString("partial_text")
            else -> ""
        }
    }

    /** The messageSendingStateFailed object of a message
     *  JSON, or null when the message is not in the failed
     *  state (v0.9.44, N1/N2). */
    fun failedStateOf(msg: JSONObject): JSONObject? {
        val state = msg.optJSONObject("sending_state") ?: return null
        return state.takeIf {
            it.optString("@type") == "messageSendingStateFailed"
        }
    }

    /** Copy [item] into the failed state, taking the whole
     *  messageSendingStateFailed payload from the message
     *  JSON [msg] (v0.9.44, N1/N2). Shared by all three
     *  message stores' updateMessageSendFailed handlers so
     *  the failure detail can never drift between hosts. */
    fun withFailedState(item: MsgItem, msg: JSONObject): MsgItem {
        val failed = failedStateOf(msg)
        return item.copy(
            sendingState = "failed",
            failCode = failed?.optJSONObject("error")
                ?.optInt("code") ?: 0,
            failId = failed?.optJSONObject("error")
                ?.optString("message") ?: "",
            canRetry = failed?.optBoolean("can_retry") ?: false,
            retryAfterSec = failed?.optDouble("retry_after")
                ?: 0.0,
            paidStarsRequired = failed
                ?.optLong("required_paid_message_star_count") ?: 0L
        )
    }

    fun parseMessage(msg: JSONObject): MsgItem {
        val base = MsgItem(
            id = msg.optLong("id"),
            outgoing = msg.optBoolean("is_outgoing"),
            dateSeconds = msg.optInt("date"),
            edited = msg.optInt("edit_date") > 0,
            kind = MsgKind.OTHER,
            text = "",
            senderName = senderNameFor(msg.optJSONObject("sender_id")),
            senderUserId = msg.optJSONObject("sender_id")
                ?.takeIf { it.optString("@type") == "messageSenderUser" }
                ?.optLong("user_id") ?: 0L,
            viaBotUserId = msg.optLong("via_bot_user_id"),
            viaBotLabel = viaBotLabelFor(msg.optLong("via_bot_user_id")),
            senderTag = msg.optString("sender_tag"),
            // Resolved at parse time when the sender's photo is
            // already on disk; the main store's publish() keeps
            // backfilling it afterwards (W0).
            senderPhotoPath = msg.optJSONObject("sender_id")
                ?.takeIf { it.optString("@type") == "messageSenderUser" }
                ?.optLong("user_id")
                ?.let { AvatarStore.pathForUser(it) },
            photoFileId = 0,
            photoPath = null,
            photoWidth = 0,
            photoHeight = 0,
            sendingState = when (
                msg.optJSONObject("sending_state")?.optString("@type")
            ) {
                "messageSendingStatePending" -> "pending"
                "messageSendingStateFailed" -> "failed"
                else -> ""
            },
            // Failed-send detail (v0.9.44, N1/N2): the whole
            // messageSendingStateFailed payload rides along
            // so the bubble can say WHY it failed and whether
            // a resend is even possible (see SendFailure).
            failCode = failedStateOf(msg)
                ?.optJSONObject("error")?.optInt("code") ?: 0,
            failId = failedStateOf(msg)
                ?.optJSONObject("error")?.optString("message") ?: "",
            canRetry = failedStateOf(msg)
                ?.optBoolean("can_retry") ?: false,
            retryAfterSec = failedStateOf(msg)
                ?.optDouble("retry_after") ?: 0.0,
            paidStarsRequired = failedStateOf(msg)
                ?.optLong("required_paid_message_star_count") ?: 0L,
            replyToId = replyTargetId(msg),
            replyChatId = replyTargetChatId(msg),
            replySummary = replySummaryOf(msg),
            mediaAlbumId = msg.optLong("media_album_id"),
            effectId = msg.optLong("effect_id"),
            reactions = reactionsOf(msg.optJSONObject("interaction_info")),
            canGetAddedReactions =
                msg.optJSONObject("interaction_info")
                    ?.optJSONObject("reactions")
                    ?.optBoolean("can_get_added_reactions") ?: false,
            commentCount =
                msg.optJSONObject("interaction_info")
                    ?.optJSONObject("reply_info")
                    ?.optInt("reply_count") ?: -1,
            buttons = parseButtons(msg.optJSONObject("reply_markup")),
            isChannelPost = msg.optBoolean("is_channel_post"),
            isPinned = msg.optBoolean("is_pinned"),
            scheduledAtSec = when (
                msg.optJSONObject("scheduling_state")?.optString("@type")
            ) {
                "messageSchedulingStateSendAtDate" ->
                    msg.optJSONObject("scheduling_state")
                        ?.optInt("send_date") ?: 0
                else -> 0
            },
            scheduledWhenOnline =
                msg.optJSONObject("scheduling_state")
                    ?.optString("@type") ==
                    "messageSchedulingStateSendWhenOnline"
        )
        val content = msg.optJSONObject("content") ?: return base
        val parsed = parseContent(base, content)
        // Speech-recognition self-heal (v0.9.56, A3): a
        // freshly parsed voice note already carrying a
        // terminal transcription state ends the local
        // recognizing flag for it, whichever host did the
        // parsing. ThreadStore / ForumStore refetch through
        // here on updateMessageContent but never see this
        // store's own update handler (it only serves the
        // open main chat), so without this a failed
        // transcription inside a thread or topic left the
        // bubble showing "正在转文字…" forever — the stale
        // busy flag outranked the parsed error state.
        if (parsed.speechState == "text" ||
            parsed.speechState == "error"
        ) {
            val key = msg.optLong("chat_id") to parsed.id
            if (key in _recognizing.value) {
                _recognizing.value = _recognizing.value - key
            }
        }
        return parsed
    }

    private fun parseContent(base: MsgItem, content: JSONObject): MsgItem {
        return when (content.optString("@type")) {
            "messageText" -> {
                val lp = content.optJSONObject("link_preview")
                val lpSize = lpPhotoFile(lp)
                val lpPhoto = lpSize?.optJSONObject("photo")
                val lpFileId = lpPhoto?.optInt("id", 0) ?: 0
                if (lpFileId != 0 && downloadedPath(lpPhoto) == null) {
                    requestDownload(lpFileId, priority = 1)
                }
                base.copy(
                    kind = MsgKind.TEXT,
                    text = content.optJSONObject("text")?.optString("text") ?: "",
                    entities = entitiesOf(content.optJSONObject("text")),
                    lpSiteName = lp?.optString("site_name") ?: "",
                    lpTitle = lp?.optString("title") ?: "",
                    lpDescription = lp?.optJSONObject("description")
                        ?.optString("text") ?: "",
                    lpUrl = lp?.optString("url") ?: "",
                    lpPhotoFileId = lpFileId,
                    lpPhotoPath = downloadedPath(lpPhoto),
                    lpPhotoWidth = lpSize?.optInt("width", 0) ?: 0,
                    lpInstantView = lp?.optInt("instant_view_version", 0)
                        ?: 0
                )
            }
            "messageSticker" -> {
                val sticker = content.optJSONObject("sticker")
                val file = sticker?.optJSONObject("sticker")
                val fileId = file?.optInt("id", 0) ?: 0
                if (fileId != 0 && downloadedPath(file) == null) {
                    requestDownload(fileId, priority = 16)
                }
                base.copy(
                    kind = MsgKind.STICKER,
                    stickerEmoji = sticker?.optString("emoji") ?: "",
                    fileId = fileId,
                    filePath = downloadedPath(file),
                    fileMime = "image/webp",
                    photoWidth = sticker?.optInt("width", 0) ?: 0,
                    photoHeight = sticker?.optInt("height", 0) ?: 0
                )
            }
            "messageAnimation" -> {
                val animation = content.optJSONObject("animation")
                val file = animation?.optJSONObject("animation")
                val fileId = file?.optInt("id", 0) ?: 0
                if (fileId != 0 && downloadedPath(file) == null) {
                    requestDownload(fileId, priority = 16)
                }
                base.copy(
                    kind = MsgKind.ANIMATION,
                    text = content.optJSONObject("caption")?.optString("text") ?: "",
                    entities = entitiesOf(content.optJSONObject("caption")),
                    fileId = fileId,
                    filePath = downloadedPath(file),
                    fileName = animation?.optString("file_name") ?: "",
                    fileSize = fileSizeOf(file),
                    fileMime = animation?.optString("mime_type") ?: "video/mp4",
                    durationSec = animation?.optInt("duration", 0) ?: 0,
                    photoWidth = animation?.optInt("width", 0) ?: 0,
                    photoHeight = animation?.optInt("height", 0) ?: 0
                )
            }
            "messagePoll" -> {
                val pollObj = content.optJSONObject("poll")
                base.copy(
                    kind = MsgKind.POLL,
                    pollId = pollObj?.optLong("id", 0L) ?: 0L,
                    poll = pollObj?.let { parsePoll(it) }
                )
            }
            "messageChecklist" -> {
                // Checklist message (v0.9.24, Q7): fields per
                // td_api.tl checklist / checklistTask. Whether
                // this account may tick tasks is the server's
                // own can_mark_tasks_as_done flag.
                val list = content.optJSONObject("list")
                val tasks = ArrayList<ChecklistTaskUi>()
                val arr = list?.optJSONArray("tasks")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val t = arr.optJSONObject(i) ?: continue
                        tasks.add(
                            ChecklistTaskUi(
                                id = t.optInt("id"),
                                text = t.optJSONObject("text")
                                    ?.optString("text") ?: "",
                                entities = entitiesOf(
                                    t.optJSONObject("text")
                                ),
                                completed =
                                    t.optInt("completion_date") != 0
                            )
                        )
                    }
                }
                base.copy(
                    kind = MsgKind.CHECKLIST,
                    checklistTitle = list?.optJSONObject("title")
                        ?.optString("text") ?: "",
                    checklistTitleEntities = entitiesOf(
                        list?.optJSONObject("title")
                    ),
                    checklistTasks = tasks,
                    checklistCanMark =
                        list?.optBoolean("can_mark_tasks_as_done")
                            ?: false,
                    checklistCanAdd =
                        list?.optBoolean("can_add_tasks") ?: false,
                    checklistOthersCanAdd =
                        list?.optBoolean("others_can_add_tasks")
                            ?: false,
                    checklistOthersCanMark =
                        list?.optBoolean("others_can_mark_tasks_as_done")
                            ?: false
                )
            }
            "messageLocation" -> {
                val loc = content.optJSONObject("location")
                base.copy(
                    kind = MsgKind.LOCATION,
                    latitude = loc?.optDouble("latitude", 0.0) ?: 0.0,
                    longitude = loc?.optDouble("longitude", 0.0) ?: 0.0
                )
            }
            "messageLiveLocation" -> {
                // Live location (v0.9.25, R2): the point lives
                // one level down (liveLocation.location) and
                // expires_in is the seconds left to update it.
                val live = content.optJSONObject("location")
                val loc = live?.optJSONObject("location")
                base.copy(
                    kind = MsgKind.LOCATION,
                    latitude = loc?.optDouble("latitude", 0.0) ?: 0.0,
                    longitude = loc?.optDouble("longitude", 0.0)
                        ?: 0.0,
                    liveExpiresIn = content.optInt("expires_in")
                )
            }
            "messageContact" -> {
                val contact = content.optJSONObject("contact")
                val first = contact?.optString("first_name") ?: ""
                val last = contact?.optString("last_name") ?: ""
                base.copy(
                    kind = MsgKind.CONTACT,
                    contactName = listOf(first, last)
                        .filter { it.isNotEmpty() }.joinToString(" "),
                    contactPhone = contact?.optString("phone_number") ?: "",
                    contactUserId = contact?.optLong("user_id", 0L) ?: 0L
                )
            }
            "messagePhoto" -> {
                val photo = content.optJSONObject("photo")
                val size = pickPhotoSize(photo?.optJSONArray("sizes"))
                val file = size?.optJSONObject("photo")
                val local = file?.optJSONObject("local")
                val downloaded = local?.optBoolean("is_downloading_completed") == true
                val fileId = file?.optInt("id", 0) ?: 0
                if (fileId != 0 && !downloaded) {
                    TdRepository.sendRaw(TdClient.request("downloadFile").apply {
                        put("file_id", fileId)
                        put("priority", 16)
                        put("offset", 0L)
                        put("limit", 0L)
                        put("synchronous", false)
                    }) {}
                }
                base.copy(
                    kind = MsgKind.PHOTO,
                    text = content.optJSONObject("caption")?.optString("text") ?: "",
                    entities = entitiesOf(content.optJSONObject("caption")),
                    photoFileId = fileId,
                    photoPath = if (downloaded) local?.optString("path") else null,
                    photoWidth = size?.optInt("width", 0) ?: 0,
                    photoHeight = size?.optInt("height", 0) ?: 0,
                    // Progressive rendering (v0.9.47, R5):
                    // the photo's inline minithumbnail, the
                    // same field videos already ride — the
                    // bubble shows it (soft, scaled up) until
                    // the picked size lands, instead of a
                    // text-only placeholder.
                    miniThumbData = photo?.optJSONObject("minithumbnail")
                        ?.optString("data") ?: ""
                )
            }
            "messageDocument" -> {
                val doc = content.optJSONObject("document")
                val file = doc?.optJSONObject("document")
                base.copy(
                    kind = MsgKind.DOCUMENT,
                    text = content.optJSONObject("caption")?.optString("text") ?: "",
                    entities = entitiesOf(content.optJSONObject("caption")),
                    fileId = file?.optInt("id", 0) ?: 0,
                    filePath = downloadedPath(file),
                    fileName = doc?.optString("file_name") ?: "",
                    fileSize = fileSizeOf(file),
                    fileMime = doc?.optString("mime_type") ?: ""
                )
            }
            "messageVideo" -> {
                val video = content.optJSONObject("video")
                val file = video?.optJSONObject("video")
                // The video's own thumbnail (v0.9.15, V4):
                // album grids and the file row's preview need
                // a still image; it rides the same thumb
                // fields the video-note branch uses, and the
                // updateFile handler already patches them.
                val vThumbFile = video?.optJSONObject("thumbnail")
                    ?.optJSONObject("file")
                val vThumbId = vThumbFile?.optInt("id", 0) ?: 0
                if (vThumbId != 0 && downloadedPath(vThumbFile) == null) {
                    requestDownload(vThumbId, priority = 1)
                }
                base.copy(
                    kind = MsgKind.VIDEO,
                    text = content.optJSONObject("caption")?.optString("text") ?: "",
                    entities = entitiesOf(content.optJSONObject("caption")),
                    fileId = file?.optInt("id", 0) ?: 0,
                    filePath = downloadedPath(file),
                    fileName = video?.optString("file_name") ?: "",
                    fileSize = fileSizeOf(file),
                    fileMime = video?.optString("mime_type") ?: "",
                    durationSec = video?.optInt("duration", 0) ?: 0,
                    videoWidth = video?.optInt("width", 0) ?: 0,
                    videoHeight = video?.optInt("height", 0) ?: 0,
                    thumbFileId = vThumbId,
                    thumbPath = downloadedPath(vThumbFile),
                    miniThumbData = video?.optJSONObject("minithumbnail")
                        ?.optString("data") ?: ""
                )
            }
            "messageVoiceNote" -> {
                val voice = content.optJSONObject("voice_note")
                val file = voice?.optJSONObject("voice")
                base.copy(
                    kind = MsgKind.VOICE,
                    text = content.optJSONObject("caption")?.optString("text") ?: "",
                    entities = entitiesOf(content.optJSONObject("caption")),
                    fileId = file?.optInt("id", 0) ?: 0,
                    filePath = downloadedPath(file),
                    fileName = "语音消息",
                    fileSize = fileSizeOf(file),
                    fileMime = voice?.optString("mime_type") ?: "",
                    durationSec = voice?.optInt("duration", 0) ?: 0,
                    waveform = decodeWaveform(voice?.optString("waveform")),
                    speechState = speechStateOf(voice),
                    speechText = speechTextOf(voice)
                )
            }
            "messageVideoNote" -> {
                // Round video message (v0.9.14, U4): the
                // thumbnail and the (small, meant-to-be-instant)
                // video both auto-download; playback goes
                // through the in-app video player on tap.
                val note = content.optJSONObject("video_note")
                val file = note?.optJSONObject("video")
                val fileId = file?.optInt("id", 0) ?: 0
                if (fileId != 0 && downloadedPath(file) == null) {
                    requestDownload(fileId, priority = 8)
                }
                val thumbFile = note?.optJSONObject("thumbnail")
                    ?.optJSONObject("file")
                val thumbId = thumbFile?.optInt("id", 0) ?: 0
                if (thumbId != 0 && downloadedPath(thumbFile) == null) {
                    requestDownload(thumbId, priority = 1)
                }
                base.copy(
                    kind = MsgKind.VIDEO_NOTE,
                    fileId = fileId,
                    filePath = downloadedPath(file),
                    fileName = "视频消息",
                    fileSize = fileSizeOf(file),
                    fileMime = "video/mp4",
                    durationSec = note?.optInt("duration", 0) ?: 0,
                    videoNoteLength = note?.optInt("length", 0) ?: 0,
                    thumbFileId = thumbId,
                    thumbPath = downloadedPath(thumbFile),
                    miniThumbData = note?.optJSONObject("minithumbnail")
                        ?.optString("data") ?: ""
                )
            }
            "messageAnimatedEmoji" -> {
                // Animated emoji (v0.9.16, W4): one big
                // sticker (animatedEmoji.sticker) shown large
                // with no bubble chrome; the emoji itself is
                // the text (summaries and previews show it
                // directly, like mature clients). The click
                // sound is a small Opus file — pull it eagerly
                // so a tap can play it without waiting.
                val ae = content.optJSONObject("animated_emoji")
                val sticker = ae?.optJSONObject("sticker")
                val file = sticker?.optJSONObject("sticker")
                val fileId = file?.optInt("id", 0) ?: 0
                if (fileId != 0 && downloadedPath(file) == null) {
                    requestDownload(fileId, priority = 16)
                }
                val sound = ae?.optJSONObject("sound")
                val soundId = sound?.optInt("id", 0) ?: 0
                if (soundId != 0 && downloadedPath(sound) == null) {
                    requestDownload(soundId, priority = 1)
                }
                val emoji = content.optString("emoji")
                base.copy(
                    kind = MsgKind.ANIMATED_EMOJI,
                    text = emoji,
                    stickerEmoji = emoji,
                    fileId = fileId,
                    filePath = downloadedPath(file),
                    fileMime = "image/webp",
                    photoWidth = sticker?.optInt("width", 0) ?: 0,
                    photoHeight = sticker?.optInt("height", 0) ?: 0,
                    animSoundFileId = soundId,
                    animSoundPath = downloadedPath(sound)
                )
            }
            "messageDice" -> {
                // Dice (v0.9.14, U5): the final state's
                // regular sticker renders as a static image
                // (animated .tgs files cannot be decoded by
                // the bitmap path — the emoji + value below
                // are the honest fallback). Slot-machine
                // states carry several stickers and no single
                // image, so they stay emoji + value.
                val state = content.optJSONObject("final_state")
                    ?: content.optJSONObject("initial_state")
                var diceFile: JSONObject? = null
                if (state?.optString("@type") == "diceStickersRegular") {
                    diceFile = state.optJSONObject("sticker")
                        ?.optJSONObject("sticker")
                }
                val diceFileId = diceFile?.optInt("id", 0) ?: 0
                if (diceFileId != 0 && downloadedPath(diceFile) == null) {
                    requestDownload(diceFileId, priority = 16)
                }
                base.copy(
                    kind = MsgKind.DICE,
                    diceEmoji = content.optString("emoji"),
                    diceValue = content.optInt("value", 0),
                    fileId = diceFileId,
                    filePath = downloadedPath(diceFile),
                    fileMime = "image/webp"
                )
            }
            "messageGame" -> {
                // Game (v0.9.14, U5): cover rides the photo
                // machinery (auto-download like messagePhoto);
                // launching rides the message's own inline
                // game button (see answerCallbackGame).
                val game = content.optJSONObject("game")
                val photo = game?.optJSONObject("photo")
                val size = pickPhotoSize(photo?.optJSONArray("sizes"))
                val file = size?.optJSONObject("photo")
                val fileId = file?.optInt("id", 0) ?: 0
                if (fileId != 0 && downloadedPath(file) == null) {
                    requestDownload(fileId, priority = 1)
                }
                base.copy(
                    kind = MsgKind.GAME,
                    gameTitle = game?.optString("title") ?: "",
                    gameDescription = game?.optString("description") ?: "",
                    gameShortName = game?.optString("short_name") ?: "",
                    photoFileId = fileId,
                    photoPath = downloadedPath(file),
                    photoWidth = size?.optInt("width", 0) ?: 0,
                    photoHeight = size?.optInt("height", 0) ?: 0
                )
            }
            "messagePaidMedia" -> {
                // Paid media (v0.9.35, B1): TDLib ships real
                // photo/video entries only to accounts that
                // have access; everyone else gets preview
                // entries (dimensions + minithumbnail only).
                // The parse keeps both shapes faithfully —
                // nothing here unlocks anything.
                val mediaArr = content.optJSONArray("media")
                val entries = ArrayList<PaidMediaUi>()
                if (mediaArr != null) {
                    for (i in 0 until mediaArr.length()) {
                        val entry = mediaArr.optJSONObject(i)
                            ?: continue
                        entries.add(parsePaidMediaEntry(entry))
                    }
                }
                base.copy(
                    kind = MsgKind.PAID_MEDIA,
                    text = content.optJSONObject("caption")
                        ?.optString("text") ?: "",
                    entities = entitiesOf(
                        content.optJSONObject("caption")
                    ),
                    paidStarCount = content.optLong("star_count"),
                    paidMedia = entries
                )
            }
            "messageAudio" -> {
                // Audio file (v0.9.37, D4): until this
                // version messageAudio fell to OTHER even
                // though the chat-list preview already knew
                // it. Playback rides the voice player — it
                // is the same MediaPlayer path.
                val audio = content.optJSONObject("audio")
                val file = audio?.optJSONObject("audio")
                base.copy(
                    kind = MsgKind.AUDIO,
                    text = content.optJSONObject("caption")
                        ?.optString("text") ?: "",
                    entities = entitiesOf(
                        content.optJSONObject("caption")
                    ),
                    fileId = file?.optInt("id", 0) ?: 0,
                    filePath = downloadedPath(file),
                    fileName = audio?.optString("file_name") ?: "",
                    fileSize = fileSizeOf(file),
                    fileMime = audio?.optString("mime_type") ?: "",
                    durationSec = audio?.optInt("duration", 0) ?: 0,
                    audioTitle = audio?.optString("title") ?: "",
                    audioPerformer =
                        audio?.optString("performer") ?: ""
                )
            }
            "messageVenue" -> {
                // Venue (v0.9.37, D4): a location with a
                // name and address; renders on the location
                // card with two extra lines.
                val venue = content.optJSONObject("venue")
                val loc = venue?.optJSONObject("location")
                base.copy(
                    kind = MsgKind.LOCATION,
                    latitude = loc?.optDouble("latitude", 0.0)
                        ?: 0.0,
                    longitude = loc?.optDouble("longitude", 0.0)
                        ?: 0.0,
                    venueTitle = venue?.optString("title") ?: "",
                    venueAddress =
                        venue?.optString("address") ?: ""
                )
            }
            "messageSuggestProfilePhoto" -> {
                // Suggested avatar (v0.9.37, D4): the photo
                // itself is the content — parse it exactly
                // like a photo message so it is visible;
                // there is no caption.
                val photo = content.optJSONObject("photo")
                val size = pickPhotoSize(photo?.optJSONArray("sizes"))
                val file = size?.optJSONObject("photo")
                val local = file?.optJSONObject("local")
                val downloaded =
                    local?.optBoolean("is_downloading_completed") == true
                val fileId = file?.optInt("id", 0) ?: 0
                if (fileId != 0 && !downloaded) {
                    requestDownload(fileId, priority = 16)
                }
                base.copy(
                    kind = MsgKind.PHOTO,
                    photoFileId = fileId,
                    photoPath = if (downloaded) {
                        local?.optString("path")
                    } else {
                        null
                    },
                    photoWidth = size?.optInt("width", 0) ?: 0,
                    photoHeight = size?.optInt("height", 0) ?: 0
                )
            }
            "messageInvoice" -> {
                // Invoice (v0.9.37, D4): a bot's payment
                // request. Rendered as an information card
                // only — this app has no payment flow and
                // the card carries no pay button.
                val info = content.optJSONObject("product_info")
                base.copy(
                    kind = MsgKind.INVOICE,
                    invoiceTitle = info?.optString("title") ?: "",
                    invoiceDescription = info
                        ?.optJSONObject("description")
                        ?.optString("text") ?: "",
                    invoiceTotalText = formatMoney(
                        content.optString("currency"),
                        content.optLong("total_amount")
                    )
                )
            }
            "messageGiveaway" -> {
                // Giveaway announcement (v0.9.37, D3).
                val params = content.optJSONObject("parameters")
                val sticker = content.optJSONObject("sticker")
                val sFile = sticker?.optJSONObject("sticker")
                val sFileId = sFile?.optInt("id", 0) ?: 0
                if (sFileId != 0 && downloadedPath(sFile) == null) {
                    requestDownload(sFileId, priority = 16)
                }
                val conditions = ArrayList<String>()
                if (params?.optBoolean("only_new_members") == true) {
                    conditions.add("仅限活动开始后加入的新成员")
                }
                val extraChats = params
                    ?.optJSONArray("additional_chat_ids")
                    ?.length() ?: 0
                if (extraChats > 0) {
                    conditions.add("需要同时是另外 $extraChats 个对话的成员")
                }
                if ((params?.optJSONArray("country_codes")
                        ?.length() ?: 0) > 0
                ) {
                    conditions.add("仅限部分国家或地区的号码参加")
                }
                if (params?.optBoolean("has_public_winners") == true) {
                    conditions.add("中奖名单将公开")
                }
                base.copy(
                    kind = MsgKind.GIVEAWAY,
                    fileId = sFileId,
                    filePath = downloadedPath(sFile),
                    giveaway = GiveawayUi(
                        prizeText = giveawayPrizeText(
                            content.optJSONObject("prize")
                        ),
                        prizeDescription = params
                            ?.optString("prize_description") ?: "",
                        winnerCount = content.optInt("winner_count"),
                        conditions = conditions,
                        deadlineSec = params
                            ?.optInt("winners_selection_date") ?: 0
                    )
                )
            }
            "messageGiveawayWinners" -> {
                // Giveaway results (v0.9.37, D3).
                val ids = content.optJSONArray("winner_user_ids")
                val names = ArrayList<String>()
                if (ids != null) {
                    for (i in 0 until minOf(ids.length(), 20)) {
                        val name = cachedUserName(ids.optLong(i))
                        if (name.isNotEmpty()) names.add(name)
                    }
                }
                base.copy(
                    kind = MsgKind.GIVEAWAY,
                    giveaway = GiveawayUi(
                        prizeText = giveawayPrizeText(
                            content.optJSONObject("prize")
                        ),
                        prizeDescription = content
                            .optString("prize_description") ?: "",
                        winnerCount = content.optInt("winner_count"),
                        isWinners = true,
                        winnersDateSec = content.optInt(
                            "actual_winners_selection_date"
                        ),
                        unclaimedCount = content.optInt(
                            "unclaimed_prize_count"
                        ),
                        winnerNames = names
                    )
                )
            }
            "messageUsersShared" -> {
                // Shared users (v0.9.37, D4): names ride
                // inside the message payload itself.
                val users = content.optJSONArray("users")
                val entries = ArrayList<SharedEntryUi>()
                if (users != null) {
                    for (i in 0 until users.length()) {
                        val u = users.optJSONObject(i) ?: continue
                        val name = listOf(
                            u.optString("first_name"),
                            u.optString("last_name")
                        ).filter { it.isNotEmpty() }
                            .joinToString(" ")
                        entries.add(
                            SharedEntryUi(
                                name = name.ifEmpty { "用户" },
                                userId = u.optLong("user_id")
                            )
                        )
                    }
                }
                base.copy(
                    kind = MsgKind.SHARED,
                    sharedHeader = "分享了 ${entries.size} 位用户",
                    sharedEntries = entries
                )
            }
            "messageChatShared" -> {
                val chat = content.optJSONObject("chat")
                val title = chat?.optString("title") ?: ""
                base.copy(
                    kind = MsgKind.SHARED,
                    sharedHeader = "分享了一个对话",
                    sharedEntries = listOf(
                        SharedEntryUi(
                            name = title.ifEmpty { "对话" },
                            chatId = chat?.optLong("chat_id") ?: 0L
                        )
                    )
                )
            }
            "messageRichMessage" -> {
                // Rich message (v0.9.40, G4): the last of
                // the four content types the v0.9.37 audit
                // left on OTHER. Its payload is a richMessage
                // { blocks: PageBlock[] } — the same block
                // list an Instant View page carries — so the
                // blocks parse through InstantViewStore and
                // the bubble renders the text skeleton with
                // basic block styles; media / table blocks
                // degrade to labelled placeholder lines.
                val rich = content.optJSONObject("message")
                val blocks = InstantViewStore.parseBlocks(
                    rich?.optJSONArray("blocks")
                )
                base.copy(
                    kind = MsgKind.RICH_MESSAGE,
                    text = InstantViewStore.blocksPlainText(blocks),
                    richBlocks = blocks
                )
            }
            // The whole service family (v0.9.37, D4):
            // until this version every one of these fell
            // to OTHER and rendered as a bare "[消息]"
            // bubble. They are centered service lines now;
            // serviceTextFor composes the wording. The
            // type set lives in SERVICE_CONTENT_TYPES
            // (v0.9.38, E4) so the chat-list preview layer
            // recognises the same family.
            in SERVICE_CONTENT_TYPES -> serviceItem(base, content)
            else -> base.copy(kind = MsgKind.OTHER, text = "")
        }
    }

    /** Display name for a user id from the parse-time
     *  cache; a miss starts a fetch and yields "" (the
     *  caller composes a name-free line instead of
     *  guessing). */
    private fun cachedUserName(userId: Long): String {
        if (userId == 0L) return ""
        return userNames[userId] ?: run {
            fetchUser(userId)
            ""
        }
    }

    /** Prize wording shared by the giveaway announcement
     *  and its results post (v0.9.37, D3). */
    private fun giveawayPrizeText(prize: JSONObject?): String {
        if (prize == null) return "奖品"
        return when (prize.optString("@type")) {
            "giveawayPrizePremium" ->
                "Telegram Premium " +
                    "${prize.optInt("month_count")} 个月"
            "giveawayPrizeStars" ->
                "⭐ ${prize.optLong("star_count")} 星星"
            else -> "奖品"
        }
    }

    /** Money in a currency's smallest units (invoice and
     *  payment messages ship int53 minor units). Most
     *  currencies divide by 100; currencies without minor
     *  units are a known approximation, recorded in the
     *  v0.9.37 audit. */
    private fun formatMoney(currency: String, minor: Long): String {
        val whole = minor / 100
        val frac = minor % 100
        val amount = if (frac == 0L) {
            whole.toString()
        } else {
            "$whole.${frac.toString().padStart(2, '0')}"
        }
        return if (currency.isEmpty()) amount else "$amount $currency"
    }

    /** Compact duration for service lines: 45 秒 /
     *  3 分 20 秒 / 1 小时 5 分钟. */
    private fun formatServiceDuration(sec: Int): String {
        return when {
            sec >= 3600 ->
                "${sec / 3600} 小时 ${(sec % 3600) / 60} 分钟"
            sec >= 60 -> "${sec / 60} 分 ${sec % 60} 秒"
            else -> "$sec 秒"
        }
    }

    /** "2026年1月2日 15:04" for service lines that carry
     *  an absolute time (scheduled video chats); the clock
     *  part follows the system 12/24-hour setting
     *  (v0.9.51, V5). */
    private fun formatServiceDateTime(sec: Int): String {
        val fmt = java.text.SimpleDateFormat(
            "yyyy年M月d日", java.util.Locale.CHINA
        )
        return fmt.format(java.util.Date(sec.toLong() * 1000)) +
            " " + TimeFmt.timeOfDay(sec.toLong())
    }

    /** The 80 service content types (v0.9.37, D4),
     *  factored out of parseContent's when in v0.9.38
     *  (E4): one set of record for the parser AND for
     *  ChatStore's preview layer, which composes the
     *  same service sentence for the chat list instead
     *  of a bare "[消息]". */
    val SERVICE_CONTENT_TYPES: Set<String> = setOf(
        "messageBasicGroupChatCreate",
        "messageSupergroupChatCreate",
        "messageChatAddMembers",
        "messageChatJoinByLink",
        "messageChatJoinByRequest",
        "messageChatDeleteMember",
        "messageChatChangePhoto",
        "messageChatDeletePhoto",
        "messageChatChangeTitle",
        "messageChatSetBackground",
        "messageChatSetTheme",
        "messageChatSetMessageAutoDeleteTime",
        "messagePinMessage",
        "messageChatUpgradeFrom",
        "messageChatUpgradeTo",
        "messageChatOwnerLeft",
        "messageChatOwnerChanged",
        "messageChatBoost",
        "messageChatHasProtectedContentToggled",
        "messageChatHasProtectedContentDisableRequested",
        "messageChatAddedToCommunity",
        "messageChatJoinFromCommunity",
        "messageChatRemovedFromCommunity",
        "messageForumTopicCreated",
        "messageForumTopicEdited",
        "messageForumTopicIsClosedToggled",
        "messageForumTopicIsHiddenToggled",
        "messageVideoChatStarted",
        "messageVideoChatEnded",
        "messageVideoChatScheduled",
        "messageInviteVideoChatParticipants",
        "messageGroupCall",
        "messageCall",
        "messageGameScore",
        "messagePaymentSuccessful",
        "messagePaymentSuccessfulBot",
        "messagePaymentRefunded",
        "messagePremiumGiftCode",
        "messageGiftedPremium",
        "messageGiftedStars",
        "messageGiftedGrams",
        "messageGift",
        "messageUpgradedGift",
        "messageRefundedUpgradedGift",
        "messageUpgradedGiftPurchaseOffer",
        "messageUpgradedGiftPurchaseOfferRejected",
        "messageGiveawayCreated",
        "messageGiveawayCompleted",
        "messageGiveawayPrizeStars",
        "messageChecklistTasksAdded",
        "messageChecklistTasksDone",
        "messagePollOptionAdded",
        "messagePollOptionDeleted",
        "messageBotWriteAccessAllowed",
        "messageWebAppDataReceived",
        "messageWebAppDataSent",
        "messagePassportDataReceived",
        "messagePassportDataSent",
        "messageProximityAlertTriggered",
        "messageScreenshotTaken",
        "messageContactRegistered",
        "messageExpiredPhoto",
        "messageExpiredVideo",
        "messageExpiredVideoNote",
        "messageExpiredVoiceNote",
        "messageStory",
        "messageSuggestBirthdate",
        "messageManagedBotCreated",
        "messageDirectMessagePriceChanged",
        "messagePaidMessagePriceChanged",
        "messagePaidMessagesRefunded",
        "messageSuggestedPostApproved",
        "messageSuggestedPostDeclined",
        "messageSuggestedPostApprovalFailed",
        "messageSuggestedPostPaid",
        "messageSuggestedPostRefunded",
        "messageTonConnectRequest",
        "messageTonWalletTransfer",
        "messageUnpaidMessagesAllowedByLink",
        "messageCustomServiceAction"
    )

    fun isServiceContentType(type: String): Boolean =
        type in SERVICE_CONTENT_TYPES

    /** The service sentence for a raw message JSON
     *  (v0.9.38, E4): the exact wording the in-chat
     *  service line shows, composed from the fields the
     *  list layer already holds. "" for non-service
     *  content. */
    fun servicePreviewText(message: JSONObject): String {
        val content = message.optJSONObject("content")
            ?: return ""
        if (!isServiceContentType(content.optString("@type"))) {
            return ""
        }
        return serviceTextFor(
            outgoing = message.optBoolean("is_outgoing"),
            senderName = senderNameFor(
                message.optJSONObject("sender_id")
            ),
            content = content
        )
    }

    private fun serviceItem(
        base: MsgItem,
        content: JSONObject
    ): MsgItem = base.copy(
        kind = MsgKind.SERVICE,
        text = serviceTextFor(base.outgoing, base.senderName, content)
    )

    /** Wording for the whole service family (v0.9.37,
     *  D4). Lines are composed so they read correctly
     *  with an actor ("你" for my own actions, the
     *  sender's cached name for other users) and without
     *  one (channel posts, unresolved names) — Chinese
     *  drops the subject cleanly in the second case.
     *  Nothing is invented: a field TDLib did not ship
     *  simply never reaches the line. */
    private fun serviceTextFor(
        outgoing: Boolean,
        senderName: String,
        content: JSONObject
    ): String {
        val actor = when {
            outgoing -> "你"
            senderName.isNotEmpty() -> senderName
            else -> ""
        }
        fun act(verb: String): String =
            if (actor.isEmpty()) verb else actor + verb
        return when (content.optString("@type")) {
            "messageCustomServiceAction" ->
                content.optString("text").ifEmpty { "服务消息" }
            "messageBasicGroupChatCreate" -> {
                val title = content.optString("title")
                if (title.isNotEmpty()) {
                    act("创建了群组「$title」")
                } else {
                    act("创建了群组")
                }
            }
            "messageSupergroupChatCreate" -> {
                val title = content.optString("title")
                if (title.isNotEmpty()) {
                    act("创建了「$title」")
                } else {
                    act("创建了这个对话")
                }
            }
            "messageChatAddMembers" -> {
                val ids = content.optJSONArray("member_user_ids")
                val count = ids?.length() ?: 0
                val names = ArrayList<String>()
                if (ids != null) {
                    for (i in 0 until ids.length()) {
                        val n = cachedUserName(ids.optLong(i))
                        if (n.isNotEmpty()) names.add(n)
                    }
                }
                when {
                    count == 0 -> act("邀请了新成员加入群组")
                    names.size == count && count <= 3 ->
                        act("邀请 ${names.joinToString("、")} 加入了群组")
                    else -> act("邀请了 $count 位成员加入群组")
                }
            }
            "messageChatJoinByLink" ->
                act("通过邀请链接加入了群组")
            "messageChatJoinByRequest" ->
                act("经管理员批准加入了群组")
            "messageChatDeleteMember" -> {
                val name = cachedUserName(content.optLong("user_id"))
                if (name.isNotEmpty()) {
                    act("移除了成员 $name")
                } else {
                    act("移除了一位成员")
                }
            }
            "messageChatChangePhoto" -> act("更新了群头像")
            "messageChatDeletePhoto" -> act("删除了群头像")
            "messageChatChangeTitle" -> {
                val title = content.optString("title")
                if (title.isNotEmpty()) {
                    act("把群名改成了「$title」")
                } else {
                    act("修改了群名")
                }
            }
            "messageChatSetBackground" -> act("更换了聊天背景")
            "messageChatSetTheme" -> {
                val theme = content.optJSONObject("theme")
                val emojiName = if (
                    theme?.optString("@type") == "chatThemeEmoji"
                ) {
                    theme.optString("name")
                } else {
                    ""
                }
                if (emojiName.isNotEmpty()) {
                    act("把聊天主题换成了 $emojiName")
                } else {
                    act("更换了聊天主题")
                }
            }
            "messageChatSetMessageAutoDeleteTime" -> {
                val sec = content.optInt("message_auto_delete_time")
                when {
                    sec == 0 -> act("关闭了消息自动删除")
                    sec == 86400 -> act("把消息自动删除设为 24 小时")
                    sec == 604800 -> act("把消息自动删除设为 7 天")
                    sec == 2678400 -> act("把消息自动删除设为 31 天")
                    sec % 86400 == 0 ->
                        act("把消息自动删除设为 ${sec / 86400} 天")
                    sec % 3600 == 0 ->
                        act("把消息自动删除设为 ${sec / 3600} 小时")
                    else -> act("把消息自动删除设为 $sec 秒")
                }
            }
            "messagePinMessage" -> act("置顶了一条消息")
            "messageChatUpgradeFrom", "messageChatUpgradeTo" ->
                "群组已升级为超级群组"
            "messageChatOwnerLeft" -> act("离开了群组")
            "messageChatOwnerChanged" -> {
                val name = cachedUserName(
                    content.optLong("new_owner_user_id")
                )
                if (name.isNotEmpty()) {
                    "新群主是 $name"
                } else {
                    act("转让了群主")
                }
            }
            "messageChatBoost" -> {
                val count = content.optInt("boost_count")
                if (count > 0) {
                    act("为本对话助力了 $count 次")
                } else {
                    act("为本对话助力了")
                }
            }
            "messageChatHasProtectedContentToggled" ->
                if (content.optBoolean("new_has_protected_content")) {
                    act("开启了内容保护（禁止转发与保存）")
                } else {
                    act("关闭了内容保护")
                }
            "messageChatHasProtectedContentDisableRequested" ->
                act("请求关闭内容保护")
            "messageChatAddedToCommunity" ->
                act("把群组加入了社区")
            "messageChatJoinFromCommunity" ->
                act("从社区加入了群组")
            "messageChatRemovedFromCommunity" ->
                act("把群组移出了社区")
            "messageForumTopicCreated" -> {
                val title = content.optString("name")
                if (title.isNotEmpty()) {
                    act("创建了话题「$title」")
                } else {
                    act("创建了话题")
                }
            }
            "messageForumTopicEdited" -> {
                val title = content.optString("name")
                if (title.isNotEmpty()) {
                    act("编辑了话题「$title」")
                } else {
                    act("编辑了话题")
                }
            }
            "messageForumTopicIsClosedToggled" ->
                if (content.optBoolean("is_closed")) {
                    act("关闭了话题")
                } else {
                    act("重新打开了话题")
                }
            "messageForumTopicIsHiddenToggled" ->
                if (content.optBoolean("is_hidden")) {
                    act("隐藏了话题")
                } else {
                    act("取消隐藏了话题")
                }
            "messageVideoChatStarted" -> act("开始了视频聊天")
            "messageVideoChatEnded" -> {
                val dur = content.optInt("duration")
                if (dur > 0) {
                    "视频聊天结束了（${formatServiceDuration(dur)}）"
                } else {
                    "视频聊天结束了"
                }
            }
            "messageVideoChatScheduled" -> {
                val at = content.optInt("start_date")
                if (at > 0) {
                    act("预定了视频聊天（${formatServiceDateTime(at)}）")
                } else {
                    act("预定了视频聊天")
                }
            }
            "messageInviteVideoChatParticipants" ->
                act("邀请了成员参加视频聊天")
            "messageGroupCall" -> when {
                content.optBoolean("is_active") -> "群组通话进行中"
                content.optBoolean("was_missed") -> "未接的群组通话"
                content.optInt("duration") > 0 ->
                    "群组通话结束了（" +
                        formatServiceDuration(
                            content.optInt("duration")
                        ) + "）"
                else -> "群组通话"
            }
            "messageCall" -> {
                // A call record, not a call feature: type,
                // outcome and duration are all in the
                // payload (unique_id is never shown).
                val word = if (content.optBoolean("is_video")) {
                    "视频通话"
                } else {
                    "语音通话"
                }
                val reason = content.optJSONObject("discard_reason")
                    ?.optString("@type") ?: ""
                val dur = content.optInt("duration")
                when {
                    reason == "callDiscardReasonMissed" ->
                        "未接的$word"
                    reason == "callDiscardReasonDeclined" ->
                        "被拒绝的$word"
                    dur > 0 ->
                        "$word · ${formatServiceDuration(dur)}"
                    else -> word
                }
            }
            "messageGameScore" ->
                act("在游戏中获得了 ${content.optInt("score")} 分")
            "messagePaymentSuccessful",
            "messagePaymentSuccessfulBot" -> {
                val money = formatMoney(
                    content.optString("currency"),
                    content.optLong("total_amount")
                )
                val name = content.optString("invoice_name")
                if (name.isNotEmpty()) {
                    "付款成功：$money（$name）"
                } else {
                    "付款成功：$money"
                }
            }
            "messagePaymentRefunded" ->
                "已退款：" + formatMoney(
                    content.optString("currency"),
                    content.optLong("total_amount")
                )
            "messagePremiumGiftCode" -> {
                val months = content.optInt("month_count")
                val days = content.optInt("day_count")
                val period = when {
                    months > 0 -> "$months 个月"
                    days > 0 -> "$days 天"
                    else -> ""
                }
                val base = if (period.isNotEmpty()) {
                    "一份 $period 的 Premium 礼品码"
                } else {
                    "一份 Premium 礼品码"
                }
                if (content.optBoolean("is_unclaimed")) {
                    "$base（尚未领取）"
                } else {
                    base
                }
            }
            "messageGiftedPremium" -> {
                val months = content.optInt("month_count")
                val days = content.optInt("day_count")
                val period = when {
                    months > 0 -> "$months 个月"
                    days > 0 -> "$days 天"
                    else -> ""
                }
                if (period.isNotEmpty()) {
                    act("赠送了 $period 的 Telegram Premium")
                } else {
                    act("赠送了 Telegram Premium")
                }
            }
            "messageGiftedStars" ->
                act("赠送了 ⭐ ${content.optLong("star_count")} 星星")
            "messageGiftedGrams" -> act("赠送了 TON 代币")
            "messageGift" -> act("送出了一份礼物")
            "messageUpgradedGift" -> act("送出了一份升级礼物")
            "messageRefundedUpgradedGift" -> "一份升级礼物已退款"
            "messageUpgradedGiftPurchaseOffer" ->
                act("发出了购买升级礼物的报价")
            "messageUpgradedGiftPurchaseOfferRejected" ->
                "升级礼物的购买报价被拒绝了"
            "messageGiveawayCreated" -> {
                val stars = content.optLong("star_count")
                if (stars > 0) {
                    act("创建了抽奖（奖品池 ⭐ $stars 星星）")
                } else {
                    act("创建了抽奖")
                }
            }
            "messageGiveawayCompleted" -> {
                val winners = content.optInt("winner_count")
                val unclaimed =
                    content.optInt("unclaimed_prize_count")
                val head = "抽奖已结束，共 $winners 位中奖者"
                if (unclaimed > 0) {
                    "$head，$unclaimed 份奖品无人领取"
                } else {
                    head
                }
            }
            "messageGiveawayPrizeStars" -> {
                val stars = content.optLong("star_count")
                if (content.optBoolean("is_unclaimed")) {
                    "抽奖奖品：⭐ $stars 星星（尚未领取）"
                } else {
                    "抽奖奖品：⭐ $stars 星星"
                }
            }
            "messageChecklistTasksAdded" -> {
                val count = content.optJSONArray("tasks")
                    ?.length() ?: 0
                if (count > 0) {
                    act("在清单里添加了 $count 项")
                } else {
                    act("在清单里添加了新项")
                }
            }
            "messageChecklistTasksDone" -> {
                val done = content.optJSONArray(
                    "marked_as_done_task_ids"
                )?.length() ?: 0
                val undone = content.optJSONArray(
                    "marked_as_not_done_task_ids"
                )?.length() ?: 0
                when {
                    done > 0 && undone > 0 ->
                        act("在清单里完成了 $done 项、取消了 $undone 项")
                    done > 0 -> act("在清单里完成了 $done 项")
                    undone > 0 ->
                        act("在清单里取消了 $undone 项的完成标记")
                    else -> act("更新了清单")
                }
            }
            "messagePollOptionAdded" -> {
                val text = content.optJSONObject("text")
                    ?.optString("text") ?: ""
                if (text.isNotEmpty()) {
                    act("给投票添加了选项「$text」")
                } else {
                    act("给投票添加了选项")
                }
            }
            "messagePollOptionDeleted" -> {
                val text = content.optJSONObject("text")
                    ?.optString("text") ?: ""
                if (text.isNotEmpty()) {
                    act("从投票删除了选项「$text」")
                } else {
                    act("从投票删除了选项")
                }
            }
            "messageBotWriteAccessAllowed" ->
                "你允许了这个机器人向你发送消息"
            "messageWebAppDataReceived" -> "收到小程序发来的数据"
            "messageWebAppDataSent" -> act("通过小程序发送了数据")
            "messagePassportDataReceived" -> "收到了护照资料"
            "messagePassportDataSent" -> "发送了护照资料"
            "messageProximityAlertTriggered" ->
                "位置提醒：对方已进入 " +
                    "${content.optInt("distance")} 米范围"
            "messageScreenshotTaken" -> act("截了屏")
            "messageContactRegistered" -> act("刚刚加入了 Telegram")
            "messageExpiredPhoto" -> "照片已过期"
            "messageExpiredVideo" -> "视频已过期"
            "messageExpiredVideoNote" -> "视频消息已过期"
            "messageExpiredVoiceNote" -> "语音已过期"
            "messageStory" -> act("分享了一条动态")
            "messageSuggestBirthdate" -> {
                val bd = content.optJSONObject("birthdate")
                if (bd != null) {
                    act(
                        "建议了生日：${bd.optInt("month")}月" +
                            "${bd.optInt("day")}日"
                    )
                } else {
                    act("建议了生日")
                }
            }
            "messageManagedBotCreated" -> act("创建了一个机器人")
            "messageDirectMessagePriceChanged" ->
                if (content.optBoolean("is_enabled")) {
                    "频道私信价格已设为 ⭐ " +
                        content.optLong("paid_message_star_count")
                } else {
                    "频道私信已改为免费"
                }
            "messagePaidMessagePriceChanged" -> {
                val count = content.optLong(
                    "paid_message_star_count"
                )
                if (count > 0) {
                    "付费消息价格已设为 ⭐ $count"
                } else {
                    "付费消息已关闭"
                }
            }
            "messagePaidMessagesRefunded" -> "付费消息已退款"
            "messageSuggestedPostApproved" -> "一条投稿已被通过"
            "messageSuggestedPostDeclined" -> "一条投稿已被拒绝"
            "messageSuggestedPostApprovalFailed" -> "一条投稿审核失败"
            "messageSuggestedPostPaid" -> "一条投稿已付费发布"
            "messageSuggestedPostRefunded" -> "一条投稿已退款"
            "messageTonConnectRequest" -> {
                val dapp = content.optString("dapp_name")
                if (dapp.isNotEmpty()) {
                    "TON 钱包连接请求：$dapp"
                } else {
                    "TON 钱包连接请求"
                }
            }
            "messageTonWalletTransfer" -> "一笔 TON 钱包转账"
            "messageUnpaidMessagesAllowedByLink" ->
                "已允许通过链接加入的用户免付费发消息"
            else -> "服务消息"
        }
    }

    /** Ask TDLib for the current user's own giveaway
     *  status (v0.9.37, D3): participation is automatic,
     *  so this is a query, never a join. The answer is
     *  mapped to one plain-language paragraph; errors say
     *  so plainly too. */
    fun loadGiveawayInfo(
        chatId: Long,
        messageId: Long,
        onResult: (String) -> Unit
    ) {
        val query = TdClient.request("getGiveawayInfo").apply {
            put("chat_id", chatId)
            put("message_id", messageId)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                // Plain language only (v0.9.60, E3): the raw
                // server sentence used to ride along after
                // the code — English gibberish to a user. The
                // numeric code stays for support screenshots.
                onResult(
                    "查不到这次抽奖的状态，再试一次。（错误码 " +
                        "${response.optInt("code")}）"
                )
                return@sendRaw
            }
            onResult(giveawayInfoText(response))
        }
    }

    private fun giveawayInfoText(info: JSONObject): String {
        return when (info.optString("@type")) {
            "giveawayInfoOngoing" -> {
                val status = info.optJSONObject("status")
                    ?.optString("@type") ?: ""
                val head = when (status) {
                    "giveawayParticipantStatusEligible" ->
                        "你符合这次抽奖的参加条件，已经自动参加，" +
                            "不用再做任何操作。"
                    "giveawayParticipantStatusParticipating" ->
                        "你已经在这次抽奖里了，等开奖就行。"
                    "giveawayParticipantStatusAlreadyWasMember" ->
                        "这次抽奖只限活动开始后加入的新成员，" +
                            "你那时已经在群里了，所以参加不了。"
                    "giveawayParticipantStatusAdministrator" ->
                        "你是这个对话的管理员，按规则参加不了" +
                            "这次抽奖。"
                    "giveawayParticipantStatusDisallowedCountry" ->
                        "这次抽奖限制了参加的国家或地区，" +
                            "你的号码不在范围内。"
                    else -> "这次抽奖还在进行中。"
                }
                if (info.optBoolean("is_ended")) {
                    head + "\n活动已经结束，正在准备开奖结果。"
                } else {
                    head
                }
            }
            "giveawayInfoCompleted" -> {
                val sb = StringBuilder("这次抽奖已经开奖了。")
                if (info.optBoolean("was_refunded")) {
                    sb.append("\n活动被取消，费用已退回。")
                }
                if (info.optBoolean("is_winner")) {
                    sb.append("\n🎉 你中奖了！")
                    val code = info.optString("gift_code")
                    val stars = info.optLong("won_star_count")
                    if (code.isNotEmpty()) {
                        sb.append("\n你的 Premium 礼品码：$code")
                    }
                    if (stars > 0) {
                        sb.append("\n你赢得了 ⭐ $stars 星星。")
                    }
                } else {
                    sb.append("\n你这次没有中奖。")
                }
                sb.toString()
            }
            else -> "这次抽奖的状态暂时读不出来。"
        }
    }

    /** One paidMedia entry of a messagePaidMedia (v0.9.35,
     *  B1). Purchased photos download like plain photos;
     *  purchased videos only fetch their still image (the
     *  video file itself downloads on tap, same as plain
     *  videos); previews carry no file at all. */
    private fun parsePaidMediaEntry(entry: JSONObject): PaidMediaUi {
        return when (entry.optString("@type")) {
            "paidMediaPhoto" -> {
                val photo = entry.optJSONObject("photo")
                val size = pickPhotoSize(photo?.optJSONArray("sizes"))
                val file = size?.optJSONObject("photo")
                val fileId = file?.optInt("id", 0) ?: 0
                val path = downloadedPath(file)
                if (fileId != 0 && path == null) {
                    requestDownload(fileId, 16)
                }
                PaidMediaUi(
                    kind = "photo",
                    photoFileId = fileId,
                    photoPath = path,
                    photoWidth = size?.optInt("width", 0) ?: 0,
                    photoHeight = size?.optInt("height", 0) ?: 0
                )
            }
            "paidMediaVideo" -> {
                val video = entry.optJSONObject("video")
                val file = video?.optJSONObject("video")
                // Still image: the sender-provided cover
                // photo when present, else the video's own
                // thumbnail.
                val coverSize = pickPhotoSize(
                    entry.optJSONObject("cover")
                        ?.optJSONArray("sizes")
                )
                val coverFile = coverSize?.optJSONObject("photo")
                val thumbFile = video?.optJSONObject("thumbnail")
                    ?.optJSONObject("file")
                val stillFile = coverFile ?: thumbFile
                val stillId = stillFile?.optInt("id", 0) ?: 0
                val stillPath = downloadedPath(stillFile)
                if (stillId != 0 && stillPath == null) {
                    requestDownload(stillId, 1)
                }
                PaidMediaUi(
                    kind = "video",
                    videoFileId = file?.optInt("id", 0) ?: 0,
                    videoPath = downloadedPath(file),
                    videoThumbFileId = stillId,
                    videoThumbPath = stillPath,
                    videoMime = video?.optString("mime_type") ?: "",
                    videoSize = fileSizeOf(file),
                    miniThumbData = video?.optJSONObject("minithumbnail")
                        ?.optString("data") ?: "",
                    durationSec = video?.optInt("duration", 0) ?: 0,
                    width = video?.optInt("width", 0) ?: 0,
                    height = video?.optInt("height", 0) ?: 0
                )
            }
            "paidMediaPreview" -> PaidMediaUi(
                kind = "preview",
                miniThumbData = entry.optJSONObject("minithumbnail")
                    ?.optString("data") ?: "",
                durationSec = entry.optInt("duration", 0),
                width = entry.optInt("width", 0),
                height = entry.optInt("height", 0)
            )
            else -> PaidMediaUi(kind = "unsupported")
        }
    }

    private fun requestDownload(fileId: Int, priority: Int) {
        TdRepository.sendRaw(TdClient.request("downloadFile").apply {
            put("file_id", fileId)
            put("priority", priority)
            put("offset", 0L)
            put("limit", 0L)
            put("synchronous", false)
        }) {}
    }

    /** The largest photo SIZE inside a linkPreview's type
     *  object, when the type carries one (article/app/photo/...
     *  types all expose a `photo` photo object). The size —
     *  not just its file — is returned so callers also know
     *  the picture's real width: a 48 px site icon and a
     *  1200 px article photo must not render the same way
     *  (v0.9.54, Y3). */
    private fun lpPhotoFile(lp: JSONObject?): JSONObject? {
        val photo = lp?.optJSONObject("type")?.optJSONObject("photo") ?: return null
        val sizes = photo.optJSONArray("sizes") ?: return null
        var best: JSONObject? = null
        for (i in 0 until sizes.length()) {
            val s = sizes.optJSONObject(i) ?: continue
            if (best == null || s.optInt("width") > best.optInt("width")) best = s
        }
        return best
    }

    /** Flatten a poll object (fields verified in td_api.tl: poll,
     *  pollOption, pollTypeQuiz). Shared with the thread / forum
     *  stores since v0.9.1 so their updatePoll handling renders
     *  identically. */
    fun parsePoll(poll: JSONObject): PollUi {
        val type = poll.optJSONObject("type")
        val isQuiz = type?.optString("@type") == "pollTypeQuiz"
        val correctIds = HashSet<Int>()
        var explanation = ""
        if (isQuiz && type != null) {
            val arr = type.optJSONArray("correct_option_ids")
            if (arr != null) {
                for (i in 0 until arr.length()) correctIds.add(arr.optInt(i))
            }
            explanation = type.optJSONObject("explanation")
                ?.optString("text") ?: ""
        }
        val options = ArrayList<PollOptionUi>()
        val arr = poll.optJSONArray("options") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            options.add(
                PollOptionUi(
                    text = o.optJSONObject("text")?.optString("text") ?: "",
                    voterCount = o.optInt("voter_count"),
                    percentage = o.optInt("vote_percentage"),
                    chosen = o.optBoolean("is_chosen"),
                    isCorrect = correctIds.contains(i)
                )
            )
        }
        return PollUi(
            question = poll.optJSONObject("question")?.optString("text") ?: "",
            options = options,
            totalVoters = poll.optInt("total_voter_count"),
            isClosed = poll.optBoolean("is_closed"),
            isAnonymous = poll.optBoolean("is_anonymous"),
            allowsMultiple = poll.optBoolean("allows_multiple_answers"),
            isQuiz = isQuiz,
            explanation = explanation,
            canGetVoters = poll.optBoolean("can_get_voters")
        )
    }

    /** Vote (or retract, with an empty option list) via
     *  setPollAnswer; results refresh through updatePoll. */
    fun setPollAnswer(item: MsgItem, optionIds: List<Int>) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val ids = JSONArray()
        for (id in optionIds) ids.put(id)
        val query = TdClient.request("setPollAnswer").apply {
            put("chat_id", chatId)
            put("message_id", item.id)
            put("option_ids", ids)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = sendErrorText(response)
            }
        }
    }

    /** Send a checklist (v0.9.24, Q7): inputMessageChecklist
     *  with an inputChecklist (title + tasks, ids assigned
     *  here as 1..n). Sending checklists is Premium-gated by
     *  the server; a refusal is mapped by sendErrorText to a
     *  plain "needs Premium" sentence. Post-send editing
     *  arrived in v0.9.26 (S2): [addChecklistTasks] and
     *  [editChecklistMeta]; toggling is [markChecklistTask]. */
    fun sendChecklist(
        title: String,
        tasks: List<String>,
        othersCanMark: Boolean
    ) {
        val taskArray = JSONArray()
        tasks.forEachIndexed { index, text ->
            taskArray.put(JSONObject().apply {
                put("@type", "inputChecklistTask")
                put("id", index + 1)
                put("text", formattedText(text))
            })
        }
        val content = JSONObject().apply {
            put("@type", "inputMessageChecklist")
            put("checklist", JSONObject().apply {
                put("@type", "inputChecklist")
                put("title", formattedText(title))
                put("tasks", taskArray)
                put("others_can_add_tasks", false)
                put("others_can_mark_tasks_as_done", othersCanMark)
            })
        }
        sendContent(content)
    }

    /** Tick or untick one checklist task (v0.9.24, Q7):
     *  markChecklistTasksAsDone with exactly one of the two
     *  id lists filled. The fresh task state arrives with
     *  updateMessageContent and re-renders the bubble. */
    fun markChecklistTask(item: MsgItem, task: ChecklistTaskUi) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val done = JSONArray()
        val notDone = JSONArray()
        if (task.completed) notDone.put(task.id) else done.put(task.id)
        val query = TdClient.request("markChecklistTasksAsDone").apply {
            put("chat_id", chatId)
            put("message_id", item.id)
            put("marked_as_done_task_ids", done)
            put("marked_as_not_done_task_ids", notDone)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = sendErrorText(response)
            }
        }
    }

    /** Append tasks to a sent checklist (v0.9.26, S2):
     *  addChecklistTasks with fresh ids continuing after the
     *  current maximum. The server only allows this when the
     *  checklist's can_add_tasks flag is set for this account
     *  (Premium-gated); a refusal lands in sendError as plain
     *  language. The appended tasks arrive with
     *  updateMessageContent and re-render the bubble. */
    fun addChecklistTasks(item: MsgItem, newTasks: List<String>) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val cleaned = newTasks.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return
        var nextId = (item.checklistTasks.maxOfOrNull { it.id } ?: 0) + 1
        val taskArray = JSONArray()
        for (text in cleaned) {
            taskArray.put(JSONObject().apply {
                put("@type", "inputChecklistTask")
                put("id", nextId)
                put("text", formattedText(text))
            })
            nextId++
        }
        val query = TdClient.request("addChecklistTasks").apply {
            put("chat_id", chatId)
            put("message_id", item.id)
            put("tasks", taskArray)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = sendErrorText(response)
            }
        }
    }

    /** Edit a sent checklist's title and sharing settings
     *  (v0.9.26, S2): editMessageChecklist replaces the whole
     *  inputChecklist, so the existing tasks are sent back
     *  with their ORIGINAL ids and plain texts — task ids are
     *  what completion state hangs on, so ticks survive the
     *  edit. Only the sender can do this; a server refusal
     *  lands in sendError as plain language. */
    fun editChecklistMeta(
        item: MsgItem,
        title: String,
        othersCanAdd: Boolean,
        othersCanMark: Boolean
    ) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val taskArray = JSONArray()
        for (task in item.checklistTasks) {
            taskArray.put(JSONObject().apply {
                put("@type", "inputChecklistTask")
                put("id", task.id)
                put("text", formattedText(task.text))
            })
        }
        val query = TdClient.request("editMessageChecklist").apply {
            put("chat_id", chatId)
            put("message_id", item.id)
            put("reply_markup", JSONObject.NULL)
            put("checklist", JSONObject().apply {
                put("@type", "inputChecklist")
                put("title", formattedText(title.trim()))
                put("tasks", taskArray)
                put("others_can_add_tasks", othersCanAdd)
                put("others_can_mark_tasks_as_done", othersCanMark)
            })
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = sendErrorText(response)
            }
        }
    }

    /** Send a sticker already known to TDLib (from the recent /
     *  favorite lists) by its local file id — inputFileId is the
     *  documented way to reference an existing file. */
    fun sendStickerFile(fileId: Int, emoji: String) {
        val chatId = currentChatId
        if (chatId == 0L || fileId == 0) return
        val replyTo = replyToJson()
        clearReplyTarget()
        val query = TdClient.request("sendMessage").apply {
            put("chat_id", chatId)
            if (replyTo != null) put("reply_to", replyTo)
            put("input_message_content", JSONObject().apply {
                put("@type", "inputMessageSticker")
                put("sticker", JSONObject().apply {
                    put("@type", "inputSticker")
                    put("sticker", JSONObject().apply {
                        put("@type", "inputFileId")
                        put("id", fileId)
                    })
                    put("thumbnail", JSONObject.NULL)
                    put("width", 0)
                    put("height", 0)
                })
                put("emoji", emoji)
            })
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = sendErrorText(response)
            } else {
                val item = parseMessage(response)
                byId[item.id] = item
                publish()
            }
        }
    }

    /** Send a saved GIF (animation) by its local file id. */
    fun sendAnimationFile(fileId: Int, duration: Int, width: Int, height: Int) {
        val chatId = currentChatId
        if (chatId == 0L || fileId == 0) return
        val replyTo = replyToJson()
        clearReplyTarget()
        val query = TdClient.request("sendMessage").apply {
            put("chat_id", chatId)
            if (replyTo != null) put("reply_to", replyTo)
            put("input_message_content", JSONObject().apply {
                put("@type", "inputMessageAnimation")
                put("animation", JSONObject().apply {
                    put("@type", "inputAnimation")
                    put("animation", JSONObject().apply {
                        put("@type", "inputFileId")
                        put("id", fileId)
                    })
                    put("thumbnail", JSONObject.NULL)
                    put("added_sticker_file_ids", JSONArray())
                    put("duration", duration)
                    put("width", width)
                    put("height", height)
                })
                put("caption", formattedText(""))
                put("show_caption_above_media", false)
                put("has_spoiler", false)
            })
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = sendErrorText(response)
            } else {
                val item = parseMessage(response)
                byId[item.id] = item
                publish()
            }
        }
    }

    /** Send a locally recorded voice note (OGG/Opus file).
     *  [waveform] is the base64 of TDLib's 5-bit packed amplitudes;
     *  a wrong waveform only renders oddly, never blocks sending. */
    fun sendVoiceNote(path: String, durationSec: Int, waveform: String) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val replyTo = replyToJson()
        clearReplyTarget()
        val query = TdClient.request("sendMessage").apply {
            put("chat_id", chatId)
            if (replyTo != null) put("reply_to", replyTo)
            put("input_message_content", JSONObject().apply {
                put("@type", "inputMessageVoiceNote")
                put("voice_note", JSONObject().apply {
                    put("@type", "inputVoiceNote")
                    put("voice_note", JSONObject().apply {
                        put("@type", "inputFileLocal")
                        put("path", path)
                    })
                    put("duration", durationSec)
                    put("waveform", waveform)
                })
                put("caption", formattedText(""))
                put("self_destruct_type", JSONObject.NULL)
            })
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = sendErrorText(response)
            } else {
                val item = parseMessage(response)
                byId[item.id] = item
                publish()
            }
        }
    }

    private fun downloadedPath(file: JSONObject?): String? {
        val local = file?.optJSONObject("local") ?: return null
        return if (local.optBoolean("is_downloading_completed")) {
            local.optString("path").ifEmpty { null }
        } else {
            null
        }
    }

    private fun fileSizeOf(file: JSONObject?): Long {
        if (file == null) return 0L
        val size = file.optLong("size", 0L)
        return if (size > 0) size else file.optLong("expected_size", 0L)
    }

    /** Start (or restart) downloading a document/video/voice file;
     *  progress and completion arrive through updateFile. */
    fun downloadFileFor(item: MsgItem) {
        if (item.fileId == 0 || item.filePath != null) return
        // User-started file downloads go through the download
        // center's queue (v0.9.23, P2): the tap counts as a
        // boost — this file jumps the queue. The message's
        // coordinates ride along so the queue can enter the
        // file into the persistent download list (v0.9.27, T2).
        DownloadStore.enqueue(
            item.fileId, boost = true,
            chatId = currentChatId, messageId = item.id
        )
        val existing = byId[item.id] ?: return
        byId[item.id] = existing.copy(fileDownloading = true)
        publish()
    }

    /** The input content for a picked file (v0.9.1: shared with
     *  the thread / forum stores): video MIME goes as a video
     *  message, .ogg/.opus audio as a voice note, everything
     *  else as a document. Input types verified in td_api.tl
     *  (inputDocument / inputVideo / inputVoiceNote and their
     *  input* wrappers). */
    fun fileContent(path: String, mimeType: String, fileName: String): JSONObject {
        val localFile = JSONObject().apply {
            put("@type", "inputFileLocal")
            put("path", path)
        }
        return when {
            mimeType.startsWith("video/") -> JSONObject().apply {
                put("@type", "inputMessageVideo")
                put("video", JSONObject().apply {
                    put("@type", "inputVideo")
                    put("video", localFile)
                    put("thumbnail", JSONObject.NULL)
                    put("cover", JSONObject.NULL)
                    put("start_timestamp", 0)
                    put("added_sticker_file_ids", JSONArray())
                    put("duration", 0)
                    put("width", 0)
                    put("height", 0)
                    put("supports_streaming", true)
                })
                put("caption", formattedText(""))
                put("show_caption_above_media", false)
                put("self_destruct_type", JSONObject.NULL)
                put("has_spoiler", false)
            }
            mimeType == "audio/ogg" || fileName.endsWith(".opus", true) ||
                fileName.endsWith(".ogg", true) -> JSONObject().apply {
                put("@type", "inputMessageVoiceNote")
                put("voice_note", JSONObject().apply {
                    put("@type", "inputVoiceNote")
                    put("voice_note", localFile)
                    put("duration", 0)
                    put("waveform", "")
                })
                put("caption", formattedText(""))
                put("self_destruct_type", JSONObject.NULL)
            }
            else -> JSONObject().apply {
                put("@type", "inputMessageDocument")
                put("document", JSONObject().apply {
                    put("@type", "inputDocument")
                    put("document", localFile)
                    put("thumbnail", JSONObject.NULL)
                    put("disable_content_type_detection", false)
                })
                put("caption", formattedText(""))
            }
        }
    }

    /** Send externally shared content into an arbitrary chat
     *  (v0.9.52, W5): the ACTION_SEND intake's landing call.
     *  Unlike the composer sends it names its chat explicitly
     *  (no chat needs to be open) and reports through [onDone]
     *  once every part has settled, with the first failure's
     *  plain-language reason when any part failed. A single
     *  image plus text rides as that photo's caption, like
     *  the official share flow; anything else sends the files
     *  one by one and the text as its own message. Images go
     *  through photoContent, everything else through
     *  fileContent's video/voice/document split. */
    fun sendSharedTo(
        chatId: Long,
        text: String,
        files: List<SharedFile>,
        onDone: (String?) -> Unit
    ) {
        val contents = ArrayList<JSONObject>()
        if (files.size == 1 &&
            files[0].mimeType.startsWith("image/") &&
            text.isNotEmpty()
        ) {
            contents.add(photoContent(files[0].path, text))
        } else {
            for (file in files) {
                contents.add(
                    if (file.mimeType.startsWith("image/")) {
                        photoContent(file.path, "")
                    } else {
                        fileContent(file.path, file.mimeType, file.fileName)
                    }
                )
            }
            if (text.isNotEmpty()) {
                contents.add(JSONObject().apply {
                    put("@type", "inputMessageText")
                    put("text", formattedText(text))
                    put("link_preview_options", JSONObject.NULL)
                    put("clear_draft", false)
                })
            }
        }
        if (contents.isEmpty()) {
            onDone("没有可发送的内容。")
            return
        }
        var remaining = contents.size
        var failure: String? = null
        for (content in contents) {
            val query = TdClient.request("sendMessage").apply {
                put("chat_id", chatId)
                put("input_message_content", content)
            }
            TdRepository.sendRaw(query) { response ->
                if (TdClient.isError(response) && failure == null) {
                    failure = sendErrorText(response)
                }
                remaining--
                if (remaining == 0) onDone(failure)
            }
        }
    }

    /** Send a picked file into the currently open chat. */
    fun sendFile(path: String, mimeType: String, fileName: String) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val replyTo = replyToJson()
        clearReplyTarget()
        val query = TdClient.request("sendMessage").apply {
            put("chat_id", chatId)
            if (replyTo != null) put("reply_to", replyTo)
            put(
                "input_message_content",
                fileContent(path, mimeType, fileName)
            )
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = sendErrorText(response)
            } else {
                val item = parseMessage(response)
                byId[item.id] = item
                publish()
            }
        }
    }

    /** Flatten messageInteractionInfo.reactions (verified in
     *  td_api.tl: messageReactions -> messageReaction{type,
     *  total_count, is_chosen}); only plain emoji reactions are
     *  rendered, custom-emoji ones are skipped for now. */
    private fun reactionsOf(interactionInfo: JSONObject?): List<ReactionChip> {
        val reactions = interactionInfo?.optJSONObject("reactions")
            ?.optJSONArray("reactions") ?: return emptyList()
        val out = ArrayList<ReactionChip>()
        for (i in 0 until reactions.length()) {
            val reaction = reactions.optJSONObject(i) ?: continue
            val type = reaction.optJSONObject("type") ?: continue
            if (type.optString("@type") != "reactionTypeEmoji") continue
            val emoji = type.optString("emoji")
            if (emoji.isEmpty()) continue
            out.add(
                ReactionChip(
                    emoji = emoji,
                    count = reaction.optInt("total_count"),
                    chosen = reaction.optBoolean("is_chosen")
                )
            )
        }
        return out
    }

    /** Flatten a reply_markup object into button rows. Only
     *  replyMarkupInlineKeyboard carries tappable buttons;
     *  reply-markup keyboards / force-reply are input-area
     *  concepts this app does not render yet. Button types
     *  verified in td_api.tl (inlineKeyboardButtonType*). */
    /** Parse a message's reply_markup into the chat-level
     *  reply-keyboard state (W2). Inline keyboards return
     *  Hidden here - they render on their own message. */
    private fun parseReplyKb(markup: JSONObject?, sourceId: Long): ReplyKb {
        if (markup == null) return ReplyKb.Hidden
        return when (markup.optString("@type")) {
            "replyMarkupShowKeyboard" -> {
                val rowsArr = markup.optJSONArray("rows")
                    ?: return ReplyKb.Hidden
                val rows = ArrayList<List<KbButton>>()
                for (r in 0 until rowsArr.length()) {
                    val rowArr = rowsArr.optJSONArray(r) ?: continue
                    val row = ArrayList<KbButton>()
                    for (b in 0 until rowArr.length()) {
                        val btn = rowArr.optJSONObject(b) ?: continue
                        val text = btn.optString("text")
                        if (text.isEmpty()) continue
                        val type = btn.optJSONObject("type")
                        row.add(
                            when (type?.optString("@type")) {
                                "keyboardButtonTypeText" ->
                                    KbButton(text, "text")
                                "keyboardButtonTypeWebApp" ->
                                    KbButton(
                                        text, "webapp",
                                        url = type.optString("url")
                                    )
                                else -> KbButton(text, "other")
                            }
                        )
                    }
                    if (row.isNotEmpty()) rows.add(row)
                }
                if (rows.isEmpty()) {
                    ReplyKb.Hidden
                } else {
                    ReplyKb.Shown(
                        rows = rows,
                        oneTime = markup.optBoolean("one_time"),
                        placeholder = markup.optString("input_field_placeholder"),
                        forceReply = markup.optBoolean("force_reply"),
                        sourceMessageId = sourceId
                    )
                }
            }
            "replyMarkupForceReply" -> ReplyKb.ForceReply(
                markup.optString("input_field_placeholder")
            )
            else -> ReplyKb.Hidden
        }
    }

    private fun parseButtons(markup: JSONObject?): List<List<InlineBtn>> {
        if (markup == null) return emptyList()
        if (markup.optString("@type") != "replyMarkupInlineKeyboard") {
            return emptyList()
        }
        val rows = markup.optJSONArray("rows") ?: return emptyList()
        val out = ArrayList<List<InlineBtn>>()
        for (r in 0 until rows.length()) {
            val rowArr = rows.optJSONArray(r) ?: continue
            val row = ArrayList<InlineBtn>()
            for (b in 0 until rowArr.length()) {
                val btn = rowArr.optJSONObject(b) ?: continue
                val type = btn.optJSONObject("type") ?: continue
                val text = btn.optString("text")
                if (text.isEmpty()) continue
                row.add(
                    when (type.optString("@type")) {
                        "inlineKeyboardButtonTypeUrl" -> InlineBtn(
                            text, "url", url = type.optString("url")
                        )
                        "inlineKeyboardButtonTypeLoginUrl" -> InlineBtn(
                            text, "url", url = type.optString("url")
                        )
                        // Web App button (v0.9.36, C1): the
                        // URL is only the input to openWebApp,
                        // never opened directly.
                        "inlineKeyboardButtonTypeWebApp" -> InlineBtn(
                            text, "webapp", url = type.optString("url")
                        )
                        "inlineKeyboardButtonTypeCallback" -> InlineBtn(
                            text, "callback", data = type.optString("data")
                        )
                        "inlineKeyboardButtonTypeCallbackWithPassword" ->
                            InlineBtn(
                                text, "callbackPw",
                                data = type.optString("data")
                            )
                        "inlineKeyboardButtonTypeSwitchInline" -> InlineBtn(
                            text, "switch", query = type.optString("query")
                        )
                        "inlineKeyboardButtonTypeCopyText" -> InlineBtn(
                            text, "copy", copyText = type.optString("text")
                        )
                        "inlineKeyboardButtonTypeUser" -> InlineBtn(
                            text, "user", userId = type.optLong("user_id")
                        )
                        "inlineKeyboardButtonTypeCallbackGame" ->
                            InlineBtn(text, "game")
                        "inlineKeyboardButtonTypeBuy" ->
                            InlineBtn(text, "buy")
                        else -> InlineBtn(text, "disabled")
                    }
                )
            }
            if (row.isNotEmpty()) out.add(row)
        }
        return out
    }

    /** Press a callback button (getCallbackQueryAnswer, verified
     *  in td_api.tl). The bot's answer comes back as
     *  callbackQueryAnswer{text, show_alert, url}; the UI decides
     *  how to present it. [password] is only for
     *  callbackWithPassword buttons (the account's 2FA password,
     *  wrapped in callbackQueryPayloadDataWithPassword). Works
     *  for any chat, so thread/forum pages reuse it too. */
    fun answerCallback(
        chatId: Long,
        messageId: Long,
        data: String,
        password: String?,
        onAnswer: (text: String, showAlert: Boolean, url: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val payload = JSONObject().apply {
            if (password != null) {
                put("@type", "callbackQueryPayloadDataWithPassword")
                put("password", password)
            } else {
                put("@type", "callbackQueryPayloadData")
            }
            put("data", data)
        }
        val query = TdClient.request("getCallbackQueryAnswer").apply {
            put("chat_id", chatId)
            put("message_id", messageId)
            put("payload", payload)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                onError(sendErrorText(response))
            } else {
                onAnswer(
                    response.optString("text"),
                    response.optBoolean("show_alert"),
                    response.optString("url")
                )
            }
        }
    }

    /** Launch a game through the message's own game button
     *  (v0.9.14, U5): getCallbackQueryAnswer with a
     *  callbackQueryPayloadGame payload (verified in
     *  td_api.tl); the bot's answer (usually a URL) is handled
     *  by the same onAnswer path as data callbacks. */
    fun answerCallbackGame(
        chatId: Long,
        messageId: Long,
        shortName: String,
        onAnswer: (text: String, showAlert: Boolean, url: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val payload = JSONObject().apply {
            put("@type", "callbackQueryPayloadGame")
            put("game_short_name", shortName)
        }
        val query = TdClient.request("getCallbackQueryAnswer").apply {
            put("chat_id", chatId)
            put("message_id", messageId)
            put("payload", payload)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                onError(sendErrorText(response))
            } else {
                onAnswer(
                    response.optString("text"),
                    response.optBoolean("show_alert"),
                    response.optString("url")
                )
            }
        }
    }

    /** Send a recorded round video as a video note
     *  (v0.9.14, U4): inputMessageVideoNote / inputVideoNote
     *  verified in td_api.tl; [length] is the (square) side
     *  the other clients crop to — the capture's smaller
     *  dimension. */
    fun sendVideoNote(path: String, durationSec: Int, length: Int) {
        val chatId = currentChatId
        if (chatId == 0L) return
        val replyTo = replyToJson()
        clearReplyTarget()
        val content = JSONObject().apply {
            put("@type", "inputMessageVideoNote")
            put("video_note", JSONObject().apply {
                put("@type", "inputVideoNote")
                put("video_note", JSONObject().apply {
                    put("@type", "inputFileLocal")
                    put("path", path)
                })
                put("thumbnail", JSONObject.NULL)
                put("duration", durationSec)
                put("length", length)
            })
            put("self_destruct_type", JSONObject.NULL)
        }
        val query = TdClient.request("sendMessage").apply {
            put("chat_id", chatId)
            if (replyTo != null) put("reply_to", replyTo)
            put("input_message_content", content)
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                _sendError.value = sendErrorText(response)
            } else {
                val item = parseMessage(response)
                byId[item.id] = item
                publish()
            }
        }
    }

    val COMMON_REACTIONS = listOf("👍", "❤️", "🔥", "🎉", "😂", "👏")

    /** Add my reaction, or remove it if I already chose it
     *  (addMessageReaction / removeMessageReaction, verified in
     *  td_api.tl). The chips refresh via updateMessageInteractionInfo. */
    fun toggleReaction(item: MsgItem, emoji: String) {
        val chatId = currentChatId
        if (chatId == 0L) return
        toggleReactionIn(chatId, item, emoji) { _sendError.value = it }
    }

    /** toggleReaction for an explicit chat — thread and forum
     *  pages (v0.8.0) show messages from chats other than the
     *  open one and reuse this; errors go to [onError] instead
     *  of the chat screen's send-error line. */
    fun toggleReactionIn(
        chatId: Long,
        item: MsgItem,
        emoji: String,
        onError: (String) -> Unit
    ) {
        val already = item.reactions.any { it.emoji == emoji && it.chosen }
        val reactionType = JSONObject().apply {
            put("@type", "reactionTypeEmoji")
            put("emoji", emoji)
        }
        val query = if (already) {
            TdClient.request("removeMessageReaction").apply {
                put("chat_id", chatId)
                put("message_id", item.id)
                put("reaction_type", reactionType)
            }
        } else {
            TdClient.request("addMessageReaction").apply {
                put("chat_id", chatId)
                put("message_id", item.id)
                put("reaction_type", reactionType)
                put("is_big", false)
                put("update_recent_reactions", false)
            }
        }
        TdRepository.sendRaw(query) { response ->
            if (TdClient.isError(response)) {
                // The picker filters to the chat's allowed list,
                // but the list can change between opening the chat
                // and tapping; a 400 here is almost always "this
                // reaction is not allowed in this chat" — say that
                // in plain words, never show the raw code.
                val message = response.optString("message")
                onError(
                    if (
                        response.optInt("code") == 400 ||
                        message.contains("REACTION", ignoreCase = true)
                    ) {
                        "这个对话不允许这个回应。"
                    } else {
                        sendErrorText(response)
                    }
                )
            }
        }
    }

    /** Shown when the UI blocks a reaction the chat does not
     *  allow, before it ever reaches the server. */
    fun reportReactionBlocked() {
        _sendError.value = "这个对话不允许这个回应。"
    }

    private fun replyTargetId(msg: JSONObject): Long {
        val replyTo = msg.optJSONObject("reply_to") ?: return 0L
        if (replyTo.optString("@type") != "messageReplyToMessage") return 0L
        return replyTo.optLong("message_id")
    }

    private fun replyTargetChatId(msg: JSONObject): Long {
        val replyTo = msg.optJSONObject("reply_to") ?: return 0L
        if (replyTo.optString("@type") != "messageReplyToMessage") return 0L
        return replyTo.optLong("chat_id")
    }

    /** One-line summary of the replied-to message. TDLib embeds the
     *  replied content in messageReplyToMessage.content when it is
     *  loaded; otherwise fetch the message once and patch every
     *  item that replies to it. */
    private fun replySummaryOf(msg: JSONObject): String {
        val replyTo = msg.optJSONObject("reply_to") ?: return ""
        if (replyTo.optString("@type") != "messageReplyToMessage") return ""
        // A chosen quote (v0.9.15, V2) is more precise than
        // the whole-message summary: the bubble's reply strip
        // shows the quoted fragment itself.
        val quoteText = replyTo.optJSONObject("quote")
            ?.optJSONObject("text")?.optString("text")
        if (!quoteText.isNullOrEmpty()) {
            return quoteText.replace('\n', ' ').take(60)
        }
        val embedded = replyTo.optJSONObject("content")
        if (embedded != null) return summarizeContent(embedded)
        val targetId = replyTo.optLong("message_id")
        val targetChat = replyTo.optLong("chat_id")
        if (targetId != 0L) fetchReplySummary(targetChat, targetId)
        return ""
    }

    private fun fetchReplySummary(chatId: Long, messageId: Long) {
        // Message ids are only unique WITHIN a chat, so the
        // pending key must carry the chat id too — keyed by the
        // message id alone, a fetch for one chat could suppress
        // or satisfy another chat's fetch (audit fix A4).
        val key = "$chatId:$messageId"
        if (!replyFetchPending.add(key)) return
        val query = TdClient.request("getMessage").apply {
            put("chat_id", chatId)
            put("message_id", messageId)
        }
        TdRepository.sendRaw(query) { response ->
            replyFetchPending.remove(key)
            // The user may have moved to another chat while the
            // answer was in flight; patching now would write
            // this chat's summary into the other chat's bubbles.
            if (currentChatId != chatId) return@sendRaw
            if (TdClient.isError(response)) return@sendRaw
            val content = response.optJSONObject("content") ?: return@sendRaw
            val summary = summarizeContent(content)
            if (summary.isEmpty()) return@sendRaw
            var changed = false
            for ((id, item) in byId) {
                if (item.replyToId == messageId && item.replySummary.isEmpty()) {
                    byId[id] = item.copy(replySummary = summary)
                    changed = true
                }
            }
            if (changed) publish()
        }
    }

    /** Short one-liner for a message content object, used for reply
     *  quotes and (later) other compact renderings. */
    fun summarizeContent(content: JSONObject): String {
        return when (content.optString("@type")) {
            "messageText" ->
                content.optJSONObject("text")?.optString("text")
                    ?.replace('\n', ' ')?.take(60) ?: ""
            "messagePhoto" -> {
                val caption = content.optJSONObject("caption")
                    ?.optString("text") ?: ""
                if (caption.isEmpty()) "[图片]" else "[图片] $caption"
            }
            "messageDocument" -> {
                val name = content.optJSONObject("document")
                    ?.optString("file_name") ?: ""
                if (name.isEmpty()) "[文件]" else "[文件] $name"
            }
            "messageVideo" -> "[视频]"
            "messagePaidMedia" -> {
                val caption = content.optJSONObject("caption")
                    ?.optString("text") ?: ""
                if (caption.isEmpty()) "[付费媒体]" else "[付费媒体] $caption"
            }
            "messageRichMessage" -> {
                // Same skeleton the full parse stores
                // (v0.9.40, G4): first words of the post,
                // or the bare label when it is all media.
                val blocks = InstantViewStore.parseBlocks(
                    content.optJSONObject("message")
                        ?.optJSONArray("blocks")
                )
                val plain = InstantViewStore.blocksPlainText(blocks)
                    .replace('\n', ' ')
                if (plain.isEmpty()) {
                    "[富文本消息]"
                } else {
                    plain.take(60)
                }
            }
            "messageVoiceNote" -> "[语音]"
            "messageAnimation" -> "[动图]"
            "messageSticker" -> "[贴纸]"
            "messagePoll" -> {
                val q = content.optJSONObject("poll")
                    ?.optJSONObject("question")?.optString("text") ?: ""
                if (q.isEmpty()) "[投票]" else "[投票] $q"
            }
            "messageChecklist" -> {
                val title = content.optJSONObject("list")
                    ?.optJSONObject("title")?.optString("text") ?: ""
                if (title.isEmpty()) "[清单]" else "[清单] $title"
            }
            "messageVideoNote" -> "[视频消息]"
            "messageAudio" -> {
                val title = content.optJSONObject("audio")
                    ?.optString("title") ?: ""
                if (title.isEmpty()) "[音频]" else "[音频] $title"
            }
            "messageVenue" -> "[位置]"
            "messageGiveaway" -> "[抽奖]"
            "messageGiveawayWinners" -> "[抽奖结果]"
            "messageGiveawayCompleted" -> "[抽奖已结束]"
            "messageInvoice" -> "[账单]"
            "messageUsersShared" -> "[分享了用户]"
            "messageChatShared" -> "[分享了对话]"
            "messageCall" -> "[通话]"
            "messagePinMessage" -> "置顶了一条消息"
            "messageChatChangeTitle" -> {
                val title = content.optString("title")
                if (title.isEmpty()) "修改了群名" else "群名改成了「$title」"
            }
            "messageDice" -> {
                val emoji = content.optString("emoji")
                if (emoji.isEmpty()) "[骰子]" else "[骰子] $emoji"
            }
            "messageGame" -> {
                val title = content.optJSONObject("game")
                    ?.optString("title") ?: ""
                if (title.isEmpty()) "[游戏]" else "[游戏] $title"
            }
            else -> "[消息]"
        }
    }

    /** Prefer a mid-size rendition (~720 px wide) for bubbles;
     *  fall back to the largest available. */
    private fun pickPhotoSize(sizes: JSONArray?): JSONObject? {
        if (sizes == null || sizes.length() == 0) return null
        var best: JSONObject? = null
        var largest: JSONObject? = null
        for (i in 0 until sizes.length()) {
            val s = sizes.optJSONObject(i) ?: continue
            val w = s.optInt("width")
            if (largest == null || w > largest.optInt("width")) largest = s
            if (w in 1..900 && (best == null || w > best.optInt("width"))) best = s
        }
        return best ?: largest
    }

    // Publish throttling (v0.9.33, Z6): publishNow() sorts
    // and re-maps the WHOLE loaded window, and publish() is
    // called from 30+ sites — every incoming update during
    // a sync burst (new messages, read ticks, content edits)
    // redid that O(n log n) work and handed Compose a brand
    // new list to diff. Same medicine as ChatStore's K1
    // recompute coalescing (v0.9.1): leading-edge immediate
    // flush, bursts coalesce into one trailing flush per
    // window, and the trailing flush is routed through a
    // throwaway request so it runs on the receiver thread
    // like every other cache access. The window is 50 ms —
    // half of ChatStore's, because a just-sent message must
    // feel instant; the leading edge already covers that.
    private const val PUBLISH_WINDOW_MS = 50L

    private val publishHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var publishDirty = false

    @Volatile
    private var publishScheduled = false

    @Volatile
    private var lastPublishAt = 0L

    private fun publish() {
        publishDirty = true
        val now = System.currentTimeMillis()
        if (now - lastPublishAt >= PUBLISH_WINDOW_MS) {
            flushPublish()
            return
        }
        if (publishScheduled) return
        publishScheduled = true
        publishHandler.postDelayed({
            publishScheduled = false
            TdRepository.sendRaw(
                TdClient.request("getAuthorizationState")
            ) {
                flushPublish()
            }
        }, PUBLISH_WINDOW_MS)
    }

    private fun flushPublish() {
        if (!publishDirty) return
        publishDirty = false
        lastPublishAt = System.currentTimeMillis()
        publishNow()
    }

    private fun publishNow() {
        _messages.value = byId.values.sortedBy { it.id }.map { item ->
            // Backfill sender names that arrived after first parse.
            val name = if (item.senderUserId != 0L) {
                userNames[item.senderUserId]
            } else {
                null
            }
            val named = if (name != null && name != item.senderName) {
                item.copy(senderName = name)
            } else {
                item
            }
            // Backfill the via-bot label the same way
            // (v0.9.39, F3): the bot's handle or name may
            // land after the message was first parsed.
            val viaLabel = if (named.viaBotUserId != 0L) {
                viaBotLabelFor(named.viaBotUserId)
            } else {
                ""
            }
            val withVia = if (viaLabel != named.viaBotLabel) {
                named.copy(viaBotLabel = viaLabel)
            } else {
                named
            }
            // Backfill the sender's avatar path the same way
            // (group bubbles, W0); null until AvatarStore has
            // the file on disk.
            val senderPath = if (withVia.senderUserId != 0L) {
                AvatarStore.pathForUser(withVia.senderUserId)
            } else {
                null
            }
            val withName = if (senderPath != withVia.senderPhotoPath) {
                withVia.copy(senderPhotoPath = senderPath)
            } else {
                withVia
            }
            // Delivery ticks follow the chat's read position, not a
            // one-off flag set at send time.
            val readState = when {
                !withName.outgoing || withName.sendingState.isNotEmpty() -> ""
                withName.id <= lastReadOutbox -> "read"
                else -> "sent"
            }
            if (readState != withName.readState) {
                withName.copy(readState = readState)
            } else {
                withName
            }
        }
    }
}
