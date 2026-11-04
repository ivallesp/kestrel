package app.kestrel.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun TestScope.newDataStore() =
        PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(context.filesDir, "settings-test-${System.nanoTime()}.preferences_pb") },
        )

    @Test
    fun defaultsMatchTheProductDecisions() =
        runTest {
            val settings = SettingsStore(newDataStore()).current()

            assertEquals(Settings(), settings)
            assertEquals(6, settings.defaultLeadSeconds)
            assertEquals(10, settings.liveUpdateMinutes)
            assertEquals(ThemeMode.SYSTEM, settings.themeMode)
            assertEquals(false, settings.pureBlack)
            assertEquals(true, settings.haptics)
            assertNull(settings.signedInName)
        }

    @Test
    fun everySettingIsStored() =
        runTest {
            val store = SettingsStore(newDataStore())

            store.setDefaultLeadSeconds(8)
            store.setLiveUpdateMinutes(15)
            store.setThemeMode(ThemeMode.DARK)
            store.setPureBlack(true)
            store.setHaptics(false)
            store.setSignedInName("  ivan_v ")

            assertEquals(
                Settings(
                    defaultLeadSeconds = 8,
                    liveUpdateMinutes = 15,
                    themeMode = ThemeMode.DARK,
                    pureBlack = true,
                    haptics = false,
                    signedInName = "ivan_v",
                ),
                store.current(),
            )
        }

    @Test
    fun outOfRangeValuesAreClamped() =
        runTest {
            val store = SettingsStore(newDataStore())

            store.setDefaultLeadSeconds(0)
            store.setLiveUpdateMinutes(500)

            assertEquals(Settings.LEAD_SECONDS_RANGE.first, store.current().defaultLeadSeconds)
            assertEquals(Settings.LIVE_UPDATE_MINUTES_RANGE.last, store.current().liveUpdateMinutes)
        }

    @Test
    fun blankNameSignsOut() =
        runTest {
            val store = SettingsStore(newDataStore())
            store.setSignedInName("ivan_v")

            store.setSignedInName("   ")

            assertNull(store.current().signedInName)
        }

    @Test
    fun valuesWrittenByAnOlderOrDamagedVersionFallBackSafely() =
        runTest {
            val dataStore = newDataStore()
            dataStore.edit {
                it[intPreferencesKey("default_lead_seconds")] = -40
                it[stringPreferencesKey("theme_mode")] = "SEPIA"
            }

            val settings = SettingsStore(dataStore).current()

            assertEquals(Settings.LEAD_SECONDS_RANGE.first, settings.defaultLeadSeconds)
            assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        }
}
