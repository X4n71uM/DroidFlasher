# DroidFlasher (`net.xantium.droidflasher`) — Package Migration & Target Compatibility Plan

Complete migration of the application package, directory paths, and branding to **DroidFlasher** (`net.xantium.droidflasher`), paired with a **Target Device Compatibility & USB Access Audit** ensuring target devices accept flashing, ADB file push, and partition read/write operations without permission failures.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Compatibility Audit Finding**: Auditing how the rename to `net.xantium.droidflasher` (`DroidFlasher`) interacts with Android's USB Host subsystem and target bootloaders revealed **4 critical compatibility points** that we will address during this update so target devices reliably accept flashing and push processes:
> 1. **Fix Android 12–16 (`targetSdk 36`) USB Permission Grant (`FLAG_MUTABLE`)**: Using `FLAG_IMMUTABLE` on `UsbManager.requestPermission()` prevents the Android OS from writing `EXTRA_PERMISSION_GRANTED = true` into the broadcast reply. Switching to an explicit package-scoped `Intent("net.xantium.droidflasher.USB_PERMISSION").setPackage(context.packageName)` with `PendingIntent.FLAG_MUTABLE` (on API 31+) ensures the host OS actually grants USB access when the user accepts the dialog.
> 2. **Package-Scoped Custom Permission (`net.xantium.droidflasher.USB_PERMISSION`)**: Prevents `INSTALL_FAILED_DUPLICATE_PERMISSION` conflicts on Android 10+ if another USB tool is installed on the host device.
> 3. **CDC-ACM Control Line Wake (`DTR/RTS 0x22`) for Odin/Heimdall Targets**: Adds a pre-handshake `controlTransfer` (`request=0x22`, `value=0x03`) so Samsung Download Mode targets wake their bulk receiver before `"ODIN"` -> `"LOKE"` and PIT/partition flashing.
> 4. **ADB Push / Sideload Host Identity & Payload Push Action**: Adds explicit ADB `CNXN` host banner authentication (`DroidFlasher@net.xantium.droidflasher`) and a dedicated **Push File / Payload (`SYNC/SEND`)** action so target devices in ADB/Recovery mode accept file pushes alongside bootloader flashing.

---

## 1. Compatibility Analysis: What Changes & Why It Works Safely

| Subsystem | Impact of Renaming to `net.xantium.droidflasher` / `DroidFlasher` | Compatibility Guarantee |
| :--- | :--- | :--- |
| **Odin / Heimdall (`LOKE`) & Fastboot Protocols** | Raw USB bulk packets (`"ODIN"`, `"getvar:all"`, `FILE_TRANSFER 0x66`) are binary/ASCII protocol frames and do not depend on the host APK package name. | **100% Compatible** — Enhanced with CDC-ACM `SET_CONTROL_LINE_STATE` (`0x22`) so targets never ignore initial bulk writes. |
| **Android Host USB Access Dialog (`UsbManager`)** | The system prompt *"Allow DroidFlasher to access USB device?"* binds permission to `applicationId` (`net.xantium.droidflasher`) and delivers the result via `PendingIntent`. | **Fixed & Hardened** — Uses explicit package-scoped action `net.xantium.droidflasher.USB_PERMISSION` + `FLAG_MUTABLE` so `EXTRA_PERMISSION_GRANTED` is properly populated on Android 12+. |
| **ADB Push / Sideload & Diagnostic Mode** | Target devices in ADB or Recovery mode inspect the `CNXN` host identity string and `SYNC` (`SEND`/`DATA`/`DONE`) stream frames. | **Enhanced** — Updates host identity to `DroidFlasher@net.xantium.droidflasher` and adds direct **ADB Push File to Target (`/sdcard/` / `/tmp/`)** support. |
| **Expanded OEM USB Device Filter (`usb_device_filter.xml`)** | Auto-launch and device attachment filters match USB Vendor IDs (`VID`). | **Expanded** — Covers Samsung (`04E8`), Google (`18D1`), Xiaomi (`2717`), Qualcomm EDL (`05C6`), MediaTek (`0E8D`), OnePlus/Oppo (`22D9`), Motorola (`22B8`), Sony (`0FCE`), and ASUS (`0B05`). |

---

## 2. User Experience & Visual Design Updates

1. **Overall Branding (`DroidFlasher`)**:
   - Updates `metadata.json`, Gradle `rootProject.name`, `res/values/strings.xml` (`app_name = "DroidFlasher"`), top status header (`DROIDFLASHER`), exported PIT comments, backup manifests, and log filenames (`DroidFlasher_SessionLog_[TIMESTAMP].log`).
2. **USB Access & ADB Push Controls in `USB & Link` & `Flasher`**:
   - Displays clear permission & interface claim status (`USB ACCESS: GRANTED`, `DTR/RTS: ACTIVE`, `PROTOCOL: READY`).
   - Adds a quick-action **Push File to Target (`ADB / Fastboot Push`)** button allowing technicians to push custom `.zip`, `.img`, `.bin`, or scripts directly to `/sdcard/Download/` or `/tmp/` on the connected target device with live byte progress and terminal packet logging.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Full Physical Directory & Namespace Move to `net.xantium.droidflasher`**
  - *Chosen Approach*: Set both `namespace = "net.xantium.droidflasher"` and `applicationId = "net.xantium.droidflasher"` in Gradle, move all Kotlin source and test files into `net/xantium/droidflasher/...`, and delete the old `com/example` directory tree.
  - *Why*: Eliminates any split between the manifest package, `R` class generation, `BuildConfig.APPLICATION_ID`, and runtime `context.packageName` used by `UsbManager`.
- **Decision 2 — Explicit Mutable `PendingIntent` for USB Permission Broadcasts**
  - *Chosen Approach*: Construct the USB permission `Intent("net.xantium.droidflasher.USB_PERMISSION").setPackage(context.packageName)` with `PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE` on Android S+ (API 31+).
  - *Why*: Required by Android's `UsbUserSettingsManager` so the OS can mutate the broadcast intent to inject `UsbManager.EXTRA_PERMISSION_GRANTED = true` and `UsbManager.EXTRA_DEVICE`.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & USB Access Handshake Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                     PACKAGE ROOT: net.xantium.droidflasher                   │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │ MainActivity & UsbBroadcastReceiver                                    │  │
│  │  • Listens for net.xantium.droidflasher.USB_PERMISSION                 │  │
│  │  • Handles USB_DEVICE_ATTACHED / DETACHED hotplug events               │  │
│  └───────────────┬────────────────────────────────────────┬───────────────┘  │
│                  │                                        │                  │
│  ┌───────────────▼────────────────┐      ┌────────────────▼───────────────┐  │
│  │ net.xantium.droidflasher.ui    │      │ net.xantium.droidflasher       │  │
│  │  ├─ theme / components         │      │   .viewmodel.WorkbenchViewModel│  │
│  │  └─ screens (USB + Push,       │      └────────────────┬───────────────┘  │
│  │     Flasher, Backup, PIT)      │                       │                  │
│  └────────────────────────────────┘      ┌────────────────▼───────────────┐  │
│                                          │ net.xantium.droidflasher.data  │  │
│                                          │  ├─ UsbOtgEngine               │  │
│                                          │  │   ├─ Explicit FLAG_MUTABLE  │  │
│                                          │  │   ├─ CDC-ACM 0x22 DTR/RTS   │  │
│                                          │  │   ├─ LOKE / Fastboot Flash  │  │
│                                          │  │   └─ ADB SYNC/SEND Push     │  │
│                                          │  ├─ PitEngine                  │  │
│                                          │  ├─ BackupExportManager        │  │
│                                          │  └─ local.WorkbenchDatabase    │  │
│                                          └────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Interactive Component & State Mapping

- **USB Permission & Endpoint Claim Flow**:
  - When the technician taps **Handshake** or **Scan USB OTG**, `UsbOtgEngine` checks `usbManager.hasPermission(device)`. If not yet granted, it dispatches the package-scoped mutable `PendingIntent` for `net.xantium.droidflasher.USB_PERMISSION`.
  - Once accepted, `UsbOtgEngine` opens `UsbDeviceConnection`, claims the Bulk IN/OUT interface with `force = true` (detaching kernel drivers), sends the CDC-ACM `0x22` control line wake signal, and completes the protocol handshake (`ODIN` -> `LOKE`, Fastboot `getvar:all`, or ADB `CNXN`).
- **Push Process (`pushFileToTargetDevice`)**:
  - Technicians can push any `.img`, `.bin`, `.tar`, or update package to the target device via the **Push to Target** control in the **USB & Link** tab, streaming `SYNC` / `SEND` / `DATA` / `DONE` frames over Bulk OUT and logging target `OKAY` ACKs in the live terminal.
