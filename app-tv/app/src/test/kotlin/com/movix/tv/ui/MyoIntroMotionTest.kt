package com.movix.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MyoIntroMotionTest {
    @Test fun introCoversApprovedAudioAndFinishesNearFourSeconds() {
        // MP3 validé : 4,056 secondes. L'animation laisse une marge de 44 ms
        // avant la navigation pour éviter de couper l'impact final.
        assertEquals(4_100L, INTRO_DURATION_MS)
    }
    @Test fun interpolationIsMonotonicAndBounded() {
        var previous = -1f
        for (n in -30..130) {
            val p = myoEase(n / 100f)
            assertTrue(p in 0f..1f)
            assertTrue(p >= previous)
            previous = p
        }
        assertEquals(0f, myoSmooth(-1f), 0f)
        assertEquals(1f, myoSmooth(2f), 0f)
        assertEquals(.5f, myoSmooth(.5f), .00001f)
    }
}