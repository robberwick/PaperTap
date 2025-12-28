package com.robberwick.papertap

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.robberwick.papertap.database.TicketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AddTicketActivity : AppCompatActivity() {

    private lateinit var ticketRepository: TicketRepository
    private lateinit var preferences: Preferences

    private lateinit var qrCodePreview: ImageView
    private lateinit var labelInput: TextInputEditText
    private lateinit var cancelButton: Button
    private lateinit var addButton: Button

    private var extractedQrBitmap: Bitmap? = null
    private var extractedRawData: String? = null
    private var extractedBarcodeFormat: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.d("AddTicketActivity", "onCreate - Activity starting")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_ticket)

        ticketRepository = TicketRepository(this)
        preferences = Preferences(this)

        // Find views
        qrCodePreview = findViewById(R.id.qrCodePreview)
        labelInput = findViewById(R.id.labelInput)
        cancelButton = findViewById(R.id.cancelButton)
        addButton = findViewById(R.id.addButton)

        // Setup buttons
        cancelButton.setOnClickListener { finish() }
        addButton.setOnClickListener { addTicket() }

        // Get document URI from intent
        val documentUriString = intent.getStringExtra("DOCUMENT_URI")
        android.util.Log.d("AddTicketActivity", "onCreate - DOCUMENT_URI: $documentUriString")
        if (documentUriString != null) {
            val uri = Uri.parse(documentUriString)
            android.util.Log.d("AddTicketActivity", "onCreate - Parsed URI: $uri")
            processDocument(uri)
        } else {
            android.util.Log.e("AddTicketActivity", "onCreate - No DOCUMENT_URI provided!")
            Toast.makeText(this, "No document provided", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun processDocument(uri: Uri) {
        android.util.Log.d("AddTicketActivity", "processDocument - URI: $uri")
        android.util.Log.d("AddTicketActivity", "processDocument - URI scheme: ${uri.scheme}")

        // Check if this is an HTTP/HTTPS URL (shared from Firefox)
        if (uri.scheme == "http" || uri.scheme == "https") {
            android.util.Log.d("AddTicketActivity", "Detected HTTP/HTTPS URL, downloading...")
            Toast.makeText(this, "Downloading ticket...", Toast.LENGTH_SHORT).show()
            downloadAndProcessPdf(uri)
            return
        }

        // Handle local file URIs
        val mimeType = contentResolver.getType(uri)
        android.util.Log.d("AddTicketActivity", "processDocument - MIME type: $mimeType")
        Toast.makeText(this, "Processing document...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (mimeType == "application/pdf") {
                        processPdf(uri)
                    } else if (mimeType?.startsWith("image/") == true) {
                        processImage(uri)
                    } else {
                        null
                    }
                }

                if (result != null) {
                    val (qrBitmap, barcodeData) = result
                    extractedQrBitmap = qrBitmap
                    extractedRawData = barcodeData.rawData
                    extractedBarcodeFormat = barcodeData.barcodeFormat
                    displayPreview(qrBitmap)
                } else {
                    Toast.makeText(
                        this@AddTicketActivity,
                        "No QR code found in document",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@AddTicketActivity,
                    "Error processing document: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun downloadAndProcessPdf(url: Uri) {
        lifecycleScope.launch {
            try {
                val tempFile = withContext(Dispatchers.IO) {
                    // Create a temporary file for the downloaded PDF
                    val tempFile = File.createTempFile("downloaded_ticket", ".pdf", cacheDir)
                    android.util.Log.d("AddTicketActivity", "Downloading to: ${tempFile.absolutePath}")

                    // Download the PDF
                    val connection = java.net.URL(url.toString()).openConnection() as java.net.HttpURLConnection
                    connection.connect()

                    if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                        throw Exception("Server returned HTTP ${connection.responseCode}")
                    }

                    connection.inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    android.util.Log.d("AddTicketActivity", "Download complete, file size: ${tempFile.length()} bytes")
                    tempFile
                }

                // Convert to URI and process as PDF
                val fileUri = Uri.fromFile(tempFile)
                android.util.Log.d("AddTicketActivity", "Processing downloaded PDF: $fileUri")
                Toast.makeText(this@AddTicketActivity, "Processing ticket...", Toast.LENGTH_SHORT).show()

                val result = withContext(Dispatchers.IO) {
                    processPdf(fileUri)
                }

                // Clean up temp file
                tempFile.delete()

                if (result != null) {
                    val (qrBitmap, barcodeData) = result
                    extractedQrBitmap = qrBitmap
                    extractedRawData = barcodeData.rawData
                    extractedBarcodeFormat = barcodeData.barcodeFormat
                    displayPreview(qrBitmap)
                } else {
                    Toast.makeText(
                        this@AddTicketActivity,
                        "No QR code found in ticket",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            } catch (e: Exception) {
                android.util.Log.e("AddTicketActivity", "Error downloading/processing PDF", e)
                e.printStackTrace()
                Toast.makeText(
                    this@AddTicketActivity,
                    "Error downloading ticket: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private suspend fun processPdf(uri: Uri): Pair<Bitmap, BarcodeData>? {
        val extractor = PdfQrExtractor(this)
        val padding = preferences.getQrPadding()
        return extractor.extractQrCodeFromPdf(uri, padding)
    }

    private suspend fun processImage(uri: Uri): Pair<Bitmap, BarcodeData>? {
        val bitmap = contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        } ?: return null

        val padding = preferences.getQrPadding()
        return extractQrFromBitmap(bitmap, padding)
    }

    private suspend fun extractQrFromBitmap(bitmap: Bitmap, padding: Int): Pair<Bitmap, BarcodeData>? = suspendCoroutine { continuation ->
        val options = com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_AZTEC,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_DATA_MATRIX,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_PDF417
            )
            .build()

        val scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient(options)
        val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    val barcode = barcodes[0]
                    val boundingBox = barcode.boundingBox
                    val rawValue = barcode.rawValue

                    if (rawValue == null) {
                        continuation.resume(null)
                        return@addOnSuccessListener
                    }

                    // Create BarcodeData object
                    val barcodeData = BarcodeData(
                        rawData = rawValue,
                        barcodeFormat = barcode.format
                    )

                    if (boundingBox != null) {
                        val left = maxOf(0, boundingBox.left - padding)
                        val top = maxOf(0, boundingBox.top - padding)
                        val width = minOf(bitmap.width - left, boundingBox.width() + padding * 2)
                        val height = minOf(bitmap.height - top, boundingBox.height() + padding * 2)

                        val croppedBitmap = Bitmap.createBitmap(bitmap, left, top, width, height)
                        continuation.resume(Pair(croppedBitmap, barcodeData))
                    } else {
                        continuation.resume(null)
                    }
                } else {
                    continuation.resume(null)
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                continuation.resume(null)
            }
    }

    private fun displayPreview(qrBitmap: Bitmap) {
        // Display QR code
        qrCodePreview.setImageBitmap(qrBitmap)
    }

    private fun addTicket() {
        val rawData = extractedRawData
        val barcodeFormat = extractedBarcodeFormat

        if (rawData == null || barcodeFormat == null) {
            Toast.makeText(this, "No barcode to save", Toast.LENGTH_SHORT).show()
            return
        }

        // Get label from input or generate default
        val label = labelInput.text?.toString()?.trim().let {
            if (it.isNullOrEmpty()) {
                generateDefaultLabel()
            } else {
                it
            }
        }

        lifecycleScope.launch {
            try {
                // Insert ticket - repository will check for duplicates
                val ticketId = withContext(Dispatchers.IO) {
                    ticketRepository.insertTicket(rawData, barcodeFormat, label)
                }

                // Check if it was a duplicate
                val ticket = withContext(Dispatchers.IO) {
                    ticketRepository.getById(ticketId)
                }

                if (ticket != null && ticket.userLabel != label) {
                    // Duplicate found (label doesn't match what we tried to insert)
                    Toast.makeText(
                        this@AddTicketActivity,
                        "This ticket is already in your collection",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this@AddTicketActivity, "Ticket added!", Toast.LENGTH_SHORT).show()
                }

                finish()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@AddTicketActivity,
                    "Error saving ticket: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun generateDefaultLabel(): String {
        val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
        return "Ticket ${dateFormat.format(Date())}"
    }
}
