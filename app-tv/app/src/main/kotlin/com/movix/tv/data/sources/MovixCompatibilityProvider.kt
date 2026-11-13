package com.movix.tv.data.sources

import com.movix.tv.data.account.SwiftFluxClient
import com.movix.tv.data.movix.PlayRequest
import com.movix.tv.data.movix.PlaybackSource
import com.movix.tv.data.movix.SourceRepository

/**
 * Transitional adapter: keeps the existing, tested Movix source routes available.
 *
 * A future independent/authorized provider can be added to SourceGateway without
 * requiring any change in the TV player or coupling it to this API.
 */
class MovixCompatibilityProvider(
    private val repository: SourceRepository,
    private val swiftflux: SwiftFluxClient,
) : SourceProvider {
    override val id: String = "movix-compat"

    override suspend fun find(
        request: PlayRequest,
        context: SourceRequestContext,
    ): List<PlaybackSource> {
        val season = if (request.isTv) request.season ?: 1 else null
        val episode = if (request.isTv) request.episode ?: 1 else null
        val swiftfluxResolver: suspend (Int) -> Pair<String, String?>? = { index ->
            swiftflux.resolve(request.tmdbId, season, episode, index)?.let { it.url to it.quality }
        }
        return repository.load(request, context.accessKey, swiftflux = swiftfluxResolver)
    }
}
