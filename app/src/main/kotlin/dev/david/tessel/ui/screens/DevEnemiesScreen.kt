package dev.david.tessel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.platform.store.Dev
import dev.david.tessel.platform.store.Settings
import dev.david.tessel.platform.store.TWEAK_DAMAGE
import dev.david.tessel.platform.store.TWEAK_HEALTH
import dev.david.tessel.platform.store.TWEAK_SPEEDS
import dev.david.tessel.platform.store.Tweak
import dev.david.tessel.ui.components.BackButton
import dev.david.tessel.ui.components.EnemyMark
import dev.david.tessel.ui.components.Segmented
import dev.david.tessel.ui.components.SecondaryButton
import dev.david.tessel.ui.components.Text
import dev.david.tessel.ui.components.focusRing
import dev.david.tessel.ui.theme.Corner
import dev.david.tessel.ui.theme.Space
import dev.david.tessel.ui.theme.Theme
import dev.david.tessel.ui.theme.Type

private fun label(v: Float) = if (v == v.toInt().toFloat()) "${v.toInt()}x" else "${v}x"

/**
 * Developer trial: tick the kinds that should come in (all of them, from the
 * first stage, once any is off), pick a boss to fight in every stage, and
 * open a kind to change its speed, health and the damage its hits do.
 */
@Composable
fun DevEnemiesScreen(settings: Settings, onChange: ((Settings) -> Settings) -> Unit, onBack: () -> Unit) {
    val c = Theme.colors
    val dev = settings.dev
    fun change(next: (Dev) -> Dev) = onChange { it.copy(dev = next(it.dev)) }
    var open by remember { mutableIntStateOf(-1) }
    val kinds = (0 until Bestiary.size).filter { Bestiary.of(it).let { d -> d.boss || d.from != 0 } }
    val regular = kinds.filter { !Bestiary.of(it).boss }

    Column(Modifier.fillMaxSize().background(c.ground).windowInsetsPadding(WindowInsets.displayCutout)) {
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Space.x4)) {
            BackButton("Developer", onBack, Modifier.align(Alignment.CenterStart))
            Text("Enemies and Bosses", Type.headline, c.ink, Modifier.align(Alignment.Center))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.x6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(top = Space.x2), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                Text(
                    "Untick a kind and it stays out. Once any is off, only the ticked kinds come, from stage 1. A boss comes in every stage, one at a time. Tap a row's name to change its speed, health and damage.",
                    Type.footnote,
                    c.ink3,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    SecondaryButton("All On", { change { it.copy(off = emptySet()) } }, Modifier.weight(1f))
                    SecondaryButton("All Off", { change { it.copy(off = regular.toSet()) } }, Modifier.weight(1f))
                    SecondaryButton("No Boss", { change { it.copy(boss = -1) } }, Modifier.weight(1f))
                }
                for (k in kinds) {
                    val def = Bestiary.of(k)
                    val ticked = if (def.boss) dev.boss == k else k !in dev.off
                    val t = dev.tweak(k)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Corner.button)).background(c.surface)) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                            val tick = remember { MutableInteractionSource() }
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .focusRing(tick, corner = 0.dp)
                                    .clickable(tick, indication = null, role = Role.Checkbox) {
                                        change {
                                            if (def.boss) it.copy(boss = if (ticked) -1 else k)
                                            else it.copy(off = if (ticked) it.off + k else it.off - k)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (ticked) c.ink else c.track),
                                )
                            }
                            val row = remember { MutableInteractionSource() }
                            Row(
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .clickable(row, indication = null, role = Role.Button) { open = if (open == k) -1 else k }
                                    .padding(end = Space.x4),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.x3),
                            ) {
                                EnemyMark(k, size = 28.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(def.name + if (def.boss) "  ·  Boss" else "", Type.body, c.ink, maxLines = 1)
                                    if (t != Tweak()) {
                                        Text("Speed ${label(t.speed)}  ·  Health ${label(t.health)}  ·  Damage ${t.damage}", Type.footnote, c.ink3, maxLines = 1)
                                    }
                                }
                            }
                        }
                        if (open == k) {
                            Column(Modifier.padding(start = Space.x4, end = Space.x4, bottom = Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                                fun tweak(next: (Tweak) -> Tweak) = change {
                                    val n = next(it.tweak(k))
                                    it.copy(tweaks = if (n == Tweak()) it.tweaks - k else it.tweaks + (k to n))
                                }
                                Text("Speed", Type.subhead, c.ink2)
                                Segmented(TWEAK_SPEEDS.map(::label), TWEAK_SPEEDS.indexOf(t.speed).coerceAtLeast(0), { i -> tweak { it.copy(speed = TWEAK_SPEEDS[i]) } })
                                Text("Health", Type.subhead, c.ink2)
                                Segmented(TWEAK_HEALTH.map(::label), TWEAK_HEALTH.indexOf(t.health).coerceAtLeast(0), { i -> tweak { it.copy(health = TWEAK_HEALTH[i]) } })
                                Text("Damage: health a hit takes", Type.subhead, c.ink2)
                                Segmented(TWEAK_DAMAGE.map { "$it" }, TWEAK_DAMAGE.indexOf(t.damage).coerceAtLeast(0), { i -> tweak { it.copy(damage = TWEAK_DAMAGE[i]) } })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Space.x4))
            }
        }
    }
}
