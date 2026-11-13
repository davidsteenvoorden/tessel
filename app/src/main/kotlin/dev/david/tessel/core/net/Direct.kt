package dev.david.tessel.core.net

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/** One of this phone's own addresses on a local network, with its prefix length. */
class LocalAddress(val address: Inet4Address, val prefix: Int)

/**
 * Setting up a [Pipe]. The host listens on a free port and, through Nearby,
 * tells each joining phone its addresses, that port and a one-time token.
 * The joining phone tries the addresses on a network it shares with the host
 * and says hello with the token. Anyone else who connects is dropped.
 */
object Direct {
    /** Wait at most this long for one address to answer. */
    const val DIAL_MS = 1500

    /** A phone that connected has this long to say hello. */
    const val HELLO_MS = 2000

    /**
     * Expedited forwarding. Wi-Fi puts packets marked this way in its voice
     * queue, which gets the air first when the network is busy.
     */
    private const val LOW_DELAY = 0xB8

    fun tune(socket: Socket) {
        socket.tcpNoDelay = true
        runCatching { socket.trafficClass = LOW_DELAY }
    }

    /** This phone's IPv4 addresses on local networks (home Wi-Fi, a hotspot, a phone-to-phone group). */
    fun localAddresses(): List<LocalAddress> {
        val out = ArrayList<LocalAddress>()
        try {
            val all = NetworkInterface.getNetworkInterfaces() ?: return out
            for (nif in all) {
                if (!nif.isUp || nif.isLoopback || NOT_LOCAL.any { nif.name.startsWith(it) }) continue
                for (a in nif.interfaceAddresses) {
                    val ip = a.address as? Inet4Address ?: continue
                    if (!isLocal(ip)) continue
                    out += LocalAddress(ip, a.networkPrefixLength.toInt())
                }
            }
        } catch (_: SocketException) {
        } catch (_: SecurityException) {
        }
        return out
    }

    /** Interfaces for mobile data and VPNs: private addresses there lead somewhere else entirely. */
    private val NOT_LOCAL = listOf("rmnet", "ccmni", "v4-", "clat", "tun", "ppp", "ipsec", "dummy")

    /** Private and link-local IPv4 ranges: the only places another phone in the room can be. */
    fun isLocal(ip: Inet4Address): Boolean = ip.isSiteLocalAddress || ip.isLinkLocalAddress

    fun sameSubnet(a: Inet4Address, b: Inet4Address, prefix: Int): Boolean {
        if (prefix !in 1..32) return false
        val mask = if (prefix == 32) -1 else (-1 shl (32 - prefix))
        return (toInt(a) and mask) == (toInt(b) and mask)
    }

    /** The host's addresses that share a network with one of [mine], best first. */
    fun reachable(offered: List<Inet4Address>, mine: List<LocalAddress>): List<Inet4Address> =
        offered.filter { o -> mine.any { m -> sameSubnet(m.address, o, m.prefix) } }

    private fun toInt(ip: Inet4Address): Int {
        val b = ip.address
        return (b[0].toInt() and 0xFF shl 24) or (b[1].toInt() and 0xFF shl 16) or (b[2].toInt() and 0xFF shl 8) or (b[3].toInt() and 0xFF)
    }

    /**
     * Joining side: connects to the first address that answers and says
     * hello. Blocks, so call it off the main thread. [socketFor] makes the
     * socket, so the caller can tie it to the right network. Null if none answered.
     */
    fun dial(addresses: List<Inet4Address>, port: Int, token: Long, seat: Int, socketFor: (Inet4Address) -> Socket): Socket? {
        for (address in addresses) {
            val socket = try {
                socketFor(address)
            } catch (_: IOException) {
                continue
            }
            try {
                tune(socket)
                socket.connect(InetSocketAddress(address, port), DIAL_MS)
                val out = DataOutputStream(socket.getOutputStream())
                val hello = Wire.directHello(token, seat)
                out.writeInt(hello.size)
                out.write(hello)
                out.flush()
                return socket
            } catch (_: IOException) {
                runCatching { socket.close() }
            }
        }
        return null
    }

    /**
     * Host side: listens on every local address, on a port the system picks.
     * Each phone that connects and says hello is handed to [onHello] with the
     * seat and token it gave, on a short-lived thread of its own; [onHello]
     * checks them and owns the socket from then on.
     */
    class Listener(private val onHello: (seat: Int, token: Long, socket: Socket) -> Unit) {
        private val server = ServerSocket(0)
        val port: Int get() = server.localPort

        fun start() {
            Thread(::acceptLoop, "pipe-listen").apply { isDaemon = true }.start()
        }

        fun close() {
            runCatching { server.close() }
        }

        private fun acceptLoop() {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (_: IOException) {
                    return
                }
                Thread({ greet(socket) }, "pipe-hello").apply { isDaemon = true }.start()
            }
        }

        private fun greet(socket: Socket) {
            try {
                tune(socket)
                socket.soTimeout = HELLO_MS
                val input = DataInputStream(socket.getInputStream())
                val n = input.readInt()
                if (n !in 4..64) throw IOException("hello of $n bytes")
                val bytes = ByteArray(n)
                input.readFully(bytes)
                if (Wire.typeOf(bytes) != Wire.DIRECT_HELLO) throw IOException("not a hello")
                val hello = Wire.readDirectHello(bytes)
                onHello(hello.seat, hello.token, socket)
            } catch (_: IOException) {
                runCatching { socket.close() }
            }
        }
    }
}
