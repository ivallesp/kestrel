package app.kestrel.ui.screens.signinbrowser

import app.kestrel.ui.model.Connectivity
import app.kestrel.ui.screens.ready.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The sign-in browser's ViewModel, driven with the page events a WebView would send. */
class SignInBrowserViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeSession(
        var cookie: Boolean = false,
    ) : SignInSession {
        val kept = mutableListOf<String?>()
        val connection = MutableStateFlow<Connectivity>(Connectivity.Online)

        override fun hasSessionCookie(host: String): Boolean = cookie

        override fun keep(name: String?) {
            kept += name
        }

        override fun connectivity(): Flow<Connectivity> = connection
    }

    private val home = "https://www.ebay.co.uk/"
    private val homeTitle = "Electronics, Cars, Fashion, Collectibles & More | eBay"
    private val signedIn = "\"{\\\"signedIn\\\":true,\\\"userId\\\":\\\"lot.42\\\",\\\"firstName\\\":\\\"Ada\\\"}\""
    private val signedOut = "\"{\\\"signedIn\\\":false,\\\"userId\\\":\\\"\\\",\\\"firstName\\\":\\\"\\\"}\""

    @Test
    fun itOpensEbayUksSignInPageWithTheHostShown() =
        runTest(main.dispatcher) {
            val viewModel = SignInBrowserViewModel(FakeSession())
            assertEquals(SignInPages.signInUrl, viewModel.url)
            assertEquals("signin.ebay.co.uk", viewModel.state.value.host)
            assertEquals(0f, viewModel.state.value.progress)
        }

    @Test
    fun progressFollowsThePageAndTheBarFollowsTheHost() =
        runTest(main.dispatcher) {
            val viewModel = SignInBrowserViewModel(FakeSession())
            viewModel.onProgress(40, "https://signin.ebay.co.uk/signin/s")
            assertEquals(0.4f, viewModel.state.value.progress)
            viewModel.onProgress(100, home)
            assertNull(viewModel.state.value.progress)
            assertEquals("www.ebay.co.uk", viewModel.state.value.host)
        }

    @Test
    fun aPageThatCannotLoadWhileOnlineSaysEbayCouldNotBeReached() =
        runTest(main.dispatcher) {
            val viewModel = SignInBrowserViewModel(FakeSession())
            runCurrent()
            viewModel.onMainFrameError(SignInPages.signInUrl)
            assertEquals(BrowserSheet.Offline, viewModel.state.value.sheet)
            assertNull(viewModel.state.value.progress)
            // Chromium's error page finishing behind the sheet leaves it up.
            viewModel.onPageFinished(SignInPages.signInUrl, "Webpage not available")
            assertEquals(BrowserSheet.Offline, viewModel.state.value.sheet)

            assertFalse(viewModel.onSheetAction())
            assertNull(viewModel.state.value.sheet)
            assertEquals(1, viewModel.state.value.pageGeneration)
        }

    @Test
    fun aFailedLoadBehindTheGoogleSheetKeepsThatSheet() =
        runTest(main.dispatcher) {
            val viewModel = SignInBrowserViewModel(FakeSession())
            runCurrent()
            viewModel.onThirdPartySignIn("https://accounts.google.com/o/oauth2/v2/auth")
            viewModel.onMainFrameError("https://accounts.google.com/o/oauth2/v2/auth")
            assertEquals(BrowserSheet.ThirdParty("Google"), viewModel.state.value.sheet)
        }

    @Test
    fun theSignInPageItselfIsNotRead() =
        runTest(main.dispatcher) {
            val viewModel = SignInBrowserViewModel(FakeSession())
            runCurrent()
            assertFalse(viewModel.onPageFinished(SignInPages.signInUrl, "Sign in or Register | eBay"))
        }

    @Test
    fun aSignedInHomePageKeepsTheSessionOnceAndShowsTheName() =
        runTest(main.dispatcher) {
            val session = FakeSession()
            val viewModel = SignInBrowserViewModel(session)
            runCurrent()
            assertTrue(viewModel.onPageFinished(home, homeTitle))
            viewModel.onHeaderRead(home, signedIn)
            viewModel.onHeaderRead(home, signedIn)
            assertEquals(listOf<String?>("lot.42"), session.kept)
            assertEquals(BrowserSheet.SignedIn("lot.42"), viewModel.state.value.sheet)
            assertTrue(viewModel.state.value.signedIn)
            assertFalse("a later page is not read again", viewModel.onPageFinished(home, homeTitle))
            assertTrue("Done closes the screen", viewModel.onSheetAction())
        }

    @Test
    fun aSignedOutHomePageIsNotASuccess() =
        runTest(main.dispatcher) {
            val session = FakeSession(cookie = true)
            val viewModel = SignInBrowserViewModel(session)
            runCurrent()
            viewModel.onHeaderRead(home, signedOut)
            assertTrue(session.kept.isEmpty())
            assertNull(viewModel.state.value.sheet)
            assertFalse(viewModel.state.value.signedIn)
        }

    @Test
    fun aPageWithoutAHeaderCountsOnlyWithASessionCookie() =
        runTest(main.dispatcher) {
            val session = FakeSession(cookie = false)
            val viewModel = SignInBrowserViewModel(session)
            runCurrent()
            viewModel.onHeaderRead(home, "null")
            assertFalse(viewModel.state.value.signedIn)
            session.cookie = true
            viewModel.onHeaderRead(home, "null")
            assertEquals(listOf<String?>(null), session.kept)
            assertEquals(BrowserSheet.SignedIn(null), viewModel.state.value.sheet)
        }

    @Test
    fun googleSignInIsExplainedAndTheSheetJustCloses() =
        runTest(main.dispatcher) {
            val viewModel = SignInBrowserViewModel(FakeSession())
            viewModel.onThirdPartySignIn("https://accounts.google.com/o/oauth2/v2/auth")
            assertEquals(BrowserSheet.ThirdParty("Google"), viewModel.state.value.sheet)
            assertFalse(viewModel.onSheetAction())
            assertNull(viewModel.state.value.sheet)
            assertEquals(0, viewModel.state.value.pageGeneration)
        }

    @Test
    fun aPageThatFinishesOfflineOffersToTryAgain() =
        runTest(main.dispatcher) {
            val session = FakeSession()
            val viewModel = SignInBrowserViewModel(session)
            session.connection.value = Connectivity.Offline(sinceUtcMillis = null)
            runCurrent()
            assertFalse(viewModel.onPageFinished(SignInPages.signInUrl, "Webpage not available"))
            assertEquals(BrowserSheet.Offline, viewModel.state.value.sheet)
            assertFalse(viewModel.onSheetAction())
            assertEquals("Try again opens a new page", 1, viewModel.state.value.pageGeneration)
        }

    @Test
    fun aLostRendererOpensANewPage() =
        runTest(main.dispatcher) {
            val viewModel = SignInBrowserViewModel(FakeSession())
            viewModel.onRendererGone()
            assertEquals(BrowserSheet.PageStopped, viewModel.state.value.sheet)
            viewModel.onSheetAction()
            assertEquals(1, viewModel.state.value.pageGeneration)
        }

    @Test
    fun aRefusedLinkLeavesANotice() =
        runTest(main.dispatcher) {
            val viewModel = SignInBrowserViewModel(FakeSession())
            viewModel.onNavigationBlocked()
            val notices = mutableListOf<String>()
            backgroundScope.launch { viewModel.notices.collect { notices += it } }
            runCurrent()
            assertEquals(listOf(SignInBrowserViewModel.ONLY_EBAY_PAGES), notices)
        }
}
