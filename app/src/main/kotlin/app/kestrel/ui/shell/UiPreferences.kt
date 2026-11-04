package app.kestrel.ui.shell

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * What only the UI remembers: whether onboarding is done. Kept apart from SettingsStore, which the data layer owns;
 * move it there once that store has a field for it. One instance per process (KestrelApp holds it).
 */
class UiPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    /** False until the user finishes onboarding; an unreadable file reads as not done. */
    val onboardingDone: Flow<Boolean> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    Log.w(TAG, "Could not read the UI preferences", error)
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }.map { it[ONBOARDING_DONE] ?: false }

    suspend fun markOnboardingDone() {
        dataStore.edit { it[ONBOARDING_DONE] = true }
    }

    companion object {
        private const val TAG = "UiPreferences"
        private const val FILE_NAME = "ui_preferences"
        private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

        fun create(context: Context): UiPreferences =
            UiPreferences(
                PreferenceDataStoreFactory.create(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    produceFile = { context.applicationContext.preferencesDataStoreFile(FILE_NAME) },
                ),
            )
    }
}
