package dev.david.tessel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.david.tessel.ui.theme.Theme

/** The app icon's three shards, drawn live so they match the theme. Geometry copied from the launcher icon. */
@Composable
fun ShardMark(modifier: Modifier = Modifier) {
    val c = Theme.colors
    Canvas(modifier) {
        val r = size.minDimension / 2f * 0.92f
        val unit = r / 24f
        val center = Offset(size.width / 2f, size.height / 2f)
        val box = Size(r * 2, r * 2)
        fun shard(start: Float, sweep: Float, dx: Float, dy: Float, lit: Boolean) {
            drawArc(
                color = if (lit) c.accentFacet else c.accent,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = Offset(center.x - r + dx * unit, center.y - r + dy * unit),
                size = box,
            )
        }
        shard(294f, 91f, 1.24f, -0.39f, lit = false)
        shard(25f, 146f, -0.23f, 1.28f, lit = false)
        shard(171f, 123f, -0.88f, -0.96f, lit = true)
    }
}
