package dev.david.tessel.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.david.tessel.design.Palette
import dev.david.tessel.design.WHITE
import dev.david.tessel.design.mix
import kotlin.math.PI

@Immutable
class Colors(
    val dark: Boolean,
    val ground: Color,
    val surface: Color,
    val raised: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val danger: Color,
    val coin: Color,
    /** Background of segmented controls and switches that are off. */
    val track: Color,
    /** The selected segment. */
    val pill: Color,
    /** Hairlines between rows. */
    val line: Color,
    /** The zone 1 teal, used only for the mark on the home screen. */
    val accent: Color,
    val accentFacet: Color,
)

fun Palette.toColors(): Colors {
    val zone = zone(1)
    return Colors(
        dark = dark,
        ground = Color(ground),
        surface = Color(surface),
        raised = Color(raised),
        ink = Color(ink),
        ink2 = Color(ink2),
        ink3 = Color(ink3),
        danger = Color(danger),
        coin = Color(coin),
        track = Color(mix(raised, ink, if (dark) 0.10f else 0.09f)),
        pill = Color(if (dark) mix(raised, ink, 0.26f) else raised),
        line = Color(mix(surface, ink, 0.12f)),
        accent = Color(zone),
        accentFacet = Color(if (dark) mix(zone, WHITE, 0.32f) else mix(zone, ground, 0.35f)),
    )
}

object Space {
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp
    val x8 = 32.dp
}

object Corner {
    val chip = 10.dp
    val button = 14.dp
    val sheet = 24.dp
}

val MinTarget = 48.dp

/** Sizes and leading follow the HIG text styles; tracking tightens as the size grows. */
object Type {
    val display = TextStyle(fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-0.03).em, fontWeight = FontWeight.Bold)
    val largeTitle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.02).em, fontWeight = FontWeight.Bold)
    val title1 = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.015).em, fontWeight = FontWeight.Bold)
    val title2 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em, fontWeight = FontWeight.SemiBold)
    val title3 = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.005).em, fontWeight = FontWeight.SemiBold)
    val headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp)
    val callout = TextStyle(fontSize = 16.sp, lineHeight = 21.sp)
    val subhead = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.01.em)

    /** Digits that do not jiggle as they count. */
    val numbers = TextStyle(fontFeatureSettings = "tnum")
}

/**
 * Springs are described the way a designer thinks about them: how long the
 * move takes to respond, and how much it overshoots (1 = none).
 */
object Springs {
    fun <T> ui(): SpringSpec<T> = responding(0.35f, 1f)
    fun <T> sheet(): SpringSpec<T> = responding(0.30f, 0.85f)
    fun <T> press(): SpringSpec<T> = responding(0.12f, 1f)
    fun <T> slow(): SpringSpec<T> = responding(0.45f, 1f)

    /** Only after a gesture that carried momentum; overshoot anywhere else looks wrong. */
    fun <T> flick(): SpringSpec<T> = responding(0.35f, 0.8f)

    private fun <T> responding(seconds: Float, damping: Float): SpringSpec<T> {
        val omega = 2f * PI.toFloat() / seconds
        return spring(dampingRatio = damping, stiffness = omega * omega)
    }
}
