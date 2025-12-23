package com.robberwick.papertap

import android.content.Context
import org.json.JSONObject

object StationLookup {
    private var stationMap: Map<String, String>? = null
    
    fun init(context: Context) {
        if (stationMap != null) return
        
        try {
            val json = context.resources.openRawResource(R.raw.station_codes)
                .bufferedReader()
                .use { it.readText() }
            
            val jsonObject = JSONObject(json)
            val map = mutableMapOf<String, String>()
            
            jsonObject.keys().forEach { key ->
                map[key] = jsonObject.getString(key)
            }
            
            stationMap = map
            android.util.Log.d("StationLookup", "Loaded ${map.size} station codes")
        } catch (e: Exception) {
            android.util.Log.e("StationLookup", "Failed to load station codes", e)
            stationMap = emptyMap()
        }
    }
    
    fun getStationName(nlc: String): String {
        return stationMap?.get(nlc) ?: nlc
    }
}
