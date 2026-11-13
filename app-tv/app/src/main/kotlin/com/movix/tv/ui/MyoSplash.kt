package com.movix.tv.ui

import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.movix.tv.ui.theme.LocalLowRam
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Identité de démarrage native, sans vidéo ni bitmap plein écran.
 * N'impose AUCUN délai : la racine la retire dès que les données minimales
 * de navigation sont disponibles. La barre est indéterminée : aucun faux %.
 *
 * Voix : TTS du système, si disponible ; sa hauteur est légèrement relevée.
 * La présence et le timbre d'une voix enfantine ne sont pas garantis sur TV.
 */
@Composable
fun MyoSplash(modifier: Modifier = Modifier) {
    val violet = Color(0xFFB07AFF)
    val accent = Color(0xFF7438D7)
    val lowRam = LocalLowRam.current
    var shownLetters by remember { mutableIntStateOf(0) }
    val honorific = remember {
        listOf(
            "le Vénérable", "le Brave", "le Magnifique", "le Grand", "le Sage",
            "l’Illustre", "le Légendaire", "le Conquérant", "le Visionnaire",
            "le Redoutable", "l’Intrépide", "le Lumineux", "le Vaillant",
            "le Mystérieux", "l’Incomparable", "le Fantastique",
        ).random()
    }
    LaunchedEffect(Unit) {
        shownLetters = 1
        delay(170)
        shownLetters = 2
        delay(170)
        shownLetters = 3
    }

    // L'initialisation est asynchrone et ne bloque pas le chargement de l'accueil.
    // Une éventuelle parole en retard ne se déclenche pas après la fermeture.
    if (!lowRam) {
        androidx.compose.runtime.key(Unit) {
            val context = androidx.compose.ui.platform.LocalContext.current
            DisposableEffect(context) {
                var active = true
                var engine: TextToSpeech? = null
                engine = TextToSpeech(context) { status ->
                    val tts = engine
                    if (active && status == TextToSpeech.SUCCESS && tts != null) {
                        tts.language = Locale.FRENCH
                        tts.setPitch(1.28f)
                        tts.setSpeechRate(0.92f)
                        tts.speak("Myo", TextToSpeech.QUEUE_FLUSH, null, "myo_splash")
                    }
                }
                onDispose {
                    active = false
                    engine?.stop()
                    engine?.shutdown()
                }
            }
        }
    }

    val animation = rememberInfiniteTransition(label = "myo_loading")
    val progress by animation.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_450, easing = LinearEasing)),
        label = "progress",
    )

    Box(modifier.fillMaxSize().background(Color(0xFF07050D)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                "MYO".forEachIndexed { index, char ->
                    AnimatedVisibility(visible = shownLetters > index, enter = fadeIn(tween(210))) {
                        Text(
                            char.toString(),
                            color = violet,
                            fontSize = 76.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Développé par Louis Hedouin", fontSize = 15.sp, color = Color(0xFFD4CEDD))
            Spacer(Modifier.height(5.dp))
            Text("Louis Hedouin $honorific", fontSize = 12.sp, color = Color(0xFF82788F))
            Spacer(Modifier.height(35.dp))
            Box(
                Modifier.width(224.dp).height(3.dp).background(Color(0xFF292035), RoundedCornerShape(4.dp)),
            ) {
                if (!lowRam) {
                    Box(
                        Modifier
                            .width(72.dp)
                            .height(3.dp)
                            .alpha(0.9f)
                            .background(Brush.horizontalGradient(listOf(accent, violet)), RoundedCornerShape(4.dp))
                            .align(if (progress < 0.5f) Alignment.CenterStart else Alignment.CenterEnd),
                    )
                } else {
                    Box(Modifier.width(64.dp).height(3.dp).background(violet))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Préparation de Myo", fontSize = 11.sp, color = Color(0xFF776D83))
        }
    }
}
