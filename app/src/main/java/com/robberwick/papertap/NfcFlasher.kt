package com.robberwick.papertap

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.os.Build
import android.os.Bundle
import android.os.PatternMatcher
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.card.MaterialCardView
import com.robberwick.papertap.database.TicketEntity
import com.robberwick.papertap.database.TicketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

class NfcFlasher : AppCompatActivity() {
    private var mTicketEntity: TicketEntity? = null
    private var ticketId: Long = -1L
    private lateinit var ticketRepository: TicketRepository
    private lateinit var flashViewModel: NfcFlashViewModel
    private lateinit var statusText: TextView
    private lateinit var displayModelText: TextView
    private lateinit var statusProgressIndicator: com.google.android.material.progressindicator.LinearProgressIndicator

    private var mIsFlashing = false
        set(isFlashing) {
            if (field == isFlashing) return
            field = isFlashing
            if (isFlashing) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                statusText.text = getString(R.string.status_writing_ticket)
                statusProgressIndicator.progress = 0
                statusProgressIndicator.visibility = android.view.View.VISIBLE
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                statusText.text = getString(R.string.status_tap_to_write)
                statusProgressIndicator.visibility = android.view.View.GONE
            }
        }

    private var mNfcAdapter: NfcAdapter? = null
    private var mPendingIntent: PendingIntent? = null
    private val mNfcTechList = arrayOf(arrayOf(NfcA::class.java.name))
    private var mNfcIntentFilters: Array<IntentFilter>? = null
    private var mBitmap: Bitmap? = null



    // @TODO - change intent to just pass raw bytearr? Cleanup path usage?
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_flasher)

        // Setup toolbar with back button
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        ticketRepository = TicketRepository(this)
        flashViewModel = ViewModelProvider(this)[NfcFlashViewModel::class.java]

        // Initialize StationLookup
        StationLookup.initialize(this)

        // Initialize status UI elements
        statusText = findViewById(R.id.statusText)
        displayModelText = findViewById(R.id.displayModelText)
        statusProgressIndicator = findViewById(R.id.statusProgressIndicator)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                flashViewModel.state.collect(::renderFlashState)
            }
        }

        // C2: surface the selected display model on the flash screen
        refreshDisplayModelLabel()

        // C3: one-time onboarding dialog; the status line doubles as the
        // persistent "how to hold the display" hint until first write.
        statusText.text = getString(R.string.onboarding_hint)
        val onboardingPreferences = Preferences(this)
        if (!onboardingPreferences.isOnboardingShown()) {
            onboardingPreferences.setOnboardingShown()
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.welcome_title)
                .setMessage(getString(R.string.welcome_message) + "\n\n" + getString(R.string.onboarding_hint))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        /**
         * Load ticket from database
         */
        ticketId = intent.getLongExtra("TICKET_ID", -1L)

        if (BuildConfig.DEBUG) Log.d("NfcFlasher", "onCreate - ticketId: $ticketId")

        if (ticketId != -1L) {
            if (BuildConfig.DEBUG) Log.d("NfcFlasher", "Loading ticket from database, ID: $ticketId")
            lifecycleScope.launch {
                mTicketEntity = withContext(Dispatchers.IO) {
                    ticketRepository.getById(ticketId)
                }

                if (BuildConfig.DEBUG) Log.d("NfcFlasher", "Ticket loaded: $mTicketEntity")

                if (mTicketEntity != null) {
                    if (BuildConfig.DEBUG) Log.d("NfcFlasher", "Ticket loaded: ${mTicketEntity!!.userLabel}")

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

        // Set up intent and intent filters for NFC / NDEF scanning
        // This is part of the setup for foreground dispatch system
        val nfcIntent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        this.mPendingIntent = PendingIntent.getActivity(this, 0, nfcIntent, PendingIntent.FLAG_MUTABLE)
        val ndefIntentFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        try {
            ndefIntentFilter.addDataAuthority("ext", null)
            ndefIntentFilter.addDataPath(".*", PatternMatcher.PATTERN_SIMPLE_GLOB)
            ndefIntentFilter.addDataScheme("vnd.android.nfc")
        } catch (_: IntentFilter.MalformedMimeTypeException) {
            Log.e("mimeTypeException", "Invalid / Malformed mimeType")
        }
        mNfcIntentFilters = arrayOf(ndefIntentFilter)

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (mNfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device.", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshDisplayModelLabel() {
        displayModelText.text = getString(
            R.string.status_display_model,
            Preferences(this).getDisplayModel().label,
        )
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onPause() {
        disableForegroundDispatch()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        refreshDisplayModelLabel()
        enableForegroundDispatch()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
            intent.action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            intent.action != NfcAdapter.ACTION_TECH_DISCOVERED
        ) return

        val detectedTag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        } ?: run {
            rejectTag("PaperTap could not read this NFC tag.")
            return
        }

        val ndefMessages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES,
                android.os.Parcelable::class.java,
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        }
        val model = Preferences(this).getDisplayModel()
        when (val validation = WaveShareTagValidator.validate(
            tag = detectedTag,
            model = model,
            requiresAar = intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED,
            ndefMessages = ndefMessages,
        )) {
            is TagValidationResult.Rejected -> rejectTag(validation.message)
            is TagValidationResult.Valid -> {
                val bitmap = mBitmap
                if (bitmap == null) {
                    rejectTag("The ticket image is still being prepared. Please try again.")
                    return
                }
                if (ticketId == -1L) {
                    rejectTag("No ticket is available to write.")
                    return
                }
                if (flashViewModel.isWriting()) {
                    Toast.makeText(this, "A display write is already in progress.", Toast.LENGTH_SHORT).show()
                    return
                }
                playStartSound()
                flashViewModel.flash(
                    tag = detectedTag,
                    bitmap = bitmap,
                    model = model,
                    ticketId = ticketId,
                    displayUid = validation.trackingUid,
                )
            }
        }
    }

    private fun rejectTag(message: String) {
        playErrorSound()
        flashHaptic(success = false)
        setStatusCardState(message, isError = true)
    }

    private fun renderFlashState(state: FlashState) {
        when (state) {
            FlashState.Idle -> {
                mIsFlashing = false
                setStatusCardState(getString(R.string.status_tap_to_write), isError = false)
            }
            is FlashState.Writing -> {
                mIsFlashing = true
                updateProgressBar(state.progress)
                setStatusCardState(getString(R.string.status_writing_ticket), isError = false)
            }
            is FlashState.Success -> {
                mIsFlashing = false
                playSuccessSound()
                flashHaptic(success = true)
                setStatusCardState(getString(R.string.status_success), isError = false)
                flashViewModel.consumeTerminalState()
            }
            is FlashState.Error -> {
                mIsFlashing = false
                playErrorSound()
                flashHaptic(success = false)
                setStatusCardState(state.message, isError = true)
                flashViewModel.consumeTerminalState()
            }
        }
    }

    private fun setStatusCardState(message: String, isError: Boolean) {
        statusText.text = message
        statusText.setTextColor(
            if (isError) {
                com.google.android.material.color.MaterialColors.getColor(
                    statusText,
                    com.google.android.material.R.attr.colorError,
                )
            } else {
                com.google.android.material.color.MaterialColors.getColor(
                    statusText,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                )
            },
        )
    }

    private fun flashHaptic(success: Boolean) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (success) {
                    vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    val timings = longArrayOf(0, 80, 100, 80)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
                }
            } else {
                @Suppress("DEPRECATION")
                if (success) {
                    vibrator.vibrate(120)
                } else {
                    vibrator.vibrate(longArrayOf(0, 80, 100, 80), -1)
                }
            }
        } catch (e: Exception) {
            Log.e("NfcFlasher", "Failed to play haptic feedback", e)
        }
    }


    private fun enableForegroundDispatch() {
        this.mNfcAdapter?.enableForegroundDispatch(this, this.mPendingIntent, this.mNfcIntentFilters, this.mNfcTechList )
    }

    private fun disableForegroundDispatch() {
        this.mNfcAdapter?.disableForegroundDispatch(this)
    }


    private fun updateProgressBar(updated: Int) {
        statusProgressIndicator.setProgressCompat(updated.coerceIn(0, 100), true)
    }
    
    private fun playStartSound() {
        try {
            if (BuildConfig.DEBUG) Log.d("NfcFlasher", "Playing start sound")
            val mp = MediaPlayer.create(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            mp?.setOnCompletionListener { it.release() }
            mp?.start()
        } catch (e: Exception) {
            Log.e("NfcFlasher", "Failed to play start sound", e)
        }
    }
    
    private fun playSuccessSound() {
        try {
            if (BuildConfig.DEBUG) Log.d("NfcFlasher", "Playing success sound (Mario coin)")
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
            if (BuildConfig.DEBUG) Log.d("NfcFlasher", "Playing error sound (sad trombone)")
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
            if (BuildConfig.DEBUG) Log.d("NfcFlasher", "Playing tone: ${frequencyHz}Hz for ${durationMs}ms")
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
            
            if (BuildConfig.DEBUG) Log.d("NfcFlasher", "AudioTrack state: ${audioTrack.state}, playState: ${audioTrack.playState}")
            audioTrack.write(samples, 0, samples.size)
            audioTrack.play()
            
            // Wait for playback to complete
            Thread.sleep(durationMs.toLong())
            
            audioTrack.stop()
            audioTrack.release()
            if (BuildConfig.DEBUG) Log.d("NfcFlasher", "Tone completed")
        } catch (e: Exception) {
            Log.e("NfcFlasher", "Failed to play tone", e)
        }
    }

    private suspend fun loadTicketImage(ticket: TicketEntity) {
        try {
            val preferences = Preferences(this)
            val displayModel = preferences.getDisplayModel()
            val screenWidth = displayModel.width
            val screenHeight = displayModel.height

            // Build list of labels based on settings
            val labels = mutableListOf<BarcodeLabel>()

            // Add station codes if enabled and available (50% smaller)
            val showStationCodes = preferences.getShowStationCodesOnBarcode()
            if (showStationCodes && !ticket.originStationCode.isNullOrEmpty() && !ticket.destinationStationCode.isNullOrEmpty()) {
                val stationCodesText = "${ticket.originStationCode} → ${ticket.destinationStationCode}"
                labels.add(BarcodeLabel(stationCodesText, sizeMultiplier = 1.0f))
                if (BuildConfig.DEBUG) Log.d("NfcFlasher", "Adding station codes to barcode: $stationCodesText (50% size)")
            }

            // Add travel date if enabled and available (70% smaller)
            val showTravelDate = preferences.getShowTravelDateOnBarcode()
            if (showTravelDate && ticket.travelDate != null) {
                val dateFormat = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
                val dateText = dateFormat.format(java.util.Date(ticket.travelDate))
                labels.add(BarcodeLabel(dateText, sizeMultiplier = 1.0f))
                if (BuildConfig.DEBUG) Log.d("NfcFlasher", "Adding travel date to barcode: $dateText (70% smaller)")
            }

            if (BuildConfig.DEBUG) Log.d("NfcFlasher", "loadTicketImage - Regenerating from raw barcode data")
            if (BuildConfig.DEBUG) Log.d("NfcFlasher", "loadTicketImage - labels: $labels")

            // ZXing generation and per-pixel compositing are CPU work; keep
            // them off the main thread and generate only once per Activity.
            val generatedBitmap = withContext(Dispatchers.Default) {
                BarcodeGenerator.generateBarcodeWithLabel(
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
                    labels = labels,
                )
            }
            this.mBitmap = generatedBitmap

            // Display preview
            val imagePreviewElem: ImageView = findViewById(R.id.previewImageView)
            imagePreviewElem.setImageBitmap(this.mBitmap)

            if (BuildConfig.DEBUG) Log.d("NfcFlasher", "Successfully regenerated bitmap from raw barcode data")
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