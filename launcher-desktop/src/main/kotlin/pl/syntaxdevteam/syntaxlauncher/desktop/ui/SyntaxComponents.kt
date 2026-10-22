package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small native vector set: crisp at every display scale, without a new icon dependency. */
internal enum class SyntaxSymbol(val path: String) {
    Texture("M3 3H21V21H3Z M3 9H21 M3 15H21 M9 3V21 M15 3V21"),
    Sun("M12 8A4 4 0 1 1 12 16A4 4 0 1 1 12 8 M12 1V4 M12 20V23 M1 12H4 M20 12H23 M4 4L6 6 M18 18L20 20 M4 20L6 18 M18 6L20 4"),
    Settings("M9 3H15L16 6L19 7L22 10V14L19 17L16 18L15 21H9L8 18L5 17L2 14V10L5 7L8 6Z M16 12A4 4 0 1 1 8 12A4 4 0 1 1 16 12"),
    Home("M3 11L12 3L21 11 M5 10V21H10V15H14V21H19V10"),
    Instances("M4 6C4 2 20 2 20 6C20 10 4 10 4 6 M4 6V18C4 22 20 22 20 18V6 M4 12C4 16 20 16 20 12"),
    Cube("M12 2L22 7V17L12 22L2 17V7Z M2 7L12 12L22 7 M12 12V22 M7 4.5L17 9.5"),
    Setup("M2 6H7 M11 6H22 M2 12H14 M18 12H22 M2 18H5 M9 18H22 M7 3V9H11V3Z M14 9V15H18V9Z M5 15V21H9V15Z"),
    Player("M7 6C7 0 17 0 17 6C17 12 7 12 7 6 M3 22V19C3 11 21 11 21 19V22"),
    Memory("M4 5H20V19H4Z M8 9H11V15H8Z M14 9H17V15H14Z M8 2V5 M12 2V5 M16 2V5 M8 19V22 M12 19V22 M16 19V22 M1 9H4 M1 15H4 M20 9H23 M20 15H23"),
    Gamepad("M8 5L10 3H14L16 5H18C20 5 21 8 22 17C22 21 19 21 16 17H8C5 21 2 21 2 17C3 8 4 5 6 5Z M6 10H12 M9 7V13 M16 10H16.1 M18 13H18.1"),
    ChevronDown("M6 9L12 15L18 9"),
    ChevronRight("M9 6L15 12L9 18"),
    Play("M7 3L21 12L7 21Z"),
    Minimize("M5 18H19"),
    Maximize("M5 5H19V19H5Z"),
    Restore("M8 4H20V16 M4 8H16V20H4Z"),
    Close("M5 5L19 19 M19 5L5 19"),
    Info("M12 16V11 M12 7H12.01 M22 12C22 25.3 2 25.3 2 12C2 -1.3 22 -1.3 22 12Z"),
}

@Composable
internal fun BrandIcon(
    symbol: SyntaxSymbol,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 22.dp,
    description: String? = null,
) {
    val vector = remember(symbol) {
        ImageVector.Builder(symbol.name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = PathParser().parsePathString(symbol.path).toNodes(),
                fill = if (symbol == SyntaxSymbol.Play) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.65f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
    }
    Icon(vector, contentDescription = description, modifier = modifier.size(size), tint = tint)
}

@Composable
internal fun SectionLabel(title: String, symbol: SyntaxSymbol, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BrandIcon(symbol)
        Text(title, color = SyntaxBrand.cyan, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
    }
}

@Composable
internal fun SelectorButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    symbol: SyntaxSymbol? = null,
    trailing: SyntaxSymbol = SyntaxSymbol.ChevronDown,
) {
    val shape = RoundedCornerShape(SyntaxBrand.controlRadius)
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 40.dp)
            .background(SyntaxBrand.field.copy(alpha = if (enabled) 1f else 0.4f), shape),
        shape = shape, border = BorderStroke(1.dp, SyntaxBrand.fieldBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = SyntaxBrand.heroText),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 9.dp),
    ) {
        if (symbol != null) {
            BrandIcon(symbol, tint = if (enabled) SyntaxBrand.heroMuted else SyntaxBrand.heroMuted.copy(alpha = 0.38f))
            Box(Modifier.width(12.dp))
        }
        Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium)
        BrandIcon(trailing, tint = if (enabled) SyntaxBrand.heroMuted else SyntaxBrand.heroMuted.copy(alpha = 0.38f),
            size = 18.dp)
    }
}

@Composable
internal fun ChannelButton(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(SyntaxBrand.controlRadius)
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = modifier.semantics { this.selected = selected }.heightIn(min = 38.dp).background(
            if (selected && enabled) Brush.verticalGradient(listOf(SyntaxBrand.blue, Color(0xFF00559E)))
            else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)), shape),
        shape = shape,
        border = BorderStroke(1.dp, if (selected && enabled) SyntaxBrand.cyan else SyntaxBrand.fieldBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = SyntaxBrand.heroText),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.bodySmall)
    }
}
