package fr.guim.cinedoury.feature.player

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.ConfirmDialog
import fr.guim.cinedoury.core.designsystem.component.LocalPreviewFocus
import fr.guim.cinedoury.core.designsystem.component.MessagePanel
import fr.guim.cinedoury.core.designsystem.component.NextEpisodeCard
import fr.guim.cinedoury.core.designsystem.component.PlaybackInfoSheet
import fr.guim.cinedoury.core.designsystem.component.PlayerProgressBar
import fr.guim.cinedoury.core.designsystem.component.PrimaryButton
import fr.guim.cinedoury.core.designsystem.component.RoundIconButton
import fr.guim.cinedoury.core.designsystem.component.StatusPill
import fr.guim.cinedoury.core.designsystem.component.TrickplayPreview
import fr.guim.cinedoury.core.designsystem.component.playerVeils
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.tabular
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.source.GestureLog
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.ImageSources
import fr.guim.cinedoury.core.ui.LocalClock
import fr.guim.cinedoury.core.ui.SourceErrorPanel
import fr.guim.cinedoury.core.ui.imageSource
import fr.guim.cinedoury.core.ui.landscapeImage
import fr.guim.cinedoury.core.ui.rememberArt
import fr.guim.cinedoury.core.ui.toTimeText
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import android.view.KeyEvent as NativeKeyEvent

/** The panels of the player, at the right of the picture. */
enum class PlayerPanel {
    /** Audio and subtitles, in two columns. */
    Tracks,
    Quality,
    Settings,
    Chapters,

    /** The size, height and background of the subtitles: a bar over the top of the picture. */
    SubtitleSize,
}

/** Preview tags of the player, for tests and screenshots. */
object PlayerPreview {
    const val PROGRESS = "player-progress"
    const val PLAY = "player-play"
    const val CHAPTERS = "player-chapters"
    const val TRACKS = "player-tracks"
    const val SUBTITLES = "player-subtitles"
    const val SUBTITLE_BAR = "player-subtitle-bar"
    const val QUALITY = "player-quality"
    const val SETTINGS = "player-settings"
    const val INFO = "player-info"
    const val INFO_BUTTON = "player-info-button"
    const val SKIP = "player-skip"
    const val NEXT = "player-next"
    const val STILL = "player-still"
    const val RETRY = "player-retry"

    /** The position over the progress bar, read from outside by `:benchmark` (the optimised APK plays). */
    const val POSITION = "player-position"
    const val ERROR = "player-error"
}

@Composable
fun PlayerRoute(
    request: PlaybackRequest,
    onClose: () -> Unit,
    onSessionEnded: () -> Unit,
    viewModel: PlayerViewModel = koinViewModel(key = "player-${request.item.id.value}") { parametersOf(request) },
    sessions: SessionManager = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.surface.collectAsStateWithLifecycle()
    CollectEvents(viewModel.closed) { onClose() }
    CollectEvents(viewModel.sessionEnded) { onSessionEnded() }
    KeepScreenOn()
    val video = state.stream?.source?.video
    MatchFrameRate(
        LocalActivity.current,
        video?.frameRate,
        state.settings.matchFrameRate,
        videoHeight = video?.height?.takeIf { state.settings.matchResolution },
    )
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }
    PlayerScreen(
        state = state,
        actions = viewModel,
        sources = sessions::imageSource,
        onBack = viewModel::close,
        video = { sample ->
            VideoSurface(player, state.settings.aspect, state.settings.subtitles, Modifier.fillMaxSize(), sample)
        },
    )
}

/**
 * The player (`UI.md` § 8, « Lecteur »): nothing over the picture while it plays. A key shows the
 * interface (title, hour and end, progress, controls), which fades out after 4 s. ← and → jump; on
 * the bar they search, with the preview picture above it, faster as the key stays down. « Passer
 * l'intro » and « Épisode suivant » take the focus when they appear. « Retour » closes a panel, then
 * the interface, then the player. Holding ↑ or ↓ while the interface is hidden makes the subtitles
 * larger or smaller at once, with their bar over the top of the picture (`UI.md` § 8).
 *
 * @param previewOsd shows the interface (screenshots); [previewPanel] opens a panel.
 */
@Suppress("CyclomaticComplexMethod") // The keys of a remote: one branch each.
@Composable
fun PlayerScreen(
    state: PlayerState,
    actions: PlayerActions,
    sources: ImageSources,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    previewOsd: Boolean = false,
    previewPanel: PlayerPanel? = null,
    video: @Composable (sample: String?) -> Unit = {},
) {
    var osd by remember { mutableStateOf(previewOsd) }
    var panel by remember { mutableStateOf(previewPanel) }
    var lastKey by remember { mutableIntStateOf(0) }
    val root = remember { FocusRequester() }
    val progress = remember { FocusRequester() }
    val current by rememberUpdatedState(state)
    val overlay = state.error != null || state.stillWatching
    val pausedForSettings = remember { mutableStateOf(false) }
    var resized by remember { mutableStateOf(false) }
    // For `ci/couch-gestures.sh` (`GestureLog`): the interface and the panels as they come and go.
    LaunchedEffect(osd) { GestureLog.mark(if (osd) "osd shown" else "osd hidden") }
    LaunchedEffect(panel) { GestureLog.mark("panel ${panel?.name ?: "closed"}") }

    fun resize(forward: Boolean) {
        resized = true
        osd = false
        panel = PlayerPanel.SubtitleSize
        if (!current.subtitlesBurnedIn) actions.updateSettings { it.copy(subtitles = it.subtitles.step(forward)) }
    }

    // The interface fades out 4 s after the last key while the film plays.
    val autoHide = osd && state.playing && !previewOsd
    LaunchedEffect(autoHide, lastKey, panel, state.scrub) {
        if (autoHide && panel == null && state.scrub == null) {
            delay(CineMotion.OSD_HIDE_AFTER_MILLIS.toLong())
            osd = false
        }
    }
    // The bar of the subtitles goes too, 4 s after the last key.
    LaunchedEffect(panel, lastKey) {
        if (panel == PlayerPanel.SubtitleSize && previewPanel == null) {
            delay(CineMotion.OSD_HIDE_AFTER_MILLIS.toLong())
            panel = null
        }
    }
    // A search left alone goes there after a moment.
    LaunchedEffect(state.scrub) {
        if (state.scrub != null) {
            delay(SCRUB_COMMIT_MILLIS)
            actions.commitScrub()
        }
    }
    // The settings show their subtitles over the paused picture.
    LaunchedEffect(panel) {
        if (panel == PlayerPanel.Settings && current.playing) {
            pausedForSettings.value = true
            actions.pause()
        } else if (panel != PlayerPanel.Settings && pausedForSettings.value) {
            pausedForSettings.value = false
            actions.play()
        }
    }

    BackHandler {
        when {
            state.scrub != null -> actions.cancelScrub()
            panel != null -> panel = null
            state.info != null -> actions.toggleInfo()
            state.nextShown -> actions.dismissNext()
            osd -> osd = false
            else -> onBack()
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(CineTheme.colors.shadow)
                .testTag("player")
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        actions.interacted()
                        lastKey++
                    }
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val repeat = event.nativeKeyEvent.repeatCount
                    when (event.key.nativeKeyCode) {
                        NativeKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            actions.togglePlay()
                        }

                        NativeKeyEvent.KEYCODE_MEDIA_PLAY -> {
                            actions.play()
                        }

                        NativeKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            actions.pause()
                        }

                        NativeKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            osd = true
                            actions.scrub(forward = true, repeat = repeat)
                        }

                        NativeKeyEvent.KEYCODE_MEDIA_REWIND -> {
                            osd = true
                            actions.scrub(forward = false, repeat = repeat)
                        }

                        NativeKeyEvent.KEYCODE_MEDIA_NEXT -> {
                            actions.chapter(forward = true)
                        }

                        NativeKeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            actions.chapter(forward = false)
                        }

                        NativeKeyEvent.KEYCODE_MEDIA_STOP -> {
                            onBack()
                        }

                        NativeKeyEvent.KEYCODE_INFO -> {
                            actions.toggleInfo()
                        }

                        else -> {
                            return@onPreviewKeyEvent false
                        }
                    }
                    true
                }.focusRequester(root)
                .onKeyEvent { event ->
                    // Keys nothing focused took: the interface is hidden.
                    if (osd || overlay) return@onKeyEvent false
                    val vertical = event.key == Key.DirectionUp || event.key == Key.DirectionDown
                    if (vertical && (panel == null || panel == PlayerPanel.SubtitleSize)) {
                        // Held: the size of the subtitles; pressed: the interface.
                        val repeat = event.nativeKeyEvent.repeatCount
                        when {
                            event.type == KeyEventType.KeyDown && repeat >= LONG_PRESS_REPEAT -> {
                                if ((repeat - LONG_PRESS_REPEAT) % RESIZE_EVERY_REPEAT == 0) {
                                    resize(forward = event.key == Key.DirectionUp)
                                }
                            }

                            event.type == KeyEventType.KeyUp -> {
                                if (!resized && panel == null) osd = true
                                resized = false
                            }
                        }
                        return@onKeyEvent true
                    }
                    if (event.type != KeyEventType.KeyDown || panel != null) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            actions.jump(forward = false)
                            osd = true
                        }

                        Key.DirectionRight -> {
                            actions.jump(forward = true)
                            osd = true
                        }

                        Key.DirectionCenter, Key.Enter -> {
                            osd = true
                        }

                        else -> {
                            return@onKeyEvent false
                        }
                    }
                    true
                }.focusable(),
    ) {
        val resizing = panel == PlayerPanel.SubtitleSize && !state.subtitlesBurnedIn
        video(if (resizing) stringResource(R.string.player_subtitle_sample) else null)
        if (state.loading && state.stream == null) Loading(state, sources)
        AnimatedVisibility(
            visible = osd && !overlay,
            enter = fadeIn(CineMotion.fade(CineMotion.OSD_FADE_MILLIS)),
            exit = fadeOut(CineMotion.fade(CineMotion.OSD_FADE_MILLIS)),
        ) {
            Osd(
                state,
                actions,
                progress,
                onPanel = {
                    panel = it
                    if (it == PlayerPanel.SubtitleSize) osd = false
                },
            )
        }
        if (state.reconnecting && state.error == null) {
            StatusPill(
                text = stringResource(R.string.player_reconnecting),
                icon = CineIcons.Retry,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = CineSpacing.safeVertical + CineSpacing.s),
            )
        }
        Corner(state, actions, sources, Modifier.align(Alignment.BottomEnd), panelOpen = panel != null)
        state.info?.let { snapshot ->
            PlaybackInfoSheet(
                title = stringResource(R.string.info_title),
                lines = state.infoLines(snapshot),
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = CineSpacing.safeHorizontal, top = CineSpacing.safeVertical + CineSpacing.xxl)
                        .testTag(PlayerPreview.INFO),
            )
        }
        PlayerPanels(panel, state, actions, onPanel = { panel = it })
        if (state.stillWatching) StillWatchingDialog(actions, onBack)
        state.error?.let { ErrorScreen(it, actions, onBack) }
    }

    // The focus: on the bar when the interface shows, else on the screen itself, which takes the keys.
    LaunchedEffect(osd, overlay, panel) {
        withFrameNanos { }
        runCatching {
            when {
                overlay || panel != null -> Unit
                osd -> progress.requestFocus()
                else -> root.requestFocus()
            }
        }
    }
}

private const val SCRUB_COMMIT_MILLIS = 1_200L

/** A key held down repeats after about half a second, then about twenty times a second. */
private const val LONG_PRESS_REPEAT = 1
private const val RESIZE_EVERY_REPEAT = 6

/** While the server is asked: the frame of the item, its title, never a black screen. */
@Composable
private fun Loading(
    state: PlayerState,
    sources: ImageSources,
) {
    val art = rememberArt(state.item.images.backdrop ?: state.item.landscapeImage, sources)
    Box(Modifier.fillMaxSize()) {
        art?.invoke()
        Box(Modifier.fillMaxSize().playerVeils(CineTheme.colors.playerVeil))
        Title(state, Modifier.padding(horizontal = CineSpacing.safeHorizontal, vertical = CineSpacing.safeVertical))
    }
}

@Composable
private fun Title(
    state: PlayerState,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CineSpacing.xs / 2)) {
        Text(
            text = state.item.playerTitle,
            style = CineTheme.typography.title1,
            color = CineTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        state.item.playerSubtitle()?.let {
            Text(text = it, style = CineTheme.typography.body, color = CineTheme.colors.text2, maxLines = 1)
        }
    }
}

/**
 * The interface over the picture: the title at the top left, the hour and the end at the top right;
 * at the bottom, the preview of a search, the bar, the times, the controls.
 */
@Composable
private fun Osd(
    state: PlayerState,
    actions: PlayerActions,
    progress: FocusRequester,
    onPanel: (PlayerPanel) -> Unit,
) {
    val colors = CineTheme.colors
    Box(Modifier.fillMaxSize().playerVeils(colors.playerVeil)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CineSpacing.safeHorizontal, vertical = CineSpacing.safeVertical),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Title(state, Modifier.weight(1f))
            Clock(state)
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = CineSpacing.safeHorizontal, vertical = CineSpacing.safeVertical),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
        ) {
            Search(state, actions)
            Bar(state, actions, progress)
            Times(state)
            Controls(state, actions, onPanel)
        }
    }
}

@Composable
private fun Clock(state: PlayerState) {
    val now = ZonedDateTime.now(LocalClock.current)
    val left = (state.duration - state.position).coerceAtLeast(Duration.ZERO)
    val end =
        now
            .plus(left.toJavaDuration())
            .plusSeconds(HALF_MINUTE)
            .truncatedTo(ChronoUnit.MINUTES)
            .toLocalTime()
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = LocalTime.from(now).toTimeText(),
            style = CineTheme.typography.title2.tabular(),
            color = CineTheme.colors.text,
        )
        Text(
            text = stringResource(R.string.player_ends_at, end.toTimeText()),
            style = CineTheme.typography.body.tabular(),
            color = CineTheme.colors.text2,
        )
    }
}

private const val HALF_MINUTE = 30L

/** The preview picture over the bar, where the search goes (`UI.md` § 8). */
@Composable
private fun Search(
    state: PlayerState,
    actions: PlayerActions,
) {
    val target = state.scrub ?: return
    val info = state.trickplay
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fraction = if (state.duration.isPositive()) (target / state.duration).toFloat().coerceIn(0f, 1f) else 0f
        val half = CineSizes.trickplayWidth / 2
        val x = (maxWidth * fraction - half).coerceIn(0.dp, maxWidth - CineSizes.trickplayWidth)
        val frame = info?.frameAt(target)
        TrickplayPreview(
            url = frame?.let { actions.trickplayUrl(it.tile) },
            headers = actions.streamHeaders,
            columns = info?.tileColumns ?: 1,
            rows = info?.tileRows ?: 1,
            column = frame?.column ?: 0,
            row = frame?.row ?: 0,
            aspectRatio = info?.let { it.width.toFloat() / it.height } ?: SIXTEEN_NINTHS,
            time = target.toClockText(),
            modifier = Modifier.offset(x = x),
        )
    }
}

private const val SIXTEEN_NINTHS = 16f / 9f

/** The bar, focused when the interface shows: ← → search, OK plays or pauses (or goes to the search). */
@Composable
private fun Bar(
    state: PlayerState,
    actions: PlayerActions,
    requester: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    val shown = focused || LocalPreviewFocus.current == PlayerPreview.PROGRESS
    val total = state.duration.takeIf { it.isPositive() }
    PlayerProgressBar(
        progress = total?.let { (state.position / it).toFloat() } ?: 0f,
        buffered = total?.let { ((state.position + state.buffered) / it).toFloat() } ?: 0f,
        seek = total?.let { t -> state.scrub?.let { (it / t).toFloat() } },
        chapters = total?.let { t -> state.chapters.map { (it.start / t).toFloat() } }.orEmpty(),
        focused = shown,
        modifier =
            Modifier
                .testTag(PlayerPreview.PROGRESS)
                .logFocus(PlayerPreview.PROGRESS)
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val repeat = event.nativeKeyEvent.repeatCount
                    when (event.key) {
                        Key.DirectionLeft -> {
                            actions.scrub(forward = false, repeat = repeat)
                        }

                        Key.DirectionRight -> {
                            actions.scrub(forward = true, repeat = repeat)
                        }

                        Key.DirectionCenter, Key.Enter -> {
                            if (state.scrub !=
                                null
                            ) {
                                actions.commitScrub()
                            } else {
                                actions.togglePlay()
                            }
                        }

                        else -> {
                            return@onKeyEvent false
                        }
                    }
                    true
                }.focusable(),
    )
}

@Composable
private fun Times(state: PlayerState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val style = CineTheme.typography.caption.tabular()
        Text(
            text = (state.scrub ?: state.position).toClockText(),
            style = style,
            color = CineTheme.colors.text,
            modifier = Modifier.testTag(PlayerPreview.POSITION),
        )
        Text(
            text =
                stringResource(
                    R.string.player_remaining,
                    (state.duration - (state.scrub ?: state.position)).coerceAtLeast(Duration.ZERO).toClockText(),
                ),
            style = style,
            color = CineTheme.colors.text2,
        )
    }
}

@Composable
private fun Controls(
    state: PlayerState,
    actions: PlayerActions,
    onPanel: (PlayerPanel) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = CineSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.m, Alignment.CenterHorizontally),
    ) {
        RoundIconButton(
            icon = if (state.playing) CineIcons.Pause else CineIcons.Play,
            label = stringResource(if (state.playing) R.string.player_pause else R.string.player_play),
            onClick = actions::togglePlay,
            focus = rememberCineFocus(PlayerPreview.PLAY),
            modifier = Modifier.testTag(PlayerPreview.PLAY).logFocus(PlayerPreview.PLAY),
        )
        if (state.chapters.isNotEmpty()) {
            RoundIconButton(
                icon = CineIcons.Chapters,
                label = stringResource(R.string.player_chapters),
                onClick = { onPanel(PlayerPanel.Chapters) },
                focus = rememberCineFocus(PlayerPreview.CHAPTERS),
                modifier = Modifier.testTag(PlayerPreview.CHAPTERS).logFocus(PlayerPreview.CHAPTERS),
            )
        }
        RoundIconButton(
            icon = CineIcons.Tracks,
            label = stringResource(R.string.player_tracks),
            onClick = { onPanel(PlayerPanel.Tracks) },
            focus = rememberCineFocus(PlayerPreview.TRACKS),
            modifier = Modifier.testTag(PlayerPreview.TRACKS).logFocus(PlayerPreview.TRACKS),
        )
        RoundIconButton(
            icon = CineIcons.TextSize,
            label = stringResource(R.string.player_subtitle_size),
            onClick = { onPanel(PlayerPanel.SubtitleSize) },
            focus = rememberCineFocus(PlayerPreview.SUBTITLES),
            modifier = Modifier.testTag(PlayerPreview.SUBTITLES).logFocus(PlayerPreview.SUBTITLES),
        )
        if (!state.trailer) {
            RoundIconButton(
                icon = CineIcons.Quality,
                label = stringResource(R.string.player_quality),
                onClick = { onPanel(PlayerPanel.Quality) },
                focus = rememberCineFocus(PlayerPreview.QUALITY),
                modifier = Modifier.testTag(PlayerPreview.QUALITY).logFocus(PlayerPreview.QUALITY),
            )
        }
        RoundIconButton(
            icon = CineIcons.Settings,
            label = stringResource(R.string.player_settings),
            onClick = { onPanel(PlayerPanel.Settings) },
            focus = rememberCineFocus(PlayerPreview.SETTINGS),
            modifier = Modifier.testTag(PlayerPreview.SETTINGS).logFocus(PlayerPreview.SETTINGS),
        )
        RoundIconButton(
            icon = CineIcons.Info,
            label = stringResource(R.string.player_info),
            onClick = actions::toggleInfo,
            selected = state.info != null,
            focus = rememberCineFocus(PlayerPreview.INFO),
            modifier = Modifier.testTag(PlayerPreview.INFO_BUTTON).logFocus(PlayerPreview.INFO_BUTTON),
        )
    }
}

/**
 * The bottom right: « Passer l'intro » while the intro plays, « Épisode suivant » during the credits;
 * each takes the focus when it appears (`UI.md` § 8).
 */
@Composable
private fun Corner(
    state: PlayerState,
    actions: PlayerActions,
    sources: ImageSources,
    modifier: Modifier,
    panelOpen: Boolean,
) {
    if (panelOpen || state.error != null || state.stillWatching) return
    val padding =
        Modifier.padding(
            end = CineSpacing.safeHorizontal,
            bottom =
                CineSpacing.safeVertical + CineSpacing.xxl * 2,
        )
    val skip = state.skip
    val next = state.next
    when {
        state.nextShown && next != null -> {
            val requester = remember { FocusRequester() }
            val art = rememberArt(next.landscapeImage, sources)
            NextEpisodeCard(
                label = stringResource(R.string.player_next),
                title = next.playerSubtitle() ?: next.title,
                subtitle = next.playerTitle,
                countdown = state.nextCountdown,
                countdownText = state.nextSeconds?.let { stringResource(R.string.player_next_in, it) },
                onPlay = actions::playNext,
                picture = art,
                focusRequester = requester,
                focus = rememberCineFocus(PlayerPreview.NEXT),
                modifier = modifier.then(padding).testTag(PlayerPreview.NEXT),
            )
            LaunchedEffect(next.id) {
                withFrameNanos { }
                runCatching { requester.requestFocus() }
            }
        }

        skip != null -> {
            val requester = remember { FocusRequester() }
            PrimaryButton(
                text = skip.type.skipLabel(),
                icon = CineIcons.SkipNext,
                onClick = actions::skipSegment,
                focusRequester = requester,
                focus = rememberCineFocus(PlayerPreview.SKIP),
                modifier = modifier.then(padding).testTag(PlayerPreview.SKIP),
            )
            LaunchedEffect(skip) {
                withFrameNanos { }
                runCatching { requester.requestFocus() }
            }
        }
    }
}

@Composable
private fun StillWatchingDialog(
    actions: PlayerActions,
    onBack: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    ConfirmDialog(
        title = stringResource(R.string.player_still_title),
        message = stringResource(R.string.player_still_message),
        confirmLabel = stringResource(R.string.player_still_stop),
        cancelLabel = stringResource(R.string.player_still_continue),
        onConfirm = onBack,
        onCancel = actions::keepWatching,
        cancelFocusRequester = requester,
        cancelFocus = rememberCineFocus(PlayerPreview.STILL),
        destructive = false,
        modifier = Modifier.testTag(PlayerPreview.STILL),
    )
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
}

/** Two sentences and « Réessayer » (`UI.md` § 10), never a silent black screen. */
@Composable
private fun ErrorScreen(
    error: PlayerError,
    actions: PlayerActions,
    onBack: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    Box(
        Modifier
            .fillMaxSize()
            .background(CineTheme.colors.dialogScrim)
            .padding(start = CineSizes.railCollapsed + CineSpacing.xl)
            .testTag(PlayerPreview.ERROR),
        contentAlignment = Alignment.CenterStart,
    ) {
        when (error) {
            is PlayerError.Source -> {
                SourceErrorPanel(
                    error = error.error,
                    onRetry = actions::retry,
                    secondaryLabel = stringResource(R.string.player_back),
                    onSecondary = onBack,
                    retryFocusRequester = requester,
                    retryFocus = rememberCineFocus(PlayerPreview.RETRY),
                )
            }

            is PlayerError.Playback -> {
                MessagePanel(
                    title = stringResource(R.string.player_error_title),
                    message = error.failure.message(),
                    actionLabel = stringResource(R.string.player_retry),
                    onAction = actions::retry,
                    secondaryLabel = stringResource(R.string.player_back),
                    onSecondary = onBack,
                    detailsLabel = stringResource(fr.guim.cinedoury.core.ui.R.string.action_more_info),
                    details = error.detail,
                    actionFocusRequester = requester,
                    actionFocus = rememberCineFocus(PlayerPreview.RETRY),
                )
            }
        }
    }
    LaunchedEffect(error) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
}

/**
 * Says in the [GestureLog] when the element [tag] takes the focus: `ci/couch-gestures.sh` finds its
 * way in the player by it, where `uiautomator dump` never sees the screen still.
 */
internal fun Modifier.logFocus(tag: String): Modifier =
    onFocusChanged {
        if (it.isFocused ||
            it.hasFocus
        ) {
            GestureLog.mark("focus $tag")
        }
    }
