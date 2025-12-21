package com.robberwick.papertap

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var preferences: Preferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // Setup toolbar with back button
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Settings"
        
        preferences = Preferences(this)
        
        setupDisplaySizeSpinner()
        setupQrPaddingSlider()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    private fun setupDisplaySizeSpinner() {
        val spinner: Spinner = findViewById(R.id.displaySizeSpinner)
        val displaySizes = ScreenSizes.map { it }.toTypedArray()
        
        val adapter = ArrayAdapter(this, R.layout.spinner_item, displaySizes)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinner.adapter = adapter
        
        // Set current selection
        val currentSize = preferences.getScreenSizeStr()
        val currentIndex = displaySizes.indexOf(currentSize)
        if (currentIndex >= 0) {
            spinner.setSelection(currentIndex)
        }
        
        // Save on change
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                preferences.setScreenSize(displaySizes[position])
            }
            
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }
    
    private fun setupQrPaddingSlider() {
        val seekBar: SeekBar = findViewById(R.id.qrPaddingSeekBar)
        val valueText: TextView = findViewById(R.id.qrPaddingValue)
        
        // Load current setting (default 5)
        val currentPadding = preferences.getQrPadding()
        seekBar.progress = currentPadding
        valueText.text = "$currentPadding pixels"
        
        // Update on change
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                valueText.text = "$progress pixels"
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.progress?.let { preferences.setQrPadding(it) }
            }
        })
    }
}
