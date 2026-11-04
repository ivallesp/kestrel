package app.kestrel.ui.screens.home

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import app.kestrel.model.Snipe
import app.kestrel.platform.ReadinessKey
import app.kestrel.ui.model.LotAction
import app.kestrel.ui.model.LotPhoto
import app.kestrel.ui.model.LotStatus
import java.time.Instant

/**
 * Everything S1 draws at [now]. The ViewModel rebuilds it on every tick and every data change; the screen only
 * formats it. Before the list has loaded once ([loaded] false) the screen shows its header and dock only, so a cold
 * start never flashes the empty state.
 */
@Immutable
data class HomeUiState(
    val now: Instant,
    val loaded: Boolean,
    /** The eBay account name, or null when Kestrel does not know one (signed out). */
    val accountName: String?,
    val sections: List<HomeSection>,
    val pill: ReadinessPill,
    /** Set while the phone is offline: the banner under the header. */
    val offline: OfflineNotice?,
    /** "See all finished snipes": shown once any snipe has finished. */
    val showFinishedLink: Boolean,
    /** True while a pull-to-refresh waits for the checks it asked for. */
    val refreshing: Boolean,
    /** The lead time new snipes get, for the empty state's third step. */
    val defaultLeadSeconds: Int,
) {
    /** Nothing open and nothing recently done: the empty catalogue (DESIGN 3.19). */
    val isEmpty: Boolean get() = loaded && sections.isEmpty()

    companion object {
        /** The first frame, before the database answers. */
        fun loading(
            now: Instant,
            defaultLeadSeconds: Int,
        ): HomeUiState =
            HomeUiState(
                now = now,
                loaded = false,
                accountName = null,
                sections = emptyList(),
                pill = ReadinessPills.checkingFirstTime(),
                offline = null,
                showFinishedLink = false,
                refreshing = false,
                defaultLeadSeconds = defaultLeadSeconds,
            )
    }
}

/** The four end-time buckets of S1, in screen order. */
enum class HomeBucket(
    val title: String,
) {
    /** Ends within 24 hours (and open lots whose end has just passed). */
    EndingSoon("Ending soon"),

    /** Ends within the next 7 calendar days. */
    ThisWeek("This week"),

    Later("Later"),

    /** Finished in the last 30 days, newest first. */
    Done("Done"),
}

/** One bucket: its head ("ENDING SOON", "2 lots") and its blocks of joined segments. */
@Immutable
data class HomeSection(
    val bucket: HomeBucket,
    val blocks: List<HomeBlock>,
) {
    val lotCount: Int get() = blocks.sumOf { it.lots.size }
}

/**
 * A run of segments drawn as one joined card stack (2 dp gaps, 20 dp outer and 4 dp inner corners). A group's block
 * opens with its [group] header; the first block of a bucket may open with the hero ([opensWithHero]).
 */
@Immutable
data class HomeBlock(
    val lots: List<HomeLot>,
    val group: GroupHeader? = null,
    val opensWithHero: Boolean = false,
) {
    val segmentCount: Int get() = lots.size + if (group != null) 1 else 0
}

/**
 * One lot as S1 shows it: the stored [snipe], its [status] (already mapped through LotStatusMapper), its photo and
 * what the row adds around the status.
 */
@Immutable
data class HomeLot(
    val snipe: Snipe,
    val status: LotStatus,
    val photo: LotPhoto,
    /** "Portable audio: win 1 of 2", when the row is not inside its group's block. */
    val groupFlag: String? = null,
    /** When the latest check failed, for the flag "Couldn’t check at 18:05 · retrying". */
    val failedCheckAt: Instant? = null,
    /** While offline, the time the prices are from (" · as of 18:02"). */
    val pricesAsOf: Instant? = null,
    /** The button the row or the hero shows, if any. */
    val action: LotAction? = null,
) {
    val id: Long get() = snipe.id
}

/** The first segment of a group's block: "Portable audio: win 1 of 2" and what Kestrel does once one is won. */
@Immutable
data class GroupHeader(
    val id: Long,
    val name: String,
    val targetWins: Int,
    val memberCount: Int,
)

/** While offline: "Offline since 18:02". [since] is null when the moment the connection went is unknown. */
@Immutable
data class OfflineNotice(
    val since: Instant?,
)

/** Which of the pill's looks (DESIGN 3.8) applies. */
enum class PillKind { Checking, Ready, OneProblem, SeveralProblems, SignedOut, Offline }

/**
 * The dock's readiness pill: a leading [icon], [title] (never truncated), [detail] (one line, ellipsised) and an
 * optional button. Every look except [PillKind.Ready] and [PillKind.Checking] sits on the Attention container.
 */
@Immutable
data class ReadinessPill(
    val kind: PillKind,
    @param:DrawableRes val icon: Int,
    val title: String,
    val detail: String,
    val action: PillAction? = null,
) {
    val needsAttention: Boolean get() = kind != PillKind.Ready && kind != PillKind.Checking
}

/** The pill's button: Fix opens the exact system page, Review opens S5, Sign in opens S6. */
@Immutable
sealed interface PillAction {
    val label: String

    data class Fix(
        val key: ReadinessKey,
    ) : PillAction {
        override val label: String get() = "Fix"
    }

    data object Review : PillAction {
        override val label: String get() = "Review"
    }

    data object SignIn : PillAction {
        override val label: String get() = "Sign in"
    }
}
