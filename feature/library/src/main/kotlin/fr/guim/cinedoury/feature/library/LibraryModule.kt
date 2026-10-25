package fr.guim.cinedoury.feature.library

import fr.guim.cinedoury.core.model.LibraryId
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val libraryModule =
    module {
        viewModel { (id: LibraryId) -> LibraryViewModel(id, get(), get(), get(), get(), get()) }
    }
