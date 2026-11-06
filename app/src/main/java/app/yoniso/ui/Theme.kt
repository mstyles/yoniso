package app.yoniso.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Muted greens and warm neutrals, matching the pause overlay.
private val LightColors = lightColorScheme(
    primary = Color(0xFF55685A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9D3CB),
    onPrimaryContainer = Color(0xFF243228),
    secondaryContainer = Color(0xFFC9D3CB),
    onSecondaryContainer = Color(0xFF243228),
    tertiaryContainer = Color(0xFFF1E3CC),
    onTertiaryContainer = Color(0xFF3D3020),
    background = Color(0xFFF7F5F1),
    onBackground = Color(0xFF3A3A38),
    surface = Color(0xFFF7F5F1),
    onSurface = Color(0xFF3A3A38),
    surfaceVariant = Color(0xFFEDEAE4),
    onSurfaceVariant = Color(0xFF5E5D59),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFC4B3),
    onPrimary = Color(0xFF1C2B20),
    primaryContainer = Color(0xFF3D4F41),
    onPrimaryContainer = Color(0xFFD3E2D6),
    secondaryContainer = Color(0xFF3D4F41),
    onSecondaryContainer = Color(0xFFD3E2D6),
    tertiaryContainer = Color(0xFF4F4330),
    onTertiaryContainer = Color(0xFFF1E3CC),
    background = Color(0xFF1C1C1A),
    onBackground = Color(0xFFE6E3DD),
    surface = Color(0xFF1C1C1A),
    onSurface = Color(0xFFE6E3DD),
    surfaceVariant = Color(0xFF2C2C29),
    onSurfaceVariant = Color(0xFFC6C3BC),
)

@Composable
fun YonisoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
