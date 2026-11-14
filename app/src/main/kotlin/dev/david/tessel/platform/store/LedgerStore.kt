package dev.david.tessel.platform.store

import dev.david.tessel.core.stats.Ledger
import dev.david.tessel.core.stats.RunEntry
import dev.david.tessel.core.stats.Tally
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Finished runs, totals and the logbook, in one file. Read when the app
 * starts (it is small); changes show at once and reach the disk on a thread
 * of their own, written beside the old file first so a phone that dies
 * mid-write keeps what it had.
 */
class LedgerStore(private val file: File) {
    private val disk = Executors.newSingleThreadExecutor { r -> Thread(r, "ledger").apply { isDaemon = true } }
    private val lock = Any()
    private var ledger = load()
    private val flow = MutableStateFlow(ledger.copy())
    val state: StateFlow<Ledger> = flow

    private fun load(): Ledger {
        val bytes = try {
            if (file.exists()) file.readBytes() else null
        } catch (_: IOException) {
            null
        }
        return bytes?.let { Ledger.read(it) } ?: Ledger()
    }

    /** Adds a finished run. True when it is the best solo score on its difficulty so far. */
    fun record(entry: RunEntry): Boolean = synchronized(lock) {
        val best = ledger.record(entry)
        publish()
        best
    }

    /** Folds a run's logbook tally in and empties the tally. */
    fun merge(tally: Tally) {
        if (tally.isEmpty) return
        synchronized(lock) {
            ledger.merge(tally)
            publish()
        }
        tally.clear()
    }

    /** Wipes everything: history, totals and logbook. */
    fun erase() = synchronized(lock) {
        ledger = Ledger()
        publish()
    }

    /** Waits for every write so far to reach the disk. For tests. */
    internal fun settle() {
        disk.submit {}.get()
    }

    private fun publish() {
        flow.value = ledger.copy()
        val bytes = ledger.write()
        disk.execute {
            val next = File(file.parentFile, file.name + ".new")
            try {
                next.writeBytes(bytes)
                if (!next.renameTo(file)) next.copyTo(file, overwrite = true)
            } catch (_: IOException) {
            }
        }
    }
}
