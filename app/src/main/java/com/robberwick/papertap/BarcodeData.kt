package com.robberwick.papertap

/**
 * Data class for storing raw barcode data.
 * Used for all barcode types.
 */
data class BarcodeData(
    val rawData: String,
    val barcodeFormat: Int // ML Kit Barcode.FORMAT_* constant
)
