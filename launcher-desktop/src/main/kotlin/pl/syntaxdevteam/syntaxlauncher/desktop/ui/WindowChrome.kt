package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import java.awt.Dimension
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import kotlin.math.roundToInt

/** Frameless-window controls are available even while the startup updater is running. */
@Composable
internal fun WindowControls(
    state: WindowState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        WindowControl("Minimize", SyntaxSymbol.Minimize) { state.isMinimized = true }
        val maximized = state.placement == WindowPlacement.Maximized
        WindowControl(if (maximized) "Restore" else "Maximize",
            if (maximized) SyntaxSymbol.Restore else SyntaxSymbol.Maximize) {
            state.placement = if (maximized) WindowPlacement.Floating else WindowPlacement.Maximized
        }
        WindowControl("Close", SyntaxSymbol.Close, destructive = true, onClick = onClose)
    }
}

@Composable
private fun WindowControl(
    label: String,
    symbol: SyntaxSymbol,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val highlight = when {
        destructive && hovered -> androidx.compose.ui.graphics.Color(0xFFC83B4E)
        hovered -> SyntaxBrand.field
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(highlight),
        interactionSource = interaction,
    ) {
        BrandIcon(symbol, description = label, size = 17.dp,
            tint = if (hovered) SyntaxBrand.heroText else SyntaxBrand.heroMuted)
    }
}

internal enum class ResizeEdge(
    val north: Boolean = false,
    val south: Boolean = false,
    val west: Boolean = false,
    val east: Boolean = false,
) {
    North(north = true), South(south = true), West(west = true), East(east = true),
    NorthWest(north = true, west = true), NorthEast(north = true, east = true),
    SouthWest(south = true, west = true), SouthEast(south = true, east = true),
}

/** Keep the opposite edge fixed and respect the minimum client area. */
internal fun resizedWindowBounds(
    original: Rectangle, edge: ResizeEdge, deltaX: Int, deltaY: Int, minimum: Dimension,
): Rectangle {
    val minW = minimum.width.coerceAtLeast(1)
    val minH = minimum.height.coerceAtLeast(1)
    val left = if (edge.west) (original.x + deltaX).coerceAtMost(original.x + original.width - minW)
        else original.x
    val right = if (edge.east) (original.x + original.width + deltaX).coerceAtLeast(original.x + minW)
        else original.x + original.width
    val top = if (edge.north) (original.y + deltaY).coerceAtMost(original.y + original.height - minH)
        else original.y
    val bottom = if (edge.south) (original.y + original.height + deltaY).coerceAtLeast(original.y + minH)
        else original.y + original.height
    return Rectangle(left, top, right - left, bottom - top)
}

/** An undecorated AWT frame does not provide consistent system resize grips on all desktops. */
@Composable
internal fun WindowResizeHandles(frame: Window, maximized: Boolean) {
    if (maximized) return
    Box(Modifier.fillMaxSize()) {
        ResizeHandle(frame, ResizeEdge.North, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(6.dp))
        ResizeHandle(frame, ResizeEdge.South, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(6.dp))
        ResizeHandle(frame, ResizeEdge.West, Modifier.align(Alignment.CenterStart).width(6.dp).fillMaxHeight())
        ResizeHandle(frame, ResizeEdge.East, Modifier.align(Alignment.CenterEnd).width(6.dp).fillMaxHeight())
        // Corners are composed last so their hit areas take precedence over the edges.
        ResizeHandle(frame, ResizeEdge.NorthWest, Modifier.align(Alignment.TopStart).size(14.dp))
        ResizeHandle(frame, ResizeEdge.NorthEast, Modifier.align(Alignment.TopEnd).size(14.dp))
        ResizeHandle(frame, ResizeEdge.SouthWest, Modifier.align(Alignment.BottomStart).size(14.dp))
        ResizeHandle(frame, ResizeEdge.SouthEast, Modifier.align(Alignment.BottomEnd).size(14.dp))
    }
}

@Composable
private fun ResizeHandle(frame: Window, edge: ResizeEdge, modifier: Modifier) {
    val density = LocalDensity.current.density
    Box(modifier.pointerInput(frame, edge, density) {
        var initialBounds = Rectangle()
        var pointerStart: Point? = null
        var accumulated = Offset.Zero
        detectDragGestures(
            onDragStart = {
                initialBounds = Rectangle(frame.bounds)
                pointerStart = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
                accumulated = Offset.Zero
            },
            onDrag = { change, amount ->
                change.consume()
                accumulated += amount
                val pointerNow = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
                // Screen coordinates from AWT match frame bounds (including multi-monitor setups).
                // Fall back to Compose pointer deltas if MouseInfo is unavailable.
                val dx = if (pointerNow != null && pointerStart != null) pointerNow.x - pointerStart!!.x
                    else (accumulated.x / density).roundToInt()
                val dy = if (pointerNow != null && pointerStart != null) pointerNow.y - pointerStart!!.y
                    else (accumulated.y / density).roundToInt()
                frame.bounds = resizedWindowBounds(initialBounds, edge, dx, dy, frame.minimumSize)
            },
        )
    })
}
