package dev.david.tessel.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.david.tessel.ui.theme.Corner
import dev.david.tessel.ui.theme.MinTarget
import dev.david.tessel.ui.theme.Space
import dev.david.tessel.ui.theme.Springs
import dev.david.tessel.ui.theme.Theme
import dev.david.tessel.ui.theme.Type

@Composable
fun Text(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = align),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Shrinks a touch the moment it lands, not when it lifts. Shows a ring when a controller has it in focus. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, enabled: Boolean = true, corner: Dp = Corner.button): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, Springs.press(), label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.focusRing(source, corner)
}

/**
 * A ring around whatever a controller or keyboard has selected. Touch never
 * focuses anything, so on a phone in the hand this never shows.
 */
@Composable
fun Modifier.focusRing(source: MutableInteractionSource, corner: Dp = Corner.button): Modifier {
    val focused by source.collectIsFocusedAsState()
    val ring = Theme.colors.ink
    return drawWithContent {
        drawContent()
        if (focused) {
            // Drawn just inside the edge, so a parent that clips (a grouped list) cannot cut it off.
            val w = 2.dp.toPx()
            drawRoundRect(
                color = ring,
                topLeft = Offset(w / 2, w / 2),
                size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius((corner.toPx() - w / 2).coerceAtLeast(0f)),
                style = Stroke(w),
            )
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Theme.colors
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(source, enabled)
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(Corner.button))
            .background(if (enabled) c.ink else c.track)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.x5),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, Type.headline, if (enabled) c.ground else c.ink3, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Theme.colors.ink,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Theme.colors
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(source, enabled)
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(Corner.button))
            .background(c.track)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.x5),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(text, Type.headline, if (enabled) tint else c.ink3, maxLines = 1)
        if (trailing != null) {
            Spacer(Modifier.width(Space.x2))
            trailing()
        }
    }
}

/** A list row that leads somewhere. Passing no [onClick] shows it greyed out. */
@Composable
fun NavRow(title: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier, detail: String? = null, value: String? = null) {
    val c = Theme.colors
    val enabled = onClick != null
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .focusRing(source, corner = 0.dp)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button) { onClick?.invoke() }
            .padding(horizontal = Space.x4, vertical = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, Type.body, if (enabled) c.ink else c.ink3)
            if (detail != null) Text(detail, Type.footnote, c.ink3)
        }
        if (value != null) {
            Text(value, Type.body, c.ink2)
            Spacer(Modifier.width(Space.x2))
        }
        if (enabled) Chevron(c.ink3)
    }
}

@Composable
fun Chevron(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 8.dp, height = 14.dp)) {
        val stroke = 2.dp.toPx()
        val w = size.width - stroke
        val h = size.height
        drawLine(color, Offset(stroke / 2, stroke / 2), Offset(w, h / 2), stroke, StrokeCap.Round)
        drawLine(color, Offset(w, h / 2), Offset(stroke / 2, h - stroke / 2), stroke, StrokeCap.Round)
    }
}

/** A back control on the leading edge: chevron and the name of where it goes. */
@Composable
fun BackButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Theme.colors
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(source)
            .heightIn(min = MinTarget)
            .clip(RoundedCornerShape(Corner.chip))
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chevron(c.ink, Modifier.graphicsLayer { rotationZ = 180f })
        Spacer(Modifier.width(Space.x2))
        Text(label, Type.body, c.ink)
    }
}

/** Two to four mutually exclusive choices with a pill that slides to the pick. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Theme.colors
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(Corner.chip))
            .background(c.track)
            .padding(3.dp),
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * selected, Springs.ui(), label = "segment")
        Box(
            Modifier
                .offset(x = x)
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(Corner.chip - 3.dp))
                .background(c.pill),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val chosen = i == selected
                val source = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .focusRing(source, Corner.chip - 3.dp)
                        .selectable(
                            selected = chosen,
                            interactionSource = source,
                            indication = null,
                            role = Role.Tab,
                        ) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        Type.subhead.copy(fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium),
                        if (chosen) c.ink else c.ink2,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A whole row that flips a setting; the switch on the trailing edge shows the state. */
@Composable
fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, detail: String? = null, enabled: Boolean = true) {
    val c = Theme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChange,
            )
            .padding(horizontal = Space.x4, vertical = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, Type.body, if (enabled) c.ink else c.ink3)
            if (detail != null) Text(detail, Type.footnote, c.ink3)
        }
        Spacer(Modifier.width(Space.x3))
        Switch(checked && enabled)
    }
}

@Composable
private fun Switch(checked: Boolean) {
    val c = Theme.colors
    val knob by animateDpAsState(if (checked) 22.dp else 2.dp, Springs.ui(), label = "knob")
    val track by animateColorAsState(if (checked) c.ink else c.track, Springs.ui(), label = "track")
    Box(
        Modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(CircleShape)
            .background(track),
    ) {
        Box(
            Modifier
                .offset(x = knob, y = 2.dp)
                .size(27.dp)
                .clip(CircleShape)
                .background(if (checked) c.ground else c.ink2),
        )
    }
}

/** Two bars in a 48 dp target. */
@Composable
fun PauseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Theme.colors
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(source)
            .size(MinTarget)
            .clip(CircleShape)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Pause" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val bar = size.width * 0.28f
            val radius = CornerRadius(1.5.dp.toPx())
            drawRoundRect(c.ink2, Offset(size.width * 0.14f, 0f), Size(bar, size.height), radius)
            drawRoundRect(c.ink2, Offset(size.width * 0.58f, 0f), Size(bar, size.height), radius)
        }
    }
}

/** A gold dot and a number, the same pairing the in-game counter uses. */
@Composable
fun CoinAmount(amount: Int, modifier: Modifier = Modifier, color: Color = Theme.colors.ink, style: TextStyle = Type.headline) {
    val c = Theme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c.coin))
        Spacer(Modifier.width(6.dp))
        Text(amount.toString(), style.merge(Type.numbers), color, maxLines = 1)
    }
}

/**
 * Filled pips for levels owned, open ones for what is left. Past ten levels
 * the pips would run off the tile, so it becomes a short bar and a number.
 */
@Composable
fun LevelPips(level: Int, max: Int, modifier: Modifier = Modifier) {
    val c = Theme.colors
    if (max > 10) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 44.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(c.track)) {
                Box(
                    Modifier
                        .size(width = 44.dp * (level / max.toFloat()), height = 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(c.ink),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(level.toString(), Type.footnote.merge(Type.numbers), c.ink2)
        }
        return
    }
    Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
        repeat(max) { k ->
            Box(
                Modifier
                    .size(width = 6.dp, height = 6.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (k < level) c.ink else c.track),
            )
        }
    }
}

/** A group of rows on a panel, the way grouped settings lists look on Apple platforms. */
@Composable
fun Group(title: String?, modifier: Modifier = Modifier, footer: String? = null, content: @Composable () -> Unit) {
    val c = Theme.colors
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(title, Type.footnote.copy(fontWeight = FontWeight.SemiBold), c.ink2, Modifier.padding(start = Space.x4, bottom = Space.x2))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Corner.button))
                .background(c.surface),
        ) { content() }
        if (footer != null) {
            Text(footer, Type.footnote, c.ink3, Modifier.padding(start = Space.x4, end = Space.x4, top = Space.x2))
        }
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(start = Space.x4)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Theme.colors.line),
    )
}
