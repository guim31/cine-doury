package fr.guim.cinedoury.core.playback

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.guim.cinedoury.core.model.PassthroughFormat
import fr.guim.cinedoury.core.model.SubtitleBackground
import fr.guim.cinedoury.core.model.SubtitlePosition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSettingsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = TestScope(UnconfinedTestDispatcher())
    private var profile: String? = "server/alice"
    private val store =
        PlayerSettingsStore(
            PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
                folder.root.resolve("player.preferences_pb")
            },
            profile = { profile },
        )

    @Test
    fun theLookOfTheSubtitlesIsKeptPerProfile() =
        scope.runTest {
            store.update {
                it.copy(
                    subtitles = it.subtitles.step(forward = true).copy(position = SubtitlePosition.High),
                )
            }
            assertEquals(1.2f, store.current().subtitles.scale)

            profile = "server/bob"
            assertEquals(1f, store.current().subtitles.scale)
            store.update { it.copy(subtitles = it.subtitles.copy(background = SubtitleBackground.Band)) }

            profile = "server/alice"
            val alice = store.current().subtitles
            assertEquals(1.2f, alice.scale)
            assertEquals(SubtitlePosition.High, alice.position)
            assertEquals(SubtitleBackground.None, alice.background)
        }

    @Test
    fun theRestBelongsToTheDevice() =
        scope.runTest {
            store.update { it.copy(passthrough = setOf(PassthroughFormat.Ac3)) }
            profile = "server/bob"
            assertEquals(setOf(PassthroughFormat.Ac3), store.current().passthrough)
        }

    @Test
    fun aProfileThatNeverSetItStartsFromTheDevice() =
        scope.runTest {
            profile = null
            store.update { it.copy(subtitles = it.subtitles.copy(scale = 1.6f)) }
            profile = "server/carol"
            assertEquals(1.6f, store.current().subtitles.scale)
        }
}
