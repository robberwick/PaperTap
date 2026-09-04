package com.robberwick.papertap

import android.content.Context
import android.content.SharedPreferences
import com.robberwick.papertap.Constants.Preference_File_Key
import com.robberwick.papertap.PrefKeys
import com.robberwick.papertap.waveshare.DisplayModel

class Preferences(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences(Preference_File_Key, Context.MODE_PRIVATE)

    fun getDisplayModel(): DisplayModel {
        val storedValue = preferences.getString(
            PrefKeys.DisplaySize,
            DisplayModel.DEFAULT_PREFERENCE_VALUE,
        )
        val model = DisplayModel.fromPreference(storedValue)
            ?: legacyDisplayModel(storedValue)
            ?: DisplayModel.ONE_54_B

        val allowedModel = if (model.isHardwareVerified || experimentalDisplayModelsEnabled()) {
            model
        } else {
            DisplayModel.ONE_54_B
        }

        if (storedValue != allowedModel.preferenceValue) {
            preferences.edit()
                .putString(PrefKeys.DisplaySize, allowedModel.preferenceValue)
                .apply()
        }
        return allowedModel
    }

    fun setDisplayModel(model: DisplayModel) {
        val allowedModel = if (model.isHardwareVerified || experimentalDisplayModelsEnabled()) {
            model
        } else {
            DisplayModel.ONE_54_B
        }
        preferences.edit()
            .putString(PrefKeys.DisplaySize, allowedModel.preferenceValue)
            .apply()
    }

    fun experimentalDisplayModelsEnabled(): Boolean {
        return preferences.getBoolean(PrefKeys.ExperimentalDisplayModels, false)
    }

    fun getQrPadding(): Int {
        return preferences.getInt(PrefKeys.QrPadding, 5)
    }

    fun setQrPadding(padding: Int) {
        preferences.edit().putInt(PrefKeys.QrPadding, padding).apply()
    }


    fun getShowStationCodesOnBarcode(): Boolean {
        return preferences.getBoolean(PrefKeys.ShowStationCodesOnBarcode, false)
    }

    fun setShowStationCodesOnBarcode(show: Boolean) {
        preferences.edit().putBoolean(PrefKeys.ShowStationCodesOnBarcode, show).apply()
    }

    fun getShowTravelDateOnBarcode(): Boolean {
        return preferences.getBoolean(PrefKeys.ShowTravelDateOnBarcode, false)
    }

    fun setShowTravelDateOnBarcode(show: Boolean) {
        preferences.edit().putBoolean(PrefKeys.ShowTravelDateOnBarcode, show).apply()
    }

    private fun legacyDisplayModel(value: String?): DisplayModel? {
        return when (value) {
            "1.54\" v.B" -> DisplayModel.ONE_54_B
            "2.13\"" -> DisplayModel.TWO_13
            "2.9\"" -> DisplayModel.TWO_9
            "4.2\"" -> DisplayModel.FOUR_2
            "7.5\"" -> DisplayModel.SEVEN_5
            "7.5\" HD" -> DisplayModel.SEVEN_5_HD
            "2.7\"" -> DisplayModel.TWO_7
            else -> null
        }
    }
}
