package fr.guim.cinedoury.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.KeyboardKey
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing

/** The pages of the on-screen keyboard. */
enum class KeyboardPage {
    Letters,
    Accents,
}

/** Tags of the keyboard, for tests and screenshots. */
object KeyboardTags {
    const val SPACE = "key-space"
    const val ERASE = "key-erase"
    const val PAGE = "key-page"
    const val VOICE = "key-voice"
    const val SYSTEM = "key-system"
    const val CLEAR = "key-clear"

    fun key(text: String) = "key-$text"
}

/**
 * The characters of each page, seven per row: the alphabet in order (the remote moves through it
 * faster than through a QWERTY layout), then the accents French needs, then the digits.
 */
internal val KEYBOARD_ROWS =
    mapOf(
        KeyboardPage.Letters to listOf("abcdefg", "hijklmn", "opqrstu", "vwxyz'-", "1234567"),
        KeyboardPage.Accents to listOf("àâäçéèê", "ëîïôöùû", "üÿœæñ&.", ":!?,()+", "1234567"),
    )

/**
 * The on-screen keyboard of the search (`SPEC.md` § 4.12), made for the D-pad: a grid of 7 keys
 * per row, every key a neighbour in the four directions. Under the characters: 8, 9, 0, space and
 * erase; then the page of accents, the voice, the keyboard of the system, and « Tout effacer ».
 *
 * @param first attached to « a »: the focus of the screen when it opens.
 * @param page the page shown, for screenshots.
 */
@Composable
fun SearchKeyboard(
    onType: (String) -> Unit,
    onErase: () -> Unit,
    onClear: () -> Unit,
    onVoice: (() -> Unit)?,
    onSystemKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
    first: FocusRequester? = null,
    page: KeyboardPage = KeyboardPage.Letters,
) {
    var shown by rememberSaveable { mutableStateOf(page) }
    Column(modifier = modifier.testTag("keyboard"), verticalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
        KEYBOARD_ROWS.getValue(shown).forEachIndexed { row, characters ->
            Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
                characters.forEachIndexed { column, character ->
                    val text = character.toString()
                    CharacterKey(text, onType, if (row == 0 && column == 0) first else null)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
            "890".forEach { CharacterKey(it.toString(), onType, null) }
            KeyboardKey(
                onClick = { onType(" ") },
                icon = CineIcons.Space,
                contentDescription = stringResource(R.string.key_space),
                span = 2,
                focus = rememberCineFocus(KeyboardTags.SPACE),
                modifier = Modifier.testTag(KeyboardTags.SPACE),
            )
            KeyboardKey(
                onClick = onErase,
                icon = CineIcons.Backspace,
                contentDescription = stringResource(R.string.key_erase),
                span = 2,
                focus = rememberCineFocus(KeyboardTags.ERASE),
                modifier = Modifier.testTag(KeyboardTags.ERASE),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
            val accents = shown == KeyboardPage.Accents
            KeyboardKey(
                onClick = { shown = if (accents) KeyboardPage.Letters else KeyboardPage.Accents },
                text = stringResource(if (accents) R.string.key_letters else R.string.key_accents),
                contentDescription =
                    stringResource(if (accents) R.string.key_letters_description else R.string.key_accents_description),
                span = 2,
                focus = rememberCineFocus(KeyboardTags.PAGE),
                modifier = Modifier.testTag(KeyboardTags.PAGE),
            )
            if (onVoice != null) {
                KeyboardKey(
                    onClick = onVoice,
                    icon = CineIcons.Mic,
                    contentDescription = stringResource(R.string.action_voice),
                    focus = rememberCineFocus(KeyboardTags.VOICE),
                    modifier = Modifier.testTag(KeyboardTags.VOICE),
                )
            }
            KeyboardKey(
                onClick = onSystemKeyboard,
                icon = CineIcons.Keyboard,
                contentDescription = stringResource(R.string.action_system_keyboard),
                focus = rememberCineFocus(KeyboardTags.SYSTEM),
                modifier = Modifier.testTag(KeyboardTags.SYSTEM),
            )
            KeyboardKey(
                onClick = onClear,
                text = stringResource(R.string.key_clear),
                span = if (onVoice != null) 3 else 4,
                focus = rememberCineFocus(KeyboardTags.CLEAR),
                modifier = Modifier.testTag(KeyboardTags.CLEAR),
            )
        }
    }
}

@Composable
private fun CharacterKey(
    text: String,
    onType: (String) -> Unit,
    focusRequester: FocusRequester?,
) {
    val tag = KeyboardTags.key(text)
    KeyboardKey(
        onClick = { onType(text) },
        text = text,
        focusRequester = focusRequester,
        focus = rememberCineFocus(tag),
        modifier = Modifier.testTag(tag),
    )
}
