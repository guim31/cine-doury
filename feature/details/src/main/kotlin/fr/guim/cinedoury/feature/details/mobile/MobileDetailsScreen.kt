package fr.guim.cinedoury.feature.details.mobile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.designsystem.component.CardProgressBar
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.mobile.MobileAvatar
import fr.guim.cinedoury.core.designsystem.mobile.MobileCard
import fr.guim.cinedoury.core.designsystem.mobile.MobileChip
import fr.guim.cinedoury.core.designsystem.mobile.MobileIconButton
import fr.guim.cinedoury.core.designsystem.mobile.MobilePrimaryButton
import fr.guim.cinedoury.core.designsystem.mobile.MobileRowTitle
import fr.guim.cinedoury.core.designsystem.mobile.MobileSheet
import fr.guim.cinedoury.core.designsystem.mobile.MobileSheetLine
import fr.guim.cinedoury.core.designsystem.mobile.MobileSheetLines
import fr.guim.cinedoury.core.designsystem.mobile.MobileTopBar
import fr.guim.cinedoury.core.designsystem.mobile.mobileGutter
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.MobileSizes
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.ImageSources
import fr.guim.cinedoury.core.ui.cardTitle
import fr.guim.cinedoury.core.ui.describe
import fr.guim.cinedoury.core.ui.imageSource
import fr.guim.cinedoury.core.ui.label
import fr.guim.cinedoury.core.ui.landscapeImage
import fr.guim.cinedoury.core.ui.mobile.MobileItemCard
import fr.guim.cinedoury.core.ui.mobile.MobileQuickMenu
import fr.guim.cinedoury.core.ui.mobile.MobileQuickMenuHost
import fr.guim.cinedoury.core.ui.mobile.MobileSourceError
import fr.guim.cinedoury.core.ui.mobile.rememberMobileQuickMenu
import fr.guim.cinedoury.core.ui.posterImage
import fr.guim.cinedoury.core.ui.rememberArt
import fr.guim.cinedoury.feature.details.DetailsActions
import fr.guim.cinedoury.feature.details.DetailsNavigation
import fr.guim.cinedoury.feature.details.DetailsState
import fr.guim.cinedoury.feature.details.DetailsViewModel
import fr.guim.cinedoury.feature.details.R
import fr.guim.cinedoury.feature.details.Trailer
import fr.guim.cinedoury.feature.details.crewLine
import fr.guim.cinedoury.feature.details.detailRows
import fr.guim.cinedoury.feature.details.detailsLine
import fr.guim.cinedoury.feature.details.episodeLine
import fr.guim.cinedoury.feature.details.hasMainButton
import fr.guim.cinedoury.feature.details.numberedTitle
import fr.guim.cinedoury.feature.details.playLabel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import fr.guim.cinedoury.core.ui.R as UiR

/** Tags of the details screen of the phone, for tests and screenshots. */
object MobileDetailsTags {
    const val SCREEN = "details"
    const val PLAY = "details-play"
    const val RESTART = "details-restart"
    const val TRAILER = "details-trailer"
    const val TRACKS = "details-tracks"
    const val WATCHED = "details-watched"
    const val FAVORITE = "details-favorite"
    const val SERIES = "details-series"
    const val OVERVIEW = "details-overview"

    fun season(index: Int) = "details-season-$index"

    fun episode(index: Int) = "details-episode-$index"
}

@Composable
fun MobileDetailsRoute(
    item: MediaItem,
    season: ItemId? = null,
    navigation: DetailsNavigation,
    onBack: () -> Unit,
    viewModel: DetailsViewModel =
        koinViewModel(key = "details-${item.id.value}-${season?.value}") { parametersOf(item, season) },
    sessions: SessionManager = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.sessionEnded) { navigation.sessionEnded() }
    CollectEvents(viewModel.deleted) { onBack() }
    MobileDetailsScreen(state, sessions::imageSource, viewModel, navigation, onBack)
}

/**
 * A details screen on the phone (M1, `UI.md` § 9): a film, a series, a season, an episode, a person
 * or a collection. Its backdrop across the top, the title, the line of metadata and the badges, then
 * the actions at once, visible without scrolling (« Lire » or « Reprendre », from the start, the
 * trailer, the tracks, « vu », favourite). The synopsis below, then the seasons and the episodes of a
 * series in a list, then the rows (cast, saga, similar titles, the works of a person).
 */
@Composable
fun MobileDetailsScreen(
    state: DetailsState,
    sources: ImageSources,
    actions: DetailsActions,
    navigation: DetailsNavigation,
    onBack: (() -> Unit)?,
    menu: MobileQuickMenu = rememberMobileQuickMenu(),
    previewTracks: Boolean = false,
) {
    var tracks by rememberSaveable { mutableStateOf(previewTracks) }
    val item = state.item
    val error = state.error
    BoxWithConstraints(
        Modifier.fillMaxSize().background(CineTheme.colors.background).testTag(MobileDetailsTags.SCREEN),
    ) {
        val gutter = mobileGutter(maxWidth)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = CineSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.m),
        ) {
            item(key = "hero", contentType = "hero") {
                Hero(state, sources, actions, navigation, gutter, backdropHeight(maxWidth, maxHeight), onTracks = {
                    tracks =
                        true
                })
            }
            if (error != null && !state.loaded) {
                item(key = "error") { MobileSourceError(error, actions::retry, Modifier.padding(horizontal = gutter)) }
            }
            if (item.kind == ItemKind.Series || item.kind == ItemKind.Season) {
                episodes(state, sources, actions, navigation, menu, gutter)
            }
            item(key = "rows", contentType = "rows") {
                Rows(state, sources, navigation, menu, gutter)
            }
        }
        // Over the backdrop, under the bar of the system: « Retour » always within reach.
        MobileTopBar(
            title = "",
            onBack = onBack,
            backLabel = stringResource(UiR.string.action_back),
            transparent = true,
        )
    }
    if (tracks) {
        TracksSheet(
            state,
            actions,
            onPlay = { actions.playRequest()?.let(navigation::play) },
        ) { tracks = false }
    }
    MobileQuickMenuHost(menu, navigation)
}

@Composable
private fun Hero(
    state: DetailsState,
    sources: ImageSources,
    actions: DetailsActions,
    navigation: DetailsNavigation,
    gutter: Dp,
    backdropHeight: Dp,
    onTracks: () -> Unit,
) {
    val item = state.item
    val colors = CineTheme.colors
    val typography = CineTheme.typography
    val person = item.kind == ItemKind.Person
    Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.s)) {
        val backdrop =
            rememberArt(item.images.backdrop ?: item.landscapeImage.takeIf { item.kind == ItemKind.Episode }, sources)
        Box(Modifier.fillMaxWidth().height(backdropHeight)) {
            if (backdrop != null) backdrop() else Box(Modifier.fillMaxSize().background(colors.surface1))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, colors.background))))
        }
        Row(
            Modifier.padding(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
            verticalAlignment = Alignment.Bottom,
        ) {
            val poster = rememberArt(item.posterImage, sources)
            if (person) {
                MobileAvatar(item.title, size = MobileSizes.avatarLarge, image = poster)
            } else if (poster != null && item.kind != ItemKind.Episode) {
                Box(
                    Modifier.width(MobileSizes.posterSmall.width).aspectRatio(POSTER_RATIO).clip(CardShape),
                ) { poster() }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CineSpacing.xs / 2)) {
                val logo = rememberArt(item.images.anyLogo, sources, ContentScale.Fit)
                if (logo != null && item.kind != ItemKind.Episode) {
                    Box(
                        Modifier
                            .height(
                                MobileSizes.logoMax.height,
                            ).widthIn(max = MobileSizes.logoMax.width)
                            .fillMaxWidth(),
                    ) { logo() }
                } else {
                    Text(
                        item.cardTitle,
                        style = typography.display,
                        color = colors.text,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.kind == ItemKind.Episode) {
                    Text(item.numberedTitle(), style = typography.title2, color = colors.text)
                }
                item.detailsLine()?.let { Text(it, style = typography.caption, color = colors.text2) }
            }
        }
        if (state.badges.isNotEmpty()) {
            FlowRow(
                Modifier.padding(horizontal = gutter),
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(CineSpacing.xs / 2),
            ) {
                state.badges.forEach { badge ->
                    Text(
                        badge.label(),
                        style = typography.minimum,
                        color = colors.text2,
                        modifier =
                            Modifier
                                .clip(
                                    CardShape,
                                ).background(
                                    colors.surface2,
                                ).padding(horizontal = CineSpacing.xs, vertical = CineSizes.pillPadding),
                    )
                }
            }
        }
        Actions(state, actions, navigation, gutter, onTracks)
        Overview(item, gutter)
    }
}

/** The actions, at once under the title (`UI.md` § 9): the main one across, the others in a line. */
@Composable
private fun Actions(
    state: DetailsState,
    actions: DetailsActions,
    navigation: DetailsNavigation,
    gutter: Dp,
    onTracks: () -> Unit,
) {
    val item = state.item
    if (item.kind == ItemKind.Person) return
    Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
        if (state.hasMainButton) {
            val label = state.playLabel()
            MobilePrimaryButton(
                text = label ?: stringResource(R.string.action_play),
                onClick = { actions.playRequest()?.let(navigation::play) },
                modifier = Modifier.fillMaxWidth().testTag(MobileDetailsTags.PLAY),
                icon = CineIcons.Play,
                enabled = label != null,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
            if (state.playable && item.progress != null) {
                MobileIconButton(
                    CineIcons.Restart,
                    stringResource(R.string.action_restart),
                    { actions.playRequest(fromStart = true)?.let(navigation::play) },
                    Modifier.testTag(MobileDetailsTags.RESTART),
                )
            }
            state.trailer?.let { trailer ->
                MobileIconButton(
                    CineIcons.Trailer,
                    stringResource(R.string.action_trailer),
                    {
                        when (trailer) {
                            is Trailer.Local -> {
                                navigation.play(
                                    PlaybackRequest(trailer.item, resume = false, trailer = true),
                                )
                            }

                            is Trailer.Remote -> {
                                navigation.openTrailer(trailer.url)
                            }
                        }
                    },
                    Modifier.testTag(MobileDetailsTags.TRAILER),
                )
            }
            if (state.playable && state.source != null) {
                MobileIconButton(
                    CineIcons.Tracks,
                    stringResource(R.string.action_tracks),
                    onTracks,
                    Modifier.testTag(MobileDetailsTags.TRACKS),
                )
            }
            if (item.kind != ItemKind.Collection) {
                val watched = item.userData?.played == true
                MobileIconButton(
                    if (watched) CineIcons.WatchedAll else CineIcons.Watched,
                    stringResource(if (watched) R.string.action_unwatched else R.string.action_watched),
                    { actions.toggleWatched(item) },
                    Modifier.testTag(MobileDetailsTags.WATCHED),
                    selected = watched,
                )
            }
            val favorite = item.userData?.isFavorite == true
            MobileIconButton(
                if (favorite) CineIcons.Favorite else CineIcons.NotFavorite,
                stringResource(if (favorite) R.string.action_unfavorite else R.string.action_favorite),
                actions::toggleFavorite,
                Modifier.testTag(MobileDetailsTags.FAVORITE),
                selected = favorite,
            )
            state.series?.let { series ->
                MobileIconButton(CineIcons.Shows, stringResource(R.string.action_series), {
                    navigation.open(series)
                }, Modifier.testTag(MobileDetailsTags.SERIES))
            }
        }
    }
}

/** The tagline, the synopsis (three lines, all of it on a tap) and the crew. */
@Composable
private fun Overview(
    item: MediaItem,
    gutter: Dp,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val colors = CineTheme.colors
    Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
        item.tagline?.let { Text(it, style = CineTheme.typography.label, color = colors.text) }
        item.overview?.let {
            Text(
                it,
                style = CineTheme.typography.body,
                color = colors.text2,
                maxLines = if (expanded) Int.MAX_VALUE else OVERVIEW_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .clickable(
                            role = Role.Button,
                        ) { expanded = !expanded }
                        .testTag(MobileDetailsTags.OVERVIEW),
            )
        }
        item.crewLine()?.let { Text(it, style = CineTheme.typography.caption, color = colors.text3) }
    }
}

/** The seasons of a series as chips, then its episodes in a list (`UI.md` § 9). */
private fun LazyListScope.episodes(
    state: DetailsState,
    sources: ImageSources,
    actions: DetailsActions,
    navigation: DetailsNavigation,
    menu: MobileQuickMenu,
    gutter: Dp,
) {
    if (!state.flattened) {
        item(key = "seasons", contentType = "seasons") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = gutter),
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
            ) {
                items(state.seasons.size, key = { state.seasons[it].id.value }) { index ->
                    val season = state.seasons[index]
                    MobileChip(
                        season.title,
                        selected = season.id == state.season,
                        onClick = { actions.selectSeason(season.id) },
                        modifier = Modifier.testTag(MobileDetailsTags.season(index)),
                    )
                }
            }
        }
    }
    val episodes = state.shownEpisodes
    items(episodes.size, key = { "episode-${episodes[it].id.value}" }, contentType = { "episode" }) { index ->
        EpisodeRow(
            episodes[index],
            sources,
            navigation,
            menu,
            gutter,
            Modifier.testTag(MobileDetailsTags.episode(index)),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    episode: MediaItem,
    sources: ImageSources,
    navigation: DetailsNavigation,
    menu: MobileQuickMenu,
    gutter: Dp,
    modifier: Modifier,
) {
    val colors = CineTheme.colors
    val typography = CineTheme.typography
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(
                    role = Role.Button,
                    onClick = { navigation.open(episode) },
                    onLongClick = { menu.open(episode) },
                ).padding(horizontal = gutter, vertical = CineSpacing.xs / 2),
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
    ) {
        Box(Modifier.size(MobileSizes.episodeThumbnail).clip(CardShape).background(colors.surface1)) {
            rememberArt(episode.landscapeImage, sources)?.invoke()
            episode.progress?.let { CardProgressBar(it, Modifier.align(Alignment.BottomCenter)) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CineSpacing.xs / 4)) {
            Text(
                episode.numberedTitle(),
                style = typography.label,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            episode.episodeLine()?.let { Text(it, style = typography.minimum, color = colors.text2) }
            episode.overview?.let {
                Text(
                    it,
                    style = typography.caption,
                    color = colors.text3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (episode.userData?.played == true) {
            Icon(
                CineIcons.Watched,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(CineSpacing.m),
            )
        }
    }
}

/** The rows of cards: the works of a person, the cast, the saga, the similar titles. */
@Composable
private fun Rows(
    state: DetailsState,
    sources: ImageSources,
    navigation: DetailsNavigation,
    menu: MobileQuickMenu,
    gutter: Dp,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.m)) {
        state.detailRows().forEach { row ->
            Column {
                MobileRowTitle(row.title, Modifier.padding(horizontal = gutter))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = gutter),
                    horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
                ) {
                    items(row.items, key = { "${row.key}-${it.id.value}" }) { card ->
                        if (row.people) {
                            MobileCard(
                                title = card.title,
                                subtitle = card.overview,
                                size = MobileSizes.posterSmall,
                                onClick = { navigation.open(card) },
                                image = rememberArt(card.images.primary, sources),
                            )
                        } else {
                            MobileItemCard(card, sources, { navigation.open(card) }, menu = menu)
                        }
                    }
                }
            }
        }
    }
}

/** The version, the audio and the subtitles before playback (the TV's panel, in a sheet). */
@Composable
private fun TracksSheet(
    state: DetailsState,
    actions: DetailsActions,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val source = state.source ?: return
    val selection = state.selection
    MobileSheet(title = stringResource(R.string.panel_tracks), onDismiss = onDismiss) {
        LazyColumn {
            item {
                MobileSheetLines(
                    listOf(
                        MobileSheetLine(
                            stringResource(R.string.action_play_with_tracks),
                            CineIcons.Play,
                            "tracks-play",
                        ) {
                            onDismiss()
                            onPlay()
                        },
                    ),
                )
            }
            if (state.item.mediaSources.size > 1) {
                item { SheetSection(stringResource(R.string.panel_version)) }
                item {
                    MobileSheetLines(
                        state.item.mediaSources.map { version ->
                            MobileSheetLine(
                                version.describe(),
                                null,
                                "version-${version.id}",
                            ) { actions.selectVersion(version.id) }
                        },
                        selected = "version-${source.id}",
                    )
                }
            }
            item { SheetSection(stringResource(R.string.panel_audio)) }
            item {
                MobileSheetLines(
                    source.audio.map { track ->
                        MobileSheetLine(
                            track.label(),
                            null,
                            "audio-${track.index}",
                        ) { actions.selectAudio(track.index) }
                    },
                    selected = "audio-${selection?.audioIndex}",
                )
            }
            item { SheetSection(stringResource(R.string.panel_subtitles)) }
            item {
                MobileSheetLines(
                    listOf(
                        MobileSheetLine(stringResource(R.string.subtitles_none), null, "subtitles-none") {
                            actions.selectSubtitles(null)
                        },
                    ) +
                        source.subtitles.map { track ->
                            MobileSheetLine(
                                track.label(),
                                null,
                                "subtitles-${track.index}",
                            ) { actions.selectSubtitles(track.index) }
                        },
                    selected = selection?.subtitleIndex?.let { "subtitles-$it" } ?: "subtitles-none",
                )
            }
        }
    }
}

@Composable
private fun SheetSection(title: String) {
    Text(
        title,
        style = CineTheme.typography.label,
        color = CineTheme.colors.text2,
        modifier = Modifier.padding(horizontal = MobileSizes.gutter, vertical = CineSpacing.xs),
    )
}

/**
 * The backdrop: 16:9 across a phone held upright; on a phone on its side or a tablet, at most
 * [BACKDROP_MAX_HEIGHT] of the height, so that the actions stay in view without scrolling.
 */
private fun backdropHeight(
    width: Dp,
    height: Dp,
): Dp = minOf(width / MobileSizes.BACKDROP_RATIO, height * BACKDROP_MAX_HEIGHT)

private const val BACKDROP_MAX_HEIGHT = 0.4f

private val CardShape = RoundedCornerShape(MobileSizes.cardCorner)
private const val POSTER_RATIO = 2f / 3f
private const val OVERVIEW_LINES = 3
