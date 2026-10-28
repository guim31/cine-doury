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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.designsystem.component.AmbientBackdrop
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.ConfirmDialog
import fr.guim.cinedoury.core.designsystem.component.Featured
import fr.guim.cinedoury.core.designsystem.component.FeaturedBackdrop
import fr.guim.cinedoury.core.designsystem.component.FeaturedBanner
import fr.guim.cinedoury.core.designsystem.component.FocusPivot
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.MessagePanel
import fr.guim.cinedoury.core.designsystem.component.PrimaryButton
import fr.guim.cinedoury.core.designsystem.component.SecondaryButton
import fr.guim.cinedoury.core.designsystem.component.SkeletonLine
import fr.guim.cinedoury.core.designsystem.component.featuredTitleHeight
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.ui.AppRail
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.ImageSources
import fr.guim.cinedoury.core.ui.RailTarget
import fr.guim.cinedoury.core.ui.RemoteIndicator
import fr.guim.cinedoury.core.ui.SourceErrorPanel
import fr.guim.cinedoury.core.ui.ambience
import fr.guim.cinedoury.core.ui.cardTitle
import fr.guim.cinedoury.core.ui.imageSource
import fr.guim.cinedoury.core.ui.metadataLine
import fr.guim.cinedoury.core.ui.rememberArt
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** What the home asks the app to open. */
interface HomeNavigation {
    fun navigate(target: RailTarget)

    /** The details of [item] (J3). */
    fun open(item: MediaItem)

    /** Plays or resumes [item] (J4). */
    fun play(item: MediaItem)

    fun customize()

    /** « Quitter Ciné Doury ? » was confirmed. */
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
    const val QUIT_CANCEL = "home-quit-cancel"

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
 * « Retour »: first back to the top of the page, then into the rail, then « Quitter Ciné Doury ? ».
 *
 * @param previewFeatured the title of the banner when nothing has the focus (screenshots).
 * @param previewRail draws the rail open on that entry (screenshots).
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
    previewQuit: Boolean = false,
) {
    val rows = state.rows
    // Read only where it is drawn (banner, backdrops): a step of the focus recomposes them, not the
    // whole home and its rows (perf/fluidite).
    val focusedItem = remember { mutableStateOf<MediaItem?>(null) }
    var focusedRow by rememberSaveable { mutableStateOf<String?>(null) }
    var railFocused by remember { mutableStateOf(false) }
    var quitAsked by rememberSaveable { mutableStateOf(previewQuit) }
    // « Annuler » gives the focus back to the rail, where « Retour » asked to quit.
    var quitCancelled by remember { mutableIntStateOf(0) }
    val featuredItem = { focusedItem.value ?: previewFeatured ?: rows?.firstOrNull()?.items?.firstOrNull() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val play = remember { FocusRequester() }
    val rail = remember { FocusRequester() }
    val rowRequesters = remember { mutableMapOf<String, FocusRequester>() }
    // « Bandeau à la une » off (`SPEC.md` § 4.17): the rows from the top, the first one focused. The
    // skeletons, an error or an empty home still use the space of the banner.
    val banner = state.featuredBanner || rows.isNullOrEmpty()
    val top = if (banner) play else rowRequesters.requester(rows.orEmpty().first().key)

    BackHandler {
        when {
            quitAsked -> {
                quitAsked = false
                quitCancelled++
            }

            railFocused -> {
                quitAsked = true
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
    Box(modifier = modifier.fillMaxSize()) {
        HomeAmbience(featuredItem)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (banner) {
                // The banner stands still (essai réel 3), at its own height at the top: the rows take
                // the rest of the screen, so that the next ones show under the focused one (essai réel
                // 4). The focused row goes to the top of their area, its cards under its title.
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
                                .testTag(HomePreview.BANNER)
                                .onFocusChanged {
                                    if (it.hasFocus) focusedRow = null
                                },
                    )
                    FocusPivot(offset = rowCardsTop()) {
                        HomeRows(
                            state = state,
                            listState = listState,
                            shared = shared,
                            sources = sources,
                            navigation = navigation,
                            rowRequesters = rowRequesters,
                            onFocused = { row, item ->
                                focusedItem.value = item
                                focusedRow = row
                            },
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
        if (quitAsked) {
            val cancel = remember { FocusRequester() }
            InitialFocus(cancel)
            ConfirmDialog(
                title = stringResource(R.string.quit_title),
                message = stringResource(R.string.quit_message),
                confirmLabel = stringResource(R.string.quit_confirm),
                cancelLabel = stringResource(R.string.quit_cancel),
                onConfirm = navigation::quit,
                onCancel = {
                    quitAsked = false
                    quitCancelled++
                },
                destructive = false,
                cancelFocusRequester = cancel,
                cancelFocus = rememberCineFocus(HomePreview.QUIT_CANCEL),
                modifier = Modifier.testTag("quit-dialog"),
            )
        }
    }
    LaunchedEffect(quitCancelled) {
        if (quitCancelled > 0) {
            withFrameNanos { }
            runCatching { rail.requestFocus() }
        }
    }
    // The first focus, and the focus found again when coming back: the row left, else « Lire ».
    // Made here, not when the lazy row composes (later): the requester is the same for both.
    val initial = focusedRow?.let { rowRequesters.requester(it) }
    // Not keyed on the quit dialog: closing it gives the focus back to the rail, not to « Lire ».
    InitialFocus(initial ?: top, key = rows?.isEmpty())
}

private val LazyListState.isScrolled: Boolean get() = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

/** The rows start right of the closed rail. */
internal val ContentStart = CineSizes.railCollapsed + CineSpacing.m

/** Where the focused card of a row stands, from the top of the screen, without the banner. */
private const val ROW_PIVOT = 0.48f
private const val SKELETON_ROWS = 2

/** The rows of the home in a list, and « Personnaliser l'accueil » at its end. */
@Composable
private fun HomeRows(
    state: HomeState,
    listState: LazyListState,
    shared: Map<String, Set<String>>,
    sources: ImageSources,
    navigation: HomeNavigation,
    rowRequesters: MutableMap<String, FocusRequester>,
    onFocused: (row: String, item: MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    top: Dp = CineSpacing.none,
) {
    val rows = state.rows
    LazyColumn(
        state = listState,
        modifier = modifier.testTag("home"),
        contentPadding = PaddingValues(top = top, bottom = CineSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.l),
    ) {
        if (rows == null) {
            items(SKELETON_ROWS) { SkeletonRow() }
        } else {
            items(rows, key = { it.key }, contentType = { it.style }) { row ->
                val next = rows.getOrNull(rows.indexOf(row) + 1)
                HomeRowView(
                    row = row,
                    next = next,
                    shared = shared[row.key].orEmpty(),
                    sources = sources,
                    restore = rowRequesters.requester(row.key),
                    onFocused = { item -> onFocused(row.key, item) },
                    onOpen = navigation::open,
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
                            .testTag("customize"),
                )
            }
        }
    }
}

/** From the top of a row to the top of its cards: its title and the gap under it. */
@Composable
private fun rowCardsTop(): Dp =
    with(LocalDensity.current) {
        CineTheme.typography.title2.lineHeight
            .toDp()
    } + CineSpacing.rowTitleGap

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
    // At its own height, under the top of the screen: the same for every title (FeaturedBanner).
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
                    modifier = Modifier.align(Alignment.BottomStart),
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
                    modifier = Modifier.align(Alignment.BottomStart),
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
    // The room of the banner (FeaturedBanner): the zone of the title, then the buttons and the metadata.
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
        Box(Modifier.height(featuredTitleHeight()), contentAlignment = Alignment.BottomStart) {
            SkeletonLine(CineSizes.heroTextWidth * HERO_SKELETON_TITLE, Modifier.height(CineSizes.buttonHeight))
        }
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
