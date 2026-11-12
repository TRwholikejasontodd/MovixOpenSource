package com.movix.tv.ui.classics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.movix.tv.R
import com.movix.tv.data.movix.Lang
import com.movix.tv.data.movix.PlayRequest
import com.movix.tv.data.movix.PlaybackSource
import com.movix.tv.data.movix.Provider
import com.movix.tv.data.movix.StreamKind
import com.movix.tv.data.tmdb.MediaType
import com.movix.tv.ui.player.PlayTarget
import com.movix.tv.ui.theme.MovixColors
import com.movix.tv.ui.theme.MovixType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private data class PublicFilm(val id: String, val title: String, val year: String, val streamUrl: String)

private object PublicDomainCatalog {
    private val http = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): List<PublicFilm> = withContext(Dispatchers.IO) {
        val query = "mediatype:movies AND licenseurl:(*publicdomain*)"
        val searchUrl = "https://archive.org/advancedsearch.php?q=" +
            URLEncoder.encode(query, "UTF-8") +
            "&fl[]=identifier&fl[]=title&fl[]=year&fl[]=licenseurl&rows=20&page=1&output=json"
        val response = http.newCall(Request.Builder().url(searchUrl).header("User-Agent", "MovixTV/0.2 (public-domain-catalog)").build()).execute()
        val body = response.use { if (!it.isSuccessful) error("HTTP ${it.code}") else it.body?.string().orEmpty() }
        val docs = json.parseToJsonElement(body).jsonObject["response"]!!.jsonObject["docs"]!!.jsonArray
        coroutineScope { docs.map { doc -> async(Dispatchers.IO) {
            val fields = doc.jsonObject
            val id = fields["identifier"]?.jsonPrimitive?.content ?: return@async null
            val title = fields["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return@async null
            val year = fields["year"]?.jsonPrimitive?.contentOrNull.orEmpty()
            runCatching { resolve(id, title, year) }.getOrNull()
        } }.awaitAll().filterNotNull() }
    }

    private fun resolve(id: String, title: String, year: String): PublicFilm? {
        val url = "https://archive.org/metadata/${URLEncoder.encode(id, "UTF-8")}"
        val response = http.newCall(Request.Builder().url(url).header("User-Agent", "MovixTV/0.2").build()).execute()
        val root = response.use {
            if (!it.isSuccessful) return null
            json.parseToJsonElement(it.body?.string().orEmpty()).jsonObject
        }
        val metadata = root["metadata"]?.jsonObject ?: return null
        val license = metadata["licenseurl"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (!license.contains("publicdomain", ignoreCase = true) &&
            !license.contains("creativecommons.org/publicdomain", ignoreCase = true)) return null
        val files = root["files"]?.jsonArray ?: return null
        val file = files.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            .filter { f ->
                val name = f["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val format = f["format"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val size = f["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                val lower = name.lowercase()
                size > 1_000_000 && (lower.endsWith(".mp4") || lower.endsWith(".m4v")) &&
                    (format.contains("MPEG4", true) || format.contains("MPEG-4", true) || format.isBlank())
            }
            .maxByOrNull { it["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L } ?: return null
        val name = file["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val encodedName = name.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        return PublicFilm(id, title, year, "https://archive.org/download/$id/$encodedName")
    }
}

@Composable
fun ClassicsScreen(onPlay: (PlayTarget) -> Unit, modifier: Modifier = Modifier) {
    var films by remember { mutableStateOf<List<PublicFilm>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        loading = true
        val result = runCatching { PublicDomainCatalog.load() }
        films = result.getOrDefault(emptyList())
        error = result.isFailure
        loading = false
    }
    Column(modifier.fillMaxSize().background(MovixColors.Ink).padding(start = 84.dp, top = 42.dp, end = 42.dp)) {
        Text(stringResource(R.string.classics_title), style = MovixType.Title)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.classics_description), style = MovixType.Body)
        Spacer(Modifier.height(20.dp))
        when {
            loading -> Text(stringResource(R.string.classics_loading), style = MovixType.Body)
            error -> Text(stringResource(R.string.classics_error), style = MovixType.Body)
            films.isEmpty() -> Text(stringResource(R.string.classics_empty), style = MovixType.Body)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(films, key = { it.id }) { film ->
                    var focused by remember { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().background(
                            if (focused) MovixColors.Text.copy(alpha = 0.14f) else MovixColors.Text.copy(alpha = 0.05f),
                            RoundedCornerShape(9.dp),
                        ).semantics {
                            role = Role.Button
                            contentDescription = "${film.title}, ${film.year}, domaine public"
                        }.clickable {
                            val source = PlaybackSource(
                                provider = Provider.LINKS,
                                label = "Internet Archive",
                                language = Lang.UNKNOWN,
                                streamUrl = film.streamUrl,
                                kind = StreamKind.FILE,
                            )
                            onPlay(PlayTarget(
                                request = PlayRequest(MediaType.MOVIE, 0),
                                title = film.title,
                                backdropPath = null,
                                directSource = source,
                            ))
                        }.onFocusChanged { focused = it.isFocused }.focusable().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(film.title, style = MovixType.Panel)
                            Text(
                                listOf(film.year.takeIf { it.isNotBlank() }, "Domaine public · Internet Archive").filterNotNull().joinToString("  •  "),
                                style = MovixType.Meta,
                            )
                        }
                        Text(stringResource(R.string.action_play), style = MovixType.Button)
                    }
                }
            }
        }
    }
}
