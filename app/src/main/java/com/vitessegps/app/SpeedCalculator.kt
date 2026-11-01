package com.vitessegps.app

import android.location.Location

/**
 * Logique pure de calcul de vitesse, indépendante d'Android UI.
 *
 * - Utilise [Location.getSpeed] quand il est disponible ([Location.hasSpeed]).
 * - Sinon, calcule la vitesse à partir de la distance et du temps écoulé
 *   entre deux positions successives.
 * - Applique une moyenne mobile sur [WINDOW_SIZE] mesures pour lisser l'affichage.
 * - Force la vitesse à 0 sous [STOP_THRESHOLD_KNOTS] pour filtrer le bruit GPS à l'arrêt.
 */
class SpeedCalculator {

    private val window = ArrayDeque<Float>(WINDOW_SIZE)
    private var previous: Location? = null

    /**
     * Intègre une nouvelle position et renvoie la vitesse lissée en m/s,
     * ou `null` si aucune vitesse n'a encore pu être déterminée.
     */
    fun onLocation(location: Location): Float? {
        val raw = rawSpeed(location, previous)
        previous = location
        if (raw == null || raw.isNaN() || raw < 0f) return currentAverage()

        if (window.size == WINDOW_SIZE) window.removeFirst()
        window.addLast(raw)

        val smoothed = currentAverage() ?: return null
        // Seuil d'arrêt : en dessous de 0,5 nœud on considère qu'on est immobile.
        return if (msToKnots(smoothed) < STOP_THRESHOLD_KNOTS) 0f else smoothed
    }

    /** Réinitialise l'historique (ex. : reprise après une mise en pause). */
    fun reset() {
        window.clear()
        previous = null
    }

    private fun currentAverage(): Float? =
        if (window.isEmpty()) null else window.sum() / window.size

    /** Vitesse brute en m/s pour cette position. */
    private fun rawSpeed(current: Location, prev: Location?): Float? {
        if (current.hasSpeed()) return current.speed

        // Repli : distance / temps entre deux positions successives.
        if (prev == null) return null
        val dtSeconds = (current.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1_000_000_000.0
        if (dtSeconds <= 0.0 || dtSeconds > MAX_GAP_SECONDS) return null
        return (prev.distanceTo(current) / dtSeconds).toFloat()
    }

    companion object {
        const val WINDOW_SIZE = 3
        const val STOP_THRESHOLD_KNOTS = 0.5f

        /** Au-delà de cet écart entre deux positions, le calcul n'est plus fiable. */
        private const val MAX_GAP_SECONDS = 10.0

        private const val MS_TO_KMH = 3.6f
        private const val MS_TO_KNOTS = 1.943844f

        fun msToKmh(ms: Float): Float = ms * MS_TO_KMH
        fun msToKnots(ms: Float): Float = ms * MS_TO_KNOTS
    }
}
