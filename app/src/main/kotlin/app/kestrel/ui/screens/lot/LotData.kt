package app.kestrel.ui.screens.lot

import android.content.Context
import app.kestrel.AppContainer
import app.kestrel.data.EditResult
import app.kestrel.data.PricePoint
import app.kestrel.model.Money
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineEvent
import app.kestrel.platform.SnipeAction
import app.kestrel.platform.SnipeService
import app.kestrel.ui.model.Connectivity
import app.kestrel.ui.model.GroupContext
import app.kestrel.ui.shell.ConnectivityMonitor
import app.kestrel.ui.shell.UiPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the lot detail (S3) and result (S4) screens read and change, behind one seam so their ViewModels are tested
 * without a database. Every change goes through the app's snipe actions, so alarms, running windows and
 * notifications always follow.
 */
interface LotData {
    fun snipe(id: Long): Flow<Snipe?>

    fun timeline(id: Long): Flow<List<TimelineEvent>>

    fun prices(id: Long): Flow<List<PricePoint>>

    /** The group of a snipe, for the words of a group cancel; null when [groupId] is null or the group is gone. */
    fun group(
        groupId: Long?,
        snipeId: Long,
    ): Flow<GroupContext?>

    /** The eBay account the app is signed in with; null when signed out. */
    val accountName: Flow<String?>

    val connectivity: Flow<Connectivity>

    /** True time, UTC epoch milliseconds: the clock every countdown and bid time is measured on. */
    fun nowUtcMillis(): Long

    suspend fun updateMax(
        id: Long,
        max: Money?,
    ): EditResult

    suspend fun setLead(
        id: Long,
        leadSeconds: Int,
    ): EditResult

    suspend fun cancel(id: Long): EditResult

    suspend fun delete(id: Long): EditResult

    suspend fun undoDelete(id: Long): EditResult

    /**
     * "Undo cancel" while the auction still runs (SnipeActions.uncancel): the same snipe is armed again and keeps its
     * timeline.
     */
    suspend fun undoCancel(id: Long): EditResult

    /** The user says how a bid Kestrel could not read ended, after checking eBay (SnipeActions.settle). */
    suspend fun settle(
        id: Long,
        won: Boolean,
    ): EditResult

    /** Settings "Suggest odd pence": whether the set-max sheet offers £90.37 for a round £90.00. */
    suspend fun suggestOddPence(): Boolean = true

    /** Whether the WON seal of [id] already stamped once (DESIGN 2.10: it plays on the first open only). */
    suspend fun sealStamped(id: Long): Boolean

    suspend fun markSealStamped(id: Long)

    /** Starts a dry run of the snipe now (it never bids). False when the snipe is gone. */
    suspend fun startDryRun(id: Long): Boolean = false

    /** The groups snipe [id] could move into (LotGroupChoices). */
    fun groupChoices(id: Long): Flow<List<GroupChoice>> = flowOf(emptyList())

    /** Moves snipe [id] into [groupId], or out of its group with null (SnipeActions.setGroup). */
    suspend fun setGroup(
        id: Long,
        groupId: Long?,
    ): EditResult = EditResult.NotFound

    /** Starts a new group named [name] with snipe [id] in it (SnipeActions.moveToNewGroup). */
    suspend fun moveToNewGroup(
        id: Long,
        name: String,
    ): EditResult = EditResult.NotFound

    /**
     * Runs [action] in the app's own scope. An Undo tapped on the next screen still reaches the data after this screen
     * and its ViewModel are gone.
     */
    fun runDetached(action: suspend () -> Unit)
}

/** [LotData] over the app's container: the repository, the snipe actions, the settings and the true clock. */
class ContainerLotData(
    private val container: AppContainer,
) : LotData {
    private val repository get() = container.repository
    private val actions get() = container.snipeActions

    override fun snipe(id: Long): Flow<Snipe?> = repository.observeSnipe(id)

    override suspend fun startDryRun(id: Long): Boolean {
        val snipe = repository.snipe(id) ?: return false
        SnipeService.start(container.context, SnipeAction.DRY_RUN, snipe)
        return true
    }

    override fun timeline(id: Long): Flow<List<TimelineEvent>> = repository.observeTimeline(id)

    override fun groupChoices(id: Long): Flow<List<GroupChoice>> =
        combine(
            repository.observeSnipe(id),
            repository.observeGroups(),
            repository.observeSnipes(),
            repository.observeGroupWins(),
        ) { snipe, groups, snipes, wins ->
            snipe?.let { LotGroupChoices.of(it, groups, snipes, wins) }.orEmpty()
        }.distinctUntilChanged()

    override suspend fun setGroup(
        id: Long,
        groupId: Long?,
    ): EditResult = actions.setGroup(id, groupId)

    override suspend fun moveToNewGroup(
        id: Long,
        name: String,
    ): EditResult = actions.moveToNewGroup(id, name)

    override fun prices(id: Long): Flow<List<PricePoint>> = repository.observePricePoints(id)

    override fun group(
        groupId: Long?,
        snipeId: Long,
    ): Flow<GroupContext?> {
        if (groupId == null) return flowOf(null)
        return combine(repository.observeGroups(), repository.observeSnipes()) { groups, snipes ->
            val group = groups.firstOrNull { it.id == groupId } ?: return@combine null
            val won = snipes.firstOrNull { it.groupId == groupId && it.id != snipeId && it.status == SnipeStatus.WON }
            GroupContext(group.name, won?.title)
        }.distinctUntilChanged()
    }

    override val accountName: Flow<String?> =
        container.settings.settings
            .map { it.signedInName }
            .distinctUntilChanged()

    override val connectivity: Flow<Connectivity> =
        ConnectivityMonitor.observe(container.context) { container.clock.nowUtcMillis() }

    override fun nowUtcMillis(): Long = container.clock.nowUtcMillis()

    override suspend fun updateMax(
        id: Long,
        max: Money?,
    ): EditResult = actions.updateMax(id, max)

    override suspend fun setLead(
        id: Long,
        leadSeconds: Int,
    ): EditResult = actions.setLead(id, leadSeconds)

    override suspend fun cancel(id: Long): EditResult = actions.cancel(id)

    override suspend fun delete(id: Long): EditResult = actions.delete(id)

    override suspend fun undoDelete(id: Long): EditResult = actions.undoDelete(id)

    override suspend fun suggestOddPence(): Boolean =
        UiPreferences
            .of(container.context)
            .values
            .first()
            .suggestOddPence

    override suspend fun undoCancel(id: Long): EditResult = actions.uncancel(id)

    override suspend fun settle(
        id: Long,
        won: Boolean,
    ): EditResult = actions.settle(id, won)

    override suspend fun sealStamped(id: Long): Boolean =
        withContext(Dispatchers.IO) { sealPreferences().getStringSet(STAMPED, emptySet()).orEmpty().contains("$id") }

    override suspend fun markSealStamped(id: Long) {
        withContext(Dispatchers.IO) {
            val preferences = sealPreferences()
            val stamped = preferences.getStringSet(STAMPED, emptySet()).orEmpty() + "$id"
            preferences.edit().putStringSet(STAMPED, stamped).apply()
        }
    }

    override fun runDetached(action: suspend () -> Unit) {
        container.applicationScope.launch { action() }
    }

    private fun sealPreferences() = container.context.getSharedPreferences(SEAL_FILE, Context.MODE_PRIVATE)

    private companion object {
        /** Only the ids of won snipes whose seal already stamped; nothing else is kept. */
        const val SEAL_FILE = "result_seals"
        const val STAMPED = "stamped"
    }
}
