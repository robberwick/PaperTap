package com.robberwick.papertap

import android.widget.ArrayAdapter
import android.widget.Filter

class StationAdapter(context: android.content.Context, stations: List<Station>) :
    ArrayAdapter<Station>(context, android.R.layout.simple_dropdown_item_1line, stations) {

    private val allStations = stations
    private var filteredStations = stations

    override fun getCount(): Int = filteredStations.size

    override fun getItem(position: Int): Station = filteredStations[position]

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()

                if (constraint.isNullOrBlank()) {
                    results.values = allStations
                    results.count = allStations.size
                } else {
                    val query = constraint.toString().lowercase()
                    val filtered = allStations.filter { station ->
                        station.code.lowercase().contains(query) ||
                        station.name.lowercase().contains(query)
                    }
                    results.values = filtered
                    results.count = filtered.size
                }

                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredStations = (results?.values as? List<Station>) ?: emptyList()
                if (results?.count ?: 0 > 0) {
                    notifyDataSetChanged()
                } else {
                    notifyDataSetInvalidated()
                }
            }

            override fun convertResultToString(resultValue: Any?): CharSequence {
                return (resultValue as? Station)?.toString() ?: ""
            }
        }
    }
}
