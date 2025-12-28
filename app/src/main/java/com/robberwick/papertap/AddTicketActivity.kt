package com.robberwick.papertap

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.robberwick.papertap.database.TicketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AddTicketActivity : AppCompatActivity() {

    private lateinit var ticketRepository: TicketRepository
    private lateinit var preferences: Preferences

    private lateinit var qrCodePreview: ImageView
    private lateinit var nameValue: TextView
    private lateinit var dateValue: TextView
    private lateinit var journeyPlaceholder: TextView
    private lateinit var journeyDetails: LinearLayout
    private lateinit var originName: TextView
    private lateinit var originCode: TextView
    private lateinit var destinationName: TextView
    private lateinit var destinationCode: TextView
    private lateinit var cancelButton: Button
    private lateinit var addButton: Button

    private var extractedQrBitmap: Bitmap? = null
    private var extractedRawData: String? = null
    private var extractedBarcodeFormat: Int? = null

    private var ticketLabel: String = ""
    private var selectedOriginStation: Station? = null
    private var selectedDestinationStation: Station? = null
    private var selectedTravelDate: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.d("AddTicketActivity", "onCreate - Activity starting")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_ticket)

        // Setup toolbar with back button
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        ticketRepository = TicketRepository(this)
        preferences = Preferences(this)

        // Initialize StationLookup
        StationLookup.initialize(this)

        // Find views
        qrCodePreview = findViewById(R.id.qrCodePreview)
        nameValue = findViewById(R.id.nameValue)
        dateValue = findViewById(R.id.dateValue)
        journeyPlaceholder = findViewById(R.id.journeyPlaceholder)
        journeyDetails = findViewById(R.id.journeyDetails)
        originName = findViewById(R.id.originName)
        originCode = findViewById(R.id.originCode)
        destinationName = findViewById(R.id.destinationName)
        destinationCode = findViewById(R.id.destinationCode)
        cancelButton = findViewById(R.id.cancelButton)
        addButton = findViewById(R.id.addButton)

        // Setup click listeners for tappable rows
        findViewById<View>(R.id.nameRow).setOnClickListener { showNameDialog() }
        findViewById<View>(R.id.dateRow).setOnClickListener { showDateDialog() }
        findViewById<View>(R.id.journeyRow).setOnClickListener { showJourneyDialog() }

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

    private fun showNameDialog() {
        val input = EditText(this)
        input.setText(ticketLabel)
        input.selectAll()
        input.setSingleLine(true)

        AlertDialog.Builder(this)
            .setTitle("Ticket Name")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val newLabel = input.text.toString().trim()
                ticketLabel = if (newLabel.isEmpty()) generateDefaultLabel() else newLabel
                nameValue.text = ticketLabel
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDateDialog() {
        val calendar = Calendar.getInstance()
        if (selectedTravelDate != null) {
            calendar.timeInMillis = selectedTravelDate!!
        }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                selectedTravelDate = calendar.timeInMillis

                val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                dateValue.text = dateFormat.format(calendar.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showJourneyDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_journey, null)
        val originInput = dialogView.findViewById<AutoCompleteTextView>(R.id.originStationInput)
        val destInput = dialogView.findViewById<AutoCompleteTextView>(R.id.destinationStationInput)

        // Setup adapters
        val adapter = StationAdapter(this, StationLookup.getAllStations())
        originInput.setAdapter(adapter)
        originInput.threshold = 1
        destInput.setAdapter(StationAdapter(this, StationLookup.getAllStations()))
        destInput.threshold = 1

        // Pre-fill if stations already selected
        selectedOriginStation?.let {  originInput.setText(it.toString(), false) }
        selectedDestinationStation?.let { destInput.setText(it.toString(), false) }

        var tempOrigin: Station? = selectedOriginStation
        var tempDest: Station? = selectedDestinationStation

        originInput.setOnItemClickListener { _, _, position, _ ->
            tempOrigin = adapter.getItem(position)
        }

        destInput.setOnItemClickListener { _, _, position, _ ->
            tempDest = (destInput.adapter as StationAdapter).getItem(position)
        }

        AlertDialog.Builder(this)
            .setTitle("Journey")
            .setView(dialogView)
            .setPositiveButton("Set") { _, _ ->
                selectedOriginStation = tempOrigin
                selectedDestinationStation = tempDest
                updateJourneyDisplay()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateJourneyDisplay() {
        if (selectedOriginStation != null || selectedDestinationStation != null) {
            journeyPlaceholder.visibility = View.GONE
            journeyDetails.visibility = View.VISIBLE

            selectedOriginStation?.let {
                originName.text = it.name
                originCode.text = it.code
            } ?: run {
                originName.text = ""
                originCode.text = "?"
            }

            selectedDestinationStation?.let {
                destinationName.text = it.name
                destinationCode.text = it.code
            } ?: run {
                destinationName.text = ""
                destinationCode.text = "?"
            }
        } else {
            journeyPlaceholder.visibility = View.VISIBLE
            journeyDetails.visibility = View.GONE
        }
    }

    private fun addTicket() {
        val rawData = extractedRawData
        val barcodeFormat = extractedBarcodeFormat

        if (rawData == null || barcodeFormat == null) {
            Toast.makeText(this, "No barcode to save", Toast.LENGTH_SHORT).show()
            return
        }

        // Use ticketLabel if set, otherwise generate default
        val label = if (ticketLabel.isEmpty()) generateDefaultLabel() else ticketLabel

        lifecycleScope.launch {
            try {
                val ticketId = withContext(Dispatchers.IO) {
                    ticketRepository.insertTicket(
                        rawData = rawData,
                        format = barcodeFormat,
                        userLabel = label,
                        originStationCode = selectedOriginStation?.code,
                        destinationStationCode = selectedDestinationStation?.code,
                        travelDate = selectedTravelDate
                    )
                }

                val ticket = withContext(Dispatchers.IO) {
                    ticketRepository.getById(ticketId)
                }

                if (ticket != null && ticket.userLabel != label) {
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

    private class StationAdapter(context: android.content.Context, stations: List<Station>) :
        ArrayAdapter<Station>(context, android.R.layout.simple_dropdown_item_1line, stations) {

        private val allStations = stations
        private var filteredStations = stations

        override fun getCount(): Int = filteredStations.size

        override fun getItem(position: Int): Station = filteredStations[position]

        override fun getFilter(): android.widget.Filter {
            return object : android.widget.Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    val results = FilterResults()

                    if (constraint.isNullOrBlank()) {
                        results.values = allStations
                        results.count = allStations.size
                    } else {
                        val query = constraint.toString().lowercase()
                        val filtered = allStations.filter { station ->
                            station.code.lowercase().contains(query) ||
                            station.name.lowercase().contains(query)
                        }
                        results.values = filtered
                        results.count = filtered.size
                    }

                    return results
                }

                @Suppress("UNCHECKED_CAST")
                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    filteredStations = (results?.values as? List<Station>) ?: emptyList()
                    if (results?.count ?: 0 > 0) {
                        notifyDataSetChanged()
                    } else {
                        notifyDataSetInvalidated()
                    }
                }

                override fun convertResultToString(resultValue: Any?): CharSequence {
                    return (resultValue as? Station)?.toString() ?: ""
                }
            }
        }
    }
}
