package com.movix.tv.data.sources

import com.movix.tv.data.movix.PlayRequest
import com.movix.tv.data.movix.PlaybackSource
import com.movix.tv.data.tmdb.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import java.net.URI

/**
 * Independently authored compatibility *port*, inspired by the observable
 * ONYX Provider + ProgressiveServersProvider roles, not copied from its APK.
 *
 * No ONYX endpoint, binary, source code, authentication or proprietary parser
 * is contained in this file. Only install implementations with an explicit,
 * verified distribution/usage permission for both metadata and video links.
 */
interface ProgressiveCatalogPort {
    val id: String

    suspend fun home(page: Int = 1): List<RemoteCatalogueItem>
    suspend fun search(query: String, page: Int = 1): List<RemoteCatalogueItem>
    suspend fun episodesBySeason(seriesTmdbId: Int, season: Int): List<RemoteEpisode>

    /** Emitted links can be delivered incrementally; they are not fabricated. */
    fun servers(request: PlayRequest, context: SourceRequestContext): Flow<PlaybackSource>
}

/** Stable identifier required for matching titles. Never guess IDs from names. */
data class RemoteCatalogueItem(
    val tmdbId: Int,
    val type: MediaType,
    val title: String,
    val artwork: String? = null,
    val synopsis: String? = null,
)

data class RemoteEpisode(
    val seriesTmdbId: Int,
    val season: Int,
    val episode: Int,
    val title: String,
)

/**
 * Bridges an *authorized* progressive provider into the existing MYO gateway.
 * No provider is registered until it has an implementation and verified rights.
 *
 * The bridge avoids the two common cross-provider errors:
 *  - film links reused for the wrong TMDB title;
 *  - links for an episode guessed from a season-only request.
 */
class ProgressiveCatalogSourceAdapter(
    private val port: ProgressiveCatalogPort,
    val permissionReference: String,
) : SourceProvider {
    init {
        require(port.id.isNotBlank()) { "Provider ID required" }
        require(permissionReference.isNotBlank()) { "Provenance/permission reference required" }
    }

    override val id: String = "progressive:${port.id}"

    override suspend fun find(
        request: PlayRequest,
        context: SourceRequestContext,
    ): List<PlaybackSource> {
        if (request.tmdbId <= 0) return emptyList()
        if (request.isTv && (request.season == null || request.episode == null || request.season!! < 0 || request.episode!! <= 0)) {
            return emptyList()
        }
        // A progressive source may emit multiple playable servers; cap each request
        // to avoid excessive server discovery and let SourceGateway own the timeout.
        return port.servers(request, context)
            .filter { source ->
                source.streamUrl?.let(::isHttpsMedia) == true ||
                    source.embedUrl?.let(::isHttpsMedia) == true
            }
            .take(24)
            .toList()
            .distinctBy { it.id }
    }

    private fun isHttpsMedia(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.fragment == null && (uri.port == -1 || uri.port == 443)
    } catch (_: IllegalArgumentException) {
        false
    }
}
