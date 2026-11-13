package dev.david.tessel.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import dev.david.tessel.ui.theme.Corner
import dev.david.tessel.ui.theme.Springs
import dev.david.tessel.ui.theme.Theme
import kotlinx.coroutines.launch

private const val DECELERATION = 0.998f

/** Where a flick would come to rest, the way a scroll view decelerates. */
private fun projection(velocity: Float) = velocity / 1000f * DECELERATION / (1f - DECELERATION)

/** Resistance past the open position: the further it is pulled, the less it follows. */
private fun rubberBand(overshoot: Float) = overshoot * 0.55f / (1f + 0.55f * overshoot)

/**
 * A panel that rises from the bottom edge over a dimmed game. It tracks the
 * finger 1:1, carries the release speed into its spring, can be grabbed again
 * mid-flight, and settles open or closed depending on where a flick would land.
 * With no [onDismiss] it cannot be swiped away and taps on the dimmed area do nothing.
 */
@Composable
fun Sheet(visible: Boolean, onDismiss: (() -> Unit)?, content: @Composable () -> Unit) {
    val c = Theme.colors
    val scope = rememberCoroutineScope()
    // 0 is fully down and out of sight, 1 is resting open.
    val shown = remember { Animatable(0f) }
    var present by remember { mutableStateOf(visible) }
    var height by remember { mutableIntStateOf(0) }

    LaunchedEffect(visible) {
        if (visible) {
            present = true
            shown.animateTo(1f, Springs.sheet())
        } else {
            shown.animateTo(0f, Springs.ui())
            present = false
        }
    }
    if (!present) return

    val drag = rememberDraggableState { delta ->
        if (height > 0) {
            scope.launch {
                val next = shown.value - delta / height
                shown.snapTo(if (next > 1f) 1f + rubberBand(next - 1f) else next.coerceAtLeast(0f))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown.value.coerceIn(0f, 1f) }
                .background(c.ground.copy(alpha = 0.6f))
                .pointerInput(onDismiss) { detectTapGestures { onDismiss?.invoke() } },
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(horizontal = 24.dp)
                .widthIn(max = 820.dp)
                .fillMaxWidth()
                .onSizeChanged { height = it.height }
                .graphicsLayer { translationY = (1f - shown.value) * (height + 24.dp.toPx()) }
                .draggable(
                    state = drag,
                    orientation = Orientation.Vertical,
                    enabled = onDismiss != null,
                    onDragStopped = { velocity ->
                        // Velocity is in pixels per second, positive downward.
                        val perHeight = if (height > 0) velocity / height else 0f
                        val landing = shown.value - (if (height > 0) projection(velocity) / height else 0f)
                        if (landing < 0.5f && onDismiss != null) {
                            scope.launch { shown.animateTo(0f, Springs.ui(), initialVelocity = -perHeight) }
                            onDismiss()
                        } else {
                            shown.animateTo(1f, Springs.flick(), initialVelocity = -perHeight)
                        }
                    },
                )
                .clip(RoundedCornerShape(topStart = Corner.sheet, topEnd = Corner.sheet))
                .background(c.surface)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column {
                if (onDismiss != null) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp)
                            .size(width = 36.dp, height = 5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(c.track),
                    )
                }
                content()
            }
        }
    }
}
