package com.movix.tv.data.sources

import com.movix.tv.data.movix.Lang
import com.movix.tv.data.movix.PlayRequest
import com.movix.tv.data.movix.PlaybackSource
import com.movix.tv.data.movix.Provider
import com.movix.tv.data.tmdb.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressiveCatalogPortTest {
    private val requested = mutableListOf<PlayRequest>()

    private fun port(vararg sources: PlaybackSource) = object : ProgressiveCatalogPort {
        override val id = "licensed-test"
        override suspend fun home(page: Int) = emptyList<RemoteCatalogueItem>()
        override suspend fun search(query: String, page: Int) = emptyList<RemoteCatalogueItem>()
        override suspend fun episodesBySeason(seriesTmdbId: Int, season: Int) = emptyList<RemoteEpisode>()
        override fun servers(request: PlayRequest, context: SourceRequestContext): Flow<PlaybackSource> {
            requested += request
            return flowOf(*sources)
        }
    }

    private fun source(url: String) = PlaybackSource(
        provider = Provider.AUTHORIZED, label = "Serveur de test",
        language = Lang.VF, streamUrl = url,
    )

    @Test fun exactMovieIdIsForwardedWithoutTitleGuessing() = runTest {
        val a = source("https://video.example.org/a.m3u8")
        val adapter = ProgressiveCatalogSourceAdapter(port(a), "test fixture permission")
        val sources = SourceGateway(listOf(adapter)).load(PlayRequest(MediaType.MOVIE, 119))
        assertEquals(listOf(a), sources)
        assertEquals(119, requested.single().tmdbId)
    }

    @Test fun episodeMustHaveExplicitSeasonAndEpisode() = runTest {
        val adapter = ProgressiveCatalogSourceAdapter(port(source("https://example.org/episode.mp4")), "test")
        val invalid = PlayRequest(MediaType.TV, 222, season = 1, episode = null)
        assertTrue(adapter.find(invalid, SourceRequestContext()).isEmpty())
        assertTrue(requested.isEmpty())
        adapter.find(PlayRequest(MediaType.TV, 222, season = 3, episode = 5), SourceRequestContext())
        assertEquals(3, requested.single().season)
        assertEquals(5, requested.single().episode)
    }

    @Test fun neverTreatsUnknownOrInsecureUrlAsPlayable() = runTest {
        val adapter = ProgressiveCatalogSourceAdapter(
            port(source("http://video.example.org/video.mp4"), source("https://trusted.example.org/play.mp4")),
            "test",
        )
        val found = adapter.find(PlayRequest(MediaType.MOVIE, 321), SourceRequestContext())
        assertEquals(listOf("https://trusted.example.org/play.mp4"), found.mapNotNull { it.streamUrl })
    }

    @Test fun rejectsUnattributedProviderRegistration() {
        try {
            ProgressiveCatalogSourceAdapter(port(), "  ".trim())
            throw AssertionError("Permission reference must be provided")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun refusesUnknownCatalogIdWithoutRequestingServers() = runTest {
        val adapter = ProgressiveCatalogSourceAdapter(port(source("https://example.org/film.mp4")), "test")
        assertTrue(adapter.find(PlayRequest(MediaType.MOVIE, 0), SourceRequestContext()).isEmpty())
        assertTrue(requested.isEmpty())
    }
}
