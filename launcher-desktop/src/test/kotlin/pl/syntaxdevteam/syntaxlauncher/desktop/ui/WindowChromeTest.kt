package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import java.awt.Dimension
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowChromeTest {
    private val minimum = Dimension(400, 400)
    private val original = Rectangle(200, 100, 900, 650)

    @Test fun everyResizeDirectionPreservesOppositeEdges() {
        assertEquals(Rectangle(260, 100, 840, 650),
            resizedWindowBounds(original, ResizeEdge.West, 60, 0, minimum))
        assertEquals(Rectangle(200, 100, 960, 650),
            resizedWindowBounds(original, ResizeEdge.East, 60, 0, minimum))
        assertEquals(Rectangle(200, 140, 900, 610),
            resizedWindowBounds(original, ResizeEdge.North, 0, 40, minimum))
        assertEquals(Rectangle(200, 100, 900, 690),
            resizedWindowBounds(original, ResizeEdge.South, 0, 40, minimum))
        assertEquals(Rectangle(150, 50, 950, 700),
            resizedWindowBounds(original, ResizeEdge.NorthWest, -50, -50, minimum))
        assertEquals(Rectangle(200, 100, 970, 720),
            resizedWindowBounds(original, ResizeEdge.SouthEast, 70, 70, minimum))
    }

    @Test fun resizeIsClampedToMinimumAndMayCrossVirtualScreenOrigin() {
        assertEquals(Rectangle(700, 350, 400, 400),
            resizedWindowBounds(original, ResizeEdge.NorthWest, 4000, 4000, minimum))
        assertEquals(Rectangle(200, 100, 400, 400),
            resizedWindowBounds(original, ResizeEdge.SouthEast, -4000, -4000, minimum))
        assertEquals(Rectangle(-150, -250, 1250, 1000),
            resizedWindowBounds(original, ResizeEdge.NorthWest, -350, -350, minimum))
    }
}
