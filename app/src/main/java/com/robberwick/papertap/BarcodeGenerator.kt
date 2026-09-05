package com.robberwick.papertap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/** Text rendered beneath a generated barcode. */
data class BarcodeLabel(
    val text: String,
    val sizeMultiplier: Float = 1.0f,
)

object BarcodeGenerator {
    fun generateBarcodeWithLabel(
        rawData: String,
        format: BarcodeFormat,
        width: Int,
        height: Int,
        edgePadding: Int = 0,
        labels: List<BarcodeLabel> = emptyList(),
    ): Bitmap {
        require(width > edgePadding * 2 && height > edgePadding * 2) {
            "Edge padding leaves no room for the barcode"
        }

        val availableWidth = width - edgePadding * 2
        val availableHeight = height - edgePadding * 2
        val nonEmptyLabels = labels.filter { it.text.isNotEmpty() }
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result).apply { drawColor(Color.WHITE) }

        if (nonEmptyLabels.isEmpty()) {
            val (requestedWidth, requestedHeight) = requestedDimensions(
                format,
                availableWidth,
                availableHeight,
            )
            val barcode = generateBarcode(rawData, format, requestedWidth, requestedHeight)
            canvas.drawBitmap(
                barcode,
                (width - barcode.width) / 2f,
                (height - barcode.height) / 2f,
                null,
            )
            barcode.recycle()
            return result
        }

        val baseTextSizePx = (availableHeight / 10f).coerceAtLeast(14f)
        val spacing = (availableWidth / 50f).toInt().coerceAtLeast(2)
        data class LabelMetrics(val label: BarcodeLabel, val height: Int, val paint: Paint)

        val labelMetrics = nonEmptyLabels.map { label ->
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = baseTextSizePx * label.sizeMultiplier
                typeface = Typeface.MONOSPACE
                isAntiAlias = false
                textAlign = Paint.Align.CENTER
            }
            val textBounds = Rect()
            paint.getTextBounds(label.text, 0, label.text.length, textBounds)
            LabelMetrics(label, textBounds.height(), paint)
        }
        val totalTextHeight = labelMetrics.sumOf { it.height + spacing } + spacing
        val barcodeAvailableHeight = (availableHeight - totalTextHeight).coerceAtLeast(1)
        val (requestedWidth, requestedHeight) = requestedDimensions(
            format,
            availableWidth,
            barcodeAvailableHeight,
        )
        val barcode = generateBarcode(rawData, format, requestedWidth, requestedHeight)
        val barcodeX = (width - barcode.width) / 2f
        val barcodeY = edgePadding.toFloat()
        canvas.drawBitmap(barcode, barcodeX, barcodeY, null)

        val textX = width / 2f
        var currentY = barcodeY + barcode.height + spacing
        for (metrics in labelMetrics) {
            currentY += metrics.height
            canvas.drawText(metrics.label.text, textX, currentY, metrics.paint)
            currentY += spacing
        }
        barcode.recycle()
        return result
    }

    private fun generateBarcode(
        rawData: String,
        format: BarcodeFormat,
        width: Int,
        height: Int,
    ): Bitmap {
        val marginModules = when (format) {
            BarcodeFormat.QR_CODE -> 4 // QR specification quiet zone
            BarcodeFormat.AZTEC -> 0
            else -> 1
        }
        val bitMatrix = MultiFormatWriter().encode(
            rawData,
            format,
            width,
            height,
            mapOf(EncodeHintType.MARGIN to marginModules),
        )
        return bitMatrixToBitmap(bitMatrix)
    }

    private fun requestedDimensions(
        format: BarcodeFormat,
        availableWidth: Int,
        availableHeight: Int,
    ): Pair<Int, Int> {
        return if (format == BarcodeFormat.PDF_417) {
            availableWidth to availableHeight
        } else {
            val size = minOf(availableWidth, availableHeight)
            size to size
        }
    }

    private fun bitMatrixToBitmap(bitMatrix: BitMatrix): Bitmap {
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                pixels[rowOffset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
}
