package dev.david.tessel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.david.tessel.platform.game.RunSummary
import dev.david.tessel.ui.components.PrimaryButton
import dev.david.tessel.ui.components.SecondaryButton
import dev.david.tessel.ui.components.Text
import dev.david.tessel.ui.theme.Space
import dev.david.tessel.ui.theme.Theme
import dev.david.tessel.ui.theme.Type

/**
 * The break reminder. The game is paused already; saving is the first choice
 * and carrying on the other. In a team it is paused on every phone, and
 * whoever answers answers for all of them.
 */
@Composable
fun BreakPanel(
    coop: Boolean,
    minutes: Int,
    stats: RunSummary?,
    onKeepPlaying: () -> Unit,
    onSave: () -> Unit,
    duel: Boolean = false,
) {
    val c = Theme.colors
    Row(
        Modifier.padding(start = Space.x6, end = Space.x6, top = Space.x3, bottom = Space.x5),
        horizontalArrangement = Arrangement.spacedBy(Space.x8),
    ) {
        Column(Modifier.width(320.dp)) {
            Text("Time for a Break", Type.title2, c.ink)
            Spacer(Modifier.height(Space.x1))
            val detail = if (coop) {
                "A phone in the team reached its break reminder, so the game is paused for everyone. Saving keeps the run for another day."
            } else {
                val spent = if (minutes > 0) "about $minutes minutes" else "a while"
                "You have been playing for $spent. The game is paused, and saving keeps the run for another day."
            }
            Text(detail, Type.subhead, c.ink2)
            Spacer(Modifier.height(Space.x4))
            if (duel) {
                PrimaryButton("Keep Playing", onKeepPlaying, Modifier.fillMaxWidth())
            } else {
                PrimaryButton(if (coop) "Multiplayer Pause" else "Save and Quit", onSave, Modifier.fillMaxWidth())
                Spacer(Modifier.height(Space.x3))
                SecondaryButton("Keep Playing", onKeepPlaying, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(Space.x2))
            Text("Reminders can be changed in Settings.", Type.footnote, c.ink3)
        }
        if (stats != null) RunStats(stats, Modifier.weight(1f).padding(top = Space.x1))
    }
}
