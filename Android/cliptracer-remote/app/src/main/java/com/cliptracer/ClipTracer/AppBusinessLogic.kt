package com.cliptracer.ClipTracer

import android.util.Log
import TimeProvider
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.edit


data class BusinessState(
    var settings: Map<String, String> = mapOf(),
    var protuneSettings: Map<String, String> = mapOf(),
    var cameraStatus: Map<String, String> = mapOf(),
    var bleConnected: Boolean,
    var currentGoPro: String,
    var phoneDiskSpace: Long,
    var showTriggerOverlay: Boolean = false,
    var triggerOverlayText: String = "",
    var showBluetoothDevicesScreen: Boolean = true,
    val bluetoothDeviceList: List<ScanResult> = listOf(),
    val artist: String = "",
    val title: String = "",
    val recording: Boolean = false
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
    private val _businessState = MutableStateFlow<BusinessState?>(null)
    val businessStateFlow: StateFlow<BusinessState?> = _businessState.asStateFlow()
    private val businessStateUpdatelock = Any()

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
    private var queryingGoProTimeJob: Job? = null
    private var startGoProBLEReconnectionJob: Job? = null
    private var goproReconnectJob: Job? = null
    private var populatingStateJob: Job? = null

    var GOPRO_DEFAULT_QUERYING_INTERVAL = 3000L
    var GOPRO_QUERYING_INTERVAL = GOPRO_DEFAULT_QUERYING_INTERVAL

    private var bleConnected = false
    private var currentGoPro = ""


    var showTriggerOverlay = false
    var triggerOverlayText = ""
    var showBluetoothDevicesScreen = (!bleConnected)


    fun populateBusinessState(){
        synchronized(businessStateUpdatelock) {
            val newBusinessState = BusinessState(
                settings = settingsManager.settings,
                protuneSettings = goproBleManager.currentSettingsFormatted,
                cameraStatus = goproBleManager.currentStatusesFormatted,
                phoneDiskSpace = getPhoneDiskSpace(),
                currentGoPro = DataStore.currentGoProBLEName?:"",
                bleConnected = bleConnected,
                showTriggerOverlay = showTriggerOverlay,
                triggerOverlayText = triggerOverlayText,
                showBluetoothDevicesScreen = showBluetoothDevicesScreen,
                bluetoothDeviceList = goproBleManager.ble.foundDevices,
                artist = goproBleManager.silentAudioService?.playerArtistText ?: "",
                title = goproBleManager.silentAudioService?.playerTitleText ?: "",
                recording = goproBleManager.silentAudioService?.isPlaying() ?: false
            )
                _businessState.value = newBusinessState
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

    private fun startQueryingGoProTime(){
        queryingGoProTimeJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive)
            {
                if(bleConnected){
                    // Run the BLE operations asynchronously.
                    goproBleManager.checkGoProTime()
                    delay(15000)
                }
            }
        }
    }

    private fun stopQueryingGoProTime() {
        queryingGoProTimeJob?.cancel()
        queryingGoProTimeJob = null
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
        if (!DataStore.intentiousSleep && !bleConnected || DataStore.lastConnectedGoProBLEMac == null ) {
            try{
                if(DataStore.lastConnectedGoProBLEMac != null && settingsManager.settings["target_gopro"] == DataStore.currentGoProBLEName)
                {
                    Log.d("","goproConnectCached lastConnectedGoProBLEMac ${DataStore.lastConnectedGoProBLEMac}")
                    goproBleManager.connectGoProCached(DataStore.lastConnectedGoProBLEMac!!, DataStore.currentGoProBLEName?:"")
                }
                else{
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
        stopQueryingGoProTime()
        ble.cleanup()

    }

    fun startPopulatingState(){
        populatingStateJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                populateBusinessState()
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
        startQueryingGoProTime()
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
        goproBleManager.titleText = "Ready"
        coroutineScope.launch(Dispatchers.IO) {
            delay(3000)
            goproBleManager.checkGoProTime() //time sync
        }
        coroutineScope.launch(Dispatchers.IO) {
            if(DataStore.intentiousSleep){
//                delay(1000)
                DataStore.intentiousSleep = false
                goproBleManager.startRecording()
            }
            else{
                goproBleManager.stopRecording()
            }
//            enableGoProWifi()
            updateTargetGopro(goproName)
        }
        updateSettings(settingsManager.settings)
    }

    private fun handleGoProDisconnected(device: BluetoothDevice) {
        Log.d("","gopro disconnected handler")
        currentGoPro = ""
        bleConnected = false
        if(!DataStore.intentiousSleep)
        {
            showBluetoothDevicesScreen = true
        }

    }


    fun startRecording() {
        GOPRO_QUERYING_INTERVAL = 1000
        CoroutineScope(Dispatchers.Main).launch {
            goproBleManager.titleText = "Starting"
            goproBleManager.silentAudioService?.updateMetadataAndNotification(goproBleManager.goproStatusShortened, goproBleManager.titleText)
            goproBleManager.silentAudioService?.playIfNotYet()
        }
        CoroutineScope(Dispatchers.IO).launch {
            goproBleManager.startRecording()
        }
    }

    fun stopRecording(powerOffAfter : Boolean = true){
        GOPRO_QUERYING_INTERVAL = GOPRO_DEFAULT_QUERYING_INTERVAL
        CoroutineScope(Dispatchers.Main).launch {
            goproBleManager.titleText = "Stopping"
            goproBleManager.silentAudioService?.pauseIfNotYet()
            goproBleManager.silentAudioService?.updateMetadataAndNotification(goproBleManager.goproStatusShortened, goproBleManager.titleText)
            if(powerOffAfter){
                Handler(Looper.getMainLooper()).postDelayed({
                    goproPowerOff()
                }, 2000)
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            goproBleManager.stopRecording()
        }
    }

    fun addHighlight(){
        CoroutineScope(Dispatchers.IO).launch {
            goproBleManager.addHighlight()
        }
    }

    fun powerOffOrOn(){
        if (bleConnected){
            stopRecording()
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                goproBleManager.titleText = "Waking up"
                DataStore.intentiousSleep = false
                goproBleManager.silentAudioService?.updateMetadataAndNotification(goproBleManager.goproStatusShortened, goproBleManager.titleText)
                if(DataStore.lastConnectedGoProBLEMac != null && settingsManager.settings["target_gopro"] == DataStore.currentGoProBLEName)
                {
                    Log.d("","goproConnectCached lastConnectedGoProBLEMac ${DataStore.lastConnectedGoProBLEMac}")
                    goproBleManager.connectGoProCached(DataStore.lastConnectedGoProBLEMac!!, DataStore.currentGoProBLEName?:"")
                }
                else{
                    Log.d("","goproConnect")
                    goproBleManager.connectGoPro(settingsManager.settings["target_gopro"]?:"any")
                }
            }
        }
    }

    fun goproPowerOff() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                goproBleManager.goproPowerOff()?.let { file ->
                    DataStore.intentiousSleep = true
                    goproBleManager.titleText = "In sleep"
                    goproBleManager.silentAudioService?.updateMetadataAndNotification(goproBleManager.goproStatusShortened, goproBleManager.titleText)
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
            populateBusinessState()
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

    fun setGoProToConnect(gopro: ScanResult){
      goproBleManager.goproToConnect = gopro
    }

    fun updateShowBluetoothDevicesScreen(value: Boolean){
        showBluetoothDevicesScreen = value
        populateBusinessState()
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
                populateBusinessState()
                goproBleManager.connectGoPro("any")
                delay(3000)
            }

        }
    }

    fun setBeepDuringRecording(value: Boolean){
        goproBleManager.silentAudioService?.setBeep(value)

    }
}

