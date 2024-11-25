package com.cliptracer.ClipTracer


data class UIState(
    val businessState: BusinessState = BusinessState(
        settings = mapOf(),
        protuneSettings = emptyMap(),
        currentGoPro = "",
        bleConnected = false,
        phoneDiskSpace = 0,
        showTriggerOverlay = false,
        triggerOverlayText = "",
        showBluetoothDevicesScreen = false,
        bluetoothDeviceList = mutableListOf(),
        artist = "Not Connected",
        title = "Not Connected",
        recording = false

    ),
    val showSettings: Boolean = false
)
