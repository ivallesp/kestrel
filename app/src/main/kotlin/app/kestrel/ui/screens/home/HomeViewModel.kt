package app.kestrel.ui.screens.home

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kestrel.AppContainer
import app.kestrel.data.EditResult
import app.kestrel.data.Settings
import app.kestrel.engine.MonitorPolicy
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.model.StatusFamily
import app.kestrel.model.TimelineEvent
import app.kestrel.platform.ReadinessItem
import app.kestrel.platform.ReadinessKey
import app.kestrel.platform.SnipeAction
import app.kestrel.platform.SnipeService
import app.kestrel.ui.format.formatted
import app.kestrel.ui.format.withCurlyQuotes
import app.kestrel.ui.model.Connectivity
import app.kestrel.ui.model.LotStatus
import app.kestrel.ui.shell.ConnectivityMonitor
import app.kestrel.ui.shell.ShellMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

/** What S1 reads and changes. The app's container in production ([AppHomeSources]); fakes in tests. */
interface HomeSources {
    /** Every snipe the user has not deleted, soonest end first. */
    val snipes: Flow<List<Snipe>>

    fun timeline(snipeId: Long): Flow<List<TimelineEvent>>

    val groups: Flow<List<SnipeGroup>>

    val settings: Flow<Settings>

    val connectivity: Flow<Connectivity>

    /** The readiness checklist, read now. */
    suspend fun readiness(): List<ReadinessItem>

    /** The system page that fixes [key], or null when the fix is inside the app. */
    fun fixIntent(key: ReadinessKey): Intent?

    /** True time, UTC epoch milliseconds. */
    fun nowUtcMillis(): Long

    suspend fun updateMax(
        snipeId: Long,
        max: Money?,
    ): EditResult

    /** Asks the platform to check [snipe]'s listing now. */
    fun requestCheck(snipe: Snipe)
}

/** [HomeSources] over the app's container: the repository, settings, readiness, true time and snipe actions. */
class AppHomeSources(
    private val container: AppContainer,
) : HomeSources {
    override val snipes: Flow<List<Snipe>> get() = container.repository.observeSnipes()

    override fun timeline(snipeId: Long): Flow<List<TimelineEvent>> = container.repository.observeTimeline(snipeId)

    override val groups: Flow<List<SnipeGroup>> get() = container.repository.observeGroups()

    override val settings: Flow<Settings> get() = container.settings.settings

    override val connectivity: Flow<Connectivity>
        get() = ConnectivityMonitor.observe(container.context) { container.clock.nowUtcMillis() }

    override suspend fun readiness(): List<ReadinessItem> = container.readiness.check()

    override fun fixIntent(key: ReadinessKey): Intent? = container.readiness.fixIntent(key)

    override fun nowUtcMillis(): Long = container.clock.nowUtcMillis()

    override suspend fun updateMax(
        snipeId: Long,
        max: Money?,
    ): EditResult = container.snipeActions.updateMax(snipeId, max)

    override fun requestCheck(snipe: Snipe) {
        SnipeService.start(container.context, SnipeAction.CHECK, snipe)
    }
}

/**
 * Pull to refresh asks for a check only of open lots nobody looked at in the last five minutes: not checked by the
 * schedule and not asked for by an earlier pull. Lots whose bid window is open are left to the window, which
 * already follows the listing every few seconds.
 */
internal object HomeRefresh {
    val MINIMUM_INTERVAL: Duration = Duration.ofMinutes(5)

    fun due(
        snipes: List<Snipe>,
        requestedAtUtcMillis: Map<Long, Long>,
        nowUtcMillis: Long,
    ): List<Snipe> =
        snipes.filter { snipe ->
            val looked = listOfNotNull(snipe.lastCheckedAtUtcMillis, requestedAtUtcMillis[snipe.id])
            !snipe.status.isFinal &&
                snipe.status.family != StatusFamily.BIDDING &&
                MonitorPolicy.windowOpensAt(snipe.endTimeUtcMillis) > nowUtcMillis &&
                looked.all { nowUtcMillis - it >= MINIMUM_INTERVAL.toMillis() }
        }
}

/**
 * Times for the screen, aligned to the wall clock: every second while [everySecond] says a lot is inside its last
 * hour, every minute otherwise. Collected only while the screen is visible, so nothing ticks in the background.
 */
internal fun clockTicks(
    nowUtcMillis: () -> Long,
    everySecond: (Instant) -> Boolean,
): Flow<Instant> =
    flow {
        while (true) {
            val millis = nowUtcMillis()
            val now = Instant.ofEpochMilli(millis)
            emit(now)
            val step = if (everySecond(now)) SECOND_MILLIS else MINUTE_MILLIS
            delay(step - Math.floorMod(millis, step))
        }
    }

private const val SECOND_MILLIS = 1_000L
private const val MINUTE_MILLIS = 60_000L

/**
 * S1's state and actions. The list, the timelines, the groups, the settings, the connection and the readiness checks
 * are combined with a clock that ticks only while the screen is collected: every second when a lot is inside its
 * last hour, every minute otherwise.
 *
 * "Raise to £2,200.00, the next bid" and "Stop sniping" change the max at once and leave an Undo snackbar for 10 s
 * ([messages]); Undo puts the old max back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val sources: HomeSources,
    private val background: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val readiness = MutableStateFlow(ReadinessSnapshot.NotChecked)
    private val pendingRefresh = MutableStateFlow<PendingRefresh?>(null)
    private val requestedChecks = mutableMapOf<Long, Long>()
    private var refreshTimeout: Job? = null
    private val messageQueue = Channel<ShellMessage>(Channel.BUFFERED)

    /** Snackbars for the screen to show: undo after a change, or why a change was refused. */
    val messages: Flow<ShellMessage> = messageQueue.receiveAsFlow()

    private val snipes = sources.snipes.shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), 1)

    private val timelines: Flow<Map<Long, List<TimelineEvent>>> =
        snipes
            .map { list -> shownTimelines(list, sources.nowUtcMillis()) }
            .distinctUntilChanged()
            .flatMapLatest { shown -> timelinesOf(shown) }

    private val ticks: Flow<Instant> =
        snipes
            .map { list -> list.filterNot { it.status.isFinal } }
            .distinctUntilChanged()
            .flatMapLatest { open ->
                clockTicks(sources::nowUtcMillis) { now -> HomeStateBuilder.ticksEverySecond(open, now) }
            }.onEach { now -> recheckIfStale(now) }

    private val data: Flow<HomeData> =
        combine(snipes, timelines, sources.groups, sources.settings, sources.connectivity) {
            list,
            events,
            groups,
            settings,
            connectivity,
            ->
            HomeData(list, events, groups, settings, connectivity)
        }

    val state: StateFlow<HomeUiState> =
        combine(data, readiness, pendingRefresh, ticks) { data, readiness, refresh, now ->
            HomeStateBuilder.build(
                HomeInput(
                    now = now,
                    snipes = data.snipes,
                    timelines = data.timelines,
                    groups = data.groups,
                    connectivity = data.connectivity,
                    readiness = readiness,
                    accountName = data.settings.signedInName,
                    defaultLeadSeconds = data.settings.defaultLeadSeconds,
                    refreshing = refresh?.waitingFor(data.snipes) == true,
                ),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
            HomeUiState.loading(Instant.ofEpochMilli(sources.nowUtcMillis()), Settings.DEFAULT_LEAD_SECONDS),
        )

    /** The screen came back into view (or the user returned from a system page): check the phone again. */
    fun onShown() = recheckReadiness()

    fun recheckReadiness() {
        if (readiness.value.checking) return
        readiness.value = readiness.value.copy(checking = true)
        viewModelScope.launch {
            val items =
                try {
                    withContext(background) { sources.readiness() }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w(TAG, "The readiness check failed", error)
                    null
                }
            val previous = readiness.value
            readiness.value =
                if (items == null) {
                    previous.copy(checking = false)
                } else {
                    ReadinessSnapshot(items, Instant.ofEpochMilli(sources.nowUtcMillis()), checking = false)
                }
        }
    }

    /** The system page that fixes [key], or null when the fix is in the app. */
    fun fixIntent(key: ReadinessKey): Intent? = sources.fixIntent(key)

    /** Pull to refresh: asks for a check of every open lot not checked in the last five minutes. */
    fun refresh() {
        val current = state.value
        val now = sources.nowUtcMillis()
        if (current.offline != null) {
            send(ShellMessage("The phone is offline. Kestrel checks again when it reconnects."))
            return
        }
        val open = current.sections.flatMap { section -> section.blocks.flatMap { it.lots } }.map { it.snipe }
        val due = HomeRefresh.due(open, requestedChecks, now)
        if (due.isEmpty()) {
            send(ShellMessage("Every lot was checked in the last 5 minutes."))
            return
        }
        due.forEach { snipe ->
            requestedChecks[snipe.id] = now
            try {
                sources.requestCheck(snipe)
            } catch (error: RuntimeException) {
                // Android refuses a service start in some states; the schedule still checks the lot later.
                Log.w(TAG, "Could not ask for a check of snipe ${snipe.id}", error)
            }
        }
        pendingRefresh.value = PendingRefresh(now, due.map { it.id }.toSet())
        refreshTimeout?.cancel()
        refreshTimeout =
            viewModelScope.launch {
                delay(REFRESH_TIMEOUT_MILLIS)
                pendingRefresh.value = null
            }
    }

    /** "Raise to £2,200.00, the next bid": the max becomes eBay's next bid at once, with Undo. */
    fun quickRaise(snipeId: Long) {
        val lot = lotOf(snipeId) ?: return
        val minimum = (lot.status as? LotStatus.OverMax)?.minimumBid ?: return
        val previous = lot.snipe.maxBid
        viewModelScope.launch {
            when (val result = sources.updateMax(snipeId, minimum)) {
                is EditResult.Done -> send(undoable("Max raised to ${minimum.formatted()}", snipeId, previous))
                is EditResult.NotAllowed -> send(ShellMessage(result.reason.withCurlyQuotes()))
                EditResult.NotFound -> return@launch
            }
        }
    }

    /** "Stop sniping": the lot goes back to watching (no max), with Undo. */
    fun stopSniping(snipeId: Long) {
        val lot = lotOf(snipeId) ?: return
        val previous = lot.snipe.maxBid ?: return
        viewModelScope.launch {
            when (val result = sources.updateMax(snipeId, null)) {
                is EditResult.Done -> {
                    send(undoable("Stopped sniping ${HomeText.shortTitle(lot.snipe.title)}", snipeId, previous))
                }

                is EditResult.NotAllowed -> {
                    send(ShellMessage(result.reason.withCurlyQuotes()))
                }

                // The snipe was deleted meanwhile; there is nothing left to change.
                EditResult.NotFound -> {
                    return@launch
                }
            }
        }
    }

    private fun undoable(
        text: String,
        snipeId: Long,
        previous: Money?,
    ): ShellMessage =
        ShellMessage(text, actionLabel = "Undo", durationMillis = ShellMessage.UNDO_MILLIS) {
            viewModelScope.launch {
                val result = sources.updateMax(snipeId, previous)
                if (result is EditResult.NotAllowed) send(ShellMessage(result.reason.withCurlyQuotes()))
            }
        }

    private fun send(message: ShellMessage) {
        messageQueue.trySend(message)
    }

    private fun lotOf(snipeId: Long): HomeLot? =
        state.value.sections
            .asSequence()
            .flatMap { it.blocks }
            .flatMap { it.lots }
            .firstOrNull { it.id == snipeId }

    private fun recheckIfStale(now: Instant) {
        val checkedAt = readiness.value.checkedAt ?: return
        if (Duration.between(checkedAt, now) >= RECHECK_EVERY) recheckReadiness()
    }

    /** Open lots follow their timeline live; finished ones read it once. */
    private fun shownTimelines(
        snipes: List<Snipe>,
        nowUtcMillis: Long,
    ): List<ShownTimeline> {
        val doneSince = nowUtcMillis - HomeStateBuilder.DONE_FOR.toMillis()
        return snipes.mapNotNull { snipe ->
            when {
                !snipe.status.isFinal -> {
                    ShownTimeline(snipe.id, live = true)
                }

                snipe.endTimeUtcMillis >= doneSince || snipe.updatedAtUtcMillis >= doneSince -> {
                    ShownTimeline(
                        snipe.id,
                        false,
                    )
                }

                else -> {
                    null
                }
            }
        }
    }

    private fun timelinesOf(shown: List<ShownTimeline>): Flow<Map<Long, List<TimelineEvent>>> {
        if (shown.isEmpty()) return flowOf(emptyMap())
        val flows =
            shown.map { timeline ->
                val events = sources.timeline(timeline.snipeId)
                (if (timeline.live) events else events.take(1)).map { timeline.snipeId to it }
            }
        return combine(flows) { pairs -> pairs.toMap() }
    }

    private class HomeData(
        val snipes: List<Snipe>,
        val timelines: Map<Long, List<TimelineEvent>>,
        val groups: List<SnipeGroup>,
        val settings: Settings,
        val connectivity: Connectivity,
    )

    private data class ShownTimeline(
        val snipeId: Long,
        val live: Boolean,
    )

    /** A pull asked at [requestedAtUtcMillis] for checks of [snipeIds]; it is over once each was checked since. */
    private data class PendingRefresh(
        val requestedAtUtcMillis: Long,
        val snipeIds: Set<Long>,
    ) {
        fun waitingFor(snipes: List<Snipe>): Boolean =
            snipes.any { it.id in snipeIds && (it.lastCheckedAtUtcMillis ?: 0L) < requestedAtUtcMillis }
    }

    private companion object {
        const val TAG = "HomeViewModel"
        const val STOP_AFTER_MILLIS = 5_000L
        const val REFRESH_TIMEOUT_MILLIS = 20_000L
        val RECHECK_EVERY: Duration = Duration.ofMinutes(15)
    }
}
