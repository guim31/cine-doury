package fr.guim.cinedoury.tv

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.image.ImageSource
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.model.EpisodeInfo
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PlayerSettings
import fr.guim.cinedoury.feature.player.PlayerActions
import fr.guim.cinedoury.feature.player.PlayerScreen
import fr.guim.cinedoury.feature.player.PlayerState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * « Épisode précédent » drawn by the Android of the emulator (X, `SPEC.md` § 4.32): its triangle in its
 * grid, next to the path of the 1.0.5 drawn the same way, and the player of an episode with both
 * buttons. The pictures go to the files of the app, which `ci/emulator-icons.sh` pulls into the
 * artifact of the job: on API 30, the Android of the Shield.
 */
@RunWith(AndroidJUnit4::class)
class PlayerIconsOnDeviceTest {
    @get:Rule
    val compose = createComposeRule()

    private val out =
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "pistes-icone-son").apply { mkdirs() }

    /** The path of « Épisode précédent » up to the 1.0.5, read by Compose as it was. */
    private val before =
        ImageVector
            .Builder("SkipPreviousBefore", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M6 6h2v12H6zm3.5 6l8.5 6V6z"), fill = SolidColor(Color.Black))
            .build()

    @Test
    fun episodePreviousKeepsItsTriangleInItsGrid() {
        compose.setContent {
            Row(Modifier.background(Color.White)) {
                Image(
                    rememberVectorPainter(CineIcons.SkipPrevious),
                    null,
                    Modifier.size(ICON).testTag("after"),
                    colorFilter = black,
                )
                Image(rememberVectorPainter(before), null, Modifier.size(ICON).testTag("before"), colorFilter = black)
            }
        }
        compose.waitForIdle()
        val after = compose.onNodeWithTag("after").captureToImage().asAndroidBitmap()
        val old = compose.onNodeWithTag("before").captureToImage().asAndroidBitmap()
        save(after, "icone-episode-precedent-api${Build.VERSION.SDK_INT}.png")
        save(old, "icone-episode-precedent-avant-api${Build.VERSION.SDK_INT}.png")
        // The triangle runs from (9.5, 12) to (18, 6) and (18, 18): its middle, (15, 12), is drawn.
        assertTrue(inked(after, 15f, 12f), "the triangle of « Épisode précédent » is not where it belongs")
        assertTrue(inked(after, 7f, 12f), "the bar of « Épisode précédent » is missing")
        // What the path of the 1.0.5 gives on this Android: logged, for the record.
        Log.i(
            TAG,
            "API ${Build.VERSION.SDK_INT}: path of the 1.0.5 has its triangle in the grid: ${inked(old, 15f, 12f)}",
        )
    }

    @Test
    fun thePlayerOfAnEpisodeShowsBothButtons() {
        val episodes = (1..3).map(::episode)
        compose.setContent {
            CineTheme {
                PlayerScreen(
                    state =
                        PlayerState(
                            item = episodes[1],
                            loading = false,
                            position = 12.minutes,
                            playing = true,
                            previous = episodes[0],
                            next = episodes[2],
                        ),
                    actions = NoActions,
                    sources = { ImageSource { _, _ -> null } },
                    onBack = {},
                    previewOsd = true,
                )
            }
        }
        compose.waitForIdle()
        save(compose.onRoot().captureToImage().asAndroidBitmap(), "lecteur-episode-api${Build.VERSION.SDK_INT}.png")
    }

    private fun episode(number: Int) =
        MediaItem(
            id = ItemId("episode-$number"),
            kind = ItemKind.Episode,
            title = listOf("Grande marée", "Mortes-eaux", "Le coefficient")[number - 1],
            runtime = 42.minutes,
            episode = EpisodeInfo(ItemId("marees"), "Les Marées", ItemId("marees-s1"), 1, number),
        )

    /** The pixel at ([x], [y]) of the 24 × 24 grid is drawn (dark on white). */
    private fun inked(
        bitmap: Bitmap,
        x: Float,
        y: Float,
    ): Boolean {
        val pixel = bitmap.getPixel((x / GRID * bitmap.width).toInt(), (y / GRID * bitmap.height).toInt())
        return android.graphics.Color.red(pixel) < DARK
    }

    private fun save(
        bitmap: Bitmap,
        name: String,
    ) = File(out, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, QUALITY, it) }

    private object NoActions : PlayerActions {
        override fun interacted() = Unit

        override fun togglePlay() = Unit

        override fun play() = Unit

        override fun pause() = Unit

        override fun jump(forward: Boolean) = Unit

        override fun seekTo(position: Duration) = Unit

        override fun scrub(
            forward: Boolean,
            repeat: Int,
        ) = Unit

        override fun commitScrub() = Unit

        override fun cancelScrub() = Unit

        override fun chapter(forward: Boolean) = Unit

        override fun skipSegment() = Unit

        override fun playNext() = Unit

        override fun playPrevious() = Unit

        override fun dismissNext() = Unit

        override fun keepWatching() = Unit

        override fun selectAudio(index: Int) = Unit

        override fun selectSubtitles(index: Int?) = Unit

        override fun selectQuality(maxBitrate: Int?) = Unit

        override fun updateSettings(change: (PlayerSettings) -> PlayerSettings) = Unit

        override fun toggleInfo() = Unit

        override fun retry() = Unit

        override fun trickplayUrl(tile: Int): String? = null

        override val streamHeaders: Map<String, String> = emptyMap()
    }

    private companion object {
        const val TAG = "PlayerIcons"
        val ICON: Dp = 240.dp
        const val GRID = 24f
        const val DARK = 128
        const val QUALITY = 100
        val black = ColorFilter.tint(Color.Black)
    }
}
