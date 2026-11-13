package dev.david.tessel.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.david.tessel.core.content.Upgrades
import dev.david.tessel.platform.game.ShopOffer
import dev.david.tessel.platform.game.ShopView
import dev.david.tessel.ui.components.CoinAmount
import dev.david.tessel.ui.components.LevelPips
import dev.david.tessel.ui.components.PrimaryButton
import dev.david.tessel.ui.components.SecondaryButton
import dev.david.tessel.ui.components.Text
import dev.david.tessel.ui.components.pressScale
import dev.david.tessel.ui.theme.Corner
import dev.david.tessel.ui.theme.Space
import dev.david.tessel.ui.theme.Theme
import dev.david.tessel.ui.theme.Type

private const val COLUMNS = 3

/** Between stages: spend coins on upgrades, as many times over as the coins allow, then go on. */
@Composable
fun ShopPanel(
    shop: ShopView,
    vibrate: Boolean,
    onBuy: (Int) -> Unit,
    onHeal: () -> Unit,
    onReroll: () -> Unit,
    onNext: () -> Unit,
) {
    val c = Theme.colors
    val view = LocalView.current
    val tap = { if (vibrate) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = Space.x6, end = Space.x6, top = Space.x5, bottom = Space.x4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Stage ${shop.stage} cleared", Type.title3, c.ink)
                val team = if (shop.coop) "   ·   ${shop.readyCount} of ${shop.teamSize} ready" else ""
                Text("Health ${shop.hp} of ${shop.maxHp}$team", Type.footnote.merge(Type.numbers), c.ink2)
            }
            CoinAmount(shop.coins, style = Type.title3)
        }
        Spacer(Modifier.height(Space.x3))

        val tiles = ArrayList<@Composable (Modifier) -> Unit>()
        for (offer in shop.offers) {
            tiles += { m ->
                OfferTile(offer, m) {
                    tap()
                    onBuy(offer.slot)
                }
            }
        }
        tiles += { m ->
            HealTile(shop, m) {
                tap()
                onHeal()
            }
        }
        for (row in tiles.chunked(COLUMNS)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (tile in row) tile(Modifier.weight(1f))
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton(
                "Reroll",
                onReroll,
                enabled = shop.canReroll,
                trailing = { CoinAmount(shop.rerollPrice, color = if (shop.canReroll) c.ink2 else c.ink3, style = Type.subhead) },
            )
            Spacer(Modifier.weight(1f))
            when {
                !shop.coop -> PrimaryButton("Next Stage", onNext, Modifier.width(220.dp))
                shop.selfReady -> PrimaryButton("Waiting for Others", {}, Modifier.width(220.dp), enabled = false)
                else -> PrimaryButton("Ready", onNext, Modifier.width(220.dp))
            }
        }
    }
}

@Composable
private fun OfferTile(offer: ShopOffer, modifier: Modifier, onBuy: () -> Unit) {
    val c = Theme.colors
    Tile(
        name = offer.name,
        blurb = offer.blurb,
        enabled = offer.affordable,
        faded = offer.maxed,
        onClick = onBuy,
        modifier = modifier,
    ) {
        LevelPips(offer.level, offer.maxLevel)
        Spacer(Modifier.weight(1f))
        if (offer.maxed) {
            Text("Maxed", Type.subhead, c.ink2)
        } else {
            CoinAmount(offer.price, color = if (offer.affordable) c.ink else c.ink3, style = Type.subhead)
        }
    }
}

@Composable
private fun HealTile(shop: ShopView, modifier: Modifier, onHeal: () -> Unit) {
    val c = Theme.colors
    val full = shop.hp >= shop.maxHp
    Tile(
        name = "Heal",
        blurb = "Restore ${Upgrades.HEAL_AMOUNT} health",
        enabled = shop.canHeal,
        faded = shop.healsLeft == 0,
        onClick = onHeal,
        modifier = modifier,
    ) {
        val note = when {
            shop.healsLeft == 0 -> "None left"
            full -> "Full health"
            else -> "${shop.healsLeft} left"
        }
        Text(note, Type.footnote, c.ink2)
        Spacer(Modifier.weight(1f))
        if (!full && shop.healsLeft > 0) {
            CoinAmount(shop.healPrice, color = if (shop.canHeal) c.ink else c.ink3, style = Type.subhead)
        }
    }
}

@Composable
private fun Tile(
    name: String,
    blurb: String,
    enabled: Boolean,
    faded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    footer: @Composable RowScope.() -> Unit,
) {
    val c = Theme.colors
    val source = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(source, enabled)
            .height(100.dp)
            .alpha(if (faded) 0.45f else 1f)
            .clip(RoundedCornerShape(Corner.button))
            .background(if (c.dark) c.raised else c.ground)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.x3, vertical = 10.dp),
    ) {
        Text(name, Type.headline, c.ink, maxLines = 1)
        Text(blurb, Type.footnote, c.ink2, maxLines = 2)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = footer)
    }
}
