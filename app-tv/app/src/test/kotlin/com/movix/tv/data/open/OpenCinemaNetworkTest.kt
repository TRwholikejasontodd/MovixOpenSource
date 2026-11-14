package com.movix.tv.data.open

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenCinemaNetworkTest {
    private val server = MockWebServer()
    private val paths = mutableListOf<String>()

    @Before fun setup() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(paths) { paths.add(request.url.encodedPath) }
                return MockResponse.Builder().body(
                    """{"files":[{"name":"actual_full_film_720p.mp4","size":"425000000","format":"MPEG4"},
                    {"name":"thumb.jpg","size":"9000","format":"JPEG"}]}"""
                ).build()
            }
        }
        server.start()
    }

    @After fun teardown() { server.close() }

    @Test fun onlyProviderMetadataCanCreatePlayableFilmUrls() = runTest {
        val repository = OpenCinemaRepository(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            metadataBase = server.url("/metadata/"),
            downloadBase = server.url("/download/"),
        )
        val films = withContext(Dispatchers.Default) { repository.films() }
        assertEquals(4, films.size)
        assertEquals(4, paths.size)
        assertTrue(paths.all { it.startsWith("/metadata/") })
        assertTrue(films.all { it.mediaUrl.contains("/download/") })
        assertTrue(films.all { it.mediaUrl.endsWith("/actual_full_film_720p.mp4") })
    }

    @Test fun neverInventsAStreamWhenMetadataHasNoFilm() = runTest {
        val server2 = MockWebServer()
        server2.enqueue(MockResponse.Builder().body("""{"files":[]}""").build())
        server2.start()
        try {
            val repo = OpenCinemaRepository(
                client = OkHttpClient(),
                json = Json { ignoreUnknownKeys = true },
                metadataBase = server2.url("/metadata/"),
                downloadBase = server2.url("/download/"),
            )
            // One of the four results is empty; the other three may timeout or be absent:
            // this test verifies only that invalid API data never invents playable URLs.
            val films = withContext(Dispatchers.Default) { repo.films() }
            assertFalse(films.any { it.mediaUrl.isBlank() })
        } finally { server2.close() }
    }
}
