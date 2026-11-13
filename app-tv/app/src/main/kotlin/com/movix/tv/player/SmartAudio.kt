package com.movix.tv.player

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.util.Log

/**
 * Traitement facultatif de la dynamique de la bande son.
 *
 * Sur Android 9+ seulement ; se fixe à l'AudioTrack de Media3 au moyen
 * de son identifiant de session audio. Si le traitement n'existe pas
 * (passthrough/firmware), la lecture garde impérativement son son original.
 * Aucun volume acoustique en dB SPL ne peut être garanti.
 *
 * Les trois modes modifient des paramètres réellement supportés par
 * Android DynamicsProcessing ; ils ne réalisent pas de séparation IA des voix.
 */
enum class AudioMode(val id: String, val title: String) {
    OFF("off", "Désactivé"),
    BALANCED("balanced", "Équilibré"),
    VOICE("voice", "Voix claires"),
    NIGHT("night", "Nuit");

    companion object {
        fun from(raw: String?): AudioMode =
            entries.firstOrNull { it.id == raw } ?: OFF
    }
}

class SmartAudio {
    var mode: AudioMode = AudioMode.OFF
        private set
    var available: Boolean = Build.VERSION.SDK_INT >= 28
        private set
    var active: Boolean = false
        private set
    private var sessionId: Int = 0
    private var effect: DynamicsProcessing? = null

    fun onAudioSession(session: Int) {
        if (session == sessionId) return
        sessionId = session
        apply()
    }

    fun setMode(next: AudioMode) {
        if (mode == next) return
        mode = next
        apply()
    }

    private fun apply() {
        releaseEffect()
        if (mode == AudioMode.OFF || sessionId <= 0 || Build.VERSION.SDK_INT < 28) return
        runCatching {
            val processor = DynamicsProcessing(sessionId)
            try {
                // La configuration d'origine peut varier selon le firmware.
                // Sur une TV où ni MBC ni limiteur ne sont offerts, on sort.
                val cfg = processor.config
                val mbc = cfg.isMbcInUse
                val limiter = cfg.isLimiterInUse
                if (!mbc && !limiter) {
                    processor.release()
                    available = false
                    Log.i("MyoAudio", "Traitement dynamique non pris en charge")
                    return
                }
                val (ratio, threshold, attack, release, limit) = when (mode) {
                    AudioMode.BALANCED -> listOf(1.8f, -21f, 22f, 170f, -2f)
                    AudioMode.VOICE -> listOf(2.2f, -27f, 16f, 180f, -3f)
                    AudioMode.NIGHT -> listOf(3.2f, -30f, 8f, 290f, -8f)
                    AudioMode.OFF -> listOf(1f, 0f, 30f, 150f, 0f)
                }
                for (channel in 0 until cfg.channelCount) {
                    if (mbc && cfg.getMbcByChannelIndex(channel).bandCount > 0) {
                        val band = processor.getMbcBandByChannelIndex(channel, 0)
                        band.isEnabled = true
                        band.ratio = ratio
                        band.threshold = threshold
                        band.attackTime = attack
                        band.releaseTime = release
                        processor.setMbcBandByChannelIndex(channel, 0, band)
                    }
                    if (limiter) {
                        val cap = processor.getLimiterByChannelIndex(channel)
                        cap.isEnabled = true
                        cap.threshold = limit
                        cap.attackTime = 2f
                        cap.releaseTime = 100f
                        processor.setLimiterByChannelIndex(channel, cap)
                    }
                }
                processor.enabled = true
                effect = processor
                active = true
            } catch (error: Exception) {
                processor.release()
                throw error
            }
        }.onFailure {
            available = false
            active = false
            Log.w("MyoAudio", "Compression indisponible ; son original conservé", it)
        }
    }

    private fun releaseEffect() {
        runCatching { effect?.enabled = false }
        runCatching { effect?.release() }
        effect = null
        active = false
    }

    fun release() {
        releaseEffect()
        sessionId = 0
    }
}
