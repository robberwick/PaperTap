package com.robberwick.papertap

import android.content.Context
import org.json.JSONObject

object FareCodeLookup {
    private var fareMap: Map<String, String>? = null
    
    fun init(context: Context) {
        if (fareMap != null) return
        
        try {
            val json = context.resources.openRawResource(R.raw.fare_codes)
                .bufferedReader()
                .use { it.readText() }
            
            val jsonObject = JSONObject(json)
            val map = mutableMapOf<String, String>()
            
            jsonObject.keys().forEach { key ->
                map[key] = jsonObject.getString(key)
            }
            
            fareMap = map
            android.util.Log.d("FareCodeLookup", "Loaded ${map.size} fare codes")
        } catch (e: Exception) {
            android.util.Log.e("FareCodeLookup", "Failed to load fare codes", e)
            fareMap = emptyMap()
        }
    }
    
    fun getFareName(code: String): String {
        return fareMap?.get(code) ?: code
    }
}
