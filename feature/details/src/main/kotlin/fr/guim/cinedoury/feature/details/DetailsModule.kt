package fr.guim.cinedoury.feature.details

import fr.guim.cinedoury.core.model.MediaItem
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val detailsModule =
    module {
        viewModel { (item: MediaItem) -> DetailsViewModel(item, get(), get()) }
    }
