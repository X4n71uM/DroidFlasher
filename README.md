# DroidFlasher ⚡📱

[![Android CI](https://img.shields.io/badge/Platform-Android_14%2B_%2F_API_24%2B-brightgreen.svg)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Design-Material%203-purple.svg)](https://m3.material.io)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**DroidFlasher** is an industrial-grade mobile firmware flashing, partition backup, and PIT (Partition Information Table) engineering suite designed for Android hardware repair technicians, kernel developers, and power users.

It runs directly on an Android host device using USB OTG (On-The-Go) cables or USB-C to USB-C interconnects to service client devices — eliminating the requirement of carrying a laptop or desktop computer to flashing stations.

---

## 🌟 Key Capabilities

### 🔌 1. Low-Level USB OTG Host Engine
- **Hardware Direct Communication**: Interfaces with target devices over Android USB Host APIs (`UsbManager`, `UsbDeviceConnection`, `UsbEndpoint`).
- **Multi-Protocol Architecture**:
  - **Samsung Download Mode (Heimdall / Odin LOKE)**: Complete CDC-ACM control handshaking, DTR/RTS carrier signals, 1024-byte packet framing, and LOKE command session negotiation.
  - **Fastboot Mode**: Fastboot bulk-endpoint querying, bootloader information probing, and partition staging.
  - **ADB Bridge / Bulk Loader**: Push, recovery, and payload transfer bridge.
- **Hotplug Resilient**: Automatic detection and instant recovery upon device re-connection or mode reboots.

### 📋 2. Real Hardware PIT Management (Partition Information Table)
- **Live PIT Pull**: Pull binary PIT tables directly from connected devices in Download Mode via LOKE `0x65` protocol.
- **Live PIT Push**: Flash custom or modified `.pit` partition tables to repartition storage safely.
- **Bidirectional Parser & Compiler**:
  - Encodes and decodes binary PIT files (`0x12349876` magic header).
  - Normalizes UFS 4.0 / eMMC 5.1 sectors and 4KB block alignments.
  - Visual visualizer and raw text syntax editor with real-time overlap validation.
- **Profile Library**: Save, export, and load device layout profiles (Room database backed, with export to external storage).

### ⚡ 3. Selective Firmware Flasher
- **Targeted Partition Flashing**: Flash individual partition images (`BOOT`, `RECOVERY`, `SUPER`, `INIT_BOOT`, `VENDOR_BOOT`, etc.) without wiping whole devices.
- **Safety Safeguards**:
  - Mandatory EFS/Radio backup verification before executing hazardous repartition operations.
  - Real-time SHA-256 and MD5 integrity checks prior to payload transmission.
  - Overlap and 4KB block alignment checker.

### 🛡️ 4. Partition Backup Studio
- **Critical Partition Archiving**: One-click backups of sensitive radio and partition calibrations (`EFS`, `SEC_EFS`, `RADIO`, `NVRAM`, `PERSIST`).
- **Comprehensive Manifests**: Generates backup archives containing `.img` partition dumps, `device_layout.pit`, and timestamped verification manifests.
- **Instant Restore Staging**: Stage previously created backups directly into the flasher queue with a single tap.

### 💻 5. Real-Time Technician Terminal
- **Detailed Live Logs**: Inspect raw endpoint handshakes, transfer throughput (MB/s), error codes, and packet status.
- **Filterable Stream**: Filter between INFO, WARN, ERROR, and SUCCESS events.
- **Exportable Telemetry**: Export terminal logs to external storage for diagnostic reviews.

---

## 📸 Screenshots & Architecture

```
   ┌─────────────────────────────────────────────────────────────┐
   │                     DroidFlasher Suite                      │
   ├──────────────┬──────────────┬──────────────┬────────────────┤
   │  USB & Link  │   Flasher    │    Backup    │   PIT Editor   │
   ├──────────────┴──────────────┴──────────────┴────────────────┤
   │             Reactive ViewModel (StateFlow)                  │
   ├─────────────────────────────┬───────────────────────────────┤
   │       UsbOtgEngine          │           PitEngine           │
   │  (LOKE / Fastboot / CDC)    │     (Parser / Validator)      │
   ├─────────────────────────────┼───────────────────────────────┤
   │    Android UsbManager       │     Room Database + Storage   │
   │      (Physical OTG)         │       (Profiles & Backups)    │
   └─────────────────────────────┴───────────────────────────────┘
```

---

## 🛠️ Tech Stack & Requirements

- **Language**: Kotlin 2.0+
- **UI Toolkit**: Jetpack Compose with Material Design 3 (Edge-to-Edge display, Dynamic Theming)
- **Architecture**: MVVM (Model-View-ViewModel) + Clean Data Layer
- **Persistence**: AndroidX Room, MediaStore API
- **Concurrency**: Kotlin Coroutines & Flow
- **Minimum SDK**: Android 7.0 (API level 24)
- **Target SDK**: Android 15 / 16 (API level 36)
- **Required Hardware**: Android smartphone/tablet supporting USB Host / OTG mode with an appropriate OTG cable or USB-C to USB-C data cable.

---

## 🚀 Building & Installing

### Prerequisites
1. Android Studio Ladybug / Meerkat (or JDK 17+ with Android SDK tools installed).
2. Git.

### Building from Source

Clone the repository:
```bash
git clone https://github.com/your-username/DroidFlasher.git
cd DroidFlasher
```

Build the debug APK:
```bash
./gradlew assembleDebug
```
The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

Run local Robolectric and unit tests:
```bash
./gradlew testDebugUnitTest
```

---

## 📖 Usage Guide for Technicians

1. **Connect Target Device**:
   - Put target device into **Download Mode** (Volume Down + Volume Up while inserting USB-C cable on most Samsung devices).
   - Connect the host device using a high-quality USB OTG adapter.
2. **Grant Permissions**:
   - When the Android USB Host authorization prompt appears, tap **Allow**.
3. **Pull Hardware PIT**:
   - Navigate to the **USB & Link** or **PIT Editor** tab and tap **Pull PIT from Device**.
   - The device partition layout will be fetched, verified, and added to your saved profiles.
4. **Flash or Backup**:
   - Use the **Flasher** tab to stage custom kernels, recoveries, or partition images.
   - Use the **Backup Studio** to safeguard `EFS` or modem calibrations before any modification.

---

## ⚠️ Disclaimer & Safety Notice

Flashing low-level partitions, bootloaders, and modifying PIT layouts carries inherent risks of soft-bricking or hard-bricking devices. DroidFlasher is intended for professional hardware technicians and developers. Always ensure:
- Your host device battery is above 50%.
- You have made a verified backup of `EFS` / `RADIO` partitions before repartitioning.
- The USB cable and OTG adapter provide stable connections without loose contacts.

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.
