/* Extensions.kt/Open GoPro, Version 2.0 (C) Copyright 2021 GoPro, Inc. (http://gopro.com/OpenGoPro). */
/* This copyright was auto-generated on Mon Mar  6 17:45:14 UTC 2023 */

package com.cliptracer.ClipTracer.goproutil

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.util.Log
import kotlinx.serialization.json.*
import org.json.JSONObject
import java.util.*

/**
 * Most of these are taken from https://punchthrough.com/android-ble-guide/
 */

fun ByteArray.toHexString(): String = joinToString(separator = ":") { String.format("%02X", it) }

@OptIn(ExperimentalUnsignedTypes::class)
fun UByteArray.toHexString(): String = this.toByteArray().toHexString()

@OptIn(ExperimentalUnsignedTypes::class)
fun UByteArray.decodeToString(): String = this.toByteArray().decodeToString()

fun BluetoothGatt.findCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
    services?.forEach { service ->
        service.characteristics?.firstOrNull { characteristic ->
            characteristic.uuid == uuid
        }?.let { matchingCharacteristic ->
            return matchingCharacteristic
        }
    }
    return null
}

fun BluetoothGattDescriptor.containsPermission(permission: Int): Boolean =
    permissions and permission != 0

fun BluetoothGattDescriptor.isReadable(): Boolean =
    containsPermission(BluetoothGattDescriptor.PERMISSION_READ)

fun BluetoothGattDescriptor.isWritable(): Boolean =
    containsPermission(BluetoothGattDescriptor.PERMISSION_WRITE)

fun BluetoothGattDescriptor.printProperties(): String = mutableListOf<String>().apply {
    if (isReadable()) add("READABLE")
    if (isWritable()) add("WRITABLE")
    if (isEmpty()) add("EMPTY")
}.joinToString()

fun BluetoothGattCharacteristic.isReadable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

fun BluetoothGattCharacteristic.isWritable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

fun BluetoothGattCharacteristic.isIndicatable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean =
    properties and property != 0

fun BluetoothGattCharacteristic.printProperties(): String = mutableListOf<String>().apply {
    if (isReadable()) add("READABLE")
    if (isWritable()) add("WRITABLE")
    if (isWritableWithoutResponse()) add("WRITABLE WITHOUT RESPONSE")
    if (isIndicatable()) add("INDICATABLE")
    if (isNotifiable()) add("NOTIFIABLE")
    if (isEmpty()) add("EMPTY")
}.joinToString()

fun BluetoothGatt.printGattTable() {
    if (services.isEmpty()) {
        Log.i("","No service and characteristic available, call discoverServices() first?")
        return
    }

    services.forEach { service ->
        val characteristicsTable = service.characteristics.joinToString(
            separator = "\n|--", prefix = "|--"
        ) { char ->
            var description = "${char.uuid}: ${char.printProperties()}"
            if (char.descriptors.isNotEmpty()) {
                description += "\n" + char.descriptors.joinToString(
                    separator = "\n|------", prefix = "|------"
                ) { descriptor ->
                    "${descriptor.uuid}: ${descriptor.printProperties()}"
                }
            }
            description
        }
        Log.d("","Service ${service.uuid}\nCharacteristics:\n$characteristicsTable")
    }
}

/**
 * Kotlinx serialization of generic map
 */

val prettyJson by lazy { Json { prettyPrint = true } }

fun Array<*>.toJsonArray(): JsonArray {
    val array = mutableListOf<JsonElement>()
    this.forEach { array.add(it.toJsonElement()) }
    return JsonArray(array)
}

fun List<*>.toJsonArray(): JsonArray {
    val array = mutableListOf<JsonElement>()
    this.forEach { array.add(it.toJsonElement()) }
    return JsonArray(array)
}

fun Map<*, *>.toJsonObject(): JsonObject {
    val map = mutableMapOf<String, JsonElement>()
    this.forEach {
        val keyStr = it.key.toString()
        if (map.containsKey(keyStr)) {
            throw Exception("Encoding duplicate keys $keyStr")
        }
        map[keyStr] = it.value.toJsonElement()
    }
    return JsonObject(map)
}

@OptIn(ExperimentalUnsignedTypes::class)
fun Any?.toJsonElement(): JsonElement {
    return when (this) {
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is Array<*> -> this.toJsonArray()
        is List<*> -> this.toJsonArray()
        is Map<*, *> -> this.toJsonObject()
        is UByteArray -> this.toHexString().toJsonElement()
        is UByte -> ubyteArrayOf(this).toJsonElement()
        is JsonElement -> this
        null -> JsonNull
        else -> {
            throw Exception("Can not encode value ${this::class} to JSON")
        }
    }
}

class TwoWayDict<K, V> where K : Any, V : Any {
    private val keyToValue = mutableMapOf<K, V>()
    private val valueToKey = mutableMapOf<V, K>()

    val values: List<V>
        get() = keyToValue.values.toList()

    val keys: List<K>
        get() = keyToValue.keys.toList()

    operator fun set(key: K, value: V) {
        keyToValue[key] = value
        valueToKey[value] = key
    }

    operator fun get(key: K): V? = keyToValue[key]

    fun getValueByKey(key: K): V? = keyToValue[key]
    fun getKeyByValue(value: V): K? = valueToKey[value]

    constructor(vararg pairs: Pair<K, V>) {
        for ((key, value) in pairs) {
            this[key] = value
        }
    }
}



fun JSONObject.toMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        map[key] = getString(key)
    }
    return map
}