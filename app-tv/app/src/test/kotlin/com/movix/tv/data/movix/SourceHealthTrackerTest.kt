package com.movix.tv.data.movix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceHealthTrackerTest {
    @Test
    fun repeatedFailuresTemporarilyDeprioritizeHost() {
        var now = 1_000L
        val health = SourceHealthTracker { now }
        assertFalse(health.isCoolingDown("voe"))
        health.recordFailure("VOE")
        assertFalse(health.isCoolingDown("voe"))
        now += 1_000
        health.recordFailure("voe")
        assertTrue(health.isCoolingDown("Voe"))
        now += 10 * 60 * 1000L + 1L
        assertFalse(health.isCoolingDown("voe"))
        assertFalse(health.isCoolingDown(null))
    }

    @Test
    fun failuresOutsideTimeWindowDoNotQuarantineHost() {
        var now = 1_000L
        val health = SourceHealthTracker { now }
        health.recordFailure("vidzy")
        now += 5 * 60 * 1000L + 1L
        health.recordFailure("vidzy")
        assertFalse(health.isCoolingDown("vidzy"))
    }

    @Test
    fun healthyHostPreferredWithoutLosingFrenchAudioPriority() {
        val vfBad = PlaybackSource(Provider.FSTREAM, "vf-bad", Lang.VF, host = "voe", streamUrl = "https://example.org/a.m3u8")
        val vfGood = PlaybackSource(Provider.J1F, "vf-good", Lang.VF, host = "vidzy", streamUrl = "https://example.org/b.m3u8")
        val vostfr = PlaybackSource(Provider.FSTREAM, "vost", Lang.VOSTFR, host = "vidzy", streamUrl = "https://example.org/c.m3u8")
        val ranked = listOf(vfBad, vfGood, vostfr)
        assertEquals(listOf(vfGood, vfBad, vostfr), SourceSelector.preferHealthy(ranked) { it == "voe" })
        assertEquals(ranked, SourceSelector.preferHealthy(ranked) { false })
    }
}
