package com.movix.tv

import com.movix.tv.data.local.AccentColor
import com.movix.tv.data.local.ProfileLibraryKeys
import com.movix.tv.data.local.TvKeys
import com.movix.tv.data.local.UiKeys
import com.movix.tv.data.local.UiPrefs
import com.movix.tv.player.AudioMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyoFeatureDefaultsTest {
    @Test fun purpleIsDefaultAccent() {
        assertEquals(AccentColor.PURPLE, UiPrefs.accent(emptyMap()))
        assertEquals(AccentColor.RED, UiPrefs.accent(mapOf(UiKeys.ACCENT to "red")))
    }

    @Test fun screensaverDefaultsToTenMinutes() {
        assertEquals(10, UiPrefs.screensaverMinutes(emptyMap()))
        assertEquals(0, UiPrefs.screensaverMinutes(mapOf(UiKeys.SCREENSAVER_MIN to "0")))
    }

    @Test fun audioIsOffByDefaultAndUnknownChoicesFailSafe() {
        assertEquals(AudioMode.OFF, AudioMode.from(null))
        assertEquals(AudioMode.OFF, AudioMode.from("unsupported"))
        assertEquals(AudioMode.NIGHT, AudioMode.from("night"))
        assertEquals(AudioMode.VOICE, AudioMode.from("voice"))
    }

    @Test fun profileLibraryDoesNotContainCredentials() {
        assertTrue(ProfileLibraryKeys.belongsToProfile("progress_42"))
        assertTrue(ProfileLibraryKeys.belongsToProfile("watchlist_movie"))
        assertTrue(ProfileLibraryKeys.belongsToProfile(UiKeys.AUDIO_MODE))
        assertTrue(ProfileLibraryKeys.belongsToProfile(TvKeys.SEARCH_RECENT))
        assertFalse(ProfileLibraryKeys.belongsToProfile(TvKeys.TMDB_KEY))
        assertFalse(ProfileLibraryKeys.belongsToProfile(TvKeys.VIP_KEY))
        assertFalse(ProfileLibraryKeys.belongsToProfile(TvKeys.ACCOUNT))
        assertFalse(ProfileLibraryKeys.belongsToProfile(TvKeys.CLIENT_ID))
    }
}
