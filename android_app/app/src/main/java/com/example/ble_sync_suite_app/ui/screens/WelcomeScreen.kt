package com.example.ble_sync_suite_app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// First screen: splash with fade/scale animation, then callback to go to main menu after 2.7s.

@Composable
fun WelcomeScreen(onAnimationFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(1000), label = "alpha")
    val scale by animateFloatAsState(if (visible) 1f else 0.95f, tween(1000), label = "scale")

    LaunchedEffect(Unit) {
        visible = true
        delay(2700)
        onAnimationFinished()
    }

    Column(
        modifier = Modifier.fillMaxSize().alpha(alpha).scale(scale).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("BLE Visualizer Demo", fontSize = 52.sp, color = Color(0xFF3F51B5), lineHeight = 60.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Scan. Connect. Visualize.", fontSize = 20.sp, color = Color(0xFF607D8B), textAlign = TextAlign.Center, lineHeight = 28.sp)
    }
}
