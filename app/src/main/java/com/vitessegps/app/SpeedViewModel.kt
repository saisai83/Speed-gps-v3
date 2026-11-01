package com.vitessegps.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** État affiché par l'écran principal. Toutes les vitesses sont en m/s. */
data class SpeedUiState(
    val speedMs: Float = 0f,
    val maxSpeedMs: Float = 0f,
    /** Cap en degrés (0–360), null si inconnu. */
    val bearingDeg: Float? = null,
    /** Précision horizontale en mètres, null si inconnue. */
    val accuracyM: Float? = null,
    /** true quand des positions récentes sont reçues. */
    val hasFix: Boolean = false,
) {
    val speedKnots get() = SpeedCalculator.msToKnots(speedMs)
    val speedKmh get() = SpeedCalculator.msToKmh(speedMs)
    val maxKnots get() = SpeedCalculator.msToKnots(maxSpeedMs)
    val maxKmh get() = SpeedCalculator.msToKmh(maxSpeedMs)
}

/**
 * ViewModel qui gère l'abonnement au GPS via [FusedLocationProviderClient]
 * et expose l'état de l'écran sous forme de [StateFlow].
 *
 * Les mises à jour sont démarrées / arrêtées par l'Activity selon son cycle
 * de vie ([startUpdates] / [stopUpdates]) : aucune mesure en arrière-plan.
 */
class SpeedViewModel(application: Application) : AndroidViewModel(application) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(application)
    private val calculator = SpeedCalculator()

    private val _uiState = MutableStateFlow(SpeedUiState())
    val uiState: StateFlow<SpeedUiState> = _uiState.asStateFlow()

    private var isUpdating = false
    private var lastFixElapsedMs = 0L
    private var watchdogJob: Job? = null

    /** Requête : haute précision, une mesure par seconde. */
    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
        .setMinUpdateIntervalMillis(UPDATE_INTERVAL_MS)
        .setWaitForAccurateLocation(false)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::handleLocation)
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            if (!availability.isLocationAvailable) {
                _uiState.update { it.copy(hasFix = false) }
            }
        }
    }

    /** Démarre les mises à jour GPS (sans effet si déjà en cours ou sans permission). */
    @SuppressLint("MissingPermission") // la permission est vérifiée juste en dessous
    fun startUpdates() {
        if (isUpdating || !hasLocationPermission()) return
        isUpdating = true
        calculator.reset()
        _uiState.update { it.copy(hasFix = false, speedMs = 0f) }
        fusedClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        startWatchdog()
    }

    /** Arrête les mises à jour GPS (appelé quand l'app passe en arrière-plan). */
    fun stopUpdates() {
        if (!isUpdating) return
        isUpdating = false
        fusedClient.removeLocationUpdates(locationCallback)
        watchdogJob?.cancel()
        watchdogJob = null
    }

    /** Remet à zéro la vitesse maximale de la session. */
    fun resetMaxSpeed() {
        _uiState.update { it.copy(maxSpeedMs = 0f) }
    }

    private fun handleLocation(location: Location) {
        lastFixElapsedMs = SystemClock.elapsedRealtime()
        val speed = calculator.onLocation(location) ?: 0f
        _uiState.update { state ->
            state.copy(
                speedMs = speed,
                maxSpeedMs = maxOf(state.maxSpeedMs, speed),
                // Le cap n'a de sens qu'en mouvement ; on garde le dernier connu sinon.
                bearingDeg = if (location.hasBearing() && speed > 0f) location.bearing else state.bearingDeg,
                accuracyM = if (location.hasAccuracy()) location.accuracy else null,
                hasFix = true,
            )
        }
    }

    /**
     * Surveille l'arrivée des positions : sans nouvelle mesure depuis
     * [FIX_TIMEOUT_MS], on repasse en « Recherche GPS… ».
     */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val stale = SystemClock.elapsedRealtime() - lastFixElapsedMs > FIX_TIMEOUT_MS
                if (stale && _uiState.value.hasFix) {
                    _uiState.update { it.copy(hasFix = false, speedMs = 0f) }
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val ctx = getApplication<Application>()
        return listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    }

    override fun onCleared() {
        stopUpdates()
        super.onCleared()
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 1_000L
        const val FIX_TIMEOUT_MS = 5_000L
    }
}
