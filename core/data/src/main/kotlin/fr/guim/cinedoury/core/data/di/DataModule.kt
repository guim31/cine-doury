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
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.data.index.IndexScheduler
import fr.guim.cinedoury.core.data.index.LibraryIndex
import fr.guim.cinedoury.core.data.library.LibraryIconRepository
import fr.guim.cinedoury.core.data.library.LibraryStateRepository
import fr.guim.cinedoury.core.data.prefs.AppPreferences
import fr.guim.cinedoury.core.data.search.RequestSettings
import fr.guim.cinedoury.core.data.search.SearchRepository
import fr.guim.cinedoury.core.data.search.TitleIndex
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
        single { get<CineDatabase>().search() }
        single { TitleIndex(get()) }
        single { SearchRepository(get()) }
        single {
            RequestSettings(
                PreferenceDataStoreFactory.create { androidContext().preferencesDataStoreFile(RequestSettings.FILE) },
                get(),
            )
        }
        single { LibraryIconRepository(get()) }
        single { IndexScheduler(get(), get(), get()) }
        single { HomeRepository(get(), get()) }
        single { LibraryStateRepository(get()) }
        single { UserDataUpdates() }
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
            val titles = get<TitleIndex>()
            val search = get<SearchRepository>()
            val requests = get<RequestSettings>()
            SessionManager(get(), get(), get(), get(), get()) { serverId, userId ->
                home.clear(serverId, userId)
                if (userId != null) search.clearRecent(ProfileKey(serverId, userId))
                // The indexes, the icons and the request server belong to the server, not to a profile.
                if (userId == null) {
                    index.clear(serverId)
                    titles.clear(serverId)
                    requests.clear(serverId)
                }
            }
        }
    }
