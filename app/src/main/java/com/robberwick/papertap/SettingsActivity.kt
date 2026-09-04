package com.robberwick.papertap

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.robberwick.papertap.waveshare.DisplayModel

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
            preferenceManager.sharedPreferencesName = Constants.Preference_File_Key
            setPreferencesFromResource(R.xml.preferences, rootKey)
            configureDisplayModelPicker()

            if (BuildConfig.DEBUG) android.util.Log.d(
                "SettingsFragment",
                "Preferences configured to use file: ${Constants.Preference_File_Key}",
            )
        }

        private fun configureDisplayModelPicker() {
            val displayPreference = requireNotNull(
                findPreference<ListPreference>(PrefKeys.DisplaySize),
            )
            val experimentalPreference = requireNotNull(
                findPreference<SwitchPreferenceCompat>(PrefKeys.ExperimentalDisplayModels),
            )

            fun refreshEntries(includeExperimental: Boolean) {
                val models = DisplayModel.selectable(includeExperimental)
                displayPreference.entries = models.map { model ->
                    if (model.isHardwareVerified) {
                        model.label
                    } else {
                        getString(R.string.experimental_display_model_entry, model.label)
                    }
                }.toTypedArray()
                displayPreference.entryValues = models
                    .map { it.preferenceValue }
                    .toTypedArray()

                val selected = DisplayModel.fromPreference(displayPreference.value)
                if (selected !in models) {
                    displayPreference.value = DisplayModel.DEFAULT_PREFERENCE_VALUE
                }
            }

            displayPreference.summaryProvider = Preference.SummaryProvider<ListPreference> { preference ->
                val selected = DisplayModel.fromPreference(preference.value) ?: DisplayModel.ONE_54_B
                if (selected.isHardwareVerified) {
                    selected.label
                } else {
                    getString(R.string.experimental_display_model_warning, selected.label)
                }
            }

            refreshEntries(experimentalPreference.isChecked)
            experimentalPreference.setOnPreferenceChangeListener { _, newValue ->
                refreshEntries(newValue as Boolean)
                true
            }
        }
    }
}
