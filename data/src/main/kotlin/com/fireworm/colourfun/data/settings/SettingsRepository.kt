package com.fireworm.colourfun.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * App settings. Sound, haptics and stay-inside-the-lines are parent settings (behind the gate);
 * the last tool and colour are remembered for the child.
 *
 * @property lastTool the app's own encoding of the tool; `null` until one is chosen.
 */
data class Settings(
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val stayInsideLines: Boolean = true,
    val lastTool: String? = null,
    val lastColor: Int? = null,
)

/** Backed by `datastore/settings.preferences_pb`, the only file included in backups. */
class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<Settings> = store.data.map { prefs ->
        Settings(
            soundEnabled = prefs[SOUND] ?: true,
            hapticsEnabled = prefs[HAPTICS] ?: true,
            stayInsideLines = prefs[STAY_INSIDE] ?: true,
            lastTool = prefs[LAST_TOOL],
            lastColor = prefs[LAST_COLOR],
        )
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        store.edit { it[SOUND] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        store.edit { it[HAPTICS] = enabled }
    }

    suspend fun setStayInsideLines(enabled: Boolean) {
        store.edit { it[STAY_INSIDE] = enabled }
    }

    suspend fun setLastTool(tool: String) {
        store.edit { it[LAST_TOOL] = tool }
    }

    suspend fun setLastColor(color: Int) {
        store.edit { it[LAST_COLOR] = color }
    }

    companion object {
        /** `datastore/settings.preferences_pb`; must match the backup rules. */
        private const val FILE_NAME = "settings"

        fun create(context: Context) = SettingsRepository(
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE_NAME) },
        )

        private val SOUND = booleanPreferencesKey("soundEnabled")
        private val HAPTICS = booleanPreferencesKey("hapticsEnabled")
        private val STAY_INSIDE = booleanPreferencesKey("stayInsideLines")
        private val LAST_TOOL = stringPreferencesKey("lastToolSpec")
        private val LAST_COLOR = intPreferencesKey("lastColor")
    }
}
