package fr.guim.cinedoury.feature.manage

import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.MediaItem
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val manageModule =
    module {
        single { ItemManager(get(), get()) }
        viewModel { (item: MediaItem) -> IdentifyViewModel(item, get()) }
        viewModel { (item: MediaItem) -> ImagesViewModel(item, get()) }
        viewModel { (library: LibraryId) -> ProblemsViewModel(library, get(), get()) }
    }
