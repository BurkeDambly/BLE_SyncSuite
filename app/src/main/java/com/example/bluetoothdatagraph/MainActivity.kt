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
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Switch
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign

// --- Main App Logic Starts Here ---

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
            // Apply the Material3 app theme (colors, typography, spacing, etc.)
            BluetoothDataGraphTheme {
                // Surface is like a full-screen background with default theme color
                Surface(color = MaterialTheme.colorScheme.background) {
                    Crossfade(targetState = showWelcomeScreen,
                        animationSpec = tween(durationMillis = 1500)
                    ) { showingWelcome ->
                    // If the welcome screen flag is true, show welcome screen
                    if(showingWelcome){
                        WelcomeScreen {
                            // After welcome animation finishes, switch to scanner list
                            showWelcomeScreen = false
                        }
                    } else {

                        // Column is a vertical layout container that expands to fill the screen
                        Column(
                            modifier = Modifier
                                .fillMaxSize()        // Use the full available width and height
                                .padding(16.dp)       // Add uniform padding around the list
                        ) {

                            BleScanToggle(
                                isScanning = isScanning,
                                onToggle = { toggled ->
                                    isScanning = toggled
                                    if (toggled) {
                                        scannedDevices.clear()
                                        startBleScan()
                                    } else {
                                        stopBleScan()
                                        scannedDevices.clear()
                                    }
                                }
                            )

                            // Lazy Column is like a vertical scrolling list that
                            // only draws the items that are currently on screen
                            LazyColumn {
                                //items() loops through each item in scannedDevices
                                // For each device, it creates a text element to show the name + address
                                items(scannedDevices) { deviceInfo ->
                                    Text(
                                        text = deviceInfo,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .clickable {
                                                // Cancel the current toast if it’s still showing
                                                currentToast?.cancel()

                                                // Show new toast with SHORT duration
                                                currentToast = Toast.makeText(
                                                    this@MainActivity,
                                                    "Clicked: $deviceInfo",
                                                    Toast.LENGTH_SHORT
                                                )
                                                currentToast?.show()
                                            },
                                        )
                                    }
                                }
                            }
                        }
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
        // Get the BLE scanner from the adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        // Check for runtime permission before starting scan
        val hasScanPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED

        if (hasScanPermission) {
            bluetoothLeScanner?.startScan(bleScanCallback)
            Log.d("BLE", "Started BLE Scan")
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
            Log.d("BLE", "Started BLE Scan")
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

    // This composable function displays a welcome screen with a fade+zoom animation.
    // It runs once at startup, fades in/out a title, then signals the app to switch screens.
    @Composable
    private fun WelcomeScreen(onAnimationFinished: () -> Unit) {
        // Controls the fade-in and scale-in animation
        val visible = remember { mutableStateOf(false) }

        val alpha by animateFloatAsState(
            targetValue = if (visible.value) 1f else 0f,
            animationSpec = tween(durationMillis = 1000)
        )

        val scale by animateFloatAsState(
            targetValue = if (visible.value) 1f else 0.95f,
            animationSpec = tween(durationMillis = 1000)
        )

        // Launch animation, then wait and switch screens
        LaunchedEffect(Unit) {
            visible.value = true
            delay(3000)
            onAnimationFinished()
        }

        // Main centered layout with fade/scale applied
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha)
                .scale(scale)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App title
            Text(
                text = "BLE Visualizer Demo",
                fontSize = 52.sp,
                color = Color(0xFF3F51B5),
                lineHeight = 60.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle
            Text(
                text = "Scan. Connect. Visualize.",
                fontSize = 20.sp,
                color = Color(0xFF607D8B),
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )
        }
    }

    @Composable
    fun BleScanToggle(
        isScanning: Boolean,
        onToggle: (Boolean) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Label
            Text(
                text = if (isScanning) "Scanning Enabled" else "Scan Disabled",
                fontSize = 20.sp,
                color = if (isScanning) Color(0xFF3F51B5) else Color.Gray
            )

            // Toggle switch
            Switch(
                checked = isScanning,
                onCheckedChange = onToggle
            )
        }
    }

}
