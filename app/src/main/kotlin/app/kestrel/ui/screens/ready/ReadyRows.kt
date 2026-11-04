package app.kestrel.ui.screens.ready

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import app.kestrel.model.EbaySite
import app.kestrel.model.Snipe
import app.kestrel.platform.ReadinessItem
import app.kestrel.platform.ReadinessKey
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.format.AuctionZone
import app.kestrel.ui.format.clock
import app.kestrel.ui.format.clockFor
import app.kestrel.ui.format.dayDate
import app.kestrel.ui.format.forDisplay
import app.kestrel.ui.format.isSameDay
import app.kestrel.ui.format.secondsWithHundredths
import app.kestrel.ui.format.withCurlyQuotes
import app.kestrel.ui.model.LotTimes
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

// The rows of S5 (DESIGN S5, 3.9 and 6.2): the platform's verdict for each check, in Kestrel's words.

/** Where a check row stands: a problem to fix, an optional extra, a pass, or nothing to check yet. */
enum class CheckStanding { Problem, Optional, Passed, NotYet }

/** What a row's button opens. */
sealed interface CheckAction {
    val label: String

    /** The system page Readiness.fixIntent(key) returns. */
    data class SystemPage(
        val key: ReadinessKey,
        override val label: String,
    ) : CheckAction

    /** S6, for a signed-out eBay account. */
    data object SignIn : CheckAction {
        override val label: String = "Sign in"
    }

    /** The snipe whose dry run failed: its detail says why and offers the fix. */
    data class OpenSnipe(
        val snipeId: Long,
    ) : CheckAction {
        override val label: String = FIX
    }
}

/**
 * One readiness row. [stateWords] is the state line of a problem ("Restricted") or an optional check
 * ("Off · optional"); passes and not-yet rows have none. [detail] is one or two sentences in ink soft.
 */
@Immutable
data class CheckRow(
    val key: ReadinessKey,
    val name: String,
    @param:DrawableRes val icon: Int,
    val standing: CheckStanding,
    val stateWords: String?,
    val detail: String,
    val action: CheckAction?,
)

/** How Kestrel last measured the phone clock against a reference. */
enum class ClockReference { Google, Network, None }

@Immutable
data class ClockReading(
    /** True time minus phone time. */
    val offsetMillis: Long,
    val reference: ClockReference,
    val syncedAt: Instant,
)

/** The most recent dry run on any snipe. */
@Immutable
data class DryRunReading(
    val snipeId: Long,
    val title: String,
    val at: Instant,
    val passed: Boolean,
)

/** What the rows quote beyond the platform's verdicts, read when the check ran. */
@Immutable
data class CheckFacts(
    val checkedAt: Instant,
    val signedInName: String?,
    val clock: ClockReading?,
    val latestDryRun: DryRunReading?,
    /** Do Not Disturb access is granted, so Kestrel's alerts can sound through it (DESIGN MF27). */
    val alertsInDoNotDisturb: Boolean,
)

/** The earliest bid Kestrel will place, for the S5 deadline sentence. */
@Immutable
data class NextBid(
    val title: String,
    val at: Instant,
)

object CheckRows {
    /** The rows in the platform's order; the screen groups them by [CheckStanding]. */
    fun build(
        items: List<ReadinessItem>,
        facts: CheckFacts,
        nextBid: NextBid?,
        now: Instant,
        zone: ZoneId = AuctionZone,
    ): List<CheckRow> = items.map { row(it, facts, nextBid, now, zone) }

    /** The next bid any snipe will place after [now], or null when nothing is armed. */
    fun nextBid(
        snipes: List<Snipe>,
        now: Instant,
    ): NextBid? =
        snipes
            .filter { it.maxBid != null && it.status.isActiveBid }
            .map { it to LotTimes.bidAt(it) }
            .filter { (_, bidAt) -> bidAt.isAfter(now) }
            .minByOrNull { (_, bidAt) -> bidAt }
            ?.let { (snipe, bidAt) -> NextBid(shortTitle(snipe.title), bidAt) }

    /**
     * A listing title short enough for a sentence: up to the first separator and at most four words, with model
     * numbers kept whole. "Canon AE-1 Program + 50mm f/1.8" becomes "Canon AE-1 Program".
     */
    fun shortTitle(title: String): String {
        val head =
            Separators
                .split(title.trim())
                .first()
                .trim()
                .ifEmpty { title.trim() }
        val kept = mutableListOf<String>()
        var counted = 0
        for (word in head.split(Whitespace)) {
            if (counted == MAXIMUM_TITLE_WORDS) break
            kept += word
            // "&" and other marks ride along with the words around them.
            if (word.any { it.isLetterOrDigit() }) counted++
        }
        return kept.joinToString(" ").forDisplay()
    }

    private fun row(
        item: ReadinessItem,
        facts: CheckFacts,
        nextBid: NextBid?,
        now: Instant,
        zone: ZoneId,
    ): CheckRow {
        val key = item.key
        val name = names.getValue(key)
        if (!item.ok && item.detail.startsWith(COULD_NOT_CHECK)) {
            // A probe that threw: the platform could not read this setting, so say exactly that.
            val action = if (systemPageKeys.contains(key)) CheckAction.SystemPage(key, FIX) else null
            return CheckRow(
                key,
                name,
                icons.getValue(key),
                CheckStanding.Problem,
                "Couldn’t check",
                platform(item),
                action,
            )
        }
        return when (key) {
            ReadinessKey.EBAY_SESSION -> session(item, facts, now, zone)
            ReadinessKey.NOTIFICATIONS -> notifications(item, facts)
            ReadinessKey.EXACT_ALARMS -> exactTiming(item)
            ReadinessKey.BATTERY -> battery(item)
            ReadinessKey.BACKGROUND -> background(item)
            ReadinessKey.UNUSED_APP_PAUSE -> pauseIfUnused(item)
            ReadinessKey.LIVE_UPDATES -> liveUpdates(item)
            ReadinessKey.CLOCK -> clock(item, facts, now)
            ReadinessKey.NETWORK -> network(item)
            ReadinessKey.DRY_RUN -> dryRun(item, facts, nextBid, now, zone)
        }
    }

    private fun session(
        item: ReadinessItem,
        facts: CheckFacts,
        now: Instant,
        zone: ZoneId,
    ): CheckRow {
        if (!item.ok) return problem(item, "Signed out", platform(item), CheckAction.SignIn)
        val name = facts.signedInName ?: return passed(item, platform(item))
        // A dry run that passed opened eBay's bid panel signed in: the latest proof that the session works.
        val seen = facts.latestDryRun?.takeIf { it.passed }?.at ?: return passed(item, "Signed in as $name")
        // A no-break space keeps each " · " with the words before it, so a wrapped line never starts with the dot.
        return passed(item, "Signed in as $name\u00A0· checked ${ago(seen, now, zone)}")
    }

    private fun notifications(
        item: ReadinessItem,
        facts: CheckFacts,
    ): CheckRow =
        when {
            !item.ok -> problem(item, "Off", platform(item), systemPage(item.key))
            facts.alertsInDoNotDisturb -> passed(item, "On\u00A0· alerts can sound in Do Not Disturb")
            else -> passed(item, "On")
        }

    private fun exactTiming(item: ReadinessItem): CheckRow =
        if (item.ok) {
            passed(item, "Allowed, so bids leave on the second")
        } else {
            problem(item, "Not allowed", platform(item), systemPage(item.key))
        }

    private fun battery(item: ReadinessItem): CheckRow =
        if (item.ok) {
            passed(item, "Unrestricted, so bids go out on time with the screen off")
        } else {
            problem(
                item,
                "Restricted",
                "Android may pause Kestrel when the screen is off, so a bid could go out late.",
                systemPage(item.key),
                icon = Symbols.BatteryAlert,
            )
        }

    private fun background(item: ReadinessItem): CheckRow =
        if (item.ok) {
            passed(item, "Not restricted")
        } else {
            problem(item, "Restricted", platform(item), systemPage(item.key))
        }

    private fun pauseIfUnused(item: ReadinessItem): CheckRow =
        if (item.ok) {
            // The platform writes "Off: Kestrel stays armed…"; the other passes read "Allowed, so bids…".
            passed(item, platform(item).replaceFirst("Off: ", "Off, so "))
        } else {
            problem(
                item,
                "On",
                "Android may pause Kestrel if you don’t open it for a few months, and paused apps can’t bid.",
                systemPage(item.key),
            )
        }

    private fun liveUpdates(item: ReadinessItem): CheckRow =
        if (item.ok) {
            passed(item, "On\u00A0· the countdown shows in the status bar")
        } else {
            CheckRow(
                key = item.key,
                name = names.getValue(item.key),
                icon = icons.getValue(item.key),
                standing = CheckStanding.Optional,
                stateWords = "Off · optional",
                detail = "Shows the countdown in the status bar and on the lock screen.",
                action = CheckAction.SystemPage(item.key, "Turn on"),
            )
        }

    private fun clock(
        item: ReadinessItem,
        facts: CheckFacts,
        now: Instant,
    ): CheckRow {
        if (!item.ok) return problem(item, "Wrong time", platform(item), systemPage(item.key))
        val reading = facts.clock
        val reference =
            when (reading?.reference) {
                ClockReference.Google -> "Google\u00A0time"
                ClockReference.Network -> "network\u00A0time"
                ClockReference.None, null -> null
            }
        if (reading == null || reference == null) return passed(item, "Checked against Google time before each bid")
        val offset = secondsWithHundredths(reading.offsetMillis)
        return passed(item, "$offset off $reference\u00A0· synced ${ago(reading.syncedAt, now, AuctionZone)}")
    }

    private fun network(item: ReadinessItem): CheckRow =
        if (item.ok) {
            passed(item, platform(item))
        } else {
            problem(
                item,
                "Offline",
                "Kestrel can’t check listings or bid until the phone reconnects.",
                systemPage(item.key),
                icon = Symbols.CloudOff,
            )
        }

    private fun dryRun(
        item: ReadinessItem,
        facts: CheckFacts,
        nextBid: NextBid?,
        now: Instant,
        zone: ZoneId,
    ): CheckRow {
        val latest = facts.latestDryRun
        // With nothing armed there is no bid to test, so an old pass proves nothing (DESIGN MF25).
        if (nextBid == null || latest == null) {
            return CheckRow(
                key = item.key,
                name = names.getValue(item.key),
                icon = icons.getValue(item.key),
                standing = CheckStanding.NotYet,
                stateWords = null,
                detail = "Runs about 10 minutes before each bid",
                action = null,
            )
        }
        val day = if (isSameDay(now, latest.at, zone)) "today" else latest.at.dayDate(zone)
        val evidence = "$day at ${latest.at.clock(zone)} on the ${shortTitle(latest.title)}\u00A0· nothing was sent"
        return if (item.ok && latest.passed) {
            passed(item, "Passed $evidence")
        } else {
            problem(item, "Failed", "Failed $evidence", CheckAction.OpenSnipe(latest.snipeId))
        }
    }

    /** "2 min ago", "today at 14:30" or "Sat 8 Nov": how long since [then]. */
    private fun ago(
        then: Instant,
        now: Instant,
        zone: ZoneId,
    ): String {
        val elapsed = Duration.between(then, now)
        return when {
            elapsed < Duration.ofMinutes(1) -> "just now"
            elapsed < Duration.ofHours(1) -> "${elapsed.toMinutes()}\u202Fmin ago"
            isSameDay(now, then, zone) -> "today at ${clockFor(now, then, zone)}"
            else -> then.dayDate(zone)
        }
    }

    private fun passed(
        item: ReadinessItem,
        detail: String,
    ): CheckRow =
        CheckRow(item.key, names.getValue(item.key), icons.getValue(item.key), CheckStanding.Passed, null, detail, null)

    private fun problem(
        item: ReadinessItem,
        state: String,
        detail: String,
        action: CheckAction?,
        @DrawableRes icon: Int = icons.getValue(item.key),
    ): CheckRow = CheckRow(item.key, names.getValue(item.key), icon, CheckStanding.Problem, state, detail, action)

    private fun systemPage(key: ReadinessKey): CheckAction = CheckAction.SystemPage(key, FIX)

    /** The platform's own words, with Kestrel's typographic quotes. */
    private fun platform(item: ReadinessItem): String = item.detail.withCurlyQuotes()

    private val names =
        mapOf(
            ReadinessKey.EBAY_SESSION to "eBay account",
            ReadinessKey.NOTIFICATIONS to "Notifications",
            ReadinessKey.EXACT_ALARMS to "Exact timing",
            ReadinessKey.BATTERY to "Battery",
            ReadinessKey.BACKGROUND to "Background activity",
            ReadinessKey.UNUSED_APP_PAUSE to "Pause if unused",
            ReadinessKey.LIVE_UPDATES to "Live Updates",
            ReadinessKey.CLOCK to "Phone clock",
            ReadinessKey.NETWORK to "Internet",
            ReadinessKey.DRY_RUN to "Dry run",
        )

    private val icons =
        mapOf(
            ReadinessKey.EBAY_SESSION to Symbols.AccountCircle,
            ReadinessKey.NOTIFICATIONS to Symbols.Notifications,
            ReadinessKey.EXACT_ALARMS to Symbols.AlarmOn,
            ReadinessKey.BATTERY to Symbols.BatteryFull,
            ReadinessKey.BACKGROUND to Symbols.Smartphone,
            ReadinessKey.UNUSED_APP_PAUSE to Symbols.HourglassTop,
            ReadinessKey.LIVE_UPDATES to Symbols.Update,
            ReadinessKey.CLOCK to Symbols.Sync,
            ReadinessKey.NETWORK to Symbols.Link,
            ReadinessKey.DRY_RUN to Symbols.TaskAlt,
        )

    /** Checks whose fix is a system page (the others are fixed inside Kestrel). */
    private val systemPageKeys = ReadinessKey.entries.toSet() - setOf(ReadinessKey.EBAY_SESSION, ReadinessKey.DRY_RUN)

    /**
     * How the platform starts the detail of a check it could not read (ReadinessEvaluator). Matched, never shown;
     * the platform writes a straight apostrophe, hence the escape.
     */
    private const val COULD_NOT_CHECK = "Couldn\u0027t check"

    private const val MAXIMUM_TITLE_WORDS = 4
    private val Separators = Regex("""\s+[+|/–-]\s+|,\s+|\s+\(|\s+with\s+""")
    private val Whitespace = Regex("""\s+""")
}

/** The label of every problem's button except sign-in (DESIGN 3.9). */
private const val FIX = "Fix"

/** The domain of eBay UK, for copy that names it ("ebay.co.uk"). */
internal val UkDomain: String = EbaySite.UK.host.removePrefix("www.")
