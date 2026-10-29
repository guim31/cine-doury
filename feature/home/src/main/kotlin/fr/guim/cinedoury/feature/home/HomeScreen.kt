package fr.guim.cinedoury.feature.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.home.CardStyle
import fr.guim.cinedoury.core.designsystem.component.AmbientBackdrop
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.Featured
import fr.guim.cinedoury.core.designsystem.component.FeaturedBackdrop
import fr.guim.cinedoury.core.designsystem.component.FeaturedBanner
import fr.guim.cinedoury.core.designsystem.component.FocusPivot
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.MessagePanel
import fr.guim.cinedoury.core.designsystem.component.PrimaryButton
import fr.guim.cinedoury.core.designsystem.component.ScrolledByIndex
import fr.guim.cinedoury.core.designsystem.component.SecondaryButton
import fr.guim.cinedoury.core.designsystem.component.SkeletonLine
import fr.guim.cinedoury.core.designsystem.component.featuredBannerMinHeight
import fr.guim.cinedoury.core.designsystem.component.mosaicFocusRoom
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.textLineHeight
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.ui.AppRail
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.ImageSources
import fr.guim.cinedoury.core.ui.QuickMenuHost
import fr.guim.cinedoury.core.ui.QuickMenuNavigation
import fr.guim.cinedoury.core.ui.QuickMenuState
import fr.guim.cinedoury.core.ui.RailTarget
import fr.guim.cinedoury.core.ui.RemoteIndicator
import fr.guim.cinedoury.core.ui.SourceErrorPanel
import fr.guim.cinedoury.core.ui.ambience
import fr.guim.cinedoury.core.ui.cardTitle
import fr.guim.cinedoury.core.ui.catchLine
import fr.guim.cinedoury.core.ui.imageSource
import fr.guim.cinedoury.core.ui.metadataLine
import fr.guim.cinedoury.core.ui.rememberArt
import fr.guim.cinedoury.core.ui.rememberQuickMenu
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** What the home asks the app to open. */
interface HomeNavigation : QuickMenuNavigation {
    fun navigate(target: RailTarget)

    /** The details of [item] (J3). */
    override fun open(item: MediaItem)

    /** Plays or resumes [item] (J4). */
    fun play(item: MediaItem)

    /** Plays [request]: from the start, from the menu of a card (essai réel 5). */
    override fun play(request: PlaybackRequest) = play(request.item)

    fun customize()

    /** « Retour » from the rail: the app closes, without asking (essai réel 4). */
    fun quit()

    fun sessionEnded()
}

@Composable
fun HomeRoute(
    navigation: HomeNavigation,
    viewModel: HomeViewModel = koinViewModel(),
    sessions: SessionManager = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.sessionEnded) { navigation.sessionEnded() }
    // Back from another screen: the rows may have changed (a film watched).
    LaunchedEffect(Unit) { viewModel.refresh() }
    // The start is over once the rows show: the « fully drawn » time of a cold start (SPEC.md § 4.16).
    ReportDrawnWhen { state.rows != null }
    HomeScreen(
        state = state,
        sources = sessions::imageSource,
        navigation = navigation,
        onRetry = viewModel::refresh,
    )
}

/** Preview tags of the home, for tests and screenshots. */
object HomePreview {
    const val PLAY = "home-play"

    /** The banner at the top of the home (title, line of metadata, buttons). */
    const val BANNER = "home-banner"
    const val DETAILS = "home-details"

    /** The mosaic « Ce soir » (essai réel 5). */
    const val TONIGHT = "home-tonight"

    /** A tile of the mosaic: 0 is the large one, the first focus of the home. */
    fun tonight(index: Int) = "tonight-$index"

    fun card(
        row: String,
        index: Int,
    ) = "card-$row-$index"

    /** The title of a row (essai réel 4: it must not move with the focus). */
    fun rowTitle(row: String) = "row-title-$row"
}

/**
 * The home (`UI.md` § 8, `FONCTIONNALITES.md` § 2): the ambient backdrop and the picture of the
 * focused title behind everything, the featured banner at the top, then the rows on the rest of the
 * screen. The banner follows the focus, never a carousel.
 *
 * « Retour »: first back to the top of the page, then into the rail, then out of the app, without a
 * dialog (essai réel 4: confirming was a chore).
 *
 * @param previewFeatured the title of the banner when nothing has the focus (screenshots).
 * @param previewRail draws the rail open on that entry (screenshots).
 * @param previewScrolledRow the row of that key scrolled to the left, under where the rail is (screenshots).
 */
@Composable
fun HomeScreen(
    state: HomeState,
    sources: ImageSources,
    navigation: HomeNavigation,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    previewFeatured: MediaItem? = null,
    previewRail: RailTarget? = null,
    previewScrolledRow: String? = null,
) {
    val rows = state.rows
    // Read only where it is drawn (banner, backdrops): a step of the focus recomposes them, not the
    // whole home and its rows (perf/fluidite).
    val focusedItem = remember { mutableStateOf<MediaItem?>(null) }
    var focusedRow by rememberSaveable { mutableStateOf<String?>(null) }
    var railFocused by remember { mutableStateOf(false) }
    val tonight = state.tonight
    val featuredItem = {
        focusedItem.value ?: previewFeatured ?: tonight?.tiles?.firstOrNull()?.item
            ?: rows?.firstOrNull()?.items?.firstOrNull()
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val play = remember { FocusRequester() }
    val rail = remember { FocusRequester() }
    val rowRequesters = remember { mutableMapOf<String, FocusRequester>() }
    // « Bandeau à la une » off (`SPEC.md` § 4.17): the rows from the top, the first one focused. The
    // skeletons, an error or an empty home still use the space of the banner.
    val banner = state.featuredBanner || rows.isNullOrEmpty()
    val mainTile = remember { FocusRequester() }
    val top =
        when {
            tonight != null -> mainTile
            banner -> play
            else -> rowRequesters.requester(rows.orEmpty().first().key)
        }

    BackHandler {
        when {
            railFocused -> {
                navigation.quit()
            }

            focusedRow != null || listState.isScrolled -> {
                focusedRow = null
                scope.launch {
                    listState.animateScrollToItem(0)
                    runCatching { top.requestFocus() }
                }
            }

            else -> {
                runCatching { rail.requestFocus() }
            }
        }
    }

    val shared = remember(rows) { rows.orEmpty().firstAppearances() }
    val menu = rememberQuickMenu()
    Box(modifier = modifier.fillMaxSize()) {
        HomeAmbience(featuredItem)
        val scrollToRow: (Int) -> Unit =
            remember(listState, scope) { { index -> scope.launch { listState.animateScrollToItem(index) } } }
        // The rows arrive in steps (the cache, then the server): a lazy list keeps its first row by key,
        // and would stay on a row read first while others came above it. Scrolled by index, the list is
        // put back on the focused row, or at its top.
        val rowKeys = remember(rows) { rows?.map { it.key } }
        LaunchedEffect(rowKeys, banner, tonight != null) {
            if (rowKeys == null) return@LaunchedEffect
            if (tonight != null) {
                // The mosaic is the first item of the list: a row stands one further.
                listState.scrollToItem(focusedRow?.let { rowKeys.indexOf(it) + 1 }?.coerceAtLeast(0) ?: 0)
            } else if (banner) {
                listState.scrollToItem(rowKeys.indexOf(focusedRow).coerceAtLeast(0))
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (tonight != null) {
                // The mosaic « Ce soir » (essai réel 5): above a row and a half, as the banner was; it
                // scrolls away by index when the focus goes down into the rows, and comes back with it.
                // Its top stands lower by the room of the focus: the list clips at the safe area, and a
                // focused tile grows and draws its outline and halo above itself (1.0.0: cut on the Shield).
                val available = maxHeight - homeRowsArea(rows) - CineSpacing.safeVertical - RowGap
                val room = mosaicFocusRoom(available)
                val mosaic = (available - room).coerceAtLeast(tonightMinHeight())
                ScrolledByIndex {
                    HomeRows(
                        state = state,
                        listState = listState,
                        shared = shared,
                        sources = sources,
                        navigation = navigation,
                        rowRequesters = rowRequesters,
                        menu = menu,
                        onFocused = { row, item ->
                            focusedItem.value = item
                            focusedRow = row
                        },
                        onRowFocused = scrollToRow,
                        top = CineSpacing.safeVertical,
                        // ↑ from the first row: back to the mosaic, scrolled out of the list (the 2D
                        // search would find the rail first, seen on the emulator).
                        onUpFromFirstRow = {
                            scope.launch {
                                listState.scrollToItem(0)
                                // The mosaic composes again a frame or two later.
                                repeat(UP_ATTEMPTS) {
                                    withFrameNanos { }
                                    if (runCatching { mainTile.requestFocus() }.getOrDefault(false)) return@launch
                                }
                            }
                        },
                        leading = {
                            TonightMosaic(
                                state = tonight,
                                height = mosaic,
                                room = room,
                                sources = sources,
                                menu = menu,
                                navigation = navigation,
                                main = mainTile,
                                onFocused = { focusedItem.value = it },
                                modifier =
                                    Modifier.onFocusChanged {
                                        if (it.hasFocus) {
                                            focusedRow = null
                                            scrollToRow(0)
                                        }
                                    },
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else if (banner) {
                // The banner stands still (essai réel 3), on what a row and a half leave (essai réel 4):
                // the first row whole and the top half of the next under it, whatever the size, at
                // least its single band. The focused row goes to the top of their area, scrolled by its
                // index: following the bounds of the focused card, which grows with the focus, moved
                // its title.
                val heroHeight =
                    (maxHeight - homeRowsArea(rows)).coerceAtLeast(
                        featuredBannerMinHeight() + CineSpacing.safeVertical + CineSpacing.s,
                    )
                HomeBackdrop(
                    featuredItem,
                    sources,
                    Modifier.fillMaxWidth().height(maxHeight * CineSizes.HERO_HEIGHT_FRACTION),
                )
                Column(Modifier.fillMaxSize()) {
                    Hero(
                        state = state,
                        focused = featuredItem,
                        sources = sources,
                        navigation = navigation,
                        onRetry = onRetry,
                        play = play,
                        modifier =
                            Modifier
                                .height(heroHeight)
                                .testTag(HomePreview.BANNER)
                                .onFocusChanged {
                                    if (it.hasFocus) focusedRow = null
                                },
                    )
                    ScrolledByIndex {
                        HomeRows(
                            state = state,
                            listState = listState,
                            shared = shared,
                            sources = sources,
                            navigation = navigation,
                            rowRequesters = rowRequesters,
                            menu = menu,
                            onFocused = { row, item ->
                                focusedItem.value = item
                                focusedRow = row
                            },
                            onRowFocused = scrollToRow,
                            previewScrolledRow = previewScrolledRow,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            } else {
                FocusPivot(fraction = ROW_PIVOT) {
                    HomeRows(
                        state = state,
                        listState = listState,
                        shared = shared,
                        sources = sources,
                        navigation = navigation,
                        rowRequesters = rowRequesters,
                        menu = menu,
                        onFocused = { row, item ->
                            focusedItem.value = item
                            focusedRow = row
                        },
                        top = CineSpacing.safeVertical,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        if (state.remote) {
            RemoteIndicator(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = CineSpacing.safeVertical, end = CineSpacing.safeHorizontal),
            )
        }
        AppRail(
            state = state.rail,
            selected = RailTarget.Home,
            onNavigate = navigation::navigate,
            focusRequester = rail,
            // → from the open rail: the row left (it gives back its card), else the banner.
            onContentFocus = { (focusedRow?.let { rowRequesters.requester(it) } ?: top).requestFocus() },
            previewFocused = previewRail,
            modifier = Modifier.onFocusChanged { railFocused = it.hasFocus },
        )
        // OK held on a card (essai réel 5).
        QuickMenuHost(menu, navigation)
    }
    // The first focus, and the focus found again when coming back: the row left, else « Lire ».
    // Made here, not when the lazy row composes (later): the requester is the same for both.
    val initial = focusedRow?.let { rowRequesters.requester(it) }
    InitialFocus(initial ?: top, key = rows?.isEmpty())
}

private val LazyListState.isScrolled: Boolean get() = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

/** Frames to wait for the mosaic to come back before it takes the focus. */
private const val UP_ATTEMPTS = 10

/** The rows start right of the closed rail. */
internal val ContentStart = CineSizes.railCollapsed + CineSpacing.m

/** Where the focused card of a row stands, from the top of the screen, without the banner. */
private const val ROW_PIVOT = 0.48f
private const val SKELETON_ROWS = 2

/**
 * Between two rows of the home: small, since a row already ends with the room of its captions, hidden
 * on a poster without the focus (essai réel 4: « un grand vide »). What it saves goes to the banner.
 */
private val RowGap = CineSpacing.s

/** How far [HomeScreen]'s `previewScrolledRow` is scrolled: two posters and a half. */
private val PREVIEW_ROW_SCROLL = CineSizes.posterNormal.width * 2.5f

/** The rows of the home in a list, and « Personnaliser l'accueil » at its end. */
@Composable
private fun HomeRows(
    state: HomeState,
    listState: LazyListState,
    shared: Map<String, Set<String>>,
    sources: ImageSources,
    navigation: HomeNavigation,
    rowRequesters: MutableMap<String, FocusRequester>,
    menu: QuickMenuState,
    onFocused: (row: String, item: MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    onRowFocused: ((index: Int) -> Unit)? = null,
    top: Dp = CineSpacing.none,
    previewScrolledRow: String? = null,
    onUpFromFirstRow: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val rows = state.rows
    // The mosaic before the rows: every row stands one further in the list.
    val first = if (leading != null) 1 else 0
    LazyColumn(
        state = listState,
        // Under the mosaic, the margin at the top clips the list: what scrolled away leaves no sliver.
        modifier = modifier.padding(top = if (leading != null) top else CineSpacing.none).testTag("home"),
        contentPadding = PaddingValues(top = if (leading != null) CineSpacing.none else top, bottom = CineSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        if (leading != null) item(key = "tonight", contentType = "tonight") { leading() }
        if (rows == null) {
            items(SKELETON_ROWS) { SkeletonRow() }
        } else {
            itemsIndexed(rows, key = { _, row -> row.key }, contentType = { _, row -> row.style }) { index, row ->
                val next = rows.getOrNull(index + 1)
                HomeRowView(
                    row = row,
                    next = next,
                    shared = shared[row.key].orEmpty(),
                    sources = sources,
                    restore = rowRequesters.requester(row.key),
                    menu = menu,
                    onFocused = { item -> onFocused(row.key, item) },
                    onOpen = navigation::open,
                    modifier =
                        Modifier
                            .onRowFocused(onRowFocused, first + index)
                            .upTo(onUpFromFirstRow.takeIf { index == 0 }),
                    previewScroll = if (row.key == previewScrolledRow) PREVIEW_ROW_SCROLL else CineSpacing.none,
                )
            }
            item(key = "customize", contentType = "customize") {
                SecondaryButton(
                    text = stringResource(R.string.action_customize),
                    icon = CineIcons.Edit,
                    onClick = navigation::customize,
                    modifier =
                        Modifier
                            .padding(start = ContentStart)
                            .onRowFocused(onRowFocused, first + rows.size)
                            .testTag("customize"),
                )
            }
        }
    }
}

/**
 * Under the banner, a row and a half (essai réel 4): the first row whole, its title, cards and captions
 * included, the gap between rows, then the top half of the second one, each at the height of its kind
 * of cards. Without rows yet, two rows of thumbnails, the shape of the skeletons.
 */
@Composable
private fun homeRowsArea(rows: List<HomeRow>?): Dp {
    val first = rows?.firstOrNull()?.style ?: CardStyle.Thumbnail
    val second = rows?.getOrNull(1)?.style ?: first
    return homeRowHeight(first) + RowGap + homeRowHeight(second) / 2
}

/**
 * The lowest the mosaic goes, larger text included: a small tile shows its pill, its title and its
 * line, twice, with the gap between them.
 */
@Composable
private fun tonightMinHeight(): Dp {
    val typography = CineTheme.typography
    val small =
        CineSpacing.s * 2 + CineSizes.progressHeight + textLineHeight(typography.minimum) +
            textLineHeight(typography.label) + textLineHeight(typography.caption) + CineSpacing.xs
    return small * 2 + CineSpacing.m
}

/** A row of the home: its title, the gap under it, a card and its caption of two lines (`HomeRowView`). */
@Composable
private fun homeRowHeight(style: CardStyle): Dp {
    val typography = CineTheme.typography
    val card = if (style == CardStyle.Poster) CineSizes.posterNormal else CineSizes.thumbnail
    return textLineHeight(typography.title2) + CineSpacing.rowTitleGap + card.height + CineSpacing.s +
        textLineHeight(typography.caption) * 2
}

/** ↑ inside this element goes to [target] instead of searching for what is above. */
private fun Modifier.upTo(target: (() -> Unit)?): Modifier =
    if (target == null) {
        this
    } else {
        onPreviewKeyEvent { event ->
            if (event.key != Key.DirectionUp) return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyDown) target()
            true
        }
    }

/** Tells [onRowFocused] when the focus comes into the element at [index] of the list. */
private fun Modifier.onRowFocused(
    onRowFocused: ((Int) -> Unit)?,
    index: Int,
): Modifier =
    if (onRowFocused == null) {
        this
    } else {
        onFocusChanged { if (it.hasFocus) onRowFocused(index) }
    }

/** The ambient backdrop of the focused title. */
@Composable
private fun HomeAmbience(item: () -> MediaItem?) {
    AmbientBackdrop(ambience = item()?.ambience)
}

/** The picture of the focused title, at the top of the home. */
@Composable
private fun HomeBackdrop(
    item: () -> MediaItem?,
    sources: ImageSources,
    modifier: Modifier = Modifier,
) {
    FeaturedBackdrop(item()?.toFeatured(sources), modifier)
}

@Composable
private fun Hero(
    state: HomeState,
    focused: () -> MediaItem?,
    sources: ImageSources,
    navigation: HomeNavigation,
    onRetry: () -> Unit,
    play: FocusRequester,
    modifier: Modifier = Modifier,
) {
    // Its content at the bottom of the height it is given, under the top of the screen: the banner
    // chooses what fits (FeaturedBanner). An error, larger, goes up under the top, never squeezed.
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = ContentStart,
                    top = CineSpacing.safeVertical,
                    end = CineSpacing.safeHorizontal,
                    bottom = CineSpacing.s,
                ),
    ) {
        val error = state.error
        val rows = state.rows
        when {
            rows.isNullOrEmpty() && error != null -> {
                SourceErrorPanel(
                    error = error,
                    onRetry = onRetry,
                    retryFocusRequester = play,
                    modifier =
                        Modifier
                            .align(
                                Alignment.BottomStart,
                            ).wrapContentHeight(Alignment.Bottom, unbounded = true),
                )
            }

            rows == null -> {
                HeroSkeleton(Modifier.align(Alignment.BottomStart))
            }

            rows.isEmpty() -> {
                MessagePanel(
                    title = stringResource(R.string.home_empty_title),
                    message = stringResource(R.string.home_empty_message),
                    actionLabel = stringResource(R.string.action_customize),
                    onAction = navigation::customize,
                    icon = CineIcons.Movies,
                    actionFocusRequester = play,
                    modifier =
                        Modifier
                            .align(
                                Alignment.BottomStart,
                            ).wrapContentHeight(Alignment.Bottom, unbounded = true),
                )
            }

            else -> {
                val item = focused()
                FeaturedBanner(
                    featured = item?.toFeatured(sources),
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomStart),
                ) {
                    if (item != null) {
                        PrimaryButton(
                            text =
                                stringResource(
                                    if (item.progress !=
                                        null
                                    ) {
                                        R.string.action_resume
                                    } else {
                                        R.string.action_play
                                    },
                                ),
                            icon = CineIcons.Play,
                            onClick = { navigation.play(item) },
                            focusRequester = play,
                            focus = rememberCineFocus(HomePreview.PLAY),
                            modifier = Modifier.testTag(HomePreview.PLAY),
                        )
                        SecondaryButton(
                            text = stringResource(R.string.action_details),
                            icon = CineIcons.Info,
                            onClick = { navigation.open(item) },
                            focus = rememberCineFocus(HomePreview.DETAILS),
                            modifier = Modifier.testTag(HomePreview.DETAILS),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSkeleton(modifier: Modifier = Modifier) {
    // The single band of the banner (FeaturedBanner): the title, then the buttons and the metadata.
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
        SkeletonLine(CineSizes.heroTextWidth * HERO_SKELETON_TITLE, Modifier.height(CineSizes.buttonHeight))
        Row(
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonLine(CineSizes.avatarLarge, Modifier.height(CineSizes.buttonHeight))
            SkeletonLine(CineSizes.avatarLarge, Modifier.height(CineSizes.buttonHeight))
            SkeletonLine(CineSizes.heroTextWidth / 2)
        }
    }
}

private const val HERO_SKELETON_TITLE = 0.7f

/** The banner of [this]: its backdrop (or its series'), its logo, its line of metadata. */
@Composable
private fun MediaItem.toFeatured(sources: ImageSources): Featured {
    val backdrop = rememberArt(images.backdrop, sources)
    val logo = rememberArt(images.anyLogo, sources, contentScale = ContentScale.Fit)
    return Featured(
        key = id.value,
        title = cardTitle,
        metadata = metadataLine(),
        tagline = catchLine,
        backdrop = backdrop,
        logo = logo,
    )
}

/**
 * For each row, the items seen there first: only their card flies to the details screen, so that a
 * title in two rows has a single shared poster on the screen.
 */
private fun List<HomeRow>.firstAppearances(): Map<String, Set<String>> {
    val seen = mutableSetOf<String>()
    return associate { row ->
        row.key to
            row.items
                .map { it.id.value }
                .filter(seen::add)
                .toSet()
    }
}

/** The requester of a row, made on first use: outside composition, where it may not be built. */
private fun MutableMap<String, FocusRequester>.requester(
    key: String,
): FocusRequester = getOrPut(key) { FocusRequester() }
