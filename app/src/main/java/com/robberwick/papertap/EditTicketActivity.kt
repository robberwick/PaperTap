package com.robberwick.papertap

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.robberwick.papertap.database.TicketEntity
import com.robberwick.papertap.database.TicketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EditTicketActivity : AppCompatActivity() {

    private lateinit var ticketRepository: TicketRepository
    private lateinit var favoriteJourneyRepository: com.robberwick.papertap.database.FavoriteJourneyRepository

    private lateinit var nameValue: TextView
    private lateinit var dateValue: TextView
    private lateinit var journeyPlaceholder: TextView
    private lateinit var journeyDetails: LinearLayout
    private lateinit var originName: TextView
    private lateinit var originCode: TextView
    private lateinit var destinationName: TextView
    private lateinit var destinationCode: TextView
    private lateinit var cancelButton: Button
    private lateinit var saveButton: Button

    private var ticketId: Long = -1L
    private var ticketLabel: String = ""
    private var selectedOriginStation: Station? = null
    private var selectedDestinationStation: Station? = null
    private var selectedTravelDate: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_ticket)

        // Setup toolbar with back button
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        ticketRepository = TicketRepository(this)
        favoriteJourneyRepository = com.robberwick.papertap.database.FavoriteJourneyRepository(this)

        // Initialize StationLookup
        StationLookup.initialize(this)

        // Find views
        nameValue = findViewById(R.id.nameValue)
        dateValue = findViewById(R.id.dateValue)
        journeyPlaceholder = findViewById(R.id.journeyPlaceholder)
        journeyDetails = findViewById(R.id.journeyDetails)
        originName = findViewById(R.id.originName)
        originCode = findViewById(R.id.originCode)
        destinationName = findViewById(R.id.destinationName)
        destinationCode = findViewById(R.id.destinationCode)
        cancelButton = findViewById(R.id.cancelButton)
        saveButton = findViewById(R.id.saveButton)

        // Setup click listeners for tappable rows
        findViewById<View>(R.id.nameRow).setOnClickListener { showNameDialog() }
        findViewById<View>(R.id.dateRow).setOnClickListener { showDateDialog() }
        findViewById<View>(R.id.journeyRow).setOnClickListener { showJourneyDialog() }

        // Setup buttons
        cancelButton.setOnClickListener { finish() }
        saveButton.setOnClickListener { saveTicket() }

        // Get ticket ID from intent and load ticket
        ticketId = intent.getLongExtra("TICKET_ID", -1L)
        if (ticketId != -1L) {
            loadTicket(ticketId)
        } else {
            Toast.makeText(this, "No ticket provided", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadTicket(id: Long) {
        lifecycleScope.launch {
            val ticket = withContext(Dispatchers.IO) {
                ticketRepository.getById(id)
            }

            if (ticket != null) {
                populateFields(ticket)
            } else {
                Toast.makeText(this@EditTicketActivity, "Ticket not found", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun populateFields(ticket: TicketEntity) {
        // Set label
        ticketLabel = ticket.userLabel
        nameValue.text = ticketLabel

        // Set origin station
        if (ticket.originStationCode != null) {
            val originStation = StationLookup.getAllStations().find { it.code == ticket.originStationCode }
            if (originStation != null) {
                selectedOriginStation = originStation
            }
        }

        // Set destination station
        if (ticket.destinationStationCode != null) {
            val destStation = StationLookup.getAllStations().find { it.code == ticket.destinationStationCode }
            if (destStation != null) {
                selectedDestinationStation = destStation
            }
        }

        // Set travel date
        if (ticket.travelDate != null) {
            selectedTravelDate = ticket.travelDate
            val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            dateValue.text = dateFormat.format(Date(ticket.travelDate))
        }

        // Update journey display
        updateJourneyDisplay()
    }

    private fun saveTicket() {
        if (ticketLabel.isEmpty()) {
            Toast.makeText(this, "Label cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val ticket = withContext(Dispatchers.IO) {
                    ticketRepository.getById(ticketId)
                }

                if (ticket != null) {
                    val updatedTicket = ticket.copy(
                        userLabel = ticketLabel,
                        originStationCode = selectedOriginStation?.code,
                        destinationStationCode = selectedDestinationStation?.code,
                        travelDate = selectedTravelDate
                    )

                    withContext(Dispatchers.IO) {
                        ticketRepository.update(updatedTicket)
                    }

                    Toast.makeText(this@EditTicketActivity, "Ticket updated", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@EditTicketActivity, "Ticket not found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@EditTicketActivity,
                    "Error updating ticket: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showNameDialog() {
        val input = EditText(this)
        input.setText(ticketLabel)
        input.setHint("Enter ticket name")

        AlertDialog.Builder(this)
            .setTitle("Ticket Name")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val newLabel = input.text.toString().trim()
                if (newLabel.isNotEmpty()) {
                    ticketLabel = newLabel
                    nameValue.text = ticketLabel
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDateDialog() {
        val calendar = Calendar.getInstance()

        // If there's an existing date, use it; otherwise use today
        if (selectedTravelDate != null) {
            calendar.timeInMillis = selectedTravelDate!!
        }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                selectedTravelDate = calendar.timeInMillis

                // Format and display the selected date
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
        val favoritesRecyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.favoritesRecyclerView)
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
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@EditTicketActivity)
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
        if (selectedOriginStation != null && selectedDestinationStation != null) {
            // Show journey details, hide placeholder
            journeyPlaceholder.visibility = View.GONE
            journeyDetails.visibility = View.VISIBLE

            // Set origin
            originName.text = selectedOriginStation!!.name
            originCode.text = selectedOriginStation!!.code

            // Set destination
            destinationName.text = selectedDestinationStation!!.name
            destinationCode.text = selectedDestinationStation!!.code
        } else {
            // Show placeholder, hide details
            journeyPlaceholder.visibility = View.VISIBLE
            journeyDetails.visibility = View.GONE
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
                            this@EditTicketActivity,
                            "Maximum 50 favorites. Delete old favorites to add more.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        favoriteJourneyRepository.insertFavorite(originCode, destCode, label)
                        Toast.makeText(
                            this@EditTicketActivity,
                            "Favorite saved",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
