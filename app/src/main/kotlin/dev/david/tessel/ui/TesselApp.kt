package dev.david.tessel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.david.tessel.platform.game.GameSurfaceView
import dev.david.tessel.platform.game.Mode
import dev.david.tessel.platform.game.Role
import dev.david.tessel.platform.game.RunSummary
import dev.david.tessel.platform.game.ShopView
import dev.david.tessel.platform.net.CoopLobby
import dev.david.tessel.platform.net.LobbyState
import dev.david.tessel.platform.store.SettingsStore
import dev.david.tessel.platform.update.Updater
import dev.david.tessel.ui.components.PauseButton
import dev.david.tessel.ui.components.PrimaryButton
import dev.david.tessel.ui.components.SecondaryButton
import dev.david.tessel.ui.components.Sheet
import dev.david.tessel.ui.components.Text
import dev.david.tessel.ui.screens.HomeScreen
import dev.david.tessel.ui.screens.LobbyScreen
import dev.david.tessel.ui.screens.NoticeCard
import dev.david.tessel.ui.screens.PausePanel
import dev.david.tessel.ui.screens.RunOverCard
import dev.david.tessel.ui.screens.SettingsScreen
import dev.david.tessel.ui.screens.ShopPanel
import dev.david.tessel.ui.theme.Space
import dev.david.tessel.ui.theme.Springs
import dev.david.tessel.ui.theme.Theme
import dev.david.tessel.ui.theme.Type

/**
 * Everything drawn over the game: home, lobby, settings, pause, shop and the
 * end card. The game view sits underneath and gets every touch these leave alone.
 */
@Composable
fun TesselApp(game: GameSurfaceView, lobby: CoopLobby, store: SettingsStore, updater: Updater, onStart: (() -> Unit)?) {
    val c = Theme.colors
    val settings by store.settings.collectAsState()
    val records by store.records.collectAsState()
    val mode by game.mode.collectAsState()
    val role by game.role.collectAsState()
    val shop by game.shop.collectAsState()
    val summary by game.summary.collectAsState()
    val lobbyState by lobby.state.collectAsState()
    val held by game.held.collectAsState()
    val coop = role != Role.SOLO

    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var lobbyOpen by rememberSaveable { mutableStateOf(false) }
    var newBest by remember { mutableStateOf(false) }
    // Kept after the game clears them, so a closing panel still has something to show.
    var lastShop by remember { mutableStateOf<ShopView?>(null) }
    var lastSummary by remember { mutableStateOf<RunSummary?>(null) }

    LaunchedEffect(settings) { game.applySettings(settings) }
    LaunchedEffect(shop) { shop?.let { lastShop = it } }
    LaunchedEffect(summary) {
        val run = summary ?: return@LaunchedEffect
        lastSummary = run
        // Only solo runs count toward the best on the home screen.
        newBest = !coop && store.record(run)
    }
    LaunchedEffect(Unit) { onStart?.invoke() }
    // Once the host presses Start, every phone leaves the lobby for the arena.
    // A tap from a host's phone opens the lobby here and joins on its own.
    LaunchedEffect(lobbyState) {
        val s = lobbyState
        if (s is LobbyState.Playing) lobbyOpen = false
        if (s is LobbyState.Searching && s.invite != null && mode == Mode.HOME) {
            settingsOpen = false
            lobbyOpen = true
        }
    }

    val leaveCoop = {
        lobby.leave()
        game.leaveRun()
    }

    Box(Modifier.fillMaxSize()) {
        if (mode == Mode.PLAYING) {
            PauseButton(
                onClick = game::pause,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(top = 6.dp, end = 10.dp),
            )
        }

        val waitingOnSomeone = lobbyState is LobbyState.Reconnecting || held
        Sheet(visible = mode == Mode.PAUSED && !settingsOpen && !waitingOnSomeone, onDismiss = game::resume) {
            PausePanel(
                coop = coop,
                onResume = game::resume,
                onSettings = { settingsOpen = true },
                onLeave = { if (coop) leaveCoop() else game.leaveRun() },
            )
        }

        Sheet(visible = mode == Mode.SHOP, onDismiss = null) {
            (shop ?: lastShop)?.let {
                ShopPanel(it, settings.vibration, game::buy, game::heal, game::reroll, game::nextStage)
            }
        }

        RunOverCard(
            visible = mode == Mode.OVER,
            summary = summary ?: lastSummary,
            newBest = newBest,
            onAgain = { game.playAgain(settings.difficulty) },
            onHome = { if (coop) leaveCoop() else game.leaveRun() },
        )

        // A link dropped mid-game: everyone waits a while for it to come back.
        Sheet(visible = waitingOnSomeone && mode != Mode.HOME && mode != Mode.OVER, onDismiss = null) {
            val lost = lobbyState as? LobbyState.Reconnecting
            val names = lost?.names?.joinToString(" and ").orEmpty()
            Column(Modifier.padding(Space.x6)) {
                Text("Connection lost", Type.title3, c.ink)
                val detail = when {
                    lost == null -> "The host is waiting for a player to come back. Hold on."
                    lost.host -> "Waiting for $names to come back. Keep the phones close, with Bluetooth and Wi-Fi on."
                    else -> "Trying to reach $names again. Keep the phones close, with Bluetooth and Wi-Fi on."
                }
                Text(detail, Type.subhead, c.ink2)
                Spacer(Modifier.height(Space.x4))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    SecondaryButton("Leave Game", leaveCoop, tint = c.danger)
                    if (lost?.host == true) {
                        PrimaryButton("Play On Without Them", {
                            lobby.continueWithout()
                            game.resume()
                        })
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = mode == Mode.HOME && !settingsOpen && !lobbyOpen,
            enter = fadeIn(Springs.ui()),
            exit = fadeOut(Springs.ui()),
        ) {
            HomeScreen(
                settings = settings,
                records = records,
                onPlay = { game.startRun(settings.difficulty) },
                onTogether = { lobbyOpen = true },
                onDifficulty = { d -> store.update { it.copy(difficulty = d) } },
                onSettings = { settingsOpen = true },
            )
        }

        // The lobby and settings slide in from the trailing edge and leave the same way.
        AnimatedVisibility(
            visible = lobbyOpen && mode == Mode.HOME,
            enter = slideInHorizontally(Springs.ui()) { it } + fadeIn(Springs.ui()),
            exit = slideOutHorizontally(Springs.ui()) { it } + fadeOut(Springs.ui()),
        ) {
            LobbyScreen(
                lobby = lobby,
                state = lobbyState,
                settings = settings,
                onDifficulty = { d -> store.update { it.copy(difficulty = d) } },
                onBack = {
                    lobby.leave()
                    lobbyOpen = false
                },
            )
        }

        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInHorizontally(Springs.ui()) { it } + fadeIn(Springs.ui()),
            exit = slideOutHorizontally(Springs.ui()) { it } + fadeOut(Springs.ui()),
        ) {
            SettingsScreen(
                settings = settings,
                updater = updater,
                inRun = mode != Mode.HOME,
                onChange = store::update,
                onBack = { settingsOpen = false },
            )
        }

        // The partner left, or the link never came back, while the lobby screen was closed.
        val ended = lobbyState as? LobbyState.Ended
        if (ended != null && !lobbyOpen) {
            NoticeCard("Game over", ended.message, "OK", lobby::dismiss)
        }
    }

    BackHandler(enabled = settingsOpen || lobbyOpen || mode != Mode.HOME) {
        when {
            settingsOpen -> settingsOpen = false
            lobbyOpen -> {
                lobby.leave()
                lobbyOpen = false
            }
            mode == Mode.PLAYING -> game.pause()
            mode == Mode.PAUSED -> game.resume()
            mode == Mode.OVER -> if (coop) leaveCoop() else game.leaveRun()
            // The shop only closes through Next Stage or Ready.
            else -> Unit
        }
    }
}
