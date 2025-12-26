package com.robberwick.papertap

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ticket_list)

        // Setup toolbar
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Initialize repository
        ticketRepository = TicketRepository(this)

        // Setup RecyclerView
        recyclerView = findViewById(R.id.ticketsRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)

        ticketAdapter = TicketAdapter { ticket ->
            // Navigate to flash screen when ticket is clicked
            val intent = Intent(this, NfcFlasher::class.java)
            intent.putExtra("TICKET_ID", ticket.id)
            startActivity(intent)
        }

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

        // Handle share intents
        android.util.Log.d("TicketListActivity", "onCreate - About to handle incoming intent")
        android.util.Log.d("TicketListActivity", "onCreate - Intent action: ${intent.action}")
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun handleIncomingIntent(intent: Intent) {
        android.util.Log.d("TicketListActivity", "handleIncomingIntent - action: ${intent.action}")
        android.util.Log.d("TicketListActivity", "handleIncomingIntent - type: ${intent.type}")
        android.util.Log.d("TicketListActivity", "handleIncomingIntent - data: ${intent.data}")
        android.util.Log.d("TicketListActivity", "handleIncomingIntent - extras: ${intent.extras?.keySet()?.joinToString()}")

        when (intent.action) {
            Intent.ACTION_SEND -> {
                // Try EXTRA_STREAM first (standard for file sharing)
                var uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                android.util.Log.d("TicketListActivity", "EXTRA_STREAM uri: $uri")

                // Fallback: Try intent.data (some apps use this)
                if (uri == null) {
                    uri = intent.data
                    android.util.Log.d("TicketListActivity", "Fallback to intent.data: $uri")
                }

                // Fallback: Try EXTRA_TEXT (might contain a URI string)
                if (uri == null) {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    android.util.Log.d("TicketListActivity", "EXTRA_TEXT: $text")
                    if (text != null) {
                        try {
                            uri = Uri.parse(text)
                            android.util.Log.d("TicketListActivity", "Parsed URI from EXTRA_TEXT: $uri")
                        } catch (e: Exception) {
                            android.util.Log.e("TicketListActivity", "Failed to parse URI from EXTRA_TEXT", e)
                        }
                    }
                }

                if (uri != null) {
                    android.util.Log.d("TicketListActivity", "Navigating to AddTicket with URI: $uri")
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
                    android.util.Log.d("TicketListActivity", "ACTION_VIEW with URI: $uri")
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
        android.util.Log.d("TicketListActivity", "navigateToAddTicket - Creating intent for AddTicketActivity")
        android.util.Log.d("TicketListActivity", "navigateToAddTicket - URI: $uri")
        val intent = Intent(this, AddTicketActivity::class.java)
        intent.putExtra("DOCUMENT_URI", uri.toString())
        android.util.Log.d("TicketListActivity", "navigateToAddTicket - Starting AddTicketActivity")
        startActivity(intent)
        android.util.Log.d("TicketListActivity", "navigateToAddTicket - AddTicketActivity started")
    }

    private fun setupSwipeToDelete() {
        val deleteIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_delete)
        val background = ColorDrawable(ContextCompat.getColor(this, android.R.color.holo_red_dark))

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
                // Require 50% swipe to trigger delete
                return 0.5f
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val iconMargin = (itemView.height - deleteIcon!!.intrinsicHeight) / 2
                val iconTop = itemView.top + (itemView.height - deleteIcon.intrinsicHeight) / 2
                val iconBottom = iconTop + deleteIcon.intrinsicHeight

                if (dX < 0) { // Swiping to the left
                    val iconLeft = itemView.right - iconMargin - deleteIcon.intrinsicWidth
                    val iconRight = itemView.right - iconMargin
                    deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)

                    background.setBounds(
                        itemView.right + dX.toInt(),
                        itemView.top,
                        itemView.right,
                        itemView.bottom
                    )
                } else { // No swipe
                    background.setBounds(0, 0, 0, 0)
                    deleteIcon.setBounds(0, 0, 0, 0)
                }

                background.draw(c)
                deleteIcon.draw(c)

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val ticket = ticketAdapter.getTicketAt(position)

                // Delete ticket
                lifecycleScope.launch {
                    ticketRepository.delete(ticket)

                    // Show undo snackbar
                    Snackbar.make(
                        recyclerView,
                        "Ticket deleted",
                        Snackbar.LENGTH_LONG
                    ).setAction("Undo") {
                        lifecycleScope.launch {
                            ticketRepository.insert(ticket)
                        }
                    }.show()
                }
            }
        })

        itemTouchHelper.attachToRecyclerView(recyclerView)
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
            else -> super.onOptionsItemSelected(item)
        }
    }
}
