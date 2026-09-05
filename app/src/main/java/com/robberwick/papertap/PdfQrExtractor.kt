package com.robberwick.papertap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

sealed interface BarcodeExtractionResult {
    data class Success(
        val bitmap: Bitmap,
        val barcodeData: BarcodeData,
    ) : BarcodeExtractionResult

    data object NoBarcode : BarcodeExtractionResult

    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : BarcodeExtractionResult
}

class PdfQrExtractor(private val context: Context) : Closeable {
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_AZTEC,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_PDF417,
            )
            .build(),
    )

    suspend fun extractQrCodeFromPdf(
        pdfUri: Uri,
        padding: Int = 5,
    ): BarcodeExtractionResult {
        return try {
            val descriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: return BarcodeExtractionResult.Error("The PDF could not be opened")

            descriptor.use { fileDescriptor ->
                PdfRenderer(fileDescriptor).use { renderer ->
                    val pagesToScan = minOf(renderer.pageCount, MAX_PAGES)
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Scanning $pagesToScan of ${renderer.pageCount} PDF pages")
                    }

                    for (pageIndex in 0 until pagesToScan) {
                        val bitmap = renderer.openPage(pageIndex).use { page ->
                            val scale = minOf(
                                MAX_RENDER_SCALE,
                                MAX_RENDER_DIMENSION.toFloat() / maxOf(page.width, page.height),
                            )
                            val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                            val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                                it.eraseColor(Color.WHITE)
                                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }

                        when (val result = extractQrFromBitmap(bitmap, padding)) {
                            is BarcodeExtractionResult.Success -> {
                                bitmap.recycle()
                                return result
                            }
                            is BarcodeExtractionResult.Error -> {
                                bitmap.recycle()
                                return result
                            }
                            BarcodeExtractionResult.NoBarcode -> bitmap.recycle()
                        }
                    }

                    BarcodeExtractionResult.NoBarcode
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting barcode from PDF", e)
            BarcodeExtractionResult.Error("The PDF could not be read", e)
        }
    }

    suspend fun extractQrFromBitmap(
        bitmap: Bitmap,
        padding: Int,
    ): BarcodeExtractionResult = suspendCoroutine { continuation ->
        scanner.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { barcodes ->
                val barcode = selectBarcode(barcodes)
                val rawValue = barcode?.rawValue
                val boundingBox = barcode?.boundingBox
                if (barcode == null || rawValue == null || boundingBox == null) {
                    continuation.resume(BarcodeExtractionResult.NoBarcode)
                    return@addOnSuccessListener
                }

                val crop = paddedCrop(boundingBox, bitmap.width, bitmap.height, padding)
                val cropped = Bitmap.createBitmap(
                    bitmap,
                    crop.left,
                    crop.top,
                    crop.width(),
                    crop.height(),
                )
                val independentCrop = if (cropped === bitmap) {
                    bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                } else {
                    cropped
                }
                continuation.resume(
                    BarcodeExtractionResult.Success(
                        bitmap = independentCrop,
                        barcodeData = BarcodeData(rawValue, barcode.format),
                    ),
                )
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Barcode scanning failed", error)
                continuation.resume(
                    BarcodeExtractionResult.Error("Barcode scanning failed", error),
                )
            }
    }

    override fun close() {
        scanner.close()
    }

    private fun selectBarcode(barcodes: List<Barcode>): Barcode? {
        return barcodes.asSequence()
            .filter { it.rawValue != null && it.boundingBox != null }
            .sortedWith(
                compareByDescending<Barcode> { it.format == Barcode.FORMAT_AZTEC }
                    .thenByDescending { barcode ->
                        barcode.boundingBox?.let { it.width() * it.height() } ?: 0
                    },
            )
            .firstOrNull()
    }

    private fun paddedCrop(
        bounds: Rect,
        bitmapWidth: Int,
        bitmapHeight: Int,
        padding: Int,
    ): Rect {
        val safePadding = padding.coerceAtLeast(0)
        return Rect(
            (bounds.left - safePadding).coerceAtLeast(0),
            (bounds.top - safePadding).coerceAtLeast(0),
            (bounds.right + safePadding).coerceAtMost(bitmapWidth),
            (bounds.bottom + safePadding).coerceAtMost(bitmapHeight),
        )
    }

    companion object {
        private const val TAG = "PdfQrExtractor"
        private const val MAX_PAGES = 20
        private const val MAX_RENDER_DIMENSION = 2_500
        private const val MAX_RENDER_SCALE = 3f
    }
}
