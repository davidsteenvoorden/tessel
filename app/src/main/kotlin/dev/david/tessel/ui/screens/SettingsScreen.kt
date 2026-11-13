package dev.david.tessel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.platform.store.Settings
import dev.david.tessel.platform.store.StickSize
import dev.david.tessel.platform.update.UpdateState
import dev.david.tessel.platform.update.Updater
import dev.david.tessel.ui.components.BackButton
import dev.david.tessel.ui.components.Divider
import dev.david.tessel.ui.components.Group
import dev.david.tessel.ui.components.Segmented
import dev.david.tessel.ui.components.Text
import dev.david.tessel.ui.components.ToggleRow
import dev.david.tessel.ui.components.focusRing
import dev.david.tessel.ui.theme.Space
import dev.david.tessel.ui.theme.Theme
import dev.david.tessel.ui.theme.Type

@Composable
fun SettingsScreen(
    settings: Settings,
    updater: Updater,
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
                    Divider()
                    ToggleRow(
                        "Connection readout",
                        settings.showLink,
                        { v -> onChange { it.copy(showLink = v) } },
                        detail = "In co-op: how the phones are linked and the round trip, top right. Green is quick, amber is noticeable, red is slow.",
                    )
                }

                Updates(updater)
                Spacer(Modifier.height(Space.x4))
            }
        }
    }
}

/** The installed version and a way to get the newest one from GitHub. */
@Composable
private fun Updates(updater: Updater) {
    val c = Theme.colors
    val state by updater.state.collectAsState()
    val footer = when (val st = state) {
        is UpdateState.Failed -> st.message
        is UpdateState.Available -> st.release.notes.lineSequence().filter { it.isNotBlank() }.take(4).joinToString("\n").ifEmpty { null }
        is UpdateState.NeedsPermission -> "Android asks once before Tessel may install its own updates. Allow it, then come back."
        else -> if (updater.testBuild) {
            "This is a test build. An update installs the regular Tessel next to it."
        } else {
            "Tessel only goes online when you tap this, to ask GitHub for the newest version."
        }
    }
    Group("Updates", footer = footer) {
        InfoRow("Version", updater.installed)
        Divider()
        when (val st = state) {
            UpdateState.Idle, is UpdateState.Failed -> ActionRow("Check for Updates", null, updater::check)
            UpdateState.Checking -> ActionRow("Checking", null, null)
            UpdateState.UpToDate -> ActionRow("Check for Updates", "Up to date", updater::check)
            is UpdateState.Available -> ActionRow(
                "Install Version ${st.release.version}",
                if (st.release.bytes > 0) "%.1f MB".format(st.release.bytes / 1_048_576f) else null,
            ) { updater.install(st.release) }
            is UpdateState.Downloading -> Column(Modifier.padding(horizontal = Space.x4, vertical = Space.x3)) {
                Text("Downloading version ${st.release.version}", Type.body, c.ink)
                Spacer(Modifier.height(Space.x2))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.track)) {
                    Box(Modifier.fillMaxWidth(st.done).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.ink))
                }
            }
            is UpdateState.NeedsPermission -> ActionRow("Allow Installing Updates", null, updater::openPermission)
            UpdateState.Installing -> ActionRow("Installing", null, null)
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    val c = Theme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Type.body, c.ink, Modifier.weight(1f))
        Text(value, Type.body.merge(Type.numbers), c.ink2)
    }
}

/** A row that does something right here, rather than leading to another screen. */
@Composable
private fun ActionRow(title: String, value: String?, onClick: (() -> Unit)?) {
    val c = Theme.colors
    val source = remember { MutableInteractionSource() }
    val enabled = onClick != null
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .focusRing(source, corner = 0.dp)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button) { onClick?.invoke() }
            .padding(horizontal = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Type.body, if (enabled) c.ink else c.ink3, Modifier.weight(1f))
        if (value != null) Text(value, Type.body.merge(Type.numbers), c.ink2)
    }
}
