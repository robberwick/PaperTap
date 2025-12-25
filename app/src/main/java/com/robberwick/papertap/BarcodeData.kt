package com.robberwick.papertap

import org.json.JSONObject

/**
 * Data class for storing raw barcode data (for regeneration).
 * Used for all barcode types, including those without RSP6 ticket decoding.
 */
data class BarcodeData(
    val rawData: String,
    val barcodeFormat: Int, // ML Kit Barcode.FORMAT_* constant
    val ticketData: TicketData? = null // Optional decoded ticket data for RSP6 tickets
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("rawData", rawData)
        json.put("barcodeFormat", barcodeFormat)
        if (ticketData != null) {
            android.util.Log.d("BarcodeData", "toJson - saving ticketData with reference: ${ticketData.ticketReference}")
            json.put("ticketData", ticketData.toJson())
        } else {
            android.util.Log.d("BarcodeData", "toJson - no ticketData to save")
        }
        val result = json.toString()
        android.util.Log.d("BarcodeData", "toJson result: ${result.take(200)}")
        return result
    }

    companion object {
        fun fromJson(jsonString: String): BarcodeData? {
            return try {
                val json = JSONObject(jsonString)

                // Parse ticket data if present
                val ticketData = if (json.has("ticketData")) {
                    val ticketDataString = json.getString("ticketData")
                    TicketData.fromJson(ticketDataString)
                } else {
                    null
                }

                android.util.Log.d("BarcodeData", "fromJson - has ticketData: ${json.has("ticketData")}")
                android.util.Log.d("BarcodeData", "fromJson - parsed ticketData: $ticketData")
                android.util.Log.d("BarcodeData", "fromJson - ticketReference: ${ticketData?.ticketReference}")

                BarcodeData(
                    rawData = json.getString("rawData"),
                    barcodeFormat = json.getInt("barcodeFormat"),
                    ticketData = ticketData
                )
            } catch (e: Exception) {
                android.util.Log.e("BarcodeData", "Failed to parse BarcodeData from JSON", e)
                null
            }
        }
    }
}
