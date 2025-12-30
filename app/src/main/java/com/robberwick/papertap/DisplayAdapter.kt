package com.robberwick.papertap

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.robberwick.papertap.database.DisplayEntity

class DisplayAdapter(
    private val onDisplayClick: (DisplayEntity) -> Unit
) : ListAdapter<DisplayEntity, DisplayAdapter.DisplayViewHolder>(DisplayDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DisplayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_display, parent, false)
        return DisplayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DisplayViewHolder, position: Int) {
        val display = getItem(position)
        holder.bind(display, onDisplayClick)
    }

    fun getDisplayAt(position: Int): DisplayEntity {
        return getItem(position)
    }

    class DisplayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val labelText: TextView = itemView.findViewById(R.id.displayLabel)
        private val uidText: TextView = itemView.findViewById(R.id.displayUid)
        private val metadataText: TextView = itemView.findViewById(R.id.displayMetadata)

        fun bind(display: DisplayEntity, onDisplayClick: (DisplayEntity) -> Unit) {
            // Show label or "Unnamed Display"
            labelText.text = display.userLabel ?: "Unnamed Display"

            // Always show UID for reference
            uidText.text = display.tagUid

            // Show last used and usage count
            val lastUsedStr = if (display.lastUsedAt != null) {
                DateUtils.getRelativeTimeSpanString(
                    display.lastUsedAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                )
            } else {
                "Never used"
            }

            val usageCount = if (display.useCount == 1) {
                "Flashed once"
            } else {
                "Flashed ${display.useCount} times"
            }

            metadataText.text = "$lastUsedStr • $usageCount"

            itemView.setOnClickListener {
                onDisplayClick(display)
            }
        }
    }

    class DisplayDiffCallback : DiffUtil.ItemCallback<DisplayEntity>() {
        override fun areItemsTheSame(oldItem: DisplayEntity, newItem: DisplayEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DisplayEntity, newItem: DisplayEntity): Boolean {
            return oldItem == newItem
        }
    }
}
