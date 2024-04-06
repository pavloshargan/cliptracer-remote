/* Ble.kt/Open GoPro, Version 2.0 (C) Copyright 2021 GoPro, Inc. (http://gopro.com/OpenGoPro). */
/* This copyright was auto-generated on Mon Mar  6 17:45:14 UTC 2023 */

package com.cliptracer.ClipTracer.gopronetwork

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import com.cliptracer.ClipTracer.goproutil.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume


private const val BLE_BASE_UUID = "0000%s-0000-1000-8000-00805F9B34FB"

enum class CoreUUID(val uuid: UUID) {
    CCC_DESCRIPTOR(UUID.fromString(BLE_BASE_UUID.format("2902"))), BATT_LEVEL(
        UUID.fromString(
            BLE_BASE_UUID.format("2a19")
        )
    )
}

@OptIn(ExperimentalUnsignedTypes::class)
class BleEventListener {
    var onNotification: ((UUID, UByteArray) -> Unit)? = null
    var onDisconnect: ((BluetoothDevice) -> Unit)? = null
    var onConnect: ((BluetoothDevice) -> Unit)? = null
}

/**
 * A per-context Bluetooth wrapper
 *
 * @property context context of this bluetooth wrapper
 */
data class DeviceWithTimestamp(val device: ScanResult, val timestamp: Long)

class Bluetooth private constructor(private val context: Context) {

    val foundDevicesTimestamps = mutableListOf<DeviceWithTimestamp>()
    var foundDevices: MutableList<ScanResult> = mutableListOf()



    companion object {
        private val instances: MutableMap<Context, Bluetooth> = mutableMapOf()

        fun getInstance(context: Context): Bluetooth = instances[context] ?: Bluetooth(context)

        val permissionsNeeded: List<String> by lazy {
            when {
                else -> listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            }
        }
    }

    val adapter: BluetoothAdapter by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            ?: throw Exception("Not able to acquire Bluetooth Adapter")
    }

    interface BluetoothEnableListener {
        fun onBluetoothEnabled()
    }


    private var bluetoothEnableListener: BluetoothEnableListener? = null

    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    suspend fun enableAdapter(listener: BluetoothEnableListener) {
        bluetoothEnableListener = listener
        if (!adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(enableBtIntent)
            waitForBluetoothEnable()
        } else {
            listener.onBluetoothEnabled()
        }
    }

    private suspend fun waitForBluetoothEnable() = suspendCancellableCoroutine<Unit> { continuation ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_ON) {
                        bluetoothEnableListener?.onBluetoothEnabled()
                        continuation.resume(Unit)
                        context?.unregisterReceiver(this)
                    }
                }
            }
        }

        IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED).also { filter ->
            context.registerReceiver(receiver, filter)
        }
        continuation.invokeOnCancellation {
            context.unregisterReceiver(receiver)
        }
    }

    private val scanner: BluetoothLeScanner by lazy {
        // This is a singleton but multiple scans can happen simultaneously by using different scan callbacks
        adapter.bluetoothLeScanner
    }

    data class DeviceEntry(
        var gatt: BluetoothGatt,
        var listeners: MutableSet<WeakReference<BleEventListener>> = mutableSetOf()
    )

    @OptIn(ExperimentalUnsignedTypes::class)
    sealed class BleJob<T> {
        companion object {
            // Only one Gatt operation can occur simultaneously (not including asynchronous notifications)
            private val mutex = Mutex()
        }

        private var continuation: Continuation<T>? = null


        suspend fun suspendWithTimeout(timeout: Long, action: () -> Any): Result<T> {
            mutex.withLock {
                try {
                    val returnValue = withTimeout(timeout) {
                        suspendCancellableCoroutine { cont ->
                            continuation = cont
                            try {
                                action()
                            } catch (e: Exception) {
                                continuation?.resumeWith(Result.failure(e))
                            }
                        }
                    }
                    continuation = null
                    return Result.success(returnValue)
                } catch (e: TimeoutCancellationException) {
                    continuation = null
                    return Result.failure(e)
                }
            }
        }

        fun resumeWithError(error: String) {
            Log.d("",error)
            continuation?.resumeWith(Result.failure((Exception(error))))
                ?: throw Exception("No current continuation")
        }

        fun resumeWithSuccess(value: T) = continuation?.resumeWith(Result.success(value))
            ?: throw Exception("No current continuation")

        object Connect : BleJob<BluetoothGatt>()
        object DiscoverServices : BleJob<Unit>()
        object Read : BleJob<UByteArray>()
        object Write : BleJob<Unit>()
        object EnableNotification : BleJob<Unit>()
    }

    private var genericListeners: MutableSet<WeakReference<BleEventListener>> = mutableSetOf()
    private var deviceGattMap = ConcurrentHashMap<BluetoothDevice, DeviceEntry>()
    private val scanObserverMap = ConcurrentHashMap<Int, ScanCallback>()

    /**
     * Private helper functions
     */
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    private fun teardownConnection(device: BluetoothDevice) {
        if (device.isConnected()) {
            Log.d("","Disconnecting from ${device.address}")
            val deviceEntry = deviceGattMap.getValue(device)
            deviceEntry.gatt.close()
            // Notify registered listeners
            deviceEntry.listeners.forEach { it.get()?.onDisconnect?.invoke(device) }
            // Notify generic listeners
            genericListeners.forEach { it.get()?.onDisconnect?.invoke(device) }
            deviceGattMap.remove(device)
        } else {
//            BleJob.Connect.resumeWithError("Connection failed during establishment")
            Log.d("","Not connected to ${device.address}, cannot teardown connection!")
        }
    }

    private fun deviceFromAddress(deviceAddress: String): BluetoothDevice? =
        BluetoothAdapter.checkBluetoothAddress(deviceAddress)
            .let { if (it) adapter.getRemoteDevice(deviceAddress) else null }


    private fun gattFromAddress(deviceAddress: String): BluetoothGatt? =
        deviceFromAddress(deviceAddress)?.let { deviceGattMap[it]?.gatt }


    private fun charFromUuid(
        gatt: BluetoothGatt, characteristic: UUID
    ): BluetoothGattCharacteristic? = gatt.findCharacteristic(characteristic)

    /**
     * Callbacks
     */
    private open class ScanCallbackWrapper(val flow: MutableSharedFlow<ScanResult>) : ScanCallback()

    private fun scanCallbackFactory(observer: MutableSharedFlow<ScanResult>) =
        object : ScanCallbackWrapper(observer) {
            override fun onScanFailed(errorCode: Int) {
                val errorDescription = when (errorCode) {
                    ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Scan already started."
                    ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Application registration failed."
                    ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Internal error occurred."
                    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "Scan feature is unsupported."
                    else -> "Unknown error occurred with code $errorCode."
                }
                Log.w("","Bluetooth scan failed: $errorDescription")
            }


            @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                Log.d("","Received scan result: ${result?.device?.name ?: ""}")
                result?.let { scanResult ->
                    runBlocking {
                        flow.emit(scanResult)
                    }

                    val currentTime = TimeProvider.getUTCTimeMilliseconds()
                    val existingDeviceIndex = foundDevicesTimestamps.indexOfFirst { it.device.device.address == scanResult.device.address }

                    if (existingDeviceIndex >= 0) {
                        // Update the timestamp of the existing device
//                        println("updating timestamp for ${scanResult.device.name} index : ${existingDeviceIndex}")
                        foundDevicesTimestamps[existingDeviceIndex] = DeviceWithTimestamp(scanResult, currentTime)
                    } else {
                        // Add new device since it doesn't exist in the list
                        foundDevicesTimestamps.add(DeviceWithTimestamp(scanResult, currentTime))
                    }

                    // Remove entries older than 20 seconds
                    foundDevicesTimestamps.removeAll { device ->
                        currentTime - device.timestamp > 20_000
                    }
                    foundDevices = foundDevicesTimestamps.mapNotNull { it.device }.toMutableList()
                }
            }


        }


    @OptIn(ExperimentalUnsignedTypes::class)
    private val connectedCallback = object : BluetoothGattCallback() {
        @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            //println("New connected state: ${newState}")
            val deviceAddress = gatt.device.address
            if (newState == BluetoothProfile.STATE_DISCONNECTED || newState == BluetoothProfile.STATE_DISCONNECTING) {
                gatt.close()
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("","onConnectionStateChange: connected to $deviceAddress")
                    BleJob.Connect.resumeWithSuccess(gatt)
                    // Notify generic listeners
                    genericListeners.forEach { it.get()?.onConnect?.invoke(gatt.device) }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("","onConnectionStateChange: disconnected from $deviceAddress")
                    teardownConnection(gatt.device)
                } else {
                    Log.d("","Unknown onConnectionStateChange state $newState")
                }
            } else {
                Log.d("","onConnectionStateChange: status $status encountered for $deviceAddress!")
                teardownConnection(gatt.device)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("","Discovered ${services.size} services for ${gatt.device.address}")
                    printGattTable()
                    BleJob.DiscoverServices.resumeWithSuccess(Unit)
                } else {
//                    BleJob.DiscoverServices.resumeWithError("Service discovery failed due to status $status")
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            // For new API (API >= 33), 'value' is already a parameter in the method
            // For old API (API <= 32), 'value' is retrieved from the characteristic
            val value = characteristic.value

            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.d("","Read characteristic ${characteristic.uuid} : value: ${value.toHexString()}")
                    BleJob.Read.resumeWithSuccess(value.toUByteArray())
                }
                else -> {
                    BleJob.Read.resumeWithError("Characteristic read failed for $characteristic.uuid, error: $status")
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    //println("Wrote characteristic ${characteristic.uuid}")
                    BleJob.Write.resumeWithSuccess(Unit)
                }
                else -> {
                    BleJob.Write.resumeWithError("Characteristic write failed for ${characteristic.uuid}, error: $status")
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int
        ) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.d("","Wrote to descriptor ${descriptor.uuid}")
                    BleJob.EnableNotification.resumeWithSuccess(Unit)
                }
                else -> {
                    BleJob.EnableNotification.resumeWithError("Descriptor write failed for ${descriptor.uuid}, error: $status")
                }
            }
        }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        // The characteristic value is always retrieved from the characteristic object
        // This works for both old and new API versions
        val value = characteristic.value

        //println("Characteristic ${characteristic.uuid} changed | value: ${value.toHexString()}")

        // Find per-device listeners and notify
        deviceGattMap.values.first { it.gatt == gatt }.listeners.forEach { listener ->
            listener.get()?.onNotification?.run {
                this(characteristic.uuid, value.toUByteArray())
            }
        }
        // Notify generic listeners
        genericListeners.forEach {
            it.get()?.onNotification?.run { this(characteristic.uuid, value.toUByteArray()) }
        }
    }
    }

    /**
     * Extensions
     */

    private fun BluetoothDevice.isConnected() = deviceGattMap.containsKey(this)

    /**
     * Public API
     */

    fun servicesOf(deviceAddress: String): Result<List<BluetoothGattService>> =
        gattFromAddress(deviceAddress)?.let { gatt ->
            Result.success(gatt.services.toList())
        } ?: Result.failure(Exception("No device found with address $deviceAddress"))

    // Register for device specific callbacks
    fun registerListener(deviceAddress: String, listener: BleEventListener): Result<Unit> {
        deviceFromAddress(deviceAddress)?.let { bleDevice ->
            deviceGattMap[bleDevice]?.let {
                it.listeners.add(WeakReference(listener))
                Result.success(Unit)
            }
        }
            ?: return Result.failure(Exception("No connected device found with address $deviceAddress"))
        // Clean up garbage collected listeners
        deviceGattMap.forEachValue(Long.MAX_VALUE) { entry ->
            entry.listeners = entry.listeners.filter { it.get() != null }.toMutableSet()
        }
        return Result.success(Unit)
    }

    // Register for all callbacks
    fun registerListener(listener: BleEventListener): Result<Unit> {
        genericListeners.add(WeakReference(listener))
        // Clean up garbage collected listeners
        genericListeners = genericListeners.filter { it.get() != null }.toMutableSet()
        return Result.success(Unit)
    }

    // Unregister from device-specific and generic callbacks
    fun unregisterListener(listener: BleEventListener) {
        // Generic listeners
        genericListeners =
            genericListeners.filter { (it.get() != null) && (it.get() != listener) }.toMutableSet()

        // Device Specific listeners
        deviceGattMap.forEachValue(Long.MAX_VALUE) { entry ->
            entry.listeners =
                entry.listeners.filter { (it.get() != null) && (it.get() != listener) }
                    .toMutableSet()
        }
    }



    @RequiresPermission(value = "android.permission.BLUETOOTH_SCAN")
    suspend fun startScan(
        filters: List<ScanFilter>, settings: ScanSettings? = ScanSettings.Builder().build()
    ): MutableSharedFlow<ScanResult> {
        val flow = MutableSharedFlow<ScanResult>()
        val id = flow.hashCode()
        val callback = scanCallbackFactory(flow)
        scanObserverMap[id] = callback
        scanner.startScan(filters, settings, callback)
        return flow
    }


    @RequiresPermission(value = "android.permission.BLUETOOTH_SCAN")
    suspend fun stopScan(observer: MutableSharedFlow<ScanResult>): Result<Unit> {
        return try {
            val id = observer.hashCode()
            scanner.stopScan(scanObserverMap.getValue(id))
            foundDevicesTimestamps.clear() // Clear the devices list when stopping the scan
            foundDevices.clear() // If you maintain a separate list for UI, clear that too
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    suspend fun connect(deviceAddress: String): Result<Unit> {
        val bleDevice = deviceFromAddress(deviceAddress)
            ?: return Result.failure(Exception("No scanned device found with address $deviceAddress"))

        // Check if we're already connected
        if (bleDevice.isConnected()) {
            Log.i("","$deviceAddress is already connected.")
            return Result.success(Unit)
        }

        var status = Result.success(Unit) // Assume success and update if failure
        BleJob.Connect.suspendWithTimeout(10000) {
            bleDevice.connectGatt(
                context, false, connectedCallback
            )
        }.onSuccess { deviceGattMap[bleDevice] = DeviceEntry(it) }
            .onFailure { status = Result.failure(it) }
        return status
    }

    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    suspend fun discoverCharacteristics(deviceAddress: String): Result<Unit> {
        val gatt = gattFromAddress(deviceAddress)
            ?: return Result.failure(Exception("No device found with address $deviceAddress"))

        return BleJob.DiscoverServices.suspendWithTimeout(10000) {
            if (!gatt.discoverServices()) {
//                BleJob.DiscoverServices.resumeWithError("Failed to start service discovery")
            }
        }
    }

    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    suspend fun enableNotification(
        deviceAddress: String, characteristic: UUID
    ): Result<Unit> {
        Log.d("","Enabling notifications for $characteristic")
        val gatt = gattFromAddress(deviceAddress)
            ?: return Result.failure(Exception("No device found with address $deviceAddress"))
        val char = charFromUuid(gatt, characteristic)
            ?: return Result.failure(Exception("Characteristic $characteristic not found"))
        val payload = when {
            char.isIndicatable() -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            char.isNotifiable() -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> return Result.failure(Exception("$characteristic doesn't support notifications/indications"))
        }

        // Enable local notifications
        if (!gatt.setCharacteristicNotification(char, true)) {
            return Result.failure(
                Exception("setCharacteristicNotification failed for $characteristic")
            )
        }

        // Get the descriptor that enables the remote device to send notifications/indications
        val descriptor = char.getDescriptor(CoreUUID.CCC_DESCRIPTOR.uuid)
            ?: return Result.failure(Exception("Could not find CCC descriptor for $characteristic"))

        val apiLevel = Build.VERSION.SDK_INT
        return BleJob.EnableNotification.suspendWithTimeout(10000) {
            val writeDescriptorResult: Boolean = if (apiLevel >= Build.VERSION_CODES.TIRAMISU) {
                //println("enableNotification writeCharacteristic NEW API")
                // New API (API Level >= 33)
                descriptor.value = payload
                gatt.writeDescriptor(descriptor)
            } else {
                //println("enableNotification writeCharacteristic API<33")
                // Old API (API Level <= 32)
                descriptor.value = payload
                gatt.writeDescriptor(descriptor)
            }

            if (!writeDescriptorResult) {
                Result.failure(Exception("writeDescriptor failed for ${descriptor.uuid}"))
            } else {
                Result.success(Unit)
            }
        } ?: Result.failure(Exception("Enabling notification did not complete within the timeout period"))

    }


    @OptIn(ExperimentalUnsignedTypes::class)
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    suspend fun readCharacteristic(
        deviceAddress: String, characteristicUuid: UUID, timeout: Long = 5000
    ): Result<UByteArray> {
        //println("Attempting to connect to device at address: $deviceAddress")
        val gatt = gattFromAddress(deviceAddress)
            ?: return Result.failure(Exception("No device found with address $deviceAddress").also { e ->
                //println("Failed to find device: ${e.message}")
            })
        //println("Device connected. Looking for characteristic: $characteristicUuid")
        val char = charFromUuid(gatt, characteristicUuid)
            ?: return Result.failure(Exception("Characteristic $characteristicUuid not found").also { e ->
                //println("Failed to find characteristic: ${e.message}")
            })

        //println("Characteristic found. Initiating read.")
        return BleJob.Read.suspendWithTimeout(timeout) {
            if (!gatt.readCharacteristic(char)) {
                //println("Failed to initiate read of characteristic: $characteristicUuid")
                BleJob.Read.resumeWithError("Read of $characteristicUuid failed")
            }
            // No need to handle the value here, it will be handled in the onCharacteristicRead callback
        }.also {
            //println("Read operation completed with result: $it")
        }
    }




    @SuppressLint("NewApi")
    @OptIn(ExperimentalUnsignedTypes::class)
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    suspend fun writeCharacteristic(
        deviceAddress: String, characteristic: UUID, payload: UByteArray
    ): Result<Unit> {
        return try {
            val gatt = gattFromAddress(deviceAddress)
                ?: return Result.failure(Exception("No device found with address $deviceAddress"))
            val char = charFromUuid(gatt, characteristic)
                ?: return Result.failure(Exception("Characteristic $characteristic not found"))

            val apiLevel = Build.VERSION.SDK_INT
            val writeType = when {
                char.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                char.isWritableWithoutResponse() -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                else -> return Result.failure(Exception("Characteristic $characteristic cannot be written to"))
            }

            BleJob.Write.suspendWithTimeout(5000) {
                val writeResult = if (apiLevel >= Build.VERSION_CODES.TIRAMISU) {
                    //println("writeCharacteristic NEW API")
                    // New API (API Level >= 33)
                    gatt.writeCharacteristic(char, payload.toByteArray(), writeType)
                } else {
                    //println("writeCharacteristic API<33")
                    // Old API (API Level < 33)
                    char.writeType = writeType
                    char.value = payload.toByteArray()
                    gatt.writeCharacteristic(char)
                }

                if (writeResult != BluetoothStatusCodes.SUCCESS) {
                    Result.failure(Exception("Write of $characteristic failed"))
                } else {
                    Result.success(Unit)
                }
            } ?: Result.failure(Exception("Write operation did not complete"))

        } catch (e: Exception) {
            // Handle any other exceptions that might be thrown.
            Result.failure(e)
        }
    }


    /**
     * Cleans up all Bluetooth resources.
     * This method should be called in the onDestroy() method of your activity or service
     * to ensure proper resource management.
     */
    @SuppressLint("MissingPermission")
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    fun cleanup() {
        // Close all Gatt connections
        for ((device, deviceEntry) in deviceGattMap) {
            try {
                deviceEntry.gatt.close()
            } catch (e: Exception) {
                Log.w("","Error closing Gatt connection for device ${device.address}: ${e.message}")
            }
            // Notify listeners about disconnection
            deviceEntry.listeners.forEach { it.get()?.onDisconnect?.invoke(device) }

            // Clear device-specific listeners
            deviceEntry.listeners.clear()
        }
        deviceGattMap.clear()

        // Stop any ongoing scans
        for ((_, callback) in scanObserverMap) {
            try {
                scanner.stopScan(callback)
            } catch (e: Exception) {
                Log.w("","Error stopping scan: ${e.message}")
            }
        }
        scanObserverMap.clear()

        // Clear all listeners
        genericListeners.clear()
    }


}
