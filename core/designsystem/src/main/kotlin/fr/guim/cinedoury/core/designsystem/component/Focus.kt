package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.TvLayout

/**
 * Whether an element holds the focus, updated synchronously by the focus system. Components read
 * it rather than the focus interactions of an `InteractionSource`, which arrive a frame later.
 * Hoist it to know, from a parent, which element has the focus (to change the backdrop, say).
 */
@Stable
class CineFocus private constructor(
    private val shownAsFocused: Boolean,
) {
    constructor() : this(shownAsFocused = false)

    private var focused by mutableStateOf(false)

    val isFocused: Boolean get() = shownAsFocused || focused

    internal fun update(isFocused: Boolean) {
        focused = isFocused
    }

    companion object {
        /**
         * Draws the element as focused whatever the focus system says: for previews and reference
         * screenshots, where Robolectric cannot give the focus (`CLAUDE.md`, « Pièges »).
         */
        fun preview(): CineFocus = CineFocus(shownAsFocused = true)
    }
}

/**
 * Tag of the element drawn as focused, for previews and reference screenshots: Robolectric cannot
 * give the focus under native graphics (`CLAUDE.md`, « Pièges »). Provide it around a screen; the
 * element whose [rememberCineFocus] tag matches is drawn focused.
 */
val LocalPreviewFocus = staticCompositionLocalOf<String?> { null }

/**
 * The first line a list shows so that its line `[prefix]N`, drawn focused by [LocalPreviewFocus],
 * is in view with a few lines above it, as the D-pad would have scrolled; 0 outside screenshots.
 */
@Composable
fun previewFirstVisible(prefix: String): Int {
    val index =
        LocalPreviewFocus.current
            ?.takeIf { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.toIntOrNull()
    return ((index ?: 0) - PREVIEW_LINES_ABOVE).coerceAtLeast(0)
}

private const val PREVIEW_LINES_ABOVE = 3

/**
 * A [CineFocus] for one element. [previewTag] names the element for [LocalPreviewFocus]: when it
 * matches, the element is drawn as focused whatever the focus system says.
 */
@Composable
fun rememberCineFocus(previewTag: String? = null): CineFocus {
    val preview = previewTag != null && LocalPreviewFocus.current == previewTag
    return remember(preview) { if (preview) CineFocus.preview() else CineFocus() }
}

/**
 * Gives the focus to [requester] once the screen is laid out (a frame later, so that an element of
 * a lazy list is attached: `CLAUDE.md`, « Pièges »). Runs again when [key] changes.
 */
@Composable
fun InitialFocus(
    requester: FocusRequester,
    key: Any? = Unit,
    enabled: Boolean = true,
) {
    LaunchedEffect(key, enabled) {
        if (!enabled) return@LaunchedEffect
        // An element just added to a lazy list is attached a frame or two later: try at each frame,
        // a few times (nothing to focus if the element left the composition in the meantime).
        repeat(FOCUS_ATTEMPTS) {
            withFrameNanos { }
            if (runCatching { requester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
}

private const val FOCUS_ATTEMPTS = 10

/**
 * Progress of the focus animation, from 0 (not focused) to 1 (focused): 150 ms on the way in,
 * 120 ms on the way out (`UI.md` § 3).
 */
@Composable
fun focusProgress(focus: CineFocus): State<Float> =
    animateFloatAsState(
        targetValue = if (focus.isFocused) 1f else 0f,
        animationSpec = CineMotion.focus(gained = focus.isFocused, reduced = CineTheme.reducedMotion),
        label = "focus",
    )

/**
 * The focus treatment of `UI.md` § 3 on a clickable element: it grows to [focusedScale], gets a
 * 3 dp outline in the `focus` colour 2 dp away from its edge, a soft light halo around the outline
 * (visible on pure black, where the shadow is not) and a drop shadow. The outline and the scale
 * make the focus visible without relying on colour.
 *
 * @param focus receives the focus state, for the parts of the component that change with it.
 * @param outlineShape shape of the outline, drawn [CineSizes.focusOutlineGap] outside [shape]:
 * pass a shape with a larger corner radius for concentric corners.
 * @param halo blur radius of the halo, [CineSizes.focusHalo] but where the room is counted (the rail).
 * @param progress the [focusProgress] of [focus], when the component reads it too (the caption of a
 * card): one animation per element, not two.
 */
@Composable
fun Modifier.cineFocusable(
    focus: CineFocus,
    shape: Shape,
    focusedScale: Float,
    onClick: () -> Unit,
    outlineShape: Shape = shape,
    interactionSource: MutableInteractionSource? = null,
    halo: Dp = CineSizes.focusHalo,
    progress: State<Float>? = null,
): Modifier =
    cineFocusDecoration(focus, shape, focusedScale, outlineShape, halo, progress)
        .onFocusChanged { focus.update(it.isFocused) }
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)

/**
 * The drawing part of [cineFocusable] (scale, halo, outline, shadow), driven by [focus], for an
 * element whose focusable part is inside it (a text field). Clips the content to [shape].
 */
@Composable
internal fun Modifier.cineFocusDecoration(
    focus: CineFocus,
    shape: Shape,
    focusedScale: Float,
    outlineShape: Shape = shape,
    halo: Dp = CineSizes.focusHalo,
    progressState: State<Float>? = null,
): Modifier {
    // Read only in the layer and draw phases: the animation never recomposes the element.
    val progress by progressState ?: focusProgress(focus)
    val colors = CineTheme.colors
    return this
        .graphicsLayer {
            val scale = 1f + (focusedScale - 1f) * progress
            scaleX = scale
            scaleY = scale
            shadowElevation = CineSizes.focusElevation.toPx() * progress
            this.shape = shape
            clip = false
            ambientShadowColor = colors.shadow
            spotShadowColor = colors.shadow
        }.drawWithCache {
            val stroke = CineSizes.focusOutlineWidth.toPx()
            val inset = CineSizes.focusOutlineGap.toPx() + stroke / 2f
            val outline =
                outlineShape.createOutline(
                    Size(size.width + inset * 2f, size.height + inset * 2f),
                    layoutDirection,
                    this,
                )
            // A gaussian glow (a blur mask filter, every API level the app supports), blurred once into a
            // bitmap shared by the elements of the same size: no blur at each frame (`FocusHalo`). Made
            // at the first focus: most elements of a screen never get it.
            val focusHalo by lazy(LazyThreadSafetyMode.NONE) {
                FocusHalo.of(
                    outline = outline,
                    blur = halo.toPx(),
                    color = colors.focusHalo.copy(alpha = 1f).toArgb(),
                    scale = TvLayout.imageScale(density),
                )
            }
            val outlineSize = Size(size.width + inset * 2f, size.height + inset * 2f)
            onDrawWithContent {
                if (progress > 0f) {
                    with(focusHalo) {
                        draw(
                            Offset(-inset, -inset),
                            outlineSize.width,
                            outlineSize.height,
                            colors.focusHalo.alpha * progress,
                        )
                    }
                }
                drawContent()
                if (progress > 0f) {
                    translate(left = -inset, top = -inset) {
                        drawOutline(outline, colors.focus, alpha = progress, style = Stroke(width = stroke))
                    }
                }
            }
        }.clip(shape)
}
