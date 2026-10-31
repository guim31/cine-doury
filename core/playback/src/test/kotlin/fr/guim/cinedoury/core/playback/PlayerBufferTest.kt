package fr.guim.cinedoury.core.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The buffer of the player (`SPEC.md` § 4.11, § 4.27 and R of § 4.29): on the TV, bounded by the heap of
 * the device; on the phone, the memory stage of the margin. And what `DefaultLoadControl` does with it at
 * its cap: loading stops, playback starts.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class PlayerBufferTest {
    @Test
    fun `the TV takes 40 percent of the heap, at least 32 MB and at most 192 MB`() {
        // The Shield: dalvik.vm.heapgrowthlimit = 192m, the heap the app grows to without largeHeap.
        val shield = PlayerBuffer.tv(heapBytes = 192L * MB)
        assertEquals((192L * MB * 40 / 100).toInt(), shield.targetBytes)
        assertTrue(shield.targetBytes < 192 * MB / 2, "less than half of the heap: ${shield.targetBytes}")
        assertEquals(PlayerBuffer(30_000, 90_000, 1_500, 4_000, shield.targetBytes), shield)
        assertEquals(256L * MB * 40 / 100, PlayerBuffer.tv(heapBytes = 256L * MB).targetBytes.toLong())
        assertEquals(32 * MB, PlayerBuffer.tv(heapBytes = 48L * MB).targetBytes)
        assertEquals(192 * MB, PlayerBuffer.tv(heapBytes = 1024L * MB).targetBytes)
    }

    @Test
    fun `the phone starts as fast, then keeps 2 minutes in memory, capped by its heap`() {
        val phone = PlayerBuffer.phone(memoryClassMegabytes = 256)
        val tv = PlayerBuffer.tv(192L * MB)
        assertEquals(tv.startMillis, phone.startMillis)
        assertEquals(tv.rebufferMillis, phone.rebufferMillis)
        assertEquals(30_000, phone.minMillis)
        assertEquals(120_000, phone.maxMillis)
        assertEquals(64 * MB, phone.targetBytes)
        assertEquals(32 * MB, PlayerBuffer.phone(memoryClassMegabytes = 64).targetBytes)
        assertEquals(128 * MB, PlayerBuffer.phone(memoryClassMegabytes = 1024).targetBytes)
    }

    /**
     * A 4K remux at 80 Mb/s on the smallest buffer (32 MB, about 3.4 s of film): the cap comes before
     * the 4 s that a start after a stall waits for. Loading stops there, and playback starts all the same.
     */
    @Test
    fun `at its cap the buffer stops loading, and playback starts or starts again`() {
        val buffer = PlayerBuffer.tv(heapBytes = 48L * MB)
        val seconds = buffer.targetBytes * BITS / REMUX_4K_BITRATE.toDouble()
        assertTrue(seconds * MILLIS < buffer.rebufferMillis, "the cap comes first: $seconds s")
        val allocator = buffer.allocator()
        val control = buffer.loadControl(allocator)
        val player = PlayerId("test")
        control.onPrepared(player)
        val params = parameters(player, bufferedMillis = 0, rebuffering = false)
        control.onTracksSelected(params, TrackGroupArray.EMPTY, arrayOfNulls<ExoTrackSelection>(0))
        val playerAllocator = control.getAllocator(player)
        assertTrue(control.shouldContinueLoading(params), "loads while under the cap")
        while (playerAllocator.totalBytesAllocated < buffer.targetBytes) playerAllocator.allocate()
        val buffered = (seconds * MILLIS).toLong()
        val full = parameters(player, bufferedMillis = buffered, rebuffering = true)
        assertFalse(control.shouldContinueLoading(full), "no more than the cap on the heap")
        assertTrue(control.shouldStartPlayback(full), "starts again after a stall, under 4 s buffered")
        assertTrue(control.shouldStartPlayback(parameters(player, buffered, rebuffering = false)))
        assertEquals(buffer.targetBytes.toLong(), allocator.totalBytesAllocated.toLong())
    }

    private fun parameters(
        player: PlayerId,
        bufferedMillis: Long,
        rebuffering: Boolean,
    ): LoadControl.Parameters {
        val item = MediaItem.fromUri(Uri.parse("http://192.0.2.10:8096/Videos/1/stream"))
        val timeline = SinglePeriodTimeline(C.TIME_UNSET, true, false, false, null, item)
        return LoadControl.Parameters(
            player,
            timeline,
            MediaPeriodId(timeline.getUidOfPeriod(0)),
            0,
            bufferedMillis * MILLIS,
            1f,
            true,
            rebuffering,
            C.TIME_UNSET,
            C.TIME_UNSET,
        )
    }

    private companion object {
        const val MB = 1024 * 1024
        const val BITS = 8
        const val MILLIS = 1_000L
        const val REMUX_4K_BITRATE = 80_000_000
    }
}
