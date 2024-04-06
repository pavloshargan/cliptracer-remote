package com.cliptracer.ClipTracer


data class UIState(
    val healthState: HealthState = HealthState(
        settings = mapOf(),
        protuneSettings = emptyMap(),
        currentGoPro = "",
        bleConnected = false,
        phoneDiskSpace = 0,
        showTriggerOverlay = false,
        triggerOverlayText = "",
        showBluetoothDevicesScreen = false,
        bluetoothDeviceList = mutableListOf()

    ),
    val showSettings: Boolean = false
)
