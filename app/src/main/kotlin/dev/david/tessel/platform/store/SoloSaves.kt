package dev.david.tessel.platform.store

import dev.david.tessel.core.sim.Checkpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

/** One solo run kept for later, in slot [slot] (1 to [SoloSaves.SLOTS]). */
class SoloSave(val slot: Int, val savedAt: Long, val checkpoint: Checkpoint)

/**
 * Up to [SLOTS] solo runs that are not finished. A run takes a slot when it
 * starts and keeps it up to date at every shop, so one that is closed, or
 * killed with the app, can still be carried on. It gives the slot up when it
 * ends or is thrown away. Read at start (the files are small); written on a
 * thread of their own, in order.
 */
class SoloSaves(private val dir: File) {
    private val disk = Executors.newSingleThreadExecutor { r -> Thread(r, "solo-save").apply { isDaemon = true } }
    private val lock = Any()
    private val slots = arrayOfNulls<SoloSave>(SLOTS + 1)
    private val flow: MutableStateFlow<List<SoloSave>>

    /** Newest first. */
    val saves: StateFlow<List<SoloSave>>

    init {
        for (slot in 1..SLOTS) slots[slot] = read(slot)
        flow = MutableStateFlow(sorted())
        saves = flow
    }

    /** The lowest slot with nothing in it, or -1 when all are taken. */
    fun free(): Int = synchronized(lock) {
        (1..SLOTS).firstOrNull { slots[it] == null } ?: -1
    }

    fun get(slot: Int): SoloSave? = synchronized(lock) { slots.getOrNull(slot) }

    fun write(slot: Int, cp: Checkpoint, now: Long = System.currentTimeMillis()) {
        if (slot !in 1..SLOTS) return
        synchronized(lock) {
            slots[slot] = SoloSave(slot, now, cp)
            flow.value = sorted()
        }
        val body = cp.write()
        val bytes = ByteBuffer.allocate(8 + body.size).order(ByteOrder.LITTLE_ENDIAN).putLong(now).put(body).array()
        disk.execute {
            val target = fileOf(slot)
            val next = File(dir, target.name + ".new")
            try {
                next.writeBytes(bytes)
                if (!next.renameTo(target)) next.copyTo(target, overwrite = true)
            } catch (_: IOException) {
            }
        }
    }

    fun delete(slot: Int) {
        if (slot !in 1..SLOTS) return
        synchronized(lock) {
            slots[slot] = null
            flow.value = sorted()
        }
        disk.execute { fileOf(slot).delete() }
    }

    /** Waits for every write so far to reach the disk. For tests. */
    internal fun settle() {
        disk.submit {}.get()
    }

    private fun sorted() = (1..SLOTS).mapNotNull { slots[it] }.sortedByDescending { it.savedAt }

    private fun fileOf(slot: Int) = File(dir, "solo-$slot.save")

    private fun read(slot: Int): SoloSave? {
        val bytes = try {
            fileOf(slot).takeIf { it.exists() }?.readBytes()
        } catch (_: IOException) {
            null
        } ?: return null
        if (bytes.size <= 8) return null
        val savedAt = ByteBuffer.wrap(bytes, 0, 8).order(ByteOrder.LITTLE_ENDIAN).long
        val cp = Checkpoint.read(bytes.copyOfRange(8, bytes.size)) ?: return null
        return SoloSave(slot, savedAt, cp)
    }

    companion object {
        const val SLOTS = 5
    }
}
