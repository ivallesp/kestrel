package app.kestrel.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.kestrel.model.EbaySite
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KestrelDatabaseTest {
    private lateinit var database: KestrelDatabase

    @Before
    fun setUp() {
        database = KestrelDatabase.inMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun row(
        itemId: String,
        endTimeUtcMillis: Long,
        status: SnipeStatus = SnipeStatus.ARMED,
        groupId: Long? = null,
    ) = testSnipe(id = 0, status = status, endTimeUtcMillis = endTimeUtcMillis)
        .toEntity(deletedAtUtcMillis = null)
        .copy(id = 0, itemId = itemId, groupId = groupId)

    @Test
    fun snipesHaveIndicesOnEndTimeAndStatus() {
        val indexed = mutableSetOf<String>()
        database.openHelper.readableDatabase.query("PRAGMA index_list('snipes')").use { cursor ->
            while (cursor.moveToNext()) indexed += cursor.getString(cursor.getColumnIndexOrThrow("name"))
        }

        assertTrue(indexed.toString(), "index_snipes_endTimeUtcMillis" in indexed)
        assertTrue(indexed.toString(), "index_snipes_status" in indexed)
    }

    @Test
    fun allSnipesFlowIsOrderedByEndTimeAndUpdatesOnWrite() =
        runTest {
            val dao = database.snipeDao()
            dao.insert(row("late", endTimeUtcMillis = TEST_NOW + 2 * HOUR))

            dao.observeAll().test {
                assertEquals(listOf("late"), awaitItem().map { it.itemId })
                dao.insert(row("early", endTimeUtcMillis = TEST_NOW + HOUR))
                assertEquals(listOf("early", "late"), awaitItem().map { it.itemId })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun statusFiltersUseStoredNames() =
        runTest {
            val dao = database.snipeDao()
            dao.insert(row("armed", TEST_NOW + HOUR))
            dao.insert(row("won", TEST_NOW + 2 * HOUR, status = SnipeStatus.WON))

            assertEquals(
                listOf("won"),
                dao.observeWithStatuses(SnipeRules.FINAL_STATUS_NAMES).first().map { it.itemId },
            )
            assertEquals(listOf("armed"), dao.withoutStatuses(SnipeRules.FINAL_STATUS_NAMES).map { it.itemId })
            assertEquals("armed", dao.findOpen(EbaySite.UK, "armed", SnipeRules.FINAL_STATUS_NAMES)?.itemId)
            assertNull(dao.findOpen(EbaySite.UK, "won", SnipeRules.FINAL_STATUS_NAMES))
        }

    @Test
    fun deletingASnipeRowDeletesItsTimelineAndPrices() =
        runTest {
            val dao = database.snipeDao()
            val id = dao.insert(row("item", TEST_NOW + HOUR))
            database.timelineDao().insert(
                TimelineEventEntity(
                    snipeId = id,
                    atUtcMillis = TEST_NOW,
                    kind = TimelineKind.ADDED,
                    message = "Added",
                    detail = null,
                ),
            )
            database.pricePointDao().insert(
                PricePointEntity(snipeId = id, atUtcMillis = TEST_NOW, price = gbp("1.00"), bidCount = 0),
            )
            dao.markDeleted(id, TEST_NOW)

            assertEquals(1, dao.purgeDeleted(TEST_NOW + 1))

            assertTrue(
                database
                    .timelineDao()
                    .observeFor(id)
                    .first()
                    .isEmpty(),
            )
            assertNull(database.pricePointDao().latest(id))
        }

    @Test
    fun deletingAGroupLeavesItsSnipesWithoutAGroup() =
        runTest {
            val groupId =
                database.groupDao().insert(
                    GroupEntity(name = "Lenses", targetWins = 1, createdAtUtcMillis = TEST_NOW),
                )
            val snipeId = database.snipeDao().insert(row("item", TEST_NOW + HOUR, groupId = groupId))

            database.openHelper.writableDatabase.execSQL("DELETE FROM `groups` WHERE id = $groupId")

            assertNull(database.snipeDao().find(snipeId)?.groupId)
            assertEquals(listOf<GroupEntity>(), database.groupDao().observeAll().first())
        }

    @Test
    fun timelineIsInTimeOrder() =
        runTest {
            val id = database.snipeDao().insert(row("item", TEST_NOW + HOUR))
            val timeline = database.timelineDao()
            timeline.insert(
                TimelineEventEntity(
                    snipeId = id,
                    atUtcMillis = TEST_NOW + 2,
                    kind = TimelineKind.BID_SENT,
                    message = "b",
                    detail = null,
                ),
            )
            timeline.insert(
                TimelineEventEntity(
                    snipeId = id,
                    atUtcMillis = TEST_NOW + 1,
                    kind = TimelineKind.WINDOW_OPENED,
                    message = "a",
                    detail = null,
                ),
            )

            assertEquals(listOf("a", "b"), timeline.observeFor(id).first().map { it.message })
            assertEquals("b", timeline.latest(id, TimelineKind.BID_SENT)?.message)
        }
}
