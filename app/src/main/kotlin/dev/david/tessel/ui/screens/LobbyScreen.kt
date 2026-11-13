package dev.david.tessel.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.core.sim.Checkpoint
import dev.david.tessel.platform.net.CoopLobby
import dev.david.tessel.platform.net.FoundGame
import dev.david.tessel.platform.net.LobbyState
import dev.david.tessel.platform.net.NfcState
import dev.david.tessel.platform.net.Readiness
import dev.david.tessel.platform.net.TapReader
import dev.david.tessel.platform.store.Settings
import dev.david.tessel.ui.components.BackButton
import dev.david.tessel.ui.components.Divider
import dev.david.tessel.ui.components.Group
import dev.david.tessel.ui.components.NavRow
import dev.david.tessel.ui.components.PrimaryButton
import dev.david.tessel.ui.components.SecondaryButton
import dev.david.tessel.ui.components.Segmented
import dev.david.tessel.ui.components.Text
import dev.david.tessel.ui.components.pressScale
import dev.david.tessel.ui.theme.Corner
import dev.david.tessel.ui.theme.Space
import dev.david.tessel.ui.theme.Theme
import dev.david.tessel.ui.theme.Type
import kotlinx.coroutines.delay

/**
 * Finding the other phones. Checks come first, in the order a person would
 * fix them: Play services, permission, radios. Then Host or Join.
 */
@Composable
fun LobbyScreen(
    lobby: CoopLobby,
    state: LobbyState,
    saved: Checkpoint?,
    settings: Settings,
    onDifficulty: (Difficulty) -> Unit,
    onBack: () -> Unit,
) {
    val c = Theme.colors
    val context = LocalContext.current
    var missing by remember { mutableStateOf(Readiness.missingPermissions(context)) }
    var asked by remember { mutableStateOf(false) }
    var bluetooth by remember { mutableStateOf(Readiness.bluetoothOn(context)) }
    var wifi by remember { mutableStateOf(Readiness.wifiOn(context)) }
    var location by remember { mutableStateOf(Readiness.locationOk(context)) }
    val services = remember { Readiness.playServices(context) }

    // Radios and permissions can change in system settings while this screen waits.
    LaunchedEffect(Unit) {
        while (true) {
            missing = Readiness.missingPermissions(context)
            bluetooth = Readiness.bluetoothOn(context)
            wifi = Readiness.wifiOn(context)
            location = Readiness.locationOk(context)
            delay(1000)
        }
    }
    val askForPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        missing = Readiness.missingPermissions(context)
    }
    val open = { action: String ->
        val intent = Intent(action)
        if (action == AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS) intent.data = Uri.fromParts("package", context.packageName, null)
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.ground)
            .windowInsetsPadding(WindowInsets.displayCutout),
    ) {
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Space.x4)) {
            BackButton("Home", onBack, Modifier.align(Alignment.CenterStart))
            Text("Play Together", Type.headline, c.ink, Modifier.align(Alignment.Center))
        }
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x6, vertical = Space.x2),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                when {
                    !services -> Explain(
                        "This phone cannot play together",
                        "Tessel finds the other phone through Google Play services, and this phone does not have them.",
                    )
                    missing.isNotEmpty() -> {
                        Explain(
                            "Find your friends' phones",
                            "Tessel talks straight to the other phones over Bluetooth and Wi-Fi. Nothing goes over the internet, and nothing leaves the phones in the game.",
                        )
                        Spacer(Modifier.height(Space.x5))
                        if (!asked) {
                            PrimaryButton("Allow", { askForPermission.launch(Readiness.permissions()) }, Modifier.fillMaxWidth())
                        } else {
                            Text("Without nearby-device access the phones cannot see each other. You can allow it in Settings.", Type.footnote, c.ink2)
                            Spacer(Modifier.height(Space.x3))
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                                SecondaryButton("Ask Again", { askForPermission.launch(Readiness.permissions()) }, Modifier.weight(1f))
                                PrimaryButton("Open Settings", { open(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS) }, Modifier.weight(1f))
                            }
                        }
                    }
                    !bluetooth || !wifi || !location -> {
                        Explain("Turn on Bluetooth and Wi-Fi", "Every phone needs them on. Wi-Fi does not have to be connected to a network.")
                        Spacer(Modifier.height(Space.x5))
                        Group(title = null) {
                            NavRow("Bluetooth", if (bluetooth) null else { { open(AndroidSettings.ACTION_BLUETOOTH_SETTINGS) } }, value = if (bluetooth) "On" else "Off")
                            Divider()
                            NavRow("Wi-Fi", if (wifi) null else { { open(AndroidSettings.ACTION_WIFI_SETTINGS) } }, value = if (wifi) "On" else "Off")
                            if (!location) {
                                Divider()
                                NavRow("Location", { open(AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS) }, detail = "Older Android versions need it to find nearby phones.", value = "Off")
                            }
                        }
                    }
                    else -> Stage(lobby, state, saved, settings, onDifficulty)
                }
            }
        }
    }
}

@Composable
private fun Stage(lobby: CoopLobby, state: LobbyState, saved: Checkpoint?, settings: Settings, onDifficulty: (Difficulty) -> Unit) {
    val c = Theme.colors
    val context = LocalContext.current
    var nfc by remember { mutableStateOf(TapReader.state(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            nfc = TapReader.state(context)
            delay(1000)
        }
    }
    when (state) {
        LobbyState.Idle, is LobbyState.Reconnecting, is LobbyState.Playing -> {
            if (saved != null) {
                SavedGame(saved, onContinue = { lobby.host(saved) }, onDiscard = lobby::discardSave)
                Spacer(Modifier.height(Space.x6))
            }
            Text("One phone hosts, up to seven join. Every phone needs Tessel, Bluetooth and Wi-Fi. No internet needed.", Type.subhead, c.ink2)
            Spacer(Modifier.height(Space.x5))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                val hostDetail = if (saved != null) "Start a new game. It replaces the saved one." else "Start a game and get a code for your friends."
                ChoiceTile("Host", hostDetail, Modifier.weight(1f)) { lobby.host() }
                ChoiceTile("Join", "Find a game a friend is hosting nearby.", Modifier.weight(1f), lobby::join)
            }
            Spacer(Modifier.height(Space.x4))
            TapHint(nfc, "With NFC on, there is a quicker way: the host taps Host, then you hold your phones back to back.")
        }

        is LobbyState.Hosting -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your code", Type.subhead, c.ink2)
                    Text(state.code, Type.display.merge(Type.numbers).copy(fontSize = 56.sp, letterSpacing = 0.12.em), c.ink)
                }
                val joined = state.players.size
                Text(if (joined == 0) "Nobody yet" else "$joined joined", Type.headline.merge(Type.numbers), c.ink2)
            }
            Spacer(Modifier.height(Space.x2))
            if (nfc == NfcState.ON) {
                Text("Hold a friend's phone back to back with this one to send them the code. Or they tap Join and pick it.", Type.subhead, c.ink2)
            } else {
                Text("Friends tap Join and pick this code.", Type.subhead, c.ink2)
                TapHint(nfc, "Turn on NFC to let friends join by holding their phone to yours.")
            }
            state.tapped?.let {
                Spacer(Modifier.height(Space.x2))
                Text("Sent the code to $it.", Type.footnote, c.ink2)
            }
            state.problem?.let {
                Spacer(Modifier.height(Space.x2))
                Text(it, Type.footnote, c.danger)
            }
            Spacer(Modifier.height(Space.x4))
            val resume = state.resume
            if (resume != null) {
                Group(title = "Saved Game") {
                    PlayerRow(lobby.myName, "Host")
                    resume.names.forEachIndexed { i, name ->
                        Divider()
                        PlayerRow(name, if (resume.filled[i]) "Back" else "Not here yet")
                    }
                }
                Spacer(Modifier.height(Space.x2))
                Text(
                    "Carries on at stage ${resume.stage} on ${resume.difficulty.label}. Each phone gets its own seat back; anyone who does not come sits this one out.",
                    Type.footnote,
                    c.ink3,
                    Modifier.padding(start = Space.x1),
                )
                Spacer(Modifier.height(Space.x5))
                PrimaryButton("Continue", { lobby.start(settings.difficulty) }, Modifier.fillMaxWidth(), enabled = resume.filled.any { it })
            } else if (state.players.isEmpty()) {
                WaitingLine("Waiting for players. Up to seven can join.")
            } else {
                Group(title = "Players") {
                    PlayerRow(lobby.myName, "Host")
                    state.players.forEach { name ->
                        Divider()
                        PlayerRow(name, null)
                    }
                }
                Spacer(Modifier.height(Space.x4))
                Segmented(
                    options = Difficulty.entries.map { it.label },
                    selected = settings.difficulty.ordinal,
                    onSelect = { onDifficulty(Difficulty.entries[it]) },
                )
                Spacer(Modifier.height(Space.x1))
                Text("${settings.difficulty.blurb} More players bring more enemies.", Type.footnote, c.ink3, Modifier.padding(start = Space.x1))
                Spacer(Modifier.height(Space.x5))
                PrimaryButton("Start", { lobby.start(settings.difficulty) }, Modifier.fillMaxWidth())
            }
        }

        is LobbyState.Searching -> {
            // A tap can ask to join before this screen had permission to search.
            LaunchedEffect(state.invite) { lobby.ensureLooking() }
            when {
                state.invite != null -> WaitingLine("Joining ${state.invite}'s game")
                state.games.isEmpty() -> WaitingLine("Looking for games nearby. A friend needs to tap Host first.")
                else -> {
                    Text("Games nearby", Type.footnote.copy(fontWeight = FontWeight.SemiBold), c.ink2, Modifier.padding(start = Space.x4, bottom = Space.x2))
                    Group(title = null) {
                        state.games.forEachIndexed { i, game ->
                            if (i > 0) Divider()
                            FoundRow(game) { lobby.connect(game) }
                        }
                    }
                    Spacer(Modifier.height(Space.x3))
                    Text("Pick the code your friend's screen shows.", Type.footnote, c.ink3, Modifier.padding(start = Space.x4))
                }
            }
            state.problem?.let {
                Spacer(Modifier.height(Space.x3))
                Text(it, Type.footnote, c.danger)
            }
        }

        is LobbyState.Connecting -> WaitingLine("Connecting to ${state.host}")

        is LobbyState.Joined -> {
            Explain("In ${state.host}'s game", "")
            Spacer(Modifier.height(Space.x3))
            Group(title = "Players") {
                state.players.forEachIndexed { i, name ->
                    if (i > 0) Divider()
                    PlayerRow(name, if (i == 0) "Host" else if (name == lobby.myName) "You" else null)
                }
            }
            Spacer(Modifier.height(Space.x3))
            WaitingLine("Waiting for ${state.host} to start.")
        }

        is LobbyState.Ended -> {
            Explain(state.title, state.message)
            Spacer(Modifier.height(Space.x5))
            PrimaryButton("OK", lobby::dismiss, Modifier.fillMaxWidth())
        }
    }
}

/** The co-op run this phone saved: where it stands and who was in it. Discarding asks once more. */
@Composable
internal fun SavedGame(saved: Checkpoint, onContinue: () -> Unit, onDiscard: () -> Unit) {
    val c = Theme.colors
    var discarding by remember { mutableStateOf(false) }
    val others = (1 until saved.players).map { saved.names[it] }.filter { it.isNotBlank() }
    val with = when (others.size) {
        0 -> ""
        1 -> "With ${others[0]}. "
        else -> "With ${others.dropLast(1).joinToString(", ")} and ${others.last()}. "
    }
    val where = if (saved.inShop) "Picks up in the shop before it." else "Starts over from the beginning."
    Group(title = "Saved Game") {
        Column(Modifier.fillMaxWidth().padding(Space.x4)) {
            Text("Stage ${saved.nextStage}, ${saved.difficulty.label}", Type.headline, c.ink)
            Text(with + where, Type.subhead, c.ink2)
            Spacer(Modifier.height(Space.x4))
            if (!discarding) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    SecondaryButton("Discard", { discarding = true }, Modifier.weight(1f), tint = c.danger)
                    PrimaryButton("Continue", onContinue, Modifier.weight(1f))
                }
            } else {
                Text("Discard the saved game? It cannot be brought back.", Type.subhead, c.ink)
                Spacer(Modifier.height(Space.x3))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    SecondaryButton("Cancel", { discarding = false }, Modifier.weight(1f))
                    SecondaryButton("Discard", onDiscard, Modifier.weight(1f), tint = c.danger)
                }
            }
        }
    }
}

/** Says when NFC could help but is off. Says nothing on phones without NFC. */
@Composable
private fun TapHint(nfc: NfcState, whenOn: String) {
    val c = Theme.colors
    val context = LocalContext.current
    when (nfc) {
        NfcState.NONE -> Unit
        NfcState.ON -> Text(whenOn, Type.footnote, c.ink3)
        NfcState.OFF -> Group(title = null) {
            NavRow(
                "NFC",
                {
                    runCatching {
                        context.startActivity(Intent(AndroidSettings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
                detail = "Turn it on to join by holding phones together.",
                value = "Off",
            )
        }
    }
}

@Composable
private fun PlayerRow(name: String, role: String?) {
    val c = Theme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, Type.body, c.ink, Modifier.weight(1f), maxLines = 1)
        if (role != null) Text(role, Type.subhead, c.ink3)
    }
}

@Composable
private fun Explain(title: String, body: String) {
    val c = Theme.colors
    Text(title, Type.title2, c.ink)
    if (body.isNotEmpty()) {
        Spacer(Modifier.height(Space.x2))
        Text(body, Type.subhead, c.ink2)
    }
}

/** A line of status with a dot that breathes, so the screen does not look stuck. */
@Composable
private fun WaitingLine(text: String) {
    val c = Theme.colors
    val breath = rememberInfiniteTransition(label = "waiting")
    val alpha by breath.animateFloat(0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dot")
    Row(Modifier.fillMaxWidth().padding(vertical = Space.x2), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Box(Modifier.size(8.dp).alpha(alpha).clip(CircleShape).background(c.ink2))
        Spacer(Modifier.width(Space.x3))
        Text(text, Type.subhead, c.ink2)
    }
}

@Composable
private fun ChoiceTile(title: String, detail: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Theme.colors
    val source = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(source)
            .heightIn(min = 120.dp)
            .clip(RoundedCornerShape(Corner.button))
            .background(c.surface)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .padding(Space.x5),
    ) {
        Text(title, Type.title3, c.ink)
        Spacer(Modifier.height(Space.x2))
        Text(detail, Type.subhead, c.ink2)
    }
}

@Composable
private fun FoundRow(game: FoundGame, onClick: () -> Unit) {
    val c = Theme.colors
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(game.name, Type.body, c.ink, Modifier.weight(1f), maxLines = 1)
        Text(game.code, Type.headline.merge(Type.numbers).copy(letterSpacing = 0.08.em), c.ink)
    }
}
