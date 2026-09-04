package com.robberwick.papertap

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.robberwick.papertap.database.DisplayRepository
import kotlinx.coroutines.launch

class ManageDisplaysActivity : AppCompatActivity() {

    private lateinit var displayRepository: DisplayRepository
    private lateinit var displayAdapter: DisplayAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_displays)

        // Setup toolbar with back button
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Initialize repository
        displayRepository = DisplayRepository(this)

        // Setup RecyclerView
        recyclerView = findViewById(R.id.displaysRecyclerView)
        emptyState = findViewById(R.id.emptyState)

        displayAdapter = DisplayAdapter(
            onDisplayClick = { display ->
                showEditLabelDialog(display)
            }
        )

        recyclerView.apply {
            adapter = displayAdapter
            layoutManager = LinearLayoutManager(this@ManageDisplaysActivity)
        }

        // Setup swipe to delete
        setupSwipeToDelete()

        // Observe displays
        displayRepository.allDisplays.observe(this) { displays ->
            displayAdapter.submitList(displays)
            if (displays.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyState.visibility = View.GONE
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun showEditLabelDialog(display: com.robberwick.papertap.database.DisplayEntity) {
        val input = EditText(this)
        input.setText(display.userLabel)
        input.hint = "e.g., Home Display, Work Badge"
        input.setSingleLine(true)

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 0)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle("Display Label")
            .setMessage(display.tagUid)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newLabel = input.text.toString().trim()
                lifecycleScope.launch {
                    displayRepository.updateLabel(
                        display.tagUid,
                        if (newLabel.isEmpty()) null else newLabel
                    )
                }
            }
            .setNeutralButton("Clear") { _, _ ->
                lifecycleScope.launch {
                    displayRepository.updateLabel(display.tagUid, null)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupSwipeToDelete() {
        val callback = SwipeToDeleteCallback(this) { viewHolder, _ ->
            val position = viewHolder.adapterPosition
            if (position == RecyclerView.NO_POSITION) {
                displayAdapter.notifyDataSetChanged()
            } else {
                val display = displayAdapter.getDisplayAt(position)
                lifecycleScope.launch {
                    displayRepository.delete(display)
                    Snackbar.make(
                        recyclerView,
                        "Display deleted",
                        Snackbar.LENGTH_LONG,
                    ).setAction("Undo") {
                        lifecycleScope.launch {
                            displayRepository.insert(display)
                        }
                    }.show()
                }
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
    }
}
