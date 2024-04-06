package com.cliptracer.ClipTracer

import android.util.Log
import TimeProvider
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.cliptracer.ClipTracer.gopronetwork.Bluetooth
import com.cliptracer.ClipTracer.goproutil.goproSettingsOptionsMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

data class HealthState(
    var settings: Map<String, String> = mapOf(),
    var protuneSettings: Map<String, String> = mapOf(),
    var cameraStatus: Map<String, String> = mapOf(),
    var bleConnected: Boolean,
    var currentGoPro: String,
    var phoneDiskSpace: Long,
    var showTriggerOverlay: Boolean = false,
    var triggerOverlayText: String = "",
    var showBluetoothDevicesScreen: Boolean = true,
    val bluetoothDeviceList: List<ScanResult> = listOf()
    )

@SuppressLint("MissingPermission")
class AppBusinessLogic(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val ble: Bluetooth
) {
    
    private val lifecycleOwner: LifecycleOwner = context as? LifecycleOwner
        ?: throw IllegalStateException("Context must be a LifecycleOwner")

    private val coroutineScope = lifecycleOwner.lifecycleScope
    private val _healthState = MutableStateFlow<HealthState?>(null)
    val healthStateFlow: StateFlow<HealthState?> = _healthState.asStateFlow()
    private val healthStateUpdatelock = Any()

    private var goproBleManager = GoProBleManager(
        ble,
        onGoproConnect = ::handleGoProConnected,
        onGoproDisconnect = ::handleGoProDisconnected
    )

    fun setService(service: SilentAudioService) {
        goproBleManager.silentAudioService = service
    }


    private var keepAliveJob: Job? = null
    private var queryingGoProSettingsAndStatusesJob: Job? = null
    private var startGoProBLEReconnectionJob: Job? = null
    private var goproReconnectJob: Job? = null
    private var populatingStateJob: Job? = null

    var GOPRO_DEFAULT_QUERYING_INTERVAL = 3000L
    var GOPRO_QUERYING_INTERVAL = GOPRO_DEFAULT_QUERYING_INTERVAL



    private var leftToUpload: Int = -1
    private var postponedUploads: Int = -1
    private var allMp4Size: Double = 0.0
    private var allMp4Count: Int = 0
    private var percentsVideoUploading: Int? = null
    private var statusVideoUploading: String? = null
    private var currentUploadingSpeed: Double? = null
    private var bleConnected = false
    private var currentGoPro = ""


    var showTriggerOverlay = false
    var triggerOverlayText = ""
    var showBluetoothDevicesScreen = (!bleConnected)


    private fun populateHealthState(){
        synchronized(healthStateUpdatelock) {
            val newHealthState = HealthState(
                settings = settingsManager.settings,
                protuneSettings = goproBleManager.currentSettingsFormatted,
                cameraStatus = goproBleManager.currentStatusesFormatted,
                phoneDiskSpace = getPhoneDiskSpace(),
                currentGoPro = DataStore.currentGoProBLEName?:"",
                bleConnected = bleConnected,
                showTriggerOverlay = showTriggerOverlay,
                triggerOverlayText = triggerOverlayText,
                showBluetoothDevicesScreen = showBluetoothDevicesScreen,
                bluetoothDeviceList = goproBleManager.ble.foundDevices
            )
                _healthState.value = newHealthState
            }
    }



    private suspend fun delayQueryingGoPro(){
        var elapsedTime = 0L
        val checkInterval = 50L
        while (elapsedTime < (GOPRO_QUERYING_INTERVAL/2)) {
            delay(checkInterval)
            elapsedTime += checkInterval
        }
    }
    private fun startQueryingGoProSettingsAndStatuses(){
        queryingGoProSettingsAndStatusesJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive)
            {
                if(bleConnected){
                    // Run the BLE operations asynchronously.
                    goproBleManager.getStatus()
                    delayQueryingGoPro() // querying statuses and settings with some interval,
                    goproBleManager.getSettings()            // so the gopro ble server is not overloaded
                    delayQueryingGoPro()
                }
            }

        }

    }

    private fun stopQueryingGoProSettingsAndStatuses() {
        queryingGoProSettingsAndStatusesJob?.cancel()
        queryingGoProSettingsAndStatusesJob = null
    }
    
    private fun startKeepingGoProAlive(){
        keepAliveJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                if(bleConnected){
                    launch(Dispatchers.IO) {
                        print("keep alive sent")
                        goproBleManager.goproKeepAlive()
                    }
                }
                delay(3000)
            }
        }
    }

    private fun stopKeepingGoProAlive(){
        keepAliveJob?.cancel()
        keepAliveJob = null
    }


    private suspend fun goproCheckReconnect() {
        Log.d("","goproCheckReconnect")
        if (!bleConnected || DataStore.lastConnectedGoProBLEMac == null ) {
            try{
                if(DataStore.lastConnectedGoProBLEMac != null && settingsManager.settings["target_gopro"] == DataStore.currentGoProBLEName)
                {
                    Log.d("","goproConnectCached lastConnectedGoProBLEMac ${DataStore.lastConnectedGoProBLEMac}")
                    goproBleManager.connectGoProCached(DataStore.lastConnectedGoProBLEMac!!, DataStore.currentGoProBLEName?:"")
                }
                else{
                    populateHealthState()
                    Log.d("","goproConnect")
                    goproBleManager.connectGoPro(settingsManager.settings["target_gopro"]?:"any")
                }
            }
            catch (e: Exception) {
                Log.w("","Error occurred connecting to GoPro: ${e.message}")
            }
        }

    }

    fun cleanup(currentContext: Context){
        stopPopulatingState()
        stopGoProBLEReconnection()
        stopKeepingGoProAlive()
        stopQueryingGoProSettingsAndStatuses()
        ble.cleanup()

    }

    fun startPopulatingState(){
        populatingStateJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                populateHealthState()
                delay(500)
            }
        }
    }

    fun stopPopulatingState() {
        populatingStateJob?.cancel()
        populatingStateJob = null

    }



        init { startPopulatingState()
        startGoProBLEReconnection()
        startKeepingGoProAlive()
        startQueryingGoProSettingsAndStatuses()
        startPopulatingState()
    }



    private fun startGoProBLEReconnection() {
        startGoProBLEReconnectionJob = coroutineScope.launch(Dispatchers.IO){
            while (isActive) {
                if (goproReconnectJob?.isActive != true) {
                    goproReconnectJob = launch {
                        goproCheckReconnect()
                    }
                }
                delay(15000)
            }
        }
    }

    private fun stopGoProBLEReconnection() {
        startGoProBLEReconnectionJob?.cancel()
        startGoProBLEReconnectionJob = null
        goproReconnectJob?.cancel()
        goproReconnectJob = null
    }

    fun updateTargetGoPro(targetGopro: String){
        settingsManager.settings["target_gopro"] = targetGopro
        updateSettings(settingsManager.settings)
    }

    private fun handleGoProConnected(goproName: String) {
        Log.d("","gopro connected handler $goproName")
        currentGoPro = goproName
        bleConnected = true
        showBluetoothDevicesScreen = false
        coroutineScope.launch(Dispatchers.IO) {
            stopRecording()
            enableGoProWifi()
            updateTargetGopro(goproName)
        }
        updateSettings(settingsManager.settings)
    }

    private fun handleGoProDisconnected(device: BluetoothDevice) {
        Log.d("","gopro disconnected handler")
        currentGoPro = ""
        bleConnected = false
        showBluetoothDevicesScreen = true

    }


    fun startRecording() {
        GOPRO_QUERYING_INTERVAL = 1000
        CoroutineScope(Dispatchers.IO).launch {
            goproBleManager.startRecording()
        }
    }

    fun stopRecording(){
        GOPRO_QUERYING_INTERVAL = GOPRO_DEFAULT_QUERYING_INTERVAL
        CoroutineScope(Dispatchers.IO).launch {
            goproBleManager.stopRecording()
        }
    }

    fun addHighlight(){
        CoroutineScope(Dispatchers.IO).launch {
            goproBleManager.addHighlight()
        }
    }

    fun goproPowerOff() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                goproBleManager.goproPowerOff()?.let { file ->
                    // Handle the result of the connection here
                }
            } catch (e: Exception) {
                // Handle any errors that occur during the connection process
            }
        }
    }

    fun updateSettings(newSettings: Map<String, String>, updatedOn: String? = null) {
        val currentTime = TimeProvider.getUTCTimeMilliseconds()
        CoroutineScope(Dispatchers.IO).launch {
            Log.d("","BL updateSettings.. newSettigns: $newSettings")
            //apply new settings here
                newSettings.keys.forEach { key ->
                    newSettings[key]?.let { newValue ->
                        if (goproSettingsOptionsMap.containsKey(key)) { // if protune setting
                            if(bleConnected){
                                goproBleManager.goproSetSetting(key, newValue)
                            if (key == "resolution" && newValue.startsWith("5")){ // wait more if resolution is 5k+ so other settings can adjust to it before we start applying nex settings
                                delay(1000)
                            }
                            else{
                                delay(300)
                            }
                        }
                    }
                }
            }

            settingsManager.updateSettings(newSettings, updatedOn)
            populateHealthState()
        }
    }

    fun getSettings(): Map<String, String> {
        Log.d("","BL getSettings..")
        return settingsManager.settings
    }

    private suspend fun enableGoProWifi(){
        Log.d("","enableGoProWifi - entered")
        val (success, ssid, password) = goproBleManager.enableWifi()
        Log.d("","enableGoProWifi - got ${success}, ${ssid}, ${password}")
        if (success) {
            if (ssid != null) {
                print("gopro ssid: ${ssid} password: ${password}")
            }
        }
    }

    private fun getPhoneDiskSpace(): Long {
        val path = Environment.getExternalStorageDirectory() // This represents the root of the external storage
        val stat = StatFs(path.path)

        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong

        val totalSpaceKb = (blockSize * totalBlocks) / 1024 // Calculate total space in kilobytes

        // Check if the total space is within the Int range
        return totalSpaceKb.toLong()
    }

    private fun getUsedRAM(): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val availableMemory = memoryInfo.availMem
        val totalMemory = memoryInfo.totalMem
        val usedMemory = totalMemory - availableMemory

        return formatBytesToReadableUnit(usedMemory)
    }

    private fun formatBytesToReadableUnit(bytes: Long): String {
        val kilobyte = 1024
        val megabyte = kilobyte * 1024
        val gigabyte = megabyte * 1024

        return when {
            bytes < kilobyte -> "$bytes B"
            bytes < megabyte -> "${bytes / kilobyte} KB"
            bytes < gigabyte -> "${bytes / megabyte} MB"
            else -> "${bytes / gigabyte} GB"
        }
    }
    fun updateTargetGopro(targetGopro: String){
        settingsManager.settings["target_gopro"] = targetGopro
        updateSettings(settingsManager.settings)
    }

    fun restartGopro() {
        goproPowerOff()
    }

    fun setGoProToConnect(gopro: ScanResult){
      goproBleManager.goproToConnect = gopro
    }

    fun updateShowBluetoothDevicesScreen(value: Boolean){
        showBluetoothDevicesScreen = value
        populateHealthState()
    }

    fun connectTappedGoPro(gopro: ScanResult)
    {
        ble.cleanup()
        updateTargetGoPro(gopro.device.name)
        goproBleManager.goproToConnect = gopro
    }

    fun scanShowDevices(){
        coroutineScope.launch(Dispatchers.Main){
            showBluetoothDevicesScreen = true
            while(showBluetoothDevicesScreen){
                showBluetoothDevicesScreen
                Log.d("","goproConnect")
                populateHealthState()
                goproBleManager.connectGoPro("any")
                delay(3000)
            }

        }
    }
}

