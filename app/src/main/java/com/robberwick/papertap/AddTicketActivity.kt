package com.robberwick.papertap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.robberwick.papertap.database.TicketEntity
import com.robberwick.papertap.database.TicketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AddTicketActivity : AppCompatActivity() {

    private lateinit var ticketRepository: TicketRepository
    private lateinit var preferences: Preferences

    private lateinit var qrCodePreview: ImageView
    private lateinit var ticketDetailsCard: MaterialCardView
    private lateinit var ticketJourneySummary: TextView
    private lateinit var ticketDateTime: TextView
    private lateinit var ticketType: TextView
    private lateinit var ticketReference: TextView
    private lateinit var cancelButton: Button
    private lateinit var addButton: Button

    private var extractedQrBitmap: Bitmap? = null
    private var extractedTicketData: TicketData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.d("AddTicketActivity", "onCreate - Activity starting")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_ticket)

        ticketRepository = TicketRepository(this)
        preferences = Preferences(this)

        // Find views
        qrCodePreview = findViewById(R.id.qrCodePreview)
        ticketDetailsCard = findViewById(R.id.ticketDetailsCard)
        ticketJourneySummary = findViewById(R.id.ticketJourneySummary)
        ticketDateTime = findViewById(R.id.ticketDateTime)
        ticketType = findViewById(R.id.ticketType)
        ticketReference = findViewById(R.id.ticketReference)
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
                    extractedTicketData = barcodeData.ticketData
                    displayPreview(qrBitmap, barcodeData.ticketData)
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
                    extractedTicketData = barcodeData.ticketData
                    displayPreview(qrBitmap, barcodeData.ticketData)
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

                    // Attempt to decode if it's an Aztec code
                    var ticketData: TicketData? = null
                    if (barcode.format == com.google.mlkit.vision.barcode.common.Barcode.FORMAT_AZTEC) {
                        ticketData = decodeTicketData(rawValue, barcode.format)
                    }

                    // Create BarcodeData object
                    val barcodeData = BarcodeData(
                        rawData = rawValue,
                        barcodeFormat = barcode.format,
                        ticketData = ticketData
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

    private fun decodeTicketData(rawValue: String, barcodeFormat: Int): TicketData? {
        return try {
            val ticket = com.robberwick.rsp6.Rsp6Decoder.decode(rawValue)

            // Initialize lookups
            StationLookup.init(this)
            FareCodeLookup.init(this)

            val originStation = StationLookup.getStationName(ticket.originNlc)
            val destinationStation = StationLookup.getStationName(ticket.destinationNlc)
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
                rawData = rawValue,
                barcodeFormat = barcodeFormat
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun displayPreview(qrBitmap: Bitmap, ticketData: TicketData?) {
        // Display QR code
        qrCodePreview.setImageBitmap(qrBitmap)

        // Display ticket details if available
        if (ticketData != null) {
            ticketDetailsCard.visibility = View.VISIBLE

            // Journey summary
            val origin = ticketData.originStation ?: "Unknown"
            val dest = ticketData.destinationStation ?: "Unknown"
            ticketJourneySummary.text = "$origin → $dest"

            // Date and time
            val date = ticketData.travelDate ?: ""
            val time = ticketData.travelTime ?: ""
            val shouldShowTime = time.isNotEmpty() && time != "00:00"

            if (date.isNotEmpty()) {
                ticketDateTime.text = if (shouldShowTime) "$date $time" else date
                ticketDateTime.visibility = View.VISIBLE
            } else {
                ticketDateTime.visibility = View.GONE
            }

            // Ticket type and class
            val typeText = buildString {
                ticketData.ticketType?.let { append(it) }
                if (ticketData.ticketClass != null && ticketData.ticketType != null) {
                    append(" • ")
                }
                ticketData.ticketClass?.let { append(it) }
            }

            if (typeText.isNotEmpty()) {
                ticketType.text = typeText
                ticketType.visibility = View.VISIBLE
            } else {
                ticketType.visibility = View.GONE
            }

            // Ticket reference
            val formattedRef = ticketData.getFormattedReference()
            if (formattedRef != null) {
                ticketReference.text = formattedRef
                ticketReference.visibility = View.VISIBLE
            } else {
                ticketReference.visibility = View.GONE
            }
        } else {
            // No ticket data - just show generic message
            ticketDetailsCard.visibility = View.VISIBLE
            ticketJourneySummary.text = "QR Code"
            ticketDateTime.visibility = View.GONE
            ticketType.visibility = View.GONE
            ticketReference.visibility = View.GONE
        }
    }

    private fun addTicket() {
        val qrBitmap = extractedQrBitmap
        if (qrBitmap == null) {
            Toast.makeText(this, "No QR code to save", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Check for duplicate ticket
                val existingTicket = withContext(Dispatchers.IO) {
                    ticketRepository.checkForDuplicate(extractedTicketData)
                }

                if (existingTicket != null) {
                    // Duplicate found - show message and close
                    val message = if (extractedTicketData != null) {
                        "This ticket is already in your collection"
                    } else {
                        "This QR code has already been added"
                    }
                    Toast.makeText(this@AddTicketActivity, message, Toast.LENGTH_LONG).show()
                    finish()
                    return@launch
                }

                // Create ticket entity (temporarily with placeholder path)
                val ticketEntity = TicketEntity.fromTicketData(
                    extractedTicketData,
                    ""  // Will be updated after we get the ID
                )

                // Insert into database to get ID
                val ticketId = withContext(Dispatchers.IO) {
                    ticketRepository.insert(ticketEntity)
                }

                // Save QR code image with the generated ID
                val imagePath = ticketRepository.getQrCodeImagePath(ticketId)
                withContext(Dispatchers.IO) {
                    saveQrCodeImage(qrBitmap, imagePath)
                }

                // Update entity with correct path
                val updatedEntity = ticketEntity.copy(
                    id = ticketId,
                    qrCodeImagePath = imagePath
                )
                withContext(Dispatchers.IO) {
                    ticketRepository.update(updatedEntity)
                }

                Toast.makeText(this@AddTicketActivity, "Ticket added!", Toast.LENGTH_SHORT).show()
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

    private fun saveQrCodeImage(bitmap: Bitmap, path: String) {
        val (sw, sh) = preferences.getScreenSizePixels()

        // Add reference text if enabled
        val withReference = addTicketReferenceToImage(bitmap, extractedTicketData?.ticketReference)

        // Convert to black and white
        val processedBitmap = convertToBlackAndWhite(withReference)

        // Scale to screen size
        val scaledBitmap = Bitmap.createScaledBitmap(processedBitmap, sw, sh, false)

        // Save to file
        FileOutputStream(File(path)).use { output ->
            scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun convertToBlackAndWhite(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val threshold = 128

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = source.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

                val newColor = if (luminance < threshold) Color.BLACK else Color.WHITE
                result.setPixel(x, y, newColor)
            }
        }

        return result
    }

    private fun addTicketReferenceToImage(qrBitmap: Bitmap, ticketReference: String?): Bitmap {
        // Reference text is now added during regeneration in NfcFlasher/MainActivity,
        // not during initial save. This keeps the stored image simple and allows
        // the reference to be toggled on/off without re-importing the ticket.
        return qrBitmap
    }
}
