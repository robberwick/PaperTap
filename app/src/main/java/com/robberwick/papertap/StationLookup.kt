package com.robberwick.papertap

import android.content.Context
import org.json.JSONArray

data class Station(
    val code: String,
    val name: String
) {
    override fun toString(): String = "$name ($code)"
}

object StationLookup {
    private var stations: List<Station>? = null

    fun initialize(context: Context) {
        if (stations != null) return

        val jsonString = context.resources.openRawResource(R.raw.stations)
            .bufferedReader()
            .use { it.readText() }

        val jsonArray = JSONArray(jsonString)
        stations = (0 until jsonArray.length()).map { index ->
            val stationObject = jsonArray.getJSONObject(index)
            Station(
                code = stationObject.getString("crsCode"),
                name = stationObject.getString("name")
            )
        }
    }

    fun getAllStations(): List<Station> {
        return stations ?: emptyList()
    }

    fun getStationName(code: String): String? {
        return stations?.find { it.code == code }?.name
    }

    fun searchStations(query: String): List<Station> {
        if (query.isBlank()) return getAllStations()

        val lowerQuery = query.lowercase()
        return stations?.filter {
            it.code.lowercase().contains(lowerQuery) ||
            it.name.lowercase().contains(lowerQuery)
        } ?: emptyList()
    }
}
