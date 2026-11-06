package app.kestrel.ui.screens.add

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import app.kestrel.data.AddResult
import app.kestrel.model.EbaySite
import app.kestrel.model.ItemRef
import app.kestrel.model.ListingStatus
import app.kestrel.model.Money
import app.kestrel.model.PageKind
import app.kestrel.model.ParsedLink
import app.kestrel.model.SnipeGroup
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.fixture.CatalogueFixture.gbp
import app.kestrel.ui.model.LotStatus
import app.kestrel.web.FetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** S2's logic with fake services: the link, the fetch, the plan and arming, without a phone or a network. */
@OptIn(ExperimentalCoroutinesApi::class)
class AddViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val itemLink = "https://www.ebay.co.uk/itm/127504318862"

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A ViewModel whose state and events are collected for the whole test. */
    private class Subject(
        val viewModel: AddViewModel,
        val events: MutableList<AddEvent>,
    ) {
        val page: AddPage get() = viewModel.uiState.value.page

        fun type(text: String) {
            viewModel.maxField.setTextAndPlaceCursorAtEnd(text)
            Snapshot.sendApplyNotifications()
        }
    }

    private fun TestScope.subject(
        services: FakeAddServices,
        link: String? = itemLink,
    ): Subject {
        val viewModel = AddViewModel(link, services)
        val events = mutableListOf<AddEvent>()
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
        backgroundScope.launch(dispatcher) { viewModel.events.collect { events += it } }
        return Subject(viewModel, events)
    }

    @Test
    fun aListingFromAnotherSiteIsArmedInItsOwnCurrency() =
        runTest(dispatcher) {
            // An ebay.de link: the fetch opens the item on ebay.co.uk, where its price stays in euros.
            val german =
                AddFixtures.sonyA700.copy(
                    ref = ItemRef(EbaySite.UK, "127504318862"),
                    currentPrice = Money.of("60.00", "EUR"),
                    currentPriceInSiteCurrency = Money.of("52.10", "GBP"),
                    postage = null,
                    buyerProtectionFee = null,
                )
            val services = FakeAddServices(fetchResult = { FetchResult.Loaded(german) })
            val subject = subject(services, link = "https://www.ebay.de/itm/127504318862")

            subject.type("90.00")
            subject.viewModel.arm()
            assertEquals(Money.of("90.00", "EUR"), services.added.single().maxBid)
        }

    @Test
    fun aListingInACurrencyKestrelCannotBidInCanOnlyBeWatched() =
        runTest(dispatcher) {
            val hongKong =
                AddFixtures.sonyA700.copy(
                    ref = ItemRef(EbaySite.UK, "127504318862"),
                    currentPrice = Money.of("600.00", "HKD"),
                    currentPriceInSiteCurrency = null,
                    postage = null,
                    buyerProtectionFee = null,
                )
            val services = FakeAddServices(fetchResult = { FetchResult.Loaded(hongKong) })
            val subject = subject(services, link = "https://www.ebay.com.hk/itm/127504318862")

            subject.type("700")
            subject.viewModel.arm()
            assertTrue(services.added.isEmpty())
            assertEquals(
                listOf<AddEvent>(
                    AddEvent.Message("Kestrel can’t bid in HKD yet. You can still watch this listing without a max."),
                ),
                subject.events,
            )

            subject.type("")
            subject.viewModel.arm()
            assertNull(services.added.single().maxBid)
        }

    @Test
    fun aSharedLinkLoadsTheListingAndOpensTheEmptyPlan() =
        runTest(dispatcher) {
            val services = FakeAddServices()
            val subject = subject(services)
            assertEquals(1, services.fetched.size)
            assertTrue(services.fetched.single() is ParsedLink.Item)
            val page = subject.page as AddPage.Ready
            assertEquals("Sony Alpha A700 body", page.header.title)
            assertEquals("Includes £3.13 Buyer Protection (private seller).", page.facts.buyerProtection)
            // The A700 has 7 bids and a private seller's fee in its price, so the conservative minimum applies (N10).
            assertEquals("£66.08", page.max.minimum)
            assertEquals(ArmButton.Watch, page.bar.button)
            assertEquals("No max yet: Kestrel will only watch the price.", page.bar.summary.text)
        }

    @Test
    fun theLinkEntryWaitsForALink() =
        runTest(dispatcher) {
            val services = FakeAddServices()
            val subject = subject(services, link = null)
            assertTrue(subject.page is AddPage.LinkEntry)
            assertTrue(services.fetched.isEmpty())
        }

    @Test
    fun aPastedTextThatIsNotEbayStaysOnTheLinkEntryWithAMessage() =
        runTest(dispatcher) {
            val services = FakeAddServices()
            val subject = subject(services, link = null)
            subject.viewModel.paste("https://example.com/whatever")
            assertTrue(subject.page is AddPage.LinkEntry)
            assertEquals(listOf(AddEvent.Message("That link isn’t an eBay listing.")), subject.events)
            assertTrue(services.fetched.isEmpty())
        }

    @Test
    fun aSharedTextThatIsNotEbayFailsWithTheReason() =
        runTest(dispatcher) {
            val subject = subject(FakeAddServices(), link = "my holiday photos")
            val page = subject.page as AddPage.Failed
            assertEquals("That link isn’t an eBay listing.", page.reason)
            assertFalse(page.canRetry)
        }

    @Test
    fun aListingAlreadyOnTheListOpensWithoutLoadingEbay() =
        runTest(dispatcher) {
            val existing = CatalogueFixture.canon
            val services = FakeAddServices(snipes = listOf(existing))
            val subject = subject(services, link = existing.ref.url)
            val page = subject.page as AddPage.AlreadySniped
            assertEquals(existing.id, page.snipeId)
            assertTrue(page.status is LotStatus.Armed)
            assertTrue(services.fetched.isEmpty())
        }

    @Test
    fun aFailedLoadSaysEbayDidNotRespondAndTriesAgain() =
        runTest(dispatcher) {
            val services =
                FakeAddServices(fetchResult = { FetchResult.Failed("Loading the item page timed out after 30 s") })
            val subject = subject(services)
            val failed = subject.page as AddPage.Failed
            assertEquals("eBay didn’t respond.", failed.reason)
            assertTrue(failed.canRetry)
            services.fetchResult = { FetchResult.Loaded(AddFixtures.sonyA700) }
            subject.viewModel.retry()
            assertEquals(2, services.fetched.size)
            assertTrue(subject.page is AddPage.Ready)
        }

    @Test
    fun aBuyItNowListingCannotBeSniped() =
        runTest(dispatcher) {
            val services = FakeAddServices(fetchResult = { FetchResult.NotAnAuction(AddFixtures.sonyA700BuyItNow) })
            val page = subject(services).page as AddPage.CannotSnipe
            assertEquals(CannotSnipeReason.BuyItNow, page.reason)
        }

    @Test
    fun anEndedAuctionCannotBeSniped() =
        runTest(dispatcher) {
            val ended = AddFixtures.sonyA700.copy(status = ListingStatus.ENDED)
            val page = subject(FakeAddServices(fetchResult = { FetchResult.Loaded(ended) })).page as AddPage.CannotSnipe
            assertEquals(CannotSnipeReason.Ended, page.reason)
        }

    @Test
    fun aSignInPageOffersSignInAndTryAgain() =
        runTest(dispatcher) {
            val services =
                FakeAddServices(fetchResult = { FetchResult.Blocked(PageKind.SIGN_IN, "https://signin.ebay.co.uk/") })
            val page = subject(services).page as AddPage.Failed
            assertTrue(page.signIn)
            assertTrue(page.canRetry)
        }

    @Test
    fun armingStoresTheTypedMaxExactlyAndClosesWithThePlan() =
        runTest(dispatcher) {
            val services = FakeAddServices()
            val subject = subject(services)
            subject.type("90.00")
            val page = subject.page as AddPage.Ready
            assertEquals("Bids £90.00 on Thu 13 Nov at 20:04:12 GMT", page.bar.summary.text)
            assertEquals(ArmButton.Arm(enabled = true), page.bar.button)
            subject.viewModel.arm()
            val call = services.added.single()
            assertEquals(gbp("90.00"), call.maxBid)
            assertEquals(6, call.leadSeconds)
            assertNull(call.groupId)
            assertEquals(listOf(AddEvent.Close("Armed: bids Thu 13 Nov at 20:04:12")), subject.events)
        }

    @Test
    fun theLeadTimeMovesTheBidTime() =
        runTest(dispatcher) {
            val services = FakeAddServices()
            val subject = subject(services)
            subject.type("90.00")
            subject.viewModel.selectLead(2)
            val page = subject.page as AddPage.Ready
            assertEquals("Bids £90.00 on Thu 13 Nov at 20:04:08 GMT", page.bar.summary.text)
            subject.viewModel.arm()
            assertEquals(10, services.added.single().leadSeconds)
        }

    @Test
    fun anEmptyMaxWatchesWithoutBidding() =
        runTest(dispatcher) {
            val services = FakeAddServices()
            val subject = subject(services)
            subject.viewModel.arm()
            assertNull(services.added.single().maxBid)
            assertEquals(listOf(AddEvent.Close(AddViewModel.WATCHING_MESSAGE)), subject.events)
        }

    @Test
    fun aMaxBelowEbaysMinimumCannotArmAndSaysWhatToType() =
        runTest(dispatcher) {
            val services = FakeAddServices()
            val subject = subject(services)
            subject.type("90.00")
            subject.type("60.00")
            val page = subject.page as AddPage.Ready
            assertEquals("Too low: eBay needs at least £66.08", page.max.error)
            assertEquals(ArmButton.Arm(enabled = false), page.bar.button)
            assertEquals("Set at least £66.08 to arm", page.bar.summary.text)
            assertEquals(WorstCase.Sum(AddPlanner.NO_AMOUNT, "£8.95", AddPlanner.NO_AMOUNT), page.worstCase)
            subject.viewModel.arm()
            assertTrue(services.added.isEmpty())
        }

    @Test
    fun signedOutArmingOpensSignInFirst() =
        runTest(dispatcher) {
            val services =
                FakeAddServices(fetchResult = { FetchResult.Loaded(AddFixtures.sonyA700.copy(signedIn = false)) })
            services.signedIn = false
            val subject = subject(services)
            subject.type("90.00")
            assertTrue((subject.page as AddPage.Ready).bar.signInNeeded)
            subject.viewModel.arm()
            assertEquals(listOf(AddEvent.OpenSignIn), subject.events)
            assertTrue(services.added.isEmpty())
            // Back from sign-in: the session is there and the snipe arms.
            services.signedIn = true
            subject.viewModel.refreshSession()
            assertFalse((subject.page as AddPage.Ready).bar.signInNeeded)
            subject.viewModel.arm()
            assertEquals(gbp("90.00"), services.added.single().maxBid)
        }

    @Test
    fun aMaxMoreThanThreeTimesThePriceIsSpeltOutBeforeArming() =
        runTest(dispatcher) {
            val services = FakeAddServices()
            val subject = subject(services)
            subject.type("900.00")
            subject.viewModel.arm()
            assertTrue(services.added.isEmpty())
            val check = requireNotNull((subject.page as AddPage.Ready).wordsCheck)
            assertEquals("Nine hundred pounds", check.words)
            assertEquals("That’s more than three times the current price of £64.00.", check.reason)
            subject.viewModel.dismissWords()
            assertNull((subject.page as AddPage.Ready).wordsCheck)
            assertTrue(services.added.isEmpty())
            subject.viewModel.arm()
            subject.viewModel.confirmWords()
            assertEquals(gbp("900.00"), services.added.single().maxBid)
        }

    @Test
    fun oddPenceIsOnlyASuggestionUntilUsed() =
        runTest(dispatcher) {
            val services = FakeAddServices()
            val subject = subject(services)
            subject.type("90.00")
            assertEquals(
                OddPence.Suggestion(gbp("90.37"), gbp("90.00")),
                (subject.page as AddPage.Ready).oddPence,
            )
            subject.viewModel.useOddPence()
            Snapshot.sendApplyNotifications()
            assertEquals(
                "90.37",
                subject.viewModel.maxField.text
                    .toString(),
            )
            assertNull((subject.page as AddPage.Ready).oddPence)
        }

    @Test
    fun aNewGroupIsCreatedOnlyWhenTheSnipeArms() =
        runTest(dispatcher) {
            val services = FakeAddServices(groups = listOf(CatalogueFixture.portableAudio))
            val subject = subject(services)
            subject.viewModel.openGroupPicker()
            assertEquals(
                GroupPick.NoGroup,
                subject.viewModel.uiState.value.groups
                    ?.selected,
            )
            subject.viewModel.selectGroup(GroupPick.New)
            subject.viewModel.newGroupName.setTextAndPlaceCursorAtEnd("Sony bodies")
            subject.viewModel.confirmGroup()
            assertNull(subject.viewModel.uiState.value.groups)
            assertTrue(services.added.isEmpty())
            assertEquals(
                GroupRowState.Chosen("Sony bodies: win 1 of 1", "A new group. Add more snipes to it later."),
                (subject.page as AddPage.Ready).group,
            )
            subject.type("90.00")
            subject.viewModel.arm()
            assertEquals("Sony bodies", services.added.single().newGroupName)
            assertNull(services.added.single().groupId)
        }

    @Test
    fun aGroupThatHasItsWinIsNotOffered() =
        runTest(dispatcher) {
            val won = SnipeGroup(id = 7, name = "Cameras")
            val services =
                FakeAddServices(groups = listOf(CatalogueFixture.portableAudio, won)).apply {
                    groupWins.value = mapOf(won.id to 1)
                }
            val subject = subject(services)
            subject.viewModel.openGroupPicker()
            assertEquals(
                listOf(CatalogueFixture.portableAudio.id),
                subject.viewModel.uiState.value.groups
                    ?.options
                    ?.map { it.id },
            )
        }

    @Test
    fun anExistingGroupCountsItsOpenSnipes() =
        runTest(dispatcher) {
            val services =
                FakeAddServices(snipes = CatalogueFixture.snipes, groups = listOf(CatalogueFixture.portableAudio))
            val subject = subject(services)
            subject.viewModel.openGroupPicker()
            assertEquals(
                listOf(GroupOption(1, "Portable audio", openSnipes = 2)),
                subject.viewModel.uiState.value.groups
                    ?.options,
            )
            subject.viewModel.selectGroup(GroupPick.Existing(1))
            subject.viewModel.confirmGroup()
            assertEquals(
                GroupRowState.Chosen("Portable audio: win 1 of 3", "Once one is won, Kestrel cancels the rest."),
                (subject.page as AddPage.Ready).group,
            )
        }

    @Test
    fun aSnipeAddedMeanwhileOpensInsteadOfASecondOne() =
        runTest(dispatcher) {
            val services = FakeAddServices()
            val existing = AddFixtures.added(id = 7)
            services.addResult = { AddResult.AlreadyAdded(existing) }
            val subject = subject(services)
            subject.type("90.00")
            subject.viewModel.arm()
            assertEquals(7L, (subject.page as AddPage.AlreadySniped).snipeId)
            assertTrue(subject.events.isEmpty())
        }

    @Test
    fun pastingADifferentLinkGoesBackToTheLinkEntry() =
        runTest(dispatcher) {
            val subject = subject(FakeAddServices())
            subject.type("90.00")
            subject.viewModel.pasteDifferentLink()
            Snapshot.sendApplyNotifications()
            assertTrue(subject.page is AddPage.LinkEntry)
            assertEquals(
                "",
                subject.viewModel.maxField.text
                    .toString(),
            )
        }

    @Test
    fun theWebLayersReasonsArePlainSentences() {
        assertEquals("That link isn’t an eBay listing.", AddViewModel.reasonFor("This is not an eBay link"))
        assertEquals("eBay has no listing at this link.", AddViewModel.reasonFor("eBay has no listing at this link"))
        assertEquals("eBay didn’t respond.", AddViewModel.reasonFor("The page could not be loaded"))
        assertEquals("ebay.co.uk/itm/…", AddViewModel.addressOf(ParsedLink.Item(AddFixtures.sonyA700.ref)))
        assertEquals("ebay.us/…", AddViewModel.addressOf(ParsedLink.ShortLink("https://ebay.us/m/AbC123?x=1")))
    }
}
