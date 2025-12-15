## INMP441 Mic + WS2812 Sound Indicator (ESP32)

This setup uses **one INMP441 I2S microphone** to detect sound and turns a **WS2812 LED green** when sound exceeds a threshold.

---

### Hardware
- ESP32 (standard DevKit / Gold Edition)
- INMP441 I2S MEMS microphone
- WS2812 RGB LED
- 3.3 V power from ESP32

---

### INMP441 Wiring (I2S)

| INMP441 | ESP32 | Description |
|--------|-------|-------------|
| VDD | 3.3V | Power (**not 5V**) |
| GND | GND | Ground |
| SCK | GPIO26 | I2S Bit Clock (BCLK) |
| WS | GPIO25 | I2S Word Select (LRCLK) |
| SD | GPIO33 | I2S Data → ESP32 |
| L/R | GND | Channel select (must be tied) |

---

### WS2812 Wiring

| WS2812 | ESP32 |
|-------|-------|
| DIN | GPIO2 |
| VCC | 3.3V or 5V |
| GND | GND |

---

### Firmware Behavior
- ESP32 runs as **I2S master (RX)**
- Audio sampled at **16 kHz**
- Peak audio level is measured
- **LED turns green when sound > threshold**, otherwise off

---

### Notes
- INMP441 outputs **24-bit audio in 32-bit I2S slots**
- Adjust the sound threshold in code for sensitivity
- Designed for **single-microphone** use




## TODO — BLE-Sync Evaluation Checklist

### Firmware (ESP32 / White-box)
- [ ] Implement event detection (clap / impulse)
- [ ] Timestamp event using `esp_timer_get_time()`
- [ ] Gate detection to emit **one timestamp per event**
- [ ] (Lab) Toggle a GPIO at the same code location as timestamp
- [ ] Log timestamp + event ID over UART/BLE
- [ ] Measure detection latency + jitter with logic analyzer
- [ ] Record bounds on sensor-side timestamp error

### Android (Central Timebase)
- [ ] Use Android system clock as global reference
- [ ] Receive BLE packets with embedded sensor timestamps
- [ ] Store `(t_sensor, t_android_arrival)` pairs
- [ ] Fit affine clock model (`t_android = a * t_sensor + b`)
- [ ] Update offset/drift periodically (filter / regression)
- [ ] Convert all sensor events into Android time

### Black-box Device Handling
- [ ] Identify available timing info (arrival time, sequence number, sample index)
- [ ] Reconstruct timeline assuming fixed sample rate
- [ ] Apply same affine fitting pipeline (index → Android time)
- [ ] Quantify reconstruction error and jitter
- [ ] Explicitly label results as **timeline alignment**, not true clock sync

### Ground Truth & Validation
- [ ] Use GPIO + logic analyzer to validate white-box timestamps
- [ ] Use audio / cross-modal events for black-box consistency checks
- [ ] Use NTP only as coarse absolute reference (not sub-ms truth)
- [ ] Report median, 95th percentile, and long-term drift

### Evaluation & Reporting
- [ ] Compare white-box vs black-box accuracy
- [ ] Measure stability over time (minutes → hours)
- [ ] Quantify how sync error affects downstream tasks
- [ ] Clearly state assumptions and limits per device class
