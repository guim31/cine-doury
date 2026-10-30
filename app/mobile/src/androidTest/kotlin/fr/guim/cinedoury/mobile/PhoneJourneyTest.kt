package fr.guim.cinedoury.mobile

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.guim.cinedoury.feature.auth.mobile.MobileAuthTags
import fr.guim.cinedoury.feature.details.mobile.MobileDetailsTags
import fr.guim.cinedoury.feature.home.mobile.MobileHomeTags
import fr.guim.cinedoury.feature.player.mobile.MobilePlayerTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * A real journey on the phone emulator of the full CI (M1, `SPEC.md` § 4.26), against the Jellyfin
 * of the CI (`ci/jellyfin`, reached at 10.0.2.2): the first launch, the address typed with the
 * keyboard of the phone, the sign-in, the home, a details screen, « Lire », a few seconds of
 * playback, then « Retour » back to the details. Run by `ci/emulator-phone.sh`, after `pm clear`.
 */
@RunWith(AndroidJUnit4::class)
class PhoneJourneyTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val arguments = InstrumentationRegistry.getArguments()
    private val url = arguments.getString("jellyfinUrl") ?: "http://10.0.2.2:8096"
    private val username = arguments.getString("jellyfinUser") ?: "admin"
    private val password = arguments.getString("jellyfinPassword") ?: "cinedoury-test"

    private fun tagged(tag: String) = hasTestTag(tag)

    private fun tagStartsWith(prefix: String) =
        SemanticsMatcher("tag starts with $prefix") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) ==
                true
        }

    /** Waits for [matcher], with the semantic tree in the failure when it never comes. */
    private fun waitFor(
        matcher: SemanticsMatcher,
        timeout: Long = TIMEOUT,
    ): SemanticsNodeInteraction {
        try {
            compose.waitUntil(
                timeout,
            ) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError(
                "Never shown: ${matcher.description}\n${compose.onRoot(useUnmergedTree = true).printToString()}",
                error,
            )
        }
        return compose.onAllNodes(matcher, useUnmergedTree = true)[0]
    }

    private fun tap(tag: String) {
        waitFor(tagged(tag)).performClick()
        compose.waitForIdle()
    }

    @Test
    fun signInHomeDetailsPlayAndBack() {
        tap(MobileAuthTags.START)
        tap(MobileAuthTags.ENTER_ADDRESS)
        waitFor(tagged(MobileAuthTags.ADDRESS)).performTextInput(url)
        tap(MobileAuthTags.ADDRESS_SUBMIT)

        // « Autre compte », then the password: the same path whatever the server lists.
        tap(MobileAuthTags.OTHER_ACCOUNT)
        tap(MobileAuthTags.USE_PASSWORD)
        waitFor(tagged(MobileAuthTags.USERNAME)).performTextInput(username)
        waitFor(tagged(MobileAuthTags.PASSWORD)).performTextInput(password)
        tap(MobileAuthTags.SIGN_IN)

        // The home, then the first card of a row: its details.
        waitFor(tagged(MobileHomeTags.SCREEN), LONG_TIMEOUT)
        waitFor(tagStartsWith("card-"), LONG_TIMEOUT).performClick()
        tap(MobileDetailsTags.PLAY)

        // A few seconds of playback: the position moves, no error.
        waitFor(tagged(MobilePlayerTags.SCREEN))
        val first = position()
        Thread.sleep(PLAY_MILLIS)
        val second = position()
        assertTrue(second > first, "the position did not move: $first, then $second")
        assertTrue(
            compose.onAllNodes(tagged(MobilePlayerTags.ERROR), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
            "the player shows an error",
        )

        // « Retour » closes the controls, then the player: back to the details.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitFor(tagged(MobileDetailsTags.PLAY))
    }

    /** The position the controls show, in seconds: a tap on the picture shows them if they are hidden. */
    private fun position(): Long {
        if (compose
                .onAllNodes(
                    tagged(MobilePlayerTags.POSITION),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
                .isEmpty()
        ) {
            compose.onNode(tagged(MobilePlayerTags.SCREEN)).performClick()
        }
        val node = waitFor(tagged(MobilePlayerTags.POSITION)).fetchSemanticsNode()
        val text =
            node.config
                .getOrNull(SemanticsProperties.Text)
                ?.joinToString { it.text }
                .orEmpty()
        return text.substringBefore(" /").split(':').fold(0L) { total, part -> total * SIXTY + part.trim().toLong() }
    }

    private companion object {
        const val TIMEOUT = 20_000L
        const val LONG_TIMEOUT = 60_000L
        const val PLAY_MILLIS = 6_000L
        const val SIXTY = 60
    }
}
