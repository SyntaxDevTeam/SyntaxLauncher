package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Restrained navy/cobalt/cyan palette inspired by the launcher logo. */
internal object SyntaxBrand {
    val heroStart = Color(0xFF102B50)
    val heroMiddle = Color(0xFF0C203C)
    val heroEnd = Color(0xFF0A1528)
    val heroGlow = Color(0xFF42BAF5)
    val heroText = Color(0xFFF1F8FF)
    val heroMuted = Color(0xFFB5CBE3)
}

private val SyntaxColors = darkColorScheme(
    primary = Color(0xFF48BEF5),
    onPrimary = Color(0xFF061C30),
    primaryContainer = Color(0xFF163C61),
    onPrimaryContainer = Color(0xFFD4EEFF),
    secondary = Color(0xFF91B5DB),
    onSecondary = Color(0xFF10243B),
    secondaryContainer = Color(0xFF253E5D),
    onSecondaryContainer = Color(0xFFD7E7F7),
    tertiary = Color(0xFF79DDF7),
    onTertiary = Color(0xFF092534),
    background = Color(0xFF0A101C),
    onBackground = Color(0xFFF0F6FF),
    surface = Color(0xFF131F31),
    onSurface = Color(0xFFF0F6FF),
    surfaceVariant = Color(0xFF203149),
    onSurfaceVariant = Color(0xFFACBFD6),
    outline = Color(0xFF49617D),
    outlineVariant = Color(0xFF293D56),
    error = Color(0xFFFFA4AA),
    onError = Color(0xFF360A14),
)

@Composable
fun SyntaxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SyntaxColors,
        typography = Typography(),
        content = content,
    )
}
