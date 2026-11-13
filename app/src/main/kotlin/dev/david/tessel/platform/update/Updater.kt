package dev.david.tessel.platform.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.david.tessel.core.update.Versions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A release on GitHub that is newer than what is installed. */
class Release(val version: String, val notes: String, val url: String, val bytes: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val done: Float) : UpdateState

    /** Android needs the player to allow Tessel to install apps, once. */
    data class NeedsPermission(val release: Release) : UpdateState
    data object Installing : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Updates from the project's GitHub releases. Nothing happens until the
 * player taps Check for Updates: then it asks GitHub for the latest release,
 * compares versions, downloads the APK and hands it to Android's installer,
 * which asks the player to confirm. This is the only time Tessel goes online.
 */
class Updater(private val activity: ComponentActivity) {
    private val stateFlow = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = stateFlow

    val installed: String = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "0"

    /** Test builds are a separate app; an update installs the regular Tessel next to them. */
    val testBuild = activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private var job: Job? = null

    init {
        InstallBus.listener = { result -> onInstallResult(result) }
    }

    fun check() {
        if (job?.isActive == true) return
        stateFlow.value = UpdateState.Checking
        job = activity.lifecycleScope.launch {
            stateFlow.value = try {
                val latest = withContext(Dispatchers.IO) { fetchLatest() }
                if (latest != null && Versions.newer(latest.version, installed)) UpdateState.Available(latest) else UpdateState.UpToDate
            } catch (e: IOException) {
                UpdateState.Failed("Could not reach GitHub. Check the internet connection and try again.")
            } catch (e: org.json.JSONException) {
                UpdateState.Failed("GitHub sent something unexpected. Try again later.")
            }
        }
    }

    fun install(release: Release) {
        if (job?.isActive == true) return
        if (!activity.packageManager.canRequestPackageInstalls()) {
            stateFlow.value = UpdateState.NeedsPermission(release)
            return
        }
        job = activity.lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { download(release) }
                stateFlow.value = UpdateState.Installing
                withContext(Dispatchers.IO) { hand(file) }
            } catch (e: IOException) {
                stateFlow.value = UpdateState.Failed("The download stopped. Try again.")
            } catch (e: SecurityException) {
                stateFlow.value = UpdateState.NeedsPermission(release)
            }
        }
    }

    /** Opens the system page where installing apps from Tessel is allowed. */
    fun openPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
        runCatching { activity.startActivity(intent) }
    }

    /** Back from the permission page: go on if it was granted. */
    fun recheckPermission() {
        val now = stateFlow.value
        if (now is UpdateState.NeedsPermission && activity.packageManager.canRequestPackageInstalls()) install(now.release)
    }

    private fun fetchLatest(): Release? {
        val connection = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Tessel")
        }
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return null
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (!asset.getString("name").endsWith(".apk")) continue
                return Release(
                    version = json.getString("tag_name").trimStart('v', 'V'),
                    notes = json.optString("body").trim(),
                    url = asset.getString("browser_download_url"),
                    bytes = asset.optLong("size"),
                )
            }
            return null
        } finally {
            connection.disconnect()
        }
    }

    private fun download(release: Release): File {
        val file = File(activity.cacheDir, "update.apk")
        file.delete()
        val connection = (URL(release.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "Tessel")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.bytes
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var got = 0L
                    var shown = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        got += n
                        val percent = if (total > 0) (got * 100 / total).toInt() else 0
                        if (percent != shown) {
                            shown = percent
                            stateFlow.value = UpdateState.Downloading(release, percent / 100f)
                        }
                    }
                }
            }
            return file
        } finally {
            connection.disconnect()
        }
    }

    /** Writes the APK into an install session; Android then asks the player to confirm. */
    private fun hand(file: File) {
        val installer = activity.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(RELEASE_PACKAGE)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("tessel.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            // The installer fills in the result, so the intent has to be mutable; it is explicit, so that is safe.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val result = PendingIntent.getBroadcast(activity, id, Intent(activity, InstallReceiver::class.java), flags)
            session.commit(result.intentSender)
        }
    }

    private fun onInstallResult(result: InstallResult) {
        when (result) {
            is InstallResult.Confirm -> runCatching { activity.startActivity(result.intent) }
            InstallResult.Done -> stateFlow.value = UpdateState.Idle
            is InstallResult.Failed -> stateFlow.value = UpdateState.Failed(result.message)
        }
    }

    companion object {
        private const val LATEST = "https://api.github.com/repos/davidsteenvoorden/tessel/releases/latest"
        private const val RELEASE_PACKAGE = "dev.david.tessel"
        private const val TIMEOUT_MS = 15_000
    }
}

sealed interface InstallResult {
    /** Android wants the player to confirm; this is the screen to show them. */
    class Confirm(val intent: Intent) : InstallResult
    data object Done : InstallResult
    class Failed(val message: String) : InstallResult
}

/** Carries the installer's answer from [InstallReceiver] back to the [Updater] on screen. */
object InstallBus {
    @Volatile var listener: ((InstallResult) -> Unit)? = null
}

/** Hears back from the system installer. Declared in the manifest, not exported. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                } ?: return
                InstallResult.Confirm(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> InstallResult.Done
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallResult.Failed("The update was cancelled.")
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                InstallResult.Failed("This copy of Tessel was signed differently. Uninstall it, then install the new version from GitHub.")
            PackageInstaller.STATUS_FAILURE_STORAGE -> InstallResult.Failed("Not enough free space for the update.")
            else -> InstallResult.Failed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "The update did not install.")
        }
        val listener = InstallBus.listener
        if (listener != null) {
            listener(result)
        } else if (result is InstallResult.Confirm) {
            runCatching { context.startActivity(result.intent) }
        }
    }
}
