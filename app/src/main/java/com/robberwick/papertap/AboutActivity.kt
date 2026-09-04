package com.robberwick.papertap

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        
        // Setup toolbar with back button
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.about_title)
        
        // Set version info
        val versionText: TextView = findViewById(R.id.versionText)
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            versionText.text = getString(R.string.about_app_version, packageInfo.versionName)
        } catch (e: Exception) {
            versionText.text = getString(R.string.about_app_version, "Unknown")
        }
        
        // Set license text
        val licenseText: TextView = findViewById(R.id.licenseText)
        licenseText.text = getString(R.string.full_license_text)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
