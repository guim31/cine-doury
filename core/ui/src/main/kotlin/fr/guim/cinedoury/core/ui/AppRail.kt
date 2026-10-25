package fr.guim.cinedoury.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.NavigationRail
import fr.guim.cinedoury.core.designsystem.component.ProfileAvatar
import fr.guim.cinedoury.core.designsystem.component.RailDestination
import fr.guim.cinedoury.core.designsystem.image.ImageSource
import fr.guim.cinedoury.core.designsystem.image.RemoteImage
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryKind

/** Where the rail leads (`UI.md` § 2, `FONCTIONNALITES.md` § 3). */
sealed interface RailTarget {
    data object Search : RailTarget

    data object Home : RailTarget

    data class Library(
        val id: LibraryId,
    ) : RailTarget

    data object Profiles : RailTarget

    data object Settings : RailTarget
}

/** What the rail shows: the libraries in the user's order and the open profile. */
@Immutable
data class RailState(
    val libraries: List<Library> = emptyList(),
    val profileName: String = "",
    val avatar: ImageSource? = null,
)

/**
 * The rail of the navigation screens: Recherche, Accueil, one entry per library, then the profile
 * (with its picture) and Réglages at the bottom. It opens over the screen when it takes the focus.
 *
 * @param focusRequester attached to the [selected] entry, for the screen to open the rail.
 * @param previewFocused draws that entry focused and the rail open, for screenshots.
 */
@Composable
fun AppRail(
    state: RailState,
    selected: RailTarget,
    onNavigate: (RailTarget) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    previewFocused: RailTarget? = null,
) {
    val targets =
        remember(state.libraries) {
            listOf(RailTarget.Search, RailTarget.Home) + state.libraries.map { RailTarget.Library(it.id) }
        }
    val bottom = listOf(RailTarget.Profiles, RailTarget.Settings)
    val all = targets + bottom
    val avatar = state.avatar
    NavigationRail(
        destinations =
            listOf(
                RailDestination(CineIcons.Search, stringResource(R.string.rail_search)),
                RailDestination(CineIcons.Home, stringResource(R.string.rail_home)),
            ) + state.libraries.map { RailDestination(it.kind.icon, it.name) },
        bottomDestinations =
            listOf(
                RailDestination(
                    CineIcons.Profile,
                    // The name of the profile: short enough for the rail, and it says who is watching.
                    state.profileName.ifEmpty { stringResource(R.string.rail_profiles) },
                    avatar = {
                        ProfileAvatar(
                            name = state.profileName,
                            size = CineSizes.icon,
                            image = avatar?.let { source -> { RemoteImage(source, null, contentDescription = null) } },
                        )
                    },
                ),
                RailDestination(CineIcons.Settings, stringResource(R.string.rail_settings)),
            ),
        selectedIndex = all.indexOf(selected),
        onSelect = { index -> all.getOrNull(index)?.let(onNavigate) },
        focusRequester = focusRequester,
        previewFocusedIndex = previewFocused?.let { all.indexOf(it) }?.takeIf { it >= 0 },
        modifier = modifier.testTag("rail"),
    )
}

private val LibraryKind.icon
    get() =
        when (this) {
            LibraryKind.Shows -> CineIcons.Shows
            LibraryKind.Collections -> CineIcons.Collections
            LibraryKind.Movies, LibraryKind.HomeVideos, LibraryKind.Mixed, LibraryKind.Other -> CineIcons.Movies
        }
