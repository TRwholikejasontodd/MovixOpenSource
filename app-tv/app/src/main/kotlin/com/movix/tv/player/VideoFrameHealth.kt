package com.movix.tv.player

/**
 * Interprète les rapports de perte d'images Media3 sans dépendre d'Android.
 *
 * Une vidéo qui perd > 12 images/s pendant plusieurs fenêtres consécutives
 * est probablement trop lourde à décoder sur l'appareil. On abaisse d'abord
 * les contraintes des pistes adaptatives avant de changer de source.
 * Les petites variations / événements très courts sont ignorés.
 */
class VideoFrameHealth {
    enum class Action { NONE, LIMIT_1080P, LIMIT_720P, SWITCH_SOURCE }

    private var severeWindows = 0

    fun observe(droppedFrames: Int, elapsedMs: Long): Action {
        if (elapsedMs < 800L || droppedFrames < 0) return Action.NONE
        val droppedPerSecond = droppedFrames.toLong() * 1_000L / elapsedMs
        if (droppedPerSecond < SEVERE_DROPS_PER_SECOND) {
            severeWindows = 0
            return Action.NONE
        }
        severeWindows++
        return when (severeWindows) {
            2 -> Action.LIMIT_1080P
            4 -> Action.LIMIT_720P
            6 -> Action.SWITCH_SOURCE
            else -> Action.NONE
        }
    }

    fun reset() {
        severeWindows = 0
    }

    private companion object {
        const val SEVERE_DROPS_PER_SECOND = 12L
    }
}
