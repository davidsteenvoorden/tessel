package dev.david.tessel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.david.tessel.core.tutorial.Tutorial
import dev.david.tessel.core.tutorial.TutorialStep
import dev.david.tessel.platform.game.TutorialView
import dev.david.tessel.ui.components.PrimaryButton
import dev.david.tessel.ui.components.SecondaryButton
import dev.david.tessel.ui.components.Text
import dev.david.tessel.ui.theme.Corner
import dev.david.tessel.ui.theme.Space
import dev.david.tessel.ui.theme.Theme
import dev.david.tessel.ui.theme.Type

/** What the card says for each step; [moveSide] and [aimSide] follow the left-handed setting. */
internal fun tutorialTitle(step: TutorialStep) = when (step) {
    TutorialStep.MOVE -> "Move"
    TutorialStep.FIRE -> "Aim and Fire"
    TutorialStep.DODGE -> "Dodge"
    TutorialStep.DONE -> "That Is the Basics"
}

internal fun tutorialText(view: TutorialView, moveSide: String, aimSide: String): String = when (view.step) {
    TutorialStep.MOVE -> "Drag with your $moveSide thumb on the $moveSide half of the screen. The stick follows your thumb."
    TutorialStep.FIRE -> {
        val done = (view.progress * Tutorial.FIRE_KILLS + 19) / 20
        "Drag with your $aimSide thumb on the $aimSide half. You shoot where you aim. Break ${Tutorial.FIRE_KILLS} Dots: $done so far."
    }
    TutorialStep.DODGE -> if (view.hit) {
        "Hit. Nothing is lost here, but the count starts over."
    } else {
        "Orange rings are enemy shots. Keep moving and slip past them. Stay untouched for ${Tutorial.DODGE_SECONDS} seconds."
    }
    TutorialStep.DONE -> "Coins from broken enemies buy upgrades in the shop between stages, and every fifth stage ends in a boss. None of this counted."
}

/**
 * The practice instructions, a compact card at the top of the arena. Skip is
 * always there; at the end it becomes Finish. Only the card takes touches, so
 * the sticks work around it.
 */
@Composable
fun TutorialCard(view: TutorialView, leftHanded: Boolean, onSkip: () -> Unit, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val c = Theme.colors
    val moveSide = if (leftHanded) "right" else "left"
    val aimSide = if (leftHanded) "left" else "right"
    val last = view.step == TutorialStep.DONE
    Row(
        modifier
            .width(440.dp)
            .clip(RoundedCornerShape(Corner.sheet))
            .background(c.surface)
            .padding(start = Space.x4, top = Space.x3, end = Space.x3, bottom = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Column(Modifier.weight(1f)) {
            val label = if (last) "Practice" else "Step ${view.step.ordinal + 1} of 3"
            Text(label, Type.footnote, c.ink3, maxLines = 1)
            Text(if (view.passed) "${tutorialTitle(view.step)}: Done" else tutorialTitle(view.step), Type.headline, c.ink, maxLines = 1)
            Text(tutorialText(view, moveSide, aimSide), Type.footnote, if (view.hit) c.danger else c.ink2)
            if (!last) {
                Spacer(Modifier.height(Space.x2))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.track)) {
                    Box(Modifier.fillMaxWidth(view.progress / 20f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.ink))
                }
            }
        }
        if (last) {
            PrimaryButton("Finish", onFinish, Modifier.width(96.dp))
        } else {
            SecondaryButton("Skip", onSkip, Modifier.width(80.dp))
        }
    }
}
