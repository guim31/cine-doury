package fr.guim.cinedoury.core.playback

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import fr.guim.cinedoury.core.model.DeviceCapabilities
import fr.guim.cinedoury.core.model.PlayerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Makes the player of a playback: ExoPlayer on the device, a fake in the tests of the screens. */
fun interface VideoPlayerFactory {
    fun create(
        capabilities: DeviceCapabilities,
        settings: PlayerSettings,
    ): VideoPlayer
}

/**
 * Where the reports of the player run: it outlives the screen of the player, for « stopped » to
 * reach the server after the screen is gone.
 */
class PlaybackScope(
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
)

/** `:core:playback` for Koin. */
val playbackModule =
    module {
        single { DeviceCapabilitiesReader(androidContext()) }
        single { PlaybackScope() }
        single {
            PlayerSettingsStore(
                PreferenceDataStoreFactory.create {
                    androidContext().preferencesDataStoreFile(
                        PlayerSettingsStore.FILE,
                    )
                },
            )
        }
        single<VideoPlayerFactory> {
            val context = androidContext()
            VideoPlayerFactory { capabilities, settings ->
                ExoVideoPlayer(context, capabilities, settings.passthrough, settings.matchFrameRate)
            }
        }
    }
