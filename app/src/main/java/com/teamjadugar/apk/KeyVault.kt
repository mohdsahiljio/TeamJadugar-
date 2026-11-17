package com.teamjadugar.apk

import android.util.Base64

object KeyVault {
    // Encrypted strings (Base64 + XOR)
    private val ENC_URL = "Kx8aHw0eCxsdGB4NBRgLCwQFEw=="
    private val ENC_HMAC = "EwUWHhEeAwAeAR8eFB4="
    private val XOR_KEY = "Sable@2026SecureKey"

    private fun decrypt(encoded: String): String {
        val decoded = Base64.decode(encoded, Base64.DEFAULT)
        val keyBytes = XOR_KEY.toByteArray()
        val result = ByteArray(decoded.size)
        for (i in decoded.indices) {
            result[i] = (decoded[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }
        return String(result, Charsets.UTF_8)
    }

    fun getPanelUrl(): String = decrypt(ENC_URL)
    fun getHmacKey(): ByteArray = decrypt(ENC_HMAC).toByteArray()
}
