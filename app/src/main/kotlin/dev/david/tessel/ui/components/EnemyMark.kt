package dev.david.tessel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.Shape
import dev.david.tessel.design.Palette
import dev.david.tessel.ui.theme.Theme
import kotlin.math.max

/**
 * An enemy's outline in its own colour, for the end screen and the logbook.
 * One not met yet is only a grey outline. Each shape is centred and scaled to
 * fill the box, so a dart and a cross read the same size.
 */
@Composable
fun EnemyMark(type: Int, modifier: Modifier = Modifier, size: Dp = 40.dp, known: Boolean = true) {
    val def = Bestiary.of(type)
    val c = Theme.colors
    val palette = if (c.dark) Palette.Dark else Palette.Light
    val body = Color(palette.enemy(def.hue, def.tone))
    val outline = c.ink3
    Canvas(modifier.size(size)) {
        val pts = def.shape.points
        val stroke = 1.5.dp.toPx()
        val half = this.size.minDimension / 2f - stroke
        if (def.shape == Shape.CIRCLE) {
            if (known) {
                drawCircle(body, half, Offset(center.x, center.y))
            } else {
                drawCircle(outline, half, Offset(center.x, center.y), style = Stroke(stroke))
            }
            return@Canvas
        }
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (k in 0 until pts.size / 2) {
            minX = minOf(minX, pts[k * 2])
            maxX = maxOf(maxX, pts[k * 2])
            minY = minOf(minY, pts[k * 2 + 1])
            maxY = maxOf(maxY, pts[k * 2 + 1])
        }
        val scale = half / max(maxX - minX, maxY - minY) * 2f
        val ox = (minX + maxX) / 2f
        val oy = (minY + maxY) / 2f
        val path = Path()
        for (k in 0 until pts.size / 2) {
            val x = center.x + (pts[k * 2] - ox) * scale
            val y = center.y + (pts[k * 2 + 1] - oy) * scale
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        if (known) drawPath(path, body) else drawPath(path, outline, style = Stroke(stroke))
    }
}
