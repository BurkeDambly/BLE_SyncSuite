package com.example.ble_sync_suite_app

import androidx.compose.runtime.mutableStateMapOf
import java.util.UUID

// --- Data classes ---
data class EspPacket(
    val seq: Long,
    val tUs: Long,
    val receivedAtNs: Long
)

data class CharacteristicInfo(
    val serviceUuid: UUID,
    val serviceName: String,
    val charUuid: UUID,
    val charName: String,
    val properties: String
)

// --- Constants ---
const val PERMISSION_BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
const val PERMISSION_BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
val CLIENT_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
val ESP32_SERVICE_UUID = UUID.fromString("0000181a-0000-1000-8000-00805f9b34fb")
val ESP32_CHAR_UUID = UUID.fromString("0015a1a1-1212-efde-1523-785feabcd123")

val standardServiceNames = mapOf(
    UUID.fromString("00001800-0000-1000-8000-00805f9b34fb") to "Generic Access",
    UUID.fromString("00001801-0000-1000-8000-00805f9b34fb") to "Generic Attribute",
    UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb") to "Heart Rate",
    UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb") to "Battery Service"
)

val standardCharacteristicNames = mapOf(
    UUID.fromString("00002A00-0000-1000-8000-00805f9b34fb") to "Device Name",
    UUID.fromString("00002A01-0000-1000-8000-00805f9b34fb") to "Appearance",
    UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb") to "Heart Rate Measurement",
    UUID.fromString("00002A38-0000-1000-8000-00805f9b34fb") to "Body Sensor Location",
    UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb") to "Battery Level"
)

val readValues = mutableStateMapOf<UUID, String>()

// --- Byte parsing ---
fun u32LE(bytes: ByteArray, offset: Int): Long =
    ((bytes[offset].toUByte().toLong() and 0xFF) shl 0) or
            ((bytes[offset + 1].toUByte().toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toUByte().toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toUByte().toLong() and 0xFF) shl 24)

fun u64LE(bytes: ByteArray, offset: Int): Long =
    ((bytes[offset].toUByte().toLong() and 0xFF) shl 0) or
            ((bytes[offset + 1].toUByte().toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toUByte().toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toUByte().toLong() and 0xFF) shl 24) or
            ((bytes[offset + 4].toUByte().toLong() and 0xFF) shl 32) or
            ((bytes[offset + 5].toUByte().toLong() and 0xFF) shl 40) or
            ((bytes[offset + 6].toUByte().toLong() and 0xFF) shl 48) or
            ((bytes[offset + 7].toUByte().toLong() and 0xFF) shl 56)
