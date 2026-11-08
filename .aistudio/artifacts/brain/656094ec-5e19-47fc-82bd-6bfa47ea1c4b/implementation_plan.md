# Persistent PIT Pull, Auto-Archive & Reusable Staging Suite

Enable automatic persistence, file system export, and immediate bidirectional reuse of pulled Samsung/Fastboot PIT (Partition Information Table) data across the Selective Flasher, PIT Editor, and Profile Library in DroidFlasher.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following user preferences were confirmed during clarification:
> - **Storage Strategy**: Pulled PIT tables will be **automatically saved** both to the internal Room database (`saved_pit_profiles`) as an indexed profile and exported to the device's external `Downloads/` directory as binary `.pit` and human-readable `.pit.txt` files.
> - **Reuse Workflow**: The pulled partition layout will be **immediately staged into both the Selective Flasher and the PIT Editor**, populating writable partitions for flashing while allowing instant inspection, editing, and manual reloading from the Profile Library.

---

### 1. Overview & Core Concept

When an Android technician taps **"Pull PIT"** in the **USB & Link** tab:
1. **Hardware Acquisition**: DroidFlasher executes the low-level USB command sequence (Heimdall/LOKE `0x64` PIT dump or bootloader partition query) via the host OTG link.
2. **Persistence & Archiving**:
   - Stores the layout as a timestamped `SavedPitProfileEntity` (`PIT_[MODEL]_[TIMESTAMP]`) in the on-device SQLite/Room database.
   - Writes standard binary `.pit` (with `0x12349876` magic header) and structured text manifest (`.pit.txt`) to the public `Downloads/PIT_[MODEL]_[TIMESTAMP]/` directory via Android MediaStore.
3. **Seamless Staging & Reusability**:
   - The **Selective Flasher** queue immediately updates with all parsed partition blocks, offsets, and IDs, ready for firmware mapping.
   - The **PIT Editor** reflects the extracted LBA layout in both Raw Monospace syntax and Visual Table inspector modes.
   - Technicians can re-load, switch, or restore any past pulled PIT profile at any time directly from the PIT Editor or Flasher via the Saved Profiles manager.

---

### 2. User Experience & Visual Design

#### Step-by-Step User Flow
1. **Triggering Pull**: Technician connects device in Download or Bootloader mode and taps `Pull PIT` in the USB & Link tab.
2. **Real-Time Telemetry & Progress**:
   - Terminal logs display request transmission, USB bulk packet extraction, and header magic verification (`0x12349876`).
3. **Automated Confirmation & Quick Actions**:
   - A contextual success banner displays: *"PIT pulled successfully from [MODEL] • Saved to Profile Library & Downloads/ • Staged in Flasher & Editor"*.
   - Includes quick navigation buttons: **"Open in Flasher"** and **"Inspect in Editor"**.
4. **Reuse in Flasher**:
   - The Flasher tab automatically displays the device's genuine partition layout (e.g. `BOOT`, `RECOVERY`, `SUPER`, `EFS`, `MODEM`).
   - A new quick-action chip **"Switch PIT Profile"** allows instant switching between previously pulled PIT snapshots.
5. **Reuse in PIT Editor**:
   - The PIT profile is listed under **"Saved Workshop Profiles"** with entry count, timestamp, and model tag.
   - Technicians can modify, re-export, or flash with custom repartition offsets.

#### Visual Polish & Aesthetics
- **Color Accent**: `CyanTelemetry` (`#00E5FF`) for active link telemetry and `SignalGreen` (`#00E676`) for successful archive tags.
- **Badges**: Monospace badges indicating entry count (`32 Partitions`), storage type (`UFS 4.0`), and file path.
- **Empty State Elimination**: Eliminates zero-state confusion by populating actual device geometries immediately upon hardware read.

---

### 3. Key Product Decisions & Trade-Offs

#### 1. Dual Persistence (Room DB + MediaStore Downloads)
- **Chosen Approach**: Insert into Room `saved_pit_profiles` for sub-millisecond in-app profile switching, while simultaneously writing standard binary `.pit` and text `.pit.txt` to `Downloads/`.
- **Why**: Room provides reactive Compose StateFlow integration; public Downloads export ensures technicians can copy the `.pit` to their PC or share via repair shop networks.
- **Alternative Considered**: Database only — discarded because technicians frequently need the physical `.pit` binary for desktop Odin or Heimdall CLI tools.

#### 2. Automatic Dual-Tab Staging (Flasher + Editor)
- **Chosen Approach**: Immediately update the ViewModel's active PIT text and flash queue upon successful pull.
- **Why**: Avoids extra clicks; the technician's primary intent when dumping a device's partition table is either to flash updated binaries or inspect partition boundaries.
- **Alternative Considered**: Requiring manual navigation to load the file — rejected per confirmed user preference.

#### 3. Graceful Hardware & Offline Fallback
- **Chosen Approach**: If the device USB connection is established, query bulk endpoints; if target returns empty or unformatted blocks, generate the verified model-specific OEM geometry and log transparent hardware telemetry.

---

### 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       DROIDFLASHER WORKBENCH HOST                           │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                        [Pull PIT] Tap Event
                                       ▼
 ┌───────────────────────────────────────────────────────────────────────────┐
 │ UsbOtgEngine: Hardware PIT Extraction                                     │
 │ - Asserts CDC-ACM DTR/RTS (0x22 -> 0x03)                                  │
 │ - Transmits LOKE PIT_DUMP request (cmd=0x64, sub=0x01)                    │
 │ - Reads bulk IN stream & validates PIT_MAGIC (0x12349876)                 │
 └─────────────────────────────────────┬─────────────────────────────────────┘
                                       │
                      Decoded Binary / Text PIT Data
                                       ▼
 ┌───────────────────────────────────────────────────────────────────────────┐
 │ WorkbenchViewModel: Persistent Pipeline                                   │
 ├─────────────────────────────────┬─────────────────────────────────────────┤
 │ 1. Room Persistence:            │ 2. MediaStore Export:                   │
 │    SavedPitProfileEntity ->     │    Downloads/PIT_[Model]_[Time]/        │
 │    WorkbenchDao.insertPitProfile│    - [model]_layout.pit (Binary)        │
 │                                 │    - [model]_layout.pit.txt (Syntax)    │
 ├─────────────────────────────────┴─────────────────────────────────────────┤
 │ 3. Bidirectional Staging:                                                 │
 │    - _pitEditorText.value = parsedText                                    │
 │    - syncFlashQueueWithEntries(result.parsedEntries)                      │
 │    - Contextual Banner: "Staged in Flasher & Editor"                      │
 └─────────────────────────────────────┬─────────────────────────────────────┘
                                       │
                ┌──────────────────────┴──────────────────────┐
                ▼                                             ▼
  ┌───────────────────────────┐                 ┌───────────────────────────┐
  │ Selective Flasher Tab     │                 │ Professional PIT Editor   │
  │ - Dynamic Writable Blocks │                 │ - Monospace Syntax Editor │
  │ - Per-Partition Mapping   │                 │ - Visual Table Inspector  │
  │ - Quick Profile Switcher  │                 │ - Saved Profiles Library  │
  └───────────────────────────┘                 └───────────────────────────┘
```

#### State & Entity Mapping
- **`SavedPitProfileEntity`**:
  - `profileName`: e.g. `"PIT_SM-S918B_20261108_074500"`
  - `deviceModel`: e.g. `"SM-S918B"`
  - `entryCount`: Parsed partition count (e.g. `14` or `48`)
  - `rawPitContent`: Complete round-trippable text syntax (`#PIT_MAGIC=0x12349876... ENTRY|...`)
- **`BackupExportManager` / `PitExportHelper`**:
  - Encodes binary `.pit` (28B header + 132B per partition entry)
  - Creates timestamped output directory under `Environment.DIRECTORY_DOWNLOADS`
- **`WorkbenchViewModel`**:
  - `pullPitFromConnectedDevice()` orchestrates extraction -> room storage -> file export -> flash queue rebuild -> editor text sync.
  - Exposes `loadSavedPitProfile(SavedPitProfileEntity)` to allow one-tap switching of active PIT profiles from either the PIT Editor or Flasher tab.
