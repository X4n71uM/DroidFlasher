package net.xantium.droidflasher.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Locale
import net.xantium.droidflasher.model.PitHeader
import net.xantium.droidflasher.model.PitPartitionEntry
import net.xantium.droidflasher.model.PitValidationIssue
import net.xantium.droidflasher.model.PitValidationResult
import net.xantium.droidflasher.model.ValidationSeverity

object PitEngine {

    const val PIT_MAGIC_HEX = "0x12349876"
    const val PIT_MAGIC_LONG = 0x12349876L
    const val HEADER_SIZE_BYTES = 28
    const val ENTRY_SIZE_BYTES = 132

    /**
     * Parses structured PIT editor syntax into a validated [PitValidationResult].
     * Format supports header directives (#PIT_MAGIC, #SOC, #PROJECT, #LUNS) and
     * pipe-delimited partition rows:
     * ENTRY|<id>|<name>|<filename>|<offset_hex>|<blocks>|<attr>|<update>|<dev>|<bin>
     */
    fun validateAndParseText(rawText: String): PitValidationResult {
        val issues = mutableListOf<PitValidationIssue>()
        val entries = mutableListOf<PitPartitionEntry>()

        var magicValue = 0L
        var magicFound = false
        var socTag = "EXYNOS_SNAPDRAGON"
        var projectTag = "UNIVERSAL_BENCH"
        var lunCount = 6

        val lines = rawText.lines()
        lines.forEachIndexed { idx, rawLine ->
            val lineNum = idx + 1
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("//")) return@forEachIndexed

            if (line.startsWith("#")) {
                val kv = line.removePrefix("#").split("=", limit = 2)
                if (kv.size != 2) {
                    issues.add(
                        PitValidationIssue(
                            lineNumber = lineNum,
                            severity = ValidationSeverity.WARNING,
                            partitionName = "HEADER",
                            message = "Malformed header directive '$line'. Expected #KEY=VALUE."
                        )
                    )
                    return@forEachIndexed
                }
                val key = kv[0].trim().uppercase(Locale.US)
                val value = kv[1].trim()
                when (key) {
                    "PIT_MAGIC" -> {
                        magicFound = true
                        val cleaned = value.removePrefix("0x").removePrefix("0X")
                        magicValue = cleaned.toLongOrNull(16) ?: -1L
                        if (magicValue != PIT_MAGIC_LONG) {
                            issues.add(
                                PitValidationIssue(
                                    lineNumber = lineNum,
                                    severity = ValidationSeverity.ERROR,
                                    partitionName = "HEADER",
                                    message = "Invalid PIT Magic '$value'. Must be 0x12349876 (Heimdall/Odin standard)."
                                )
                            )
                        }
                    }
                    "SOC" -> socTag = value
                    "PROJECT" -> projectTag = value
                    "LUNS" -> lunCount = value.toIntOrNull() ?: 6
                }
                return@forEachIndexed
            }

            if (!line.startsWith("ENTRY|") && !line.startsWith("ENTRY |")) {
                issues.add(
                    PitValidationIssue(
                        lineNumber = lineNum,
                        severity = ValidationSeverity.ERROR,
                        partitionName = "SYNTAX",
                        message = "Unrecognized token on line $lineNum. Rows must begin with 'ENTRY|'."
                    )
                )
                return@forEachIndexed
            }

            val parts = line.split("|").map { it.trim() }
            if (parts.size < 10) {
                issues.add(
                    PitValidationIssue(
                        lineNumber = lineNum,
                        severity = ValidationSeverity.ERROR,
                        partitionName = parts.getOrNull(2) ?: "UNKNOWN",
                        message = "Incomplete ENTRY definition (${parts.size}/10 columns). Expected ENTRY|ID|NAME|FILE|OFFSET|BLOCKS|ATTR|UPD|DEV|BIN."
                    )
                )
                return@forEachIndexed
            }

            val id = parts[1].toIntOrNull()
            val partName = parts[2]
            val filename = parts[3]
            val offsetStr = parts[4].removePrefix("0x").removePrefix("0X")
            val blockOffset = offsetStr.toLongOrNull(16) ?: offsetStr.toLongOrNull()
            val rawBlockCount = parts[5].toLongOrNull()
            val blockCount = if (rawBlockCount != null && rawBlockCount == 0L) 64L else rawBlockCount
            val attr = parts[6].uppercase(Locale.US)
            val updateAttr = parts[7].uppercase(Locale.US)
            val devType = if (parts[8].equals("UFS", ignoreCase = true)) 4 else 2
            val binType = if (parts[9].equals("CP", ignoreCase = true)) 1 else 0

            if (id == null || id < 0) {
                issues.add(
                    PitValidationIssue(
                        lineNum,
                        ValidationSeverity.ERROR,
                        partName,
                        "Partition ID '${parts[1]}' must be a non-negative integer."
                    )
                )
            }
            if (partName.isBlank() || partName.length > 31) {
                issues.add(
                    PitValidationIssue(
                        lineNum,
                        ValidationSeverity.ERROR,
                        partName.ifBlank { "EMPTY" },
                        "Partition name must be 1..31 ASCII characters (binary PIT 32-byte null-terminated limit)."
                    )
                )
            }
            if (blockOffset == null || blockOffset < 0L) {
                issues.add(
                    PitValidationIssue(
                        lineNum,
                        ValidationSeverity.ERROR,
                        partName,
                        "Invalid hexadecimal/decimal block offset '${parts[4]}'."
                    )
                )
            }
            if (blockCount == null || blockCount < 0L) {
                issues.add(
                    PitValidationIssue(
                        lineNum,
                        ValidationSeverity.ERROR,
                        partName,
                        "Block count '${parts[5]}' must be non-negative."
                    )
                )
            }
            if (filename.isBlank() || filename == "-") {
                issues.add(
                    PitValidationIssue(
                        lineNum,
                        ValidationSeverity.WARNING,
                        partName,
                        "No default flash filename mapped for partition '$partName'."
                    )
                )
            }

            if (id != null && blockOffset != null && blockCount != null && partName.isNotBlank()) {
                entries.add(
                    PitPartitionEntry(
                        index = entries.size,
                        binaryType = binType,
                        deviceType = devType,
                        identifier = id,
                        attributes = attr,
                        updateAttributes = updateAttr,
                        blockOffset = blockOffset,
                        blockCount = blockCount.coerceAtLeast(1L),
                        partitionName = partName,
                        flashFilename = if (filename == "-" || filename.isBlank()) "${partName.lowercase(Locale.US)}.img" else filename
                    )
                )
            }
        }

        if (!magicFound) {
            issues.add(
                PitValidationIssue(
                    lineNumber = 1,
                    severity = ValidationSeverity.ERROR,
                    partitionName = "HEADER",
                    message = "Missing #PIT_MAGIC=0x12349876 header directive."
                )
            )
        }

        val seenIds = mutableSetOf<Int>()
        val seenNames = mutableSetOf<String>()
        entries.forEach { entry ->
            if (!seenIds.add(entry.identifier)) {
                issues.add(
                    PitValidationIssue(
                        lineNumber = entry.index + 6,
                        severity = ValidationSeverity.WARNING,
                        partitionName = entry.partitionName,
                        message = "Duplicate Partition ID ${entry.identifier} detected."
                    )
                )
            }
            if (!seenNames.add(entry.partitionName.uppercase(Locale.US))) {
                issues.add(
                    PitValidationIssue(
                        lineNumber = entry.index + 6,
                        severity = ValidationSeverity.WARNING,
                        partitionName = entry.partitionName,
                        message = "Duplicate Partition Name '${entry.partitionName}' detected."
                    )
                )
            }
        }

        var hasOverlap = false
        var isAligned4K = true
        val sortedByOffset = entries.sortedBy { it.blockOffset }
        for (i in sortedByOffset.indices) {
            val current = sortedByOffset[i]
            if (current.blockOffset % 8L != 0L) {
                isAligned4K = false
                issues.add(
                    PitValidationIssue(
                        lineNumber = current.index + 6,
                        severity = ValidationSeverity.WARNING,
                        partitionName = current.partitionName,
                        message = "Offset 0x${current.blockOffset.toString(16).uppercase()} is not aligned to 32KB erase-block boundary (multiple of 8 blocks)."
                    )
                )
            }
            if (i < sortedByOffset.size - 1) {
                val next = sortedByOffset[i + 1]
                val currentEnd = current.blockOffset + current.blockCount
                if (currentEnd > next.blockOffset) {
                    hasOverlap = true
                    val overlapBlocks = currentEnd - next.blockOffset
                    issues.add(
                        PitValidationIssue(
                            lineNumber = next.index + 6,
                            severity = ValidationSeverity.ERROR,
                            partitionName = "${current.partitionName} ↔ ${next.partitionName}",
                            message = "CRITICAL OVERLAP: '${current.partitionName}' ends at 0x${currentEnd.toString(16).uppercase()} which collides with '${next.partitionName}' start 0x${next.blockOffset.toString(16).uppercase()} by $overlapBlocks blocks!"
                        )
                    )
                }
            }
        }

        val hasCriticalEfs = entries.any { it.partitionName.contains("EFS", ignoreCase = true) }
        if (!hasCriticalEfs && entries.isNotEmpty()) {
            issues.add(
                PitValidationIssue(
                    lineNumber = 1,
                    severity = ValidationSeverity.WARNING,
                    partitionName = "EFS",
                    message = "No EFS radio calibration partition defined in table. Repartitioning may erase IMEI/baseband data."
                )
            )
        }

        val hasErrors = issues.any { it.severity == ValidationSeverity.ERROR }
        val totalMB = entries.sumOf { it.sizeMB }
        val header = PitHeader(
            magic = if (magicFound) magicValue else 0L,
            entryCount = entries.size,
            socTag = socTag,
            projectTag = projectTag,
            lunCount = lunCount
        )

        return PitValidationResult(
            isValid = !hasErrors && entries.isNotEmpty(),
            magicValid = magicFound && magicValue == PIT_MAGIC_LONG,
            header = header,
            parsedEntries = entries,
            issues = issues,
            totalAllocatedMB = totalMB,
            hasOverlap = hasOverlap,
            isAligned4K = isAligned4K
        )
    }

    /**
     * Serializes a [PitHeader] and list of [PitPartitionEntry] into formatted PIT Editor syntax.
     */
    fun serializeToText(header: PitHeader, entries: List<PitPartitionEntry>): String {
        val sb = StringBuilder()
        sb.appendLine("// DroidFlasher (net.xantium.droidflasher) — Partition Information Table (PIT) Specification")
        sb.appendLine("#PIT_MAGIC=0x${header.magic.toString(16).uppercase(Locale.US)}")
        sb.appendLine("#SOC=${header.socTag}")
        sb.appendLine("#PROJECT=${header.projectTag}")
        sb.appendLine("#LUNS=${header.lunCount}")
        sb.appendLine("// FORMAT: ENTRY | ID | NAME | FILENAME | OFFSET_HEX | BLOCKS_4K | ATTR | UPDATE | DEV | PROC")
        entries.forEach { e ->
            val offsetHex = String.format(Locale.US, "0x%08X", e.blockOffset)
            val devLabel = if (e.deviceType == 4) "UFS" else "EMMC"
            val binLabel = if (e.binaryType == 1) "CP" else "AP"
            sb.appendLine(
                String.format(
                    Locale.US,
                    "ENTRY | %02d | %-12s | %-18s | %s | %8d | %-4s | %-6s | %-4s | %s",
                    e.identifier,
                    e.partitionName,
                    e.flashFilename,
                    offsetHex,
                    e.blockCount,
                    e.attributes,
                    e.updateAttributes,
                    devLabel,
                    binLabel
                )
            )
        }
        return sb.toString().trimEnd()
    }

    /**
     * Encodes the parsed PIT into a genuine binary Heimdall/Odin `.pit` byte array:
     * 28-byte header (magic 0x12349876, count, SOC/project strings) + N * 132-byte partition entries.
     */
    fun encodeToBinaryPit(header: PitHeader, entries: List<PitPartitionEntry>): ByteArray {
        val totalSize = HEADER_SIZE_BYTES + (entries.size * ENTRY_SIZE_BYTES)
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(header.magic.toInt())
        buffer.putInt(entries.size)
        val socBytes = header.socTag.take(8).toByteArray(StandardCharsets.US_ASCII)
        val socPadded = ByteArray(8)
        System.arraycopy(socBytes, 0, socPadded, 0, socBytes.size)
        buffer.put(socPadded)

        val projBytes = header.projectTag.take(12).toByteArray(StandardCharsets.US_ASCII)
        val projPadded = ByteArray(12)
        System.arraycopy(projBytes, 0, projPadded, 0, projBytes.size)
        buffer.put(projPadded)

        entries.forEach { entry ->
            buffer.putInt(entry.binaryType)
            buffer.putInt(entry.deviceType)
            buffer.putInt(entry.identifier)
            val attrInt = when (entry.attributes.uppercase(Locale.US)) {
                "RO" -> 0
                "RW" -> 1
                "STL" -> 2
                else -> 1
            }
            buffer.putInt(attrInt)
            val updInt = when (entry.updateAttributes.uppercase(Locale.US)) {
                "FOTA" -> 1
                "SECURE" -> 2
                else -> 5
            }
            buffer.putInt(updInt)
            buffer.putInt(entry.blockOffset.toInt())
            buffer.putInt(entry.blockCount.toInt())
            buffer.putInt(entry.fileOffset.toInt())
            buffer.putInt((entry.blockCount * 4096L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())

            buffer.put(fixedAsciiBytes(entry.partitionName, 32))
            buffer.put(fixedAsciiBytes(entry.flashFilename, 32))
            buffer.put(fixedAsciiBytes(entry.fotaFilename, 32))
        }

        return buffer.array()
    }

    /**
     * Locates the offset of the PIT magic `0x12349876` (`76 98 34 12`) in a byte buffer,
     * in case a bootloader prepends a packet header before the 28-byte PIT header.
     */
    fun findPitMagicOffset(bytes: ByteArray): Int {
        if (bytes.size < HEADER_SIZE_BYTES) return -1
        val maxSearch = minOf(bytes.size - HEADER_SIZE_BYTES, 128)
        for (offset in 0..maxSearch) {
            if (bytes[offset] == 0x76.toByte() &&
                bytes[offset + 1] == 0x98.toByte() &&
                bytes[offset + 2] == 0x34.toByte() &&
                bytes[offset + 3] == 0x12.toByte()
            ) {
                return offset
            }
        }
        return -1
    }

    /**
     * Decodes a binary `.pit` file (starting with little-endian 0x12349876) into structured text.
     * Handles both LBA-offset PITs and modern UFS block-size PITs (where `blockSizeOrOffset` is
     * constant e.g. 4096 or 512 across entries on multiple UFS LUNs) so decoded tables never
     * produce false overlap errors.
     */
    fun decodeBinaryPitToText(bytes: ByteArray): String? {
        val magicOffset = findPitMagicOffset(bytes)
        if (magicOffset < 0) return null
        val slice = if (magicOffset == 0) bytes else bytes.copyOfRange(magicOffset, bytes.size)
        if (slice.size < HEADER_SIZE_BYTES) return null

        val buffer = ByteBuffer.wrap(slice).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buffer.int.toLong() and 0xFFFFFFFFL
        if (magic != PIT_MAGIC_LONG) return null

        val count = buffer.int.coerceIn(0, 128)
        val socBytes = ByteArray(8)
        buffer.get(socBytes)
        val projBytes = ByteArray(12)
        buffer.get(projBytes)

        val socTag = socBytes.toSafeAsciiTag().ifBlank { "SAMSUNG_SOC" }
        val projTag = projBytes.toSafeAsciiTag().ifBlank { "DEVICE_PIT" }

        val rawEntries = mutableListOf<PitPartitionEntry>()
        val usedIds = mutableSetOf<Int>()

        for (i in 0 until count) {
            if (buffer.remaining() < ENTRY_SIZE_BYTES) break
            val binType = buffer.int
            val devType = buffer.int
            val rawId = buffer.int
            val attrInt = buffer.int
            val updInt = buffer.int
            val blockOffset = buffer.int.toLong() and 0xFFFFFFFFL
            val blockCount = buffer.int.toLong() and 0xFFFFFFFFL
            val fileOffset = buffer.int.toLong() and 0xFFFFFFFFL
            val fileSize = buffer.int.toLong() and 0xFFFFFFFFL

            val partName = ByteArray(32).also { buffer.get(it) }.toSafeAsciiTag()
            val flashName = ByteArray(32).also { buffer.get(it) }.toSafeAsciiFilename()
            val fotaName = ByteArray(32).also { buffer.get(it) }.toSafeAsciiFilename()

            if (partName.isNotBlank()) {
                val uniqueId = if (rawId >= 0 && usedIds.add(rawId)) {
                    rawId
                } else {
                    var candidate = maxOf(rawId.coerceAtLeast(0), i + 1)
                    while (!usedIds.add(candidate)) candidate++
                    candidate
                }
                val resolvedBlocks = when {
                    blockCount > 0L -> blockCount
                    fileSize >= 4096L -> fileSize / 4096L
                    else -> 1024L
                }
                rawEntries.add(
                    PitPartitionEntry(
                        index = rawEntries.size,
                        binaryType = if (binType == 1) 1 else 0,
                        deviceType = if (devType == 4) 4 else 2,
                        identifier = uniqueId,
                        attributes = when (attrInt) {
                            0 -> "RO"
                            2 -> "STL"
                            else -> "RW"
                        },
                        updateAttributes = when (updInt) {
                            2 -> "SECURE"
                            5 -> "RAW"
                            else -> "FOTA"
                        },
                        blockOffset = blockOffset,
                        blockCount = resolvedBlocks.coerceAtLeast(64L),
                        fileOffset = fileOffset,
                        partitionName = partName.take(31),
                        flashFilename = flashName.ifBlank { "${partName.lowercase(Locale.US)}.img" },
                        fotaFilename = fotaName.ifBlank { "steady.bin" }
                    )
                )
            }
        }

        // Real Samsung UFS PITs often store blockSize (e.g., 4096 or 512 or 0) in blockSizeOrOffset
        // or reset LBA offsets per UFS LUN. Normalize overlapping offsets into sequential 8-block aligned LBAs
        // so the parsed PIT has valid non-overlapping ranges in the editor and flasher.
        val normalizedEntries = normalizeSequentialOffsetsIfOverlapping(rawEntries)

        val header = PitHeader(
            magic = magic,
            entryCount = normalizedEntries.size,
            socTag = socTag,
            projectTag = projTag
        )
        return serializeToText(header, normalizedEntries)
    }

    private fun normalizeSequentialOffsetsIfOverlapping(entries: List<PitPartitionEntry>): List<PitPartitionEntry> {
        if (entries.size <= 1) return entries
        val sorted = entries.sortedBy { it.blockOffset }
        var hasCollision = false
        for (i in 0 until sorted.size - 1) {
            val cur = sorted[i]
            val nxt = sorted[i + 1]
            if (cur.blockOffset % 8L != 0L || cur.blockOffset + cur.blockCount > nxt.blockOffset) {
                hasCollision = true
                break
            }
        }
        if (!hasCollision) return entries

        var runningOffset = 0x00000800L // Start at 2048 blocks (8MB aligned)
        return entries.mapIndexed { idx, entry ->
            val alignedOffset = ((runningOffset + 7L) / 8L) * 8L
            val blocks = entry.blockCount.coerceAtLeast(64L)
            runningOffset = alignedOffset + blocks
            entry.copy(
                index = idx,
                blockOffset = alignedOffset,
                blockCount = blocks
            )
        }
    }

    private fun fixedAsciiBytes(text: String, length: Int): ByteArray {
        val out = ByteArray(length)
        val raw = text.toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(raw, 0, out, 0, minOf(raw.size, length - 1))
        return out
    }

    private fun ByteArray.toSafeAsciiTag(): String {
        val zeroIdx = indexOfFirst { it == 0.toByte() }
        val len = if (zeroIdx >= 0) zeroIdx else size
        val sb = StringBuilder()
        for (i in 0 until len) {
            val ch = (this[i].toInt() and 0xFF).toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '_' || ch == '-') {
                sb.append(ch)
            } else if (ch == ' ' && sb.isNotEmpty()) {
                sb.append('_')
            }
        }
        return sb.toString().trim('_')
    }

    private fun ByteArray.toSafeAsciiFilename(): String {
        val zeroIdx = indexOfFirst { it == 0.toByte() }
        val len = if (zeroIdx >= 0) zeroIdx else size
        val sb = StringBuilder()
        for (i in 0 until len) {
            val ch = (this[i].toInt() and 0xFF).toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '_' || ch == '-' || ch == '.') {
                sb.append(ch)
            }
        }
        return sb.toString().trim('.')
    }

    fun getDefaultPitForModel(modelCode: String): String {
        val entries = when {
            modelCode.contains("pixel", ignoreCase = true) || modelCode.contains("husky", ignoreCase = true) -> listOf(
                PitPartitionEntry(0, 0, 4, 1, "RO", "SECURE", 0x00001000, 2048, 0, "ABL_A", "abl.img"),
                PitPartitionEntry(1, 0, 4, 2, "RW", "SECURE", 0x00002000, 16384, 0, "BOOT_A", "boot.img"),
                PitPartitionEntry(2, 0, 4, 3, "RW", "SECURE", 0x00006000, 16384, 0, "INIT_BOOT_A", "init_boot.img"),
                PitPartitionEntry(3, 0, 4, 4, "RW", "SECURE", 0x0000A000, 24576, 0, "VENDOR_BOOT_A", "vendor_boot.img"),
                PitPartitionEntry(4, 0, 4, 5, "RW", "RAW", 0x00010000, 5120, 0, "EFS", "efs_modemst.img"),
                PitPartitionEntry(5, 1, 4, 6, "RW", "SECURE", 0x00012000, 40960, 0, "RADIO_A", "radio.img"),
                PitPartitionEntry(6, 0, 4, 7, "RW", "FOTA", 0x0001C000, 2097152, 0, "SUPER", "super.img"),
                PitPartitionEntry(7, 0, 4, 8, "RW", "FOTA", 0x0021C000, 4194304, 0, "USERDATA", "userdata.img")
            )
            else -> listOf(
                PitPartitionEntry(0, 0, 4, 1, "RO", "SECURE", 0x00000800, 2048, 0, "BL", "sboot.bin"),
                PitPartitionEntry(1, 0, 4, 2, "RW", "RAW", 0x00001000, 4096, 0, "PARAM", "param.bin"),
                PitPartitionEntry(2, 0, 4, 3, "RW", "RAW", 0x00002000, 8192, 0, "EFS", "efs.img"),
                PitPartitionEntry(3, 0, 4, 4, "RW", "RAW", 0x00004000, 4096, 0, "SEC_EFS", "sec_efs.img"),
                PitPartitionEntry(4, 0, 4, 5, "RW", "SECURE", 0x00005000, 24576, 0, "BOOT", "boot.img"),
                PitPartitionEntry(5, 0, 4, 6, "RW", "SECURE", 0x0000B000, 32768, 0, "RECOVERY", "recovery.img"),
                PitPartitionEntry(6, 0, 4, 7, "RW", "SECURE", 0x00013000, 4096, 0, "DTBO", "dtbo.img"),
                PitPartitionEntry(7, 0, 4, 8, "RW", "SECURE", 0x00014000, 256, 0, "VBMETA", "vbmeta.img"),
                PitPartitionEntry(8, 1, 4, 9, "RW", "SECURE", 0x00015000, 49152, 0, "MODEM", "modem.bin"),
                PitPartitionEntry(9, 0, 4, 10, "RW", "FOTA", 0x00021000, 2621440, 0, "SUPER", "super.img"),
                PitPartitionEntry(10, 0, 4, 11, "RW", "FOTA", 0x002A1000, 131072, 0, "PRISM", "prism.img"),
                PitPartitionEntry(11, 0, 4, 12, "RW", "FOTA", 0x002C1000, 65536, 0, "OPTICS", "optics.img"),
                PitPartitionEntry(12, 0, 4, 13, "RW", "FOTA", 0x002D1000, 16384, 0, "CACHE", "cache.img"),
                PitPartitionEntry(13, 0, 4, 14, "RW", "FOTA", 0x002D5000, 8388608, 0, "USERDATA", "userdata.img")
            )
        }

        val header = PitHeader(
            magic = PIT_MAGIC_LONG,
            entryCount = entries.size,
            socTag = if (modelCode.contains("pixel", ignoreCase = true)) "TENSOR_G3" else "SM8550_UFS4",
            projectTag = modelCode.uppercase(Locale.US).take(12)
        )
        return serializeToText(header, entries)
    }
}
