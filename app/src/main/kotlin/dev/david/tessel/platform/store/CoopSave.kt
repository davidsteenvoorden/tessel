package dev.david.tessel.platform.store

import android.content.Context
import dev.david.tessel.core.sim.Checkpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * The one co-op run this phone hosted that can still be carried on. Updated
 * at every shop while the run lasts, so it survives the app being closed or
 * the phone dying; cleared when the run ends or the host leaves it for good.
 * Reads and writes happen on a thread of their own, in order.
 */
class CoopSave(context: Context) {
    private val file = File(context.filesDir, "coop.save")
    private val disk = Executors.newSingleThreadExecutor { r -> Thread(r, "coop-save").apply { isDaemon = true } }
    private val flow = MutableStateFlow<Checkpoint?>(null)
    val saved: StateFlow<Checkpoint?> = flow

    init {
        disk.execute {
            val bytes = try {
                if (file.exists()) file.readBytes() else null
            } catch (_: IOException) {
                null
            }
            flow.value = bytes?.let { Checkpoint.read(it) }
        }
    }

    fun write(cp: Checkpoint) {
        flow.value = cp
        val bytes = cp.write()
        disk.execute {
            // Written beside it first, so a phone that dies mid-write still has the old save.
            val next = File(file.parentFile, file.name + ".new")
            try {
                next.writeBytes(bytes)
                if (!next.renameTo(file)) next.copyTo(file, overwrite = true)
            } catch (_: IOException) {
            }
        }
    }

    fun clear() {
        flow.value = null
        disk.execute { file.delete() }
    }
}
