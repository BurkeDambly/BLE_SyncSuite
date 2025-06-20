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


// --- Main App Logic Starts Here ---

class MainActivity : ComponentActivity() {

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

                startBleScan()
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
                    // Display simple text in the middle of the screen
                    Text(text = "Bluetooth Permission App Started")
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
            startBleScan()
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

    // This is the callback that Android calls every time a BLE device is found while scanning
    private val bleScanCallback = object : ScanCallback(){
        // Called when a device is found

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            // Check if we have permission to read Bluetooth device details
            val hasConnectPermission = ContextCompat.checkSelfPermission(
                this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            if (hasConnectPermission) {
                val deviceName = result.device.name ?: "Unnamed"
                val deviceAddress = result.device.address

                Log.d("BLE", "Found device: $deviceName [$deviceAddress]")
            } else {
                Log.w("BLE", "BLUETOOTH_CONNECT permission not granted — skipping device info")
            }

        }

        // Called if scanning fails for some reason
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)

            // Log an error message with the error code
            Log.e("BLE", "Scan failed with error code: $errorCode")
        }
    }

}
