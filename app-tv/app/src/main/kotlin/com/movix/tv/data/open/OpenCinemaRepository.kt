package com.movix.tv.data.open

import com.movix.tv.data.movix.StreamKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** A film released by its producer under Creative Commons Attribution. */
data class OpenFilmEntry(
    val id: String,
    val title: String,
    val year: Int,
    val synopsis: String,
    val attribution: String,
    val license: String,
)

/** A file verified in the Internet Archive metadata API, not a fabricated streaming URL. */
data class OpenFilm(
    val entry: OpenFilmEntry,
    val mediaUrl: String,
    val artworkUrl: String,
) {
    val title: String get() = entry.title
    val attribution: String get() = entry.attribution
    val license: String get() = entry.license
}

/**
 * Keyless, independently licensed catalogue.
 *
 * Only four films with independently verified Blender Foundation CC-BY licensing
 * are whitelisted. The official Internet Archive metadata endpoint is queried
 * before exposing any media file; names/URLs cannot be guessed.
 * Archive playback requires Internet access and is subject to host availability.
 */
class OpenCinemaRepository(
    private val client: OkHttpClient,
    private val json: Json,
    private val metadataBase: HttpUrl = "https://archive.org/metadata/".toHttpUrl(),
    private val downloadBase: HttpUrl = "https://archive.org/download/".toHttpUrl(),
) {
    @Volatile private var cachedAt: Long = 0L
    @Volatile private var cached: List<OpenFilm> = emptyList()

    suspend fun films(force: Boolean = false): List<OpenFilm> {
        val now = System.currentTimeMillis()
        if (!force && now - cachedAt < 30 * 60_000L && cached.isNotEmpty()) return cached
        val found = supervisorScope {
            catalogue.map { entry ->
                async {
                    withTimeoutOrNull(12_000L) { runCatching { readFilm(entry) }.getOrNull() }
                }
            }.awaitAll().filterNotNull()
        }
        if (found.isNotEmpty()) {
            cached = found
            cachedAt = System.currentTimeMillis()
        }
        return found.ifEmpty { cached }
    }

    private suspend fun readFilm(entry: OpenFilmEntry): OpenFilm? = withContext(Dispatchers.IO) {
        val metadataUrl = metadataBase.newBuilder()
            .addPathSegment(entry.id).build()
        val raw = client.newCall(Request.Builder().url(metadataUrl).build()).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            response.body.string()
        }
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return@withContext null
        if (root["is_dark"]?.jsonPrimitive?.contentOrNull == "true") return@withContext null
        val files = root["files"] as? JsonArray ?: return@withContext null
        val name = choosePlayableName(files) ?: return@withContext null
        val mediaUrl = downloadBase.newBuilder()
            .addPathSegment(entry.id)
            .addPathSegment(name)
            .build()
            .toString()
        OpenFilm(
            entry = entry,
            mediaUrl = mediaUrl,
            artworkUrl = "https://archive.org/services/img/".toHttpUrl().newBuilder().addPathSegment(entry.id).build().toString(),
        )
    }

    companion object {
        /** Source licensing: Blender Foundation's original open-movie project pages. */
        val catalogue: List<OpenFilmEntry> = listOf(
            OpenFilmEntry(
                id = "BigBuckBunny_124",
                title = "Big Buck Bunny",
                year = 2008,
                synopsis = "Un lapin géant paisible affronte trois animaux turbulents. Animation et humour.",
                attribution = "© 2008 Blender Foundation / www.bigbuckbunny.org — générique original conservé",
                license = "CC BY 3.0",
            ),
            OpenFilmEntry(
                id = "Sintel",
                title = "Sintel",
                year = 2010,
                synopsis = "Une jeune aventurière recherche son ami dragon dans un monde fantastique.",
                attribution = "© 2010 Blender Foundation / durian.blender.org — générique original conservé",
                license = "CC BY 3.0",
            ),
            OpenFilmEntry(
                id = "Tears-of-Steel",
                title = "Tears of Steel",
                year = 2012,
                synopsis = "Dans Amsterdam, des humains tentent de résoudre un conflit avec des robots.",
                attribution = "© 2012 Blender Foundation / mango.blender.org — générique original conservé",
                license = "CC BY 3.0",
            ),
            OpenFilmEntry(
                id = "ElephantsDream",
                title = "Elephants Dream",
                year = 2006,
                synopsis = "Deux personnages explorent un monde mécanique aussi étrange que mystérieux.",
                attribution = "© 2006 Blender Foundation / orange.blender.org — générique original conservé",
                license = "CC BY 2.5",
            ),
        )

        /**
         * Extract an existing, substantial MP4 stream from actual Archive files.
         * Exclude trailers, samples, thumbnails, tiny files and implausible URLs.
         */
        fun choosePlayableName(files: JsonArray): String? =
            files.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val name = (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val size = (obj["size"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
                val lower = name.lowercase()
                if (!lower.endsWith(".mp4") || size < 15_000_000L || size > 2_000_000_000L) return@mapNotNull null
                if (listOf("preview", "trailer", "sample", "thumb", "clip", "teaser", "segment").any { it in lower }) return@mapNotNull null
                name to size
            }.sortedWith(compareBy<Pair<String, Long>> {
                when {
                    "720" in it.first -> 0
                    "480" in it.first -> 1
                    "1080" in it.first -> 2
                    else -> 3
                }
            }.thenBy { it.second }).firstOrNull()?.first
    }
}
