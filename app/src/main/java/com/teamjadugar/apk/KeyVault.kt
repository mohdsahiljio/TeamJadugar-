package com.teamjadugar.apk

import android.util.Base64

object KeyVault {
    // 🔥 Encrypted Strings (URL & HMAC Key)
    // Actual URL: https://panel.spectreapi.net
    // Actual HMAC: SPECTRE_SECURE_2026
    private const val ENC_URL = "AB4eDUsKDB0cAxsbEBgCXh4LDAYcGwAYAho="
    private const val ENC_HMAC = "GxkcHR0fAF4cBhscAx0ZAA=="
    private const val XOR_KEY = "Sable@2026SecureKey"

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
