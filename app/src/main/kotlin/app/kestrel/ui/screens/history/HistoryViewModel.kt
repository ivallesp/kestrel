package app.kestrel.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kestrel.AppContainer
import app.kestrel.data.SnipeStats
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.model.TimelineEvent
import app.kestrel.ui.screens.home.clockTicks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import java.time.Instant

/** What History reads. The app's container in production ([AppHistorySources]); fakes in tests. */
interface HistorySources {
    /** Finished snipes, most recent end first. */
    val history: Flow<List<Snipe>>

    val stats: Flow<SnipeStats>

    fun timeline(snipeId: Long): Flow<List<TimelineEvent>>

    val groups: Flow<List<SnipeGroup>>

    /** True time, UTC epoch milliseconds. */
    fun nowUtcMillis(): Long
}

/** [HistorySources] over the app's repository and true time. */
class AppHistorySources(
    private val container: AppContainer,
) : HistorySources {
    override val history: Flow<List<Snipe>> get() = container.repository.observeHistory()

    override val stats: Flow<SnipeStats> get() = container.repository.observeStats()

    override fun timeline(snipeId: Long): Flow<List<TimelineEvent>> = container.repository.observeTimeline(snipeId)

    override val groups: Flow<List<SnipeGroup>> get() = container.repository.observeGroups()

    override fun nowUtcMillis(): Long = container.clock.nowUtcMillis()
}

/**
 * History's state: the finished snipes with their timelines (read once each, because a finished snipe no longer
 * changes) and the totals, with a clock that ticks once a minute while the screen is collected. Finished rows have
 * nothing inside its last hour, so the clock never needs seconds here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val sources: HistorySources,
) : ViewModel() {
    private val finished = sources.history.shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), 1)

    private val timelines: Flow<Map<Long, List<TimelineEvent>>> =
        finished
            .map { list -> list.map { it.id } }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(ids.map { id -> sources.timeline(id).take(1).map { id to it } }) { it.toMap() }
                }
            }

    private val ticks: Flow<Instant> = clockTicks(sources::nowUtcMillis) { false }

    val state: StateFlow<HistoryUiState> =
        combine(finished, timelines, sources.groups, sources.stats, ticks) { list, events, groups, stats, now ->
            HistoryStateBuilder.build(HistoryInput(now, list, events, groups, stats))
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
            HistoryUiState.loading(Instant.ofEpochMilli(sources.nowUtcMillis())),
        )

    private companion object {
        const val STOP_AFTER_MILLIS = 5_000L
    }
}
