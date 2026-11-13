package com.movix.tv.data.sources

import com.movix.tv.data.movix.Lang
import com.movix.tv.data.movix.PlayRequest
import com.movix.tv.data.movix.PlaybackSource
import com.movix.tv.data.movix.Provider
import com.movix.tv.data.tmdb.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract tests written before installing the new provider gateway. */
class SourceGatewayTest {
    private val request = PlayRequest(MediaType.MOVIE, 42)

    private fun playback(url: String) = PlaybackSource(
        provider = Provider.LINKS,
        label = "Flux autorisé",
        language = Lang.VF,
        streamUrl = url,
    )

    private fun provider(name: String, block: suspend () -> List<PlaybackSource>) = object : SourceProvider {
        override val id = name
        override suspend fun find(request: PlayRequest, context: SourceRequestContext) = block()
    }

    @Test fun mergesResultsByProviderPriorityAndDeduplicates() = runTest {
        val first = playback("https://example.org/film.m3u8")
        val second = playback("https://example.org/second.m3u8")
        val gateway = SourceGateway(listOf(
            provider("primary") { listOf(first) },
            provider("secondary") { listOf(first, second) },
        ))
        assertEquals(listOf(first, second), gateway.load(request))
    }

    @Test fun oneProviderFailureDoesNotHideAnotherProvidersResults() = runTest {
        val expected = playback("https://example.org/ok.m3u8")
        val gateway = SourceGateway(listOf(
            provider("broken") { error("Serveur indisponible") },
            provider("working") { listOf(expected) },
        ))
        assertEquals(listOf(expected), gateway.load(request))
    }

    @Test fun emptyProvidersGiveEmptyCatalogueWithoutMandatoryCredentials() = runTest {
        val gateway = SourceGateway(listOf(provider("empty") { emptyList() }))
        assertTrue(gateway.load(request, SourceRequestContext()).isEmpty())
    }

    @Test fun cancelledRequestsStayCancelledInsteadOfTurningIntoMissingSources() = runTest {
        val gateway = SourceGateway(listOf(provider("cancelled") { throw CancellationException("stopped") }))
        try {
            gateway.load(request)
            throw AssertionError("Expected cancellation")
        } catch (expected: CancellationException) {
            // Host navigation must remain able to cancel a pending source search.
        }
    }
}
