package com.movix.tv.data.open

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The displayed video must exist in the provider's real metadata response. */
class OpenCinemaRepositoryTest {
    @Test fun choosesActualFullLengthMp4AndNotThumbnailOrSample() {
        val files = Json.parseToJsonElement(
            """[
                {"name":"preview.mp4","size":"125000000"},
                {"name":"movie_480p.mp4","size":"210000000"},
                {"name":"movie_720p.mp4","size":"350000000"},
                {"name":"movie_thumb.mp4","size":"200000000"},
                {"name":"tiny.mp4","size":"3000"}
            ]"""
        ).jsonArray
        assertEquals("movie_720p.mp4", OpenCinemaRepository.choosePlayableName(files))
    }

    @Test fun rejectsMetadataWithoutUsableFile() {
        val files = Json.parseToJsonElement(
            """[{"name":"movie.torrent","size":"30000"},{"name":"preview.mp4","size":"8000000"}]"""
        ).jsonArray
        assertNull(OpenCinemaRepository.choosePlayableName(files))
    }

    @Test fun curatedCatalogueContainsOnlyAttributedOpenFilms() {
        assertTrue(OpenCinemaRepository.catalogue.isNotEmpty())
        for (film in OpenCinemaRepository.catalogue) {
            assertTrue(film.id.isNotBlank())
            assertTrue(film.license.startsWith("CC BY"))
            assertTrue(film.attribution.contains("Blender"))
            assertTrue(film.title.isNotBlank())
        }
    }
}
