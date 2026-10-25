package fr.guim.cinedoury.feature.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.CineTab
import fr.guim.cinedoury.core.designsystem.component.FocusPivot
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.PosterCard
import fr.guim.cinedoury.core.designsystem.component.SecondaryButton
import fr.guim.cinedoury.core.designsystem.component.SkeletonCard
import fr.guim.cinedoury.core.designsystem.component.ThumbnailCard
import fr.guim.cinedoury.core.designsystem.component.previewFirstVisible
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.image.ImageSource
import fr.guim.cinedoury.core.designsystem.image.RemoteImage
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.model.ImageType
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.SEPARATOR
import fr.guim.cinedoury.core.ui.cardTitle
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import fr.guim.cinedoury.core.model.RemoteImage as ProviderImage

@Composable
fun ImagesRoute(
    item: MediaItem,
    onDone: () -> Unit,
    viewModel: ImagesViewModel = koinViewModel(key = "images-${item.id.value}") { parametersOf(item) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.done) { onDone() }
    ImagesScreen(state, viewModel)
}

/** Preview tags of « Changer les images ». */
object ImagesPreview {
    const val LANGUAGES = "images-languages"

    fun type(type: ImageType) = "images-type-${type.name}"

    fun provider(name: String?) = "images-provider-${name ?: "all"}"

    fun image(index: Int) = "$IMAGE$index"

    internal const val IMAGE = "images-image-"
}

/**
 * « Changer l'affiche, le fond ou le logo » (`UI.md` § 8, « Gestion »): a gallery of the pictures
 * the providers offer, by type, provider and language, each with its resolution. OK on a picture
 * makes it the item's; the screen closes and the item shows it at once.
 */
@Composable
fun ImagesScreen(
    state: ImagesState,
    actions: ImagesActions,
    modifier: Modifier = Modifier,
) {
    val colors = CineTheme.colors
    val first = remember { FocusRequester() }
    val tab = remember { FocusRequester() }
    Box(modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier =
                Modifier.padding(
                    start = CineSpacing.safeHorizontal,
                    end = CineSpacing.safeHorizontal,
                    top = CineSpacing.safeVertical + CineSpacing.s,
                ),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
            ) {
                Text(stringResource(R.string.images_title), style = CineTheme.typography.title1, color = colors.text)
                Text(state.item.cardTitle, style = CineTheme.typography.title2, color = colors.text2, maxLines = 1)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ImagesViewModel.TYPES.forEach { type ->
                    CineTab(
                        label = stringResource(type.label()),
                        selected = type == state.type,
                        onClick = { actions.selectType(type) },
                        focusRequester = if (type == state.type) tab else null,
                        focus = rememberCineFocus(ImagesPreview.type(type)),
                        modifier = Modifier.testTag(ImagesPreview.type(type)),
                    )
                }
                Spacer(Modifier.widthIn(min = CineSpacing.l))
                (listOf<String?>(null) + state.providers).forEach { provider ->
                    CineTab(
                        label = provider?.let(::providerLabel) ?: stringResource(R.string.images_all_providers),
                        selected = provider == state.provider,
                        onClick = { actions.selectProvider(provider) },
                        focus = rememberCineFocus(ImagesPreview.provider(provider)),
                        modifier = Modifier.testTag(ImagesPreview.provider(provider)),
                    )
                }
                SecondaryButton(
                    text = stringResource(R.string.images_all_languages),
                    icon = if (state.allLanguages) CineIcons.Checked else CineIcons.Unchecked,
                    onClick = actions::toggleAllLanguages,
                    focus = rememberCineFocus(ImagesPreview.LANGUAGES),
                    modifier = Modifier.testTag(ImagesPreview.LANGUAGES),
                )
            }
            Gallery(state, actions, first)
        }
        state.error?.let { ManageErrorDialog(it, onDismiss = actions::dismissError) }
    }
    val images = state.images
    InitialFocus(first, key = images, enabled = !images.isNullOrEmpty() && state.error == null)
    InitialFocus(tab, key = state.type, enabled = images?.isEmpty() == true && state.error == null)
}

private fun ImageType.label(): Int =
    when (this) {
        ImageType.Backdrop -> R.string.images_backdrop
        ImageType.Logo -> R.string.images_logo
        else -> R.string.images_primary
    }

/** The size of a cell of the gallery: a poster, or a 16:9 thumbnail for a backdrop or a logo. */
private val ImageType.cell: DpSize get() =
    if (this ==
        ImageType.Primary
    ) {
        CineSizes.posterNormal
    } else {
        CineSizes.thumbnail
    }

@Composable
private fun Gallery(
    state: ImagesState,
    actions: ImagesActions,
    first: FocusRequester,
) {
    val images = state.images
    val cell = state.type.cell
    if (images?.isEmpty() == true) {
        Column(
            modifier = Modifier.widthIn(max = CineSizes.formWidth).padding(top = CineSpacing.l),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
        ) {
            Text(
                stringResource(R.string.images_empty_title),
                style = CineTheme.typography.title2,
                color = CineTheme.colors.text,
            )
            Text(
                stringResource(R.string.images_empty_message),
                style = CineTheme.typography.body,
                color = CineTheme.colors.text2,
            )
        }
        return
    }
    FocusPivot(fraction = GALLERY_PIVOT) {
        LazyVerticalGrid(
            columns = GridCells.FixedSize(cell.width),
            state = rememberLazyGridState(initialFirstVisibleItemIndex = previewFirstVisible(ImagesPreview.IMAGE)),
            contentPadding = PaddingValues(top = CineSpacing.s, bottom = CineSpacing.xxl, start = CineSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.m),
            modifier = Modifier.testTag("images-gallery"),
        ) {
            if (images == null) {
                items(SKELETONS) { SkeletonCard(cell) }
            } else {
                itemsIndexed(images, key = { index, image -> "$index-${image.url}" }) { index, image ->
                    ImageCard(
                        image,
                        index,
                        onClick = { actions.apply(image) },
                        focusRequester =
                            if (index ==
                                0
                            ) {
                                first
                            } else {
                                null
                            },
                    )
                }
            }
        }
    }
}

private const val SKELETONS = 12
private const val GALLERY_PIVOT = 0.2f

@Composable
private fun ImageCard(
    image: ProviderImage,
    index: Int,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
) {
    val source = remember(image.url) { ImageSource { _, _ -> image.thumbnailUrl ?: image.url } }
    val resolution =
        if (image.width != null && image.height != null) {
            stringResource(R.string.images_resolution, image.width ?: 0, image.height ?: 0)
        } else {
            null
        }
    val details =
        listOfNotNull(
            image.language?.uppercase() ?: stringResource(R.string.images_language_none),
            image.provider?.let(::providerLabel),
        ).joinToString(SEPARATOR)
    val fit = if (image.type == ImageType.Logo) ContentScale.Fit else ContentScale.Crop
    val art: @Composable () -> Unit = {
        RemoteImage(
            source,
            null,
            contentDescription = null,
            Modifier.fillMaxSize(),
            fit,
        )
    }
    val focus = rememberCineFocus(ImagesPreview.image(index))
    val tag = Modifier.testTag(ImagesPreview.image(index))
    if (image.type == ImageType.Primary) {
        PosterCard(
            title = resolution ?: details,
            subtitle = details.takeIf { resolution != null },
            alwaysShowTitle = true,
            onClick = onClick,
            focusRequester = focusRequester,
            focus = focus,
            image = art,
            modifier = tag,
        )
    } else {
        ThumbnailCard(
            title = resolution ?: details,
            subtitle = details.takeIf { resolution != null },
            onClick = onClick,
            focusRequester = focusRequester,
            focus = focus,
            image = art,
            modifier = tag,
        )
    }
}
