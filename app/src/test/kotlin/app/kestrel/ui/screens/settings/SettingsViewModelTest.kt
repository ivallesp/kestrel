package app.kestrel.ui.screens.settings

import android.content.Intent
import app.kestrel.data.Settings
import app.kestrel.data.ThemeMode
import app.kestrel.model.Snipe
import app.kestrel.platform.ReadinessItem
import app.kestrel.platform.ReadinessKey
import app.kestrel.ui.fixture.CatalogueFixture
import app.kestrel.ui.screens.ready.CheckFacts
import app.kestrel.ui.screens.ready.FakePhone
import app.kestrel.ui.screens.ready.MainDispatcherRule
import app.kestrel.ui.screens.ready.ReadyFixtures
import app.kestrel.ui.screens.ready.ReadySources
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Settings' ViewModel: what it reads, what each control stores, and sign-out behind its confirmation. */
class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeStore : SettingsSources {
        val stored = MutableStateFlow(Settings(signedInName = CatalogueFixture.ACCOUNT))
        var cookie = true
        var signOuts = 0

        override fun settings(): Flow<Settings> = stored

        override fun snipes(): Flow<List<Snipe>> = MutableStateFlow(CatalogueFixture.snipes)

        override fun hasSessionCookie(): Boolean = cookie

        override suspend fun setDefaultLeadSeconds(seconds: Int) =
            stored.update { it.copy(defaultLeadSeconds = seconds) }

        override suspend fun setThemeMode(mode: ThemeMode) = stored.update { it.copy(themeMode = mode) }

        override suspend fun signOut() {
            signOuts++
            cookie = false
            stored.update { it.copy(signedInName = null) }
        }

        override fun nowUtcMillis(): Long = CatalogueFixture.now.toEpochMilli()
    }

    private class FakeReadiness(
        var phone: FakePhone = FakePhone(),
    ) : ReadySources {
        override suspend fun check(): List<ReadinessItem> = phone.items()

        override suspend fun facts(checkedAt: Instant): CheckFacts = ReadyFixtures.facts(phone, checkedAt)

        override fun snipes(): Flow<List<Snipe>> = MutableStateFlow(CatalogueFixture.snipes)

        override fun fixIntent(key: ReadinessKey): Intent? = null

        override fun nowUtcMillis(): Long = CatalogueFixture.now.toEpochMilli()
    }

    @Test
    fun itReadsTheAccountTheSettingsAndTheArmedSnipes() =
        runTest(main.dispatcher) {
            val viewModel = SettingsViewModel(FakeStore(), FakeReadiness(), version = "1.0 (1)")
            backgroundScope.launch { viewModel.state.collect {} }
            runCurrent()
            val state = viewModel.state.value
            assertEquals(AccountState.SignedIn(CatalogueFixture.ACCOUNT), state.account)
            assertEquals("DESIGN: “Your 5 armed snipes pause…”", 5, state.armedSnipes)
            assertEquals(6, state.defaultLeadSeconds)
            assertEquals(ThemeMode.SYSTEM, state.themeMode)
            assertNull("no check has run yet", state.readiness)
        }

    @Test
    fun everyControlStoresItsValue() =
        runTest(main.dispatcher) {
            val store = FakeStore()
            val viewModel = SettingsViewModel(store, FakeReadiness(), version = "1.0 (1)")
            backgroundScope.launch { viewModel.state.collect {} }
            viewModel.setDefaultLead(10)
            viewModel.setThemeMode(ThemeMode.DARK)
            runCurrent()
            val stored = store.stored.value
            assertEquals(10, stored.defaultLeadSeconds)
            assertEquals(ThemeMode.DARK, stored.themeMode)
            assertEquals(ThemeMode.DARK, viewModel.state.value.themeMode)
        }

    @Test
    fun signingOutNeedsTheConfirmation() =
        runTest(main.dispatcher) {
            val store = FakeStore()
            val viewModel = SettingsViewModel(store, FakeReadiness(), version = "1.0 (1)")
            backgroundScope.launch { viewModel.state.collect {} }
            val messages = mutableListOf<String>()
            backgroundScope.launch { viewModel.messages.collect { messages += it } }
            viewModel.askToSignOut()
            runCurrent()
            assertTrue(viewModel.state.value.confirmingSignOut)
            viewModel.cancelSignOut()
            runCurrent()
            assertFalse(viewModel.state.value.confirmingSignOut)
            assertEquals(0, store.signOuts)

            viewModel.askToSignOut()
            viewModel.signOut()
            runCurrent()
            assertEquals(1, store.signOuts)
            assertEquals(AccountState.SignedOut, viewModel.state.value.account)
            assertEquals(listOf(SettingsViewModel.SIGNED_OUT), messages)
        }

    @Test
    fun theThisPhoneRowCountsTheProblemsS5Shows() =
        runTest(main.dispatcher) {
            val readiness = FakeReadiness()
            val viewModel = SettingsViewModel(FakeStore(), readiness, version = "1.0 (1)")
            backgroundScope.launch { viewModel.state.collect {} }
            viewModel.refresh()
            runCurrent()
            assertEquals(
                1,
                viewModel.state.value.readiness
                    ?.problems,
            )
            readiness.phone = FakePhone(signedIn = false)
            viewModel.refresh()
            runCurrent()
            assertEquals(
                2,
                viewModel.state.value.readiness
                    ?.problems,
            )
        }

    @Test
    fun aSessionCookieWithoutANameStillReadsAsSignedIn() =
        runTest(main.dispatcher) {
            val store = FakeStore().apply { stored.value = Settings(signedInName = null) }
            val viewModel = SettingsViewModel(store, FakeReadiness(), version = "1.0 (1)")
            backgroundScope.launch { viewModel.state.collect {} }
            runCurrent()
            assertEquals(AccountState.SignedIn(null), viewModel.state.value.account)
        }

    @Test
    fun sheetsOpenAndClose() =
        runTest(main.dispatcher) {
            val viewModel = SettingsViewModel(FakeStore(), FakeReadiness(), version = "1.0 (1)")
            backgroundScope.launch { viewModel.state.collect {} }
            viewModel.openSheet(SettingsSheet.Licences)
            runCurrent()
            assertEquals(SettingsSheet.Licences, viewModel.state.value.sheet)
            viewModel.closeSheet()
            runCurrent()
            assertNull(viewModel.state.value.sheet)
        }

    @Test
    fun theSignOutWordsCountTheArmedSnipes() {
        assertEquals("Your 5 armed snipes pause until you sign in again. Kestrel keeps your list.", signOutText(5))
        assertEquals("Your armed snipe pauses until you sign in again. Kestrel keeps your list.", signOutText(1))
        assertEquals("Kestrel keeps your list. Sign in again before you arm a snipe.", signOutText(0))
    }

    @Test
    fun theFontLicenceReflowsIntoParagraphs() {
        val text =
            """
            PREAMBLE
            The goals of the Open Font License (OFL) are to stimulate worldwide
            development of collaborative font projects.

            -----------------------------------------------------------
            SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
            -----------------------------------------------------------

            1) Neither the Font Software nor any of its individual components,
            in Original or Modified Versions, may be sold by itself.
            """.trimIndent()
        assertEquals(
            "PREAMBLE\nThe goals of the Open Font License (OFL) are to stimulate worldwide development of " +
                "collaborative font projects.\n\nSIL OPEN FONT LICENSE Version 1.1 - 26 February 2007\n\n" +
                "1) Neither the Font Software nor any of its individual components, in Original or Modified " +
                "Versions, may be sold by itself.",
            reflowLicence(text),
        )
    }
}
