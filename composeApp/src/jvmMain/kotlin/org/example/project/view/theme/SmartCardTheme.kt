package org.example.project.view.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Bảng màu hiện đại (Modern Emerald/Sage Theme)
private val PrimaryGreen = Color(0xFF2E7D32)
private val SecondaryGreen = Color(0xFF43A047)
private val SurfaceColor = Color(0xFFF5F7F5) // Màu nền hơi xám xanh nhẹ
private val CardBackground = Color(0xFFFFFFFF)

private val AppColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F5E9),
    onPrimaryContainer = Color(0xFF003300),

    secondary = SecondaryGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E6C9),
    onSecondaryContainer = Color(0xFF003300),

    background = SurfaceColor,
    onBackground = Color(0xFF1C1C1E),

    surface = CardBackground,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF424242),

    error = Color(0xFFBA1A1A),
    onError = Color.White
)

@Composable
fun SmartCardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp), // Bo góc mềm mại hơn
            large = RoundedCornerShape(20.dp)
        ),
        content = content
    )
}