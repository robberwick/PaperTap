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
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
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
    private lateinit var favoriteJourneyRepository: com.robberwick.papertap.database.FavoriteJourneyRepository
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
        favoriteJourneyRepository = com.robberwick.papertap.database.FavoriteJourneyRepository(this)
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

                    // Check for duplicate ticket
                    if (checkForDuplicateAndAlert(barcodeData.rawData)) {
                        return@launch
                    }

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

                    // Check for duplicate ticket
                    if (checkForDuplicateAndAlert(barcodeData.rawData)) {
                        return@launch
                    }

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

    /**
     * Check if a ticket with this barcode already exists and alert the user
     * Returns true if duplicate found (and activity should close), false otherwise
     */
    private suspend fun checkForDuplicateAndAlert(rawBarcodeData: String): Boolean {
        val existingTicket = withContext(Dispatchers.IO) {
            ticketRepository.findByBarcodeData(rawBarcodeData)
        }

        if (existingTicket != null) {
            // Build ticket details for display
            val details = buildString {
                append("Name: ${existingTicket.userLabel}\n")

                if (existingTicket.travelDate != null) {
                    val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                    append("Date: ${dateFormat.format(Date(existingTicket.travelDate))}\n")
                }

                if (existingTicket.originStationCode != null && existingTicket.destinationStationCode != null) {
                    val originName = StationLookup.getStationName(existingTicket.originStationCode)
                    val destName = StationLookup.getStationName(existingTicket.destinationStationCode)
                    append("Journey: $originName → $destName\n")
                }

                val addedFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                append("Added: ${addedFormat.format(Date(existingTicket.addedAt))}")
            }

            // Show alert dialog
            AlertDialog.Builder(this)
                .setTitle("Ticket Already Exists")
                .setMessage("This ticket is already saved:\n\n$details")
                .setPositiveButton("OK") { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()

            return true
        }

        return false
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

        // Views
        val tabLayout = dialogView.findViewById<com.google.android.material.tabs.TabLayout>(R.id.journeyTabs)
        val favoritesContent = dialogView.findViewById<LinearLayout>(R.id.favoritesContent)
        val searchContent = dialogView.findViewById<LinearLayout>(R.id.searchContent)
        val favoritesRecyclerView = dialogView.findViewById<RecyclerView>(R.id.favoritesRecyclerView)
        val emptyFavoritesState = dialogView.findViewById<LinearLayout>(R.id.emptyFavoritesState)
        val originInput = dialogView.findViewById<AutoCompleteTextView>(R.id.originStationInput)
        val destInput = dialogView.findViewById<AutoCompleteTextView>(R.id.destinationStationInput)
        val saveFavoriteButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.saveFavoriteButton)

        // Setup station search adapters (same as before)
        val stationAdapter = StationAdapter(this, StationLookup.getAllStations())
        originInput.setAdapter(stationAdapter)
        originInput.threshold = 1
        destInput.setAdapter(StationAdapter(this, StationLookup.getAllStations()))
        destInput.threshold = 1

        // Pre-fill if stations already selected
        selectedOriginStation?.let { originInput.setText(it.toString(), false) }
        selectedDestinationStation?.let { destInput.setText(it.toString(), false) }

        var tempOrigin: Station? = selectedOriginStation
        var tempDest: Station? = selectedDestinationStation

        originInput.setOnItemClickListener { _, _, position, _ ->
            tempOrigin = stationAdapter.getItem(position)
            updateSaveFavoriteButtonVisibility(tempOrigin, tempDest, saveFavoriteButton)
        }

        destInput.setOnItemClickListener { _, _, position, _ ->
            tempDest = (destInput.adapter as StationAdapter).getItem(position)
            updateSaveFavoriteButtonVisibility(tempOrigin, tempDest, saveFavoriteButton)
        }

        // Setup favorites RecyclerView
        val favoriteAdapter = FavoriteJourneyAdapter(
            onFavoriteClick = { favorite ->
                // Select this favorite
                tempOrigin = Station(favorite.originStationCode,
                    StationLookup.getStationName(favorite.originStationCode) ?: favorite.originStationCode)
                tempDest = Station(favorite.destinationStationCode,
                    StationLookup.getStationName(favorite.destinationStationCode) ?: favorite.destinationStationCode)

                // Record usage
                lifecycleScope.launch {
                    favoriteJourneyRepository.recordUsage(favorite.id)
                }

                // Apply selection and close dialog
                selectedOriginStation = tempOrigin
                selectedDestinationStation = tempDest
                updateJourneyDisplay()
                (dialogView.parent as? AlertDialog)?.dismiss()
            },
            onSwapClick = { favorite ->
                // Reverse direction
                tempOrigin = Station(favorite.destinationStationCode,
                    StationLookup.getStationName(favorite.destinationStationCode) ?: favorite.destinationStationCode)
                tempDest = Station(favorite.originStationCode,
                    StationLookup.getStationName(favorite.originStationCode) ?: favorite.originStationCode)

                // Record usage
                lifecycleScope.launch {
                    favoriteJourneyRepository.recordUsage(favorite.id)
                }

                // Apply selection and close dialog
                selectedOriginStation = tempOrigin
                selectedDestinationStation = tempDest
                updateJourneyDisplay()
                (dialogView.parent as? AlertDialog)?.dismiss()
            }
        )

        favoritesRecyclerView.apply {
            adapter = favoriteAdapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@AddTicketActivity)
        }

        // Observe favorites
        var favoritesCount = 0
        favoriteJourneyRepository.allFavorites.observe(this) { favorites ->
            favoriteAdapter.submitList(favorites)
            favoritesCount = favorites.size

            if (favorites.isEmpty()) {
                favoritesRecyclerView.visibility = View.GONE
                emptyFavoritesState.visibility = View.VISIBLE
            } else {
                favoritesRecyclerView.visibility = View.VISIBLE
                emptyFavoritesState.visibility = View.GONE
            }
        }

        // Tab switching logic
        tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { // Favorites tab
                        favoritesContent.visibility = View.VISIBLE
                        searchContent.visibility = View.GONE
                    }
                    1 -> { // Search tab
                        favoritesContent.visibility = View.GONE
                        searchContent.visibility = View.VISIBLE
                    }
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        // Default to Favorites tab if user has favorites, otherwise Search tab
        lifecycleScope.launch {
            val count = favoriteJourneyRepository.getFavoritesCount()
            if (count > 0) {
                tabLayout.selectTab(tabLayout.getTabAt(0)) // Favorites
                favoritesContent.visibility = View.VISIBLE
                searchContent.visibility = View.GONE
            } else {
                tabLayout.selectTab(tabLayout.getTabAt(1)) // Search
                favoritesContent.visibility = View.GONE
                searchContent.visibility = View.VISIBLE
            }
        }

        // Save favorite button click
        saveFavoriteButton.setOnClickListener {
            tempOrigin?.let { origin ->
                tempDest?.let { dest ->
                    showSaveFavoriteDialog(origin.code, dest.code)
                }
            }
        }

        // Show dialog
        val dialog = AlertDialog.Builder(this)
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

    private fun updateSaveFavoriteButtonVisibility(
        origin: Station?,
        dest: Station?,
        button: com.google.android.material.button.MaterialButton
    ) {
        button.visibility = if (origin != null && dest != null) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun showSaveFavoriteDialog(originCode: String, destCode: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_save_favorite, null)
        val labelInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(
            R.id.favoriteLabelInput
        )
        val warningText = dialogView.findViewById<TextView>(R.id.favoriteCountWarning)

        // Pre-fill with default label
        val defaultLabel = favoriteJourneyRepository.generateDefaultLabel(originCode, destCode)
        labelInput.setText(defaultLabel)
        labelInput.selectAll()

        lifecycleScope.launch {
            val count = favoriteJourneyRepository.getFavoritesCount()
            if (count >= 50) {
                warningText.visibility = View.VISIBLE
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Save as favorite")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val label = labelInput.text?.toString()?.trim() ?: defaultLabel

                lifecycleScope.launch {
                    val count = favoriteJourneyRepository.getFavoritesCount()
                    if (count >= 50) {
                        Toast.makeText(
                            this@AddTicketActivity,
                            "Maximum 50 favorites. Delete old favorites to add more.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        favoriteJourneyRepository.insertFavorite(originCode, destCode, label)
                        Toast.makeText(
                            this@AddTicketActivity,
                            "Favorite saved",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateDefaultLabel(): String {
        val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
        return "Ticket ${dateFormat.format(Date())}"
    }

}
