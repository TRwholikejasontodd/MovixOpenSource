package com.movix.tv.data.movix

/**
 * Petit coupe-circuit en mémoire pour une séance TV : après deux erreurs
 * rapprochées sur le même hébergeur, ses flux passent en bas de la liste
 * durant dix minutes. Aucune donnée personnelle ni URL n'est conservée.
 *
 * Les erreurs de lecture seules comptent : une source qui n'a pas encore été
 * essayée n'est jamais déclarée en panne simplement parce qu'une sonde de
 * qualité a expiré.
 */
class SourceHealthTracker(private val clockMs: () -> Long = System::currentTimeMillis) {
    private data class Health(var failures: Int, var firstFailureMs: Long, var blockedUntilMs: Long)
    private val health = HashMap<String, Health>()

    fun recordFailure(host: String?) {
        val key = host?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return
        val now = clockMs()
        val state = health[key]
        if (state == null || now - state.firstFailureMs >= FAILURE_WINDOW_MS) {
            health[key] = Health(1, now, 0L)
            return
        }
        state.failures++
        if (state.failures >= FAILURES_BEFORE_BLOCK) {
            state.blockedUntilMs = now + COOLDOWN_MS
        }
    }

    fun isCoolingDown(host: String?): Boolean {
        val key = host?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
        return (health[key]?.blockedUntilMs ?: 0L) > clockMs()
    }

    private companion object {
        const val FAILURES_BEFORE_BLOCK = 2
        const val FAILURE_WINDOW_MS = 5 * 60 * 1000L
        const val COOLDOWN_MS = 10 * 60 * 1000L
    }
}
