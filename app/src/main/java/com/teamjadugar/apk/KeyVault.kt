package com.teamjadugar.apk

import android.util.Base64

object KeyVault {
    // 🔥 Simple Base64 Strings (No XOR, No Corruption)
    // URL: https://panel.spectreapi.net
    private const val B64_URL = "aHR0cHM6Ly9wYW5lbC5zcGVjdHJlYXBpLm5ldA=="
    // HMAC: SPECTRE_SECURE_2026
    private const val B64_HMAC = "U1BFQ1RSRV9TRUNVUkVfMjAyNg=="

    fun getPanelUrl(): String {
        return String(Base64.decode(B64_URL, Base64.DEFAULT), Charsets.UTF_8)
    }

    fun getHmacKey(): ByteArray {
        return Base64.decode(B64_HMAC, Base64.DEFAULT)
    }
}
