package app.kestrel.ui.screens.home

import app.kestrel.platform.ReadinessItem
import app.kestrel.platform.ReadinessKey
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.fixture.LotMoment
import app.kestrel.ui.model.Connectivity
import java.time.Instant

/**
 * S1 in each of its states, built from the design fixture (DESIGN Appendix A) through the real builder and the real
 * status mapper, so every screenshot runs the code the phone runs.
 */
object HomeFixtures {
    private val fixture = CatalogueFixture

    // Readiness checks, as the platform's evaluator words them.

    private fun item(
        key: ReadinessKey,
        ok: Boolean,
        detail: String,
    ) = ReadinessItem(key, ok, detail)

    private fun checks(vararg failing: ReadinessKey): List<ReadinessItem> =
        listOf(
            item(ReadinessKey.EBAY_SESSION, ReadinessKey.EBAY_SESSION !in failing, "Signed in as ${fixture.ACCOUNT}"),
            item(ReadinessKey.NOTIFICATIONS, ReadinessKey.NOTIFICATIONS !in failing, "Results and alerts allowed"),
            item(ReadinessKey.EXACT_ALARMS, ReadinessKey.EXACT_ALARMS !in failing, "Granted automatically"),
            item(ReadinessKey.BATTERY, ReadinessKey.BATTERY !in failing, "Optimised: snipes may run late."),
            item(ReadinessKey.BACKGROUND, true, "Not restricted"),
            item(ReadinessKey.UNUSED_APP_PAUSE, true, "Off: Kestrel stays armed even if you don’t open it"),
            // Optional: never counted as a problem on S1.
            item(ReadinessKey.LIVE_UPDATES, false, "The final countdown can’t show in the status bar."),
            item(ReadinessKey.CLOCK, true, "Phone clock is right, corrected (checked 1 min ago)"),
            item(ReadinessKey.NETWORK, true, "Connected (Wi-Fi)"),
            item(ReadinessKey.DRY_RUN, ReadinessKey.DRY_RUN !in failing, "Last dry run passed Sun 18:11"),
        )

    val batteryRestricted: List<ReadinessItem> = checks(ReadinessKey.BATTERY)
    val allPassed: List<ReadinessItem> = checks()
    val signedOut: List<ReadinessItem> = checks(ReadinessKey.EBAY_SESSION)
    val batteryAndSignedOut: List<ReadinessItem> = checks(ReadinessKey.BATTERY, ReadinessKey.EBAY_SESSION)

    /** The checks made at 18:06 (DESIGN Appendix A). */
    fun readiness(
        items: List<ReadinessItem>?,
        checking: Boolean = false,
    ): ReadinessSnapshot = ReadinessSnapshot(items, fixture.readinessCheckedAt.takeIf { items != null }, checking)

    // The list at 18:08:33.

    val listMoments: List<LotMoment>
        get() =
            listOf(
                fixture.canonArmed,
                fixture.omegaOverMax,
                fixture.miniDiscArmed,
                fixture.walkmanArmed,
                fixture.polaroidWatching,
                fixture.gameBoyArmed,
                fixture.gameAndWatchWon,
                fixture.nintendo64Lost,
            )

    fun input(
        moments: List<LotMoment>,
        now: Instant = fixture.now,
        connectivity: Connectivity = Connectivity.Online,
        readiness: ReadinessSnapshot = readiness(batteryRestricted),
        accountName: String? = fixture.ACCOUNT,
        refreshing: Boolean = false,
    ): HomeInput =
        HomeInput(
            now = now,
            snipes = moments.map { it.snipe }.sortedWith(compareBy({ it.endTimeUtcMillis }, { it.id })),
            timelines = moments.associate { it.snipe.id to it.events },
            groups = listOf(fixture.portableAudio),
            connectivity = connectivity,
            readiness = readiness,
            accountName = accountName,
            defaultLeadSeconds = 6,
            refreshing = refreshing,
            photoOf = fixture::photoOf,
        )

    fun build(input: HomeInput): HomeUiState = HomeStateBuilder.build(input)

    /** S1 at 18:08:33 (DESIGN S1): the battery is restricted. */
    val home: HomeUiState get() = build(input(listMoments))

    /** Nothing on the list; every check passed at 18:06. */
    val empty: HomeUiState get() = build(input(emptyList(), readiness = readiness(allPassed), refreshing = false))

    /** Offline since 18:02, with the Canon's bid due within the hour. */
    val offline: HomeUiState
        get() = build(input(listMoments, connectivity = Connectivity.Offline(fixture.millis("2025-11-09T18:02:00Z"))))

    /** Signed out of eBay: the pill says so once and armed lots are paused. */
    val signedOutHome: HomeUiState get() = build(input(listMoments, readiness = readiness(signedOut)))

    /** Two problems at once. */
    val severalProblems: HomeUiState get() = build(input(listMoments, readiness = readiness(batteryAndSignedOut)))

    /** Every check passed. */
    val ready: HomeUiState get() = build(input(listMoments, readiness = readiness(allPassed)))

    /** The checks run again: the pill keeps its look and says "Checking…". */
    val rechecking: HomeUiState get() =
        build(
            input(listMoments, readiness = readiness(batteryRestricted, checking = true)),
        )

    /** The first check has not answered yet. */
    val firstCheck: HomeUiState get() = build(input(listMoments, readiness = readiness(null, checking = true)))

    /** 18:22:05: the Canon was won, so the Omega (over its max) is the hero. */
    val attentionHero: HomeUiState
        get() {
            val now = fixture.at("2025-11-09T18:22:05Z")
            return build(input(listOf(fixture.canonWon) + listMoments.drop(1), now = now))
        }

    /** 18:21:04: the Canon's final minute. */
    val finalMinuteHero: HomeUiState
        get() = build(input(listOf(fixture.canonFinalMinute) + listMoments.drop(1), now = fixture.canonFinalMinute.now))

    /** 18:21:34.4: the bid left at 18:21:33.9 and eBay has not replied. */
    val bidSentHero: HomeUiState
        get() = build(input(listOf(fixture.canonBidSent) + listMoments.drop(1), now = fixture.canonBidSent.now))

    /** 18:21:35.2: eBay replied that the bid leads. */
    val inTheLeadHero: HomeUiState
        get() = build(input(listOf(fixture.canonInTheLead) + listMoments.drop(1), now = fixture.canonInTheLead.now))

    /** 18:21:35.2: eBay replied that another bidder's maximum is higher. */
    val outbidHero: HomeUiState
        get() = build(input(listOf(fixture.canonOutbid) + listMoments.drop(1), now = fixture.canonOutbid.now))

    /** Tue 11 Nov 19:55:58: Kestrel's bid reset the Walkman's clock to 19:56:42. */
    val extendedHero: HomeUiState
        get() {
            val moments =
                listOf(
                    fixture.walkmanExtended,
                    fixture.polaroidWatching,
                    fixture.gameBoyArmed,
                    fixture.canonWon,
                    fixture.gameAndWatchWon,
                    fixture.nintendo64Lost,
                )
            return build(input(moments, now = fixture.walkmanExtended.now, readiness = readiness(allPassed)))
        }

    /** One lot in [moment]'s state at its own time, as a row would show it. */
    fun lotOf(
        moment: LotMoment,
        readiness: ReadinessSnapshot = readiness(allPassed),
    ): HomeLot {
        val state =
            build(input(listOf(moment), now = moment.now, connectivity = moment.connectivity, readiness = readiness))
        return state.sections
            .flatMap { it.blocks }
            .flatMap { it.lots }
            .single()
    }

    /** One moment per status, in ladder order (the gallery's list). */
    val statusMoments: List<LotMoment>
        get() =
            listOf(
                fixture.polaroidWatching,
                fixture.miniDiscArmed,
                fixture.omegaOverMax,
                fixture.canonSignInNeeded,
                fixture.canonCaptcha,
                fixture.canonPhoneCheck,
                fixture.canonDryRunFailed,
                fixture.canonOffline,
                fixture.canonUnreachable,
                fixture.omegaNeedsYou,
                fixture.canonFinalMinute,
                fixture.canonBidSent,
                fixture.canonInTheLead,
                fixture.canonOutbid,
                fixture.canonNoReply,
                fixture.walkmanExtended,
                fixture.canonReadingResult,
                fixture.gameAndWatchWon,
                fixture.nintendo64Lost,
                fixture.gameBoyEndedEarly,
                fixture.gameBoyCancelled,
                fixture.walkmanCancelledByGroup,
                fixture.canonBidNotSent,
                fixture.canonBidFailed,
            )
}
