// This defines the package name — it matches the folder structure and helps Android locate your code
package com.example.ble_sync_suite_app

// --- Android System Imports ---

import android.Manifest // Android constants for all dangerous permissions (e.g., BLUETOOTH_SCAN, LOCATION)
import android.content.pm.PackageManager // Lets us check if a permission is granted (returns GRANTED or DENIED)
import android.os.Bundle // Carries info if this activity is being reloaded from saved state (e.g. screen rotation)
import android.widget.Toast // Android popup messages shown at the bottom of the screen (non-blocking)

// --- Jetpack Compose Activity and Permission Tools ---

import androidx.activity.ComponentActivity // This is the Compose version of an activity (no XML layout needed)
import androidx.activity.compose.setContent // Required to render Compose-based UI content into the activity
import androidx.activity.result.contract.ActivityResultContracts // Modern way to request permissions and receive results

// --- Jetpack Compose UI Elements ---

import androidx.core.content.ContextCompat // Lets us check if each individual permission has been granted
import androidx.compose.material3.Text // Basic text UI element (like a label)
import androidx.compose.material3.MaterialTheme // Access to app-wide color, font, padding theme settings
import androidx.compose.material3.Surface // A container composable that fills the screen and applies background

// --- App-Specific Theme Import ---

import com.example.ble_sync_suite_app.ui.theme.BleSyncSuiteAppTheme

// --- Bluetooth Connection ---
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

// --- Jetpack Compose: State-Driven Lists ---
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

// --- GUI Elements ---
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.TextButton

// --- Animations ---
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import android.bluetooth.le.ScanSettings
import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import java.util.Locale
import android.os.Handler
import android.os.SystemClock
import android.annotation.SuppressLint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.collectAsState

// --- Main App Logic Starts Here ---

// ESP32 notification packet data class
data class EspPacket(
    val seq: Long,           // uint32 sequence number
    val tUs: Long,           // uint64 timestamp in microseconds since boot
    val receivedAtNs: Long   // SystemClock.elapsedRealtimeNanos() when received
)

// Simple data class to represent each characteristic and its service
data class CharacteristicInfo(
    val serviceUuid: UUID,
    val serviceName: String,
    val charUuid: UUID,
    val charName: String,
    val properties: String
)


// Common BLE Service and Characteristic UUIDs with names
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

private const val PERMISSION_BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
private const val PERMISSION_BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
private val CLIENT_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

// ESP32 service and characteristic UUIDs
// Service: Environmental Sensing (0x181A) - 16-bit UUID
private val ESP32_SERVICE_UUID = UUID.fromString("0000181a-0000-1000-8000-00805f9b34fb")  // 0x181A
// Characteristic: Custom 128-bit UUID (0015a1a1-1212-efde-1523-785feabcd123)
private val ESP32_CHAR_UUID = UUID.fromString("0015a1a1-1212-efde-1523-785feabcd123")  // notify characteristic

// Helper functions to parse little-endian unsigned integers from ByteArray
private fun u32LE(bytes: ByteArray, offset: Int): Long {
    return ((bytes[offset + 0].toUByte().toLong() and 0xFF) shl 0) or
           ((bytes[offset + 1].toUByte().toLong() and 0xFF) shl 8) or
           ((bytes[offset + 2].toUByte().toLong() and 0xFF) shl 16) or
           ((bytes[offset + 3].toUByte().toLong() and 0xFF) shl 24)
}

private fun u64LE(bytes: ByteArray, offset: Int): Long {
    return ((bytes[offset + 0].toUByte().toLong() and 0xFF) shl 0) or
           ((bytes[offset + 1].toUByte().toLong() and 0xFF) shl 8) or
           ((bytes[offset + 2].toUByte().toLong() and 0xFF) shl 16) or
           ((bytes[offset + 3].toUByte().toLong() and 0xFF) shl 24) or
           ((bytes[offset + 4].toUByte().toLong() and 0xFF) shl 32) or
           ((bytes[offset + 5].toUByte().toLong() and 0xFF) shl 40) or
           ((bytes[offset + 6].toUByte().toLong() and 0xFF) shl 48) or
           ((bytes[offset + 7].toUByte().toLong() and 0xFF) shl 56)
}


// Main logic
class MainActivity : ComponentActivity() {

    // This is a relative list that updates the UI when modified.
    // We can store the device names and addr as string in a list
    private val scannedDevices: SnapshotStateList<String> = mutableStateListOf()

    // Keeps track of whether the welcome screen is currently showing.
    // Once the animation ends, we set this to false and show the scanner list.
    private var showWelcomeScreen by mutableStateOf(true)
    private var showMainMenu by mutableStateOf(false)
    private var showScannerScreen by mutableStateOf(false)
    private var startScanWhenPermissionGranted = false

    // Keeps track of the switch state for BLE scanning
    private var isScanning by mutableStateOf(false)

    // Used for keeping track of the available connections with filter
    private var searchQuery by mutableStateOf("")

    // Holds the connection to the GATT server once connected
    private var bluetoothGatt: BluetoothGatt? = null

    // Used to move into the graphing screen
    private var showDataScreen by mutableStateOf(false)
    private var showGraphScreen by mutableStateOf(false)

    // Signals when the device connects
    private var connectedDeviceName by mutableStateOf("")

    // The data coming from the device
    private val receivedDataList = mutableStateListOf<String>()
    private val bleBuffer = StringBuilder()

    // Holds the list of all discovered characteristics for UI display
    private val characteristicInfoList = mutableStateListOf<CharacteristicInfo>()

    // Track the characteristic to graph
    private var graphCharUuid: UUID? = null
    private var graphServiceUuid: UUID? = null

    // ESP32 packet state - latest received packet
    private val _latestEspPacket = MutableStateFlow<EspPacket?>(null)
    val latestEspPacket: StateFlow<EspPacket?> = _latestEspPacket.asStateFlow()
    
    // Store all ESP32 packets for graphing
    private val esp32PacketHistory = mutableStateListOf<EspPacket>()

    // This is a list of all the permissions we want to request from the user at runtime.
    // Android doesn't grant these automatically; the user must approve them.
    private val isAtLeastS: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private fun permissionsToRequest(): Array<String> {
        val required = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (isAtLeastS) {
            required += listOf(PERMISSION_BLUETOOTH_SCAN, PERMISSION_BLUETOOTH_CONNECT)
        }

        return required.toTypedArray()
    }

    private fun hasConnectPermission(context: Context = this): Boolean {
        if (!isAtLeastS) return true
        return ContextCompat.checkSelfPermission(context, PERMISSION_BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasScanPermission(): Boolean {
        if (!isAtLeastS) return true
        return ContextCompat.checkSelfPermission(this, PERMISSION_BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    @SuppressLint("MissingPermission")
    private fun writeClientConfigValue(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value)
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                val method = descriptor.javaClass.getMethod("setValue", ByteArray::class.java)
                method.invoke(descriptor, value)
                gatt.writeDescriptor(descriptor)
            }.onFailure {
                Log.e("BLE", "Legacy descriptor write failed", it)
            }
        }
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    @SuppressLint("MissingPermission")
    private fun setNotificationsForCharacteristic(
        info: CharacteristicInfo,
        enable: Boolean
    ): Boolean {
        val gatt = bluetoothGatt ?: return false
        val characteristic = gatt.getService(info.serviceUuid)?.getCharacteristic(info.charUuid) ?: return false

        val supportsNotify = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        if (!supportsNotify) return false

        gatt.setCharacteristicNotification(characteristic, enable)
        characteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR_UUID)?.let { descriptor ->
            val value = if (enable) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            }
            writeClientConfigValue(gatt, descriptor, value)
        }
        return true
    }

    // This will be used to start BLE scanning
    // lateinit allows for a late initialization of the value of type BluetoothAdapter
    private lateinit var bluetoothAdapter: BluetoothAdapter
    // The BLE scanner can be null, might not be available on some devices.
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    // This is the modern replacement for the old onRequestPermissionsResult() method.
    // We "register" a launcher object, and Android automatically calls this lambda when the user responds.
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->

            // The `results` object is a Map<String, Boolean>
            // Key = permission name (e.g., "android.permission.BLUETOOTH_SCAN")
            // Value = true if granted, false if denied

            // Check if ANY permission was denied
            val denied = results.any { !it.value }

            if (denied) {
                // Show a message if one or more permissions were not granted
                Toast.makeText(this, "Some permissions were denied.", Toast.LENGTH_LONG).show()
                startScanWhenPermissionGranted = false
            } else {
                // All permissions granted — app is ready to scan/connect
                Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()

                if (startScanWhenPermissionGranted && hasScanPermission()) {
                    startScanWhenPermissionGranted = false
                    isScanning = true
                    startBleScan()
                } else {
                    startScanWhenPermissionGranted = false
                }
            }
        }

    // This function is automatically called by Android when the app starts (like "main()" in other languages)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set up the UI using Jetpack Compose instead of XML layout
        setContent {
            BleSyncSuiteAppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    when {
                        showWelcomeScreen -> WelcomeScreen {
                            showWelcomeScreen = false
                            showMainMenu = true
                        }
                        showGraphScreen -> GraphScreen(
                            onBack = { showGraphScreen = false }
                        )
                        showDataScreen -> DataDisplayScreen(
                            deviceName = connectedDeviceName,
                            onBack = {
                                showDataScreen = false
                                showGraphScreen = false
                                if (hasScanPermission()) {
                                    stopBleScan()
                                }
                                isScanning = false
                                if (hasConnectPermission()) {
                                    try {
                                        bluetoothGatt?.disconnect()
                                    } catch (securityException: SecurityException) {
                                        Log.e("BLE", "Disconnect rejected by system", securityException)
                                    }
                                    try {
                                        bluetoothGatt?.close()
                                    } catch (securityException: SecurityException) {
                                        Log.e("BLE", "Close rejected by system", securityException)
                                    }
                                } else {
                                    try {
                                        bluetoothGatt?.close()
                                    } catch (securityException: SecurityException) {
                                        Log.e("BLE", "Close rejected without permission", securityException)
                                    }
                                }
                                bluetoothGatt = null
                                showMainMenu = true
                            }
                        )
                        showScannerScreen -> MainScannerScreen(
                            onBack = {
                                stopBleScan()
                                isScanning = false
                                showScannerScreen = false
                                showMainMenu = true
                            }
                        )
                        showMainMenu -> MainMenuScreen(
                            onConnectToDevice = {
                                showMainMenu = false
                                showScannerScreen = true
                            }
                        )
                        else -> MainMenuScreen(
                            onConnectToDevice = {
                                showMainMenu = false
                                showScannerScreen = true
                            }
                        )
                    }
                }
            }
        }

        // Get the system's Bluetooth service from Android. This returns a BluetoothManager object.
        // We're using "getSystemService" to get a system-level object. It's like a global accessor.
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager

        // From the BluetoothManager, get the BluetoothAdapter (this represents the actual Bluetooth hardware interface).
        val adapter = bluetoothManager.adapter

        // Defensive check: if the device has no Bluetooth adapter, show an error and return early.
        // This prevents us from trying to scan on devices that don't support BLE at all.
        if (adapter == null) {
            Log.e("BLE", "Bluetooth not supported on this device") // Print an error to Logcat
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show() // Show a user-visible message
            return // Exit the onCreate function so we don’t continue trying to scan
        }

        bluetoothAdapter = adapter

        // After UI is drawn, check if we have permissions, and request them if we don’t
        requestPermissionsModernWay()
    }



    // This function filters out already-granted permissions and only asks for what’s missing
    private fun requestPermissionsModernWay() {
        val neededPermissions = permissionsToRequest().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        // If we’re missing any permissions, ask for them using the modern launcher
        if (neededPermissions.isNotEmpty()) {
            permissionLauncher.launch(neededPermissions.toTypedArray())
        } else {
            // Nothing to request — let the user know
            Toast.makeText(this, "All permissions already granted!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureScanPermission(onGranted: () -> Unit) {
        if (!isAtLeastS) {
            onGranted()
            return
        }

        if (hasScanPermission()) {
            onGranted()
        } else {
            startScanWhenPermissionGranted = true
            permissionLauncher.launch(arrayOf(PERMISSION_BLUETOOTH_SCAN))
            Toast.makeText(
                this,
                "Scan permission is required to discover devices.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    // THis function starts the bluetooth low energy scan
    // it uses the adapter to get the ble scanner, then starts scanning and logs the action
    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (!hasScanPermission()) {
            Log.e("BLE", "BLUETOOTH_SCAN permission not granted — scan aborted")
            Toast.makeText(this, "Scan permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner

        if (scanner == null) {
            Log.e("BLE", "BLE scanner unavailable on this device")
            Toast.makeText(this, "BLE scanner unavailable", Toast.LENGTH_SHORT).show()
            return
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, scanSettings, bleScanCallback)
        bluetoothLeScanner = scanner
    }




    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        if (!hasScanPermission()) {
            Log.e("BLE", "BLUETOOTH_SCAN permission not granted — scan aborted")
            Toast.makeText(this, "Scan permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        val scanner = bluetoothLeScanner ?: bluetoothAdapter.bluetoothLeScanner

        if (scanner == null) {
            Log.w("BLE", "No scanner instance available to stop")
            return
        }

        scanner.stopScan(bleScanCallback)
    }



    // This is the callback that Android calls every time a BLE device is found while scanning
    private val bleScanCallback = object : ScanCallback(){
        // Called when a device is found

        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            // Add this guard clause to prevent race condition
            if (!isScanning) return

            // Check if we have permission to read Bluetooth device details
            val hasConnectPermission = hasConnectPermission()

            if (hasConnectPermission) {
                val deviceName = result.device.name ?: "Unnamed"
                val deviceAddress = result.device.address
                val displayString = "$deviceName [$deviceAddress]"

                if(!scannedDevices.contains(displayString)){
                    scannedDevices.add(displayString)
                }

            } else {
                Log.w("BLE", "BLUETOOTH_CONNECT permission not granted — skipping device info")
            }

        }


        // Called if scanning fails for some reason
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)

            // Log an error message
            Log.e("BLE", "Scan failed with error code: $errorCode")
        }
    }



    // Callback to handle GATT events such as connection state changes and service discovery
    private val gattCallback = object : BluetoothGattCallback() {

        @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("BLE", "GATT connection failed, status=$status")

                val deviceName = gatt.device.name ?: "Unnamed"

                // Notify user on the UI thread
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Lost connection to $deviceName",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Close the connection to clean up resources
                gatt.close()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // Successfully connected
                    Log.i("BLE", "✅ Connected to GATT server. Discovering services...")

                    val deviceName = gatt.device.name ?: "Unnamed"

                    // Show toast to indicate connection
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Connected to $deviceName",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    // Update UI state on the main thread
                    runOnUiThread {
                        connectedDeviceName = deviceName
                        showScannerScreen = false
                        showDataScreen = true // Trigger screen change to data display
                    }

                    // Start discovering services on the GATT server
                    gatt.requestMtu(247)
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    // Device got disconnected
                    Log.w("BLE", "Disconnected from GATT server")

                    val deviceName = gatt.device.name ?: "Unnamed"

                    // Notify user of disconnection
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "❌ Disconnected from $deviceName",
                            Toast.LENGTH_SHORT
                        ).show()
                        isScanning = false
                        showDataScreen = false
                        showGraphScreen = false
                        showScannerScreen = false
                        showMainMenu = true
                    }
                    gatt.close()
                    bluetoothGatt = null
                }

                else -> {
                    // Unknown state encountered
                    Log.w("BLE", "⚠️ Unknown connection state: $newState")
                }
            }
        }



        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // Only handle ESP32 notifications on ESP32_CHAR_UUID - ignore all other characteristics
            if (characteristic.uuid != ESP32_CHAR_UUID) {
                return
            }

            @Suppress("DEPRECATION")
            val value = characteristic.value
            
            // Decode ESP32 12-byte payload
            if (value == null || value.size < 12) {
                Log.w("ESP32", "Invalid packet size: ${value?.size ?: 0}, expected 12 bytes")
                return
            }

            try {
                val seq = u32LE(value, 0)        // bytes[0..3] = uint32 seq
                val tUs = u64LE(value, 4)        // bytes[4..11] = uint64 timestamp
                val receivedAtNs = SystemClock.elapsedRealtimeNanos()

                val packet = EspPacket(seq = seq, tUs = tUs, receivedAtNs = receivedAtNs)

                // Log the packet
                Log.d("ESP32", "ESP pkt seq=$seq, tUs=$tUs, receivedAtNs=$receivedAtNs, len=${value.size}")

                // Update StateFlow for UI
                _latestEspPacket.value = packet
                
                // Add to history for graphing (keep last 1000 packets)
                this@MainActivity.runOnUiThread {
                    esp32PacketHistory.add(packet)
                    if (esp32PacketHistory.size > 1000) {
                        esp32PacketHistory.removeAt(0)
                    }
                }
            } catch (e: Exception) {
                Log.e("ESP32", "Error decoding packet", e)
            }
        }




        // Callback invoked after writing a descriptor (e.g., the CCCD)
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            // Log what was written and the status code returned by the stack
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("BLE", "❌ Descriptor write failed")
            }
        }



        // Called when BLE services have been discovered on the connected device
        @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)

            // Check if service discovery was successful
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w("BLE", "❌ Service discovery failed: $status")
                return
            }

            // Enable notifications for ESP32 characteristic (ESP32_CHAR_UUID) if found
            gatt.getService(ESP32_SERVICE_UUID)
                ?.getCharacteristic(ESP32_CHAR_UUID)
                ?.let { tx ->
                    gatt.setCharacteristicNotification(tx, true)
                    tx.getDescriptor(CLIENT_CONFIG_DESCRIPTOR_UUID)?.let { cccd ->
                        writeClientConfigValue(gatt, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    }
                    graphCharUuid = tx.uuid
                    graphServiceUuid = ESP32_SERVICE_UUID
                    Log.i("BLE", "✅ Subscribed to ESP32 sensor characteristic")
                    
                    // Only add the ESP32 characteristic to the UI list (must run on UI thread)
                    runOnUiThread {
                        val props = tx.properties
                        val propsList = mutableListOf<String>().apply {
                            if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
                            if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
                            if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
                            if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
                        }.joinToString()

                        val serviceName = standardServiceNames[ESP32_SERVICE_UUID] ?: "Environmental Sensing"
                        val charName = "ESP32 Sensor Data"
                        val info = CharacteristicInfo(ESP32_SERVICE_UUID, serviceName, tx.uuid, charName, propsList)
                        
                        characteristicInfoList.clear()
                        characteristicInfoList.add(info)
                        Log.i("BLE", "✅ Added ESP32 characteristic to UI list")
                    }
                } ?: Log.e("BLE", "❌ ESP32 sensor characteristic not found")

        }


        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                @Suppress("DEPRECATION")
                val bytes = characteristic.value
                val value = bytes?.joinToString(" ") { it.toUByte().toString() } ?: "null"

                // 👇 this must update the same map the Composable sees
                readValues[characteristic.uuid] = value

                Log.i("BLE", "✅ Read from ${characteristic.uuid}: $value")
            } else {
                Log.w("BLE", "❌ Failed to read characteristic ${characteristic.uuid}, status: $status")
            }
        }

    }



    // Function to initiate a BLE connection to a device by its MAC address
    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    @SuppressLint("MissingPermission")
    private fun connectToDevice(address: String) {
        // Permission check
        if (!hasConnectPermission()) {
            Toast.makeText(this, "Permission denied for connecting", Toast.LENGTH_SHORT).show()
            return
        }

        // ⬇️ 1)  stop scan & mark flag
        stopBleScan()
        isScanning = false

        // ⬇️ 2)  close any previous GATT cleanly
        bluetoothGatt?.close()
        bluetoothGatt = null

        val device = bluetoothAdapter.getRemoteDevice(address)

        // ⬇️ 3)  small delay then connect **with LE transport**
        Handler(mainLooper).postDelayed({
            bluetoothGatt = device.connectGatt(
                this,
                /* autoConnect = */ false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE   // <-- key line
            )

            Toast.makeText(this, "Connecting to $address", Toast.LENGTH_SHORT).show()
        }, 750)   // 0.75 s delay prevents stack race
    }





    // This composable function displays a welcome screen with a fade-in and zoom-in animation.
    // It shows the app title and subtitle at launch, then transitions to the main screen.
    @Composable
    private fun WelcomeScreen(onAnimationFinished: () -> Unit) {
        // State variable that controls when to start the animations
        val visible = remember { mutableStateOf(false) }

        // Animates the screen's opacity from 0 (invisible) to 1 (fully visible)
        val alpha by animateFloatAsState(
            targetValue = if (visible.value) 1f else 0f, // Animate to visible if 'visible' is true
            animationSpec = tween(durationMillis = 1000) // Duration of the fade-in animation
        )

        // Animates the screen's scale from 95% to 100% for a subtle zoom-in effect
        val scale by animateFloatAsState(
            targetValue = if (visible.value) 1f else 0.95f,
            animationSpec = tween(durationMillis = 1000) // Duration of the scale animation
        )

        // Launch the animation and hold the welcome screen for 3 seconds
        LaunchedEffect(Unit) {
            visible.value = true // Start fade/scale animations
            delay(2700) // Hold the welcome screen for 3 seconds
            onAnimationFinished() // Trigger transition to main screen
        }

        // Layout container for the welcome content
        Column(
            modifier = Modifier
                .fillMaxSize() // Take up the full screen
                .alpha(alpha) // Apply animated fade-in
                .scale(scale) // Apply animated zoom-in
                .padding(horizontal = 24.dp), // Horizontal padding
            verticalArrangement = Arrangement.Center, // Center content vertically
            horizontalAlignment = Alignment.CenterHorizontally // Center content horizontally
        ) {
            // Main app title
            Text(
                text = "BLE Visualizer Demo",
                fontSize = 52.sp, // Large title text
                color = Color(0xFF3F51B5), // Indigo color
                lineHeight = 60.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp)) // Vertical space between title and subtitle

            // App subtitle or slogan
            Text(
                text = "Scan. Connect. Visualize.",
                fontSize = 20.sp, // Smaller subtitle
                color = Color(0xFF607D8B), // Soft gray-blue color
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )
        }
    }



    // Composable UI function that displays a labeled toggle switch for BLE scanning
    @Composable
    fun BleScanToggle(
        isScanning: Boolean,                // Current scan state (true = scanning, false = not scanning)
        onToggle: (Boolean) -> Unit         // Callback when the switch is toggled
    ) {
        // Horizontal row layout containing the label and the switch
        Row(
            modifier = Modifier
                .fillMaxWidth()            // Span the full width of the screen
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 36.dp,
                    bottom = 12.dp // ≈ 1/4 inch
                ),                        // Add spacing around the row
            verticalAlignment = Alignment.CenterVertically,       // Align items vertically centered
            horizontalArrangement = Arrangement.SpaceBetween       // Place label and switch on opposite ends
        ) {
            // Text label that reflects the current scanning status
            Text(
                text = if (isScanning) "Scanning Enabled" else "Scan Disabled", // Dynamic label based on state
                fontSize = 20.sp,                                               // Medium-large font
                color = if (isScanning) Color(0xFF3F51B5) else Color.Gray       // Color reflects active/inactive state
            )

            // Switch that toggles BLE scanning
            Switch(
                checked = isScanning,       // Current state of the switch
                onCheckedChange = onToggle  // Notify parent when toggled
            )
        }
    }



    // Composable UI function that renders a filter/search bar
    @Composable
    fun FilterBar(
        query: String,                        // Current text entered in the search bar
        onQueryChanged: (String) -> Unit     // Callback to update the search text as the user types
    ) {
        // Outlined text input field with a label and full-width styling
        OutlinedTextField(
            value = query,                   // Bind the current input value to the field
            onValueChange = onQueryChanged, // Update search text on each keystroke
            label = { Text("Filter devices...") }, // Input label shown when field is focused or empty
            modifier = Modifier
                .fillMaxWidth()             // Input spans the entire screen width
                .padding(horizontal = 16.dp) // Margin from screen edges
        )
    }



    // Composable function that shows the BLE scanner UI
    @Composable
    fun MainScannerScreen(onBack: () -> Unit) {
        // Main layout column with padding and full screen height
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 12.dp,
                    bottom = 48.dp // ≈ 1/4 inch
                )
        ) {
            Button(onClick = onBack) {
                Text("Back to Menu")
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Scan toggle button (start/stop scanning)
            BleScanToggle(isScanning = isScanning) { toggled ->
                if (toggled) {
                    scannedDevices.clear()
                    ensureScanPermission {
                        isScanning = true
                        startBleScan()
                    }
                    if (!hasScanPermission()) {
                        isScanning = false
                    }
                } else {
                    isScanning = false
                    stopBleScan()
                    scannedDevices.clear()
                }
            }

            // Search/filter bar to refine displayed devices
            FilterBar(
                query = searchQuery,
                onQueryChanged = { searchQuery = it }
            )

            // List of scanned devices that match the search query
            LazyColumn {
                val filteredDevices = scannedDevices.filter {
                    it.contains(searchQuery, ignoreCase = true)
                }

                items(filteredDevices) { deviceInfo ->
                    Text(
                        text = deviceInfo, // Show device info (e.g., name [MAC])
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .padding(16.dp)
                            .clickable {
                                // Extract MAC address and device name
                                val address = deviceInfo.substringAfter("[").substringBefore("]")
                                val name = deviceInfo.substringBefore(" [")

                                // Connect and save connected device name
                                if (hasConnectPermission()) {
                                    try {
                                        connectToDevice(address)
                                        connectedDeviceName = name
                                    } catch (securityException: SecurityException) {
                                        Log.e("BLE", "Connect rejected by system", securityException)
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Unable to connect without Bluetooth permission",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Bluetooth permission not granted",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                    )
                }
            }
        }
    }



    @Composable
    fun MainMenuScreen(
        onConnectToDevice: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("BLE Sync Suite", fontSize = 36.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Select what you want to do",
                fontSize = 18.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(onClick = onConnectToDevice, modifier = Modifier.fillMaxWidth()) {
                Text("Connect to Device")
            }
        }
    }



    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun DataDisplayScreen(deviceName: String, onBack: () -> Unit) {
        // For fade-in animation
        var visible by remember { mutableStateOf(false) }

        // Track which characteristic is expanded
        var expandedCharUuid by remember { mutableStateOf<UUID?>(null) }

        val context = LocalContext.current
        val listState = rememberLazyListState()

        val notificationStates = remember { mutableStateMapOf<UUID, Boolean>() }


        // Fade-in animation
        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(durationMillis = 1000),
            label = "fadeIn"
        )

        LaunchedEffect(Unit) {
            visible = true
            listState.scrollToItem(0) // Optional: scroll to top when screen launches
        }

        // Screen layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha)
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 36.dp,
                    bottom = 48.dp
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("Back to Menu")
                }
                Text(
                    "Connected to $deviceName",
                    fontSize = 24.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentWidth(Alignment.End)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // ESP32 packet display section
            val latestPacket by latestEspPacket.collectAsState()
            if (latestPacket != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Text("ESP32 Latest Packet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("seq: ${latestPacket!!.seq}", fontSize = 14.sp, color = Color.Black)
                    Text("tUs: ${latestPacket!!.tUs}", fontSize = 14.sp, color = Color.Black)
                    Text("receivedAtNs: ${latestPacket!!.receivedAtNs}", fontSize = 14.sp, color = Color.Black)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Single LazyColumn for all characteristics
            LazyColumn(state = listState) {
                items(characteristicInfoList) { info ->
                    val bringIntoViewRequester = remember { BringIntoViewRequester() }
                    val coroutineScope = rememberCoroutineScope()
                    val isNotificationActive = notificationStates[info.charUuid] == true

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(bringIntoViewRequester)
                            .clickable {
                                // Clicking on ESP32 characteristic opens graph
                                if (info.charUuid == ESP32_CHAR_UUID) {
                                    showGraphScreen = true
                                } else {
                                    expandedCharUuid = if (expandedCharUuid == info.charUuid) null else info.charUuid

                                    coroutineScope.launch {
                                        bringIntoViewRequester.bringIntoView()
                                    }
                                }
                            }
                            .padding(16.dp)
                    ) {
                        Text("Characteristic: ${info.charName}")
                        Text("Properties: ${info.properties}")
                        Text("UUID: ${info.charUuid}", fontSize = 12.sp, color = Color.Gray)

                        if (expandedCharUuid == info.charUuid) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Button(onClick = {
                                    if (hasConnectPermission(context)) {
                                        try {
                                            readCharacteristicOnce(info.charUuid, info.serviceUuid)
                                        } catch (securityException: SecurityException) {
                                            Log.e("BLE", "Read rejected by system", securityException)
                                            Toast.makeText(
                                                context,
                                                "Unable to read without Bluetooth permission",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Bluetooth permission not granted",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }) {
                                    Text("Read")
                                }
                                Button(
                                    onClick = {
                                        if (!hasConnectPermission(context)) {
                                            Toast.makeText(
                                                context,
                                                "Bluetooth permission not granted",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@Button
                                        }

                                        try {
                                            val updated = if (isNotificationActive) {
                                                setNotificationsForCharacteristic(info, false).also { success ->
                                                    if (success) {
                                                        notificationStates[info.charUuid] = false
                                                    }
                                                }
                                            } else {
                                                setNotificationsForCharacteristic(info, true).also { success ->
                                                    if (success) {
                                                        notificationStates[info.charUuid] = true
                                                        graphCharUuid = info.charUuid
                                                        graphServiceUuid = info.serviceUuid
                                                        showGraphScreen = true
                                                    }
                                                }
                                            }

                                            if (!updated) {
                                                Toast.makeText(
                                                    context,
                                                    "Unable to update notifications for this characteristic",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        } catch (securityException: SecurityException) {
                                            Log.e("BLE", "Notification toggle rejected", securityException)
                                            Toast.makeText(
                                                context,
                                                "OS rejected notification request",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                ) {
                                    Text(if (isNotificationActive) "Stop Notify" else "Notify")
                                }

                                Text(
                                    "Last Value: ${readValues[info.charUuid] ?: "—"}",
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }




    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    @SuppressLint("MissingPermission")
    private fun readCharacteristicOnce(charUuid: UUID, serviceUuid: UUID) {
        bluetoothGatt?.let { gatt ->
            val service = gatt.getService(serviceUuid)
            val characteristic = service?.getCharacteristic(charUuid)

            if (characteristic != null) {
                val success = gatt.readCharacteristic(characteristic)
                Log.i("BLE", if (success) "📖 Read initiated" else "❌ Read failed to initiate")
            } else {
                Log.e("BLE", "❌ Characteristic not found")
            }
        }
    }



    @Composable
    fun GraphScreen(onBack: () -> Unit) {
        val packets: List<EspPacket> = esp32PacketHistory.toList()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("Back")
                }
                Text("📈 Seq vs Timestamps", fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (packets.isEmpty()) {
                Text("No data yet. Waiting for ESP32 packets...", color = Color.Gray)
            } else {
                // Graph: seq (x-axis) vs normalized timestamps (y-axis)
                // Plot both ESP32 tUs and Android receivedAtNs on same graph
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (packets.size < 2) {
                            // Need at least 2 points to draw a line
                            return@Canvas
                        }

                        val minSeq = packets.minOf { it.seq }.toFloat()
                        val maxSeq = packets.maxOf { it.seq }.toFloat()
                        
                        // Normalize ESP32 timestamps (microseconds) to start from 0
                        val firstTUs = packets.first().tUs.toFloat()
                        val tUsValues = packets.map { (it.tUs - firstTUs).toFloat() }
                        val maxTUsNormalized = tUsValues.maxOrNull() ?: 1f
                        val tUsRange = maxTUsNormalized.coerceAtLeast(1f)
                        
                        // Normalize Android reception timestamps (nanoseconds) to start from 0
                        val firstReceivedAtNs = packets.first().receivedAtNs.toFloat()
                        val receivedAtNsValues = packets.map { (it.receivedAtNs - firstReceivedAtNs) / 1000f } // Convert ns to μs
                        val maxReceivedAtNsNormalized = receivedAtNsValues.maxOrNull() ?: 1f
                        val receivedAtNsRange = maxReceivedAtNsNormalized.coerceAtLeast(1f)

                        // Use the larger range for y-axis scaling
                        val maxYRange = maxOf(tUsRange, receivedAtNsRange)

                        val padding = 50f
                        val graphWidth = size.width - padding * 2
                        val graphHeight = size.height - padding * 2
                        val seqRange = (maxSeq - minSeq).coerceAtLeast(1f)

                        // Draw axes
                        drawLine(
                            start = Offset(padding, size.height - padding),
                            end = Offset(size.width - padding, size.height - padding),
                            color = Color.Gray,
                            strokeWidth = 2f
                        )
                        drawLine(
                            start = Offset(padding, padding),
                            end = Offset(padding, size.height - padding),
                            color = Color.Gray,
                            strokeWidth = 2f
                        )

                        // Draw ESP32 timestamp line (blue)
                        val esp32Path = Path()
                        packets.forEachIndexed { index, packet ->
                            val x = padding + ((packet.seq - minSeq) / seqRange) * graphWidth
                            val normalizedTUs = ((packet.tUs - firstTUs).toFloat() / maxYRange)
                            val y = size.height - padding - (normalizedTUs * graphHeight)

                            if (index == 0) {
                                esp32Path.moveTo(x, y)
                            } else {
                                esp32Path.lineTo(x, y)
                            }
                        }

                        drawPath(
                            path = esp32Path,
                            color = Color(0xFF3F51B5), // Blue for ESP32
                            style = Stroke(width = 3f)
                        )

                        // Draw Android reception timestamp line (green)
                        val androidPath = Path()
                        packets.forEachIndexed { index, packet ->
                            val x = padding + ((packet.seq - minSeq) / seqRange) * graphWidth
                            val normalizedReceivedAt = (((packet.receivedAtNs - firstReceivedAtNs) / 1000f) / maxYRange)
                            val y = size.height - padding - (normalizedReceivedAt * graphHeight)

                            if (index == 0) {
                                androidPath.moveTo(x, y)
                            } else {
                                androidPath.lineTo(x, y)
                            }
                        }

                        drawPath(
                            path = androidPath,
                            color = Color(0xFF4CAF50), // Green for Android
                            style = Stroke(width = 3f)
                        )
                    }
                    
                    // Legend
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(0xFF3F51B5))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ESP32 tUs", fontSize = 10.sp, color = Color.Black)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(0xFF4CAF50))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Android Rx", fontSize = 10.sp, color = Color.Black)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Calculate drift and jitter metrics
                if (packets.size >= 2) {
                    val first = packets.first()
                    
                    // Filter out packets with sequence number gaps (packet loss)
                    // Only include consecutive packets for baseline calculation
                    val validPackets = mutableListOf<EspPacket>()
                    validPackets.add(packets.first()) // Always include first packet
                    
                    for (i in 1 until packets.size) {
                        val prevSeq = packets[i - 1].seq
                        val currSeq = packets[i].seq
                        // Check if sequence is consecutive (allowing for uint32 wraparound at 0xFFFF_FFFFL)
                        if (currSeq == prevSeq + 1L || (prevSeq == 0xFFFF_FFFFL && currSeq == 0L)) {
                            validPackets.add(packets[i])
                        }
                    }
                    
                    // Calculate offset-based jitter strictly from valid (consecutive) packets only
                    // For each valid packet, compute offsetMs = ((receivedAtNs - first.receivedAtNs) - ((tUs - first.tUs) * 1000L)) / 1_000_000.0
                    val validOffsetValues = validPackets.map { packet ->
                        val androidRxElapsed = packet.receivedAtNs - first.receivedAtNs // ns
                        val esp32SendElapsed = (packet.tUs - first.tUs) * 1000L // Convert μs to ns
                        (androidRxElapsed - esp32SendElapsed) / 1_000_000.0 // Convert to ms
                    }
                    
                    // Estimate baseline offset as median of valid offsets (data-derived, not fixed)
                    val baselineOffsetMs = if (validOffsetValues.isNotEmpty()) {
                        val sorted = validOffsetValues.sorted()
                        val mid = sorted.size / 2
                        if (sorted.size % 2 == 0) {
                            (sorted[mid - 1] + sorted[mid]) / 2.0
                        } else {
                            sorted[mid]
                        }
                    } else {
                        0.0
                    }
                    
                    // Calculate jitterMs = abs(offsetMs - baselineOffsetMs) only for valid packets
                    val jitterValues = validOffsetValues.map { offsetMs ->
                        kotlin.math.abs(offsetMs - baselineOffsetMs)
                    }
                    
                    // Running average jitter (calculated only from valid jitter values)
                    val runningJitterAvg = if (jitterValues.isNotEmpty()) {
                        jitterValues.average()
                    } else {
                        0.0
                    }
                    
                    // Calculate delay metrics (offset)
                    // Running average offset (from valid packets only)
                    val runningOffsetAvg = if (validOffsetValues.isNotEmpty()) {
                        validOffsetValues.average()
                    } else {
                        0.0
                    }
                    
                    // Latest offset: use the last valid consecutive packet (validPackets.last)
                    val latestOffset = if (validOffsetValues.isNotEmpty() && validPackets.isNotEmpty()) {
                        validOffsetValues.last()
                    } else {
                        0.0
                    }
                    
                    // Latest jitter: compute from latest valid packet using abs(latestOffset - baselineOffsetMs)
                    val latestJitter = if (validOffsetValues.isNotEmpty() && validPackets.isNotEmpty()) {
                        kotlin.math.abs(latestOffset - baselineOffsetMs)
                    } else {
                        0.0
                    }
                    
                    // Calculate dropped packet count
                    val droppedPacketCount = packets.size - validPackets.size
                    
                    // Calculate transmission rate from ESP32 intervals
                    val transmissionRate = if (validPackets.size >= 2) {
                        val intervals = mutableListOf<Long>()
                        for (i in 1 until validPackets.size) {
                            val intervalUs = validPackets[i].tUs - validPackets[i - 1].tUs
                            intervals.add(intervalUs)
                        }
                        if (intervals.isNotEmpty()) {
                            val avgIntervalUs = intervals.average()
                            avgIntervalUs / 1000.0 // Convert to ms
                        } else {
                            0.0
                        }
                    } else {
                        0.0
                    }
                    
                    // Make metrics section scrollable
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Transmission rate
                        Text("Transmission Rate:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("  Average Interval: ${String.format("%.2f", transmissionRate)} ms", fontSize = 12.sp, color = Color.White)
                        if (transmissionRate > 0) {
                            val packetsPerSecond = 1000.0 / transmissionRate
                            Text("  Rate: ${String.format("%.2f", packetsPerSecond)} packets/sec", fontSize = 12.sp, color = Color.White)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Delay metrics (offset)
                        Text("Delay Metrics:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("  Running Average: ${String.format("%.3f", runningOffsetAvg)} ms", fontSize = 12.sp, color = Color.White)
                        Text("  Latest: ${String.format("%.3f", latestOffset)} ms", fontSize = 12.sp, color = Color.White)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Jitter metrics
                        Text("Jitter Metrics:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("  Running Average: ${String.format("%.3f", runningJitterAvg)} ms", fontSize = 12.sp, color = Color.White)
                        Text("  Latest: ${String.format("%.3f", latestJitter)} ms", fontSize = 12.sp, color = Color.White)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Packet statistics
                        Text("Packet Statistics:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("  Total packets: ${packets.size}", fontSize = 12.sp, color = Color.White)
                        Text("  Valid packets: ${validPackets.size}", fontSize = 12.sp, color = Color.White)
                        Text("  Dropped packets: $droppedPacketCount", fontSize = 12.sp, color = Color.White)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Time spans
                        val latest = packets.last()
                        val tUsDelta = (latest.tUs - first.tUs) / 1000.0
                        val rxDelta = (latest.receivedAtNs - first.receivedAtNs) / 1_000_000.0
                        
                        Text("Time Spans:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("  ESP32 span: ${String.format("%.1f", tUsDelta)} ms", fontSize = 12.sp, color = Color(0xFF3F51B5))
                        Text("  Android Rx span: ${String.format("%.1f", rxDelta)} ms", fontSize = 12.sp, color = Color(0xFF4CAF50))
                    }
                } else if (packets.size == 1) {
                    Text("Waiting for more packets to calculate drift and jitter...", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }

}
