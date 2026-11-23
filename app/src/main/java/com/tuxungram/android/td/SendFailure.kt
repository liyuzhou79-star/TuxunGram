package com.tuxungram.android.td

import org.json.JSONObject

/**
 * Send-failure classification (v0.9.44, N1/N2).
 *
 * Until this version a failed send was one undifferentiated
 * state: the bubble offered 「点这里重发」 for EVERY failure
 * and resendMessages was called unconditionally — but
 * td_api.tl is explicit that resendMessages "can be called
 * only for messages for which messageSendingStateFailed
 * .can_retry is true and after retry_after time passed".
 * For a member muted by a group admin (can_retry = false,
 * error CHAT_WRITE_FORBIDDEN) every tap re-failed with the
 * same error and the app answered 「重发失败，再试一次」 —
 * an endless loop that looked like an app bug while the
 * real cause (no posting rights in that group) was never
 * surfaced. That is the user's 2026-11-23 report.
 *
 * Classification happens ONCE, here, at the boundary where
 * TDLib's error object is read: the numeric code plus the
 * protocol error identifier (error.message in TDLib is a
 * machine identifier such as "CHAT_WRITE_FORBIDDEN" or
 * "SLOWMODE_WAIT_12", never localized display text) map to
 * the typed [Kind]; every decision downstream — whether a
 * retry affordance exists at all, what the bubble footer
 * says, what the error bar says — reads the Kind, never
 * the raw string.
 */
enum class SendFailKind {
    /** The group / channel does not let this account post
     *  (server error CHAT_WRITE_FORBIDDEN). Not retryable:
     *  resending can never succeed while the ban / mute /
     *  read-only state stands. */
    WRITE_FORBIDDEN,

    /** Removed from the group (USER_BANNED_IN_CHANNEL and
     *  friends). Not retryable. */
    BANNED,

    /** Group slow mode: retryable once the wait is over;
     *  the failed state carries the remaining seconds in
     *  retry_after. */
    SLOWMODE,

    /** Sending too fast overall (FLOOD_WAIT / HTTP 429):
     *  retryable after a pause. */
    FLOOD_WAIT,

    /** The send needs Telegram Stars the account has not
     *  agreed to pay (required_paid_message_star_count > 0).
     *  This app never pays Stars, so it is presented as a
     *  plain explanation, not a retry. */
    PAID_REQUIRED,

    /** Premium-gated send (checklists, premium effects…). */
    PREMIUM_REQUIRED,

    /** The chat type refuses scheduled sends (secret chats)
     *  or the chosen time is invalid. */
    SCHEDULE_UNSUPPORTED,

    /** Anything else: the raw identifier is kept for the
     *  detail text; retryability comes from TDLib's own
     *  can_retry flag on the failed state. */
    UNKNOWN
}

object SendFailure {

    /** Map a TDLib error object (or its code + identifier)
     *  to the typed kind. [paidStarsRequired] is the failed
     *  state's required_paid_message_star_count. */
    fun classify(
        code: Int,
        identifier: String,
        paidStarsRequired: Long = 0L
    ): SendFailKind {
        if (paidStarsRequired > 0) return SendFailKind.PAID_REQUIRED
        val id = identifier.uppercase()
        return when {
            id.contains("WRITE_FORBIDDEN") ->
                SendFailKind.WRITE_FORBIDDEN
            id.contains("BANNED") -> SendFailKind.BANNED
            id.startsWith("SLOWMODE_WAIT") -> SendFailKind.SLOWMODE
            id.startsWith("FLOOD_WAIT") || code == 429 ->
                SendFailKind.FLOOD_WAIT
            id.contains("SCHEDULE") || id.contains("SCHEDULING") ->
                SendFailKind.SCHEDULE_UNSUPPORTED
            id.contains("PREMIUM") -> SendFailKind.PREMIUM_REQUIRED
            else -> SendFailKind.UNKNOWN
        }
    }

    fun classify(error: JSONObject?): SendFailKind {
        if (error == null) return SendFailKind.UNKNOWN
        return classify(
            error.optInt("code"),
            error.optString("message")
        )
    }

    /** Whether a tap-to-resend affordance may be offered:
     *  TDLib's can_retry flag is authoritative; the kind can
     *  only veto it (permission / payment failures never
     *  succeed on a blind resend). */
    fun retryable(kind: SendFailKind, canRetry: Boolean): Boolean {
        if (!canRetry) return false
        return when (kind) {
            SendFailKind.WRITE_FORBIDDEN, SendFailKind.BANNED,
            SendFailKind.PAID_REQUIRED,
            SendFailKind.PREMIUM_REQUIRED,
            SendFailKind.SCHEDULE_UNSUPPORTED -> false
            else -> true
        }
    }

    /** One-line reason for the bubble footer (after
     *  「发送失败：」). */
    fun shortReason(kind: SendFailKind, retryAfterSec: Double): String =
        when (kind) {
            SendFailKind.WRITE_FORBIDDEN -> "此群组不能发"
            SendFailKind.BANNED -> "你已被移出此群组"
            SendFailKind.SLOWMODE ->
                if (retryAfterSec > 0.5) {
                    "慢速模式，还需等 ${retryAfterSec.toInt()} 秒"
                } else {
                    "慢速模式"
                }
            SendFailKind.FLOOD_WAIT -> "发太快了，等一会儿"
            SendFailKind.PAID_REQUIRED -> "需要星星付费才能发"
            SendFailKind.PREMIUM_REQUIRED -> "需要 Premium 才能发"
            SendFailKind.SCHEDULE_UNSUPPORTED -> "此对话不支持定时发送"
            SendFailKind.UNKNOWN -> "点这里查看"
        }

    /** Full plain-language sentence for the error bar /
     *  detail dialog. [rawIdentifier] and [code] only feed
     *  the UNKNOWN fallback so a genuinely new failure is
     *  still diagnosable. */
    fun fullText(
        kind: SendFailKind,
        retryAfterSec: Double,
        code: Int = 0,
        rawIdentifier: String = ""
    ): String = when (kind) {
        SendFailKind.WRITE_FORBIDDEN ->
            "此群组不能发：你在这里没有发言权限（可能被管理员禁言，或这个群只允许管理员发言）。这条消息发不出去，可以删掉。"
        SendFailKind.BANNED ->
            "你已被移出这个群组，不能再发言。这条消息发不出去，可以删掉。"
        SendFailKind.SLOWMODE ->
            if (retryAfterSec > 0.5) {
                "这个群开了慢速模式，还需等 ${retryAfterSec.toInt()} 秒才能再发，到时间点「重发」即可。"
            } else {
                "这个群开了慢速模式，两条消息之间要隔一会儿，稍等再点「重发」。"
            }
        SendFailKind.FLOOD_WAIT ->
            "发太快了，服务器让先等一会儿，过几十秒再点「重发」。"
        SendFailKind.PAID_REQUIRED ->
            "在这个对话发消息需要 Telegram 星星付费，本 App 不经手星星支付，这条发不出去，可以删掉。"
        SendFailKind.PREMIUM_REQUIRED ->
            "这个功能要 Telegram Premium 才能用（比如发送清单、会员特效），普通账号用不了。"
        SendFailKind.SCHEDULE_UNSUPPORTED ->
            "这个对话不支持定时发送（比如秘密聊天），或者定的时间不对。"
        SendFailKind.UNKNOWN ->
            if (rawIdentifier.isNotEmpty()) {
                "发送失败，再试一次。（$rawIdentifier）"
            } else if (code != 0) {
                "发送失败，再试一次。（错误码 $code）"
            } else {
                "发送失败，再试一次。"
            }
    }

    /** Convenience for the stores: the full text straight
     *  from a TDLib error response object. */
    fun fullTextOf(error: JSONObject?): String {
        if (error == null) return "发送失败，再试一次。"
        return fullText(
            classify(error),
            retryAfterSec = 0.0,
            code = error.optInt("code"),
            rawIdentifier = error.optString("message")
        )
    }
}
