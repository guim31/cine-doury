package fr.guim.cinedoury.feature.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.designsystem.component.AmbientBackdrop
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.ExpandableText
import fr.guim.cinedoury.core.designsystem.component.FocusPivot
import fr.guim.cinedoury.core.designsystem.component.GeneratedArtwork
import fr.guim.cinedoury.core.designsystem.component.PrimaryButton
import fr.guim.cinedoury.core.designsystem.component.RoundIconButton
import fr.guim.cinedoury.core.designsystem.component.TechBadges
import fr.guim.cinedoury.core.designsystem.component.readabilityScrims
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.component.sharedPoster
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.tabular
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.ImageSources
import fr.guim.cinedoury.core.ui.SEPARATOR
import fr.guim.cinedoury.core.ui.SourceErrorPanel
import fr.guim.cinedoury.core.ui.ambience
import fr.guim.cinedoury.core.ui.cardTitle
import fr.guim.cinedoury.core.ui.imageSource
import fr.guim.cinedoury.core.ui.label
import fr.guim.cinedoury.core.ui.rememberArt
import fr.guim.cinedoury.feature.manage.DeleteDialog
import fr.guim.cinedoury.feature.manage.ManageErrorDialog
import fr.guim.cinedoury.feature.manage.ManageNoticePill
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/** What a details screen asks the app to open. */
interface DetailsNavigation {
    /** Another details screen: an episode, a person, a film of the saga. */
    fun open(item: MediaItem)

    /** The player (J4), with the version and tracks chosen. */
    fun play(request: PlaybackRequest)

    /** A trailer on the web, in the app of its site (YouTube). */
    fun openTrailer(url: String)

    fun sessionEnded()

    /** « Corriger l'identification » of [item] (J6). */
    fun identify(item: MediaItem) {}

    /** « Changer l'affiche, le fond ou le logo » of [item] (J6). */
    fun images(item: MediaItem) {}

    /** The item was deleted: back to the screen before (J6). */
    fun close() {}
}

/** The side panels of a details screen. */
enum class DetailsPanel {
    /** The version, the audio and the subtitles, before playback. */
    Tracks,

    /** « ⋯ »: the other actions. */
    More,

    /** « Informations sur le fichier ». */
    FileInfo,
}

@Composable
fun DetailsRoute(
    item: MediaItem,
    navigation: DetailsNavigation,
    viewModel: DetailsViewModel = koinViewModel(key = "details-${item.id.value}") { parametersOf(item) },
    sessions: SessionManager = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.sessionEnded) { navigation.sessionEnded() }
    CollectEvents(viewModel.deleted) { navigation.close() }
    DetailsScreen(state = state, sources = sessions::imageSource, actions = viewModel, navigation = navigation)
}

/** Preview tags of a details screen, for tests and screenshots. */
object DetailsPreview {
    const val PLAY = "details-play"
    const val RESTART = "details-restart"
    const val TRAILER = "details-trailer"
    const val TRACKS = "details-tracks"
    const val WATCHED = "details-watched"
    const val FAVORITE = "details-favorite"
    const val SERIES = "details-series"
    const val MORE = "details-more"
    const val OVERVIEW = "details-overview"
    const val SEASON_WATCHED = "details-season-watched"

    fun season(index: Int) = "season-$index"

    fun episode(index: Int) = "episode-$index"

    fun card(
        row: String,
        index: Int,
    ) = "details-$row-$index"
}

/**
 * A details screen (`UI.md` § 8, « Fiche », `FONCTIONNALITES.md` § 4): the backdrop of the title
 * over the whole screen under the veils, the poster, then a column of 45 %: logo, metadata, genres,
 * badges, synopsis, crew and the buttons, « Lire » or « Reprendre » with the focus. Below: the
 * seasons and episodes of a series, the cast, the saga, similar titles, the works of a person.
 * « Retour » closes a panel, then goes back exactly where the user came from.
 *
 * @param previewPanel opens a panel (screenshots); [previewExpanded] unfolds the synopsis;
 * [previewFirstItem] scrolls the page to that item (0: the hero, 1: the seasons).
 */
@Composable
fun DetailsScreen(
    state: DetailsState,
    sources: ImageSources,
    actions: DetailsActions,
    navigation: DetailsNavigation,
    modifier: Modifier = Modifier,
    previewPanel: DetailsPanel? = null,
    previewExpanded: Boolean = false,
    previewFirstItem: Int = 0,
) {
    var panel by rememberSaveable { mutableStateOf(previewPanel) }
    var expanded by rememberSaveable { mutableStateOf(previewExpanded) }
    // The element that had the focus, found again when coming back to the screen.
    var focused by rememberSaveable { mutableStateOf<String?>(null) }
    val requesters = remember { FocusRequesters() }
    val item = state.item
    // A closing panel first gives the focus back to the element that opened it, then fades out.
    val closePanel = {
        focused?.let { runCatching { requesters[it].requestFocus() } }
        panel = null
    }
    BackHandler(enabled = panel != null) {
        if (panel == DetailsPanel.FileInfo) panel = DetailsPanel.More else closePanel()
    }
    val restoreFocus = { focused?.let { runCatching { requesters[it].requestFocus() } } }
    BackHandler(enabled = state.deletion != null || state.manageError != null) {
        restoreFocus()
        if (state.deletion != null) actions.cancelDelete() else actions.dismissManageError()
    }

    val play: () -> Unit = { actions.playRequest()?.let(navigation::play) }
    val open: (MediaItem) -> Unit = navigation::open
    Box(modifier = modifier.fillMaxSize().testTag("details")) {
        AmbientBackdrop(ambience = item.ambience)
        val backdrop = rememberArt(item.images.backdrop, sources)
        if (backdrop != null) {
            Box(Modifier.fillMaxSize().readabilityScrims(CineTheme.colors.background)) { backdrop() }
        }
        FocusPivot(fraction = PIVOT) {
            LazyColumn(
                state = rememberLazyListState(initialFirstVisibleItemIndex = previewFirstItem),
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        top = CineSpacing.safeVertical + CineSpacing.s,
                        bottom = CineSpacing.xxl,
                    ),
                verticalArrangement = Arrangement.spacedBy(CineSpacing.l),
            ) {
                item(key = "hero", contentType = "hero") {
                    Hero(
                        state = state,
                        sources = sources,
                        actions = actions,
                        expanded = expanded,
                        onExpand = { expanded = !expanded },
                        onPlay = play,
                        onRestart = { actions.playRequest(fromStart = true)?.let(navigation::play) },
                        onTrailer = {
                            when (val trailer = state.trailer) {
                                is Trailer.Local -> {
                                    navigation.play(PlaybackRequest(trailer.item, resume = false, trailer = true))
                                }

                                is Trailer.Remote -> {
                                    navigation.openTrailer(trailer.url)
                                }

                                null -> {}
                            }
                        },
                        onPanel = { panel = it },
                        onSeries = { state.series?.let(open) },
                        requesters = requesters,
                        onFocused = { focused = it },
                    )
                }
                episodes(state, sources, actions, open, requesters) { focused = it }
                rows(state, sources, open, requesters) { focused = it }
            }
        }
        DetailsPanels(
            panel = panel,
            state = state,
            actions = actions,
            onPanel = { if (it == null) closePanel() else panel = it },
            onPlay = {
                closePanel()
                play()
            },
            onIdentify = { navigation.identify(item) },
            onImages = { navigation.images(item) },
        )
        ManageNoticePill(state.notice)
        // A dialog that closes first gives the focus back to the element it came from, as a panel
        // does: the focus never falls out of the screen.
        state.deletion?.let {
            DeleteDialog(
                it,
                onConfirm = actions::confirmDelete,
                onCancel = {
                    restoreFocus()
                    actions.cancelDelete()
                },
            )
        }
        state.manageError?.let {
            ManageErrorDialog(
                it,
                onDismiss = {
                    restoreFocus()
                    actions.dismissManageError()
                },
            )
        }
    }
    // The first focus: « Lire », or the element left when coming back (or that opened the panel just
    // closed); for a person or a collection, the first card, once read.
    val target =
        focused ?: when {
            state.hasMainButton -> KEY_PLAY

            item.kind == ItemKind.Person -> state.firstRowKey()?.let(::rowKey)

            // A collection, or a season: its first card, else « Favori », always there.
            else -> state.firstRowKey()?.let(::rowKey) ?: DetailsPreview.FAVORITE.takeIf { state.loaded }
        }
    val ready = target != null && (target == KEY_PLAY || state.loaded)
    if (target != null) {
        LaunchedEffect(target, ready, panel == null) {
            if (ready && panel == null) {
                withFrameNanos { }
                runCatching { requesters[target].requestFocus() }
            }
        }
    }
}

/** Where the focused element of the page stands, from the top of the screen. */
private const val PIVOT = 0.3f

internal const val KEY_PLAY = DetailsPreview.PLAY

/** The focus requesters of a screen, made on first use: outside composition, where they may not be built. */
internal class FocusRequesters {
    private val map = mutableMapOf<String, FocusRequester>()

    operator fun get(key: String): FocusRequester = map.getOrPut(key) { FocusRequester() }
}

/** A person or a collection has no main button: the focus goes to its first card. */
internal val DetailsState.hasMainButton: Boolean
    get() = item.kind !in setOf(ItemKind.Person, ItemKind.Collection) && (playable || item.kind == ItemKind.Series)

@Composable
private fun Hero(
    state: DetailsState,
    sources: ImageSources,
    actions: DetailsActions,
    expanded: Boolean,
    onExpand: () -> Unit,
    onPlay: () -> Unit,
    onRestart: () -> Unit,
    onTrailer: () -> Unit,
    onPanel: (DetailsPanel) -> Unit,
    onSeries: () -> Unit,
    requesters: FocusRequesters,
    onFocused: (String) -> Unit,
) {
    val item = state.item
    val colors = CineTheme.colors
    val typography = CineTheme.typography
    // The picture and the text, which keeps a reading width; under them, the buttons take the whole
    // width: « Reprendre — 1 h 09 restantes » and six round buttons do not fit beside the poster.
    Column(
        modifier = Modifier.padding(horizontal = CineSpacing.safeHorizontal),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
    ) {
        // A title with a logo shows no poster: the logo already says the title, and the text takes
        // the place (`UI.md` § 8, retouche du J3).
        val logoInPlace = item.showsLogoInPlaceOfPoster
        Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.l)) {
            if (!logoInPlace) Picture(item, sources)
            Column(
                modifier = Modifier.widthIn(max = CineSizes.detailsTextWidth),
                verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
            ) {
                Title(item, sources, sharedKey = item.id.value.takeIf { logoInPlace })
                item.detailsLine()?.let {
                    Text(
                        text = it,
                        style = typography.caption.tabular(),
                        color = colors.text2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                item.genres.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        text = it.joinToString(SEPARATOR),
                        style = typography.caption,
                        color = colors.text2,
                        maxLines = 1,
                    )
                }
                if (state.badges.isNotEmpty()) {
                    TechBadges(state.badges.map { it.label() }, Modifier.padding(vertical = CineSpacing.xs / 2))
                }
                item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    ExpandableText(
                        text = overview,
                        expanded = expanded,
                        onToggle = onExpand,
                        focus = rememberCineFocus(DetailsPreview.OVERVIEW),
                        focusRequester = requesters[KEY_OVERVIEW],
                        modifier =
                            Modifier
                                .testTag(
                                    DetailsPreview.OVERVIEW,
                                ).onFocusChanged { if (it.hasFocus) onFocused(KEY_OVERVIEW) },
                    )
                }
                item.crewLine()?.let {
                    Text(
                        text = it,
                        style = typography.caption,
                        color = colors.text2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        val error = state.error
        if (error != null && !state.loaded) {
            SourceErrorPanel(error = error, onRetry = actions::retry, retryFocusRequester = requesters[KEY_PLAY])
        } else {
            Buttons(state, actions, onPlay, onRestart, onTrailer, onPanel, onSeries, requesters, onFocused)
        }
    }
}

private const val KEY_OVERVIEW = "overview"

/** The poster (it flies from the card that was opened), the frame of an episode, a photo. */
@Composable
private fun Picture(
    item: MediaItem,
    sources: ImageSources,
) {
    val episode = item.kind == ItemKind.Episode
    val size = if (episode) CineSizes.episodePicture else CineSizes.posterLarge
    val image = if (episode) item.images.primary ?: item.images.landscape else item.images.primary
    val art = rememberArt(image, sources)
    Box(
        Modifier
            .sharedPoster(item.id.value.takeIf { !episode })
            .size(size),
    ) {
        if (art != null) art() else GeneratedArtwork(item.cardTitle, compact = episode)
    }
}

/** A film, a series or a collection with a logo: the logo stands in the place of the poster. */
internal val MediaItem.showsLogoInPlaceOfPoster: Boolean
    get() = kind !in setOf(ItemKind.Episode, ItemKind.Person) && images.anyLogo != null

/** The logo of the title (`UI.md` § 5), its name in « Affiche » otherwise; for an episode, its series above. */
@Composable
private fun Title(
    item: MediaItem,
    sources: ImageSources,
    sharedKey: String? = null,
) {
    val colors = CineTheme.colors
    val typography = CineTheme.typography
    val logo = rememberArt(item.images.anyLogo, sources, contentScale = ContentScale.Fit)
    if (item.kind == ItemKind.Episode) {
        if (logo != null) {
            Box(
                Modifier.sizeIn(maxWidth = CineSizes.logoMax.width, maxHeight = CineSizes.logoMax.height / 2),
            ) { logo() }
        } else {
            Text(text = item.cardTitle, style = typography.title2, color = colors.text2, maxLines = 1)
        }
        Text(
            text = item.numberedTitle(),
            style = typography.title1,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    } else if (logo != null) {
        // The poster of the card that was opened fades into the logo as it flies (`UI.md` § 7).
        Box(
            Modifier
                .sharedPoster(sharedKey)
                .sizeIn(maxWidth = CineSizes.logoMax.width, maxHeight = CineSizes.logoMax.height),
        ) { logo() }
    } else {
        Text(
            text = item.title,
            style = typography.display,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * « Lire » or « Reprendre » (the focus), then the round buttons, their label shown with the focus:
 * Depuis le début, Bande-annonce, Audio et sous-titres, Marquer comme vu, Favori, Aller à la série, ⋯.
 */
@Composable
private fun Buttons(
    state: DetailsState,
    actions: DetailsActions,
    onPlay: () -> Unit,
    onRestart: () -> Unit,
    onTrailer: () -> Unit,
    onPanel: (DetailsPanel) -> Unit,
    onSeries: () -> Unit,
    requesters: FocusRequesters,
    onFocused: (String) -> Unit,
) {
    val item = state.item
    if (item.kind == ItemKind.Person) return
    val watched = item.userData?.played == true
    val favorite = item.userData?.isFavorite == true

    fun Modifier.remembered(key: String) = testTag(key).onFocusChanged { if (it.hasFocus) onFocused(key) }
    Row(
        modifier = Modifier.padding(top = CineSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
        verticalAlignment = Alignment.Top,
    ) {
        state.playLabel()?.let { label ->
            PrimaryButton(
                text = label,
                icon = CineIcons.Play,
                onClick = onPlay,
                focusRequester = requesters[KEY_PLAY],
                focus = rememberCineFocus(DetailsPreview.PLAY),
                modifier = Modifier.remembered(KEY_PLAY),
            )
        }
        if (state.playable && item.progress != null) {
            Round(
                CineIcons.Restart,
                stringResource(R.string.action_restart),
                onRestart,
                DetailsPreview.RESTART,
                requesters,
                onFocused,
            )
        }
        if (state.trailer != null) {
            Round(
                CineIcons.Trailer,
                stringResource(R.string.action_trailer),
                onTrailer,
                DetailsPreview.TRAILER,
                requesters,
                onFocused,
            )
        }
        if (state.playable && item.mediaSources.isNotEmpty()) {
            Round(
                CineIcons.Tracks,
                stringResource(R.string.action_tracks),
                { onPanel(DetailsPanel.Tracks) },
                DetailsPreview.TRACKS,
                requesters,
                onFocused,
            )
        }
        if (item.kind != ItemKind.Collection) {
            Round(
                if (watched) CineIcons.Unwatched else CineIcons.Watched,
                stringResource(
                    when {
                        item.kind == ItemKind.Series && watched -> R.string.action_series_unwatched
                        item.kind == ItemKind.Series -> R.string.action_series_watched
                        watched -> R.string.action_unwatched
                        else -> R.string.action_watched
                    },
                ),
                { actions.toggleWatched(item) },
                DetailsPreview.WATCHED,
                requesters,
                onFocused,
                selected = watched,
            )
        }
        Round(
            if (favorite) CineIcons.Favorite else CineIcons.NotFavorite,
            stringResource(if (favorite) R.string.action_unfavorite else R.string.action_favorite),
            actions::toggleFavorite,
            DetailsPreview.FAVORITE,
            requesters,
            onFocused,
            selected = favorite,
        )
        if (state.series != null) {
            Round(
                CineIcons.Shows,
                stringResource(R.string.action_series),
                onSeries,
                DetailsPreview.SERIES,
                requesters,
                onFocused,
            )
        }
        if (state.playable || state.rights.canManage) {
            Round(CineIcons.More, stringResource(R.string.action_more), {
                onPanel(DetailsPanel.More)
            }, DetailsPreview.MORE, requesters, onFocused)
        }
    }
}

@Composable
private fun Round(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tag: String,
    requesters: FocusRequesters,
    onFocused: (String) -> Unit,
    selected: Boolean = false,
) {
    RoundIconButton(
        icon = icon,
        label = label,
        onClick = onClick,
        selected = selected,
        focusRequester = requesters[tag],
        focus = rememberCineFocus(tag),
        modifier = Modifier.testTag(tag).onFocusChanged { if (it.hasFocus) onFocused(tag) },
    )
}
