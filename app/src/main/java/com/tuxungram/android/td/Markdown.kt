package com.tuxungram.android.td

/**
 * Lightweight send-side markdown (v0.9.15, V3). Before a
 * text message is sent, the markers
 *
 *   **bold**  *italic*  __underline__  ~~strikethrough~~
 *   `code`  ```pre block```  ||spoiler||  [label](https://…)
 *
 * are parsed client-side into formattedText entities (the
 * shapes MessageStore.entityJson emits; offsets/lengths are
 * UTF-16 code units of the CLEAN text, which is exactly how
 * Kotlin strings index, so no conversion is needed).
 *
 * The parser is deliberately conservative, per the brief:
 * - code spans and pre blocks are extracted literally —
 *   markers inside them are never parsed;
 * - a span's content must be non-empty and may not start or
 *   end with whitespace;
 * - an opening marker immediately preceded by a letter or
 *   digit, or a closing marker immediately followed by one,
 *   does not count ("2*3*4" stays arithmetic, not italics);
 * - no nesting: once a span is consumed, its content is
 *   literal (a ` inside **…** stays a backtick);
 * - link labels must pair with a URL starting http://,
 *   https:// or tg://, otherwise the brackets stay literal;
 * - a backslash before a marker character (` * _ ~ | [ \`)
 *   escapes it to a literal character;
 * - anything unmatched stays literal text — the parser can
 *   only ever REMOVE marker characters it has paired, it
 *   never eats message text.
 *
 * [Parsed.rawToClean] maps every raw UTF-16 boundary index
 * to the clean text's boundary index, so entities computed
 * against the raw input (the V6 mention-name insertions)
 * can be remapped into clean coordinates by the caller.
 */
object MarkdownSend {

    data class Parsed(
        val text: String,
        val entities: List<TextEntityUi>,
        val rawToClean: IntArray
    )

    private const val ESCAPABLE = "`*_~|[\\"

    fun parse(raw: String): Parsed {
        val clean = StringBuilder(raw.length)
        val map = IntArray(raw.length + 1) { -1 }
        val entities = ArrayList<TextEntityUi>()
        var i = 0

        fun keep(from: Int, to: Int) {
            var k = from
            while (k < to) {
                map[k] = clean.length
                clean.append(raw[k])
                k++
            }
            map[to] = clean.length
        }

        fun drop(from: Int, to: Int) {
            var k = from
            while (k <= to) {
                map[k] = clean.length
                k++
            }
        }

        fun boundaryOk(openAt: Int, closeEnd: Int): Boolean {
            if (openAt > 0 && raw[openAt - 1].isLetterOrDigit()) {
                return false
            }
            if (closeEnd < raw.length &&
                raw[closeEnd].isLetterOrDigit()
            ) {
                return false
            }
            return true
        }

        /** Try a paired-marker span at [i] ("**", "__", "~~",
         *  "||", "*"): on success the content is kept with an
         *  entity of [kind] and the new index is returned;
         *  null when the marker does not pair up here. */
        fun span(i: Int, marker: String, kind: String): Int? {
            val contentStart = i + marker.length
            if (contentStart >= raw.length) return null
            val close = raw.indexOf(marker, contentStart)
            if (close < 0) return null
            val content = raw.substring(contentStart, close)
            if (content.isEmpty() ||
                content.first().isWhitespace() ||
                content.last().isWhitespace()
            ) {
                return null
            }
            val closeEnd = close + marker.length
            if (!boundaryOk(i, closeEnd)) return null
            drop(i, contentStart)
            val entityOffset = clean.length
            keep(contentStart, close)
            entities.add(
                TextEntityUi(
                    offset = entityOffset,
                    length = clean.length - entityOffset,
                    kind = kind
                )
            )
            drop(close, closeEnd)
            return closeEnd
        }

        while (i < raw.length) {
            val c = raw[i]
            when {
                // Escape: \ before a marker char keeps the
                // char and drops the backslash.
                c == '\\' && i + 1 < raw.length &&
                    raw[i + 1] in ESCAPABLE -> {
                    drop(i, i + 1)
                    keep(i + 1, i + 2)
                    i += 2
                }
                // Pre block: ``` … ``` — fully literal.
                c == '`' && raw.startsWith("```", i) -> {
                    val contentStart = i + 3
                    val close = if (contentStart <= raw.length) {
                        raw.indexOf("```", contentStart)
                    } else {
                        -1
                    }
                    if (close < 0) {
                        keep(i, i + 1)
                        i += 1
                    } else {
                        drop(i, contentStart)
                        val entityOffset = clean.length
                        keep(contentStart, close)
                        if (clean.length > entityOffset) {
                            entities.add(
                                TextEntityUi(
                                    offset = entityOffset,
                                    length = clean.length - entityOffset,
                                    kind = "pre"
                                )
                            )
                        }
                        drop(close, close + 3)
                        i = close + 3
                    }
                }
                // Inline code: ` … ` — fully literal.
                c == '`' -> {
                    val close = raw.indexOf('`', i + 1)
                    if (close < 0 || close == i + 1) {
                        keep(i, i + 1)
                        i += 1
                    } else {
                        drop(i, i + 1)
                        val entityOffset = clean.length
                        keep(i + 1, close)
                        entities.add(
                            TextEntityUi(
                                offset = entityOffset,
                                length = clean.length - entityOffset,
                                kind = "code"
                            )
                        )
                        drop(close, close + 1)
                        i = close + 1
                    }
                }
                c == '*' && raw.startsWith("**", i) -> {
                    val next = span(i, "**", "bold")
                    if (next != null) {
                        i = next
                    } else {
                        keep(i, i + 1)
                        i += 1
                    }
                }
                c == '_' && raw.startsWith("__", i) -> {
                    val next = span(i, "__", "underline")
                    if (next != null) {
                        i = next
                    } else {
                        keep(i, i + 1)
                        i += 1
                    }
                }
                c == '~' && raw.startsWith("~~", i) -> {
                    val next = span(i, "~~", "strike")
                    if (next != null) {
                        i = next
                    } else {
                        keep(i, i + 1)
                        i += 1
                    }
                }
                c == '|' && raw.startsWith("||", i) -> {
                    val next = span(i, "||", "spoiler")
                    if (next != null) {
                        i = next
                    } else {
                        keep(i, i + 1)
                        i += 1
                    }
                }
                c == '*' -> {
                    val next = span(i, "*", "italic")
                    if (next != null) {
                        i = next
                    } else {
                        keep(i, i + 1)
                        i += 1
                    }
                }
                // [label](url) — label literal, URL checked.
                c == '[' -> {
                    val labelEnd = raw.indexOf(']', i + 1)
                    val urlEnd = if (
                        labelEnd > i + 1 &&
                        labelEnd + 1 < raw.length &&
                        raw[labelEnd + 1] == '('
                    ) {
                        raw.indexOf(')', labelEnd + 2)
                    } else {
                        -1
                    }
                    val url = if (urlEnd > labelEnd + 2) {
                        raw.substring(labelEnd + 2, urlEnd)
                    } else {
                        ""
                    }
                    if (url.isNotEmpty() && !url.contains(' ') &&
                        (url.startsWith("http://") ||
                            url.startsWith("https://") ||
                            url.startsWith("tg://"))
                    ) {
                        drop(i, i + 1)
                        val entityOffset = clean.length
                        keep(i + 1, labelEnd)
                        entities.add(
                            TextEntityUi(
                                offset = entityOffset,
                                length = clean.length - entityOffset,
                                kind = "textUrl",
                                url = url
                            )
                        )
                        drop(labelEnd, urlEnd + 1)
                        i = urlEnd + 1
                    } else {
                        keep(i, i + 1)
                        i += 1
                    }
                }
                else -> {
                    keep(i, i + 1)
                    i += 1
                }
            }
        }
        map[raw.length] = clean.length
        // Safety sweep: any boundary the walk somehow left
        // unset collapses to the nearest known position, so
        // callers remapping entities never read a -1.
        var last = 0
        for (k in map.indices) {
            if (map[k] < 0) {
                map[k] = last
            } else {
                last = map[k]
            }
        }
        return Parsed(clean.toString(), entities, map)
    }
}
