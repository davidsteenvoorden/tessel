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
import dev.david.tessel.platform.game.RunSummary
import dev.david.tessel.ui.components.PrimaryButton
import dev.david.tessel.ui.components.SecondaryButton
import dev.david.tessel.ui.components.Text
import dev.david.tessel.ui.theme.Corner
import dev.david.tessel.ui.theme.Space
import dev.david.tessel.ui.theme.Springs
import dev.david.tessel.ui.theme.Theme
import dev.david.tessel.ui.theme.Type

@Composable
fun RunOverCard(visible: Boolean, summary: RunSummary?, newBest: Boolean, onAgain: () -> Unit, onHome: () -> Unit) {
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
        // How far the run got, and what to do next, on the left; how it went, on the right.
        Row(
            Modifier
                .graphicsLayer {
                    val s = 0.96f + 0.04f * shown.value
                    scaleX = s
                    scaleY = s
                }
                .padding(horizontal = Space.x6)
                .widthIn(max = 820.dp)
                .clip(RoundedCornerShape(Corner.sheet))
                .background(c.surface)
                .padding(Space.x6),
            horizontalArrangement = Arrangement.spacedBy(Space.x8),
        ) {
            Column(Modifier.width(300.dp)) {
                Text("Run over", Type.title1, c.ink)
                val sub = summary.difficulty.label + if (newBest) " · New best" else ""
                Text(sub, Type.subhead, c.ink2)
                Spacer(Modifier.height(Space.x5))
                Row {
                    Stat("Stage", summary.stage.toString(), Modifier.weight(1f))
                    Stat("Time", clock(summary.seconds), Modifier.weight(1f))
                    if (summary.bosses > 0) Stat(if (summary.bosses == 1) "Boss" else "Bosses", summary.bosses.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(Space.x6))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    SecondaryButton("Home", onHome, Modifier.weight(1f))
                    PrimaryButton("Play Again", onAgain, Modifier.weight(1f))
                }
            }
            RunStats(summary, Modifier.weight(1f, fill = false).padding(top = Space.x2), header = false)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val c = Theme.colors
    Column(modifier) {
        Text(value, Type.title2.merge(Type.numbers), c.ink, maxLines = 1)
        Text(label, Type.footnote, c.ink2)
    }
}
