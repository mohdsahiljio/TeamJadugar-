package com.teamjadugar.apk

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SessionStore {
    private const val PREF = "sable_secure_session"

    private fun sp(ctx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            ctx, PREF, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun currentKey(ctx: Context): String =
        sp(ctx).getString("key", "") ?: ""
    fun saveKey(ctx: Context, key: String) {
        sp(ctx).edit().putString("key", key).apply()
    }
    fun clear(ctx: Context) { sp(ctx).edit().clear().apply() }
}
