package com.movix.tv.data.sources

import com.movix.tv.data.movix.Lang
import com.movix.tv.data.movix.PlayRequest
import com.movix.tv.data.movix.PlaybackSource
import com.movix.tv.data.movix.Provider
import com.movix.tv.data.movix.StreamKind
import com.movix.tv.data.tmdb.MediaType
import kotlinx.serialization.Serializable
import java.net.URI

/**
 * Independent, source-provider implementation for streams the application
 * distributor is actually licensed or otherwise authorized to publish.
 *
 * This is intentionally NOT an ONYX scraper. Empty records produce no source,
 * and no titles or playable URLs are fabricated.
 */
@Serializable
data class AuthorizedSourceRecord(
    val tmdbId: Int,
    val type: String,
    val url: String,
    val title: String,
    /** Licensing/permission statement supplied and verified by the distributor. */
    val license: String,
    val season: Int? = null,
    val episode: Int? = null,
    val format: String = "hls",
    val language: String = "vf",
    val quality: String? = null,
)

class AuthorizedSourceProvider(private val records: List<AuthorizedSourceRecord>) : SourceProvider {
    override val id: String = "myo-authorized"

    override suspend fun find(
        request: PlayRequest,
        context: SourceRequestContext,
    ): List<PlaybackSource> = records.asSequence()
        .filter { it.tmdbId == request.tmdbId && it.type.equals(request.type.path, ignoreCase = true) }
        .filter { !request.isTv || (it.season == request.season && it.episode == request.episode) }
        .filter { request.isTv || (it.season == null && it.episode == null) }
        .filter { it.title.isNotBlank() && it.license.isNotBlank() && secureHttps(it.url) }
        .map { entry ->
            PlaybackSource(
                provider = Provider.AUTHORIZED,
                label = entry.title,
                language = when (entry.language.lowercase()) {
                    "vf" -> Lang.VF
                    "vostfr" -> Lang.VOSTFR
                    "vo" -> Lang.VO
                    else -> Lang.UNKNOWN
                },
                quality = entry.quality,
                streamUrl = entry.url,
                kind = if (entry.format.equals("file", ignoreCase = true)) StreamKind.FILE else StreamKind.HLS,
            )
        }
        .distinctBy { it.streamUrl }
        .toList()

    private fun secureHttps(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.fragment == null
    } catch (_: IllegalArgumentException) {
        false
    }
}
