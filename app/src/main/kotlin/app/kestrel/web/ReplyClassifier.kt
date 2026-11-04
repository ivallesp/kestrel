package app.kestrel.web

import app.kestrel.engine.BidReply

/** What one line of eBay's reply says about the user's standing. */
enum class Standing { LEADING, OUTBID, OTHER }

/**
 * Maps the texts eBay shows after a bid to a [BidReply], always keeping eBay's own words. The two documented
 * replies come first; the looser matches cover wording variants. Within a sentence outbid wording is checked before
 * leading wording, so "no longer the highest bidder" reads as outbid.
 */
object ReplyClassifier {
    private val rules: List<Pair<Regex, Standing>> =
        listOf(
            // Documented layer notices, 2024-07-26 and 2026-04-30.
            "puts you in the lead" to Standing.LEADING,
            "another bidder is winning" to Standing.OUTBID,
            // Variants: older eBay wording and tolerant matches.
            "\\boutbid\\b" to Standing.OUTBID,
            "\\banother bidder\\b" to Standing.OUTBID,
            "\\bincrease your (?:max(?:imum)? )?bid\\b" to Standing.OUTBID,
            // Negated leading phrases come before the leading ones: "not the highest bidder" is not a lead.
            "\\b(?:not|no longer)(?: currently)? (?:the )?high(?:est)? bidder\\b" to Standing.OUTBID,
            "\\b(?:not|no longer)(?: currently)? in the lead\\b" to Standing.OUTBID,
            "\\b(?:not|no longer)(?: currently)? winning\\b" to Standing.OUTBID,
            "\\bnicht (?:mehr )?h\u00f6chstbietende" to Standing.OUTBID,
            "\u00fcberboten" to Standing.OUTBID,
            "\\bin the lead\\b" to Standing.LEADING,
            "\\bhigh(?:est)? bidder\\b" to Standing.LEADING,
            "\\byou(?:'re| are) winning\\b" to Standing.LEADING,
            "h\u00f6chstbietende" to Standing.LEADING,
        ).map { (pattern, standing) -> Regex(pattern, RegexOption.IGNORE_CASE) to standing }

    /**
     * Each sentence is read on its own, in order, and the first one that says where the user stands decides. A
     * sentence that only says the user could be outbid later ("We'll let you know if you're outbid") says nothing.
     */
    fun standingOf(text: String): Standing {
        val normalized = text.replace('\u2019', '\'')
        for (sentence in normalized.split(sentenceBreak)) {
            val standing = rules.firstOrNull { (pattern, _) -> pattern.containsMatchIn(sentence) }?.second ?: continue
            if (standing == Standing.OUTBID && outbidLater.containsMatchIn(sentence)) continue
            return standing
        }
        return Standing.OTHER
    }

    private val sentenceBreak = Regex("""(?<=[.!?])\s+|\n+""")
    private val outbidLater =
        Regex(
            """\bif you(?:'re| are| get)? (?:be )?outbid\b|\bif (?:someone|anyone|another bidder)\b|""" +
                """\b(?:could|might|may|can) (?:still )?(?:be )?outbid\b|\blet you know if\b""",
            RegexOption.IGNORE_CASE,
        )

    // Words with which eBay refuses or fails a bid. An unknown notice without them is not taken as a refusal.
    private val refusal =
        Regex(
            listOf(
                """\b(?:can't|cannot|can not|couldn't|could not|unable|not able)\b""",
                """\b(?:isn't|wasn't|not)\s+(?:been\s+)?(?:accepted|placed|allowed|eligible|possible|available|valid)\b""",
                """\btoo low\b""",
                """\benter\b.{0,40}\bor (?:more|higher)\b""",
                """\bminimum bid\b""",
                """\bended\b""",
                """\bno longer available\b""",
                """\brestrict""",
                """\bblocked\b""",
                """\berror\b""",
                """\bsomething went wrong\b""",
                """\btry again\b""",
                """\boops\b""",
                """\bsorry\b""",
                """\bcontact the seller\b""",
                """\bnot met\b""",
                """\bnicht m\u00f6glich\b|\bfehler\b|\bbeendet\b""",
            ).joinToString("|"),
            RegexOption.IGNORE_CASE,
        )

    /** True when [text] says eBay refused or could not take the bid, as opposed to an unknown success or status. */
    fun isRefusal(text: String): Boolean = refusal.containsMatchIn(text.replace('\u2019', '\''))

    /**
     * A leading or outbid reply from the notices that appeared after the press, or failing that from lines that
     * were added to the layer. Null when neither says where the user stands.
     */
    fun decisive(
        newNotices: List<String>,
        addedLines: List<String>,
        latencyMillis: Long?,
    ): BidReply? {
        for (text in newNotices + addedLines) {
            when (standingOf(text)) {
                Standing.LEADING -> return BidReply.Leading(text, latencyMillis)
                Standing.OUTBID -> return BidReply.Outbid(text, latencyMillis)
                Standing.OTHER -> Unit
            }
        }
        return null
    }

    /** The first new notice that says something else, other than a status line: eBay's reason when it refused. */
    fun otherNotice(newNotices: List<String>): String? =
        newNotices.firstOrNull { standingOf(it) == Standing.OTHER && !isStatusLine(it) }

    // Lines that change by themselves and answer nothing: time left ("5m 33s left", "Ends in 4m") and bid counts.
    private val timeLeft =
        Regex("""\b\d+\s?[dhms]\b.*\bleft\b|\bends? in\b|\b\d+\s?[dhm]\s+\d+\s?[hms]\b""", RegexOption.IGNORE_CASE)
    private val bidCount = Regex("""\b\d+\s+bids?\b""", RegexOption.IGNORE_CASE)
    private const val STATUS_LINE_LIMIT = 80

    fun isStatusLine(text: String): Boolean =
        text.length <= STATUS_LINE_LIMIT && (timeLeft.containsMatchIn(text) || bidCount.containsMatchIn(text))
}
