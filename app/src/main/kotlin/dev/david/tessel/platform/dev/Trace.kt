package dev.david.tessel.platform.dev

import android.util.Log

/**
 * The last few hundred things that happened, kept in memory so the developer
 * page can export them. Mode changes, links, crashes. Nothing here leaves the phone unless the player shares the export.
 */
object Trace {
    private const val CAPACITY = 400
    private val lines = arrayOfNulls<String>(CAPACITY)
    private var next = 0
    private var total = 0

    @Synchronized
    fun log(message: String) {
        lines[next] = "${System.currentTimeMillis()} $message"
        next = (next + 1) % CAPACITY
        total++
        Log.d("Tessel", message)
    }

    @Synchronized
    fun dump(): String {
        val count = minOf(total, CAPACITY)
        val first = if (total < CAPACITY) 0 else next
        return buildString {
            for (k in 0 until count) appendLine(lines[(first + k) % CAPACITY])
        }
    }
}
