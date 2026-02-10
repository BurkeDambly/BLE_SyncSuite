# CheepSync — Standalone clock sync module

This folder contains **only** the CheepSync clock synchronization logic. It has **no** dependency on Android, BLE, or any transport.

## Drag-and-drop usage

1. Copy `CheepSync.kt` (and optionally this README) into your project.
2. Dependencies: **Kotlin stdlib only** (`kotlin.math`).

## Contract

- **Input**: Pairs of (beacon time in **microseconds**, receiver time in **nanoseconds**). You decide what “beacon” and “receiver” mean (e.g. device clock vs host clock, sensor time vs app time).
- **Output**: A linear fit **Tr ≈ α + β × tb** (with tb in ns), so you can map any beacon timestamp into the receiver’s time base.

## API

```kotlin
// Create (optional: custom window size)
val sync = CheepSync(windowSize = 50)  // default 50

// Feed samples from your transport (e.g. each packet)
sync.addSample(beaconTimeUs = tUs, receiverTimeNs = receivedAtNs)

// Read current fit
val alpha: Double = sync.alpha   // offset (ns)
val beta: Double  = sync.beta    // skew (dimensionless)
val rmsMs: Double = sync.rmsResidualMs

// Map a beacon timestamp → receiver timeline (ns)
val receiverNs: Long = sync.mapBeaconToReceiverNs(beaconTimeUs)

// Or take a snapshot to use elsewhere / different units
val fit: SyncFit = sync.getFit()
val receiverNs2 = fit.mapBeaconToReceiverNs(beaconTimeUs)
val receiverMs  = fit.mapBeaconToReceiverMs(beaconTimeUs)

// Residual for one sample (sanity metric)
val residualMs = sync.residualMs(beaconTimeUs, receiverTimeNs)

// Reset when starting a new session
sync.reset()
```

## Time units

- **Beacon time**: always passed in **microseconds** (`beaconTimeUs`).
- **Receiver time**: always in **nanoseconds** (`receiverTimeNs`) for regression and `mapBeaconToReceiverNs`; use `SyncFit.mapBeaconToReceiverMs` if you need milliseconds.

Your “receiver” clock can be monotonic elapsed time, wall time in ns, or any consistent ns-scale clock; the math is the same.
