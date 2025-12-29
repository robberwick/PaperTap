package com.robberwick.papertap.waveshare

import android.graphics.Bitmap
import android.graphics.Matrix
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.os.SystemClock
import java.io.IOException

/**
 * WaveShare NFC e-paper display writer.
 * Handles communication with WaveShare NFC-powered e-paper displays.
 *
 * This is a clean-room reimplementation of the WaveShare NFC SDK to remove
 * the proprietary JAR dependency.
 */
class WaveShareNfcWriter {
    // Image data buffers (58080 bytes each)
    private val blackLayerData = ByteArray(0xE2E0)
    private val redLayerData = ByteArray(0xE2E0)

    // Progress percentage (0-100, or -1 on error)
    var progress: Int = 0
        private set

    private var nfcA: NfcA? = null

    /**
     * Result codes for write operations
     */
    enum class WriteResult {
        SUCCESS,           // Write completed successfully
        DIMENSION_MISMATCH, // Bitmap dimensions don't match display
        COMMUNICATION_ERROR // NFC communication failed
    }

    /**
     * Initialize NFC connection.
     * @param nfcA NFC-A technology instance
     * @return true if connection successful, false otherwise
     */
    fun connect(nfcA: NfcA): Boolean {
        this.nfcA = nfcA
        progress = 0

        return try {
            nfcA.connect()
            // Override default 700ms timeout to 1200ms for more reliable writes
            nfcA.timeout = 1200
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Write bitmap to e-paper display.
     * @param displayType WaveShare display type (1-8)
     * @param bitmap Image to write (must match display dimensions)
     * @return WriteResult indicating success or error type
     */
    fun writeBitmap(displayType: Int, bitmap: Bitmap): WriteResult {
        val nfc = nfcA ?: return WriteResult.COMMUNICATION_ERROR
        val config = DisplayConfig.forDisplayType(displayType) ?: return WriteResult.DIMENSION_MISMATCH

        // Validate bitmap dimensions
        if (!DisplayConfig.validateDimensions(displayType, bitmap.width, bitmap.height)) {
            return WriteResult.DIMENSION_MISMATCH
        }

        return try {
            progress = 0

            val success = if (displayType == 8) {
                // 1.54" B uses special protocol
                writeDisplay154B(nfc, config, bitmap)
            } else {
                // Standard protocol for all other displays
                writeNdefRecord(nfc)
                writeStandardDisplay(nfc, config, bitmap)
            }

            if (success) {
                WriteResult.SUCCESS
            } else {
                progress = -1
                WriteResult.COMMUNICATION_ERROR
            }
        } catch (e: IOException) {
            e.printStackTrace()
            progress = -1
            WriteResult.COMMUNICATION_ERROR
        }
    }

    /**
     * Write NDEF record to tag for Android app association.
     * This ensures the tag is recognized as a WaveShare tag.
     */
    private fun writeNdefRecord(nfc: NfcA) {
        val currentRecord = ByteArray(48)

        try {
            // Read existing NDEF record (3 blocks of 16 bytes)
            val block1 = nfc.transceive(byteArrayOf(0x30, 0x04))
            System.arraycopy(block1, 0, currentRecord, 0, 16)

            val block2 = nfc.transceive(byteArrayOf(0x30, 0x08))
            System.arraycopy(block2, 0, currentRecord, 16, 16)

            val block3 = nfc.transceive(byteArrayOf(0x30, 0x0C))
            System.arraycopy(block3, 0, currentRecord, 32, 16)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // Expected NDEF record with Android Application Record (AAR) pointing to WaveShare app
        val expectedRecord = byteArrayOf(
            0x03, 0x27, 0xD4.toByte(), 0x0F, 0x15, 0x61, 0x6E, 0x64, // NDEF header
            0x72, 0x6F, 0x69, 0x64, 0x2E, 0x63, 0x6F, 0x6D, // "android.com"
            0x3A, 0x70, 0x6B, 0x67, 0x77, 0x61, 0x76, 0x65, // ":pkgwave"
            0x73, 0x68, 0x61, 0x72, 0x65, 0x2E, 0x66, 0x65, // "share.fe"
            0x6E, 0x67, 0x2E, 0x6E, 0x66, 0x63, 0x74, 0x61, // "ng.nfcta"
            0x67, 0xFE.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00  // "g" + terminator
        )

        // Only write if record doesn't match
        if (!currentRecord.contentEquals(expectedRecord)) {
            for (i in 0 until 11) {
                try {
                    val blockNum = (i + 4).toByte()
                    val data = byteArrayOf(
                        expectedRecord[i * 4],
                        expectedRecord[i * 4 + 1],
                        expectedRecord[i * 4 + 2],
                        expectedRecord[i * 4 + 3]
                    )
                    nfc.transceive(byteArrayOf(0xA2.toByte(), blockNum, data[0], data[1], data[2], data[3]))
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Write to standard e-paper displays (types 1-7).
     */
    private fun writeStandardDisplay(nfc: NfcA, config: DisplayConfig, bitmap: Bitmap): Boolean {
        // Command sequence: Initialize display
        if (!sendCommand(nfc, 0xCD.toByte(), 0x0D)) return false
        if (!sendCommand(nfc, 0xCD.toByte(), 0x00, config.commandByte)) return false
        SystemClock.sleep(50)

        if (!sendCommand(nfc, 0xCD.toByte(), 0x01)) return false
        SystemClock.sleep(20)

        if (!sendCommand(nfc, 0xCD.toByte(), 0x02)) return false
        SystemClock.sleep(20)

        if (!sendCommand(nfc, 0xCD.toByte(), 0x03)) return false
        SystemClock.sleep(20)

        if (!sendCommand(nfc, 0xCD.toByte(), 0x05)) return false
        SystemClock.sleep(20)

        if (!sendCommand(nfc, 0xCD.toByte(), 0x06)) return false
        SystemClock.sleep(10)

        // Process and prepare image data
        val processedBitmap = if (config.needsRotation) {
            rotateBitmap(bitmap, 270f)
        } else {
            bitmap
        }

        prepareImageData(config, processedBitmap)

        // Send first layer data (swapped to match display protocol)
        if (!sendCommand(nfc, 0xCD.toByte(), 0x07, 0x00)) return false

        for (packetIndex in 0 until config.packetCount) {
            val packet = ByteArray(config.packetSize.toInt())
            packet[0] = 0xCD.toByte()
            packet[1] = 0x08
            packet[2] = (config.packetSize - 3).toByte()

            val dataSize = config.packetSize - 3
            // Send redLayerData first (this goes to the black layer on the display)
            System.arraycopy(redLayerData, packetIndex * dataSize, packet, 3, dataSize)

            val response = nfc.transceive(packet)
            if (response[0] != 0.toByte() || response[1] != 0.toByte()) return false

            // Update progress (0-50% for black layer on dual-layer displays)
            progress = if (config.hasRedLayer) {
                packetIndex * 50 / config.packetCount
            } else {
                packetIndex * 100 / config.packetCount
            }

            SystemClock.sleep(2)
        }

        // Special handling for 4.2" V2 display
        if (config.width == 880 && config.height == 528) {
            val padding = ByteArray(113)
            padding[0] = 0xCD.toByte()
            padding[1] = 0x08
            padding[2] = 120
            for (i in 3 until 113) {
                padding[i] = 0xFF.toByte()
            }
            nfc.transceive(padding)
        }

        // Prepare for refresh or red layer
        if (!sendCommand(nfc, 0xCD.toByte(), 0x18)) return false

        // Handle second layer for dual-layer displays
        if (config.hasRedLayer) {
            when (config.width) {
                296 -> { // 2.13" B
                    for (packetIndex in 0 until config.packetCount) {
                        val packet = ByteArray(config.packetSize.toInt())
                        packet[0] = 0xCD.toByte()
                        packet[1] = 0x08
                        packet[2] = (config.packetSize - 3).toByte()

                        val dataSize = config.packetSize - 3
                        // Send blackLayerData second (this goes to the red layer on the display)
                        System.arraycopy(blackLayerData, packetIndex * dataSize, packet, 3, dataSize)

                        val response = nfc.transceive(packet)
                        if (response[0] != 0.toByte() || response[1] != 0.toByte()) return false

                        progress = packetIndex * 50 / config.packetCount + 50
                        SystemClock.sleep(2)
                    }
                }
                264 -> { // 2.7" with special red layer protocol
                    SystemClock.sleep(100)

                    for (packetIndex in 0 until 48) {
                        val packet = ByteArray(124)
                        packet[0] = 0xCD.toByte()
                        packet[1] = 0x19
                        packet[2] = 121

                        // Send redLayerData for 2.7" second layer
                        System.arraycopy(redLayerData, packetIndex * 121, packet, 3, 121)

                        progress = packetIndex * 50 / 48 + 51
                        val response = nfc.transceive(packet)
                        if (response[0] != 0.toByte() || response[1] != 0.toByte()) return false

                        SystemClock.sleep(2)
                    }

                    SystemClock.sleep(100)
                }
            }
        }

        // Trigger display refresh
        SystemClock.sleep(200)
        if (!sendCommand(nfc, 0xCD.toByte(), 0x09)) return false

        // Wait for refresh to complete
        SystemClock.sleep(300)
        var attempts = 0
        while (true) {
            attempts++
            val response = nfc.transceive(byteArrayOf(0xCD.toByte(), 0x0A))
            if (response[0] == 0xFF.toByte() && response[1] == 0.toByte()) {
                // Refresh complete
                if (!sendCommand(nfc, 0xCD.toByte(), 0x04)) return false
                progress = 100
                return true
            }

            if (attempts > 100) {
                return false // Timeout
            }

            SystemClock.sleep(25)
        }
    }

    /**
     * Write to 1.54" B display (special protocol).
     */
    private fun writeDisplay154B(nfc: NfcA, config: DisplayConfig, bitmap: Bitmap): Boolean {
        SystemClock.sleep(10)

        // Initialize display
        if (!sendCommand(nfc, 0xCD.toByte(), 0x0D)) return false
        SystemClock.sleep(10)

        if (!sendCommand(nfc, 0xCD.toByte(), 0x00)) return false
        SystemClock.sleep(10)

        if (!sendCommand(nfc, 0xCD.toByte(), 0x01)) return false
        SystemClock.sleep(10)

        if (!sendCommand(nfc, 0xCD.toByte(), 0x02)) return false
        SystemClock.sleep(100)

        if (!sendCommand(nfc, 0xCD.toByte(), 0x03)) return false
        SystemClock.sleep(100)

        // Prepare image data
        prepareImageData(config, bitmap)

        // Send first layer (swapped)
        for (packetIndex in 0 until 50) {
            val packet = ByteArray(103)
            packet[0] = 0xCD.toByte()
            packet[1] = 0x05
            packet[2] = 100

            // Send redLayerData first for 1.54" B
            System.arraycopy(redLayerData, packetIndex * 100, packet, 3, 100)

            progress = packetIndex * 100 / 100
            val response = nfc.transceive(packet)
            if (response[0] != 0.toByte() || response[1] != 0.toByte()) return false

            SystemClock.sleep(5)
        }

        if (!sendCommand(nfc, 0xCD.toByte(), 0x04)) return false
        SystemClock.sleep(30)

        // Send second layer (swapped)
        for (packetIndex in 0 until 50) {
            val packet = ByteArray(103)
            packet[0] = 0xCD.toByte()
            packet[1] = 0x05
            packet[2] = 100

            // Send blackLayerData second for 1.54" B
            System.arraycopy(blackLayerData, packetIndex * 100, packet, 3, 100)

            progress = (packetIndex + 50) * 100 / 100
            val response = nfc.transceive(packet)
            if (response[0] != 0.toByte() || response[1] != 0.toByte()) return false

            SystemClock.sleep(5)
        }

        // Trigger refresh
        SystemClock.sleep(100)
        if (!sendCommand(nfc, 0xCD.toByte(), 0x06)) return false
        SystemClock.sleep(1000)

        // Wait for completion
        while (true) {
            val response = nfc.transceive(byteArrayOf(0xCD.toByte(), 0x08))
            if (response[0] == 0xFF.toByte() && response[1] == 0.toByte()) {
                progress = 100
                return true
            }
            SystemClock.sleep(500)
        }
    }

    /**
     * Prepare image data from bitmap (convert to black/white bytes).
     */
    private fun prepareImageData(config: DisplayConfig, bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val width = bitmap.width
        val height = bitmap.height

        for (y in 0 until height) {
            for (x in 0 until width / 8) {
                var blackByte: Byte = 0
                var redByte: Byte = 0

                for (bit in 0 until 8) {
                    val pixelIndex = (bit + x * 8) + y * width
                    val pixel = pixels[pixelIndex]

                    blackByte = (blackByte.toInt() shl 1).toByte()
                    redByte = (redByte.toInt() shl 1).toByte()

                    if (config.hasRedLayer) {
                        // Dual-layer display (black + red e-paper)
                        // Empirically determined encoding (with swapped send order):
                        // First transmission controls black layer, Second controls red layer
                        // Black pixels (0,0) → show black ✓
                        // White pixels (1,0) → show white (first=1, second=0)
                        // Red pixels (0,1) → show red (first=0, second=1)

                        val gray = ((pixel shr 16) and 0xFF) * 0.299f +
                                   ((pixel shr 8) and 0xFF) * 0.587f +
                                   (pixel and 0xFF) * 0.114f

                        // For white pixels: set redByte (sent first), keep blackByte at 0 (sent second)
                        if (pixel == -1) {  // 0xFFFFFFFF = pure white
                            redByte = (redByte.toInt() or 1).toByte()
                            // blackByte stays 0
                        }
                        // For dark pixels: leave both at 0 to show black
                        // (gray <= 128 → both bytes stay 0)
                    } else {
                        // Single-layer display: white pixels (> 128 threshold)
                        val gray = ((pixel shr 16) and 0xFF) * 0.299f +
                                   ((pixel shr 8) and 0xFF) * 0.587f +
                                   (pixel and 0xFF) * 0.114f

                        if (gray > 128) {
                            blackByte = (blackByte.toInt() or 1).toByte()
                        }
                    }
                }

                val dataIndex = y * (width / 8) + x
                blackLayerData[dataIndex] = blackByte
                redLayerData[dataIndex] = redByte
            }
        }
    }

    /**
     * Send a command to the NFC tag and verify response.
     * @return true if command successful (response = [0x00, 0x00])
     */
    private fun sendCommand(nfc: NfcA, vararg command: Byte): Boolean {
        return try {
            val response = nfc.transceive(command)
            response[0] == 0.toByte() && response[1] == 0.toByte()
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Rotate a bitmap by the specified angle.
     */
    private fun rotateBitmap(bitmap: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.setRotate(angle)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
    }

    /**
     * Get UID string from NFC tag.
     */
    fun getUidString(tag: Tag): String? {
        val uid = tag.id
        if (uid == null || uid.isEmpty()) return null

        val sb = StringBuilder("UID")
        for (byte in uid) {
            sb.append(":")
            sb.append(String.format("%02X", byte))
        }
        return sb.toString()
    }

    companion object {
        /**
         * Find the null terminator in a byte array.
         * @return index of first null byte, or array length if none found
         */
        fun findNullTerminator(array: ByteArray): Int {
            for (i in array.indices) {
                if (array[i] == 0.toByte()) return i
            }
            return array.size
        }
    }
}
