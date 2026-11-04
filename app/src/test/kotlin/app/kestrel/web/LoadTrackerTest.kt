package app.kestrel.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadTrackerTest {
    private val url = "https://www.ebay.co.uk/itm/127820098965"

    @Test
    fun `a page that started and finished ends the load with its final URL and title`() {
        val tracker = LoadTracker(url)
        tracker.pageStarted("https://www.ebay.co.uk/itm/127820098966")
        tracker.redirected("https://www.ebay.co.uk/itm/127820098966")
        tracker.pageFinished("https://www.ebay.co.uk/itm/127820098966", "Item | eBay UK")
        assertEquals(
            LoadEnd.Finished("https://www.ebay.co.uk/itm/127820098966", "Item | eBay UK", null),
            tracker.end.getCompleted(),
        )
        assertEquals(listOf("https://www.ebay.co.uk/itm/127820098966"), tracker.redirects)
    }

    @Test
    fun `a late finish of the previous page does not end the load`() {
        val tracker = LoadTracker(url)
        tracker.pageFinished("https://www.ebay.co.uk/itm/111111111111", "Previous | eBay UK")
        assertFalse(tracker.end.isCompleted)
        tracker.pageFinished(url, "Item | eBay UK")
        assertTrue(tracker.end.isCompleted)
    }

    @Test
    fun `a late finish of the previous page after this load started does not end it either`() {
        val tracker = LoadTracker(url)
        tracker.pageStarted(url)
        tracker.pageFinished("https://www.ebay.co.uk/itm/111111111111", "Previous | eBay UK")
        assertFalse(tracker.end.isCompleted)
        tracker.pageFinished(url, "Item | eBay UK")
        assertEquals(LoadEnd.Finished(url, "Item | eBay UK", null), tracker.end.getCompleted())
    }

    @Test
    fun `a page that a script moved to by itself is followed`() {
        val tracker = LoadTracker(url)
        tracker.pageStarted("https://www.ebay.co.uk/splashui/challenge?ap=1")
        tracker.pageFinished("https://www.ebay.co.uk/splashui/challenge?ap=1", "Pardon Our Interruption...")
        assertEquals(
            "https://www.ebay.co.uk/splashui/challenge?ap=1",
            (tracker.end.getCompleted() as LoadEnd.Finished).url,
        )
    }

    @Test
    fun `an HTTP error keeps the load going and is reported with the page`() {
        val tracker = LoadTracker(url)
        tracker.pageStarted(url)
        tracker.mainFrameHttpError(403)
        assertFalse(tracker.end.isCompleted)
        tracker.pageFinished(url, "Error Page | eBay")
        assertEquals(LoadEnd.Finished(url, "Error Page | eBay", 403), tracker.end.getCompleted())
    }

    @Test
    fun `a network error ends the load`() {
        val tracker = LoadTracker(url)
        tracker.mainFrameError(url, -2, "net::ERR_NAME_NOT_RESOLVED")
        assertEquals(LoadEnd.NetworkError(url, -2, "net::ERR_NAME_NOT_RESOLVED"), tracker.end.getCompleted())
    }

    @Test
    fun `a refused navigation ends the load`() {
        val tracker = LoadTracker(url)
        val verdict = NavigationVerdict(NavigationDecision.THIRD_PARTY_SIGN_IN, "accounts.google.com sign-in")
        tracker.navigationRefused("https://accounts.google.com/o/oauth2", verdict)
        assertEquals(LoadEnd.Blocked("https://accounts.google.com/o/oauth2", verdict), tracker.end.getCompleted())
    }

    @Test
    fun `the first ending wins`() {
        val tracker = LoadTracker(url)
        tracker.rendererGone()
        tracker.pageStarted(url)
        tracker.pageFinished(url, "Item | eBay UK")
        tracker.mainFrameError(url, -2, "late")
        assertEquals(LoadEnd.RendererGone, tracker.end.getCompleted())
    }
}
