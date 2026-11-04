package app.kestrel.platform

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kestrel.engine.SntpSample
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowSystemClock

@RunWith(AndroidJUnit4::class)
class AndroidTimeSourcesTest {
    @After
    fun tearDown() {
        ShadowSystemClock.setNetworkTimeAvailable(true)
    }

    @Test
    fun networkTimeIsNullWhenAndroidHasNone() {
        ShadowSystemClock.setNetworkTimeAvailable(false)

        assertNull(AndroidTimeSources { null }.networkUtcMillis())
    }

    @Test
    fun networkTimeIsReadWhenAvailable() {
        ShadowSystemClock.setNetworkTimeAvailable(true)

        assertNotNull(AndroidTimeSources { null }.networkUtcMillis())
    }

    @Test
    fun elapsedRealtimeIsAndroidsMonotonicClock() {
        assertEquals(SystemClock.elapsedRealtime(), AndroidTimeSources { null }.elapsedRealtimeMillis())
    }

    @Test
    fun sntpGoesThroughTheEnginesExchangeAndAFailureIsNull() =
        runTest {
            val sample = SntpSample(offsetMillis = 312, roundTripMillis = 28)

            assertEquals(
                sample,
                AndroidTimeSources { host -> sample.takeIf { host == "time.google.com" } }.sntp("time.google.com"),
            )
            assertNull(
                AndroidTimeSources { throw java.net.SocketTimeoutException("timed out") }.sntp("time.google.com"),
            )
        }
}
