package fr.guim.cinedoury.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey

internal typealias TvComposeRule = AndroidComposeTestRule<*, ComponentActivity>

internal fun createTvComposeRule(): TvComposeRule = createAndroidComposeRule<ComponentActivity>()

/** A 1080p TV screen (960 × 540 dp at xhdpi), in French, the app's default language. */
internal const val TV_1080P = "fr-rFR-w960dp-h540dp-land-television-xhdpi-notouch"

/** A 4K TV screen: the same 960 × 540 dp at xxxhdpi, 3840 × 2160 pixels. */
internal const val TV_4K = "fr-rFR-w960dp-h540dp-land-television-xxxhdpi-notouch"

/**
 * Shows [content] the way a TV does: Robolectric starts in touch mode, where Compose refuses the
 * focus to clickable elements, whereas a TV without a touch screen is in keyboard mode. A key
 * press on a placeholder switches the input mode before the screen is composed.
 */
@OptIn(ExperimentalTestApi::class)
internal fun TvComposeRule.setTvContent(content: @Composable () -> Unit) {
    var ready by mutableStateOf(false)
    setContent {
        if (ready) {
            content()
        } else {
            val requester = remember { FocusRequester() }
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag(PLACEHOLDER)
                    .focusRequester(requester)
                    .focusable(),
            )
            LaunchedEffect(Unit) { requester.requestFocus() }
        }
    }
    waitForIdle()
    onNodeWithTag(PLACEHOLDER).performKeyInput { pressKey(Key.ShiftLeft) }
    runOnIdle { ready = true }
    waitForIdle()
}

private const val PLACEHOLDER = "keyboard-mode-placeholder"

/**
 * « Retour » on the remote. A Back key sent by `performKeyInput` stops at Compose and never reaches
 * the activity's `OnBackPressedDispatcher`, where `BackHandler` listens: go through the dispatcher.
 */
internal fun TvComposeRule.pressBack() {
    runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
    waitForIdle()
}
