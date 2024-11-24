package com.cliptracer.ClipTracer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Bluetooth

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat.getSystemService
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun format_kb(kilobytes: Long): String {
    return when {
        kilobytes < 1024 -> "$kilobytes KB"
        kilobytes < 1024 * 1024 -> "${kilobytes / 1024} MB"
        else -> {
            val gb = kilobytes / (1024.0 * 1024.0)
            if (gb > 10) String.format("%.0f GB", gb) // Integer part only for numbers > 10
            else String.format("%.1f GB", gb) // One decimal place for numbers <= 10
        }
    }
}


@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedOverlay(show: Boolean, text: String) {
    if (show) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)) // Semi-transparent background
        ) {
            AnimatedVisibility(
                visible = show,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Text(
                    text,
                    fontSize = 24.sp,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}




// Define an enum to represent the current screen
enum class CurrentScreen {
    WELCOME, BLUETOOTH_DEVICES, MAIN
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun BluetoothDevicesScreen(mainIntent: MainIntent, scannedDevices: List<ScanResult>,
                           onTap: (ScanResult) -> Unit) {

    val headerTitle = remember { mutableStateOf("Bluetooth Devices") }

    BackHandler(enabled = true) {
        mainIntent.setShowBluetoothDevicesScreen(false)
    }

    Column {
        TopAppBar(
            title = { Text(headerTitle.value) },
        )

        val bleConnected = mainIntent.state.value.businessState.bleConnected
        val targetGopro = mainIntent.state.value.businessState.settings["target_gopro"]
        val displayText = if (targetGopro == "any") "GoPro devices will appear here." else "Going to autoconnect to ${targetGopro}. You can put the phone in the pocket now."

        Box(modifier = Modifier.weight(1f)) {
            if (scannedDevices.isEmpty() && !bleConnected) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        displayText,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.Gray,
                            fontSize = 16.sp
                        ),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn {
                    items(scannedDevices) { scannedDevice ->
                        ListItem(
                            modifier = Modifier
                                .clickable {
                                    headerTitle.value = "Connecting to ${scannedDevice.device.name ?: scannedDevice.device.address}..."
                                    CoroutineScope(Dispatchers.Main).launch {
                                        delay(15000)
                                        headerTitle.value = "Please try again"
                                    }
                                    onTap(scannedDevice)
                                }
                                .padding(vertical = 8.dp, horizontal = 16.dp), // Added horizontal padding here
                            headlineContent = {
                                Text(
                                    text = scannedDevice.device.name ?: scannedDevice.device.address,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(mainIntent: MainIntent) {
    var currentScreen by remember { mutableStateOf(CurrentScreen.WELCOME) }

    // This effect runs every time showBluetoothDevicesScreen changes.
    LaunchedEffect(mainIntent.state.value.businessState.showBluetoothDevicesScreen) {
        currentScreen = if (mainIntent.state.value.businessState.showBluetoothDevicesScreen) {
            CurrentScreen.BLUETOOTH_DEVICES
        } else {
            CurrentScreen.MAIN
        }
    }

    when (currentScreen) {
        CurrentScreen.WELCOME -> WelcomeScreen(onContinueClicked = { mainIntent.setShowBluetoothDevicesScreen(true) })
        CurrentScreen.BLUETOOTH_DEVICES -> {
            // Make sure the BluetoothDevicesScreen updates the state to false after connecting to a device
            BluetoothDevicesScreen(
                mainIntent = mainIntent,
                scannedDevices = mainIntent.state.value.businessState.bluetoothDeviceList,
                onTap = { tappedDevice ->
                    mainIntent.connectTappedGoPro(tappedDevice)
                    // This might set some state that triggers the LaunchedEffect above
                }
            )
        }
        CurrentScreen.MAIN -> {
            ClipTracerApp(mainIntent = mainIntent, onBackClicked = {
                mainIntent.scanShowDevices()
            })
        }
    }
}


@Composable
fun WelcomeScreen(onContinueClicked: () -> Unit) {
    // Your welcome screen layout with a button
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Welcome to my app", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onContinueClicked) {
            Text("Continue")
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipTracerApp(mainIntent: MainIntent, onBackClicked: () -> Unit) {
    // State for showing the info popup
    val showInfoPopup = remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Use a Row to center the title and place the icon on the far right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(DataStore.currentGoProBLEName ?: "", textAlign = TextAlign.Center)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Info icon that toggles the visibility of the popup
                    IconButton(onClick = { showInfoPopup.value = true }) {
                        Icon(Icons.Filled.Info, "Info")
                    }
                }
            )
        }
    ) {
        if (showInfoPopup.value) {
            AlertDialog(
                onDismissRequest = { showInfoPopup.value = false },
                confirmButton = {
                    Button(onClick = { showInfoPopup.value = false }) {
                        Text("Close")
                    }
                },
                text = {
                    Column {
                        Text("""
                            Camera Status:
                            
                            Ready - powered on, ready to record
                            Sleep - powered off, ready to record
                            Not Connected - camera not ready
                        """.trimIndent())

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("""
                            Camera Settings:
                            
                            Field 1: resolution
                            Field 2: fps
                            Field 3: lenses
                            Field 4: battery
                            Field 5: memory left                        """.trimIndent())

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("""
                            Lenses Abbreviations:
                            
                            w - wide
                            n - narrow
                            l - linear
                            sv - super view
                            msv - max super view
                            lev - linear + horizon leveling
                            loc - linear + horizon loc
                            hv - hyper view
                        """.trimIndent())

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("""
                            Stay tuned:
                            
                            cliptracer.com
                            Youtube: @Cliptracer
                        """.trimIndent())
                    }
                }
            )
        }

        var showButtonPressedOverlay by remember { mutableStateOf(false) }
        val uiState = mainIntent.state.value
        val context = LocalContext.current

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(context, VibratorManager::class.java) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            getSystemService(context, Vibrator::class.java) as Vibrator
        }

        fun vibrate(pattern: LongArray) {
            if (vibrator.hasVibrator()) {
                // For newer versions (API 26+), use VibrationEffect
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(pattern, -1) // -1: Do not repeat
                    vibrator.vibrate(effect)

                } else {
                    vibrator.vibrate(pattern, -1)
                }
            }
        }

        if (uiState.businessState.showBluetoothDevicesScreen) {
            BluetoothDevicesScreen(mainIntent = mainIntent,
                scannedDevices = uiState.businessState.bluetoothDeviceList,
                onTap = { tappedDevice ->
                    mainIntent.connectTappedGoPro(tappedDevice)
                })
        } else {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.size(20.dp))

                TextLabel(
                    text1 = uiState.businessState.currentGoPro,
                    icon1 = if (uiState.businessState.bleConnected) {
                        Icons.Filled.Bluetooth
                    } else {
                        Icons.Filled.BluetoothDisabled
                    },
                    scale = 20.sp
                )
                Spacer(modifier = Modifier.size(40.dp))

                Text(
                    text = uiState.businessState.artist,
                    style = MaterialTheme.typography.titleLarge.copy(),
                    color = Color(0xFFFFA500), // Orange color for the text
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                )

                TextLabel(
                    text1 = uiState.businessState.title,
                    icon1 = Icons.Filled.FiberManualRecord,
                    scale = 20.sp
                )



                var isRecording by remember { mutableStateOf(false) }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                ) {
                    Button(
                        onClick = { mainIntent.addHighlight() })
                    { Text(text = "Highlight") }
                    Spacer(modifier = Modifier.size(6.dp))
                    Button(onClick = {
                        if (isRecording) {
                            // Stop recording
                            mainIntent.stopRecording()
                            mainIntent.setTriggerOverlayText("Stop recording")
                            vibrate(longArrayOf(0, 100, 150, 100))
                        } else {
                            // Start recording
                            mainIntent.startRecording()
                            mainIntent.setTriggerOverlayText("Start recording")
                            vibrate(longArrayOf(0, 200))
                        }
                        mainIntent.showTriggerOverlay()
                        showButtonPressedOverlay = true
                        isRecording = !isRecording // Toggle the recording state
                    }) {
                        Text(text = "Shutter")
                    }
                    Spacer(modifier = Modifier.size(6.dp))

                    Button(
                        onClick = { mainIntent.addHighlight() })
                    { Text(text = "Highlight") }
                }
            }
            if (uiState.businessState.showTriggerOverlay) {
                AnimatedOverlay(show = true, text = uiState.businessState.triggerOverlayText)

                LaunchedEffect(key1 = uiState.businessState.triggerOverlayText) {
                }
            }
        }
    }
}