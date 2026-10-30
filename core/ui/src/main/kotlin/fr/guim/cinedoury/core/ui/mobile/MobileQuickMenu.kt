package fr.guim.cinedoury.core.ui.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.guim.cinedoury.core.designsystem.mobile.MobileSheet
import fr.guim.cinedoury.core.designsystem.mobile.MobileSheetLine
import fr.guim.cinedoury.core.designsystem.mobile.MobileSheetLines
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.QuickOptions
import fr.guim.cinedoury.core.ui.LocalQuickActions
import fr.guim.cinedoury.core.ui.QuickMenuNavigation
import fr.guim.cinedoury.core.ui.cardSubtitle
import fr.guim.cinedoury.core.ui.cardTitle
import fr.guim.cinedoury.core.ui.icon
import fr.guim.cinedoury.core.ui.label
import fr.guim.cinedoury.core.ui.runQuickOption

/** The menu of a card on the phone (M1): which card a long press opened it for. One per screen. */
@Stable
class MobileQuickMenu {
    var item: MediaItem? by mutableStateOf(null)
        private set
    internal var continueRow: String? = null
        private set

    fun open(
        item: MediaItem,
        continueRow: String? = null,
    ) {
        this.continueRow = continueRow
        this.item = item
    }

    fun close() {
        item = null
    }
}

@Composable
fun rememberMobileQuickMenu(): MobileQuickMenu = remember { MobileQuickMenu() }

/**
 * The menu of the TV's cards (`SPEC.md` § 4.23 C) in a sheet from the bottom, after a long press on a
 * card (M1): the same options for the same item ([QuickOptions]), doing the same things
 * ([runQuickOption]).
 */
@Composable
fun MobileQuickMenuHost(
    menu: MobileQuickMenu,
    navigation: QuickMenuNavigation,
) {
    val shown = menu.item ?: return
    val actions = LocalQuickActions.current
    val item = actions?.current(shown) ?: shown
    val continueRow = menu.continueRow
    val options = QuickOptions.of(item, inContinue = continueRow != null, canSelect = false)
    val labels = options.associateWith { it.label() }
    MobileSheet(
        title = item.cardTitle,
        subtitle = item.cardSubtitle().takeIf { item.kind == ItemKind.Episode },
        onDismiss = menu::close,
    ) {
        MobileSheetLines(
            options.map { option ->
                MobileSheetLine(labels.getValue(option), option.icon, "quick-${option.name}") {
                    menu.close()
                    runQuickOption(option, item, navigation, actions, continueRow, onSelect = null)
                }
            },
        )
    }
}
