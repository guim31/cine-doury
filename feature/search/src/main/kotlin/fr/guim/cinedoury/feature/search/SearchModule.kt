package fr.guim.cinedoury.feature.search

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** `:feature:search` for Koin. The app provides the `RequestConnector` (Seerr). */
val searchModule =
    module {
        viewModel { SearchViewModel(get(), get(), get(), get(), get(), get()) }
        viewModel { SeerrSettingsViewModel(get(), get(), get()) }
    }
