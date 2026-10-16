package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SyntaxColors = darkColorScheme(
    primary = Color(0xFF74E0D1),
    onPrimary = Color(0xFF06201C),
    primaryContainer = Color(0xFF153C36),
    onPrimaryContainer = Color(0xFFB5F3EA),
    secondary = Color(0xFFB6C8FF),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE7EDF5),
    surface = Color(0xFF121821),
    onSurface = Color(0xFFE7EDF5),
    surfaceVariant = Color(0xFF1A2330),
    onSurfaceVariant = Color(0xFFAEB9C8),
    error = Color(0xFFFFB4AB),
)

@Composable
fun SyntaxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SyntaxColors,
        content = content,
    )
}
