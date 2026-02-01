package com.example.ble_sync_suite_app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BleScanToggle(isScanning: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (isScanning) "Scanning Enabled" else "Scan Disabled",
            fontSize = 20.sp,
            color = if (isScanning) Color(0xFF3F51B5) else Color.Gray
        )
        Switch(checked = isScanning, onCheckedChange = onToggle)
    }
}

@Composable
fun FilterBar(query: String, onQueryChanged: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        label = { Text("Filter devices...") },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    )
}

@Composable
fun MainScannerScreen(
    isScanning: Boolean,
    searchQuery: String,
    scannedDevices: List<String>,
    onBack: () -> Unit,
    onScanToggle: (Boolean) -> Unit,
    onQueryChanged: (String) -> Unit,
    onDeviceClick: (address: String, name: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 48.dp)) {
        Button(onClick = onBack) { Text("Back to Menu") }
        Spacer(Modifier.height(12.dp))
        BleScanToggle(isScanning = isScanning, onToggle = onScanToggle)
        FilterBar(query = searchQuery, onQueryChanged = onQueryChanged)
        val filtered = scannedDevices.filter { it.contains(searchQuery, ignoreCase = true) }
        LazyColumn {
            items(filtered) { deviceInfo ->
                val address = deviceInfo.substringAfter("[").substringBefore("]")
                val name = deviceInfo.substringBefore(" [")
                Text(
                    text = deviceInfo,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp).clickable { onDeviceClick(address, name) }
                )
            }
        }
    }
}
