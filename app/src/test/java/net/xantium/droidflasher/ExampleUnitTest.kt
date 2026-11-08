package net.xantium.droidflasher

import net.xantium.droidflasher.data.PitEngine
import net.xantium.droidflasher.model.PitHeader
import net.xantium.droidflasher.model.PitPartitionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ExampleUnitTest {
    @Test
    fun pitBinaryRoundTripAndValidation_succeeds() {
        val defaultText = PitEngine.getDefaultPitForModel("SM-S918B")
        val initialValidation = PitEngine.validateAndParseText(defaultText)
        assertTrue("Initial PIT text should be valid", initialValidation.isValid)
        assertTrue(initialValidation.parsedEntries.isNotEmpty())

        val binaryBytes = PitEngine.encodeToBinaryPit(initialValidation.header, initialValidation.parsedEntries)
        assertTrue(binaryBytes.size >= 28 + 132 * initialValidation.parsedEntries.size)

        val decodedText = PitEngine.decodeBinaryPitToText(binaryBytes)
        assertNotNull("Decoded PIT text should not be null", decodedText)

        val decodedValidation = PitEngine.validateAndParseText(decodedText!!)
        assertTrue("Decoded PIT text should validate cleanly", decodedValidation.isValid)
        assertEquals(initialValidation.parsedEntries.size, decodedValidation.parsedEntries.size)
    }

    @Test
    fun samsungUfsPitWithConstantBlockSizeField_normalizesWithoutFalseOverlap() {
        // Real Samsung UFS devices store blockSize (4096) in field 0x10 of every 132-byte entry
        val entries = listOf(
            PitPartitionEntry(
                index = 0,
                binaryType = 0,
                deviceType = 4,
                identifier = 1,
                attributes = "RO",
                updateAttributes = "SECURE",
                blockOffset = 4096L,
                blockCount = 2048L,
                partitionName = "BL_SBOOT",
                flashFilename = "sboot.bin"
            ),
            PitPartitionEntry(
                index = 1,
                binaryType = 0,
                deviceType = 4,
                identifier = 2,
                attributes = "RW",
                updateAttributes = "RAW",
                blockOffset = 4096L,
                blockCount = 5120L,
                partitionName = "EFS",
                flashFilename = "efs.img"
            ),
            PitPartitionEntry(
                index = 2,
                binaryType = 0,
                deviceType = 4,
                identifier = 3,
                attributes = "RW",
                updateAttributes = "FOTA",
                blockOffset = 4096L,
                blockCount = 16384L,
                partitionName = "BOOT",
                flashFilename = "boot.img"
            )
        )
        val header = PitHeader(
            entryCount = entries.size,
            socTag = "EXYNOS2400",
            projectTag = "SM-S921B_EUR_OPEN"
        )

        // Simulate 500-byte chunked Odin/LOKE PIT dump with trailing zero padding
        val rawPit = PitEngine.encodeToBinaryPit(header, entries)
        val paddedDump = ByteArray(1000)
        System.arraycopy(rawPit, 0, paddedDump, 0, rawPit.size)

        val decodedText = PitEngine.decodeBinaryPitToText(paddedDump)
        assertNotNull("Chunk-padded PIT dump must decode", decodedText)

        val validation = PitEngine.validateAndParseText(decodedText!!)
        assertTrue(
            "UFS PIT with constant blockSize=4096 must not fail overlap validation: ${validation.issues}",
            validation.isValid
        )
        assertEquals(3, validation.parsedEntries.size)
        assertTrue(validation.parsedEntries[1].blockOffset > validation.parsedEntries[0].blockOffset)
        assertTrue(validation.parsedEntries[2].blockOffset > validation.parsedEntries[1].blockOffset)
    }

    @Test
    fun pitMagicOffsetDetection_handlesPrefixedHeader() {
        val validation = PitEngine.validateAndParseText(PitEngine.getDefaultPitForModel("SM-S918B"))
        val rawPit = PitEngine.encodeToBinaryPit(validation.header, validation.parsedEntries)
        // Prefix with 8 bytes of Odin response header
        val prefixed = ByteArray(8 + rawPit.size)
        ByteBuffer.wrap(prefixed).order(ByteOrder.LITTLE_ENDIAN).putInt(0, 0x65).putInt(4, rawPit.size)
        System.arraycopy(rawPit, 0, prefixed, 8, rawPit.size)

        assertEquals(8, PitEngine.findPitMagicOffset(prefixed))
        val decoded = PitEngine.decodeBinaryPitToText(prefixed)
        assertNotNull(decoded)
    }
}
