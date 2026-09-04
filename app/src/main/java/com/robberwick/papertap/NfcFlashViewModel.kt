package com.robberwick.papertap

import android.app.Application
import android.graphics.Bitmap
import android.nfc.Tag
import android.nfc.tech.NfcA
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.robberwick.papertap.database.DisplayRepository
import com.robberwick.papertap.database.TicketRepository
import com.robberwick.papertap.waveshare.DisplayModel
import com.robberwick.papertap.waveshare.WaveShareNfcWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface FlashState {
    data object Idle : FlashState
    data class Writing(val progress: Int) : FlashState
    data class Success(val displayUid: String) : FlashState
    data class Error(val message: String) : FlashState
}

class NfcFlashViewModel(application: Application) : AndroidViewModel(application) {
    private val ticketRepository = TicketRepository(application)
    private val displayRepository = DisplayRepository(application)
    private val _state = MutableStateFlow<FlashState>(FlashState.Idle)

    val state: StateFlow<FlashState> = _state.asStateFlow()

    fun isWriting(): Boolean = _state.value is FlashState.Writing

    fun flash(
        tag: Tag,
        bitmap: Bitmap,
        model: DisplayModel,
        ticketId: Long,
        displayUid: String,
    ) {
        if (isWriting()) return

        viewModelScope.launch {
            _state.value = FlashState.Writing(0)
            val result = try {
                writeBitmap(tag, bitmap, model)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = FlashState.Error(
                    "The NFC connection was interrupted. Re-align the display and tap again.",
                )
                return@launch
            }

            if (result == WaveShareNfcWriter.WriteResult.SUCCESS) {
                try {
                    // The display must exist before its mapping once the v9 FK lands.
                    displayRepository.getOrCreateDisplay(displayUid)
                    ticketRepository.addDisplayToTicket(ticketId, displayUid)
                    displayRepository.recordUsage(displayUid)
                    ticketRepository.recordFlashEvent(ticketId)
                    _state.value = FlashState.Success(displayUid)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _state.value = FlashState.Error(
                        "The display updated, but PaperTap could not save its usage history.",
                    )
                }
            } else {
                _state.value = FlashState.Error(
                    when (result) {
                        WaveShareNfcWriter.WriteResult.DIMENSION_MISMATCH ->
                            "The ticket image does not match the selected display model."
                        WaveShareNfcWriter.WriteResult.COMMUNICATION_ERROR ->
                            "The NFC connection was interrupted. Re-align the display and tap again."
                        WaveShareNfcWriter.WriteResult.SUCCESS -> "The display could not be updated."
                    },
                )
            }
        }
    }

    fun consumeTerminalState() {
        if (_state.value !is FlashState.Writing) {
            _state.value = FlashState.Idle
        }
    }

    private suspend fun writeBitmap(
        tag: Tag,
        bitmap: Bitmap,
        model: DisplayModel,
    ): WaveShareNfcWriter.WriteResult = withContext(Dispatchers.IO) {
        val nfc = NfcA.get(tag) ?: return@withContext WaveShareNfcWriter.WriteResult.COMMUNICATION_ERROR
        val writer = WaveShareNfcWriter()
        try {
            if (!writer.connect(nfc)) {
                return@withContext WaveShareNfcWriter.WriteResult.COMMUNICATION_ERROR
            }
            coroutineScope {
                val write = async(Dispatchers.IO) { writer.writeBitmap(model, bitmap) }
                while (!write.isCompleted) {
                    _state.value = FlashState.Writing(writer.progress.coerceIn(0, 100))
                    delay(PROGRESS_POLL_INTERVAL_MS)
                }
                write.await()
            }
        } finally {
            writer.close()
        }
    }

    companion object {
        private const val PROGRESS_POLL_INTERVAL_MS = 100L
    }
}
