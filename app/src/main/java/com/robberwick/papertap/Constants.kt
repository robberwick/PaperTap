package com.robberwick.papertap

const val PackageName = "com.robberwick.papertap"

val WaveShareUIDs = arrayOf(
    "WSDZ10m",
    "FSTN10m", // 1.54" B model
)

object Constants {
    const val Preference_File_Key = "Preferences"
}

object PrefKeys {
    const val DisplaySize = "Display_Size"
    const val ExperimentalDisplayModels = "Experimental_Display_Models"
    const val QrPadding = "Qr_Padding"
    const val ShowStationCodesOnBarcode = "Show_Station_Codes_On_Barcode"
    const val ShowTravelDateOnBarcode = "Show_Travel_Date_On_Barcode"
}

object IntentKeys {
    const val GeneratedImgPath = "$PackageName.imgUri"
    const val GeneratedImgMime = "$PackageName.imgMime"
}
