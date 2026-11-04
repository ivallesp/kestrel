package app.kestrel.ui.screens.ready

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kestrel.AppContainer
import app.kestrel.engine.ClockSources
import app.kestrel.model.Snipe
import app.kestrel.platform.ReadinessItem
import app.kestrel.platform.ReadinessKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/** S5's state: the last check's rows, whether a check is running, and the next bid for the deadline sentence. */
@Immutable
data class ReadyUiState(
    val now: Instant,
    val checking: Boolean,
    /** When the last check finished; null until the first one does. */
    val checkedAt: Instant?,
    val rows: List<CheckRow>,
    val nextBid: NextBid?,
) {
    val problems: List<CheckRow> get() = rows.filter { it.standing == CheckStanding.Problem }

    /** The top bar's button reads "Checking…" and is disabled while a check runs, and before the first one ends. */
    val checkInProgress: Boolean get() = checking || checkedAt == null
}

/** What S5 reads, behind one seam so the ViewModel runs on fakes in tests. */
interface ReadySources {
    /** The platform's checklist, in its fixed order. */
    suspend fun check(): List<ReadinessItem>

    /** The facts the rows quote, read right after [check] finished at [checkedAt]. */
    suspend fun facts(checkedAt: Instant): CheckFacts

    /** Every snipe, for the next bid and whether anything is armed at all. */
    fun snipes(): Flow<List<Snipe>>

    /** The system page that fixes [key], or null when the fix is inside Kestrel. */
    fun fixIntent(key: ReadinessKey): Intent?

    /** True time, from the container's clock. */
    fun nowUtcMillis(): Long
}

/**
 * Runs the readiness checks when S5 opens and each time the user comes back to it (from a system page, for example),
 * and when they tap "Check again". The clock ticks every [tickMillis] so "checked 2 min ago" stays true.
 */
class ReadyViewModel(
    private val sources: ReadySources,
    private val tickMillis: Long = TICK_MILLIS,
) : ViewModel() {
    private val result = MutableStateFlow<CheckResult?>(null)
    private val checking = MutableStateFlow(false)

    private val clock: Flow<Instant> =
        flow {
            while (true) {
                emit(now())
                delay(tickMillis)
            }
        }

    val state: StateFlow<ReadyUiState> =
        combine(result, checking, sources.snipes(), clock) { result, checking, snipes, now ->
            val nextBid = CheckRows.nextBid(snipes, now)
            ReadyUiState(
                now = now,
                checking = checking,
                checkedAt = result?.facts?.checkedAt,
                rows = result?.let { CheckRows.build(it.items, it.facts, nextBid, now) }.orEmpty(),
                nextBid = nextBid,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            ReadyUiState(now = now(), checking = false, checkedAt = null, rows = emptyList(), nextBid = null),
        )

    /** Runs every check again; a tap while one runs does nothing. */
    fun checkAgain() {
        if (checking.value) return
        checking.value = true
        viewModelScope.launch {
            try {
                val items = sources.check()
                result.value = CheckResult(items, sources.facts(now()))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Readiness reports each failing probe as a row; this is only a broken store. Keep the last rows.
                Log.w(TAG, "The readiness check failed", error)
            } finally {
                checking.value = false
            }
        }
    }

    fun fixIntent(key: ReadinessKey): Intent? = sources.fixIntent(key)

    private fun now(): Instant = Instant.ofEpochMilli(sources.nowUtcMillis())

    private data class CheckResult(
        val items: List<ReadinessItem>,
        val facts: CheckFacts,
    )

    companion object {
        private const val TAG = "ReadyViewModel"
        const val TICK_MILLIS = 15_000L
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun from(
            container: AppContainer,
            context: Context,
        ): ReadyViewModel = ReadyViewModel(ContainerReadySources(container, context.applicationContext))
    }
}

/** The app's own sources: Readiness, settings, the repository and the true-time clock. */
internal class ContainerReadySources(
    private val container: AppContainer,
    private val context: Context,
) : ReadySources {
    override suspend fun check(): List<ReadinessItem> = container.readiness.check()

    override suspend fun facts(checkedAt: Instant): CheckFacts {
        val dryRun = container.repository.latestDryRun()
        val dryRunAt = dryRun?.dryRunAtUtcMillis
        return CheckFacts(
            checkedAt = checkedAt,
            signedInName = container.settings.current().signedInName,
            clock = clockReading(checkedAt),
            latestDryRun =
                if (dryRun == null || dryRunAt == null) {
                    null
                } else {
                    DryRunReading(dryRun.id, dryRun.title, Instant.ofEpochMilli(dryRunAt), dryRun.dryRunPassed == true)
                },
            alertsInDoNotDisturb =
                context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true,
        )
    }

    override fun snipes(): Flow<List<Snipe>> = container.repository.observeSnipes()

    override fun fixIntent(key: ReadinessKey): Intent? = container.readiness.fixIntent(key)

    override fun nowUtcMillis(): Long = container.clock.nowUtcMillis()

    private fun clockReading(checkedAt: Instant): ClockReading? {
        val sync = container.trueTime.lastSync ?: return null
        val age = container.trueTime.elapsedRealtimeMillis() - sync.atElapsedRealtimeMillis
        val reference =
            when (sync.source) {
                ClockSources.SNTP, ClockSources.SNTP_EARLIER -> ClockReference.Google
                ClockSources.NETWORK -> ClockReference.Network
                else -> ClockReference.None
            }
        return ClockReading(sync.offsetMillis, reference, checkedAt.minusMillis(age.coerceAtLeast(0)))
    }
}
