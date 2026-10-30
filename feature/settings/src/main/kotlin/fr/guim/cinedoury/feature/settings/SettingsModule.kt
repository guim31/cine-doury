package fr.guim.cinedoury.feature.settings

import fr.guim.cinedoury.core.data.DeviceIdentity
import fr.guim.cinedoury.core.playback.DeviceCapabilitiesReader
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule =
    module {
        viewModel {
            val reader = get<DeviceCapabilitiesReader>()
            SettingsViewModel(
                sessions = get(),
                home = get(),
                icons = get(),
                index = get(),
                scheduler = get(),
                preferences = get(),
                player = get(),
                readCapabilities = { reader.read() },
                tvName = get<DeviceIdentity>().deviceName,
                updates = getOrNull(),
            )
        }
        viewModel { ConnectDeviceViewModel(get()) }
        viewModel {
            val reader = get<DeviceCapabilitiesReader>()
            DiagnosticViewModel(get(), get(), get(), get(), AndroidDeviceFacts(androidContext())) { reader.read() }
        }
    }
