package dev.david.tessel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.platform.store.SoloSave
import dev.david.tessel.platform.store.SoloSaves
import dev.david.tessel.ui.components.PrimaryButton
import dev.david.tessel.ui.components.SecondaryButton
import dev.david.tessel.ui.components.Segmented
import dev.david.tessel.ui.components.Text
import dev.david.tessel.ui.theme.Corner
import dev.david.tessel.ui.theme.Space
import dev.david.tessel.ui.theme.Theme
import dev.david.tessel.ui.theme.Type

/**
 * What Play opens when there are runs waiting: a new run on the left, the
 * saved ones on the right. Deleting a saved run asks once more, in its row.
 */
@Composable
fun PlayPanel(
    saves: List<SoloSave>,
    difficulty: Difficulty,
    now: Long,
    onDifficulty: (Difficulty) -> Unit,
    onNew: () -> Unit,
    onContinue: (Int) -> Unit,
    onDelete: (Int) -> Unit,
) {
    val c = Theme.colors
    val full = saves.size >= SoloSaves.SLOTS
    Row(
        Modifier.padding(start = Space.x6, end = Space.x6, top = Space.x3, bottom = Space.x5),
        horizontalArrangement = Arrangement.spacedBy(Space.x8),
    ) {
        Column(Modifier.width(340.dp)) {
            Text("New Run", Type.title2, c.ink)
            Spacer(Modifier.height(Space.x3))
            Segmented(
                options = Difficulty.entries.map { it.label },
                selected = difficulty.ordinal,
                onSelect = { onDifficulty(Difficulty.entries[it]) },
            )
            Spacer(Modifier.height(Space.x1))
            Text(difficulty.blurb, Type.footnote, c.ink3, Modifier.padding(start = Space.x1))
            Spacer(Modifier.height(Space.x4))
            PrimaryButton("New Run", onNew, Modifier.fillMaxWidth(), enabled = !full)
            if (full) {
                Spacer(Modifier.height(Space.x2))
                Text("All ${SoloSaves.SLOTS} slots are in use. Delete a saved run to start another.", Type.footnote, c.ink2)
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Saved Runs", Type.title2, c.ink, Modifier.weight(1f))
                Text("${saves.size} of ${SoloSaves.SLOTS}", Type.footnote.merge(Type.numbers), c.ink3)
            }
            Spacer(Modifier.height(Space.x3))
            Column(
                Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.x2),
            ) {
                for (save in saves) SavedRun(save, now, { onContinue(save.slot) }, { onDelete(save.slot) })
            }
        }
    }
}

@Composable
private fun SavedRun(save: SoloSave, now: Long, onContinue: () -> Unit, onDelete: () -> Unit) {
    val c = Theme.colors
    val cp = save.checkpoint
    var asking by remember { mutableIntStateOf(0) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Corner.button))
            .background(c.raised)
            .padding(Space.x3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            Column(Modifier.weight(1f)) {
                Text("Stage ${cp.nextStage}  ·  ${cp.difficulty.label}", Type.headline, c.ink, maxLines = 1)
                Text(
                    "Score ${grouped(cp.score())}  ·  ${clock(cp.seconds)}  ·  ${ago(save.savedAt, now)}",
                    Type.footnote.merge(Type.numbers),
                    c.ink2,
                    maxLines = 1,
                )
            }
            if (asking == 0) {
                SecondaryButton("Delete", { asking = 1 }, Modifier.width(92.dp), tint = c.danger)
                PrimaryButton("Continue", onContinue, Modifier.width(112.dp))
            }
        }
        if (asking == 1) {
            Spacer(Modifier.height(Space.x2))
            Text("Delete this run? It cannot be brought back.", Type.subhead, c.ink)
            Spacer(Modifier.height(Space.x2))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                SecondaryButton("Cancel", { asking = 0 }, Modifier.weight(1f))
                SecondaryButton("Delete", onDelete, Modifier.weight(1f), tint = c.danger)
            }
        }
    }
}

/** How long ago, in the fewest words: just now, 5 min ago, 3 h ago, yesterday, 4 days ago. */
internal fun ago(then: Long, now: Long): String {
    val s = ((now - then) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "just now"
        s < 3_600 -> "${s / 60} min ago"
        s < 86_400 -> "${s / 3_600} h ago"
        s < 2 * 86_400 -> "yesterday"
        else -> "${s / 86_400} days ago"
    }
}
