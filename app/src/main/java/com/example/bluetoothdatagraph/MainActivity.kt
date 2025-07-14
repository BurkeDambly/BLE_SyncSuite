// This defines the package name — it matches the folder structure and helps Android locate your code
package com.example.bluetoothdatagraph

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

import com.example.bluetoothdatagraph.ui.theme.BluetoothDataGraphTheme // Auto-generated theme from the new project wizard

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

// --- Jetpack Compose: State-Driven Lists ---
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

// --- GUI Elements ---
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
import androidx.compose.animation.Crossfade // Make sure this is imported
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import android.bluetooth.le.ScanSettings
import android.bluetooth.BluetoothDevice

// --- Main App Logic Starts Here ---

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

// Main logic
class MainActivity : ComponentActivity() {

    // This is a relative list that updates the UI when modified.
    // We can store the device names and addr as string in a list
    private val scannedDevices: SnapshotStateList<String> = mutableStateListOf()

    // Keeps track of whether the welcome screen is currently showing.
    // Once the animation ends, we set this to false and show the scanner list.
    private var showWelcomeScreen by mutableStateOf(true)

    // Holds a reference to the currently visible Toast message.
    // This allows us to cancel any existing Toast before showing a new one,
    // which prevents overlapping or delayed popups when users click quickly.
    private var currentToast: Toast? = null

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

    // Holds the list of all discovered characteristics for UI display
    private val characteristicInfoList = mutableStateListOf<CharacteristicInfo>()

    // Track the characteristic to graph
    private var graphCharUuid: UUID? = null
    private var graphServiceUuid: UUID? = null

    // This is a list of all the permissions we want to request from the user at runtime.
    // Android doesn't grant these automatically; the user must approve them.
    private val permissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,          // Required to detect nearby Bluetooth LE devices (Android 12+)
        Manifest.permission.BLUETOOTH_CONNECT,       // Required to initiate BLE connections (Android 12+)
        Manifest.permission.ACCESS_FINE_LOCATION,    // Still needed for scanning on Android < 12
        Manifest.permission.ACCESS_COARSE_LOCATION   // Provides compatibility with older phones
    )

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
            } else {
                // All permissions granted — app is ready to scan/connect
                Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
            }
        }

    // This function is automatically called by Android when the app starts (like "main()" in other languages)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set up the UI using Jetpack Compose instead of XML layout
        setContent {
            BluetoothDataGraphTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    when {
                        showWelcomeScreen -> WelcomeScreen { showWelcomeScreen = false }
                        showGraphScreen -> GraphScreen()
                        showDataScreen -> DataDisplayScreen(connectedDeviceName)
                        else -> MainScannerScreen()
                    }
                }
            }
        }

        // Get the system's Bluetooth service from Android. This returns a BluetoothManager object.
        // We're using "getSystemService" to get a system-level object. It's like a global accessor.
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        // From the BluetoothManager, get the BluetoothAdapter (this represents the actual Bluetooth hardware interface).
        bluetoothAdapter = bluetoothManager.adapter

        // Defensive check: if the device has no Bluetooth adapter, show an error and return early.
        // This prevents us from trying to scan on devices that don't support BLE at all.
        if (bluetoothAdapter == null) {
            Log.e("BLE", "Bluetooth not supported on this device") // Print an error to Logcat
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show() // Show a user-visible message
            return // Exit the onCreate function so we don’t continue trying to scan
        }

        // After UI is drawn, check if we have permissions, and request them if we don’t
        requestPermissionsModernWay()
    }



    // This function filters out already-granted permissions and only asks for what’s missing
    private fun requestPermissionsModernWay() {
        // Check each permission and keep only the ones that haven't been granted yet
        val neededPermissions = permissions.filter {
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



    // THis function starts the bluetooth low energy scan
    // it uses the adapter to get the ble scanner, then starts scanning and logs the action
    private fun startBleScan() {
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        val hasScanPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED

        if (hasScanPermission) {
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            bluetoothLeScanner?.startScan(null, scanSettings, bleScanCallback)
            Log.d("BLE", "Started BLE Scan with LOW_LATENCY mode")
        } else {
            Log.e("BLE", "BLUETOOTH_SCAN permission not granted — scan aborted")
            Toast.makeText(this, "Scan permission not granted", Toast.LENGTH_SHORT).show()
        }
    }




    private fun stopBleScan() {
        // Check for runtime permission before starting scan
        val hasScanPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED

        if (hasScanPermission) {
            bluetoothLeScanner?.stopScan(bleScanCallback)
            Log.d("BLE", "Stopped BLE Scan")
        } else {
            Log.e("BLE", "BLUETOOTH_SCAN permission not granted — scan aborted")
            Toast.makeText(this, "Scan permission not granted", Toast.LENGTH_SHORT).show()
        }
    }



    // This is the callback that Android calls every time a BLE device is found while scanning
    private val bleScanCallback = object : ScanCallback(){
        // Called when a device is found

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            // Add this guard clause to prevent race condition
            if (!isScanning) return

            // Check if we have permission to read Bluetooth device details
            val hasConnectPermission = ContextCompat.checkSelfPermission(
                this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            if (hasConnectPermission) {
                val deviceName = result.device.name ?: "Unnamed"
                val deviceAddress = result.device.address
                val displayString = "$deviceName [$deviceAddress]"

                Log.d("BLE", "Found device: $deviceName [$deviceAddress]")

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

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.i("BLE", "onConnectionStateChange: status=$status, newState=$newState")

            // If the connection failed, log and inform the user
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
                        showDataScreen = true // Trigger screen change to data display
                    }

                    // Start discovering services on the GATT server
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
                    }
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
            val uuid = characteristic.uuid

            val rawData = characteristic.value?.toString(Charsets.UTF_8)?.trim()
            Log.d("BLE_RAW_PACKET", rawData ?: "NULL")

            // Only graph data if this is the subscribed graph characteristic
            if (uuid == graphCharUuid) {
                val dataValue = characteristic.value?.toString(Charsets.UTF_8)?.trim()
                if (dataValue != null) {
                    Log.i("BLE", "📈 Data for Graph: $dataValue")

                    // Update the live data list for graphing
                    runOnUiThread {
                        receivedDataList.add(dataValue)
                    }
                }
            } else {
                Log.i("BLE", "📩 Notification from other characteristic: $uuid")
            }
        }



        // Callback invoked after writing a descriptor (e.g., the CCCD)
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            // Log what was written and the status code returned by the stack
            Log.i("BLE", "📝 onDescriptorWrite: UUID=${descriptor.uuid}, status=$status")

            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Success: notifications/indications are now enabled or the descriptor update succeeded
                Log.i("BLE", "✅ CCCD descriptor written successfully")
            } else {
                // Failure: handle retry logic or inform the user if necessary
                Log.e("BLE", "❌ Descriptor write failed")
            }
        }



        // Called when BLE services have been discovered on the connected device
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)

            // Check if service discovery was successful
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w("BLE", "❌ Service discovery failed: $status")
                return
            }

            // Clear previous entries before adding new ones
            characteristicInfoList.clear()

            // Loop through all discovered services
            for (service in gatt.services) {
                Log.i("BLE", "🧩 Service UUID: ${service.uuid}")

                // For each service, loop through its characteristics
                for (characteristic in service.characteristics) {
                    val props = characteristic.properties
                    val propsList = mutableListOf<String>().apply {
                        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
                        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
                        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
                        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
                    }.joinToString()

                    val serviceName = standardServiceNames[service.uuid] ?: "Unknown Service"
                    val charName = standardCharacteristicNames[characteristic.uuid] ?: "Unknown Characteristic"

                    val info = CharacteristicInfo(service.uuid, serviceName, characteristic.uuid, charName, propsList)

                    // ✅ Only add if it's not already in the list
                    if (!characteristicInfoList.any {
                            it.charUuid == info.charUuid && it.serviceUuid == info.serviceUuid
                        }) {
                        characteristicInfoList.add(info)
                    }
                }
            }

        }


        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = characteristic.value?.joinToString(" ") { it.toUByte().toString() } ?: "null"

                // 👇 this must update the same map the Composable sees
                readValues[characteristic.uuid] = value

                Log.i("BLE", "✅ Read from ${characteristic.uuid}: $value")
            } else {
                Log.w("BLE", "❌ Failed to read characteristic ${characteristic.uuid}, status: $status")
            }
        }

    }



    // Function to initiate a BLE connection to a device by its MAC address
    // Function to initiate a BLE connection to a device by its MAC address
    private fun connectToDevice(address: String) {
        // Permission check
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
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
        android.os.Handler(mainLooper).postDelayed({
            bluetoothGatt =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(
                        this,
                        /* autoConnect = */ false,
                        gattCallback,
                        BluetoothDevice.TRANSPORT_LE   // <-- key line
                    )
                } else {
                    device.connectGatt(this, false, gattCallback)
                }

            Log.d("BLE", "Connecting to device: $address (LE transport)")
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
    fun MainScannerScreen() {
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
            // Scan toggle button (start/stop scanning)
            BleScanToggle(isScanning = isScanning) { toggled ->
                isScanning = toggled
                if (toggled) {
                    scannedDevices.clear() // Clear previous scan results
                    startBleScan()         // Begin scanning
                } else {
                    stopBleScan()          // Stop scanning
                    scannedDevices.clear() // Clear list after stopping
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
                                connectToDevice(address)
                                connectedDeviceName = name
                            }
                    )
                }
            }
        }
    }



    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun DataDisplayScreen(deviceName: String) {
        // For fade-in animation
        var visible by remember { mutableStateOf(false) }

        // Track which characteristic is expanded
        var expandedCharUuid by remember { mutableStateOf<UUID?>(null) }

        val context = LocalContext.current
        val listState = rememberLazyListState()

        val pollingJobs = remember { mutableStateMapOf<UUID, Job>() }
        val mainScope = rememberCoroutineScope()


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
            Text("Connected to $deviceName", fontSize = 28.sp)
            Spacer(modifier = Modifier.height(16.dp))

            // Single LazyColumn for all characteristics
            LazyColumn(state = listState) {
                items(characteristicInfoList) { info ->
                    val isReadable = "READ" in info.properties
                    val bringIntoViewRequester = remember { BringIntoViewRequester() }
                    val coroutineScope = rememberCoroutineScope()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(bringIntoViewRequester)
                            .clickable {
                                expandedCharUuid = if (expandedCharUuid == info.charUuid) null else info.charUuid

                                coroutineScope.launch {
                                    bringIntoViewRequester.bringIntoView()
                                }

                                // Enable notifications immediately on click if allowed
                                val gatt = bluetoothGatt
                                val service = gatt?.getService(info.serviceUuid)
                                val characteristic = service?.getCharacteristic(info.charUuid)

                                if (
                                    characteristic?.properties?.and(BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    if (gatt != null) {
                                        gatt.setCharacteristicNotification(characteristic, true)
                                    }

                                    val descriptor = characteristic?.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                                    if (descriptor != null) {
                                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    }
                                    if (gatt != null) {
                                        gatt.writeDescriptor(descriptor)
                                    }

                                    // Set as active graphing characteristic
                                    graphCharUuid = info.charUuid
                                    graphServiceUuid = info.serviceUuid

                                    // Optional: automatically switch to graph screen
                                    showGraphScreen = true
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
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                                        == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        readCharacteristicOnce(info.charUuid, info.serviceUuid)
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
                                        // Stop if already polling
                                        if (pollingJobs.containsKey(info.charUuid)) {
                                            pollingJobs[info.charUuid]?.cancel()
                                            pollingJobs.remove(info.charUuid)
                                        } else {
                                            // Start polling
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                                                == PackageManager.PERMISSION_GRANTED
                                            ) {
                                                val gatt = bluetoothGatt
                                                val service = gatt?.getService(info.serviceUuid)
                                                val characteristic = service?.getCharacteristic(info.charUuid)

                                                if (characteristic?.properties?.and(BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                                    if (gatt != null) {
                                                        gatt.setCharacteristicNotification(characteristic, true)
                                                    }
                                                    val descriptor = characteristic?.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                                                    if (descriptor != null) {
                                                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                                    }
                                                    if (gatt != null) {
                                                        gatt.writeDescriptor(descriptor)
                                                    }

                                                    // 👇 NEW: Set as the characteristic to graph
                                                    graphCharUuid = info.charUuid
                                                    graphServiceUuid = info.serviceUuid

                                                    // 👇 OPTIONAL: Go to the graphing screen
                                                    showGraphScreen = true
                                                }
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Bluetooth permission not granted",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                ) {
                                    Text("Notify")
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




    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
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
    fun GraphScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text("📈 Graphing characteristic data...", fontSize = 24.sp)

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                items(receivedDataList) { entry ->
                    Text("Data: $entry")
                }
            }
        }
    }

}
