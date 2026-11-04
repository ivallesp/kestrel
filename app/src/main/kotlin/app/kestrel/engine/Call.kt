package app.kestrel.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

/** The result of a call into another layer that ran with a time limit. */
internal sealed interface Call<out T> {
    data class Done<out T>(
        val value: T,
    ) : Call<T>

    data class Threw(
        val error: Exception,
    ) : Call<Nothing>

    data object TimedOut : Call<Nothing>
}

internal fun <T> Call<T>.valueOrNull(): T? = (this as? Call.Done<T>)?.value

/** "Loading the item page timed out" or "Loading the item page failed: <cause>". */
internal fun Call<*>.failureText(action: String): String =
    when (this) {
        is Call.Done -> action
        is Call.Threw -> "$action failed: ${error.message ?: error.javaClass.simpleName}"
        Call.TimedOut -> "$action timed out"
    }

/**
 * Runs [block] for at most [timeoutMillis]. Failures and timeouts come back as values, so every caller has to decide
 * what they mean; only a real cancellation of the caller propagates.
 */
internal suspend fun <T> callWithin(
    timeoutMillis: Long,
    block: suspend () -> T,
): Call<T> {
    if (timeoutMillis <= 0) return Call.TimedOut
    val finished =
        withTimeoutOrNull(timeoutMillis) {
            try {
                Call.Done(block())
            } catch (cancellation: CancellationException) {
                // When this scope is cancelled (our own timeout or the caller) the cancellation must propagate.
                // A stray cancellation thrown by the callee while this scope is still active is just a failed call.
                if (!isActive) throw cancellation
                Call.Threw(cancellation)
            } catch (error: Exception) {
                Call.Threw(error)
            }
        }
    return finished ?: Call.TimedOut
}
