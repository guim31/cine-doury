package fr.guim.cinedoury.tv

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.feature.details.DetailsPreview
import fr.guim.cinedoury.feature.details.DetailsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * An actor who plays several roles: the server gives one credit per role, with the same person id,
 * and the cast row keyed by that id closed the app (« Key … was already used »). One card, the roles
 * joined.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class DetailsCastTest {
    @get:Rule
    val compose = createTvComposeRule()

    @Test
    fun `an actor with three roles has one card on the details screen`() {
        val people = J3Samples.phare.people
        val actor = people.first()
        val film =
            J3Samples.phare.copy(
                people =
                    listOf(actor.copy(role = "Rôle A"), actor.copy(role = "Rôle B")) + people.drop(1) +
                        listOf(actor.copy(role = "Rôle C"), actor.copy(role = "")),
            )
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                SampleArt.Provide {
                    DetailsScreen(
                        state = J3Samples.film.copy(item = film),
                        sources = SampleArt.sources,
                        actions = NoDetailsActions,
                        navigation = NoDetailsNavigation,
                        // The rows under the hero, composed.
                        previewFirstItem = 1,
                    )
                }
            }
        }
        compose.waitForIdle()
        val cast = hasAnyAncestor(hasTestTag("row:cast"))
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText("Rôle A, Rôle B, Rôle C") and cast).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, compose.onAllNodesWithText(actor.name).fetchSemanticsNodes().size)
        // The second card is the second person.
        compose
            .onNode(hasText(people[1].name) and hasAnyAncestor(hasTestTag(DetailsPreview.card("cast", 1))))
            .assertExists()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
