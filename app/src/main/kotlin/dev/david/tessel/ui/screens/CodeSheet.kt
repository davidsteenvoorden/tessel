package dev.david.tessel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.david.tessel.core.dev.DevAccess
import dev.david.tessel.ui.components.Text
import dev.david.tessel.ui.components.focusRing
import dev.david.tessel.ui.components.pressScale
import dev.david.tessel.ui.theme.Corner
import dev.david.tessel.ui.theme.Space
import dev.david.tessel.ui.theme.Theme
import dev.david.tessel.ui.theme.Type

/**
 * Asks for the developer code with a number pad of its own, so no keyboard has
 * to cover the screen in landscape. The fifth digit decides: right opens the page, wrong clears.
 */
@Composable
fun CodePanel(onUnlock: () -> Unit, onCancel: () -> Unit) {
    val c = Theme.colors
    var entered by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    fun press(digit: Char) {
        if (entered.length >= DevAccess.CODE_LENGTH) return
        wrong = false
        entered += digit
        if (entered.length == DevAccess.CODE_LENGTH) {
            if (entered == DevAccess.CODE) onUnlock() else {
                entered = ""
                wrong = true
            }
        }
    }

    Row(
        Modifier.padding(start = Space.x6, end = Space.x6, top = Space.x3, bottom = Space.x5),
        horizontalArrangement = Arrangement.spacedBy(Space.x8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(260.dp)) {
            Text("Developer Options", Type.title3, c.ink)
            Spacer(Modifier.height(Space.x1))
            Text("Enter the code.", Type.subhead, c.ink2)
            Spacer(Modifier.height(Space.x5))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                for (k in 0 until DevAccess.CODE_LENGTH) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (k < entered.length) c.ink else c.track),
                    )
                }
            }
            Spacer(Modifier.height(Space.x3))
            Text(if (wrong) "That is not it." else " ", Type.footnote, c.danger)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            for (row in listOf("123", "456", "789")) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    for (d in row) Key(d.toString()) { press(d) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                Key("Cancel", small = true, onClick = onCancel)
                Key("0") { press('0') }
                Key("Delete", small = true) {
                    wrong = false
                    entered = entered.dropLast(1)
                }
            }
        }
    }
}

@Composable
private fun Key(label: String, small: Boolean = false, onClick: () -> Unit) {
    val c = Theme.colors
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(source)
            .width(84.dp)
            .height(46.dp)
            .focusRing(source)
            .clip(RoundedCornerShape(Corner.button))
            .background(c.track)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, if (small) Type.subhead else Type.title3, c.ink, maxLines = 1)
    }
}
