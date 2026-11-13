package com.movix.tv.ui.demo

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.movix.tv.container
import com.movix.tv.data.movix.StreamKind
import com.movix.tv.ui.components.PillButton
import com.movix.tv.ui.theme.MovixColors
import com.movix.tv.ui.theme.MovixType
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

private val Violet = Color(0xFF9C66FF)

/** Genuine offline metadata and a minimal, remotely hosted, openly licensed playback offer. */
@Composable
fun DemoCatalogScreen(
    onPlay: (DemoFilm) -> Unit,
    onRetry: () -> Unit,
    missingKey: Boolean,
    modifier: Modifier = Modifier,
) {
    val firstFilm = remember { FocusRequester() }
    Column(
        modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF1D1234), MovixColors.Ink, MovixColors.Ink)))
            .padding(start = 96.dp, end = 48.dp, top = 54.dp, bottom = 40.dp),
    ) {
        Text("MYO  /  CINÉMA LIBRE", style = MovixType.Kicker.copy(color = Violet))
        Spacer(Modifier.height(12.dp))
        Text("Bienvenue dans MYO", style = MovixType.Title.copy(fontSize = 37.sp))
        Spacer(Modifier.height(12.dp))
        Text(
            if (missingKey)
                "Des films de démonstration sont disponibles immédiatement, sans compte ni clé TMDB."
            else
                "Le catalogue distant est momentanément indisponible. Ces films restent accessibles sans configuration.",
            style = MovixType.Body.copy(color = MovixColors.Muted),
        )
        Spacer(Modifier.height(38.dp))
        Text("À regarder maintenant", style = MovixType.Row)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            DemoCatalog.films.forEachIndexed { index, film ->
                Column(
                    Modifier.width(310.dp)
                        .background(Color(0xFF261B3E), RoundedCornerShape(15.dp))
                        .border(1.dp, Violet.copy(alpha = 0.38f), RoundedCornerShape(15.dp))
                        .padding(22.dp),
                ) {
                    Text(film.title, style = MovixType.Panel.copy(color = MovixColors.Text))
                    Spacer(Modifier.height(6.dp))
                    Text("${film.year}  ·  ${film.license}  ·  Film libre", style = MovixType.Kicker.copy(color = Violet))
                    Spacer(Modifier.height(12.dp))
                    Text(film.synopsis, style = MovixType.Body.copy(color = MovixColors.Muted), maxLines = 3)
                    Spacer(Modifier.height(18.dp))
                    PillButton(
                        "Lancer la lecture",
                        onClick = { onPlay(film) },
                        modifier = if (index == 0) Modifier.focusRequester(firstFilm) else Modifier,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        PillButton("Réessayer le catalogue en ligne", onClick = onRetry)
        Spacer(Modifier.height(20.dp))
        Text(
            "Vidéos sous licence Creative Commons, hébergées sur un serveur de démonstration. Une connexion Internet est nécessaire pour les lire.",
            style = MovixType.Body.copy(color = MovixColors.Muted, fontSize = 12.sp),
        )
    }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { firstFilm.requestFocus() }
    }
}

/** Test the same native Media3 decoding path as the regular player; no account and no source API. */
@Composable
fun DemoPlaybackScreen(film: DemoFilm, onExit: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val engine = context.container.playerEngine
    val player = remember(film.id) {
        engine.newPlayer().apply {
            setMediaSource(engine.mediaSource(film.videoUrl, StreamKind.FILE))
            prepare()
            playWhenReady = true
        }
    }
    var isPlaying by remember(film.id) { mutableStateOf(false) }
    var buffering by remember(film.id) { mutableStateOf(true) }
    var error by remember(film.id) { mutableStateOf<String?>(null) }
    val playButton = remember { FocusRequester() }

    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
            }
            override fun onPlayerError(exception: PlaybackException) {
                error = exception.errorCodeName
                buffering = false
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }

    fun seekBy(ms: Long) {
        val destination = (player.currentPosition + ms).coerceAtLeast(0L)
        player.seekTo(if (player.duration > 0L) destination.coerceAtMost(player.duration) else destination)
    }
    BackHandler(onBack = onExit)
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.MediaPlayPause -> { if (player.isPlaying) player.pause() else player.play(); true }
                    Key.MediaPlay -> { player.play(); true }
                    Key.MediaPause -> { player.pause(); true }
                    Key.MediaFastForward -> { seekBy(10_000L); true }
                    Key.MediaRewind -> { seekBy(-10_000L); true }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { SurfaceView(it).also(player::setVideoSurfaceView) },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxWidth().height(160.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.88f), Color.Transparent))),
        )
        Column(Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = 32.dp)) {
            Text(film.title, style = MovixType.Title)
            Text("${film.license}  ·  ${film.attribution}", style = MovixType.Body.copy(fontSize = 12.sp, color = MovixColors.Muted))
        }
        if (buffering && error == null) {
            Text(
                "Préparation de la vidéo…",
                style = MovixType.Panel,
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(10.dp)).padding(20.dp),
            )
        }
        if (error != null) {
            Column(
                Modifier.align(Alignment.Center)
                    .background(Color(0xFF21182E), RoundedCornerShape(14.dp)).padding(24.dp),
            ) {
                Text("Lecture impossible (${error ?: "réseau"}).", style = MovixType.Panel)
                Spacer(Modifier.height(12.dp))
                PillButton("Réessayer", onClick = {
                    error = null
                    buffering = true
                    player.prepare()
                    player.play()
                })
            }
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))))
                .padding(horizontal = 48.dp, vertical = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PillButton(if (isPlaying) "Pause" else "Lecture", onClick = { if (player.isPlaying) player.pause() else player.play() }, modifier = Modifier.focusRequester(playButton))
                PillButton("− 10 s", onClick = { seekBy(-10_000L) })
                PillButton("+ 10 s", onClick = { seekBy(10_000L) })
                PillButton("Retour", onClick = onExit)
            }
            Spacer(Modifier.height(10.dp))
            Text("Film original avec son générique · ${film.license}", style = MovixType.Body.copy(color = MovixColors.Muted, fontSize = 12.sp))
        }
    }
    LaunchedEffect(film.id) {
        delay(75)
        runCatching { playButton.requestFocus() }
    }
}
