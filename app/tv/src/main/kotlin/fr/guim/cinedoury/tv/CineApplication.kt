package fr.guim.cinedoury.tv

import android.app.Application
import android.content.pm.ApplicationInfo
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import fr.guim.cinedoury.core.data.DeviceIdentity
import fr.guim.cinedoury.core.data.di.dataModule
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.source.ServerDiscovery
import fr.guim.cinedoury.core.source.SourceConnector
import fr.guim.cinedoury.feature.auth.authModule
import fr.guim.cinedoury.feature.home.homeModule
import fr.guim.cinedoury.feature.library.libraryModule
import fr.guim.cinedoury.feature.settings.settingsModule
import fr.guim.cinedoury.source.jellyfin.ClientIdentity
import fr.guim.cinedoury.source.jellyfin.JellyfinConnector
import fr.guim.cinedoury.source.jellyfin.JellyfinDiscovery
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/** Starts Koin and gives Coil its image loader (`SPEC.md` § 4.2). */
class CineApplication :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // Robolectric creates an application per test in the same process: start afresh.
        stopKoin()
        startKoin {
            androidContext(this@CineApplication)
            modules(dataModule, jellyfinModule, authModule, homeModule, libraryModule, settingsModule)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .crossfade(CineMotion.IMAGE_FADE_MILLIS)
            .build()
}

/** The Jellyfin source behind `:core:source`: the only place the app names it. */
val jellyfinModule =
    module {
        single {
            val device = get<DeviceIdentity>()
            val context = get<android.content.Context>()
            val version =
                context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .versionName
                    .orEmpty()
            ClientIdentity(appVersion = version, deviceId = device.deviceId, deviceName = device.deviceName)
        }
        single<SourceConnector> { JellyfinConnector(get()) }
        single<ServerDiscovery> { JellyfinDiscovery() }
    }

/** Whether this is a debug build: the catalogue is offered in the settings. */
val Application.isDebuggable: Boolean
    get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
