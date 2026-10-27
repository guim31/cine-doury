package fr.guim.cinedoury.feature.settings.page

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One line of a page of settings (`FONCTIONNALITES.md` § 12): each option says what it does and its
 * default. [key] is stable: tests and screenshots find the line by it.
 */
@Immutable
sealed interface SettingItem {
    val key: String

    /** A group of lines: « Ce profil : Léa », « Cette télévision ». */
    data class Section(
        override val key: String,
        val title: String,
        val note: String? = null,
    ) : SettingItem

    /** On or off: OK switches it. */
    data class Toggle(
        override val key: String,
        val title: String,
        val description: String,
        val checked: Boolean,
        val onToggle: () -> Unit,
        val enabled: Boolean = true,
    ) : SettingItem

    /** One value among [options]: OK opens them in a panel, the chosen one checked. */
    data class Choice(
        override val key: String,
        val title: String,
        val description: String,
        val options: List<String>,
        val selected: Int,
        val onSelect: (Int) -> Unit,
        val enabled: Boolean = true,
    ) : SettingItem

    /** A value ← and → move by steps (the size of the subtitles, the audio offset). */
    data class Stepper(
        override val key: String,
        val title: String,
        val description: String,
        val value: String,
        val canDecrease: Boolean,
        val canIncrease: Boolean,
        val onStep: (forward: Boolean) -> Unit,
    ) : SettingItem

    /** Another screen, or an action: OK does it. */
    data class Action(
        override val key: String,
        val title: String,
        val description: String? = null,
        val value: String? = null,
        val icon: ImageVector? = null,
        val destructive: Boolean = false,
        /** The title on as many lines as it needs (a line of a report, a failure). */
        val wrap: Boolean = false,
        val onClick: () -> Unit,
    ) : SettingItem
}
