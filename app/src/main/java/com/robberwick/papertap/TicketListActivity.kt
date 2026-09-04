package com.robberwick.papertap

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.robberwick.papertap.database.TicketRepository
import kotlinx.coroutines.launch
import android.widget.TextView

class TicketListActivity : AppCompatActivity() {

    private lateinit var ticketRepository: TicketRepository
    private lateinit var ticketAdapter: TicketAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView

    companion object {
        private const val REQUEST_PICK_DOCUMENT = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        installSplashScreen()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ticket_list)

        // Setup toolbar
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Tint overflow menu icon to match theme
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(R.attr.colorOnAppBar, typedValue, true)
        val overflowColor = typedValue.data
        toolbar.overflowIcon?.setTint(overflowColor)

        // Initialize StationLookup (background load; other activities also call this,
        // but the list screen may be the first activity in the process to need station names)
        StationLookup.initialize(this)

        // Initialize repository
        ticketRepository = TicketRepository(this)
        val displayRepository = com.robberwick.papertap.database.DisplayRepository(this)

        // Setup RecyclerView
        recyclerView = findViewById(R.id.ticketsRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)

        ticketAdapter = TicketAdapter(
            onTicketClick = { ticket ->
                // Navigate to flash screen when ticket is clicked
                val intent = Intent(this, NfcFlasher::class.java)
                intent.putExtra("TICKET_ID", ticket.id)
                startActivity(intent)
            },
            onTicketLongClick = { ticket ->
                // Launch edit activity on long press
                val intent = Intent(this, EditTicketActivity::class.java)
                intent.putExtra("TICKET_ID", ticket.id)
                startActivity(intent)
            },
            displayRepository = displayRepository,
            ticketRepository = ticketRepository
        )

        recyclerView.apply {
            adapter = ticketAdapter
            layoutManager = LinearLayoutManager(this@TicketListActivity)
        }

        // Setup swipe to delete
        setupSwipeToDelete()

        // Observe ticket list
        ticketRepository.allTickets.observe(this) { tickets ->
            ticketAdapter.submitList(tickets)
            if (tickets.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyStateText.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyStateText.visibility = View.GONE
            }
        }

        // Setup FAB
        val addTicketFab: FloatingActionButton = findViewById(R.id.addTicketFab)
        addTicketFab.setOnClickListener {
            openDocumentPicker()
        }

        // Handle a launch/share intent once; configuration changes must not
        // re-launch AddTicketActivity for the same intent.
        if (savedInstanceState == null) {
            if (BuildConfig.DEBUG) android.util.Log.d("TicketListActivity", "onCreate - About to handle incoming intent")
            if (BuildConfig.DEBUG) android.util.Log.d("TicketListActivity", "onCreate - Intent action: ${intent.action}")
            handleIncomingIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Force adapter to rebind to pick up display label changes
        ticketAdapter.notifyDataSetChanged()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (BuildConfig.DEBUG) android.util.Log.d("TicketListActivity", "handleIncomingIntent - action: ${intent.action}")
        if (BuildConfig.DEBUG) android.util.Log.d("TicketListActivity", "handleIncomingIntent - type: ${intent.type}")
        if (BuildConfig.DEBUG) android.util.Log.d("TicketListActivity", "handleIncomingIntent - data: ${intent.data}")
        if (BuildConfig.DEBUG) android.util.Log.d("TicketListActivity", "handleIncomingIntent - extras: ${intent.extras?.keySet()?.joinToString()}")

        when (intent.action) {
            Intent.ACTION_SEND -> {
                // Try EXTRA_STREAM first (standard for file sharing)
                var uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (BuildConfig.DEBUG) android.util.Log.d("TicketListActivity", "EXTRA_STREAM uri: $uri")

                // Fallback: Try intent.data (some apps use this)
                if (uri == null) {
                    uri = intent.data
                    if (BuildConfig.DEBUG) android.util.Log.d("TicketListActivity", "Fallback to intent.data: $uri")
                }

                // Fallback: Try EXTRA_TEXT (might contain a URI string)
                if (uri == null) {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (BuildConfig.DEBUG) android.util.Log.d("TicketListActivity", "EXTRA_TEXT: $text")
                    if (text != null) {
                        try {
                            uri = Uri.parse(text)
                            if (BuildConfig.DEBUG) android.util.Log.d("TicketListActivity", "Parsed URI from EXTRA_TEXT: $uri")
                        } catch (e: Exception) {
                            android.util.Log.e("TicketListActivity", "Failed to parse URI from EXTRA_TEXT", e)
                        }
                    }
                }

                if (uri != null) {
                    if (BuildConfig.DEBUG) android.util.Log.d("TicketListActivity", "Navigating to AddTicket with URI: $uri")
                    navigateToAddTicket(uri)
                } else {
                    android.util.Log.e("TicketListActivity", "No URI found in SEND intent!")
                    android.widget.Toast.makeText(
                        this,
                        "Could not access the shared file. Please try again.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    if (BuildConfig.DEBUG) android.util.Log.d("TicketListActivity", "ACTION_VIEW with URI: $uri")
                    navigateToAddTicket(uri)
                }
            }
        }
    }

    private fun openDocumentPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "application/pdf"))
        }
        startActivityForResult(intent, REQUEST_PICK_DOCUMENT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_DOCUMENT && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                navigateToAddTicket(uri)
            }
        }
    }

    private fun navigateToAddTicket(uri: Uri) {
        if (BuildConfig.DEBUG) android.util.Log.d("TicketListActivity", "navigateToAddTicket - Creating intent for AddTicketActivity")
        if (BuildConfig.DEBUG) android.util.Log.d("TicketListActivity", "navigateToAddTicket - URI: $uri")
        val intent = Intent(this, AddTicketActivity::class.java)
        intent.putExtra("DOCUMENT_URI", uri.toString())
        if (BuildConfig.DEBUG) android.util.Log.d("TicketListActivity", "navigateToAddTicket - Starting AddTicketActivity")
        startActivity(intent)
        if (BuildConfig.DEBUG) android.util.Log.d("TicketListActivity", "navigateToAddTicket - AddTicketActivity started")
    }

    private fun setupSwipeToDelete() {
        val callback = SwipeToDeleteCallback(this) { viewHolder, _ ->
            val position = viewHolder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) {
                ticketAdapter.notifyDataSetChanged()
            } else {
                val ticket = ticketAdapter.getTicketAt(position)
                lifecycleScope.launch {
                    ticketRepository.delete(ticket)
                    Snackbar.make(
                        recyclerView,
                        "Ticket deleted",
                        Snackbar.LENGTH_LONG,
                    ).setAction("Undo") {
                        lifecycleScope.launch {
                            ticketRepository.insert(ticket)
                        }
                    }.show()
                }
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
    }

    private fun showEditLabelDialog(ticket: com.robberwick.papertap.database.TicketEntity) {
        val input = android.widget.EditText(this)
        input.setText(ticket.userLabel)
        input.selectAll()

        android.app.AlertDialog.Builder(this)
            .setTitle("Edit label")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newLabel = input.text.toString().trim()
                val finalLabel = if (newLabel.isEmpty()) {
                    val dateFormat = java.text.SimpleDateFormat("MMM d, yyyy 'at' h:mm a", java.util.Locale.getDefault())
                    "Ticket ${dateFormat.format(java.util.Date())}"
                } else {
                    newLabel
                }

                lifecycleScope.launch {
                    ticketRepository.updateTicketLabel(ticket.id, finalLabel)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.ticket_list_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            R.id.action_manage_favorites -> {
                startActivity(Intent(this, ManageFavoriteJourneysActivity::class.java))
                true
            }
            R.id.action_manage_displays -> {
                startActivity(Intent(this, ManageDisplaysActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
