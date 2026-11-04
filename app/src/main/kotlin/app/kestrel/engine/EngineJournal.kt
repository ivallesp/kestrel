package app.kestrel.engine

import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.StatusFamily
import app.kestrel.model.TimelineKind
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Writes timeline events and user alerts for the engine. The timeline is a receipt and alerts are best effort: neither
 * may stop a bid, so failures in the store or the notifier are swallowed here.
 */
internal class EngineJournal(
    private val store: SnipeStore,
    private val notifier: SnipeNotifier,
    private val clock: EngineClock,
) {
    /**
     * Adds a timeline event. The store's own timestamp may come from the phone's clock, so the detail JSON always
     * carries the true-time instant ("trueUtcMillis") and the monotonic one ("elapsedRealtimeMillis").
     */
    suspend fun event(
        snipeId: Long,
        kind: TimelineKind,
        message: String,
        details: Map<String, Any?> = emptyMap(),
    ) {
        val detail =
            buildJsonObject {
                put("trueUtcMillis", clock.nowUtcMillis())
                put("elapsedRealtimeMillis", clock.elapsedRealtimeMillis())
                for ((key, value) in details) {
                    when (value) {
                        null -> put(key, JsonNull)
                        is Number -> put(key, value)
                        is Boolean -> put(key, value)
                        is Money -> put(key, "${value.currency} ${value.major.toPlainString()}")
                        else -> put(key, value.toString())
                    }
                }
            }.toString()
        // Bounded and failure-tolerant: a lost or slow timeline line must not cost the user the auction.
        callWithin(SnipeWriter.STORE_TIMEOUT_MILLIS) { store.addEvent(snipeId, kind, message, detail) }
    }

    fun notify(alert: SnipeNotifier.() -> Unit) {
        try {
            notifier.alert()
        } catch (ignored: Exception) {
            // A failed notification must not cost the user the auction.
        }
    }
}

/** Read-modify-write updates of a snipe that keep the user's concurrent edits (max, cancel) intact. */
internal class SnipeWriter(
    private val store: SnipeStore,
    private val clock: EngineClock,
) {
    /**
     * Re-reads the snipe, applies [change] and writes it. When the read fails the last known copy is used; when the
     * snipe was deleted nothing is written.
     */
    suspend fun update(
        snipeId: Long,
        lastKnown: Snipe,
        timeoutMillis: Long = STORE_TIMEOUT_MILLIS,
        change: (Snipe) -> Snipe,
    ): Snipe {
        val read = callWithin(timeoutMillis) { store.snipe(snipeId) }
        if (read is Call.Done && read.value == null) return change(lastKnown)
        val current = read.valueOrNull() ?: lastKnown
        val updated = change(current).copy(updatedAtUtcMillis = clock.nowUtcMillis())
        callWithin(timeoutMillis) { store.update(updated) }
        return updated
    }

    /**
     * Like [update], but only while no bid window owns the snipe and it has not finished: a background check or a dry
     * run that finishes late must not overwrite a window's status. Returns null, having written nothing, when the snipe
     * is taken, finished, deleted or cannot be read.
     */
    suspend fun updateIfIdle(
        snipeId: Long,
        change: (Snipe) -> Snipe,
    ): Snipe? {
        val current = callWithin(STORE_TIMEOUT_MILLIS) { store.snipe(snipeId) }.valueOrNull() ?: return null
        if (current.status.isFinal || current.status.family == StatusFamily.BIDDING) return null
        val updated = change(current).copy(updatedAtUtcMillis = clock.nowUtcMillis())
        callWithin(STORE_TIMEOUT_MILLIS) { store.update(updated) }
        return updated
    }

    companion object {
        const val STORE_TIMEOUT_MILLIS = 5_000L
    }
}
