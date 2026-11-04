package app.kestrel.ui.screens.home

import app.kestrel.platform.ReadinessItem
import app.kestrel.platform.ReadinessKey
import app.kestrel.ui.components.Symbols
import app.kestrel.ui.format.clock
import app.kestrel.ui.format.clockFor
import app.kestrel.ui.format.dayDate
import app.kestrel.ui.format.isSameDay
import java.time.Duration
import java.time.Instant

/**
 * The dock's readiness pill (DESIGN 3.8 and 6.2), from the platform's readiness checks, the connection and the lots.
 *
 * - All good: "Ready to snipe" / "All checks passed at 18:06".
 * - One problem: its name and its consequence, with "Fix" for the exact system page ("Battery restricted" /
 *   "A bid could go out late"). Signed out has its own words and "Sign in".
 * - Several problems: "2 things need attention" / "Battery restricted · signed out" with "Review".
 * - Offline with a bid due within the hour: "Phone offline" / "The 18:21:34 bid can’t go out", no button.
 * - While a check runs the second line reads "Checking…" and the rest stays.
 *
 * Live Updates are optional (S5 lists them as recommended), so they never count as a problem here. The network item
 * is left to the live connection, because a check made while offline goes stale the moment the phone reconnects.
 */
internal object ReadinessPills {
    const val CHECKING = "Checking…"

    /**
     * The order problems are named in on the second line, most serious first: signed out pauses every snipe and
     * offline stops every bid, so an ellipsis never hides them behind a battery setting.
     */
    private val dockOrder =
        listOf(
            ReadinessKey.EBAY_SESSION,
            ReadinessKey.NETWORK,
            ReadinessKey.BATTERY,
            ReadinessKey.EXACT_ALARMS,
            ReadinessKey.BACKGROUND,
            ReadinessKey.UNUSED_APP_PAUSE,
            ReadinessKey.NOTIFICATIONS,
            ReadinessKey.CLOCK,
            ReadinessKey.DRY_RUN,
        )

    /** A pass older than this is shown as when the phone was last checked, not as a pass. */
    private val PASS_STAYS_FRESH: Duration = Duration.ofMinutes(30)

    private val notCounted = setOf(ReadinessKey.LIVE_UPDATES, ReadinessKey.NETWORK)

    /** Before the first check has answered. Line 2 says how long, rather than saying "checking" twice. */
    fun checkingFirstTime(): ReadinessPill =
        ReadinessPill(PillKind.Checking, Symbols.HourglassTop, "Checking this phone", "This takes a few seconds")

    /**
     * [items] are the latest checks (null before the first one), made at [checkedAt]; [checking] is true while a new
     * check runs. [offlineBidAt] is the earliest bid due within the hour, when the phone is [offline].
     * [lotsNeedSignIn] is true when a lot already met eBay's sign-in page, which counts as signed out.
     */
    fun of(
        items: List<ReadinessItem>?,
        checkedAt: Instant?,
        checking: Boolean,
        offline: Boolean,
        offlineBidAt: Instant?,
        lotsNeedSignIn: Boolean,
        now: Instant,
    ): ReadinessPill {
        val problems = problems(items, offline, lotsNeedSignIn)
        val pill =
            when {
                offline && offlineBidAt != null -> {
                    ReadinessPill(
                        kind = PillKind.Offline,
                        icon = Symbols.CloudOff,
                        title = "Phone offline",
                        detail = "The ${clockFor(now, offlineBidAt)} bid can’t go out",
                    )
                }

                items == null && problems.isEmpty() -> {
                    checkingFirstTime()
                }

                problems.isEmpty() -> {
                    passed(checkedAt, now)
                }

                problems.size == 1 -> {
                    single(problems.single())
                }

                else -> {
                    ReadinessPill(
                        kind = PillKind.SeveralProblems,
                        icon = Symbols.Warning,
                        title = "${problems.size} things need attention",
                        detail =
                            problems
                                .joinToString(" · ") { shortName(it) }
                                .replaceFirstChar { it.uppercase() },
                        action = PillAction.Review,
                    )
                }
            }
        return if (checking && pill.kind != PillKind.Checking) pill.copy(detail = CHECKING) else pill
    }

    /** The keys that need the user, in dock order. */
    fun problems(
        items: List<ReadinessItem>?,
        offline: Boolean,
        lotsNeedSignIn: Boolean,
    ): List<ReadinessKey> {
        val failing =
            items
                .orEmpty()
                .filter { !it.ok && it.key !in notCounted }
                .map { it.key }
                .toMutableSet()
        if (offline) failing += ReadinessKey.NETWORK
        if (lotsNeedSignIn) failing += ReadinessKey.EBAY_SESSION
        return dockOrder.filter { it in failing }
    }

    /**
     * Every check passed: "All checks passed at 18:06". Never "passed" without a check to show for it, and a pass
     * older than half an hour says only when the phone was last checked, in ink soft, while it is checked again.
     */
    private fun passed(
        checkedAt: Instant?,
        now: Instant,
    ): ReadinessPill =
        when {
            checkedAt == null -> {
                checkingFirstTime()
            }

            Duration.between(checkedAt, now) > PASS_STAYS_FRESH -> {
                ReadinessPill(PillKind.Checking, Symbols.Info, "Ready to snipe", lastChecked(checkedAt, now))
            }

            else -> {
                ReadinessPill(PillKind.Ready, Symbols.CheckCircle, "Ready to snipe", passedLine(checkedAt, now))
            }
        }

    private fun passedLine(
        checkedAt: Instant,
        now: Instant,
    ): String =
        if (isSameDay(now, checkedAt)) {
            "All checks passed at ${checkedAt.clock()}"
        } else {
            "All checks passed ${checkedAt.dayDate()} at ${checkedAt.clock()}"
        }

    /** "Last checked at 07:00", "Last checked Sat 8 Nov at 21:40": an old pass, while the phone is checked again. */
    private fun lastChecked(
        checkedAt: Instant,
        now: Instant,
    ): String =
        if (isSameDay(now, checkedAt)) {
            "Last checked at ${checkedAt.clock()}"
        } else {
            "Last checked ${checkedAt.dayDate()} at ${checkedAt.clock()}"
        }

    private fun single(key: ReadinessKey): ReadinessPill =
        when (key) {
            ReadinessKey.EBAY_SESSION -> {
                ReadinessPill(
                    PillKind.SignedOut,
                    Symbols.AccountCircle,
                    "Signed out of eBay",
                    "Snipes are paused",
                    PillAction.SignIn,
                )
            }

            ReadinessKey.NETWORK -> {
                ReadinessPill(PillKind.Offline, Symbols.CloudOff, "Phone offline", "Kestrel can’t check or bid")
            }

            ReadinessKey.DRY_RUN -> {
                ReadinessPill(
                    PillKind.OneProblem,
                    Symbols.TaskAlt,
                    "Dry run failed",
                    "A bid could fail",
                    PillAction.Review,
                )
            }

            else -> {
                ReadinessPill(PillKind.OneProblem, iconOf(key), titleOf(key), consequenceOf(key), PillAction.Fix(key))
            }
        }

    private fun iconOf(key: ReadinessKey): Int =
        when (key) {
            ReadinessKey.BATTERY, ReadinessKey.BACKGROUND -> Symbols.BatteryAlert
            ReadinessKey.EXACT_ALARMS -> Symbols.AlarmOn
            ReadinessKey.NOTIFICATIONS -> Symbols.Notifications
            ReadinessKey.CLOCK -> Symbols.Timer
            ReadinessKey.LIVE_UPDATES -> Symbols.Update
            ReadinessKey.DRY_RUN -> Symbols.TaskAlt
            ReadinessKey.EBAY_SESSION -> Symbols.AccountCircle
            ReadinessKey.NETWORK -> Symbols.CloudOff
            ReadinessKey.UNUSED_APP_PAUSE -> Symbols.Info
        }

    private fun titleOf(key: ReadinessKey): String =
        when (key) {
            ReadinessKey.BATTERY -> "Battery restricted"
            ReadinessKey.EXACT_ALARMS -> "Exact alarms off"
            ReadinessKey.BACKGROUND -> "Background restricted"
            ReadinessKey.UNUSED_APP_PAUSE -> "App pausing is on"
            ReadinessKey.NOTIFICATIONS -> "Notifications off"
            ReadinessKey.CLOCK -> "Phone clock is off"
            ReadinessKey.LIVE_UPDATES -> "Live Updates off"
            ReadinessKey.DRY_RUN -> "Dry run failed"
            ReadinessKey.EBAY_SESSION -> "Signed out of eBay"
            ReadinessKey.NETWORK -> "Phone offline"
        }

    private fun consequenceOf(key: ReadinessKey): String =
        when (key) {
            ReadinessKey.BATTERY, ReadinessKey.EXACT_ALARMS, ReadinessKey.BACKGROUND -> "A bid could go out late"
            ReadinessKey.UNUSED_APP_PAUSE -> "Android may pause Kestrel if unused"
            ReadinessKey.NOTIFICATIONS -> "You won’t hear results or alerts"
            ReadinessKey.CLOCK -> "Turn on automatic time"
            ReadinessKey.LIVE_UPDATES -> "No countdown in the status bar"
            ReadinessKey.DRY_RUN -> "A bid could fail"
            ReadinessKey.EBAY_SESSION -> "Snipes are paused"
            ReadinessKey.NETWORK -> "Kestrel can’t check or bid"
        }

    /** How a problem is named in a list of several: "battery restricted", "signed out". */
    private fun shortName(key: ReadinessKey): String =
        when (key) {
            ReadinessKey.EBAY_SESSION -> "signed out"
            ReadinessKey.NETWORK -> "offline"
            else -> titleOf(key).replaceFirstChar { it.lowercase() }
        }
}
