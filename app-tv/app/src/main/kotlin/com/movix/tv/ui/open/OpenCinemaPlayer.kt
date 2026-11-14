package com.movix.tv.ui.open

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.input.key.key
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
import androidx.tv.material3.Text
import com.movix.tv.container
import com.movix.tv.data.movix.StreamKind
import com.movix.tv.data.open.OpenFilm
import com.movix.tv.ui.components.PillButton
import com.movix.tv.ui.home.PlaybackActivity
import com.movix.tv.ui.theme.MovixColors
import com.movix.tv.ui.theme.MovixType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Native Media3 player for real open-film files from the Internet Archive API.
 * No installed external player, TMDB key, Movix account, or Movix source endpoint.
 */
@Composable
fun OpenCinemaPlayer(film: OpenFilm, onExit: () -> Unit) {
    val container = LocalContext.current.container
    val lifecycleOwner = LocalLifecycleOwner.current
    val progressKey = "myo_open_resume_${film.entry.id}"
    val player = remember(film.mediaUrl) {
        container.playerEngine.newPlayer().apply {
            setMediaSource(container.playerEngine.mediaSource(film.mediaUrl, StreamKind.FILE))
            val previous = container.store.get(progressKey)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            if (previous > 0L) seekTo(previous)
            prepare()
            playWhenReady = true
        }
    }
    var playing by remember(film.mediaUrl) { mutableStateOf(false) }
    var buffering by remember(film.mediaUrl) { mutableStateOf(true) }
    var ended by remember(film.mediaUrl) { mutableStateOf(false) }
    var errorCode by remember(film.mediaUrl) { mutableStateOf<String?>(null) }
    var progressMs by remember(film.mediaUrl) { mutableLongStateOf(0L) }
    var durationMs by remember(film.mediaUrl) { mutableLongStateOf(0L) }
    val playFocus = remember { FocusRequester() }

    DisposableEffect(player, lifecycleOwner) {
        PlaybackActivity.begin()
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) { playing = value }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
                ended = state == Player.STATE_ENDED
                if (state == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                errorCode = error.errorCodeName
                buffering = false
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            val position = if (ended) 0L else player.currentPosition.coerceAtLeast(0L)
            container.appScope.launch { container.store.set(progressKey, position.toString()) }
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
            PlaybackActivity.end()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(1000L)
            progressMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
        }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(10_000L)
            if (player.currentPosition > 0L) container.store.set(progressKey, player.currentPosition.toString())
        }
    }
    LaunchedEffect(Unit) {
        delay(90L)
        runCatching { playFocus.requestFocus() }
    }

    fun seekBy(millis: Long) {
        val destination = (player.currentPosition + millis).coerceAtLeast(0L)
        val d = player.duration
        player.seekTo(if (d > 0L) destination.coerceAtMost(d) else destination)
    }
    BackHandler { onExit() }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (keyEvent.key) {
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
        Column(
            Modifier.align(Alignment.TopStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.93f), Color.Transparent)))
                .padding(horizontal = 44.dp, vertical = 22.dp),
        ) {
            Text(film.title, style = MovixType.Title.copy(fontSize = 29.sp))
            Text("${film.license} · ${film.attribution}", style = MovixType.Meta.copy(fontSize = 11.sp), maxLines = 2)
        }
        if (buffering && errorCode == null) {
            Text(
                "Préparation et mise en mémoire tampon…",
                style = MovixType.Panel,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(14.dp))
                    .padding(24.dp),
            )
        }
        if (ended) {
            Text(
                "Film terminé",
                style = MovixType.Panel,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(14.dp)).padding(24.dp),
            )
        }
        if (errorCode != null) {
            Column(
                modifier = Modifier.align(Alignment.Center)
                    .background(Color(0xFF241735), RoundedCornerShape(14.dp)).padding(24.dp),
            ) {
                Text("Le serveur vidéo n'a pas pu fournir ce film.", style = MovixType.Panel)
                Text("Diagnostic Media3 : ${errorCode ?: "inconnu"}", style = MovixType.Body)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Réessayer", onClick = {
                        errorCode = null
                        buffering = true
                        player.prepare()
                        player.play()
                    })
                    PillButton("Retour", onClick = onExit)
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.96f))))
                .padding(start = 42.dp, end = 42.dp, bottom = 20.dp, top = 32.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton(
                    if (playing) "Pause" else if (ended) "Revoir" else "Lecture",
                    onClick = {
                        if (ended) { player.seekTo(0); player.play() }
                        else if (player.isPlaying) player.pause() else player.play()
                    },
                    modifier = Modifier.focusRequester(playFocus),
                )
                PillButton("−10 s", onClick = { seekBy(-10_000L) })
                PillButton("+10 s", onClick = { seekBy(10_000L) })
                PillButton("Retour", onClick = onExit)
                Text("${clock(progressMs)} / ${clock(durationMs)}", style = MovixType.Body.copy(fontSize = 15.sp))
            }
        }
    }
}

private fun clock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return "%02d:%02d:%02d".format(total / 3600L, (total / 60L) % 60L, total % 60L)
}
