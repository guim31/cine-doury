package fr.guim.cinedoury.feature.player.mobile

import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.TrickplayPreview
import fr.guim.cinedoury.core.designsystem.mobile.MobileGestureIndicator
import fr.guim.cinedoury.core.designsystem.mobile.MobileMessage
import fr.guim.cinedoury.core.designsystem.mobile.MobilePrimaryButton
import fr.guim.cinedoury.core.designsystem.mobile.MobileSecondaryButton
import fr.guim.cinedoury.core.designsystem.mobile.MobileSeekBar
import fr.guim.cinedoury.core.designsystem.mobile.MobileSheet
import fr.guim.cinedoury.core.designsystem.mobile.MobileSheetLine
import fr.guim.cinedoury.core.designsystem.mobile.MobileSheetLines
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.MobileSizes
import fr.guim.cinedoury.core.model.AspectMode
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.model.Qualities
import fr.guim.cinedoury.core.playback.PlayerSnapshot
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.cardTitle
import fr.guim.cinedoury.core.ui.label
import fr.guim.cinedoury.core.ui.mobile.MobileSourceError
import fr.guim.cinedoury.feature.player.KeepScreenOn
import fr.guim.cinedoury.feature.player.PlayerActions
import fr.guim.cinedoury.feature.player.PlayerError
import fr.guim.cinedoury.feature.player.PlayerState
import fr.guim.cinedoury.feature.player.PlayerViewModel
import fr.guim.cinedoury.feature.player.R
import fr.guim.cinedoury.feature.player.VideoSurface
import fr.guim.cinedoury.feature.player.hourlyEstimate
import fr.guim.cinedoury.feature.player.infoLines
import fr.guim.cinedoury.feature.player.megabits
import fr.guim.cinedoury.feature.player.message
import fr.guim.cinedoury.feature.player.playerSubtitle
import fr.guim.cinedoury.feature.player.skipLabel
import fr.guim.cinedoury.feature.player.toClockText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Tags of the player of the phone, for tests and screenshots. */
object MobilePlayerTags {
    const val SCREEN = "player"
    const val SURFACE = "player-surface"
    const val PLAY = "player-play"
    const val BACK_10 = "player-back-10"
    const val FORWARD_10 = "player-forward-10"
    const val SEEK = "player-seek"
    const val POSITION = "player-position"
    const val TRACKS = "player-tracks"
    const val QUALITY = "player-quality"
    const val INFO = "player-info"
    const val LOCK = "player-lock"
    const val UNLOCK = "player-unlock"
    const val ROTATE = "player-rotate"
    const val NEXT = "player-next"
    const val SKIP = "player-skip"
    const val CLOSE = "player-close"
    const val ERROR = "player-error"
    const val OFFLINE = "player-offline"
    const val JUMP = "player-jump"
    const val NOTICE = "player-quality-notice"
}

/** What the screenshots open over the player (M2). */
enum class MobilePlayerPreview { Controls, Tracks, Quality, Info, Locked, Jump, Seek, Brightness }

/** The brightness of the window and the volume of the media, for the slides of the player (M2). */
interface PlayerLevels {
    fun brightness(): Float

    fun setBrightness(level: Float)

    fun volume(): Float

    fun setVolume(level: Float)

    object None : PlayerLevels {
        override fun brightness() = HALF

        override fun setBrightness(level: Float) = Unit

        override fun volume() = HALF

        override fun setVolume(level: Float) = Unit

        private const val HALF = 0.5f
    }
}

@Composable
fun MobilePlayerRoute(
    request: PlaybackRequest,
    onClose: () -> Unit,
    onSessionEnded: () -> Unit,
    viewModel: PlayerViewModel = koinViewModel(key = "player-${request.item.id.value}") { parametersOf(request) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.surface.collectAsStateWithLifecycle()
    CollectEvents(viewModel.closed) { onClose() }
    CollectEvents(viewModel.sessionEnded) { onSessionEnded() }
    KeepScreenOn()
    val activity = LocalActivity.current
    var portrait by rememberSaveable { mutableStateOf(false) }
    FullScreen(activity, portrait)
    val levels = rememberLevels(activity)
    val pictureInPicture by rememberPictureInPicture(
        activity as? ComponentActivity,
        playing = state.playing,
        onPlayPause = viewModel::togglePlay,
        onJump = { forward -> viewModel.seekTo(jumpFrom(viewModel.state.value, forward)) },
    )
    // In the background or the screen off, the film pauses; the small window keeps it playing (M2).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }
    MobilePlayerScreen(
        state = state,
        actions = viewModel,
        onBack = viewModel::close,
        pictureInPicture = pictureInPicture,
        portrait = portrait,
        onPortrait = { portrait = it },
        levels = levels,
        video = { VideoSurface(player, state.settings.aspect, state.settings.subtitles, Modifier.fillMaxSize()) },
    )
}

private fun jumpFrom(
    state: PlayerState,
    forward: Boolean,
): Duration =
    if (forward) {
        (state.position + JUMP).coerceAtMost(state.duration)
    } else {
        (state.position - JUMP).coerceAtLeast(Duration.ZERO)
    }

/**
 * The film full screen (M1, M2): the bars of the system hidden (a swipe from an edge brings them back
 * for a moment); in landscape with the sensor, or held upright when [portrait] (the button of the
 * controls). All given back when the player closes.
 */
@Composable
private fun FullScreen(
    activity: Activity?,
    portrait: Boolean,
) {
    DisposableEffect(activity) {
        val window = activity?.window
        val before = activity?.requestedOrientation
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = before ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    DisposableEffect(activity, portrait) {
        activity?.requestedOrientation =
            if (portrait) {
                ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        onDispose { }
    }
}

/**
 * The brightness of the window (never the setting of the system: it comes back as it was when the
 * player closes) and the volume of the media.
 */
@Composable
private fun rememberLevels(activity: Activity?): PlayerLevels {
    val levels =
        remember(activity) {
            if (activity == null) {
                PlayerLevels.None
            } else {
                object : PlayerLevels {
                    val audio = activity.getSystemService(AudioManager::class.java)

                    override fun brightness(): Float {
                        val own = activity.window.attributes.screenBrightness
                        if (own >= 0f) return own
                        val system =
                            Settings.System.getInt(
                                activity.contentResolver,
                                Settings.System.SCREEN_BRIGHTNESS,
                                MAX_BRIGHTNESS / 2,
                            )
                        return system.toFloat() / MAX_BRIGHTNESS
                    }

                    override fun setBrightness(level: Float) {
                        val attributes = activity.window.attributes
                        attributes.screenBrightness = level.coerceIn(MIN_BRIGHTNESS, 1f)
                        activity.window.attributes = attributes
                    }

                    override fun volume(): Float {
                        val max = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: return 0f
                        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
                    }

                    override fun setVolume(level: Float) {
                        val max = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: return
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (level * max).roundToInt(), 0)
                    }
                }
            }
        }
    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { window ->
                val attributes = window.attributes
                attributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = attributes
            }
        }
    }
    return levels
}

/**
 * The touch player of the phone (M1, M2, `UI.md` § 9): nothing over the picture while it plays; a tap
 * shows the controls (the title, the quality, the tracks, the information, −10 s, play or pause, +10 s,
 * the bar with its margin, the padlock, the rotation), which fade out 4 s after the last touch. On the
 * picture: two taps on a side jump 10 s, cumulated; a slide up or down sets the brightness (left) or the
 * volume (right); a slide sideways searches with the preview picture; a pinch fills the screen or comes
 * back to the format of the film. The padlock ignores every touch until it is held down.
 *
 * @param pictureInPicture the film plays in a small window: nothing over it.
 * @param preview what the screenshots open; [previewLocked], [previewNow] for them too.
 */
@Composable
fun MobilePlayerScreen(
    state: PlayerState,
    actions: PlayerActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pictureInPicture: Boolean = false,
    portrait: Boolean = false,
    onPortrait: (Boolean) -> Unit = {},
    levels: PlayerLevels = PlayerLevels.None,
    preview: MobilePlayerPreview? = null,
    previewControls: Boolean = preview == MobilePlayerPreview.Controls,
    previewTracks: Boolean = preview == MobilePlayerPreview.Tracks,
    video: @Composable () -> Unit = {},
) {
    var controls by remember { mutableStateOf(previewControls) }
    var sheet by remember {
        mutableStateOf(
            when {
                previewTracks -> Sheet.Tracks
                preview == MobilePlayerPreview.Quality -> Sheet.Quality
                preview == MobilePlayerPreview.Info -> Sheet.Info
                else -> null
            },
        )
    }
    var touched by remember { mutableIntStateOf(0) }
    var lockShown by remember { mutableStateOf(preview == MobilePlayerPreview.Locked) }
    // Where the finger holds the bar, while it slides; null otherwise.
    var sliding by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(state)
    val touch: () -> Unit = {
        touched++
        actions.interacted()
    }
    val gestures =
        remember {
            PlayerGestures(
                onToggleControls = {
                    touch()
                    controls = !controls
                },
                onShowLock = { lockShown = true },
                onJump = {
                    touch()
                    actions.seekTo(it)
                },
                onFill = { fill ->
                    touch()
                    actions.updateSettings { it.copy(aspect = if (fill) AspectMode.Zoom else AspectMode.Fit) }
                },
                onSeek = {
                    touch()
                    actions.seekTo(it)
                },
                setBrightness = levels::setBrightness,
                setVolume = levels::setVolume,
            )
        }
    gestures.position = { latest.position }
    gestures.duration = { latest.duration }
    gestures.brightness = levels::brightness
    gestures.volume = levels::volume
    if (preview == MobilePlayerPreview.Locked) gestures.locked = true
    val locked = gestures.locked
    // The controls fade out 4 s after the last touch while the film plays, unless a finger is busy.
    val busy = sliding != null || sheet != null || previewControls
    LaunchedEffect(controls, state.playing, touched, busy) {
        if (controls && state.playing && !busy) {
            delay(CineMotion.OSD_HIDE_AFTER_MILLIS.toLong())
            controls = false
        }
    }
    LaunchedEffect(lockShown, touched) {
        if (lockShown && preview == null) {
            delay(CineMotion.OSD_HIDE_AFTER_MILLIS.toLong())
            lockShown = false
        }
    }
    val burst = gestures.burst
    LaunchedEffect(burst != null) {
        while (gestures.burst != null) {
            delay(BURST_LOOK)
            gestures.commit(now())
        }
    }
    BackHandler {
        when {
            locked -> lockShown = true
            sheet != null -> sheet = null
            state.nextShown -> actions.dismissNext()
            controls -> controls = false
            else -> onBack()
        }
    }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(CineTheme.colors.shadow)
                .testTag(MobilePlayerTags.SCREEN)
                .playerGestures(gestures, ::now) { at ->
                    scope.launch {
                        delay((at - now()).coerceAtLeast(Duration.ZERO))
                        gestures.decideTap(now())
                    }
                },
    ) {
        Box(Modifier.fillMaxSize().testTag(MobilePlayerTags.SURFACE)) { video() }
        if (pictureInPicture) return@Box
        Waiting(state, Modifier.align(Alignment.Center))
        val shownBurst = burst ?: previewBurst(preview, state)
        shownBurst?.let {
            Jump(
                it,
                Modifier.align(
                    if (it.side ==
                        Side.Left
                    ) {
                        Alignment.CenterStart
                    } else {
                        Alignment.CenterEnd
                    },
                ),
            )
        }
        val slide = gestures.slide ?: previewSlide(preview, state)
        slide?.let { SlideIndicator(it, state, actions, Modifier.align(Alignment.Center)) }
        AnimatedVisibility(controls && !locked && state.error == null, enter = fadeIn(), exit = fadeOut()) {
            Controls(
                state = state,
                actions = actions,
                onBack = onBack,
                sliding = sliding,
                onSlide = {
                    sliding = it
                    touch()
                },
                onSheet = {
                    sheet = it
                    touch()
                },
                onLock = {
                    gestures.locked = true
                    controls = false
                    lockShown = true
                },
                portrait = portrait,
                onPortrait = onPortrait,
                touch = touch,
            )
        }
        if (state.qualityReduced) QualityNotice(Modifier.align(Alignment.TopCenter))
        if (!locked) Pills(state, actions, controls, Modifier.align(Alignment.BottomEnd))
        if (locked && lockShown) {
            Unlock(
                onUnlock = {
                    gestures.locked = false
                    lockShown = false
                    controls = true
                },
                Modifier.align(Alignment.CenterStart),
            )
        }
        state.error?.let { ErrorOverlay(it, actions, onBack) }
        if (state.stillWatching) StillWatching(actions, onBack)
    }
    when (sheet) {
        Sheet.Tracks -> TracksSheet(state, actions) { sheet = null }
        Sheet.Quality -> QualitySheet(state, actions) { sheet = null }
        Sheet.Info -> InfoSheet(state, actions) { sheet = null }
        null -> Unit
    }
}

private enum class Sheet { Tracks, Quality, Info }

private fun now(): Duration = SystemClock.uptimeMillis().milliseconds

private fun previewBurst(
    preview: MobilePlayerPreview?,
    state: PlayerState,
): JumpBurst? = if (preview == MobilePlayerPreview.Jump) JumpBurst(Side.Left, PREVIEW_JUMP, state.position) else null

private fun previewSlide(
    preview: MobilePlayerPreview?,
    state: PlayerState,
): Slide? =
    when (preview) {
        MobilePlayerPreview.Seek -> Slide.Seek(state.position + PREVIEW_SEEK)
        MobilePlayerPreview.Brightness -> Slide.Brightness(PREVIEW_LEVEL)
        else -> null
    }

/**
 * The loading circle; without a network once the margin is spent, the sentence that says the film
 * will start again by itself (M2).
 */
@Composable
private fun Waiting(
    state: PlayerState,
    modifier: Modifier,
) {
    if (!state.loading && !state.buffering) return
    Column(
        modifier.padding(CineSpacing.m).widthIn(max = MobileSizes.formWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
    ) {
        CircularProgressIndicator(color = CineTheme.colors.accent)
        if (state.offline && state.buffering) {
            Text(
                stringResource(R.string.mobile_player_offline),
                style = CineTheme.typography.body,
                color = CineTheme.colors.text,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(MobileSizes.cardCorner))
                        .background(CineTheme.colors.playerPanel)
                        .padding(CineSpacing.s)
                        .testTag(MobilePlayerTags.OFFLINE),
            )
        }
    }
}

/** « −30 s » on the side tapped, while the taps go on. */
@Composable
private fun Jump(
    burst: JumpBurst,
    modifier: Modifier,
) {
    val text =
        if (burst.side == Side.Left) {
            stringResource(R.string.mobile_player_jump_back, burst.seconds)
        } else {
            stringResource(R.string.mobile_player_jump_forward, burst.seconds)
        }
    Box(
        modifier
            .padding(horizontal = CineSpacing.xl)
            .size(MobileSizes.jumpBubble)
            .clip(CircleShape)
            .background(CineTheme.colors.playerPanel)
            .testTag(MobilePlayerTags.JUMP),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (burst.side == Side.Left) CineIcons.FastRewind else CineIcons.FastForward,
                contentDescription = null,
                tint = CineTheme.colors.text,
            )
            Text(text, style = CineTheme.typography.title2, color = CineTheme.colors.text)
        }
    }
}

/** The level of the brightness or the volume, or the time the search goes to with its picture. */
@Composable
private fun SlideIndicator(
    slide: Slide,
    state: PlayerState,
    actions: PlayerActions,
    modifier: Modifier,
) {
    when (slide) {
        is Slide.Brightness -> {
            MobileGestureIndicator(CineIcons.Brightness, modifier, level = slide.level)
        }

        is Slide.Volume -> {
            MobileGestureIndicator(CineIcons.Volume, modifier, level = slide.level)
        }

        is Slide.Seek -> {
            val info = state.trickplay
            val frame = info?.frameAt(slide.to)
            val time = "${slide.to.toClockText()} / ${state.duration.toClockText()}"
            if (info == null) {
                MobileGestureIndicator(null, modifier, text = time)
            } else {
                TrickplayPreview(
                    url = frame?.let { actions.trickplayUrl(it.tile) },
                    headers = actions.streamHeaders,
                    columns = info.tileColumns,
                    rows = info.tileRows,
                    column = frame?.column ?: 0,
                    row = frame?.row ?: 0,
                    aspectRatio = info.width.toFloat() / info.height,
                    time = time,
                    modifier = modifier,
                )
            }
        }
    }
}

/** « Qualité réduite : réseau faible », a few seconds at the top (M2). */
@Composable
private fun QualityNotice(modifier: Modifier) {
    MobileGestureIndicator(
        CineIcons.Quality,
        modifier.safeDrawingPadding().padding(top = CineSpacing.s).testTag(MobilePlayerTags.NOTICE),
        text = stringResource(R.string.mobile_player_quality_reduced),
    )
}

/** The padlock of a locked screen: a tap says how to unlock it, holding it down unlocks. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Unlock(
    onUnlock: () -> Unit,
    modifier: Modifier,
) {
    val description = stringResource(R.string.mobile_player_unlock)
    Row(
        modifier.safeDrawingPadding().padding(CineSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
    ) {
        Box(
            Modifier
                .size(MobileSizes.playerButton)
                .clip(CircleShape)
                .background(CineTheme.colors.playerPanel)
                .combinedClickable(onClick = {}, onLongClick = onUnlock)
                .semantics { contentDescription = description }
                .testTag(MobilePlayerTags.UNLOCK),
            contentAlignment = Alignment.Center,
        ) {
            Icon(CineIcons.Lock, contentDescription = null, tint = CineTheme.colors.accent)
        }
        Text(
            stringResource(R.string.mobile_player_locked_hint),
            style = CineTheme.typography.caption,
            color = CineTheme.colors.text,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(MobileSizes.cardCorner))
                    .background(CineTheme.colors.playerPanel)
                    .padding(CineSpacing.xs),
        )
    }
}

@Composable
private fun Controls(
    state: PlayerState,
    actions: PlayerActions,
    onBack: () -> Unit,
    sliding: Float?,
    onSlide: (Float?) -> Unit,
    onSheet: (Sheet) -> Unit,
    onLock: () -> Unit,
    portrait: Boolean,
    onPortrait: (Boolean) -> Unit,
    touch: () -> Unit,
) {
    val colors = CineTheme.colors
    Box(Modifier.fillMaxSize().background(colors.playerScrim)) {
        // The title and the panels, at the top.
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.playerVeil)
                .safeDrawingPadding()
                .padding(CineSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(
                CineIcons.Back,
                stringResource(R.string.player_back),
                onBack,
                Modifier.testTag(MobilePlayerTags.CLOSE),
            )
            Column(Modifier.weight(1f).padding(horizontal = CineSpacing.xs)) {
                Text(
                    state.item.cardTitle,
                    style = CineTheme.typography.title2,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                state.item.playerSubtitle()?.let {
                    Text(it, style = CineTheme.typography.caption, color = colors.text2, maxLines = 1)
                }
            }
            SmallButton(CineIcons.Quality, stringResource(R.string.player_quality), MobilePlayerTags.QUALITY) {
                onSheet(Sheet.Quality)
            }
            SmallButton(CineIcons.Tracks, stringResource(R.string.player_tracks), MobilePlayerTags.TRACKS) {
                onSheet(Sheet.Tracks)
            }
            SmallButton(CineIcons.Info, stringResource(R.string.mobile_player_info), MobilePlayerTags.INFO) {
                onSheet(Sheet.Info)
            }
        }
        // −10 s, play or pause, +10 s, in the middle.
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(CineIcons.FastRewind, stringResource(R.string.mobile_player_back_10), {
                touch()
                actions.seekTo(jumpFrom(state, forward = false))
            }, Modifier.testTag(MobilePlayerTags.BACK_10))
            RoundButton(
                if (state.playing) CineIcons.Pause else CineIcons.Play,
                stringResource(if (state.playing) R.string.player_pause else R.string.player_play),
                {
                    touch()
                    actions.togglePlay()
                },
                Modifier.testTag(MobilePlayerTags.PLAY),
                large = true,
            )
            RoundButton(CineIcons.FastForward, stringResource(R.string.mobile_player_forward_10), {
                touch()
                actions.seekTo(jumpFrom(state, forward = true))
            }, Modifier.testTag(MobilePlayerTags.FORWARD_10))
        }
        // The bar and its margin, the times, the padlock, the next episode and the rotation, at the bottom.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(colors.playerVeil)
                .safeDrawingPadding()
                .padding(start = CineSpacing.s, end = CineSpacing.s, bottom = CineSpacing.xs),
        ) {
            val duration = state.duration.inWholeMilliseconds.coerceAtLeast(1)
            val shown = sliding ?: (state.position.inWholeMilliseconds.toFloat() / duration)
            val loaded =
                (state.margin?.end ?: (state.position + state.buffered)).inWholeMilliseconds.toFloat() / duration
            MobileSeekBar(
                progress = shown,
                margin = loaded,
                onSeek = onSlide,
                onSeekEnd = {
                    sliding?.let { actions.seekTo((it * duration).toLong().milliseconds) }
                    onSlide(null)
                },
                dragging = sliding != null,
                modifier = Modifier.testTag(MobilePlayerTags.SEEK),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallButton(CineIcons.Lock, stringResource(R.string.mobile_player_lock), MobilePlayerTags.LOCK, onLock)
                val position = sliding?.let { (it * duration).toLong().milliseconds } ?: state.position
                Text(
                    "${position.toClockText()} / ${state.duration.toClockText()}",
                    style = CineTheme.typography.caption,
                    color = colors.text2,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .weight(
                                1f,
                            ).padding(horizontal = CineSpacing.xs)
                            .testTag(MobilePlayerTags.POSITION),
                )
                state.next?.let {
                    MobileSecondaryButton(
                        stringResource(R.string.player_next_episode),
                        actions::playNext,
                        Modifier.testTag(MobilePlayerTags.NEXT),
                        icon = CineIcons.SkipNext,
                    )
                    Spacer(Modifier.size(CineSpacing.xs))
                }
                SmallButton(
                    CineIcons.ScreenRotation,
                    stringResource(if (portrait) R.string.mobile_player_landscape else R.string.mobile_player_portrait),
                    MobilePlayerTags.ROTATE,
                ) { onPortrait(!portrait) }
            }
        }
    }
}

/** « Passer l'intro » while a segment plays, « Épisode suivant » during the credits. */
@Composable
private fun Pills(
    state: PlayerState,
    actions: PlayerActions,
    controls: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier.safeDrawingPadding().padding(
            bottom = if (controls) CineSpacing.xxl * 2 else CineSpacing.m,
            end = CineSpacing.m,
        ),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
    ) {
        state.skip?.let { segment ->
            MobilePrimaryButton(segment.type.skipLabel(), actions::skipSegment, Modifier.testTag(MobilePlayerTags.SKIP))
        }
        if (state.nextShown && state.next != null) {
            val seconds = state.nextSeconds
            MobilePrimaryButton(
                if (seconds !=
                    null
                ) {
                    stringResource(R.string.player_next_in, seconds)
                } else {
                    stringResource(R.string.player_next)
                },
                actions::playNext,
                icon = CineIcons.SkipNext,
            )
        }
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val size = if (large) MobileSizes.playerButtonLarge else MobileSizes.playerButton
    IconButton(
        onClick = onClick,
        modifier = modifier.size(size).clip(CircleShape).background(CineTheme.colors.playerPanel),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = CineTheme.colors.text,
            modifier = Modifier.size(if (large) MobileSizes.playerIconLarge else MobileSizes.playerIcon),
        )
    }
}

/** A secondary button of the controls: 48 dp for the finger, no disc. */
@Composable
private fun SmallButton(
    icon: ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(MobileSizes.playerSmallButton).testTag(tag)) {
        Icon(icon, contentDescription = label, tint = CineTheme.colors.text, modifier = Modifier.size(MobileSizes.icon))
    }
}

/** The audio and the subtitles of the version played, in a sheet. */
@Composable
private fun TracksSheet(
    state: PlayerState,
    actions: PlayerActions,
    onDismiss: () -> Unit,
) {
    val source = state.stream?.source ?: state.item.mediaSources.firstOrNull() ?: return
    MobileSheet(stringResource(R.string.player_tracks), onDismiss) {
        LazyColumn {
            item { Section(stringResource(R.string.player_audio)) }
            item {
                MobileSheetLines(
                    source.audio.map { track ->
                        MobileSheetLine(
                            track.label(),
                            null,
                            "audio-${track.index}",
                        ) { actions.selectAudio(track.index) }
                    },
                    selected = "audio-${state.audioIndex}",
                )
            }
            item { Section(stringResource(R.string.player_subtitles)) }
            item {
                MobileSheetLines(
                    listOf(
                        MobileSheetLine(stringResource(R.string.player_subtitles_none), null, "subtitles-none") {
                            actions.selectSubtitles(null)
                        },
                    ) +
                        source.subtitles.map { track ->
                            MobileSheetLine(track.label(), null, "subtitles-${track.index}") {
                                actions.selectSubtitles(track.index)
                            }
                        },
                    selected = state.subtitleIndex?.let { "subtitles-$it" } ?: "subtitles-none",
                )
            }
        }
    }
}

/**
 * « Automatique » then the bitrates, each with what an hour of it weighs (M2): on a mobile network,
 * « Automatique » follows the margin unless a quality is chosen here.
 */
@Composable
private fun QualitySheet(
    state: PlayerState,
    actions: PlayerActions,
    onDismiss: () -> Unit,
) {
    val source =
        state.stream?.source?.bitrate ?: state.item.mediaSources
            .firstOrNull()
            ?.bitrate
    val automatic = !state.qualityChosen
    val lines =
        listOf(
            MobileSheetLine(
                stringResource(R.string.player_quality_auto),
                null,
                "quality-auto",
                detail =
                    listOfNotNull(
                        stringResource(R.string.mobile_player_quality_auto_mobile).takeIf { state.margin != null },
                        source?.let { hourlyEstimate(it) },
                    ).joinToString(" · "),
            ) {
                actions.selectQuality(null)
                onDismiss()
            },
        ) +
            Qualities.BITRATES.map { bitrate ->
                MobileSheetLine(
                    stringResource(R.string.player_quality_bitrate, bitrate.megabits()),
                    null,
                    "quality-$bitrate",
                    detail = hourlyEstimate(bitrate),
                ) {
                    actions.selectQuality(bitrate)
                    onDismiss()
                }
            }
    val selected =
        if (automatic &&
            state.margin != null
        ) {
            "quality-auto"
        } else {
            state.maxBitrate?.let { "quality-$it" } ?: "quality-auto"
        }
    MobileSheet(stringResource(R.string.player_quality), onDismiss) {
        LazyColumn {
            item { MobileSheetLines(lines, selected = selected) }
        }
    }
}

/** The information screen of the TV in a sheet, with the margin and the speed measured (M2). */
@Composable
private fun InfoSheet(
    state: PlayerState,
    actions: PlayerActions,
    onDismiss: () -> Unit,
) {
    // The view model reads the player for the sheet while it is open, as for the TV's screen.
    DisposableEffect(Unit) {
        actions.toggleInfo()
        onDispose { actions.toggleInfo() }
    }
    val lines = state.infoLines(state.info ?: PlayerSnapshot(margin = state.margin))
    MobileSheet(stringResource(R.string.mobile_player_info), onDismiss) {
        LazyColumn(Modifier.padding(horizontal = MobileSizes.gutter)) {
            items(lines, key = { it.first + it.second }) { (name, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = CineSpacing.xs / 2)) {
                    Text(
                        name,
                        style = CineTheme.typography.caption,
                        color = CineTheme.colors.text2,
                        modifier = Modifier.weight(INFO_NAME_WEIGHT),
                    )
                    Text(
                        value,
                        style = CineTheme.typography.caption,
                        color = CineTheme.colors.text,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = CineTheme.typography.label,
        color = CineTheme.colors.text2,
        modifier = Modifier.padding(horizontal = MobileSizes.gutter, vertical = CineSpacing.xs),
    )
}

/** Two sentences and « Réessayer » (`UI.md` § 10), never a silent black screen. */
@Composable
private fun ErrorOverlay(
    error: PlayerError,
    actions: PlayerActions,
    onBack: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(CineTheme.colors.dialogScrim)
            .safeDrawingPadding()
            .testTag(MobilePlayerTags.ERROR),
        contentAlignment = Alignment.Center,
    ) {
        when (error) {
            is PlayerError.Source -> {
                MobileSourceError(
                    error.error,
                    actions::retry,
                    secondaryLabel = stringResource(R.string.player_back),
                    onSecondary = onBack,
                )
            }

            is PlayerError.Playback -> {
                MobileMessage(
                    stringResource(R.string.player_error_title),
                    error.failure.message(),
                    icon = CineIcons.Warning,
                ) {
                    MobilePrimaryButton(stringResource(R.string.player_retry), actions::retry, icon = CineIcons.Retry)
                    MobileSecondaryButton(stringResource(R.string.player_back), onBack)
                }
            }
        }
    }
}

/** « Vous êtes toujours là ? » after a few episodes without a touch (`SPEC.md` § 4.11). */
@Composable
private fun StillWatching(
    actions: PlayerActions,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(CineTheme.colors.dialogScrim), contentAlignment = Alignment.Center) {
        MobileMessage(
            stringResource(R.string.player_still_title),
            stringResource(R.string.mobile_player_still_message),
        ) {
            MobilePrimaryButton(stringResource(R.string.player_still_continue), actions::keepWatching)
            MobileSecondaryButton(stringResource(R.string.player_still_stop), onBack)
        }
    }
}

/** The jumps of the buttons of the phone: 10 s each way (M1). */
private val JUMP = 10.seconds
private val BURST_LOOK = 100.milliseconds
private const val MAX_BRIGHTNESS = 255
private const val MIN_BRIGHTNESS = 0.01f
private const val INFO_NAME_WEIGHT = 0.8f
private const val PREVIEW_JUMP = 30
private val PREVIEW_SEEK = 3.seconds * 60
private const val PREVIEW_LEVEL = 0.7f
