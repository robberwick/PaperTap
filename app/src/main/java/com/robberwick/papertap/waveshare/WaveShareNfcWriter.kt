package com.robberwick.papertap.waveshare

import com.robberwick.papertap.BuildConfig

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
            if (BuildConfig.DEBUG) e.printStackTrace()
            false
        }
    }

    /**
     * Write [bitmap] to [model]. The bitmap must match the selected model's
     * physical resolution in either orientation.
     */
    fun writeBitmap(model: DisplayModel, bitmap: Bitmap): WriteResult {
        val nfc = nfcA ?: return WriteResult.COMMUNICATION_ERROR
        if (!model.matchesDimensions(bitmap.width, bitmap.height)) {
            return WriteResult.DIMENSION_MISMATCH
        }

        var success = false
        return try {
            progress = 0
            success = if (model.protocol == DisplayModel.Protocol.ONE_54_B) {
                writeDisplay154B(nfc, model, bitmap)
            } else {
                // A4 moves this after tag validation/handshake in the next NFC-path cluster.
                writeNdefRecord(nfc)
                writeStandardDisplay(nfc, model, bitmap)
            }

            if (success) {
                WriteResult.SUCCESS
            } else {
                progress = -1
                WriteResult.COMMUNICATION_ERROR
            }
        } catch (e: IOException) {
            if (BuildConfig.DEBUG) e.printStackTrace()
            progress = -1
            WriteResult.COMMUNICATION_ERROR
        } finally {
            // B3: best-effort cleanup on every failure path. A failed cleanup
            // must not mask the original write result.
            if (!success) {
                sendCommand(nfc, 0xCD.toByte(), 0x04)
            }
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
            if (BuildConfig.DEBUG) e.printStackTrace()
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
                    if (BuildConfig.DEBUG) e.printStackTrace()
                }
            }
        }
    }

    /**
     * Standard WaveShare protocol, driven entirely by [DisplayModel]. Model
     * selectors, payload lengths, packet counts, and special passes mirror
     * proxmark3's WaveShare implementation.
     */
    private fun writeStandardDisplay(nfc: NfcA, model: DisplayModel, bitmap: Bitmap): Boolean {
        val modelSelectByte = model.modelSelectByte ?: return false

        if (!sendCommand(nfc, 0xCD.toByte(), 0x0D)) return false
        if (!sendCommand(nfc, 0xCD.toByte(), 0x00, modelSelectByte)) return false
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
        if (!sendCommand(nfc, 0xCD.toByte(), 0x07)) return false

        val processedBitmap = if (
            model.needsRotation && bitmap.width == model.width && bitmap.height == model.height
        ) {
            rotateBitmap(bitmap, 270f)
        } else {
            bitmap
        }

        try {
            prepareImageData(model, processedBitmap)

            val firstPassComplete = when (model.protocol) {
                DisplayModel.Protocol.SINGLE_PASS -> sendDataPass(
                    nfc = nfc,
                    command = 0x08,
                    model = model,
                    source = blackLayerData,
                    progressStart = 0,
                    progressEnd = 100,
                )
                DisplayModel.Protocol.BLANK_THEN_BLACK -> sendDataPass(
                    nfc = nfc,
                    command = 0x08,
                    model = model,
                    fillByte = 0xFF.toByte(),
                    progressStart = 0,
                    progressEnd = 50,
                )
                DisplayModel.Protocol.DUAL_LAYER -> sendDataPass(
                    nfc = nfc,
                    command = 0x08,
                    model = model,
                    // prepareImageData preserves the working 1.54B encoding:
                    // this buffer is the first (black-plane) transmission.
                    source = redLayerData,
                    progressStart = 0,
                    progressEnd = 50,
                )
                DisplayModel.Protocol.ONE_54_B -> false
            }
            if (!firstPassComplete) return false

            if (model.trailingPadding && !sendTrailingPadding(nfc)) return false
            if (!sendCommand(nfc, 0xCD.toByte(), 0x18)) return false

            when (model.protocol) {
                DisplayModel.Protocol.BLANK_THEN_BLACK -> {
                    SystemClock.sleep(100)
                    if (!sendDataPass(
                            nfc = nfc,
                            command = 0x19,
                            model = model,
                            source = blackLayerData,
                            progressStart = 50,
                            progressEnd = 100,
                        )
                    ) return false
                    SystemClock.sleep(100)
                }
                DisplayModel.Protocol.DUAL_LAYER -> {
                    if (!sendDataPass(
                            nfc = nfc,
                            command = 0x19,
                            model = model,
                            source = blackLayerData,
                            progressStart = 50,
                            progressEnd = 100,
                        )
                    ) return false
                }
                DisplayModel.Protocol.SINGLE_PASS,
                DisplayModel.Protocol.ONE_54_B -> Unit
            }

            SystemClock.sleep(200)
            if (!sendCommand(nfc, 0xCD.toByte(), 0x09)) return false

            SystemClock.sleep(model.readyPollDelayMs)
            var attempts = 0
            while (true) {
                attempts++
                val response = nfc.transceive(byteArrayOf(0xCD.toByte(), 0x0A))
                if (isSuccessResponse(response, first = 0xFF.toByte())) {
                    if (!sendCommand(nfc, 0xCD.toByte(), 0x04)) return false
                    progress = 100
                    return true
                }
                if (attempts > 100) return false
                SystemClock.sleep(25)
            }
        } finally {
            if (processedBitmap !== bitmap) {
                processedBitmap.recycle()
            }
        }
    }

    private fun sendDataPass(
        nfc: NfcA,
        command: Byte,
        model: DisplayModel,
        source: ByteArray? = null,
        fillByte: Byte? = null,
        progressStart: Int,
        progressEnd: Int,
    ): Boolean {
        require((source == null) != (fillByte == null)) {
            "Exactly one packet source must be supplied"
        }

        val packet = ByteArray(model.dataBytesPerPacket + 3)
        packet[0] = 0xCD.toByte()
        packet[1] = command
        packet[2] = model.dataBytesPerPacket.toByte()
        if (fillByte != null) {
            packet.fill(fillByte, 3, packet.size)
        }

        for (packetIndex in 0 until model.packetCount) {
            if (source != null) {
                System.arraycopy(
                    source,
                    packetIndex * model.dataBytesPerPacket,
                    packet,
                    3,
                    model.dataBytesPerPacket,
                )
            }
            val response = nfc.transceive(packet)
            if (!isSuccessResponse(response)) return false

            progress = progressStart +
                ((packetIndex + 1) * (progressEnd - progressStart) / model.packetCount)
            SystemClock.sleep(2)
        }
        return true
    }

    /** 7.5" HD requires one final partial white packet after its 484 full packets. */
    private fun sendTrailingPadding(nfc: NfcA): Boolean {
        val packet = ByteArray(113)
        packet[0] = 0xCD.toByte()
        packet[1] = 0x08
        packet[2] = 120
        packet.fill(0xFF.toByte(), 3, packet.size)
        return isSuccessResponse(nfc.transceive(packet))
    }

    private fun isSuccessResponse(
        response: ByteArray,
        first: Byte = 0,
    ): Boolean = response.size >= 2 && response[0] == first && response[1] == 0.toByte()

    /**
     * Write to 1.54" B display (special protocol).
     */
    private fun writeDisplay154B(nfc: NfcA, model: DisplayModel, bitmap: Bitmap): Boolean {
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
        prepareImageData(model, bitmap)

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

    /** Pack bitmap pixels into the exact byte layout declared by [model]. */
    private fun prepareImageData(model: DisplayModel, bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val bytesPerRow = (bitmap.width + 7) / 8
        val packedSize = bytesPerRow * bitmap.height
        val expectedSize = model.dataBytesPerPacket * model.packetCount
        require(packedSize == expectedSize) {
            "${model.label} packing mismatch: $packedSize bytes for ${bitmap.width}x${bitmap.height}, expected $expectedSize"
        }

        blackLayerData.fill(0, 0, packedSize)
        redLayerData.fill(0, 0, packedSize)
        val dualLayer = model.protocol == DisplayModel.Protocol.DUAL_LAYER ||
            model.protocol == DisplayModel.Protocol.ONE_54_B

        for (y in 0 until bitmap.height) {
            for (xByte in 0 until bytesPerRow) {
                var blackByte: Byte = 0
                var redByte: Byte = 0

                for (bit in 0 until 8) {
                    blackByte = (blackByte.toInt() shl 1).toByte()
                    redByte = (redByte.toInt() shl 1).toByte()

                    val x = xByte * 8 + bit
                    val isWhite = if (x >= bitmap.width) {
                        true // white-pad controllers whose width is not byte-aligned
                    } else {
                        val pixel = pixels[x + y * bitmap.width]
                        if (dualLayer) {
                            pixel == -1 // BarcodeGenerator emits pure B/W pixels
                        } else {
                            val gray = ((pixel shr 16) and 0xFF) * 0.299f +
                                ((pixel shr 8) and 0xFF) * 0.587f +
                                (pixel and 0xFF) * 0.114f
                            gray > 128
                        }
                    }

                    if (isWhite) {
                        if (dualLayer) {
                            // Preserves the hardware-verified 1.54B encoding:
                            // redLayerData is transmitted first as the black plane.
                            redByte = (redByte.toInt() or 1).toByte()
                        } else {
                            blackByte = (blackByte.toInt() or 1).toByte()
                        }
                    }
                }

                val dataIndex = y * bytesPerRow + xByte
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
            isSuccessResponse(nfc.transceive(command))
        } catch (e: IOException) {
            if (BuildConfig.DEBUG) e.printStackTrace()
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
