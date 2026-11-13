package dev.david.tessel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.platform.store.Settings
import dev.david.tessel.platform.store.StickSize
import dev.david.tessel.ui.components.BackButton
import dev.david.tessel.ui.components.Divider
import dev.david.tessel.ui.components.Group
import dev.david.tessel.ui.components.Segmented
import dev.david.tessel.ui.components.Text
import dev.david.tessel.ui.components.ToggleRow
import dev.david.tessel.ui.theme.Space
import dev.david.tessel.ui.theme.Theme
import dev.david.tessel.ui.theme.Type

@Composable
fun SettingsScreen(
    settings: Settings,
    inRun: Boolean,
    onChange: ((Settings) -> Settings) -> Unit,
    onBack: () -> Unit,
) {
    val c = Theme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.ground)
            .windowInsetsPadding(WindowInsets.displayCutout),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = Space.x4),
        ) {
            BackButton(if (inRun) "Paused" else "Home", onBack, Modifier.align(Alignment.CenterStart))
            Text("Settings", Type.headline, c.ink, Modifier.align(Alignment.Center))
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(top = Space.x2),
                verticalArrangement = Arrangement.spacedBy(Space.x6),
            ) {
                val difficultyNote = if (inRun) " A change takes effect from the next run." else ""
                Group("Difficulty", footer = settings.difficulty.blurb + difficultyNote) {
                    Box(Modifier.padding(Space.x3)) {
                        Segmented(
                            options = Difficulty.entries.map { it.label },
                            selected = settings.difficulty.ordinal,
                            onSelect = { i -> onChange { it.copy(difficulty = Difficulty.entries[i]) } },
                        )
                    }
                }

                Group("Controls") {
                    Column(Modifier.padding(horizontal = Space.x4, vertical = Space.x3)) {
                        Text("Stick size", Type.body, c.ink)
                        Spacer(Modifier.height(Space.x2))
                        Segmented(
                            options = StickSize.entries.map { it.label },
                            selected = settings.stickSize.ordinal,
                            onSelect = { i -> onChange { it.copy(stickSize = StickSize.entries[i]) } },
                        )
                    }
                    Divider()
                    ToggleRow(
                        "Left-handed",
                        settings.leftHanded,
                        { v -> onChange { it.copy(leftHanded = v) } },
                        detail = "Aim with the left thumb, move with the right.",
                    )
                }

                Group("Vibration") {
                    ToggleRow("Vibration", settings.vibration, { v -> onChange { it.copy(vibration = v) } })
                    Divider()
                    ToggleRow(
                        "Tick on every shot",
                        settings.shotVibration,
                        { v -> onChange { it.copy(shotVibration = v) } },
                        detail = "A light tap while you fire.",
                        enabled = settings.vibration,
                    )
                }

                Group("Display") {
                    ToggleRow(
                        "Performance readout",
                        settings.showStats,
                        { v -> onChange { it.copy(showStats = v) } },
                        detail = "Frame time and enemy count along the bottom edge.",
                    )
                }
                Spacer(Modifier.height(Space.x4))
            }
        }
    }
}
