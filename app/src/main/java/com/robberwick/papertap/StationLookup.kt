package com.robberwick.papertap

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.util.concurrent.CountDownLatch

data class Station(
    val code: String,
    val name: String
) {
    override fun toString(): String = "$name ($code)"
}

/**
 * Case-insensitive substring match against station code and name, shared by
 * [StationLookup.searchStations] and [StationAdapter] so the two don't drift.
 */
fun List<Station>.filterByQuery(query: String): List<Station> {
    if (query.isBlank()) return this
    val lowerQuery = query.lowercase()
    return filter {
        it.code.lowercase().contains(lowerQuery) || it.name.lowercase().contains(lowerQuery)
    }
}

object StationLookup {
    private const val TAG = "StationLookup"

    @Volatile
    private var stations: List<Station> = emptyList()

    @Volatile
    private var stationsByCode: Map<String, Station> = emptyMap()

    private var initStarted = false
    private val loadedLatch = CountDownLatch(1)

    /**
     * Kicks off a background load of the station dataset (2,970 entries, ~500KB).
     * Safe to call repeatedly from multiple activities' onCreate — only the
     * first call actually starts the load. Parsing happens on a daemon thread,
     * never the caller's thread, so onCreate returns immediately.
     *
     * The accessors below block briefly on [loadedLatch] only if called before
     * the background load finishes, so callers never observe a partially
     * loaded or empty dataset due to a race — worst case they see the same
     * synchronous wait the previous main-thread-parsing implementation always
     * incurred; best/common case (load finishes before the data is needed)
     * they see no wait at all.
     */
    fun initialize(context: Context) {
        synchronized(this) {
            if (initStarted) return
            initStarted = true
            val appContext = context.applicationContext
            Thread({ load(appContext) }, "StationLookup-loader")
                .apply { isDaemon = true }
                .start()
        }
    }

    private fun load(context: Context) {
        try {
            val jsonString = context.resources.openRawResource(R.raw.stations)
                .bufferedReader()
                .use { it.readText() }

            val jsonArray = JSONArray(jsonString)
            val parsed = (0 until jsonArray.length()).map { index ->
                val stationObject = jsonArray.getJSONObject(index)
                Station(
                    code = stationObject.getString("crsCode"),
                    name = stationObject.getString("name")
                )
            }
            stations = parsed
            stationsByCode = parsed.associateBy { it.code }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load station data — station lookups will return empty results", e)
        } finally {
            loadedLatch.countDown()
        }
    }

    private fun awaitLoaded() {
        loadedLatch.await()
    }

    fun getAllStations(): List<Station> {
        awaitLoaded()
        return stations
    }

    fun getStationName(code: String): String? {
        awaitLoaded()
        return stationsByCode[code]?.name
    }

    fun findStation(code: String): Station? {
        awaitLoaded()
        return stationsByCode[code]
    }

    fun searchStations(query: String): List<Station> {
        awaitLoaded()
        return stations.filterByQuery(query)
    }
}
