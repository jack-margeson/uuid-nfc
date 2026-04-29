package com.example.uuidnfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef

class NfcController(
    private val activity: Activity,
    private val nfcAdapter: NfcAdapter,
    private val onResult: (String, NfcState.Mode) -> Unit,
    private val onError: (String) -> Unit
) {
    fun start() {
        val intent = Intent(activity, activity.javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            activity,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        nfcAdapter.enableForegroundDispatch(activity, pendingIntent, null, null)
    }

    fun stop() {
        nfcAdapter.disableForegroundDispatch(activity)
    }

    fun handleIntent(
        intent: Intent,
        mode: NfcState.Mode,
        payloadToWrite: String? = null
    ) {
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            ?: return onError("No NFC tag found")

        when (mode) {
            NfcState.Mode.READ -> readTag(tag, mode)
            NfcState.Mode.WRITE -> writeTag(tag, payloadToWrite ?: "", mode)
        }
    }

    private fun readTag(tag: Tag, mode: NfcState.Mode) {
        try {
            val ndef = Ndef.get(tag)
                ?: return onError("Tag does not support NDEF")

            ndef.connect()

            val message = ndef.cachedNdefMessage
            val record = message?.records?.firstOrNull()

            val text = record?.let { decodeMimeRecord(it) }

            ndef.close()

            onResult(text ?: "", mode)

        } catch (e: Exception) {
            onError("Read failed: ${e.message}")
        }
    }

    private fun writeTag(tag: Tag, text: String, mode: NfcState.Mode) {
        var ndef: Ndef? = null

        try {
            ndef = Ndef.get(tag)
                ?: return onError("Tag does not support NDEF")

            ndef.connect()

            if (!ndef.isWritable) {
                return onError("Tag is not writable")
            }

            val record = NdefRecord.createMime(
                "text/plain",
                text.toByteArray(Charsets.UTF_8)
            )

            val message = NdefMessage(arrayOf(record))

            if (ndef.maxSize < message.toByteArray().size) {
                return onError("Tag too small")
            }

            ndef.writeNdefMessage(message)

            onResult(text, mode)

        } catch (e: Exception) {
            onError("Write failed: ${e.message}")
        } finally {
            try {
                ndef?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun decodeMimeRecord(record: NdefRecord): String {
        return try {
            String(record.payload, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}