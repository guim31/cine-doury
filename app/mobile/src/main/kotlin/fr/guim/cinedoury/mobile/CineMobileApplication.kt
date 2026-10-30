package fr.guim.cinedoury.mobile

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import fr.guim.cinedoury.core.data.DeviceIdentity
import fr.guim.cinedoury.core.data.di.dataModule
import fr.guim.cinedoury.core.data.index.IndexScheduler
import fr.guim.cinedoury.core.designsystem.image.ImageBudget
import fr.guim.cinedoury.core.designsystem.image.addCineImages
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.playback.DeviceCapabilitiesReader
import fr.guim.cinedoury.core.playback.ExoVideoPlayer
import fr.guim.cinedoury.core.playback.PlayerBuffer
import fr.guim.cinedoury.core.playback.VideoPlayerFactory
import fr.guim.cinedoury.core.playback.playbackModule
import fr.guim.cinedoury.core.source.FailureLog
import fr.guim.cinedoury.core.source.RequestConnector
import fr.guim.cinedoury.core.source.ServerDiscovery
import fr.guim.cinedoury.core.source.SourceConnector
import fr.guim.cinedoury.core.source.SourceLog
import fr.guim.cinedoury.feature.auth.authModule
import fr.guim.cinedoury.feature.details.detailsModule
import fr.guim.cinedoury.feature.home.homeModule
import fr.guim.cinedoury.feature.library.libraryModule
import fr.guim.cinedoury.feature.manage.manageModule
import fr.guim.cinedoury.feature.player.playerModule
import fr.guim.cinedoury.feature.search.searchModule
import fr.guim.cinedoury.feature.settings.settingsModule
import fr.guim.cinedoury.source.jellyfin.ClientIdentity
import fr.guim.cinedoury.source.jellyfin.JellyfinConnector
import fr.guim.cinedoury.source.jellyfin.JellyfinDiscovery
import fr.guim.cinedoury.source.seerr.SeerrConnector
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File

/**
 * Starts Koin with the modules of the TV (M1, `SPEC.md` § 4.26: the same view models, repositories,
 * source and player), then what differs on a phone ([phoneModule]), and gives Coil its loader.
 */
class CineMobileApplication :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // Every caught failure goes to logcat, with its stack: `adb logcat -s CineDoury`.
        FailureLog.sink =
            FailureLog.Sink { level, message, error ->
                when (level) {
                    FailureLog.Level.Warning -> Log.w(LOG_TAG, message, error)
                    FailureLog.Level.Error -> Log.e(LOG_TAG, message, error)
                }
            }
        // Robolectric creates an application per test in the same process: start afresh.
        stopKoin()
        startKoin {
            androidContext(this@CineMobileApplication)
            modules(
                dataModule,
                playbackModule,
                jellyfinModule,
                authModule,
                homeModule,
                libraryModule,
                detailsModule,
                manageModule,
                playerModule,
                searchModule,
                settingsModule,
                requestsModule,
                phoneModule,
            )
        }
        // The local index follows the open profile, in the background (SPEC.md § 4.10).
        GlobalContext.get().get<IndexScheduler>().start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val budget = imageBudget()
        return ImageLoader
            .Builder(context)
            .crossfade(CineMotion.IMAGE_FADE_MILLIS)
            .memoryCache { MemoryCache.Builder().maxSizeBytes(budget.memoryCacheBytes).build() }
            .diskCache { imageDiskCache(cacheDir.resolve(IMAGE_CACHE), budget.diskCacheBytes) }
            .components { addCineImages() }
            .build()
    }
}

/** The images this device may hold (`ImageBudget`). */
fun Context.imageBudget(): ImageBudget {
    val activities = getSystemService(ActivityManager::class.java)
    return ImageBudget.of(lowRam = activities.isLowRamDevice, memoryClassMegabytes = activities.memoryClass)
}

/** One disk cache per folder and per process (Robolectric makes an application per test). */
private var diskCache: Pair<File, DiskCache>? = null

@Synchronized
private fun imageDiskCache(
    folder: File,
    maxBytes: Long,
): DiskCache =
    diskCache?.takeIf { it.first == folder }?.second
        ?: DiskCache
            .Builder()
            .directory(folder.toOkioPath())
            .maxSizeBytes(maxBytes)
            .build()
            .also { diskCache = folder to it }

private const val IMAGE_CACHE = "images"

/** The Jellyfin source behind `:core:source`: the only place the app names it. */
val jellyfinModule =
    module {
        single {
            val device = get<DeviceIdentity>()
            val context = get<Context>()
            val version =
                context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .versionName
                    .orEmpty()
            ClientIdentity(appVersion = version, deviceId = device.deviceId, deviceName = device.deviceName)
        }
        single<SourceConnector> { JellyfinConnector(get(), log = SourceLog { Log.i(LOG_TAG, it) }) }
        single<ServerDiscovery> { JellyfinDiscovery() }
    }

/** The request server behind `:core:source` (Seerr), optional. */
val requestsModule =
    module {
        single<RequestConnector> { SeerrConnector() }
    }

/**
 * What a phone does otherwise than a TV (M1, `SPEC.md` § 4.26), over the modules of the TV:
 * - its capabilities without passthrough: a phone has no amplifier, the server sends audio it decodes;
 * - its player with the buffer of the phone ([PlayerBuffer.Phone]: the TV's until M2 sets it) and no
 *   passthrough, whatever the settings say.
 */
val phoneModule =
    module {
        single { DeviceCapabilitiesReader(get(), allowPassthrough = false) }
        single<VideoPlayerFactory> {
            val context = get<Context>()
            VideoPlayerFactory { capabilities, settings ->
                ExoVideoPlayer(
                    context,
                    capabilities,
                    allowedPassthrough = emptySet(),
                    matchFrameRate = settings.matchFrameRate,
                    audioOffsetMillis = settings.audioOffsetMillis,
                    allowDolbyVision = settings.allowDolbyVision,
                    buffer = PlayerBuffer.Phone,
                )
            }
        }
    }

/** The tag of the lines of the app in logcat. */
const val LOG_TAG = "CineDoury"
