package com.teamjadugar.apk

import android.util.Base64

object KeyVault {
    // 🔥 Tera Asli URL (https://panel.spectreapi.net)
    private const val ENC_URL = "AB4eDUsKDB0cAxsbEBgCXh4LDAYcGwAYAho="
    
    // 🔥 Tera Naya HMAC Key (AETH_SECURE_HMAC_2026_X99!)
    private const val ENC_HMAC = "HwUdHhsCAxkaEx0aBQRZWRsKDAYcBxoEABEW"
    
    // 🔥 XOR Key
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
