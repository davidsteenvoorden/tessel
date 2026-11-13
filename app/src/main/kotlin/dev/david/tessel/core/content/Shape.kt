package dev.david.tessel.core.content

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The outline of a body as vertices around its centre, at unit radius,
 * pointing along +x. Every outline can be seen whole from its centre, so it
 * splits into a fan of triangles: those are the facets it is drawn with and
 * the shards it breaks into. A circle has no vertices and uses its own split.
 */
enum class Shape(val points: FloatArray) {
    CIRCLE(FloatArray(0)),
    SQUARE(floatArrayOf(0.86f, -0.86f, 0.86f, 0.86f, -0.86f, 0.86f, -0.86f, -0.86f)),
    TRIANGLE(floatArrayOf(1.15f, 0f, -0.7f, 0.82f, -0.7f, -0.82f)),

    /** An arrowhead with a notch at the back, for things that charge. */
    DART(floatArrayOf(1.3f, 0f, -0.8f, 0.78f, -0.35f, 0f, -0.8f, -0.78f)),
    DIAMOND(floatArrayOf(1.2f, 0f, 0f, 0.75f, -1.2f, 0f, 0f, -0.75f)),
    PENTAGON(regular(5, 1f)),
    HEXAGON(regular(6, 1f)),
    OCTAGON(regular(8, 1f)),

    /** Four long points, for turrets that spin. */
    CROSS(star(4, 1.15f, 0.42f)),

    /** Six short points. */
    STAR(star(6, 1.1f, 0.62f)),
    ;

    val sides get() = points.size / 2
}

private fun regular(n: Int, r: Float) = FloatArray(n * 2) { i ->
    val a = (i / 2) * 2 * PI / n
    (if (i % 2 == 0) cos(a) else sin(a)).toFloat() * r
}

private fun star(points: Int, outer: Float, inner: Float) = FloatArray(points * 4) { i ->
    val k = i / 2
    val a = k * PI / points
    val r = if (k % 2 == 0) outer else inner
    (if (i % 2 == 0) cos(a) else sin(a)).toFloat() * r
}
