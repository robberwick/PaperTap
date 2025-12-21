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
        licenseText.text = """
MIT License

Copyright (c) 2025 Rob Berwick - PaperTap modifications
Copyright (c) 2024 harinworks - NFC E-Paper Writer fork
Copyright (c) 2024 mk-fg - NFC E-Paper Writer fork
Copyright (c) 2021 Joshua Tzucker - Original NFC E-Paper Writer

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
        """.trimIndent()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
