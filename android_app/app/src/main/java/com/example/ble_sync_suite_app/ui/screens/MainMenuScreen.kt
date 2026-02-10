package com.example.ble_sync_suite_app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Main menu: single button to go to "Connect to Device" (scanner screen).

@Composable
fun MainMenuScreen(onConnectToDevice: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("BLE Sync Suite", fontSize = 36.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text("Select what you want to do", fontSize = 18.sp, color = Color.Gray, textAlign = TextAlign.Center)
        Spacer(Modifier.height(48.dp))
        Button(onClick = onConnectToDevice, modifier = Modifier.fillMaxWidth()) { Text("Connect to Device") }
    }
}
