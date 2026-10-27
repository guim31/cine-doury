package fr.guim.cinedoury.feature.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.CineTab
import fr.guim.cinedoury.core.designsystem.component.CineTextField
import fr.guim.cinedoury.core.designsystem.component.FocusPivot
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.PosterCard
import fr.guim.cinedoury.core.designsystem.component.PrimaryButton
import fr.guim.cinedoury.core.designsystem.component.SecondaryButton
import fr.guim.cinedoury.core.designsystem.component.SkeletonCard
import fr.guim.cinedoury.core.designsystem.component.asHeading
import fr.guim.cinedoury.core.designsystem.component.previewFirstVisible
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.image.ImageSource
import fr.guim.cinedoury.core.designsystem.image.RemoteImage
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.model.IdentifyMatch
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.SEPARATOR
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun IdentifyRoute(
    item: MediaItem,
    onDone: () -> Unit,
    viewModel: IdentifyViewModel = koinViewModel(key = "identify-${item.id.value}") { parametersOf(item) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.done) { onDone() }
    IdentifyScreen(state, viewModel)
}

/** Preview tags of « Corriger l'identification ». */
object IdentifyPreview {
    const val TITLE = "identify-title"
    const val YEAR = "identify-year"
    const val SEARCH = "identify-search"
    const val IMAGES = "identify-images"

    fun result(index: Int) = "$RESULT$index"

    fun provider(name: String) = "identify-provider-$name"

    internal const val RESULT = "identify-result-"
}

/**
 * « Corriger l'identification » (`UI.md` § 8, « Gestion »): the title and year of the search,
 * pre-filled from the name on the server and editable, the provider, then the results as a gallery
 * of posters with their year; under it, the summary of the focused one. OK on a result applies it.
 */
@Composable
fun IdentifyScreen(
    state: IdentifyState,
    actions: IdentifyActions,
    modifier: Modifier = Modifier,
) {
    val colors = CineTheme.colors
    val typography = CineTheme.typography
    var focused by rememberSaveable { mutableIntStateOf(0) }
    val firstResult = remember { FocusRequester() }
    val titleField = remember { FocusRequester() }
    Box(modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal = CineSpacing.safeHorizontal,
                    vertical = CineSpacing.safeVertical + CineSpacing.s,
                ),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
        ) {
            Text(
                stringResource(R.string.identify_title),
                style = typography.title1,
                color = colors.text,
                modifier = Modifier.asHeading(),
            )
            Text(
                text =
                    stringResource(
                        R.string.identify_current,
                        listOfNotNull(state.item.title, state.item.path).joinToString(SEPARATOR),
                    ),
                style = typography.caption,
                color = colors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
                verticalAlignment = Alignment.Bottom,
            ) {
                CineTextField(
                    value = state.title,
                    onValueChange = actions::setTitle,
                    label = stringResource(R.string.identify_field_title),
                    imeAction = ImeAction.Search,
                    onImeAction = actions::search,
                    focusRequester = titleField,
                    focus = rememberCineFocus(IdentifyPreview.TITLE),
                    modifier = Modifier.leavesWithArrows().testTag(IdentifyPreview.TITLE),
                )
                CineTextField(
                    value = state.year,
                    onValueChange = actions::setYear,
                    label = stringResource(R.string.identify_field_year),
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Search,
                    onImeAction = actions::search,
                    width = CineSizes.yearFieldWidth,
                    focus = rememberCineFocus(IdentifyPreview.YEAR),
                    modifier = Modifier.leavesWithArrows().testTag(IdentifyPreview.YEAR),
                )
                PrimaryButton(
                    text = stringResource(R.string.identify_search),
                    icon = CineIcons.Search,
                    onClick = actions::search,
                    focus = rememberCineFocus(IdentifyPreview.SEARCH),
                    modifier = Modifier.testTag(IdentifyPreview.SEARCH),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.providers.size > 1) {
                    state.providers.forEach { provider ->
                        CineTab(
                            label = providerLabel(provider),
                            selected = provider == state.provider,
                            onClick = { actions.selectProvider(provider) },
                            focus = rememberCineFocus(IdentifyPreview.provider(provider)),
                            modifier = Modifier.testTag(IdentifyPreview.provider(provider)),
                        )
                    }
                }
                SecondaryButton(
                    text = stringResource(R.string.identify_replace_images),
                    icon = if (state.replaceImages) CineIcons.Checked else CineIcons.Unchecked,
                    onClick = actions::toggleReplaceImages,
                    focus = rememberCineFocus(IdentifyPreview.IMAGES),
                    modifier = Modifier.testTag(IdentifyPreview.IMAGES),
                )
            }
            Results(state, actions, firstResult, onFocused = { focused = it })
            val shown = state.results?.getOrNull(focused)
            if (shown != null) {
                MatchSummary(shown)
            } else if (state.results?.isEmpty() == true) {
                NoResult()
            }
        }
        state.error?.let { ManageErrorDialog(it, onDismiss = actions::dismissError) }
    }
    // The first result once found, else the title to correct.
    val results = state.results
    InitialFocus(firstResult, key = results, enabled = !results.isNullOrEmpty() && state.error == null)
    InitialFocus(titleField, key = results?.isEmpty(), enabled = results?.isEmpty() == true && state.error == null)
}

/** The arrows up and down leave a text field: the remote never stays caught in it. */
@Composable
internal fun Modifier.leavesWithArrows(): Modifier {
    val focus = LocalFocusManager.current
    return onPreviewKeyEvent { event ->
        val direction =
            when (event.key) {
                Key.DirectionUp -> FocusDirection.Up
                Key.DirectionDown -> FocusDirection.Down
                else -> return@onPreviewKeyEvent false
            }
        if (event.type == KeyEventType.KeyDown) focus.moveFocus(direction)
        true
    }
}

/** « TheMovieDb » in the words of the user: « TMDb ». */
internal fun providerLabel(name: String): String =
    when (name) {
        "TheMovieDb" -> "TMDb"
        "TheTVDB" -> "TVDB"
        "The Open Movie Database" -> "OMDb"
        else -> name
    }

@Composable
private fun Results(
    state: IdentifyState,
    actions: IdentifyActions,
    first: FocusRequester,
    onFocused: (Int) -> Unit,
) {
    val results = state.results
    FocusPivot(fraction = 0f) {
        LazyRow(
            state = rememberLazyListState(initialFirstVisibleItemIndex = previewFirstVisible(IdentifyPreview.RESULT)),
            contentPadding = PaddingValues(vertical = CineSpacing.m, horizontal = CineSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
            modifier = Modifier.fillMaxWidth().testTag("identify-results"),
        ) {
            if (results == null) {
                items(SKELETONS) { SkeletonCard(CineSizes.posterNormal) }
            } else {
                itemsIndexed(results, key = { index, match -> "$index-${match.providerIds}" }) { index, match ->
                    val image = remember(match.imageUrl) { match.imageUrl?.let { url -> ImageSource { _, _ -> url } } }
                    PosterCard(
                        title = match.title,
                        subtitle = match.year?.toString(),
                        alwaysShowTitle = true,
                        onClick = { actions.apply(match) },
                        focusRequester = if (index == 0) first else null,
                        focus = rememberCineFocus(IdentifyPreview.result(index)),
                        image =
                            image?.let { source ->
                                @Composable {
                                    RemoteImage(
                                        source,
                                        null,
                                        contentDescription = null,
                                        Modifier.fillMaxSize(),
                                    )
                                }
                            },
                        modifier =
                            Modifier
                                .testTag(IdentifyPreview.result(index))
                                .onFocusChanged { if (it.hasFocus) onFocused(index) },
                    )
                }
            }
        }
    }
}

private const val SKELETONS = 6

/** The focused result: its title, year, provider and the beginning of its summary. */
@Composable
private fun MatchSummary(match: IdentifyMatch) {
    val colors = CineTheme.colors
    Column(
        modifier = Modifier.widthIn(max = CineSizes.heroTextWidth),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
    ) {
        Text(
            text =
                listOfNotNull(
                    match.title,
                    match.year?.toString(),
                    match.provider?.let(::providerLabel),
                ).joinToString(SEPARATOR),
            style = CineTheme.typography.title2,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        match.overview?.let {
            Text(
                it,
                style = CineTheme.typography.body,
                color = colors.text2,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(stringResource(R.string.identify_hint), style = CineTheme.typography.caption, color = colors.text3)
    }
}

@Composable
private fun NoResult() {
    val colors = CineTheme.colors
    Column(
        modifier = Modifier.widthIn(max = CineSizes.formWidth),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
    ) {
        Text(stringResource(R.string.identify_empty_title), style = CineTheme.typography.title2, color = colors.text)
        Text(stringResource(R.string.identify_empty_message), style = CineTheme.typography.body, color = colors.text2)
    }
}
