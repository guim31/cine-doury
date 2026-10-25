package fr.guim.cinedoury.tv.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.component.CineFocus
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.LocalPreviewFocus
import fr.guim.cinedoury.core.designsystem.component.NavigationRail
import fr.guim.cinedoury.core.designsystem.component.RailDestination
import fr.guim.cinedoury.core.designsystem.component.RoundIconButton
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.tv.R

/** Index of the catalogue sections in the list, for tests and screenshots. */
object CatalogSection {
    const val HEADER = 0
    const val COLORS = 1
    const val TYPOGRAPHY = 2
    const val POSTERS = 3
    const val THUMBNAILS = 4
    const val BUTTONS = 5
    const val RAIL = 6
    const val SCRIMS = 7
    const val SPACING = 8
    const val AMBIENT = 9
}

/**
 * The design system catalogue: every token and base component of `UI.md`, navigable with the
 * remote. The app opens on it until the home screen exists (J2).
 */
@Composable
fun CatalogScreen(
    variant: ThemeVariant,
    onToggleVariant: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    requestInitialFocus: Boolean = true,
    previewFocus: String? = null,
) {
    val colors = CineTheme.colors
    var selected by remember { mutableIntStateOf(1) }
    val firstFocus = remember { FocusRequester() }
    CompositionLocalProvider(LocalPreviewFocus provides previewFocus) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(colors.background),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = CineSizes.railCollapsed + CineSpacing.m,
                        end = CineSpacing.safeHorizontal,
                        top = CineSpacing.safeVertical + CineSpacing.m,
                        bottom = CineSpacing.safeVertical + CineSpacing.xxl,
                    ),
                verticalArrangement = Arrangement.spacedBy(CineSpacing.xxl),
            ) {
                item(key = "header") { CatalogHeader(variant, onToggleVariant, firstFocus) }
                item(key = "colors") { ColorsSection() }
                item(key = "typography") { TypographySection() }
                item(key = "posters") { PostersSection() }
                item(key = "thumbnails") { ThumbnailsSection() }
                item(key = "buttons") { ButtonsSection() }
                item(key = "rail") { RailSection() }
                item(key = "scrims") { ScrimsSection() }
                item(key = "spacing") { SpacingSection() }
                item(key = "ambient") { AmbientSection() }
            }
            NavigationRail(
                destinations = railDestinations(),
                bottomDestinations =
                    listOf(
                        RailDestination(CineIcons.Settings, stringResource(R.string.rail_settings)),
                    ),
                selectedIndex = selected,
                onSelect = { selected = it },
                modifier = Modifier.testTag("rail"),
                previewFocusedIndex = RAIL_MOVIES.takeIf { previewFocus == PREVIEW_RAIL },
            )
        }
    }
    if (requestInitialFocus) {
        LaunchedEffect(Unit) {
            // The header lives in a lazy list, composed during the first layout: wait for it.
            withFrameNanos { }
            firstFocus.requestFocus()
        }
    }
}

/** Tag that draws the side rail open, its « Films » destination focused. */
const val PREVIEW_RAIL = "rail"
private const val RAIL_MOVIES = 2

@Composable
internal fun focusFor(tag: String): CineFocus = rememberCineFocus(tag)

@Composable
internal fun railDestinations() =
    listOf(
        RailDestination(CineIcons.Search, stringResource(R.string.rail_search)),
        RailDestination(CineIcons.Home, stringResource(R.string.rail_home)),
        RailDestination(CineIcons.Movies, stringResource(R.string.rail_movies)),
        RailDestination(CineIcons.Shows, stringResource(R.string.rail_shows)),
    )

@Composable
private fun CatalogHeader(
    variant: ThemeVariant,
    onToggleVariant: () -> Unit,
    toggleFocus: FocusRequester,
) {
    val colors = CineTheme.colors
    val typography = CineTheme.typography
    val dark = variant == ThemeVariant.Dark
    val current = stringResource(if (dark) R.string.theme_dark else R.string.theme_oled)
    val switch = stringResource(if (dark) R.string.theme_switch_to_oled else R.string.theme_switch_to_dark)
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.catalog_title), style = typography.title1, color = colors.text)
            Text(
                stringResource(R.string.catalog_subtitle),
                style = typography.body,
                color = colors.text2,
                modifier = Modifier.padding(top = CineSpacing.xs),
            )
        }
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
        ) {
            Text(
                text = current,
                style = typography.caption,
                color = colors.text2,
                modifier = Modifier.padding(top = CineSpacing.s),
            )
            RoundIconButton(
                icon = CineIcons.Settings,
                label = switch,
                onClick = onToggleVariant,
                focusRequester = toggleFocus,
                focus = focusFor("theme-toggle"),
                modifier = Modifier.testTag("theme-toggle"),
            )
        }
    }
}
