package app.kestrel.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    /** Seconds before the end that a new snipe bids; each snipe keeps its own copy. */
    val defaultLeadSeconds: Int = DEFAULT_LEAD_SECONDS,
    /** How long before the end the countdown Live Update may start. */
    val liveUpdateMinutes: Int = DEFAULT_LIVE_UPDATE_MINUTES,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    val haptics: Boolean = true,
    /** The win celebration's sound, at media volume. */
    val celebrationSound: Boolean = true,
    /** The eBay user name shown in the app after sign-in; null when signed out. */
    val signedInName: String? = null,
) {
    companion object {
        const val DEFAULT_LEAD_SECONDS = 6
        const val DEFAULT_LIVE_UPDATE_MINUTES = 10
        val LEAD_SECONDS_RANGE: IntRange = SnipeRules.LEAD_SECONDS_RANGE
        val LIVE_UPDATE_MINUTES_RANGE = 2..30
    }
}

/** App settings in DataStore Preferences. Out-of-range values are clamped, unreadable files fall back to defaults. */
class SettingsStore(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<Settings> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    Log.w(TAG, "Could not read settings; using defaults", error)
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun setDefaultLeadSeconds(seconds: Int) {
        dataStore.edit { it[Keys.DEFAULT_LEAD_SECONDS] = seconds.coerceIn(Settings.LEAD_SECONDS_RANGE) }
    }

    suspend fun setLiveUpdateMinutes(minutes: Int) {
        dataStore.edit { it[Keys.LIVE_UPDATE_MINUTES] = minutes.coerceIn(Settings.LIVE_UPDATE_MINUTES_RANGE) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setPureBlack(enabled: Boolean) {
        dataStore.edit { it[Keys.PURE_BLACK] = enabled }
    }

    suspend fun setHaptics(enabled: Boolean) {
        dataStore.edit { it[Keys.HAPTICS] = enabled }
    }

    suspend fun setCelebrationSound(enabled: Boolean) {
        dataStore.edit { it[Keys.CELEBRATION_SOUND] = enabled }
    }

    /** Null or blank clears the name (signed out). */
    suspend fun setSignedInName(name: String?) {
        dataStore.edit { preferences ->
            val trimmed = name?.trim()
            if (trimmed.isNullOrEmpty()) {
                preferences.remove(Keys.SIGNED_IN_NAME)
            } else {
                preferences[Keys.SIGNED_IN_NAME] =
                    trimmed
            }
        }
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            defaultLeadSeconds =
                (this[Keys.DEFAULT_LEAD_SECONDS] ?: defaults.defaultLeadSeconds).coerceIn(
                    Settings.LEAD_SECONDS_RANGE,
                ),
            liveUpdateMinutes =
                (this[Keys.LIVE_UPDATE_MINUTES] ?: defaults.liveUpdateMinutes).coerceIn(
                    Settings.LIVE_UPDATE_MINUTES_RANGE,
                ),
            themeMode = ThemeMode.entries.firstOrNull { it.name == this[Keys.THEME_MODE] } ?: defaults.themeMode,
            pureBlack = this[Keys.PURE_BLACK] ?: defaults.pureBlack,
            haptics = this[Keys.HAPTICS] ?: defaults.haptics,
            celebrationSound = this[Keys.CELEBRATION_SOUND] ?: defaults.celebrationSound,
            signedInName = this[Keys.SIGNED_IN_NAME],
        )
    }

    private object Keys {
        val DEFAULT_LEAD_SECONDS = intPreferencesKey("default_lead_seconds")
        val LIVE_UPDATE_MINUTES = intPreferencesKey("live_update_minutes")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val HAPTICS = booleanPreferencesKey("haptics")
        val CELEBRATION_SOUND = booleanPreferencesKey("celebration_sound")
        val SIGNED_IN_NAME = stringPreferencesKey("signed_in_name")
    }

    companion object {
        private const val TAG = "SettingsStore"
        private const val FILE_NAME = "settings"

        /** Create once per process (the container does): two DataStores on one file throw. */
        fun create(context: Context): SettingsStore =
            SettingsStore(
                PreferenceDataStoreFactory.create(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    produceFile = { context.applicationContext.preferencesDataStoreFile(FILE_NAME) },
                ),
            )
    }
}
