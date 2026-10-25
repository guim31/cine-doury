package fr.guim.cinedoury.feature.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.ui.AppRail
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.ImageSources
import fr.guim.cinedoury.core.ui.RailTarget
import fr.guim.cinedoury.core.ui.RemoteIndicator
import fr.guim.cinedoury.core.ui.SourceErrorPanel
import fr.guim.cinedoury.core.ui.ambience
import fr.guim.cinedoury.core.ui.cardTitle
import fr.guim.cinedoury.core.ui.catchLine
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
    const val DETAILS = "home-details"
    const val QUIT_CANCEL = "home-quit-cancel"

    fun card(
        row: String,
        index: Int,
    ) = "card-$row-$index"
}

/**
 * The home (`UI.md` § 8, `FONCTIONNALITES.md` § 2): the ambient backdrop and the picture of the
 * focused title behind everything, the featured banner on 60 % of the screen, then the rows. The
 * banner follows the focus, never a carousel.
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
    var focusedItem by remember { mutableStateOf<MediaItem?>(null) }
    var focusedRow by rememberSaveable { mutableStateOf<String?>(null) }
    var railFocused by remember { mutableStateOf(false) }
    var quitAsked by rememberSaveable { mutableStateOf(previewQuit) }
    // « Annuler » gives the focus back to the rail, where « Retour » asked to quit.
    var quitCancelled by remember { mutableIntStateOf(0) }
    val featuredItem = focusedItem ?: previewFeatured ?: rows?.firstOrNull()?.items?.firstOrNull()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val play = remember { FocusRequester() }
    val rail = remember { FocusRequester() }
    val rowRequesters = remember { mutableMapOf<String, FocusRequester>() }

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
                    runCatching { play.requestFocus() }
                }
            }

            else -> {
                runCatching { rail.requestFocus() }
            }
        }
    }

    val featured = featuredItem?.toFeatured(sources)
    Box(modifier = modifier.fillMaxSize()) {
        AmbientBackdrop(ambience = featuredItem?.ambience)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val heroHeight = maxHeight * CineSizes.HERO_HEIGHT_FRACTION
            FeaturedBackdrop(featured, Modifier.fillMaxWidth().height(heroHeight))
            FocusPivot(fraction = ROW_PIVOT) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("home"),
                    contentPadding = PaddingValues(bottom = CineSpacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(CineSpacing.l),
                ) {
                    item(key = "hero", contentType = "hero") {
                        Hero(
                            state = state,
                            featured = featured,
                            item = featuredItem,
                            navigation = navigation,
                            onRetry = onRetry,
                            play = play,
                            modifier =
                                Modifier
                                    .height(heroHeight)
                                    .onFocusChanged {
                                        if (it.hasFocus) focusedRow = null
                                    },
                        )
                    }
                    if (rows == null) {
                        items(SKELETON_ROWS) { SkeletonRow() }
                    } else {
                        items(rows, key = { it.key }, contentType = { it.style }) { row ->
                            val next = rows.getOrNull(rows.indexOf(row) + 1)
                            HomeRowView(
                                row = row,
                                next = next,
                                sources = sources,
                                restore = rowRequesters.getOrPut(row.key) { FocusRequester() },
                                onFocused = { item ->
                                    focusedItem = item
                                    focusedRow = row.key
                                },
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
    val initial = focusedRow?.let { rowRequesters[it] }
    // Not keyed on the quit dialog: closing it gives the focus back to the rail, not to « Lire ».
    InitialFocus(initial ?: play, key = rows != null)
}

private val LazyListState.isScrolled: Boolean get() = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

/** The rows start right of the closed rail. */
internal val ContentStart = CineSizes.railCollapsed + CineSpacing.m

/** Where the focused card of a row stands, from the top of the screen. */
private const val ROW_PIVOT = 0.48f
private const val SKELETON_ROWS = 2

@Composable
private fun Hero(
    state: HomeState,
    featured: Featured?,
    item: MediaItem?,
    navigation: HomeNavigation,
    onRetry: () -> Unit,
    play: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().padding(start = ContentStart, bottom = CineSpacing.s)) {
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
                FeaturedBanner(featured = featured, modifier = Modifier.align(Alignment.BottomStart)) {
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CineSpacing.s)) {
        SkeletonLine(CineSizes.heroTextWidth * HERO_SKELETON_TITLE, Modifier.height(CineSizes.buttonHeight))
        SkeletonLine(CineSizes.heroTextWidth / 2)
        SkeletonLine(CineSizes.heroTextWidth * HERO_SKELETON_TAGLINE)
        Row(Modifier.padding(top = CineSpacing.xs), horizontalArrangement = Arrangement.spacedBy(CineSpacing.m)) {
            SkeletonLine(CineSizes.avatarLarge, Modifier.height(CineSizes.buttonHeight))
            SkeletonLine(CineSizes.avatarLarge, Modifier.height(CineSizes.buttonHeight))
        }
    }
}

private const val HERO_SKELETON_TITLE = 0.7f
private const val HERO_SKELETON_TAGLINE = 0.9f

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
