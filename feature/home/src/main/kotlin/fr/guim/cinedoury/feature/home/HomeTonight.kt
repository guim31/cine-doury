package fr.guim.cinedoury.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import fr.guim.cinedoury.core.data.home.HomeRows
import fr.guim.cinedoury.core.data.home.TonightKind
import fr.guim.cinedoury.core.data.home.TonightTile
import fr.guim.cinedoury.core.designsystem.component.MosaicTile
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.ui.ImageSources
import fr.guim.cinedoury.core.ui.LocalClock
import fr.guim.cinedoury.core.ui.QuickMenuState
import fr.guim.cinedoury.core.ui.cardSubtitle
import fr.guim.cinedoury.core.ui.cardTitle
import fr.guim.cinedoury.core.ui.landscapeImage
import fr.guim.cinedoury.core.ui.metadataLine
import fr.guim.cinedoury.core.ui.quickMenu
import fr.guim.cinedoury.core.ui.rememberArt
import fr.guim.cinedoury.core.ui.toDurationText
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.toJavaDuration

/** The mosaic « Ce soir » as the home draws it (essai réel 5): the large tile first. */
@Immutable
data class TonightState(
    val tiles: List<TonightTile>,
)

/**
 * The mosaic « Ce soir » at the top of the home (essai réel 5, `UI.md` § 8): four columns on two
 * lines in the width of the content, the large tile on two columns and two lines on the left, four
 * small ones on the right. OK plays what is in progress or next, opens the details of the others; OK
 * held opens the menu of the tile. Each tile reads its own focus: a step of the focus recomposes the
 * two tiles concerned, nothing else.
 *
 * @param height of the tiles.
 * @param room above the tiles, inside the list that clips them: their outline, halo and growth with
 * the focus ([mosaicFocusRoom]).
 * @param main attached to the large tile: the first focus of the home.
 */
@Composable
internal fun TonightMosaic(
    state: TonightState,
    height: Dp,
    room: Dp,
    sources: ImageSources,
    menu: QuickMenuState,
    navigation: HomeNavigation,
    main: FocusRequester,
    onFocused: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tiles = state.tiles
    // → from the large tile: the small one at the top left, never the one under it, at the same distance.
    val firstSmall = remember { FocusRequester() }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = room)
                .height(height)
                .padding(start = ContentStart, end = CineSpacing.safeHorizontal)
                .testTag(HomePreview.TONIGHT),
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
    ) {
        tiles.firstOrNull()?.let { tile ->
            val large = Modifier.weight(2f).fillMaxHeight().focusProperties { right = firstSmall }
            Tile(tile, 0, large = true, sources, menu, navigation, main, onFocused, large)
        }
        Column(Modifier.weight(2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(CineSpacing.m)) {
            for (line in 0 until 2) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CineSpacing.m)) {
                    for (column in 0 until 2) {
                        val index = 1 + line * 2 + column
                        val requester = firstSmall.takeIf { index == 1 }
                        SmallTile(tiles.getOrNull(index), index, sources, menu, navigation, requester, onFocused)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.SmallTile(
    tile: TonightTile?,
    index: Int,
    sources: ImageSources,
    menu: QuickMenuState,
    navigation: HomeNavigation,
    focusRequester: FocusRequester?,
    onFocused: (MediaItem) -> Unit,
) {
    val modifier = Modifier.weight(1f).fillMaxHeight()
    if (tile == null) {
        // A server with fewer titles than tiles: the place stays, empty.
        androidx.compose.foundation.layout
            .Spacer(modifier)
    } else {
        Tile(tile, index, large = false, sources, menu, navigation, focusRequester, onFocused, modifier)
    }
}

@Composable
private fun Tile(
    tile: TonightTile,
    index: Int,
    large: Boolean,
    sources: ImageSources,
    menu: QuickMenuState,
    navigation: HomeNavigation,
    focusRequester: FocusRequester?,
    onFocused: (MediaItem) -> Unit,
    modifier: Modifier,
) {
    val item = tile.item
    val tag = HomePreview.tonight(index)
    val playable =
        tile.kind == TonightKind.Resume || tile.kind == TonightKind.NextUp || tile.kind == TonightKind.NewEpisode
    MosaicTile(
        title = item.cardTitle,
        eyebrow = tile.eyebrow(),
        line = item.tonightLine(),
        large = large,
        progress = item.progress,
        onClick = { if (playable) navigation.play(item) else navigation.open(item) },
        focusRequester = focusRequester,
        focus = rememberCineFocus(tag),
        logo = if (large) rememberArt(item.images.anyLogo, sources, ContentScale.Fit) else null,
        image = rememberArt(if (large) item.images.backdrop ?: item.landscapeImage else item.landscapeImage, sources),
        modifier =
            modifier
                .testTag(tag)
                .quickMenu(menu, item, anchor = "tonight/${item.id.value}", continueRow = tile.continueRow)
                .onFocusChanged { if (it.hasFocus) onFocused(item) },
    )
}

/** « Retirer de Continuer » takes a playback in progress out of the mosaic. */
private val TonightTile.continueRow: String?
    get() = HomeRows.TonightKeys.RESUME.takeIf { kind == TonightKind.Resume }

/** « S01 · E08 — titre » for an episode, else the year, the length and the rating. */
@Composable
private fun MediaItem.tonightLine(): String? = if (kind == ItemKind.Episode) cardSubtitle() else metadataLine()

/** Why the tile is there, in capitals: « REPRENDRE · RESTE 14 MIN », « AJOUTÉ HIER ». */
@Composable
private fun TonightTile.eyebrow(): String {
    val locale = LocalConfiguration.current.locales[0]
    val now = LocalClock.current.instant()
    val text =
        when (kind) {
            TonightKind.Resume -> {
                val left =
                    (item.runtime ?: kotlin.time.Duration.ZERO) -
                        (item.userData?.playbackPosition ?: kotlin.time.Duration.ZERO)
                stringResource(R.string.tonight_resume, left.coerceAtLeast(kotlin.time.Duration.ZERO).toDurationText())
            }

            TonightKind.NextUp -> {
                stringResource(R.string.tonight_next_up)
            }

            TonightKind.NewEpisode -> {
                stringResource(R.string.tonight_new_episode)
            }

            TonightKind.Added -> {
                val zone = ZoneId.systemDefault()
                val days =
                    item.dateAdded?.let {
                        java.time.temporal.ChronoUnit.DAYS
                            .between(it.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate())
                    }
                when {
                    days == null -> stringResource(R.string.tonight_added)
                    days <= 0 -> stringResource(R.string.tonight_added_today)
                    days == 1L -> stringResource(R.string.tonight_added_yesterday)
                    days < WEEK -> stringResource(R.string.tonight_added_this_week)
                    days < MONTH -> stringResource(R.string.tonight_added_this_month)
                    else -> stringResource(R.string.tonight_added)
                }
            }

            TonightKind.Rediscover -> {
                val last = item.userData?.lastPlayed?.takeIf { item.userData?.played == true }
                if (last == null) {
                    stringResource(R.string.tonight_never_watched)
                } else {
                    val zone = ZoneId.systemDefault()
                    val months =
                        java.time.Period
                            .between(last.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate())
                            .toTotalMonths()
                            .toInt()
                    if (months >= MONTHS_PER_YEAR) {
                        val years = months / MONTHS_PER_YEAR
                        pluralStringResource(R.plurals.tonight_not_watched_years, years, years)
                    } else {
                        pluralStringResource(R.plurals.tonight_not_watched_months, months, months)
                    }
                }
            }
        }
    return text.uppercase(locale)
}

private const val WEEK = 7L
private const val MONTH = 31L
private const val MONTHS_PER_YEAR = 12
