# Demo_BLE_Sensor_App

An Android app for scanning nearby Bluetooth Low Energy (BLE) devices and visualizing their data in real time.

---

## 📱 Android Setup Instructions

Before running this app on your Android phone, follow these steps to ensure everything works properly:

### 1. Enable Developer Options
- Go to **Settings > System information > About phone**
- Tap **Build number** seven times until you see "You are now a developer!"

### 2. Enable USB Debugging
- Go to **Settings > System > Developer options**
- Turn on **USB debugging**

### 3. Enable Location Services
- BLE scanning requires location services to be enabled.
- Go to **Settings > Location** and turn it **ON**

### 4. Grant Permissions on First Launch
- When the app runs, it will request the following permissions:
  - `BLUETOOTH_SCAN`
  - `BLUETOOTH_CONNECT`
  - `ACCESS_FINE_LOCATION`
  - `ACCESS_COARSE_LOCATION`
- Accept all permission prompts for full functionality.

---

## 🚀 Features

- 📡 **BLE Device Scanning**  
  Detects and lists nearby BLE-enabled devices, displaying their name and MAC address in the log.

- 📈 **Future: Sensor Data Visualization**  
  Will connect to specific BLE peripherals and graph incoming sensor data in real time.

---

## 🧑‍💻 Tech Stack

- Kotlin
- Jetpack Compose
- Android BLE APIs
- Material 3 UI Components

---

## 🛠️ Development

Clone the repo and open it in Android Studio:

```bash
git clone https://github.com/iotrustlab/Demo_BLE_Sensor_App.git
