package com.movix.tv.ui

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.movix.tv.ui.theme.Instrument
import com.movix.tv.ui.theme.LocalLowRam
import com.movix.tv.ui.theme.LocalReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/**
 * Signature MYO : construction vectorielle physique, non un fondu de texte.
 * M et Y se forment avec cinq fragments mobiles, O avec quatre segments
 * circulaires. Logo final strictement fixe (couleur, angle, dimensions).
 * Pas de vidéo, de chargement réseau supplémentaire, ni d'image plein écran.
 */
@Composable
fun MyoSplash(waitingForData: Boolean, modifier: Modifier = Modifier, onIntroFinished: () -> Unit = {}) {
    val lowRam = LocalLowRam.current
    val reduceMotion = LocalReduceMotion.current
    val context = LocalContext.current
    val animation = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val honorific = remember { MYO_HONORIFICS.random() }
    // Le MP3 contient déjà la voix n°6, la musique ET l'impact final.
    // Ne jamais lancer en plus TextToSpeech ou MyoSoundSignature.
    // Préparation sur IO, hors du thread de composition de la télévision.
    val audio = remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            audio.value?.let { player ->
                runCatching { if (player.isPlaying) player.stop() }
                runCatching { player.release() }
            }
            audio.value = null
        }
    }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            animation.snapTo(1f)
            onIntroFinished()
            return@LaunchedEffect
        }
        // L'animation ne démarre pas avant la bande-son : pas de décalage
        // arbitraire de synthèse vocale lié au firmware TCL/Android.
        val ready = withContext(Dispatchers.IO) {
            runCatching {
                MediaPlayer.create(context.applicationContext, com.movix.tv.R.raw.myo_intro_voice6)
            }.onFailure {
                Log.w("MyoIntro", "Impossible de préparer la piste MYO, intro muette", it)
            }.getOrNull()
        }
        if (!isActive) {
            ready?.release()
            return@LaunchedEffect
        }
        audio.value = ready
        runCatching { ready?.start() }.onFailure {
            Log.w("MyoIntro", "Sortie sonore indisponible, animation maintenue", it)
        }
        animation.animateTo(1f, tween(INTRO_DURATION_MS.toInt(), easing = LinearEasing))
        onIntroFinished()
    }
    val time = if (reduceMotion) 4f else 4f * animation.value

    val purple = Color(0xFF8B5CF6)
    val lavender = Color(0xFFC4B5FD)
    Box(modifier.fillMaxSize().background(Color(0xFF08060F)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val haze = myoSmooth(time / .67f) * (1f - myoSmooth((time - 2.5f) / .9f))
            if (haze > 0f && !lowRam) {
                val center = Offset(w * (.14f + .64f * (time / 2.3f).coerceIn(0f, 1f)), h * .52f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF6825B9).copy(alpha = haze * .2f), Color.Transparent),
                        center = center, radius = w * .17f,
                    ),
                    radius = w * .17f, center = center,
                )
            }
            val ribbonAlpha = myoSmooth(time / .55f) * (1f - myoSmooth((time - 2.2f) / .7f))
            if (ribbonAlpha > 0f && !lowRam) {
                val ribbon = Path()
                val count = (60 * myoSmooth((time - .06f) / 1.42f)).toInt()
                for (i in 0..count) {
                    val u = i / 60f
                    val x = w * (.16f + .68f * u)
                    val y = h * .5f + 88f * h / 540f +
                        43f * h / 540f * sin((u * .92f + time * .2f) * 2f * PI.toFloat())
                    if (i == 0) ribbon.moveTo(x, y) else ribbon.lineTo(x, y)
                }
                drawPath(ribbon, color = Color(0xFF8750D8), alpha = ribbonAlpha * .48f,
                    style = Stroke(width = 2f * w / 960f, cap = StrokeCap.Round))
            }
            val factor = minOf(w / 1030f, h / 600f)
            val originX = (w - 354f * factor) / 2f
            val originY = (h - 96f * factor) / 2f - 12f * factor
            withTransform({ translate(originX, originY); scale(factor, factor, Offset.Zero) }) {
                for (piece in MYO_PIECES) {
                    val e = myoEase((time - piece.start) / (piece.end - piece.start))
                    if (e <= 0f) continue
                    val cx = piece.points.sumOf { it.first.toDouble() }.toFloat() / piece.points.size
                    val cy = piece.points.sumOf { it.second.toDouble() }.toFloat() / piece.points.size
                    withTransform({
                        translate(piece.dx * (1f - e), piece.dy * (1f - e))
                        rotate(piece.angle * (1f - e), pivot = Offset(cx, cy))
                    }) {
                        drawPath(piece.shape, purple, alpha = myoSmooth((time - piece.start) / .27f))
                    }
                }
                // O construit par 4 arcs articulés. En position finale, ils
                // constituent un cercle fermé de géométrie identique.
                repeat(4) { index ->
                    val start = 1.15f + index * .07f
                    val end = 1.98f + index * .04f
                    val e = myoEase((time - start) / (end - start))
                    if (e > 0f) drawArc(
                        color = purple,
                        startAngle = -90f + index * 90f - 18f * (1f - e),
                        sweepAngle = 90f * e,
                        useCenter = false,
                        topLeft = Offset(260f, 1f),
                        size = Size(94f, 94f),
                        style = Stroke(width = 23f, cap = StrokeCap.Butt),
                        alpha = myoSmooth((time - start) / .28f),
                    )
                }
                val gleam = if (time in 1.94f..2.64f) {
                    sin(PI.toFloat() * myoSmooth((time - 1.94f) / .70f))
                } else 0f
                if (gleam > 0f && !lowRam) {
                    val x = 4f + 370f * myoSmooth((time - 1.94f) / .70f)
                    drawLine(lavender, Offset(x - 5f, -5f), Offset(x + 25f, 107f),
                        strokeWidth = 1.5f, alpha = .15f * gleam)
                }
            }
        }
        Text(
            "Développé par Louis Hedouin $honorific",
            fontFamily = Instrument,
            fontWeight = FontWeight.Normal,
            fontSize = 9.sp,
            letterSpacing = .2.sp,
            color = Color(0xFFAAA3B4),
            modifier = Modifier.offset(y = 78.dp)
                .alpha(myoSmooth((time - 3.44f) / .36f) * .36f),
        )
        // Jamais de faux pourcentage : la barre n'apparaît qu'après les 4 s,
        // lorsque les données ne sont pas encore prêtes.
        if (waitingForData && time >= 4f) {
            val looping = rememberInfiniteTransition(label = "myo_loading")
            val phase by looping.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing)),
                label = "indicator",
            )
            Box(Modifier.offset(y = 129.dp).width(120.dp).height(2.dp)
                .background(Color(0xFF282039), RoundedCornerShape(10.dp))) {
                Box(Modifier.width(30.dp).height(2.dp).offset(x = (phase * 90f).dp)
                    .background(Color(0xFF8B5CF6), RoundedCornerShape(10.dp)))
            }
        }
    }
}

private data class LogoPiece(
    val points: List<Pair<Float, Float>>, val dx: Float, val dy: Float,
    val angle: Float, val start: Float, val end: Float,
) {
    val shape = Path().apply {
        points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
        close()
    }
}
private val MYO_PIECES = listOf(
    LogoPiece(listOf(0f to 0f, 22f to 0f, 60f to 54f, 60f to 82f, 25f to 39f, 25f to 96f, 0f to 96f), -95f, -34f, -13f, .61f, 1.45f),
    LogoPiece(listOf(60f to 54f, 95f to 0f, 118f to 0f, 118f to 96f, 93f to 96f, 93f to 39f, 60f to 82f), 108f, 55f, 11f, .73f, 1.51f),
    LogoPiece(listOf(143f to 0f, 173f to 0f, 199f to 40f, 199f to 69f, 187f to 60f), -62f, -95f, -15f, .91f, 1.71f),
    LogoPiece(listOf(255f to 0f, 225f to 0f, 199f to 40f, 199f to 69f, 212f to 60f), 89f, -67f, 17f, 1.02f, 1.77f),
    LogoPiece(listOf(187f to 57f, 212f to 57f, 212f to 96f, 187f to 96f), 0f, 85f, 0f, 1.16f, 1.91f),
)
private val MYO_HONORIFICS = listOf(
    "le Vénérable", "le Brave", "le Magnifique", "le Grand", "le Sage",
    "l’Illustre", "le Légendaire", "le Conquérant", "le Visionnaire",
    "le Redoutable", "l’Intrépide", "le Lumineux", "le Vaillant",
    "le Mystérieux", "l’Incomparable", "le Fantastique",
)
internal const val INTRO_DURATION_MS = 4_100L

internal fun myoEase(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return 1f - (1f - t) * (1f - t) * (1f - t)
}
internal fun myoSmooth(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
