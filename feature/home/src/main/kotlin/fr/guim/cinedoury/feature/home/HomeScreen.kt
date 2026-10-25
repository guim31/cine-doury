package fr.guim.cinedoury.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.designsystem.component.Ambience
import fr.guim.cinedoury.core.designsystem.component.AmbientBackdrop
import fr.guim.cinedoury.core.designsystem.component.CineFocus
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.NavigationRail
import fr.guim.cinedoury.core.designsystem.component.RailDestination
import fr.guim.cinedoury.core.designsystem.component.ThumbnailCard
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.image.ImageSource
import fr.guim.cinedoury.core.designsystem.image.RemoteImage
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.model.ImageRef
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.RemoteIndicator
import fr.guim.cinedoury.core.ui.SourceErrorPanel
import fr.guim.cinedoury.core.ui.imageSource
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun HomeRoute(
    onSettings: () -> Unit,
    onProfiles: () -> Unit,
    onSessionEnded: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
    sessions: SessionManager = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.sessionEnded) { onSessionEnded() }
    HomeScreen(
        state = state,
        imageSource = sessions::imageSource,
        onSettings = onSettings,
        onProfiles = onProfiles,
        onRetry = viewModel::load,
    )
}

/** Index of the destinations of the rail: home, then the libraries, then profile and settings. */
private const val HOME = 0

/** Tag of the first library tile, for tests and screenshots. */
const val PREVIEW_LIBRARY = "library-0"

/**
 * The provisional home (J1): the side rail and the libraries of the server as tiles with their
 * image, over the ambient backdrop of the focused tile. J2 brings the real home.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    imageSource: (ImageRef) -> ImageSource,
    onSettings: () -> Unit,
    onProfiles: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val libraries = state.libraries.orEmpty()
    val focuses = libraries.map { rememberCineFocus("library-${libraries.indexOf(it)}") }
    val focused = libraries.indices.firstOrNull { focuses[it].isFocused }
    val ambience = libraries.getOrNull(focused ?: 0)?.let { Ambience.of(it.name, it.image?.blurHash) }
    Box(modifier = modifier.fillMaxSize()) {
        AmbientBackdrop(ambience = ambience)
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = CineSizes.railCollapsed + CineSpacing.m,
                        top = CineSpacing.safeVertical + CineSpacing.m,
                        end = CineSpacing.safeHorizontal,
                    ),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_greeting, state.profileName),
                    style = CineTheme.typography.title1,
                    color = CineTheme.colors.text,
                    modifier = Modifier.weight(1f),
                )
                if (state.remote) RemoteIndicator()
            }
            Text(
                text = stringResource(R.string.home_libraries),
                style = CineTheme.typography.title2,
                color = CineTheme.colors.text,
                modifier = Modifier.padding(top = CineSpacing.xxl, bottom = CineSpacing.rowTitleGap),
            )
            val error = state.error
            when {
                error != null -> SourceErrorPanel(error = error, onRetry = onRetry)
                state.libraries == null -> LibrarySkeleton()
                else -> LibraryRow(libraries, focuses, imageSource)
            }
        }
        NavigationRail(
            destinations = railDestinations(libraries),
            bottomDestinations =
                listOf(
                    RailDestination(CineIcons.Profile, stringResource(R.string.rail_profiles)),
                    RailDestination(CineIcons.Settings, stringResource(R.string.rail_settings)),
                ),
            selectedIndex = HOME,
            onSelect = { index ->
                when (index - libraries.size - 1) {
                    0 -> onProfiles()

                    1 -> onSettings()

                    // Home is here; the libraries open with J2.
                    else -> Unit
                }
            },
            modifier = Modifier.testTag("rail"),
        )
    }
}

@Composable
private fun railDestinations(libraries: List<Library>): List<RailDestination> =
    listOf(RailDestination(CineIcons.Home, stringResource(R.string.rail_home))) +
        libraries.map { library ->
            RailDestination(if (library.kind == LibraryKind.Shows) CineIcons.Shows else CineIcons.Movies, library.name)
        }

@Composable
private fun LibraryRow(
    libraries: List<Library>,
    focuses: List<CineFocus>,
    imageSource: (ImageRef) -> ImageSource,
) {
    val first = remember { FocusRequester() }
    LazyRow(
        modifier = Modifier.testTag("libraries"),
        contentPadding = PaddingValues(vertical = CineSpacing.m, horizontal = CineSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
    ) {
        itemsIndexed(libraries, key = { _, library -> library.id.value }) { index, library ->
            val image = library.image
            ThumbnailCard(
                title = library.name,
                // « Films » under a library named « Films » says nothing.
                subtitle =
                    stringResource(
                        library.kind.label(),
                    ).takeUnless { it.equals(library.name, ignoreCase = true) },
                onClick = {},
                focus = focuses[index],
                focusRequester = if (index == 0) first else null,
                modifier = Modifier.testTag("library-$index"),
                image =
                    image?.let {
                        {
                            RemoteImage(
                                imageSource(it),
                                it.blurHash,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    },
            )
        }
    }
    InitialFocus(first, key = libraries.size)
}

private fun LibraryKind.label(): Int =
    when (this) {
        LibraryKind.Movies -> R.string.kind_movies
        LibraryKind.Shows -> R.string.kind_shows
        LibraryKind.Collections -> R.string.kind_collections
        LibraryKind.HomeVideos -> R.string.kind_home_videos
        LibraryKind.Mixed, LibraryKind.Other -> R.string.kind_mixed
    }

/** Skeletons of the exact shape of the tiles, never a spinner (`UI.md` § 1). */
@Composable
private fun LibrarySkeleton() {
    Row(
        modifier = Modifier.padding(vertical = CineSpacing.m, horizontal = CineSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
    ) {
        repeat(SKELETON_TILES) {
            Box(
                Modifier
                    .size(CineSizes.thumbnail)
                    .background(CineTheme.colors.surface1, RoundedCornerShape(CineSizes.cardCorner)),
            )
        }
    }
}

private const val SKELETON_TILES = 3
