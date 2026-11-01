package fr.guim.cinedoury.mobile

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import fr.guim.cinedoury.core.designsystem.mobile.CineMobileTheme
import fr.guim.cinedoury.core.model.Credit
import fr.guim.cinedoury.core.model.CreditType
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.feature.details.mobile.MobileDetailsScreen
import fr.guim.cinedoury.feature.details.mobile.MobileDetailsTags
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
@Config(sdk = [35], qualifiers = PHONE)
class MobileDetailsCastTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `an actor with three roles has one card on the details screen`() {
        val samples = MobileSamples
        fun actor(role: String?) = Credit(ItemId("p1"), "Inès Garnier", CreditType.Actor, role = role)
        val film =
            samples.film.item.copy(
                people =
                    listOf(
                        actor("Rôle A"),
                        Credit(ItemId("p2"), "Paul Moreau", CreditType.Actor, role = "Le gardien"),
                        actor("Rôle B"),
                        actor(null),
                        actor("Rôle C"),
                        actor("Rôle A"),
                    ),
            )
        compose.setContent {
            CineMobileTheme {
                SampleArt.Provide {
                    MobileDetailsScreen(samples.film.copy(item = film), SampleArt.sources, NoDetails, NoNavigation, {})
                }
            }
        }
        compose
            .onNodeWithTag(MobileDetailsTags.SCREEN)
            .onChildren()
            .filterToOne(hasScrollToNodeAction())
            .performScrollToNode(hasText("Rôle A, Rôle B, Rôle C"))
        assertEquals(1, compose.onAllNodesWithText("Inès Garnier").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithText("Le gardien").fetchSemanticsNodes().size)
    }
}
