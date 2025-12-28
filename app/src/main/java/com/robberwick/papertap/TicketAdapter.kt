package com.robberwick.papertap

import android.content.res.ColorStateList
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.robberwick.papertap.database.TicketEntity

class TicketAdapter(
    private val onTicketClick: (TicketEntity) -> Unit,
    private val onTicketLongClick: ((TicketEntity) -> Unit)? = null
) : ListAdapter<TicketEntity, TicketAdapter.TicketViewHolder>(TicketDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TicketViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.ticket_list_item, parent, false)
        return TicketViewHolder(view)
    }

    override fun onBindViewHolder(holder: TicketViewHolder, position: Int) {
        val ticket = getItem(position)

        // Find the most recently flashed ticket
        val mostRecentTicket = currentList
            .filter { it.lastFlashedAt != null }
            .maxByOrNull { it.lastFlashedAt!! }

        val isMostRecent = mostRecentTicket != null && ticket.id == mostRecentTicket.id

        holder.bind(ticket, isMostRecent, onTicketClick, onTicketLongClick)
    }

    fun getTicketAt(position: Int): TicketEntity {
        return getItem(position)
    }

    class TicketViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val dateTimeText: TextView = itemView.findViewById(R.id.ticketDateTime)
        private val journeyText: TextView = itemView.findViewById(R.id.ticketJourney)
        private val usageInfoText: TextView = itemView.findViewById(R.id.usageInfo)

        fun bind(ticket: TicketEntity, isMostRecent: Boolean, onTicketClick: (TicketEntity) -> Unit, onTicketLongClick: ((TicketEntity) -> Unit)?) {
            // Show label as primary text
            journeyText.text = ticket.userLabel

            // Show journey metadata if available, otherwise show "Added [date]"
            val journeyInfo = buildJourneyInfo(ticket)
            if (journeyInfo != null) {
                dateTimeText.text = journeyInfo
            } else {
                val dateFormat = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                dateTimeText.text = "Added ${dateFormat.format(java.util.Date(ticket.addedAt))}"
            }

            // Apply background and text colors for most recently flashed ticket
            if (isMostRecent) {
                val primaryContainer = MaterialColors.getColor(
                    itemView,
                    com.google.android.material.R.attr.colorPrimaryContainer
                )
                val onPrimaryContainer = MaterialColors.getColor(
                    itemView,
                    com.google.android.material.R.attr.colorOnPrimaryContainer
                )
                cardView.setCardBackgroundColor(ColorStateList.valueOf(primaryContainer))
                dateTimeText.setTextColor(onPrimaryContainer)
                journeyText.setTextColor(onPrimaryContainer)
                usageInfoText.setTextColor(onPrimaryContainer)
            } else {
                val surface = MaterialColors.getColor(
                    itemView,
                    com.google.android.material.R.attr.colorSurface
                )
                val onSurface = MaterialColors.getColor(
                    itemView,
                    com.google.android.material.R.attr.colorOnSurface
                )
                val onSurfaceVariant = MaterialColors.getColor(
                    itemView,
                    com.google.android.material.R.attr.colorOnSurfaceVariant
                )
                cardView.setCardBackgroundColor(ColorStateList.valueOf(surface))
                dateTimeText.setTextColor(onSurface)
                journeyText.setTextColor(onSurface)
                usageInfoText.setTextColor(onSurfaceVariant)
            }

            // Show usage information if ticket has been flashed
            if (ticket.lastFlashedAt != null && ticket.flashCount > 0) {
                val currentTime = System.currentTimeMillis()
                val timeDiffSeconds = (currentTime - ticket.lastFlashedAt) / 1000

                val relativeTime = if (timeDiffSeconds < 60) {
                    "just now"
                } else {
                    DateUtils.getRelativeTimeSpanString(
                        ticket.lastFlashedAt,
                        currentTime,
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE
                    )
                }

                val usageText = if (ticket.flashCount == 1) {
                    "Last used $relativeTime"
                } else {
                    "Used ${ticket.flashCount} times • Last used $relativeTime"
                }

                usageInfoText.text = usageText
                usageInfoText.visibility = View.VISIBLE
            } else {
                usageInfoText.visibility = View.GONE
            }

            itemView.setOnClickListener {
                onTicketClick(ticket)
            }

            itemView.setOnLongClickListener {
                onTicketLongClick?.invoke(ticket)
                true
            }
        }

        private fun buildJourneyInfo(ticket: TicketEntity): String? {
            val hasOrigin = !ticket.originStationCode.isNullOrEmpty()
            val hasDestination = !ticket.destinationStationCode.isNullOrEmpty()
            val hasTravelDate = ticket.travelDate != null

            // Build the journey string if we have any metadata
            if (!hasOrigin && !hasDestination && !hasTravelDate) {
                return null
            }

            val parts = mutableListOf<String>()

            // Add origin → destination if available
            if (hasOrigin || hasDestination) {
                val originName = ticket.originStationCode?.let {
                    StationLookup.getStationName(it)
                }
                val destName = ticket.destinationStationCode?.let {
                    StationLookup.getStationName(it)
                }

                val routePart = when {
                    hasOrigin && hasDestination ->
                        "${originName ?: ticket.originStationCode} (${ticket.originStationCode}) → ${destName ?: ticket.destinationStationCode} (${ticket.destinationStationCode})"
                    hasOrigin ->
                        "${originName ?: ticket.originStationCode} (${ticket.originStationCode}) → ?"
                    else ->
                        "? → ${destName ?: ticket.destinationStationCode} (${ticket.destinationStationCode})"
                }
                parts.add(routePart)
            }

            // Add travel date if available
            if (hasTravelDate && ticket.travelDate != null) {
                val dateFormat = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
                parts.add(dateFormat.format(java.util.Date(ticket.travelDate)))
            }

            return parts.joinToString(" | ")
        }
    }

    class TicketDiffCallback : DiffUtil.ItemCallback<TicketEntity>() {
        override fun areItemsTheSame(oldItem: TicketEntity, newItem: TicketEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TicketEntity, newItem: TicketEntity): Boolean {
            return oldItem == newItem
        }
    }
}
