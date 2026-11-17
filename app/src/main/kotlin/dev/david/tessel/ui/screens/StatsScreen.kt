package dev.david.tessel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.core.sim.Cause
import dev.david.tessel.core.stats.Achievement
import dev.david.tessel.core.stats.Achievements
import dev.david.tessel.core.stats.Ledger
import dev.david.tessel.core.stats.Tier
import dev.david.tessel.core.stats.RunEntry
import dev.david.tessel.ui.components.BackButton
import dev.david.tessel.ui.components.Divider
import dev.david.tessel.ui.components.EnemyMark
import dev.david.tessel.ui.components.Group
import dev.david.tessel.ui.components.SecondaryButton
import dev.david.tessel.ui.components.Segmented
import dev.david.tessel.ui.components.Text
import dev.david.tessel.ui.theme.Corner
import dev.david.tessel.ui.theme.Space
import dev.david.tessel.ui.theme.Theme
import dev.david.tessel.ui.theme.Type

private val tabs = listOf("Overview", "Runs", "Logbook", "Achievements")

/**
 * Everything the phone remembers about past runs: totals and bests, the
 * runs themselves, and a logbook of every enemy and boss met so far.
 */
@Composable
fun StatsScreen(ledger: Ledger, now: Long, onErase: () -> Unit, onBack: () -> Unit) {
    val c = Theme.colors
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(
        Modifier
            .fillMaxSize()
            .background(c.ground)
            .windowInsetsPadding(WindowInsets.displayCutout),
    ) {
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Space.x4)) {
            BackButton("Home", onBack, Modifier.align(Alignment.CenterStart))
            Text("Statistics", Type.headline, c.ink, Modifier.align(Alignment.Center))
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = Space.x6), contentAlignment = Alignment.Center) {
            Segmented(tabs, tab, { tab = it }, Modifier.widthIn(max = 600.dp))
        }
        Spacer(Modifier.height(Space.x3))
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 700.dp).fillMaxSize().padding(horizontal = Space.x6),
                verticalArrangement = Arrangement.spacedBy(Space.x2),
            ) {
                when (tab) {
                    0 -> overview(ledger, onErase)
                    1 -> runs(ledger, now)
                    2 -> logbook(ledger)
                    else -> achievements(ledger)
                }
                item { Spacer(Modifier.height(Space.x6)) }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.overview(ledger: Ledger, onErase: () -> Unit) {
    val t = ledger.totals
    if (t.runs == 0) {
        item { Empty("Nothing here yet. Finish a run and its numbers show up here.") }
        return
    }
    item {
        Group("Best Score") {
            Difficulty.entries.forEachIndexed { i, d ->
                if (i > 0) Divider()
                val best = ledger.bestOn(d)
                StatRow(d.label, if (best == null) "None yet" else "${grouped(best.score)}  ·  Stage ${best.stage}")
            }
        }
    }
    item { Spacer(Modifier.height(Space.x2)) }
    item {
        Group("All Runs") {
            val rows = listOf(
                "Runs played" to grouped(t.runs),
                "Time played" to playtime(t.seconds),
                "Deepest stage" to t.deepest.toString(),
                "Enemies defeated" to grouped(t.kills),
                "Bosses beaten" to grouped(t.bosses),
                "Damage dealt" to grouped(t.damage),
                "Shots fired" to grouped(t.shots),
                "Coins collected" to grouped(t.coins),
                "Upgrades bought" to grouped(t.upgrades),
                "Hits taken" to grouped(t.hitsTaken),
                "Times downed" to grouped(t.downed),
                "Teammates revived" to grouped(t.revives),
            )
            rows.forEachIndexed { i, (label, value) ->
                if (i > 0) Divider()
                StatRow(label, value)
            }
        }
    }
    item { Spacer(Modifier.height(Space.x2)) }
    item { Erase(onErase) }
}

@Composable
private fun Erase(onErase: () -> Unit) {
    val c = Theme.colors
    var asking by remember { mutableStateOf(false) }
    if (!asking) {
        SecondaryButton("Erase Statistics", { asking = true }, Modifier.fillMaxWidth(), tint = c.danger)
    } else {
        Text("Erase every run, total and logbook entry? It cannot be brought back.", Type.subhead, c.ink)
        Spacer(Modifier.height(Space.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
            SecondaryButton("Cancel", { asking = false }, Modifier.weight(1f))
            SecondaryButton("Erase", { asking = false; onErase() }, Modifier.weight(1f), tint = c.danger)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.runs(ledger: Ledger, now: Long) {
    if (ledger.runs.isEmpty()) {
        item { Empty("No runs yet. Each one you finish or leave is listed here, newest first.") }
        return
    }
    items(ledger.runs) { RunRow(it, now) }
}

@Composable
private fun RunRow(e: RunEntry, now: Long) {
    val c = Theme.colors
    val type = Cause.type(e.killedBy)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Corner.button))
            .background(c.surface)
            .padding(horizontal = Space.x4, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        if (!e.left && type in 0 until Bestiary.size) EnemyMark(type, size = 30.dp) else Spacer(Modifier.padding(15.dp))
        Column(Modifier.weight(1f)) {
            val team = if (e.players > 1) "  ·  Co-op, ${e.players}" else ""
            Text("Stage ${e.stage}  ·  ${e.difficulty.label}$team", Type.headline, c.ink, maxLines = 1)
            val how = when {
                e.won -> "Completed"
                e.left -> "Left the run"
                e.stage >= dev.david.tessel.core.content.CoreRules.STAGE -> "Failed at the Core"
                type in 0 until Bestiary.size -> "Killed by ${Bestiary.of(type).name}"
                else -> "Killed"
            }
            Text(
                "$how  ·  ${clock(e.seconds)}  ·  ${grouped(e.kills)} kills  ·  ${ago(e.endedAt, now)}",
                Type.footnote.merge(Type.numbers),
                c.ink2,
                maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(grouped(e.score), Type.title3.merge(Type.numbers), c.ink, maxLines = 1)
            Text("Score", Type.footnote, c.ink3)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.logbook(ledger: Ledger) {
    val kinds = (0 until Bestiary.size)
    val met = kinds.count { ledger.seen[it] }
    item {
        Text("Met $met of ${Bestiary.size}", Type.footnote.merge(Type.numbers), Theme.colors.ink2, Modifier.padding(start = Space.x1, bottom = Space.x1))
    }
    section("Enemies", kinds.filter { !Bestiary.of(it).boss }, ledger)
    item { Spacer(Modifier.height(Space.x2)) }
    section("Bosses", kinds.filter { Bestiary.of(it).boss }, ledger)
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String, types: List<Int>, ledger: Ledger) {
    item {
        Text(title, Type.footnote.copy(fontWeight = FontWeight.SemiBold), Theme.colors.ink2, Modifier.padding(start = Space.x1))
    }
    items(types.chunked(2)) { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            for (type in pair) Entry(type, ledger, Modifier.weight(1f))
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Entry(type: Int, ledger: Ledger, modifier: Modifier) {
    val c = Theme.colors
    val def = Bestiary.of(type)
    val known = ledger.seen[type]
    Row(
        modifier
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(Corner.button))
            .background(c.surface)
            .padding(Space.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        EnemyMark(type, size = 40.dp, known = known)
        Column {
            if (!known) {
                Text("???", Type.headline, c.ink3)
                Text("Not met yet.", Type.footnote, c.ink3)
            } else {
                Text(def.name, Type.headline, c.ink, maxLines = 1)
                val parts = mutableListOf("Defeated ${grouped(ledger.defeated[type])}")
                if (ledger.killedYou[type] > 0) parts += "Killed you ${grouped(ledger.killedYou[type])}"
                Text(parts.joinToString("  ·  "), Type.footnote.merge(Type.numbers), c.ink2, maxLines = 1)
                val first = ledger.firstStage[type]
                val note = def.blurb.ifEmpty { if (first > 0) "First met on stage $first." else "" }
                if (note.isNotEmpty()) Text(note, Type.footnote, c.ink3, maxLines = 2)
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    val c = Theme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = Space.x4, vertical = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Type.body, c.ink, Modifier.weight(1f))
        Text(value, Type.body.merge(Type.numbers), c.ink2)
    }
}

@Composable
private fun Empty(message: String) {
    Text(message, Type.subhead, Theme.colors.ink2, Modifier.padding(vertical = Space.x6, horizontal = Space.x1))
}

/** 2 h 14 min, 14 min, 45 s. */
internal fun playtime(seconds: Int): String = when {
    seconds >= 3_600 -> "${seconds / 3_600} h ${seconds % 3_600 / 60} min"
    seconds >= 60 -> "${seconds / 60} min"
    else -> "$seconds s"
}


private fun tierColor(t: Tier, c: dev.david.tessel.ui.theme.Colors): Color = when (t) {
    Tier.EASY -> c.accent
    Tier.MEDIUM -> c.perk
    Tier.HARD -> c.rare
    Tier.LEGENDARY -> c.coin
}

private fun androidx.compose.foundation.lazy.LazyListScope.achievements(ledger: Ledger) {
    item {
        Text(
            "${Achievements.doneCount(ledger)} of ${Achievements.all.size} done",
            Type.footnote.merge(Type.numbers),
            Theme.colors.ink2,
            Modifier.padding(start = Space.x1, bottom = Space.x1),
        )
    }
    for (tier in Tier.entries) {
        val list = Achievements.all.filter { it.tier == tier }
        val done = list.count { it.done(ledger) }
        item {
            Text(
                "${tier.label}  ·  $done of ${list.size}",
                Type.footnote.copy(fontWeight = FontWeight.SemiBold),
                tierColor(tier, Theme.colors),
                Modifier.padding(start = Space.x1, top = Space.x2),
            )
        }
        items(list.chunked(2)) { pair ->
            Row(Modifier.fillMaxWidth().heightIn(min = 92.dp), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                for (a in pair) Card(a, ledger, Modifier.weight(1f).fillMaxHeight())
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Card(a: Achievement, ledger: Ledger, modifier: Modifier) {
    val c = Theme.colors
    val have = a.progress(ledger).coerceAtMost(a.target)
    val done = have >= a.target
    val tint = tierColor(a.tier, c)
    Column(
        modifier
            .clip(RoundedCornerShape(Corner.button))
            .background(c.surface)
            .padding(Space.x3),
        verticalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a.name, Type.headline, if (done) c.ink else c.ink2, Modifier.weight(1f), maxLines = 1)
            if (done) Text("Done", Type.footnote.copy(fontWeight = FontWeight.SemiBold), tint)
        }
        Text(a.text, Type.footnote, c.ink2, maxLines = 2)
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.track)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(have.toFloat() / a.target).background(tint))
        }
        if (!done) Text("${grouped(have)} / ${grouped(a.target)}", Type.footnote.merge(Type.numbers), c.ink3, maxLines = 1)
    }
}
