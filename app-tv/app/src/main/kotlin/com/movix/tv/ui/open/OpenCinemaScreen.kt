package com.movix.tv.ui.open

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.movix.tv.container
import com.movix.tv.data.open.OpenFilm
import com.movix.tv.ui.components.PillButton
import com.movix.tv.ui.theme.MovixColors
import com.movix.tv.ui.theme.MovixType
import kotlinx.coroutines.delay

private val Violet = Color(0xFFA77AF7)
private val Surface = Color(0xFF1C1730)

/** Independent real catalogue. Offered when TMDB is unavailable and in 'Films libres'. */
@Composable
fun OpenCinemaScreen(
    onPlay: (OpenFilm) -> Unit,
    modifier: Modifier = Modifier,
    searchMode: Boolean = false,
) {
    val repository = LocalContext.current.container.openCinema
    var films by remember { mutableStateOf<List<OpenFilm>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<OpenFilm?>(null) }
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(repository, refresh) {
        loading = true
        films = repository.films(force = refresh > 0)
        selected = films.firstOrNull()
        loading = false
    }
    val visible = remember(films, query) {
        films.filter { it.title.contains(query.trim(), ignoreCase = true) }
    }
    Box(
        modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1D1034), Color(0xFF0B0813), MovixColors.Ink))),
    ) {
        Column(Modifier.fillMaxSize().padding(start = 84.dp, end = 32.dp, top = 36.dp)) {
            Text("MYO  /  CINÉMA LIBRE", style = MovixType.Kicker.copy(color = Violet, fontSize = 13.sp))
            Spacer(Modifier.height(12.dp))
            Text(
                if (searchMode) "Recherche dans le cinéma libre" else "Le cinéma, sans configuration",
                style = MovixType.Hero.copy(fontSize = 32.sp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Films complets sous licences Creative Commons, publiés par la Blender Foundation. " +
                    "Le catalogue utilise les fichiers réellement déclarés par Internet Archive.",
                style = MovixType.Body.copy(fontSize = 15.sp, color = MovixColors.Soft),
                maxLines = 2,
            )
            if (searchMode) {
                Spacer(Modifier.height(14.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it.take(60) },
                    singleLine = true,
                    textStyle = MovixType.Panel.copy(color = MovixColors.Text),
                    modifier = Modifier.width(440.dp)
                        .background(Surface, RoundedCornerShape(9.dp))
                        .border(1.dp, Violet.copy(alpha = 0.6f), RoundedCornerShape(9.dp))
                        .padding(12.dp),
                    decorationBox = { field ->
                        Box {
                            if (query.isBlank()) Text("Rechercher un film libre…", style = MovixType.Body)
                            field()
                        }
                    },
                )
            }
            Spacer(Modifier.height(28.dp))
            when {
                loading -> Text("Recherche des fichiers vidéo disponibles…", style = MovixType.Panel)
                films.isEmpty() -> {
                    Text("Le service de films libres est temporairement inaccessible.", style = MovixType.Panel)
                    Spacer(Modifier.height(16.dp))
                    PillButton("Réessayer", onClick = { refresh++ }, modifier = Modifier.focusRequester(firstRequester))
                }
                visible.isEmpty() -> Text("Aucun film libre ne correspond à cette recherche.", style = MovixType.Panel)
                else -> {
                    Text("Films disponibles · ${visible.size}", style = MovixType.Row.copy(fontSize = 18.sp))
                    Spacer(Modifier.height(14.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        items(visible, key = { it.entry.id }) { film ->
                            Column(
                                Modifier.width(214.dp)
                                    .background(Surface, RoundedCornerShape(12.dp))
                                    .border(1.dp, Violet.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                    .padding(11.dp),
                            ) {
                                AsyncImage(
                                    model = film.artworkUrl,
                                    contentDescription = "Affiche de ${film.title}",
                                    modifier = Modifier.fillMaxWidth().height(138.dp),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(film.title, style = MovixType.Panel.copy(fontSize = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${film.entry.year} · ${film.license}", style = MovixType.Meta.copy(color = Violet))
                                Spacer(Modifier.height(10.dp))
                                PillButton(
                                    "Voir le film",
                                    onClick = { onPlay(film) },
                                    modifier = if (film == visible.first()) Modifier.focusRequester(firstRequester) else Modifier,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(26.dp))
                    val film = selected ?: visible.first()
                    Text(film.entry.synopsis, style = MovixType.Body.copy(fontSize = 15.sp), maxLines = 3)
                    Spacer(Modifier.height(8.dp))
                    Text(film.attribution, style = MovixType.Meta.copy(fontSize = 11.sp), maxLines = 2)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Sélection indépendante de films libres ; elle ne représente pas les sources d'ONYX. " +
                    "La disponibilité des vidéos dépend d'Internet Archive et de votre connexion.",
                style = MovixType.Meta.copy(fontSize = 11.sp),
                maxLines = 2,
            )
        }
    }
    LaunchedEffect(visible, loading) {
        if (!loading && (visible.isNotEmpty() || films.isEmpty())) {
            delay(55)
            runCatching { firstRequester.requestFocus() }
        }
    }
}
