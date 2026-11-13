package dev.david.tessel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.david.tessel.ui.components.PrimaryButton
import dev.david.tessel.ui.components.SecondaryButton
import dev.david.tessel.ui.components.Text
import dev.david.tessel.ui.theme.Space
import dev.david.tessel.ui.theme.Theme
import dev.david.tessel.ui.theme.Type

@Composable
fun PausePanel(coop: Boolean, onResume: () -> Unit, onSettings: () -> Unit, onLeave: () -> Unit) {
    val c = Theme.colors
    // Leaving throws the run away, so it takes a second, deliberate tap.
    var confirming by remember { mutableStateOf(false) }
    Column(Modifier.padding(start = Space.x6, end = Space.x6, top = Space.x3, bottom = Space.x5)) {
        Text("Paused", Type.title2, c.ink)
        if (coop) Text("The game is paused on both phones.", Type.footnote, c.ink2)
        Spacer(Modifier.height(Space.x4))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
            PrimaryButton("Resume", onResume, Modifier.weight(1f))
            SecondaryButton("Settings", onSettings, Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.x3))
        if (!confirming) {
            SecondaryButton(if (coop) "Leave Game" else "Leave Run", { confirming = true }, Modifier.fillMaxWidth(), tint = c.danger)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                Text(
                    if (coop) "Leave? The game ends for both of you." else "Leave and lose this run?",
                    Type.body,
                    c.ink2,
                    Modifier.weight(1f),
                )
                SecondaryButton("Stay", { confirming = false })
                SecondaryButton("Leave", onLeave, tint = c.danger)
            }
        }
    }
}
