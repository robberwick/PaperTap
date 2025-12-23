package com.robberwick.papertap

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import com.robberwick.papertap.Constants.PreferenceKeys
import com.robberwick.papertap.Constants.Preference_File_Key

class Preferences {
    private var mActivity: Activity
    private var mAppContext: Context

    constructor(activity: Activity) {
        this.mActivity = activity
        this.mAppContext = activity.applicationContext
    }

    fun getPreferences(): SharedPreferences {
        return this.mAppContext.getSharedPreferences(Preference_File_Key, Context.MODE_PRIVATE)
    }

    fun getScreenSize(): String {
        val screenSize = this.getPreferences().getString(PreferenceKeys.DisplaySize, DefaultScreenSize)
        return screenSize ?: DefaultScreenSize
    }

    fun getScreenSizeEnum(): Int {
        val screenSize: String = this.getPreferences().getString(PreferenceKeys.DisplaySize, DefaultScreenSize)!!
        return (ScreenSizes.indexOf(screenSize) + 1)
    }

    fun getScreenSizePixels(): Pair<Int, Int> {
        val screenSize: String = this.getPreferences().getString(PreferenceKeys.DisplaySize, DefaultScreenSize)!!
        return ScreenSizesInPixels[screenSize]!!
    }

    fun showScreenSizePicker(callback: (String) -> Void?) {
        val alertBuilder = AlertDialog.Builder(this.mActivity)
        alertBuilder
            .setTitle("Pick Your Screen Size")
            .setItems(ScreenSizes) { _, which ->
                val selectedSize = ScreenSizes[which]
                with(this.getPreferences().edit()) {
                    putString(PreferenceKeys.DisplaySize, selectedSize)
                    apply()
                }
                callback(selectedSize)
            }
        alertBuilder.show()
    }
    
    // New settings methods
    fun getScreenSizeStr(): String {
        return getScreenSize()
    }
    
    fun setScreenSize(size: String) {
        with(getPreferences().edit()) {
            putString(PreferenceKeys.DisplaySize, size)
            apply()
        }
    }
    
    fun getQrPadding(): Int {
        return getPreferences().getInt(PreferenceKeys.QrPadding, 5)
    }
    
    fun setQrPadding(padding: Int) {
        with(getPreferences().edit()) {
            putInt(PreferenceKeys.QrPadding, padding)
            apply()
        }
    }
    
    fun saveTicketData(ticketData: TicketData) {
        with(getPreferences().edit()) {
            putString(PreferenceKeys.TicketData, ticketData.toJson())
            apply()
        }
    }
    
    fun getTicketData(): TicketData? {
        val json = getPreferences().getString(PreferenceKeys.TicketData, null)
        return if (json != null) {
            TicketData.fromJson(json)
        } else {
            null
        }
    }
    
    fun clearTicketData() {
        with(getPreferences().edit()) {
            remove(PreferenceKeys.TicketData)
            apply()
        }
    }
}