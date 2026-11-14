package com.movix.tv.ui

import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.movix.tv.container
import com.movix.tv.ui.theme.Bricolage
import com.movix.tv.ui.theme.Instrument
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

private val Honorifics = listOf(
    "Brave", "Sage", "Magnifique", "Illustre", "Légendaire", "Visionnaire",
    "Conquérant", "Redoutable", "Créatif", "Audacieux",
)

/**
 * Lightweight cinematic MYO startup, original vector-friendly lettering.
 *
 * An optional higher-pitch *system* speech-synthesis voice says "Myo"; whether
 * it sounds childlike depends on available TV voices. No embedded voice asset
 * and no external TTS service is required. A missing TTS engine cannot crash
 * or prevent launch.
 */
@Composable
fun MyoStartup(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var completed by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(0) }
    var spoken by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    val honorific = remember { Honorifics[Random.nextInt(Honorifics.size)] }
    val tts = remember(context) {
        runCatching {
            TextToSpeech(context.applicationContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
            }
        }.getOrNull()
    }
    DisposableEffect(tts) {
        onDispose {
            runCatching { tts?.stop(); tts?.shutdown() }
        }
    }

    LaunchedEffect(Unit) {
        // Catalogue and UI do real work while the short introduction is playing.
        if (context.container.tmdbKey().isNullOrBlank()) {
            context.container.appScope.launch {
                runCatching { context.container.openCinema.films() }
            }
        }
        delay(450L)
        step = 1
        delay(350L)
        step = 2
        delay(350L)
        step = 3
        delay(2_400L)
        completed = true
    }

    LaunchedEffect(step, ttsReady) {
        if (step == 3 && ttsReady && !spoken) {
            spoken = true
            runCatching {
                tts?.language = Locale.FRENCH
                tts?.setPitch(1.38f)
                tts?.setSpeechRate(0.9f)
                tts?.speak("Myo", TextToSpeech.QUEUE_FLUSH, null, "myo-logo")
            }
        }
    }

    if (completed) {
        content()
    } else {
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF36175C), Color(0xFF160D27), Color(0xFF050408)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("M", "Y", "O").forEachIndexed { index, letter ->
                        AnimatedVisibility(
                            visible = step >= index + 1,
                            enter = fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.72f),
                        ) {
                            Text(
                                letter,
                                fontFamily = Bricolage,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 96.sp,
                                color = Color(0xFFB692FF),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                AnimatedVisibility(visible = step >= 3, enter = fadeIn(tween(420))) {
                    Text(
                        "Développé par Louis Hedouin le $honorific",
                        fontFamily = Instrument,
                        fontSize = 15.sp,
                        color = Color(0xFFDBD1EC),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
    }
}
