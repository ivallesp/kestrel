package app.kestrel.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kestrel.data.EditResult
import app.kestrel.engine.GroupPolicy
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.model.SnipeStatus
import app.kestrel.model.StatusFamily
import app.kestrel.model.TimelineEvent
import app.kestrel.ui.format.countdown
import app.kestrel.ui.format.formatted
import app.kestrel.ui.format.withCurlyQuotes
import app.kestrel.ui.model.Connectivity
import app.kestrel.ui.model.GroupContext
import app.kestrel.ui.model.LotPhoto
import app.kestrel.ui.model.LotStatusMapper
import app.kestrel.ui.model.LotTimes
import app.kestrel.ui.model.UndoCancel
import app.kestrel.ui.screens.add.AddPlanner
import app.kestrel.ui.screens.lot.minutesText
import app.kestrel.ui.screens.lot.shortTitle
import app.kestrel.ui.shell.ShellMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/** What the Group screen asks its destination to do once. */
sealed interface GroupEvent {
    /** The group was emptied: go back and leave [message] ("Ungrouped Portable audio", with Undo). */
    data class Ungrouped(
        val message: ShellMessage,
    ) : GroupEvent

    data class Message(
        val text: String,
    ) : GroupEvent
}

/**
 * The Group screen's logic: one "win 1 of N" group, its members with their states, the rule card, and the moves the
 * user can make (ungroup, add a snipe). Members change only through the app's snipe actions.
 */
class GroupViewModel(
    private val groupId: Long,
    private val services: GroupServices,
) : ViewModel() {
    private val eventChannel = Channel<GroupEvent>(Channel.BUFFERED)

    val events: Flow<GroupEvent> = eventChannel.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val timelines: Flow<Map<Long, List<TimelineEvent>>> =
        services
            .snipes()
            .map { snipes -> snipes.filter { it.groupId == groupId }.map { it.id } }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(ids.map { id -> services.timeline(id).map { id to it } }) { pairs -> pairs.toMap() }
                }
            }.onStart { emit(emptyMap()) }

    val uiState: StateFlow<GroupUiState> =
        combine(
            services.groups().map { groups -> groups.firstOrNull { it.id == groupId } },
            services.snipes(),
            timelines,
            services.connectivity().onStart { emit(Connectivity.Online) },
            services.ticks().onStart { emit(Instant.ofEpochMilli(services.nowUtcMillis())) },
        ) { group, snipes, events, connectivity, now ->
            GroupPlanner.state(group, groupId, snipes, events, connectivity, now)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue =
                GroupUiState(
                    Instant.ofEpochMilli(services.nowUtcMillis()),
                    GENERIC_TITLE,
                    GroupContent.Loading,
                ),
        )

    /** "Ungroup": every open member keeps its own snipe, armed as before; the list offers Undo. */
    fun ungroup() {
        viewModelScope.launch {
            val group = services.groups().first().firstOrNull { it.id == groupId } ?: return@launch
            val members = services.snipes().first().filter { it.groupId == groupId && !it.status.isFinal }
            val moved = members.filter { services.setGroup(it.id, null) is EditResult.Done }.map { it.id }
            val message =
                ShellMessage(
                    text = "Ungrouped ${group.name}",
                    actionLabel = "Undo",
                    durationMillis = ShellMessage.UNDO_MILLIS,
                ) { services.runDetached { moved.forEach { services.setGroup(it, groupId) } } }
            eventChannel.trySend(GroupEvent.Ungrouped(message))
        }
    }

    /**
     * "Undo cancel" on a member the group cancelled while its auction runs: it bids again, on its own, so it leaves
     * the group (whose rule would cancel it again).
     */
    fun undoCancel(snipeId: Long) {
        viewModelScope.launch {
            val title =
                services
                    .snipes()
                    .first()
                    .firstOrNull { it.id == snipeId }
                    ?.title ?: return@launch
            val text =
                when (val result = services.undoCancel(snipeId)) {
                    is EditResult.Done -> UndoCancel.message(shortTitle(title))
                    is EditResult.NotAllowed -> result.reason.withCurlyQuotes()
                    EditResult.NotFound -> return@launch
                }
            eventChannel.trySend(GroupEvent.Message(text))
        }
    }

    /** "Rename": a blank name is refused in the sheet; the store says whether it kept the new one. */
    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val refusal = services.renameGroup(groupId, trimmed)
            eventChannel.trySend(GroupEvent.Message(refusal ?: "Renamed to $trimmed"))
        }
    }

    /** Adds [snipeId] from the picker. */
    fun add(snipeId: Long) {
        viewModelScope.launch {
            when (val result = services.setGroup(snipeId, groupId)) {
                is EditResult.NotAllowed -> eventChannel.trySend(GroupEvent.Message(result.reason.withCurlyQuotes()))
                EditResult.NotFound -> eventChannel.trySend(GroupEvent.Message("That snipe no longer exists."))
                is EditResult.Done -> Unit
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** The title while the group loads and when it no longer exists. */
        const val GENERIC_TITLE = "Group"
    }
}

/** The Group screen's words and rows, from the group, its members and their timelines. Pure. */
object GroupPlanner {
    fun state(
        group: SnipeGroup?,
        groupId: Long,
        snipes: List<Snipe>,
        events: Map<Long, List<TimelineEvent>>,
        connectivity: Connectivity,
        now: Instant,
    ): GroupUiState {
        if (group == null) return GroupUiState(now, GroupViewModel.GENERIC_TITLE, GroupContent.Missing)
        val members = snipes.filter { it.groupId == groupId }.sortedWith(compareBy({ it.endTimeUtcMillis }, { it.id }))
        val openMembers = members.filter { !it.status.isFinal }
        val candidates =
            snipes
                .filter { it.groupId != groupId && it.maxBid != null && it.status.canJoinAGroup }
                .sortedBy { it.endTimeUtcMillis }
                .map { snipe ->
                    GroupCandidate(
                        snipeId = snipe.id,
                        title = snipe.title,
                        photo = LotPhoto.of(snipe),
                        status = LotStatusMapper.statusOf(snipe, emptyList(), now, connectivity),
                        endsAt = Instant.ofEpochMilli(snipe.endTimeUtcMillis),
                        endsCloseToAMember =
                            GroupPolicy.closeEndings(openMembers + snipe).any {
                                snipe.id in
                                    setOf(it.earlier.id, it.later.id)
                            },
                    )
                }
        val canUngroup = members.any { !it.status.isFinal }
        if (members.isEmpty()) return GroupUiState(now, group.name, GroupContent.Empty, candidates, canUngroup)
        val wonMember = members.firstOrNull { it.status == SnipeStatus.WON }
        val rows =
            members.map { snipe ->
                val history = events[snipe.id].orEmpty()
                val context = GroupContext(group.name, wonMember?.takeIf { it.id != snipe.id }?.title)
                GroupLotRowState(
                    snipeId = snipe.id,
                    title = snipe.title,
                    photo = LotPhoto.of(snipe),
                    status = LotStatusMapper.statusOf(snipe, history, now, connectivity, context),
                    price = snipe.lastPrice,
                    bidCount = snipe.lastBidCount,
                    postage = snipe.postage,
                    endsAt = Instant.ofEpochMilli(snipe.endTimeUtcMillis),
                    extendedBidding = snipe.extendedBidding,
                    failedCheckAt = LotStatusMapper.failedCheckAt(snipe, history),
                )
            }
        val complete = members.count { it.status == SnipeStatus.WON } >= group.targetWins
        val content = GroupContent.Members(rule(group, members), rows, canAdd = !complete)
        return GroupUiState(now, group.name, content, if (complete) emptyList() else candidates, canUngroup)
    }

    /** The rule card: the win once it happened, a warning when two members bid too close, else the rule. */
    fun rule(
        group: SnipeGroup,
        members: List<Snipe>,
    ): RuleCard {
        val target = group.targetWins
        val won = members.filter { it.status == SnipeStatus.WON }
        if (won.isNotEmpty() && won.size >= target) {
            val first = won.first()
            val price = (first.result?.finalPrice ?: first.lastPrice)?.let { " for ${it.formatted()}" }.orEmpty()
            val cancelled = members.count { it.status == SnipeStatus.SKIPPED_BY_GROUP }
            val note =
                when (cancelled) {
                    0 -> null
                    1 -> "Kestrel cancelled the other snipe."
                    else -> "Kestrel cancelled the other $cancelled snipes."
                }
            return RuleCard(RuleTone.Won, "Won ${won.size} of ${members.size}", "${first.title}$price.", note)
        }
        val headline = "Win $target of ${members.size}"
        val open = members.filter { !it.status.isFinal }
        GroupPolicy.closeEndings(open).firstOrNull()?.let { close ->
            val gapMillis = abs(LotTimes.bidAtUtcMillis(close.later) - LotTimes.bidAtUtcMillis(close.earlier))
            val (earlier, later) = distinctNames(close.earlier.title, close.later.title)
            return RuleCard(
                tone = RuleTone.Attention,
                headline = headline,
                body =
                    "$earlier and $later bid ${apart(gapMillis)} apart. Kestrel may not know the first result in " +
                        "time, so you could win both.",
            )
        }
        val body =
            if (members.size == 1) {
                "Add another snipe, so Kestrel wins one and cancels the rest."
            } else {
                AddPlanner.cancelsSentence(members.size)
            }
        return RuleCard(RuleTone.Neutral, headline, body, gapNote(open))
    }

    /**
     * The words that tell two titles apart: the first word after what they share ("Sony MiniDisc Walkman MZ-N707" and
     * "Sony Walkman WM-FX421" give "MiniDisc" and "Walkman"), or their first two words when they share nothing.
     */
    fun distinctNames(
        first: String,
        second: String,
    ): Pair<String, String> {
        val firstWords = first.split(' ').filter { it.isNotBlank() }
        val secondWords = second.split(' ').filter { it.isNotBlank() }
        val shared = firstWords.zip(secondWords).takeWhile { (a, b) -> a.equals(b, ignoreCase = true) }.size
        if (shared == 0 || shared >= firstWords.size || shared >= secondWords.size) {
            return shortTitle(first) to shortTitle(second)
        }
        val clean: (String) -> String = { it.trim('“', '”', '"', ',', '.') }
        return clean(firstWords[shared]) to clean(secondWords[shared])
    }

    /** "2 min", rounded to whole minutes, or "under a minute". */
    private fun apart(gapMillis: Long): String =
        if (gapMillis < MINUTE_MILLIS) {
            "under a minute"
        } else {
            minutesText((gapMillis + MINUTE_MILLIS / 2) / MINUTE_MILLIS)
        }

    private const val MINUTE_MILLIS = 60_000L

    /** "They end 23 h 45 m apart, so Kestrel will know the first result in time." */
    private fun gapNote(open: List<Snipe>): String? {
        if (open.size < 2) return null
        val ends = open.map { it.endTimeUtcMillis }.sorted()
        val closest = Duration.ofMillis(ends.zipWithNext { earlier, later -> later - earlier }.min()).countdown()
        return if (open.size == 2) {
            "They end $closest apart, so Kestrel will know the first result in time."
        } else {
            "The closest two end $closest apart, so Kestrel will know each result in time."
        }
    }

    /** Statuses a snipe may join a group from: not finished, and not while its bid window is open. */
    private val SnipeStatus.canJoinAGroup: Boolean
        get() = !isFinal && family != StatusFamily.BIDDING
}
