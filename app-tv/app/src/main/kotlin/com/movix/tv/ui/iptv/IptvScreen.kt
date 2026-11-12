package com.movix.tv.ui.iptv

import android.net.Uri
import android.util.Xml
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.movix.tv.ui.theme.MovixColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

private data class Channel(val name: String, val url: String, val group: String = "Autres", val logo: String? = null, val tvgId: String? = null)
private data class GuideProgram(val channelId: String, val title: String, val start: Long, val stop: Long)

@Composable
fun IptvScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playlistUrl by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf(emptyList<Channel>()) }
    var programs by remember { mutableStateOf(emptyList<GuideProgram>()) }
    var selected by remember { mutableStateOf<Channel?>(null) }
    var status by remember { mutableStateOf("Importe une playlist M3U ou connecte ton abonnement IPTV autorisé.") }
    var busy by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.clearVideoSurface()
            player.release()
        }
    }

    fun play(channel: Channel) {
        selected = channel
        player.setMediaItem(MediaItem.fromUri(channel.url))
        player.prepare()
        player.playWhenReady = true
    }

    fun loadM3u(text: String) {
        val parsed = parseM3u(text)
        channels = parsed
        selected = null
        status = if (parsed.isEmpty()) "Aucune chaîne trouvée. Vérifie la playlist." else "${parsed.size} chaînes importées."
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            runCatching {
                val body = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Impossible de lire le fichier.")
                }
                loadM3u(body)
            }.onFailure { status = "Import impossible : ${it.message ?: "erreur inconnue"}" }
            busy = false
        }
    }

    Column(modifier.fillMaxSize().background(MovixColors.Ink).padding(start = 76.dp, end = 28.dp, top = 24.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("TV EN DIRECT", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Tes playlists et abonnements autorisés, réunis au même endroit.", color = MovixColors.Dim, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Playlist M3U / M3U8", color = Color.White, fontWeight = FontWeight.SemiBold)
                InputField(playlistUrl, { playlistUrl = it }, "https://exemple.tld/playlist.m3u")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Charger l'URL", busy || playlistUrl.isBlank()) {
                        scope.launch {
                            busy = true
                            runCatching {
                                val body = withContext(Dispatchers.IO) { URL(playlistUrl.trim()).openStream().bufferedReader().use { it.readText() } }
                                loadM3u(body)
                            }.onFailure { status = "Playlist inaccessible : ${it.message ?: "erreur réseau"}" }
                            busy = false
                        }
                    }
                    ActionButton("Ouvrir un fichier", busy) { filePicker.launch(arrayOf("audio/x-mpegurl", "application/x-mpegURL", "text/plain", "*/*")) }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connexion Xtream Codes", color = Color.White, fontWeight = FontWeight.SemiBold)
                InputField(server, { server = it }, "https://serveur.example:8080")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { InputField(username, { username = it }, "Utilisateur") }
                    Box(Modifier.weight(1f)) { InputField(password, { password = it }, "Mot de passe") }
                }
                ActionButton("Charger les chaînes", busy || server.isBlank() || username.isBlank() || password.isBlank()) {
                    scope.launch {
                        busy = true
                        runCatching {
                            val base = server.trim().trimEnd('/')
                            val u = URLEncoder.encode(username, "UTF-8")
                            val p = URLEncoder.encode(password, "UTF-8")
                            val endpoint = "$base/player_api.php?username=$u&password=$p&action=get_live_streams"
                            val body = withContext(Dispatchers.IO) { URL(endpoint).openStream().bufferedReader().use { it.readText() } }
                            val array = JSONArray(body)
                            val result = buildList {
                                for (i in 0 until array.length()) {
                                    val item = array.optJSONObject(i) ?: continue
                                    val id = item.optString("stream_id")
                                    if (id.isBlank()) continue
                                    val ext = item.optString("container_extension").ifBlank { "m3u8" }
                                    add(Channel(item.optString("name", "Chaîne $id"), "$base/live/$u/$p/$id.$ext",
                                        item.optString("category_name", "Autres"), item.optString("stream_icon").takeIf(String::isNotBlank),
                                        item.optString("epg_channel_id").takeIf(String::isNotBlank)))
                                }
                            }
                            channels = result
                            selected = null
                            status = if (result.isEmpty()) "Aucune chaîne renvoyée par le serveur." else "${result.size} chaînes chargées."
                        }.onFailure { status = "Connexion impossible : ${it.message ?: "vérifie les identifiants"}" }
                        busy = false
                    }
                }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(0.9f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("CHAÎNES · ${channels.size}", color = Color.White, fontWeight = FontWeight.Bold)
                    InputField(filter, { filter = it }, "Filtrer")
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(channels.filter { it.name.contains(filter, true) || it.group.contains(filter, true) }, key = { it.url }) { channel ->
                        val active = selected?.url == channel.url
                        Column(
                            Modifier.fillMaxWidth().background(if (active) Color(0xFF351719) else Color(0xFF17191F), RoundedCornerShape(8.dp))
                                .border(if (active) 1.dp else 0.dp, if (active) MovixColors.Red else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { play(channel) }.focusable().padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Text(channel.name, color = Color.White, fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                            Text(channel.group, color = MovixColors.Dim, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
            Column(Modifier.weight(1.35f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    AndroidView(
                        factory = { viewContext -> SurfaceView(viewContext).also { player.setVideoSurfaceView(it) } },
                        update = { player.setVideoSurfaceView(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (selected == null) Text("Sélectionne une chaîne", color = Color.White.copy(alpha = 0.72f))
                }
                Text(selected?.name ?: status, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                val currentProgram = selected?.tvgId?.let { id -> programs.firstOrNull { it.channelId == id && it.start <= System.currentTimeMillis() && it.stop > System.currentTimeMillis() } }
                if (currentProgram != null) Text("EN COURS · ${currentProgram.title}", color = MovixColors.Dim, fontSize = 12.sp, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(if (player.isPlaying) "Pause" else "Lecture", selected == null) {
                        if (player.isPlaying) player.pause() else player.play()
                    }
                    ActionButton("Charger EPG XMLTV", busy || epgUrl.isBlank()) {
                        scope.launch {
                            busy = true
                            runCatching {
                                val xml = withContext(Dispatchers.IO) { URL(epgUrl.trim()).openStream().use { it.readBytes() } }
                                programs = parseXmltv(xml)
                                status = "${programs.size} programmes EPG chargés."
                            }.onFailure { status = "EPG inaccessible : ${it.message ?: "erreur réseau"}" }
                            busy = false
                        }
                    }
                }
                InputField(epgUrl, { epgUrl = it }, "URL du guide XMLTV")
            }
        }
        Text(status, color = MovixColors.Dim, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun InputField(value: String, onValue: (String) -> Unit, hint: String) {
    BasicTextField(
        value = value, onValueChange = onValue, singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
        modifier = Modifier.fillMaxWidth().background(Color(0xFF20232A), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 11.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, color = Color(0xFF8A8F99), fontSize = 13.sp)
                inner()
            }
        }
    )
}

@Composable
private fun ActionButton(label: String, disabled: Boolean = false, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !disabled, modifier = Modifier.height(40.dp)) {
        Text(label, fontSize = 12.sp)
    }
}

private fun parseM3u(source: String): List<Channel> {
    val result = mutableListOf<Channel>()
    var name = ""
    var group = "Autres"
    var logo: String? = null
    var tvgId: String? = null
    source.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.startsWith("#EXTINF", true)) {
            name = line.substringAfterLast(',').trim().ifBlank { "Chaîne" }
            group = Regex("group-title=\\"([^\\"]*)\\"").find(line)?.groupValues?.getOrNull(1) ?: "Autres"
            logo = Regex("tvg-logo=\\"([^\\"]*)\\"").find(line)?.groupValues?.getOrNull(1)
            tvgId = Regex("tvg-id=\\"([^\\"]*)\\"").find(line)?.groupValues?.getOrNull(1)
        } else if (line.isNotEmpty() && !line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://"))) {
            result += Channel(name.ifBlank { line.substringAfterLast('/').take(50) }, line, group, logo, tvgId)
            name = ""; group = "Autres"; logo = null; tvgId = null
        }
    }
    return result
}

private fun parseXmltv(bytes: ByteArray): List<GuideProgram> {
    val parser = Xml.newPullParser()
    parser.setInput(bytes.inputStream(), "UTF-8")
    val result = mutableListOf<GuideProgram>()
    var event = parser.eventType
    var channel = ""
    var title = ""
    var start = 0L
    var stop = 0L
    val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    while (event != XmlPullParser.END_DOCUMENT && result.size < 100_000) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "programme" -> {
                    channel = parser.getAttributeValue(null, "channel").orEmpty()
                    start = parseXmltvTime(parser.getAttributeValue(null, "start"), format)
                    stop = parseXmltvTime(parser.getAttributeValue(null, "stop"), format)
                    title = ""
                }
                "title" -> title = parser.nextText()
            }
            XmlPullParser.END_TAG -> if (parser.name == "programme" && channel.isNotBlank() && title.isNotBlank()) {
                result += GuideProgram(channel, title, start, stop)
            }
        }
        event = parser.next()
    }
    return result
}

private fun parseXmltvTime(value: String?, format: SimpleDateFormat): Long =
    value?.let { runCatching { format.parse(it.trim())?.time }.getOrNull() } ?: 0L
