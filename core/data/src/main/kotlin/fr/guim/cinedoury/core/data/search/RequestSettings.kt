package fr.guim.cinedoury.core.data.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.guim.cinedoury.core.data.security.TokenCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The request server of a media server: its address and API key (`SPEC.md` § 4.12). */
data class RequestServer(
    val address: String,
    val apiKey: String,
) {
    override fun toString(): String = "RequestServer(address=$address, apiKey=***)"
}

/**
 * The Seerr settings, per media server, on this device (DataStore `requests`). The API key is kept
 * like the tokens (`SPEC.md` § 4.7): encrypted by the key of the Android Keystore, which never leaves
 * it; a key that cannot be read any more counts as no setting.
 */
class RequestSettings(
    private val store: DataStore<Preferences>,
    private val cipher: TokenCipher,
) {
    fun server(serverId: String): Flow<RequestServer?> =
        store.data
            .map { prefs ->
                val address = prefs[address(serverId)]
                val key = prefs[key(serverId)]?.let(cipher::decrypt)
                if (address.isNullOrBlank() || key.isNullOrBlank()) null else RequestServer(address, key)
            }.distinctUntilChanged()

    suspend fun save(
        serverId: String,
        server: RequestServer,
    ) {
        val encrypted = cipher.encrypt(server.apiKey)
        store.edit {
            it[address(serverId)] = server.address
            it[key(serverId)] = encrypted
        }
    }

    suspend fun clear(serverId: String) {
        store.edit {
            it.remove(address(serverId))
            it.remove(key(serverId))
        }
    }

    private fun address(serverId: String) = stringPreferencesKey("seerr_address_$serverId")

    private fun key(serverId: String) = stringPreferencesKey("seerr_key_$serverId")

    companion object {
        const val FILE = "requests"
    }
}
