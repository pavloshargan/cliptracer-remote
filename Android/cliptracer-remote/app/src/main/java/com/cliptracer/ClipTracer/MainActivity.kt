package com.cliptracer.ClipTracer


import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.datadog.android.DatadogSite
import com.cliptracer.ClipTracer.gopronetwork.Bluetooth
import com.cliptracer.ClipTracer.goproutil.TwoWayDict
import com.cliptracer.ClipTracer.ui.theme.ClipTracerTheme
import kotlinx.coroutines.launch


fun hasPermissions(context: Context): Boolean {
    val requiredPermissions = arrayOf(
        android.Manifest.permission.BLUETOOTH_SCAN,
        android.Manifest.permission.BLUETOOTH_CONNECT,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
    )

    val result = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    if (!result){
        val activity = context.findActivity()
        activity?.let {
            ActivityCompat.requestPermissions(
                it,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        } ?: run {
            // Handle the case where the activity is null
        }
    }

    return result
}
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}


object DataStore {
    var lastConnectedGoProBLEMac: String? = null
    var currentGoProBLEName: String? = null
    var deviceId: String? = null
    var appVersion: String = "ClipTracer_0.1"
    var session_options = TwoWayDict<Int, String>()
    var goproVersion: String = "HERO10"
    var intentiousSleep = false
}

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {
    private lateinit var audioService: SilentAudioService
    private var bound: Boolean = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as SilentAudioService.LocalBinder
            this@MainActivity.audioService = binder.getService()
            bound = true
            // Pass the service to AppBusinessLogic
            appBusinessLogic.setService(this@MainActivity.audioService)
            this@MainActivity.audioService.setAppBusinessLogic(appBusinessLogic)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            bound = false
        }
    }
    fun bindService() {
        Intent(this, SilentAudioService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun getAndroidId(): String {
        return Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID)
    }

    lateinit var appBusinessLogic: AppBusinessLogic

    fun initialSetup(ble: Bluetooth) { // Removed the context parameter
        lifecycleScope.launch {
            val deviceId = getAndroidId()
            DataStore.deviceId = deviceId

            val serviceIntent = Intent(this@MainActivity, SilentAudioService::class.java)
            startService(serviceIntent)
            bindService()

            val settingsManager = SettingsManager(this@MainActivity)
            appBusinessLogic = AppBusinessLogic(this@MainActivity, settingsManager, ble)
            val mainIntent = MainIntent(appBusinessLogic, lifecycleScope)
            mainIntent.loadAndApplySettings()



            setContent {
                ClipTracerTheme {
                    MainScreen(mainIntent)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hasPermissions(this)

        val ble = Bluetooth.getInstance(this)

        lifecycleScope.launch {
            if (ble?.adapter?.isEnabled != true) {
                ble?.enableAdapter(object : Bluetooth.BluetoothEnableListener {
                    override fun onBluetoothEnabled() {
                        initialSetup(ble)
                    }
                })
            } else {
                initialSetup(ble)
            }
        }
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d("","onSaveInstanceState called, likely the Android cleaning up resources")
    }

    override fun onDestroy() {
        Log.d("MainActivity", "onDestroy started")
        if (isFinishing) {
            Log.d("MainActivity", "Activity is finishing")

            // Only unbind the service if the activity is finishing
            if (bound) {
                unbindService(connection)
                bound = false
            }

            // Clean up any other resources or listeners
            appBusinessLogic.cleanup(this)

            val stopServiceIntent = Intent(this, SilentAudioService::class.java).apply {
                action = SilentAudioService.ACTION_STOP_SERVICE
            }
            startService(stopServiceIntent)

            this@MainActivity.audioService.cleanupService()
        } else {
            Log.d("MainActivity", "Activity is being destroyed by the system or due to configuration change")
        }

        super.onDestroy()
        Log.d("MainActivity", "onDestroy end")
    }

}