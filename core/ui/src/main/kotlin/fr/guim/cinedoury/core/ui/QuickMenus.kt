package fr.guim.cinedoury.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.data.quick.QuickActions
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.QuickMenu
import fr.guim.cinedoury.core.designsystem.component.QuickMenuLine
import fr.guim.cinedoury.core.designsystem.component.onLongPress
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemImages
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.model.QuickOption
import fr.guim.cinedoury.core.model.QuickOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The options of the menu of a card that change the item (« vu », favourites), for every screen;
 * null in previews and screenshots, where they do nothing. `CineApp` gives the one of Koin.
 */
val LocalQuickActions = staticCompositionLocalOf<QuickActions?> { null }

/** Where the options of the menu of a card lead: the navigation of the screen. */
interface QuickMenuNavigation {
    fun open(item: MediaItem)

    fun play(request: PlaybackRequest)

    /** The details of the series of an episode, on the season [season] when it is given. */
    fun openSeries(
        series: MediaItem,
        season: ItemId? = null,
    ) = open(series)
}

/**
 * The menu of a card on a screen (essai réel 5): which card it is open for, and the card that gets
 * the focus back when it closes. One per screen, [rememberQuickMenu].
 */
@Stable
class QuickMenuState internal constructor(
    private val scope: CoroutineScope,
) {
    /** The card the menu is open for, null when closed. */
    var item: MediaItem? by mutableStateOf(null)
        private set

    /** The card the menu came from, on the screen: it gets the focus back. */
    var anchor: Any? by mutableStateOf(null)
        private set

    /** The row of the home the card is in, when it is « Reprendre ». */
    internal var continueRow: String? = null
        private set

    internal val requester = FocusRequester()

    fun open(
        item: MediaItem,
        anchor: Any = item.id,
        continueRow: String? = null,
    ) {
        this.continueRow = continueRow
        this.anchor = anchor
        this.item = item
    }

    /** Closes the menu: the focus goes back to the card it came from. */
    fun close() {
        item = null
        val requester = requester
        scope.launch {
            // A frame for the menu to leave, then the card (the menu kept the focus until then).
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }
}

@Composable
fun rememberQuickMenu(): QuickMenuState {
    val scope = rememberCoroutineScope()
    return remember { QuickMenuState(scope) }
}

/**
 * OK held on this card opens [menu] for [item]; the card gets the focus back when it closes. Put it
 * on the modifier of a card of the design system (its container: the focus goes to the card inside).
 *
 * @param anchor tells the card apart when the same item is on the screen twice (two rows).
 * @param continueRow the row of the home, when the card is in « Reprendre ».
 */
@Composable
fun Modifier.quickMenu(
    menu: QuickMenuState,
    item: MediaItem,
    anchor: Any = item.id,
    continueRow: String? = null,
): Modifier =
    onLongPress { menu.open(item, anchor, continueRow) }
        .then(if (menu.anchor == anchor) Modifier.focusRequester(menu.requester) else Modifier)

/**
 * The menu of the screen, for the cards deep in its lists ([Modifier.quickMenu] without a state):
 * provided by the screen around them.
 */
val LocalQuickMenu = staticCompositionLocalOf<QuickMenuState?> { null }

/** OK held on this card opens the menu of the screen ([LocalQuickMenu]); nothing without one. */
@Composable
fun Modifier.quickMenu(
    item: MediaItem,
    anchor: Any = item.id,
): Modifier = LocalQuickMenu.current?.let { quickMenu(it, item, anchor) } ?: this

/**
 * The menu of [menu]'s card, over the screen, while it is open: its options for the item
 * (`QuickOptions`), each leading through [navigation], or changing the item through
 * [LocalQuickActions]. [onSelect]: « Sélectionner », in a library, for the profiles that manage.
 */
@Composable
fun QuickMenuHost(
    menu: QuickMenuState,
    navigation: QuickMenuNavigation,
    onSelect: ((MediaItem) -> Unit)? = null,
    previewFocusedIndex: Int? = null,
) {
    val shown = menu.item ?: return
    val actions = LocalQuickActions.current
    val item = actions?.current(shown) ?: shown
    val continueRow = menu.continueRow
    BackHandler { menu.close() }
    val options = QuickOptions.of(item, inContinue = continueRow != null, canSelect = onSelect != null)
    val labels = options.associateWith { option -> option.label() }
    QuickMenu(
        title = item.cardTitle,
        subtitle = item.cardSubtitle().takeIf { item.kind == ItemKind.Episode },
        lines =
            options.map { option ->
                QuickMenuLine(labels.getValue(option), option.icon, "quick-${option.name}") {
                    menu.close()
                    runQuickOption(option, item, navigation, actions, continueRow, onSelect)
                }
            },
        onDismiss = menu::close,
        previewFocusedIndex = previewFocusedIndex,
    )
}

/**
 * Does what [option] of the menu of [item] says (essai réel 5): plays, opens, or changes the item
 * through [actions]. The same on the TV and on the phone (M1), whatever draws the menu.
 */
fun runQuickOption(
    option: QuickOption,
    item: MediaItem,
    navigation: QuickMenuNavigation,
    actions: QuickActions?,
    continueRow: String?,
    onSelect: ((MediaItem) -> Unit)?,
) {
    when (option) {
        QuickOption.Resume -> navigation.play(PlaybackRequest(item, resume = true))
        QuickOption.Play, QuickOption.PlayFromStart -> navigation.play(PlaybackRequest(item, resume = false))
        QuickOption.OpenDetails -> navigation.open(item)
        QuickOption.GoToSeries -> item.seriesCard()?.let { navigation.openSeries(it) }
        QuickOption.GoToSeason -> item.seriesCard()?.let { navigation.openSeries(it, item.episode?.seasonId) }
        QuickOption.Select -> onSelect?.invoke(item)
        else -> actions?.apply(item, option, continueRow)
    }
}

/** The series of an episode, as a card: its details screen reads the rest. */
private fun MediaItem.seriesCard(): MediaItem? {
    val series = episode?.seriesId ?: return null
    return MediaItem(
        id = series,
        kind = ItemKind.Series,
        title = episode?.seriesTitle.orEmpty(),
        images =
            ItemImages(
                primary = images.seriesPrimary,
                backdrops = listOfNotNull(images.parentBackdrop),
                logo = images.parentLogo,
                thumb = images.parentThumb,
            ),
    )
}

@Composable
internal fun QuickOption.label(): String =
    stringResource(
        when (this) {
            QuickOption.Resume -> R.string.quick_resume
            QuickOption.Play -> R.string.quick_play
            QuickOption.PlayFromStart -> R.string.quick_play_from_start
            QuickOption.OpenDetails -> R.string.quick_open_details
            QuickOption.GoToSeries -> R.string.quick_go_to_series
            QuickOption.GoToSeason -> R.string.quick_go_to_season
            QuickOption.MarkWatched -> R.string.quick_mark_watched
            QuickOption.MarkUnwatched -> R.string.quick_mark_unwatched
            QuickOption.RemoveFromContinue -> R.string.quick_remove_from_continue
            QuickOption.AddFavorite -> R.string.quick_add_favorite
            QuickOption.RemoveFavorite -> R.string.quick_remove_favorite
            QuickOption.Select -> R.string.quick_select
        },
    )

internal val QuickOption.icon: ImageVector
    get() =
        when (this) {
            QuickOption.Resume, QuickOption.Play -> CineIcons.Play
            QuickOption.PlayFromStart -> CineIcons.Restart
            QuickOption.OpenDetails -> CineIcons.Info
            QuickOption.GoToSeries -> CineIcons.Shows
            QuickOption.GoToSeason -> CineIcons.ListView
            QuickOption.MarkWatched, QuickOption.MarkUnwatched -> CineIcons.Watched
            QuickOption.RemoveFromContinue -> CineIcons.Remove
            QuickOption.AddFavorite -> CineIcons.NotFavorite
            QuickOption.RemoveFavorite -> CineIcons.Favorite
            QuickOption.Select -> CineIcons.Checked
        }
