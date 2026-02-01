package com.example.ble_sync_suite_app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

class BleManager(
    private val activity: ComponentActivity,
    private val hasScanPermission: () -> Boolean,
    private val hasConnectPermission: () -> Boolean,
    private val onDeviceFound: (String) -> Unit,
    private val onConnected: (String) -> Unit,
    private val onDisconnected: () -> Unit,
    private val onCharacteristicsDiscovered: (List<CharacteristicInfo>) -> Unit,
    private val onPacketReceived: (EspPacket) -> Unit
) {
    private val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    var bluetoothGatt: BluetoothGatt? = null
        private set

    // -----------------------------
    // CheepSync drift management
    // Model: Tr ≈ α + β * tb
    // tb = beacon timestamp in PHONE units? (we keep tb in beacon-ns, Tr in phone-ns)
    // α = offset (ns), β = skew (dimensionless scale factor)
    // -----------------------------
    private data class CheepSyncSample(
        val tbNs: Double, // beacon time converted to ns (beacon clock domain)
        val TrNs: Double  // phone monotonic receive time in ns (phone clock domain)
    )

    private val cheepSyncWindow = ArrayDeque<CheepSyncSample>()
    private val CHEEP_SYNC_WINDOW_SIZE = 50

    // Regression outputs
    private val _cheepSyncAlpha = MutableStateFlow(0.0) // ns
    private val _cheepSyncBeta = MutableStateFlow(1.0)  // scale
    val cheepSyncAlpha: StateFlow<Double> = _cheepSyncAlpha.asStateFlow()
    val cheepSyncBeta: StateFlow<Double> = _cheepSyncBeta.asStateFlow()

    // Optional: simple health metrics you can log/plot
    private val _cheepSyncRmsResidualMs = MutableStateFlow(0.0)
    val cheepSyncRmsResidualMs: StateFlow<Double> = _cheepSyncRmsResidualMs.asStateFlow()

    private fun resetCheepSync() {
        cheepSyncWindow.clear()
        _cheepSyncAlpha.value = 0.0
        _cheepSyncBeta.value = 1.0
        _cheepSyncRmsResidualMs.value = 0.0
    }

    /**
     * Add a (tb, Tr) sample and recompute α, β via least-squares over a sliding window.
     *
     * This matches the paper’s “continuous skew adjustments over a measurement window”
     * using linear regression for frequency (β) and phase/offset (α). :contentReference[oaicite:2]{index=2}
     */
    private fun updateCheepSync(packet: EspPacket) {
        // Phone receive time (monotonic, ns)
        val TrNs = packet.receivedAtNs.toDouble()

        // Beacon time: packet.tUs is beacon microseconds since beacon boot
        val tbNs = packet.tUs * 1000.0 // μs -> ns

        // Append to sliding window
        cheepSyncWindow.addLast(CheepSyncSample(tbNs = tbNs, TrNs = TrNs))
        while (cheepSyncWindow.size > CHEEP_SYNC_WINDOW_SIZE) {
            cheepSyncWindow.removeFirst()
        }

        // Need at least 2 points for regression
        if (cheepSyncWindow.size < 2) return

        // Compute means
        val n = cheepSyncWindow.size.toDouble()
        var tbMean = 0.0
        var TrMean = 0.0
        for (s in cheepSyncWindow) {
            tbMean += s.tbNs
            TrMean += s.TrNs
        }
        tbMean /= n
        TrMean /= n

        // Least squares:
        // β = cov(tb,Tr) / var(tb)
        // α = TrMean - β*tbMean
        var cov = 0.0
        var varTb = 0.0
        for (s in cheepSyncWindow) {
            val x = s.tbNs - tbMean
            val y = s.TrNs - TrMean
            cov += x * y
            varTb += x * x
        }

        if (varTb == 0.0) return

        val beta = cov / varTb
        val alpha = TrMean - beta * tbMean

        _cheepSyncBeta.value = beta
        _cheepSyncAlpha.value = alpha

        // Optional: compute RMS residual (how well the line fits the window)
        var rss = 0.0
        for (s in cheepSyncWindow) {
            val pred = alpha + beta * s.tbNs
            val r = s.TrNs - pred
            rss += r * r
        }
        val rmsNs = kotlin.math.sqrt(rss / n)
        _cheepSyncRmsResidualMs.value = rmsNs / 1_000_000.0
    }

    /**
     * Map a beacon timestamp (μs since beacon boot) into the phone’s monotonic ns timeline:
     *   T_hat_phone_ns = α + β * tb_ns
     *
     * This is the “use the estimated skew+offset to coordinate time” step. :contentReference[oaicite:3]{index=3}
     */
    fun mapBeaconToPhoneNs(beaconTimeUs: Long): Long {
        val tbNs = beaconTimeUs.toDouble() * 1000.0
        val alpha = _cheepSyncAlpha.value
        val beta = _cheepSyncBeta.value
        return (alpha + beta * tbNs).toLong()
    }

    /**
     * Convenience: estimate current one-way delay-like residual for the most recent packet.
     * (Not the paper’s low-level event “best fit” selection; just a sanity metric.)
     */
    fun estimateLatestResidualMs(packet: EspPacket): Double {
        val TrNs = packet.receivedAtNs.toDouble()
        val tbNs = packet.tUs * 1000.0
        val pred = _cheepSyncAlpha.value + _cheepSyncBeta.value * tbNs
        return abs(TrNs - pred) / 1_000_000.0
    }

    // -----------------------------
    // BLE scanning
    // -----------------------------
    private val bleScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (!hasConnectPermission()) return
            val name = result.device.name ?: "Unnamed"
            val addr = result.device.address
            val display = "$name [$addr]"
            activity.runOnUiThread { onDeviceFound(display) }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("BLE", "Scan failed: $errorCode")
        }
    }

    // -----------------------------
    // GATT callbacks
    // -----------------------------
    private val gattCallback = object : BluetoothGattCallback() {

        @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("BLE", "GATT failed status=$status")
                activity.runOnUiThread {
                    Toast.makeText(activity, "Lost connection", Toast.LENGTH_SHORT).show()
                }
                gatt.close()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    val name = gatt.device.name ?: "Unnamed"
                    activity.runOnUiThread {
                        Toast.makeText(activity, "Connected to $name", Toast.LENGTH_SHORT).show()
                        onConnected(name)
                    }
                    gatt.requestMtu(247)
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    resetCheepSync()
                    activity.runOnUiThread {
                        Toast.makeText(activity, "Disconnected", Toast.LENGTH_SHORT).show()
                        onDisconnected()
                    }
                    gatt.close()
                    bluetoothGatt = null
                }

                else -> Log.w("BLE", "Unknown state: $newState")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid != ESP32_CHAR_UUID) return

            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            if (value.size < 12) return

            try {
                val packet = EspPacket(
                    seq = u32LE(value, 0),
                    tUs = u64LE(value, 4),
                    receivedAtNs = SystemClock.elapsedRealtimeNanos()
                )

                // Update CheepSync regression (α,β) on every packet
                updateCheepSync(packet)

                activity.runOnUiThread { onPacketReceived(packet) }
            } catch (e: Exception) {
                Log.e("ESP32", "Decode error", e)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) Log.e("BLE", "Descriptor write failed")
        }

        @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status != BluetoothGatt.GATT_SUCCESS) return

            gatt.getService(ESP32_SERVICE_UUID)?.getCharacteristic(ESP32_CHAR_UUID)?.let { tx ->
                gatt.setCharacteristicNotification(tx, true)
                tx.getDescriptor(CLIENT_CONFIG_DESCRIPTOR_UUID)?.let { cccd ->
                    writeClientConfigValue(gatt, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                }

                val props = tx.properties
                val propsList = buildList {
                    if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
                    if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
                    if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
                    if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
                }.joinToString()

                val info = CharacteristicInfo(
                    ESP32_SERVICE_UUID,
                    standardServiceNames[ESP32_SERVICE_UUID] ?: "Environmental Sensing",
                    tx.uuid,
                    "ESP32 Sensor Data",
                    propsList
                )

                activity.runOnUiThread { onCharacteristicsDiscovered(listOf(info)) }
            } ?: Log.e("BLE", "ESP32 characteristic not found")
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                @Suppress("DEPRECATION")
                val bytes = characteristic.value
                readValues[characteristic.uuid] =
                    bytes?.joinToString(" ") { it.toUByte().toString() } ?: "null"
            }
        }
    }

    // -----------------------------
    // Descriptor write helper
    // -----------------------------
    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    @SuppressLint("MissingPermission")
    private fun writeClientConfigValue(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value)
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                descriptor.javaClass.getMethod("setValue", ByteArray::class.java).invoke(descriptor, value)
                gatt.writeDescriptor(descriptor)
            }.onFailure { Log.e("BLE", "Legacy descriptor write failed", it) }
        }
    }

    // -----------------------------
    // Public BLE controls
    // -----------------------------
    @SuppressLint("MissingPermission")
    fun startBleScan() {
        if (!hasScanPermission()) {
            Toast.makeText(activity, "Scan permission not granted", Toast.LENGTH_SHORT).show()
            return
        }
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            Toast.makeText(activity, "BLE scanner unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        scanner.startScan(
            null,
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            bleScanCallback
        )
        bluetoothLeScanner = scanner
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        if (!hasScanPermission()) return
        (bluetoothLeScanner ?: bluetoothAdapter?.bluetoothLeScanner)?.stopScan(bleScanCallback)
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    @SuppressLint("MissingPermission")
    fun setNotificationsForCharacteristic(info: CharacteristicInfo, enable: Boolean): Boolean {
        val gatt = bluetoothGatt ?: return false
        val char = gatt.getService(info.serviceUuid)?.getCharacteristic(info.charUuid) ?: return false
        if (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0) return false

        gatt.setCharacteristicNotification(char, enable)
        char.getDescriptor(CLIENT_CONFIG_DESCRIPTOR_UUID)?.let { desc ->
            writeClientConfigValue(
                gatt,
                desc,
                if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            )
        }
        return true
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    @SuppressLint("MissingPermission")
    fun readCharacteristicOnce(charUuid: java.util.UUID, serviceUuid: java.util.UUID) {
        bluetoothGatt?.getService(serviceUuid)?.getCharacteristic(charUuid)?.let { char ->
            val ok = bluetoothGatt!!.readCharacteristic(char)
            Log.i("BLE", if (ok) "Read initiated" else "Read failed")
        } ?: Log.e("BLE", "Characteristic not found")
    }

    @RequiresPermission(PERMISSION_BLUETOOTH_CONNECT)
    @SuppressLint("MissingPermission")
    fun connectToDevice(address: String) {
        if (!hasConnectPermission()) {
            Toast.makeText(activity, "Permission denied", Toast.LENGTH_SHORT).show()
            return
        }

        stopBleScan()
        bluetoothGatt?.close()
        bluetoothGatt = null
        resetCheepSync()

        val device = bluetoothAdapter!!.getRemoteDevice(address)
        Handler(Looper.getMainLooper()).postDelayed({
            bluetoothGatt = device.connectGatt(activity, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            Toast.makeText(activity, "Connecting to $address", Toast.LENGTH_SHORT).show()
        }, 750)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try { bluetoothGatt?.disconnect() } catch (_: SecurityException) {}
        try { bluetoothGatt?.close() } catch (_: SecurityException) {}
        bluetoothGatt = null
        resetCheepSync()
    }
}
