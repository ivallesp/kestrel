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
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import app.kestrel.KestrelApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** The UI's own choices at one moment (see [UiPreferences]). */
data class UiPreferenceValues(
    /** Settings "Suggest odd pence": S2 and the set-max sheets offer £90.37 for a round £90.00. */
    val suggestOddPence: Boolean = true,
    /** When the phone last signed in to eBay through Kestrel, for "Signed in on this phone since Sun 2 Nov". */
    val signedInSinceUtcMillis: Long? = null,
)

/**
 * What only the UI remembers: whether onboarding is done, Settings' "Suggest odd pence", and when the phone signed in
 * through Kestrel. Kept apart from SettingsStore, which the data layer owns; move them there once that store has
 * fields for them. One instance per process (KestrelApp holds it).
 */
class UiPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    /** The file's contents; an unreadable file reads as empty. */
    private val stored: Flow<Preferences> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    Log.w(TAG, "Could not read the UI preferences", error)
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }

    /** False until the user finishes onboarding; an unreadable file reads as not done. */
    val onboardingDone: Flow<Boolean> = stored.map { it[ONBOARDING_DONE] ?: false }

    /** The screens' choices, then each change. */
    val values: Flow<UiPreferenceValues> =
        stored
            .map { preferences ->
                UiPreferenceValues(
                    suggestOddPence = preferences[SUGGEST_ODD_PENCE] ?: true,
                    signedInSinceUtcMillis = preferences[SIGNED_IN_SINCE],
                )
            }.distinctUntilChanged()

    suspend fun markOnboardingDone() {
        dataStore.edit { it[ONBOARDING_DONE] = true }
    }

    suspend fun setSuggestOddPence(on: Boolean) {
        dataStore.edit { it[SUGGEST_ODD_PENCE] = on }
    }

    /** A sign-in on eBay's own page finished at [atUtcMillis]. */
    suspend fun recordSignIn(atUtcMillis: Long) {
        dataStore.edit { it[SIGNED_IN_SINCE] = atUtcMillis }
    }

    /** Signed out: the sign-in time no longer applies. */
    suspend fun clearSignIn() {
        dataStore.edit { it.remove(SIGNED_IN_SINCE) }
    }

    companion object {
        private const val TAG = "UiPreferences"
        private const val FILE_NAME = "ui_preferences"
        private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val SUGGEST_ODD_PENCE = booleanPreferencesKey("suggest_odd_pence")
        private val SIGNED_IN_SINCE = longPreferencesKey("signed_in_since")

        /** The process's one instance, held by the app. */
        fun of(context: Context): UiPreferences = (context.applicationContext as KestrelApp).uiPreferences

        fun create(context: Context): UiPreferences =
            UiPreferences(
                PreferenceDataStoreFactory.create(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    produceFile = { context.applicationContext.preferencesDataStoreFile(FILE_NAME) },
                ),
            )
    }
}
