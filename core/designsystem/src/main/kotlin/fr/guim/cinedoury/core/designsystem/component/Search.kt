package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.designsystem.theme.CineScale
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme

private val KeyShape = RoundedCornerShape(CineSizes.cardCorner)
private val KeyOutlineShape =
    RoundedCornerShape(CineSizes.cardCorner + CineSizes.focusOutlineGap + CineSizes.focusOutlineWidth / 2)
private val ChipShape = RoundedCornerShape(percent = 50)

/**
 * A key of the on-screen keyboard of the search (`SPEC.md` § 4.12): a character, or an icon (space,
 * erase). [span] keys wide, gaps included, so that the rows of the keyboard line up.
 *
 * @param selected a key that toggles a mode (the page of accents), while it is on.
 */
@Composable
fun KeyboardKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    span: Int = 1,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
    focus: CineFocus = rememberCineFocus(),
) {
    val colors = CineTheme.colors
    val focused = focus.isFocused
    val content =
        when {
            focused -> colors.background
            selected -> colors.onAccent
            else -> colors.text
        }
    val background =
        when {
            focused -> colors.text
            selected -> colors.accent
            else -> colors.surface2
        }
    Box(
        modifier =
            modifier
                .size(width = keySpan(span), height = CineSizes.keyboardKey)
                .withFocusRequester(focusRequester)
                .cineFocusable(focus, KeyShape, CineScale.BUTTON, onClick, KeyOutlineShape)
                .background(background),
        contentAlignment = Alignment.Center,
    ) {
        when {
            text != null -> {
                Text(text = text, style = CineTheme.typography.label, color = content, maxLines = 1)
            }

            icon != null -> {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = content,
                    modifier = Modifier.size(CineSizes.icon),
                )
            }
        }
    }
}

/** The width of a key [span] keys wide, the gaps between them included. */
fun keySpan(span: Int): Dp = CineSizes.keyboardKey * span + CineSpacing.xs * (span - 1)

/**
 * What the user typed, above the keyboard; never focusable (the keyboard types into it). A light
 * caret in the accent colour follows the text; without text, the [placeholder].
 */
@Composable
fun SearchQueryLine(
    text: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = CineIcons.Search,
) {
    val colors = CineTheme.colors
    val typography = CineTheme.typography
    Row(
        modifier =
            modifier
                .height(CineSizes.searchFieldHeight)
                .background(colors.surface1, KeyShape)
                .padding(horizontal = CineSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text2,
            modifier = Modifier.size(CineSizes.icon),
        )
        if (text.isEmpty()) {
            Caret()
            Text(text = placeholder, style = typography.body, color = colors.text3, maxLines = 1)
        } else {
            // The end of a long query stays in view: it is where the user types.
            Text(
                text = text,
                style = typography.body,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Caret()
        }
    }
}

@Composable
private fun Caret() {
    Box(
        Modifier
            .width(CineSizes.focusOutlineGap)
            .height(CineSizes.icon)
            .background(CineTheme.colors.accent),
    )
}

/**
 * A chip: a recent search, a genre. Smaller than a button, so that a few fit on a line; the focus
 * treatment of `UI.md` § 3.
 */
@Composable
fun SearchChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    focus: CineFocus = rememberCineFocus(),
) {
    val colors = CineTheme.colors
    val focused = focus.isFocused
    val content = if (focused) colors.background else colors.text
    Row(
        modifier =
            modifier
                .height(CineSizes.chipHeight)
                .withFocusRequester(focusRequester)
                .cineFocusable(focus, ChipShape, CineScale.BUTTON, onClick)
                .background(if (focused) colors.text else colors.surface2)
                .padding(horizontal = CineSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier =
                    Modifier.size(CineSizes.icon * 3 / 4),
            )
        }
        Text(text = text, style = CineTheme.typography.caption, color = content, maxLines = 1)
    }
}

/**
 * The voice search listening (`SPEC.md` § 4.12): over a dimmed screen, the microphone in the accent
 * colour, a ring that grows with the voice ([level], from 0 to 1, read at drawing time), [title] and
 * what was understood so far. With [reducedMotion] the ring stays still.
 */
@Composable
fun VoiceListening(
    title: String,
    transcript: String?,
    hint: String,
    level: () -> Float,
    modifier: Modifier = Modifier,
) {
    val colors = CineTheme.colors
    val reduced = CineTheme.reducedMotion
    val breathing = rememberInfiniteTransition(label = "voice")
    val pulse by breathing.animateFloat(
        initialValue = 0f,
        targetValue = if (reduced) 0f else 1f,
        animationSpec = infiniteRepeatable(tween(CineMotion.VOICE_PULSE_MILLIS), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        modifier = modifier.fillMaxSize().background(colors.dialogScrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CineSpacing.m),
            modifier = Modifier.widthIn(max = CineSizes.dialogWidth),
        ) {
            Box(Modifier.size(CineSizes.voiceRing), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(CineSizes.voiceRing)
                        .graphicsLayer {
                            val grow = (level().coerceIn(0f, 1f) * VOICE_GROWTH) + pulse * VOICE_PULSE
                            val scale = MIC_RATIO + (1f - MIC_RATIO) * grow.coerceAtMost(1f)
                            scaleX = scale
                            scaleY = scale
                            alpha = RING_ALPHA
                        }.background(colors.accent, CircleShape),
                )
                Box(
                    Modifier.size(CineSizes.voiceMic).background(colors.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = CineIcons.Mic,
                        contentDescription = null,
                        tint = colors.onAccent,
                        modifier = Modifier.size(CineSizes.icon * 2),
                    )
                }
            }
            Text(text = title, style = CineTheme.typography.title2, color = colors.text, textAlign = TextAlign.Center)
            Text(
                text = transcript?.takeIf { it.isNotBlank() } ?: hint,
                style = CineTheme.typography.body,
                color = if (transcript.isNullOrBlank()) colors.text2 else colors.text,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The ring at rest is the size of the microphone. */
private const val MIC_RATIO = 0.6f
private const val VOICE_GROWTH = 0.85f
private const val VOICE_PULSE = 0.15f
private const val RING_ALPHA = 0.3f
