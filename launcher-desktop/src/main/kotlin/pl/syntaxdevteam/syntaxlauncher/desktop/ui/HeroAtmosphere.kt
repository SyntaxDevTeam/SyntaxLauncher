package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.sin
import kotlin.random.Random

/**
 * A lightweight, entirely local voxel-night backdrop.
 * No network, video, looping animations, image decoding or effects pipeline.
 * Text and the product mark are separate composables above this canvas.
 */
@Composable
internal fun HeroAtmosphere(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(
            Color(0xFF0E325C), Color(0xFF0A2548), Color(0xFF07172D),
        )))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x5540BFFF), Color(0x0021559C)),
                center = Offset(w * 0.76f, h * 0.31f),
                radius = h * 0.42f,
            ),
            radius = h * 0.42f,
            center = Offset(w * 0.76f, h * 0.31f),
        )

        // A few tiny, static stars: recognizable at large size, unobtrusive at small size.
        val stars = Random(2031)
        repeat(65) {
            val x = w * stars.nextFloat()
            val y = h * (0.06f + stars.nextFloat() * 0.58f)
            val side = 1f + (stars.nextInt(3)).toFloat()
            drawRect(
                color = if (it % 6 == 0) Color(0x997FCBFA) else Color(0x5573ACDA),
                topLeft = Offset(x, y),
                size = Size(side, side),
            )
        }

        // Angular mountains give the atmosphere its Minecraft/voxel character.
        drawRidge(w, h, baseline = 0.69f, variation = 0.10f, phase = 1.5f, color = Color(0xFF102D51))
        drawRidge(w, h, baseline = 0.79f, variation = 0.12f, phase = 3.2f, color = Color(0xFF0A2341))
        drawRidge(w, h, baseline = 0.90f, variation = 0.08f, phase = 0.6f, color = Color(0xFF091B33))

        // Repeating blocky trees sit behind the logo; no detailed noise behind body text.
        repeat(20) { i ->
            val x = w * (i + 0.35f) / 20f
            val y = h * (0.80f + 0.09f * sin(i * 2.4f).toFloat())
            val scale = (h / 420f) * (0.7f + 0.38f * (i % 4) / 3f)
            drawBlockTree(x, y, scale, if (i % 3 == 0) Color(0xFF112D43) else Color(0xFF0C263D))
        }
        drawRect(
            brush = Brush.verticalGradient(
                0.62f to Color.Transparent,
                1f to Color(0xFF08152A),
            ),
        )
    }
}

private fun DrawScope.drawRidge(
    w: Float, h: Float, baseline: Float, variation: Float, phase: Float, color: Color,
) {
    val path = Path().apply {
        moveTo(0f, h)
        lineTo(0f, h * baseline)
        for (i in 1..34) {
            val x = i * w / 34f
            val noise = sin(i * 0.87f + phase).toFloat() * 0.7f +
                sin(i * 1.93f + phase).toFloat() * 0.3f
            val step = ((baseline - variation * noise) * h / 9f).toInt() * (h / 9f)
            lineTo(x, step.coerceIn(h * 0.42f, h))
        }
        lineTo(w, h)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawBlockTree(x: Float, bottom: Float, factor: Float, color: Color) {
    val trunk = 4f * factor
    drawRect(Color(0xFF092035), Offset(x - trunk / 2f, bottom - 27f * factor), Size(trunk, 27f * factor))
    val widths = floatArrayOf(15f, 26f, 36f, 27f)
    widths.forEachIndexed { index, width ->
        val top = bottom - (48f - index * 9f) * factor
        drawRect(color, Offset(x - width * factor / 2f, top), Size(width * factor, 10f * factor))
    }
}
