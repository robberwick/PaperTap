package com.robberwick.papertap

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/**
 * Utility class for generating barcode images from raw data using ZXing.
 */
object BarcodeGenerator {

    /**
     * Generates a barcode bitmap from raw data.
     *
     * @param rawData The raw barcode data (string content)
     * @param format The barcode format (AZTEC, QR_CODE, etc.)
     * @param width The desired width in pixels
     * @param height The desired height in pixels
     * @param padding The padding in pixels around the barcode (default: 5)
     * @return The generated barcode as a black/white bitmap
     * @throws Exception if encoding fails
     */
    fun generateBarcode(
        rawData: String,
        format: BarcodeFormat,
        width: Int,
        height: Int
    ): Bitmap {
        // Use a minimal fixed margin for the barcode quiet zone
        val hints = hashMapOf<EncodeHintType, Any>(
            EncodeHintType.MARGIN to 1
        )

        val writer = MultiFormatWriter()
        val bitMatrix: BitMatrix = writer.encode(
            rawData,
            format,
            width,
            height,
            hints
        )

        return bitMatrixToBitmap(bitMatrix)
    }

    /**
     * Generates an Aztec code bitmap from raw data.
     * Convenience method for Aztec code generation.
     *
     * @param rawData The raw barcode data (string content)
     * @param size The desired size in pixels (width and height)
     * @param padding The padding in pixels around the barcode (default: 5)
     * @return The generated Aztec code as a black/white bitmap
     */
    fun generateAztecCode(
        rawData: String,
        size: Int
    ): Bitmap {
        return generateBarcode(rawData, BarcodeFormat.AZTEC, size, size)
    }

    /**
     * Generates a QR code bitmap from raw data.
     * Convenience method for QR code generation.
     *
     * @param rawData The raw barcode data (string content)
     * @param size The desired size in pixels (width and height)
     * @param padding The padding in pixels around the barcode (default: 5)
     * @return The generated QR code as a black/white bitmap
     */
    fun generateQRCode(
        rawData: String,
        size: Int
    ): Bitmap {
        return generateBarcode(rawData, BarcodeFormat.QR_CODE, size, size)
    }

    /**
     * Generates a barcode with optional ticket reference text below it.
     * The barcode is scaled to fit within the target dimensions while maintaining aspect ratio,
     * with space reserved for the text at the bottom.
     *
     * @param rawData The raw barcode data (string content)
     * @param format The barcode format (AZTEC, QR_CODE, etc.)
     * @param width The desired final width in pixels
     * @param height The desired final height in pixels
     * @param padding The padding in pixels around the barcode
     * @param ticketReference Optional ticket reference to add below the barcode
     * @return The generated barcode with optional text, scaled to fit target dimensions
     */
    fun generateBarcodeWithReference(
        rawData: String,
        format: BarcodeFormat,
        width: Int,
        height: Int,
        edgePadding: Int = 0,
        ticketReference: String? = null
    ): Bitmap {
        android.util.Log.d("BarcodeGenerator", "generateBarcodeWithReference called")
        android.util.Log.d("BarcodeGenerator", "  ticketReference: '$ticketReference'")
        android.util.Log.d("BarcodeGenerator", "  width: $width, height: $height, edgePadding: $edgePadding")

        // Calculate available space after accounting for edge padding on all sides
        val availableWidth = width - (edgePadding * 2)
        val availableHeight = height - (edgePadding * 2)

        // If no reference text, generate plain barcode within padded area
        if (ticketReference.isNullOrEmpty()) {
            android.util.Log.d("BarcodeGenerator", "  No ticket reference, returning plain barcode")

            // Keep barcode square
            val barcodeSize = minOf(availableWidth, availableHeight)
            val barcode = generateBarcode(rawData, format, barcodeSize, barcodeSize)

            // Create result with padding
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(result)
            canvas.drawColor(Color.WHITE)

            // Center the barcode with edge padding
            val x = ((width - barcodeSize) / 2f)
            val y = ((height - barcodeSize) / 2f)
            canvas.drawBitmap(barcode, x, y, null)

            barcode.recycle()
            return result
        }

        android.util.Log.d("BarcodeGenerator", "  Adding ticket reference to barcode")

        // Calculate text size (about 2/15th of the available height, doubled for readability)
        val textSizePx = (availableHeight / 15f * 2f).coerceAtLeast(24f)

        // Create paint for text
        val paint = android.graphics.Paint().apply {
            color = Color.BLACK
            textSize = textSizePx
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = false  // Keep crisp for e-ink
            textAlign = android.graphics.Paint.Align.CENTER
        }

        // Measure text dimensions
        val textBounds = android.graphics.Rect()
        paint.getTextBounds(ticketReference, 0, ticketReference.length, textBounds)
        val textHeight = textBounds.height()

        // Calculate spacing (about 1/20th of available width)
        val spacing = (availableWidth / 20f).toInt().coerceAtLeast(5)

        // Calculate how much space the text + spacing needs
        val textAreaHeight = spacing + textHeight + spacing

        // Calculate available space for barcode (within the padded area)
        val barcodeAvailableHeight = availableHeight - textAreaHeight

        // CRITICAL: Keep barcode square to maintain aspect ratio for 2D barcodes
        val barcodeSize = minOf(availableWidth, barcodeAvailableHeight)

        // Generate square barcode at the calculated size
        val barcode = generateBarcode(rawData, format, barcodeSize, barcodeSize)

        // Create composite bitmap at full target dimensions
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)

        // Fill with white background
        canvas.drawColor(Color.WHITE)

        // Draw the barcode centered horizontally with edge padding at top
        val barcodeX = ((width - barcodeSize) / 2f)
        val barcodeY = edgePadding.toFloat()
        canvas.drawBitmap(barcode, barcodeX, barcodeY, null)

        // Draw the text below the barcode with TR prefix
        val textX = width / 2f
        val textY = barcodeY + barcodeSize + spacing + textHeight.toFloat()
        canvas.drawText("TR$ticketReference", textX, textY, paint)

        // Clean up
        barcode.recycle()

        return result
    }

    /**
     * Converts a ZXing BitMatrix to an Android Bitmap.
     * Creates a pure black/white image suitable for e-paper displays.
     *
     * @param bitMatrix The BitMatrix from ZXing encoding
     * @return A black/white bitmap
     */
    private fun bitMatrixToBitmap(bitMatrix: BitMatrix): Bitmap {
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        return bitmap
    }
}
