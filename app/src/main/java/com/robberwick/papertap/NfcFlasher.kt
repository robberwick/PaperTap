package com.robberwick.papertap

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PatternMatcher
import android.os.SystemClock
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import androidx.lifecycle.lifecycleScope
import com.robberwick.papertap.database.TicketEntity
import com.robberwick.papertap.database.TicketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import waveshare.feng.nfctag.activity.WaveShareHandler
import waveshare.feng.nfctag.activity.a
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.math.sin

class NfcFlasher : AppCompatActivity() {
    private var mTicketEntity: TicketEntity? = null
    private lateinit var ticketRepository: TicketRepository
    private var mIsFlashing = false
        get() = field
        set(isFlashing) {
            field = isFlashing

            // Update action card to show progress or instruction
            runOnUiThread {
                val activityIndicator = findViewById<android.widget.ProgressBar>(R.id.activityIndicator)
                val nfcIcon = findViewById<android.widget.ImageView>(R.id.nfcIcon)
                val actionText = findViewById<android.widget.TextView>(R.id.actionText)

                if (isFlashing) {
                    // Show spinner and "Writing ticket" text
                    activityIndicator?.visibility = android.view.View.VISIBLE
                    nfcIcon?.visibility = android.view.View.GONE
                    actionText?.text = "Writing ticket"

                    // Keep screen on during flashing to prevent timeout
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    // Show NFC icon and "Hold to back of display" text
                    activityIndicator?.visibility = android.view.View.GONE
                    nfcIcon?.visibility = android.view.View.VISIBLE
                    actionText?.text = "Hold to back of display"

                    // Allow screen to timeout again when not flashing
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            // Regardless of state change, progress should be reset to zero
            this.mProgressVal = 0
        }
    private var mNfcAdapter: NfcAdapter? = null
    private var mPendingIntent: PendingIntent? = null
    private var mNfcTechList = arrayOf(arrayOf(NfcA::class.java.name))
    private var mNfcIntentFilters: Array<IntentFilter>? = null
    private var mNfcCheckHandler: Handler? = null
    private val mNfcCheckIntervalMs = 250L
    private val mProgressCheckInterval = 50L
    private var mProgressVal: Int = 0
    private var mBitmap: Bitmap? = null
    private var mImgFilePath: String? = null
    private var mImgFileUri: Uri? = null

    // Note: Use of object expression / anon class is so `this` can be used
    // for reference to runnable (which would normally be off-limits)
    private val mNfcCheckCallback: Runnable = object: Runnable {
        override fun run() {
            checkNfcAndAttemptRecover()
            // Loop!
            mNfcCheckHandler?.postDelayed(this, mNfcCheckIntervalMs)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (mImgFileUri != null) {
            outState.putString("serializedGeneratedImgUri",mImgFileUri.toString())
        }
    }

    // @TODO - change intent to just pass raw bytearr? Cleanup path usage?
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_flasher)

        // Setup toolbar with back button
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Initialize repository
        ticketRepository = TicketRepository(this)

        /**
         * Load ticket from database
         */
        val ticketId = intent.getLongExtra("TICKET_ID", -1L)

        android.util.Log.d("NfcFlasher", "onCreate - ticketId: $ticketId")

        if (ticketId != -1L) {
            // Load ticket from database
            android.util.Log.d("NfcFlasher", "Loading ticket from database, ID: $ticketId")
            lifecycleScope.launch {
                mTicketEntity = withContext(Dispatchers.IO) {
                    ticketRepository.getById(ticketId)
                }

                android.util.Log.d("NfcFlasher", "Ticket loaded: $mTicketEntity")

                if (mTicketEntity != null) {
                    val ticketData = mTicketEntity!!.getTicketData()
                    android.util.Log.d("NfcFlasher", "TicketData from entity: $ticketData")
                    android.util.Log.d("NfcFlasher", "TicketData has rawData: ${ticketData?.rawData?.isNotEmpty()}")

                    loadTicketImage(mTicketEntity!!)
                    displayTicketDetails(mTicketEntity!!)
                } else {
                    Toast.makeText(this@NfcFlasher, "Ticket not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        } else {
            Toast.makeText(this, "No ticket provided", Toast.LENGTH_SHORT).show()
            finish()
        }

        /**
         * Actual flasher stuff
         */

        // Action card elements are accessed directly when needed via findViewById

        val originatingIntent = intent

        // Set up intent and intent filters for NFC / NDEF scanning
        // This is part of the setup for foreground dispatch system
        val nfcIntent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        this.mPendingIntent = PendingIntent.getActivity(this, 0, nfcIntent, PendingIntent.FLAG_MUTABLE)
        // Set up the filters
        var ndefIntentFilter: IntentFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        try {
            // android:host
            ndefIntentFilter.addDataAuthority("ext", null)

            // android:pathPattern
            // allow all data paths - see notes below
            ndefIntentFilter.addDataPath(".*", PatternMatcher.PATTERN_SIMPLE_GLOB)
            // NONE of the below work, although at least one or more should
            // I think because the payload isn't getting extracted out into the intent by Android
            // Debugging shows mData.path = null, which makes no sense (it definitely is not, and if
            // I don't intercept AAR, Android definitely tries to open the corresponding app...
            //ndefIntentFilter.addDataPath("waveshare.feng.nfctag.*", PatternMatcher.PATTERN_SIMPLE_GLOB);
            //ndefIntentFilter.addDataPath(".*waveshare\\.feng\\.nfctag.*", PatternMatcher.PATTERN_SIMPLE_GLOB);
            //ndefIntentFilter.addDataPath("waveshare.feng.nfctag", PatternMatcher.PATTERN_LITERAL);
            //ndefIntentFilter.addDataPath("waveshare\\.feng\\.nfctag", PatternMatcher.PATTERN_LITERAL);

            // android:scheme
            ndefIntentFilter.addDataScheme("vnd.android.nfc")
        } catch (e: IntentFilter.MalformedMimeTypeException) {
            Log.e("mimeTypeException", "Invalid / Malformed mimeType")
        }
        mNfcIntentFilters = arrayOf(ndefIntentFilter)

        // Init NFC adapter
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (mNfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device.", Toast.LENGTH_LONG).show()
        }

        // Start NFC check loop in case adapter dies
        startNfcCheckLoop()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    override fun onPause() {
        super.onPause()
        this.stopNfcCheckLoop()
        this.disableForegroundDispatch()
    }

    override fun onResume() {
        super.onResume()

        // Regenerate bitmap from database ticket in case settings changed
        if (mTicketEntity != null) {
            loadTicketImage(mTicketEntity!!)
            displayTicketDetails(mTicketEntity!!)
        }

        this.startNfcCheckLoop()
        this.enableForegroundDispatch()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.i("New intent", "New Intent: $intent")
        Log.v("Intent.action", intent.action ?: "no action")

        val preferences = Preferences(this)
        val screenSizeEnum = preferences.getScreenSizeEnum()

        if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED || intent.action == NfcAdapter.ACTION_TAG_DISCOVERED || intent.action == NfcAdapter.ACTION_TECH_DISCOVERED) {
            val detectedTag: Tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)!!
            val tagId = String(detectedTag.id, StandardCharsets.US_ASCII)
            val tagTechList = detectedTag.techList

            // Do we still have a bitmap to flash?
            val bitmap = this.mBitmap
            if (bitmap == null) {
                Log.v("Missing bitmap", "mBitmap = null")
                return
            }

            // Check for correct NFC type support
            if (tagTechList[0] != "android.nfc.tech.NfcA") {
                Log.v("Invalid tag type. Found:", tagTechList.toString())
                return
            }

            // Do an explicit check for the ID. You may need to add the correct ID for your tag model.
            if (tagId !in WaveShareUIDs) {
                Log.v("Invalid tag ID", "$tagId not in " + WaveShareUIDs.joinToString(", "))
                // Currently, this ID is sometimes coming back corrupted, so it is a unreliable check
                // only enforce check if type != ndef, because in those cases we can't check AAR
                if (intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED) {
                    return
                }
            }

            // ACTION_NDEF_DISCOVERED has the filter applied for the AAR record *type*,
            // but the filter for the payload (dataPath / pathPattern) is not working, so as
            // an extra check, AAR payload will be manually checked, as well as ID
            if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
                var aarFound = false
                val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
                if (rawMsgs != null) {
                    for (msg in rawMsgs) {
                        val ndefMessage: NdefMessage = msg as NdefMessage
                        val records = ndefMessage.records
                        for (record in records) {
                            val payloadStr = String(record.payload)
                            if (!aarFound) aarFound = payloadStr == "waveshare.feng.nfctag"
                            if (aarFound) break
                        }
                        if (aarFound) break
                    }
                }

                if (!aarFound) {
                    Log.v("Bad NDEFs:", "records found, but missing AAR")
                }
            }

            if (!mIsFlashing) {
                // Here we go!!!
                Log.v("Matched!", "Tag is a match! Preparing to flash...")
                playStartSound()
                lifecycleScope.launch {
                    flashBitmap(detectedTag, bitmap, screenSizeEnum)
                }
            } else {
                Log.v("Not flashing", "Flashing already in progress!")
            }
        }
    }

    private suspend fun flashBitmap(tag: Tag, bitmap: Bitmap, screenSizeEnum: Int) {
        this.mIsFlashing = true
        // val waveShareHandler = WaveShareHandler(this)
        val a = a() // Create a new instance.
        val nfcObj = NfcA.get(tag)
        a.a(nfcObj) // Init
        // Override WaveShare's SDK default of 700
        nfcObj.timeout = 1200
        var errorString = ""

        val t: Thread = object : Thread() {
            //Create an new thread
            override fun run() {
                var success = false
                val tntag: NfcA //NFC tag
                val thread = Thread(Runnable
                //Create thread
                {
                    var EPD_total_progress = 0
                    while (EPD_total_progress != -1) {
                        EPD_total_progress = a.c //Read the progress
                        runOnUiThread(Runnable {
                            updateProgressBar(EPD_total_progress)
                        })
                        if (EPD_total_progress == 100) {
                            break
                        }
                        SystemClock.sleep(10)
                    }
                })
                thread.start() //start the thread
                tntag = NfcA.get(tag) //Get the tag instance.
                try {
                    val whether_succeed: Int = a.a(screenSizeEnum, bitmap) //Send picture
                    if (whether_succeed == 1) {
                        success = true
                    }
                } catch (e: IOException) {
                    errorString = e.toString()
                } finally {
                        try {
                            // Need to run toast on main thread...
                            runOnUiThread(Runnable {
                                var toast: Toast? = null
                                if (!success) {
                                    playErrorSound()
                                    toast = Toast.makeText(
                                        applicationContext,
                                        "FAILED to Flash :( $errorString",
                                        Toast.LENGTH_LONG
                                    )
                                } else {
                                    playSuccessSound()

                                    // Record successful flash event
                                    mTicketEntity?.let { ticket ->
                                        lifecycleScope.launch {
                                            withContext(Dispatchers.IO) {
                                                ticketRepository.recordFlashEvent(ticket.id)
                                            }
                                        }
                                    }

                                    toast = Toast.makeText(
                                        applicationContext,
                                        "Success! Flashed display!",
                                        Toast.LENGTH_LONG
                                    )
                                }
                                toast?.show()
                            })
                            Log.v("Final success val", "Success = $success")
                            tntag.close()
                        } catch (e: IOException) { //handle exception error
                            e.printStackTrace()
                            Log.v("Flashing failed", "See trace above")
                        }
                        Log.v("Tag closed", "Setting flash in progress = false")
                        runOnUiThread(Runnable {
                            mIsFlashing = false
                        })
                }
            }
        }
        t.start() //Start thread
    }

    private fun enableForegroundDispatch() {
        this.mNfcAdapter?.enableForegroundDispatch(this, this.mPendingIntent, this.mNfcIntentFilters, this.mNfcTechList )
    }

    private fun disableForegroundDispatch() {
        this.mNfcAdapter?.disableForegroundDispatch(this)
    }

    private fun startNfcCheckLoop() {
        if (mNfcCheckHandler == null) {
            Log.v("NFC Check Loop", "START")
            mNfcCheckHandler = Handler(Looper.getMainLooper())
            mNfcCheckHandler?.postDelayed(mNfcCheckCallback, mNfcCheckIntervalMs)
        }
    }

    private fun stopNfcCheckLoop() {
        if (mNfcCheckHandler != null) {
            mNfcCheckHandler?.removeCallbacks(mNfcCheckCallback)
        }
        mNfcCheckHandler = null
    }

    private fun checkNfcAndAttemptRecover() {
        if (mNfcAdapter != null) {
            var isEnabled = false
            // Apparently querying the property can cause it to get updated
            // https://stackoverflow.com/a/55691449/11447682
            try {
                isEnabled = mNfcAdapter?.isEnabled ?: false
                if (!isEnabled) {
                    Log.v("NFC Check #1", "NFC is disabled. Checking again.")
                }
            } catch (_: Exception) {}
            try {
                isEnabled = mNfcAdapter?.isEnabled ?: false
                if (!isEnabled) {
                    Log.v("NFC Check #2", "NFC is disabled.")
                }
            } catch (_: Exception) {}
            if (isEnabled) {
                enableForegroundDispatch()
            } else {
                Log.w("NFC Check", "NFC is disabled - could be waiting on a system recovery")
            }
        } else {
            Log.e("NFC Check", "Adapter is completely unavailable!")
        }
    }

    private fun updateProgressBar(updated: Int) {
        // Progress bar removed - using indeterminate spinner in action card instead
        // The spinner is shown/hidden by the mIsFlashing property setter
    }
    
    private fun playStartSound() {
        try {
            Log.d("NfcFlasher", "Playing start sound")
            val mp = MediaPlayer.create(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            mp?.setOnCompletionListener { it.release() }
            mp?.start()
        } catch (e: Exception) {
            Log.e("NfcFlasher", "Failed to play start sound", e)
        }
    }
    
    private fun playSuccessSound() {
        try {
            Log.d("NfcFlasher", "Playing success sound (Mario coin)")
            Thread {
                try {
                    playTone(988.0, 80)
                    Thread.sleep(80)
                    playTone(1319.0, 150)
                } catch (e: Exception) {
                    Log.e("NfcFlasher", "Error in success sound thread", e)
                }
            }.start()
        } catch (e: Exception) {
            Log.e("NfcFlasher", "Failed to play success sound", e)
        }
    }
    
    private fun playErrorSound() {
        try {
            Log.d("NfcFlasher", "Playing error sound (sad trombone)")
            Thread {
                try {
                    // Sad trombone: Three descending notes - womp womp wommmmp
                    // C4 -> A3 -> F3 (longer)
                    playTone(261.6, 250)   // C4 - womp
                    Thread.sleep(50)
                    playTone(220.0, 250)   // A3 - womp
                    Thread.sleep(50)
                    playTone(174.6, 500)   // F3 - wommmmp (longer and lower)
                } catch (e: Exception) {
                    Log.e("NfcFlasher", "Error in sad trombone thread", e)
                }
            }.start()
        } catch (e: Exception) {
            Log.e("NfcFlasher", "Failed to play error sound", e)
        }
    }
    
    private fun playTone(frequencyHz: Double, durationMs: Int) {
        try {
            Log.d("NfcFlasher", "Playing tone: ${frequencyHz}Hz for ${durationMs}ms")
            val sampleRate = 44100
            val numSamples = (durationMs * sampleRate / 1000)
            val samples = ShortArray(numSamples)
            
            val fadeInSamples = (sampleRate * 0.005).toInt() // 5ms fade in
            val fadeOutSamples = (sampleRate * 0.02).toInt() // 20ms fade out
            
            // Generate sine wave with envelope (fade in/out) to prevent clicking
            for (i in samples.indices) {
                var envelope = 1.0
                
                // Fade in
                if (i < fadeInSamples) {
                    envelope = i.toDouble() / fadeInSamples
                }
                // Fade out
                else if (i > numSamples - fadeOutSamples) {
                    envelope = (numSamples - i).toDouble() / fadeOutSamples
                }
                
                val sample = (sin(2.0 * Math.PI * i / (sampleRate / frequencyHz)) * Short.MAX_VALUE * 0.5 * envelope).toInt().toShort()
                samples[i] = sample
            }
            
            val bufferSize = samples.size * 2
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            
            Log.d("NfcFlasher", "AudioTrack state: ${audioTrack.state}, playState: ${audioTrack.playState}")
            audioTrack.write(samples, 0, samples.size)
            audioTrack.play()
            
            // Wait for playback to complete
            Thread.sleep(durationMs.toLong())
            
            audioTrack.stop()
            audioTrack.release()
            Log.d("NfcFlasher", "Tone completed")
        } catch (e: Exception) {
            Log.e("NfcFlasher", "Failed to play tone", e)
        }
    }

    private fun loadTicketImage(ticket: TicketEntity) {
        // Try to regenerate from TicketData if available
        val ticketData = ticket.getTicketData()
        if (ticketData != null && ticketData.rawData.isNotEmpty()) {
            try {
                val preferences = Preferences(this)
                val (screenWidth, screenHeight) = preferences.getScreenSizePixels()
                val showReference = preferences.getShowTicketReference()
                val ticketReference = if (showReference) ticketData.ticketReference else null

                android.util.Log.d("NfcFlasher", "loadTicketImage - Regenerating from TicketData")
                android.util.Log.d("NfcFlasher", "loadTicketImage - showReference: $showReference")
                android.util.Log.d("NfcFlasher", "loadTicketImage - ticketReference: $ticketReference")

                this.mBitmap = BarcodeGenerator.generateBarcodeWithReference(
                    rawData = ticketData.rawData,
                    format = when (ticketData.barcodeFormat) {
                        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_AZTEC -> com.google.zxing.BarcodeFormat.AZTEC
                        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE -> com.google.zxing.BarcodeFormat.QR_CODE
                        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_DATA_MATRIX -> com.google.zxing.BarcodeFormat.DATA_MATRIX
                        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_PDF417 -> com.google.zxing.BarcodeFormat.PDF_417
                        else -> com.google.zxing.BarcodeFormat.QR_CODE
                    },
                    width = screenWidth,
                    height = screenHeight,
                    edgePadding = preferences.getQrPadding(),
                    ticketReference = ticketReference
                )

                // Display preview
                val imagePreviewElem: ImageView = findViewById(R.id.previewImageView)
                imagePreviewElem.setImageBitmap(this.mBitmap)

                android.util.Log.d("NfcFlasher", "Successfully regenerated bitmap from ticket data")
                return
            } catch (e: Exception) {
                android.util.Log.e("NfcFlasher", "Failed to regenerate from TicketData, falling back to file", e)
            }
        }

        // Fallback: load from file
        val imageFile = File(ticket.qrCodeImagePath)
        if (imageFile.exists()) {
            android.util.Log.d("NfcFlasher", "loadTicketImage - Loading from file: ${ticket.qrCodeImagePath}")
            mImgFileUri = Uri.fromFile(imageFile)
            val imagePreviewElem: ImageView = findViewById(R.id.previewImageView)
            imagePreviewElem.setImageURI(mImgFileUri)

            val bmOptions = BitmapFactory.Options()
            mBitmap = BitmapFactory.decodeFile(imageFile.path, bmOptions)
        } else {
            Toast.makeText(this, "Ticket image not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun displayTicketDetails(ticket: TicketEntity) {
        val ticketDetailsCard: MaterialCardView = findViewById(R.id.flasherTicketDetailsCard)
        val journeySummary: TextView = findViewById(R.id.flasherTicketJourneySummary)
        val dateTime: TextView = findViewById(R.id.flasherTicketDateTime)
        val ticketType: TextView = findViewById(R.id.flasherTicketType)
        val reference: TextView = findViewById(R.id.flasherTicketReference)

        ticketDetailsCard.visibility = android.view.View.VISIBLE
        journeySummary.text = ticket.journeySummary

        if (ticket.dateTime.isNotEmpty() && ticket.dateTime != "Unknown") {
            dateTime.text = ticket.dateTime
            dateTime.visibility = android.view.View.VISIBLE
        } else {
            dateTime.visibility = android.view.View.GONE
        }

        if (ticket.ticketType != null) {
            ticketType.text = ticket.ticketType
            ticketType.visibility = android.view.View.VISIBLE
        } else {
            ticketType.visibility = android.view.View.GONE
        }

        if (ticket.reference != null) {
            reference.text = "Ref: ${ticket.reference}"
            reference.visibility = android.view.View.VISIBLE
        } else {
            reference.visibility = android.view.View.GONE
        }
    }

}