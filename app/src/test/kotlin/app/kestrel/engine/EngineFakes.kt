@file:OptIn(ExperimentalCoroutinesApi::class)

package app.kestrel.engine

import app.kestrel.model.AutoRefreshState
import app.kestrel.model.EbaySite
import app.kestrel.model.ItemRef
import app.kestrel.model.Listing
import app.kestrel.model.ListingStatus
import app.kestrel.model.Money
import app.kestrel.model.PageKind
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.model.SnipeResult
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** True UTC when the virtual clock reads zero: 2026-11-04T13:46:40Z. */
const val BASE_UTC = 1_793_800_000_000L

/** elapsedRealtime when the virtual clock reads zero. */
const val BASE_ELAPSED = 5_000_000L

/** The window opens at virtual time zero, two minutes before this end. */
const val END_UTC = BASE_UTC + 120_000L

val ITEM = ItemRef(EbaySite.UK, "127820098965")

fun gbp(amount: String): Money = Money.of(amount, "GBP")

/** eBay's rule simplified for tests: the current price with no bids, else one pound more. */
internal val TEST_MINIMUM_RULE =
    MinimumBidRule { _, current, bidCount, _ -> if (bidCount == 0) current else current + Money(100, current.currency) }

fun testSnipe(
    id: Long = 1,
    max: Money? = gbp("48.00"),
    endUtc: Long = END_UTC,
    leadSeconds: Int = 6,
    status: SnipeStatus = SnipeStatus.ARMED,
    groupId: Long? = null,
    lastPrice: Money? = gbp("20.00"),
    lastBidCount: Int? = 3,
    dryRunPassed: Boolean? = null,
    title: String = "Item $id",
    ref: ItemRef = ITEM,
): Snipe =
    Snipe(
        id = id,
        ref = ref,
        title = title,
        imageUrl = null,
        sellerName = "seller",
        maxBid = max,
        leadSeconds = leadSeconds,
        groupId = groupId,
        status = status,
        endTimeUtcMillis = endUtc,
        lastPrice = lastPrice,
        lastBidCount = lastBidCount,
        lastCheckedAtUtcMillis = null,
        postage = null,
        buyerProtectionFee = null,
        buyItNow = null,
        hasReserve = false,
        reserveMet = null,
        extendedBidding = false,
        dryRunAtUtcMillis = null,
        dryRunPassed = dryRunPassed,
        result = null,
        createdAtUtcMillis = BASE_UTC - 86_400_000L,
        updatedAtUtcMillis = BASE_UTC - 86_400_000L,
    )

fun testListing(
    endUtc: Long = END_UTC,
    status: ListingStatus = ListingStatus.ACTIVE,
    price: Money = gbp("20.00"),
    bidCount: Int = 3,
    signedIn: Boolean? = true,
    userIsHighBidder: Boolean? = null,
    userBiddingStatus: String? = null,
    endedText: String? = null,
    endedEarly: Boolean = false,
    scheduledEndUtc: Long? = endUtc,
    extendedBidding: Boolean = false,
    hasReserve: Boolean = false,
    reserveMet: Boolean? = null,
    buyItNow: Money? = null,
    relistedItemId: String? = null,
    ref: ItemRef = ITEM,
    isAuction: Boolean = true,
): Listing =
    Listing(
        ref = ref,
        title = "Vintage camera",
        imageUrl = "https://i.ebayimg.com/images/g/abc/s-l1600.jpg",
        sellerName = "seller",
        isAuction = isAuction,
        status = status,
        endTimeUtcMillis = endUtc,
        scheduledEndUtcMillis = scheduledEndUtc,
        currentPrice = price,
        currentPriceInSiteCurrency = null,
        bidCount = bidCount,
        hasReserve = hasReserve,
        reserveMet = reserveMet,
        postage = gbp("3.20"),
        buyerProtectionFee = null,
        buyItNow = buyItNow,
        extendedBidding = extendedBidding,
        userIsHighBidder = userIsHighBidder,
        userBiddingStatus = userBiddingStatus,
        endedText = endedText,
        endedEarly = endedEarly,
        signedIn = signedIn,
        relistedItemId = relistedItemId,
        fetchedAtUtcMillis = 0,
    )

/** Time sources on virtual time. The phone's clock runs [systemSkewMillis] fast; SNTP measures that. */
class FakeTimeSources(
    private val scheduler: TestCoroutineScheduler,
) : TimeSources {
    var systemSkewMillis = 3_000L
    var networkSkewMillis: Long? = 400L

    /** Scripted SNTP samples, in order; null entries are failed exchanges. When empty, SNTP fails. */
    var sntpScript: ArrayDeque<SntpSample?>? = null
    var sntpDelayMillis = 30L
    var sntpCalls = 0

    fun trueUtc(): Long = BASE_UTC + scheduler.currentTime

    override fun systemUtcMillis(): Long = trueUtc() + systemSkewMillis

    /** Moves elapsedRealtime ahead of the virtual clock, like a phone whose main thread woke up late. */
    var elapsedShiftMillis = 0L

    override fun elapsedRealtimeMillis(): Long = BASE_ELAPSED + scheduler.currentTime + elapsedShiftMillis

    override fun networkUtcMillis(): Long? = networkSkewMillis?.let { trueUtc() + it }

    /** Runs in the middle of each exchange, for example to set the wall clock while a request is in flight. */
    var duringSntp: (callNumber: Int) -> Unit = {}

    override suspend fun sntp(host: String): SntpSample? {
        sntpCalls++
        val measuredOffset = -systemSkewMillis
        delay(sntpDelayMillis / 2)
        duringSntp(sntpCalls)
        delay(sntpDelayMillis / 2)
        val script = sntpScript ?: return SntpSample(offsetMillis = measuredOffset, roundTripMillis = 40)
        return script.removeFirstOrNull()
    }
}

/** An [EngineClock] that reads true time straight from the virtual clock. */
class VirtualClock(
    private val scheduler: TestCoroutineScheduler,
) : EngineClock {
    override fun nowUtcMillis(): Long = BASE_UTC + scheduler.currentTime

    override fun elapsedRealtimeMillis(): Long = BASE_ELAPSED + scheduler.currentTime
}

data class RecordedEvent(
    val snipeId: Long,
    val kind: TimelineKind,
    val message: String,
    val detail: String?,
) {
    val trueUtcMillis: Long
        get() =
            Json
                .parseToJsonElement(detail!!)
                .jsonObject["trueUtcMillis"]!!
                .jsonPrimitive.long
}

data class RecordedPrice(
    val snipeId: Long,
    val atUtcMillis: Long,
    val price: Money,
    val bidCount: Int,
)

class FakeStore : SnipeStore {
    val snipes = mutableMapOf<Long, Snipe>()
    val groups = mutableMapOf<Long, SnipeGroup>()
    val events = mutableListOf<RecordedEvent>()
    val prices = mutableListOf<RecordedPrice>()

    /** Reads of a snipe throw while this is set, like a database that cannot be read. */
    var readsFail = false

    fun put(snipe: Snipe) {
        snipes[snipe.id] = snipe
    }

    fun edit(
        id: Long,
        change: (Snipe) -> Snipe,
    ) {
        snipes[id] = change(snipes.getValue(id))
    }

    fun eventsOf(kind: TimelineKind): List<RecordedEvent> = events.filter { it.kind == kind }

    override suspend fun snipe(id: Long): Snipe? {
        if (readsFail) throw IllegalStateException("database is locked")
        return snipes[id]
    }

    override suspend fun update(snipe: Snipe) {
        snipes[snipe.id] = snipe
    }

    override suspend fun addEvent(
        snipeId: Long,
        kind: TimelineKind,
        message: String,
        detail: String?,
    ) {
        events += RecordedEvent(snipeId, kind, message, detail)
    }

    override suspend fun recordPrice(
        snipeId: Long,
        atUtcMillis: Long,
        price: Money,
        bidCount: Int,
    ) {
        prices += RecordedPrice(snipeId, atUtcMillis, price, bidCount)
    }

    override suspend fun group(id: Long): SnipeGroup? = groups[id]

    override suspend fun groupMembers(groupId: Long): List<Snipe> = snipes.values.filter { it.groupId == groupId }
}

class FakeNotifier : SnipeNotifier {
    val windowsOpened = mutableListOf<Long>()
    val phases = mutableListOf<WindowPhase>()
    val windowsClosed = mutableListOf<Long>()
    val priceAlerts = mutableListOf<Pair<Money, Money?>>()
    val userAlerts = mutableListOf<Pair<NeedsUserReason, String?>>()
    val results = mutableListOf<SnipeResult>()

    override fun windowOpened(
        snipe: Snipe,
        bidAtUtcMillis: Long,
    ) {
        windowsOpened += bidAtUtcMillis
    }

    override fun windowProgress(
        snipe: Snipe,
        phase: WindowPhase,
        detail: String?,
    ) {
        phases += phase
    }

    override fun windowClosed(snipeId: Long) {
        windowsClosed += snipeId
    }

    override fun priceOverMax(
        snipe: Snipe,
        current: Money,
        minimumNext: Money?,
    ) {
        priceAlerts += current to minimumNext
    }

    override fun needsUser(
        snipe: Snipe,
        reason: NeedsUserReason,
        detail: String?,
    ) {
        userAlerts += reason to detail
    }

    override fun result(
        snipe: Snipe,
        result: SnipeResult,
    ) {
        results += result
    }
}

/** What the ended item page shows. */
data class FinalPage(
    val price: Money? = null,
    val bidCount: Int? = null,
    val userIsHighBidder: Boolean? = null,
    val userBiddingStatus: String? = null,
    val endedText: String? = "Item sold on Wed, 4 Nov at 1:48 PM.",
)

/** A small model of one eBay auction on virtual time. */
class FakeAuction(
    private val scheduler: TestCoroutineScheduler,
) {
    var endUtc = END_UTC
    var price = gbp("20.00")
    var bidCount = 3
    var signedIn: Boolean? = true
    var finalPage = FinalPage()

    /** The seller ended the listing before the window: every page shows it ended early. */
    var endedEarly = false

    /** This many AUTO_REFRESH replies wrongly say the auction ended while it still runs. */
    var strayEndedReplies = 0

    fun nowUtc(): Long = BASE_UTC + scheduler.currentTime

    fun isOver(): Boolean = endedEarly || nowUtc() >= endUtc

    fun listing(): Listing =
        when {
            endedEarly -> {
                testListing(
                    endUtc = BASE_UTC - 3_600_000L,
                    status = ListingStatus.ENDED,
                    price = price,
                    bidCount = bidCount,
                    endedText = "This listing was ended by the seller because the item is no longer available.",
                    endedEarly = true,
                    scheduledEndUtc = endUtc,
                )
            }

            !isOver() -> {
                testListing(endUtc = endUtc, price = price, bidCount = bidCount, signedIn = signedIn)
            }

            else -> {
                testListing(
                    endUtc = endUtc,
                    status = ListingStatus.ENDED,
                    price = finalPage.price ?: price,
                    bidCount = finalPage.bidCount ?: bidCount,
                    signedIn = signedIn,
                    userIsHighBidder = finalPage.userIsHighBidder,
                    userBiddingStatus = finalPage.userBiddingStatus,
                    endedText = finalPage.endedText,
                )
            }
        }

    // eBay's own cadence: 2 s in the last minute, 3 s before that.
    fun autoRefresh(): AutoRefreshState {
        if (isOver()) {
            return AutoRefreshState(
                finalPage.price ?: price,
                finalPage.bidCount ?: bidCount,
                endUtc,
                null,
                true,
            )
        }
        if (strayEndedReplies > 0) {
            strayEndedReplies--
            return AutoRefreshState(price, bidCount, endUtc, null, true)
        }
        val secondsLeft = (endUtc - nowUtc()) / 1_000
        return AutoRefreshState(price, bidCount, endUtc, if (secondsLeft <= 60) 2 else 3, false)
    }
}

/** A bid page whose every answer comes from a script and [FakeAuction]. Calls take realistic virtual time. */
class ScriptedBidPage(
    private val scheduler: TestCoroutineScheduler,
    private val auction: FakeAuction,
) : BidPage {
    /** Kinds of page the next item loads show; a normal item page when empty. */
    val pageKinds = ArrayDeque<PageKind>()
    var readListingResult: () -> Listing? = { auction.listing() }
    var layerState: () -> LayerState = {
        LayerState.Ready(minimum = null, submitLabel = "Submit bid", report = "layer: .placebid-redesign-wrapper")
    }
    var fieldValueFor: (Money) -> String = { it.major.toPlainString() }
    var typedMinimum: () -> Money? = { null }
    var typedInlineError: () -> String? = { null }

    /** Replies to successive submits; an unreadable reply when empty. */
    val replies = ArrayDeque<BidReply>()
    var onSubmit: (submitNumber: Int) -> Unit = {}

    /** Thrown from [submit]: an Error, so nothing in the engine can swallow it. */
    var submitError: Throwable? = null

    val openItemAtUtc = mutableListOf<Long>()
    val typed = mutableListOf<Money>()
    val submitsAtUtc = mutableListOf<Long>()
    var layerOpens = 0
    var layerCloses = 0
    var observerArms = 0
    var autoRefreshCalls = 0
    var closed = false

    override suspend fun openItem(ref: ItemRef): PageState {
        delay(1_500)
        openItemAtUtc += auction.nowUtc()
        return when (val kind = pageKinds.removeFirstOrNull() ?: PageKind.NORMAL) {
            PageKind.NORMAL -> {
                PageState(kind, ref.url, "Vintage camera | eBay", auction.listing())
            }

            PageKind.SIGN_IN -> {
                PageState(
                    kind,
                    "https://signin.ebay.co.uk/ws/eBayISAPI.dll?SignIn&ru=x",
                    "Sign in or Register | eBay",
                    null,
                )
            }

            PageKind.CAPTCHA -> {
                PageState(kind, "https://www.ebay.co.uk/splashui/captcha?ap=1&appName=orch", "Security Measure", null)
            }

            PageKind.CHALLENGE -> {
                PageState(
                    kind,
                    "https://www.ebay.co.uk/splashui/challenge?ap=1&appName=orch",
                    "Pardon Our Interruption...",
                    null,
                )
            }

            PageKind.RATE_WALL -> {
                PageState(kind, ref.url, "Error Page | eBay", null)
            }

            else -> {
                PageState(kind, ref.url, "Something else | eBay", null)
            }
        }
    }

    override suspend fun readListing(): Listing? = readListingResult()

    override suspend fun openBidLayer(): LayerState {
        delay(800)
        layerOpens++
        return layerState()
    }

    override suspend fun typeMax(amount: Money): TypedAmount {
        delay(300)
        typed += amount
        return TypedAmount(fieldValueFor(amount), typedInlineError(), typedMinimum())
    }

    override suspend fun closeLayer() {
        layerCloses++
    }

    override suspend fun autoRefresh(): AutoRefreshState? {
        delay(200)
        autoRefreshCalls++
        return auction.autoRefresh()
    }

    override suspend fun armReplyObserver() {
        observerArms++
    }

    override suspend fun submit(): SubmitDispatch {
        submitError?.let { throw it }
        submitsAtUtc += auction.nowUtc()
        onSubmit(submitsAtUtc.size)
        return SubmitDispatch(BASE_ELAPSED + scheduler.currentTime, InputMethod.NATIVE_TOUCH, "Submit bid")
    }

    override suspend fun awaitReply(timeoutMillis: Long): BidReply {
        val reply = replies.removeFirstOrNull()
        if (reply == null || reply is BidReply.Unconfirmed) {
            delay(timeoutMillis)
            return BidReply.Unconfirmed(null)
        }
        delay(600)
        return reply
    }

    override suspend fun report(): String = "url=${ITEM.url}; title=Vintage camera | eBay"

    override fun close() {
        closed = true
    }
}
