package dev.david.tessel.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

/** The direct link, over real sockets on this computer. */
class DirectTest {
    private val here = InetAddress.getByName("127.0.0.1") as Inet4Address

    private fun ip(s: String) = InetAddress.getByName(s) as Inet4Address

    private class Ends(val host: Pipe, val client: Pipe, val atHost: LinkedBlockingQueue<ByteArray>, val atClient: LinkedBlockingQueue<ByteArray>)

    /** A host and a joining phone, connected the way the lobby does it. Both pipes started; neither opened. */
    private fun connect(clientClosed: CountDownLatch? = null, hostClosed: CountDownLatch? = null): Ends {
        val greeted = LinkedBlockingQueue<Socket>()
        val listener = Direct.Listener { seat, token, socket ->
            assertEquals(3, seat)
            assertEquals(1234L, token)
            greeted.put(socket)
        }
        listener.start()
        val socket = Direct.dial(listOf(here), listener.port, 1234L, 3) { Socket() }
        assertNotNull(socket)
        val hostSocket = greeted.poll(2, SECONDS)
        assertNotNull("the host heard hello", hostSocket)
        listener.close()
        val atHost = LinkedBlockingQueue<ByteArray>()
        val atClient = LinkedBlockingQueue<ByteArray>()
        val host = Pipe(hostSocket!!, { atHost.put(it) }, { hostClosed?.countDown() }).also { it.start() }
        val client = Pipe(socket!!, { atClient.put(it) }, { clientClosed?.countDown() }).also { it.start() }
        return Ends(host, client, atHost, atClient)
    }

    @Test
    fun messagesGoBothWaysInOrder() {
        val e = connect()
        e.host.open()
        e.client.open()
        for (k in 0 until 300) assertTrue(e.client.send(byteArrayOf(k.toByte(), 7), latest = false))
        for (k in 0 until 300) assertEquals(k.toByte(), e.atHost.poll(2, SECONDS)!![0])
        val big = ByteArray(MAX_MESSAGE) { it.toByte() }
        assertTrue(e.host.send(big, latest = true))
        assertTrue(big.contentEquals(e.atClient.poll(2, SECONDS)))
        e.host.close()
        e.client.close()
    }

    @Test
    fun messagesWaitUntilThePipeIsOpened() {
        val e = connect()
        e.host.open()
        for (k in 1..3) e.host.send(byteArrayOf(k.toByte()), latest = false)
        assertEquals(null, e.atClient.poll(300, MILLISECONDS))
        e.client.open()
        for (k in 1..3) assertEquals(k.toByte(), e.atClient.poll(2, SECONDS)!![0])
        e.host.close()
        e.client.close()
    }

    @Test
    fun closingOneEndClosesTheOther() {
        val gone = CountDownLatch(1)
        val e = connect(clientClosed = gone)
        e.host.close()
        assertTrue(gone.await(1, SECONDS))
        assertTrue(e.client.isClosed)
        assertFalse(e.client.send(byteArrayOf(1), latest = false))
    }

    @Test
    fun aQuietLinkStaysUp() {
        val e = connect()
        Thread.sleep(Pipe.SILENCE_MS + 600L)
        assertFalse(e.host.isClosed)
        assertFalse(e.client.isClosed)
        e.host.close()
        e.client.close()
    }

    @Test
    fun aPeerThatWentSilentIsNoticed() {
        // Something that accepts the connection and then never says a word.
        val mute = ServerSocket(0)
        val held = LinkedBlockingQueue<Socket>()
        Thread { held.put(mute.accept()) }.apply { isDaemon = true }.start()
        val socket = Socket().apply { connect(java.net.InetSocketAddress(here, mute.localPort), 1000) }
        val gone = CountDownLatch(1)
        Pipe(socket, {}, { gone.countDown() }).apply { start() }
        assertTrue(gone.await(Pipe.SILENCE_MS + 1500L, MILLISECONDS))
        mute.close()
    }

    @Test
    fun nobodyAnsweringGivesNull() {
        val closed = ServerSocket(0).let { val p = it.localPort; it.close(); p }
        assertEquals(null, Direct.dial(listOf(here), closed, 1L, 1) { Socket() })
    }

    @Test
    fun onlyAddressesOnASharedNetworkAreTried() {
        assertTrue(Direct.sameSubnet(ip("192.168.1.57"), ip("192.168.1.187"), 24))
        assertFalse(Direct.sameSubnet(ip("192.168.1.57"), ip("192.168.2.187"), 24))
        assertTrue(Direct.sameSubnet(ip("10.0.3.1"), ip("10.0.200.9"), 16))
        assertFalse(Direct.sameSubnet(ip("10.0.3.1"), ip("10.0.3.1"), 0))
        val mine = listOf(LocalAddress(ip("192.168.1.187"), 24))
        val offered = listOf(ip("10.94.12.7"), ip("192.168.1.57"), ip("192.168.49.1"))
        assertEquals(listOf(ip("192.168.1.57")), Direct.reachable(offered, mine))
        assertTrue(Direct.isLocal(ip("192.168.49.1")))
        assertTrue(Direct.isLocal(ip("169.254.3.4")))
        assertFalse(Direct.isLocal(ip("8.8.8.8")))
    }

    @Test
    fun theHandshakeMessagesSurviveTheTrip() {
        val offer = Wire.readDirectOffer(Wire.directOffer(-77L, 51234, listOf(ip("192.168.1.57"), ip("10.1.2.3"))))
        assertEquals(-77L, offer.token)
        assertEquals(51234, offer.port)
        assertEquals(listOf(ip("192.168.1.57"), ip("10.1.2.3")), offer.addresses)
        assertEquals(Wire.DIRECT_OFFER, Wire.typeOf(Wire.directOffer(1L, 1, emptyList())))
        assertEquals(42L, Wire.readDirectOn(Wire.directOn(42L)))
        val hello = Wire.readDirectHello(Wire.directHello(Long.MIN_VALUE, 7))
        assertEquals(Long.MIN_VALUE, hello.token)
        assertEquals(7, hello.seat)
    }
}
