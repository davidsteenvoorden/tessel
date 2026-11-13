package dev.david.tessel.design

/**
 * ARGB values shared by the canvas renderer and the Compose theme.
 * Every pair used for text is checked by tools/contrast.py; change values there first.
 */
class Palette(
    val dark: Boolean,
    val ground: Int,
    val surface: Int,
    val raised: Int,
    val ink: Int,
    val ink2: Int,
    val ink3: Int,
    val danger: Int,
    val coin: Int,
    private val zones: IntArray,
) {
    /** Accent for zone 1..8. Hues between red and yellow are kept free for danger and coin. */
    fun zone(number: Int): Int = zones[(number - 1).coerceIn(0, zones.lastIndex)]

    /**
     * An enemy's own colour. Every kind shares one lightness and chroma per
     * appearance (the same recipe as the zone accents) and differs by hue, so
     * they read as one family and none outshouts the rest.
     */
    fun enemy(hue: Int, tone: Int): Int =
        if (dark) oklch(0.78f + 0.06f * tone, 0.13f, hue.toFloat()) else oklch(0.52f + 0.06f * tone, 0.14f, hue.toFloat())

    companion object {
        val Dark = Palette(
            dark = true,
            ground = 0xFF14171B.toInt(),
            surface = 0xFF1D2127.toInt(),
            raised = 0xFF262B32.toInt(),
            ink = 0xFFF2F4F6.toInt(),
            ink2 = 0xFFA9B0BA.toInt(),
            ink3 = 0xFF8A929C.toInt(),
            danger = 0xFFFF6B4A.toInt(),
            coin = 0xFFF2C14E.toInt(),
            zones = intArrayOf(
                0xFF2BD2C2.toInt(), 0xFF76CF8A.toInt(), 0xFFBDBE53.toInt(), 0xFF41C8F6.toInt(),
                0xFF8EB5FF.toInt(), 0xFFCBA1FA.toInt(), 0xFFF294CA.toInt(), 0xFFAEB9C4.toInt(),
            ),
        )

        val Light = Palette(
            dark = false,
            ground = 0xFFECEEF0.toInt(),
            surface = 0xFFF8F9FA.toInt(),
            raised = 0xFFFFFFFF.toInt(),
            ink = 0xFF16191D.toInt(),
            ink2 = 0xFF4C535C.toInt(),
            ink3 = 0xFF6A727C.toInt(),
            danger = 0xFFC2381B.toInt(),
            coin = 0xFF8F5F00.toInt(),
            zones = intArrayOf(
                0xFF007B6E.toInt(), 0xFF007834.toInt(), 0xFF696800.toInt(), 0xFF00719E.toInt(),
                0xFF3B5EB2.toInt(), 0xFF764AA2.toInt(), 0xFF983C75.toInt(), 0xFF5B646F.toInt(),
            ),
        )
    }
}

/**
 * OKLCH to an opaque ARGB int. Chroma is lowered until the colour fits in
 * sRGB, which keeps the hue and lightness honest at the gamut edge.
 */
fun oklch(l: Float, c: Float, hueDegrees: Float): Int {
    val h = Math.toRadians(hueDegrees.toDouble())
    var chroma = c.toDouble()
    while (true) {
        val a = chroma * Math.cos(h)
        val b = chroma * Math.sin(h)
        val l1 = cube(l + 0.3963377774 * a + 0.2158037573 * b)
        val m1 = cube(l - 0.1055613458 * a - 0.0638541728 * b)
        val s1 = cube(l - 0.0894841775 * a - 1.2914855480 * b)
        val r = 4.0767416621 * l1 - 3.3077115913 * m1 + 0.2309699292 * s1
        val g = -1.2684380046 * l1 + 2.6097574011 * m1 - 0.3413193965 * s1
        val bl = -0.0041960863 * l1 - 0.7034186147 * m1 + 1.7076147010 * s1
        val fits = r in 0.0..1.0 && g in 0.0..1.0 && bl in 0.0..1.0
        if (fits || chroma <= 0.0) {
            return (0xFF shl 24) or (encode(r) shl 16) or (encode(g) shl 8) or encode(bl)
        }
        chroma -= 0.004
    }
}

private fun cube(x: Double) = x * x * x

private fun encode(linear: Double): Int {
    val x = linear.coerceIn(0.0, 1.0)
    val v = if (x <= 0.0031308) 12.92 * x else 1.055 * Math.pow(x, 1 / 2.4) - 0.055
    return (v * 255 + 0.5).toInt().coerceIn(0, 255)
}

/** Straight per-channel blend, t = 0 gives a, t = 1 gives b. */
fun mix(a: Int, b: Int, t: Float): Int {
    var out = 0
    for (shift in intArrayOf(24, 16, 8, 0)) {
        val from = a ushr shift and 0xFF
        val to = b ushr shift and 0xFF
        out = out or ((from + (to - from) * t + 0.5f).toInt() shl shift)
    }
    return out
}

const val WHITE = 0xFFFFFFFF.toInt()
