package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.tabular
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
    val background = CineTheme.colors.background
    Crossfade(
        targetState = shown,
        animationSpec = CineMotion.fade<Float>(CineMotion.BACKDROP_FADE_MILLIS),
        modifier = modifier,
        label = "featured-backdrop",
    ) { key ->
        Box(Modifier.fillMaxSize().bannerFade(background)) {
            recent[key]?.backdrop?.invoke()
        }
    }
}

/**
 * The featured banner of the home (`FONCTIONNALITES.md` § 2): the logo of the focused title (its
 * title in type otherwise), a line of metadata, one line that sells it, and the [actions] (« Lire »,
 * « Fiche »). It follows the focus like [FeaturedBackdrop]; the [actions] stay out of the fade, so
 * that a button keeps the focus.
 */
@Composable
fun FeaturedBanner(
    featured: Featured?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val (shown, recent) = settledFeatured(featured)
    Column(
        modifier = modifier.width(CineSizes.heroTextWidth),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
    ) {
        Crossfade(
            targetState = shown,
            animationSpec = CineMotion.fade<Float>(CineMotion.BACKDROP_FADE_MILLIS),
            label = "featured-text",
        ) { key ->
            recent[key]?.let { FeaturedText(it) }
        }
        Row(
            modifier = Modifier.padding(top = CineSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

@Composable
private fun FeaturedText(featured: Featured) {
    val colors = CineTheme.colors
    val typography = CineTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
        val logo = featured.logo
        if (logo != null) {
            Box(Modifier.sizeIn(maxWidth = CineSizes.logoMax.width, maxHeight = CineSizes.logoMax.height)) { logo() }
        } else {
            Text(
                text = featured.title,
                style = typography.display,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (featured.metadata != null) {
            Text(
                text = featured.metadata,
                style = typography.caption.tabular(),
                color = colors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = featured.tagline.orEmpty(),
            style = typography.body,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The backdrop of the banner fades into [color] on the left and at the bottom (`UI.md` § 4). */
private fun Modifier.bannerFade(color: Color): Modifier =
    drawWithCache {
        val side =
            Brush.horizontalGradient(
                0f to color.copy(alpha = BANNER_SIDE_ALPHA),
                BANNER_SIDE_EXTENT to Color.Transparent,
            )
        val bottom = Brush.verticalGradient(BANNER_BOTTOM_START to Color.Transparent, 1f to color)
        onDrawWithContent {
            drawContent()
            drawRect(side)
            drawRect(bottom)
        }
    }

private const val RECENT_TITLES = 4
private const val BANNER_SIDE_ALPHA = 0.92f
private const val BANNER_SIDE_EXTENT = 0.6f
private const val BANNER_BOTTOM_START = 0.45f
