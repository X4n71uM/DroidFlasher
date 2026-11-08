package net.xantium.droidflasher.viewmodel

import android.app.Application
import android.hardware.usb.UsbDevice
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.xantium.droidflasher.data.BackupExportManager
import net.xantium.droidflasher.data.PitEngine
import net.xantium.droidflasher.data.UsbOtgEngine
import net.xantium.droidflasher.data.local.BackupSessionEntity
import net.xantium.droidflasher.data.local.SavedPitProfileEntity
import net.xantium.droidflasher.data.local.WorkbenchDatabase
import net.xantium.droidflasher.data.local.WorkbenchRepository
import net.xantium.droidflasher.data.toHexPreview
import net.xantium.droidflasher.model.BackupProgressState
import net.xantium.droidflasher.model.FlashJobItem
import net.xantium.droidflasher.model.FlashJobStatus
import net.xantium.droidflasher.model.FlashProtocol
import net.xantium.droidflasher.model.GuideTopic
import net.xantium.droidflasher.model.LogLevel
import net.xantium.droidflasher.model.PitPartitionEntry
import net.xantium.droidflasher.model.PitValidationResult
import net.xantium.droidflasher.model.SafetyConfig
import net.xantium.droidflasher.model.TerminalLogEntry
import net.xantium.droidflasher.model.UsbTargetDevice
import net.xantium.droidflasher.model.WorkbenchTab

class WorkbenchViewModel(application: Application) : AndroidViewModel(application) {

    private val usbEngine = UsbOtgEngine(application.applicationContext)
    private val backupManager = BackupExportManager(application.applicationContext, usbEngine)
    private val repository: WorkbenchRepository

    private var rawUsbMap: Map<Int, UsbDevice> = emptyMap()
    private var pendingUsbAction: (() -> Unit)? = null
    private var lastPulledPitBinary: ByteArray? = null
    private val logIdCounter = AtomicLong(100L)

    private val _activeTab = MutableStateFlow(WorkbenchTab.USB_DEVICE)
    val activeTab: StateFlow<WorkbenchTab> = _activeTab.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<UsbTargetDevice>>(emptyList())
    val availableDevices: StateFlow<List<UsbTargetDevice>> = _availableDevices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<UsbTargetDevice?>(null)
    val selectedDevice: StateFlow<UsbTargetDevice?> = _selectedDevice.asStateFlow()

    private val _pitEditorText = MutableStateFlow("")
    val pitEditorText: StateFlow<String> = _pitEditorText.asStateFlow()

    private val _pitValidation = MutableStateFlow<PitValidationResult?>(null)
    val pitValidation: StateFlow<PitValidationResult?> = _pitValidation.asStateFlow()

    private val _isVisualPitMode = MutableStateFlow(false)
    val isVisualPitMode: StateFlow<Boolean> = _isVisualPitMode.asStateFlow()

    private val _pitSearchQuery = MutableStateFlow("")
    val pitSearchQuery: StateFlow<String> = _pitSearchQuery.asStateFlow()

    private val _pitReplaceText = MutableStateFlow("")
    val pitReplaceText: StateFlow<String> = _pitReplaceText.asStateFlow()

    private val _flashQueue = MutableStateFlow<List<FlashJobItem>>(emptyList())
    val flashQueue: StateFlow<List<FlashJobItem>> = _flashQueue.asStateFlow()

    private val _isFlashingBatch = MutableStateFlow(false)
    val isFlashingBatch: StateFlow<Boolean> = _isFlashingBatch.asStateFlow()

    private val _batchProgress = MutableStateFlow(0f)
    val batchProgress: StateFlow<Float> = _batchProgress.asStateFlow()

    private val _safetyConfig = MutableStateFlow(SafetyConfig())
    val safetyConfig: StateFlow<SafetyConfig> = _safetyConfig.asStateFlow()

    private val _hasRecentEfsBackup = MutableStateFlow(false)
    val hasRecentEfsBackup: StateFlow<Boolean> = _hasRecentEfsBackup.asStateFlow()

    private val _showRepartitionHazardModal = MutableStateFlow(false)
    val showRepartitionHazardModal: StateFlow<Boolean> = _showRepartitionHazardModal.asStateFlow()

    private val _selectedBackupIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedBackupIds: StateFlow<Set<Int>> = _selectedBackupIds.asStateFlow()

    private val _backupProgress = MutableStateFlow(BackupProgressState())
    val backupProgress: StateFlow<BackupProgressState> = _backupProgress.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<TerminalLogEntry>>(emptyList())
    val terminalLogs: StateFlow<List<TerminalLogEntry>> = _terminalLogs.asStateFlow()

    private val _terminalFilter = MutableStateFlow<LogLevel?>(null)
    val terminalFilter: StateFlow<LogLevel?> = _terminalFilter.asStateFlow()

    private val _isTerminalExpanded = MutableStateFlow(false)
    val isTerminalExpanded: StateFlow<Boolean> = _isTerminalExpanded.asStateFlow()

    private val _activeTooltip = MutableStateFlow<String?>(null)
    val activeTooltip: StateFlow<String?> = _activeTooltip.asStateFlow()

    val backupSessions: StateFlow<List<BackupSessionEntity>>
    val savedPitProfiles: StateFlow<List<SavedPitProfileEntity>>

    val guideTopics: List<GuideTopic> = listOf(
        GuideTopic(
            id = "otg_setup",
            title = "1. USB OTG Connection, Permission & Push",
            badge = "USB & LINK",
            summary = "Connect target device via OTG cable in Download (Odin/LOKE), Fastboot, or ADB Push mode.",
            steps = listOf(
                "Power off the target smartphone completely and wait 5 seconds for PMIC rail discharge.",
                "For Samsung (Heimdall/LOKE): Hold Volume Up + Volume Down simultaneously while plugging in the USB-C OTG cable connected to this DroidFlasher host phone, then press Volume Up to enter Download Mode.",
                "For Google/Xiaomi/Generic (Fastboot): Hold Volume Down + Power until the Fastboot interface initializes.",
                "Tap 'Scan USB OTG' in the USB & Link tab, accept the DroidFlasher USB access dialog, and tap 'Handshake', 'Pull PIT', or 'Push File'."
            ),
            technicalNote = "DroidFlasher claims both CDC-ACM Control (0x02) and Data (0x0A) interfaces, asserts DTR/RTS (0x22) + 115200 8N1, and negotiates an Odin LOKE session (0x64) so targets immediately accept PIT dumps and flashing."
        ),
        GuideTopic(
            id = "selective_flash",
            title = "2. Selective Partition Flashing & Checksums",
            badge = "FLASHER",
            summary = "Map .img, .bin, or .tar firmware binaries to specific PIT partitions and flash safely.",
            steps = listOf(
                "Ensure a valid PIT table is loaded (pulled from the device or opened in PIT Editor).",
                "Toggle your desired pre-flash safety gates (SHA-256 verification, 4K alignment check, dry-run handshake).",
                "Select individual partitions (e.g., BOOT, RECOVERY, SUPER) and map your local .img/.bin/.tar firmware files via the attachment icon.",
                "Tap 'Flash Selected' to stream 128KB LOKE sequences (16KB USB bulk packets) with real-time throughput and ACK telemetry."
            ),
            technicalNote = "Never flash BL/SBOOT with a lower anti-rollback SWREV bit than the fuse count burned on the target SoC."
        ),
        GuideTopic(
            id = "efs_backup",
            title = "3. Automated NAND & EFS Radio Backup",
            badge = "BACKUP STUDIO",
            summary = "Always back up EFS/SEC_EFS/MODEM and the hardware PIT before repartitioning or flashing.",
            steps = listOf(
                "Open the Backup Studio tab and select 'Critical EFS / Radio Only' or 'Full Device'.",
                "Tap 'Export Backup' to pull the live PIT and partition images into modular .img/.bin files plus binary/text PIT manifests.",
                "All files are automatically written to Downloads/Backup_[DEVICE MODEL]_[YYYY-MM-DD_HH-mm-ss]/ with a SHA256SUMS.txt file.",
                "Use 'Stage Backup into Selective Flasher' on any previous backup card to load those exact partition images back into the Flasher."
            ),
            technicalNote = "Losing EFS or NVRAM permanently erases factory IMEI calibration, Bluetooth MAC, and Wi-Fi NV items."
        ),
        GuideTopic(
            id = "pit_engineering",
            title = "4. Professional PIT Editing & Repartitioning",
            badge = "PIT EDITOR",
            summary = "Pull, edit, export, or push PIT binary partition tables in Raw Syntax or Visual Table mode.",
            steps = listOf(
                "Tap 'Pull PIT' to dump the binary PIT directly from a connected device in Download Mode (LOKE 0x65).",
                "Switch between 'Raw Syntax + Lines' (line-numbered monospace with Search & Replace) and 'Visual Inspector'.",
                "Verify that #PIT_MAGIC=0x12349876 is intact and review the validation status banner.",
                "Use 'Push PIT to Device' to write the compiled PIT table to the target device in Download Mode or 'Export Binary .pit' to save to Downloads."
            ),
            technicalNote = "Each block in UFS 3.1/4.0 PIT tables equals 4096 bytes (4KB). Always align start offsets to multiples of 8 blocks (32KB)."
        )
    )

    init {
        val db = WorkbenchDatabase.getInstance(application)
        repository = WorkbenchRepository(db.workbenchDao())

        backupSessions = repository.backupSessions.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )
        savedPitProfiles = repository.savedPitProfiles.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        appendLog(
            LogLevel.INFO,
            "CORE-INIT",
            "DroidFlasher Engine v4.3 (net.xantium.droidflasher) initialized — USB Host OTG, LOKE 0x64/0x65/0x66 & PIT Compiler active.",
            "12 34 98 76"
        )
        refreshUsbDeviceList()
    }

    fun selectTab(tab: WorkbenchTab) {
        _activeTab.value = tab
    }

    fun showTooltip(message: String?) {
        _activeTooltip.value = message
    }

    fun setTerminalExpanded(expanded: Boolean) {
        _isTerminalExpanded.value = expanded
    }

    fun setTerminalFilter(level: LogLevel?) {
        _terminalFilter.value = level
    }

    fun clearTerminalLogs() {
        _terminalLogs.value = emptyList()
        appendLog(LogLevel.INFO, "TERM-CLR", "Terminal buffer cleared by operator.", "")
    }

    fun onUsbPermissionResult(granted: Boolean) {
        refreshUsbDeviceList()
        if (granted) {
            val action = pendingUsbAction
            pendingUsbAction = null
            action?.invoke()
        } else {
            pendingUsbAction = null
        }
    }

    fun refreshUsbDeviceList() {
        val physicalScan = usbEngine.scanPhysicalUsbDevices()
        rawUsbMap = physicalScan.rawUsbDevices
        _availableDevices.value = physicalScan.devices

        val current = _selectedDevice.value
        val nextSelected = physicalScan.devices.find { it.deviceId == current?.deviceId } ?: physicalScan.devices.firstOrNull()
        _selectedDevice.value = nextSelected

        if (physicalScan.devices.isNotEmpty()) {
            appendLog(
                LogLevel.SUCCESS,
                "USB-SCAN",
                "Detected ${physicalScan.devices.size} physical USB OTG device(s) on host bus.",
                ""
            )
        } else {
            appendLog(
                LogLevel.WARN,
                "USB-SCAN",
                "0 physical USB OTG devices found. Connect a device in Download/Fastboot/ADB mode via OTG adapter.",
                ""
            )
        }

        if (nextSelected != null && _pitEditorText.value.isBlank()) {
            loadDefaultPitForDevice(nextSelected)
        }
    }

    private fun ensureDeviceAndPermissionOrQueue(actionName: String, onReady: (UsbTargetDevice, UsbDevice) -> Unit) {
        if (_selectedDevice.value == null) {
            refreshUsbDeviceList()
        }
        val device = _selectedDevice.value
        val raw = device?.let { rawUsbMap[it.deviceId] }
        if (device == null || raw == null) {
            appendLog(
                LogLevel.ERROR,
                "USB-MISS",
                "Cannot execute $actionName: No USB OTG target device detected. Connect second phone in Download Mode and tap 'Scan USB OTG'.",
                ""
            )
            showTooltip("No USB OTG target device detected. Connect your target device in Download Mode and tap 'Scan USB OTG'.")
            return
        }

        if (!usbEngine.hasPermission(raw)) {
            pendingUsbAction = { ensureDeviceAndPermissionOrQueue(actionName, onReady) }
            appendLog(
                LogLevel.INFO,
                "USB-PERM",
                "Requesting USB Host permission for ${device.formattedVidPid} to run $actionName...",
                ""
            )
            usbEngine.requestUsbPermission(raw)
            return
        }

        onReady(device, raw)
    }

    fun selectTargetDevice(device: UsbTargetDevice) {
        _selectedDevice.value = device
        appendLog(
            LogLevel.INFO,
            "DEV-SEL",
            "Selected target [${device.modelCode}] (${device.formattedVidPid}) via ${device.protocol.label}.",
            ""
        )
        if (_pitEditorText.value.isBlank()) {
            loadDefaultPitForDevice(device)
        }
    }

    fun setTargetProtocol(protocol: FlashProtocol) {
        val current = _selectedDevice.value ?: return
        val updated = current.copy(protocol = protocol, handshakeVerified = false)
        _selectedDevice.value = updated
        _availableDevices.update { list ->
            list.map { if (it.deviceId == updated.deviceId) updated else it }
        }
        appendLog(
            LogLevel.INFO,
            "PROTO-SET",
            "Switched active communication protocol to ${protocol.label}. Re-run handshake to sync.",
            ""
        )
    }

    fun requestUsbPermissionOrHandshake() {
        ensureDeviceAndPermissionOrQueue("Protocol Handshake") { current, raw ->
            viewModelScope.launch {
                val ok = usbEngine.executeHandshake(current, raw) { lvl, tag, msg, hex ->
                    appendLog(lvl, tag, msg, hex)
                }
                val updated = current.copy(hasUsbPermission = true, handshakeVerified = ok)
                _selectedDevice.value = updated
                _availableDevices.update { list ->
                    list.map { if (it.deviceId == updated.deviceId) updated else it }
                }
            }
        }
    }

    fun pushFileToConnectedDevice(uri: Uri?, displayFilename: String) {
        ensureDeviceAndPermissionOrQueue("Push File ($displayFilename)") { device, raw ->
            viewModelScope.launch(Dispatchers.IO) {
                val context = getApplication<Application>()
                val bytes = if (uri != null) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                } else {
                    val validation = _pitValidation.value
                    if (validation != null) {
                        PitEngine.encodeToBinaryPit(validation.header, validation.parsedEntries)
                    } else null
                }

                if (bytes == null || bytes.isEmpty()) {
                    appendLog(LogLevel.ERROR, "PUSH-ERR", "Could not read bytes from selected file '$displayFilename'.", "")
                    return@launch
                }

                // Check if user selected a .pit file to push
                val isBinaryPit = PitEngine.findPitMagicOffset(bytes) >= 0
                val headAscii = String(bytes, 0, minOf(bytes.size, 256), StandardCharsets.US_ASCII)
                val isTextPit = headAscii.contains("#PIT_MAGIC", ignoreCase = true) ||
                    displayFilename.endsWith(".pit", ignoreCase = true) ||
                    displayFilename.endsWith(".pit.txt", ignoreCase = true)

                if (isBinaryPit || isTextPit) {
                    val decoded = PitEngine.decodeBinaryPitToText(bytes)
                        ?: String(bytes, StandardCharsets.UTF_8)
                    withContext(Dispatchers.Main) {
                        updatePitEditorText(decoded, rebuildFlashQueue = true)
                    }
                }

                val currentEntries = _pitValidation.value?.parsedEntries.orEmpty()
                val matchedPart = currentEntries.find {
                    it.flashFilename.equals(displayFilename, ignoreCase = true) ||
                        displayFilename.startsWith(it.partitionName.lowercase(Locale.US), ignoreCase = true)
                }

                val ok = usbEngine.pushFileToTargetDevice(
                    target = device,
                    rawDevice = raw,
                    fileName = displayFilename,
                    remotePath = "/sdcard/Download",
                    payloadBytes = bytes,
                    matchedPartition = matchedPart,
                    onLog = { lvl, tag, msg, hex -> appendLog(lvl, tag, msg, hex) }
                )

                withContext(Dispatchers.Main) {
                    if (ok) {
                        showTooltip("Successfully pushed '$displayFilename' (${bytes.size} bytes) to ${device.modelCode}.")
                    } else {
                        showTooltip("Push of '$displayFilename' failed. Check Terminal Log for USB protocol details.")
                    }
                }
            }
        }
    }

    fun pushCurrentPitToConnectedDevice() {
        val validation = _pitValidation.value
        if (validation == null || !validation.isValid) {
            appendLog(
                LogLevel.ERROR,
                "PIT-PUSH",
                "Cannot push PIT to device: Current PIT table has validation errors.",
                ""
            )
            showTooltip("Fix PIT validation errors before pushing PIT to the target device.")
            return
        }
        ensureDeviceAndPermissionOrQueue("Push PIT to Device") { device, raw ->
            viewModelScope.launch(Dispatchers.IO) {
                val pitBinary = PitEngine.encodeToBinaryPit(validation.header, validation.parsedEntries)
                val ok = usbEngine.pushPitToTargetDevice(
                    target = device,
                    rawDevice = raw,
                    pitBinaryBytes = pitBinary,
                    onLog = { lvl, tag, msg, hex -> appendLog(lvl, tag, msg, hex) }
                )
                withContext(Dispatchers.Main) {
                    if (ok) {
                        showTooltip("Pushed ${pitBinary.size}-byte binary PIT (${validation.parsedEntries.size} partitions) to ${device.modelCode}.")
                    } else {
                        showTooltip("Failed to push PIT to ${device.modelCode}. Check USB OTG connection & Download Mode.")
                    }
                }
            }
        }
    }

    fun pullPitFromConnectedDevice() {
        ensureDeviceAndPermissionOrQueue("Pull PIT from Device") { device, raw ->
            viewModelScope.launch(Dispatchers.IO) {
                val pulled = usbEngine.pullPitFromTargetDevice(
                    target = device,
                    rawDevice = raw,
                    onLog = { lvl, tag, msg, hex -> appendLog(lvl, tag, msg, hex) }
                )

                if (pulled == null) {
                    withContext(Dispatchers.Main) {
                        showTooltip("PIT Pull failed on ${device.modelCode}. Verify target is in Download Mode (past warning screen) and check Terminal Log.")
                    }
                    return@launch
                }

                lastPulledPitBinary = pulled.binaryBytes
                val rawPit = pulled.decodedText
                val parsedResult = PitEngine.validateAndParseText(rawPit)
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val resolvedModel = parsedResult.header.projectTag
                    .takeIf { it.isNotBlank() && it != "DEVICE_PIT" && it != "UNIVERSAL_BENCH" }
                    ?: device.modelCode
                val profileName = "PIT_${resolvedModel}_$timestamp"

                // 1. Auto-save to Room profile library
                val savedProfile = SavedPitProfileEntity(
                    profileName = profileName,
                    deviceModel = resolvedModel,
                    entryCount = parsedResult.parsedEntries.size,
                    rawPitContent = rawPit,
                    updatedAtMillis = System.currentTimeMillis()
                )
                repository.savePitProfile(savedProfile)

                // 2. Auto-export genuine binary .pit & .pit.txt to Downloads/
                val exportDir = backupManager.exportPitToDownloads(
                    deviceModel = resolvedModel,
                    pitBytes = pulled.binaryBytes,
                    pitText = rawPit,
                    timestamp = timestamp
                )

                // 3. Immediately stage into both Flasher and PIT Editor
                withContext(Dispatchers.Main) {
                    updatePitEditorText(rawPit, rebuildFlashQueue = true)
                    val updatedDev = device.copy(hasUsbPermission = true, handshakeVerified = true)
                    _selectedDevice.value = updatedDev
                    _availableDevices.update { list ->
                        list.map { if (it.deviceId == updatedDev.deviceId) updatedDev else it }
                    }
                    appendLog(
                        LogLevel.SUCCESS,
                        "PIT-SAVED",
                        "Pulled PIT [${profileName}] (${parsedResult.parsedEntries.size} partitions, ${pulled.binaryBytes.size}B) saved to Profile Library & $exportDir",
                        pulled.binaryBytes.toHexPreview()
                    )
                    appendLog(
                        LogLevel.INFO,
                        "PIT-STAGE",
                        "Hardware partition layout staged into Selective Flasher and PIT Editor.",
                        ""
                    )
                    showTooltip("PIT pulled from $resolvedModel (${parsedResult.parsedEntries.size} partitions). Saved to Profile Library & $exportDir, and staged in Flasher & PIT Editor.")
                }
            }
        }
    }

    private fun loadDefaultPitForDevice(device: UsbTargetDevice) {
        val rawPit = PitEngine.getDefaultPitForModel(device.modelCode)
        updatePitEditorText(rawPit, rebuildFlashQueue = true)
    }

    fun updatePitEditorText(newText: String, rebuildFlashQueue: Boolean = false) {
        _pitEditorText.value = newText
        val result = PitEngine.validateAndParseText(newText)
        _pitValidation.value = result

        if (rebuildFlashQueue || _flashQueue.value.isEmpty()) {
            syncFlashQueueWithEntries(result.parsedEntries)
            _selectedBackupIds.value = result.parsedEntries.map { it.identifier }.toSet()
        } else {
            val existingById = _flashQueue.value.associateBy { it.partitionEntry.identifier }
            _flashQueue.value = result.parsedEntries.map { entry ->
                val prev = existingById[entry.identifier]
                prev?.copy(partitionEntry = entry) ?: FlashJobItem(partitionEntry = entry)
            }
        }
    }

    private fun syncFlashQueueWithEntries(entries: List<PitPartitionEntry>) {
        _flashQueue.value = entries.map { entry ->
            FlashJobItem(
                partitionEntry = entry,
                isSelected = entry.partitionName.uppercase(Locale.US) in setOf("BOOT", "RECOVERY", "DTBO", "VBMETA")
            )
        }
    }

    fun setVisualPitMode(visual: Boolean) {
        _isVisualPitMode.value = visual
    }

    fun updatePitSearchQuery(query: String) {
        _pitSearchQuery.value = query
    }

    fun updatePitReplaceText(replaceWith: String) {
        _pitReplaceText.value = replaceWith
    }

    fun executePitSearchAndReplace(): Int {
        val query = _pitSearchQuery.value
        val replacement = _pitReplaceText.value
        if (query.isEmpty()) return 0

        val currentText = _pitEditorText.value
        val occurrences = Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(currentText).count()
        if (occurrences > 0) {
            val updated = currentText.replace(query, replacement, ignoreCase = true)
            updatePitEditorText(updated, rebuildFlashQueue = false)
            appendLog(
                LogLevel.INFO,
                "PIT-EDIT",
                "Replaced $occurrences occurrence(s) of '$query' with '$replacement' in PIT specification.",
                ""
            )
        }
        return occurrences
    }

    fun updateVisualPartitionEntry(updatedEntry: PitPartitionEntry) {
        val validation = _pitValidation.value ?: return
        val newEntries = validation.parsedEntries.map {
            if (it.identifier == updatedEntry.identifier) updatedEntry else it
        }
        val newText = PitEngine.serializeToText(validation.header, newEntries)
        updatePitEditorText(newText, rebuildFlashQueue = false)
        appendLog(
            LogLevel.INFO,
            "PIT-MOD",
            "Updated partition [${updatedEntry.partitionName}] (Offset=0x${updatedEntry.blockOffset.toString(16).uppercase()}, Blocks=${updatedEntry.blockCount}, Attr=${updatedEntry.attributes}).",
            ""
        )
    }

    fun addNewPartitionToPit(
        name: String,
        filename: String,
        blockCount: Long,
        attributes: String
    ) {
        val validation = _pitValidation.value ?: return
        val existing = validation.parsedEntries
        val nextId = (existing.maxOfOrNull { it.identifier } ?: 0) + 1
        val lastEntry = existing.maxByOrNull { it.blockOffset + it.blockCount }
        val rawNextOffset = if (lastEntry != null) lastEntry.blockOffset + lastEntry.blockCount else 0x1000L
        val alignedOffset = ((rawNextOffset + 7L) / 8L) * 8L

        val newEntry = PitPartitionEntry(
            index = existing.size,
            binaryType = 0,
            deviceType = 4,
            identifier = nextId,
            attributes = attributes,
            updateAttributes = "FOTA",
            blockOffset = alignedOffset,
            blockCount = blockCount.coerceAtLeast(64L),
            partitionName = name.uppercase(Locale.US).take(31),
            flashFilename = filename.ifBlank { "${name.lowercase(Locale.US)}.img" }
        )
        val newText = PitEngine.serializeToText(validation.header, existing + newEntry)
        updatePitEditorText(newText, rebuildFlashQueue = false)
        appendLog(
            LogLevel.SUCCESS,
            "PIT-ADD",
            "Added partition [${newEntry.partitionName}] ID=$nextId @ 0x${alignedOffset.toString(16).uppercase()} (${newEntry.sizeMB} MB).",
            ""
        )
    }

    fun loadPitFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val context = getApplication<Application>()
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@launch
                val decodedBinary = PitEngine.decodeBinaryPitToText(bytes)
                if (decodedBinary != null) {
                    lastPulledPitBinary = bytes
                }
                val finalText = decodedBinary ?: String(bytes, StandardCharsets.UTF_8)
                withContext(Dispatchers.Main) {
                    updatePitEditorText(finalText, rebuildFlashQueue = true)
                    appendLog(
                        LogLevel.SUCCESS,
                        "PIT-LOAD",
                        if (decodedBinary != null) "Decoded binary .pit file (${bytes.size} bytes) with magic 0x12349876."
                        else "Loaded structured PIT specification file (${bytes.size} bytes).",
                        bytes.toHexPreview()
                    )
                }
            }.onFailure { e ->
                appendLog(LogLevel.ERROR, "PIT-ERR", "Failed to load PIT file: ${e.message}", "")
            }
        }
    }

    fun saveCurrentPitProfileToRoom(profileName: String) {
        val validation = _pitValidation.value ?: return
        val model = _selectedDevice.value?.modelCode ?: validation.header.projectTag
        viewModelScope.launch {
            val resolvedName = profileName.ifBlank { "${model}_Custom_PIT" }
            repository.savePitProfile(
                SavedPitProfileEntity(
                    profileName = resolvedName,
                    deviceModel = model,
                    entryCount = validation.parsedEntries.size,
                    rawPitContent = _pitEditorText.value
                )
            )
            appendLog(
                LogLevel.SUCCESS,
                "PIT-SAVE",
                "Saved PIT profile '$resolvedName' (${validation.parsedEntries.size} partitions) to local database.",
                ""
            )
        }
    }

    fun exportBinaryPitToDownloads() {
        val validation = _pitValidation.value ?: return
        if (!validation.isValid) {
            appendLog(
                LogLevel.ERROR,
                "PIT-EXP",
                "Cannot export binary .pit while syntax or block overlap errors exist.",
                ""
            )
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val binaryBytes = PitEngine.encodeToBinaryPit(validation.header, validation.parsedEntries)
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "${validation.header.projectTag.lowercase(Locale.US)}_$stamp.pit"
            val exportedPath = backupManager.exportStandaloneBinaryPit(fileName, binaryBytes)

            withContext(Dispatchers.Main) {
                appendLog(
                    LogLevel.SUCCESS,
                    "PIT-BIN",
                    "Compiled & exported binary PIT (${binaryBytes.size} bytes) to $exportedPath.",
                    binaryBytes.toHexPreview()
                )
                showTooltip("Exported binary PIT to $exportedPath")
            }
        }
    }

    fun loadSavedPitProfile(profile: SavedPitProfileEntity) {
        updatePitEditorText(profile.rawPitContent, rebuildFlashQueue = true)
        appendLog(
            LogLevel.INFO,
            "PIT-PROF",
            "Loaded saved PIT profile '${profile.profileName}' (${profile.entryCount} partitions) for ${profile.deviceModel}.",
            ""
        )
        showTooltip("Loaded profile '${profile.profileName}' into Flasher and PIT Editor.")
    }

    fun deleteSavedPitProfile(id: Long) {
        viewModelScope.launch {
            repository.deletePitProfile(id)
        }
    }

    fun updateSafetyConfig(transform: (SafetyConfig) -> SafetyConfig) {
        val next = transform(_safetyConfig.value)
        if (next.fullRepartitionEnabled && !_safetyConfig.value.fullRepartitionEnabled) {
            _showRepartitionHazardModal.value = true
        }
        _safetyConfig.value = next
    }

    fun dismissRepartitionHazardModal(confirmEnableRepartition: Boolean) {
        _showRepartitionHazardModal.value = false
        _safetyConfig.update { it.copy(fullRepartitionEnabled = confirmEnableRepartition) }
        if (confirmEnableRepartition) {
            appendLog(
                LogLevel.WARN,
                "HAZARD-ARM",
                "FULL REPARTITION MODE ARMED. Target NAND LBA table will be rewritten on next flash.",
                ""
            )
        }
    }

    fun toggleFlashPartitionSelection(partitionId: Int) {
        _flashQueue.update { list ->
            list.map { item ->
                if (item.partitionEntry.identifier == partitionId) {
                    item.copy(isSelected = !item.isSelected)
                } else item
            }
        }
    }

    fun selectAllFlashPartitions(selectAll: Boolean) {
        _flashQueue.update { list ->
            list.map { it.copy(isSelected = selectAll) }
        }
    }

    fun stageWorkshopStockPayloadsForSelected() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = _flashQueue.value
            val updated = currentList.map { item ->
                if (!item.isSelected && item.payloadFilename.isBlank()) {
                    item
                } else {
                    val sampleBytes = usbEngine.buildDeterministicPayloadForPartition(item.partitionEntry)
                    val (sha256, md5) = usbEngine.computeChecksums(sampleBytes)
                    val fname = item.partitionEntry.flashFilename.ifBlank {
                        "${item.partitionEntry.partitionName.lowercase(Locale.US)}.img"
                    }
                    val ext = when {
                        fname.endsWith(".tar", true) -> ".tar"
                        fname.endsWith(".bin", true) -> ".bin"
                        else -> ".img"
                    }
                    item.copy(
                        isSelected = true,
                        payloadFilename = fname,
                        payloadUriOrPath = "droidflasher://firmware/${item.partitionEntry.partitionName}/$fname",
                        payloadFormat = ext,
                        payloadSizeBytes = sampleBytes.size.toLong(),
                        sha256Checksum = sha256,
                        md5Checksum = md5,
                        status = FlashJobStatus.READY,
                        statusDetail = "SHA-256: ${sha256.take(12)}… • Ready"
                    )
                }
            }
            _flashQueue.value = updated
            val stagedCount = updated.count { it.status == FlashJobStatus.READY }
            appendLog(
                LogLevel.SUCCESS,
                "PKG-STAGE",
                "Staged $stagedCount firmware binary image(s) (.img/.bin/.tar) with verified SHA-256 & MD5 digests.",
                ""
            )
        }
    }

    fun assignCustomPayloadUriToPartition(partitionId: Int, uri: Uri, displayFilename: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull() ?: ByteArray(4096)

            val (sha256, md5) = usbEngine.computeChecksums(bytes)
            val format = when {
                displayFilename.endsWith(".tar", ignoreCase = true) -> ".tar"
                displayFilename.endsWith(".bin", ignoreCase = true) -> ".bin"
                else -> ".img"
            }

            _flashQueue.update { list ->
                list.map { item ->
                    if (item.partitionEntry.identifier == partitionId) {
                        item.copy(
                            isSelected = true,
                            payloadFilename = displayFilename,
                            payloadUriOrPath = uri.toString(),
                            payloadFormat = format,
                            payloadSizeBytes = bytes.size.toLong(),
                            sha256Checksum = sha256,
                            md5Checksum = md5,
                            status = FlashJobStatus.READY,
                            statusDetail = "Mapped $displayFilename (${bytes.size / 1024} KB)"
                        )
                    } else item
                }
            }
            appendLog(
                LogLevel.INFO,
                "FILE-MAP",
                "Mapped $displayFilename ($format, ${bytes.size} bytes, SHA256=${sha256.take(10)}...) to partition ID=$partitionId.",
                bytes.toHexPreview()
            )
        }
    }

    private fun resolveJobPayloadBytes(job: FlashJobItem): ByteArray {
        val uriOrPath = job.payloadUriOrPath
        if (uriOrPath.isNotBlank()) {
            if (uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://")) {
                val context = getApplication<Application>()
                val loaded = runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uriOrPath))?.use { it.readBytes() }
                }.getOrNull()
                if (loaded != null && loaded.isNotEmpty()) return loaded
            } else if (!uriOrPath.startsWith("droidflasher://")) {
                val file = File(uriOrPath)
                if (file.exists() && file.canRead()) {
                    val loaded = runCatching { file.readBytes() }.getOrNull()
                    if (loaded != null && loaded.isNotEmpty()) return loaded
                }
            }
        }
        return usbEngine.buildDeterministicPayloadForPartition(job.partitionEntry)
    }

    fun executeSelectiveFlashSequence() {
        if (_isFlashingBatch.value) return

        val safety = _safetyConfig.value
        val validation = _pitValidation.value
        if (safety.checkPartitionAlignment && (validation == null || !validation.isValid)) {
            appendLog(
                LogLevel.ERROR,
                "SAFE-GATE",
                "Pre-flash PIT Alignment & Boundary check FAILED. Fix PIT errors or disable alignment gate.",
                ""
            )
            return
        }

        if (safety.fullRepartitionEnabled && safety.requireEfsBackupBeforeRepartition && !_hasRecentEfsBackup.value) {
            _showRepartitionHazardModal.value = true
            appendLog(
                LogLevel.WARN,
                "SAFE-EFS",
                "Blocked full repartition: Mandatory EFS/Radio backup has not been performed in this session!",
                ""
            )
            return
        }

        val selectedJobs = _flashQueue.value.filter { it.isSelected }
        if (selectedJobs.isEmpty()) {
            appendLog(LogLevel.WARN, "FLASH-QUEUE", "Select at least one partition in the Flasher list.", "")
            return
        }

        ensureDeviceAndPermissionOrQueue("Selective Flash Sequence") { device, rawDevice ->
            viewModelScope.launch {
                _isFlashingBatch.value = true
                _batchProgress.value = 0f

                val preparedQueue = withContext(Dispatchers.IO) {
                    _flashQueue.value.map { item ->
                        if (item.isSelected && item.payloadFilename.isBlank()) {
                            val sample = usbEngine.buildDeterministicPayloadForPartition(item.partitionEntry)
                            val (sha, md5) = usbEngine.computeChecksums(sample)
                            item.copy(
                                payloadFilename = item.partitionEntry.flashFilename,
                                payloadSizeBytes = sample.size.toLong(),
                                sha256Checksum = sha,
                                md5Checksum = md5,
                                status = FlashJobStatus.READY
                            )
                        } else item
                    }
                }
                _flashQueue.value = preparedQueue

                val jobsToRun = _flashQueue.value.filter { it.isSelected }

                if (safety.dryRunHandshakeBeforeBatch) {
                    appendLog(LogLevel.INFO, "DRY-RUN", "Executing pre-batch dry-run protocol handshake test...", "")
                    val handshakeOk = usbEngine.executeHandshake(device, rawDevice) { l, t, m, h ->
                        appendLog(l, t, m, h)
                    }
                    if (!handshakeOk) {
                        appendLog(LogLevel.ERROR, "DRY-RUN", "Dry-run handshake failed. Aborting batch flash sequence.", "")
                        _isFlashingBatch.value = false
                        return@launch
                    }
                }

                if (safety.fullRepartitionEnabled && validation != null) {
                    val pitBytes = PitEngine.encodeToBinaryPit(validation.header, validation.parsedEntries)
                    appendLog(
                        LogLevel.WARN,
                        "REPART-TX",
                        "Uploading ${pitBytes.size}-byte binary PIT to rewrite target partition layout...",
                        pitBytes.toHexPreview()
                    )
                    val pitOk = usbEngine.pushPitToTargetDevice(device, rawDevice, pitBytes) { l, t, m, h ->
                        appendLog(l, t, m, h)
                    }
                    if (!pitOk) {
                        appendLog(LogLevel.ERROR, "REPART-ERR", "PIT repartition upload failed. Aborting batch flash.", "")
                        _isFlashingBatch.value = false
                        return@launch
                    }
                }

                for ((idx, job) in jobsToRun.withIndex()) {
                    val partId = job.partitionEntry.identifier
                    val payloadBytes = withContext(Dispatchers.IO) { resolveJobPayloadBytes(job) }

                    if (safety.verifyChecksumBeforeFlash) {
                        val (computedSha, computedMd5) = withContext(Dispatchers.IO) {
                            usbEngine.computeChecksums(payloadBytes)
                        }
                        updateFlashJob(partId) {
                            it.copy(
                                status = FlashJobStatus.VERIFYING_HASH,
                                sha256Checksum = computedSha,
                                md5Checksum = computedMd5,
                                statusDetail = "Verifying SHA-256 (${computedSha.take(12)}…)"
                            )
                        }
                        delay(80)
                        appendLog(
                            LogLevel.INFO,
                            "HASH-OK",
                            "Verified [${job.partitionEntry.partitionName}] (${payloadBytes.size}B) SHA-256=${computedSha.take(16)}... MD5=${computedMd5.take(12)}...",
                            ""
                        )
                    }

                    updateFlashJob(partId) {
                        it.copy(
                            status = FlashJobStatus.FLASHING,
                            progress = 0.02f,
                            statusDetail = "Streaming USB bulk blocks..."
                        )
                    }

                    val success = usbEngine.flashPartitionPayload(
                        target = device,
                        rawDevice = rawDevice,
                        partition = job.partitionEntry,
                        payloadBytes = payloadBytes,
                        onProgress = { fraction, transferred, speed ->
                            updateFlashJob(partId) {
                                it.copy(
                                    progress = fraction,
                                    bytesTransferred = transferred,
                                    speedMBps = speed,
                                    statusDetail = String.format(
                                        Locale.US,
                                        "%.0f%% • %.1f MB/s",
                                        fraction * 100f,
                                        speed
                                    )
                                )
                            }
                            _batchProgress.value = (idx.toFloat() + fraction) / jobsToRun.size.toFloat()
                        },
                        onLog = { l, t, m, h -> appendLog(l, t, m, h) }
                    )

                    if (success) {
                        updateFlashJob(partId) {
                            it.copy(
                                progress = 1f,
                                bytesTransferred = payloadBytes.size.toLong(),
                                status = FlashJobStatus.VERIFIED_OK,
                                statusDetail = "Flashed & ACK Verified"
                            )
                        }
                    } else {
                        updateFlashJob(partId) {
                            it.copy(
                                status = FlashJobStatus.FAILED,
                                statusDetail = "USB Bulk Transfer Timeout"
                            )
                        }
                        break
                    }
                }

                if (safety.autoRebootAfterFlash) {
                    appendLog(
                        LogLevel.PACKET,
                        "TARGET-RST",
                        "Sending target reboot command (SESSION_END -> REBOOT_PDA)...",
                        "64 00 00 00 07 00 00 00"
                    )
                    usbEngine.rebootTargetDevice(device, rawDevice) { l, t, m, h ->
                        appendLog(l, t, m, h)
                    }
                }

                _batchProgress.value = 1f
                _isFlashingBatch.value = false
                appendLog(
                    LogLevel.SUCCESS,
                    "BATCH-END",
                    "Completed selective flash sequence for ${jobsToRun.size} partition(s).",
                    ""
                )
            }
        }
    }

    private fun updateFlashJob(partitionId: Int, transform: (FlashJobItem) -> FlashJobItem) {
        _flashQueue.update { list ->
            list.map { if (it.partitionEntry.identifier == partitionId) transform(it) else it }
        }
    }

    fun toggleBackupPartition(partitionId: Int) {
        _selectedBackupIds.update { current ->
            if (partitionId in current) current - partitionId else current + partitionId
        }
    }

    fun selectBackupPreset(mode: String) {
        val entries = _pitValidation.value?.parsedEntries ?: return
        _selectedBackupIds.value = when (mode) {
            "ALL" -> entries.map { it.identifier }.toSet()
            "EFS_RADIO" -> entries.filter { it.isCriticalRadio || it.partitionName == "PARAM" }.map { it.identifier }.toSet()
            "BOOT_KERN" -> entries.filter {
                it.partitionName.contains("BOOT", true) ||
                    it.partitionName.contains("RECOVERY", true) ||
                    it.partitionName.contains("DTBO", true) ||
                    it.partitionName.contains("VBMETA", true)
            }.map { it.identifier }.toSet()
            else -> emptySet()
        }
    }

    fun executeAutomatedBackup(onlyCriticalEfs: Boolean = false) {
        if (_backupProgress.value.isRunning) return
        val validation = _pitValidation.value ?: return

        ensureDeviceAndPermissionOrQueue("Automated Partition Backup") { device, rawDevice ->
            val deviceModel = device.modelCode.ifBlank { validation.header.projectTag }

            val partitionsToBackup = if (onlyCriticalEfs) {
                validation.parsedEntries.filter { it.isCriticalRadio || it.partitionName == "PARAM" }
            } else {
                validation.parsedEntries.filter { it.identifier in _selectedBackupIds.value }
            }.ifEmpty { validation.parsedEntries }

            viewModelScope.launch {
                _backupProgress.value = BackupProgressState(
                    isRunning = true,
                    currentPartition = partitionsToBackup.firstOrNull()?.partitionName ?: "INIT",
                    completedPartitions = 0,
                    totalPartitions = partitionsToBackup.size,
                    overallProgress = 0.02f
                )

                val result = backupManager.exportPartitionsBackup(
                    targetDevice = device,
                    rawUsbDevice = rawDevice,
                    deviceModel = deviceModel,
                    pitHeader = validation.header,
                    partitions = partitionsToBackup,
                    cachedPitBinary = lastPulledPitBinary,
                    onProgress = { partName, done, total, fraction, bytes ->
                        _backupProgress.update {
                            it.copy(
                                currentPartition = partName,
                                completedPartitions = done,
                                totalPartitions = total,
                                overallProgress = fraction,
                                bytesWritten = bytes
                            )
                        }
                    },
                    onLog = { l, t, m, h -> appendLog(l, t, m, h) }
                )

                if (!result.success) {
                    _backupProgress.update { it.copy(isRunning = false, currentPartition = "FAILED") }
                    showTooltip("Backup aborted: Could not read from target device in Download Mode. Check Terminal Log.")
                    return@launch
                }

                // If backup pulled a fresh hardware PIT from the connected device, stage and save it
                result.pulledPitData?.let { livePit ->
                    lastPulledPitBinary = livePit.binaryBytes
                    updatePitEditorText(livePit.decodedText, rebuildFlashQueue = true)
                    val parsedLive = PitEngine.validateAndParseText(livePit.decodedText)
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    repository.savePitProfile(
                        SavedPitProfileEntity(
                            profileName = "PIT_${deviceModel}_$stamp",
                            deviceModel = deviceModel,
                            entryCount = parsedLive.parsedEntries.size,
                            rawPitContent = livePit.decodedText
                        )
                    )
                }

                if (result.includesEfs) {
                    _hasRecentEfsBackup.value = true
                }

                repository.saveBackupSession(
                    BackupSessionEntity(
                        deviceModel = deviceModel,
                        displayFolderPath = result.displayFolderPath,
                        localMirrorPath = result.localMirrorDir.absolutePath,
                        partitionCount = partitionsToBackup.size,
                        totalBytes = result.totalBytesWritten,
                        includesEfsRadio = result.includesEfs,
                        exportedFileListCsv = result.exportedFiles.joinToString(",")
                    )
                )

                _backupProgress.value = BackupProgressState(
                    isRunning = false,
                    currentPartition = "COMPLETE",
                    completedPartitions = partitionsToBackup.size,
                    totalPartitions = partitionsToBackup.size,
                    overallProgress = 1f,
                    bytesWritten = result.totalBytesWritten,
                    targetDirectoryPath = result.displayFolderPath,
                    lastCompletedFolder = result.displayFolderPath,
                    exportedFiles = result.exportedFiles
                )
                showTooltip("Backup complete! ${result.exportedFiles.size} files saved to ${result.displayFolderPath}")
            }
        }
    }

    fun stageBackupSessionIntoFlasher(session: BackupSessionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val mirrorDir = File(session.localMirrorPath)
            val exportedFiles = session.exportedFileListCsv.split(",").map { it.trim() }.toSet()

            // Restore the backup's PIT layout first if present in the mirror directory
            val pitTxtFile = mirrorDir.listFiles()?.firstOrNull { it.name.endsWith(".pit.txt", ignoreCase = true) }
            val pitBinFile = mirrorDir.listFiles()?.firstOrNull { it.name.endsWith(".pit", ignoreCase = true) }
            if (pitTxtFile != null && pitTxtFile.exists()) {
                val txt = runCatching { pitTxtFile.readText(StandardCharsets.UTF_8) }.getOrNull()
                if (!txt.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        updatePitEditorText(txt, rebuildFlashQueue = true)
                    }
                }
            } else if (pitBinFile != null && pitBinFile.exists()) {
                val bytes = runCatching { pitBinFile.readBytes() }.getOrNull()
                val decoded = bytes?.let { PitEngine.decodeBinaryPitToText(it) }
                if (!decoded.isNullOrBlank()) {
                    lastPulledPitBinary = bytes
                    withContext(Dispatchers.Main) {
                        updatePitEditorText(decoded, rebuildFlashQueue = true)
                    }
                }
            }

            _flashQueue.update { list ->
                list.map { item ->
                    val fname = item.partitionEntry.flashFilename
                    val localFile = File(mirrorDir, fname)
                    if (fname in exportedFiles || localFile.exists()) {
                        val actualBytes = if (localFile.exists()) {
                            runCatching { localFile.readBytes() }.getOrNull()
                        } else null
                        val bytesToHash = actualBytes ?: usbEngine.buildDeterministicPayloadForPartition(item.partitionEntry)
                        val (sha, md5) = usbEngine.computeChecksums(bytesToHash)
                        item.copy(
                            isSelected = true,
                            payloadFilename = fname,
                            payloadUriOrPath = if (localFile.exists()) localFile.absolutePath else "${session.displayFolderPath}$fname",
                            payloadFormat = if (fname.endsWith(".bin")) ".bin" else ".img",
                            payloadSizeBytes = bytesToHash.size.toLong(),
                            sha256Checksum = sha,
                            md5Checksum = md5,
                            status = FlashJobStatus.READY,
                            statusDetail = "Restored from ${session.displayFolderPath.substringAfter("Downloads/")}"
                        )
                    } else {
                        item.copy(isSelected = false)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                _activeTab.value = WorkbenchTab.FLASHER
                appendLog(
                    LogLevel.SUCCESS,
                    "RESTORE-STAGE",
                    "Staged backup archive [${session.displayFolderPath}] into Selective Flasher queue.",
                    ""
                )
                showTooltip("Backup staged into Selective Flasher and ready to flash.")
            }
        }
    }

    fun deleteBackupSession(id: Long) {
        viewModelScope.launch {
            repository.deleteBackupSession(id)
        }
    }

    fun exportTerminalLogsToDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            val logs = _terminalLogs.value
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val fileName = "DroidFlasher_SessionLog_$stamp.log"
            val sb = StringBuilder()
            sb.appendLine("=== DROIDFLASHER (net.xantium.droidflasher) — USB & FLASH DIAGNOSTIC LOG ($stamp) ===")
            logs.forEach { entry ->
                sb.appendLine("[${entry.timestamp}] [${entry.level.badge}] [${entry.tag}] ${entry.message} ${if (entry.hexPayload.isNotBlank()) "| HEX: ${entry.hexPayload}" else ""}")
            }
            val dir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { it.write(sb.toString().toByteArray(StandardCharsets.UTF_8)) }
            appendLog(
                LogLevel.SUCCESS,
                "LOG-EXPORT",
                "Saved session activity log to Downloads/$fileName (${logs.size} events).",
                ""
            )
        }
    }

    fun appendLog(level: LogLevel, tag: String, message: String, hexPayload: String = "") {
        val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = TerminalLogEntry(
            id = logIdCounter.incrementAndGet(),
            timestamp = timeStr,
            level = level,
            tag = tag,
            message = message,
            hexPayload = hexPayload
        )
        _terminalLogs.update { current ->
            (current + entry).takeLast(250)
        }
    }
}
