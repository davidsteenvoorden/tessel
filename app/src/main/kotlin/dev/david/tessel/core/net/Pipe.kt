package dev.david.tessel.core.net

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A plain socket straight to another phone on the same Wi-Fi network. Nearby
 * finds the phones and pairs them; once a game is on, its messages go this
 * way instead, because Nearby's own pipeline adds tens of milliseconds to each
 * one and lets them bunch up.
 *
 * Each message goes out the moment it is queued, with its length in front. A
 * writer thread does the sending, so the game never waits on the radio, and a
 * reader thread hands each arriving message to [onMessage]. When there is
 * nothing to say an empty message goes out instead, so a peer that went
 * silent is noticed within [SILENCE_MS]. Then the pipe closes and [onClosed]
 * runs, once, on whichever thread noticed.
 *
 * Messages that arrive before [open] is called are held back. That lets the
 * switch from Nearby wait until everything sent the old way has been handled,
 * so nothing is seen out of order.
 */
class Pipe(
    private val socket: Socket,
    private val onMessage: (ByteArray) -> Unit,
    private val onClosed: () -> Unit,
) {
    private val lock = Object()
    private val queue = ArrayDeque<ByteArray>()
    private var latestWaiting: ByteArray? = null
    private val closed = AtomicBoolean(false)

    private val gate = Any()
    private var opened = false
    private val held = ArrayList<ByteArray>()

    val isClosed: Boolean get() = closed.get()

    fun start() {
        socket.soTimeout = SILENCE_MS
        Thread(::readLoop, "pipe-in").apply { isDaemon = true }.start()
        Thread(::writeLoop, "pipe-out").apply { isDaemon = true }.start()
    }

    /** As [Link.send]: a latest-state message is skipped while the previous one has not gone out yet. */
    fun send(bytes: ByteArray, latest: Boolean): Boolean {
        synchronized(lock) {
            if (closed.get()) return false
            if (latest && latestWaiting != null) return false
            if (queue.size >= MAX_QUEUED) {
                // The other side stopped taking messages. Better to fall back than to pile up.
                close()
                return false
            }
            queue.addLast(bytes)
            if (latest) latestWaiting = bytes
            lock.notifyAll()
        }
        return true
    }

    /** Lets held and future messages through to [onMessage], in the order they came. */
    fun open() {
        synchronized(gate) {
            for (b in held) onMessage(b)
            held.clear()
            opened = true
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { socket.close() }
        synchronized(lock) { lock.notifyAll() }
        onClosed()
    }

    private fun readLoop() {
        try {
            val input = DataInputStream(BufferedInputStream(socket.getInputStream(), BUFFER))
            while (!closed.get()) {
                val n = input.readInt()
                if (n == 0) continue
                if (n < 0 || n > MAX_FRAME) throw IOException("frame of $n bytes")
                val bytes = ByteArray(n)
                input.readFully(bytes)
                synchronized(gate) {
                    if (opened) onMessage(bytes) else held += bytes
                }
            }
        } catch (_: IOException) {
        } finally {
            close()
        }
    }

    private fun writeLoop() {
        val batch = ArrayList<ByteArray>()
        try {
            val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), BUFFER))
            while (!closed.get()) {
                var latest: ByteArray?
                synchronized(lock) {
                    if (queue.isEmpty() && !closed.get()) lock.wait(KEEPALIVE_MS)
                    while (queue.isNotEmpty()) batch += queue.removeFirst()
                    latest = latestWaiting
                }
                if (closed.get()) break
                if (batch.isEmpty()) {
                    out.writeInt(0)
                } else {
                    for (b in batch) {
                        out.writeInt(b.size)
                        out.write(b)
                    }
                }
                out.flush()
                batch.clear()
                // Everything queued is out, including the latest-state message if there was one.
                synchronized(lock) { if (latest != null && latestWaiting === latest) latestWaiting = null }
            }
        } catch (_: IOException) {
        } catch (_: InterruptedException) {
        } finally {
            close()
        }
    }

    companion object {
        /** Nothing at all for this long, not even an empty message, means the other phone is gone. */
        const val SILENCE_MS = 2000

        /** How long the writer stays quiet before sending an empty message. */
        const val KEEPALIVE_MS = 400L

        const val MAX_FRAME = MAX_MESSAGE * 2
        const val MAX_QUEUED = 256
        private const val BUFFER = 64 * 1024
    }
}
