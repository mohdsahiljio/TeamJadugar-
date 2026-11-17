package com.teamjadugar.apk

import android.util.Base64

object KeyVault {
    // 🔥 Pure Base64 (No XOR, No Corruption)
    // URL: https://panel.spectreapi.net
    private const val B64_URL = "aHR0cHM6Ly9wYW5lbC5zcGVjdHJlYXBpLm5ldA=="
    
    // HMAC: SPECTRE_SIG_999X_SECURE
    private const val B64_HMAC = "U1BFQ1RSRV9TSUdfOTk5WF9TRUNVUkU="

    fun getPanelUrl(): String {
        return String(Base64.decode(B64_URL, Base64.DEFAULT), Charsets.UTF_8)
    }

    fun getHmacKey(): ByteArray {
        return Base64.decode(B64_HMAC, Base64.DEFAULT)
    }
}
