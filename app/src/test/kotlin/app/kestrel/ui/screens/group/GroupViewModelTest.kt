package app.kestrel.ui.screens.group

import app.kestrel.data.EditResult
import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineEvent
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.model.Connectivity
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.model.LotStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** The Group screen's rule card, rows and moves, from the design fixture's Portable audio group. */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeGroupServices(
        snipes: List<Snipe> = CatalogueFixture.snipes,
        groups: List<SnipeGroup> = listOf(CatalogueFixture.portableAudio),
    ) : GroupServices {
        val snipeList = MutableStateFlow(snipes)
        val groupList = MutableStateFlow(groups)
        val moves = mutableListOf<Pair<Long, Long?>>()
        val detached = mutableListOf<suspend () -> Unit>()

        override fun groups(): Flow<List<SnipeGroup>> = groupList

        override fun snipes(): Flow<List<Snipe>> = snipeList

        override fun timeline(snipeId: Long): Flow<List<TimelineEvent>> = flowOf(emptyList())

        override fun connectivity(): Flow<Connectivity> = flowOf(Connectivity.Online)

        override fun ticks(): Flow<Instant> = flowOf(GroupFixtures.now)

        override fun nowUtcMillis(): Long = GroupFixtures.now.toEpochMilli()

        override suspend fun setGroup(
            snipeId: Long,
            groupId: Long?,
        ): EditResult {
            moves += snipeId to groupId
            val snipe = snipeList.value.first { it.id == snipeId }.copy(groupId = groupId)
            snipeList.value = snipeList.value.map { if (it.id == snipeId) snipe else it }
            return EditResult.Done(snipe)
        }

        val undoCancels = mutableListOf<Long>()
        val renames = mutableListOf<String>()

        override suspend fun undoCancel(snipeId: Long): EditResult {
            undoCancels += snipeId
            val snipe = snipeList.value.first { it.id == snipeId }
            return EditResult.Done(snipe.copy(id = snipeId + 100, groupId = null))
        }

        override suspend fun renameGroup(
            groupId: Long,
            name: String,
        ): String? {
            renames += name
            groupList.value = groupList.value.map { if (it.id == groupId) it.copy(name = name) else it }
            return null
        }

        override fun runDetached(work: suspend () -> Unit) {
            detached += work
        }
    }

    @Test
    fun theRuleCardSaysTheRuleAndThatTheTimingIsSafe() {
        val content = GroupFixtures.members.content as GroupContent.Members
        assertEquals(
            RuleCard(
                tone = RuleTone.Neutral,
                headline = "Win 1 of 2",
                body = "Once one is won, Kestrel cancels the other.",
                note = "They end 23\u202Fh 45\u202Fm apart, so Kestrel will know the first result in time.",
            ),
            content.rule,
        )
        assertEquals(listOf(3L, 4L), content.rows.map { it.snipeId })
        assertTrue(content.rows.all { it.status is LotStatus.Armed })
        assertTrue(content.canAdd)
    }

    @Test
    fun bidsTooCloseTogetherTurnTheCardIntoAWarning() {
        val rule = (GroupFixtures.closeEndings.content as GroupContent.Members).rule
        assertEquals(RuleTone.Attention, rule.tone)
        assertEquals(
            "MiniDisc and Walkman bid 2\u202Fmin apart. " +
                "Kestrel may not know the first result in time, so you could win both.",
            rule.body,
        )
    }

    @Test
    fun afterAWinTheCardSaysSoAndTheOtherIsCancelled() {
        val state = GroupFixtures.afterWin
        val content = state.content as GroupContent.Members
        assertEquals(
            RuleCard(
                RuleTone.Won,
                "Won 1 of 2",
                "Sony MiniDisc Walkman MZ-N707 for £31.00.",
                "Kestrel cancelled the other snipe.",
            ),
            content.rule,
        )
        val cancelled = content.rows.single { it.snipeId == 4L }.status as LotStatus.CancelledByGroup
        assertEquals("Portable audio", cancelled.groupName)
        assertEquals("Sony MiniDisc Walkman MZ-N707", cancelled.wonTitle)
        // While the Walkman's auction runs, its row offers to bid on it again.
        assertEquals(LotAction.UNDO_CANCEL, content.rows.single { it.snipeId == 4L }.rowAction(state.now))
        assertFalse(content.canAdd)
        assertTrue(state.candidates.isEmpty())
        assertFalse(state.canUngroup)
    }

    @Test
    fun onlyArmedSnipesOutsideTheGroupMayJoin() {
        val candidates = GroupFixtures.members.candidates.map { it.snipeId }
        // Canon, Omega and Game Boy have a max; the Polaroid watches; the finished ones are done.
        assertEquals(listOf(1L, 2L, 6L), candidates)
    }

    @Test
    fun aGroupWithNoSnipesSaysSo() {
        assertEquals(GroupContent.Empty, GroupFixtures.empty.content)
        assertEquals(GroupContent.Missing, GroupFixtures.missing.content)
        assertEquals("Group", GroupFixtures.missing.title)
    }

    @Test
    fun aWatchedMemberOffersSetAMax() {
        val watched = CatalogueFixture.polaroid.copy(groupId = CatalogueFixture.portableAudio.id)
        val state =
            GroupFixtures.state(
                snipes =
                    CatalogueFixture.snipes.map {
                        if (it.id ==
                            watched.id
                        ) {
                            watched
                        } else {
                            it
                        }
                    },
            )
        val row = (state.content as GroupContent.Members).rows.single { it.snipeId == watched.id }
        assertEquals(LotAction.SET_MAX, row.rowAction(state.now))
    }

    @Test
    fun ungroupKeepsEverySnipeAndLeavesUndoForTheList() =
        runTest(dispatcher) {
            val services = FakeGroupServices()
            val viewModel = GroupViewModel(CatalogueFixture.portableAudio.id, services)
            val events = mutableListOf<GroupEvent>()
            backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
            backgroundScope.launch(dispatcher) { viewModel.events.collect { events += it } }
            assertEquals("Portable audio", viewModel.uiState.value.title)
            viewModel.ungroup()
            assertEquals(listOf(3L to null, 4L to null), services.moves)
            val message = (events.single() as GroupEvent.Ungrouped).message
            assertEquals("Ungrouped Portable audio", message.text)
            assertEquals("Undo", message.actionLabel)
            message.onAction?.invoke()
            services.detached.single().invoke()
            assertEquals(listOf(3L to 1L, 4L to 1L), services.moves.drop(2))
            assertTrue(
                services.snipeList.value
                    .filter { it.id in setOf(3L, 4L) }
                    .all { it.groupId == 1L },
            )
        }

    @Test
    fun addingAPickedSnipeMovesItIntoTheGroup() =
        runTest(dispatcher) {
            val services = FakeGroupServices()
            val viewModel = GroupViewModel(CatalogueFixture.portableAudio.id, services)
            backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
            viewModel.add(6)
            assertEquals(listOf(6L to 1L), services.moves)
            val rows = (viewModel.uiState.value.content as GroupContent.Members).rows
            assertEquals(listOf(3L, 4L, 6L), rows.map { it.snipeId })
            assertEquals(
                "Win 1 of 3",
                (viewModel.uiState.value.content as GroupContent.Members).rule.headline,
            )
            assertTrue(rows.none { it.status.kind.family == app.kestrel.model.StatusFamily.WON })
            assertEquals(
                SnipeStatus.ARMED,
                services.snipeList.value
                    .first { it.id == 6L }
                    .status,
            )
        }
}
