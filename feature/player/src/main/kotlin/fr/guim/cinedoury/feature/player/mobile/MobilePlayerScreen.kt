package fr.guim.cinedoury.feature.player.mobile

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.mobile.MobileMessage
import fr.guim.cinedoury.core.designsystem.mobile.MobilePrimaryButton
import fr.guim.cinedoury.core.designsystem.mobile.MobileSecondaryButton
import fr.guim.cinedoury.core.designsystem.mobile.MobileSheet
import fr.guim.cinedoury.core.designsystem.mobile.MobileSheetLine
import fr.guim.cinedoury.core.designsystem.mobile.MobileSheetLines
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.MobileSizes
import fr.guim.cinedoury.core.model.PlaybackRequest
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
import fr.guim.cinedoury.feature.player.message
import fr.guim.cinedoury.feature.player.playerSubtitle
import fr.guim.cinedoury.feature.player.skipLabel
import fr.guim.cinedoury.feature.player.toClockText
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
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
    const val NEXT = "player-next"
    const val SKIP = "player-skip"
    const val CLOSE = "player-close"
    const val ERROR = "player-error"
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
    FullScreen(LocalActivity.current)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }
    MobilePlayerScreen(
        state = state,
        actions = viewModel,
        onBack = viewModel::close,
        video = { VideoSurface(player, state.settings.aspect, state.settings.subtitles, Modifier.fillMaxSize()) },
    )
}

/**
 * The film in landscape and full screen (M1): the bars of the system hidden (a swipe from an edge
 * brings them back for a moment), the orientation of the sensor in landscape; all given back when the
 * player closes.
 */
@Composable
private fun FullScreen(activity: Activity?) {
    DisposableEffect(activity) {
        val window = activity?.window
        val before = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = before ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

/**
 * The player of the phone (M1, `UI.md` § 9): nothing over the picture while it plays; a tap shows the
 * controls (the title, −10 s, play or pause, +10 s, the bar to slide, the tracks, the next episode),
 * which fade out 4 s after the last touch. « Passer l'intro » and « Épisode suivant » come as on the
 * TV. The gestures of the screen (double tap, brightness, volume, pinch) come with M2.
 *
 * @param previewControls shows the controls (screenshots); [previewTracks] opens the tracks.
 */
@Composable
fun MobilePlayerScreen(
    state: PlayerState,
    actions: PlayerActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    previewControls: Boolean = false,
    previewTracks: Boolean = false,
    video: @Composable () -> Unit = {},
) {
    var controls by remember { mutableStateOf(previewControls) }
    var tracks by remember { mutableStateOf(previewTracks) }
    var touched by remember { mutableIntStateOf(0) }
    // Where the finger holds the bar, while it slides; null otherwise.
    var sliding by remember { mutableStateOf<Float?>(null) }
    val touch: () -> Unit = {
        touched++
        actions.interacted()
    }
    // The controls fade out 4 s after the last touch while the film plays, unless a finger is busy.
    val busy = sliding != null || tracks || previewControls
    LaunchedEffect(controls, state.playing, touched, busy) {
        if (controls && state.playing && !busy) {
            delay(CineMotion.OSD_HIDE_AFTER_MILLIS.toLong())
            controls = false
        }
    }
    BackHandler {
        when {
            tracks -> tracks = false
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
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    touch()
                    controls = !controls
                },
    ) {
        Box(Modifier.fillMaxSize().testTag(MobilePlayerTags.SURFACE)) { video() }
        if (state.loading || state.buffering) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = CineTheme.colors.accent)
        }
        AnimatedVisibility(controls && state.error == null, enter = fadeIn(), exit = fadeOut()) {
            Controls(
                state = state,
                actions = actions,
                onBack = onBack,
                sliding = sliding,
                onSlide = {
                    sliding = it
                    touch()
                },
                onTracks = {
                    tracks = true
                    touch()
                },
                touch = touch,
            )
        }
        Pills(state, actions, controls, Modifier.align(Alignment.BottomEnd))
        state.error?.let { ErrorOverlay(it, actions, onBack) }
        if (state.stillWatching) StillWatching(actions, onBack)
    }
    if (tracks) TracksSheet(state, actions) { tracks = false }
}

@Composable
private fun Controls(
    state: PlayerState,
    actions: PlayerActions,
    onBack: () -> Unit,
    sliding: Float?,
    onSlide: (Float?) -> Unit,
    onTracks: () -> Unit,
    touch: () -> Unit,
) {
    val colors = CineTheme.colors
    Box(Modifier.fillMaxSize().background(colors.playerScrim)) {
        // The title, at the top.
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
                    Text(
                        it,
                        style = CineTheme.typography.caption,
                        color = colors.text2,
                        maxLines = 1,
                    )
                }
            }
            RoundButton(
                CineIcons.Tracks,
                stringResource(R.string.player_tracks),
                onTracks,
                Modifier.testTag(MobilePlayerTags.TRACKS),
            )
        }
        // −10 s, play or pause, +10 s, in the middle.
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(CineIcons.FastRewind, stringResource(R.string.mobile_player_back_10), {
                touch()
                actions.seekTo((state.position - JUMP).coerceAtLeast(Duration.ZERO))
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
                actions.seekTo((state.position + JUMP).coerceAtMost(state.duration))
            }, Modifier.testTag(MobilePlayerTags.FORWARD_10))
        }
        // The bar, the times and the next episode, at the bottom.
        Column(
            Modifier
                .align(
                    Alignment.BottomCenter,
                ).fillMaxWidth()
                .background(colors.playerVeil)
                .safeDrawingPadding()
                .padding(horizontal = CineSpacing.s),
        ) {
            val duration = state.duration.inWholeMilliseconds.coerceAtLeast(1)
            val shown = sliding ?: (state.position.inWholeMilliseconds.toFloat() / duration)
            Slider(
                value = shown.coerceIn(0f, 1f),
                onValueChange = onSlide,
                onValueChangeFinished = {
                    sliding?.let { actions.seekTo((it * duration).toLong().milliseconds) }
                    onSlide(null)
                },
                modifier = Modifier.fillMaxWidth().testTag(MobilePlayerTags.SEEK),
                colors =
                    SliderDefaults.colors(
                        thumbColor = colors.accent,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = colors.progressTrack,
                    ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val position = sliding?.let { (it * duration).toLong().milliseconds } ?: state.position
                Text(
                    "${position.toClockText()} / ${state.duration.toClockText()}",
                    style = CineTheme.typography.caption,
                    color = colors.text2,
                    modifier = Modifier.weight(1f).testTag(MobilePlayerTags.POSITION),
                )
                state.next?.let {
                    MobileSecondaryButton(
                        stringResource(R.string.player_next_episode),
                        actions::playNext,
                        Modifier.testTag(MobilePlayerTags.NEXT),
                        icon = CineIcons.SkipNext,
                    )
                }
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
                            MobileSheetLine(
                                track.label(),
                                null,
                                "subtitles-${track.index}",
                            ) { actions.selectSubtitles(track.index) }
                        },
                    selected = state.subtitleIndex?.let { "subtitles-$it" } ?: "subtitles-none",
                )
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
            .background(
                CineTheme.colors.dialogScrim,
            ).safeDrawingPadding()
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
