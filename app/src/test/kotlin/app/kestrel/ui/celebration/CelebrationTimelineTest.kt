package app.kestrel.ui.celebration

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The celebration's story as pure geometry: what each moment shows, on a Pixel 9a-sized scene. */
class CelebrationTimelineTest {
    private val size = Size(1078f, 1140f)
    private val layout = SceneLayout(size)
    private val unit = layout.unit

    @Test
    fun theTalonsCloseOnTheTagAtTheStrike() {
        val talons = CelebrationTimeline.bird(CelebrationTimeline.STRIKE, size).talons
        assertTrue(
            "talons ${talons - layout.tagRest} from the tag",
            (talons - layout.tagRest).getDistance() < 2f * unit,
        )
    }

    @Test
    fun theTagLiesInTheGrassUntilTheStrikeThenTravelsWithTheTalons() {
        for (t in listOf(0L, 1_500L, CelebrationTimeline.STRIKE - 1L)) {
            val tag = CelebrationTimeline.tag(t, size)
            assertTrue(tag.lying)
            assertEquals(layout.tagRest, tag.anchor)
        }
        for (t in listOf(CelebrationTimeline.STRIKE + 200L, 3_900L, CelebrationTimeline.PERCH + 50L)) {
            val tag = CelebrationTimeline.tag(t, size)
            assertFalse(tag.lying)
            assertEquals(CelebrationTimeline.bird(t, size).talons, tag.anchor)
        }
    }

    @Test
    fun theKestrelEndsOnThePostFacingTheSceneWithTheTagHangingStill() {
        val end = CelebrationTimeline.bird(CelebrationTimeline.END, size)
        assertEquals(1f, end.facing)
        assertTrue((end.talons - layout.postTop).getDistance() < 0.5f * unit)
        assertEquals(0f, CelebrationTimeline.tag(CelebrationTimeline.END, size).swing, 0.01f)
    }

    @Test
    fun theFeetStayOnThePostWhileTheBirdTurnsRound() {
        var t = CelebrationTimeline.PERCH
        while (t <= CelebrationTimeline.END) {
            val talons = CelebrationTimeline.bird(t, size).talons
            assertTrue("at $t ms", (talons - layout.postTop).getDistance() < 0.5f * unit)
            t += 20L
        }
    }

    @Test
    fun nothingMovesAfterTheEnd() {
        val end = CelebrationTimeline.END
        assertEquals(CelebrationTimeline.bird(end, size), CelebrationTimeline.bird(end + 10_000L, size))
        assertEquals(CelebrationTimeline.tag(end, size), CelebrationTimeline.tag(end + 10_000L, size))
    }

    @Test
    fun theHeadHoldsStillWhileTheBodyHovers() {
        // The hovering kestrel's trick: the body rides each wingbeat, the head does not move at all.
        val heads =
            (0L until CelebrationTimeline.FOLD_FROM step 13L).map { t ->
                CelebrationTimeline.bird(t, size).toScene(KestrelShape.HeadCentre)
            }
        val bobs = (0L until CelebrationTimeline.FOLD_FROM step 13L).map { CelebrationTimeline.bird(it, size).bob }
        assertTrue("the body bobs", bobs.max() - bobs.min() > 2f)
        for (head in heads) assertTrue((head - heads.first()).getDistance() < 0.01f)
    }

    @Test
    fun theBirdAndTheTagStayInTheScene() {
        var t = 0L
        while (t <= CelebrationTimeline.END) {
            val bird = CelebrationTimeline.bird(t, size)
            for (point in listOf(bird.position, bird.talons, bird.toScene(KestrelShape.HeadCentre))) {
                assertTrue("x at $t ms: ${point.x}", point.x in 0f..size.width)
                assertTrue("y at $t ms: ${point.y}", point.y in 0f..size.height)
            }
            t += 10L
        }
    }

    @Test
    fun theWordsComeAfterTheirMoments() {
        assertTrue(CelebrationTimeline.TITLE_FROM < CelebrationTimeline.STRIKE)
        assertTrue(CelebrationTimeline.LABEL_FROM > CelebrationTimeline.STRIKE)
        assertTrue(CelebrationTimeline.COFFEE_FROM > CelebrationTimeline.PERCH)
        assertTrue(CelebrationTimeline.COFFEE_FROM + 600L <= CelebrationTimeline.END)
    }
}
