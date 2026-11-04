package app.kestrel.web

import app.kestrel.engine.BidPage
import app.kestrel.engine.BidReply
import app.kestrel.engine.InputMethod
import app.kestrel.engine.LayerState
import app.kestrel.model.Money
import app.kestrel.model.PageKind
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebBidPageTest {
    private val driver = FakePageDriver()
    private val ebay = SimulatedEbayPage(driver)
    private val parsers = FakeParsers()
    private val max = Money.of("12.50", "GBP")

    private fun TestScope.page() =
        WebBidPage(driver, PageScript("var VERSION = '1';"), parsers, TestClock(testScheduler))

    private suspend fun WebBidPage.readyToSubmit(): WebBidPage {
        openItem(ukItem)
        assertTrue(openBidLayer() is LayerState.Ready)
        assertNull(typeMax(max).inlineError)
        armReplyObserver()
        return this
    }

    // -------------------------------------------------------------------------------------------- item page

    @Test
    fun `openItem loads the item, installs the helper and parses the listing`() =
        runTest {
            val state = page().openItem(ukItem)
            assertEquals(PageKind.NORMAL, state.kind)
            assertEquals("https://www.ebay.co.uk/itm/127820098965", state.url)
            assertEquals("Britain after Rome | eBay UK", state.title)
            assertEquals(ukItem, state.listing?.ref)
            assertEquals(listOf(ukItem.url), driver.loads)
            assertEquals(1, driver.installs)
            assertEquals("<title>Britain after Rome | eBay UK</title>", parsers.parsedPageTexts.single())
            assertEquals(TestClock.UTC_START, state.listing?.fetchedAtUtcMillis)
        }

    @Test
    fun `a self-clearing challenge is waited out in place, never reloaded`() =
        runTest {
            ebay.url = "https://www.ebay.co.uk/splashui/challenge?ap=1&appName=orch&ru=x"
            ebay.title = "Pardon Our Interruption..."
            driver.on("probe") {
                if (ebay.probes++ >= 3) {
                    ebay.url = "https://www.ebay.co.uk/itm/127820098965"
                    ebay.title = "Britain after Rome | eBay UK"
                }
                json(mapOf("ok" to true, "url" to ebay.url, "title" to ebay.title, "readyState" to "complete"))
            }
            val state = page().openItem(ukItem)
            assertEquals(PageKind.NORMAL, state.kind)
            assertEquals(1, driver.loads.size)
            assertNotNull(state.listing)
        }

    @Test
    fun `a captcha is returned to the engine without a listing`() =
        runTest {
            ebay.url = "https://www.ebay.co.uk/splashui/captcha?ap=1&appName=orch"
            ebay.title = "Security Measure"
            val state = page().openItem(ukItem)
            assertEquals(PageKind.CAPTCHA, state.kind)
            assertNull(state.listing)
            assertTrue(parsers.parsedPageTexts.isEmpty())
        }

    @Test
    fun `a redirect to Google sign-in is reported as third-party sign-in`() =
        runTest {
            driver.loadResult = { url ->
                LoadResult(
                    url,
                    url,
                    "",
                    emptyList(),
                    LoadOutcome.Blocked(
                        "https://accounts.google.com/o/oauth2",
                        NavigationDecision.THIRD_PARTY_SIGN_IN,
                        "google",
                    ),
                )
            }
            val state = page().openItem(ukItem)
            assertEquals(PageKind.THIRD_PARTY_SIGN_IN, state.kind)
            assertEquals("https://accounts.google.com/o/oauth2", state.url)
        }

    @Test
    fun `a network failure throws with the reason`() =
        runTest {
            driver.loadResult =
                { url ->
                    LoadResult(
                        url,
                        url,
                        "",
                        emptyList(),
                        LoadOutcome.NetworkError(-2, "net::ERR_INTERNET_DISCONNECTED"),
                    )
                }
            val page = page()
            val failure = runCatching { page.openItem(ukItem) }.exceptionOrNull()
            assertTrue(failure is PageLoadException)
            assertTrue(failure!!.message!!.contains("ERR_INTERNET_DISCONNECTED"))
        }

    @Test
    fun `a listing parser failure gives no listing instead of failing the page`() =
        runTest {
            parsers.listingFor = { _, _, _ -> throw IllegalStateException("model changed") }
            val state = page().openItem(ukItem)
            assertEquals(PageKind.NORMAL, state.kind)
            assertNull(state.listing)
        }

    // -------------------------------------------------------------------------------------------- bid layer

    @Test
    fun `openBidLayer waits for hydration, probes touch, taps the bid button and reads the layer`() =
        runTest {
            ebay.probesBeforeHydration = 3
            val page = page()
            page.openItem(ukItem)
            val layer = page.openBidLayer()
            assertTrue(layer.toString(), layer is LayerState.Ready)
            layer as LayerState.Ready
            assertEquals(Money.of("5.57", "GBP"), layer.minimum)
            assertEquals("Bid", layer.submitLabel)
            assertEquals(
                "1",
                pageJson
                    .parseToJsonElement(
                        layer.report,
                    ).jsonObject["page"]!!
                    .jsonObject["version"]!!
                    .jsonPrimitive.content,
            )
            assertEquals(listOf(FakeTap(205.5, 461.0, 2.625), FakeTap(206.0, 624.0, 2.625)), driver.taps)
            assertEquals(1, ebay.bidButtonPresses)
            assertTrue(driver.callsOf("openBidLayer").isEmpty())
            assertTrue(driver.callsOf("disarmTap").isNotEmpty())
        }

    @Test
    fun `without trusted touch the bid button is pressed with DOM events`() =
        runTest {
            ebay.touchReachesPage = false
            val page = page()
            page.openItem(ukItem)
            assertTrue(page.openBidLayer() is LayerState.Ready)
            assertEquals(1, driver.taps.size)
            assertEquals(1, driver.callsOf("openBidLayer").size)
            assertTrue(driver.callsOf("armTap").isEmpty())
        }

    @Test
    fun `a probe tap that lands in the wrong place also means DOM events`() =
        runTest {
            ebay.touchMatches = false
            val page = page()
            page.openItem(ukItem)
            assertTrue(page.openBidLayer() is LayerState.Ready)
            assertEquals(1, driver.callsOf("openBidLayer").size)
            assertTrue(
                pageJson
                    .parseToJsonElement(page.report())
                    .jsonObject["kestrel"]
                    .toString()
                    .contains("not used"),
            )
        }

    @Test
    fun `a native tap that misses the bid button falls back to DOM events`() =
        runTest {
            ebay.nativeTapReachesTarget = false
            val page = page()
            page.openItem(ukItem)
            assertTrue(page.openBidLayer() is LayerState.Ready)
            assertEquals(1, driver.callsOf("openBidLayer").size)
            assertEquals(1, ebay.bidButtonPresses)
        }

    @Test
    fun `a signed-out page needs sign-in and nothing is pressed`() =
        runTest {
            ebay.signedIn = false
            val page = page()
            page.openItem(ukItem)
            assertEquals(LayerState.NeedsSignIn, page.openBidLayer())
            assertEquals(0, ebay.bidButtonPresses)
            assertTrue(driver.taps.isEmpty())
        }

    @Test
    fun `a bid button that leads to sign-in needs sign-in`() =
        runTest {
            ebay.signedIn = null
            ebay.signInHref = "https://signin.ebay.co.uk/ws/eBayISAPI.dll?SignIn&ru=x"
            val page = page()
            page.openItem(ukItem)
            assertEquals(LayerState.NeedsSignIn, page.openBidLayer())
            assertEquals(0, ebay.bidButtonPresses)
        }

    @Test
    fun `a verification step is handed to the user with eBay's text`() =
        runTest {
            ebay.interstitial = "verification" to "Verify it's you. We'll text a code to the number ending 1234."
            val page = page()
            page.openItem(ukItem)
            val layer = page.openBidLayer()
            assertTrue(layer is LayerState.NeedsUser)
            assertEquals(
                "Verify it's you. We'll text a code to the number ending 1234.",
                (layer as LayerState.NeedsUser).text,
            )
        }

    @Test
    fun `a sign-in prompt in the layer needs sign-in`() =
        runTest {
            ebay.interstitial = "signIn" to "Please sign in again"
            val page = page()
            page.openItem(ukItem)
            assertEquals(LayerState.NeedsSignIn, page.openBidLayer())
        }

    @Test
    fun `eBay's layer error fails with its text`() =
        runTest {
            ebay.layerError = "Oops. Looks like we're having trouble connecting to our server."
            val page = page()
            page.openItem(ukItem)
            val layer = page.openBidLayer() as LayerState.Failed
            assertTrue(layer.reason.contains("Oops. Looks like we're having trouble connecting"))
        }

    @Test
    fun `an ended listing without a bid button fails after a short grace period`() =
        runTest {
            ebay.bidButtonPresent = false
            val page = page()
            page.openItem(ukItem)
            val layer = page.openBidLayer() as LayerState.Failed
            assertTrue(layer.reason.contains("no bid button"))
            assertTrue(testScheduler.currentTime < 5_000)
        }

    @Test
    fun `a bid button that never hydrates fails`() =
        runTest {
            ebay.probesBeforeHydration = Int.MAX_VALUE
            val page = page()
            page.openItem(ukItem)
            val layer = page.openBidLayer() as LayerState.Failed
            assertTrue(layer.reason.contains("stayed disabled"))
        }

    @Test
    fun `a layer whose bid button Kestrel does not recognise fails and lists what it saw`() =
        runTest {
            ebay.submitLabel = null
            val page = page()
            page.openItem(ukItem)
            val layer = page.openBidLayer() as LayerState.Failed
            assertTrue(layer.reason, layer.reason.contains("Review bid"))
        }

    @Test
    fun `a dead renderer fails the layer without touching the page`() =
        runTest {
            val page = page()
            page.openItem(ukItem)
            driver.failure = DriverFailure.RendererGone(crashed = true)
            val layer = page.openBidLayer() as LayerState.Failed
            assertEquals("The page's renderer crashed", layer.reason)
        }

    // -------------------------------------------------------------------------------------------- typing

    @Test
    fun `typeMax types the site's format, reads the field back and reports the minimum`() =
        runTest {
            val page = page()
            page.openItem(ukItem)
            page.openBidLayer()
            val typed = page.typeMax(max)
            assertEquals("12.50", typed.fieldValue)
            assertNull(typed.inlineError)
            assertEquals(Money.of("5.57", "GBP"), typed.minimum)
            assertEquals(
                "12.50",
                driver
                    .callsOf("typeMax")
                    .single()
                    .arguments
                    .single()
                    .text(),
            )
        }

    @Test
    fun `a whole maximum is typed without decimals`() =
        runTest {
            val page = page()
            page.openItem(ukItem)
            page.openBidLayer()
            assertEquals("90", page.typeMax(Money.of("90", "GBP")).fieldValue)
        }

    @Test
    fun `eBay's too-low error is returned and the page refuses to submit`() =
        runTest {
            val page = page()
            page.openItem(ukItem)
            page.openBidLayer()
            val typed = page.typeMax(Money.of("4", "GBP"))
            assertEquals("Enter £5.57 or more", typed.inlineError)
            val refused = runCatching { page.submit() }.exceptionOrNull()
            assertTrue(refused is SubmitNotDispatchedException)
            assertEquals(0, ebay.presses)
            assertTrue(driver.callsOf("submit").isEmpty())
            assertTrue(driver.callsOf("armTap").none { it.arguments.first().text() == "submit" })
        }

    @Test
    fun `a field that eBay rewrote to another amount is never submitted`() =
        runTest {
            ebay.fieldRewrite = { it.replace(".", "") }
            val page = page()
            page.openItem(ukItem)
            page.openBidLayer()
            val typed = page.typeMax(max)
            assertEquals("1250", typed.fieldValue)
            assertEquals("Kestrel: the amount field shows \"1250\", not 12.50 GBP", typed.inlineError)
            assertTrue(runCatching { page.submit() }.exceptionOrNull() is SubmitNotDispatchedException)
            assertEquals(0, ebay.presses)
        }

    @Test
    fun `a bid button that stays disabled after typing is reported`() =
        runTest {
            ebay.submitEnabledWhenValid = false
            val page = page()
            page.openItem(ukItem)
            page.openBidLayer()
            assertEquals(
                "Kestrel: eBay's bid button stayed disabled after typing the amount",
                page.typeMax(max).inlineError,
            )
        }

    @Test
    fun `a maximum below the minimum eBay shows is refused even without eBay's error`() =
        runTest {
            ebay.minimumMinor = 0
            ebay.minimumAmountText = "£15.00"
            val page = page()
            page.openItem(ukItem)
            page.openBidLayer()
            val typed = page.typeMax(max)
            assertEquals("Kestrel: the maximum is below eBay's minimum of 15.00 GBP", typed.inlineError)
            assertEquals(Money.of("15", "GBP"), typed.minimum)
        }

    @Test
    fun `a maximum in another currency than the listing's is never typed`() =
        runTest {
            val page = page()
            page.openItem(ukItem)
            page.openBidLayer()
            val typed = page.typeMax(Money.of("12.50", "USD"))
            assertEquals("Kestrel: the maximum is in USD but this listing takes bids in GBP", typed.inlineError)
            assertTrue(driver.callsOf("typeMax").isEmpty())
            assertTrue(runCatching { page.submit() }.exceptionOrNull() is SubmitNotDispatchedException)
        }

    @Test
    fun `typing before the item is open says so`() =
        runTest {
            assertEquals("Kestrel: open the item before typing the maximum", page().typeMax(max).inlineError)
            assertTrue(driver.calls.isEmpty())
        }

    // -------------------------------------------------------------------------------------------- submit

    @Test
    fun `submit taps natively when touch works and arms the ceiling with the maximum`() =
        runTest {
            val page = page().readyToSubmit()
            val sent = page.submit()
            assertEquals(InputMethod.NATIVE_TOUCH, sent.method)
            assertEquals("Bid", sent.buttonLabel)
            assertTrue(
                sent.dispatchedAtElapsedMillis in
                    TestClock.ELAPSED_START..TestClock.ELAPSED_START + testScheduler.currentTime,
            )
            assertEquals(FakeTap(206.0, 484.0, 2.625), driver.taps.last())
            assertEquals(1, ebay.presses)
            val ceiling = ebay.ceiling as JsonObject
            assertEquals(1250L, ceiling["ceilingMinor"]!!.jsonPrimitive.long)
            assertEquals("GBP", ceiling["currency"]!!.jsonPrimitive.content)
            assertEquals(
                "12.50",
                driver
                    .callsOf("armTap")
                    .last()
                    .arguments[1]
                    .objectField("expectValue")
                    ?.text(),
            )
            assertTrue(driver.callsOf("submit").isEmpty())
        }

    @Test
    fun `submit can only be called once per page`() =
        runTest {
            val page = page().readyToSubmit()
            page.submit()
            val callsBefore = driver.calls.size
            val second = runCatching { page.submit() }.exceptionOrNull()
            assertTrue(second is IllegalStateException)
            assertTrue(second!!.message!!.contains("one press per page"))
            assertEquals(callsBefore, driver.calls.size)
            assertEquals(1, ebay.presses)
        }

    @Test
    fun `a refused first submit still uses up the page's one press`() =
        runTest {
            val page = page()
            assertTrue(runCatching { page.submit() }.exceptionOrNull() is SubmitNotDispatchedException)
            page.readyToSubmit()
            assertTrue(runCatching { page.submit() }.exceptionOrNull() is IllegalStateException)
            assertEquals(0, ebay.presses)
        }

    @Test
    fun `a native tap that misses the submit falls back to one DOM press`() =
        runTest {
            ebay.nativeTapReachesTarget = false
            val page = page()
            page.openItem(ukItem)
            page.openBidLayer()
            page.typeMax(max)
            val sent = page.submit()
            assertEquals(InputMethod.DOM_EVENTS, sent.method)
            assertEquals(1, ebay.presses)
            assertEquals(1, driver.callsOf("submit").size)
        }

    @Test
    fun `without trusted touch the submit is pressed with DOM events`() =
        runTest {
            ebay.touchReachesPage = false
            val page = page().readyToSubmit()
            val sent = page.submit()
            assertEquals(InputMethod.DOM_EVENTS, sent.method)
            assertTrue(driver.callsOf("armTap").isEmpty())
            assertEquals(
                "12.50",
                driver
                    .callsOf("submit")
                    .single()
                    .arguments
                    .single()
                    .objectField("expectValue")
                    ?.text(),
            )
        }

    @Test
    fun `a field changed since typing stops the press`() =
        runTest {
            val page = page().readyToSubmit()
            ebay.fieldValue = "120.50"
            val refused = runCatching { page.submit() }.exceptionOrNull()
            assertTrue(refused is SubmitNotDispatchedException)
            assertEquals("the amount field no longer shows the typed maximum", refused!!.message)
            assertEquals(0, ebay.presses)
        }

    @Test
    fun `closing the layer withdraws the verified maximum`() =
        runTest {
            val page = page().readyToSubmit()
            page.closeLayer()
            assertTrue(runCatching { page.submit() }.exceptionOrNull() is SubmitNotDispatchedException)
            assertEquals(0, ebay.presses)
        }

    @Test
    fun `a dry-run page cannot submit at all`() =
        runTest {
            val preview: Any = LayerPreviewPage(page())
            assertFalse(preview is BidPage)
            preview as LayerPreviewPage
            assertEquals(PageKind.NORMAL, preview.openItem(ukItem).kind)
            assertTrue(preview.openBidLayer() is LayerState.Ready)
            assertNull(preview.typeMax(max).inlineError)
            preview.closeLayer()
            preview.close()
            assertTrue(driver.destroyed)
            assertEquals(0, ebay.presses)
        }

    @Test
    fun `bid requests are held from the bid button until the moment of the press`() =
        runTest {
            val page = page()
            page.openItem(ukItem)
            page.openBidLayer()
            assertTrue("held before the bid button is pressed", ebay.hold)
            page.typeMax(max)
            page.armReplyObserver()
            assertTrue("still held while waiting for the moment", ebay.hold)
            page.submit()
            assertEquals(listOf(true, true, true, false), ebay.observerArms)
            assertEquals(1, ebay.presses)
            assertEquals(0, ebay.pressesWhileHeld)
        }

    @Test
    fun `a hold that will not release stops the press`() =
        runTest {
            ebay.holdStuck = true
            val page = page().readyToSubmit()
            val refused = runCatching { page.submit() }.exceptionOrNull()
            assertTrue(refused is SubmitNotDispatchedException)
            assertEquals(0, ebay.presses)
        }

    @Test
    fun `a JavaScript dialog the page opens with the layer is handed to the user`() =
        runTest {
            ebay.dialogOnLayerOpen = "confirm: Please confirm you accept the terms of sale"
            val page = page()
            page.openItem(ukItem)
            val layer = page.openBidLayer()
            assertTrue(layer.toString(), layer is LayerState.NeedsUser)
            assertEquals("confirm: Please confirm you accept the terms of sale", (layer as LayerState.NeedsUser).text)
        }

    @Test
    fun `a JavaScript dialog after typing stops the press`() =
        runTest {
            val page = page().readyToSubmit()
            driver.pageOpensDialog("alert: Your session will expire soon")
            val refused = runCatching { page.submit() }.exceptionOrNull()
            assertTrue(refused is SubmitNotDispatchedException)
            assertEquals("eBay showed a message first: alert: Your session will expire soon", refused!!.message)
            assertEquals(0, ebay.presses)
        }

    @Test
    fun `a press whose answer was lost is read back from the page`() =
        runTest {
            ebay.touchReachesPage = false
            ebay.submitAnswerLost = true
            val page = page().readyToSubmit()
            val sent = page.submit()
            assertEquals(InputMethod.DOM_EVENTS, sent.method)
            assertEquals(1, ebay.presses)
        }

    @Test
    fun `a lost answer that the page cannot clear up is never reported as not sent`() =
        runTest {
            ebay.touchReachesPage = false
            ebay.submitAnswerLost = true
            val page = page().readyToSubmit()
            driver.on("replyState") { throw ScriptTimeoutException("A page script did not answer within 5000 ms") }
            val outcome = runCatching { page.submit() }.exceptionOrNull()
            assertTrue(outcome is WebPageException)
            assertFalse("the bid may have been sent", outcome is SubmitNotDispatchedException)
        }

    @Test
    fun `a lost answer read from a newer document is not taken as not sent`() =
        runTest {
            ebay.touchReachesPage = false
            ebay.submitAnswerLost = true
            ebay.navigateAfterPress = true
            val page = page().readyToSubmit()
            val outcome = runCatching { page.submit() }.exceptionOrNull()
            assertTrue(outcome is WebPageException)
            assertFalse("the bid may have been sent before the page moved on", outcome is SubmitNotDispatchedException)
            assertEquals(1, ebay.presses)
        }

    @Test
    fun `a native tap after which the page moved on is possibly sent and never pressed again`() =
        runTest {
            ebay.nativeTapReachesTarget = false
            ebay.navigateOnSubmitTap = true
            val page = page().readyToSubmit()
            assertEquals(InputMethod.NATIVE_TOUCH, page.submit().method)
            assertEquals(1, ebay.presses)
            assertTrue("no DOM fallback on the new document", driver.callsOf("submit").isEmpty())
        }

    @Test
    fun `every press names the document the amount was typed in`() =
        runTest {
            ebay.touchReachesPage = false
            val page = page().readyToSubmit()
            page.submit()
            val options =
                driver
                    .callsOf("submit")
                    .single()
                    .arguments
                    .single()
            assertEquals("document-1", options.objectField("documentToken")?.text())
            assertEquals("12.50", options.objectField("expectValue")?.text())
        }

    @Test
    fun `bid requests are held even when the layer is already open`() =
        runTest {
            val page = page()
            page.openItem(ukItem)
            ebay.layerOpen = true
            assertTrue(page.openBidLayer() is LayerState.Ready)
            assertEquals(0, ebay.bidButtonPresses)
            assertTrue(ebay.hold)
        }

    @Test
    fun `a native tap the page cannot rule out is reported as possibly sent`() =
        runTest {
            ebay.nativeTapReachesTarget = false
            ebay.submitRefusalOverride = "nativeTapUnknown"
            val page = page().readyToSubmit()
            assertEquals(InputMethod.NATIVE_TOUCH, page.submit().method)
        }

    // -------------------------------------------------------------------------------------------- reply

    @Test
    fun `a new error on the amount field after the press is eBay's refusal`() =
        runTest {
            ebay.replyNotices = { emptyList() }
            ebay.inlineErrorAfterPress = "Enter £15.00 or more"
            val page = page().readyToSubmit()
            page.submit()
            assertEquals(BidReply.Rejected("Enter £15.00 or more", 180), page.awaitReply(4_000))
        }

    @Test
    fun `a leading reply carries eBay's text and the confirmbid round trip`() =
        runTest {
            val page = page().readyToSubmit()
            page.submit()
            assertEquals(BidReply.Leading("Your current bid puts you in the lead.", 180), page.awaitReply(4_000))
        }

    @Test
    fun `an outbid reply is read`() =
        runTest {
            ebay.replyNotices = { listOf("Another bidder is winning. Increase your bid.") }
            val page = page().readyToSubmit()
            page.submit()
            assertEquals(BidReply.Outbid("Another bidder is winning. Increase your bid.", 180), page.awaitReply(4_000))
        }

    @Test
    fun `any other notice that stays is eBay's refusal`() =
        runTest {
            ebay.replyNotices = { listOf("This listing has ended.") }
            val page = page().readyToSubmit()
            page.submit()
            assertEquals(BidReply.Rejected("This listing has ended.", 180), page.awaitReply(4_000))
        }

    @Test
    fun `a notice that keeps changing is not taken as the answer`() =
        runTest {
            ebay.replyNotices = { poll -> listOf("Please wait $poll") }
            val page = page().readyToSubmit()
            page.submit()
            assertEquals(BidReply.Unconfirmed(180), page.awaitReply(4_000))
        }

    @Test
    fun `a leading notice that follows a passing notice wins`() =
        runTest {
            ebay.replyNotices =
                { poll ->
                    if (poll <
                        4
                    ) {
                        listOf("Placing your bid")
                    } else {
                        listOf("Your current bid puts you in the lead.")
                    }
                }
            val page = page().readyToSubmit()
            page.submit()
            assertTrue(page.awaitReply(4_000) is BidReply.Leading)
        }

    @Test
    fun `a request the ceiling blocked is a refusal written by Kestrel`() =
        runTest {
            ebay.blockedReason = "amount 5.57 is above the maximum"
            ebay.replyNotices = { emptyList() }
            val page = page().readyToSubmit()
            page.submit()
            val reply = page.awaitReply(4_000) as BidReply.Rejected
            assertEquals(
                "Kestrel: stopped a bid request of 5.57 GBP before it was sent: amount 5.57 is above the maximum",
                reply.text,
            )
        }

    @Test
    fun `no answer at all is unconfirmed without a latency`() =
        runTest {
            ebay.replyNotices = { emptyList() }
            ebay.confirmFinishedPerf = null
            val page = page().readyToSubmit()
            page.submit()
            val waitedFrom = testScheduler.currentTime
            assertEquals(BidReply.Unconfirmed(null), page.awaitReply(4_000))
            assertTrue(testScheduler.currentTime - waitedFrom >= 3_900)
        }

    @Test
    fun `awaiting a reply before any press is unconfirmed`() =
        runTest {
            assertEquals(BidReply.Unconfirmed(null), page().awaitReply(1_000))
        }

    @Test
    fun `the tap guard is disarmed after the reply`() =
        runTest {
            val page = page().readyToSubmit()
            page.submit()
            page.awaitReply(4_000)
            assertNull(ebay.guardTarget)
        }

    // -------------------------------------------------------------------------------------------- auto refresh

    @Test
    fun `autoRefresh fetches the page's own AUTO_REFRESH address and parses it`() =
        runTest {
            val page = page()
            page.openItem(ukItem)
            assertEquals(parsers.autoRefreshState, page.autoRefresh())
            assertEquals(
                "/itemmodules/127820098965?module_groups=AUTO_REFRESH",
                driver
                    .callsOf("autoRefresh")
                    .single()
                    .arguments
                    .single()
                    .text(),
            )
            assertEquals("""{"states":[]}""", parsers.parsedAutoRefresh.single())
        }

    @Test
    fun `autoRefresh never runs more than once a second on one page`() =
        runTest {
            val page = page()
            page.openItem(ukItem)
            page.autoRefresh()
            val first = testScheduler.currentTime
            page.autoRefresh()
            assertTrue(testScheduler.currentTime - first >= 1_000)
        }

    @Test
    fun `autoRefresh gives null on an HTTP error or without an address`() =
        runTest {
            ebay.autoRefreshStatus = 503
            val page = page()
            page.openItem(ukItem)
            assertNull(page.autoRefresh())
            parsers.refreshUrl = null
            val other =
                WebBidPage(
                    FakePageDriver().also {
                        SimulatedEbayPage(it)
                    },
                    PageScript("var VERSION = '1';"),
                    parsers,
                    TestClock(testScheduler),
                )
            other.openItem(ukItem)
            assertNull(other.autoRefresh())
        }

    @Test
    fun `autoRefresh before any item is null`() =
        runTest {
            assertNull(page().autoRefresh())
        }

    // -------------------------------------------------------------------------------------------- report and close

    @Test
    fun `the report carries Kestrel's notes and the page's own report`() =
        runTest {
            val page = page().readyToSubmit()
            page.submit()
            val report = pageJson.parseToJsonElement(page.report()).jsonObject
            val kestrel = report["kestrel"]!!.jsonObject
            assertEquals(ukItem.url, kestrel["item"]!!.jsonPrimitive.content)
            assertEquals("NATIVE_TOUCH", kestrel["submitMethod"]!!.jsonPrimitive.content)
            assertEquals("12.50", kestrel["typedField"]!!.jsonPrimitive.content)
            assertTrue(kestrel["notes"].toString().contains("typed 12.50"))
            assertEquals(
                "Bid",
                report["page"]!!
                    .jsonObject["matched"]!!
                    .jsonObject["submitLabel"]!!
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun `the report still answers when the page is gone`() =
        runTest {
            val page = page()
            page.openItem(ukItem)
            driver.failure = DriverFailure.RendererGone(crashed = false)
            val report = pageJson.parseToJsonElement(page.report()).jsonObject
            assertTrue(report["page"]!!.jsonPrimitive.content.startsWith("unavailable"))
            assertNull(page.readListing())
            assertNull(page.autoRefresh())
            assertTrue(page.typeMax(max).inlineError!!.startsWith("Kestrel:"))
            assertTrue(runCatching { page.openItem(ukItem) }.exceptionOrNull() is PageUnavailableException)
            assertTrue(runCatching { page.submit() }.exceptionOrNull() is SubmitNotDispatchedException)
        }

    @Test
    fun `close destroys the WebView`() =
        runTest {
            page().close()
            assertTrue(driver.destroyed)
        }
}
