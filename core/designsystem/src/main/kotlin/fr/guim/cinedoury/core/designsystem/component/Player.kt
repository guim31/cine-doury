package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import fr.guim.cinedoury.core.designsystem.theme.CineScale
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.tabular

private val PlayerShape = RoundedCornerShape(CineSizes.cardCorner)
private val PlayerOutlineShape =
    RoundedCornerShape(CineSizes.cardCorner + CineSizes.focusOutlineGap + CineSizes.focusOutlineWidth / 2)

/**
 * The veils of the player's interface (`UI.md` § 8, « Lecteur »): dark at the top and at the bottom
 * of the picture, so that the title, the hour and the controls read over any frame.
 */
fun Modifier.playerVeils(color: Color): Modifier =
    background(
        Brush.verticalGradient(
            0f to color,
            PLAYER_VEIL_TOP to Color.Transparent,
            PLAYER_VEIL_BOTTOM to Color.Transparent,
            1f to color,
        ),
    )

private const val PLAYER_VEIL_TOP = 0.28f
private const val PLAYER_VEIL_BOTTOM = 0.55f

/**
 * The progress bar of the player, over the whole width: what is read (accent), what is loaded
 * (white), the chapters (gaps), and with the focus, a knob where the picture is or where the search
 * goes ([seek]).
 *
 * @param progress, [buffered], [seek] and [chapters] between 0 and 1.
 */
@Composable
fun PlayerProgressBar(
    progress: Float,
    buffered: Float,
    modifier: Modifier = Modifier,
    seek: Float? = null,
    chapters: List<Float> = emptyList(),
    focused: Boolean = false,
) {
    val colors = CineTheme.colors
    Canvas(modifier.fillMaxWidth().height(CineSizes.progressKnob)) {
        val bar = (if (focused) CineSizes.progressBarFocused else CineSizes.progressBar).toPx()
        val top = (size.height - bar) / 2
        val radius = CornerRadius(bar / 2)
        drawRoundRect(colors.progressTrack, Offset(0f, top), Size(size.width, bar), radius)
        drawRoundRect(colors.progressBuffer, Offset(0f, top), Size(size.width * buffered.coerceIn(0f, 1f), bar), radius)
        drawRoundRect(colors.accent, Offset(0f, top), Size(size.width * progress.coerceIn(0f, 1f), bar), radius)
        val gap = CineSizes.chapterGap.toPx()
        chapters.filter { it > 0f && it < 1f }.forEach { start ->
            drawRect(colors.background, Offset(size.width * start - gap / 2, top), Size(gap, bar))
        }
        if (focused) {
            val at = (seek ?: progress).coerceIn(0f, 1f)
            drawCircle(colors.text, radius = size.height / 2, center = Offset(size.width * at, size.height / 2))
        }
    }
}

/**
 * The preview picture of a position (trickplay, `UI.md` § 8): one picture of a tile of
 * [columns] × [rows] pictures, 320 dp wide, with the time under it. The tile comes from [url], with
 * [headers] (the server asks for its token).
 */
@Composable
fun TrickplayPreview(
    url: String?,
    headers: Map<String, String>,
    columns: Int,
    rows: Int,
    column: Int,
    row: Int,
    aspectRatio: Float,
    time: String,
    modifier: Modifier = Modifier,
) {
    val width = CineSizes.trickplayWidth
    val height = width / aspectRatio
    val context = LocalPlatformContext.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width, height)
                .clip(PlayerShape)
                .background(CineTheme.colors.surface1)
                .border(CineSizes.hairline, CineTheme.colors.focus, PlayerShape)
                .clipToBounds(),
        ) {
            if (url != null) {
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .httpHeaders(NetworkHeaders.Builder().apply { headers.forEach { (k, v) -> set(k, v) } }.build())
                        .build()
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier =
                        Modifier
                            .requiredSize(width * columns, height * rows)
                            .offset(
                                x = width * (columns - 1) / 2 - width * column,
                                y =
                                    height * (rows - 1) / 2 - height * row,
                            ),
                )
            }
        }
        Text(
            text = time,
            style = CineTheme.typography.label.tabular(),
            color = CineTheme.colors.text,
            modifier = Modifier.padding(top = CineSpacing.xs),
        )
    }
}

/**
 * « Épisode suivant » (`UI.md` § 8): a card at the bottom right during the credits, with the frame of
 * the episode, its title, and a circular countdown around « Lire »; the film stays visible behind.
 *
 * @param countdown what is left of the countdown, from 1 to 0; null without countdown (the user
 * plays it).
 */
@Composable
fun NextEpisodeCard(
    label: String,
    title: String,
    subtitle: String?,
    countdown: Float?,
    countdownText: String?,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    picture: (@Composable () -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    focus: CineFocus = rememberCineFocus(),
) {
    val colors = CineTheme.colors
    Row(
        modifier =
            modifier
                .width(CineSizes.nextCardWidth)
                .withFocusRequester(focusRequester)
                .cineFocusable(focus, PlayerShape, CineScale.THUMBNAIL, onPlay, PlayerOutlineShape)
                .background(colors.playerPanel)
                .padding(CineSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(CineSizes.thumbnail).clip(PlayerShape).background(colors.surface3)) {
            picture?.invoke()
            Box(Modifier.align(Alignment.Center).size(CineSizes.countdownRing), contentAlignment = Alignment.Center) {
                val ring = colors.accent
                val track = colors.progressTrack
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = CineSizes.countdownStroke.toPx()
                    drawCircle(colors.playerVeil, radius = size.minDimension / 2)
                    drawArc(track, 0f, FULL_TURN, false, style = Stroke(stroke))
                    if (countdown != null) {
                        drawArc(
                            ring,
                            START_ANGLE,
                            FULL_TURN * countdown.coerceIn(0f, 1f),
                            false,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                Icon(
                    CineIcons.Play,
                    contentDescription = null,
                    tint = colors.text,
                    modifier = Modifier.size(CineSizes.icon),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CineSpacing.xs / 2)) {
            Text(
                text = countdownText ?: label,
                style = CineTheme.typography.caption,
                color = colors.accent,
                maxLines = 1,
            )
            Text(
                text = title,
                style = CineTheme.typography.label,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(text = subtitle, style = CineTheme.typography.caption, color = colors.text2, maxLines = 1)
            }
        }
    }
}

private const val FULL_TURN = 360f
private const val START_ANGLE = -90f

/**
 * Subtitles as the player draws them, over a picture of any size: the text is a part of the height
 * of the picture ([sizeFraction]), as are its edge and its height above the bottom
 * ([bottomFraction]); [band] spreads the background over the width of the picture.
 */
@Composable
fun SubtitlePreview(
    text: String,
    color: Color,
    background: Color,
    outline: Boolean,
    shadow: Boolean,
    sizeFraction: Float,
    bottomFraction: Float,
    modifier: Modifier = Modifier,
    band: Boolean = false,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val pictureHeight = maxHeight
        val density = LocalDensity.current
        val textHeight = pictureHeight * sizeFraction
        val fontSize = with(density) { textHeight.toSp() }
        val edgePx = with(density) { textHeight.toPx() } * EDGE_FRACTION
        val edge = CineTheme.colors.shadow
        val line =
            @Composable { textModifier: Modifier ->
                Text(
                    text = text,
                    textAlign = TextAlign.Center,
                    color = color,
                    style =
                        CineTheme.typography.body.copy(
                            fontSize = fontSize,
                            lineHeight = fontSize * LINE_HEIGHT,
                            shadow =
                                when {
                                    outline -> Shadow(edge, Offset.Zero, edgePx * 2)
                                    shadow -> Shadow(edge, Offset(edgePx, edgePx), edgePx)
                                    else -> null
                                },
                        ),
                    modifier = textModifier,
                )
            }
        val bottom = Modifier.align(Alignment.BottomCenter).padding(bottom = pictureHeight * bottomFraction)
        if (band) {
            Box(bottom.fillMaxWidth().background(background), contentAlignment = Alignment.Center) {
                line(Modifier.padding(vertical = textHeight * BAND_PADDING))
            }
        } else {
            line(bottom.background(background).padding(horizontal = textHeight * BOX_PADDING))
        }
    }
}

private const val LINE_HEIGHT = 1.2f

/** The edge of the letters: a part of their height, the same at any resolution. */
private const val EDGE_FRACTION = 0.06f
private const val BOX_PADDING = 0.25f
private const val BAND_PADDING = 0.2f

/**
 * The bar of the subtitles, over the top of the picture while their size is set: the picture and
 * its subtitles stay in sight below. [content] holds the stepper and its rows, side by side; [note]
 * says when the subtitles cannot change.
 */
@Composable
fun SubtitleBar(
    modifier: Modifier = Modifier,
    note: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .width(CineSizes.subtitleBarWidth)
                .background(CineTheme.colors.playerPanel, PlayerShape)
                .padding(CineSpacing.s),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        if (note != null) {
            Text(text = note, style = CineTheme.typography.caption, color = CineTheme.colors.text2)
        }
    }
}

/**
 * The technical information of the player (`FONCTIONNALITES.md` § 5): a block over the picture,
 * at the top left, one fact per line, in tabular figures; readable on a photo of the screen.
 */
@Composable
fun PlaybackInfoSheet(
    title: String,
    lines: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val colors = CineTheme.colors
    Column(
        modifier =
            modifier
                .width(CineSizes.infoSheetWidth)
                .background(colors.playerPanel, PlayerShape)
                .padding(CineSpacing.m),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.xs / 2),
    ) {
        Text(
            text = title,
            style = CineTheme.typography.title2,
            color = colors.text,
            modifier = Modifier.padding(bottom = CineSpacing.xs),
        )
        lines.forEach { (label, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.s)) {
                Text(
                    text = label,
                    style = CineTheme.typography.caption,
                    color = colors.text2,
                    modifier = Modifier.width(CineSizes.infoLabelWidth),
                )
                Text(
                    text = value,
                    style = CineTheme.typography.caption.tabular(),
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
