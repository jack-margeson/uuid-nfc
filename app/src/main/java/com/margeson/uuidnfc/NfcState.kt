package com.margeson.uuidnfc

sealed class NfcState {

    object Idle : NfcState()

    data class Scanning(val mode: Mode) : NfcState()

    data class Success(
        val message: String,
        val mode: Mode
    ) : NfcState()

    data class Error(val message: String) : NfcState()

    enum class Mode {
        READ,
        WRITE
    }
}