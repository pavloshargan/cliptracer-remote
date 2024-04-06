package com.cliptracer.ClipTracer

import TimeProvider
import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay


@SuppressLint("MissingPermission")
class MainIntent(private val appBusinessLogic: AppBusinessLogic, private val coroutineScope: CoroutineScope) {
    private val _state = mutableStateOf(UIState())
    val state: State<UIState> = _state

    init {
        CoroutineScope(Dispatchers.Main).launch {
            appBusinessLogic.healthStateFlow.collect { newHealthState ->
                newHealthState?.let { nonNullHealthState ->
                    // Update UI state here with non-null value
                    _state.value = _state.value.copy(healthState = nonNullHealthState)
                }
            }
        }
        scanShowDevices()
    }

    fun onSettingsChange(newSettings: Map<String, String>) {
        println("MI onSettingsChange.. $newSettings")
        appBusinessLogic.updateSettings(newSettings, "${TimeProvider.getUTCTimeMilliseconds()}")
    }

    // Function to load settings from SettingsManager and update AppState
    fun loadAndApplySettings() {
        Log.d("","MI loadAndApplySettings..")
        val settings = appBusinessLogic.getSettings()
        Log.d("","MI settings ${settings}")
    }

    fun setShowSettings(show: Boolean) {
        Log.d("","MI setShowSettings.. $show")
        _state.value = _state.value.copy(showSettings = show)
    }

    fun showTriggerOverlay(){
        CoroutineScope(Dispatchers.IO).launch {
            appBusinessLogic.showTriggerOverlay = true
            delay(750)
            appBusinessLogic.showTriggerOverlay = false
        }
    }

    fun setTriggerOverlayText(value: String){
        appBusinessLogic. triggerOverlayText = value
    }
    fun setShowBluetoothDevicesScreen(value: Boolean) {
        appBusinessLogic.updateShowBluetoothDevicesScreen(value)
    }

    fun connectTappedGoPro(gopro: ScanResult){
        appBusinessLogic.connectTappedGoPro(gopro)
    }

    fun scanShowDevices(){
        CoroutineScope(Dispatchers.IO).launch {
            appBusinessLogic.scanShowDevices()
        }
    }

    fun startRecording(){
        appBusinessLogic.startRecording()
    }
    fun stopRecording(){
        appBusinessLogic.stopRecording()

    }
    fun addHighlight(){
        appBusinessLogic.addHighlight()
    }
}