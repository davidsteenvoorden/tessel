package dev.david.tessel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.david.tessel.ui.components.SecondaryButton
import dev.david.tessel.ui.components.Text
import dev.david.tessel.ui.theme.Corner
import dev.david.tessel.ui.theme.Space
import dev.david.tessel.ui.theme.Theme
import dev.david.tessel.ui.theme.Type

/** A short message over a dimmed screen with one way out. */
@Composable
fun NoticeCard(title: String, body: String, button: String, onButton: () -> Unit, danger: Boolean = false) {
    val c = Theme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(c.ground.copy(alpha = 0.72f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(Corner.sheet))
                .background(c.surface)
                .padding(Space.x6),
        ) {
            Text(title, Type.title3, c.ink)
            Spacer(Modifier.height(Space.x2))
            Text(body, Type.subhead, c.ink2)
            Spacer(Modifier.height(Space.x5))
            SecondaryButton(button, onButton, Modifier.fillMaxWidth(), tint = if (danger) c.danger else c.ink)
        }
    }
}
