package com.vitessegps.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitessegps.app.SpeedUiState
import java.util.Locale
import kotlin.math.roundToInt

/** Formate une valeur avec 1 décimale, à la française (virgule). */
private fun Float.oneDecimal(): String = String.format(Locale.FRANCE, "%.1f", this)

/** Convertit un cap en degrés vers un point cardinal français. */
private fun cardinal(deg: Float): String {
    val points = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    return points[(((deg % 360f) + 360f) % 360f / 45f).roundToInt() % 8]
}

/** Style des grands chiffres : gras, chiffres à largeur fixe (pas de « sautillement »). */
private fun bigNumberStyle(color: Color, size: Dp, density: androidx.compose.ui.unit.Density) = TextStyle(
    color = color,
    fontWeight = FontWeight.Bold,
    fontSize = with(density) { size.toSp() }, // taille indépendante du réglage de police système
    fontFeatureSettings = "tnum",
    textAlign = TextAlign.Center,
)

/**
 * Écran principal : vitesse en nœuds et en km/h en très grand,
 * puis cap, précision, vitesse maximale et état du GPS.
 * S'adapte automatiquement au portrait et au paysage.
 */
@Composable
fun SpeedScreen(state: SpeedUiState, onResetMax: () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .safeDrawingPadding()
            .padding(12.dp)
    ) {
        val landscape = maxWidth > maxHeight

        Column(modifier = Modifier.fillMaxSize()) {
            StatusBar(state)

            if (landscape) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    SpeedBlock(state.speedKnots, "nœuds", AppColors.Knots, Modifier.weight(1f).fillMaxHeight())
                    Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 16.dp).background(AppColors.Divider))
                    SpeedBlock(state.speedKmh, "km/h", AppColors.Kmh, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                SpeedBlock(state.speedKnots, "nœuds", AppColors.Knots, Modifier.weight(1f).fillMaxWidth())
                HorizontalDivider(color = AppColors.Divider)
                SpeedBlock(state.speedKmh, "km/h", AppColors.Kmh, Modifier.weight(1f).fillMaxWidth())
            }

            Spacer(Modifier.height(8.dp))
            InfoBar(state, onResetMax)
        }
    }
}

/** Ligne du haut : indicateur d'état GPS et précision. */
@Composable
private fun StatusBar(state: SpeedUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (state.hasFix) AppColors.GpsOk else AppColors.GpsSearching
        Box(Modifier.size(14.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (state.hasFix) "GPS OK" else "Recherche GPS…",
            color = color,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "Précision : " + (state.accuracyM?.let { "± ${it.roundToInt()} m" } ?: "—"),
            color = AppColors.Label,
            fontSize = 18.sp,
        )
    }
}

/** Bloc affichant une vitesse en très grand, dimensionnée selon la place disponible. */
@Composable
private fun SpeedBlock(value: Float, unit: String, color: Color, modifier: Modifier) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // Jusqu'à 5 caractères (« 123,4 ») : on adapte la taille à la largeur et à la hauteur.
        val numberSize = minOf(maxWidth / 3.1f, maxHeight * 0.62f)
        val unitSize = (numberSize * 0.22f).coerceIn(16.dp, 48.dp)
        val density = LocalDensity.current
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value.oneDecimal(),
                style = bigNumberStyle(color, numberSize, density),
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = unit,
                color = color.copy(alpha = 0.85f),
                fontSize = with(density) { unitSize.toSp() },
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Ligne du bas : cap, vitesse maximale et bouton de remise à zéro. */
@Composable
private fun InfoBar(state: SpeedUiState, onResetMax: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        InfoItem(
            label = "Cap",
            value = state.bearingDeg?.let { "${it.roundToInt() % 360}° ${cardinal(it)}" } ?: "—",
        )
        InfoItem(
            label = "Max",
            value = "${state.maxKnots.oneDecimal()} kn\n${state.maxKmh.oneDecimal()} km/h",
        )
        OutlinedButton(onClick = onResetMax) {
            Text("RAZ max", fontSize = 16.sp, color = Color.White)
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column(modifier = Modifier.padding(end = 8.dp)) {
        Text(label, color = AppColors.Label, fontSize = 14.sp)
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 22.sp)
    }
}

/** Écran affiché tant que la permission de localisation n'est pas accordée. */
@Composable
fun PermissionScreen(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    MessageScreen(
        title = "Accès à la position requis",
        message = if (permanentlyDenied) {
            "L'autorisation de localisation a été refusée.\n\n" +
                "Pour afficher votre vitesse, ouvrez les paramètres de l'application, " +
                "puis Autorisations > Position > « Autoriser seulement si l'appli est en cours d'utilisation » " +
                "et choisissez « Position exacte »."
        } else {
            "Cette application mesure votre vitesse grâce au GPS du téléphone.\n\n" +
                "Elle a besoin d'accéder à votre position précise, uniquement pendant qu'elle est ouverte. " +
                "Aucune donnée n'est enregistrée ni envoyée."
        },
        buttonText = if (permanentlyDenied) "Ouvrir les paramètres" else "Autoriser la localisation",
        onClick = if (permanentlyDenied) onOpenAppSettings else onRequestPermission,
    )
}

/** Écran affiché quand la localisation est désactivée sur le téléphone. */
@Composable
fun LocationDisabledScreen(onOpenLocationSettings: () -> Unit) {
    MessageScreen(
        title = "GPS désactivé",
        message = "La localisation est désactivée sur votre téléphone.\n\n" +
            "Activez-la pour que l'application puisse mesurer votre vitesse.",
        buttonText = "Paramètres de localisation",
        onClick = onOpenLocationSettings,
    )
}

@Composable
private fun MessageScreen(title: String, message: String, buttonText: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(message, color = AppColors.Label, fontSize = 18.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Kmh, contentColor = Color.Black),
        ) {
            Text(buttonText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(widthDp = 380, heightDp = 780)
@Composable
private fun PreviewPortrait() = VitesseGpsTheme {
    SpeedScreen(SpeedUiState(speedMs = 6.2f, maxSpeedMs = 9.1f, bearingDeg = 212f, accuracyM = 4f, hasFix = true)) {}
}

@Preview(widthDp = 800, heightDp = 380)
@Composable
private fun PreviewLandscape() = VitesseGpsTheme {
    SpeedScreen(SpeedUiState(speedMs = 12.4f, maxSpeedMs = 15f, bearingDeg = 45f, accuracyM = 3f, hasFix = false)) {}
}
