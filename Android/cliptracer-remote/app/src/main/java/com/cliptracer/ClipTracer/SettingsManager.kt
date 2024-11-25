package com.cliptracer.ClipTracer

import TimeProvider
import android.content.Context
import android.util.Log

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SettingsManager(private val context: Context) {
    private val settingsFile = File(context.filesDir, "settings.json") // Use internal storage

    var settings = mutableMapOf(
        "resolution" to "4K",
        "fps" to "120",
        "shutter_speed" to "Auto",
        "iso_min" to "100",
        "iso_max" to "1600",
        "white_balance" to "Auto",
        "color" to "Flat",
        "sharpness" to "Low",
        "exposure" to "-0.5",
        "lens" to "Narrow",
        "bitrate" to "Standard",
        "lcd_brightness" to "100%",
        "target_gopro" to "any",
        "updated_on" to "${TimeProvider.getUTCTimeMilliseconds()}"
    )
        set(value) {
            field = value
            saveSettings()
        }

    init {
        loadSettings()
    }

    fun updateSettings(newSettings: Map<String, String>, updatedOn: String? = null) {
        Log.d("","SM updateSettings.. $newSettings")
        // Update existing settings with new values, if they exist
        Log.d("","Settings before $settings")
        settings.keys.forEach { key ->
            newSettings[key]?.let { newValue ->
                settings[key] = newValue
            }
        }
        updatedOn?.let{
            settings["updated_on"] = updatedOn
        }
        Log.d("","Settings after $settings")

        saveSettings()
    }

    private fun loadSettings() {
        Log.d("","SM loadSettings..")
        if (settingsFile.exists()) {
            val settingsJsonString = settingsFile.readText()
            println("settings file: $settingsJsonString")
            val loadedSettings = Json.parseToJsonElement(settingsJsonString).jsonObject

            // Iterate through the keys of the loaded settings and update if the key exists
            loadedSettings.forEach { (key, value) ->
                settings[key] = value.jsonPrimitive.content
            }
        }
        else{
            Log.w("","SM loadSettings.. settingsFile does not exist")
        }


        saveSettings()
        Log.i("","SM loadSettings.. Loaded settings: $settings")
    }


    fun saveSettings() {
        Log.i("","SM saveSettings to disk.. settings: $settings")
        val jsonContent = Json.encodeToJsonElement(settings)
        settingsFile.writeText(jsonContent.toString())
    }
}