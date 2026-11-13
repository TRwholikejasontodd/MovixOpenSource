package com.movix.tv.ui.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression protection for first-launch content without an account or TMDB key. */
class DemoCatalogTest {
    @Test fun demoCatalogueProvidesDistinctPlayableFilmsWithAttribution() {
        val films = DemoCatalog.films
        assertFalse("A first launch must expose at least one real playable sample", films.isEmpty())
        assertEquals("Each film needs a stable, unique identifier", films.size, films.map { it.id }.distinct().size)
        for (film in films) {
            assertTrue("Only HTTPS media is allowed", film.videoUrl.startsWith("https://"))
            assertTrue("Attribution is compulsory for CC BY content", film.attribution.contains("Blender Foundation"))
            assertTrue("The licence must be visible", film.license.startsWith("CC BY"))
            assertTrue("A film should have a real title", film.title.isNotBlank())
        }
    }
}
