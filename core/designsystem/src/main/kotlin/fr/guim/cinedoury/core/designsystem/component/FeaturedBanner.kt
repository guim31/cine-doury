package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.tabular
import fr.guim.cinedoury.core.designsystem.theme.textLineHeight
import kotlinx.coroutines.delay

/**
 * What the featured banner shows: the focused title (`UI.md` § 8, « Accueil »). [key] identifies
 * it, so that the banner changes only when the title does.
 *
 * @param backdrop the full-width picture; null leaves the ambient backdrop visible.
 * @param logo the title as a picture (clearlogo), drawn instead of the [title] text.
 */
@Immutable
data class Featured(
    val key: String,
    val title: String,
    val metadata: String? = null,
    val tagline: String? = null,
    val backdrop: (@Composable () -> Unit)? = null,
    val logo: (@Composable () -> Unit)? = null,
)

/**
 * [featured] once it has rested for 250 ms (the same title is always up to date), and the recent
 * titles by key: the fades are keyed by title, not by value, since a value holds new lambdas at each
 * composition.
 */
@Composable
private fun settledFeatured(featured: Featured?): Pair<String?, Map<String, Featured>> {
    var settled by remember { mutableStateOf(featured) }
    val latest by rememberUpdatedState(featured)
    LaunchedEffect(featured?.key) {
        if (settled != null && settled?.key != latest?.key) delay(CineMotion.BACKDROP_DELAY_MILLIS.toLong())
        settled = latest
    }
    val shown = if (settled?.key == featured?.key) featured else settled
    val recent = remember { LinkedHashMap<String, Featured>() }
    shown?.let { recent[it.key] = it }
    while (recent.size > RECENT_TITLES) recent.remove(recent.keys.first())
    return shown?.key to recent
}

/**
 * The picture of the featured title, full width at the top of the home, faded into the screen on the
 * left and at the bottom (`UI.md` § 4 and § 8). It stays in place while the rows scroll, and follows
 * the focus once it has rested for 250 ms, with a 300 ms fade: never a carousel that moves alone.
 */
@Composable
fun FeaturedBackdrop(
    featured: Featured?,
    modifier: Modifier = Modifier,
) {
    val (shown, recent) = settledFeatured(featured)
    Crossfade(
        targetState = shown,
        animationSpec = CineMotion.fade<Float>(CineMotion.BACKDROP_FADE_MILLIS),
        modifier = modifier,
        label = "featured-backdrop",
    ) { key ->
        Box(Modifier.fillMaxSize().bannerFade()) {
            recent[key]?.backdrop?.invoke()
        }
    }
}

/**
 * The featured banner of the home (`FONCTIONNALITES.md` § 2): the logo of the focused title (its
 * title in type otherwise), its line of metadata, one line that sells it, and the [actions]
 * (« Lire », « Fiche ») under them, each always at the same place. It follows the focus like
 * [FeaturedBackdrop]; the [actions] stay out of the fade, so that a button keeps the focus.
 *
 * Laid out in the height it is given (essai réel 4, the home gives it what a row and a half of the
 * rows leave), at the bottom of it; what does not fit goes, in this order: the line that sells the
 * title, then the column itself, for a single band (the title on two lines of « Titre 2 », the
 * metadata beside the buttons, [featuredBannerMinHeight]). The choice depends on the height only,
 * never on the title: moving from one title to the next changes the content, in a fade, never the
 * layout.
 */
@Composable
fun FeaturedBanner(
    featured: Featured?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val (shown, recent) = settledFeatured(featured)
    val heights = featuredHeights()
    BoxWithConstraints(modifier, contentAlignment = Alignment.BottomStart) {
        when {
            maxHeight >= heights.full -> FeaturedColumn(shown, recent, tagline = true, actions)
            maxHeight >= heights.noTagline -> FeaturedColumn(shown, recent, tagline = false, actions)
            else -> FeaturedBand(shown, recent, actions)
        }
    }
}

/** The smallest height of [FeaturedBanner]: the single band, under which nothing goes. */
@Composable
fun featuredBannerMinHeight(): Dp = featuredHeights().band

private class FeaturedHeights(
    val full: Dp,
    val noTagline: Dp,
    val band: Dp,
)

/** The heights of the three layouts of the banner, at the text size of the settings. */
@Composable
private fun featuredHeights(): FeaturedHeights {
    val typography = CineTheme.typography
    val zone = heroTitleZone(textLineHeight(typography.title1))
    val metadata = textLineHeight(typography.caption)
    val tagline = textLineHeight(typography.body) * TAGLINE_LINES
    val title2 = textLineHeight(typography.title2)
    val buttons = CineSpacing.s + CineSpacing.xs + CineSizes.buttonHeight
    return FeaturedHeights(
        full = zone + CineSpacing.xs + metadata + CineSpacing.xs + tagline + buttons,
        noTagline = zone + CineSpacing.xs + metadata + buttons,
        band = title2 * TITLE_LINES + CineSpacing.xs + CineSizes.buttonHeight,
    )
}

/** The zone of the logo or of the title in a column: [CineSizes.heroTitleHeight], or two lines of a larger text. */
private fun heroTitleZone(title1Line: Dp): Dp = maxOf(CineSizes.heroTitleHeight, title1Line * TITLE_LINES)

/** The banner of 0.9.0: logo or title, metadata, [tagline] if asked, the buttons under them. */
@Composable
private fun FeaturedColumn(
    shown: String?,
    recent: Map<String, Featured>,
    tagline: Boolean,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = Modifier.width(CineSizes.heroTextWidth),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
    ) {
        Crossfade(
            targetState = shown,
            animationSpec = CineMotion.fade<Float>(CineMotion.BACKDROP_FADE_MILLIS),
            label = "featured-text",
        ) { key ->
            recent[key]?.let { FeaturedText(it, tagline) }
        }
        Row(
            modifier = Modifier.padding(top = CineSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/**
 * The text of the column on a fixed layout (essai réel 3): the logo or the title in a zone of constant
 * height, aligned at its bottom; the line of metadata and, if [tagline], [TAGLINE_LINES] lines of text
 * always take their room, even empty or short.
 */
@Composable
private fun FeaturedText(
    featured: Featured,
    tagline: Boolean,
) {
    val colors = CineTheme.colors
    val typography = CineTheme.typography
    val zone = heroTitleZone(textLineHeight(typography.title1))
    Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
        TitleZone(featured, zone, typography.title1)
        Text(
            text = featured.metadata.orEmpty(),
            style = typography.caption.tabular(),
            color = colors.text2,
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (tagline) {
            Text(
                text = featured.tagline.orEmpty(),
                style = typography.body,
                color = colors.text,
                minLines = TAGLINE_LINES,
                maxLines = TAGLINE_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The single band (a small banner): title on two lines of « Titre 2 », the metadata beside the buttons. */
@Composable
private fun FeaturedBand(
    shown: String?,
    recent: Map<String, Featured>,
    actions: @Composable RowScope.() -> Unit,
) {
    val zone =
        with(LocalDensity.current) {
            CineTheme.typography.title2.lineHeight
                .toDp()
        } * TITLE_LINES
    Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
        Crossfade(
            targetState = shown,
            animationSpec = CineMotion.fade<Float>(CineMotion.BACKDROP_FADE_MILLIS),
            modifier = Modifier.width(CineSizes.heroTextWidth),
            label = "featured-title",
        ) { key ->
            recent[key]?.let { TitleZone(it, zone, CineTheme.typography.title2) }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions()
            Crossfade(
                targetState = shown,
                animationSpec = CineMotion.fade<Float>(CineMotion.BACKDROP_FADE_MILLIS),
                modifier = Modifier.weight(1f),
                label = "featured-metadata",
            ) { key ->
                Text(
                    text = recent[key]?.metadata.orEmpty(),
                    style = CineTheme.typography.caption.tabular(),
                    color = CineTheme.colors.text2,
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The logo, scaled into the zone [height] high, or the title on two lines at most, at its bottom. */
@Composable
private fun TitleZone(
    featured: Featured,
    height: Dp,
    style: TextStyle,
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(height),
        contentAlignment = Alignment.BottomStart,
    ) {
        val logo = featured.logo
        if (logo != null) {
            Box(Modifier.sizeIn(maxWidth = CineSizes.logoMax.width, maxHeight = height)) { logo() }
        } else {
            Text(
                text = featured.title,
                style = style,
                color = CineTheme.colors.text,
                maxLines = TITLE_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The backdrop of the banner fades out to transparent on the left and at the bottom, into the ambient
 * backdrop under it (`UI.md` § 4 and § 8): a mask, not a veil of the background colour, so that no
 * edge shows where the picture ends, whatever the ambience and the theme. The ambient backdrop keeps
 * its own readability veils under the text.
 */
private fun Modifier.bannerFade(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val side =
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 1f - BANNER_SIDE_ALPHA),
                    BANNER_SIDE_EXTENT to Color.Black,
                )

            // A gentle curve rather than a straight ramp: a linear fade still shows where it starts.
            // Built once per size: the spread costs nothing.
            @Suppress("SpreadOperator")
            val bottom =
                Brush.verticalGradient(
                    *BANNER_BOTTOM_STOPS.map { (at, alpha) -> at to Color.Black.copy(alpha = alpha) }.toTypedArray(),
                )
            onDrawWithContent {
                drawContent()
                drawRect(side, blendMode = BlendMode.DstIn)
                drawRect(bottom, blendMode = BlendMode.DstIn)
            }
        }

private const val RECENT_TITLES = 4

/** The lines of the title, when there is no logo. */
private const val TITLE_LINES = 2

/** The lines of text under the metadata: their room is kept, even for a short one. */
private const val TAGLINE_LINES = 1
private const val BANNER_SIDE_ALPHA = 0.92f
private const val BANNER_SIDE_EXTENT = 0.6f

/** Opacity of the picture from the top of the banner to its bottom edge, where nothing is left. */
private val BANNER_BOTTOM_STOPS =
    listOf(
        0f to 1f,
        0.4f to 1f,
        0.55f to 0.9f,
        0.7f to 0.6f,
        0.85f to 0.25f,
        0.95f to 0.05f,
        1f to 0f,
    )
