package com.vitessegps.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Couleurs à fort contraste, lisibles en plein soleil comme de nuit. */
object AppColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF121212)
    val Knots = Color(0xFFFFFFFF)       // vitesse principale : blanc pur
    val Kmh = Color(0xFFFFD54F)         // vitesse secondaire : jaune ambré
    val Label = Color(0xFFB0BEC5)
    val GpsOk = Color(0xFF00E676)
    val GpsSearching = Color(0xFFFF9100)
    val Divider = Color(0xFF37474F)
}

private val DarkColors = darkColorScheme(
    primary = AppColors.Kmh,
    onPrimary = Color.Black,
    secondary = AppColors.GpsOk,
    background = AppColors.Background,
    onBackground = Color.White,
    surface = AppColors.Surface,
    onSurface = Color.White,
)

/** Thème de l'application : toujours sombre. */
@Composable
fun VitesseGpsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
