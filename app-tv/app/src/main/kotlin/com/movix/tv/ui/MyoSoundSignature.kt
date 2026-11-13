package com.movix.tv.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/**
 * Jingle MYO original synthétisé à la demande : 400 ms de deux notes
 * harmoniques avec une enveloppe douce. Aucun échantillon externe ni vidéo.
 * Échec de sortie audio = simple absence de jingle, sans bloquer le démarrage.
 */
internal object MyoSoundSignature {
    suspend fun playSafely() = withContext(Dispatchers.IO) {
        runCatching {
            val hz = 16_000
            val n = 6_400
            val samples = ShortArray(n) { i ->
                val time = i.toDouble() / hz
                val attack = (time / .045).coerceIn(0.0, 1.0)
                val release = ((.40 - time) / .28).coerceIn(0.0, 1.0)
                val note = .52 * sin(2 * PI * 392 * time) +
                    .30 * sin(2 * PI * 587.33 * time) +
                    .12 * sin(2 * PI * 784 * time)
                (note * attack * release * 12000).toInt()
                    .coerceIn(-32767, 32767).toShort()
            }
            val track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                AudioFormat.Builder().setSampleRate(hz)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                n * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
            try {
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.setVolume(.11f)
                    if (track.write(samples, 0, n) > 0) {
                        track.play()
                        delay(430)
                    }
                }
            } finally {
                runCatching { track.stop() }
                track.release()
            }
        }.onFailure { Log.d("MyoIntro", "signature sonore indisponible : ${it.javaClass.simpleName}") }
        Unit
    }
}
