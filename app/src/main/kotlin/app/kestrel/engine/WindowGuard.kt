package app.kestrel.engine

import app.kestrel.model.SnipeResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.ConcurrentHashMap

/**
 * One window per snipe in this process. The backup alarm or a double start of the service must never run a second
 * window next to a running one (two windows could send two bids), so a second caller waits for the first window's
 * result. Process-wide on purpose: the platform may build a new [SnipeEngine] for each service start.
 */
internal object WindowGuard {
    private val running = ConcurrentHashMap<Long, CompletableDeferred<SnipeResult>>()

    suspend fun runExclusive(
        snipeId: Long,
        window: suspend () -> SnipeResult,
    ): SnipeResult {
        while (true) {
            val mine = CompletableDeferred<SnipeResult>()
            val existing = running.putIfAbsent(snipeId, mine)
            if (existing == null) {
                val result =
                    try {
                        window()
                    } catch (error: Throwable) {
                        running.remove(snipeId, mine)
                        mine.completeExceptionally(error)
                        throw error
                    }
                running.remove(snipeId, mine)
                mine.complete(result)
                return result
            }
            try {
                return existing.await()
            } catch (cancellation: CancellationException) {
                currentCoroutineContext().ensureActive()
                // The other window was stopped before it finished, but this caller is alive: it takes over.
            }
        }
    }
}
