package app.kestrel.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.kestrel.ui.components.SmallCaps
import app.kestrel.ui.fixture.LotMoment
import app.kestrel.ui.harness.PhoneInsets
import app.kestrel.ui.theme.Spacing

// Review pages for the screenshot tests: every row state and every pill state on one phone screen each.

/** Rows of [moments], each in its own state at its own time, as S1 draws them. */
@Composable
fun RowStatesPage(
    title: String,
    moments: List<LotMoment>,
    modifier: Modifier = Modifier,
    companions: List<LotMoment> = emptyList(),
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .windowInsetsPadding(PhoneInsets)
                .padding(horizontal = Spacing.ColumnA, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            moments.forEach { moment ->
                val lot = lotWithCompanions(moment, companions)
                LotRow(lot, moment.now, index = 0, count = 1, actions = HomeActions())
            }
        }
    }
}

/** The lot of [moment] in a list that also holds [companions] (a group's won lot, for example). */
private fun lotWithCompanions(
    moment: LotMoment,
    companions: List<LotMoment>,
): HomeLot {
    if (companions.isEmpty()) return HomeFixtures.lotOf(moment)
    val input =
        HomeFixtures.input(
            moments = listOf(moment) + companions,
            now = moment.now,
            readiness = HomeFixtures.readiness(HomeFixtures.allPassed),
        )
    return HomeFixtures
        .build(input)
        .sections
        .flatMap { it.blocks }
        .flatMap { it.lots }
        .single { it.id == moment.snipe.id }
}

/** Each of the pill's looks (DESIGN 3.8, Q26), named. */
val dockStates: List<Pair<String, HomeUiState>>
    get() =
        listOf(
            "Ready" to HomeFixtures.ready,
            "One problem" to HomeFixtures.home,
            "Several problems" to HomeFixtures.severalProblems,
            "Signed out" to HomeFixtures.signedOutHome,
            "Offline near a bid" to HomeFixtures.offline,
            "Re-checking" to HomeFixtures.rechecking,
            "First check" to HomeFixtures.firstCheck,
        )

/** The dock in each of [states]; large text shows them in two halves. */
@Composable
fun DockStatesPage(
    modifier: Modifier = Modifier,
    states: List<Pair<String, HomeUiState>> = dockStates,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .windowInsetsPadding(PhoneInsets)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Dock",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = Spacing.ColumnA),
            )
            states.forEach { (name, state) ->
                SmallCaps(name, modifier = Modifier.padding(horizontal = Spacing.ColumnA))
                HomeDock(
                    pill = state.pill,
                    showAdd = true,
                    windowInsets = WindowInsets(0),
                    onOpenReady = {},
                    onPillAction = {},
                    onAdd = {},
                )
            }
        }
    }
}
