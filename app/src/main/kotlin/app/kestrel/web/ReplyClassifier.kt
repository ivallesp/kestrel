package app.kestrel.web

import app.kestrel.engine.BidReply

/** What one line of eBay's reply says about the user's standing. */
enum class Standing { LEADING, OUTBID, OTHER }

/**
 * Maps the texts eBay shows after a bid to a [BidReply], always keeping eBay's own words. The two documented
 * replies come first; the looser matches cover wording variants. Outbid wording is checked before leading wording,
 * so "no longer the highest bidder" reads as outbid.
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
            "\\bno longer the high(?:est)? bidder\\b" to Standing.OUTBID,
            "\u00fcberboten" to Standing.OUTBID,
            "\\bin the lead\\b" to Standing.LEADING,
            "\\bhigh(?:est)? bidder\\b" to Standing.LEADING,
            "\\byou(?:'re| are) winning\\b" to Standing.LEADING,
            "h\u00f6chstbietende" to Standing.LEADING,
        ).map { (pattern, standing) -> Regex(pattern, RegexOption.IGNORE_CASE) to standing }

    fun standingOf(text: String): Standing {
        val normalized = text.replace('\u2019', '\'')
        return rules.firstOrNull { (pattern, _) -> pattern.containsMatchIn(normalized) }?.second ?: Standing.OTHER
    }

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
