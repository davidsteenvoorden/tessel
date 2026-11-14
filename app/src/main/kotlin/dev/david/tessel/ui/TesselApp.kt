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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import dev.david.tessel.core.dev.DevAccess
import dev.david.tessel.core.dev.TapCounter
import dev.david.tessel.platform.game.GameSurfaceView
import dev.david.tessel.platform.game.Mode
import dev.david.tessel.platform.game.Role
import dev.david.tessel.platform.game.RunSummary
import dev.david.tessel.platform.game.ShopView
import dev.david.tessel.platform.net.CoopLobby
import dev.david.tessel.platform.net.LobbyState
import dev.david.tessel.platform.store.LedgerStore
import dev.david.tessel.platform.store.MAX_BREAK_MINUTES
import dev.david.tessel.platform.store.SettingsStore
import dev.david.tessel.platform.store.SoloSaves
import dev.david.tessel.platform.update.Updater
import dev.david.tessel.ui.components.PauseButton
import dev.david.tessel.ui.components.PrimaryButton
import dev.david.tessel.ui.components.SecondaryButton
import dev.david.tessel.ui.components.Sheet
import dev.david.tessel.ui.components.Text
import dev.david.tessel.ui.screens.BreakPanel
import dev.david.tessel.ui.screens.CodePanel
import dev.david.tessel.ui.screens.MinutesPanel
import dev.david.tessel.ui.screens.DevEnemiesScreen
import dev.david.tessel.ui.screens.DevScreen
import dev.david.tessel.ui.screens.HomeScreen
import dev.david.tessel.ui.screens.LobbyScreen
import dev.david.tessel.ui.screens.NoticeCard
import dev.david.tessel.ui.screens.PausePanel
import dev.david.tessel.ui.screens.PlayPanel
import dev.david.tessel.ui.screens.RunOverCard
import dev.david.tessel.ui.screens.SettingsScreen
import dev.david.tessel.ui.screens.ShopPanel
import dev.david.tessel.ui.screens.StatsScreen
import dev.david.tessel.ui.screens.TutorialCard
import kotlinx.coroutines.delay
import dev.david.tessel.ui.theme.Space
import dev.david.tessel.ui.theme.Springs
import dev.david.tessel.ui.theme.Theme
import dev.david.tessel.ui.theme.Type

/**
 * Everything drawn over the game: home, lobby, settings, pause, shop and the
 * end card. The game view sits underneath and gets every touch these leave alone.
 */
@Composable
fun TesselApp(
    game: GameSurfaceView,
    lobby: CoopLobby,
    store: SettingsStore,
    ledgerStore: LedgerStore,
    soloSaves: SoloSaves,
    updater: Updater,
    onStart: (() -> Unit)?,
) {
    val c = Theme.colors
    val settings by store.settings.collectAsState()
    val ledger by ledgerStore.state.collectAsState()
    val savedRuns by soloSaves.saves.collectAsState()
    val mode by game.mode.collectAsState()
    val role by game.role.collectAsState()
    val shop by game.shop.collectAsState()
    val summary by game.summary.collectAsState()
    val runStats by game.runStats.collectAsState()
    val saved by lobby.save.saved.collectAsState()
    val lobbyState by lobby.state.collectAsState()
    val held by game.held.collectAsState()
    val breakDue by game.breakDue.collectAsState()
    val tutorial by game.tutorial.collectAsState()
    val coop = role != Role.SOLO

    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var lobbyOpen by rememberSaveable { mutableStateOf(false) }
    var statsOpen by rememberSaveable { mutableStateOf(false) }
    var playOpen by remember { mutableStateOf(false) }
    var devOpen by rememberSaveable { mutableStateOf(false) }
    var codeOpen by remember { mutableStateOf(false) }
    var minutesOpen by remember { mutableStateOf(false) }
    var enemiesOpen by rememberSaveable { mutableStateOf(false) }
    // Seven taps on the version line, then a second of quiet, ask for the developer code.
    val taps = remember { TapCounter() }
    var tapSeq by remember { mutableIntStateOf(0) }
    LaunchedEffect(tapSeq) {
        if (tapSeq == 0) return@LaunchedEffect
        delay(DevAccess.QUIET_MS)
        if (taps.settle()) codeOpen = true
    }
    val startTutorial = {
        settingsOpen = false
        devOpen = false
        store.update { it.copy(tutorialSeen = true) }
        game.startTutorial()
    }
    // Kept after the game clears them, so a closing panel still has something to show.
    var lastShop by remember { mutableStateOf<ShopView?>(null) }
    var lastSummary by remember { mutableStateOf<RunSummary?>(null) }

    LaunchedEffect(settings) { game.applySettings(settings) }
    LaunchedEffect(shop) { shop?.let { lastShop = it } }
    LaunchedEffect(summary) { summary?.let { lastSummary = it } }
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
    // Leaving a run on purpose; for the host that also throws the save away.
    val abandonCoop = {
        lobby.leave(abandon = true)
        game.leaveRun()
    }

    Box(Modifier.fillMaxSize()) {
        val tutorialNow = tutorial
        if (mode == Mode.PLAYING && tutorialNow != null) {
            TutorialCard(
                view = tutorialNow,
                leftHanded = settings.leftHanded,
                onSkip = game::leaveRun,
                onFinish = game::leaveRun,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(top = 8.dp),
            )
        }
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
        Sheet(visible = mode == Mode.PAUSED && !breakDue && !settingsOpen && !waitingOnSomeone, onDismiss = game::resume) {
            PausePanel(
                coop = coop,
                host = role == Role.HOST,
                stats = runStats,
                onResume = game::resume,
                onSettings = { settingsOpen = true },
                onSave = game::saveForLater,
                onLeave = { if (tutorial != null) game.leaveRun() else if (coop) abandonCoop() else game.abandonRun() },
                practice = tutorial != null,
            )
        }

        // A break reminder paused the game: save and stop, or carry on.
        Sheet(visible = mode == Mode.PAUSED && breakDue && !settingsOpen && !waitingOnSomeone, onDismiss = game::resume) {
            BreakPanel(
                coop = coop,
                minutes = settings.breakEvery,
                stats = runStats,
                onKeepPlaying = game::resume,
                onSave = game::saveForLater,
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
                    SecondaryButton("Leave Game", abandonCoop, tint = c.danger)
                    if (lost?.host == true) {
                        // Saved with everyone in it, so the missing player has their seat next time.
                        SecondaryButton("Multiplayer Pause", game::saveForLater)
                        PrimaryButton("Play On Without Them", {
                            lobby.continueWithout()
                            game.resume()
                        })
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = mode == Mode.HOME && !settingsOpen && !lobbyOpen && !statsOpen,
            enter = fadeIn(Springs.ui()),
            exit = fadeOut(Springs.ui()),
        ) {
            HomeScreen(
                settings = settings,
                ledger = ledger,
                onPlay = { if (savedRuns.isEmpty()) game.startRun(settings.difficulty) else playOpen = true },
                onTogether = { lobbyOpen = true },
                onStats = { statsOpen = true },
                onDifficulty = { d -> store.update { it.copy(difficulty = d) } },
                onSettings = { settingsOpen = true },
                onLogo = if (settings.devUnlocked) {
                    {
                        settingsOpen = true
                        devOpen = true
                    }
                } else null,
            )
        }

        // Play with runs waiting asks first: a new one, or one of the saved ones. Drawn after the home
        // screen, or that would cover it.
        Sheet(visible = playOpen && mode == Mode.HOME && !settingsOpen && !lobbyOpen && !statsOpen, onDismiss = { playOpen = false }) {
            PlayPanel(
                saves = savedRuns,
                difficulty = settings.difficulty,
                now = System.currentTimeMillis(),
                onDifficulty = { d -> store.update { it.copy(difficulty = d) } },
                onNew = {
                    playOpen = false
                    game.startRun(settings.difficulty)
                },
                onContinue = { slot ->
                    playOpen = false
                    game.continueRun(slot)
                },
                onDelete = game::discardSave,
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
                saved = saved,
                settings = settings,
                onDifficulty = { d -> store.update { it.copy(difficulty = d) } },
                onBack = {
                    lobby.leave()
                    lobbyOpen = false
                },
            )
        }

        AnimatedVisibility(
            visible = statsOpen && mode == Mode.HOME,
            enter = slideInHorizontally(Springs.ui()) { it } + fadeIn(Springs.ui()),
            exit = slideOutHorizontally(Springs.ui()) { it } + fadeOut(Springs.ui()),
        ) {
            StatsScreen(
                ledger = ledger,
                now = System.currentTimeMillis(),
                onErase = ledgerStore::erase,
                onBack = { statsOpen = false },
            )
        }

        // A new install offers the tutorial once. Drawn after the home screen, or that would cover it.
        Sheet(
            visible = onStart == null && !settings.tutorialSeen && mode == Mode.HOME && !settingsOpen && !lobbyOpen && !statsOpen && !playOpen,
            onDismiss = { store.update { it.copy(tutorialSeen = true) } },
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = Space.x6, end = Space.x6, top = Space.x3, bottom = Space.x5),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Column(Modifier.width(420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("New to Tessel?", Type.title2, c.ink, align = TextAlign.Center)
                Spacer(Modifier.height(Space.x1))
                Text(
                    "A short practice shows the controls and how to dodge enemy shots. You can skip it and find it again under How to Play in Settings.",
                    Type.subhead,
                    c.ink2,
                    align = TextAlign.Center,
                )
                Spacer(Modifier.height(Space.x4))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    SecondaryButton("Skip", { store.update { it.copy(tutorialSeen = true) } }, Modifier.weight(1f))
                    PrimaryButton("Start Tutorial", startTutorial, Modifier.weight(1f))
                }
              }
            }
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
                onTutorial = startTutorial,
                onDev = { devOpen = true },
                onEditBreak = { minutesOpen = true },
                onUnlockDev = {
                    taps.tap()
                    tapSeq++
                },
            )
        }

        AnimatedVisibility(
            visible = settingsOpen && devOpen,
            enter = slideInHorizontally(Springs.ui()) { it } + fadeIn(Springs.ui()),
            exit = slideOutHorizontally(Springs.ui()) { it } + fadeOut(Springs.ui()),
        ) {
            DevScreen(
                settings = settings,
                version = updater.installed,
                inRun = mode != Mode.HOME,
                onChange = store::update,
                onBreakNow = {
                    devOpen = false
                    settingsOpen = false
                    game.breakNow()
                },
                onTutorial = startTutorial,
                onEnemies = { enemiesOpen = true },
                onStartRun = {
                    devOpen = false
                    settingsOpen = false
                    game.startDevRun(settings.difficulty)
                },
                onBack = { devOpen = false },
            )
        }

        AnimatedVisibility(
            visible = settingsOpen && devOpen && enemiesOpen,
            enter = slideInHorizontally(Springs.ui()) { it } + fadeIn(Springs.ui()),
            exit = slideOutHorizontally(Springs.ui()) { it } + fadeOut(Springs.ui()),
        ) {
            DevEnemiesScreen(settings = settings, onChange = store::update, onBack = { enemiesOpen = false })
        }

        Sheet(visible = minutesOpen && settingsOpen, onDismiss = { minutesOpen = false }) {
            MinutesPanel(
                initial = settings.breakEvery,
                max = MAX_BREAK_MINUTES,
                onSet = { m ->
                    store.update { it.copy(breakEvery = m) }
                    minutesOpen = false
                },
                onCancel = { minutesOpen = false },
            )
        }

        Sheet(visible = codeOpen && settingsOpen, onDismiss = { codeOpen = false }) {
            CodePanel(
                onUnlock = {
                    codeOpen = false
                    store.update { it.copy(devUnlocked = true) }
                    devOpen = true
                },
                onCancel = { codeOpen = false },
            )
        }

        // The partner left, or the link never came back, while the lobby screen was closed.
        val ended = lobbyState as? LobbyState.Ended
        if (ended != null && !lobbyOpen) {
            NoticeCard(ended.title, ended.message, "OK", lobby::dismiss)
        }
    }

    BackHandler(enabled = settingsOpen || lobbyOpen || statsOpen || playOpen || mode != Mode.HOME) {
        when {
            codeOpen -> codeOpen = false
            minutesOpen -> minutesOpen = false
            enemiesOpen && devOpen && settingsOpen -> enemiesOpen = false
            devOpen && settingsOpen -> devOpen = false
            settingsOpen -> settingsOpen = false
            statsOpen -> statsOpen = false
            playOpen -> playOpen = false
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
