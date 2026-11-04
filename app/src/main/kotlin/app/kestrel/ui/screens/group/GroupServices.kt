package app.kestrel.ui.screens.group

import android.content.Context
import app.kestrel.AppContainer
import app.kestrel.data.EditResult
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.model.TimelineEvent
import app.kestrel.ui.model.Connectivity
import app.kestrel.ui.model.UndoCancel
import app.kestrel.ui.screens.add.tickingNow
import app.kestrel.ui.shell.ConnectivityMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.Instant

/** What the Group screen needs from the app, behind one seam so its ViewModel runs on the JVM with fakes. */
interface GroupServices {
    fun groups(): Flow<List<SnipeGroup>>

    fun snipes(): Flow<List<Snipe>>

    fun timeline(snipeId: Long): Flow<List<TimelineEvent>>

    fun connectivity(): Flow<Connectivity>

    fun ticks(): Flow<Instant>

    fun nowUtcMillis(): Long

    suspend fun setGroup(
        snipeId: Long,
        groupId: Long?,
    ): EditResult

    /** "Undo cancel": arms a snipe the group cancelled again, on its own (see [UndoCancel]). */
    suspend fun undoCancel(snipeId: Long): EditResult

    /** Gives the group a new name; null when it was renamed, otherwise why it was not. */
    suspend fun renameGroup(
        groupId: Long,
        name: String,
    ): String?

    /** Runs [work] outside any screen, so an Undo left for the previous screen still completes. */
    fun runDetached(work: suspend () -> Unit)
}

/** [GroupServices] over the app's container and the phone's connection. */
internal class ContainerGroupServices(
    private val container: AppContainer,
    private val context: Context,
) : GroupServices {
    override fun groups(): Flow<List<SnipeGroup>> = container.repository.observeGroups()

    override fun snipes(): Flow<List<Snipe>> = container.repository.observeSnipes()

    override fun timeline(snipeId: Long): Flow<List<TimelineEvent>> = container.repository.observeTimeline(snipeId)

    override fun connectivity(): Flow<Connectivity> =
        ConnectivityMonitor.observe(context) { System.currentTimeMillis() }

    override fun ticks(): Flow<Instant> = tickingNow(::nowUtcMillis)

    override fun nowUtcMillis(): Long = container.clock.nowUtcMillis()

    override suspend fun setGroup(
        snipeId: Long,
        groupId: Long?,
    ): EditResult = container.snipeActions.setGroup(snipeId, groupId)

    override suspend fun undoCancel(snipeId: Long): EditResult {
        val snipe = container.repository.snipe(snipeId) ?: return EditResult.NotFound
        return UndoCancel.run(
            snipe = snipe,
            nowUtcMillis = nowUtcMillis(),
            add = { listing, max, lead -> container.snipeActions.add(listing, max, lead, groupId = null) },
            delete = container.snipeActions::delete,
        )
    }

    // The data layer stores a group's name when the group is made and has no way to change it yet, so Rename says
    // so instead of pretending; the sheet and its checks are ready for a repository call.
    override suspend fun renameGroup(
        groupId: Long,
        name: String,
    ): String? = RENAME_NOT_STORED

    override fun runDetached(work: suspend () -> Unit) {
        container.applicationScope.launch { work() }
    }

    private companion object {
        const val RENAME_NOT_STORED = "Kestrel can’t store a new name for a group yet."
    }
}
