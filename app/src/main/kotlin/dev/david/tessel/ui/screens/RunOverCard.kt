package dev.david.tessel.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.sim.Cause
import dev.david.tessel.platform.game.RunSummary
import dev.david.tessel.ui.components.EnemyMark
import dev.david.tessel.ui.components.PrimaryButton
import dev.david.tessel.ui.components.SecondaryButton
import dev.david.tessel.ui.components.Text
import dev.david.tessel.ui.theme.Corner
import dev.david.tessel.ui.theme.Space
import dev.david.tessel.ui.theme.Springs
import dev.david.tessel.ui.theme.Theme
import dev.david.tessel.ui.theme.Type

/**
 * The last screen of a run: what ended it, the score and where it came from,
 * and the run in numbers.
 */
@Composable
fun RunOverCard(visible: Boolean, summary: RunSummary?, onAgain: () -> Unit, onHome: () -> Unit) {
    val c = Theme.colors
    val shown = remember { Animatable(0f) }
    var present by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            present = true
            shown.animateTo(1f, Springs.ui())
        } else {
            shown.animateTo(0f, Springs.ui())
            present = false
        }
    }
    if (!present || summary == null) return

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown.value }
            .background(c.ground.copy(alpha = 0.72f))
            // Swallows taps so a thumb still on the screen does not reach the game below.
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .graphicsLayer {
                    val s = 0.96f + 0.04f * shown.value
                    scaleX = s
                    scaleY = s
                }
                .padding(horizontal = Space.x6)
                .widthIn(max = 840.dp)
                .clip(RoundedCornerShape(Corner.sheet))
                .background(c.surface)
                .padding(Space.x6),
            horizontalArrangement = Arrangement.spacedBy(Space.x8),
        ) {
            // What ended it and what it was worth, with the way out.
            Column(Modifier.width(310.dp)) {
                Text("Run over", Type.title1, c.ink)
                val sub = summary.difficulty.label + if (summary.newBest) "  ·  New best" else ""
                Text(sub, Type.subhead, c.ink2)
                Spacer(Modifier.height(Space.x4))
                KilledBy(summary.killedBy)
                ScoreBlock(summary)
                Spacer(Modifier.weight(1f, fill = false))
                Spacer(Modifier.height(Space.x5))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    SecondaryButton("Home", onHome, Modifier.weight(1f))
                    PrimaryButton("Play Again", onAgain, Modifier.weight(1f))
                }
            }
            RunStats(summary, Modifier.weight(1f, fill = false).padding(top = Space.x2))
        }
    }
}

/** The enemy that took the last health, with its outline in its own colour. Nothing when that is not known. */
@Composable
private fun KilledBy(code: Int) {
    val type = Cause.type(code)
    if (type < 0 || type >= Bestiary.size) return
    val c = Theme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
        EnemyMark(type, size = 44.dp)
        Column {
            Text("Killed by", Type.footnote, c.ink3)
            Text(Bestiary.of(type).name, Type.title3, c.ink, maxLines = 1)
            Text(if (Cause.isShot(code)) "Hit by its shots." else "Hit by touching it.", Type.footnote, c.ink2)
        }
    }
    Spacer(Modifier.height(Space.x4))
}

@Composable
private fun ScoreBlock(summary: RunSummary) {
    val c = Theme.colors
    Text("Score", Type.footnote, c.ink3)
    Text(grouped(summary.score), Type.largeTitle.merge(Type.numbers), c.ink, maxLines = 1)
    val b = summary.breakdown ?: return
    Spacer(Modifier.height(Space.x1))
    // Only the parts that count for something; two to a line keeps the card short.
    val parts = buildList {
        add("Kills" to b.kills)
        add("Time" to b.time)
        add("Stages" to b.stages)
        if (b.upgrades > 0) add("Upgrades" to b.upgrades)
        if (b.bosses > 0) add("Bosses" to b.bosses)
        if (b.revives > 0) add("Revives" to b.revives)
    }
    for (pair in parts.chunked(2)) {
        Row(Modifier.fillMaxWidth()) {
            for ((label, value) in pair) Part(label, grouped(value), Modifier.weight(1f))
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    val x = if (b.multiplier == 1f) "" else "  ×${trimZero(b.multiplier)}"
    Text("${summary.difficulty.label}$x", Type.footnote, c.ink3)
}

private fun trimZero(v: Float) = String.format(java.util.Locale.US, "%.1f", v).removeSuffix(".0")

@Composable
private fun Part(label: String, value: String, modifier: Modifier) {
    val c = Theme.colors
    Row(modifier.padding(end = Space.x3)) {
        Text(label, Type.footnote, c.ink3, Modifier.weight(1f), maxLines = 1)
        Text(value, Type.footnote.merge(Type.numbers), c.ink2, maxLines = 1)
    }
}
