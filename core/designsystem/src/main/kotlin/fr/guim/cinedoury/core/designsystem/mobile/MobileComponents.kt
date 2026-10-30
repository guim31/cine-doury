package fr.guim.cinedoury.core.designsystem.mobile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import fr.guim.cinedoury.core.designsystem.component.CardProgressBar
import fr.guim.cinedoury.core.designsystem.component.UnwatchedTriangle
import fr.guim.cinedoury.core.designsystem.component.artworkColors
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.MobileSizes

private val CardShape = RoundedCornerShape(MobileSizes.cardCorner)

/** The main action of a screen (`UI.md` principle 5): yellow, at least 48 dp high. */
@Composable
fun MobilePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    busy: Boolean = false,
    destructive: Boolean = false,
) {
    val colors = CineTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.heightIn(min = MobileSizes.buttonHeight),
        colors =
            if (destructive) {
                ButtonDefaults.buttonColors(containerColor = colors.dangerButton, contentColor = colors.text)
            } else {
                ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
            },
        contentPadding = PaddingValues(horizontal = CineSpacing.m),
    ) {
        ButtonContent(text, icon, busy)
    }
}

/** A secondary action: outlined, as high as the main one. */
@Composable
fun MobileSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = MobileSizes.buttonHeight),
        contentPadding = PaddingValues(horizontal = CineSpacing.m),
    ) {
        ButtonContent(text, icon, busy = false)
    }
}

/** A discreet action in a line of text (« Saisir une adresse », « Utiliser un mot de passe »). */
@Composable
fun MobileTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = MobileSizes.touchTarget)) {
        Text(text, style = CineTheme.typography.label, color = CineTheme.colors.accent)
    }
}

@Composable
private fun ButtonContent(
    text: String,
    icon: ImageVector?,
    busy: Boolean,
) {
    if (busy) {
        CircularProgressIndicator(
            modifier = Modifier.size(MobileSizes.icon),
            color = CineTheme.colors.onAccent,
            strokeWidth = CineSpacing.xs / 4,
        )
    } else if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(MobileSizes.icon))
    }
    if (busy || icon != null) Box(Modifier.width(CineSpacing.xs))
    Text(text, style = CineTheme.typography.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** A round icon with its label for the accessibility services: 48 dp of finger. */
@Composable
fun MobileIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    IconButton(onClick = onClick, modifier = modifier.size(MobileSizes.touchTarget)) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) CineTheme.colors.accent else CineTheme.colors.text,
        )
    }
}

/** A choice that toggles (a filter, a season, a genre): 32 dp drawn, 48 dp of finger. */
@Composable
fun MobileChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = CineTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, style = CineTheme.typography.caption, maxLines = 1) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(CineSpacing.s)) } },
        modifier = modifier.heightIn(min = MobileSizes.chipHeight),
        shape = CircleShape,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = colors.surface2,
                labelColor = colors.text,
                iconColor = colors.text2,
                selectedContainerColor = colors.accent,
                selectedLabelColor = colors.onAccent,
                selectedLeadingIconColor = colors.onAccent,
            ),
        border = null,
    )
}

/**
 * A card of the phone: its picture at [size] (a poster, or a 16:9 frame), its progress and « non vu »
 * mark, then its title and a second line under it (`UI.md` § 6). A tap opens; a long press opens the
 * menu of the card, in a sheet (`SPEC.md` § 4.23 C).
 *
 * @param image null: the generated artwork of the title.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MobileCard(
    title: String,
    subtitle: String?,
    size: DpSize,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    image: (@Composable () -> Unit)? = null,
    progress: Float? = null,
    unwatched: Boolean = false,
    unwatchedCount: Int? = null,
    showTitle: Boolean = true,
    fillWidth: Boolean = false,
) {
    val colors = CineTheme.colors
    val typography = CineTheme.typography
    val cards = CineTheme.cards
    Column(
        modifier =
            (if (fillWidth) modifier.fillMaxWidth() else modifier.width(size.width))
                .clip(CardShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    role = Role.Button,
                ).semantics(mergeDescendants = true) { contentDescription = title },
        verticalArrangement = Arrangement.spacedBy(CineSpacing.xs / 2),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(size.width / size.height)
                    .clip(CardShape)
                    .background(colors.surface1),
        ) {
            if (image != null) image() else MobileArtwork(title, compact = size.width < MobileSizes.thumbnail.width)
            if (unwatched && cards.unwatchedMarks) {
                UnwatchedTriangle(Modifier.align(Alignment.TopEnd), size = CineSpacing.s)
            }
            if (unwatchedCount != null && unwatchedCount > 0 && cards.unwatchedMarks) {
                MobileBadge(unwatchedCount.toString(), Modifier.align(Alignment.TopEnd).padding(CineSpacing.xs / 2))
            }
            progress?.let { CardProgressBar(it, Modifier.align(Alignment.BottomCenter)) }
        }
        if (showTitle && (image != null || subtitle != null)) {
            if (image != null) {
                Text(
                    title,
                    style = typography.caption,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            subtitle?.let {
                Text(
                    it,
                    style = typography.minimum,
                    color = colors.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A small count over a card (the episodes left to watch of a series). */
@Composable
fun MobileBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .heightIn(min = MobileSizes.badgeHeight)
                .widthIn(min = MobileSizes.badgeHeight)
                .clip(CircleShape)
                .background(CineTheme.colors.accent)
                .padding(horizontal = CineSpacing.xs / 2),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = CineTheme.typography.minimum, color = CineTheme.colors.onAccent)
    }
}

/** A card without a picture (`UI.md` § 6): the title set in type on a gradient taken from the name. */
@Composable
fun MobileArtwork(
    title: String,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
) {
    val (top, bottom) = remember(title) { artworkColors(title) }
    Box(
        modifier = modifier.fillMaxSize().background(Brush.linearGradient(listOf(top, bottom))),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = title,
            style = if (compact) CineTheme.typography.caption else CineTheme.typography.title2,
            color = CineTheme.colors.text,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(CineSpacing.xs),
        )
    }
}

/** A round picture of a profile: its image, or the initial on a disc coloured from the name. */
@Composable
fun MobileAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = MobileSizes.avatarMedium,
    image: (@Composable () -> Unit)? = null,
) {
    val (top, bottom) = remember(name) { artworkColors(name) }
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            image()
        } else {
            Text(
                text = name.trim().take(1).uppercase(),
                style =
                    if (size >=
                        MobileSizes.avatarMedium
                    ) {
                        CineTheme.typography.display
                    } else {
                        CineTheme.typography.label
                    },
                color = CineTheme.colors.text,
            )
        }
    }
}

/** The title of a row, then its cards in a line that scrolls under the finger. */
@Composable
fun MobileRowTitle(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = MobileSizes.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = CineTheme.typography.title2,
            color = CineTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

/**
 * A message in place of a screen's content (`UI.md` § 10): what happened, what to do, then the
 * buttons. Empty states and errors alike.
 */
@Composable
fun MobileMessage(
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.widthIn(max = MobileSizes.formWidth).padding(CineSpacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = CineTheme.colors.text2, modifier = Modifier.size(CineSpacing.xl))
        }
        Text(title, style = CineTheme.typography.title2, color = CineTheme.colors.text, textAlign = TextAlign.Center)
        message?.let {
            Text(it, style = CineTheme.typography.body, color = CineTheme.colors.text2, textAlign = TextAlign.Center)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
            content = { actions() },
        )
    }
}

/** A grey block at the place of what loads (`UI.md` principle 2: no spinner in front). */
@Composable
fun MobileSkeleton(
    modifier: Modifier = Modifier,
) {
    Box(modifier.clip(CardShape).background(CineTheme.colors.surface1))
}

/** A line of a list: an optional picture, a title and a line under it, a tap. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MobileListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    destructive: Boolean = false,
) {
    val colors = CineTheme.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = MobileSizes.touchTarget + CineSpacing.xs)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)
                .padding(horizontal = MobileSizes.gutter, vertical = CineSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
    ) {
        leading?.invoke()
        icon?.let { Icon(it, contentDescription = null, tint = if (destructive) colors.danger else colors.text2) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CineSpacing.xs / 4)) {
            Text(
                title,
                style = CineTheme.typography.body,
                color = if (destructive) colors.danger else colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    it,
                    style = CineTheme.typography.caption,
                    color = colors.text2,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** The height of a row of cards of [size] with its two lines of text, for its skeleton. */
fun mobileCardHeight(size: DpSize): Dp = size.height + MobileSizes.touchTarget

/** The gutter for a window [width] wide: wider on a tablet or a phone on its side. */
fun mobileGutter(width: Dp): Dp = if (width >= MobileSizes.compactWidth) MobileSizes.gutterWide else MobileSizes.gutter

/** A line of space, for the end of a list over the bar at the bottom. */
@Composable
fun MobileSpacer(height: Dp = CineSpacing.m) {
    Box(Modifier.height(height))
}
