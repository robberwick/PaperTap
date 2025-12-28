package com.robberwick.papertap

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
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
import com.google.android.material.card.MaterialCardView
import androidx.lifecycle.lifecycleScope
import com.robberwick.papertap.database.TicketEntity
import com.robberwick.papertap.database.TicketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import waveshare.feng.nfctag.activity.a
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.math.sin

class NfcFlasher : AppCompatActivity() {
    private var mTicketEntity: TicketEntity? = null
    private lateinit var ticketRepository: TicketRepository
    private lateinit var statusText: TextView
    private lateinit var statusProgressIndicator: com.google.android.material.progressindicator.CircularProgressIndicator

    private var mIsFlashing = false
        set(isFlashing) {
            field = isFlashing

            // Manage screen wake lock and UI updates
            runOnUiThread {
                if (isFlashing) {
                    // Keep screen on during flashing to prevent timeout
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                    // Update status UI
                    if (::statusText.isInitialized) {
                        statusText.text = getString(R.string.status_writing_ticket)
                        statusProgressIndicator.visibility = android.view.View.VISIBLE
                    }
                } else {
                    // Allow screen to timeout again when not flashing
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                    // Reset status UI
                    if (::statusText.isInitialized) {
                        statusText.text = getString(R.string.status_tap_to_write)
                        statusProgressIndicator.visibility = android.view.View.GONE
                    }
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
    private var mProgressVal: Int = 0
    private var mBitmap: Bitmap? = null
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

        // Initialize StationLookup
        StationLookup.initialize(this)

        // Initialize status UI elements
        statusText = findViewById(R.id.statusText)
        statusProgressIndicator = findViewById(R.id.statusProgressIndicator)

        /**
         * Load ticket from database
         */
        val ticketId = intent.getLongExtra("TICKET_ID", -1L)

        Log.d("NfcFlasher", "onCreate - ticketId: $ticketId")

        if (ticketId != -1L) {
            // Load ticket from database
            Log.d("NfcFlasher", "Loading ticket from database, ID: $ticketId")
            lifecycleScope.launch {
                mTicketEntity = withContext(Dispatchers.IO) {
                    ticketRepository.getById(ticketId)
                }

                Log.d("NfcFlasher", "Ticket loaded: $mTicketEntity")

                if (mTicketEntity != null) {
                    Log.d("NfcFlasher", "Ticket loaded: ${mTicketEntity!!.userLabel}")

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

        // Set up intent and intent filters for NFC / NDEF scanning
        // This is part of the setup for foreground dispatch system
        val nfcIntent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        this.mPendingIntent = PendingIntent.getActivity(this, 0, nfcIntent, PendingIntent.FLAG_MUTABLE)
        // Set up the filters
        val ndefIntentFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
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
        } catch (_: IntentFilter.MalformedMimeTypeException) {
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
        finish()
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
            @Suppress("DEPRECATION")
            val detectedTag: Tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)!!
            } else {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)!!
            }
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
                Log.v("Invalid tag type", "Found: ${tagTechList.joinToString()}")
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
                @Suppress("DEPRECATION")
                val rawMsgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, android.os.Parcelable::class.java)
                } else {
                    intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
                }
                if (rawMsgs != null) {
                    for (msg in rawMsgs) {
                        val ndefMessage: NdefMessage = msg as NdefMessage
                        val records = ndefMessage.records
                        for (record in records) {
                            val payloadStr = String(record.payload)
                            aarFound = aarFound || payloadStr == "waveshare.feng.nfctag"
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

    private fun flashBitmap(tag: Tag, bitmap: Bitmap, screenSizeEnum: Int) {
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
                //Create thread
                val thread = Thread {
                    var epdTotalProgress = 0
                    while (epdTotalProgress != -1) {
                        epdTotalProgress = a.c //Read the progress
                        runOnUiThread {
                            updateProgressBar(epdTotalProgress)
                        }
                        if (epdTotalProgress == 100) {
                            break
                        }
                        SystemClock.sleep(10)
                    }
                }
                thread.start() //start the thread
                tntag = NfcA.get(tag) //Get the tag instance.
                try {
                    val whetherSucceed = a.a(screenSizeEnum, bitmap) //Send picture
                    if (whetherSucceed == 1) {
                        success = true
                    }
                } catch (e: IOException) {
                    errorString = e.toString()
                } finally {
                        try {
                            // Need to run toast on main thread...
                            runOnUiThread {
                                if (!success) {
                                    playErrorSound()
                                    Toast.makeText(
                                        applicationContext,
                                        "FAILED to Flash :( $errorString",
                                        Toast.LENGTH_LONG
                                    ).show()
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

                                    Toast.makeText(
                                        applicationContext,
                                        "Success! Flashed display!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            Log.v("Final success val", "Success = $success")
                            tntag.close()
                        } catch (e: IOException) { //handle exception error
                            e.printStackTrace()
                            Log.v("Flashing failed", "See trace above")
                        }
                        Log.v("Tag closed", "Setting flash in progress = false")
                        runOnUiThread {
                            mIsFlashing = false
                        }
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

    private fun updateProgressBar(@Suppress("UNUSED_PARAMETER") updated: Int) {
        // Progress is displayed via indeterminate spinner in status card
        // The spinner is shown/hidden by the mIsFlashing property setter
        // We ignore the actual progress value and just show a spinner
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
            @Suppress("DEPRECATION")
            val audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
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
            } else {
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STATIC
                )
            }
            
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
        try {
            val preferences = Preferences(this)
            val (screenWidth, screenHeight) = preferences.getScreenSizePixels()

            // Build list of labels based on settings
            val labels = mutableListOf<BarcodeLabel>()

            // Add station codes if enabled and available (50% smaller)
            val showStationCodes = preferences.getShowStationCodesOnBarcode()
            if (showStationCodes && !ticket.originStationCode.isNullOrEmpty() && !ticket.destinationStationCode.isNullOrEmpty()) {
                val stationCodesText = "${ticket.originStationCode} → ${ticket.destinationStationCode}"
                labels.add(BarcodeLabel(stationCodesText, sizeMultiplier = 1.0f))
                Log.d("NfcFlasher", "Adding station codes to barcode: $stationCodesText (50% size)")
            }

            // Add travel date if enabled and available (70% smaller)
            val showTravelDate = preferences.getShowTravelDateOnBarcode()
            if (showTravelDate && ticket.travelDate != null) {
                val dateFormat = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
                val dateText = dateFormat.format(java.util.Date(ticket.travelDate))
                labels.add(BarcodeLabel(dateText, sizeMultiplier = 1.0f))
                Log.d("NfcFlasher", "Adding travel date to barcode: $dateText (70% smaller)")
            }

            Log.d("NfcFlasher", "loadTicketImage - Regenerating from raw barcode data")
            Log.d("NfcFlasher", "loadTicketImage - labels: $labels")

            // Generate barcode with labels
            this.mBitmap = BarcodeGenerator.generateBarcodeWithLabel(
                rawData = ticket.rawBarcodeData,
                format = when (ticket.barcodeFormat) {
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_AZTEC -> com.google.zxing.BarcodeFormat.AZTEC
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE -> com.google.zxing.BarcodeFormat.QR_CODE
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_DATA_MATRIX -> com.google.zxing.BarcodeFormat.DATA_MATRIX
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_PDF417 -> com.google.zxing.BarcodeFormat.PDF_417
                    else -> com.google.zxing.BarcodeFormat.QR_CODE
                },
                width = screenWidth,
                height = screenHeight,
                edgePadding = preferences.getQrPadding(),
                labels = labels
            )

            // Display preview
            val imagePreviewElem: ImageView = findViewById(R.id.previewImageView)
            imagePreviewElem.setImageBitmap(this.mBitmap)

            Log.d("NfcFlasher", "Successfully regenerated bitmap from raw barcode data")
        } catch (e: Exception) {
            Log.e("NfcFlasher", "Failed to regenerate barcode", e)
            Toast.makeText(this, "Failed to generate barcode: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun displayTicketDetails(ticket: TicketEntity) {
        val ticketDetailsCard: MaterialCardView = findViewById(R.id.flasherTicketDetailsCard)
        val labelText: TextView = findViewById(R.id.flasherTicketLabel)
        val timestampText: TextView = findViewById(R.id.flasherTicketTimestamp)

        ticketDetailsCard.visibility = android.view.View.VISIBLE
        labelText.text = ticket.userLabel

        // Show journey metadata if available, otherwise show "Added [date]"
        val journeyInfo = buildJourneyInfo(ticket)
        if (journeyInfo != null) {
            timestampText.text = journeyInfo
        } else {
            val dateFormat = java.text.SimpleDateFormat("MMM d, yyyy 'at' h:mm a", java.util.Locale.getDefault())
            timestampText.text = "Added ${dateFormat.format(java.util.Date(ticket.addedAt))}"
        }
    }

    private fun buildJourneyInfo(ticket: TicketEntity): String? {
        val hasOrigin = !ticket.originStationCode.isNullOrEmpty()
        val hasDestination = !ticket.destinationStationCode.isNullOrEmpty()
        val hasTravelDate = ticket.travelDate != null

        // Build the journey string if we have any metadata
        if (!hasOrigin && !hasDestination && !hasTravelDate) {
            return null
        }

        val parts = mutableListOf<String>()

        // Add origin → destination if available
        if (hasOrigin || hasDestination) {
            val originName = ticket.originStationCode?.let {
                StationLookup.getStationName(it)
            }
            val destName = ticket.destinationStationCode?.let {
                StationLookup.getStationName(it)
            }

            val routePart = when {
                hasOrigin && hasDestination ->
                    "${originName ?: ticket.originStationCode} (${ticket.originStationCode}) → ${destName ?: ticket.destinationStationCode} (${ticket.destinationStationCode})"
                hasOrigin ->
                    "${originName ?: ticket.originStationCode} (${ticket.originStationCode}) → ?"
                else ->
                    "? → ${destName ?: ticket.destinationStationCode} (${ticket.destinationStationCode})"
            }
            parts.add(routePart)
        }

        // Add travel date if available
        if (hasTravelDate && ticket.travelDate != null) {
            val dateFormat = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
            parts.add(dateFormat.format(java.util.Date(ticket.travelDate)))
        }

        return parts.joinToString(" | ")
    }

}