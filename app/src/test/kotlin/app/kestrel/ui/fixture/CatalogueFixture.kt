package app.kestrel.ui.fixture

import app.kestrel.data.PricePoint
import app.kestrel.ebay.MoneyText
import app.kestrel.model.EbaySite
import app.kestrel.model.ItemRef
import app.kestrel.model.Listing
import app.kestrel.model.ListingStatus
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeResult
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineEvent
import app.kestrel.model.TimelineKind
import app.kestrel.ui.harness.FixturePhotos
import app.kestrel.ui.model.Connectivity
import app.kestrel.ui.model.GroupContext
import app.kestrel.ui.model.LotPhoto
import app.kestrel.ui.model.LotStatus
import app.kestrel.ui.model.LotStatusMapper
import app.kestrel.ui.model.PhotoStyle
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.Locale

/**
 * One snipe at one moment, as the app would read it: the stored [snipe], its timeline [events] (shaped like the
 * engine writes them), the time and the connection. [status] runs the real mapper, so screenshots exercise it.
 */
data class LotMoment(
    val snipe: Snipe,
    val events: List<TimelineEvent>,
    val now: Instant,
    val connectivity: Connectivity = Connectivity.Online,
    val group: GroupContext? = null,
    val pricePoints: List<PricePoint> = emptyList(),
) {
    val status: LotStatus get() = LotStatusMapper.statusOf(snipe, events, now, connectivity, group)

    val photo: LotPhoto get() = CatalogueFixture.photoOf(snipe)
}

/**
 * The design fixture (DESIGN Appendix A) in the app's own types. "Now" is Sun 9 Nov 2025, 18:08:33 GMT; the 2025
 * calendar matches the brief's weekdays and no screen shows the year. Every day label is formatted from these
 * instants, never written out.
 */
object CatalogueFixture {
    val now: Instant = at("2025-11-09T18:08:33Z")

    /** A neutral eBay account name for the fixture. */
    const val ACCOUNT = "lot.42"

    /** When the account signed in on the phone. */
    val signedInSince: Instant = at("2025-11-02T10:31:00Z")

    /** The last full readiness check before S1 (18:06) and S5's render time (18:13:52, after the 18:11:40 dry run). */
    val readinessCheckedAt: Instant = at("2025-11-09T18:06:00Z")
    val readyScreenNow: Instant = at("2025-11-09T18:13:52Z")

    val portableAudio = SnipeGroup(id = 1, name = "Portable audio", targetWins = 1)

    // The list at 18:08:33.

    val canon =
        snipe(
            id = 1,
            itemId = "126771134521",
            title = "Canon AE-1 Program + 50mm f/1.8",
            photo = "canon_ae1",
            max = gbp("120.00"),
            status = SnipeStatus.ARMED,
            end = "2025-11-09T18:21:40Z",
            price = gbp("88.00"),
            bids = 14,
            postage = gbp("6.50"),
            created = "2025-11-09T09:14:02Z",
            lastChecked = "2025-11-09T18:00:12Z",
            dryRunAt = "2025-11-09T18:11:40.200Z",
        )

    val omega =
        snipe(
            id = 2,
            itemId = "205880412977",
            title = "Omega Speedmaster “Pre-moon” 1960s",
            photo = "speedmaster",
            max = gbp("1900.00"),
            status = SnipeStatus.PRICE_OVER_MAX,
            end = "2025-11-09T21:02:15Z",
            price = gbp("2150.00"),
            bids = 31,
            postage = gbp("12.95"),
            created = "2025-11-06T20:41:09Z",
            lastChecked = "2025-11-09T18:02:47Z",
        )

    val miniDisc =
        snipe(
            id = 3,
            itemId = "146690203318",
            title = "Sony MiniDisc Walkman MZ-N707",
            photo = "minidisc",
            max = gbp("45.00"),
            status = SnipeStatus.ARMED,
            end = "2025-11-10T20:10:05Z",
            price = gbp("24.00"),
            bids = 5,
            postage = gbp("3.49"),
            created = "2025-11-08T11:02:30Z",
            lastChecked = "2025-11-09T14:30:00Z",
            groupId = portableAudio.id,
        )

    val walkman =
        snipe(
            id = 4,
            itemId = "187522914460",
            title = "Sony Walkman WM-FX421",
            photo = "walkman",
            max = gbp("40.00"),
            status = SnipeStatus.ARMED,
            end = "2025-11-11T19:55:48Z",
            price = gbp("18.50"),
            bids = 3,
            postage = gbp("3.20"),
            created = "2025-11-08T11:05:12Z",
            lastChecked = "2025-11-09T12:20:00Z",
            groupId = portableAudio.id,
            extendedBidding = true,
        )

    val polaroid =
        snipe(
            id = 5,
            itemId = "116833290154",
            title = "Polaroid SX-70 Land Camera",
            photo = "polaroid",
            max = null,
            status = SnipeStatus.WATCHING,
            end = "2025-11-13T19:30:00Z",
            price = gbp("61.00"),
            bids = 9,
            postage = gbp("7.50"),
            created = "2025-11-07T08:15:40Z",
            lastChecked = "2025-11-09T07:00:00Z",
        )

    val gameBoy =
        snipe(
            id = 6,
            itemId = "296611804325",
            title = "Nintendo Game Boy DMG-01, boxed",
            photo = "gameboy",
            max = gbp("72.37"),
            status = SnipeStatus.ARMED,
            end = "2025-11-16T21:43:52Z",
            price = gbp("46.00"),
            bids = 11,
            postage = gbp("4.10"),
            created = "2025-11-09T13:40:18Z",
            lastChecked = "2025-11-09T13:40:18Z",
        )

    val gameAndWatch =
        snipe(
            id = 7,
            itemId = "135602278841",
            title = "Game & Watch Donkey Kong II",
            photo = "gameandwatch",
            max = gbp("45.00"),
            status = SnipeStatus.WON,
            end = "2025-11-08T20:14:09Z",
            price = gbp("34.00"),
            bids = 8,
            postage = gbp("3.10"),
            created = "2025-11-04T19:22:51Z",
            lastChecked = "2025-11-08T20:14:31Z",
            result =
                SnipeResult(
                    outcome = SnipeOutcome.WON,
                    finalPrice = gbp("34.00"),
                    bidSentAtUtcMillis = millis("2025-11-08T20:14:03.100Z"),
                    bidLeadMillis = 5_900,
                    replyLatencyMillis = 700,
                    ebayReplyText = "Your current bid puts you in the lead.",
                    reason = "eBay says you are the winning bidder.",
                ),
        )

    val nintendo64 =
        snipe(
            id = 8,
            itemId = "157306622019",
            title = "Nintendo 64 console",
            photo = "n64",
            max = gbp("65.00"),
            status = SnipeStatus.LOST,
            end = "2025-11-07T19:41:00Z",
            price = gbp("79.00"),
            bids = 17,
            postage = gbp("6.99"),
            created = "2025-11-05T21:12:44Z",
            lastChecked = "2025-11-07T19:41:05.300Z",
            dryRunAt = "2025-11-07T19:31:02.400Z",
            result =
                SnipeResult(
                    outcome = SnipeOutcome.LOST,
                    finalPrice = gbp("79.00"),
                    bidSentAtUtcMillis = millis("2025-11-07T19:40:54.000Z"),
                    bidLeadMillis = 6_000,
                    replyLatencyMillis = 900,
                    ebayReplyText = "You’ve been outbid. Another bidder’s maximum is higher.",
                    reason = "eBay says another bidder won.",
                ),
        )

    /** Every snipe on the list, soonest end first, as the repository returns them. */
    val snipes: List<Snipe> =
        listOf(canon, omega, miniDisc, walkman, polaroid, gameBoy, gameAndWatch, nintendo64)
            .sortedBy { it.endTimeUtcMillis }

    /** The S2 listing, outside the list: a UK private seller's auction, fetched at 18:09:10. */
    val sonyA700: Listing =
        Listing(
            ref = ItemRef(EbaySite.UK, "127504318862"),
            title = "Sony Alpha A700 body",
            imageUrl = FixturePhotos.url("sony_a700"),
            sellerName = "camerashed_uk",
            isAuction = true,
            status = ListingStatus.ACTIVE,
            endTimeUtcMillis = millis("2025-11-13T20:04:18Z"),
            scheduledEndUtcMillis = millis("2025-11-13T20:04:18Z"),
            currentPrice = gbp("64.00"),
            currentPriceInSiteCurrency = null,
            bidCount = 7,
            hasReserve = false,
            reserveMet = null,
            postage = gbp("8.95"),
            buyerProtectionFee = gbp("3.13"),
            buyItNow = null,
            extendedBidding = false,
            userIsHighBidder = null,
            userBiddingStatus = null,
            endedText = null,
            endedEarly = false,
            signedIn = true,
            relistedItemId = null,
            fetchedAtUtcMillis = millis("2025-11-09T18:09:10Z"),
        )

    /** How each fixture photo sits on its plate (DESIGN 2.4: the Canon and the Polaroid are scenes). */
    fun photoOf(snipe: Snipe): LotPhoto {
        val url = snipe.imageUrl
        return when (url) {
            FixturePhotos.url("canon_ae1") -> LotPhoto(url, PhotoStyle.Scene, focusY = -0.25f)
            FixturePhotos.url("polaroid") -> LotPhoto(url, PhotoStyle.Scene, focusY = -0.2f)
            else -> LotPhoto(url)
        }
    }

    // The list at 18:08:33, with each snipe's timeline.

    val canonArmed: LotMoment get() = LotMoment(canon, canonUntilDryRun(), now)
    val omegaOverMax: LotMoment get() = LotMoment(omega, added(omega), now)
    val miniDiscArmed: LotMoment get() = LotMoment(miniDisc, added(miniDisc), now)
    val walkmanArmed: LotMoment get() = LotMoment(walkman, added(walkman), now)
    val polaroidWatching: LotMoment get() = LotMoment(polaroid, added(polaroid), now)
    val gameBoyArmed: LotMoment get() = LotMoment(gameBoy, added(gameBoy), now)
    val gameAndWatchWon: LotMoment get() = LotMoment(gameAndWatch, added(gameAndWatch), now)
    val nintendo64Lost: LotMoment get() = LotMoment(nintendo64, nintendo64Timeline(), at("2025-11-07T19:42:10Z"))

    // S3 and S4 moments of the Canon.

    /** S3 final minute at 18:21:04: £96.00 (up from £88.00), 16 bids, edits allowed until 18:21:31. */
    val canonFinalMinute: LotMoment
        get() =
            LotMoment(
                snipe = canon.copy(status = SnipeStatus.BIDDING, lastPrice = gbp("96.00"), lastBidCount = 16),
                events = canonUntilWindow(),
                now = at("2025-11-09T18:21:04Z"),
                pricePoints = canonPrices,
            )

    /** S3 bid sent at 18:21:34.4: the bid left at 18:21:33.9, eBay has not replied. */
    val canonBidSent: LotMoment
        get() =
            LotMoment(
                snipe = canon.copy(status = SnipeStatus.BID_SENT, lastPrice = gbp("96.00"), lastBidCount = 16),
                events = canonUntilWindow() + bidSent(canon, "2025-11-09T18:21:33.900Z", leadMillis = 6_100),
                now = at("2025-11-09T18:21:34.400Z"),
                pricePoints = canonPrices,
            )

    /** No reply 5.6 s after the bid. */
    val canonNoReply: LotMoment
        get() = canonBidSent.copy(now = at("2025-11-09T18:21:39.500Z"))

    /** S3 in the lead at 18:21:35.2: eBay replied in 0.8 s. */
    val canonInTheLead: LotMoment
        get() =
            LotMoment(
                snipe = canon.copy(status = SnipeStatus.LEADING, lastPrice = gbp("98.00"), lastBidCount = 17),
                events = canonUntilReply("Leading", "eBay: \"Your current bid puts you in the lead.\""),
                now = at("2025-11-09T18:21:35.200Z"),
                pricePoints = canonPrices,
            )

    /** S3 outbid: eBay's reply says another bidder's maximum is higher. */
    val canonOutbid: LotMoment
        get() =
            LotMoment(
                snipe = canon.copy(status = SnipeStatus.OUTBID, lastPrice = gbp("122.00"), lastBidCount = 18),
                events =
                    canonUntilReply(
                        "Outbid",
                        "eBay: \"You’ve been outbid. Another bidder’s maximum is higher.\" " +
                            "Another bidder's max is higher than yours.",
                    ),
                now = at("2025-11-09T18:21:35.200Z"),
                pricePoints = canonPrices,
            )

    /** The auction ended at 18:21:40 and the result is not read yet. */
    val canonReadingResult: LotMoment
        get() = canonInTheLead.copy(now = at("2025-11-09T18:21:41.500Z"))

    /** S4 won at 18:22:05: won for £101.00, confirmed on the listing page at 18:21:46.2. */
    val canonWon: LotMoment
        get() =
            LotMoment(
                snipe =
                    canon.copy(
                        status = SnipeStatus.WON,
                        lastPrice = gbp("101.00"),
                        lastBidCount = 17,
                        result =
                            SnipeResult(
                                outcome = SnipeOutcome.WON,
                                finalPrice = gbp("101.00"),
                                bidSentAtUtcMillis = millis("2025-11-09T18:21:33.900Z"),
                                bidLeadMillis = 6_100,
                                replyLatencyMillis = 800,
                                ebayReplyText = "Your current bid puts you in the lead.",
                                reason = "eBay says you are the winning bidder.",
                            ),
                    ),
                events =
                    canonUntilReply("Leading", "eBay: \"Your current bid puts you in the lead.\"") +
                        listOf(
                            event(
                                canon,
                                30,
                                "2025-11-09T18:21:46.000Z",
                                TimelineKind.ENDED,
                                "The auction ended at £101.00.",
                                "finalPrice" to "GBP 101.00",
                                "bidCount" to 17,
                            ),
                            event(
                                canon,
                                31,
                                "2025-11-09T18:21:46.200Z",
                                TimelineKind.RESULT,
                                "eBay says you are the winning bidder.",
                                "outcome" to "WON",
                            ),
                        ),
                now = at("2025-11-09T18:22:05Z"),
                pricePoints = canonPrices,
            )

    /** S4 bid not sent at 18:22:05: eBay signed the user out at 18:21:20, before the bid. */
    val canonBidNotSent: LotMoment
        get() {
            val reason = "eBay asked you to sign in before bidding. Sign in to eBay in Kestrel again."
            return LotMoment(
                snipe =
                    canon.copy(
                        status = SnipeStatus.FAILED,
                        lastPrice = gbp("104.00"),
                        lastBidCount = 18,
                        result = SnipeResult(SnipeOutcome.FAILED, gbp("104.00"), null, null, null, null, reason),
                    ),
                events =
                    canonUntilWindow() +
                        listOf(
                            event(
                                canon,
                                20,
                                "2025-11-09T18:21:20.300Z",
                                TimelineKind.NEEDS_USER,
                                reason,
                                "reason" to "SIGN_IN",
                            ),
                            event(
                                canon,
                                21,
                                "2025-11-09T18:21:20.400Z",
                                TimelineKind.RESULT,
                                reason,
                                "outcome" to "FAILED",
                            ),
                        ),
                now = at("2025-11-09T18:22:05Z"),
            )
        }

    /** eBay refused the bid. */
    val canonBidFailed: LotMoment
        get() {
            val text = "Your bid wasn’t placed. Please try again."
            return LotMoment(
                snipe =
                    canon.copy(
                        status = SnipeStatus.FAILED,
                        lastPrice = gbp("104.00"),
                        lastBidCount = 18,
                        result =
                            SnipeResult(
                                outcome = SnipeOutcome.FAILED,
                                finalPrice = gbp("104.00"),
                                bidSentAtUtcMillis = millis("2025-11-09T18:21:33.900Z"),
                                bidLeadMillis = 6_100,
                                replyLatencyMillis = 900,
                                ebayReplyText = text,
                                reason = "eBay refused the bid: \"$text\"",
                            ),
                    ),
                events = canonUntilReply("Rejected", "eBay refused the bid: \"$text\""),
                now = at("2025-11-09T18:22:05Z"),
            )
        }

    /** S3 captcha at 18:12:05: a check found eBay's human check instead of the listing. */
    val canonCaptcha: LotMoment
        get() =
            LotMoment(
                snipe = canon.copy(status = SnipeStatus.NEEDS_YOU),
                events =
                    canonUntilDryRun() +
                        event(
                            canon,
                            12,
                            "2025-11-09T18:12:01.500Z",
                            TimelineKind.NEEDS_USER,
                            "eBay showed a security check. Solve it on eBay's page so Kestrel can bid.",
                            "reason" to "CAPTCHA",
                        ),
                now = at("2025-11-09T18:12:05Z"),
            )

    /** eBay asked for a code by text during the dry run. */
    val canonPhoneCheck: LotMoment
        get() =
            LotMoment(
                snipe = canon.copy(status = SnipeStatus.DRY_RUN_FAILED, dryRunPassed = false),
                events =
                    added(canon) +
                        event(
                            canon,
                            5,
                            "2025-11-09T18:11:40.200Z",
                            TimelineKind.DRY_RUN,
                            "Dry run failed: eBay needs you before it accepts a bid: Confirm it's you. " +
                                "No bid was placed.",
                            "reason" to "VERIFICATION",
                        ),
                now = at("2025-11-09T18:12:05Z"),
            )

    /** The dry run met eBay's sign-in page. */
    val canonDryRunFailed: LotMoment
        get() =
            LotMoment(
                snipe = canon.copy(status = SnipeStatus.DRY_RUN_FAILED, dryRunPassed = false),
                events =
                    added(canon) +
                        event(
                            canon,
                            5,
                            "2025-11-09T18:11:40.200Z",
                            TimelineKind.DRY_RUN,
                            "Dry run failed: eBay asked you to sign in before bidding. No bid was placed.",
                            "reason" to "SIGN_IN",
                        ),
                now = at("2025-11-09T18:12:05Z"),
            )

    /** A check found the eBay session gone. */
    val canonSignInNeeded: LotMoment
        get() = LotMoment(canon.copy(status = SnipeStatus.NEEDS_SIGN_IN), canonUntilDryRun(), now)

    /** S1 offline since 18:02, with the Canon's bid due within the hour. */
    val canonOffline: LotMoment
        get() = canonArmed.copy(connectivity = Connectivity.Offline(millis("2025-11-09T18:02:00Z")))

    /** Three checks in a row failed inside the final hour. */
    val canonUnreachable: LotMoment
        get() =
            LotMoment(
                snipe = canon.copy(lastCheckedAtUtcMillis = millis("2025-11-09T18:05:00.000Z")),
                events =
                    added(canon) +
                        listOf("17:35:00", "17:50:00", "18:05:00").mapIndexed { index, time ->
                            event(
                                canon,
                                40L + index,
                                "2025-11-09T$time.200Z",
                                TimelineKind.PROBLEM,
                                "Loading the item page timed out after 25 s.",
                            )
                        },
                now = now,
            )

    /** A pending window that would bid but the price passed the max in the final minute. */
    val canonOverMaxInTheWindow: LotMoment
        get() = canonFinalMinute.copy(snipe = canonFinalMinute.snipe.copy(lastPrice = gbp("121.00"), lastBidCount = 19))

    /** S3 extended (Walkman, Tue 11 Nov 19:55:58): Kestrel's bid at 19:55:42 reset the clock to 19:56:42. */
    val walkmanExtended: LotMoment
        get() {
            val extendedEnd = "2025-11-11T19:56:42Z"
            return LotMoment(
                snipe =
                    walkman.copy(
                        status = SnipeStatus.EXTENDED,
                        endTimeUtcMillis = millis(extendedEnd),
                        lastPrice = gbp("19.50"),
                        lastBidCount = 4,
                    ),
                events =
                    added(walkman) +
                        listOf(
                            windowOpened(walkman, "2025-11-11T19:53:48Z"),
                            bidSent(walkman, "2025-11-11T19:55:42.000Z", leadMillis = 6_000),
                            event(
                                walkman,
                                24,
                                "2025-11-11T19:55:42.700Z",
                                TimelineKind.EBAY_REPLY,
                                "eBay: \"Your current bid puts you in the lead.\"",
                                "reply" to "Leading",
                                "latencyMillis" to 700,
                                "attempt" to 1,
                            ),
                            event(
                                walkman,
                                25,
                                "2025-11-11T19:55:44.100Z",
                                TimelineKind.EXTENDED,
                                "eBay extended the auction by 54 s (extension 1).",
                                "previousEndUtcMillis" to millis("2025-11-11T19:55:48Z"),
                                "endUtcMillis" to millis(extendedEnd),
                            ),
                        ),
                now = at("2025-11-11T19:55:58Z"),
            )
        }

    /** The MiniDisc was won, so the group cancelled the Walkman (DESIGN Group screen, after a win). */
    val walkmanCancelledByGroup: LotMoment
        get() =
            LotMoment(
                snipe = walkman.copy(status = SnipeStatus.SKIPPED_BY_GROUP),
                events = added(walkman),
                now = at("2025-11-10T20:12:00Z"),
                group = GroupContext(portableAudio.name, wonTitle = miniDisc.title),
            )

    /** The user stopped the Game Boy. */
    val gameBoyCancelled: LotMoment
        get() =
            LotMoment(
                snipe = gameBoy.copy(status = SnipeStatus.CANCELLED),
                events =
                    added(gameBoy) +
                        event(gameBoy, 9, "2025-11-08T16:20:00Z", TimelineKind.CANCELLED, "Cancelled by you."),
                now = now,
            )

    /** The seller ended the Game Boy listing early. */
    val gameBoyEndedEarly: LotMoment
        get() =
            LotMoment(
                snipe =
                    gameBoy.copy(
                        status = SnipeStatus.ENDED_EARLY,
                        endTimeUtcMillis = millis("2025-11-12T09:30:00Z"),
                        result =
                            SnipeResult(
                                SnipeOutcome.ENDED_EARLY,
                                gbp("46.00"),
                                null,
                                null,
                                null,
                                "This listing was ended by the seller.",
                                "The seller ended the listing early.",
                            ),
                    ),
                events = added(gameBoy),
                now = at("2025-11-12T09:31:00Z"),
            )

    /** A page Kestrel did not recognise was handed to the user. */
    val omegaNeedsYou: LotMoment
        get() =
            LotMoment(
                snipe = omega.copy(status = SnipeStatus.NEEDS_YOU, lastPrice = gbp("1850.00"), lastBidCount = 29),
                events =
                    added(omega) +
                        event(
                            omega,
                            7,
                            "2025-11-09T18:01:00Z",
                            TimelineKind.NEEDS_USER,
                            "eBay showed \"Confirm your age\" instead of the item. Open it to check.",
                            "reason" to "UNKNOWN_PAGE",
                        ),
                now = now,
            )

    // Building blocks.

    private val canonPrices =
        listOf(
            PricePoint(millis("2025-11-09T09:14:02Z"), gbp("88.00"), 14),
            PricePoint(millis("2025-11-09T18:20:51Z"), gbp("96.00"), 16),
        )

    private fun canonUntilDryRun(): List<TimelineEvent> =
        added(canon) +
            event(
                canon,
                5,
                "2025-11-09T18:11:40.200Z",
                TimelineKind.DRY_RUN,
                "Dry run passed: eBay's bid layer opened and took your max £120.00. No bid was placed.",
                "fieldValue" to "120.00",
                "minimum" to "GBP 90.00",
            )

    private fun canonUntilWindow(): List<TimelineEvent> =
        canonUntilDryRun() +
            listOf(
                windowOpened(canon, "2025-11-09T18:19:40Z"),
                event(
                    canon,
                    11,
                    "2025-11-09T18:19:41.200Z",
                    TimelineKind.CLOCK_SYNCED,
                    "Clock checked against time.google.com: the phone's clock is 0.04 s behind (round trip 38 ms).",
                    "source" to "SNTP",
                    "offsetMillis" to 40,
                    "roundTripMillis" to 38,
                ),
                event(
                    canon,
                    12,
                    "2025-11-09T18:20:10Z",
                    TimelineKind.PAGE_READY,
                    "Item page loaded: £88.00 with 14 bids, ending in 1 min 30 s.",
                ),
                event(
                    canon,
                    13,
                    "2025-11-09T18:20:55.400Z",
                    TimelineKind.LAYER_READY,
                    "Bid layer open and your max typed; eBay's minimum bid is £98.00.",
                ),
            )

    private fun canonUntilReply(
        reply: String,
        message: String,
    ): List<TimelineEvent> =
        canonUntilWindow() +
            listOf(
                bidSent(canon, "2025-11-09T18:21:33.900Z", leadMillis = 6_100),
                event(
                    canon,
                    23,
                    "2025-11-09T18:21:34.700Z",
                    TimelineKind.EBAY_REPLY,
                    message,
                    "reply" to reply,
                    "latencyMillis" to 800,
                    "attempt" to 1,
                ),
            )

    private fun nintendo64Timeline(): List<TimelineEvent> =
        added(nintendo64) +
            listOf(
                event(
                    nintendo64,
                    5,
                    "2025-11-07T19:31:02.400Z",
                    TimelineKind.DRY_RUN,
                    "Dry run passed: eBay's bid layer opened and took your max £65.00. No bid was placed.",
                ),
                windowOpened(nintendo64, "2025-11-07T19:39:00Z"),
                bidSent(nintendo64, "2025-11-07T19:40:54.000Z", leadMillis = 6_000),
                event(
                    nintendo64,
                    23,
                    "2025-11-07T19:40:54.900Z",
                    TimelineKind.EBAY_REPLY,
                    "eBay: \"You’ve been outbid. Another bidder’s maximum is higher.\" " +
                        "Another bidder's max is higher than yours.",
                    "reply" to "Outbid",
                    "latencyMillis" to 900,
                    "attempt" to 1,
                ),
                event(
                    nintendo64,
                    31,
                    "2025-11-07T19:41:05.300Z",
                    TimelineKind.RESULT,
                    "eBay says another bidder won.",
                    "outcome" to "LOST",
                ),
            )

    private fun added(snipe: Snipe): List<TimelineEvent> {
        // The data layer writes this event, with eBay's money format.
        val message =
            snipe.maxBid?.let { "Added with a max of ${MoneyText.format(it)}. Kestrel bids 6 s before the end." }
                ?: "Added to watch. Kestrel will not bid."
        return listOf(event(snipe, 1, instantText(snipe.createdAtUtcMillis), TimelineKind.ADDED, message))
    }

    private fun windowOpened(
        snipe: Snipe,
        time: String,
    ): TimelineEvent =
        event(
            snipe,
            10,
            time,
            TimelineKind.WINDOW_OPENED,
            "Bid window opened. Kestrel bids your max £${snipe.maxBid?.major?.toPlainString()} 6 s before the end, " +
                "in 1 min 54 s.",
            "fireAtUtcMillis" to snipe.endTimeUtcMillis - 6_000,
            "endUtcMillis" to snipe.endTimeUtcMillis,
        )

    private fun bidSent(
        snipe: Snipe,
        time: String,
        leadMillis: Long,
    ): TimelineEvent {
        val max = snipe.maxBid ?: error("A bid needs a max")
        return event(
            snipe,
            22,
            time,
            TimelineKind.BID_SENT,
            "Bid of £${max.major.toPlainString()} sent ${String.format(Locale.ROOT, "%.2f", leadMillis / 1_000.0)} s " +
                "before the end.",
            "attempt" to 1,
            "amount" to "${max.currency} ${max.major.toPlainString()}",
            "sentAtUtcMillis" to millis(time),
            "leadMillis" to leadMillis,
            "inputMethod" to "NATIVE_TOUCH",
        )
    }

    /** A timeline event with the engine's detail JSON: the true time first, then [details]. */
    private fun event(
        snipe: Snipe,
        sequence: Long,
        time: String,
        kind: TimelineKind,
        message: String,
        vararg details: Pair<String, Any>,
    ): TimelineEvent {
        val atUtcMillis = millis(time)
        val detail =
            buildJsonObject {
                put("trueUtcMillis", atUtcMillis)
                for ((key, value) in details) {
                    when (value) {
                        is Number -> put(key, JsonPrimitive(value))
                        else -> put(key, value.toString())
                    }
                }
            }.toString()
        return TimelineEvent(snipe.id * 100 + sequence, snipe.id, atUtcMillis, kind, message, detail)
    }

    private fun snipe(
        id: Long,
        itemId: String,
        title: String,
        photo: String,
        max: Money?,
        status: SnipeStatus,
        end: String,
        price: Money,
        bids: Int,
        postage: Money,
        created: String,
        lastChecked: String,
        dryRunAt: String? = null,
        groupId: Long? = null,
        extendedBidding: Boolean = false,
        result: SnipeResult? = null,
    ): Snipe =
        Snipe(
            id = id,
            ref = ItemRef(EbaySite.UK, itemId),
            title = title,
            imageUrl = FixturePhotos.url(photo),
            sellerName = null,
            maxBid = max,
            leadSeconds = 6,
            groupId = groupId,
            status = status,
            endTimeUtcMillis = millis(end),
            lastPrice = price,
            lastBidCount = bids,
            lastCheckedAtUtcMillis = millis(lastChecked),
            postage = postage,
            buyerProtectionFee = null,
            buyItNow = null,
            hasReserve = false,
            reserveMet = null,
            extendedBidding = extendedBidding,
            dryRunAtUtcMillis = dryRunAt?.let(::millis),
            dryRunPassed = dryRunAt?.let { true },
            result = result,
            createdAtUtcMillis = millis(created),
            updatedAtUtcMillis = millis(lastChecked),
        )

    fun gbp(amount: String): Money = Money.of(amount, "GBP")

    fun at(text: String): Instant = Instant.parse(text)

    fun millis(text: String): Long = Instant.parse(text).toEpochMilli()

    private fun instantText(utcMillis: Long): String = Instant.ofEpochMilli(utcMillis).toString()
}
