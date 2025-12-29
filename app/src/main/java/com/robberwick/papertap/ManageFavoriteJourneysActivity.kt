package com.robberwick.papertap

import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.robberwick.papertap.database.FavoriteJourneyEntity
import com.robberwick.papertap.database.FavoriteJourneyRepository
import kotlinx.coroutines.launch

class ManageFavoriteJourneysActivity : AppCompatActivity() {

    private lateinit var favoriteJourneyRepository: FavoriteJourneyRepository
    private lateinit var favoriteAdapter: FavoriteJourneyAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_favorites)

        // Setup toolbar
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Tint back button
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(R.attr.colorOnAppBar, typedValue, true)
        val iconColor = typedValue.data
        toolbar.navigationIcon?.setTint(iconColor)
        toolbar.setNavigationOnClickListener { finish() }

        // Initialize repository
        favoriteJourneyRepository = FavoriteJourneyRepository(this)

        // Initialize StationLookup
        StationLookup.initialize(this)

        // Setup RecyclerView
        recyclerView = findViewById(R.id.favoritesRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)

        favoriteAdapter = FavoriteJourneyAdapter(
            onFavoriteClick = { favorite ->
                // No action on click in management screen (could show details)
            },
            onSwapClick = { favorite ->
                // Swap creates a new reversed favorite
                showSaveReversedFavoriteDialog(favorite)
            },
            onFavoriteLongClick = { favorite ->
                // Show edit/delete options
                showEditDeleteDialog(favorite)
            }
        )

        recyclerView.apply {
            adapter = favoriteAdapter
            layoutManager = LinearLayoutManager(this@ManageFavoriteJourneysActivity)
        }

        // Setup swipe to delete
        setupSwipeToDelete()

        // Observe favorites
        favoriteJourneyRepository.allFavorites.observe(this) { favorites ->
            favoriteAdapter.submitList(favorites)
            if (favorites.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyStateText.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyStateText.visibility = View.GONE
            }
        }

        // Setup FAB
        val addFavoriteFab: FloatingActionButton = findViewById(R.id.addFavoriteFab)
        addFavoriteFab.setOnClickListener {
            showAddFavoriteDialog()
        }
    }

    private fun setupSwipeToDelete() {
        val deleteIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_delete)
        val background = ColorDrawable(ContextCompat.getColor(this, android.R.color.holo_red_light))

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.5f

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

                if (dX < 0) {
                    val iconLeft = itemView.right - iconMargin - deleteIcon.intrinsicWidth
                    val iconRight = itemView.right - iconMargin
                    deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)

                    background.setBounds(
                        itemView.right + dX.toInt(),
                        itemView.top,
                        itemView.right,
                        itemView.bottom
                    )
                } else {
                    background.setBounds(0, 0, 0, 0)
                    deleteIcon.setBounds(0, 0, 0, 0)
                }

                background.draw(c)
                deleteIcon.draw(c)

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val favorite = favoriteAdapter.getFavoriteAt(position)

                // Delete with undo option
                lifecycleScope.launch {
                    favoriteJourneyRepository.delete(favorite)

                    Snackbar.make(
                        recyclerView,
                        "Favorite deleted",
                        Snackbar.LENGTH_LONG
                    ).setAction("UNDO") {
                        lifecycleScope.launch {
                            favoriteJourneyRepository.insert(favorite)
                        }
                    }.show()
                }
            }
        }

        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerView)
    }

    private fun showAddFavoriteDialog() {
        // Show journey selection dialog
        val dialogView = layoutInflater.inflate(R.layout.dialog_journey, null)

        // Simplified version: just show search tab, no favorites tab
        val tabLayout = dialogView.findViewById<com.google.android.material.tabs.TabLayout>(R.id.journeyTabs)
        tabLayout.visibility = View.GONE // Hide tabs

        val searchContent = dialogView.findViewById<LinearLayout>(R.id.searchContent)
        val favoritesContent = dialogView.findViewById<LinearLayout>(R.id.favoritesContent)
        searchContent.visibility = View.VISIBLE
        favoritesContent.visibility = View.GONE

        val originInput = dialogView.findViewById<AutoCompleteTextView>(R.id.originStationInput)
        val destInput = dialogView.findViewById<AutoCompleteTextView>(R.id.destinationStationInput)
        val saveFavoriteButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.saveFavoriteButton)
        saveFavoriteButton.visibility = View.GONE // Not needed here

        // Setup adapters
        val adapter = StationAdapter(this, StationLookup.getAllStations())
        originInput.setAdapter(adapter)
        originInput.threshold = 1
        destInput.setAdapter(StationAdapter(this, StationLookup.getAllStations()))
        destInput.threshold = 1

        var tempOrigin: Station? = null
        var tempDest: Station? = null

        originInput.setOnItemClickListener { _, _, position, _ ->
            tempOrigin = adapter.getItem(position)
        }

        destInput.setOnItemClickListener { _, _, position, _ ->
            tempDest = (destInput.adapter as StationAdapter).getItem(position)
        }

        AlertDialog.Builder(this)
            .setTitle("Add favorite journey")
            .setView(dialogView)
            .setPositiveButton("Next") { _, _ ->
                tempOrigin?.let { origin ->
                    tempDest?.let { dest ->
                        showSaveFavoriteDialog(origin.code, dest.code)
                    } ?: run {
                        Toast.makeText(this, "Please select both origin and destination", Toast.LENGTH_SHORT).show()
                    }
                } ?: run {
                    Toast.makeText(this, "Please select both origin and destination", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSaveFavoriteDialog(originCode: String, destCode: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_save_favorite, null)
        val labelInput = dialogView.findViewById<TextInputEditText>(R.id.favoriteLabelInput)
        val warningText = dialogView.findViewById<TextView>(R.id.favoriteCountWarning)

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
                            this@ManageFavoriteJourneysActivity,
                            "Maximum 50 favorites. Delete old favorites to add more.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        favoriteJourneyRepository.insertFavorite(originCode, destCode, label)
                        Toast.makeText(
                            this@ManageFavoriteJourneysActivity,
                            "Favorite saved",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSaveReversedFavoriteDialog(favorite: FavoriteJourneyEntity) {
        val reversedOrigin = favorite.destinationStationCode
        val reversedDest = favorite.originStationCode
        showSaveFavoriteDialog(reversedOrigin, reversedDest)
    }

    private fun showEditDeleteDialog(favorite: FavoriteJourneyEntity) {
        val originName = StationLookup.getStationName(favorite.originStationCode) ?: favorite.originStationCode
        val destName = StationLookup.getStationName(favorite.destinationStationCode) ?: favorite.destinationStationCode

        AlertDialog.Builder(this)
            .setTitle("$originName → $destName")
            .setItems(arrayOf("Edit label", "Delete")) { _, which ->
                when (which) {
                    0 -> showEditLabelDialog(favorite)
                    1 -> deleteFavorite(favorite)
                }
            }
            .show()
    }

    private fun showEditLabelDialog(favorite: FavoriteJourneyEntity) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_save_favorite, null)
        val labelInput = dialogView.findViewById<TextInputEditText>(R.id.favoriteLabelInput)

        labelInput.setText(favorite.label)
        labelInput.selectAll()

        AlertDialog.Builder(this)
            .setTitle("Edit label")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newLabel = labelInput.text?.toString()?.trim()
                    ?: favoriteJourneyRepository.generateDefaultLabel(
                        favorite.originStationCode,
                        favorite.destinationStationCode
                    )

                lifecycleScope.launch {
                    favoriteJourneyRepository.updateLabel(favorite.id, newLabel)
                    Toast.makeText(
                        this@ManageFavoriteJourneysActivity,
                        "Label updated",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteFavorite(favorite: FavoriteJourneyEntity) {
        lifecycleScope.launch {
            favoriteJourneyRepository.delete(favorite)
            Toast.makeText(
                this@ManageFavoriteJourneysActivity,
                "Favorite deleted",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
