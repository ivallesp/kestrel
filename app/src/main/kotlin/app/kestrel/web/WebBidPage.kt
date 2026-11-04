package app.kestrel.web

import app.kestrel.engine.BidLayerPreview
import app.kestrel.engine.BidPage
import app.kestrel.engine.BidReply
import app.kestrel.engine.EngineClock
import app.kestrel.engine.InputMethod
import app.kestrel.engine.LayerState
import app.kestrel.engine.PageState
import app.kestrel.engine.SubmitDispatch
import app.kestrel.engine.TypedAmount
import app.kestrel.model.AutoRefreshState
import app.kestrel.model.EbaySite
import app.kestrel.model.ItemRef
import app.kestrel.model.Listing
import app.kestrel.model.Money
import app.kestrel.model.PageKind
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToLong

/** The amount typed into eBay's field and the value the field showed afterwards, once Kestrel checked them. */
internal data class VerifiedTyping(
    val amount: Money,
    val fieldValue: String,
    /** How many JavaScript dialogs the page had opened when the amount was verified. */
    val dialogCount: Int,
    /** kestrel.js's name for the document the amount was typed in. */
    val documentToken: String?,
)

/** Whether a MotionEvent dispatched to this WebView reached the page as a trusted tap at the intended point. */
internal data class NativeTouchCheck(
    val works: Boolean,
    val detail: String,
)

/**
 * eBay's item page and bid layer in one headless WebView, driven through kestrel.js the way a person would use it.
 *
 * Safety rules enforced here, in addition to kestrel.js's own:
 * - [submit] can be called once per page; a second call throws before touching the page.
 * - [submit] presses only after [typeMax] verified, on this document, that eBay's field shows exactly the maximum.
 * - The bid request is armed with the typed maximum as its ceiling (kestrel.js blocks anything above it).
 * - A native tap is used only after a probe tap on this WebView arrived as a trusted event at the right point.
 * - Captchas, verification, terms and unknown dialogs come back as [LayerState.NeedsUser]; nothing answers them.
 */
class WebBidPage internal constructor(
    private val driver: PageDriver,
    script: PageScript,
    private val parsers: EbayParsers,
    private val clock: EngineClock,
    private val timing: PageTiming = PageTiming(),
) : BidPage {
    private val helper = PageHelper(driver, script, timing.scriptTimeoutMillis)
    private val reader = ItemPageReader(driver, helper, parsers, clock::nowUtcMillis, timing)
    private val submitAttempted = AtomicBoolean(false)
    private val notes = ArrayDeque<String>()

    private var ref: ItemRef? = null
    private var lastListing: Listing? = null
    private var autoRefreshUrl: String? = null
    private var verifiedTyping: VerifiedTyping? = null
    private var nativeTouch: NativeTouchCheck? = null
    private var dispatch: SubmitDispatch? = null
    private var lastAutoRefreshStartElapsed: Long? = null
    private var layerDialogBaseline = 0

    override suspend fun openItem(ref: ItemRef): PageState {
        driver.failure?.let { throw PageUnavailableException(it.description) }
        this.ref = ref
        lastListing = null
        autoRefreshUrl = null
        verifiedTyping = null
        val view = reader.settle(driver.load(ref.url, timing.loadTimeoutMillis))
        note("opened ${ref.url}: ${view.kind} at ${view.url}")
        val listing =
            if (view.kind != PageKind.NORMAL) {
                null
            } else {
                try {
                    readListingFor(ref)
                } catch (unavailable: PageUnavailableException) {
                    throw unavailable
                } catch (problem: WebPageException) {
                    note("listing: ${problem.message}")
                    null
                }
            }
        return PageState(view.kind, view.url, view.title, listing)
    }

    override suspend fun readListing(): Listing? {
        val current = ref ?: return null
        if (driver.failure != null) return null
        return try {
            val view = reader.current(null)
            if (view.kind == PageKind.NORMAL) readListingFor(current) else null
        } catch (problem: WebPageException) {
            note("readListing: ${problem.message}")
            null
        }
    }

    override suspend fun openBidLayer(): LayerState {
        driver.failure?.let { return LayerState.Failed(it.description, report()) }
        return try {
            openLayer()
        } catch (problem: WebPageException) {
            note("openBidLayer: ${problem.message}")
            LayerState.Failed(problem.message ?: "The page did not answer", report())
        } finally {
            quietly { helper.call(PageFunction.DISARM_TAP) }
        }
    }

    override suspend fun typeMax(amount: Money): TypedAmount {
        verifiedTyping = null
        val site = ref?.site ?: return notTyped("open the item before typing the maximum")
        if (amount.minor <= 0) return notTyped("the maximum must be above zero")
        val listingCurrency = lastListing?.currentPrice?.currency
        if (listingCurrency != null && listingCurrency != amount.currency) {
            return notTyped("the maximum is in ${amount.currency} but this listing takes bids in $listingCurrency")
        }
        if (driver.failure != null) return notTyped(driver.failure?.description ?: "the page stopped")
        return try {
            typeVerified(amount, site)
        } catch (problem: WebPageException) {
            note("typeMax: ${problem.message}")
            notTyped(problem.message ?: "the page did not answer")
        }
    }

    override suspend fun closeLayer() {
        verifiedTyping = null
        if (driver.failure != null) return
        quietly {
            val closed = helper.call(PageFunction.CLOSE_LAYER, CloseSnapshot.serializer())
            if (!closed.closed) {
                driver.waitUntil(timing.closeTimeoutMillis, timing.pollMillis) { layerState().takeIf { !it.open } }
            }
        }
    }

    override suspend fun autoRefresh(): AutoRefreshState? {
        if (driver.failure != null) return null
        return try {
            fetchAutoRefresh()
        } catch (problem: WebPageException) {
            note("autoRefresh: ${problem.message}")
            null
        }
    }

    override suspend fun armReplyObserver() {
        if (driver.failure != null) return
        quietly { armObserver(hold = true) }
        quietly { ensureNativeTouchChecked() }
    }

    override suspend fun submit(): SubmitDispatch {
        if (!submitAttempted.compareAndSet(false, true)) {
            throw IllegalStateException("submit() was already called on this page: one press per page")
        }
        val typing =
            verifiedTyping ?: throw SubmitNotDispatchedException("The typed maximum was not verified on this page")
        driver.failure?.let { throw SubmitNotDispatchedException(it.description) }
        // The one place the hold on bid requests is released, with the typed maximum as the ceiling.
        val released =
            try {
                armObserver(hold = false)
            } catch (problem: WebPageException) {
                throw SubmitNotDispatchedException("The bid request could not be armed: ${problem.message}")
            }
        if (!released.ok || released.hold) {
            throw SubmitNotDispatchedException(
                "The bid request could not be armed: ${released.holdReason ?: released.error ?: "still held"}",
            )
        }
        val sent = press(typing)
        dispatch = sent
        note("submit: ${sent.method} on '${sent.buttonLabel}' at ${sent.dispatchedAtElapsedMillis}")
        return sent
    }

    override suspend fun awaitReply(timeoutMillis: Long): BidReply {
        val sent = dispatch ?: return BidReply.Unconfirmed(null)
        // An unrecognised notice is eBay's answer only once it has stayed the same for the grace period; a notice
        // that keeps changing is a countdown or a status line, not a reply.
        var candidate: String? = null
        var candidateSince = 0L
        var answeredLatency: Long? = null
        val reply =
            try {
                driver.waitUntil(timeoutMillis, timing.replyPollMillis) {
                    val state = replyStateOrNull() ?: return@waitUntil null
                    answeredLatency = confirmLatency(state) ?: answeredLatency
                    val latency =
                        answeredLatency
                            ?: (clock.elapsedRealtimeMillis() - sent.dispatchedAtElapsedMillis).coerceAtLeast(0)
                    state.blockedSincePress.firstOrNull()?.let {
                        return@waitUntil BidReply.Rejected(
                            blockedText(it),
                            latency,
                        )
                    }
                    ReplyClassifier.decisive(state.newNotices, state.addedLines, latency)?.let { return@waitUntil it }
                    // An error on the amount field after the press is eBay's answer too (the press required none).
                    val other = ReplyClassifier.otherNotice(state.newNotices) ?: state.inlineError
                    val now = clock.elapsedRealtimeMillis()
                    when {
                        other == null -> {
                            candidate = null
                            null
                        }

                        other != candidate -> {
                            candidate = other
                            candidateSince = now
                            null
                        }

                        now - candidateSince >= timing.otherNoticeGraceMillis -> {
                            BidReply.Rejected(other, latency)
                        }

                        else -> {
                            null
                        }
                    }
                }
            } finally {
                quietly { helper.call(PageFunction.DISARM_TAP) }
            }
        // Unconfirmed carries a latency only when eBay did answer the bid request (without a readable text).
        val answer = reply ?: BidReply.Unconfirmed(answeredLatency)
        note("reply: $answer")
        return answer
    }

    override suspend fun report(): String {
        val page: JsonElement =
            if (driver.failure != null) {
                JsonPrimitive("unavailable: ${driver.failure?.description}")
            } else {
                try {
                    helper.call(PageFunction.REPORT)
                } catch (problem: WebPageException) {
                    JsonPrimitive("unavailable: ${problem.message}")
                }
            }
        val report =
            buildJsonObject {
                put("kestrel", kestrelReport())
                put("page", page)
            }.toString()
        return if (report.length <= REPORT_LIMIT) report else report.take(REPORT_LIMIT)
    }

    override fun close() {
        driver.destroy()
    }

    // ---------------------------------------------------------------------------------------------
    // Item page
    // ---------------------------------------------------------------------------------------------

    private suspend fun readListingFor(ref: ItemRef): Listing? {
        val read = reader.listing(ref)
        read.problem?.let { note("listing: $it") }
        autoRefreshUrl = read.autoRefreshUrl ?: autoRefreshUrl
        lastListing = read.listing ?: lastListing
        return read.listing
    }

    private suspend fun fetchAutoRefresh(): AutoRefreshState? {
        val url =
            autoRefreshUrl ?: ref?.let {
                readListingFor(it)
                autoRefreshUrl
            }
        if (url == null) {
            note("autoRefresh: this page carries no AUTO_REFRESH address")
            return null
        }
        paceAutoRefresh()
        lastAutoRefreshStartElapsed = clock.elapsedRealtimeMillis()
        val started = helper.call(PageFunction.AUTO_REFRESH, AutoRefreshStartSnapshot.serializer(), JsonPrimitive(url))
        val requestId = started.requestId
        if (!started.ok || requestId == null) {
            note("autoRefresh refused: ${started.reason ?: started.error}")
            return null
        }
        val result =
            driver.waitUntil(timing.autoRefreshTimeoutMillis, timing.autoRefreshPollMillis) {
                helper
                    .call(
                        PageFunction.AUTO_REFRESH_RESULT,
                        AutoRefreshResultSnapshot.serializer(),
                        JsonPrimitive(requestId),
                    ).takeIf { it.done || !it.ok }
            }
        val text = result?.text
        if (result == null || result.status != HTTP_OK || text == null) {
            note("autoRefresh: status ${result?.status} ${result?.error ?: result?.reason ?: "no answer"}")
            return null
        }
        return try {
            parsers.parseAutoRefresh(text)
        } catch (parserFailure: Exception) {
            note("autoRefresh: the parser failed: ${parserFailure.message}")
            null
        }
    }

    private suspend fun paceAutoRefresh() {
        val last = lastAutoRefreshStartElapsed ?: return
        val wait = timing.autoRefreshMinimumIntervalMillis - (clock.elapsedRealtimeMillis() - last)
        if (wait > 0) delay(wait)
    }

    // ---------------------------------------------------------------------------------------------
    // Bid layer
    // ---------------------------------------------------------------------------------------------

    private suspend fun openLayer(): LayerState {
        layerDialogBaseline = driver.javaScriptDialogCount
        // From here until submit() no bid request may leave the page, whatever presses what, even if the layer is
        // already open.
        holdBidRequests("openBidLayer")
        decideLayer(layerState(), timedOut = false)?.let { return it }
        awaitHydratedBidButton()?.let { return it }
        ensureNativeTouchChecked()
        pressBidButton()?.let { return it }
        return driver.waitUntil(timing.layerOpenTimeoutMillis, timing.pollMillis) {
            decideLayer(layerState(), timedOut = false)
        } ?: decideLayer(layerState(), timedOut = true) ?: LayerState.Failed("eBay's bid layer did not open", report())
    }

    /** Waits until eBay's scripts made the bid button usable. Returns the reason to stop, or null to go on. */
    private suspend fun awaitHydratedBidButton(): LayerState? {
        val startedAt = clock.elapsedRealtimeMillis()
        var last: ProbeSnapshot? = null
        driver.waitUntil(timing.hydrationTimeoutMillis, timing.pollMillis) {
            val probe = probe().also { last = it }
            val button = probe.bidButton
            val waitedLongEnough = clock.elapsedRealtimeMillis() - startedAt >= timing.missingButtonGraceMillis
            probe.takeIf {
                probe.signedIn == false ||
                    (button.signInHref != null && probe.signedIn != true) ||
                    (button.present && !button.disabled && !button.loading) ||
                    (!button.present && probe.readyState == "complete" && waitedLongEnough)
            }
        }
        val probe = last ?: probe()
        val button = probe.bidButton
        val kind = parsers.classify(probe.url, probe.title)
        return when {
            kind == PageKind.SIGN_IN || probe.signedIn == false -> LayerState.NeedsSignIn
            button.signInHref != null && probe.signedIn != true -> LayerState.NeedsSignIn
            kind != PageKind.NORMAL -> stateForPage(kind, probe.title, probe.url)
            !button.present -> LayerState.Failed("This page has no bid button (the auction may have ended)", report())
            button.disabled || button.loading -> LayerState.Failed("eBay's bid button stayed disabled", report())
            else -> null
        }
    }

    /** Presses the bid button: a guarded native tap when touch works, else DOM events. Null when it was pressed. */
    private suspend fun pressBidButton(): LayerState? {
        if (nativeTouch?.works == true) {
            val target = helper.call(PageFunction.ARM_TAP, TapTargetSnapshot.serializer(), JsonPrimitive("bidButton"))
            if (target.ok && target.hit == true && tapSafely(target)) {
                val reached =
                    driver.waitUntil(timing.tapConfirmMillis, timing.tapConfirmPollMillis) {
                        tapResult().takeIf { it.reached }
                    }
                if (reached != null) {
                    note("bid button: native tap")
                    return null
                }
            }
            note("bid button: native tap did not reach it (${target.reason ?: "hit ${target.hit}"}), using DOM events")
        }
        val opened = helper.call(PageFunction.OPEN_BID_LAYER, OpenSnapshot.serializer())
        return when {
            opened.ok || opened.reason == "nativeTapReached" -> {
                null
            }

            opened.reason == "signInRequired" -> {
                LayerState.NeedsSignIn
            }

            else -> {
                LayerState.Failed(
                    "Kestrel could not press eBay's bid button (${opened.reason ?: opened.error})",
                    report(),
                )
            }
        }
    }

    /** The decision a layer snapshot allows, or null to keep waiting. */
    private suspend fun decideLayer(
        state: LayerSnapshot,
        timedOut: Boolean,
    ): LayerState? {
        if (!state.ok) throw PageScriptException("layerState failed: ${state.error}")
        dialogSince(layerDialogBaseline)?.let { return LayerState.NeedsUser(it, report()) }
        val kind = parsers.classify(state.url, state.title)
        if (state.signInRequired || kind == PageKind.SIGN_IN) return LayerState.NeedsSignIn
        if (kind != PageKind.NORMAL) return stateForPage(kind, state.title, state.url)
        state.interstitial?.let { return LayerState.NeedsUser(it.text.ifBlank { it.kind }, report()) }
        val submit = state.submit
        if (state.ready && state.input != null && submit != null && submit.found) {
            return LayerState.Ready(
                minimumOf(state.minimumAmountText, state.lowestPowerBidAmountText),
                submit.label,
                report(),
            )
        }
        state.layerError?.let { return LayerState.Failed("eBay could not open the bid layer: $it", report()) }
        if (!timedOut) return null
        val pending = state.pendingDialogText
        return when {
            state.open && state.input != null -> {
                val seen = submit?.rejected.orEmpty().mapNotNull { it.label }
                LayerState.Failed(
                    "The bid layer opened but Kestrel did not recognise its bid button (seen: $seen)",
                    report(),
                )
            }

            pending != null -> {
                LayerState.NeedsUser(pending, report())
            }

            else -> {
                LayerState.Failed("eBay's bid layer did not open", report())
            }
        }
    }

    private suspend fun stateForPage(
        kind: PageKind,
        title: String,
        url: String,
    ): LayerState =
        when (kind) {
            PageKind.SIGN_IN -> LayerState.NeedsSignIn
            PageKind.RATE_WALL -> LayerState.Failed("eBay's request-rate wall is up ($title)", report())
            PageKind.NOT_FOUND -> LayerState.Failed("The listing is no longer there", report())
            else -> LayerState.NeedsUser(title.ifBlank { url }, report())
        }

    private suspend fun typeVerified(
        amount: Money,
        site: EbaySite,
    ): TypedAmount {
        holdBidRequests("typeMax")
        val before = layerState()
        val input = before.input ?: return notTyped("eBay's amount field is not open")
        val text = AmountText.forInput(amount, site, input.type)
        val typed = helper.call(PageFunction.TYPE_MAX, TypedSnapshot.serializer(), JsonPrimitive(text))
        if (!typed.ok) return notTyped("the amount could not be typed (${typed.reason ?: typed.error})")
        // eBay may validate after a short delay: wait for its error or for the button to enable.
        val settled =
            driver.waitUntil(timing.typingSettleMillis, timing.pollMillis) {
                layerState().takeIf { it.inlineError != null || it.submit?.enabled == true || it.interstitial != null }
            } ?: layerState()
        val fieldValue = settled.input?.value ?: typed.value
        val minimum =
            minimumOf(
                settled.minimumAmountText ?: typed.minimumAmountText,
                settled.lowestPowerBidAmountText,
                amount.currency,
            )
        val shown = AmountText.readField(fieldValue, site, amount.currency)
        val interstitial = settled.interstitial
        val submit = settled.submit
        val dialog = dialogSince(layerDialogBaseline)
        val problem =
            when {
                dialog != null -> {
                    kestrel("eBay showed a message that needs you: $dialog")
                }

                interstitial != null -> {
                    kestrel(
                        "eBay is asking for something first (${interstitial.kind}): ${interstitial.text}",
                    )
                }

                settled.inlineError != null -> {
                    settled.inlineError
                }

                shown != amount -> {
                    kestrel("the amount field shows \"$fieldValue\", not ${describe(amount)}")
                }

                minimum != null && amount < minimum -> {
                    kestrel(
                        "the maximum is below eBay's minimum of ${describe(minimum)}",
                    )
                }

                submit == null || !submit.found -> {
                    kestrel("eBay's bid button is not in the layer")
                }

                !submit.enabled -> {
                    kestrel("eBay's bid button stayed disabled after typing the amount")
                }

                else -> {
                    null
                }
            }
        if (problem ==
            null
        ) {
            verifiedTyping = VerifiedTyping(amount, fieldValue, driver.javaScriptDialogCount, typed.documentToken)
        }
        note("typed $text, field shows \"$fieldValue\", ${problem ?: "verified"}")
        return TypedAmount(fieldValue, problem, minimum)
    }

    private fun minimumOf(
        minimumAmountText: String?,
        lowestPowerBidAmountText: String?,
        currency: String? = bidCurrency(),
    ): Money? {
        if (currency == null) return null
        return minimumAmountText?.let { AmountText.parseDisplayed(it, currency) }
            ?: lowestPowerBidAmountText?.let { AmountText.parseDisplayed(it, currency) }
    }

    /** The currency a bid is placed in: the listing's own (bidflow IS13, MF12), else the site's. */
    private fun bidCurrency(): String? = lastListing?.currentPrice?.currency ?: ref?.site?.currency

    // ---------------------------------------------------------------------------------------------
    // Pressing submit
    // ---------------------------------------------------------------------------------------------

    private suspend fun press(typing: VerifiedTyping): SubmitDispatch {
        dialogSince(typing.dialogCount)?.let { throw SubmitNotDispatchedException("eBay showed a message first: $it") }
        val expectation = buildJsonObject { put("expectValue", typing.fieldValue) }
        var tapDispatchedAt: Long? = null
        var tapLabel: String? = null
        if (nativeTouch?.works == true) {
            val target =
                try {
                    helper.call(
                        PageFunction.ARM_TAP,
                        TapTargetSnapshot.serializer(),
                        JsonPrimitive("submit"),
                        expectation,
                    )
                } catch (problem: WebPageException) {
                    throw SubmitNotDispatchedException("The page did not answer before the press: ${problem.message}")
                }
            if (!target.ok && target.reason != NOT_IN_VIEWPORT) {
                throw SubmitNotDispatchedException(describeRefusal(target.reason ?: target.error))
            }
            if (target.ok && target.hit == true && tapSafely(target)) {
                tapDispatchedAt = clock.elapsedRealtimeMillis()
                tapLabel = target.label
                val reached =
                    driver.waitUntil(timing.tapConfirmMillis, timing.tapConfirmPollMillis) {
                        tapResult().takeIf { it.reached }
                    }
                if (reached != null) return SubmitDispatch(tapDispatchedAt, InputMethod.NATIVE_TOUCH, target.label)
                note("submit: the native tap did not reach the button, pressing with DOM events")
            }
        }
        val before = clock.elapsedRealtimeMillis()
        val pressed =
            try {
                helper.call(PageFunction.SUBMIT, PressSnapshot.serializer(), expectation)
            } catch (problem: WebPageException) {
                // The press may have run even though its answer was lost: ask the page before saying anything.
                return pressAfterLostAnswer(problem, typing, before, tapDispatchedAt, tapLabel)
            }
        val after = clock.elapsedRealtimeMillis()
        if (pressed.ok && pressed.dispatched) {
            return SubmitDispatch((before + after) / 2, InputMethod.DOM_EVENTS, pressed.label)
        }
        if (pressed.reason in nativeOutcomeUnknownOrReached && tapDispatchedAt != null) {
            return SubmitDispatch(tapDispatchedAt, InputMethod.NATIVE_TOUCH, tapLabel)
        }
        throw SubmitNotDispatchedException(describeRefusal(pressed.reason ?: pressed.error))
    }

    /**
     * The submit call failed (timeout, page error). The page's own record of the press decides, but only the record of
     * the document the amount was typed in: after a navigation the helper starts afresh and knows nothing. When the
     * page cannot tell, the outcome is unknown, which is never reported as "not sent".
     */
    private suspend fun pressAfterLostAnswer(
        problem: WebPageException,
        typing: VerifiedTyping,
        before: Long,
        tapDispatchedAt: Long?,
        tapLabel: String?,
    ): SubmitDispatch {
        val state = replyStateOrNull()
        val sameDocument = state != null && typing.documentToken != null && state.documentToken == typing.documentToken
        return when {
            state == null || !sameDocument -> {
                throw WebPageException("The page did not answer the press; the bid may have been sent", problem)
            }

            state.pressed && state.pressMethod == "native" -> {
                SubmitDispatch(tapDispatchedAt ?: before, InputMethod.NATIVE_TOUCH, tapLabel)
            }

            state.pressed -> {
                SubmitDispatch(before, InputMethod.DOM_EVENTS, null)
            }

            tapDispatchedAt != null -> {
                SubmitDispatch(tapDispatchedAt, InputMethod.NATIVE_TOUCH, tapLabel)
            }

            else -> {
                throw SubmitNotDispatchedException("The page did not press the bid button: ${problem.message}")
            }
        }
    }

    private suspend fun tapSafely(target: TapTargetSnapshot): Boolean =
        try {
            driver.tap(target.x, target.y, target.devicePixelRatio, timing.pressMillis)
            true
        } catch (refused: IllegalArgumentException) {
            note("tap refused: ${refused.message}")
            false
        }

    private suspend fun tapResult(): TapResultSnapshot =
        helper.call(PageFunction.TAP_RESULT, TapResultSnapshot.serializer())

    /**
     * Arms kestrel.js's watch on the bid request. With [hold], every bid request is blocked (Kestrel is not pressing
     * yet: a dry run, or the wait before the snipe); without it, one bid up to the verified maximum may go out.
     */
    private suspend fun armObserver(hold: Boolean): ObserverSnapshot {
        val typing = verifiedTyping
        val options =
            buildJsonObject {
                put("hold", hold)
                if (typing != null) {
                    put("ceilingMinor", typing.amount.minor)
                    put("currency", typing.amount.currency)
                }
            }
        return helper.call(PageFunction.ARM_OBSERVER, ObserverSnapshot.serializer(), options)
    }

    /** The newest JavaScript dialog the page opened after [count] dialogs, or null when there was none. */
    private fun dialogSince(count: Int): String? =
        if (driver.javaScriptDialogCount > count) driver.javaScriptDialogs.lastOrNull() ?: "a dialog" else null

    /** Blocks bid requests on this document until submit() releases them. A page that stopped is thrown. */
    private suspend fun holdBidRequests(where: String) {
        try {
            armObserver(hold = true)
        } catch (unavailable: PageUnavailableException) {
            throw unavailable
        } catch (problem: WebPageException) {
            note("$where: the hold on bid requests could not be armed: ${problem.message}")
        }
    }

    private suspend fun ensureNativeTouchChecked(): NativeTouchCheck {
        nativeTouch?.let { return it }
        val check =
            try {
                probeNativeTouch()
            } catch (unavailable: PageUnavailableException) {
                throw unavailable
            } catch (problem: WebPageException) {
                NativeTouchCheck(false, "the touch probe failed: ${problem.message}")
            }
        nativeTouch = check
        note("native touch: ${check.detail}")
        return check
    }

    /** Taps the page once with every tap swallowed by kestrel.js, then asks whether it arrived trusted, in place. */
    private suspend fun probeNativeTouch(): NativeTouchCheck {
        val armed = helper.call(PageFunction.ARM_TOUCH_PROBE, ProbeTapSnapshot.serializer())
        if (!armed.ok) return NativeTouchCheck(false, "the touch probe could not be armed: ${armed.error}")
        try {
            if (!tapSafely(
                    TapTargetSnapshot(ok = true, x = armed.x, y = armed.y, devicePixelRatio = armed.devicePixelRatio),
                )
            ) {
                return NativeTouchCheck(false, "the probe tap was outside the view")
            }
            val seen =
                driver.waitUntil(timing.probeTimeoutMillis, timing.tapConfirmPollMillis) {
                    helper
                        .call(PageFunction.TOUCH_PROBE_RESULT, ProbeOutcomeSnapshot.serializer(), JsonPrimitive(false))
                        .takeIf { it.trusted }
                }
            return when {
                seen == null -> {
                    NativeTouchCheck(false, "no trusted event reached the page")
                }

                !seen.matched -> {
                    val first = seen.events.firstOrNull()
                    NativeTouchCheck(
                        false,
                        "the tap arrived at (${first?.x}, ${first?.y}), not (${seen.expectClientX}, ${seen.expectClientY})",
                    )
                }

                else -> {
                    NativeTouchCheck(true, "a trusted tap reached the page at the intended point")
                }
            }
        } finally {
            quietly { helper.call(PageFunction.TOUCH_PROBE_RESULT, JsonPrimitive(true)) }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Reply
    // ---------------------------------------------------------------------------------------------

    private suspend fun replyStateOrNull(): ReplySnapshot? =
        try {
            helper.call(PageFunction.REPLY_STATE, ReplySnapshot.serializer()).takeIf { it.ok }
        } catch (unavailable: PageUnavailableException) {
            null
        } catch (problem: WebPageException) {
            note("replyState: ${problem.message}")
            null
        }

    /** From the press to eBay's answer to confirmbid, both on the page's clock; null until the answer arrived. */
    private fun confirmLatency(state: ReplySnapshot): Long? {
        val pressAt = state.pressPerfTime ?: return null
        val answeredAt =
            state.records
                .filter { it.action == CONFIRM_BID && it.finishedPerf != null }
                .minByOrNull { if (it.via == "resource") 1 else 0 }
                ?.finishedPerf
                ?: return null
        return if (answeredAt >= pressAt) (answeredAt - pressAt).roundToLong() else null
    }

    private fun blockedText(blocked: BlockedRequest): String {
        val price = blocked.requestPrice?.let { " of ${it.value} ${it.currency.orEmpty()}".trimEnd() }.orEmpty()
        return kestrel("stopped a bid request$price before it was sent: ${blocked.reason}")
    }

    // ---------------------------------------------------------------------------------------------
    // Small helpers
    // ---------------------------------------------------------------------------------------------

    private suspend fun probe(): ProbeSnapshot {
        val probe = helper.call(PageFunction.PROBE, ProbeSnapshot.serializer())
        if (!probe.ok) throw PageScriptException("probe failed: ${probe.error}")
        return probe
    }

    private suspend fun layerState(): LayerSnapshot = helper.call(PageFunction.LAYER_STATE, LayerSnapshot.serializer())

    /** Runs a best-effort page call; a page that stopped or did not answer is noted, not thrown. */
    private suspend fun quietly(action: suspend () -> Unit) {
        try {
            action()
        } catch (problem: WebPageException) {
            note("${problem.javaClass.simpleName}: ${problem.message}")
        }
    }

    private fun notTyped(reason: String): TypedAmount {
        note("typeMax: $reason")
        return TypedAmount(fieldValue = "", inlineError = kestrel(reason), minimum = null)
    }

    // The engine may read report() from another coroutine while a page call is still writing notes.
    private fun note(text: String) {
        synchronized(notes) {
            notes.addLast("${clock.elapsedRealtimeMillis()} $text".take(NOTE_LIMIT))
            while (notes.size > NOTES_KEPT) notes.removeFirst()
        }
    }

    private fun kestrelReport(): JsonObject =
        buildJsonObject {
            put("item", ref?.url)
            put("nativeTouch", nativeTouch?.let { "${if (it.works) "works" else "not used"}: ${it.detail}" })
            put("typedField", verifiedTyping?.fieldValue)
            put("submitAttempted", submitAttempted.get())
            put("submitMethod", dispatch?.method?.name)
            put("submitAtElapsedMillis", dispatch?.dispatchedAtElapsedMillis)
            put("failure", driver.failure?.description)
            val noted = synchronized(notes) { notes.toList() }
            put("notes", buildJsonArray { noted.forEach { add(JsonPrimitive(it)) } })
            put("driver", buildJsonArray { driver.diagnostics.forEach { add(JsonPrimitive(it)) } })
        }

    private companion object {
        const val HTTP_OK = 200
        const val CONFIRM_BID = "confirmbid"
        const val NOT_IN_VIEWPORT = "notInViewport"

        // kestrel.js refuses a DOM press with these when a native tap reached the button, or may have.
        val nativeOutcomeUnknownOrReached = setOf("nativeTapReached", "nativeTapUnknown")
        const val REPORT_LIMIT = 32_000
        const val NOTE_LIMIT = 400
        const val NOTES_KEPT = 40

        /** Messages Kestrel itself writes where eBay's text would otherwise go, marked so nobody reads them as eBay's. */
        fun kestrel(text: String) = "Kestrel: $text"

        fun describe(money: Money) = "${money.major.toPlainString()} ${money.currency}"

        fun describeRefusal(reason: String?): String =
            when (reason) {
                "bidLayerClosed" -> "eBay's bid layer is no longer open"
                "submitNotFound" -> "eBay's bid button is not in the layer"
                "submitDisabled" -> "eBay's bid button is disabled"
                "amountChanged" -> "the amount field no longer shows the typed maximum"
                "inlineError" -> "eBay shows an error for the amount"
                "interstitial" -> "eBay is asking for something before the bid (verification or terms)"
                "alreadySubmitted" -> "the bid was already pressed on this page"
                "expectedValueMissing" -> "the typed maximum was not passed to the page"
                "bidButtonNotRecognised" -> "eBay's bid button does not say bid (it may carry an amount)"
                "covered" -> "another dialog lies over eBay's bid button"
                "nativeTapUnknown" -> "a tap on the bid button may have landed, so it is not pressed again"
                null -> "the page did not say why"
                else -> reason
            }
    }
}

/**
 * The dry-run side of a bid page: it has no submit step and cannot be cast to [BidPage], so code holding it has no
 * way to place a bid (ARCHITECTURE section 1, rule 6).
 */
class LayerPreviewPage internal constructor(
    private val page: WebBidPage,
) : BidLayerPreview,
    AutoCloseable {
    override suspend fun openItem(ref: ItemRef): PageState = page.openItem(ref)

    override suspend fun readListing(): Listing? = page.readListing()

    override suspend fun openBidLayer(): LayerState = page.openBidLayer()

    override suspend fun typeMax(amount: Money): TypedAmount = page.typeMax(amount)

    override suspend fun closeLayer() = page.closeLayer()

    suspend fun report(): String = page.report()

    override fun close() = page.close()
}
