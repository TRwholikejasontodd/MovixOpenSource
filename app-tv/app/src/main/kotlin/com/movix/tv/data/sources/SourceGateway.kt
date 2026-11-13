package com.movix.tv.data.sources

import com.movix.tv.data.movix.PlayRequest
import com.movix.tv.data.movix.PlaybackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A provider-neutral boundary between the player and the source-discovery engines.
 *
 * This interface is an independent MYO implementation; it does not embed or claim
 * to implement ONYX's unpublished source engine. An authorized ONYX-compatible
 * source provider can be installed without touching PlayerController or Media3.
 */
interface SourceProvider {
    val id: String

    /** Returns only sources the provider is authorized to disclose. Never invent links. */
    suspend fun find(request: PlayRequest, context: SourceRequestContext): List<PlaybackSource>
}

/** Optional credentials remain at the provider boundary, never in an APK constant. */
data class SourceRequestContext(val accessKey: String? = null)

/**
 * Tries providers concurrently and merges their answers in declared priority order.
 *
 * A failing or stalled provider does not make others fail. Cancellation of the
 * caller is propagated, so navigating away from the loading screen stops searches.
 * The existing SourceSelector remains responsible for stream quality, language,
 * codec compatibility, and playback failover.
 */
class SourceGateway(
    private val providers: List<SourceProvider>,
    private val providerTimeoutMs: Long = 40_000L,
) {
    init {
        require(providerTimeoutMs > 0) { "providerTimeoutMs must be positive" }
        require(providers.map { it.id }.distinct().size == providers.size) { "Provider IDs must be unique" }
    }

    suspend fun load(
        request: PlayRequest,
        context: SourceRequestContext = SourceRequestContext(),
    ): List<PlaybackSource> = supervisorScope {
        providers.map { provider ->
            async {
                try {
                    withTimeoutOrNull(providerTimeoutMs) {
                        provider.find(request, context)
                    }.orEmpty()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep other providers available on HTTP/JSON/authorization failures.
                    emptyList()
                }
            }
        }.awaitAll().flatten().distinctBy { it.id }
    }
}
