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
                val filtered = allStations.filterByQuery(constraint?.toString() ?: "")
                results.values = filtered
                results.count = filtered.size
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredStations = (results?.values as? List<Station>) ?: emptyList()
                notifyDataSetChanged()
            }

            override fun convertResultToString(resultValue: Any?): CharSequence {
                return (resultValue as? Station)?.toString() ?: ""
            }
        }
    }
}
