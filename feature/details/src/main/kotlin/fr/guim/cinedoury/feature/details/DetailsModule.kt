package fr.guim.cinedoury.feature.details

import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.MediaItem
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val detailsModule =
    module {
        viewModel { params ->
            DetailsViewModel(
                initial = params.get<MediaItem>(),
                sessions = get(),
                index = get(),
                updates = get(),
                manager = get(),
                manageUpdates = get(),
                season = params.values.getOrNull(1) as ItemId?,
            )
        }
    }
