package fr.guim.cinedoury.feature.home.mobile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.home.CardStyle
import fr.guim.cinedoury.core.data.home.HomeRowType
import fr.guim.cinedoury.core.data.home.TonightKind
import fr.guim.cinedoury.core.data.home.TonightTile
import fr.guim.cinedoury.core.designsystem.component.CardProgressBar
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.mobile.MobileArtwork
import fr.guim.cinedoury.core.designsystem.mobile.MobileMessage
import fr.guim.cinedoury.core.designsystem.mobile.MobilePrimaryButton
import fr.guim.cinedoury.core.designsystem.mobile.MobileRowTitle
import fr.guim.cinedoury.core.designsystem.mobile.MobileSkeleton
import fr.guim.cinedoury.core.designsystem.mobile.mobileCardHeight
import fr.guim.cinedoury.core.designsystem.mobile.mobileGutter
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.MobileSizes
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.ImageSources
import fr.guim.cinedoury.core.ui.cardTitle
import fr.guim.cinedoury.core.ui.imageSource
import fr.guim.cinedoury.core.ui.landscapeImage
import fr.guim.cinedoury.core.ui.mobile.MobileItemCard
import fr.guim.cinedoury.core.ui.mobile.MobileQuickMenu
import fr.guim.cinedoury.core.ui.mobile.MobileQuickMenuHost
import fr.guim.cinedoury.core.ui.mobile.MobileSourceError
import fr.guim.cinedoury.core.ui.mobile.rememberMobileQuickMenu
import fr.guim.cinedoury.core.ui.rememberArt
import fr.guim.cinedoury.feature.home.HomeNavigation
import fr.guim.cinedoury.feature.home.HomeRow
import fr.guim.cinedoury.feature.home.HomeState
import fr.guim.cinedoury.feature.home.HomeViewModel
import fr.guim.cinedoury.feature.home.R
import fr.guim.cinedoury.feature.home.TonightState
import fr.guim.cinedoury.feature.home.continueRow
import fr.guim.cinedoury.feature.home.eyebrow
import fr.guim.cinedoury.feature.home.title
import fr.guim.cinedoury.feature.home.tonightLine
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import fr.guim.cinedoury.core.ui.R as UiR

/** Tags of the home of the phone, for tests and screenshots. */
object MobileHomeTags {
    const val SCREEN = "home"
    const val SKELETON = "home-skeleton"

    fun tonight(index: Int) = "tonight-$index"

    fun row(key: String) = "row-$key"

    fun card(
        row: String,
        index: Int,
    ) = "card-$row-$index"
}

@Composable
fun MobileHomeRoute(
    navigation: HomeNavigation,
    viewModel: HomeViewModel = koinViewModel(),
    sessions: SessionManager = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.sessionEnded) { navigation.sessionEnded() }
    // Back from another screen: the rows may have changed (a film watched).
    LaunchedEffect(Unit) { viewModel.refresh() }
    MobileHomeScreen(state, sessions::imageSource, navigation, onRetry = viewModel::refresh)
}

/**
 * The home of the phone (M1, `UI.md` § 9): the mosaic « Ce soir » at the top, then the rows of the
 * TV, the same ones filled by the same rules (`SPEC.md` § 4.23) and arranged the same way (the
 * personalisation of the profile), each scrolled sideways under the finger.
 *
 * « Ce soir » held upright: the large tile across the width, the four small ones in a grid of two
 * columns under it, all at once (a carousel would hide four of the five titles behind a swipe). Wider
 * (a phone on its side, a tablet), the TV's own layout: the large tile on the left, the small ones in
 * two lines on the right. A tap plays what is in progress or next, opens the details of the others; a
 * long press opens the menu of the card (`SPEC.md` § 4.23 C), as everywhere.
 */
@Composable
fun MobileHomeScreen(
    state: HomeState,
    sources: ImageSources,
    navigation: HomeNavigation,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    menu: MobileQuickMenu = rememberMobileQuickMenu(),
) {
    val rows = state.rows
    val error = state.error
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(CineTheme.colors.background).testTag(MobileHomeTags.SCREEN),
    ) {
        val gutter = mobileGutter(maxWidth)
        val wide = maxWidth >= MobileSizes.compactWidth
        when {
            rows == null -> {
                HomeSkeleton(gutter)
            }

            rows.isEmpty() && state.tonight == null && error != null -> {
                Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
                    MobileSourceError(error, onRetry)
                }
            }

            rows.isEmpty() && state.tonight == null -> {
                Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
                    MobileMessage(
                        stringResource(R.string.home_empty_title),
                        stringResource(R.string.home_empty_message),
                        icon = CineIcons.Movies,
                    )
                }
            }

            else -> {
                LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = CineSpacing.m),
                    verticalArrangement = Arrangement.spacedBy(CineSpacing.m),
                ) {
                    item(key = "header", contentType = "header") {
                        Text(
                            stringResource(UiR.string.brand_name),
                            style = CineTheme.typography.title1,
                            color = CineTheme.colors.accent,
                            modifier =
                                Modifier.statusBarsPadding().padding(
                                    horizontal = gutter,
                                    vertical = CineSpacing.xs,
                                ),
                        )
                    }
                    state.tonight?.let { tonight ->
                        item(key = "tonight", contentType = "tonight") {
                            TonightTiles(tonight, wide, gutter, sources, navigation, menu)
                        }
                    }
                    items(rows, key = { it.key }, contentType = { it.style }) { row ->
                        RowOfCards(row, gutter, sources, navigation, menu)
                    }
                }
            }
        }
    }
    MobileQuickMenuHost(menu, navigation)
}

@Composable
private fun RowOfCards(
    row: HomeRow,
    gutter: Dp,
    sources: ImageSources,
    navigation: HomeNavigation,
    menu: MobileQuickMenu,
) {
    val poster = row.style == CardStyle.Poster
    Column(Modifier.testTag(MobileHomeTags.row(row.key))) {
        MobileRowTitle(row.title(), Modifier.padding(horizontal = gutter))
        LazyRow(
            contentPadding = PaddingValues(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
        ) {
            items(row.items.size, key = { row.items[it].id.value }, contentType = { "card" }) { index ->
                val item = row.items[index]
                MobileItemCard(
                    item = item,
                    sources = sources,
                    onClick = { navigation.open(item) },
                    poster = poster,
                    menu = menu,
                    continueRow = row.resumeRows[item.id] ?: row.key.takeIf { row.type == HomeRowType.Resume },
                    tag = MobileHomeTags.card(row.key, index),
                )
            }
        }
    }
}

@Composable
private fun TonightTiles(
    state: TonightState,
    wide: Boolean,
    gutter: Dp,
    sources: ImageSources,
    navigation: HomeNavigation,
    menu: MobileQuickMenu,
) {
    val tiles = state.tiles
    val large = tiles.first()
    val small = tiles.drop(1)
    val spacing = CineSpacing.xs
    if (wide) {
        Row(Modifier.padding(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(spacing)) {
            Tile(large, 0, large = true, sources, navigation, menu, Modifier.weight(1f))
            SmallGrid(small, sources, navigation, menu, Modifier.weight(1f))
        }
    } else {
        Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(spacing)) {
            Tile(large, 0, large = true, sources, navigation, menu, Modifier.fillMaxWidth())
            SmallGrid(small, sources, navigation, menu, Modifier.fillMaxWidth())
        }
    }
}

/** The small tiles in two columns, in the order of the mosaic. */
@Composable
private fun SmallGrid(
    tiles: List<TonightTile>,
    sources: ImageSources,
    navigation: HomeNavigation,
    menu: MobileQuickMenu,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
        tiles.chunked(2).forEachIndexed { line, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
                pair.forEachIndexed { column, tile ->
                    Tile(tile, 1 + line * 2 + column, large = false, sources, navigation, menu, Modifier.weight(1f))
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(
    tile: TonightTile,
    index: Int,
    large: Boolean,
    sources: ImageSources,
    navigation: HomeNavigation,
    menu: MobileQuickMenu,
    modifier: Modifier,
) {
    val item = tile.item
    val colors = CineTheme.colors
    val typography = CineTheme.typography
    val playable =
        tile.kind == TonightKind.Resume || tile.kind == TonightKind.NextUp || tile.kind == TonightKind.NewEpisode
    val image = rememberArt(if (large) item.images.backdrop ?: item.landscapeImage else item.landscapeImage, sources)
    val logo = if (large) rememberArt(item.images.anyLogo, sources, ContentScale.Fit) else null
    val eyebrow = tile.eyebrow()
    Box(
        modifier =
            modifier
                .aspectRatio(MobileSizes.BACKDROP_RATIO)
                .clip(TileShape)
                .background(colors.surface1)
                .combinedClickable(
                    role = Role.Button,
                    onClick = { if (playable) navigation.play(item) else navigation.open(item) },
                    onLongClick = { menu.open(item, tile.continueRow) },
                ).semantics(mergeDescendants = true) { contentDescription = "${item.cardTitle}, $eyebrow" }
                .testTag(MobileHomeTags.tonight(index)),
    ) {
        if (image != null) image() else MobileArtwork(item.cardTitle, compact = !large)
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, colors.playerVeil))),
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(if (large) CineSpacing.s else CineSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.xs / 4),
        ) {
            Text(
                eyebrow,
                style = typography.minimum,
                color = colors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (logo != null && large) {
                Box(Modifier.height(MobileSizes.logoMax.height / 2).width(MobileSizes.logoMax.width / 2)) { logo() }
            } else {
                Text(
                    item.cardTitle,
                    style = if (large) typography.title1 else typography.caption,
                    color = colors.text,
                    maxLines = if (large) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (large) {
                item.tonightLine()?.let { Text(it, style = typography.caption, color = colors.text2, maxLines = 1) }
                if (playable) {
                    MobilePrimaryButton(
                        stringResource(
                            if (tile.kind ==
                                TonightKind.Resume
                            ) {
                                UiR.string.quick_resume
                            } else {
                                UiR.string.quick_play
                            },
                        ),
                        { navigation.play(item) },
                        Modifier.padding(top = CineSpacing.xs),
                        icon = CineIcons.Play,
                    )
                }
            }
        }
        item.progress?.let { CardProgressBar(it, Modifier.align(Alignment.BottomCenter)) }
    }
}

private val TileShape = RoundedCornerShape(MobileSizes.cardCorner)

/** Skeletons of the shape of the content while nothing is known (`UI.md` principle 2). */
@Composable
private fun HomeSkeleton(gutter: Dp) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = gutter)
            .testTag(MobileHomeTags.SKELETON),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.m),
    ) {
        MobileSkeleton(Modifier.fillMaxWidth().aspectRatio(MobileSizes.BACKDROP_RATIO))
        repeat(2) {
            MobileSkeleton(Modifier.width(MobileSizes.posterNormal.width * 2).heightIn(min = CineSpacing.m))
            Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.s)) {
                repeat(SKELETON_CARDS) {
                    MobileSkeleton(
                        Modifier
                            .width(
                                MobileSizes.posterNormal.width,
                            ).height(mobileCardHeight(MobileSizes.posterNormal)),
                    )
                }
            }
        }
    }
}

private const val SKELETON_CARDS = 4
