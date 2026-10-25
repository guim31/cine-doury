package fr.guim.cinedoury.feature.auth

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The view models of the sign-in path, for Koin. */
val authModule =
    module {
        viewModel { ServerSearchViewModel(get(), get()) }
        viewModel { params -> AddressViewModel(get(), params.get()) }
        viewModel { QuickConnectViewModel(get()) }
        viewModel { params -> PasswordViewModel(get(), params.get<PasswordArgs>().username) }
        viewModel { ProfilesViewModel(get()) }
        viewModel { params ->
            val args = params.get<PinArgs>()
            PinViewModel(get(), args.profile, args.purpose)
        }
    }
