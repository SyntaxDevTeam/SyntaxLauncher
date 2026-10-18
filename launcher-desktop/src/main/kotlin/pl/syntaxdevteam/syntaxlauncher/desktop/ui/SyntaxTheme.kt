package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SyntaxColors = darkColorScheme(
    primary = Color(0xFF83E4C4),
    onPrimary = Color(0xFF09251D),
    primaryContainer = Color(0xFF194039),
    onPrimaryContainer = Color(0xFFD1F8EA),
    secondary = Color(0xFF9DAFCE),
    background = Color(0xFF0D1118),
    onBackground = Color(0xFFF2F5F7),
    surface = Color(0xFF161C25),
    onSurface = Color(0xFFF2F5F7),
    surfaceVariant = Color(0xFF222B35),
    onSurfaceVariant = Color(0xFF9DA9B6),
    outline = Color(0xFF3A4650),
    error = Color(0xFFFFAAA8),
)

@Composable
fun SyntaxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SyntaxColors,
        typography = Typography(),
        content = content,
    )
}
