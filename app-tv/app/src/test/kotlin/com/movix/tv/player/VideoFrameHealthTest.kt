package com.movix.tv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFrameHealthTest {
    @Test
    fun severeDropsTriggerProgressiveRecoveryNotAnImmediateSwitch() {
        val health = VideoFrameHealth()
        val none = VideoFrameHealth.Action.NONE
        assertEquals(none, health.observe(2, 1_000))
        assertEquals(none, health.observe(20, 1_000))
        assertEquals(VideoFrameHealth.Action.LIMIT_1080P, health.observe(22, 1_000))
        assertEquals(none, health.observe(22, 1_000))
        assertEquals(VideoFrameHealth.Action.LIMIT_720P, health.observe(22, 1_000))
        assertEquals(none, health.observe(22, 1_000))
        assertEquals(VideoFrameHealth.Action.SWITCH_SOURCE, health.observe(22, 1_000))
    }

    @Test
    fun shortOrHealthyWindowsNeverTriggerRecovery() {
        val health = VideoFrameHealth()
        val none = VideoFrameHealth.Action.NONE
        repeat(8) { assertEquals(none, health.observe(100, 200)) }
        repeat(8) { assertEquals(none, health.observe(7, 1_000)) }
        assertEquals(none, health.observe(-1, 1_000))
    }

    @Test
    fun healthyWindowOrNewSourceResetsCounter() {
        val health = VideoFrameHealth()
        val none = VideoFrameHealth.Action.NONE
        assertEquals(none, health.observe(20, 1_000))
        assertEquals(none, health.observe(0, 1_000))
        assertEquals(none, health.observe(20, 1_000))
        health.reset()
        assertEquals(none, health.observe(20, 1_000))
        assertEquals(VideoFrameHealth.Action.LIMIT_1080P, health.observe(20, 1_000))
    }
}
