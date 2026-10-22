package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared colors and geometry for the reference's ink-blue surfaces and cyan accents. */
internal object SyntaxBrand {
    val background = Color(0xFF030F20)
    val panel = Color(0xFF08192E)
    val panelHighlight = Color(0xFF0C2444)
    val field = Color(0xFF10243B)
    val border = Color(0xFF135582)
    val fieldBorder = Color(0xFF284967)
    val cyan = Color(0xFF00D5FF)
    val markBlue = Color(0xFF779CF6)
    val blue = Color(0xFF009EF4)
    val playStart = Color(0xFF34D7F5)
    val playBorder = Color(0xFF88EFFF)
    val heroText = Color(0xFFF3F7FF)
    val heroMuted = Color(0xFFB3CBEA)
    val heroEnd = Color(0xFF041329)
    val scrim = Color(0xFF031226)
    val warning = Color(0xFFFFCE73)
    val catalogAccents = listOf(cyan, Color(0xFFC2A5FF), Color(0xFFFFCF82), Color(0xFFFFA9CE))
    val success = Color(0xFF3CE477)
    val panelRadius = 18.dp
    val controlRadius = 9.dp
    val panelGap = 16.dp
}

private val SyntaxColors = darkColorScheme(
    primary = SyntaxBrand.cyan,
    onPrimary = Color(0xFF03172B),
    primaryContainer = Color(0xFF06355A),
    onPrimaryContainer = Color(0xFFD4EEFF),
    secondary = Color(0xFF91B5DB),
    onSecondary = Color(0xFF10243B),
    secondaryContainer = Color(0xFF183553),
    onSecondaryContainer = Color(0xFFD7E7F7),
    tertiary = SyntaxBrand.cyan,
    onTertiary = Color(0xFF092534),
    background = SyntaxBrand.background,
    onBackground = SyntaxBrand.heroText,
    surface = SyntaxBrand.panel,
    onSurface = SyntaxBrand.heroText,
    surfaceVariant = SyntaxBrand.field,
    onSurfaceVariant = SyntaxBrand.heroMuted,
    outline = SyntaxBrand.fieldBorder,
    outlineVariant = Color(0xFF233D56),
    error = Color(0xFFFFA4AA),
    onError = Color(0xFF360A14),
)

private val SyntaxTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp),
)

@Composable
fun SyntaxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SyntaxColors, typography = SyntaxTypography, content = content)
}
