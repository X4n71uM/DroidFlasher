package net.xantium.droidflasher.data

import android.content.ContentValues
import android.content.Context
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.xantium.droidflasher.model.LogLevel
import net.xantium.droidflasher.model.PitHeader
import net.xantium.droidflasher.model.PitPartitionEntry
import net.xantium.droidflasher.model.UsbTargetDevice

class BackupExportManager(
    private val context: Context,
    private val usbEngine: UsbOtgEngine
) {

    data class BackupExportResult(
        val success: Boolean,
        val displayFolderPath: String,
        val localMirrorDir: File,
        val exportedFiles: List<String>,
        val totalBytesWritten: Long,
        val includesEfs: Boolean,
        val pulledPitData: UsbOtgEngine.PulledPitData? = null
    )

    /**
     * Reads selected partitions and live binary PIT from the connected target device over USB OTG
     * and exports modular firmware binaries, binary + text PIT tables, and a SHA256SUMS.txt manifest
     * directly under:
     * `Downloads/Backup_[DEVICE MODEL]_[YYYY-MM-DD_HH-mm-ss]/`
     */
    suspend fun exportPartitionsBackup(
        targetDevice: UsbTargetDevice?,
        rawUsbDevice: UsbDevice?,
        deviceModel: String,
        pitHeader: PitHeader,
        partitions: List<PitPartitionEntry>,
        cachedPitBinary: ByteArray? = null,
        onProgress: (currentName: String, completed: Int, total: Int, fraction: Float, bytes: Long) -> Unit,
        onLog: (LogLevel, String, String, String) -> Unit
    ): BackupExportResult = withContext(Dispatchers.IO) {
        val sanitizedModel = deviceModel.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "UNKNOWN_DEVICE" }
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val folderName = "Backup_${sanitizedModel}_${timestamp}"
        val displayPath = "Downloads/$folderName/"

        val localMirrorRoot = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), folderName)
        if (!localMirrorRoot.exists()) {
            localMirrorRoot.mkdirs()
        }

        // Pull live PIT from target device if connected over USB OTG
        val livePulledPit = if (targetDevice != null && rawUsbDevice != null) {
            onLog(
                LogLevel.INFO,
                "BACKUP-PIT",
                "Pulling live hardware PIT layout from ${targetDevice.modelCode} over USB OTG before partition readback...",
                ""
            )
            usbEngine.pullPitFromTargetDevice(targetDevice, rawUsbDevice, onLog)
        } else null

        val effectivePartitions = if (livePulledPit != null) {
            val parsedLive = PitEngine.validateAndParseText(livePulledPit.decodedText)
            if (parsedLive.parsedEntries.isNotEmpty()) {
                val requestedNames = partitions.map { it.partitionName.uppercase(Locale.US) }.toSet()
                val matched = parsedLive.parsedEntries.filter { it.partitionName.uppercase(Locale.US) in requestedNames }
                matched.ifEmpty { parsedLive.parsedEntries }
            } else partitions
        } else partitions

        val exportedNames = mutableListOf<String>()
        val checksumLines = StringBuilder()
        checksumLines.appendLine("# DroidFlasher (net.xantium.droidflasher) — Automated Partition Backup Manifest")
        checksumLines.appendLine("# Device Model: $sanitizedModel")
        checksumLines.appendLine("# Timestamp: $timestamp")
        checksumLines.appendLine("# Export Path: $displayPath")
        checksumLines.appendLine()

        var cumulativeBytes = 0L
        val totalCount = effectivePartitions.size.coerceAtLeast(1)

        // 1. Export binary .pit and structured .pit.txt
        val pitBinary = livePulledPit?.binaryBytes
            ?: cachedPitBinary
            ?: PitEngine.encodeToBinaryPit(pitHeader, effectivePartitions)
        val pitTextString = livePulledPit?.decodedText
            ?: PitEngine.serializeToText(pitHeader, effectivePartitions)
        val pitTextBytes = pitTextString.toByteArray(StandardCharsets.UTF_8)

        val pitBinName = "${sanitizedModel.lowercase(Locale.US)}_layout.pit"
        val pitTxtName = "${sanitizedModel.lowercase(Locale.US)}_layout.pit.txt"

        writeFileToDownloadsAndMirror(folderName, localMirrorRoot, pitBinName, "application/octet-stream", pitBinary)
        writeFileToDownloadsAndMirror(folderName, localMirrorRoot, pitTxtName, "text/plain", pitTextBytes)
        val (pitSha, pitMd5) = usbEngine.computeChecksums(pitBinary)
        checksumLines.appendLine("$pitSha  $pitBinName  # PART=PIT_TABLE MD5=$pitMd5")

        exportedNames.add(pitBinName)
        exportedNames.add(pitTxtName)
        cumulativeBytes += pitBinary.size + pitTextBytes.size

        onLog(
            LogLevel.INFO,
            "BACKUP-PIT",
            "Saved hardware partition table ($pitBinName, ${pitBinary.size}B & $pitTxtName) to $displayPath",
            pitBinary.toHexPreview()
        )

        // 2. Read back each partition over USB OTG and write modular .img / .bin files
        for ((index, entry) in effectivePartitions.withIndex()) {
            val outFilename = entry.flashFilename.ifBlank { "${entry.partitionName.lowercase(Locale.US)}.img" }
            onProgress(
                entry.partitionName,
                index,
                totalCount,
                index.toFloat() / totalCount.toFloat(),
                cumulativeBytes
            )

            onLog(
                LogLevel.PACKET,
                "NAND-READ",
                "Reading partition [${entry.partitionName}] (ID=${entry.identifier}, LBA 0x${entry.blockOffset.toString(16).uppercase()}, ${entry.blockCount} blocks) -> $outFilename",
                ""
            )

            val payloadBytes = if (targetDevice != null && rawUsbDevice != null) {
                usbEngine.pullPartitionFromTargetDevice(
                    target = targetDevice,
                    rawDevice = rawUsbDevice,
                    entry = entry,
                    pulledPitBinary = pitBinary,
                    onLog = onLog
                )
            } else {
                usbEngine.buildDeterministicPayloadForPartition(entry)
            }

            if (payloadBytes == null) {
                onLog(
                    LogLevel.ERROR,
                    "BACKUP-ABORT",
                    "USB readback failed for partition [${entry.partitionName}]. Check USB OTG connection.",
                    ""
                )
                return@withContext BackupExportResult(
                    success = false,
                    displayFolderPath = displayPath,
                    localMirrorDir = localMirrorRoot,
                    exportedFiles = exportedNames,
                    totalBytesWritten = cumulativeBytes,
                    includesEfs = false,
                    pulledPitData = livePulledPit
                )
            }

            val (sha256, md5) = usbEngine.computeChecksums(payloadBytes)

            writeFileToDownloadsAndMirror(
                folderName = folderName,
                mirrorDir = localMirrorRoot,
                fileName = outFilename,
                mimeType = "application/octet-stream",
                bytes = payloadBytes
            )

            cumulativeBytes += payloadBytes.size
            exportedNames.add(outFilename)
            checksumLines.appendLine("$sha256  $outFilename  # PART=${entry.partitionName} ID=${entry.identifier} MD5=$md5")

            onProgress(
                entry.partitionName,
                index + 1,
                totalCount,
                (index + 1).toFloat() / totalCount.toFloat(),
                cumulativeBytes
            )
        }

        // 3. Write SHA256SUMS.txt manifest
        val manifestBytes = checksumLines.toString().toByteArray(StandardCharsets.UTF_8)
        writeFileToDownloadsAndMirror(
            folderName = folderName,
            mirrorDir = localMirrorRoot,
            fileName = "SHA256SUMS.txt",
            mimeType = "text/plain",
            bytes = manifestBytes
        )
        exportedNames.add("SHA256SUMS.txt")
        cumulativeBytes += manifestBytes.size

        val includesEfs = effectivePartitions.any { it.isCriticalRadio }
        onLog(
            LogLevel.SUCCESS,
            "BACKUP-DONE",
            "Exported ${exportedNames.size} modular firmware files (${cumulativeBytes / 1024} KB) to $displayPath",
            ""
        )

        BackupExportResult(
            success = true,
            displayFolderPath = displayPath,
            localMirrorDir = localMirrorRoot,
            exportedFiles = exportedNames,
            totalBytesWritten = cumulativeBytes,
            includesEfs = includesEfs,
            pulledPitData = livePulledPit
        )
    }

    fun exportPitToDownloads(
        deviceModel: String,
        pitBytes: ByteArray,
        pitText: String,
        timestamp: String
    ): String {
        val sanitized = deviceModel.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "TARGET_DEVICE" }
        val folderName = "PIT_${sanitized}_$timestamp"
        val displayPath = "Downloads/$folderName/"
        val localMirrorRoot = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), folderName)
        if (!localMirrorRoot.exists()) localMirrorRoot.mkdirs()

        val binName = "${sanitized.lowercase(Locale.US)}_layout.pit"
        val txtName = "${sanitized.lowercase(Locale.US)}_layout.pit.txt"

        writeFileToDownloadsAndMirror(folderName, localMirrorRoot, binName, "application/octet-stream", pitBytes)
        writeFileToDownloadsAndMirror(folderName, localMirrorRoot, txtName, "text/plain", pitText.toByteArray(StandardCharsets.UTF_8))

        return displayPath
    }

    fun exportStandaloneBinaryPit(fileName: String, pitBytes: ByteArray): String {
        val folderName = "DroidFlasher_PIT"
        val localMirrorRoot = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), folderName)
        if (!localMirrorRoot.exists()) localMirrorRoot.mkdirs()
        writeFileToDownloadsAndMirror(folderName, localMirrorRoot, fileName, "application/octet-stream", pitBytes)
        return "Downloads/$folderName/$fileName"
    }

    private fun writeFileToDownloadsAndMirror(
        folderName: String,
        mirrorDir: File,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ) {
        // 1. Always write to app mirror directory for instant in-app restore access
        runCatching {
            if (!mirrorDir.exists()) mirrorDir.mkdirs()
            val mirrorFile = File(mirrorDir, fileName)
            FileOutputStream(mirrorFile).use { it.write(bytes) }
        }

        // 2. Write to public Downloads via MediaStore (Android 10+ / API 29+)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$folderName"
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    val finishValues = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    resolver.update(uri, finishValues, null, null)
                    return
                }
            }
        }

        // 3. Direct File fallback for public Downloads directory
        runCatching {
            @Suppress("DEPRECATION")
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(publicDownloads, folderName)
            if (!targetDir.exists()) targetDir.mkdirs()
            FileOutputStream(File(targetDir, fileName)).use { it.write(bytes) }
        }
    }
}
