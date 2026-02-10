package com.example.ble_sync_suite_app.ui.screens

import com.example.ble_sync_suite_app.EspPacket
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

// Sync statistics screen: shows CheepSync fit (alpha, beta, skew), residuals, packet stats, transmission rate, time spans.
// Opened by tapping the ESP32 characteristic or enabling Notify on it. No graph drawing — stats only.

@Composable
fun GraphScreen(
    onBack: () -> Unit,
    packets: List<EspPacket>,
    cheepSyncAlpha: StateFlow<Double>,
    cheepSyncBeta: StateFlow<Double>
) {
    val alpha by cheepSyncAlpha.collectAsState()
    val beta by cheepSyncBeta.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Sync statistics", fontSize = 24.sp)
        }
        Spacer(Modifier.height(16.dp))

        if (packets.isEmpty()) {
            Text("No data yet. Waiting for ESP32 packets...", color = Color.Gray)
            return@Column
        }

        // Need at least 2 packets to compute fit-based stats
        if (packets.size >= 2) {
            val first = packets.first()
            // Residual = actual receive time minus (alpha + beta * tb) in ms
            val residualsMs = packets.map { p ->
                val Tr = p.receivedAtNs.toDouble()
                val tb = p.tUs * 1000.0
                (Tr - (alpha + beta * tb)) / 1_000_000.0
            }
            val meanAbsResidualMs = residualsMs.map { abs(it) }.average()
            val latestResidualMs = residualsMs.last()
            val clockSkew = beta - 1.0
            // Count sequence gaps (missing packets)
            val droppedPacketCount = run {
                var valid = 1
                for (i in 1 until packets.size) {
                    val prev = packets[i - 1].seq
                    val curr = packets[i].seq
                    if (curr == prev + 1L || (prev == 0xFFFF_FFFFL && curr == 0L)) valid++
                }
                packets.size - valid
            }
            val intervalsUs = (1 until packets.size).map { packets[it].tUs - packets[it - 1].tUs }.filter { it > 0 }
            val transmissionRateMs = if (intervalsUs.isNotEmpty()) intervalsUs.map { it.toDouble() }.average() / 1000.0 else 0.0
            val latest = packets.last()
            val tUsDeltaMs = (latest.tUs - first.tUs) / 1000.0
            val rxDeltaMs = (latest.receivedAtNs - first.receivedAtNs) / 1_000_000.0

            // Scrollable stats block: fit, residuals, packet count, rate, time spans
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).background(Color(0xFF111111), RoundedCornerShape(8.dp)).padding(12.dp)
            ) {
                Text("CheepSync Fit Metrics:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("  alpha (ns): ${"%.0f".format(alpha)}", fontSize = 12.sp, color = Color.White)
                Text("  beta (unitless): ${"%.9f".format(beta)}", fontSize = 12.sp, color = Color.White)
                Text("  skew = beta-1: ${"%.9f".format(clockSkew)}", fontSize = 12.sp, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text("Residual (sync error):", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("  Mean |residual|: ${"%.3f".format(meanAbsResidualMs)} ms", fontSize = 12.sp, color = Color.White)
                Text("  Latest residual: ${"%.3f".format(latestResidualMs)} ms", fontSize = 12.sp, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text("Packet Statistics:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("  Total packets: ${packets.size}", fontSize = 12.sp, color = Color.White)
                Text("  Dropped packets (seq gaps): $droppedPacketCount", fontSize = 12.sp, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text("Transmission Rate:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("  Avg interval: ${"%.2f".format(transmissionRateMs)} ms", fontSize = 12.sp, color = Color.White)
                if (transmissionRateMs > 0) Text("  Rate: ${"%.2f".format(1000.0 / transmissionRateMs)} packets/sec", fontSize = 12.sp, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text("Time Spans:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("  ESP32 span: ${"%.1f".format(tUsDeltaMs)} ms", fontSize = 12.sp, color = Color(0xFF90CAF9))
                Text("  Android Rx span: ${"%.1f".format(rxDeltaMs)} ms", fontSize = 12.sp, color = Color(0xFFA5D6A7))
            }
        } else {
            Text("Waiting for more packets to calculate sync metrics...", fontSize = 12.sp, color = Color.Gray)
        }
    }
}
