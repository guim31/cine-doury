package fr.guim.cinedoury.feature.player

import android.app.Activity
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.model.AspectMode
import fr.guim.cinedoury.core.model.SubtitleEdge
import fr.guim.cinedoury.core.model.SubtitleStyle
import fr.guim.cinedoury.core.playback.DisplayModes
import fr.guim.cinedoury.core.playback.ScreenMode

/**
 * The picture of the player: the `PlayerView` of Media3 without its controls (the interface is the
 * app's), never focused, its subtitles drawn in the style of the settings (ASS keep their colours
 * and positions, Media3 permitting; their sizes follow the setting).
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    player: Player?,
    aspect: AspectMode,
    subtitles: SubtitleStyle,
    modifier: Modifier = Modifier,
) {
    val black = CineTheme.colors.shadow.toArgb()
    val edge = CineTheme.colors.shadow.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                setKeepContentOnPlayerReset(true)
                setShutterBackgroundColor(black)
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
            view.subtitleView?.apply {
                setApplyEmbeddedStyles(true)
                setApplyEmbeddedFontSizes(false)
                setFractionalTextSize(subtitles.size.fraction)
                setBottomPaddingFraction(subtitles.position.bottomFraction)
                setStyle(
                    CaptionStyleCompat(
                        subtitles.color.argb.toInt(),
                        subtitles.background.argb.toInt(),
                        android.graphics.Color.TRANSPARENT,
                        when (subtitles.edge) {
                            SubtitleEdge.None -> CaptionStyleCompat.EDGE_TYPE_NONE
                            SubtitleEdge.Outline -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
                            SubtitleEdge.Shadow -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                        },
                        edge,
                        null,
                    ),
                )
            }
        },
        onRelease = { it.player = null },
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
 * when the player closes. On by default, in the settings.
 */
@Composable
fun MatchFrameRate(
    activity: Activity?,
    frameRate: Float?,
    enabled: Boolean,
) {
    DisposableEffect(activity, frameRate, enabled) {
        val window = activity?.window
        val display = activity?.display
        val previous = window?.attributes?.preferredDisplayModeId ?: 0
        val rate = frameRate.takeIf { enabled }
        if (window != null && display != null && rate != null) {
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
            DisplayModes.choose(modes, current, rate)?.let { mode ->
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
