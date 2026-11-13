package com.movix.tv.ui

import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.movix.tv.ui.theme.Bricolage
import com.movix.tv.ui.theme.Instrument
import com.movix.tv.ui.theme.LocalLowRam
import com.movix.tv.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.sin

/**
 * Signature MYO originale de 3,2 secondes.
 * Un mot unique, même police/couleur/position à chaque image : le logo est
 * révélé par un masque et non par des lettres générées indépendamment.
 * Le fond est un chemin vectoriel éclairé par une lueur légère, sans vidéo,
 * bitmap permanent, shader lourd, ni requêtes réseau.
 *
 * La voix vient du TTS système. Une vraie voix enfantine / synchro labiale
 * n'est PAS garantie : le moteur peut être absent, lent ou en sortie passthrough.
 */
@Composable
fun MyoSplash(modifier: Modifier = Modifier) {
    val lowRam = LocalLowRam.current
    val reduceMotion = LocalReduceMotion.current
    val context = LocalContext.current
    val startedAt = remember { SystemClock.elapsedRealtime() }
    val progress = remember { Animatable(0f) }
    val honorific = remember {
        listOf(
            "le Vénérable", "le Brave", "le Magnifique", "le Grand", "le Sage",
            "l’Illustre", "le Légendaire", "le Conquérant", "le Visionnaire",
            "le Redoutable", "l’Intrépide", "le Lumineux", "le Vaillant",
            "le Mystérieux", "l’Incomparable", "le Fantastique",
        ).random()
    }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) progress.snapTo(1f)
        else progress.animateTo(1f, tween(INTRO_DURATION_MS.toInt(), easing = LinearEasing))
    }
    val t = progress.value
    // La typographie ne se transforme jamais : seul un masque continu
    // découvre le mot de gauche à droite après la montée de la lumière.
    val reveal = smooth(((t - 0.18f) / 0.51f).coerceIn(0f, 1f))
    val creditAlpha = 0.52f * smooth(((t - 0.77f) / 0.18f).coerceIn(0f, 1f))

    // Préparer la voix en parallèle du mouvement et ne la prononcer
    // qu'à l'instant de formation finale (vers 2,55 s).
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    val voiceReady = remember { mutableStateOf(false) }
    DisposableEffect(context) {
        var disposed = false
        val engine = TextToSpeech(context.applicationContext) { status ->
            if (!disposed && status == TextToSpeech.SUCCESS) voiceReady.value = true
        }
        tts.value = engine
        onDispose {
            disposed = true
            voiceReady.value = false
            engine.stop()
            engine.shutdown()
            tts.value = null
        }
    }
    LaunchedEffect(voiceReady.value, reduceMotion) {
        if (!voiceReady.value || reduceMotion) return@LaunchedEffect
        val untilVoice = 2_540L - (SystemClock.elapsedRealtime() - startedAt)
        if (untilVoice > 0) delay(untilVoice)
        if (SystemClock.elapsedRealtime() - startedAt > INTRO_DURATION_MS) return@LaunchedEffect
        runCatching {
            tts.value?.apply {
                language = Locale.FRENCH
                setPitch(1.25f)
                setSpeechRate(0.92f)
                speak("Myo", TextToSpeech.QUEUE_FLUSH, null, "myo_signature")
            }
        }
    }

    val logo = Color(0xFFB98AF3) // Violet lavande FIXE, aucun changement de couleur des lettres.
    val shine = Color(0xFF9D55EF)
    val indeterminate = rememberInfiniteTransition(label = "myo_waiting")
    val phase by indeterminate.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_300, easing = LinearEasing)),
        label = "phase",
    )

    Box(modifier.fillMaxSize().background(Color(0xFF050309)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (t > 0.02f) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w * -0.06f, h * 0.52f)
                    cubicTo(w * 0.25f, h * 0.40f, w * 0.67f, h * 0.59f, w * 1.06f, h * 0.42f)
                }
                // Deux traits fins suffisent : pas de particules ni de zoom.
                val lineAlpha = (0.25f + t * 0.25f).coerceAtMost(0.46f)
                drawPath(path, color = shine, style = Stroke(width = if (lowRam) 1.4.dp.toPx() else 2.dp.toPx(), cap = StrokeCap.Round), alpha = lineAlpha)
                if (!lowRam && !reduceMotion && t < 0.84f) {
                    val x = w * (-0.06f + 1.12f * t)
                    val y = h * (0.52f - 0.065f * sin(t * Math.PI * 2.0).toFloat())
                    val center = Offset(x, y)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(shine.copy(alpha = 0.23f), shine.copy(alpha = 0f)),
                            center = center,
                            radius = w * 0.15f,
                        ),
                        radius = w * 0.15f,
                        center = center,
                    )
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "MYO",
                style = TextStyle(
                    fontFamily = Bricolage,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 87.sp,
                    letterSpacing = (-0.045).em,
                    color = logo,
                    shadow = Shadow(color = logo.copy(alpha = 0.20f), offset = Offset.Zero, blurRadius = 14f),
                ),
                modifier = Modifier.drawWithContent {
                    clipRect(left = 0f, top = 0f, right = size.width * reveal, bottom = size.height) {
                        drawContent()
                    }
                },
            )
            Spacer(Modifier.height(9.dp))
            Text(
                "Développé par Louis Hedouin $honorific",
                style = TextStyle(
                    fontFamily = Instrument,
                    fontWeight = FontWeight.Normal,
                    fontSize = 10.sp,
                    letterSpacing = (0.02).em,
                    color = Color(0xFFAAA0B6),
                ),
                modifier = Modifier.alpha(creditAlpha),
            )
            Spacer(Modifier.height(20.dp))
            // Aucune fausse progression : segment indéterminé discret à la fin
            // de l'introduction, si le chargement des données reste en cours.
            Box(
                Modifier.width(132.dp).height(2.dp)
                    .alpha(if (t > 0.94f) 0.50f else 0f)
                    .background(Color(0xFF292036), RoundedCornerShape(10.dp)),
            ) {
                Box(
                    Modifier.width(32.dp).height(2.dp)
                        .offset(x = (phase * 100f).dp)
                        .background(shine, RoundedCornerShape(10.dp)),
                )
            }
        }
    }
}

private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

internal const val INTRO_DURATION_MS = 3_200L
