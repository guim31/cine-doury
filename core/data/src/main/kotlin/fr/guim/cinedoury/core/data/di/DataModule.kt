package fr.guim.cinedoury.core.data.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import fr.guim.cinedoury.core.data.DeviceIdentity
import fr.guim.cinedoury.core.data.ProfileRepository
import fr.guim.cinedoury.core.data.ServerRepository
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.UserDataUpdates
import fr.guim.cinedoury.core.data.db.CineDatabase
import fr.guim.cinedoury.core.data.home.HomeRepository
import fr.guim.cinedoury.core.data.index.IndexScheduler
import fr.guim.cinedoury.core.data.index.LibraryIndex
import fr.guim.cinedoury.core.data.library.LibraryIconRepository
import fr.guim.cinedoury.core.data.library.LibraryStateRepository
import fr.guim.cinedoury.core.data.manage.ManageRepository
import fr.guim.cinedoury.core.data.manage.ManageUpdates
import fr.guim.cinedoury.core.data.prefs.AppPreferences
import fr.guim.cinedoury.core.data.security.KeystoreTokenCipher
import fr.guim.cinedoury.core.data.security.TokenCipher
import fr.guim.cinedoury.core.data.server.AddressResolver
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** `:core:data` for Koin. The app provides the `SourceConnector` and the `ServerDiscovery`. */
val dataModule =
    module {
        single { Room.databaseBuilder(androidContext(), CineDatabase::class.java, CineDatabase.NAME).build() }
        single { get<CineDatabase>().servers() }
        single { get<CineDatabase>().profiles() }
        single { get<CineDatabase>().homeCache() }
        single { get<CineDatabase>().preferences() }
        single { get<CineDatabase>().index() }
        single { LibraryIndex(get()) }
        single { LibraryIconRepository(get()) }
        single { IndexScheduler(get(), get()) }
        single { HomeRepository(get(), get()) }
        single { LibraryStateRepository(get()) }
        single { UserDataUpdates() }
        single { ManageUpdates() }
        single { ManageRepository(get(), get(), get()) }
        single<TokenCipher> { KeystoreTokenCipher() }
        single {
            AppPreferences(
                PreferenceDataStoreFactory.create { androidContext().preferencesDataStoreFile(AppPreferences.FILE) },
            )
        }
        single { DeviceIdentity(androidContext()) }
        single { ServerRepository(get()) }
        single { ProfileRepository(get(), get()) }
        single { AddressResolver(get()) }
        single {
            val home = get<HomeRepository>()
            val index = get<LibraryIndex>()
            SessionManager(get(), get(), get(), get(), get()) { serverId, userId ->
                home.clear(serverId, userId)
                // The index and the icons belong to the server, not to a profile.
                if (userId == null) index.clear(serverId)
            }
        }
    }
