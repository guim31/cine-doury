package fr.guim.cinedoury.feature.manage

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.guim.cinedoury.core.model.IdentifyMatch
import fr.guim.cinedoury.core.model.IdentifySearch
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.SearchTitle
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.Events
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * « Corriger l'identification »: the search, pre-filled with the title cleaned of what the name of
 * its folder carries, and its results.
 *
 * @param results null while searched.
 * @param provider the provider searched; null for every provider of the server.
 */
@Immutable
data class IdentifyState(
    val item: MediaItem,
    val title: String,
    val year: String,
    val providers: List<String> = emptyList(),
    val provider: String? = null,
    val replaceImages: Boolean = true,
    val results: List<IdentifyMatch>? = null,
    val applying: IdentifyMatch? = null,
    val error: SourceException? = null,
)

interface IdentifyActions {
    fun setTitle(title: String)

    fun setYear(year: String)

    fun selectProvider(provider: String)

    fun toggleReplaceImages()

    fun search()

    fun apply(match: IdentifyMatch)

    fun dismissError()
}

class IdentifyViewModel(
    item: MediaItem,
    private val manager: ItemManager,
) : ViewModel(),
    IdentifyActions {
    private val local =
        SearchTitle.of(item.title, item.year).let {
            MutableStateFlow(IdentifyState(item, it.title, it.year?.toString().orEmpty()))
        }
    val state: StateFlow<IdentifyState> = local.asStateFlow()

    /** The identity was applied: the screen closes. */
    val done = Events<Unit>()

    private var searching: Job? = null

    init {
        viewModelScope.launch {
            val providers = guarded { manager.providers(item) } ?: return@launch
            local.update { it.copy(providers = providers, provider = it.provider ?: providers.firstOrNull()) }
            search()
        }
    }

    private suspend fun <T> guarded(block: suspend () -> T): T? =
        try {
            block()
        } catch (error: SourceException) {
            local.update { it.copy(error = error, applying = null, results = it.results ?: emptyList()) }
            null
        }

    override fun setTitle(title: String) = local.update { it.copy(title = title) }

    override fun setYear(year: String) = local.update { it.copy(year = year.filter(Char::isDigit).take(YEAR_DIGITS)) }

    override fun selectProvider(provider: String) {
        if (provider == local.value.provider) return
        local.update { it.copy(provider = provider) }
        search()
    }

    override fun toggleReplaceImages() = local.update { it.copy(replaceImages = !it.replaceImages) }

    override fun search() {
        val state = local.value
        if (state.title.isBlank()) return
        searching?.cancel()
        local.update { it.copy(results = null, error = null) }
        searching =
            viewModelScope.launch {
                val search =
                    IdentifySearch(state.item.kind, state.title.trim(), state.year.toIntOrNull(), state.provider)
                val results = guarded { manager.search(state.item, search) } ?: return@launch
                local.update { it.copy(results = results) }
            }
    }

    override fun apply(match: IdentifyMatch) {
        val state = local.value
        if (state.applying != null) return
        local.update { it.copy(applying = match) }
        viewModelScope.launch {
            guarded { manager.identify(state.item.id, match, state.replaceImages) } ?: return@launch
            done.send(Unit)
        }
    }

    override fun dismissError() = local.update { it.copy(error = null) }

    private companion object {
        const val YEAR_DIGITS = 4
    }
}
