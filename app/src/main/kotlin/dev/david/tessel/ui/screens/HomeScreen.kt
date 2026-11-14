package dev.david.tessel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.core.stats.Ledger
import dev.david.tessel.platform.store.Settings
import dev.david.tessel.ui.components.Divider
import dev.david.tessel.ui.components.Group
import dev.david.tessel.ui.components.NavRow
import dev.david.tessel.ui.components.PrimaryButton
import dev.david.tessel.ui.components.Segmented
import dev.david.tessel.ui.components.ShardMark
import dev.david.tessel.ui.components.Text
import dev.david.tessel.ui.theme.Space
import dev.david.tessel.ui.theme.Theme
import dev.david.tessel.ui.theme.Type

@Composable
fun HomeScreen(
    settings: Settings,
    ledger: Ledger,
    onPlay: () -> Unit,
    onTogether: () -> Unit,
    onStats: () -> Unit,
    onDifficulty: (Difficulty) -> Unit,
    onSettings: () -> Unit,
    onLogo: (() -> Unit)? = null,
) {
    val c = Theme.colors
    Row(
        Modifier
            .fillMaxSize()
            .background(c.ground)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = 56.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // With developer options on, the logo is a shortcut to them.
            ShardMark(
                Modifier
                    .size(64.dp)
                    .semantics { contentDescription = "Tessel logo" }
                    .then(if (onLogo == null) Modifier else Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onLogo() }),
            )
            Spacer(Modifier.height(Space.x5))
            Text("Tessel", Type.display, c.ink)
            Spacer(Modifier.height(Space.x2))
            val best = ledger.bestOn(settings.difficulty)
            val line = if (best == null) {
                "No ${settings.difficulty.label} runs yet"
            } else {
                "Best on ${settings.difficulty.label}: ${grouped(best.score)}  ·  Stage ${best.stage} in ${clock(best.seconds)}"
            }
            Text(line, Type.subhead.merge(Type.numbers), c.ink2)
        }

        Column(Modifier.width(360.dp)) {
            PrimaryButton("Play", onPlay, Modifier.fillMaxWidth().height(56.dp))
            Spacer(Modifier.height(Space.x4))
            Text("Difficulty", Type.footnote.copy(fontWeight = FontWeight.SemiBold), c.ink2, Modifier.padding(start = Space.x1))
            Spacer(Modifier.height(6.dp))
            Segmented(
                options = Difficulty.entries.map { it.label },
                selected = settings.difficulty.ordinal,
                onSelect = { onDifficulty(Difficulty.entries[it]) },
            )
            Spacer(Modifier.height(Space.x1))
            Text(settings.difficulty.blurb, Type.footnote, c.ink3, Modifier.padding(start = Space.x1))
            Spacer(Modifier.height(Space.x4))
            Group(title = null) {
                NavRow("Play Together", onClick = onTogether, detail = "Up to eight phones, one team.")
                Divider()
                NavRow("Statistics", onClick = onStats, detail = "Past runs, totals and the logbook.")
                Divider()
                NavRow("Settings", onClick = onSettings)
            }
        }
    }
}

fun clock(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)
