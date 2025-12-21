package com.robberwick.papertap

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import androidx.lifecycle.lifecycleScope
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageView
import com.vansuita.pickimage.bundle.PickSetup
import com.vansuita.pickimage.dialog.PickImageDialog
import com.vansuita.pickimage.enums.EPickType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    private var mPreferencesController: Preferences? = null
    private var mHasReFlashableImage: Boolean = false
    private var mSharedImageUri: Uri? = null
    private var mSharedText: String? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup toolbar
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // Logo only, no title text

        // Get user preferences
        mPreferencesController = Preferences(this)

        // Check for previously generated image, enable re-flash button if available
        checkReFlashAbility()

        findViewById<Button>(R.id.reflashButton).setOnClickListener {
            val navIntent = Intent(this, NfcFlasher::class.java)
            startActivity(navIntent)
        }

        // Manual crop/process button - commented out but kept for future use
        // val imageFilePickerProcCTA: Button = findViewById(R.id.cta_image_proc)
        // imageFilePickerProcCTA.setOnClickListener {
        //     val (sw, sh) = mPreferencesController!!.getScreenSizePixels()
        //     CropImage
        //         .activity()
        //         .setGuidelines(CropImageView.Guidelines.ON)
        //         .setAspectRatio(sw, sh)
        //         .setRequestedSize(sw, sh, CropImageView.RequestSizeOptions.RESIZE_EXACT)
        //         .start(this)
        // }

        // Setup QR extraction button
        val extractQrCTA: Button = findViewById(R.id.cta_extract_qr)
        extractQrCTA.setOnClickListener {
            val setup = PickSetup()
                .setTitle("Select image with QR code")
                .setMaxSize(2000) // higher resolution for better QR detection
                .setPickTypes(EPickType.GALLERY, EPickType.CAMERA)
                .setSystemDialog(false)
            PickImageDialog.build(setup).setOnPickResult { result ->
                if (result.error != null) {
                    Toast.makeText(this, result.error.toString(), Toast.LENGTH_LONG).show()
                    return@setOnPickResult
                }
                val bitmap = result.bitmap
                if (bitmap != null) {
                    handleImageQrExtraction(bitmap)
                }
            }.show(this)
        }

        // Set image uri if launched from another app
        mSharedImageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM)
        mSharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        
        // Handle ACTION_VIEW for direct URL opens
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            mSharedText = intent.data.toString()
        }
        
        // Debug logging
        android.util.Log.d("MainActivity", "onCreate - Action: ${intent.action}")
        android.util.Log.d("MainActivity", "onCreate - Type: ${intent.type}")
        android.util.Log.d("MainActivity", "onCreate - Data: ${intent.data}")
        android.util.Log.d("MainActivity", "onCreate - EXTRA_TEXT: ${intent.getStringExtra(Intent.EXTRA_TEXT)}")
        android.util.Log.d("MainActivity", "onCreate - EXTRA_STREAM: ${intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)}")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Set image uri if launched from another app
        mSharedImageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM)
        mSharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        
        // Handle ACTION_VIEW for direct URL opens
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            mSharedText = intent.data.toString()
        }
        
        // Debug logging
        android.util.Log.d("MainActivity", "onNewIntent - Action: ${intent.action}")
        android.util.Log.d("MainActivity", "onNewIntent - Type: ${intent.type}")
        android.util.Log.d("MainActivity", "onNewIntent - Data: ${intent.data}")
        android.util.Log.d("MainActivity", "onNewIntent - EXTRA_TEXT: ${intent.getStringExtra(Intent.EXTRA_TEXT)}")
        android.util.Log.d("MainActivity", "onNewIntent - EXTRA_STREAM: ${intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)}")
    }

    override fun onResume() {
        super.onResume()
        checkReFlashAbility()
        
        // Debug logging to understand what we received
        android.util.Log.d("MainActivity", "onResume called")
        android.util.Log.d("MainActivity", "mSharedText: $mSharedText")
        android.util.Log.d("MainActivity", "mSharedImageUri: $mSharedImageUri")
        
        // Handle shared URL/text (e.g., PDF URLs from browser)
        if (mSharedText != null) {
            val sharedText = mSharedText ?: return
            mSharedText = null
            
            android.util.Log.d("MainActivity", "Processing shared text: $sharedText")
            
            // Extract URL from the shared text (handles various formats)
            val url = extractUrlFromText(sharedText)
            if (url != null) {
                android.util.Log.d("MainActivity", "Extracted URL: $url")
                handleSharedUrl(url)
                return
            } else {
                android.util.Log.d("MainActivity", "No URL found in shared text")
                Toast.makeText(this, "No valid URL found in shared text", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Handle shared file URI
        if (mSharedImageUri == null) return
        
        val uri = mSharedImageUri ?: return
        mSharedImageUri = null
        
        // Check if it's a PDF
        val mimeType = contentResolver.getType(uri)
        if (mimeType == "application/pdf") {
            // Handle PDF - extract QR code
            handlePdfShare(uri)
        } else {
            // Handle image - normal flow
            val (sw, sh) = mPreferencesController!!.getScreenSizePixels()
            CropImage
                .activity(uri)
                .setGuidelines(CropImageView.Guidelines.ON)
                .setAspectRatio(sw, sh)
                .setRequestedSize(sw, sh, CropImageView.RequestSizeOptions.RESIZE_EXACT)
                .start(this)
        }
    }
    
    private fun extractUrlFromText(text: String): String? {
        // Direct URL check
        if (text.startsWith("http://", ignoreCase = true) || 
            text.startsWith("https://", ignoreCase = true)) {
            // Extract just the URL if there's extra text (e.g., "URL\nTitle" format from Firefox)
            return text.lines().firstOrNull { line ->
                line.trim().startsWith("http://", ignoreCase = true) || 
                line.trim().startsWith("https://", ignoreCase = true)
            }?.trim()
        }
        
        // Try to find URL in text using regex
        val urlPattern = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
        return urlPattern.find(text)?.value
    }
    
    private fun handleSharedUrl(url: String) {
        Toast.makeText(this, "Downloading PDF from URL...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                val pdfUri = withContext(Dispatchers.IO) {
                    downloadPdfFromUrl(url)
                }
                
                if (pdfUri != null) {
                    // Successfully downloaded, now process it
                    handlePdfShare(pdfUri)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to download PDF from URL",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@MainActivity,
                    "Error downloading PDF: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun downloadPdfFromUrl(urlString: String): Uri? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.requestMethod = "GET"
            connection.doInput = true
            connection.connect()
            
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }
            
            // Save to temp file
            val tempFile = File(cacheDir, "downloaded_pdf_${System.currentTimeMillis()}.pdf")
            FileOutputStream(tempFile).use { output ->
                connection.inputStream.use { input ->
                    input.copyTo(output)
                }
            }
            
            Uri.fromFile(tempFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            connection?.disconnect()
        }
    }
    
    private fun handlePdfShare(pdfUri: Uri) {
        Toast.makeText(this, "Extracting QR code from PDF...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                val qrBitmap = withContext(Dispatchers.IO) {
                    val extractor = PdfQrExtractor(this@MainActivity)
                    val padding = mPreferencesController!!.getQrPadding()
                    extractor.extractQrCodeFromPdf(pdfUri, padding)
                }
                
                if (qrBitmap != null) {
                    // QR code found, now crop/resize it
                    Toast.makeText(
                        this@MainActivity,
                        "QR code found! Processing...",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    val (sw, sh) = mPreferencesController!!.getScreenSizePixels()
                    val processedBitmap = convertToBlackAndWhite(qrBitmap)
                    val scaledBitmap = Bitmap.createScaledBitmap(processedBitmap, sw, sh, false)
                    flashBitmap(scaledBitmap)
                } else {
                    // QR not found - let user manually select area
                    Toast.makeText(
                        this@MainActivity,
                        "No QR code detected. Opening PDF for manual selection...",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Render first page and let user crop it
                    val firstPageBitmap = withContext(Dispatchers.IO) {
                        renderFirstPage(pdfUri)
                    }
                    
                    if (firstPageBitmap != null) {
                        // Save to temp file and open cropper
                        val tempFile = getFileStreamPath("temp_pdf_page.png")
                        openFileOutput("temp_pdf_page.png", Context.MODE_PRIVATE).use {
                            firstPageBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        
                        val (sw, sh) = mPreferencesController!!.getScreenSizePixels()
                        CropImage
                            .activity(Uri.fromFile(tempFile))
                            .setGuidelines(CropImageView.Guidelines.ON)
                            .setAspectRatio(sw, sh)
                            .setRequestedSize(sw, sh, CropImageView.RequestSizeOptions.RESIZE_EXACT)
                            .start(this@MainActivity)
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to render PDF",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@MainActivity,
                    "Error processing PDF: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun renderFirstPage(pdfUri: Uri): Bitmap? {
        var fileDescriptor: ParcelFileDescriptor? = null
        var pdfRenderer: android.graphics.pdf.PdfRenderer? = null
        
        return try {
            fileDescriptor = contentResolver.openFileDescriptor(pdfUri, "r")
            if (fileDescriptor == null) return null
            
            pdfRenderer = android.graphics.pdf.PdfRenderer(fileDescriptor)
            if (pdfRenderer.pageCount == 0) return null
            
            val page = pdfRenderer.openPage(0)
            val bitmap = Bitmap.createBitmap(
                page.width * 2,
                page.height * 2,
                Bitmap.Config.ARGB_8888
            )
            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            pdfRenderer?.close()
            fileDescriptor?.close()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            val result = CropImage.getActivityResult(resultData)
            var error: String? = null
            var bitmap: Bitmap? = null
            if (resultCode == Activity.RESULT_OK) {
                bitmap = result?.getBitmap(this)
                if (bitmap != null) bitmap = convertToBlackAndWhite(bitmap)
                if (bitmap == null) error = "result not available"
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                error = result!!.error.toString()
            } else return
            if (error != null) {
                Toast.makeText(this, "Crop image failure: $error", Toast.LENGTH_LONG).show()
                return
            }
            flashBitmap(bitmap!!)
        }
    }

    private fun flashBitmap(bitmap: Bitmap) {
        openFileOutput(GeneratedImageFilename, Context.MODE_PRIVATE).use { fileOutStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutStream)
            fileOutStream.close()
            val navIntent = Intent(this, NfcFlasher::class.java)
            startActivity(navIntent)
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkReFlashAbility() {
        val lastGeneratedFile = getFileStreamPath(GeneratedImageFilename)
        
        val welcomeCard = findViewById<MaterialCardView>(R.id.welcomeCard)
        val reflashButton = findViewById<Button>(R.id.reflashButton)
        val reflashPreviewCard = findViewById<MaterialCardView>(R.id.reflashPreviewCard)
        val reflashImagePreview = findViewById<ImageView>(R.id.reflashButtonImage)
        
        if (lastGeneratedFile.exists()) {
            // Hide welcome, show reflash UI
            mHasReFlashableImage = true
            welcomeCard.visibility = android.view.View.GONE
            reflashButton.visibility = android.view.View.VISIBLE
            reflashPreviewCard.visibility = android.view.View.VISIBLE
            
            // Need to set null first, or else Android will cache previous image
            reflashImagePreview.setImageURI(null)
            reflashImagePreview.setImageURI(Uri.fromFile(lastGeneratedFile))
        } else {
            // Show welcome, hide reflash UI
            mHasReFlashableImage = false
            welcomeCard.visibility = android.view.View.VISIBLE
            reflashButton.visibility = android.view.View.GONE
            reflashPreviewCard.visibility = android.view.View.GONE
        }
    }
    
    /**
     * Convert bitmap to pure black and white without dithering
     * This is ideal for QR codes and line art where we want crisp edges
     */
    private fun convertToBlackAndWhite(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Use threshold of 128 (middle grey) to decide black vs white
        val threshold = 128
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = source.getPixel(x, y)
                // Calculate luminance
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                
                // Set to pure black or pure white
                val newColor = if (luminance < threshold) Color.BLACK else Color.WHITE
                result.setPixel(x, y, newColor)
            }
        }
        
        return result
    }
    
    /**
     * Handle QR code extraction from an image
     */
    private fun handleImageQrExtraction(bitmap: Bitmap) {
        Toast.makeText(this, "Detecting QR code...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                val qrBitmap = withContext(Dispatchers.IO) {
                    val padding = mPreferencesController!!.getQrPadding()
                    extractQrFromBitmap(bitmap, padding)
                }
                
                if (qrBitmap != null) {
                    // QR code found, process and flash it
                    Toast.makeText(
                        this@MainActivity,
                        "QR code found! Processing...",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    val (sw, sh) = mPreferencesController!!.getScreenSizePixels()
                    val processedBitmap = convertToBlackAndWhite(qrBitmap)
                    val scaledBitmap = Bitmap.createScaledBitmap(processedBitmap, sw, sh, false)
                    flashBitmap(scaledBitmap)
                } else {
                    // QR not found
                    Toast.makeText(
                        this@MainActivity,
                        "No QR code detected in image",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@MainActivity,
                    "Error extracting QR code: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Extract QR code from bitmap using ML Kit barcode scanning
     */
    private suspend fun extractQrFromBitmap(bitmap: Bitmap, padding: Int): Bitmap? = suspendCoroutine { continuation ->
        val options = com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_AZTEC,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_DATA_MATRIX,
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_PDF417
            )
            .build()
        
        val scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient(options)
        val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
        
        android.util.Log.d("MainActivity", "Starting barcode scan on bitmap ${bitmap.width}x${bitmap.height}")
        
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                android.util.Log.d("MainActivity", "Scan complete. Found ${barcodes.size} barcodes")
                
                if (barcodes.isNotEmpty()) {
                    val barcode = barcodes[0]
                    val boundingBox = barcode.boundingBox
                    
                    android.util.Log.d("MainActivity", "Barcode format: ${barcode.format}, value: ${barcode.rawValue?.take(50)}")
                    
                    if (boundingBox != null) {
                        android.util.Log.d("MainActivity", "Bounding box: $boundingBox")
                        android.util.Log.d("MainActivity", "Using padding: $padding pixels")
                        val left = maxOf(0, boundingBox.left - padding)
                        val top = maxOf(0, boundingBox.top - padding)
                        val width = minOf(bitmap.width - left, boundingBox.width() + padding * 2)
                        val height = minOf(bitmap.height - top, boundingBox.height() + padding * 2)
                        
                        val croppedBitmap = Bitmap.createBitmap(bitmap, left, top, width, height)
                        android.util.Log.d("MainActivity", "Cropped bitmap: ${croppedBitmap.width}x${croppedBitmap.height}")
                        continuation.resume(croppedBitmap)
                    } else {
                        android.util.Log.w("MainActivity", "Barcode found but no bounding box")
                        continuation.resume(null)
                    }
                } else {
                    android.util.Log.d("MainActivity", "No barcodes found")
                    continuation.resume(null)
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("MainActivity", "Barcode scanning failed", e)
                e.printStackTrace()
                continuation.resume(null)
            }
    }

}
