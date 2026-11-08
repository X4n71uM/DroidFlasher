package net.xantium.droidflasher.model

enum class FlashProtocol(val label: String, val shortCode: String, val defaultPortDesc: String) {
    ODIN_LOKE("Heimdall / Odin (LOKE)", "LOKE", "Samsung Download Mode (VID:04E8)"),
    FASTBOOT("Android Fastboot", "FB", "Bootloader Fastboot Interface (VID:18D1)"),
    ADB_LOADER("ADB Push / Sideload / EDL", "ADB", "Bulk SYNC Push & Recovery Bridge")
}

data class UsbTargetDevice(
    val deviceId: Int,
    val vendorId: Int,
    val productId: Int,
    val productName: String,
    val modelCode: String,
    val serialNumber: String,
    val protocol: FlashProtocol,
    val interfaceCount: Int,
    val bulkInAddress: String,
    val bulkOutAddress: String,
    val maxPacketSize: Int,
    val hasUsbPermission: Boolean,
    val handshakeVerified: Boolean,
    val isSimulated: Boolean = false,
    val bootloaderStatus: String = "LOCKED (KG: CHECKING)",
    val storageType: String = "UFS 4.0 (4096B Sector)"
) {
    val formattedVidPid: String
        get() = String.format("%04X:%04X", vendorId, productId)
}

data class PitHeader(
    val magic: Long = 0x12349876L,
    val entryCount: Int,
    val socTag: String,
    val projectTag: String,
    val lunCount: Int = 6
)

data class PitPartitionEntry(
    val index: Int,
    val binaryType: Int,       // 0 = AP (Application Processor), 1 = CP (Modem)
    val deviceType: Int,       // 2 = eMMC, 4 = UFS
    val identifier: Int,       // Unique partition ID
    val attributes: String,    // RW, RO, STL, BML
    val updateAttributes: String, // FOTA, SECURE, RAW
    val blockOffset: Long,     // Starting LBA / Block offset
    val blockCount: Long,      // Size in 4KB blocks
    val fileOffset: Long = 0L,
    val partitionName: String, // e.g., BOOT, RECOVERY, SUPER, EFS
    val flashFilename: String, // e.g., boot.img, super.img, efs.img
    val fotaFilename: String = "steady.bin"
) {
    val sizeMB: Double
        get() = (blockCount * 4096.0) / (1024.0 * 1024.0)

    val isCriticalRadio: Boolean
        get() = partitionName.uppercase() in setOf(
            "EFS", "SEC_EFS", "MODEM", "RADIO", "NVRAM", "NVDATA", "PERSIST", "CPEFS"
        )

    val isBootloaderCritical: Boolean
        get() = partitionName.uppercase() in setOf(
            "BL", "SBOOT", "ABL", "XBL", "LK", "PARAM", "UP_PARAM", "TZSW"
        )
}

enum class ValidationSeverity {
    ERROR,
    WARNING,
    INFO
}

data class PitValidationIssue(
    val lineNumber: Int,
    val severity: ValidationSeverity,
    val partitionName: String,
    val message: String
)

data class PitValidationResult(
    val isValid: Boolean,
    val magicValid: Boolean,
    val header: PitHeader,
    val parsedEntries: List<PitPartitionEntry>,
    val issues: List<PitValidationIssue>,
    val totalAllocatedMB: Double,
    val hasOverlap: Boolean,
    val isAligned4K: Boolean
)

enum class FlashJobStatus(val label: String) {
    IDLE("Unassigned"),
    READY("Staged"),
    VERIFYING_HASH("Verifying SHA-256"),
    HANDSHAKING("Syncing Endpoint"),
    FLASHING("Writing Blocks"),
    VERIFIED_OK("Flash Verified"),
    FAILED("Transfer Error")
}

data class FlashJobItem(
    val partitionEntry: PitPartitionEntry,
    val isSelected: Boolean = false,
    val payloadFilename: String = "",
    val payloadUriOrPath: String = "",
    val payloadFormat: String = ".img",
    val payloadSizeBytes: Long = 0L,
    val sha256Checksum: String = "",
    val md5Checksum: String = "",
    val progress: Float = 0f,
    val bytesTransferred: Long = 0L,
    val speedMBps: Double = 0.0,
    val status: FlashJobStatus = FlashJobStatus.IDLE,
    val statusDetail: String = "Awaiting payload mapping"
)

data class SafetyConfig(
    val verifyChecksumBeforeFlash: Boolean = true,
    val checkPartitionAlignment: Boolean = true,
    val requireEfsBackupBeforeRepartition: Boolean = true,
    val dryRunHandshakeBeforeBatch: Boolean = true,
    val autoRebootAfterFlash: Boolean = false,
    val fullRepartitionEnabled: Boolean = false
)

enum class LogLevel(val badge: String) {
    INFO("INFO"),
    PACKET("USB-PKT"),
    SUCCESS("OK"),
    WARN("WARN"),
    ERROR("ERR")
}

data class TerminalLogEntry(
    val id: Long,
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val hexPayload: String = ""
)

data class BackupProgressState(
    val isRunning: Boolean = false,
    val currentPartition: String = "",
    val completedPartitions: Int = 0,
    val totalPartitions: Int = 0,
    val overallProgress: Float = 0f,
    val bytesWritten: Long = 0L,
    val targetDirectoryPath: String = "",
    val lastCompletedFolder: String = "",
    val exportedFiles: List<String> = emptyList()
)

data class GuideTopic(
    val id: String,
    val title: String,
    val badge: String,
    val summary: String,
    val steps: List<String>,
    val technicalNote: String
)

enum class WorkbenchTab(val title: String) {
    USB_DEVICE("USB & Link"),
    FLASHER("Flasher"),
    BACKUP("Backup Studio"),
    PIT_EDITOR("PIT Editor")
}
