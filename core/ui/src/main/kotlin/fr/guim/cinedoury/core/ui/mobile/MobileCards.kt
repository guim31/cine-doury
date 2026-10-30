package fr.guim.cinedoury.core.ui.mobile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import fr.guim.cinedoury.core.designsystem.mobile.MobileCard
import fr.guim.cinedoury.core.designsystem.theme.MobileSizes
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.ui.ImageSources
import fr.guim.cinedoury.core.ui.cardSubtitle
import fr.guim.cinedoury.core.ui.cardTitle
import fr.guim.cinedoury.core.ui.landscapeImage
import fr.guim.cinedoury.core.ui.posterImage
import fr.guim.cinedoury.core.ui.rememberArt

/**
 * The card of an item on the phone (M1): its poster, or its 16:9 frame ([poster] false), the progress
 * and the « non vu » marks as on the TV (`UI.md` § 6). A tap calls [onClick]; a long press opens
 * [menu] for it (`SPEC.md` § 4.23 C).
 *
 * @param continueRow the row of the home the card is in, when it is a playback in progress (« Retirer
 * de Continuer »).
 */
@Composable
fun MobileItemCard(
    item: MediaItem,
    sources: ImageSources,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    poster: Boolean = true,
    size: DpSize = if (poster) MobileSizes.posterNormal else MobileSizes.thumbnail,
    menu: MobileQuickMenu? = null,
    continueRow: String? = null,
    fillWidth: Boolean = false,
    tag: String? = null,
) {
    MobileCard(
        title = item.cardTitle,
        subtitle = item.cardSubtitle(),
        size = size,
        onClick = onClick,
        onLongClick = menu?.let { { it.open(item, continueRow) } },
        image = rememberArt(if (poster) item.posterImage else item.landscapeImage, sources),
        progress = item.progress,
        unwatched = item.isUnwatched && (poster || item.progress == null),
        unwatchedCount = item.unwatchedEpisodes,
        fillWidth = fillWidth,
        modifier = if (tag != null) modifier.testTag(tag) else modifier,
    )
}
