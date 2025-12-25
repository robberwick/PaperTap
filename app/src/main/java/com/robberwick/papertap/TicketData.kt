package com.robberwick.papertap

import org.json.JSONObject

data class TicketData(
    val originStation: String?,
    val destinationStation: String?,
    val travelDate: String?,
    val travelTime: String?,
    val ticketType: String?,
    val railcardType: String?,
    val ticketClass: String?,
    val ticketReference: String?,
    val rawData: String,
    val barcodeFormat: Int = 0 // Barcode format type (AZTEC=4096, QR_CODE=256, etc.)
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("originStation", originStation)
        json.put("destinationStation", destinationStation)
        json.put("travelDate", travelDate)
        json.put("travelTime", travelTime)
        json.put("ticketType", ticketType)
        json.put("railcardType", railcardType)
        json.put("ticketClass", ticketClass)
        json.put("ticketReference", ticketReference)
        json.put("rawData", rawData)
        json.put("barcodeFormat", barcodeFormat)
        return json.toString()
    }
    
    fun getJourneySummary(): String {
        val origin = originStation ?: "Unknown"
        val dest = destinationStation ?: "Unknown"
        val date = travelDate ?: ""
        val time = travelTime ?: ""

        // Only show time if it's not empty and not "00:00"
        val shouldShowTime = time.isNotEmpty() && time != "00:00"

        return if (date.isNotEmpty() && shouldShowTime) {
            "$origin → $dest | $date $time"
        } else if (date.isNotEmpty()) {
            "$origin → $dest | $date"
        } else {
            "$origin → $dest"
        }
    }
    
    companion object {
        fun fromJson(jsonString: String): TicketData? {
            return try {
                val json = JSONObject(jsonString)
                TicketData(
                    originStation = json.optString("originStation").takeIf { it.isNotEmpty() },
                    destinationStation = json.optString("destinationStation").takeIf { it.isNotEmpty() },
                    travelDate = json.optString("travelDate").takeIf { it.isNotEmpty() },
                    travelTime = json.optString("travelTime").takeIf { it.isNotEmpty() },
                    ticketType = json.optString("ticketType").takeIf { it.isNotEmpty() },
                    railcardType = json.optString("railcardType").takeIf { it.isNotEmpty() },
                    ticketClass = json.optString("ticketClass").takeIf { it.isNotEmpty() },
                    ticketReference = json.optString("ticketReference").takeIf { it.isNotEmpty() },
                    rawData = json.getString("rawData"),
                    barcodeFormat = json.optInt("barcodeFormat", 0)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
