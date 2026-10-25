package fr.guim.cinedoury.feature.player

import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.playback.DeviceCapabilitiesReader
import fr.guim.cinedoury.core.playback.PlaybackScope
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val playerModule =
    module {
        viewModel { (request: PlaybackRequest) ->
            val reader = get<DeviceCapabilitiesReader>()
            PlayerViewModel(
                request = request,
                sessions = get(),
                settingsStore = get(),
                readCapabilities = reader::read,
                players = get(),
                home = get(),
                updates = get(),
                reportScope = get<PlaybackScope>().scope,
            )
        }
        viewModel { PlaybackSettingsViewModel(get(), get()) }
    }
