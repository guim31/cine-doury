package fr.guim.cinedoury.feature.manage

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.guim.cinedoury.core.model.ImageType
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.RemoteImage
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.Events
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * « Changer l'affiche, le fond ou le logo »: the pictures the providers offer for one [type].
 *
 * @param images null while read.
 * @param provider null for every provider.
 * @param allLanguages also the pictures in other languages than the item's (and without text).
 */
@Immutable
data class ImagesState(
    val item: MediaItem,
    val type: ImageType = ImageType.Primary,
    val provider: String? = null,
    val allLanguages: Boolean = false,
    val providers: List<String> = emptyList(),
    val images: List<RemoteImage>? = null,
    val applying: RemoteImage? = null,
    val error: SourceException? = null,
)

interface ImagesActions {
    fun selectType(type: ImageType)

    fun selectProvider(provider: String?)

    fun toggleAllLanguages()

    fun apply(image: RemoteImage)

    fun dismissError()
}

class ImagesViewModel(
    item: MediaItem,
    private val manager: ItemManager,
) : ViewModel(),
    ImagesActions {
    private val local = MutableStateFlow(ImagesState(item))
    val state: StateFlow<ImagesState> = local.asStateFlow()

    /** The picture was set: the screen closes, the item shows it. */
    val done = Events<Unit>()

    private var reading: Job? = null

    init {
        load()
    }

    private fun load() {
        val state = local.value
        reading?.cancel()
        local.update { it.copy(images = null, error = null) }
        reading =
            viewModelScope.launch {
                try {
                    val result = manager.images(state.item.id, state.type, state.provider, state.allLanguages)
                    local.update {
                        it.copy(
                            images = result.images,
                            providers = result.providers.ifEmpty { it.providers },
                        )
                    }
                } catch (error: SourceException) {
                    local.update { it.copy(images = emptyList(), error = error) }
                }
            }
    }

    override fun selectType(type: ImageType) {
        if (type == local.value.type) return
        local.update { it.copy(type = type) }
        load()
    }

    override fun selectProvider(provider: String?) {
        if (provider == local.value.provider) return
        local.update { it.copy(provider = provider) }
        load()
    }

    override fun toggleAllLanguages() {
        local.update { it.copy(allLanguages = !it.allLanguages) }
        load()
    }

    override fun apply(image: RemoteImage) {
        val state = local.value
        if (state.applying != null) return
        local.update { it.copy(applying = image) }
        viewModelScope.launch {
            try {
                manager.setImage(state.item.id, image.type, image.url)
                done.send(Unit)
            } catch (error: SourceException) {
                local.update { it.copy(applying = null, error = error) }
            }
        }
    }

    override fun dismissError() = local.update { it.copy(error = null) }

    companion object {
        /** The types of picture offered, in the order of their tabs. */
        val TYPES = listOf(ImageType.Primary, ImageType.Backdrop, ImageType.Logo)
    }
}
