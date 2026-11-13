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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.david.tessel.platform.game.RunSummary
import dev.david.tessel.ui.components.PrimaryButton
import dev.david.tessel.ui.components.SecondaryButton
import dev.david.tessel.ui.components.Text
import dev.david.tessel.ui.theme.Space
import dev.david.tessel.ui.theme.Theme
import dev.david.tessel.ui.theme.Type

private enum class Asking { NOTHING, SAVE, LEAVE }

/**
 * The pause menu: what to do on the left, the run so far on the right. In
 * co-op, Multiplayer Pause saves the game for everyone so it can carry on
 * another time. Saving and leaving both end the session, so each asks once
 * more, in place, before it happens.
 */
@Composable
fun PausePanel(
    coop: Boolean,
    host: Boolean,
    stats: RunSummary?,
    onResume: () -> Unit,
    onSettings: () -> Unit,
    onSave: () -> Unit,
    onLeave: () -> Unit,
) {
    val c = Theme.colors
    var asking by remember { mutableStateOf(Asking.NOTHING) }
    Row(
        Modifier.padding(start = Space.x6, end = Space.x6, top = Space.x3, bottom = Space.x5),
        horizontalArrangement = Arrangement.spacedBy(Space.x8),
    ) {
        Column(Modifier.width(320.dp)) {
            Text("Paused", Type.title2, c.ink)
            if (coop) Text("The game is paused on every phone.", Type.footnote, c.ink2)
            Spacer(Modifier.height(Space.x4))
            when (asking) {
                Asking.NOTHING -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                        PrimaryButton("Resume", onResume, Modifier.weight(1f))
                        SecondaryButton("Settings", onSettings, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(Space.x3))
                    if (coop) {
                        SecondaryButton("Multiplayer Pause", { asking = Asking.SAVE }, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(Space.x3))
                    }
                    SecondaryButton(if (coop) "Leave Game" else "Leave Run", { asking = Asking.LEAVE }, Modifier.fillMaxWidth(), tint = c.danger)
                }
                Asking.SAVE -> Confirm(
                    question = "Save and stop for everyone?",
                    detail = stats?.let { picksUp(it.stage) } ?: "",
                    action = "Save and Stop",
                    danger = false,
                    onCancel = { asking = Asking.NOTHING },
                    onConfirm = onSave,
                )
                Asking.LEAVE -> Confirm(
                    question = when {
                        !coop -> "Leave and lose this run?"
                        host -> "Leave for good?"
                        else -> "Leave this game?"
                    },
                    detail = when {
                        !coop -> ""
                        host -> "The game ends for everyone and is not saved. Multiplayer Pause keeps it."
                        else -> "The others carry on without you."
                    },
                    action = "Leave",
                    danger = true,
                    onCancel = { asking = Asking.NOTHING },
                    onConfirm = onLeave,
                )
            }
        }
        if (stats != null) RunStats(stats, Modifier.weight(1f).padding(top = Space.x1))
    }
}

/** Where a saved run starts again: the shop before the stage being played, or the start when there was no shop yet. */
private fun picksUp(stage: Int) =
    if (stage <= 1) "It starts over from stage 1, with the same players." else "You carry on in the shop before stage $stage, with what you had then."

@Composable
private fun Confirm(question: String, detail: String, action: String, danger: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val c = Theme.colors
    Text(question, Type.headline, c.ink)
    if (detail.isNotEmpty()) {
        Spacer(Modifier.height(Space.x1))
        Text(detail, Type.subhead, c.ink2)
    }
    Spacer(Modifier.height(Space.x4))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
        SecondaryButton("Cancel", onCancel, Modifier.weight(1f))
        if (danger) {
            SecondaryButton(action, onConfirm, Modifier.weight(1f), tint = c.danger)
        } else {
            PrimaryButton(action, onConfirm, Modifier.weight(1f))
        }
    }
}
