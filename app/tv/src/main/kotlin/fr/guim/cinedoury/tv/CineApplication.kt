package fr.guim.cinedoury.tv

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
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
import fr.guim.cinedoury.core.playback.playbackModule
import fr.guim.cinedoury.core.source.FailureLog
import fr.guim.cinedoury.core.source.GestureLog
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
import fr.guim.cinedoury.feature.update.updateModule
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

/** Starts Koin and gives Coil its image loader (`SPEC.md` § 4.2). */
class CineApplication :
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
        // The moments `ci/couch-gestures.sh` times, after `adb shell setprop log.tag.CineDoury DEBUG` only.
        GestureLog.sink =
            GestureLog.Sink { event -> if (Log.isLoggable(LOG_TAG, Log.DEBUG)) Log.d(LOG_TAG, "gesture $event") }
        // Robolectric creates an application per test in the same process: start afresh.
        stopKoin()
        startKoin {
            androidContext(this@CineApplication)
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
                updateModule,
                requestsModule,
            )
        }
        // The local index follows the open profile, in the background (SPEC.md § 4.10).
        GlobalContext.get().get<IndexScheduler>().start()
    }

    /** Coil's caches sized for this device (`ImageBudget`, `SPEC.md` § 4.20). */
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

/** The images this device may hold (`ImageBudget`): less on a Chromecast than on a Shield. */
fun Context.imageBudget(): ImageBudget {
    val activities = getSystemService(ActivityManager::class.java)
    return ImageBudget.of(lowRam = activities.isLowRamDevice, memoryClassMegabytes = activities.memoryClass)
}

/**
 * One disk cache per folder and per process: two on the same folder would corrupt it (Robolectric
 * makes an application per test).
 */
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
            val context = get<android.content.Context>()
            val version =
                context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .versionName
                    .orEmpty()
            ClientIdentity(appVersion = version, deviceId = device.deviceId, deviceName = device.deviceName)
        }
        // Every management call goes to logcat, without a secret (SPEC.md § 4.14).
        single<SourceConnector> { JellyfinConnector(get(), log = SourceLog { Log.i(LOG_TAG, it) }) }
        single<ServerDiscovery> { JellyfinDiscovery() }
    }

/** The request server behind `:core:source` (Seerr, J5), optional. */
val requestsModule =
    module {
        single<RequestConnector> { SeerrConnector() }
    }

/** The tag of the lines of the app in logcat. */
const val LOG_TAG = "CineDoury"

/** Whether this is a debug build: the catalogue is offered in the settings. */
val Application.isDebuggable: Boolean
    get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
