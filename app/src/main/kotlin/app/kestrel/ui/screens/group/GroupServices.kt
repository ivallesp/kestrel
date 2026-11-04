package app.kestrel.ui.screens.group

import android.content.Context
import app.kestrel.AppContainer
import app.kestrel.data.EditResult
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.model.TimelineEvent
import app.kestrel.ui.model.Connectivity
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

    override fun runDetached(work: suspend () -> Unit) {
        container.applicationScope.launch { work() }
    }
}
