package org.example.project.api

interface SmartCardApi {
    fun connect(): Boolean
    fun disconnect()
    fun sendApdu(apdu: ByteArray): ByteArray
}
