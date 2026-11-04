package app.kestrel.web

import app.kestrel.engine.EngineClock
import app.kestrel.model.AutoRefreshState
import app.kestrel.model.EbaySite
import app.kestrel.model.ItemRef
import app.kestrel.model.Listing
import app.kestrel.model.ListingStatus
import app.kestrel.model.Money
import app.kestrel.model.PageKind
import app.kestrel.model.ParsedLink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

// Test doubles shared by the web tests. None of them touches the network or Android.

internal val ukItem = ItemRef(EbaySite.UK, "127820098965")

internal fun sampleListing(
    ref: ItemRef = ukItem,
    isAuction: Boolean = true,
    currency: String = "GBP",
    fetchedAtUtcMillis: Long = 0,
) = Listing(
    ref = ref,
    title = "Britain after Rome",
    imageUrl = null,
    sellerName = "seller",
    isAuction = isAuction,
    status = ListingStatus.ACTIVE,
    endTimeUtcMillis = 1_777_288_402_000,
    scheduledEndUtcMillis = null,
    currentPrice = Money.of("5.37", currency),
    currentPriceInSiteCurrency = null,
    bidCount = 1,
    hasReserve = false,
    reserveMet = null,
    postage = null,
    buyerProtectionFee = null,
    buyItNow = null,
    extendedBidding = false,
    userIsHighBidder = false,
    userBiddingStatus = null,
    endedText = null,
    endedEarly = false,
    signedIn = true,
    relistedItemId = null,
    fetchedAtUtcMillis = fetchedAtUtcMillis,
)

/** The engine clock on the test's virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TestClock(
    private val scheduler: TestCoroutineScheduler,
) : EngineClock {
    override fun nowUtcMillis(): Long = UTC_START + scheduler.currentTime

    override fun elapsedRealtimeMillis(): Long = ELAPSED_START + scheduler.currentTime

    companion object {
        const val UTC_START = 1_777_288_000_000
        const val ELAPSED_START = 50_000L
    }
}

internal class FakeParsers : EbayParsers {
    var kindFor: (String, String) -> PageKind = { url, title ->
        when {
            "//signin." in url -> PageKind.SIGN_IN
            "/splashui/challenge" in url -> PageKind.CHALLENGE
            "/splashui/captcha" in url -> PageKind.CAPTCHA
            title.startsWith("Error Page") -> PageKind.RATE_WALL
            "/p/" in url -> PageKind.NOT_FOUND
            else -> PageKind.NORMAL
        }
    }
    var listingFor: (String, ItemRef, Long) -> Listing? = { _, ref, at -> sampleListing(ref, fetchedAtUtcMillis = at) }
    var autoRefreshState: AutoRefreshState? = AutoRefreshState(Money.of("6.00", "GBP"), 2, 1_777_288_402_000, 2, false)
    var refreshUrl: String? = "/itemmodules/127820098965?module_groups=AUTO_REFRESH"
    val parsedPageTexts = mutableListOf<String>()
    val parsedAutoRefresh = mutableListOf<String>()

    override fun classify(
        url: String,
        title: String,
    ): PageKind = kindFor(url, title)

    override fun parseListing(
        pageText: String,
        requested: ItemRef,
        fetchedAtUtcMillis: Long,
    ): Listing? {
        parsedPageTexts += pageText
        return listingFor(pageText, requested, fetchedAtUtcMillis)
    }

    override fun parseAutoRefresh(json: String): AutoRefreshState? {
        parsedAutoRefresh += json
        return autoRefreshState
    }

    override fun autoRefreshUrl(pageText: String): String? = refreshUrl

    override fun parseLink(text: String): ParsedLink {
        val match =
            Regex("""https://(www\.ebay\.[a-z.]+)/itm/(?:[^/?#]+/)?(\d{9,15})""").find(text)
                ?: return ParsedLink.NotAListing(text)
        val site = EbaySite.fromHost(match.groupValues[1]) ?: return ParsedLink.NotEbay
        return ParsedLink.Item(ItemRef(site, match.groupValues[2]))
    }
}

/** One kestrel.js call the fake driver received. */
internal data class PageCall(
    val function: String,
    val arguments: List<JsonElement>,
)

internal data class FakeTap(
    val xCssPx: Double,
    val yCssPx: Double,
    val devicePixelRatio: Double,
)

/**
 * A [PageDriver] that answers kestrel.js calls from handlers instead of a WebView. A new document starts without the
 * helper, so the first call of each document goes through the install path, as on the phone.
 */
internal class FakePageDriver : PageDriver {
    override var failure: DriverFailure? = null
    override var currentUrl: String? = null
    override val diagnostics = mutableListOf<String>()
    override val javaScriptDialogs = mutableListOf<String>()
    override val javaScriptDialogCount: Int get() = javaScriptDialogs.size

    /** The page opened alert(), confirm() or prompt(): the driver dismissed it and kept the message. */
    fun pageOpensDialog(message: String) {
        javaScriptDialogs += message
    }

    val calls = mutableListOf<PageCall>()
    val loads = mutableListOf<String>()
    val taps = mutableListOf<FakeTap>()
    var installs = 0
    var destroyed = false
    var helperInstalled = false
    var onTap: (FakeTap) -> Unit = {}
    var loadResult: (String) -> LoadResult = { url ->
        LoadResult(url, url, "Britain after Rome | eBay UK", emptyList(), LoadOutcome.Loaded(null, "complete"))
    }
    private val handlers = mutableMapOf<String, (List<JsonElement>) -> String>()

    fun on(
        function: String,
        handler: (List<JsonElement>) -> String,
    ) {
        handlers[function] = handler
    }

    fun callsOf(function: String) = calls.filter { it.function == function }

    override suspend fun load(
        url: String,
        timeoutMillis: Long,
    ): LoadResult {
        failure?.let { throw PageUnavailableException(it.description) }
        loads += url
        helperInstalled = false
        navigationCount += 1
        if (loadDelayMillis > 0) delay(loadDelayMillis)
        return loadResult(url).also { currentUrl = it.finalUrl }
    }

    /** How long a load takes on virtual time, for a slow network. */
    var loadDelayMillis = 0L

    override suspend fun evaluate(
        script: String,
        timeoutMillis: Long,
    ): String {
        failure?.let { throw PageUnavailableException(it.description) }
        if (script.endsWith("\n;true")) {
            installs += 1
            helperInstalled = true
            return "true"
        }
        if (script == "document.readyState") return "complete"
        val match = callPattern.find(script) ?: error("Unexpected script: ${script.take(200)}")
        if (!helperInstalled) return PageScript.HELPER_MISSING
        val arguments = pageJson.parseToJsonElement("[${match.groupValues[2]}]") as JsonArray
        val call = PageCall(match.groupValues[1], arguments)
        calls += call
        val handler = handlers[call.function] ?: return """{"ok":false,"error":"no fake for ${call.function}"}"""
        return handler(arguments)
    }

    override suspend fun tap(
        xCssPx: Double,
        yCssPx: Double,
        devicePixelRatio: Double,
        pressMillis: Long,
        onlyInNavigation: Long?,
    ) {
        failure?.let { throw PageUnavailableException(it.description) }
        TapGeometry.toViewPixels(
            xCssPx,
            yCssPx,
            devicePixelRatio,
            EbayWebViewFactory.HEADLESS_WIDTH_PX,
            EbayWebViewFactory.HEADLESS_HEIGHT_PX,
        )
        if (onlyInNavigation != null && navigationCount != onlyInNavigation) {
            throw TapAbortedException("The page navigated before the tap", fingerWentDown = false)
        }
        val tap = FakeTap(xCssPx, yCssPx, devicePixelRatio)
        taps += tap
        onTap(tap)
    }

    override var navigationCount = 0L

    /** A main-frame navigation the page made by itself (a redirect, a reload). */
    fun pageNavigates() {
        navigationCount += 1
        helperInstalled = false
    }

    override fun destroy() {
        destroyed = true
        if (failure == null) failure = DriverFailure.Destroyed
    }

    private companion object {
        val callPattern = Regex("""return k\.(\w+)\((.*)\);\}\)\(\)$""", RegexOption.DOT_MATCHES_ALL)
    }
}

internal fun JsonElement.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonElement.objectField(name: String): JsonElement? = (this as? JsonObject)?.get(name)

internal fun json(value: Any?): String = jsonElement(value).toString()

private fun jsonElement(value: Any?): JsonElement =
    when (value) {
        null -> kotlinx.serialization.json.JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (key, entry) -> key.toString() to jsonElement(entry) })
        is List<*> -> JsonArray(value.map { jsonElement(it) })
        else -> error("Cannot encode $value")
    }

/**
 * eBay's item page and bid layer at the level of kestrel.js's answers: enough behaviour (hydration, the layer
 * opening, typing, the guarded tap, the reply) to drive [WebBidPage] through every path.
 */
internal class SimulatedEbayPage(
    private val driver: FakePageDriver,
) {
    var url = "https://www.ebay.co.uk/itm/127820098965"
    var title = "Britain after Rome | eBay UK"
    var signedIn: Boolean? = true
    var bidButtonPresent = true
    var probesBeforeHydration = 0
    var signInHref: String? = null
    var interstitial: Pair<String, String>? = null
    var layerError: String? = null
    var minimumAmountText: String? = "£5.57"
    var lowestPowerBidAmountText: String? = "£5.57"
    var minimumMinor = 557L
    var fieldRewrite: (String) -> String = { it }
    var submitLabel: String? = "Bid"
    var submitEnabledWhenValid = true
    var touchReachesPage = true
    var touchMatches = true
    var nativeTapReachesTarget = true
    var replyNotices: (Int) -> List<String> = { listOf("Your current bid puts you in the lead.") }
    var pollsBeforeReply = 1
    var confirmFinishedPerf: Double? = 1180.0
    var blockedReason: String? = null
    var autoRefreshStatus = 200
    var autoRefreshPollsBeforeDone = 1

    /** The page's clock, for behaviour that takes time (the rigs pass the test's virtual clock). */
    var clock: () -> Long = { 0L }

    /** eBay's self-clearing browser check stands in front of the item until this time on [clock]. */
    var challengeUntilMillis: Long? = null
    private val inChallenge get() = challengeUntilMillis?.let { clock() < it } == true
    private val shownUrl get() = if (inChallenge) "https://www.ebay.co.uk/splashui/challenge?ap=1&appName=orch" else url
    private val shownTitle get() = if (inChallenge) "Pardon Our Interruption..." else title

    /** eBay's own check of the typed amount: its bid button only enables this long after typing. */
    var submitEnableDelayMillis = 0L
    private var typedAtMillis = 0L

    /** A check eBay shows right after the amount is typed (kind, text). */
    var interstitialAfterTyping: Pair<String, String>? = null

    /** eBay answers the press with a check instead of a reply (kind, text), or by moving to another page. */
    var interstitialAfterPress: Pair<String, String>? = null
    var urlAfterPress: String? = null

    /** eBay rewrites the field this long after typing (a debounced formatter, or the layer redrawing itself). */
    var fieldChangeAfterTypingMillis: Long? = null
    var fieldChange: (String) -> String = { it }
    private var fieldChanged = false

    private fun applyLateFieldChange() {
        val after = fieldChangeAfterTypingMillis ?: return
        if (fieldChanged || fieldValue.isEmpty() || clock() - typedAtMillis < after) return
        fieldChanged = true
        fieldValue = fieldChange(fieldValue)
    }

    var probes = 0
    var layerOpen = false
    var interstitialShown = false
    var fieldValue = ""
    var guardTarget: String? = null
    var guardReached = false
    var probeArmed = false
    var probeTapSeen = false
    var pressMethod: String? = null
    var presses = 0
    var replyPolls = 0
    var autoRefreshPolls = 0
    var ceiling: JsonElement? = null
    var hold = false
    var holdStuck = false
    val observerArms = mutableListOf<Boolean>()
    var pressesWhileHeld = 0

    /** The submit call presses, then its answer never comes back (an evaluateJavascript timeout). */
    var submitAnswerLost = false
    var inlineErrorAfterPress: String? = null
    var dialogOnLayerOpen: String? = null
    var submitRefusalOverride: String? = null

    /** kestrel.js's name for the current document; a navigation gives the next document a new one. */
    var documentToken = "document-1"
    var navigateAfterPress = false
    var navigateOnSubmitTap = false
    var navigateAfterSubmitArmed = false
    var submitAnswerNull = false
    var bidButtonPresses = 0

    init {
        driver.loadResult =
            { requested ->
                LoadResult(requested, shownUrl, shownTitle, emptyList(), LoadOutcome.Loaded(null, "complete"))
            }
        driver.onTap = { tapped() }
        driver.on("probe") { probe() }
        driver.on("modelText") {
            json(
                mapOf(
                    "ok" to true,
                    "url" to url,
                    "title" to title,
                    "text" to "<title>$title</title>",
                    "modelScripts" to 1,
                ),
            )
        }
        driver.on("layerState") { layerState() }
        driver.on("openBidLayer") { openBidLayer() }
        driver.on("typeMax") { arguments -> typeMax(arguments.first().text().orEmpty()) }
        driver.on("armObserver") { arguments ->
            ceiling = arguments.firstOrNull()
            arguments
                .firstOrNull()
                ?.objectField("hold")
                ?.jsonPrimitive
                ?.booleanOrNull
                ?.let { hold = it }
            observerArms += hold
            json(mapOf("ok" to true, "fetchWrapped" to true, "xhrWrapped" to true, "hold" to (hold || holdStuck)))
        }
        driver.on("armTouchProbe") {
            probeArmed = true
            json(mapOf("ok" to true, "x" to 205.5, "y" to 461.0, "devicePixelRatio" to 2.625))
        }
        driver.on("touchProbeResult") { arguments ->
            val trusted = probeTapSeen && touchReachesPage
            if (arguments.firstOrNull()?.jsonPrimitive?.booleanOrNull == true) probeArmed = false
            json(
                mapOf(
                    "ok" to true,
                    "trusted" to trusted,
                    "matched" to (trusted && touchMatches),
                    "events" to listOf(mapOf("type" to "pointerdown", "x" to 205.5, "y" to 461.0)),
                ),
            )
        }
        driver.on(
            "armTap",
        ) { arguments -> armTap(arguments[0].text(), arguments.getOrNull(1)?.objectField("expectValue")?.text()) }
        driver.on(
            "tapResult",
        ) {
            json(
                mapOf(
                    "ok" to true,
                    "armed" to (guardTarget != null),
                    "reached" to guardReached,
                    "documentToken" to documentToken,
                ),
            )
        }
        driver.on("disarmTap") {
            guardTarget = null
            guardReached = false
            json(mapOf("ok" to true))
        }
        driver.on("submit") { arguments ->
            val options = arguments.firstOrNull()
            submit(options?.objectField("expectValue")?.text(), options?.objectField("documentToken")?.text())
        }
        driver.on("replyState") { replyState() }
        driver.on("closeLayer") {
            layerOpen = false
            json(mapOf("ok" to true, "closed" to true, "method" to "closeButton"))
        }
        driver.on("report") {
            json(
                mapOf(
                    "ok" to true,
                    "version" to "1",
                    "matched" to mapOf("submitLabel" to submitLabel),
                ),
            )
        }
        driver.on("autoRefresh") { json(mapOf("ok" to true, "requestId" to 7)) }
        driver.on("autoRefreshResult") {
            autoRefreshPolls += 1
            val done = autoRefreshPolls >= autoRefreshPollsBeforeDone
            json(
                mapOf(
                    "ok" to true,
                    "done" to done,
                    "status" to if (done) autoRefreshStatus else null,
                    "text" to if (done) """{"states":[]}""" else null,
                ),
            )
        }
    }

    private val hydrated get() = probes > probesBeforeHydration

    private fun probe(): String {
        probes += 1
        return json(
            mapOf(
                "ok" to true,
                "url" to shownUrl,
                "title" to shownTitle,
                "readyState" to "complete",
                "signedIn" to signedIn,
                "layerOpen" to layerOpen,
                "devicePixelRatio" to 2.625,
                "bidButton" to
                    mapOf(
                        "present" to bidButtonPresent,
                        "disabled" to (bidButtonPresent && !hydrated),
                        "loading" to (bidButtonPresent && !hydrated),
                        "signInHref" to signInHref,
                    ),
            ),
        )
    }

    private val valid get() =
        fieldValue.isNotEmpty() &&
            (AmountText.readField(fieldValue, EbaySite.UK, "GBP")?.minor ?: 0) >= minimumMinor

    private val submitEnabled get() =
        valid && submitEnabledWhenValid && clock() - typedAtMillis >= submitEnableDelayMillis

    private fun layerState(): String {
        applyLateFieldChange()
        if (interstitialShown) {
            val (kind, text) = interstitial!!
            return json(
                mapOf(
                    "ok" to true,
                    "url" to url,
                    "title" to title,
                    "open" to true,
                    "interstitial" to mapOf("kind" to kind, "text" to text),
                    "signInRequired" to (kind == "signIn"),
                ),
            )
        }
        if (!layerOpen) return json(mapOf("ok" to true, "url" to url, "title" to title, "open" to false))
        if (layerError !=
            null
        ) {
            return json(
                mapOf(
                    "ok" to true,
                    "url" to url,
                    "title" to title,
                    "open" to true,
                    "layerError" to layerError,
                ),
            )
        }
        val inlineError = if (fieldValue.isNotEmpty() && !valid) "Enter £5.57 or more" else null
        return json(
            mapOf(
                "ok" to true,
                "url" to url,
                "title" to title,
                "open" to true,
                "ready" to (submitLabel != null),
                "input" to mapOf("type" to "tel", "value" to fieldValue),
                "submit" to
                    mapOf(
                        "found" to (submitLabel != null),
                        "label" to submitLabel,
                        "enabled" to submitEnabled,
                        "rejected" to listOf(mapOf("label" to "Review bid")),
                    ),
                "minimumAmountText" to minimumAmountText,
                "lowestPowerBidAmountText" to lowestPowerBidAmountText,
                "inlineError" to inlineError,
            ),
        )
    }

    /** The bid button leads to a page of its own on eBay's site (a check page) instead of the layer. */
    var pageOnBidButton: String? = null

    private fun showLayer() {
        bidButtonPresses += 1
        pageOnBidButton?.let {
            url = it
            title = "Confirm your identity | eBay"
            return
        }
        if (interstitial != null) interstitialShown = true else layerOpen = true
        dialogOnLayerOpen?.let { driver.pageOpensDialog(it) }
    }

    private fun openBidLayer(): String {
        if (layerOpen) return json(mapOf("ok" to true, "alreadyOpen" to true))
        if (signInHref != null) return json(mapOf("ok" to false, "reason" to "signInRequired"))
        if (guardTarget == "bidButton" &&
            guardReached
        ) {
            return json(mapOf("ok" to false, "reason" to "nativeTapReached"))
        }
        showLayer()
        return json(mapOf("ok" to true, "selector" to "#bidBtn_btn"))
    }

    private fun typeMax(text: String): String {
        fieldValue = fieldRewrite(text)
        typedAtMillis = clock()
        fieldChanged = false
        interstitialAfterTyping?.let {
            interstitial = it
            interstitialShown = true
        }
        return json(
            mapOf(
                "ok" to true,
                "value" to fieldValue,
                "minimumAmountText" to minimumAmountText,
                "submitEnabled" to submitEnabled,
                "documentToken" to documentToken,
            ),
        )
    }

    private fun armTap(
        which: String?,
        expectValue: String?,
    ): String {
        if (which == "submit") {
            applyLateFieldChange()
            if (pressMethod != null) return json(mapOf("ok" to false, "reason" to "alreadySubmitted"))
            if (fieldValue != expectValue) return json(mapOf("ok" to false, "reason" to "amountChanged"))
            guardTarget = "submit"
            if (navigateAfterSubmitArmed) {
                // A new document replaces this one before the tap: same form, same amount, no guard, a new token.
                driver.pageNavigates()
                documentToken = "document-2"
                guardTarget = null
            }
            return json(
                mapOf(
                    "ok" to true,
                    "x" to 206.0,
                    "y" to 484.0,
                    "hit" to true,
                    "devicePixelRatio" to 2.625,
                    "label" to submitLabel,
                ),
            )
        }
        guardTarget = "bidButton"
        return json(
            mapOf(
                "ok" to true,
                "x" to 206.0,
                "y" to 624.0,
                "hit" to true,
                "devicePixelRatio" to 2.625,
                "label" to "Submit bid",
            ),
        )
    }

    private fun tapped() {
        when {
            probeArmed -> {
                probeTapSeen = true
            }

            guardTarget == "bidButton" && nativeTapReachesTarget -> {
                guardReached = true
                showLayer()
            }

            guardTarget == "submit" && nativeTapReachesTarget -> {
                guardReached = true
                pressMethod = "native"
                press()
            }

            guardTarget == "submit" && navigateOnSubmitTap -> {
                // The tap sent the bid and the page moved on before anyone could ask: a fresh helper, no history.
                press()
                documentToken = "document-2"
                guardTarget = null
            }
        }
    }

    private fun press() {
        presses += 1
        if (hold) pressesWhileHeld += 1
    }

    private fun refused(reason: String) = json(mapOf("ok" to false, "dispatched" to false, "reason" to reason))

    private fun submit(
        expectValue: String?,
        expectedDocument: String?,
    ): String {
        applyLateFieldChange()
        val refusal =
            when {
                submitRefusalOverride != null -> submitRefusalOverride
                expectedDocument != null && expectedDocument != documentToken -> "documentChanged"
                pressMethod == "native" -> "nativeTapReached"
                pressMethod != null -> "alreadySubmitted"
                !layerOpen -> "bidLayerClosed"
                expectValue == null -> "expectedValueMissing"
                fieldValue != expectValue -> "amountChanged"
                else -> null
            }
        if (refusal != null) return refused(refusal)
        pressMethod = "dom"
        press()
        if (navigateAfterPress) {
            // The press sent the bid and the page moved on: the next document's helper has no press history.
            documentToken = "document-2"
            pressMethod = null
        }
        if (submitAnswerNull) {
            // The navigation took the answer with it: WebView hands the callback null.
            driver.pageNavigates()
            documentToken = "document-2"
            pressMethod = null
            return "null"
        }
        if (submitAnswerLost) throw ScriptTimeoutException("A page script did not answer within 5000 ms")
        return json(mapOf("ok" to true, "dispatched" to true, "label" to submitLabel, "perfTime" to 1000.0))
    }

    private fun replyState(): String {
        replyPolls += 1
        val answered = pressMethod != null && replyPolls >= pollsBeforeReply
        val records =
            if (answered && confirmFinishedPerf != null) {
                listOf(
                    mapOf(
                        "via" to "fetch",
                        "action" to "confirmbid",
                        "status" to 200,
                        "finishedPerf" to confirmFinishedPerf,
                    ),
                )
            } else {
                emptyList()
            }
        val blocked =
            if (answered &&
                blockedReason != null
            ) {
                listOf(
                    mapOf(
                        "action" to "confirmbid",
                        "reason" to blockedReason,
                        "requestPrice" to mapOf("value" to "5.57", "currency" to "GBP"),
                    ),
                )
            } else {
                emptyList()
            }
        val check = interstitialAfterPress?.takeIf { answered }
        return json(
            mapOf(
                "ok" to true,
                "url" to (urlAfterPress?.takeIf { answered } ?: url),
                "title" to title,
                "layerOpen" to layerOpen,
                "newNotices" to if (answered) replyNotices(replyPolls) else emptyList<String>(),
                "inlineError" to if (answered) inlineErrorAfterPress else null,
                "interstitial" to check?.let { (kind, text) -> mapOf("kind" to kind, "text" to text) },
                "records" to records,
                "blocked" to blocked,
                "blockedSincePress" to blocked,
                "pressed" to (pressMethod != null),
                "pressMethod" to pressMethod,
                "pressPerfTime" to if (pressMethod != null) 1000.0 else null,
                "documentToken" to documentToken,
            ),
        )
    }
}
