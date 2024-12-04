/* GoProData.kt/Open GoPro, Version 2.0 (C) Copyright 2021 GoPro, Inc. (http://gopro.com/OpenGoPro). */
/* This copyright was auto-generated on Mon Mar  6 17:45:14 UTC 2023 */

package com.cliptracer.ClipTracer.goproutil

import java.util.UUID

const val GOPRO_UUID = "0000FEA6-0000-1000-8000-00805f9b34fb"
const val GOPRO_BASE_UUID = "b5f9%s-aa8d-11e3-9046-0002a5d5c51b"

enum class GoProUUID(val uuid: UUID) {
    WIFI_AP_PASSWORD(UUID.fromString(GOPRO_BASE_UUID.format("0003"))),
    WIFI_AP_SSID(UUID.fromString(GOPRO_BASE_UUID.format("0002"))),
    CQ_COMMAND(UUID.fromString(GOPRO_BASE_UUID.format("0072"))),
    CQ_COMMAND_RSP(UUID.fromString(GOPRO_BASE_UUID.format("0073"))),
    CQ_SETTING(UUID.fromString(GOPRO_BASE_UUID.format("0074"))),
    CQ_SETTING_RSP(UUID.fromString(GOPRO_BASE_UUID.format("0075"))),
    CQ_QUERY(UUID.fromString(GOPRO_BASE_UUID.format("0076"))),
    CQ_QUERY_RSP(UUID.fromString(GOPRO_BASE_UUID.format("0077")));

    companion object {
        private val map: Map<UUID, GoProUUID> by lazy { GoProUUID.values().associateBy { it.uuid } }
        fun fromUuid(uuid: UUID): GoProUUID? = map[uuid]
    }
}

const val GOPRO_BASE_URL = "http://10.5.5.9:8080/"
const val GOPRO_USB_BASE_URL = "http://172.2X.1YZ.51:8080/"  // where XYZ are the last three digits of the camera's serial number


val resolutionOptions = TwoWayDict(
    1 to "4K",
    4 to "2.7K",
    6 to "2.7K4:3",
    7 to "1440",
    9 to "1080p",
    18 to "4K4:3",
    24 to "5K",
    25 to "5K4:3",
    100 to "5.3K"
)

val fpsOptions = TwoWayDict(
    0 to "240",
    1 to "120",
    2 to "100",
    5 to "60",
    6 to "50",
    8 to "30",
    9 to "25",
    10 to "24",
    13 to "200"
)

val shutterOptions = TwoWayDict(
    0 to "Auto",
    3 to "1/24",
    4 to "1/25",
    5 to "1/30",
    6 to "1/48",
    7 to "1/50",
    8 to "1/60",
    11 to "1/96",
    12 to "1/100",
    13 to "1/120",
    16 to "1/192",
    17 to "1/200",
    18 to "1/240",
    25 to "1/384",
    21 to "1/400",
    22 to "1/480",
    28 to "1/800",
    23 to "1/960",
    29 to "1/1600",
    24 to "1/1920",
    30 to "1/3200",
    31 to "1/3840"
)

val isoMinOptions = TwoWayDict(
    0 to "6400",
    3 to "3200",
    1 to "1600",
    4 to "800",
    2 to "400",
    7 to "200",
    8 to "100"
)

val isoMaxOptions = TwoWayDict(
    0 to "6400",
    3 to "3200",
    1 to "1600",
    4 to "800",
    2 to "400",
    7 to "200",
    8 to "100"
)

val whiteBalanceOptions = TwoWayDict(
    3 to "6500K",
    7 to "6000K",
    2 to "5500K",
    12 to "5000K",
    11 to "4500K",
    0 to "Auto",
    4 to "Native",
    5 to "4000K",
    10 to "3200K",
    9 to "2800K",
    8 to "2300K"
)

val colorOptions = TwoWayDict(
    1 to "Flat",
    2 to "Natural",
    100 to "Vibrant"
)

val lensOptions = TwoWayDict(
    10 to "loc",
    7 to "msv",
    3 to "sv",
    0 to "w",
    4 to "l",
    8 to "lev",
    2 to "n"
)

val sharpnessOptions = TwoWayDict(
    0 to "High",
    1 to "Medium",
    2 to "Low"
)

val exposureOptions = TwoWayDict(
    8 to "-2.0",
    7 to "-1.5",
    6 to "-1.0",
    5 to "-0.5",
    4 to "0.0",
    3 to "0.5",
    2 to "1.0",
    1 to "1.5",
    0 to "2.0"
)

val bitrateOptions = TwoWayDict(
    1 to "High",
    100 to "Standard"
)

val lcdBrightnessOptions = TwoWayDict(
    10 to "10%",
    20 to "20%",
    30 to "30%",
    40 to "40%",
    50 to "50%",
    60 to "60%",
    70 to "70%",
    80 to "80%",
    90 to "90%",
    100 to "100%"
)

val presetOptions = TwoWayDict(
    0 to "standard",
    1 to "activity",
    2 to "cinematic"
)

val modeOptions = TwoWayDict(
    12 to "Video",
    15 to "Looping",
    16 to "Photo",
    17 to "Burst Photo",
    18 to "Night Photo",
    19 to "Time Lapse Video",
    20 to "Time Lapse Photo",
    21 to "Night Lapse Photo",
    24 to "Time Warp Video",
    25 to "Live Burst",
    26 to "Night Lapse Video",
    27 to "Slo-Mo"
)



val gopro12AndBelowSettingsKeyMap = TwoWayDict(
    "2" to "resolution",
    "3" to "fps",
    "145" to "shutter_speed",
    "102" to "iso_min",
    "13" to "iso_max",
    "115" to "white_balance",
    "116" to "color",
    "117" to "sharpness",
    "118" to "exposure",
    "121" to "lens",
    "124" to "bitrate",
    "88" to "lcd_brightness",
    "144" to "mode",
    "30" to "ssid"
)

val gopro13AndAboveSettingsKeyMap = TwoWayDict(
    "2" to "resolution",
    "234" to "fps",
    "145" to "shutter_speed",
    "102" to "iso_min",
    "13" to "iso_max",
    "115" to "white_balance",
    "116" to "color",
    "117" to "sharpness",
    "118" to "exposure",
    "229" to "lens",
    "124" to "bitrate",
    "88" to "lcd_brightness",
    "144" to "mode",
    "30" to "ssid"
)

var settingsKeyMap = gopro12AndBelowSettingsKeyMap

val goproSettingsOptionsMap = mapOf(
    "resolution" to resolutionOptions,
    "fps" to fpsOptions,
    "shutter_speed" to shutterOptions,
    "iso_min" to isoMinOptions,
    "iso_max" to isoMaxOptions,
    "white_balance" to whiteBalanceOptions,
    "color" to colorOptions,
    "sharpness" to sharpnessOptions,
    "exposure" to exposureOptions,
    "lens" to lensOptions,
    "bitrate" to bitrateOptions,
    "lcd_brightness" to lcdBrightnessOptions,
    "mode" to modeOptions,
    "preset" to presetOptions
)

enum class StatusType(val value: String) {
    BOOL("BOOL"),
    INT("INT"),
    STRING("STRING"),
}

val statusesTypes = TwoWayDict(
    "battery_present" to StatusType.BOOL,
    "camera_hot" to StatusType.BOOL,
    "camera_busy" to StatusType.BOOL,
    "quick_capture" to StatusType.BOOL,
    "encoding" to StatusType.BOOL,
    "lcd_locked" to StatusType.BOOL,
    "current_video_duration" to StatusType.INT,
    "wifi_enabled" to StatusType.BOOL,
    "pairing_state" to StatusType.INT,
    "wlan_connected_name" to StatusType.STRING,
    "preview_stream_enabled" to StatusType.BOOL,
    "devices_connected_count" to StatusType.INT,
    "sd_card_status" to StatusType.INT,
    "videos_on_sd_card" to StatusType.INT,
    "video_time_remaining" to StatusType.INT,
    "camera_disk_space" to StatusType.INT,
    "preview_stream_allowed" to StatusType.BOOL,
    "wlan_connected_strength" to StatusType.INT,
    "last_highlight_time" to StatusType.INT,
    "status_updates_interval" to StatusType.INT,
    "ap_mode" to StatusType.BOOL,
    "battery_level" to StatusType.INT,
    "zoom" to StatusType.INT,
    "wifi_band" to StatusType.INT,
    "5ghz_available" to StatusType.BOOL,
    "system_ready" to StatusType.BOOL,
    "orientation" to StatusType.INT,
    "lens_type" to StatusType.INT,
    "sd_card_meets_requirements" to StatusType.BOOL,
    "sd_card_errors_count" to StatusType.INT,
    "camera_control_status" to StatusType.INT,
    "usb_connected" to StatusType.BOOL,
    "usb_control_enabled" to StatusType.BOOL
)


val statusesKeyMap = TwoWayDict(
    "1" to "battery_present", // 0/1
    "6" to "camera_hot", // 0/1
    "8" to "camera_busy", // 0/1
    "9" to "quick_capture", // 0/1
    "10" to "encoding", // 0/1
    "11" to "lcd_locked", // 0/1
    "13" to "current_video_duration", // seconds int
    "17" to "wifi_enabled", // 1/0
    "19" to "pairing_state",    //0	Never Started
                               //1	Started
                              //2 Aborted
                             //3	Cancelled
                            //4	Completed
    "29" to "wlan_connected_name", //string
    "32" to "preview_stream_enabled", // 1/0
    "31" to "devices_connected_count",
    "33" to "sd_card_status", // -1: Unknown 0: OK  1: SD Card Full 2: SD Card Removed
                              // 3: SD Card Format Error 4: SD Card Busy 8: SD Card Swapped
    "35" to "video_time_remaining", // int minutes
    "39" to "videos_on_sd_card",
    "54" to "camera_disk_space", // int Kilobytes
    "55" to "preview_stream_allowed", // 0/1
    "56" to "wlan_connected_strength", // int bars
    "59" to "last_highlight_time", // Time since boot (milliseconds)
    "60" to "status_updates_interval", // min interval for status updates in milliseconds
    "69" to "ap_mode", // 0/1 - is camera acting like AP
    "70" to "battery_level", // int percents
    "75" to "zoom", // zoom in percents (0-100%)
    "76" to "wifi_band", // 0 - 2.5GHz, 1 - 5Ghz, 2 - Max
    "81" to "5ghz_available", // 0/1
    "82" to "system_ready", // 0-1 - if camera able to receive commands
    "86" to "orientation", // 0 : 0 degrees (upright) 1: 180 degrees (upside down)
                          // 2: 90 degrees (laying on right side) 3: 270 degrees (laying on left side)
    "105" to "lens_type",  // 0: Default 1: Max Lens 2: Max Lens 2.0
    "111" to "sd_card_meets_requirements",
    "112" to "sd_card_errors_count",
    "114" to "camera_control_status", // 0: Camera Idle: No one is attempting to change camera settings
                                     // 1: Camera Control: Camera is in a menu or changing settings.
                                    // To intervene, app must request control
                                   //2: Camera External Control: An outside entity (app) has control
                                  // and is in a menu or modifying settings
    "115" to "usb_connected", // 0/1
    "116" to "usb_control_enabled"
)