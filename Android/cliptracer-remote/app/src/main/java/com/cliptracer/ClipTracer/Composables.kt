package com.cliptracer.ClipTracer

import androidx.compose.material3.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.unit.Dp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.scale
import com.cliptracer.ClipTracer.goproutil.TwoWayDict


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
            },
            onBeepDuringRecordingChange = { value ->
                mainIntent.setBeepDuringRecording(value=="True")
            },
            onDismissRequest = {
                mainIntent.setShowSettings(false)
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
    onBeepDuringRecordingChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier
) {
    val settingsToInclude = listOf("beep_during_recording")

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
            // Only include settings that are in `settingsToInclude`
            settings.filterKeys { it in settingsToInclude }.forEach { (key, value) ->
                SettingItem(
                    settingKey = key,
                    settingAlias = key,
                    settingValue = value,
                    onValueChange = { newValue ->
                        onSettingChange(settings.toMutableMap().apply {
                            put(key, newValue)
                        })
                        if (key == "beep_during_recording"){
                            onBeepDuringRecordingChange(newValue)
                        }
                        onSave() // save all settings when some setting is changed
                    }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

//            Row(
//                horizontalArrangement = Arrangement.SpaceEvenly,
//                modifier = Modifier.fillMaxWidth()
//            ) {
//                Button(onClick = onSave) {
//                    Text(text = "Save")
//                }
//
//                Button(onClick = onCancel) {
//                    Text(text = "Cancel")
//                }
//
//            }
        }
    }
}

@Composable
fun SettingItem(
    settingKey: String,
    settingAlias: String,
    settingValue: String,
    onValueChange: (String) -> Unit,
    borderColor: Color = Color.Unspecified,
    foregroundColor: Color = Color.Unspecified,
    scale: Float = 1f
) {
    // Convert lists in additionalOptionsMap to TwoWayDict
    val optionsMap = mapOf(
        "beep_during_recording" to TwoWayDict(0 to "False", 1 to "True"),
    )
    val options = optionsMap[settingKey]?.values ?: listOf()
    // Determine if settingKey is in combinedOptionsMap
    val isOptionSetting = optionsMap.containsKey(settingKey)

    var expanded by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var tempValue by remember { mutableStateOf(settingValue) }
    var errorText by remember { mutableStateOf("") }

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
                        onValueChange(tempValue)
                        showDialog = false
                        errorText = ""
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

@Composable
fun TextLabel(
    text1: String,
    icon1: ImageVector,
    scale: TextUnit,
    gap: Dp = 8.dp
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
    ) {
        Icon(
            icon1,
            contentDescription = null, // Descriptive text for the icon
            tint = Color(0xFFFFA500), // Icon color set to orange
        )
        Spacer(modifier = Modifier.size(gap)) // Space between icon and text
        Text(
            text = text1,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = scale),
            color = Color(0xFFFFA500), // Orange color for the text
        )

    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReusableTopAppBar(
    title: String,
    onNavigationIconClick: () -> Unit
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onNavigationIconClick) {
                Icon(Icons.Filled.ArrowBack, "Back")
            }
        }
    )
}

@Composable
fun CustomDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    if (showDialog) {
        Dialog(onDismissRequest = onDismissRequest) {
            // Customizing the dialog's appearance and layout
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 8.dp
            ) {
                content()
            }
        }
    }
}






