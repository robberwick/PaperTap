package com.robberwick.papertap

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Setup toolbar with back button
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Settings"

        // Load preferences fragment
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // CRITICAL: Set the preference manager to use the same SharedPreferences file
            // that the rest of the app uses (defined in Constants.kt)
            preferenceManager.sharedPreferencesName = Constants.Preference_File_Key

            // Load preferences from XML
            setPreferencesFromResource(R.xml.preferences, rootKey)

            android.util.Log.d("SettingsFragment", "Preferences configured to use file: ${Constants.Preference_File_Key}")
        }
    }
}
