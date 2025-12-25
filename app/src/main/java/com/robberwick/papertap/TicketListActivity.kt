package com.robberwick.papertap

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
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
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun handleIncomingIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) {
                    navigateToAddTicket(uri)
                }
            }
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
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
        val intent = Intent(this, AddTicketActivity::class.java)
        intent.putExtra("DOCUMENT_URI", uri.toString())
        startActivity(intent)
    }

    private fun setupSwipeToDelete() {
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
