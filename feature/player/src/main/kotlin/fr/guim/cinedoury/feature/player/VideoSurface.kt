package fr.guim.cinedoury.feature.player

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.model.AspectMode
import fr.guim.cinedoury.core.model.SubtitleBackground
import fr.guim.cinedoury.core.model.SubtitleEdge
import fr.guim.cinedoury.core.model.SubtitleStyle
import fr.guim.cinedoury.core.playback.DisplayModes
import fr.guim.cinedoury.core.playback.ScreenMode
import fr.guim.cinedoury.core.playback.SubtitleCues

/**
 * The picture of the player: the `PlayerView` of Media3 without its controls (the interface is the
 * app's), never focused. Its own subtitles are hidden: a [SubtitleLayer] over the same place draws
 * the cues at the size and height of the settings.
 *
 * @param sample a line drawn while the size is set and no subtitle is on screen.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    player: Player?,
    aspect: AspectMode,
    subtitles: SubtitleStyle,
    modifier: Modifier = Modifier,
    sample: String? = null,
) {
    val black = CineTheme.colors.shadow.toArgb()
    var cues by remember { mutableStateOf<List<Cue>>(emptyList()) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onCues(cueGroup: CueGroup) {
                    cues = cueGroup.cues
                }
            }
        player?.addListener(listener)
        cues = player?.currentCues?.cues.orEmpty()
        onDispose { player?.removeListener(listener) }
    }
    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    setKeepContentOnPlayerReset(true)
                    setShutterBackgroundColor(black)
                    subtitleView?.visibility = View.GONE
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode =
                    when (aspect) {
                        AspectMode.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectMode.Fill -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectMode.Zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
            },
            onRelease = { it.player = null },
        )
        SubtitleLayer(cues, subtitles, Modifier.fillMaxSize(), sample)
    }
}

/**
 * The subtitles over the picture, drawn by Media3's `SubtitleView` at the size and height of the
 * settings ([SubtitleCues]): a part of the height of the picture, the same in 1080p and in 4K. ASS
 * keep their colours, positions and the proportions of their sizes; PGS and VobSub pictures grow.
 *
 * @param sample drawn as a cue when [cues] is empty (the size is being set).
 */
@OptIn(UnstableApi::class)
@Composable
fun SubtitleLayer(
    cues: List<Cue>,
    style: SubtitleStyle,
    modifier: Modifier = Modifier,
    sample: String? = null,
) {
    val edge = CineTheme.colors.shadow.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { context -> SubtitleView(context) },
        update = { view ->
            view.setApplyEmbeddedStyles(true)
            view.setApplyEmbeddedFontSizes(true)
            view.setFractionalTextSize(style.textFraction)
            view.setBottomPaddingFraction(style.position.bottomFraction)
            view.setStyle(style.caption(edge))
            val shown = SubtitleCues.scale(cues, style)
            view.setCues(
                if (shown.isEmpty() && sample != null) listOf(Cue.Builder().setText(sample).build()) else shown,
            )
        },
    )
}

/** The colours of Media3: the text, the shade behind it, the band behind the line, the edge. */
@OptIn(UnstableApi::class)
private fun SubtitleStyle.caption(edgeColor: Int): CaptionStyleCompat {
    val transparent = android.graphics.Color.TRANSPARENT
    val shade = background.argb.toInt()
    return CaptionStyleCompat(
        color.argb.toInt(),
        if (background == SubtitleBackground.Shaded) shade else transparent,
        if (background == SubtitleBackground.Band) shade else transparent,
        when (edge) {
            SubtitleEdge.None -> CaptionStyleCompat.EDGE_TYPE_NONE
            SubtitleEdge.Outline -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            SubtitleEdge.Shadow -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        },
        edgeColor,
        null,
    )
}

/** The screen stays on while the player is open. */
@Composable
fun KeepScreenOn() {
    val view: View = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Frame rate matching with a change of mode (`FONCTIONNALITES.md` § 5): the window asks for the mode
 * of the same size whose refresh rate fits the film ([DisplayModes]); the mode of before comes back
 * when the player closes. On by default, in the settings. With [videoHeight] (« Résolution
 * adaptée », off by default), the mode of the size of the film.
 */
@Composable
fun MatchFrameRate(
    activity: Activity?,
    frameRate: Float?,
    enabled: Boolean,
    videoHeight: Int? = null,
) {
    DisposableEffect(activity, frameRate, enabled, videoHeight) {
        val window = activity?.window
        // The display of the window's view: `Activity.display` throws where the context has none.
        val display = activity?.window?.decorView?.display
        val previous = window?.attributes?.preferredDisplayModeId ?: 0
        val rate = frameRate.takeIf { enabled } ?: 0f
        if (window != null && display != null && (rate > 0f || videoHeight != null)) {
            val current =
                display.mode.let {
                    ScreenMode(
                        it.modeId,
                        it.physicalWidth,
                        it.physicalHeight,
                        it.refreshRate,
                    )
                }
            val modes =
                display.supportedModes.map {
                    ScreenMode(
                        it.modeId,
                        it.physicalWidth,
                        it.physicalHeight,
                        it.refreshRate,
                    )
                }
            DisplayModes.choose(modes, current, rate, videoHeight)?.let { mode ->
                window.attributes = window.attributes.apply { preferredDisplayModeId = mode.id }
            }
        }
        onDispose {
            if (window != null && window.attributes.preferredDisplayModeId != previous) {
                window.attributes = window.attributes.apply { preferredDisplayModeId = previous }
            }
        }
    }
}
