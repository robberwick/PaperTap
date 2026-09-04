package com.robberwick.papertap

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import com.robberwick.papertap.waveshare.DisplayModel
import java.nio.charset.StandardCharsets

sealed interface TagValidationResult {
    data class Valid(
        val waveShareUid: String,
        val trackingUid: String,
    ) : TagValidationResult
    data class Rejected(val message: String) : TagValidationResult
}

object WaveShareTagValidator {
    fun validate(
        tag: Tag,
        model: DisplayModel,
        requiresAar: Boolean,
        ndefMessages: Array<out android.os.Parcelable>?,
    ): TagValidationResult {
        if (tag.techList.none { it == "android.nfc.tech.NfcA" }) {
            return TagValidationResult.Rejected("This tag does not support NFC-A.")
        }

        val uidBytes = tag.id
        val uid = if (uidBytes?.size == WAVE_SHARE_UID_LENGTH) {
            String(uidBytes, StandardCharsets.US_ASCII)
        } else {
            null
        }
        if (uid !in WaveShareUIDs) {
            return TagValidationResult.Rejected(
                "This is not a supported WaveShare NFC display.",
            )
        }

        if (model == DisplayModel.ONE_54_B && uid != UID_154_B) {
            return TagValidationResult.Rejected(
                "This tag is not the selected 1.54\" B display model.",
            )
        }
        if (model != DisplayModel.ONE_54_B && uid != UID_STANDARD) {
            return TagValidationResult.Rejected(
                "This 1.54\" B tag does not match the selected display model.",
            )
        }

        if (requiresAar && !containsWaveShareAar(ndefMessages)) {
            return TagValidationResult.Rejected(
                "The tag is missing the WaveShare application record.",
            )
        }
        return TagValidationResult.Valid(
            waveShareUid = uid,
            trackingUid = "UID:" + uidBytes.joinToString(":") { byte -> "%02X".format(byte) },
        )
    }

    private fun containsWaveShareAar(messages: Array<out android.os.Parcelable>?): Boolean {
        return messages.orEmpty()
            .filterIsInstance<NdefMessage>()
            .flatMap { it.records.asIterable() }
            .any { record ->
                record.tnf == NdefRecord.TNF_EXTERNAL_TYPE &&
                    String(record.type, StandardCharsets.US_ASCII) == AAR_TYPE &&
                    String(record.payload, StandardCharsets.US_ASCII) == AAR_PACKAGE
            }
    }

    private const val WAVE_SHARE_UID_LENGTH = 7
    private const val UID_154_B = "FSTN10m"
    private const val UID_STANDARD = "WSDZ10m"
    private const val AAR_TYPE = "android.com:pkg"
    private const val AAR_PACKAGE = "waveshare.feng.nfctag"
}
