package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.syntaxlauncher.core.account.PlayerSkin
import pl.syntaxdevteam.syntaxlauncher.core.account.PlayerSkinCatalog
import pl.syntaxdevteam.syntaxlauncher.core.account.SavedAccount
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

@Composable
internal fun PlayerPortrait(player: SavedAccount, catalog: PlayerSkinCatalog, height: Dp) {
    val id = player.account.uuid
    val description = LocalStrings.current.text("players.portrait", player.account.username)
    var portrait by remember(id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(id, player.microsoft, catalog) {
        if (!player.microsoft) return@LaunchedEffect
        try {
            portrait = withContext(Dispatchers.IO) { catalog.load(id)?.let(::renderSkin) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Skins are optional artwork and account selection is available offline.
        }
    }
    Canvas(Modifier.size(height / 2, height).testTag("player-portrait")
        .semantics { contentDescription = description }) {
        drawImage(portrait ?: defaultPortrait, dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.None)
    }
}

private fun renderSkin(skin: PlayerSkin): ImageBitmap {
    val texture = ImageIO.read(skin.png.inputStream())
    val legacy = texture.height == 32
    val arm = if (skin.slim && !legacy) 3 else 4
    val portrait = BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB)
    val graphics = portrait.createGraphics()
    try {
        fun part(x: Int, y: Int, w: Int, h: Int, u: Int, v: Int, mirror: Boolean = false) {
            graphics.drawImage(texture, x, y, x + w, y + h,
                if (mirror) u + w else u, v, if (mirror) u else u + w, v + h, null)
        }
        part(4, 0, 8, 8, 8, 8)
        part(4, 8, 8, 12, 20, 20)
        part(4 - arm, 8, arm, 12, 44, 20)
        part(12, 8, arm, 12, if (legacy) 44 else 36, if (legacy) 20 else 52, legacy)
        part(4, 20, 4, 12, 4, 20)
        part(8, 20, 4, 12, if (legacy) 4 else 20, if (legacy) 20 else 52, legacy)
        if (!legacy) {
            part(4, 8, 8, 12, 20, 36)
            part(4 - arm, 8, arm, 12, 44, 36)
            part(12, 8, arm, 12, 52, 52)
            part(4, 20, 4, 12, 4, 36)
            part(8, 20, 4, 12, 4, 52)
        }
        val hasLegacyHat = !legacy || (0 until 64).any { x ->
            (0 until 16).any { y -> x >= 32 && texture.getRGB(x, y) ushr 24 == 0 }
        }
        if (hasLegacyHat) part(4, 0, 8, 8, 40, 8)
    } finally {
        graphics.dispose()
    }
    return portrait.toComposeImageBitmap()
}

private val defaultPortrait by lazy {
    val pixels = BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB)
    val graphics = pixels.createGraphics()
    try {
        fun block(color: Int, x: Int, y: Int, w: Int, h: Int) {
            graphics.color = java.awt.Color(color)
            graphics.fillRect(x, y, w, h)
        }
        block(0xBE8466, 4, 0, 8, 8)
        block(0x302438, 4, 0, 8, 2)
        block(0x302438, 4, 2, 2, 2)
        block(0x302438, 11, 2, 1, 2)
        block(0xDEE9F3, 5, 4, 2, 1)
        block(0xDEE9F3, 9, 4, 2, 1)
        block(0x233454, 6, 4, 1, 1)
        block(0x233454, 9, 4, 1, 1)
        block(0x925A4B, 7, 6, 2, 1)
        block(0x214D91, 0, 8, 16, 12)
        block(0x386EC2, 4, 8, 8, 11)
        block(0x62C9EC, 7, 10, 2, 3)
        block(0xBE8466, 0, 17, 4, 3)
        block(0xA26A52, 12, 17, 4, 3)
        block(0x22334E, 4, 20, 8, 10)
        block(0x324A67, 4, 20, 3, 9)
        block(0x152033, 4, 30, 8, 2)
    } finally {
        graphics.dispose()
    }
    pixels.toComposeImageBitmap()
}
