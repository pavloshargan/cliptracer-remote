package com.cliptracer.ClipTracer/* Tutorial1ConnectBle.kt/Open GoPro, Version 2.0 (C) Copyright 2021 GoPro, Inc. (http://gopro.com/OpenGoPro). */
/* This copyright was auto-generated on Mon Mar  6 17:45:15 UTC 2023 */

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanFilter
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import com.cliptracer.ClipTracer.gopronetwork.Bluetooth
import com.cliptracer.ClipTracer.gopronetwork.BleEventListener
import com.cliptracer.ClipTracer.goproutil.GOPRO_UUID
import com.cliptracer.ClipTracer.goproutil.GoProUUID
import com.cliptracer.ClipTracer.goproutil.goproSettingsOptionsMap
import com.cliptracer.ClipTracer.goproutil.settingsKeyMap
import com.cliptracer.ClipTracer.goproutil.isNotifiable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import com.cliptracer.ClipTracer.goproutil.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.reflect.KFunction1
import android.bluetooth.le.*
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import java.nio.ByteBuffer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.abs


class GoProBleManager(val ble: Bluetooth, var onGoproConnect: KFunction1<String, Unit>, var onGoproDisconnect: KFunction1<BluetoothDevice, Unit>) {

    private val scanFilters = listOf(
        ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(GOPRO_UUID)).build()
    )

    var silentAudioService: SilentAudioService? = null

    var goproToConnect: ScanResult? = null

    private var response: Response<*>? = null

    var goproVersion: String? = null

    var currentSettings: Response<*>? = null
    var currentStatus: Response<*>? = null

    var currentSettingsFormatted: Map<String, String> = emptyMap()
    var currentStatusesFormatted: Map<String, String> = emptyMap()
    var currentDatetimeSeconds: Long = 0


    var settingsUpdatedAt = TimeProvider.getUTCTimeMilliseconds()
    var statusUpdatedAt = TimeProvider.getUTCTimeMilliseconds()
    var datetimeUpdatedAt = TimeProvider.getUTCTimeMilliseconds()

    var goproStatusShortened = "-|-|-|-|-"
    var titleText = "Not Connected"

    private enum class Resolution(val value: UByte) {
        RES_4K(1U),
        RES_2_7K(4U),
        RES_2_7K_4_3(6U),
        RES_1080(9U),
        RES_4K_4_3(18U),
        RES_5K(24U);

        companion object {
            private val valueMap: Map<UByte, Resolution> by lazy { values().associateBy { it.value } }
        }
    }

    @SuppressLint("MissingPermission")
    @OptIn(ExperimentalUnsignedTypes::class)
    private fun tlvResponseNotificationHandler(characteristic: UUID, data: UByteArray) {

        GoProUUID.fromUuid(characteristic)?.let { uuid ->
            response = response ?: Response.fromUuid(uuid)
        }
            ?: return // We don't care about non-GoPro characteristics (i.e. the BT Core Battery service)

        response?.let { rsp ->
            rsp.accumulate(data)
            if (rsp.isReceived) {
                rsp.parse()
                // Notify the command sender the the procedure is complete
                response = null // Clear for next command
                val rspString = rsp.toString()
//                print("rspString: ${rspString}")

                if (characteristic == GoProUUID.CQ_COMMAND_RSP.uuid && data.size == 12) { //received gopro time
                    try {
                        currentDatetimeSeconds = formatDatetime(data) + 1 // correction for delay
                        val timeOnPhone = TimeProvider.getUTCTimeMilliseconds() / 1000
                        val timeDifference = abs(timeOnPhone - currentDatetimeSeconds)
                        if (timeDifference > 5) {
                            println("DateTime on GoPro: $currentDatetimeSeconds")
                            println("DateTime on Phone: $timeOnPhone")
                            println("Time difference between the phone and the GoPro is > 5: $timeDifference. GoProTime: $currentDatetimeSeconds PhoneTime: $timeOnPhone")
                            CoroutineScope(Dispatchers.IO).launch {
                                setDateTimeOnGoPro()
                            }
                        }
                    } catch (e: Exception) {
                        println("Error processing date: ${e.message} date in bytes: ${data}")
                    }
                }

                if (rspString.contains("\"134\":") || rspString.contains("\"234\":")) { //using a hacky approach to differentiate settings and statuses responses,
                    // because the characteristic is the same for both.
                    // key 134 is present for settings only for all GoPro models
                    println("settings pooled")
                    if(rspString.contains("\"234\":")){
                        //this is HERO13
                        settingsKeyMap = gopro13AndAboveSettingsKeyMap
                    } else{
                        //this is not HERO13
                        settingsKeyMap = gopro12AndBelowSettingsKeyMap
                    }
                    currentSettings = rsp
                    print("got settings: $currentSettings")

                    settingsUpdatedAt = TimeProvider.getUTCTimeMilliseconds()
                    currentSettingsFormatted = formatAllSettings() as Map<String, String>


                    val cameraDiskSpaceRaw = currentStatusesFormatted["camera_disk_space"]
                    val cameraDiskSpace =
                        cameraDiskSpaceRaw?.takeIf { it.all { char -> char.isDigit() } }?.toLong()
                            ?.let { format_kb(it) } ?: "-"
                    goproStatusShortened =
                        "${currentSettingsFormatted["resolution"] ?: " - "}|${currentSettingsFormatted["lens"] ?: " - "}|${currentSettingsFormatted["fps"] ?: " - "}|${currentStatusesFormatted["battery_level"] ?: "-"}%|${cameraDiskSpace}"

                    if (currentStatusesFormatted["encoding"] == "true" && titleText!="Stopping"){
                        titleText  = "Recording"
                    }
                    else if (titleText!="Stopping" && titleText!="Starting"){
                        titleText  = "Ready"
                    }
                    if(!DataStore.intentiousSleep){
                        silentAudioService?.updateMetadataAndNotification(goproStatusShortened, titleText)
                    }
                }

                else if (rspString.contains("\"17\":")) { //key 17 is present for statuses only
                        println("status pooled")
                        currentStatus = rsp
                        // the code below is a workaround to differentiate gopro models by the status codes available
                        if(goproVersion==null){
                            //104 - hero10 only setting ("Is the system’s Linux core active?" setting)
                            print("rspString: ${rspString}")
                            if (rspString.contains("\"99\":")) {
                                goproVersion = "HERO10"
                                Log.d("","setting time for HERO10")
                                print(goproVersion)
                                DataStore.goproVersion = goproVersion as String
                                CoroutineScope(Dispatchers.IO).launch {
                                    setDateTimeOnGoPro()
                                }
                            }
                            else{
                                goproVersion = "HERO11+"
                                Log.d("","setting time for HERO11+")
                                print(goproVersion)
                                DataStore.goproVersion = goproVersion as String
                                CoroutineScope(Dispatchers.IO).launch {
                                    setDateTimeOnGoPro()
                                }
                            }
                        }

                        statusUpdatedAt = TimeProvider.getUTCTimeMilliseconds()
                        currentStatusesFormatted = formatAllStatuses() as Map<String, String>
//                        println("Statuses formatted: ${currentStatusesFormatted}")
                    }

//                    else if (data.size == 12) {
//                        currentDatetimeBytes = data
//                        datetimeUpdatedAt = TimeProvider.getUTCTimeMilliseconds()
//                        println("datetime updated")
//                        currentDatetimeSeconds = formatDatetime(data)
//
//                    }
                }

        } ?: throw Exception("This should be impossible")
    }

    fun disconnectHandler(device: BluetoothDevice) {
        println("disconnectHandler entered")
        currentSettings = null
        currentStatus = null
        goproVersion = null
        onGoproDisconnect(device)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private val bleListeners by lazy {
        BleEventListener().apply {
            onNotification = ::tlvResponseNotificationHandler
            onDisconnect = { device -> disconnectHandler(device) }
        }
    }

    private fun checkStatus(data: UByteArray) =
        if (data[2].toUInt() == 0U) Log.i("","Command sent successfully")
        else Log.i("","Command Failed")

    @OptIn(ExperimentalUnsignedTypes::class)
    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"])
    suspend fun connectGoPro(targetDeviceName: String) {
        Log.d("","connectGoPro entered")
        var goproAddress = ""
        var currentGoProBLEName = ""

        val scanTimeoutMillis = 20_000L // Timeout for scanning
        var scanResults: MutableSharedFlow<ScanResult>? = null
        var scanningCoroutine: Job? = null // Reference to the scanning coroutine
        var hasResumed = false // Flag to track if the continuation has already been resumed

        try {
            withTimeoutOrNull(scanTimeoutMillis) {
                suspendCancellableCoroutine<Unit> { cont ->
                    scanningCoroutine = CoroutineScope(Dispatchers.IO).launch {
                        try {
                            scanResults = ble.startScan(scanFilters)
                            scanResults!!.collect { scanResult ->
                                val matchesTargetName = scanResult.device.name?.toLowerCase()
                                    ?.replace("\\s".toRegex(), "") == targetDeviceName.toLowerCase()
                                    .replace("\\s".toRegex(), "")
                                Log.d("","matchesTargetName: ${matchesTargetName}  scanned:${scanResult.device.name} target: ${targetDeviceName}")
                                if (matchesTargetName) { //|| (targetDeviceName == "any" && !hasResumed)
                                    goproToConnect = scanResult
                                    hasResumed = true
                                    cont.resume(Unit)
                                }
                                if (goproToConnect != null)
                                {
                                    hasResumed = true
                                    cont.resume(Unit)
                                }
                            }
                        } catch (e: Exception) {
                            if (!hasResumed) {
                                hasResumed = true
                                cont.resumeWithException(e)
                            }
                        }
                    }
                }
            }

            // Connect to the device after delay
            goproToConnect?.let { gopro ->
                goproToConnect = null
                goproAddress = gopro.device.address
                currentGoProBLEName = gopro.device.name
                Log.i("","Connecting to $goproAddress")
                ble.connect(goproAddress).onFailure { throw it }

                ble.registerListener(goproAddress, bleListeners)
                // Discover all characteristics
                Log.i("","Discovering characteristics")
                ble.discoverCharacteristics(goproAddress).onFailure { throw it }
                // Read a known encrypted characteristic to trigger pairing
                ble.readCharacteristic(goproAddress, GoProUUID.WIFI_AP_PASSWORD.uuid, 30000)
                    .onFailure { throw it }
                Log.i("","Enabling notifications")
                // Now that we're paired, for each characteristic that is notifiable...
                ble.servicesOf(goproAddress)
                    .fold(onFailure = { throw it }, onSuccess = { services ->
                        services.forEach { service ->
                            service.characteristics.forEach { char ->
                                if (char.isNotifiable()) {
                                    //char.uuid != GoProUUID.CQ_COMMAND_RSP.uuid && char.uuid != GoProUUID.CQ_COMMAND.uuid &&
                                    if (char.uuid != GoProUUID.CQ_SETTING_RSP.uuid) { // ignore responses from commands to receive query responses only
                                        Log.d("","enabling notifications for: ${char.uuid}")
                                        // Enable notifications for this characteristic
                                        ble.enableNotification(goproAddress, char.uuid)
                                            .onFailure {
                                                Log.w("","enableNotification failure")
                                                throw it
                                            }
                                    }

                                }
                            }
                        }
                    })

                DataStore.lastConnectedGoProBLEMac = goproAddress
                DataStore.currentGoProBLEName = currentGoProBLEName
                Log.d("","currentGoProBLEName ${currentGoProBLEName}")
                goproVersion = null //to be parsed
                onGoproConnect(currentGoProBLEName)
            } ?: throw IllegalStateException("No device named ${currentGoProBLEName} found")
        } catch (e: TimeoutCancellationException) {
            Log.w("","Scanning for GoPro timed out.")
        } catch (e: Exception) {
            Log.w("","Error connecting to GoPro: ${e.message}")
        } finally {
            scanningCoroutine?.cancel() // Ensure scanning is stopped
            scanResults?.let {
                ble.stopScan(it) // Ensure stopScan is called only if scanResults is not null
            }
        }
    }


    @OptIn(ExperimentalUnsignedTypes::class)
    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"])
    suspend fun connectGoProCached(goproAddress: String, goproName: String) {
        Log.d("","connectGoProCached entered")
        val scanTimeoutMillis = 10_000L // Timeout for scanning, e.g., 10 seconds

        try {
            // Now that we found a gopro, connect to it
            Log.i("","Connecting to $goproAddress")
            ble.connect(goproAddress).onFailure { throw it }

            /**
             * Perform initial BLE setup
             */
            ble.registerListener(goproAddress, bleListeners)
            // Discover all characteristics
            Log.i("","Discovering characteristics")
            ble.discoverCharacteristics(goproAddress).onFailure { throw it }
            Log.i("","Reading characteristics")
            // Read a known encrypted characteristic to trigger pairing
            ble.readCharacteristic(goproAddress, GoProUUID.WIFI_AP_PASSWORD.uuid, 30000)
                .onFailure { throw it }
            Log.i("","Enabling notifications")

            // Now that we're paired, for each characteristic that is notifiable...
            ble.servicesOf(goproAddress).fold(onFailure = { throw it }, onSuccess = { services ->
                services.forEach { service ->
                    service.characteristics.forEach { char ->
                        if (char.isNotifiable()) {
                            if (char.uuid != GoProUUID.CQ_COMMAND_RSP.uuid && char.uuid != GoProUUID.CQ_COMMAND.uuid && char.uuid != GoProUUID.CQ_SETTING_RSP.uuid) { // ignore responses from commands to receive query responses only
                                Log.d("","enabling notifications for: ${char.uuid}")
                                // Enable notifications for this characteristic
                                ble.enableNotification(goproAddress, char.uuid)
                                    .onFailure { throw it }
                            }

                        }
                    }
                }
            })

            DataStore.lastConnectedGoProBLEMac = goproAddress
            DataStore.currentGoProBLEName = goproName
            goproVersion = null //to be parsed
            onGoproConnect(goproName)

            Log.i("","Bluetooth is ready for communication!")
        } catch (e: TimeoutCancellationException) {
            Log.w("","Connecting to GoPro timed out.")

            // Handle timeout scenario
        } catch (e: Exception) {
            Log.w("","Error connecting to GoPro: ${e.message}")
            // Handle other exceptions
        } finally {
            // Perform any necessary cleanup
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"])
    suspend fun startRecording() {
        if(DataStore.intentiousSleep){
            println("waking up before start recording")
            connectGoProCached(DataStore.lastConnectedGoProBLEMac!!, DataStore.currentGoProBLEName?:"")
        }
        else{
            Log.i("","Setting the shutter on")
            val shutterOnCmd = ubyteArrayOf(0x03U, 0x01U, 0x01U, 0x01U)
            val lastConnectedGoProBLEMac = DataStore.lastConnectedGoProBLEMac
            if (lastConnectedGoProBLEMac != null) {
                ble.writeCharacteristic(lastConnectedGoProBLEMac, GoProUUID.CQ_COMMAND.uuid, shutterOnCmd)
                silentAudioService?.seekToZeroOnStartRecording()
            }
        }

    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"])
    suspend fun stopRecording() {
        Log.i("","Setting the shutter off")
        val shutterOffCmd = ubyteArrayOf(0x03U, 0x01U, 0x01U, 0x00U)
        val lastConnectedGoProBLEMac = DataStore.lastConnectedGoProBLEMac
        if (lastConnectedGoProBLEMac != null) {
            ble.writeCharacteristic(lastConnectedGoProBLEMac, GoProUUID.CQ_COMMAND.uuid, shutterOffCmd)
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"])
    suspend fun addHighlight() {
        Log.i("", "Adding highlight")
        val addTagCmd = ubyteArrayOf(0x01U, 0x18U)
        val lastConnectedGoProBLEMac = DataStore.lastConnectedGoProBLEMac
        if (lastConnectedGoProBLEMac != null) {
            ble.writeCharacteristic(lastConnectedGoProBLEMac, GoProUUID.CQ_COMMAND.uuid, addTagCmd)
        }
    }



    @OptIn(ExperimentalUnsignedTypes::class)
    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"])
    suspend fun goproKeepAlive(){
        val lastConnectedGoProBLEMac = DataStore.lastConnectedGoProBLEMac
        if (lastConnectedGoProBLEMac != null) {
            val keepAliveCmd = ubyteArrayOf(0x03U, 0x5BU,0x01U,0x42U)
            ble.writeCharacteristic(lastConnectedGoProBLEMac, GoProUUID.CQ_SETTING.uuid, keepAliveCmd)
            Log.d("","keep ble alive sent")
            //checkStatus(receivedData.receive())
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"])
    suspend fun goproPowerOff(){
        Log.i("","powering off")
        val powerOffCmd = ubyteArrayOf(0x01U, 0x05U)
        val lastConnectedGoProBLEMac = DataStore.lastConnectedGoProBLEMac
        println("lastConnectedGoProBLEMac: ${lastConnectedGoProBLEMac}")
        if (lastConnectedGoProBLEMac != null) {
            Log.d("","sending power off command")
            ble.writeCharacteristic(lastConnectedGoProBLEMac, GoProUUID.CQ_COMMAND.uuid, powerOffCmd)
        }
        //checkStatus(receivedData.receive()) // this line waits until requested status is fully received.
                                             // It was commented out as it may hang forever if the gopro
                                            // suddenly disconnects. We have to add a timeout for this to work
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"])
    suspend fun goproSetSetting(settingName: String, settingValue: String){
        Log.i("","setting $settingName to $settingValue")
        val settingNameUbyteCode =  settingsKeyMap.getKeyByValue(settingName)?.toUByte() ?: 0x01U
        val options = goproSettingsOptionsMap[settingName]
        val settingOptionUbyteCode = options?.getKeyByValue(settingValue)?.toUByte() ?: 0x01U

        val setSettingCmd = ubyteArrayOf(0x03U, settingNameUbyteCode, 0x01U, settingOptionUbyteCode)

        val lastConnectedGoProBLEMac = DataStore.lastConnectedGoProBLEMac
        if (lastConnectedGoProBLEMac != null) {
            ble.writeCharacteristic(
                lastConnectedGoProBLEMac,
                GoProUUID.CQ_SETTING.uuid,
                setSettingCmd
            )
        }

        //checkStatus(receivedData.receive())   // this line waits until requested status is fully received.
                                               // It was commented out as it may hang forever if the gopro
                                              // suddenly disconnects. We have to add a timeout for this to work
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"])
    suspend fun getSettings() {
        val lastConnectedGoProBLEMac = DataStore.lastConnectedGoProBLEMac
        if (lastConnectedGoProBLEMac != null) {
            //Log.i("","Getting the camera's settings")
            val getCameraSettings = ubyteArrayOf(0x01U, 0x12U)
            ble.writeCharacteristic(lastConnectedGoProBLEMac, GoProUUID.CQ_QUERY.uuid, getCameraSettings)
//            val settings = receivedResponse.receive()
//            currentSettings = settings
//            print("got settings: $currentSettings")
//
//            settingsUpdatedAt = TimeProvider.getUTCTimeMilliseconds()
//            currentSettingsFormatted = formatAllSettings() as Map<String, String>
        } else {
            Log.w("","No connected GoPro available.")
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"])
    suspend fun getStatus() {
        val lastConnectedGoProBLEMac = DataStore.lastConnectedGoProBLEMac
        if (lastConnectedGoProBLEMac != null) {
            //Log.i("","Getting the camera's statuses")
            val getCameraStatuses = ubyteArrayOf(0x01U, 0x13U)
            ble.writeCharacteristic(lastConnectedGoProBLEMac, GoProUUID.CQ_QUERY.uuid, getCameraStatuses)
//            val statuses = receivedResponse.receive()
//            currentStatus = statuses
//            print("got status: $currentStatus")
//            statusUpdatedAt = TimeProvider.getUTCTimeMilliseconds()
//            currentStatusesFormatted = formatAllStatuses()
        } else {
            Log.w("","No connected GoPro available.")
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"])
    suspend fun setDateTimeOnGoPro() {
        val model = goproVersion?:"HERO11+"

        println("Setting UTC date and time on GoPro")
        var utcDateTime: ZonedDateTime
        if(model=="HERO11+"){
            utcDateTime = ZonedDateTime.now()
            println("Zoned datetime: ${utcDateTime}")
        }
        else{//hero10
            utcDateTime = ZonedDateTime.now(ZoneOffset.UTC)
        }
        // Convert year to bytes
        val yearBytes = ByteBuffer.allocate(2).putShort(utcDateTime.year.toShort()).array()

        val dateTimeCmd = ubyteArrayOf(
            0x09U,  // Total number of bytes in the query
            0x0DU,  // Command ID for set date/time
            0x07U,
            yearBytes[0].toUByte(),                // First byte of year
            yearBytes[1].toUByte(),                // Second byte of year
            utcDateTime.monthValue.toUByte(),      // Month
            utcDateTime.dayOfMonth.toUByte(),      // Day
            utcDateTime.hour.toUByte(),            // Hour
            utcDateTime.minute.toUByte(),          // Minute
            utcDateTime.second.toUByte()           // Second
        )

        val connectedGoProBLEMac = DataStore.lastConnectedGoProBLEMac
        if (connectedGoProBLEMac != null) {
            ble.writeCharacteristic(connectedGoProBLEMac, GoProUUID.CQ_COMMAND.uuid, dateTimeCmd)
        }
        delay(5000)
        checkGoProTime()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"])
    suspend fun checkGoProTime() {
        val connectedGoProBLEMac = DataStore.lastConnectedGoProBLEMac
        if (connectedGoProBLEMac != null) {
            val getCameraTime = ubyteArrayOf(0x01U, 0x0EU)
            ble.writeCharacteristic(connectedGoProBLEMac, GoProUUID.CQ_COMMAND.uuid, getCameraTime)

        } else {
            println("No connected GoPro available.")
        }
    }

    fun formatDatetime(currentDatetimeBytes: UByteArray): Long {
        try {
            // Extract the year, which is in the first two bytes
            val year = ByteBuffer.wrap(byteArrayOf(currentDatetimeBytes[4].toByte(), currentDatetimeBytes[5].toByte())).short.toInt()
            val month = currentDatetimeBytes[6].toInt()
            val day = currentDatetimeBytes[7].toInt()
            val hour = currentDatetimeBytes[8].toInt()
            val minute = currentDatetimeBytes[9].toInt()
            val second = currentDatetimeBytes[10].toInt()
            val tenthOfSecond = currentDatetimeBytes[11].toInt() // The tenth of a second
            val stringTime = "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}T${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}:${second.toString().padStart(2, '0')}"


            val localDateTime = LocalDateTime.parse(stringTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            // Convert LocalDateTime to Instant with or without offset depending on gopro model
            val instant = if (goproVersion == "HERO11+") {
                val zoneOffset = ZoneId.systemDefault().rules.getOffset(localDateTime)
                localDateTime.toInstant(zoneOffset)
            } else {
                localDateTime.toInstant(ZoneOffset.UTC)
            }

            // Round to the nearest second
            val epochSecond = instant.epochSecond

            // Convert Instant to LocalDateTime for printing
            val dateTime = LocalDateTime.ofEpochSecond(epochSecond, 0, ZoneOffset.UTC)
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val formattedDateTime = dateTime.format(formatter)

            println("Formatted DateTime: $formattedDateTime")

            return epochSecond
        } catch (e: DateTimeParseException) {
            println("Failed to parse DateTime: $e")
            throw IllegalArgumentException("Invalid datetime data provided")
        }
    }

    fun formatAllSettings(): Map<String, String?> {
        // Retrieve the entire current settings map once
        val currentSettingsMap = (currentSettings as? Response.Query)?.data

        // Convert the current settings to a map of setting names to option values
        return settingsKeyMap.keys.associate { settingId ->
            // Get the setting name using the key from the TwoWayDict
            val settingName = settingsKeyMap.getValueByKey(settingId)

            // Convert setting ID to UByte and retrieve the corresponding UByteArray from currentSettings
            val settingUByteId = settingId.toUByteOrNull()
            val settingValueAsUByteArray = settingUByteId?.let { currentSettingsMap?.get(it) }

            // Convert the UByteArray to a hex string
            val settingValueAsHexString = settingValueAsUByteArray?.joinToString(separator = "") { byte -> "%02X".format(byte.toInt()) }

            // Use the setting name and hex string to find the human-readable option value
            val humanReadableValue = settingValueAsHexString?.toIntOrNull(16)?.let { hexValue ->
                goproSettingsOptionsMap[settingName]?.getValueByKey(hexValue)
            }

            // Pair the setting name with its corresponding human-readable value
            settingName to humanReadableValue
        }.filterKeys { it != null }.mapKeys { it.key!! } // Remove entries with null keys and unwrap non-null keys
    }


    fun formatAllStatuses(): Map<String, String> {
        // Retrieve the entire current settings map once
        val currentStatusMap = (currentStatus as? Response.Query)?.data

        // Function to convert raw value to human-readable format based on status type
        fun convertToReadable(statusName: String?, hexString: String): String {
            if (statusName == null) return "Unknown"

            // Determine the status type
            val statusType = statusesTypes.getValueByKey(statusName)

            // Handle each type appropriately
            return when (statusType) {
                StatusType.BOOL -> {
                    val rawValue = hexString.toIntOrNull(16) ?: return "Invalid"
                    if (rawValue == 0) "false" else "true"
                }
                StatusType.INT -> {
                    hexString.toIntOrNull(16)?.toString() ?: "-"
                }
                StatusType.STRING -> {
                    // Convert each pair of hex characters to the corresponding ASCII character
                    hexString.chunked(2).mapNotNull { it.toIntOrNull(16)?.toChar() }.joinToString("")
                }
                else -> "Unknown"
            }
        }

        // Convert the current settings to a map of setting names to human-readable values
        return statusesKeyMap.keys.associate { statusId ->
            val statusName = statusesKeyMap.getValueByKey(statusId)
            val statusUByteId = statusId.toUByteOrNull()



            val statusValueAsUByteArray = statusUByteId?.let { currentStatusMap?.get(it) }
            val settingValueAsHexString = statusValueAsUByteArray?.joinToString(separator = "") { byte -> "%02X".format(byte.toInt()) }


            val humanReadableValue = convertToReadable(statusName, settingValueAsHexString ?: "")

            statusName to humanReadableValue
        }.filterKeys { it != null }.mapKeys { it.key!! }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"])
    suspend fun disableWifi() {
        Log.d("","disableWifi entered")
        val goproAddress = DataStore.lastConnectedGoProBLEMac
        goproAddress?.let{
            try {
                Log.i("","Disabling the camera's Wifi AP")
                val disableWifiCommand = ubyteArrayOf(0x03U, 0x17U, 0x01U, 0x00U)
                ble.writeCharacteristic(goproAddress, GoProUUID.CQ_COMMAND.uuid, disableWifiCommand)
            } catch (e: Exception) {
                Log.w("","Error disabling Wifi ${e.message}")
            }
        }

    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @RequiresPermission(allOf = ["android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"])
    suspend fun enableWifi(): Triple<Boolean, String?, String?> {
        Log.d("","enableWifi entered")

        val goproAddress = DataStore.lastConnectedGoProBLEMac ?: return Triple(false, null, null)

        lateinit var password: String
        lateinit var ssid: String
        try {
            Log.i("","Getting the password")
            ble.readCharacteristic(goproAddress, GoProUUID.WIFI_AP_PASSWORD.uuid)
                .onSuccess { password = it.decodeToString() }.onFailure { throw it }
            Log.i("","Password is $password")
            Log.i("","Getting the SSID")
            ble.readCharacteristic(goproAddress, GoProUUID.WIFI_AP_SSID.uuid)
                .onSuccess { ssid = it.decodeToString() }.onFailure { throw it }
            Log.i("","SSID is $ssid")

            Log.i("","Enabling the camera's Wifi AP")
            val enableWifiCommand = ubyteArrayOf(0x03U, 0x17U, 0x01U, 0x01U)
            ble.writeCharacteristic(goproAddress, GoProUUID.CQ_COMMAND.uuid, enableWifiCommand)
//            receivedData.receive()
            return Triple(true, ssid, password)
        } catch (e: Exception) {
            Log.w("","Error enabling Wifi ${e.message}")
            return Triple(false, null, null)
        }
    }


}