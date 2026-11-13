package dev.david.tessel

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.david.tessel.platform.game.GameSurfaceView
import dev.david.tessel.platform.game.StressRig
import dev.david.tessel.platform.net.CoopLobby
import dev.david.tessel.platform.store.SettingsStore
import dev.david.tessel.ui.TesselApp
import dev.david.tessel.ui.theme.TesselTheme

class MainActivity : ComponentActivity() {
    private lateinit var game: GameSurfaceView
    private lateinit var lobby: CoopLobby

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val store = SettingsStore(this)
        game = GameSurfaceView(this)
        lobby = CoopLobby(this, game)

        // Debug launch options, ignored in release builds by the game view itself.
        val stress = intent.getBooleanExtra("stress", false)
        val loopback = intent.getBooleanExtra("loopback", false)
        if (stress) game.stress = StressRig()
        val onStart: (() -> Unit)? = when {
            loopback -> game::startLoopback
            stress || intent.getBooleanExtra("autostart", false) -> { { game.startRun(store.settings.value.difficulty) } }
            else -> null
        }

        // The menus sit in a separate layer above the game. Where they draw
        // nothing that can be touched, touches fall through to the game view
        // untouched, with no translation between toolkits.
        val menus = ComposeView(this).apply {
            setContent { TesselTheme { TesselApp(game, lobby, store, onStart) } }
        }
        setContentView(
            FrameLayout(this).apply {
                addView(game)
                addView(menus)
            },
        )
        hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        game.setResumed(true)
    }

    override fun onPause() {
        // Coming back to a run that kept going without you is no fun. In co-op this pauses both phones.
        game.pause()
        game.setResumed(false)
        super.onPause()
    }

    override fun onDestroy() {
        if (isFinishing) lobby.leave()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The bars come back after a dialog or the notification shade.
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
