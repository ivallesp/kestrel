package app.kestrel.platform

import android.os.SystemClock
import android.util.Log
import app.kestrel.engine.SntpSample
import app.kestrel.engine.TimeSources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DateTimeException

/**
 * Android's clocks for the engine's TrueTime. [sntpExchange] performs one SNTP exchange; it is the engine's client,
 * run here on the IO dispatcher so a socket never blocks the main thread.
 */
class AndroidTimeSources(
    private val sntpExchange: suspend (host: String) -> SntpSample?,
) : TimeSources {
    override fun systemUtcMillis(): Long = System.currentTimeMillis()

    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()

    override fun networkUtcMillis(): Long? =
        try {
            SystemClock.currentNetworkTimeClock().millis()
        } catch (error: DateTimeException) {
            // Android has not synced network time since boot.
            null
        }

    override suspend fun sntp(host: String): SntpSample? =
        try {
            withContext(Dispatchers.IO) { sntpExchange(host) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "SNTP exchange with $host failed", error)
            null
        }

    private companion object {
        const val TAG = "AndroidTimeSources"
    }
}
