package com.cliptracer.ClipTracer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import com.cliptracer.ClipTracer.goproutil.TwoWayDict
import com.cliptracer.ClipTracer.goproutil.goproSettingsOptionsMap




@Composable
fun SettingsScreen(mainIntent: MainIntent) {
    BackHandler(enabled = true) {
        mainIntent.setShowSettings(false)
    }
    val originalSettings = mainIntent.state.value.businessState.settings
    var tempSettings by remember { mutableStateOf(originalSettings) }

    Scaffold(
        topBar = {
            ReusableTopAppBar(
                title = "Settings",
                onNavigationIconClick =  {mainIntent.setShowSettings(false)}
            )
        }
    ) { paddingValues ->
        SettingsView(
            settings = tempSettings,
            onSettingChange = { updatedSettings ->
                tempSettings = updatedSettings
            },
            onSave = {
                mainIntent.onSettingsChange(tempSettings)
                mainIntent.setShowSettings(false)
            },
            onDismissRequest = {
                mainIntent.setShowSettings(false)
            },
            onScanShowDevices = {
                mainIntent.scanShowDevices()
            },
            onCancel = {
                tempSettings = originalSettings // Revert any changes
                mainIntent.setShowSettings(false)
            },
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    settings: Map<String, String>,
    onSettingChange: (Map<String, String>) -> Unit,
    onSave: () -> Unit,
    onScanShowDevices: () -> Unit,
    onDismissRequest: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Settings") },
                navigationIcon = {
                    IconButton(onClick = { onDismissRequest() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            settings.entries.filter { it.key != "updated_on" }.forEach { (key, value) ->
                SettingItem(
                    settingKey = key,
                    settingAlias = key,
                    settingValue = value,
                    onValueChange = { newValue ->
                        // This will update the local copy of the settings
                        onSettingChange(settings.toMutableMap().apply { put(key, newValue) })
                    }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Advanced",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally) // Centers the text horizontally
            )
            Spacer(modifier = Modifier.height(5.dp))
            Button(
                onClick = {
                    onCancel()
                    onScanShowDevices() },
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),


                )
            {Text(text = "Connect GoPro")}

            Spacer(modifier = Modifier.height(40.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = onSave) {
                    Text(text = "Save")
                }

                Button(onClick = onCancel) {
                    Text(text = "Cancel")
                }

            }
        }
    }
}

@Composable
fun SettingItem(
    settingKey: String,
    settingAlias: String,
    settingValue: String,
    onValueChange: (String) -> Unit,
    backgroundColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    foregroundColor: Color = Color.Unspecified,
    scale: Float = 1f
) {
    // Convert lists in additionalOptionsMap to TwoWayDict
    val additionalOptionsMapConverted = mapOf(
        "upload_to_the_cloud" to TwoWayDict(0 to "Not Upload", 1 to "Upload Postponed(All)", 2 to "Upload Postponed(Odd)", 3 to "Upload Postponed(Even)"),
        "save_videos" to TwoWayDict(0 to "False", 1 to "True"),
        "remove_audio" to TwoWayDict(0 to "False", 1 to "True"),
        "connectivity_mode" to TwoWayDict(0 to "GoProWifi", 1 to "StadiumWifi", 2 to "GoProWifi30secMax", 3 to "GoProWifi3minMax", 4 to "GoProWifi15minMax", 5 to "Auto"),
        "session_name" to DataStore.session_options

    )
    // Merge the two maps
    val combinedOptionsMap = goproSettingsOptionsMap + additionalOptionsMapConverted

    // Determine if settingKey is in combinedOptionsMap
    val isOptionSetting = combinedOptionsMap.containsKey(settingKey)
    val options = combinedOptionsMap[settingKey]?.values ?: listOf()


    var expanded by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var tempValue by remember { mutableStateOf(settingValue) }
    var errorText by remember { mutableStateOf("") }

    fun validateInput(settingKey: String, inputValue: String): Boolean {
        when (settingKey) {
            "role_odd", "role_even" -> {
                if (!inputValue.matches(Regex("^[A-Za-z0-9]+$"))) {
                    errorText = "Invalid characters. Only letters and numbers are allowed."
                    return false
                }
            }
            "duration" -> {
                try {
                    val value = inputValue.toFloat()
                    if (value <= 0) {
                        errorText = "Duration must be a positive number."
                        return false
                    }
                } catch (e: NumberFormatException) {
                    errorText = "Invalid number format."
                    return false
                }
            }
            "cam_id" -> {
                try {
                    val value = inputValue.toInt()
                    if (value <= 0) {
                        errorText = "Cam ID must be a positive integer."
                        return false
                    }
                } catch (e: NumberFormatException) {
                    errorText = "Invalid number format."
                    return false
                }
            }
            "max_clip_size_allowed_mb" -> {
                try {
                    val value = inputValue.toFloat()
                    if (value <= 0) {
                        errorText = "Max Clip size must be a positive number."
                        return false
                    }
                } catch (e: NumberFormatException) {
                    errorText = "Invalid number format."
                    return false
                }
            }
        }
        return true
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .scale(scale)
            .background(Color.White), // Correct placement for the background color modifier
        border = if (borderColor != Color.Unspecified) BorderStroke(1.dp, borderColor) else null
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = settingAlias,
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                color = foregroundColor.takeUnless { it == Color.Unspecified } ?: LocalContentColor.current
            )
            Spacer(modifier = Modifier.width(8.dp))

            if (isOptionSetting) {
                Box(modifier = Modifier.weight(2f)) {
                    Text(
                        text = settingValue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true }
                            .padding(8.dp),
                        color = foregroundColor.takeUnless { it == Color.Unspecified } ?: LocalContentColor.current
                    )

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                onClick = {
                                    onValueChange(option)
                                    expanded = false
                                },
                                text = { Text(text = option) }
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = settingValue,
                    modifier = Modifier
                        .weight(2f)
                        .clickable { showDialog = true }
                        .padding(8.dp),
                    color = foregroundColor.takeUnless { it == Color.Unspecified } ?: LocalContentColor.current
                )
            }
        }
    }

    if (!isOptionSetting && showDialog) {
        CustomDialog(
            showDialog = showDialog,
            onDismissRequest = { showDialog = false
                tempValue = settingValue
            }
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                TextField(
                    value = tempValue,
                    onValueChange = { tempValue = it },
                    singleLine = true,
                    isError = errorText.isNotEmpty()
                )
                if (errorText.isNotEmpty()) {
                    Text(errorText)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(onClick = { showDialog = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFA500),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = {
                        if (validateInput(settingKey, tempValue)) {
                            onValueChange(tempValue)
                            showDialog = false
                            errorText = ""
                        }
                    },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFA500),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

