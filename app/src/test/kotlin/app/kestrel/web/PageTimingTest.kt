package app.kestrel.web

import app.kestrel.engine.WindowTimings
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page's own waits must end before the engine's time limit on the same call. Otherwise the engine cancels the
 * call first: the page's reason and its report never arrive, and a load the page would still have accepted is thrown
 * away and loaded again from scratch.
 */
class PageTimingTest {
    private val timing = PageTiming()

    @Test
    fun `a load gives up, or takes a usable document, before the engine cuts openItem off`() {
        // Room after the load for the helper's install and the model read, which are quick on a loaded page.
        val readsAfterTheLoad = 5_000L
        assertTrue(timing.loadTimeoutMillis + readsAfterTheLoad <= WindowTimings.OPEN_ITEM_TIMEOUT)
    }

    @Test
    fun `opening the bid layer answers with its reason before the engine cuts it off`() {
        val worstCase =
            timing.hydrationTimeoutMillis + timing.probeTimeoutMillis + timing.tapConfirmMillis +
                timing.layerOpenTimeoutMillis
        // Room for the quick page calls between those waits.
        val pageCalls = 1_000L
        assertTrue("$worstCase", worstCase + pageCalls <= WindowTimings.OPEN_LAYER_TIMEOUT)
    }
}
