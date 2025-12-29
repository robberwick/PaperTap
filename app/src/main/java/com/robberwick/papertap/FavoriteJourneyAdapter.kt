package com.robberwick.papertap

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.robberwick.papertap.database.FavoriteJourneyEntity

class FavoriteJourneyAdapter(
    private val onFavoriteClick: (FavoriteJourneyEntity) -> Unit,
    private val onSwapClick: (FavoriteJourneyEntity) -> Unit,
    private val onFavoriteLongClick: ((FavoriteJourneyEntity) -> Unit)? = null
) : ListAdapter<FavoriteJourneyEntity, FavoriteJourneyAdapter.FavoriteViewHolder>(FavoriteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.favorite_journey_item, parent, false)
        return FavoriteViewHolder(view)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        val favorite = getItem(position)
        holder.bind(favorite, onFavoriteClick, onSwapClick, onFavoriteLongClick)
    }

    fun getFavoriteAt(position: Int): FavoriteJourneyEntity {
        return getItem(position)
    }

    class FavoriteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val journeyRouteText: TextView = itemView.findViewById(R.id.journeyRoute)
        private val favoriteLabelText: TextView = itemView.findViewById(R.id.favoriteLabel)
        private val swapButton: MaterialButton = itemView.findViewById(R.id.swapButton)

        fun bind(
            favorite: FavoriteJourneyEntity,
            onFavoriteClick: (FavoriteJourneyEntity) -> Unit,
            onSwapClick: (FavoriteJourneyEntity) -> Unit,
            onFavoriteLongClick: ((FavoriteJourneyEntity) -> Unit)?
        ) {
            // Build journey route display
            val originName = StationLookup.getStationName(favorite.originStationCode)
                ?: favorite.originStationCode
            val destName = StationLookup.getStationName(favorite.destinationStationCode)
                ?: favorite.destinationStationCode

            journeyRouteText.text = "$originName → $destName"

            // Show label only if it's different from the default route
            val defaultLabel = "$originName → $destName"
            if (favorite.label != defaultLabel && favorite.label.isNotBlank()) {
                favoriteLabelText.text = favorite.label
                favoriteLabelText.visibility = View.VISIBLE
            } else {
                favoriteLabelText.visibility = View.GONE
            }

            // Click handlers
            itemView.setOnClickListener {
                onFavoriteClick(favorite)
            }

            itemView.setOnLongClickListener {
                onFavoriteLongClick?.invoke(favorite)
                true
            }

            swapButton.setOnClickListener {
                onSwapClick(favorite)
            }
        }
    }

    class FavoriteDiffCallback : DiffUtil.ItemCallback<FavoriteJourneyEntity>() {
        override fun areItemsTheSame(
            oldItem: FavoriteJourneyEntity,
            newItem: FavoriteJourneyEntity
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: FavoriteJourneyEntity,
            newItem: FavoriteJourneyEntity
        ): Boolean {
            return oldItem == newItem
        }
    }
}
