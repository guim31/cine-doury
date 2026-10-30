package fr.guim.cinedoury.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class QualityGovernorTest {
    private val target = 15.minutes

    private fun margin(
        ahead: Duration,
        offline: Boolean = false,
    ) = MarginStatus(Duration.ZERO, ahead, 30.seconds, target, offline = offline)

    /** Samples every second from [from] for [seconds] s, the margin given by [ahead]; the first change. */
    private fun QualityGovernor.run(
        from: Int,
        seconds: Int,
        throughput: Long? = null,
        offline: Boolean = false,
        ahead: (Int) -> Duration,
    ): QualityChange? =
        (from until from + seconds).firstNotNullOfOrNull { second ->
            sample(second.seconds, margin(ahead(second), offline), playing = true, throughput = throughput)
        }

    @Test
    fun `a margin that melts for a minute while the network answers takes the quality one step down`() {
        val governor = QualityGovernor(ceiling = 4_000_000, sourceBitrate = 12_000_000)
        val change = governor.run(0, 90) { (150 - it / 2).seconds }
        assertEquals(QualityChange(2_000_000, down = true), change)
        assertEquals(2_000_000, governor.current)
    }

    @Test
    fun `an outage is not slowness, nor is a margin still large`() {
        val outage = QualityGovernor(ceiling = 4_000_000, sourceBitrate = 12_000_000)
        assertNull(outage.run(0, 180, offline = true) { (170 - it).seconds })
        val large = QualityGovernor(ceiling = 4_000_000, sourceBitrate = 12_000_000)
        assertNull(large.run(0, 180) { (10.minutes - (it / 2).seconds) })
    }

    @Test
    fun `from the original, the first step down is under the bitrate of the file`() {
        val governor = QualityGovernor(ceiling = null, sourceBitrate = 9_000_000)
        assertEquals(
            QualityChange(8_000_000, down = true),
            governor.run(0, 90) {
                (120 - it).seconds.coerceAtLeast(Duration.ZERO)
            },
        )
    }

    @Test
    fun `back up only after two full minutes, a fast network, and never above the default`() {
        val governor = QualityGovernor(ceiling = 4_000_000, sourceBitrate = 12_000_000, current = 1_000_000)
        assertNull(governor.run(0, 100, throughput = 10_000_000) { target })
        assertNull(governor.run(100, 200, throughput = 2_500_000) { target }, "the network carries 1.25 times the step")
        val fresh = QualityGovernor(ceiling = 4_000_000, sourceBitrate = 12_000_000, current = 2_000_000)
        assertEquals(QualityChange(4_000_000, down = false), fresh.run(0, 200, throughput = 10_000_000) { target })
        assertNull(fresh.run(200, 900, throughput = 100_000_000) { target }, "at the default, no higher")
    }

    @Test
    fun `back to the original when the default is the original`() {
        val governor = QualityGovernor(ceiling = null, sourceBitrate = 9_000_000, current = 8_000_000)
        assertEquals(QualityChange(null, down = false), governor.run(0, 200, throughput = 20_000_000) { target })
    }

    @Test
    fun `the default quality follows the network of the phone`() {
        val settings = PlayerSettings()
        assertEquals(4_000_000, settings.quality(remote = false, network = NetworkKind.Mobile))
        assertEquals(null, settings.defaultQuality(remote = false, network = NetworkKind.Wifi))
        assertEquals(settings.remoteQuality, settings.defaultQuality(remote = true, network = NetworkKind.Wifi))
        assertEquals(
            null,
            settings.copy(mobileQuality = null).defaultQuality(remote = true, network = NetworkKind.Mobile),
        )
    }
}
