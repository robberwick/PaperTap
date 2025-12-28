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
        val originInput = dialogView.findViewById<AutoCompleteTextView>(R.id.originStationInput)
        val destInput = dialogView.findViewById<AutoCompleteTextView>(R.id.destinationStationInput)

        // Setup adapters
        val originAdapter = StationAdapter(this, StationLookup.getAllStations())
        originInput.setAdapter(originAdapter)
        originInput.threshold = 1

        val destAdapter = StationAdapter(this, StationLookup.getAllStations())
        destInput.setAdapter(destAdapter)
        destInput.threshold = 1

        // Pre-populate with current values if set
        if (selectedOriginStation != null) {
            originInput.setText(selectedOriginStation.toString(), false)
        }
        if (selectedDestinationStation != null) {
            destInput.setText(selectedDestinationStation.toString(), false)
        }

        // Track temporary selections
        var tempOrigin: Station? = selectedOriginStation
        var tempDest: Station? = selectedDestinationStation

        // Handle origin station selection
        originInput.setOnItemClickListener { _, _, position, _ ->
            tempOrigin = originAdapter.getItem(position)
        }

        // Handle text changes for origin station
        originInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                if (query.isEmpty()) {
                    tempOrigin = null
                } else {
                    val matchedStation = StationLookup.getAllStations().find { it.toString() == query }
                    if (matchedStation == null) {
                        tempOrigin = null
                    }
                }
            }
        })

        // Handle destination station selection
        destInput.setOnItemClickListener { _, _, position, _ ->
            tempDest = destAdapter.getItem(position)
        }

        // Handle text changes for destination station
        destInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                if (query.isEmpty()) {
                    tempDest = null
                } else {
                    val matchedStation = StationLookup.getAllStations().find { it.toString() == query }
                    if (matchedStation == null) {
                        tempDest = null
                    }
                }
            }
        })

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
