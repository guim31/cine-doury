package fr.guim.cinedoury.tv

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.tv.catalog.CatalogScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Temporary: which of native graphics or the parameterized runner loses the focus?
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_1080P)
class FocusDiagnosticNativeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun nativeGraphics() {
        compose.setTvContent { CineTheme(ThemeVariant.Dark) { CatalogScreen(ThemeVariant.Dark, {}) } }
        compose.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("theme-toggle"))).assertIsFocused()
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class FocusDiagnosticParameterizedTest(
    private val variant: ThemeVariant,
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun parameterized() {
        compose.setTvContent { CineTheme(variant) { CatalogScreen(variant, {}) } }
        compose.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("theme-toggle"))).assertIsFocused()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = listOf(arrayOf(ThemeVariant.Dark))
    }
}
