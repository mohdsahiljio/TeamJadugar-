package com.teamjadugar.apk

object KeyVault {
    // 🩸 Humne filhal seedha URL daal diya hai taaki connection fix ho jaye
    // Original: "https://panel.spectreapi.net"
    fun getPanelUrl(): String {
        return "https://panel.spectreapi.net"
    }

    // 🩸 HMAC Secret Key
    fun getHmacKey(): ByteArray {
        return "SPECTRE_SECURE_2026".toByteArray()
    }
}
