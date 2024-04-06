package com.cliptracer.ClipTracer.goproutil

import kotlinx.serialization.encodeToString
import kotlin.properties.Delegates


@OptIn(ExperimentalUnsignedTypes::class)
sealed class Response<T > {
    private enum class Header(val value: UByte) {
        GENERAL(0b00U), EXT_13(0b01U), EXT_16(0b10U), RESERVED(0b11U);

        companion object {
            private val valueMap: Map<UByte, Header> by lazy {
                Header.values().associateBy { it.value }
            }

            fun fromValue(value: Int) = valueMap.getValue(value.toUByte())
        }
    }

    private enum class Mask(val value: UByte) {
        Header(0b01100000U),
        Continuation(0b10000000U),
        GenLength(0b00011111U),
        Ext13Byte0(0b00011111U)
    }

    private var bytesRemaining = 0
    protected var packet = ubyteArrayOf()
    abstract val data: T
    var id by Delegates.notNull<Int>()
        protected set
    var status by Delegates.notNull<Int>()
        protected set

    val isReceived get() = bytesRemaining == 0
    var isParsed = false
        protected set

    override fun toString() = prettyJson.encodeToString(data.toJsonElement())

    fun accumulate(data: UByteArray) {
        var buf = data
        // If this is a continuation packet
        if (data.first().and(Mask.Continuation.value) == Mask.Continuation.value) {
            buf = buf.drop(1).toUByteArray() // Pop the header byte
        } else {
            // This is a new packet so start with empty array
            packet = ubyteArrayOf()
            when (Header.fromValue((buf.first() and Mask.Header.value).toInt() shr 5)) {
                Header.GENERAL -> {
                    bytesRemaining = buf[0].and(Mask.GenLength.value).toInt()
                    buf = buf.drop(1).toUByteArray()
                }
                Header.EXT_13 -> {
                    bytesRemaining = ((buf[0].and(Mask.Ext13Byte0.value)
                        .toLong() shl 8) or buf[1].toLong()).toInt()
                    buf = buf.drop(2).toUByteArray()
                }
                Header.EXT_16 -> {
                    bytesRemaining = ((buf[1].toLong() shl 8) or buf[2].toLong()).toInt()
                    buf = buf.drop(3).toUByteArray()
                }
                Header.RESERVED -> {
                    throw Exception("Unexpected RESERVED header")
                }
            }
        }
        // Accumulate the payload now that headers are handled and dropped
        packet += buf
        bytesRemaining -= buf.size
//        Log.i("","Received packet of length ${buf.size}. $bytesRemaining bytes remaining")

//        if (bytesRemaining < 0) {
//            println("Unrecoverable parsing error. Received too much data.")
//        }
    }

    abstract fun parse()

    class Complex : Response<MutableList<UByteArray>>() {
        override val data: MutableList<UByteArray> = mutableListOf()

        override fun parse() {
            require(isReceived)
            // Parse header bytes
            id = packet[0].toInt()
            status = packet[1].toInt()
            var buf = packet.drop(2)
            // Parse remaining packet
            while (buf.isNotEmpty()) {
                // Get each parameter's ID and length
                val paramLen = buf[0].toInt()
                buf = buf.drop(1)
                // Get the parameter's value
                val paramVal = buf.take(paramLen)
                // Store in data list
                data += paramVal.toUByteArray()
                // Advance the buffer for continued parsing
                buf = buf.drop(paramLen)
            }
            isParsed = true
        }
    }

    class Query : Response<MutableMap<UByte, UByteArray>>() {
        override val data: MutableMap<UByte, UByteArray> = mutableMapOf()

        override fun parse() {
            require(isReceived)
            id = packet[0].toInt()
            status = packet[1].toInt()
            // Parse remaining packet
            var buf = packet.drop(2)
            while (buf.isNotEmpty()) {
                // Get each parameter's ID and length
                val paramId = buf[0]
                val paramLen = buf[1].toInt()
                buf = buf.drop(2)
                // Get the parameter's value
                val paramVal = buf.take(paramLen)
                // Store in data dict for access later
                data[paramId] = paramVal.toUByteArray()
                // Advance the buffer for continued parsing
                buf = buf.drop(paramLen)
            }
            isParsed = true
        }
    }

    companion object {
        fun fromUuid(uuid: GoProUUID): Response<*> =
            when (uuid) {
                GoProUUID.CQ_COMMAND_RSP -> Complex()
                GoProUUID.CQ_QUERY_RSP -> Query()
                else -> throw Exception("Not supported")
            }
    }

}
