package com.vitessegps.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitessegps.app.ui.LocationDisabledScreen
import com.vitessegps.app.ui.PermissionScreen
import com.vitessegps.app.ui.SpeedScreen
import com.vitessegps.app.ui.VitesseGpsTheme

/**
 * Activité unique. Elle :
 *  - garde l'écran allumé (FLAG_KEEP_SCREEN_ON) ;
 *  - gère la permission de localisation et l'état du GPS ;
 *  - démarre / arrête les mises à jour GPS selon le cycle de vie (premier plan uniquement).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            VitesseGpsTheme {
                MainContent(activity = this)
            }
        }
    }
}

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private fun hasLocationPermission(context: Context): Boolean =
    LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

private fun isLocationEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        lm.isLocationEnabled
    } else {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}

@Composable
private fun MainContent(activity: ComponentActivity, viewModel: SpeedViewModel = viewModel()) {
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionGranted by remember { mutableStateOf(hasLocationPermission(activity)) }
    var locationEnabled by remember { mutableStateOf(isLocationEnabled(activity)) }
    var isForeground by remember { mutableStateOf(false) }
    // Indique si la permission a déjà été demandée (pour détecter un refus définitif).
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionGranted = results.values.any { it }
        // Après un refus, si Android ne propose plus d'explication, le refus est définitif :
        // seul l'écran des paramètres de l'app permet alors d'accorder la permission.
        permanentlyDenied = !permissionGranted && LOCATION_PERMISSIONS.none {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
    }

    // Suivi du cycle de vie : premier plan / arrière-plan, et rafraîchissement
    // des états au retour (ex. après un passage dans les paramètres).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> isForeground = true
                Lifecycle.Event.ON_RESUME -> {
                    permissionGranted = hasLocationPermission(activity)
                    locationEnabled = isLocationEnabled(activity)
                    if (permissionGranted) permanentlyDenied = false
                }
                Lifecycle.Event.ON_STOP -> isForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Détecte l'activation / désactivation du GPS pendant que l'app est ouverte.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                locationEnabled = isLocationEnabled(context)
            }
        }
        ContextCompat.registerReceiver(
            activity,
            receiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { activity.unregisterReceiver(receiver) }
    }

    // Les mises à jour GPS ne tournent que si l'app est visible ET tout est autorisé.
    LaunchedEffect(isForeground, permissionGranted, locationEnabled) {
        if (isForeground && permissionGranted && locationEnabled) {
            viewModel.startUpdates()
        } else {
            viewModel.stopUpdates()
        }
    }

    when {
        !permissionGranted -> PermissionScreen(
            permanentlyDenied = permanentlyDenied && permissionRequested,
            onRequestPermission = {
                permissionRequested = true
                permissionLauncher.launch(LOCATION_PERMISSIONS)
            },
            onOpenAppSettings = {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", activity.packageName, null),
                    )
                )
            },
        )

        !locationEnabled -> LocationDisabledScreen(
            onOpenLocationSettings = {
                activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            },
        )

        else -> {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            SpeedScreen(state = state, onResetMax = viewModel::resetMaxSpeed)
        }
    }
}
