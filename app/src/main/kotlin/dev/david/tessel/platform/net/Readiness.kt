package dev.david.tessel.platform.net

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/** What has to be true on this phone before it can find another one. */
object Readiness {
    /** Runtime permissions Nearby Connections needs on this Android version. */
    fun permissions(): Array<String> {
        val list = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_ADVERTISE
            list += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.NEARBY_WIFI_DEVICES
        if (Build.VERSION.SDK_INT <= 32) list += Manifest.permission.ACCESS_FINE_LOCATION
        // Only enforced for the Wi-Fi LAN medium on Android 17 and later.
        if (Build.VERSION.SDK_INT >= 37) list += "android.permission.ACCESS_LOCAL_NETWORK"
        return list.toTypedArray()
    }

    fun missingPermissions(context: Context): List<String> =
        permissions().filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

    fun playServices(context: Context): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    fun bluetoothOn(context: Context): Boolean =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    fun wifiOn(context: Context): Boolean =
        context.applicationContext.getSystemService(WifiManager::class.java)?.isWifiEnabled == true

    /** Older Android versions only discover nearby devices with location switched on. */
    fun locationOk(context: Context): Boolean {
        if (Build.VERSION.SDK_INT > 32) return true
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled else lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }
}
