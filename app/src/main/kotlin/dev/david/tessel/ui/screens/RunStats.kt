package dev.david.tessel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.david.tessel.platform.game.PlayerStats
import dev.david.tessel.platform.game.RunSummary
import dev.david.tessel.ui.components.Text
import dev.david.tessel.ui.theme.Space
import dev.david.tessel.ui.theme.Theme
import dev.david.tessel.ui.theme.Type
import java.util.Locale

/** One line of the statistics: its label, how to read it off a player, and whether the most is worth pointing out. */
private class Line(val label: String, val crown: Boolean, val coopOnly: Boolean = false, val value: (PlayerStats) -> Int)

private val lines = listOf(
    Line("Kills", crown = true) { it.kills },
    Line("Damage", crown = true) { it.damage },
    Line("Shots", crown = false) { it.shots },
    Line("Coins", crown = true) { it.coins },
    Line("Hits Taken", crown = false) { it.hitsTaken },
    Line("Downed", crown = false, coopOnly = true) { it.downed },
    Line("Revives", crown = true, coopOnly = true) { it.revives },
    Line("Upgrades", crown = false) { it.upgrades },
)

private val LabelWidth = 96.dp
private val ColumnWidth = 78.dp
private val RowHeight = 26.dp

/**
 * The run in numbers. Alone it is two short columns of figures; in a team,
 * a table with a column per player, where the top figure in each row worth
 * winning stands out, so it is plain at a glance who carried what.
 */
@Composable
fun RunStats(summary: RunSummary, modifier: Modifier = Modifier, header: Boolean = true) {
    val c = Theme.colors
    Column(modifier) {
        if (header) {
            Text(headline(summary), Type.footnote.merge(Type.numbers), c.ink2, maxLines = 1)
            Spacer(Modifier.height(Space.x3))
        }
        val team = summary.team
        if (team.size <= 1) {
            team.firstOrNull()?.let { Solo(it) }
        } else {
            Table(team)
        }
    }
}

/** "Stage 7 · 6:42 · 1 boss beaten" */
private fun headline(s: RunSummary): String {
    val parts = mutableListOf("Stage ${s.stage}", clock(s.seconds))
    if (s.bosses > 0) parts += if (s.bosses == 1) "1 boss beaten" else "${s.bosses} bosses beaten"
    return parts.joinToString("  ·  ")
}

@Composable
private fun Solo(p: PlayerStats) {
    val shown = lines.filter { !it.coopOnly }
    val half = (shown.size + 1) / 2
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x6)) {
        for (column in listOf(shown.take(half), shown.drop(half))) {
            Column(Modifier.weight(1f)) {
                for (line in column) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(line.label, Type.subhead, Theme.colors.ink2, Modifier.weight(1f), maxLines = 1)
                        Text(figure(line.value(p)), Type.headline.merge(Type.numbers), Theme.colors.ink, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Table(team: List<PlayerStats>) = BoxWithConstraints {
    val c = Theme.colors
    // Eight players do not fit beside the menu on a phone; then the table scrolls sideways, and says so.
    val fits = ((maxWidth - LabelWidth) / ColumnWidth).toInt().coerceAtLeast(1)
    Column {
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row(Modifier.height(RowHeight), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(LabelWidth))
            for (p in team) {
                val name = if (p.you) "You" else p.name
                val tone = when {
                    !p.present -> c.ink3
                    p.you -> c.ink
                    else -> c.ink2
                }
                Text(
                    name,
                    Type.footnote.copy(fontWeight = FontWeight.SemiBold),
                    tone,
                    Modifier.width(ColumnWidth).padding(start = Space.x2),
                    align = TextAlign.End,
                    maxLines = 1,
                )
            }
        }
        Box(Modifier.width(LabelWidth + ColumnWidth * team.size).height(1.dp).background(c.line))
        for (line in lines) {
            val values = team.map { line.value(it) }
            val top = values.max()
            // Only a clear leader gets picked out; a tie, or nothing at all, picks nobody.
            val leader = if (line.crown && top > 0 && values.count { it == top } == 1) values.indexOf(top) else -1
            Row(Modifier.height(RowHeight), verticalAlignment = Alignment.CenterVertically) {
                Text(line.label, Type.subhead, c.ink2, Modifier.width(LabelWidth), maxLines = 1)
                values.forEachIndexed { i, v ->
                    val lead = i == leader
                    val style = Type.subhead.merge(Type.numbers).let { if (lead) it.copy(fontWeight = FontWeight.SemiBold) else it }
                    val tone = when {
                        lead -> c.ink
                        !team[i].present -> c.ink3
                        else -> c.ink2
                    }
                    Text(figure(v), style, tone, Modifier.width(ColumnWidth).padding(start = Space.x1), align = TextAlign.End, maxLines = 1)
                }
            }
        }
    }
    if (team.size > fits) {
        Spacer(Modifier.height(Space.x2))
        Text("Swipe the table for ${team.size - fits} more.", Type.footnote, c.ink3)
    }
    }
}

/** 8,412 up to five digits, then 18.2k and 1.2M, so a column never grows past its width. */
internal fun figure(v: Int): String = when {
    v < 10_000 -> String.format(Locale.US, "%,d", v)
    v < 1_000_000 -> trimmed(v / 1_000f) + "k"
    else -> trimmed(v / 1_000_000f) + "M"
}

private fun trimmed(x: Float): String =
    if (x >= 100f) x.toInt().toString() else String.format(Locale.US, "%.1f", x).removeSuffix(".0")
