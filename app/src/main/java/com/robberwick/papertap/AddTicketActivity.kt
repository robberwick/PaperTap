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
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.robberwick.papertap.database.FavoriteJourneyEntity
import com.robberwick.papertap.database.TicketRepository
import com.robberwick.papertap.database.FavoriteJourneyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddTicketActivity : AppCompatActivity() {

    private lateinit var ticketRepository: TicketRepository
    private lateinit var favoriteJourneyRepository: com.robberwick.papertap.database.FavoriteJourneyRepository
    private lateinit var preferences: Preferences
    private lateinit var qrExtractor: PdfQrExtractor


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
    private lateinit var extractionState: View
    private lateinit var extractionProgress: View
    private lateinit var extractionErrorText: TextView
    private lateinit var retryExtractionButton: Button


    private var extractedQrBitmap: Bitmap? = null
    private var extractedRawData: String? = null
    private var extractedBarcodeFormat: Int? = null

    private var ticketLabel: String = ""
    private var selectedOriginStation: Station? = null
    private var selectedDestinationStation: Station? = null
    private var selectedTravelDate: Long? = null
    private var currentDocumentUri: Uri? = null

    private var isDirty = false
    private var currentFavorites: List<FavoriteJourneyEntity> = emptyList()


    override fun onCreate(savedInstanceState: Bundle?) {
        if (BuildConfig.DEBUG) android.util.Log.d("AddTicketActivity", "onCreate - Activity starting")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_ticket)

        // Setup toolbar with back button
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        ticketRepository = TicketRepository(this)
        favoriteJourneyRepository = com.robberwick.papertap.database.FavoriteJourneyRepository(this)
        preferences = Preferences(this)
        qrExtractor = PdfQrExtractor(this)

        // A17: observe favorites once for the Activity lifetime; dialogs read the cached list
        favoriteJourneyRepository.allFavorites.observe(this) { favorites ->
            currentFavorites = favorites
        }


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
        extractionState = findViewById(R.id.extractionState)
        extractionProgress = findViewById(R.id.extractionProgress)
        extractionErrorText = findViewById(R.id.extractionErrorText)
        retryExtractionButton = findViewById(R.id.retryExtractionButton)

        // Setup click listeners for tappable rows
        findViewById<View>(R.id.nameRow).setOnClickListener { showNameDialog() }
        findViewById<View>(R.id.dateRow).setOnClickListener { showDateDialog() }
        findViewById<View>(R.id.journeyRow).setOnClickListener { showJourneyDialog() }

        // Setup buttons
        cancelButton.setOnClickListener { confirmDiscardOrFinish() }
        addButton.setOnClickListener { addTicket() }
        retryExtractionButton.setOnClickListener {
            currentDocumentUri?.let(::processDocument)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmDiscardOrFinish()
            }
        })


        // Get document URI from intent
        val documentUriString = intent.getStringExtra("DOCUMENT_URI")
        if (BuildConfig.DEBUG) android.util.Log.d("AddTicketActivity", "onCreate - DOCUMENT_URI: $documentUriString")
        if (documentUriString != null) {
            val uri = Uri.parse(documentUriString)
            if (BuildConfig.DEBUG) android.util.Log.d("AddTicketActivity", "onCreate - Parsed URI: $uri")
            processDocument(uri)
        } else {
            android.util.Log.e("AddTicketActivity", "onCreate - No DOCUMENT_URI provided!")
            Toast.makeText(this, "No document provided", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun processDocument(uri: Uri) {
        currentDocumentUri = uri
        lifecycleScope.launch {
            showExtractionLoading()
            val result = withContext(Dispatchers.IO) { extractDocument(uri) }
            when (result) {
                is BarcodeExtractionResult.Success -> {
                    hideExtractionState()
                    extractedQrBitmap?.takeIf { it !== result.bitmap }?.recycle()
                    extractedQrBitmap = result.bitmap
                    extractedRawData = result.barcodeData.rawData
                    extractedBarcodeFormat = result.barcodeData.barcodeFormat
                    if (!checkForDuplicateAndAlert(result.barcodeData.rawData)) {
                        // A18: the coroutine may resume after the activity is gone
                        if (isFinishing || isDestroyed) return@launch
                        displayPreview(result.bitmap)
                    }
                }
                BarcodeExtractionResult.NoBarcode -> showExtractionError(
                    getString(R.string.no_barcode_found),
                )
                is BarcodeExtractionResult.Error -> {
                    result.cause?.let {
                        android.util.Log.e("AddTicketActivity", result.message, it)
                    }
                    showExtractionError(result.message)
                }
            }
        }
    }

    private suspend fun extractDocument(uri: Uri): BarcodeExtractionResult {
        return if (uri.scheme == "http" || uri.scheme == "https") {
            downloadAndExtractPdf(uri)
        } else {
            when (val mimeType = contentResolver.getType(uri)) {
                "application/pdf" -> qrExtractor.extractQrCodeFromPdf(uri, preferences.getQrPadding())
                else -> if (mimeType?.startsWith("image/") == true) {
                    extractFromImage(uri)
                } else {
                    BarcodeExtractionResult.Error(getString(R.string.unsupported_document))
                }
            }
        }
    }

    private suspend fun downloadAndExtractPdf(url: Uri): BarcodeExtractionResult {
        val tempFile = File.createTempFile("downloaded_ticket", ".pdf", cacheDir)
        return try {
            val connection = java.net.URL(url.toString()).openConnection() as java.net.HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = true
                connection.connect()
                if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                    return BarcodeExtractionResult.Error(
                        "The ticket download returned HTTP ${connection.responseCode}",
                    )
                }
                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
            } finally {
                connection.disconnect()
            }
            qrExtractor.extractQrCodeFromPdf(
                Uri.fromFile(tempFile),
                preferences.getQrPadding(),
            )
        } catch (e: Exception) {
            BarcodeExtractionResult.Error("The ticket could not be downloaded", e)
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun extractFromImage(uri: Uri): BarcodeExtractionResult {
        val bitmap = decodeSampledBitmap(uri)
            ?: return BarcodeExtractionResult.Error("The image could not be opened")
        return try {
            qrExtractor.extractQrFromBitmap(bitmap, preferences.getQrPadding())
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeSampledBitmap(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > MAX_IMAGE_DIMENSION ||
            bounds.outHeight / sampleSize > MAX_IMAGE_DIMENSION
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun showExtractionLoading() {
        extractionState.visibility = View.VISIBLE
        extractionProgress.visibility = View.VISIBLE
        extractionErrorText.visibility = View.GONE
        retryExtractionButton.visibility = View.GONE
        addButton.isEnabled = false
    }

    private fun showExtractionError(message: String) {
        extractionState.visibility = View.VISIBLE
        extractionProgress.visibility = View.GONE
        extractionErrorText.text = message
        extractionErrorText.visibility = View.VISIBLE
        retryExtractionButton.visibility = View.VISIBLE
        addButton.isEnabled = false
    }

    private fun hideExtractionState() {
        extractionState.visibility = View.GONE
        addButton.isEnabled = true
    }

    override fun onDestroy() {
        qrExtractor.close()
        extractedQrBitmap?.recycle()
        extractedQrBitmap = null
        super.onDestroy()
    }

    private fun displayPreview(qrBitmap: Bitmap) {
        qrCodePreview.setImageBitmap(qrBitmap)
    }

    companion object {
        private const val MAX_IMAGE_DIMENSION = 2_000
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
            // A18: the coroutine may resume after the activity is gone
            if (isFinishing || isDestroyed) return true

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
                .setCancelable(true)
                .show()

            return true
        }

        return false
    }

    /** C10: confirm before discarding unsaved edits. */
    private fun confirmDiscardOrFinish() {
        if (!isDirty) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Discard changes?")
            .setMessage("Your unsaved changes will be lost.")
            .setPositiveButton("Discard") { _, _ -> finish() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        confirmDiscardOrFinish()
        return true
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
                val appliedLabel = if (newLabel.isEmpty()) generateDefaultLabel() else newLabel
                if (appliedLabel != ticketLabel) {
                    ticketLabel = appliedLabel
                    isDirty = true
                }
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
                // Normalize to midnight local time so same-day tickets compare equal
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val newDate = calendar.timeInMillis
                if (newDate != selectedTravelDate) {
                    selectedTravelDate = newDate
                    isDirty = true
                }

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
            originInput.error = null
            updateSaveFavoriteButtonVisibility(tempOrigin, tempDest, saveFavoriteButton)
        }

        destInput.setOnItemClickListener { _, _, position, _ ->
            tempDest = (destInput.adapter as StationAdapter).getItem(position)
            destInput.error = null
            updateSaveFavoriteButtonVisibility(tempOrigin, tempDest, saveFavoriteButton)
        }

        // Show dialog
        val dialog = AlertDialog.Builder(this)
            .setTitle("Journey")
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .setNegativeButton("Cancel", null)
            .create()

        // C9: OK must not silently discard typed-but-unselected stations, so the
        // default auto-dismiss is replaced with a validating click listener.
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                var valid = true
                if (originInput.text.isBlank()) {
                    tempOrigin = null
                } else if (tempOrigin?.toString() != originInput.text.toString()) {
                    originInput.error = "Pick a station from the suggestions"
                    valid = false
                }
                if (destInput.text.isBlank()) {
                    tempDest = null
                } else if (tempDest?.toString() != destInput.text.toString()) {
                    destInput.error = "Pick a station from the suggestions"
                    valid = false
                }
                if (!valid) return@setOnClickListener

                if (tempOrigin != selectedOriginStation || tempDest != selectedDestinationStation) {
                    isDirty = true
                }
                selectedOriginStation = tempOrigin
                selectedDestinationStation = tempDest
                updateJourneyDisplay()
                dialog.dismiss()
            }
        }

        dialog.show()

        // Setup favorites RecyclerView
        val favoriteAdapter = FavoriteJourneyAdapter(
            onFavoriteClick = { favorite ->
                // Apply A→B selection
                tempOrigin = Station(favorite.originStationCode,
                    StationLookup.getStationName(favorite.originStationCode) ?: favorite.originStationCode)
                tempDest = Station(favorite.destinationStationCode,
                    StationLookup.getStationName(favorite.destinationStationCode) ?: favorite.destinationStationCode)

                selectedOriginStation = tempOrigin
                selectedDestinationStation = tempDest
                updateJourneyDisplay()
                isDirty = true

                // Record usage
                lifecycleScope.launch {
                    favoriteJourneyRepository.recordUsage(favorite.id)
                }

                dialog.dismiss()
            },
            onSwapClick = { favorite ->
                // Apply B→A selection (reversed)
                tempOrigin = Station(favorite.destinationStationCode,
                    StationLookup.getStationName(favorite.destinationStationCode) ?: favorite.destinationStationCode)
                tempDest = Station(favorite.originStationCode,
                    StationLookup.getStationName(favorite.originStationCode) ?: favorite.originStationCode)

                selectedOriginStation = tempOrigin
                selectedDestinationStation = tempDest
                updateJourneyDisplay()
                isDirty = true

                // Record usage
                lifecycleScope.launch {
                    favoriteJourneyRepository.recordUsage(favorite.id)
                }

                dialog.dismiss()
            }
        )

        favoritesRecyclerView.apply {
            adapter = favoriteAdapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@AddTicketActivity)
        }

        // A17: favorites are observed once in onCreate; submit the cached list here
        favoriteAdapter.submitList(currentFavorites)
        if (currentFavorites.isEmpty()) {
            favoritesRecyclerView.visibility = View.GONE
            emptyFavoritesState.visibility = View.VISIBLE
        } else {
            favoritesRecyclerView.visibility = View.VISIBLE
            emptyFavoritesState.visibility = View.GONE
        }

        // Save favorite button click
        saveFavoriteButton.setOnClickListener {
            tempOrigin?.let { origin ->
                tempDest?.let { dest ->
                    showSaveFavoriteDialog(origin.code, dest.code)
                }
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
                val result = withContext(Dispatchers.IO) {
                    ticketRepository.insertTicket(
                        rawData = rawData,
                        format = barcodeFormat,
                        userLabel = label,
                        originStationCode = selectedOriginStation?.code,
                        destinationStationCode = selectedDestinationStation?.code,
                        travelDate = selectedTravelDate
                    )
                }

                when (result) {
                    is TicketRepository.InsertTicketResult.Duplicate -> {
                        Toast.makeText(
                            this@AddTicketActivity,
                            "This ticket is already in your collection",
                            Toast.LENGTH_LONG,
                        ).show()
                        finish()
                    }
                    is TicketRepository.InsertTicketResult.Inserted -> {
                        Toast.makeText(this@AddTicketActivity, "Ticket added!", Toast.LENGTH_SHORT).show()
                        promptWriteToDisplay(result.ticketId)
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) e.printStackTrace()
                Toast.makeText(
                    this@AddTicketActivity,
                    "Error saving ticket: ${e.message}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    /** C4: offer a direct path from "Ticket added!" into the flash flow. */
    private fun promptWriteToDisplay(ticketId: Long) {
        AlertDialog.Builder(this)
            .setTitle(R.string.write_to_display_now)
            .setMessage(R.string.write_to_display_now_message)
            .setPositiveButton(R.string.write_now) { _, _ ->
                startActivity(
                    android.content.Intent(this, NfcFlasher::class.java)
                        .putExtra("TICKET_ID", ticketId),
                )
                finish()
            }
            .setNegativeButton(R.string.later) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
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
                    when (
                        favoriteJourneyRepository.insertFavoriteWithLimit(
                            originCode,
                            destCode,
                            label,
                        )
                    ) {
                        FavoriteJourneyRepository.SaveFavoriteResult.LimitReached -> {
                            Toast.makeText(
                                this@AddTicketActivity,
                                "Maximum 50 favorites. Delete old favorites to add more.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        is FavoriteJourneyRepository.SaveFavoriteResult.Saved -> {
                            Toast.makeText(
                                this@AddTicketActivity,
                                "Favorite saved",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
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
