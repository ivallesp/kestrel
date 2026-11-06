package app.kestrel.web

import app.kestrel.model.ItemRef
import app.kestrel.model.Listing
import app.kestrel.model.PageKind

/** How long the page operations wait. One place, so the snipe timeline (ARCHITECTURE section 5) stays readable. */
internal data class PageTiming(
    // Under the engine's 25 s for openItem, with room for the reads after it: the page's own answer always arrives.
    // A usable document ends the load earlier still.
    val loadTimeoutMillis: Long = 20_000,
    val scriptTimeoutMillis: Long = PageDriver.DEFAULT_SCRIPT_TIMEOUT_MILLIS,
    // eBay's self-clearing browser check redirects on its own; Kestrel waits in place, never re-navigates.
    val challengeWaitMillis: Long = 20_000,
    val challengePollMillis: Long = 1_000,
    val hydrationTimeoutMillis: Long = 15_000,
    // A finished page with no bid button after this long has none (ended or fixed-price listing).
    val missingButtonGraceMillis: Long = 3_000,
    val layerOpenTimeoutMillis: Long = 8_000,
    val pollMillis: Long = 150,
    val typingSettleMillis: Long = 1_200,
    // eBay's confirm screen after "Review bid" (a render, maybe a request).
    val reviewTimeoutMillis: Long = 8_000,
    val pressMillis: Long = PageDriver.DEFAULT_PRESS_MILLIS,
    val tapConfirmMillis: Long = 400,
    val tapConfirmPollMillis: Long = 40,
    val probeTimeoutMillis: Long = 800,
    val replyPollMillis: Long = 100,
    // An unrecognised notice is only eBay's final answer if no leading or outbid notice follows it this soon.
    val otherNoticeGraceMillis: Long = 1_200,
    val autoRefreshTimeoutMillis: Long = 12_000,
    val autoRefreshPollMillis: Long = 100,
    // A floor between AUTO_REFRESH calls on one page, in case a caller loops; eBay's own page polls every 2 s at most.
    val autoRefreshMinimumIntervalMillis: Long = 1_000,
    val closeTimeoutMillis: Long = 2_000,
)

/** Where a loaded document is: its kind (from URL and title), URL and title. */
internal data class PageView(
    val kind: PageKind,
    val url: String,
    val title: String,
    val probe: ProbeSnapshot? = null,
)

/** What the item page's model says, plus the AUTO_REFRESH address it carries. */
internal data class ListingRead(
    val listing: Listing?,
    val autoRefreshUrl: String?,
    val problem: String?,
)

/** Reads an item page loaded in a [PageDriver]. Shared by the bid page and the listing fetcher. */
internal class ItemPageReader(
    private val driver: PageDriver,
    private val helper: PageHelper,
    private val parsers: EbayParsers,
    private val utcClock: () -> Long,
    private val timing: PageTiming,
) {
    /**
     * The page a load ended on. A self-clearing challenge is waited out in place when [waitOutChallenge]; the bid page
     * leaves that to the engine, which waits within its own deadline instead of being cut off inside this wait.
     */
    suspend fun settle(
        load: LoadResult,
        waitOutChallenge: Boolean = true,
    ): PageView {
        val outcome = load.outcome
        if (outcome is LoadOutcome.Blocked) {
            val thirdParty = outcome.decision == NavigationDecision.THIRD_PARTY_SIGN_IN
            return PageView(if (thirdParty) PageKind.THIRD_PARTY_SIGN_IN else PageKind.OTHER, outcome.url, "")
        }
        if (outcome is LoadOutcome.NetworkError) {
            throw PageLoadException("eBay could not be reached: ${outcome.description} (${outcome.code})")
        }
        if (outcome == LoadOutcome.RendererGone) {
            throw PageUnavailableException(driver.failure?.description ?: "The page's renderer stopped")
        }
        if (outcome == LoadOutcome.TimedOut) {
            throw PageLoadException("The page did not load within ${timing.loadTimeoutMillis / 1000} s")
        }
        val first = current(load)
        if (first.kind != PageKind.CHALLENGE || !waitOutChallenge) return first
        return driver.waitUntil(timing.challengeWaitMillis, timing.challengePollMillis) {
            current(null).takeIf { it.kind != PageKind.CHALLENGE }
        } ?: current(null)
    }

    /** The document as it is now. */
    suspend fun current(load: LoadResult?): PageView {
        val probe =
            try {
                helper.call(PageFunction.PROBE, ProbeSnapshot.serializer()).takeIf { it.ok }
            } catch (unreadable: PageScriptException) {
                null
            } catch (slow: ScriptTimeoutException) {
                null
            } catch (lost: ScriptAnswerLostException) {
                null
            }
        val url = probe?.url?.takeIf { it.isNotEmpty() } ?: load?.finalUrl ?: driver.currentUrl.orEmpty()
        val title = probe?.title ?: load?.title.orEmpty()
        return PageView(parsers.classify(url, title), url, title, probe)
    }

    /** Parses the listing from the page model. A parser failure is reported, never thrown. */
    suspend fun listing(ref: ItemRef): ListingRead {
        val model = helper.call(PageFunction.MODEL_TEXT, ModelTextSnapshot.serializer())
        if (!model.ok) return ListingRead(null, null, "modelText failed: ${model.error}")
        val listing =
            try {
                parsers.parseListing(model.text, ref, utcClock())
            } catch (parserFailure: Exception) {
                return ListingRead(
                    null,
                    autoRefreshUrlIn(model.text),
                    "the listing parser failed: ${parserFailure.message}",
                )
            }
        val problem = if (listing == null) "no item model in the page (${model.modelScripts} model scripts)" else null
        return ListingRead(listing, autoRefreshUrlIn(model.text), problem)
    }

    private fun autoRefreshUrlIn(pageText: String): String? =
        try {
            parsers.autoRefreshUrl(pageText)
        } catch (parserFailure: Exception) {
            null
        }
}
