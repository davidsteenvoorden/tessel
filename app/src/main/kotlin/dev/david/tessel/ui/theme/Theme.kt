package dev.david.tessel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import dev.david.tessel.design.Palette

private val LocalColors = staticCompositionLocalOf { Palette.Dark.toColors() }

/** Follows the system appearance; there is deliberately no in-app switch for it. */
@Composable
fun TesselTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = remember(dark) { (if (dark) Palette.Dark else Palette.Light).toColors() }
    CompositionLocalProvider(LocalColors provides colors, content = content)
}

object Theme {
    val colors: Colors
        @Composable @ReadOnlyComposable
        get() = LocalColors.current
}
