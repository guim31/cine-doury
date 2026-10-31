package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.PlaybackProfiles
import fr.guim.cinedoury.core.source.fake.SampleDevices
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Jellyfin `DeviceProfile` sent to `POST /Items/{id}/PlaybackInfo` (`SPEC.md` § 4.11). */
class DeviceProfilesTest {
    @Test
    fun `the profile of a Shield in the form of Jellyfin`() {
        val profile = PlaybackProfiles.build(SampleDevices.shield).toDeviceProfile()
        val direct = profile.directPlayProfiles.single()
        assertTrue("mkv" in direct.container.split(","))
        assertTrue(
            direct.videoCodec
                .orEmpty()
                .split(",")
                .containsAll(listOf("h264", "hevc", "vp9")),
        )
        assertTrue("truehd" in direct.audioCodec.orEmpty().split(","))
        val transcoding = profile.transcodingProfiles.single()
        assertEquals(MediaStreamProtocol.HLS, transcoding.protocol)
        assertEquals("hevc,h264", transcoding.videoCodec)
        assertEquals("mp4", transcoding.container)
        val hevc = profile.codecProfiles.single { it.codec == "hevc" }
        val ranges =
            hevc.conditions
                .single { it.property == ProfileConditionValue.VIDEO_RANGE_TYPE }
                .value
                .orEmpty()
        assertTrue("DOVI" in ranges.split("|") && "HDR10" in ranges.split("|"))
        assertEquals("153", hevc.conditions.single { it.property == ProfileConditionValue.VIDEO_LEVEL }.value)
        val audio = profile.codecProfiles.single { it.type == CodecType.VIDEO_AUDIO }
        assertEquals("8", audio.conditions.single().value)
        assertTrue(profile.subtitleProfiles.any { it.format == "pgssub" && it.method == SubtitleDeliveryMethod.EMBED })
        assertTrue(profile.subtitleProfiles.any { it.format == "srt" && it.method == SubtitleDeliveryMethod.EXTERNAL })
    }

    @Test
    fun `the transcoding is bounded by the screen, and by a bitrate only with the quality Automatique`() {
        val profile = PlaybackProfiles.build(SampleDevices.chromecastSdr)

        fun conditions(automatic: Boolean) =
            profile
                .toDeviceProfile(automatic)
                .transcodingProfiles
                .single()
                .conditions
                .associate { it.property to it.value }
        val auto = conditions(automatic = true)
        assertEquals("1920", auto[ProfileConditionValue.WIDTH])
        assertEquals("1080", auto[ProfileConditionValue.HEIGHT])
        assertEquals("20000000", auto[ProfileConditionValue.VIDEO_BITRATE])
        val chosen = conditions(automatic = false)
        assertEquals("1920", chosen[ProfileConditionValue.WIDTH])
        assertEquals(null, chosen[ProfileConditionValue.VIDEO_BITRATE])
        // Direct play keeps the ceiling of the profile.
        assertEquals(profile.maxBitrate, profile.toDeviceProfile().maxStreamingBitrate)
    }
}
