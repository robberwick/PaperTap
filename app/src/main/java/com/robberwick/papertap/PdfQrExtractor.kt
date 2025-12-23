package com.robberwick.papertap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PdfQrExtractor(private val context: Context) {
    
    private val TAG = "PdfQrExtractor"

    suspend fun extractQrCodeFromPdf(pdfUri: Uri, padding: Int = 5): Pair<Bitmap, TicketData?>? {
        var fileDescriptor: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null
        
        try {
            fileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
            if (fileDescriptor == null) {
                Log.e(TAG, "Failed to open file descriptor")
                return null
            }
            
            pdfRenderer = PdfRenderer(fileDescriptor)
            Log.d(TAG, "PDF has ${pdfRenderer.pageCount} pages")
            
            // Scan all pages for QR code
            for (pageIndex in 0 until pdfRenderer.pageCount) {
                Log.d(TAG, "Scanning page $pageIndex")
                val page = pdfRenderer.openPage(pageIndex)
                
                // Render page to bitmap at high resolution for better QR detection
                val bitmap = Bitmap.createBitmap(
                    page.width * 3,
                    page.height * 3,
                    Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                
                // Try to find QR code in this page
                val result = extractQrFromBitmap(bitmap, padding)
                if (result != null) {
                    Log.d(TAG, "QR code found on page $pageIndex")
                    return result
                }
                
                bitmap.recycle()
            }
            
            Log.d(TAG, "No QR code found in any page")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting QR from PDF", e)
            e.printStackTrace()
            return null
        } finally {
            pdfRenderer?.close()
            fileDescriptor?.close()
        }
    }
    
    private suspend fun extractQrFromBitmap(bitmap: Bitmap, padding: Int): Pair<Bitmap, TicketData?>? = suspendCoroutine { continuation ->
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_AZTEC,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_PDF417
            )
            .build()
        
        val scanner = BarcodeScanning.getClient(options)
        val image = InputImage.fromBitmap(bitmap, 0)
        
        Log.d(TAG, "Starting barcode scan on bitmap ${bitmap.width}x${bitmap.height}")
        
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                Log.d(TAG, "Scan complete. Found ${barcodes.size} barcodes")
                
                if (barcodes.isNotEmpty()) {
                    val barcode = barcodes[0]
                    val boundingBox = barcode.boundingBox
                    
                    Log.d(TAG, "Barcode format: ${barcode.format}, value: ${barcode.rawValue?.take(50)}")
                    
                    // Attempt to decode if it's an Aztec code
                    var ticketData: TicketData? = null
                    if (barcode.format == Barcode.FORMAT_AZTEC && barcode.rawValue != null) {
                        ticketData = decodeTicketData(barcode.rawValue!!)
                    }
                    
                    if (boundingBox != null) {
                        Log.d(TAG, "Bounding box: $boundingBox")
                        // Use padding from settings
                        Log.d(TAG, "Using padding: $padding pixels")
                        val left = maxOf(0, boundingBox.left - padding)
                        val top = maxOf(0, boundingBox.top - padding)
                        val width = minOf(bitmap.width - left, boundingBox.width() + padding * 2)
                        val height = minOf(bitmap.height - top, boundingBox.height() + padding * 2)
                        
                        val croppedBitmap = Bitmap.createBitmap(bitmap, left, top, width, height)
                        Log.d(TAG, "Cropped bitmap: ${croppedBitmap.width}x${croppedBitmap.height}")
                        continuation.resume(Pair(croppedBitmap, ticketData))
                    } else {
                        Log.w(TAG, "Barcode found but no bounding box")
                        continuation.resume(null)
                    }
                } else {
                    Log.d(TAG, "No barcodes found")
                    continuation.resume(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Barcode scanning failed", e)
                e.printStackTrace()
                continuation.resume(null)
            }
    }
    
    private fun decodeTicketData(rawValue: String): TicketData? {
        return try {
            Log.d(TAG, "Attempting to decode Aztec ticket data")
            val ticket = com.robberwick.rsp6.Rsp6Decoder.decode(rawValue)
            
            // Initialize lookups if not already done
            StationLookup.init(context)
            FareCodeLookup.init(context)
            
            // Convert NLC codes to station names
            val originStation = StationLookup.getStationName(ticket.originNlc)
            val destinationStation = StationLookup.getStationName(ticket.destinationNlc)
            
            // Convert fare code to human-readable name
            val fareName = FareCodeLookup.getFareName(ticket.fare)
            
            TicketData(
                originStation = originStation,
                destinationStation = destinationStation,
                travelDate = ticket.startDate.toString(),
                travelTime = ticket.departTime.toString(),
                ticketType = fareName,
                railcardType = if (ticket.discountCode > 0) "Code ${ticket.discountCode}" else null,
                ticketClass = if (ticket.standardClass) "Standard" else "First",
                ticketReference = ticket.ticketReference,
                rawData = rawValue
            )
        } catch (e: com.robberwick.rsp6.Rsp6DecoderException) {
            Log.e(TAG, "Failed to decode RSP6 ticket", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode ticket data", e)
            null
        }
    }
}
