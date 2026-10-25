package fr.guim.cinedoury.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.guim.cinedoury.core.data.StartupMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Device preferences of J1 (DataStore): the last server and profile, the startup mode. */
class AppPreferences(
    private val store: DataStore<Preferences>,
) {
    val startupMode: Flow<StartupMode> =
        store.data.map { prefs ->
            prefs[STARTUP_MODE]?.let { name -> StartupMode.entries.firstOrNull { it.name == name } } ?: DEFAULT_STARTUP
        }

    suspend fun setStartupMode(mode: StartupMode) {
        store.edit { it[STARTUP_MODE] = mode.name }
    }

    suspend fun lastServerId(): String? = store.data.first()[LAST_SERVER]

    suspend fun lastUserId(): String? = store.data.first()[LAST_USER]

    suspend fun setLastServer(id: String?) {
        store.edit { prefs ->
            if (id == null) prefs.remove(LAST_SERVER) else prefs[LAST_SERVER] = id
            prefs.remove(LAST_USER)
        }
    }

    suspend fun setLastUser(id: String?) {
        store.edit { prefs -> if (id == null) prefs.remove(LAST_USER) else prefs[LAST_USER] = id }
    }

    companion object {
        const val FILE = "settings"

        /** A shared TV: the profile picker by default. */
        val DEFAULT_STARTUP = StartupMode.AlwaysAsk
        private val STARTUP_MODE = stringPreferencesKey("startup_mode")
        private val LAST_SERVER = stringPreferencesKey("last_server")
        private val LAST_USER = stringPreferencesKey("last_user")
    }
}
