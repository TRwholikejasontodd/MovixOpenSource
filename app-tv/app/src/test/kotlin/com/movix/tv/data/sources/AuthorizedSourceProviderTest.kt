package com.movix.tv.data.sources

import com.movix.tv.data.tmdb.MediaType
import com.movix.tv.data.movix.PlayRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizedSourceProviderTest {
    private val records = listOf(
        AuthorizedSourceRecord(
            tmdbId = 101, type = "movie", url = "https://cdn.example.org/film.m3u8",
            title = "Film autorisé", license = "Accord de distribution confirmé", format = "hls",
        ),
        AuthorizedSourceRecord(
            tmdbId = 101, type = "movie", url = "http://insecure.example.org/film.m3u8",
            title = "Flux non sécurisé", license = "Autorisation",
        ),
        AuthorizedSourceRecord(
            tmdbId = 101, type = "movie", url = "https://cdn.example.org/unlicensed.m3u8",
            title = "Sans droits", license = "",
        ),
        AuthorizedSourceRecord(
            tmdbId = 202, type = "tv", season = 1, episode = 2,
            url = "https://cdn.example.org/episode.mp4", title = "Épisode autorisé", license = "Licence confirmée",
            format = "file",
        ),
    )

    @Test fun onlyExplicitlyAuthorizedHttpsStreamsAreReturned() = runTest {
        val sources = AuthorizedSourceProvider(records).find(PlayRequest(MediaType.MOVIE, 101), SourceRequestContext())
        assertEquals(1, sources.size)
        assertEquals("https://cdn.example.org/film.m3u8", sources.single().streamUrl)
    }

    @Test fun episodesMustMatchSeasonAndEpisodeExactly() = runTest {
        val source = AuthorizedSourceProvider(records)
        assertTrue(source.find(PlayRequest(MediaType.TV, 202, 1, 1), SourceRequestContext()).isEmpty())
        assertEquals(1, source.find(PlayRequest(MediaType.TV, 202, 1, 2), SourceRequestContext()).size)
    }

    @Test fun emptyManifestDoesNotCreateFakeSources() = runTest {
        assertTrue(AuthorizedSourceProvider(emptyList()).find(PlayRequest(MediaType.MOVIE, 101), SourceRequestContext()).isEmpty())
    }
}
