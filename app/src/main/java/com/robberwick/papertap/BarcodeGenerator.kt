package com.robberwick.papertap

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/**
 * Represents a text label with an optional size multiplier for barcode generation.
 * @param text The text to display
 * @param sizeMultiplier The font size multiplier relative to base size (default 1.0)
 */
data class BarcodeLabel(
    val text: String,
    val sizeMultiplier: Float = 1.0f
)

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
     * Generates a barcode with optional text lines below it.
     * The barcode is scaled to fit within the target dimensions while maintaining aspect ratio,
     * with space reserved for the text at the bottom.
     *
     * @param rawData The raw barcode data (string content)
     * @param format The barcode format (AZTEC, QR_CODE, etc.)
     * @param width The desired final width in pixels
     * @param height The desired final height in pixels
     * @param edgePadding The padding in pixels around the barcode and text
     * @param labels List of text labels with size multipliers to add below the barcode (empty list for no labels)
     * @return The generated barcode with optional text, scaled to fit target dimensions
     */
    fun generateBarcodeWithLabel(
        rawData: String,
        format: BarcodeFormat,
        width: Int,
        height: Int,
        edgePadding: Int = 0,
        labels: List<BarcodeLabel> = emptyList()
    ): Bitmap {
        android.util.Log.d("BarcodeGenerator", "generateBarcodeWithLabel called")
        android.util.Log.d("BarcodeGenerator", "  labels: $labels")
        android.util.Log.d("BarcodeGenerator", "  width: $width, height: $height, edgePadding: $edgePadding")

        // Calculate available space after accounting for edge padding on all sides
        val availableWidth = width - (edgePadding * 2)
        val availableHeight = height - (edgePadding * 2)

        // Filter out empty labels
        val nonEmptyLabels = labels.filter { it.text.isNotEmpty() }

        // If no label text, generate plain barcode within padded area
        if (nonEmptyLabels.isEmpty()) {
            android.util.Log.d("BarcodeGenerator", "  No labels, returning plain barcode")

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

        android.util.Log.d("BarcodeGenerator", "  Adding ${nonEmptyLabels.size} label(s) to barcode")

        // Calculate base text size (optimized for small metadata labels)
        val baseTextSizePx = (availableHeight / 10f).coerceAtLeast(14f)

        // Calculate spacing (tight spacing to maximize barcode size)
        val spacing = (availableWidth / 50f).toInt().coerceAtLeast(2)

        // Measure each label's height with its specific size multiplier
        data class LabelMetrics(val label: BarcodeLabel, val height: Int, val paint: android.graphics.Paint)
        val labelMetrics = nonEmptyLabels.map { label ->
            val paint = android.graphics.Paint().apply {
                color = Color.BLACK
                textSize = baseTextSizePx * label.sizeMultiplier
                typeface = android.graphics.Typeface.MONOSPACE
                isAntiAlias = false  // Keep crisp for e-ink
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val textBounds = android.graphics.Rect()
            paint.getTextBounds(label.text, 0, label.text.length, textBounds)
            LabelMetrics(label, textBounds.height(), paint)
        }

        // Calculate total text area height
        // Each line needs its specific height + spacing below it
        // Plus one spacing above the first line
        val totalTextHeight = labelMetrics.sumOf { it.height + spacing } + spacing

        // Calculate available space for barcode (within the padded area)
        val barcodeAvailableHeight = availableHeight - totalTextHeight

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

        // Draw each label below the barcode with its specific size
        val textX = width / 2f
        var currentY = barcodeY + barcodeSize + spacing

        for (metrics in labelMetrics) {
            currentY += metrics.height.toFloat()
            canvas.drawText(metrics.label.text, textX, currentY, metrics.paint)
            currentY += spacing
        }

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
