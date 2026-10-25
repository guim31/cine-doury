package fr.guim.cinedoury.tv.catalog

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.GeneratedArtwork
import fr.guim.cinedoury.core.designsystem.component.NavigationRail
import fr.guim.cinedoury.core.designsystem.component.PosterCard
import fr.guim.cinedoury.core.designsystem.component.PosterSize
import fr.guim.cinedoury.core.designsystem.component.PrimaryButton
import fr.guim.cinedoury.core.designsystem.component.RoundIconButton
import fr.guim.cinedoury.core.designsystem.component.ThumbnailCard
import fr.guim.cinedoury.core.designsystem.component.artworkColors
import fr.guim.cinedoury.core.designsystem.component.readabilityScrims
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.tabular
import fr.guim.cinedoury.tv.R

private val SwatchShape = RoundedCornerShape(CineSizes.cardCorner)
private const val SWATCHES_PER_ROW = 7
private const val PERCENT = 100
private const val RGB_MASK = 0xFFFFFF
private const val BACKDROP_GLOW_ALPHA = 0.35f

@Composable
private fun Section(
    @StringRes title: Int,
    @StringRes hint: Int,
    content: @Composable () -> Unit,
) {
    val colors = CineTheme.colors
    Column {
        Text(stringResource(title), style = CineTheme.typography.title2, color = colors.text)
        Text(
            text = stringResource(hint),
            style = CineTheme.typography.body,
            color = colors.text2,
            modifier = Modifier.padding(top = CineSpacing.xs, bottom = CineSpacing.rowTitleGap),
        )
        content()
    }
}

/**
 * Lets a row draw [horizontal] beyond its parent on each side, so that the scale and the outline
 * of a focused card at either end are not clipped, while its first card stays aligned.
 */
private fun Modifier.bleed(horizontal: Dp): Modifier =
    layout { measurable, constraints ->
        val extra = horizontal.roundToPx() * 2
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth
        val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = maxWidth))
        layout((placeable.width - extra).coerceAtLeast(0), placeable.height) {
            placeable.place(-extra / 2, 0)
        }
    }

private val RowBleed = CineSpacing.m

private fun hex(color: Color): String = "#%06X".format(color.toArgb() and RGB_MASK)

@Composable
internal fun ColorsSection() {
    val colors = CineTheme.colors
    val alphaFocus = (colors.focus.alpha * PERCENT).toInt()
    val alphaTrack = (colors.progressTrack.alpha * PERCENT).toInt()
    val swatches =
        listOf(
            Triple(R.string.token_background, colors.background, hex(colors.background)),
            Triple(R.string.token_surface1, colors.surface1, hex(colors.surface1)),
            Triple(R.string.token_surface2, colors.surface2, hex(colors.surface2)),
            Triple(R.string.token_surface3, colors.surface3, hex(colors.surface3)),
            Triple(R.string.token_text, colors.text, hex(colors.text)),
            Triple(R.string.token_text2, colors.text2, hex(colors.text2)),
            Triple(R.string.token_text3, colors.text3, hex(colors.text3)),
            Triple(R.string.token_accent, colors.accent, hex(colors.accent)),
            Triple(R.string.token_on_accent, colors.onAccent, hex(colors.onAccent)),
            Triple(R.string.token_accent_pressed, colors.accentPressed, hex(colors.accentPressed)),
            Triple(
                R.string.token_focus,
                colors.focus,
                stringResource(R.string.color_with_alpha, hex(colors.focus), alphaFocus),
            ),
            Triple(
                R.string.token_progress_track,
                colors.progressTrack,
                stringResource(R.string.color_with_alpha, hex(colors.progressTrack), alphaTrack),
            ),
            Triple(R.string.token_danger, colors.danger, hex(colors.danger)),
            Triple(R.string.token_success, colors.success, hex(colors.success)),
        )
    Section(R.string.section_colors, R.string.section_colors_hint) {
        Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.m)) {
            swatches.chunked(SWATCHES_PER_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
                    row.forEach { (name, color, value) -> Swatch(stringResource(name), color, value) }
                }
            }
        }
    }
}

@Composable
private fun Swatch(
    name: String,
    color: Color,
    value: String,
) {
    val colors = CineTheme.colors
    Column(Modifier.width(CineSizes.swatch.width)) {
        Box(
            Modifier
                .size(CineSizes.swatch)
                .clip(SwatchShape)
                .background(colors.background)
                .background(color)
                .border(CineSizes.hairline, colors.surface3, SwatchShape),
        )
        Text(
            name,
            style = CineTheme.typography.minimum,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = CineSpacing.xs),
        )
        Text(
            value,
            style = CineTheme.typography.minimum.tabular(),
            color = colors.text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun TypographySection() {
    val typography = CineTheme.typography
    val roles =
        listOf(
            TypeRole(R.string.type_display, typography.display, R.string.sample_display),
            TypeRole(R.string.type_title1, typography.title1, R.string.sample_title1),
            TypeRole(R.string.type_title2, typography.title2, R.string.sample_title2),
            TypeRole(R.string.type_body, typography.body, R.string.sample_body),
            TypeRole(R.string.type_caption, typography.caption.tabular(), R.string.sample_caption),
            TypeRole(R.string.type_minimum, typography.minimum, R.string.sample_minimum),
        )
    Section(R.string.section_type, R.string.section_type_hint) {
        Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.s)) {
            roles.forEach { role -> TypeRow(role) }
        }
    }
}

private data class TypeRole(
    @StringRes val name: Int,
    val style: TextStyle,
    @StringRes val sample: Int,
)

@Composable
private fun TypeRow(role: TypeRole) {
    val colors = CineTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(CineSizes.posterLarge.width)) {
            Text(stringResource(role.name), style = CineTheme.typography.caption, color = colors.text)
            Text(
                stringResource(
                    R.string.type_spec,
                    role.style.fontSize.value
                        .toInt(),
                    role.style.fontWeight?.weight ?: 0,
                ),
                style = CineTheme.typography.minimum.tabular(),
                color = colors.text3,
            )
        }
        Text(
            stringResource(role.sample),
            style = role.style,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun PostersSection() {
    var selected by remember { mutableIntStateOf(-1) }
    Section(R.string.section_posters, R.string.section_posters_hint) {
        LazyRow(
            modifier = Modifier.bleed(RowBleed),
            contentPadding = PaddingValues(horizontal = RowBleed, vertical = CineSpacing.m),
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
        ) {
            itemsIndexed(SamplePosters) { index, poster ->
                PosterCard(
                    title = poster.title,
                    subtitle = poster.year.toString(),
                    onClick = { selected = index },
                    progress = poster.progress,
                    unwatched = poster.unwatched,
                    unwatchedCount = poster.unwatchedCount,
                    modifier = Modifier.testTag("poster-$index"),
                )
            }
        }
        Text(
            stringResource(R.string.section_poster_sizes),
            style = CineTheme.typography.caption,
            color = CineTheme.colors.text2,
            modifier = Modifier.padding(top = CineSpacing.s, bottom = CineSpacing.xs),
        )
        Row(
            modifier = Modifier.bleed(RowBleed).padding(horizontal = RowBleed, vertical = CineSpacing.m),
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
            verticalAlignment = Alignment.Bottom,
        ) {
            listOf(
                PosterSize.Small to R.string.poster_size_small,
                PosterSize.Normal to R.string.poster_size_normal,
                PosterSize.Large to R.string.poster_size_large,
            ).forEach { (size, label) ->
                PosterCard(
                    title = SAMPLE_FILM,
                    subtitle = stringResource(label),
                    size = size,
                    alwaysShowTitle = true,
                    onClick = {},
                )
            }
        }
    }
}

@Composable
internal fun ThumbnailsSection() {
    Section(R.string.section_thumbnails, R.string.section_thumbnails_hint) {
        LazyRow(
            modifier = Modifier.bleed(RowBleed),
            contentPadding = PaddingValues(horizontal = RowBleed, vertical = CineSpacing.m),
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
        ) {
            item {
                ThumbnailCard(
                    title = SAMPLE_SERIES,
                    subtitle =
                        stringResource(
                            R.string.metadata_pair,
                            stringResource(R.string.thumb_episode),
                            stringResource(R.string.thumb_remaining),
                        ),
                    progress = 0.65f,
                    onClick = {},
                    modifier = Modifier.testTag("thumbnail-0"),
                )
            }
            item {
                ThumbnailCard(
                    title = SAMPLE_FILM,
                    subtitle = stringResource(R.string.thumb_resume),
                    progress = 0.24f,
                    onClick = {},
                )
            }
            item {
                ThumbnailCard(
                    title = SAMPLE_SINGLE_SEASON,
                    subtitle = stringResource(R.string.thumb_next),
                    onClick = {},
                )
            }
        }
    }
}

@Composable
internal fun ButtonsSection() {
    var favorite by remember { mutableIntStateOf(0) }
    Section(R.string.section_buttons, R.string.section_buttons_hint) {
        Row(
            modifier = Modifier.padding(vertical = CineSpacing.s),
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
            verticalAlignment = Alignment.Top,
        ) {
            PrimaryButton(
                text = stringResource(R.string.button_resume),
                icon = CineIcons.Play,
                onClick = {},
                modifier = Modifier.testTag("primary-button"),
            )
            RoundIconButton(CineIcons.Restart, stringResource(R.string.button_restart), onClick = {})
            RoundIconButton(CineIcons.Watched, stringResource(R.string.button_watched), onClick = {})
            RoundIconButton(
                icon = if (favorite % 2 == 1) CineIcons.Favorite else CineIcons.NotFavorite,
                label = stringResource(R.string.button_favorite),
                selected = favorite % 2 == 1,
                onClick = { favorite++ },
            )
            RoundIconButton(CineIcons.More, stringResource(R.string.button_more), onClick = {})
        }
        // Room for the label that appears under a focused round button.
        Spacer(Modifier.height(CineSpacing.l))
    }
}

@Composable
internal fun RailSection() {
    Section(R.string.section_rail, R.string.section_rail_hint) {
        Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.l)) {
            RailSpecimen(R.string.rail_closed, expanded = false)
            RailSpecimen(R.string.rail_open, expanded = true)
        }
    }
}

private val SpecimenSize =
    DpSize(
        width = CineSizes.railExpanded + CineSizes.posterSmall.width,
        height = CineSizes.posterLarge.height + CineSpacing.xxl,
    )

@Composable
private fun RailSpecimen(
    @StringRes label: Int,
    expanded: Boolean,
) {
    val colors = CineTheme.colors
    Column {
        Text(
            stringResource(label),
            style = CineTheme.typography.caption,
            color = colors.text2,
            modifier = Modifier.padding(bottom = CineSpacing.xs),
        )
        Box(
            Modifier
                .size(SpecimenSize)
                .clip(SwatchShape)
                .background(colors.background)
                .border(CineSizes.hairline, colors.surface3, SwatchShape),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .padding(start = CineSizes.railCollapsed + CineSpacing.s, top = CineSpacing.l),
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
            ) {
                SamplePosters.take(2).forEach { poster ->
                    Box(Modifier.size(CineSizes.posterSmall).clip(SwatchShape)) {
                        GeneratedArtwork(poster.title, compact = true)
                    }
                }
            }
            NavigationRail(
                destinations = railDestinations(),
                selectedIndex = 1,
                onSelect = {},
                expanded = expanded,
            )
        }
    }
}

@Composable
internal fun ScrimsSection() {
    val colors = CineTheme.colors
    val typography = CineTheme.typography
    val (top, bottom) = remember { artworkColors(SAMPLE_FILM) }
    Section(R.string.section_scrims, R.string.section_scrims_hint) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(CineSizes.posterLarge.height + CineSpacing.xl)
                    .clip(SwatchShape),
            contentAlignment = Alignment.BottomStart,
        ) {
            // A stand-in for a full-screen backdrop, with its veils; the text sits above both.
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.linearGradient(listOf(top, bottom)))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(colors.accent.copy(alpha = BACKDROP_GLOW_ALPHA), Color.Transparent),
                        ),
                    ).readabilityScrims(colors.background),
            )
            Column(Modifier.padding(CineSpacing.xl)) {
                Text(stringResource(R.string.sample_display), style = typography.display, color = colors.text)
                Text(
                    stringResource(R.string.sample_caption),
                    style = typography.caption.tabular(),
                    color = colors.text2,
                )
                Text(
                    stringResource(R.string.sample_body),
                    style = typography.body,
                    color = colors.text,
                    maxLines = 2,
                    modifier = Modifier.padding(top = CineSpacing.xs).width(CineSizes.railExpanded * 2),
                )
            }
        }
    }
}

@Composable
internal fun SpacingSection() {
    val colors = CineTheme.colors
    val typography = CineTheme.typography
    Section(R.string.section_spacing, R.string.section_spacing_hint) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.l),
            verticalAlignment = Alignment.Bottom,
        ) {
            CineSpacing.all.forEach { space ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(space).background(colors.accent))
                    Text(
                        stringResource(R.string.spacing_dp, space.value.toInt()),
                        style = typography.minimum.tabular(),
                        color = colors.text2,
                        modifier = Modifier.padding(top = CineSpacing.xs),
                    )
                }
            }
        }
        Spacer(Modifier.height(CineSpacing.l))
        val motions =
            listOf(
                R.string.motion_focus to stringResource(R.string.motion_focus_value),
                R.string.motion_backdrop to stringResource(R.string.motion_backdrop_value),
                R.string.motion_detail to stringResource(R.string.duration_ms, CineMotion.DETAIL_OPEN_MILLIS),
                R.string.motion_panel to stringResource(R.string.duration_ms, CineMotion.PANEL_MILLIS),
                R.string.motion_osd to stringResource(R.string.duration_ms, CineMotion.OSD_FADE_MILLIS),
            )
        Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
            motions.forEach { (label, value) ->
                Row {
                    Text(
                        stringResource(label),
                        style = typography.body,
                        color = colors.text,
                        modifier = Modifier.width(CineSizes.railExpanded + CineSizes.posterSmall.width),
                    )
                    Text(value, style = typography.body.tabular(), color = colors.text2)
                }
            }
        }
    }
}
