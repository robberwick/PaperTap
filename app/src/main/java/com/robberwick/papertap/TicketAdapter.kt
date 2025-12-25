package com.robberwick.papertap

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.robberwick.papertap.database.TicketEntity

class TicketAdapter(
    private val onTicketClick: (TicketEntity) -> Unit
) : ListAdapter<TicketEntity, TicketAdapter.TicketViewHolder>(TicketDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TicketViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.ticket_list_item, parent, false)
        return TicketViewHolder(view)
    }

    override fun onBindViewHolder(holder: TicketViewHolder, position: Int) {
        val ticket = getItem(position)
        holder.bind(ticket, onTicketClick)
    }

    fun getTicketAt(position: Int): TicketEntity {
        return getItem(position)
    }

    class TicketViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateTimeText: TextView = itemView.findViewById(R.id.ticketDateTime)
        private val journeyText: TextView = itemView.findViewById(R.id.ticketJourney)

        fun bind(ticket: TicketEntity, onTicketClick: (TicketEntity) -> Unit) {
            dateTimeText.text = ticket.dateTime
            journeyText.text = ticket.journeySummary

            itemView.setOnClickListener {
                onTicketClick(ticket)
            }
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
