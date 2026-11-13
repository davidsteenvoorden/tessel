package dev.david.tessel.platform.net

import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.BandwidthInfo
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import dev.david.tessel.core.content.Difficulty
import dev.david.tessel.core.net.Wire
import dev.david.tessel.core.sim.MAX_PLAYERS
import dev.david.tessel.platform.game.GameSurfaceView
import dev.david.tessel.platform.game.Mode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class FoundGame(val endpointId: String, val name: String, val code: String)

sealed interface LobbyState {
    data object Idle : LobbyState

    /** This phone hosts. [players] are the phones that joined, in seat order. [tapped] is the last phone sent the code by NFC. */
    data class Hosting(val code: String, val players: List<String>, val problem: String? = null, val tapped: String? = null) : LobbyState

    /** Looking for games. With [invite] set, a tap told us which one, and it joins on its own. */
    data class Searching(val games: List<FoundGame>, val problem: String? = null, val invite: String? = null) : LobbyState
    data class Connecting(val host: String) : LobbyState

    /** In someone's game, waiting for them to start. [players] lists everyone, host first. */
    data class Joined(val host: String, val code: String, val players: List<String>) : LobbyState
    data class Playing(val host: Boolean) : LobbyState

    /** A link dropped mid-game; these are the players being waited for. */
    data class Reconnecting(val names: List<String>, val host: Boolean) : LobbyState
    data class Ended(val message: String) : LobbyState
}

/**
 * Finds the other phones and keeps the connections, using Nearby Connections:
 * Bluetooth to find each other, then whatever is fastest (usually a direct
 * Wi-Fi link) to play. No router, no internet, no account.
 *
 * One phone hosts and up to seven join it, in a star. The host advertises
 * "name|CODE"; joining phones list what they find and the player taps the
 * code shown on the host, or the two phones are simply held together (see
 * Tap.kt). Callbacks arrive on the main thread.
 */
class CoopLobby(private val activity: ComponentActivity, private val game: GameSurfaceView) {
    private val nearby = Nearby.getConnectionsClient(activity.applicationContext)
    private val main = Handler(Looper.getMainLooper())

    private val stateFlow = MutableStateFlow<LobbyState>(LobbyState.Idle)
    val state: StateFlow<LobbyState> = stateFlow

    val myName: String = deviceName()

    private class Seat(var endpoint: String?, val name: String) {
        var connected = true
        var inFlight = 0L
        var inFlightSince = 0L
    }

    // Host side: seats 1..7, by player number once the game starts.
    private val seats = arrayOfNulls<Seat>(MAX_PLAYERS)
    private val waiting = HashMap<Int, Runnable>()

    // Joining side.
    @Volatile private var hostEndpoint: String? = null
    @Volatile private var hostInFlight = 0L
    @Volatile private var hostInFlightSince = 0L
    private var hostName = ""
    private var mySeat = 0
    private var wantCode: String? = null
    private var needsDiscovery = false

    private var hosting = false
    private var code = ""
    private var playing = false
    private var reconnecting = false
    private var resumed = false
    private val found = LinkedHashMap<String, FoundGame>()

    private val tap = TapReader(activity) { result -> onTap(result) }

    /**
     * Phone Wi-Fi naps between packets to save power, which adds delay in
     * spikes. While linked to another phone, this asks Android to keep it
     * awake (on Android 10 and later; older ones get the high-performance mode).
     */
    @Suppress("DEPRECATION")
    private val wifiLock = activity.applicationContext.getSystemService(WifiManager::class.java)?.createWifiLock(
        if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF,
        "Tessel co-op",
    )?.apply { setReferenceCounted(false) }

    init {
        TapInbox.name = myName
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                resumed = true
                updateTap()
            }

            override fun onPause(owner: LifecycleOwner) {
                resumed = false
                updateTap()
            }
        })
    }

    /** The host's sends to the other players, by player number. */
    val toPlayers = object : HostLink {
        override fun send(player: Int, bytes: ByteArray, latest: Boolean): Boolean {
            val seat = seats.getOrNull(player) ?: return false
            val to = seat.endpoint ?: return false
            if (!seat.connected) return false
            if (latest && seat.inFlight != 0L && SystemClock.elapsedRealtime() - seat.inFlightSince < STALE_MS) return false
            val payload = Payload.fromBytes(bytes)
            if (latest) {
                seat.inFlight = payload.id
                seat.inFlightSince = SystemClock.elapsedRealtime()
            }
            nearby.sendPayload(to, payload)
            return true
        }
    }

    /** A joining phone's sends to the host. */
    val toHost = object : Link {
        override fun send(bytes: ByteArray, latest: Boolean): Boolean {
            val to = hostEndpoint ?: return false
            if (latest && hostInFlight != 0L && SystemClock.elapsedRealtime() - hostInFlightSince < STALE_MS) return false
            val payload = Payload.fromBytes(bytes)
            if (latest) {
                hostInFlight = payload.id
                hostInFlightSince = SystemClock.elapsedRealtime()
            }
            nearby.sendPayload(to, payload)
            return true
        }
    }

    fun host() {
        reset()
        hosting = true
        code = newCode()
        publishHosting()
        advertise()
        updateTap()
    }

    fun join() {
        reset()
        stateFlow.value = LobbyState.Searching(emptyList())
        discover()
    }

    /** Starts looking if a tap asked to join before this phone was ready to search. */
    fun ensureLooking() {
        if (!needsDiscovery) return
        needsDiscovery = false
        discover()
    }

    fun connect(target: FoundGame) {
        code = target.code
        hostName = target.name
        wantCode = null
        stateFlow.value = LobbyState.Connecting(target.name)
        nearby.requestConnection("$myName|join", target.endpointId, lifecycle)
            .addOnFailureListener { e ->
                if (statusOf(e) == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) return@addOnFailureListener
                stateFlow.value = LobbyState.Searching(found.values.toList(), "Could not connect to ${target.name}. Try again.")
            }
    }

    /** Host only: starts the run on every phone. */
    fun start(difficulty: Difficulty) {
        if (!hosting || playing) return
        // Seats are packed so players are numbered 1, 2, 3... with no gaps.
        val taken = seats.filterNotNull().filter { it.connected }
        if (taken.isEmpty()) return
        seats.fill(null)
        taken.forEachIndexed { k, seat -> seats[k + 1] = seat }
        val players = taken.size + 1
        stopLooking()
        playing = true
        updateTap()
        stateFlow.value = LobbyState.Playing(host = true)
        game.startCoopHost(toPlayers, difficulty, players)
    }

    /** Host only, while waiting on a dropped phone: play on without it. */
    fun continueWithout() {
        for (seat in waiting.keys.toList()) giveUpOn(seat)
    }

    /** Leaving on purpose: tells everyone, then drops everything. */
    fun leave() {
        val bye = Wire.bye()
        val ends = ArrayList<String>()
        hostEndpoint?.let { ends += it }
        for (s in seats) s?.endpoint?.let { ends += it }
        for (to in ends) nearby.sendPayload(to, Payload.fromBytes(bye))
        // Give the goodbye a moment to go out before the links close.
        main.postDelayed({ ends.forEach { nearby.disconnectFromEndpoint(it) } }, 300)
        hostEndpoint = null
        seats.fill(null)
        reset()
        stateFlow.value = LobbyState.Idle
    }

    /** Back to the start after an ended session has been read. */
    fun dismiss() {
        reset()
        stateFlow.value = LobbyState.Idle
    }

    private fun reset() {
        stopLooking()
        hostEndpoint?.let { nearby.disconnectFromEndpoint(it) }
        for (s in seats) s?.endpoint?.let { nearby.disconnectFromEndpoint(it) }
        seats.fill(null)
        hostEndpoint = null
        hostInFlight = 0L
        found.clear()
        hosting = false
        playing = false
        reconnecting = false
        wantCode = null
        needsDiscovery = false
        for (r in waiting.values) main.removeCallbacks(r)
        waiting.clear()
        main.removeCallbacks(hostGone)
        updateTap()
    }

    private fun stopLooking() {
        nearby.stopAdvertising()
        nearby.stopDiscovery()
    }

    private fun advertise() {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        nearby.startAdvertising("$myName|$code", SERVICE_ID, lifecycle, options)
            .addOnFailureListener { e ->
                if (statusOf(e) == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) return@addOnFailureListener
                if (!playing) publishHosting(problem = describe(e))
            }
    }

    private fun discover() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        nearby.startDiscovery(SERVICE_ID, discovery, options)
            .addOnFailureListener { e ->
                if (statusOf(e) == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) return@addOnFailureListener
                if (!reconnecting) stateFlow.value = LobbyState.Searching(found.values.toList(), describe(e))
            }
    }

    private fun publishHosting(problem: String? = null, tapped: String? = null) {
        val names = seats.filterNotNull().filter { it.connected }.map { it.name }
        stateFlow.value = LobbyState.Hosting(code, names, problem, tapped)
    }

    private fun broadcastRoster() {
        val names = listOf(myName) + seats.filterNotNull().filter { it.connected }.map { it.name }
        val bytes = Wire.roster(names)
        for (s in seats) if (s != null && s.connected) s.endpoint?.let { nearby.sendPayload(it, Payload.fromBytes(bytes)) }
    }

    private fun seatOf(endpointId: String): Int {
        for (k in 1 until MAX_PLAYERS) if (seats[k]?.endpoint == endpointId) return k
        return -1
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val name = info.endpointName.substringBefore('|')
            val theirCode = info.endpointName.substringAfter('|', "")
            if (reconnecting) {
                if (theirCode == code) nearby.requestConnection("$myName|back|$mySeat", endpointId, lifecycle)
                return
            }
            val game = FoundGame(endpointId, name, theirCode)
            found[endpointId] = game
            if (wantCode != null && theirCode == wantCode) {
                connect(game)
                return
            }
            val now = stateFlow.value
            if (now is LobbyState.Searching) stateFlow.value = now.copy(games = found.values.toList())
        }

        override fun onEndpointLost(endpointId: String) {
            found.remove(endpointId)
            val now = stateFlow.value
            if (now is LobbyState.Searching) stateFlow.value = now.copy(games = found.values.toList())
        }
    }

    // Seats being filled, by endpoint, between the knock and the connection result.
    private val arriving = HashMap<String, Int>()

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            if (!hosting) {
                // A joining phone only ever accepts the host it asked for.
                nearby.acceptConnection(endpointId, payloads)
                return
            }
            val parts = info.endpointName.split('|')
            val name = parts.getOrElse(0) { "Player" }
            val seat = if (parts.getOrNull(1) == "back") {
                // A phone that dropped out mid-game, coming back to its own seat.
                val s = parts.getOrNull(2)?.toIntOrNull() ?: -1
                if (playing && s in waiting.keys) s else -1
            } else if (!playing) {
                (1 until MAX_PLAYERS).firstOrNull { seats[it] == null && it !in arriving.values } ?: -1
            } else {
                -1
            }
            if (seat < 0) {
                nearby.rejectConnection(endpointId)
                return
            }
            arriving[endpointId] = seat
            if (seats[seat] == null) seats[seat] = Seat(null, name).apply { connected = false }
            nearby.acceptConnection(endpointId, payloads)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val ok = result.status.statusCode == ConnectionsStatusCodes.STATUS_OK
            if (hosting) {
                val seat = arriving.remove(endpointId) ?: return
                val s = seats[seat] ?: return
                if (!ok) {
                    if (!playing) seats[seat] = null
                    return
                }
                s.endpoint = endpointId
                s.connected = true
                s.inFlight = 0L
                updateTap()
                if (playing) {
                    waiting.remove(seat)?.let { main.removeCallbacks(it) }
                    game.linkRestored(seat)
                    if (waiting.isEmpty()) {
                        stopLooking()
                        stateFlow.value = LobbyState.Playing(host = true)
                    } else {
                        publishWaiting()
                    }
                } else {
                    publishHosting()
                    broadcastRoster()
                }
                return
            }

            if (!ok) {
                if (reconnecting) return
                stateFlow.value = LobbyState.Searching(found.values.toList(), "That did not connect. Try again.")
                return
            }
            hostEndpoint = endpointId
            hostInFlight = 0L
            stopLooking()
            updateTap()
            if (reconnecting) {
                reconnecting = false
                main.removeCallbacks(hostGone)
                game.linkRestored(0)
                stateFlow.value = LobbyState.Playing(host = false)
            } else {
                stateFlow.value = LobbyState.Joined(hostName, code, listOf(hostName, myName))
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (hosting) {
                val seat = seatOf(endpointId)
                if (seat < 0) return
                val s = seats[seat] ?: return
                s.connected = false
                s.endpoint = null
                updateTap()
                if (playing) {
                    // Hold the game for a while in case the phone comes back.
                    val giveUp = Runnable { giveUpOn(seat) }
                    waiting[seat] = giveUp
                    main.postDelayed(giveUp, RECONNECT_MS)
                    game.linkLost(seat)
                    publishWaiting()
                    advertise()
                } else {
                    seats[seat] = null
                    publishHosting()
                    broadcastRoster()
                }
                return
            }
            if (endpointId != hostEndpoint) return
            hostEndpoint = null
            updateTap()
            if (playing) {
                reconnecting = true
                stateFlow.value = LobbyState.Reconnecting(listOf(hostName), host = false)
                game.linkLost(0)
                discover()
                main.postDelayed(hostGone, RECONNECT_MS)
            } else {
                end("$hostName closed the game.")
            }
        }

        override fun onBandwidthChanged(endpointId: String, info: BandwidthInfo) {
            Log.d(TAG, "link quality ${info.quality}")
            val quality = when (info.quality) {
                BandwidthInfo.Quality.HIGH -> 3
                BandwidthInfo.Quality.MEDIUM -> 2
                BandwidthInfo.Quality.LOW -> 1
                else -> 0
            }
            game.setLinkQuality(if (hosting) seatOf(endpointId) else 0, quality)
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            when (Wire.typeOf(bytes)) {
                Wire.ROSTER -> {
                    val names = Wire.readRoster(bytes)
                    val now = stateFlow.value
                    if (now is LobbyState.Joined) stateFlow.value = now.copy(players = names)
                }
                Wire.START -> {
                    if (playing) {
                        // Play Again on the host: same game, new run.
                        game.deliver(0, bytes)
                        return
                    }
                    val start = Wire.readStart(bytes)
                    mySeat = start.you
                    playing = true
                    stateFlow.value = LobbyState.Playing(host = false)
                    game.startCoopClient(toHost, start.you, start.players)
                }
                Wire.BYE -> {
                    if (hosting) {
                        val seat = seatOf(endpointId)
                        if (seat < 0) return
                        if (playing) {
                            giveUpOn(seat)
                        } else {
                            seats[seat] = null
                            publishHosting()
                            broadcastRoster()
                        }
                    } else {
                        end("$hostName left the game.")
                    }
                }
                Wire.VERSION_MISMATCH -> end("These phones have different versions of Tessel. Install the same one on all of them.")
                -1, Wire.HELLO -> Unit
                else -> game.deliver(if (hosting) seatOf(endpointId) else 0, bytes)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.IN_PROGRESS) return
            if (update.payloadId == hostInFlight) hostInFlight = 0L
            for (s in seats) if (s != null && s.inFlight == update.payloadId) s.inFlight = 0L
        }
    }

    /** Host: a dropped phone did not come back in time, or left. Its player is gone for this run. */
    private fun giveUpOn(seat: Int) {
        waiting.remove(seat)?.let { main.removeCallbacks(it) }
        seats[seat]?.endpoint?.let { nearby.disconnectFromEndpoint(it) }
        seats[seat] = null
        game.dropPlayer(seat)
        if (seats.none { it != null }) {
            end("Everyone else left.")
            return
        }
        if (waiting.isEmpty()) {
            stopLooking()
            stateFlow.value = LobbyState.Playing(host = true)
        } else {
            publishWaiting()
        }
    }

    private fun publishWaiting() {
        stateFlow.value = LobbyState.Reconnecting(waiting.keys.sorted().mapNotNull { seats[it]?.name }, host = true)
    }

    private val hostGone = Runnable { end("Lost the connection to $hostName.") }

    private fun end(message: String) {
        reset()
        stateFlow.value = LobbyState.Ended(message)
        game.partnerGone()
    }

    // NFC. The host reads; everyone else can be read while they are not busy in a run.

    private fun updateTap() {
        val linked = hostEndpoint != null || seats.any { it?.connected == true }
        wifiLock?.let { if (linked && resumed) it.acquire() else if (it.isHeld) it.release() }

        val canRead = resumed && hosting && !playing
        tap.set(canRead, code, myName)
        val canAnswer = resumed && !hosting && !playing && hostEndpoint == null
        TapInbox.listener = if (canAnswer) { c, h -> onInvite(c, h) } else null
    }

    private fun onTap(result: TapResult) {
        if (!hosting || playing) return
        when (result) {
            is TapResult.Invited -> publishHosting(tapped = result.name)
            TapResult.Busy -> publishHosting(problem = "That phone needs Tessel open on its home or Play Together screen.")
        }
    }

    private fun onInvite(theirCode: String, host: String): Boolean {
        if (game.mode.value != Mode.HOME || theirCode.isEmpty()) return false
        // Already listed on the Join screen? Go straight there.
        val known = found.values.firstOrNull { it.code == theirCode }
        reset()
        hostName = host
        if (known != null) {
            connect(known)
            return true
        }
        wantCode = theirCode
        stateFlow.value = LobbyState.Searching(emptyList(), invite = host.ifEmpty { theirCode })
        if (readyToSearch()) {
            discover()
        } else {
            // The lobby screen starts the search once permissions and radios are in order.
            needsDiscovery = true
        }
        return true
    }

    private fun readyToSearch() = Readiness.missingPermissions(activity).isEmpty() &&
        Readiness.bluetoothOn(activity) && Readiness.wifiOn(activity) && Readiness.locationOk(activity)

    private fun statusOf(e: Exception) = (e as? ApiException)?.statusCode ?: -1

    private fun describe(e: Exception): String {
        val status = statusOf(e)
        return when {
            status == ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR -> "Bluetooth is not working. Turn it off and on again."
            status in 8030..8049 -> "Tessel needs permission to find nearby devices."
            else -> "Could not start (error $status). Check that Bluetooth and Wi-Fi are on."
        }
    }

    private fun newCode(): String = String(CharArray(4) { CODE_LETTERS.random() })

    private fun deviceName(): String {
        val named = if (Build.VERSION.SDK_INT >= 25) Settings.Global.getString(activity.contentResolver, Settings.Global.DEVICE_NAME) else null
        return (named ?: Build.MODEL ?: "Player").replace("|", " ").take(24)
    }

    private companion object {
        const val TAG = "Tessel"

        /** Fixed rather than the package name: debug builds carry a suffix and must still find release builds. */
        const val SERVICE_ID = "dev.david.tessel.coop"

        /**
         * One host, up to seven others: a star. The host keeps a link to each
         * phone; the others never talk to each other.
         */
        val STRATEGY: Strategy = Strategy.P2P_STAR

        /** No letters that read as digits (O, I, L) and no digits that read as letters (0, 1). */
        const val CODE_LETTERS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        /**
         * A latest-only message waits for the one before it to finish sending.
         * This is only a safety net for a transfer report that never comes;
         * any shorter and a slow link piles up a queue of stale snapshots,
         * which is exactly what makes a joining phone lag behind.
         */
        const val STALE_MS = 1000L
        const val RECONNECT_MS = 20_000L
    }
}
