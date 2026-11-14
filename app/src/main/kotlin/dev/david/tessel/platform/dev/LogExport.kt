package dev.david.tessel.platform.dev

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import dev.david.tessel.platform.store.Settings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Collects what is needed to look into a problem into one text file and hands it to the share sheet. */
object LogExport {
    const val CRASH_FILE = "crash.txt"

    /** Remembers a crash in a file, so the next export has the stack trace even after the app was killed. */
    fun installCrashNote(context: Context) {
        val file = File(context.filesDir, CRASH_FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file.writeText("${Date()} thread=${thread.name}\n${error.stackTraceToString()}") }
            previous?.uncaughtException(thread, error)
        }
    }

    fun write(context: Context, settings: Settings, version: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = context.getExternalFilesDir(null) ?: context.cacheDir
        val out = File(dir, "tessel-log-$stamp.txt")
        out.writeText(report(context, settings, version))
        return out
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Tessel log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun report(context: Context, settings: Settings, version: String) = buildString {
        appendLine("Tessel $version")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Settings: $settings")
        appendLine("Saved runs: ${context.filesDir.listFiles { f -> f.name.startsWith("solo-") }?.size ?: 0}")
        appendLine()
        val crash = File(context.filesDir, CRASH_FILE)
        if (crash.exists()) {
            appendLine("--- Last crash ---")
            appendLine(crash.readText())
        }
        appendLine("--- Trace ---")
        append(Trace.dump())
        appendLine()
        appendLine("--- Logcat ---")
        append(logcat())
    }

    private fun logcat(): String = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-v", "time", "-t", "2500").redirectErrorStream(true).start()
        process.inputStream.bufferedReader().readText().also { process.waitFor() }
    }.getOrElse { "unavailable: $it" }
}
