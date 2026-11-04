package app.kestrel.ui.screens.group

import app.kestrel.model.Snipe
import app.kestrel.model.SnipeGroup
import app.kestrel.model.SnipeOutcome
import app.kestrel.model.SnipeResult
import app.kestrel.model.SnipeStatus
import app.kestrel.model.TimelineEvent
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.fixture.CatalogueFixture.at
import app.kestrel.ui.fixture.CatalogueFixture.gbp
import app.kestrel.ui.fixture.CatalogueFixture.millis
import app.kestrel.ui.model.Connectivity
import java.time.Instant

/** The Portable audio group (DESIGN Group screen) in each state, through the real planner and status mapper. */
object GroupFixtures {
    /** The Group screen renders at 18:08:40 (DESIGN Group screen "Now"). */
    val now: Instant = at("2025-11-09T18:08:40Z")

    val group: SnipeGroup = CatalogueFixture.portableAudio

    private val miniDisc = CatalogueFixture.miniDisc
    private val walkman = CatalogueFixture.walkman

    /** The list as the repository returns it: the two members and the rest. */
    val snipes: List<Snipe> = CatalogueFixture.snipes

    fun state(
        snipes: List<Snipe> = this.snipes,
        at: Instant = now,
        events: Map<Long, List<TimelineEvent>> = emptyMap(),
        group: SnipeGroup? = this.group,
    ): GroupUiState =
        withDesignPhotos(GroupPlanner.state(group, this.group.id, snipes, events, Connectivity.Online, at), snipes)

    /** The design's own photo treatment (the Canon and the Polaroid are scenes), which the domain does not store. */
    private fun withDesignPhotos(
        state: GroupUiState,
        snipes: List<Snipe>,
    ): GroupUiState {
        val photos = snipes.associate { it.id to CatalogueFixture.photoOf(it) }
        val content =
            when (val members = state.content) {
                is GroupContent.Members -> {
                    members.copy(
                        rows = members.rows.map { it.copy(photo = photos.getValue(it.snipeId)) },
                    )
                }

                else -> {
                    members
                }
            }
        return state.copy(
            content = content,
            candidates = state.candidates.map { it.copy(photo = photos.getValue(it.snipeId)) },
        )
    }

    /** MiniDisc and Walkman, 23 h 45 m apart. */
    val members: GroupUiState get() = state()

    /** The Walkman moved to end 1 m 55 s after the MiniDisc: the rule card warns. */
    val closeEndings: GroupUiState
        get() {
            val closeWalkman = walkman.copy(endTimeUtcMillis = miniDisc.endTimeUtcMillis + 115_000)
            return state(snipes = snipes.map { if (it.id == walkman.id) closeWalkman else it })
        }

    /** Mon 10 Nov 20:12: the MiniDisc was won for £31.00, so the group cancelled the Walkman. */
    val afterWin: GroupUiState
        get() {
            val won =
                miniDisc.copy(
                    status = SnipeStatus.WON,
                    lastPrice = gbp("31.00"),
                    lastBidCount = 8,
                    result =
                        SnipeResult(
                            outcome = SnipeOutcome.WON,
                            finalPrice = gbp("31.00"),
                            bidSentAtUtcMillis = millis("2025-11-10T20:09:59.100Z"),
                            bidLeadMillis = 5_900,
                            replyLatencyMillis = 700,
                            ebayReplyText = "Your current bid puts you in the lead.",
                            reason = "eBay says you are the winning bidder.",
                        ),
                )
            val cancelled = walkman.copy(status = SnipeStatus.SKIPPED_BY_GROUP)
            val list =
                snipes.map {
                    if (it.id == won.id) {
                        won
                    } else if (it.id == cancelled.id) {
                        cancelled
                    } else {
                        it
                    }
                }
            return state(snipes = list, at = at("2025-11-10T20:12:00Z"))
        }

    /** A group whose members all left. */
    val empty: GroupUiState
        get() = state(snipes = snipes.filter { it.groupId != group.id })

    val missing: GroupUiState get() = state(group = null)
}
