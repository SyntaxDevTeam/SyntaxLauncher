package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Keep packaged branding on the classpath while using the current Compose bitmap decoder. */
@Composable
internal fun rememberBrandingPainter(path: String): Painter = remember(path) {
    val bytes = checkNotNull(BrandingResources::class.java.getResourceAsStream("/$path")) {
        "Missing packaged branding resource: $path"
    }.use { it.readAllBytes() }
    BitmapPainter(bytes.decodeToImageBitmap())
}

private object BrandingResources
