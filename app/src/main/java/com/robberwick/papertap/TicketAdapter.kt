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
import com.robberwick.papertap.database.TicketWithDisplays
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TicketAdapter(
    private val onTicketClick: (TicketEntity) -> Unit,
    private val onTicketLongClick: ((TicketEntity) -> Unit)? = null,
) : ListAdapter<TicketWithDisplays, TicketAdapter.TicketViewHolder>(TicketDiffCallback()) {
    private var mostRecentTicketId: Long? = null

    fun submitTickets(tickets: List<TicketWithDisplays>) {
        mostRecentTicketId = tickets.asSequence()
            .map { it.ticket }
            .filter { it.lastFlashedAt != null }
            .maxByOrNull { it.lastFlashedAt!! }
            ?.id
        submitList(tickets)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TicketViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.ticket_list_item, parent, false)
        return TicketViewHolder(view)
    }

    override fun onBindViewHolder(holder: TicketViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(
            item = item,
            isMostRecent = item.ticket.id == mostRecentTicketId,
            onTicketClick = onTicketClick,
            onTicketLongClick = onTicketLongClick,
        )
    }

    fun getTicketAt(position: Int): TicketEntity = getItem(position).ticket

    class TicketViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val dateTimeText: TextView = itemView.findViewById(R.id.ticketDateTime)
        private val journeyText: TextView = itemView.findViewById(R.id.ticketJourney)
        private val usageInfoText: TextView = itemView.findViewById(R.id.usageInfo)
        private val lastFlashedDisplayText: TextView = itemView.findViewById(R.id.lastFlashedDisplayText)

        fun bind(
            item: TicketWithDisplays,
            isMostRecent: Boolean,
            onTicketClick: (TicketEntity) -> Unit,
            onTicketLongClick: ((TicketEntity) -> Unit)?,
        ) {
            val ticket = item.ticket
            journeyText.text = ticket.userLabel
            dateTimeText.text = buildJourneyInfo(ticket)
                ?: "Added ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ticket.addedAt))}"

            applyColors(isMostRecent)
            bindUsage(ticket)
            lastFlashedDisplayText.text = displaySummary(item)

            itemView.setOnClickListener { onTicketClick(ticket) }
            itemView.setOnLongClickListener {
                onTicketLongClick?.invoke(ticket)
                true
            }
        }

        private fun applyColors(isMostRecent: Boolean) {
            if (isMostRecent) {
                val primaryContainer = MaterialColors.getColor(
                    itemView,
                    com.google.android.material.R.attr.colorPrimaryContainer,
                )
                val onPrimaryContainer = MaterialColors.getColor(
                    itemView,
                    com.google.android.material.R.attr.colorOnPrimaryContainer,
                )
                cardView.setCardBackgroundColor(ColorStateList.valueOf(primaryContainer))
                dateTimeText.setTextColor(onPrimaryContainer)
                journeyText.setTextColor(onPrimaryContainer)
                usageInfoText.setTextColor(onPrimaryContainer)
            } else {
                val surface = MaterialColors.getColor(
                    itemView,
                    com.google.android.material.R.attr.colorSurface,
                )
                val onSurface = MaterialColors.getColor(
                    itemView,
                    com.google.android.material.R.attr.colorOnSurface,
                )
                val onSurfaceVariant = MaterialColors.getColor(
                    itemView,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                )
                cardView.setCardBackgroundColor(ColorStateList.valueOf(surface))
                dateTimeText.setTextColor(onSurface)
                journeyText.setTextColor(onSurface)
                usageInfoText.setTextColor(onSurfaceVariant)
            }
        }

        private fun bindUsage(ticket: TicketEntity) {
            val lastFlashedAt = ticket.lastFlashedAt
            if (lastFlashedAt == null || ticket.flashCount <= 0) {
                usageInfoText.visibility = View.GONE
                return
            }

            val currentTime = System.currentTimeMillis()
            val relativeTime = if ((currentTime - lastFlashedAt) / 1000 < 60) {
                "just now"
            } else {
                DateUtils.getRelativeTimeSpanString(
                    lastFlashedAt,
                    currentTime,
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                )
            }
            usageInfoText.text = if (ticket.flashCount == 1) {
                "Last used $relativeTime"
            } else {
                "Used ${ticket.flashCount} times • Last used $relativeTime"
            }
            usageInfoText.visibility = View.VISIBLE
        }

        private fun displaySummary(item: TicketWithDisplays): String {
            val displays = item.displays
            return when (displays.size) {
                0 -> "Not yet flashed"
                1 -> "On display: ${displays[0].userLabel ?: displays[0].tagUid}"
                else -> {
                    val names = displays.map { it.userLabel ?: it.tagUid }
                    if (names.all { it.startsWith("UID:") }) {
                        "On displays: ${names.size} displays"
                    } else {
                        val labels = names.take(2).joinToString(", ")
                        if (names.size > 2) {
                            "On displays: $labels, +${names.size - 2} more"
                        } else {
                            "On displays: $labels"
                        }
                    }
                }
            }
        }

        private fun buildJourneyInfo(ticket: TicketEntity): String? {
            val hasOrigin = !ticket.originStationCode.isNullOrEmpty()
            val hasDestination = !ticket.destinationStationCode.isNullOrEmpty()
            val hasTravelDate = ticket.travelDate != null
            if (!hasOrigin && !hasDestination && !hasTravelDate) return null

            val parts = mutableListOf<String>()
            if (hasOrigin || hasDestination) {
                val originName = ticket.originStationCode?.let(StationLookup::getStationName)
                val destinationName = ticket.destinationStationCode?.let(StationLookup::getStationName)
                parts += when {
                    hasOrigin && hasDestination ->
                        "${originName ?: ticket.originStationCode} (${ticket.originStationCode}) → ${destinationName ?: ticket.destinationStationCode} (${ticket.destinationStationCode})"
                    hasOrigin -> "${originName ?: ticket.originStationCode} (${ticket.originStationCode}) → ?"
                    else -> "? → ${destinationName ?: ticket.destinationStationCode} (${ticket.destinationStationCode})"
                }
            }
            ticket.travelDate?.let { travelDate ->
                parts += SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(travelDate))
            }
            return parts.joinToString(" | ")
        }
    }

    class TicketDiffCallback : DiffUtil.ItemCallback<TicketWithDisplays>() {
        override fun areItemsTheSame(
            oldItem: TicketWithDisplays,
            newItem: TicketWithDisplays,
        ): Boolean = oldItem.ticket.id == newItem.ticket.id

        override fun areContentsTheSame(
            oldItem: TicketWithDisplays,
            newItem: TicketWithDisplays,
        ): Boolean = oldItem == newItem
    }
}
