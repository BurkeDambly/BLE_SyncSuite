package com.example.ble_sync_suite_app.sync

import kotlin.math.abs
import kotlin.math.sqrt

// =============================================================================
// CHEEP SYNC — Standalone clock synchronization (no Android/BLE dependency)
// =============================================================================
//
// Purpose: Estimate the linear relationship between two clocks so we can convert
// timestamps from one (beacon, e.g. ESP32) to the other (receiver, e.g. phone).
//
// Model:  Tr ≈ α + β * tb
//   tb  = beacon time (e.g. microseconds since beacon boot), in ns for math
//   Tr  = receiver time (e.g. phone monotonic time) in nanoseconds
//   α   = offset in nanoseconds (phase: when tb=0, predicted Tr = α)
//   β   = skew, dimensionless (rate ratio; β=1 means same rate)
//
// Input:  Pairs (beaconTimeUs, receiverTimeNs) from your transport (e.g. each BLE packet).
// Output: Updated alpha, beta, and the ability to map any beacon timestamp → receiver ns.
//         No automatic correction of raw data; you call mapBeaconToReceiverNs when you need a converted time.
// =============================================================================

class CheepSync(
    private val windowSize: Int = DEFAULT_WINDOW_SIZE
) {
    // One (beacon time in ns, receiver time in ns) pair for the regression.
    private data class Sample(
        val tbNs: Double,
        val TrNs: Double
    )

    // Sliding window of recent samples. Oldest dropped when over windowSize.
    private val window = ArrayDeque<Sample>()

    /** Current offset α in nanoseconds. When beacon time is 0, receiver time ≈ alpha. */
    var alpha: Double = 0.0
        private set

    /** Current skew β (dimensionless). Ratio of receiver clock rate to beacon clock rate. */
    var beta: Double = 1.0
        private set

    /** How well the line fits the window: root-mean-square error in milliseconds. */
    var rmsResidualMs: Double = 0.0
        private set

    /** How many samples are currently in the sliding window. */
    val sampleCount: Int get() = window.size

    /**
     * Main sync method: add one (beacon time, receiver time) sample and recompute α, β.
     * Uses least-squares linear regression over the sliding window.
     *
     * Input:  beaconTimeUs  = timestamp from beacon clock (microseconds)
     *         receiverTimeNs = timestamp from receiver clock when packet was received (nanoseconds)
     * Output: None. Updates alpha, beta, rmsResidualMs. Need at least 2 samples to compute a fit.
     */
    fun addSample(beaconTimeUs: Long, receiverTimeNs: Long) {
        // Convert beacon μs → ns for consistent units in regression
        val tbNs = beaconTimeUs * 1000.0
        val TrNs = receiverTimeNs.toDouble()
        window.addLast(Sample(tbNs = tbNs, TrNs = TrNs))
        while (window.size > windowSize) {
            window.removeFirst()
        }

        if (window.size < 2) return

        val n = window.size.toDouble()
        // Compute means of tb and Tr over the window
        var tbMean = 0.0
        var TrMean = 0.0
        for (s in window) {
            tbMean += s.tbNs
            TrMean += s.TrNs
        }
        tbMean /= n
        TrMean /= n

        // Least-squares: β = cov(tb, Tr) / var(tb),  α = TrMean - β * tbMean
        var cov = 0.0
        var varTb = 0.0
        for (s in window) {
            val x = s.tbNs - tbMean
            val y = s.TrNs - TrMean
            cov += x * y
            varTb += x * x
        }

        if (varTb == 0.0) return

        val newBeta = cov / varTb
        val newAlpha = TrMean - newBeta * tbMean
        beta = newBeta
        alpha = newAlpha

        // RMS residual: sqrt(mean of squared errors) in ns, then convert to ms
        var rss = 0.0
        for (s in window) {
            val pred = alpha + beta * s.tbNs
            val r = s.TrNs - pred
            rss += r * r
        }
        val rmsNs = sqrt(rss / n)
        rmsResidualMs = rmsNs / 1_000_000.0
    }

    /**
     * Convert a beacon timestamp (μs) into the receiver's timeline (ns).
     * Formula: receiver_ns = α + β * (beaconTimeUs * 1000).
     */
    fun mapBeaconToReceiverNs(beaconTimeUs: Long): Long {
        val tbNs = beaconTimeUs * 1000.0
        return (alpha + beta * tbNs).toLong()
    }

    /**
     * Sync error for one sample: |actual receiver time - predicted| in milliseconds.
     * Useful to check how well a single packet fits the current fit.
     */
    fun residualMs(beaconTimeUs: Long, receiverTimeNs: Long): Double {
        val tbNs = beaconTimeUs * 1000.0
        val pred = alpha + beta * tbNs
        return abs(receiverTimeNs.toDouble() - pred) / 1_000_000.0
    }

    /**
     * Snapshot of (α, β) so other code can convert timestamps without holding a reference to CheepSync.
     * Use SyncFit.mapBeaconToReceiverNs(...) or mapBeaconToReceiverMs(...) with that snapshot.
     */
    fun getFit(): SyncFit = SyncFit(alpha = alpha, beta = beta)

    /** Clear the window and reset α=0, β=1. Call when starting a new connection/session. */
    fun reset() {
        window.clear()
        alpha = 0.0
        beta = 1.0
        rmsResidualMs = 0.0
    }

    companion object {
        const val DEFAULT_WINDOW_SIZE = 50
    }
}

/**
 * Immutable copy of (α, β). Use this to convert timestamps in another module without depending on CheepSync.
 * mapBeaconToReceiverNs: beacon μs → receiver ns.
 * mapBeaconToReceiverMs: beacon μs → receiver ms.
 */
data class SyncFit(
    val alpha: Double,
    val beta: Double
) {
    fun mapBeaconToReceiverNs(beaconTimeUs: Long): Long {
        val tbNs = beaconTimeUs * 1000.0
        return (alpha + beta * tbNs).toLong()
    }

    fun mapBeaconToReceiverMs(beaconTimeUs: Long): Double {
        return mapBeaconToReceiverNs(beaconTimeUs) / 1_000_000.0
    }
}
